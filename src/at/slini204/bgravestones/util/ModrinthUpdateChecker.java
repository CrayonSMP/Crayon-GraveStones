package at.slini204.bgravestones.util;

import at.slini204.bgravestones.GravePlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.scheduler.BukkitTask;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ModrinthUpdateChecker implements Listener {

    private static final String MODRINTH_PROJECT_ID = "noXbBINp";
    private static final String MODRINTH_API_BASE_URL = "https://api.modrinth.com/v2";
    private static final String MODRINTH_PROJECT_URL = "https://modrinth.com/plugin/noXbBINp/versions";
    private static final String USER_AGENT_PREFIX = "SLINIcraftet204/betterGraveStones";
    private static final int TIMEOUT_SECONDS = 10;

    private final GravePlugin plugin;
    private final HttpClient httpClient;

    private BukkitTask scheduledTask;
    private volatile CheckResult latestResult;
    private String lastAnnouncedVersion;

    public ModrinthUpdateChecker(GravePlugin plugin) {
        this.plugin = plugin;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public void start() {
        shutdown();

        if (!isEnabled()) {
            return;
        }

        long intervalTicks = getIntervalTicks();
        long initialDelayTicks = plugin.getConfig().getBoolean("updateChecker.checkOnStartup", true)
                ? 40L
                : intervalTicks;

        scheduledTask = Bukkit.getScheduler().runTaskTimerAsynchronously(
                plugin,
                () -> check(false, true),
                initialDelayTicks,
                intervalTicks
        );
    }

    public void shutdown() {
        if (scheduledTask != null) {
            scheduledTask.cancel();
            scheduledTask = null;
        }
    }

    public void checkNow(CommandSender sender) {
        if (!isEnabled()) {
            send(sender, "&8[&6bGraveStones&8] &7Update checker is disabled in config.yml.");
            return;
        }

        send(sender, "&8[&6bGraveStones&8] &7Checking Modrinth for updates...");
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> check(sender, true));
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!isEnabled() || !plugin.getConfig().getBoolean("updateChecker.notifyAdminsOnJoin", true)) {
            return;
        }

        Player player = event.getPlayer();
        if (!player.hasPermission("graves.admin")) {
            return;
        }

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            CheckResult result = latestResult;
            if (result != null && result.updateAvailable()) {
                sendUpdateMessage(player, result);
            }
        }, 60L);
    }

    private void check(boolean notifyNoUpdate, boolean announceUpdate) {
        check(null, notifyNoUpdate, announceUpdate);
    }

    private void check(CommandSender requester, boolean notifyNoUpdate) {
        check(requester, notifyNoUpdate, true);
    }

    private void check(CommandSender requester, boolean notifyNoUpdate, boolean announceUpdate) {
        CheckResult result;

        try {
            result = fetchLatestVersion();
        } catch (Exception ex) {
            result = CheckResult.error("Could not check Modrinth updates: " + ex.getMessage());
        }

        latestResult = result;

        CheckResult finalResult = result;
        Bukkit.getScheduler().runTask(plugin, () -> handleResult(requester, finalResult, notifyNoUpdate, announceUpdate));
    }

    private void handleResult(CommandSender requester, CheckResult result, boolean notifyNoUpdate, boolean announceUpdate) {
        if (result == null) {
            return;
        }

        if (result.errorMessage() != null) {
            if (requester != null || plugin.getConfig().getBoolean("updateChecker.logErrors", true)) {
                String message = "&8[&6bGraveStones&8] &c" + result.errorMessage();
                if (requester != null) {
                    send(requester, message);
                } else {
                    plugin.getLogger().warning(ChatColor.stripColor(color(message)));
                }
            }
            return;
        }

        if (result.updateAvailable()) {
            if (announceUpdate && !result.latestVersion().equalsIgnoreCase(lastAnnouncedVersion)) {
                lastAnnouncedVersion = result.latestVersion();
                plugin.getLogger().warning("[bGraveStones] A new version is available on Modrinth: "
                        + result.latestVersion()
                        + " (current: "
                        + result.currentVersion()
                        + ") - "
                        + result.projectUrl());

                if (plugin.getConfig().getBoolean("updateChecker.notifyOnlineAdmins", true)) {
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        if (player.hasPermission("graves.admin")) {
                            sendUpdateMessage(player, result);
                        }
                    }
                }
            }

            if (requester != null) {
                sendUpdateMessage(requester, result);
            }
            return;
        }

        if (notifyNoUpdate && requester != null) {
            send(requester, "&8[&6bGraveStones&8] &aYou are running the latest known Modrinth version &f"
                    + result.currentVersion()
                    + "&a.");
        }
    }

    private CheckResult fetchLatestVersion() throws IOException, InterruptedException {
        String currentVersion = plugin.getDescription().getVersion();
        String encodedProjectId = URLEncoder.encode(MODRINTH_PROJECT_ID, StandardCharsets.UTF_8);
        URI uri = URI.create(MODRINTH_API_BASE_URL + "/project/" + encodedProjectId + "/version?include_changelog=false");

        HttpRequest request = HttpRequest.newBuilder(uri)
                .GET()
                .timeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                .header("Accept", "application/json")
                .header("User-Agent", USER_AGENT_PREFIX + "/" + currentVersion)
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        int status = response.statusCode();

        if (status < 200 || status >= 300) {
            throw new IOException("Modrinth API returned HTTP " + status);
        }

        VersionInfo latest = findLatestVersion(response.body());
        if (latest == null) {
            throw new IOException("No matching Modrinth versions found");
        }

        boolean updateAvailable = compareVersions(latest.versionNumber(), currentVersion) > 0;
        return CheckResult.success(currentVersion, latest.versionNumber(), latest.name(), MODRINTH_PROJECT_URL, updateAvailable);
    }

    private VersionInfo findLatestVersion(String json) {
        boolean includePrereleases = plugin.getConfig().getBoolean("updateChecker.includePrereleases", false);
        VersionInfo latest = null;

        for (String object : splitTopLevelObjects(json)) {
            String versionNumber = extractJsonString(object, "version_number");
            if (versionNumber == null || versionNumber.isBlank()) {
                continue;
            }

            String versionType = extractJsonString(object, "version_type");
            if (!includePrereleases && versionType != null && !versionType.equalsIgnoreCase("release")) {
                continue;
            }

            String status = extractJsonString(object, "status");
            if (status != null && !status.equalsIgnoreCase("listed")) {
                continue;
            }

            String name = extractJsonString(object, "name");
            VersionInfo candidate = new VersionInfo(versionNumber, name, versionType == null ? "unknown" : versionType);

            if (latest == null || compareVersions(candidate.versionNumber(), latest.versionNumber()) > 0) {
                latest = candidate;
            }
        }

        return latest;
    }

    private List<String> splitTopLevelObjects(String json) {
        List<String> objects = new ArrayList<>();
        if (json == null || json.isBlank()) {
            return objects;
        }

        boolean inString = false;
        boolean escaped = false;
        int depth = 0;
        int objectStart = -1;

        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);

            if (escaped) {
                escaped = false;
                continue;
            }

            if (c == '\\' && inString) {
                escaped = true;
                continue;
            }

            if (c == '"') {
                inString = !inString;
                continue;
            }

            if (inString) {
                continue;
            }

            if (c == '{') {
                if (depth == 0) {
                    objectStart = i;
                }
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0 && objectStart >= 0) {
                    objects.add(json.substring(objectStart, i + 1));
                    objectStart = -1;
                }
            }
        }

        return objects;
    }

    private String extractJsonString(String jsonObject, String key) {
        if (jsonObject == null || key == null) {
            return null;
        }

        Pattern pattern = Pattern.compile("\\\"" + Pattern.quote(key) + "\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\"])*)\\\"");
        Matcher matcher = pattern.matcher(jsonObject);
        if (!matcher.find()) {
            return null;
        }

        return unescapeJsonString(matcher.group(1));
    }

    private String unescapeJsonString(String value) {
        if (value == null) {
            return null;
        }

        return value
                .replace("\\\\", "\\")
                .replace("\\\"", "\"")
                .replace("\\/", "/")
                .replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\t", "\t");
    }

    private int compareVersions(String left, String right) {
        ParsedVersion a = ParsedVersion.parse(left);
        ParsedVersion b = ParsedVersion.parse(right);

        int max = Math.max(a.numbers().length, b.numbers().length);
        for (int i = 0; i < max; i++) {
            int av = i < a.numbers().length ? a.numbers()[i] : 0;
            int bv = i < b.numbers().length ? b.numbers()[i] : 0;

            if (av != bv) {
                return Integer.compare(av, bv);
            }
        }

        return Integer.compare(a.qualifierRank(), b.qualifierRank());
    }

    private long getIntervalTicks() {
        int hours = clamp(plugin.getConfig().getInt("updateChecker.checkIntervalHours", 12), 1, 24 * 14);
        return hours * 60L * 60L * 20L;
    }

    private boolean isEnabled() {
        return plugin.getConfig().getBoolean("updateChecker.enabled", true);
    }

    private void sendUpdateMessage(CommandSender sender, CheckResult result) {
        send(sender, "&8[&6bGraveStones&8] &eA new update is available on Modrinth: &f"
                + result.latestVersion()
                + " &7(current: &f"
                + result.currentVersion()
                + "&7) &8- &b"
                + result.projectUrl());
    }

    private void send(CommandSender sender, String message) {
        if (sender == null || message == null) {
            return;
        }
        sender.sendMessage(color(message));
    }

    private String color(String message) {
        return ChatColor.translateAlternateColorCodes('&', message == null ? "" : message);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private record VersionInfo(String versionNumber, String name, String versionType) {
    }

    private record CheckResult(String currentVersion, String latestVersion, String latestName, String projectUrl,
                               boolean updateAvailable, String errorMessage) {
        static CheckResult success(String currentVersion, String latestVersion, String latestName, String projectUrl, boolean updateAvailable) {
            return new CheckResult(currentVersion, latestVersion, latestName, projectUrl, updateAvailable, null);
        }

        static CheckResult error(String errorMessage) {
            return new CheckResult(null, null, null, null, false, errorMessage);
        }
    }

    private record ParsedVersion(int[] numbers, int qualifierRank) {
        static ParsedVersion parse(String raw) {
            if (raw == null) {
                return new ParsedVersion(new int[] {0}, 5);
            }

            String normalized = raw.trim().toLowerCase(Locale.ROOT);
            if (normalized.startsWith("v")) {
                normalized = normalized.substring(1);
            }

            String numericPart = normalized;
            String qualifier = "";

            int dash = normalized.indexOf('-');
            int plus = normalized.indexOf('+');
            int cut = -1;
            if (dash >= 0 && plus >= 0) {
                cut = Math.min(dash, plus);
            } else if (dash >= 0) {
                cut = dash;
            } else if (plus >= 0) {
                cut = plus;
            }

            if (cut >= 0) {
                numericPart = normalized.substring(0, cut);
                qualifier = normalized.substring(cut + 1);
            }

            String[] parts = numericPart.split("\\.");
            int[] numbers = new int[Math.max(1, parts.length)];

            for (int i = 0; i < numbers.length; i++) {
                numbers[i] = i < parts.length ? parseLeadingNumber(parts[i]) : 0;
            }

            return new ParsedVersion(numbers, rankQualifier(qualifier));
        }

        private static int parseLeadingNumber(String value) {
            if (value == null || value.isBlank()) {
                return 0;
            }

            StringBuilder digits = new StringBuilder();
            for (int i = 0; i < value.length(); i++) {
                char c = value.charAt(i);
                if (!Character.isDigit(c)) {
                    break;
                }
                digits.append(c);
            }

            if (digits.isEmpty()) {
                return 0;
            }

            try {
                return Integer.parseInt(digits.toString());
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }

        private static int rankQualifier(String qualifier) {
            if (qualifier == null || qualifier.isBlank()) {
                return 5;
            }

            String q = qualifier.toLowerCase(Locale.ROOT);
            if (q.startsWith("rc")) return 4;
            if (q.startsWith("beta")) return 3;
            if (q.startsWith("b")) return 3;
            if (q.startsWith("alpha")) return 2;
            if (q.startsWith("a")) return 2;
            if (q.startsWith("snapshot") || q.startsWith("dev")) return 1;
            return 0;
        }
    }
}
