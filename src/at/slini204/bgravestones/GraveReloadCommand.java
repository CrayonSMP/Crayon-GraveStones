package at.slini204.bgravestones;

import at.slini204.bgravestones.model.Grave;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;

public class GraveReloadCommand implements CommandExecutor, TabCompleter {
    private final GravePlugin plugin;

    public GraveReloadCommand(GravePlugin plugin) {
        this.plugin = plugin;
    }

    private int clamp(int value, int min, int max) {
        if (value < min) return min;
        if (value > max) return max;
        return value;
    }

    private Integer tryParseInt(String value) {
        try {
            return Integer.parseInt(value);
        } catch (Exception ignored) {
            return null;
        }
    }

    private TextComponent tc(String legacy) {
        return new TextComponent(ChatColor.translateAlternateColorCodes('&', legacy));
    }

    private Collection<Grave> getAllGravesSafe() {
        if (plugin == null || plugin.getGraveStorage() == null) {
            return Collections.emptyList();
        }

        Collection<Grave> graves = plugin.getGraveStorage().getAll();
        return graves == null ? Collections.emptyList() : graves;
    }

    private DateTimeFormatter getListDateFormatter() {
        String pattern = plugin.getConfig().getString("listDateFormat", "dd.MM.yyyy HH:mm");
        if (pattern == null || pattern.isBlank()) {
            pattern = "dd.MM.yyyy HH:mm";
        }

        try {
            return DateTimeFormatter.ofPattern(pattern).withZone(ZoneId.systemDefault());
        } catch (IllegalArgumentException ex) {
            plugin.getLogger().warning("[bGraveStones] Invalid listDateFormat '" + pattern + "', falling back to dd.MM.yyyy HH:mm");
            return DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(ZoneId.systemDefault());
        }
    }

    private String formatGraveDate(Grave grave) {
        if (grave == null || grave.getCreatedAtEpochMs() <= 0L) {
            return "unknown";
        }

        try {
            return getListDateFormatter().format(Instant.ofEpochMilli(grave.getCreatedAtEpochMs()));
        } catch (DateTimeParseException | IllegalArgumentException ex) {
            return "unknown";
        }
    }

    private boolean canTp(Player player) {
        String perm = plugin.getConfig().getString("graveTeleportPermission", "graves.admin.tp");
        return player != null && player.hasPermission(perm);
    }

    private void sendLocatorAllState(Player player, boolean enabled) {
        if (player == null) {
            return;
        }

        player.sendMessage(ChatColor.translateAlternateColorCodes('&', enabled
                ? "&8[&6bGraveStones&8] &aYou can now see all grave waypoints."
                : "&8[&6bGraveStones&8] &7You can now only see your own grave waypoints."));
    }

    private void sendPager(Player player, String targetName, boolean isSelf, int page, int pages) {
        String prevTxt = plugin.getMessages().format("nav.prev", Map.of());
        String nextTxt = plugin.getMessages().format("nav.next", Map.of());
        String sepTxt = plugin.getMessages().format("nav.sep", Map.of());
        String pageTxt = plugin.getMessages().format("nav.page", Map.of(
                "page", String.valueOf(page),
                "pages", String.valueOf(pages)
        ));

        String base = isSelf ? "/graves list " : ("/graves list " + targetName + " ");

        TextComponent prev = tc(prevTxt);
        TextComponent next = tc(nextTxt);
        TextComponent sep = tc(sepTxt);
        TextComponent mid = tc(pageTxt);

        if (page > 1) {
            prev.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, base + (page - 1)));
        } else {
            prev.setColor(ChatColor.DARK_GRAY);
        }

        if (page < pages) {
            next.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, base + (page + 1)));
        } else {
            next.setColor(ChatColor.DARK_GRAY);
        }

        player.spigot().sendMessage(new ComponentBuilder().append(prev).append(sep).append(mid).append(sep).append(next).create());
    }

    private Map<UUID, String> getStoredOwnerNamesByUuid() {
        List<Grave> sorted = new ArrayList<>();

        for (Grave grave : getAllGravesSafe()) {
            if (grave != null && grave.getOwnerUuid() != null) {
                sorted.add(grave);
            }
        }

        sorted.sort((a, b) -> Long.compare(b.getCreatedAtEpochMs(), a.getCreatedAtEpochMs()));

        Map<UUID, String> owners = new LinkedHashMap<>();
        for (Grave grave : sorted) {
            UUID ownerUuid = grave.getOwnerUuid();
            if (!owners.containsKey(ownerUuid)) {
                owners.put(ownerUuid, normalizeOwnerName(grave));
            }
        }

        return owners;
    }

    private String normalizeOwnerName(Grave grave) {
        if (grave == null) {
            return "unknown";
        }

        String ownerName = grave.getOwnerName();
        if (ownerName != null && !ownerName.isBlank()) {
            return ownerName;
        }

        return grave.getOwnerUuid() == null ? "unknown" : grave.getOwnerUuid().toString();
    }

    private UUID resolveStoredOwnerUuid(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }

        String query = input.trim();
        Map<UUID, String> owners = getStoredOwnerNamesByUuid();

        try {
            UUID uuid = UUID.fromString(query);
            return owners.containsKey(uuid) ? uuid : null;
        } catch (IllegalArgumentException ignored) {
        }

        String lowerQuery = query.toLowerCase(Locale.ROOT);
        UUID prefixMatch = null;
        int prefixMatches = 0;

        for (Map.Entry<UUID, String> entry : owners.entrySet()) {
            String name = entry.getValue();
            if (name == null || name.isBlank()) {
                continue;
            }

            if (name.equalsIgnoreCase(query)) {
                return entry.getKey();
            }

            if (name.toLowerCase(Locale.ROOT).startsWith(lowerQuery)) {
                prefixMatch = entry.getKey();
                prefixMatches++;
            }
        }

        return prefixMatches == 1 ? prefixMatch : null;
    }

    private String getStoredOwnerName(UUID ownerUuid) {
        if (ownerUuid == null) {
            return "unknown";
        }

        String name = getStoredOwnerNamesByUuid().get(ownerUuid);
        return name == null || name.isBlank() ? ownerUuid.toString() : name;
    }

    private List<Grave> getGravesOf(UUID ownerUuid) {
        if (ownerUuid == null) {
            return Collections.emptyList();
        }

        List<Grave> result = new ArrayList<>();
        for (Grave grave : getAllGravesSafe()) {
            if (grave != null && ownerUuid.equals(grave.getOwnerUuid())) {
                result.add(grave);
            }
        }

        result.sort(Comparator.comparingLong(Grave::getCreatedAtEpochMs).reversed());
        return result;
    }

    private int getListPageSize() {
        int pageSize = plugin.getConfig().getInt("listPageSize", 10);
        return pageSize <= 0 ? 10 : pageSize;
    }

    private int getPageCountFor(UUID ownerUuid) {
        if (ownerUuid == null) {
            return 0;
        }

        List<Grave> graves = getGravesOf(ownerUuid);
        if (graves.isEmpty()) {
            return 0;
        }

        return (int) Math.ceil(graves.size() / (double) getListPageSize());
    }

    private List<String> tabListPages(UUID ownerUuid, String input) {
        int pages = getPageCountFor(ownerUuid);

        /*
         * Do not suggest useless "1" when there is only one page.
         * Page tab-completion should only appear when it actually helps.
         */
        if (pages <= 1) {
            return Collections.emptyList();
        }

        String prefix = input == null ? "" : input.toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();

        for (int page = 1; page <= pages; page++) {
            String value = String.valueOf(page);
            if (value.startsWith(prefix)) {
                result.add(value);
            }
        }

        return result.size() > 50 ? new ArrayList<>(result.subList(0, 50)) : result;
    }

    private Grave findGraveById(UUID graveId) {
        if (graveId == null) {
            return null;
        }

        for (Grave grave : getAllGravesSafe()) {
            if (grave != null && graveId.equals(grave.getId())) {
                return grave;
            }
        }

        return null;
    }

    private Grave resolveTeleportTarget(Player player, String ownerInput, String numberInput) {
        if (player == null || ownerInput == null || ownerInput.isBlank() || numberInput == null || numberInput.isBlank()) {
            return null;
        }

        Integer graveNumber = tryParseInt(numberInput);
        if (graveNumber == null || graveNumber < 1) {
            return null;
        }

        UUID ownerUuid = resolveStoredOwnerUuid(ownerInput);
        if (ownerUuid == null) {
            return null;
        }

        if (!ownerUuid.equals(player.getUniqueId()) && !player.hasPermission("graves.admin")) {
            return null;
        }

        List<Grave> graves = getGravesOf(ownerUuid);
        if (graveNumber > graves.size()) {
            return null;
        }

        return graves.get(graveNumber - 1);
    }



    private List<String> tabOptions(String input, Collection<String> options) {
        String prefix = input == null ? "" : input.toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();

        for (String option : options) {
            if (option != null && option.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                result.add(option);
            }
        }

        result.sort(String.CASE_INSENSITIVE_ORDER);
        return result;
    }

    private List<String> tabStoredOwnerNames(String input) {
        String prefix = input == null ? "" : input.toLowerCase(Locale.ROOT);
        List<String> names = new ArrayList<>();

        for (String name : getStoredOwnerNamesByUuid().values()) {
            if (name == null || name.isBlank()) {
                continue;
            }

            boolean duplicate = false;
            for (String existing : names) {
                if (existing.equalsIgnoreCase(name)) {
                    duplicate = true;
                    break;
                }
            }

            if (!duplicate && name.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                names.add(name);
            }
        }

        names.sort(String.CASE_INSENSITIVE_ORDER);
        return names.size() > 50 ? new ArrayList<>(names.subList(0, 50)) : names;
    }

    private List<String> tabTeleportIndexes(Player player, String ownerInput, String input) {
        if (player == null || ownerInput == null || ownerInput.isBlank()) {
            return Collections.emptyList();
        }

        UUID ownerUuid = resolveStoredOwnerUuid(ownerInput);
        if (ownerUuid == null) {
            return Collections.emptyList();
        }

        if (!ownerUuid.equals(player.getUniqueId()) && !player.hasPermission("graves.admin")) {
            return Collections.emptyList();
        }

        String prefix = input == null ? "" : input.toLowerCase(Locale.ROOT);
        List<Grave> graves = getGravesOf(ownerUuid);
        List<String> result = new ArrayList<>();

        for (int i = 0; i < graves.size(); i++) {
            String value = String.valueOf(i + 1);
            if (value.startsWith(prefix)) {
                result.add(value);
            }
        }

        return result.size() > 50 ? new ArrayList<>(result.subList(0, 50)) : result;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            plugin.getMessages().send(sender, "usage.main");
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        switch (sub) {
            case "reload": {
                if (!sender.hasPermission("graves.admin")) {
                    plugin.getMessages().send(sender, "errors.noPermission");
                    return true;
                }

                plugin.reloadPlugin();
                plugin.getGraveManager().restoreMissingGraveBlocks();
                plugin.getGraveManager().refreshAllHolograms();
                plugin.getMessages().send(sender, "reload.done");
                return true;
            }

            case "updatecheck": {
                if (!sender.hasPermission("graves.admin")) {
                    plugin.getMessages().send(sender, "errors.noPermission");
                    return true;
                }

                if (plugin.getUpdateChecker() == null) {
                    sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                            "&8[&6bGraveStones&8] &cUpdate checker is not available right now."));
                    return true;
                }

                plugin.getUpdateChecker().checkNow(sender);
                return true;
            }

            case "tp": {
                if (!(sender instanceof Player player)) {
                    plugin.getMessages().send(sender, "errors.onlyIngame");
                    return true;
                }

                if (!canTp(player)) {
                    plugin.getMessages().send(player, "errors.noPermission");
                    return true;
                }

                if (args.length != 3) {
                    plugin.getMessages().send(player, "tp.usage");
                    return true;
                }

                Grave grave = resolveTeleportTarget(player, args[1], args[2]);
                if (grave == null) {
                    plugin.getMessages().send(player, "tp.notFound");
                    return true;
                }

                World world = plugin.getServer().getWorld(grave.getWorldUuid());
                if (world == null) {
                    plugin.getMessages().send(player, "tp.notFound");
                    return true;
                }

                Location loc = new Location(world, grave.getX() + 0.5D, grave.getY() + 1.0D, grave.getZ() + 0.5D);
                player.teleport(loc);
                plugin.getMessages().send(player, "tp.done", Map.of(
                        "index", args[2],
                        "player", normalizeOwnerName(grave),
                        "date", formatGraveDate(grave)
                ));
                return true;
            }

            case "list": {
                if (!(sender instanceof Player player)) {
                    plugin.getMessages().send(sender, "errors.onlyIngame");
                    return true;
                }

                boolean isAdmin = player.hasPermission("graves.admin");
                UUID targetUuid = player.getUniqueId();
                String targetName = player.getName();
                int page = 1;

                if (args.length == 2) {
                    Integer maybePage = tryParseInt(args[1]);
                    if (maybePage != null) {
                        page = maybePage;
                    } else {
                        if (!isAdmin) {
                            plugin.getMessages().send(player, "usage.main");
                            return true;
                        }

                        UUID ownerUuid = resolveStoredOwnerUuid(args[1]);
                        if (ownerUuid == null) {
                            plugin.getMessages().send(player, "list.noOther", Map.of("player", args[1]));
                            return true;
                        }

                        targetUuid = ownerUuid;
                        targetName = getStoredOwnerName(ownerUuid);
                    }
                } else if (args.length == 3) {
                    if (!isAdmin) {
                        plugin.getMessages().send(player, "usage.main");
                        return true;
                    }

                    UUID ownerUuid = resolveStoredOwnerUuid(args[1]);
                    if (ownerUuid == null) {
                        plugin.getMessages().send(player, "list.noOther", Map.of("player", args[1]));
                        return true;
                    }

                    targetUuid = ownerUuid;
                    targetName = getStoredOwnerName(ownerUuid);

                    Integer maybePage = tryParseInt(args[2]);
                    if (maybePage == null) {
                        plugin.getMessages().send(player, "usage.main");
                        return true;
                    }
                    page = maybePage;
                } else if (args.length != 1) {
                    plugin.getMessages().send(player, "usage.main");
                    return true;
                }

                boolean isSelf = targetUuid.equals(player.getUniqueId());
                List<Grave> all = getGravesOf(targetUuid);

                if (all.isEmpty()) {
                    if (isSelf) {
                        plugin.getMessages().send(player, "list.noSelf");
                    } else {
                        plugin.getMessages().send(player, "list.noOther", Map.of("player", targetName));
                    }
                    return true;
                }

                int pageSize = getListPageSize();

                int pages = (int) Math.ceil(all.size() / (double) pageSize);
                page = clamp(page, 1, Math.max(1, pages));

                int from = (page - 1) * pageSize;
                int to = Math.min(all.size(), from + pageSize);

                if (isSelf) {
                    plugin.getMessages().send(player, "list.titleSelf", Map.of(
                            "count", String.valueOf(all.size()),
                            "page", String.valueOf(page),
                            "pages", String.valueOf(pages)
                    ));
                } else {
                    plugin.getMessages().send(player, "list.titleOther", Map.of(
                            "player", targetName,
                            "count", String.valueOf(all.size()),
                            "page", String.valueOf(page),
                            "pages", String.valueOf(pages)
                    ));
                }

                boolean tpAllowed = canTp(player);

                for (int i = from; i < to; i++) {
                    Grave grave = all.get(i);
                    int displayIndex = i + 1;

                    World world = plugin.getServer().getWorld(grave.getWorldUuid());
                    String worldName = world != null ? world.getName() : "unknown";
                    String date = formatGraveDate(grave);

                    String entryLegacy = plugin.getMessages().format("list.entry", Map.of(
                            "owner", normalizeOwnerName(grave),
                            "x", String.valueOf(grave.getX()),
                            "y", String.valueOf(grave.getY()),
                            "z", String.valueOf(grave.getZ()),
                            "world", worldName,
                            "player", targetName,
                            "index", String.valueOf(displayIndex),
                            "date", date,
                            "time", date
                    ));

                    TextComponent line = tc(entryLegacy);

                    if (tpAllowed) {
                        line.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/graves tp " + targetName + " " + displayIndex));
                        String hover = plugin.getMessages().format("list.hoverTeleport", Map.of(
                                "index", String.valueOf(displayIndex),
                                "player", normalizeOwnerName(grave),
                                "date", date,
                                "x", String.valueOf(grave.getX()),
                                "y", String.valueOf(grave.getY()),
                                "z", String.valueOf(grave.getZ()),
                                "world", worldName
                        ));
                        if (hover == null || hover.isBlank()) {
                            hover = "&aClick to teleport to grave #" + displayIndex;
                        }
                        line.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(tc(hover).toLegacyText())));
                    }

                    player.spigot().sendMessage(line);
                }

                if (pages > 1) {
                    sendPager(player, targetName, isSelf, page, pages);
                }

                return true;
            }

            case "emergency": {
                if (!(sender instanceof Player player)) {
                    plugin.getMessages().send(sender, "errors.onlyIngame");
                    return true;
                }

                if (!plugin.isEmergencyPlayer(player.getName())) {
                    plugin.getMessages().send(player, "emergency.onlyEmergencyPlayer");
                    return true;
                }

                int lookDistance = clamp(plugin.getConfig().getInt("emergencyLookDistance", 6), 1, 32);
                Block targetBlock = player.getTargetBlockExact(lookDistance);

                if (targetBlock == null) {
                    plugin.getMessages().send(player, "emergency.noTargetGrave", Map.of("distance", String.valueOf(lookDistance)));
                    return true;
                }

                Grave targetGrave = plugin.getGraveManager().getGraveAt(targetBlock).orElse(null);
                if (targetGrave == null) {
                    plugin.getMessages().send(player, "emergency.noTargetGrave", Map.of("distance", String.valueOf(lookDistance)));
                    return true;
                }

                plugin.getGraveManager().emergencyOpenAsNonOwner(player, targetGrave);
                plugin.getMessages().send(player, "emergency.done", Map.of(
                        "player", normalizeOwnerName(targetGrave),
                        "date", formatGraveDate(targetGrave)
                ));
                return true;
            }


            case "locatorall":
            case "waypointsall":
            case "showall": {
                if (!(sender instanceof Player player)) {
                    plugin.getMessages().send(sender, "errors.onlyIngame");
                    return true;
                }

                String permission = plugin.getConfig().getString("locatorBar.graves.viewAllPermission", "graves.admin");
                if (!player.hasPermission(permission)) {
                    plugin.getMessages().send(player, "errors.noPermission");
                    return true;
                }

                if (plugin.getLocatorBarManager() == null) {
                    player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                            "&8[&6bGraveStones&8] &cLocator-Bar support is not available right now."));
                    return true;
                }

                boolean enabled;

                if (args.length >= 2) {
                    String value = args[1].toLowerCase(Locale.ROOT);

                    if (value.equals("on") || value.equals("true") || value.equals("1") || value.equals("an")) {
                        enabled = true;
                    } else if (value.equals("off") || value.equals("false") || value.equals("0") || value.equals("aus")) {
                        enabled = false;
                    } else if (value.equals("toggle")) {
                        enabled = plugin.getLocatorBarManager().toggleViewingAllGraves(player);
                        sendLocatorAllState(player, enabled);
                        return true;
                    } else {
                        player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                                "&8[&6bGraveStones&8] &cUse: /graves locatorall <on|off|toggle>"));
                        return true;
                    }

                    plugin.getLocatorBarManager().setViewingAllGraves(player, enabled);
                } else {
                    enabled = plugin.getLocatorBarManager().toggleViewingAllGraves(player);
                }

                sendLocatorAllState(player, enabled);
                return true;
            }

            default: {
                plugin.getMessages().send(sender, "usage.main");
                return true;
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> options = new ArrayList<>();
            options.add("list");

            if (sender.hasPermission("graves.admin")) {
                options.add("reload");
                options.add("updatecheck");
            }

            String locatorAllPermission = plugin.getConfig().getString("locatorBar.graves.viewAllPermission", "graves.admin");
            if (sender.hasPermission(locatorAllPermission)) {
                options.add("locatorall");
            }

            if (sender instanceof Player player) {
                if (canTp(player)) {
                    options.add("tp");
                }
                if (plugin.isEmergencyPlayer(player.getName())) {
                    options.add("emergency");
                }
            }

            return tabOptions(args[0], options);
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        if (args.length == 2) {
            if ("locatorall".equals(sub) || "waypointsall".equals(sub) || "showall".equals(sub)) {
                String permission = plugin.getConfig().getString("locatorBar.graves.viewAllPermission", "graves.admin");
                if (sender.hasPermission(permission)) {
                    return tabOptions(args[1], List.of("on", "off", "toggle"));
                }
                return Collections.emptyList();
            }

            if ("list".equals(sub)) {
                if (!(sender instanceof Player player)) {
                    return Collections.emptyList();
                }

                /*
                 * Normal players: /graves list <page>
                 * Only suggest page numbers if their own grave list has more than one page.
                 */
                if (!player.hasPermission("graves.admin")) {
                    return tabListPages(player.getUniqueId(), args[1]);
                }

                /*
                 * Admins: /graves list <PlayerName> <page>
                 * At this position only player names are suggested.
                 */
                return tabStoredOwnerNames(args[1]);
            }

            if ("tp".equals(sub)) {
                if (sender instanceof Player player && canTp(player)) {
                    if (player.hasPermission("graves.admin")) {
                        return tabStoredOwnerNames(args[1]);
                    }
                    return tabOptions(args[1], List.of(player.getName()));
                }
                return Collections.emptyList();
            }
        }

        if (args.length == 3 && "tp".equals(sub)) {
            if (sender instanceof Player player && canTp(player)) {
                return tabTeleportIndexes(player, args[1], args[2]);
            }
            return Collections.emptyList();
        }

        if (args.length == 3 && "list".equals(sub)) {
            if (!(sender instanceof Player player) || !player.hasPermission("graves.admin")) {
                return Collections.emptyList();
            }

            UUID ownerUuid = resolveStoredOwnerUuid(args[1]);
            if (ownerUuid == null) {
                return Collections.emptyList();
            }

            /*
             * Admins: /graves list <PlayerName> <page>
             * Only suggest pages if the selected player actually has more than one page.
             */
            return tabListPages(ownerUuid, args[2]);
        }

        return Collections.emptyList();
    }
}
