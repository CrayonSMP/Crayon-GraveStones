package at.slini.crayonsmp.graves;

import at.slini.crayonsmp.graves.listener.BlockListener;
import at.slini.crayonsmp.graves.listener.DeathListener;
import at.slini.crayonsmp.graves.listener.EntityProtectionListener;
import at.slini.crayonsmp.graves.listener.ExplosionListener;
import at.slini.crayonsmp.graves.listener.InteractListener;
import at.slini.crayonsmp.graves.listener.PickupListener;
import at.slini.crayonsmp.graves.storage.GraveStorage;
import at.slini.crayonsmp.graves.storage.IGraveStorage;
import at.slini.crayonsmp.graves.storage.MySqlConfig;
import at.slini.crayonsmp.graves.storage.MySqlGraveStorage;

import java.io.File;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public final class GravePlugin extends JavaPlugin {

    private GraveManager graveManager;
    private IGraveStorage graveStorage;
    private GraveStorage yamlStorage;
    private MessageManager messages;

    public void onEnable() {

        saveDefaultConfig();

        this.messages = new MessageManager(this);
        this.messages.reload();
        this.yamlStorage = new GraveStorage(this);
        this.yamlStorage.load();

        boolean migrated = initStorage(true);
        try {
            this.graveStorage.load();
        } catch (Throwable t) {
            getLogger().severe("[bGraveStones] Storage load failed: " + t.getMessage());
            t.printStackTrace();
        }
        if (!migrated) {
            applyYamlLimitCleanup();
        }
        getLogger().info("[bGraveStones] Loaded " + this.graveStorage.getAll().size() + " graves (" + this.graveStorage.getClass().getSimpleName() + ").");

        this.graveManager.bootstrapVisuals();

        getServer().getPluginManager().registerEvents((Listener) new DeathListener(this.graveManager), (Plugin) this);
        getServer().getPluginManager().registerEvents((Listener) new InteractListener(this.graveManager), (Plugin) this);
        getServer().getPluginManager().registerEvents((Listener) new PickupListener(this.graveManager), (Plugin) this);
        getServer().getPluginManager().registerEvents((Listener) new EntityProtectionListener(this.graveManager), (Plugin) this);
        getServer().getPluginManager().registerEvents((Listener) new BlockListener(this.graveManager), (Plugin) this);
        getServer().getPluginManager().registerEvents((Listener) new ExplosionListener(this.graveManager), (Plugin) this);

        PluginCommand cmd = getCommand("graves");

        if (cmd != null) cmd.setExecutor(new GraveReloadCommand(this));

        getLogger().info("[bGraveStones] enabled.");
    }

    private boolean initStorage(boolean allowMigration) {
        boolean migrated = false;

        this.graveStorage = this.yamlStorage;

        try {
            MySqlConfig mc = loadMySqlConfig();
            if (mc.isUsable()) {
                MySqlGraveStorage mysql = new MySqlGraveStorage(this, mc);
                mysql.connect();
                if (mysql.isConnected()) mysql.load();
                if (allowMigration) {
                    int m = mysql.migrateFrom(this.yamlStorage);
                    if (m > 0) {
                        migrated = true;
                        getLogger().info("Migrated " + m + " gravestones from graves.yml to MySQL.");
                    }
                }

                this.graveStorage = mysql;
                getLogger().info("Using MySQL storage for gravestones.");
            } else {
                getLogger().info("MySQL.yml not configured (or still sample values). Using YAML storage.");
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

    public void reloadPlugin() {
        reloadConfig();

        if (messages != null) messages.reload();

        initStorage(false);

        if (graveStorage != null) {
            try {
                graveStorage.load();
                getLogger().info("[bGraveStones] Loaded " + graveStorage.getAll().size() + " graves (" + graveStorage.getClass().getSimpleName() + ").");
            } catch (Throwable t) {
                getLogger().severe("[bGraveStones] Storage load failed: " + t.getMessage());
                t.printStackTrace();
            }
        }
        applyYamlLimitCleanup();

        if (graveManager != null) {
            graveManager.reload();
            graveManager.bootstrapVisuals();
        }
    }


    public void onDisable() {
        if (this.graveManager != null) this.graveManager.shutdown();
        if (this.graveStorage != null) this.graveStorage.save();
        MySqlGraveStorage.close();
        getLogger().info("[bGraveStones] disabled.");
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

    private MySqlConfig loadMySqlConfig() {
        File f = new File(getDataFolder(), "MySQL.yml");
        if (!f.exists()) saveResource("MySQL.yml", false);
        YamlConfiguration yc = YamlConfiguration.loadConfiguration(f);
        return MySqlConfig.from((FileConfiguration) yc);
    }

    public boolean isEmergencyPlayer(String playerName) {
        if (playerName == null || playerName.isBlank()) return false;
        Set<String> allowed = new HashSet<>();
        for (String s : getConfig().getStringList("emergencyPlayers")) {
            if (s != null && !s.isBlank()) {
                allowed.add(s.toLowerCase(Locale.ROOT));
            }
        }
        return allowed.contains(playerName.toLowerCase(Locale.ROOT));
    }

    private void applyYamlLimitCleanup() {
        if (this.graveManager == null || this.graveStorage == null) return;
        if (!isYamlActive()) return;

        int limit = getConfig().getInt("graveLimit", 10);
        int purged = this.graveManager.purgeGravesOverLimitPerPlayer(limit);

        if (purged <= 0) return;

        getLogger().info("[bGraveStones] Removed " + purged + " graves over limit (" + limit + ").");

        Bukkit.getOnlinePlayers().stream().filter(p -> p.hasPermission("graves.admin")).forEach(p -> messages.send(p, "cleanup.removed", Map.of("count", String.valueOf(purged), "limit", String.valueOf(limit))));
        Bukkit.broadcastMessage(messages.format("cleanup.broadcast", Map.of("count", String.valueOf(purged), "limit", String.valueOf(limit))));
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
        if (isDebugMysql()) getLogger().info("[DB-DEBUG] " + msg);
    }

    public void debugSql(String msg) {
        if (isDebugSql()) getLogger().info("[SQL] " + msg);
    }

    public void debugMigration(String msg) {
        if (isDebugMigration()) getLogger().info("[MIGRATION] " + msg);
    }
}
