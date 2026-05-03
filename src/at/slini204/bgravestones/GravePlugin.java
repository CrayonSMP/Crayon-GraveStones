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

        messages = new MessageManager(this);
        messages.reload();

        yamlStorage = new GraveStorage(this);
        yamlStorage.load();

        boolean migrated = initStorage(true);
        loadActiveStorage();

        if (!migrated) applyYamlLimitCleanup();

        getLogger().info("[bGraveStones] Loaded " + graveStorage.getAll().size() + " graves (" + graveStorage.getClass().getSimpleName() + ").");

        locatorBarManager = new LocatorBarManager(this);
        registerListeners();
        registerCommand();

        graveManager.bootstrapVisuals();
        locatorBarManager.start();

        getLogger().info("[bGraveStones] enabled.");
    }

    @Override
    public void onDisable() {
        if (locatorBarManager != null) locatorBarManager.shutdown();
        if (graveManager != null) graveManager.shutdown();
        if (graveStorage != null) {
            graveStorage.save();
            graveStorage.close();
        }
        getLogger().info("[bGraveStones] disabled.");
    }

    private void registerListeners() {
        register(new DeathListener(graveManager));
        register(new InteractListener(graveManager));
        register(new PickupListener(graveManager));
        register(new EntityProtectionListener(graveManager));
        register(new BlockListener(graveManager));
        register(new ExplosionListener(graveManager));
        register(locatorBarManager);
    }

    private void register(Listener listener) {
        getServer().getPluginManager().registerEvents(listener, (Plugin) this);
    }

    private void registerCommand() {
        PluginCommand cmd = getCommand("graves");
        if (cmd != null) cmd.setExecutor(new GraveReloadCommand(this));
    }

    private boolean initStorage(boolean allowMigration) {
        closeNonYamlStorage();
        boolean migrated = false;
        graveStorage = yamlStorage;

        try {
            MySqlConfig mysqlConfig = loadMySqlConfig();
            if (mysqlConfig.isUsable()) {
                MySqlGraveStorage mysql = new MySqlGraveStorage(this, mysqlConfig);
                mysql.connect();
                mysql.load();

                if (allowMigration) {
                    int migratedCount = mysql.migrateFrom(yamlStorage);
                    if (migratedCount > 0) {
                        migrated = true;
                        getLogger().info("Migrated " + migratedCount + " gravestones from graves.yml to MySQL.");
                    }
                }

                graveStorage = mysql;
                getLogger().info("Using MySQL storage for gravestones.");
            } else {
                getLogger().info("MySQL.yml is disabled, incomplete, or still contains sample values. Using YAML storage.");
            }
        } catch (Exception ex) {
            getLogger().warning("MySQL storage could not be initialized. Falling back to YAML. Reason: " + ex.getMessage());
            graveStorage = yamlStorage;
        }

        if (graveManager == null) {
            graveManager = new GraveManager(this, graveStorage);
        } else {
            graveManager.setStorage(graveStorage);
            graveManager.reload();
        }

        return migrated;
    }

    private void loadActiveStorage() {
        if (graveStorage == null) return;
        try {
            graveStorage.load();
        } catch (Throwable t) {
            getLogger().severe("[bGraveStones] Storage load failed: " + t.getMessage());
            t.printStackTrace();
        }
    }

    public void reloadPlugin() {
        reloadConfig();
        if (messages != null) messages.reload();

        initStorage(false);
        loadActiveStorage();
        applyYamlLimitCleanup();

        if (graveManager != null) {
            graveManager.reload();
            graveManager.bootstrapVisuals();
        }

        if (locatorBarManager != null) locatorBarManager.start();

        if (graveStorage != null) {
            getLogger().info("[bGraveStones] Loaded " + graveStorage.getAll().size() + " graves (" + graveStorage.getClass().getSimpleName() + ").");
        }
    }

    private void closeNonYamlStorage() {
        if (graveStorage != null && graveStorage != yamlStorage) {
            graveStorage.close();
        }
    }

    public GraveManager getGraveManager() {
        return graveManager;
    }

    public IGraveStorage getGraveStorage() {
        return graveStorage;
    }

    public GraveStorage getYamlStorage() {
        return yamlStorage;
    }

    public MessageManager getMessages() {
        return messages;
    }

    public LocatorBarManager getLocatorBarManager() {
        return locatorBarManager;
    }

    private MySqlConfig loadMySqlConfig() {
        File file = new File(getDataFolder(), "MySQL.yml");
        if (!file.exists()) saveResource("MySQL.yml", false);
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        return MySqlConfig.from((FileConfiguration) yaml);
    }

    public boolean isEmergencyPlayer(String playerName) {
        if (playerName == null || playerName.isBlank()) return false;

        Set<String> allowed = new HashSet<>();
        for (String entry : getConfig().getStringList("emergencyPlayers")) {
            if (entry != null && !entry.isBlank()) {
                allowed.add(entry.toLowerCase(Locale.ROOT));
            }
        }
        return allowed.contains(playerName.toLowerCase(Locale.ROOT));
    }

    private void applyYamlLimitCleanup() {
        if (graveManager == null || graveStorage == null || !isYamlActive()) return;

        int limit = getConfig().getInt("graveLimit", 10);
        int purged = graveManager.purgeGravesOverLimitPerPlayer(limit);
        if (purged <= 0) return;

        getLogger().info("[bGraveStones] Removed " + purged + " graves over limit (" + limit + ").");

        Bukkit.getOnlinePlayers().stream()
                .filter(player -> player.hasPermission("graves.admin"))
                .forEach(player -> messages.send(player, "cleanup.removed", Map.of(
                        "count", String.valueOf(purged),
                        "limit", String.valueOf(limit)
                )));

        Bukkit.broadcastMessage(messages.format("cleanup.broadcast", Map.of(
                "count", String.valueOf(purged),
                "limit", String.valueOf(limit)
        )));
    }

    public boolean isYamlActive() {
        return graveStorage == yamlStorage;
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
        if (isDebugMysql()) getLogger().info("[DB-DEBUG] " + msg);
    }

    public void debugSql(String msg) {
        if (isDebugSql()) getLogger().info("[SQL] " + msg);
    }

    public void debugMigration(String msg) {
        if (isDebugMigration()) getLogger().info("[MIGRATION] " + msg);
    }
}
