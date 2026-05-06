package at.slini204.bgravestones.util;

import at.slini204.bgravestones.GravePlugin;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public final class GeneratedFileVersionUpdater {

    private static final int CURRENT_CONFIG_VERSION = 3;
    private static final int CURRENT_MESSAGES_VERSION = 2;
    private static final int CURRENT_MYSQL_CONFIG_VERSION = 1;

    private static final DateTimeFormatter BACKUP_TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final GravePlugin plugin;

    public GeneratedFileVersionUpdater(GravePlugin plugin) {
        this.plugin = plugin;
    }

    public void updateAll() {
        updateFile(new ManagedFile("config.yml", "configVersion", CURRENT_CONFIG_VERSION));
        updateFile(new ManagedFile("messages.yml", "messagesVersion", CURRENT_MESSAGES_VERSION));
        updateFile(new ManagedFile("MySQL.yml", "mysqlConfigVersion", CURRENT_MYSQL_CONFIG_VERSION));
    }

    private void updateFile(ManagedFile managedFile) {
        if (managedFile == null) {
            return;
        }

        File targetFile = new File(plugin.getDataFolder(), managedFile.fileName());
        boolean existedBefore = targetFile.exists();

        if (!targetFile.exists()) {
            plugin.saveResource(managedFile.fileName(), false);
            plugin.getLogger().info("[bGraveStones] Created missing generated file " + managedFile.fileName() + ".");
        }

        YamlConfiguration current = YamlConfiguration.loadConfiguration(targetFile);
        YamlConfiguration defaults = loadDefaultYaml(managedFile.fileName());
        if (defaults == null) {
            plugin.getLogger().warning("[bGraveStones] Could not load default " + managedFile.fileName() + " from plugin jar. Skipping generated file update.");
            return;
        }

        UpdateState state = new UpdateState();

        mergeMissingKeys(defaults, current, "", managedFile.versionKey(), managedFile.fileName(), state);
        updateVersionKey(current, managedFile, state);

        if (!state.changed()) {
            logWarnings(state);
            return;
        }

        if (existedBefore) {
            createBackup(targetFile, managedFile.fileName());
        }

        try {
            current.save(targetFile);
            plugin.getLogger().info("[bGraveStones] Updated "
                    + managedFile.fileName()
                    + " to generated file version "
                    + readVersion(current, managedFile.versionKey(), managedFile.currentVersion())
                    + ". Added "
                    + state.addedKeys()
                    + " missing key(s)."
            );
        } catch (IOException ex) {
            plugin.getLogger().severe("[bGraveStones] Could not save updated " + managedFile.fileName() + ": " + ex.getMessage());
        }

        logWarnings(state);
    }

    private void logWarnings(UpdateState state) {
        if (state == null) {
            return;
        }

        for (String warning : state.warnings()) {
            plugin.getLogger().warning(warning);
        }
    }

    private YamlConfiguration loadDefaultYaml(String resourceName) {
        try (InputStream stream = plugin.getResource(resourceName)) {
            if (stream == null) {
                return null;
            }

            try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                return YamlConfiguration.loadConfiguration(reader);
            }
        } catch (IOException ex) {
            plugin.getLogger().warning("[bGraveStones] Could not read default " + resourceName + ": " + ex.getMessage());
            return null;
        }
    }

    private void mergeMissingKeys(YamlConfiguration defaults, YamlConfiguration current, String path, String versionKey, String fileName, UpdateState state) {
        ConfigurationSection defaultSection = path.isEmpty() ? defaults : defaults.getConfigurationSection(path);
        if (defaultSection == null) {
            return;
        }

        for (String key : defaultSection.getKeys(false)) {
            String fullPath = path.isEmpty() ? key : path + "." + key;

            if (fullPath.equals(versionKey)) {
                continue;
            }

            if (defaults.isConfigurationSection(fullPath)) {
                if (!current.contains(fullPath)) {
                    current.createSection(fullPath);
                    state.markChanged();
                    state.incrementAddedKeys();
                } else if (!current.isConfigurationSection(fullPath)) {
                    state.addWarning("[bGraveStones] "
                            + fullPath
                            + " in "
                            + fileName
                            + " should be a section, but is set to a value. User value was kept and nested defaults were not injected."
                    );
                    continue;
                }

                mergeMissingKeys(defaults, current, fullPath, versionKey, fileName, state);
                continue;
            }

            if (!current.contains(fullPath)) {
                current.set(fullPath, defaults.get(fullPath));
                state.markChanged();
                state.incrementAddedKeys();
            }
        }
    }

    private void updateVersionKey(YamlConfiguration current, ManagedFile managedFile, UpdateState state) {
        Integer existingVersion = parseVersion(current.get(managedFile.versionKey()));

        if (existingVersion == null) {
            current.set(managedFile.versionKey(), managedFile.currentVersion());
            state.markChanged();
            state.addWarning("[bGraveStones] "
                    + managedFile.fileName()
                    + " had no valid "
                    + managedFile.versionKey()
                    + ". It was reset to "
                    + managedFile.currentVersion()
                    + "."
            );
            return;
        }

        if (existingVersion < managedFile.currentVersion()) {
            current.set(managedFile.versionKey(), managedFile.currentVersion());
            state.markChanged();
            return;
        }

        if (existingVersion > managedFile.currentVersion()) {
            state.addWarning("[bGraveStones] "
                    + managedFile.fileName()
                    + " has a newer "
                    + managedFile.versionKey()
                    + " ("
                    + existingVersion
                    + ") than this plugin knows ("
                    + managedFile.currentVersion()
                    + "). Keeping the newer value."
            );
        }
    }

    private Integer parseVersion(Object raw) {
        if (raw instanceof Number number) {
            return number.intValue();
        }

        if (raw == null) {
            return null;
        }

        String value = String.valueOf(raw).trim();
        if (value.isBlank()) {
            return null;
        }

        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private int readVersion(YamlConfiguration current, String versionKey, int fallback) {
        Integer version = parseVersion(current.get(versionKey));
        return version == null ? fallback : version;
    }

    private void createBackup(File originalFile, String fileName) {
        if (originalFile == null || !originalFile.exists()) {
            return;
        }

        File backupDir = new File(plugin.getDataFolder(), "backups/generated-files");
        if (!backupDir.exists() && !backupDir.mkdirs()) {
            plugin.getLogger().warning("[bGraveStones] Could not create generated file backup directory: " + backupDir.getPath());
            return;
        }

        String timestamp = LocalDateTime.now().format(BACKUP_TIMESTAMP_FORMAT);
        String safeFileName = fileName.replaceAll("[^a-zA-Z0-9_.-]", "_");
        File backupFile = new File(backupDir, safeFileName + "." + timestamp + ".bak");

        int duplicateCounter = 1;
        while (backupFile.exists()) {
            backupFile = new File(backupDir, safeFileName + "." + timestamp + "." + duplicateCounter + ".bak");
            duplicateCounter++;
        }

        try {
            Files.copy(originalFile.toPath(), backupFile.toPath(), StandardCopyOption.COPY_ATTRIBUTES);
            plugin.getLogger().info("[bGraveStones] Backup created before generated file update: " + backupFile.getPath());
        } catch (IOException ex) {
            plugin.getLogger().warning("[bGraveStones] Could not create backup for " + fileName + ": " + ex.getMessage());
        }
    }

    private static final class ManagedFile {
        private final String fileName;
        private final String versionKey;
        private final int currentVersion;

        private ManagedFile(String fileName, String versionKey, int currentVersion) {
            this.fileName = fileName;
            this.versionKey = versionKey;
            this.currentVersion = currentVersion;
        }

        private String fileName() {
            return fileName;
        }

        private String versionKey() {
            return versionKey;
        }

        private int currentVersion() {
            return currentVersion;
        }
    }

    private static final class UpdateState {
        private boolean changed;
        private int addedKeys;
        private final List<String> warnings = new ArrayList<>();

        boolean changed() {
            return changed;
        }

        void markChanged() {
            this.changed = true;
        }

        int addedKeys() {
            return addedKeys;
        }

        void incrementAddedKeys() {
            this.addedKeys++;
        }

        List<String> warnings() {
            return warnings;
        }

        void addWarning(String warning) {
            if (warning != null && !warning.isBlank()) {
                this.warnings.add(warning);
            }
        }
    }
}
