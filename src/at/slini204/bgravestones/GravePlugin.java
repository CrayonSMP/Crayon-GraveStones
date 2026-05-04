package at.slini204.bgravestones;

import at.slini204.bgravestones.listener.BlockListener;
import at.slini204.bgravestones.listener.DeathListener;
import at.slini204.bgravestones.listener.EntityProtectionListener;
import at.slini204.bgravestones.listener.ExplosionListener;
import at.slini204.bgravestones.listener.InteractListener;
import at.slini204.bgravestones.listener.PickupListener;
import at.slini204.bgravestones.locator.LocatorBarManager;
import at.slini204.bgravestones.storage.GraveStorage;
import at.slini204.bgravestones.storage.IGraveStorage;
import at.slini204.bgravestones.storage.MySqlConfig;
import at.slini204.bgravestones.storage.MySqlGraveStorage;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class GravePlugin extends JavaPlugin {

    private GraveManager graveManager;
    private IGraveStorage graveStorage;
    private GraveStorage yamlStorage;
    private MessageManager messages;
    private LocatorBarManager locatorBarManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.messages = new MessageManager(this);
        this.messages.reload();

        this.yamlStorage = new GraveStorage(this);
        this.yamlStorage.load();

        boolean migrated = initStorage(true);
        loadActiveStorage();

        if (!migrated) {
            applyYamlLimitCleanup();
        }

        getLogger().info("[bGraveStones] Loaded "
                + this.graveStorage.getAll().size()
                + " graves ("
                + this.graveStorage.getClass().getSimpleName()
                + ").");

        this.locatorBarManager = new LocatorBarManager(this);

        registerListeners();
        registerCommand();

        this.graveManager.bootstrapVisuals();
        this.locatorBarManager.start();

        getLogger().info("[bGraveStones] enabled.");
    }

    @Override
    public void onDisable() {
        if (this.locatorBarManager != null) {
            this.locatorBarManager.shutdown();
        }

        if (this.graveManager != null) {
            this.graveManager.shutdown();
        }

        if (this.graveStorage != null) {
            this.graveStorage.save();
            this.graveStorage.close();
        }

        getLogger().info("[bGraveStones] disabled.");
    }

    private void registerListeners() {
        register(new DeathListener(this.graveManager));
        register(new InteractListener(this.graveManager));
        register(new PickupListener(this.graveManager));
        register(new EntityProtectionListener(this.graveManager));
        register(new BlockListener(this.graveManager));
        register(new ExplosionListener(this.graveManager));

        if (this.locatorBarManager != null) {
            register(this.locatorBarManager);
        }
    }

    private void register(Listener listener) {
        getServer().getPluginManager().registerEvents(listener, (Plugin) this);
    }

    private void registerCommand() {
        PluginCommand cmd = getCommand("graves");

        if (cmd == null) {
            return;
        }

        GraveReloadCommand graveCommand = new GraveReloadCommand(this);
        cmd.setExecutor(graveCommand);
        cmd.setTabCompleter(graveCommand);
    }

    private boolean initStorage(boolean allowMigration) {
        closeNonYamlStorage();

        boolean migrated = false;
        this.graveStorage = this.yamlStorage;

        try {
            MySqlConfig mysqlConfig = loadMySqlConfig();

            if (mysqlConfig.isUsable()) {
                MySqlGraveStorage mysql = new MySqlGraveStorage(this, mysqlConfig);
                mysql.connect();
                mysql.load();

                if (allowMigration) {
                    int migratedCount = mysql.migrateFrom(this.yamlStorage);

                    if (migratedCount > 0) {
                        migrated = true;
                        getLogger().info("Migrated " + migratedCount + " gravestones from graves.yml to MySQL.");
                    }
                }

                this.graveStorage = mysql;
                getLogger().info("Using MySQL storage for gravestones.");
            } else {
                getLogger().info("MySQL.yml is disabled, incomplete, or still contains sample values. Using YAML storage.");
            }
        } catch (Exception ex) {
            getLogger().warning("MySQL storage could not be initialized. Falling back to YAML. Reason: " + ex.getMessage());
            this.graveStorage = this.yamlStorage;
        }

        if (this.graveManager == null) {
            this.graveManager = new GraveManager(this, this.graveStorage);
        } else {
            this.graveManager.setStorage(this.graveStorage);
            this.graveManager.reload();
        }

        return migrated;
    }

    private void loadActiveStorage() {
        if (this.graveStorage == null) {
            return;
        }

        try {
            this.graveStorage.load();
        } catch (Throwable t) {
            getLogger().severe("[bGraveStones] Storage load failed: " + t.getMessage());
            t.printStackTrace();
        }
    }

    public void reloadPlugin() {
        reloadConfig();

        if (this.messages != null) {
            this.messages.reload();
        }

        initStorage(false);
        loadActiveStorage();
        applyYamlLimitCleanup();

        if (this.graveManager != null) {
            this.graveManager.reload();
            this.graveManager.bootstrapVisuals();
        }

        if (this.locatorBarManager != null) {
            this.locatorBarManager.start();
        }

        if (this.graveStorage != null) {
            getLogger().info("[bGraveStones] Loaded "
                    + this.graveStorage.getAll().size()
                    + " graves ("
                    + this.graveStorage.getClass().getSimpleName()
                    + ").");
        }
    }

    private void closeNonYamlStorage() {
        if (this.graveStorage != null && this.graveStorage != this.yamlStorage) {
            this.graveStorage.close();
        }
    }

    public GraveManager getGraveManager() {
        return this.graveManager;
    }

    public IGraveStorage getGraveStorage() {
        return this.graveStorage;
    }

    public GraveStorage getYamlStorage() {
        return this.yamlStorage;
    }

    public MessageManager getMessages() {
        return this.messages;
    }

    public LocatorBarManager getLocatorBarManager() {
        return this.locatorBarManager;
    }

    private MySqlConfig loadMySqlConfig() {
        File file = new File(getDataFolder(), "MySQL.yml");

        if (!file.exists()) {
            saveResource("MySQL.yml", false);
        }

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        return MySqlConfig.from((FileConfiguration) yaml);
    }

    public boolean isEmergencyPlayer(String playerName) {
        if (playerName == null || playerName.isBlank()) {
            return false;
        }

        Set<String> allowed = new HashSet<>();

        for (String entry : getConfig().getStringList("emergencyPlayers")) {
            if (entry != null && !entry.isBlank()) {
                allowed.add(entry.toLowerCase(Locale.ROOT));
            }
        }

        return allowed.contains(playerName.toLowerCase(Locale.ROOT));
    }

    private void applyYamlLimitCleanup() {
        if (this.graveManager == null || this.graveStorage == null || !isYamlActive()) {
            return;
        }

        int limit = getConfig().getInt("graveLimit", 10);
        int purged = this.graveManager.purgeGravesOverLimitPerPlayer(limit);

        if (purged <= 0) {
            return;
        }

        getLogger().info("[bGraveStones] Removed " + purged + " graves over limit (" + limit + ").");

        Bukkit.getOnlinePlayers().stream()
                .filter(player -> player.hasPermission("graves.admin"))
                .forEach(player -> this.messages.send(player, "cleanup.removed", Map.of(
                        "count", String.valueOf(purged),
                        "limit", String.valueOf(limit)
                )));

        Bukkit.broadcastMessage(this.messages.format("cleanup.broadcast", Map.of(
                "count", String.valueOf(purged),
                "limit", String.valueOf(limit)
        )));
    }

    public boolean isYamlActive() {
        return this.graveStorage == this.yamlStorage;
    }

    public boolean isDebugMysql() {
        return getConfig().getBoolean("debug.mysql", false);
    }

    public boolean isDebugMigration() {
        return getConfig().getBoolean("debug.migration", false);
    }

    public boolean isDebugSql() {
        return getConfig().getBoolean("debug.sql", false);
    }

    public void debugMysql(String msg) {
        if (isDebugMysql()) {
            getLogger().info("[DB-DEBUG] " + msg);
        }
    }

    public void debugSql(String msg) {
        if (isDebugSql()) {
            getLogger().info("[SQL] " + msg);
        }
    }

    public void debugMigration(String msg) {
        if (isDebugMigration()) {
            getLogger().info("[MIGRATION] " + msg);
        }
    }
}