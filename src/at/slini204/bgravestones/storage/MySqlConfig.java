package at.slini204.bgravestones.storage;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.Locale;

public final class MySqlConfig {

    public boolean enabled;
    public String host;
    public int port;
    public String database;
    public String username;
    public String password;
    public String tablePrefix;
    public boolean useTablePrefix;
    public boolean useSSL;
    public String parameters;
    public boolean keepAliveEnabled;
    public int keepAliveIntervalSeconds;
    public int validationTimeoutSeconds;

    public static MySqlConfig from(FileConfiguration c) {
        MySqlConfig cfg = new MySqlConfig();
        cfg.enabled = c.getBoolean("enabled", false);
        cfg.host = c.getString("host", "127.0.0.1");
        cfg.port = c.getInt("port", 3306);
        cfg.database = c.getString("database", "database");
        cfg.username = c.getString("username", "user");
        cfg.password = c.getString("password", "password");
        cfg.tablePrefix = c.getString("tablePrefix", "crayon_");
        cfg.useTablePrefix = c.getBoolean("useTablePrefix", c.getBoolean("usetablePrefix", false));
        cfg.useSSL = c.getBoolean("useSSL", false);
        cfg.parameters = c.getString("parameters", "");
        cfg.keepAliveEnabled = c.getBoolean("keepAlive.enabled", true);
        cfg.keepAliveIntervalSeconds = Math.max(15, c.getInt("keepAlive.intervalSeconds", 240));
        cfg.validationTimeoutSeconds = Math.max(1, c.getInt("keepAlive.validationTimeoutSeconds", 3));
        return cfg;
    }

    public boolean isUsable() {
        if (!enabled) return false;
        if (isBlank(host) || isBlank(database) || isBlank(username) || isBlank(password)) return false;

        boolean looksLikeSample = "127.0.0.1".equals(host)
                && "database".equalsIgnoreCase(database)
                && "user".equalsIgnoreCase(username)
                && "password".equalsIgnoreCase(password);

        return !looksLikeSample;
    }

    public String table(String baseName) {
        String table = useTablePrefix && !isBlank(tablePrefix) ? tablePrefix + baseName : baseName;
        return sanitizeIdentifier(table);
    }

    public String jdbcUrl() {
        StringBuilder url = new StringBuilder("jdbc:mysql://")
                .append(host.trim())
                .append(':')
                .append(port)
                .append('/')
                .append(database.trim())
                .append("?useUnicode=true")
                .append("&characterEncoding=utf8")
                .append("&useSSL=").append(useSSL)
                .append("&serverTimezone=UTC")
                .append("&tcpKeepAlive=true")
                .append("&connectTimeout=5000")
                .append("&socketTimeout=30000");

        String extra = normalizeParameters(parameters);
        if (!extra.isEmpty()) {
            url.append('&').append(extra);
        }
        return url.toString();
    }

    private static String normalizeParameters(String raw) {
        if (raw == null) return "";
        String value = raw.trim();
        while (value.startsWith("?")) value = value.substring(1).trim();
        while (value.startsWith("&")) value = value.substring(1).trim();
        return value;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String sanitizeIdentifier(String identifier) {
        String value = identifier == null ? "" : identifier.trim().toLowerCase(Locale.ROOT);
        if (!value.matches("[a-z0-9_]+")) {
            throw new IllegalArgumentException("Invalid MySQL table name/prefix: " + identifier + " (allowed: a-z, 0-9, _)");
        }
        return value;
    }
}
