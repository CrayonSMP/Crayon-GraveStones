package at.slini.crayonsmp.graves.storage;

import org.bukkit.configuration.file.FileConfiguration;

public class MySqlConfig {

    public boolean enabled;
    public String host;
    public int port;
    public String database;
    public String username;
    public String password;
    public String tablePrefix;
    public boolean useTablePrefix;
    public boolean useSSL;

    public static MySqlConfig from(FileConfiguration c) {
        MySqlConfig cfg = new MySqlConfig();
        cfg.enabled = c.getBoolean("enabled", false);
        cfg.host = c.getString("host", "127.0.0.1");
        cfg.port = c.getInt("port", 3306);
        cfg.database = c.getString("database", "database");
        cfg.username = c.getString("username", "user");
        cfg.password = c.getString("password", "password");
        cfg.tablePrefix = c.getString("tablePrefix", "crayon_");
        cfg.useTablePrefix = c.getBoolean("usetablePrefix", false);
        cfg.useSSL = c.getBoolean("useSSL", false);
        return cfg;
    }

    public boolean isEffectivelyConfigured() {
        if (!this.enabled) return false;
        if (this.host == null || this.host.isBlank()) return false;
        if (this.database == null || this.database.isBlank()) return false;
        if (this.username == null || this.username.isBlank()) return false;
        if (this.password == null || this.password.isBlank()) return false;

        boolean looksLikeSample = "127.0.0.1".equals(this.host) && "database".equalsIgnoreCase(this.database) && "user".equalsIgnoreCase(this.username) && "password".equalsIgnoreCase(this.password);

        return !looksLikeSample;
    }

    public boolean isUsable() {
        return isEffectivelyConfigured();
    }

    public String table(String baseName) {
        if (useTablePrefix && tablePrefix != null && !tablePrefix.isBlank()) {
            return tablePrefix + baseName;
        }
        return baseName;
    }
}
