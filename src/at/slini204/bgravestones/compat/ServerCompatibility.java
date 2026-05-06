package at.slini204.bgravestones.compat;

import at.slini204.bgravestones.GravePlugin;
import org.bukkit.Bukkit;

import java.util.Locale;

public final class ServerCompatibility {

    private final GravePlugin plugin;
    private final ServerPlatform platform;
    private final MinecraftVersion minecraftVersion;
    private final String serverName;
    private final String serverVersion;
    private final String bukkitVersion;

    public ServerCompatibility(GravePlugin plugin) {
        this.plugin = plugin;
        this.serverName = safe(Bukkit.getName());
        this.serverVersion = safe(Bukkit.getVersion());
        this.bukkitVersion = safe(Bukkit.getBukkitVersion());
        this.platform = detectPlatform();
        this.minecraftVersion = MinecraftVersion.parse(this.bukkitVersion);
    }

    public void logSummary() {
        if (!plugin.getConfig().getBoolean("compatibility.logPlatformOnStartup", true)) {
            return;
        }

        plugin.getLogger().info("[bGraveStones] Server platform detected: "
                + platform.displayName()
                + " | Server name: "
                + serverName
                + " | Bukkit version: "
                + bukkitVersion
                + " | Server version: "
                + serverVersion);
    }

    public ServerPlatform platform() {
        return platform;
    }

    public MinecraftVersion minecraftVersion() {
        return minecraftVersion;
    }

    public boolean isFolia() {
        return platform == ServerPlatform.FOLIA;
    }

    public boolean shouldDisablePlugin() {
        return isFolia() && plugin.getConfig().getBoolean("compatibility.folia.disablePlugin", true);
    }

    public String disableReason() {
        if (!isFolia()) {
            return "";
        }

        return "Folia was detected. This plugin currently uses classic Bukkit/Paper scheduler, teleport and entity access patterns. "
                + "Folia support needs a dedicated scheduler/entity adapter and is blocked by default to prevent data or entity issues.";
    }

    public boolean shouldCreateLocatorBarManager() {
        if (!plugin.getConfig().getBoolean("compatibility.features.locatorBar.autoDisableWhenUnsupported", true)) {
            return true;
        }

        return isLocatorBarSupportedByPlatform();
    }

    public boolean shouldStartLocatorBar() {
        return plugin.getConfig().getBoolean("locatorBar.enabled", false) && shouldCreateLocatorBarManager();
    }

    public boolean isLocatorBarSupportedByPlatform() {
        if (isFolia()) {
            return false;
        }

        return minecraftVersion.isAtLeast(1, 21, 6);
    }

    public void logDisabledLocatorBarIfNeeded() {
        if (!plugin.getConfig().getBoolean("locatorBar.enabled", false)) {
            return;
        }

        if (shouldStartLocatorBar()) {
            return;
        }

        plugin.getLogger().warning("[bGraveStones] Locator-Bar support was disabled by compatibility checks. "
                + "Detected platform="
                + platform.displayName()
                + ", minecraft="
                + minecraftVersion.display()
                + ". Locator-Bar requires a compatible non-Folia server on Minecraft 1.21.6+.");
    }

    private ServerPlatform detectPlatform() {
        String serverClassName = Bukkit.getServer().getClass().getName();

        /*
         * Important:
         * Do not use Bukkit#getGlobalRegionScheduler or Server#getGlobalRegionScheduler
         * as a Folia signal. Modern Paper/Purpur API jars can expose these methods as
         * compatibility API even when the running server still uses the classic tick model.
         */

        if (containsIgnoreCase(serverName, "purpur")
                || containsIgnoreCase(serverVersion, "purpur")
                || containsIgnoreCase(serverClassName, "purpur")
                || hasServerClass("org.purpurmc.purpur.PurpurConfig")
                || hasServerClass("org.purpurmc.purpur.PurpurWorldConfig")) {
            return ServerPlatform.PURPUR;
        }

        if (containsIgnoreCase(serverName, "folia")
                || containsIgnoreCase(serverVersion, "folia")
                || containsIgnoreCase(serverClassName, "folia")
                || hasServerClass("io.papermc.paper.threadedregions.RegionizedServer")) {
            return ServerPlatform.FOLIA;
        }

        if (containsIgnoreCase(serverName, "paper")
                || containsIgnoreCase(serverVersion, "paper")
                || containsIgnoreCase(serverClassName, "paper")
                || hasServerClass("io.papermc.paper.configuration.Configuration")
                || hasServerClass("com.destroystokyo.paper.PaperConfig")) {
            return ServerPlatform.PAPER;
        }

        if (containsIgnoreCase(serverName, "spigot")
                || containsIgnoreCase(serverVersion, "spigot")
                || containsIgnoreCase(serverClassName, "spigot")
                || hasServerClass("org.spigotmc.SpigotConfig")) {
            return ServerPlatform.SPIGOT;
        }

        if (serverClassName.toLowerCase(Locale.ROOT).contains("craftbukkit")) {
            return ServerPlatform.CRAFTBUKKIT;
        }

        return ServerPlatform.UNKNOWN;
    }

    private boolean hasServerClass(String className) {
        if (className == null || className.isBlank()) {
            return false;
        }

        ClassLoader[] loaders = new ClassLoader[] {
                Bukkit.getServer().getClass().getClassLoader(),
                Bukkit.class.getClassLoader(),
                getClass().getClassLoader(),
                Thread.currentThread().getContextClassLoader(),
                ClassLoader.getSystemClassLoader()
        };

        for (ClassLoader loader : loaders) {
            if (loader == null) {
                continue;
            }

            try {
                Class.forName(className, false, loader);
                return true;
            } catch (Throwable ignored) {
            }
        }

        return false;
    }

    private static boolean containsIgnoreCase(String text, String search) {
        if (text == null || search == null) {
            return false;
        }

        return text.toLowerCase(Locale.ROOT).contains(search.toLowerCase(Locale.ROOT));
    }

    private static String safe(String value) {
        return value == null ? "unknown" : value;
    }

    public static final class MinecraftVersion {
        private final int major;
        private final int minor;
        private final int patch;
        private final String raw;

        private MinecraftVersion(int major, int minor, int patch, String raw) {
            this.major = major;
            this.minor = minor;
            this.patch = patch;
            this.raw = raw == null ? "unknown" : raw;
        }

        public static MinecraftVersion parse(String raw) {
            if (raw == null || raw.isBlank()) {
                return new MinecraftVersion(0, 0, 0, raw);
            }

            String[] firstPart = raw.split("-", 2);
            String version = firstPart.length == 0 ? raw : firstPart[0];
            String[] parts = version.split("\\.");

            int major = parsePart(parts, 0);
            int minor = parsePart(parts, 1);
            int patch = parsePart(parts, 2);

            return new MinecraftVersion(major, minor, patch, raw);
        }

        public boolean isAtLeast(int wantedMajor, int wantedMinor, int wantedPatch) {
            if (major != wantedMajor) {
                return major > wantedMajor;
            }

            if (minor != wantedMinor) {
                return minor > wantedMinor;
            }

            return patch >= wantedPatch;
        }

        public String display() {
            if (major <= 0) {
                return raw;
            }

            return major + "." + minor + "." + patch;
        }

        private static int parsePart(String[] parts, int index) {
            if (parts == null || index < 0 || index >= parts.length) {
                return 0;
            }

            String part = parts[index];
            if (part == null || part.isBlank()) {
                return 0;
            }

            StringBuilder digits = new StringBuilder();
            for (int i = 0; i < part.length(); i++) {
                char c = part.charAt(i);
                if (!Character.isDigit(c)) {
                    break;
                }
                digits.append(c);
            }

            if (digits.length() == 0) {
                return 0;
            }

            try {
                return Integer.parseInt(digits.toString());
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
    }
}
