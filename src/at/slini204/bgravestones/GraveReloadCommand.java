package at.slini204.bgravestones;

import at.slini204.bgravestones.model.Grave;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.World;
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

    private int clamp(int v, int min, int max) {
        if (v < min) return min;
        if (v > max) return max;
        return v;
    }

    private Integer tryParseInt(String s) {
        try {
            return Integer.parseInt(s);
        } catch (Exception ignored) {
            return null;
        }
    }

    private TextComponent tc(String legacy) {
        return new TextComponent(ChatColor.translateAlternateColorCodes('&', legacy));
    }

    private void sendPager(Player p, String targetName, boolean isSelf, int page, int pages) {
        String prevTxt = plugin.getMessages().format("nav.prev", Map.of());
        String nextTxt = plugin.getMessages().format("nav.next", Map.of());
        String sepTxt = plugin.getMessages().format("nav.sep", Map.of());
        String pageTxt = plugin.getMessages().format("nav.page", Map.of("page", String.valueOf(page), "pages", String.valueOf(pages)));

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

        p.spigot().sendMessage(new ComponentBuilder().append(prev).append(sep).append(mid).append(sep).append(next).create());
    }

    private boolean canTp(Player p) {
        String perm = plugin.getConfig().getString("graveTeleportPermission", "graves.admin.tp");
        return p.hasPermission(perm);
    }

    private Collection<Grave> getAllGravesSafe() {
        if (plugin == null || plugin.getGraveStorage() == null) {
            return Collections.emptyList();
        }

        Collection<Grave> graves = plugin.getGraveStorage().getAll();
        return graves == null ? Collections.emptyList() : graves;
    }

    private Map<UUID, String> getStoredOwnerNamesByUuid() {
        List<Grave> sorted = new ArrayList<>();

        for (Grave grave : getAllGravesSafe()) {
            if (grave != null && grave.getOwnerUuid() != null) {
                sorted.add(grave);
            }
        }

        sorted.sort((a, b) -> Long.compare(b.getCreatedAtEpochMs(), a.getCreatedAtEpochMs()));

        Map<UUID, String> ownersByUuid = new LinkedHashMap<>();
        for (Grave grave : sorted) {
            UUID ownerUuid = grave.getOwnerUuid();
            if (!ownersByUuid.containsKey(ownerUuid)) {
                ownersByUuid.put(ownerUuid, normalizeStoredOwnerName(grave));
            }
        }

        return ownersByUuid;
    }

    private String normalizeStoredOwnerName(Grave grave) {
        String ownerName = grave.getOwnerName();
        if (ownerName != null && !ownerName.isBlank()) {
            return ownerName;
        }
        return grave.getOwnerUuid().toString();
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
        } catch (IllegalArgumentException ignored) {}

        String lowerQuery = query.toLowerCase(Locale.ROOT);
        UUID prefixMatch = null;
        int prefixMatches = 0;

        for (Map.Entry<UUID, String> entry : owners.entrySet()) {
            String name = entry.getValue();
            if (name == null) {
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

        result.sort((a, b) -> Long.compare(b.getCreatedAtEpochMs(), a.getCreatedAtEpochMs()));
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

        if (names.size() > 50) {
            return new ArrayList<>(names.subList(0, 50));
        }

        return names;
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

    private List<String> tabGraveIds(String input) {
        String prefix = input == null ? "" : input.toLowerCase(Locale.ROOT);
        List<String> ids = new ArrayList<>();

        for (Grave grave : getAllGravesSafe()) {
            if (grave == null || grave.getId() == null) {
                continue;
            }

            String id = grave.getId().toString();
            if (id.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                ids.add(id);
            }
        }

        ids.sort(String.CASE_INSENSITIVE_ORDER);

        if (ids.size() > 50) {
            return new ArrayList<>(ids.subList(0, 50));
        }

        return ids;
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

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (args.length == 0) {
            this.plugin.getMessages().send(sender, "usage.main");
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        switch (sub) {
            case "reload": {
                if (!sender.hasPermission("graves.admin")) {
                    this.plugin.getMessages().send(sender, "errors.noPermission");
                    return true;
                }

                plugin.reloadPlugin();

                plugin.getGraveManager().restoreMissingGraveBlocks();
                plugin.getGraveManager().refreshAllHolograms();

                plugin.getMessages().send(sender, "reload.done");
                return true;
            }

            case "tp": {
                if (!(sender instanceof Player p)) {
                    plugin.getMessages().send(sender, "errors.onlyIngame");
                    return true;
                }

                if (!canTp(p)) {
                    plugin.getMessages().send(p, "errors.noPermission");
                    return true;
                }

                if (args.length != 2) {
                    plugin.getMessages().send(p, "usage.main");
                    return true;
                }

                UUID graveId;
                try {
                    graveId = UUID.fromString(args[1]);
                } catch (IllegalArgumentException ex) {
                    plugin.getMessages().send(p, "usage.main");
                    return true;
                }

                Grave g = findGraveById(graveId);

                if (g == null) {
                    plugin.getMessages().send(p, "emergency.noGraveFound");
                    return true;
                }

                World w = plugin.getServer().getWorld(g.getWorldUuid());
                if (w == null) {
                    plugin.getMessages().send(p, "emergency.noGraveFound");
                    return true;
                }

                Location loc = new Location(w, g.getX() + 0.5, g.getY() + 1, g.getZ() + 0.5);
                p.teleport(loc);
                return true;
            }

            case "list": {
                if (!(sender instanceof Player p)) {
                    this.plugin.getMessages().send(sender, "errors.onlyIngame");
                    return true;
                }

                boolean isAdmin = p.hasPermission("graves.admin");
                UUID targetUuid = p.getUniqueId();
                String targetName = p.getName();
                int page = 1;

                if (args.length == 2) {
                    Integer maybePage = tryParseInt(args[1]);
                    if (maybePage != null) {
                        page = maybePage;
                    } else {
                        if (!isAdmin) {
                            this.plugin.getMessages().send(p, "usage.main");
                            return true;
                        }

                        UUID ownerUuid = resolveStoredOwnerUuid(args[1]);
                        if (ownerUuid == null) {
                            this.plugin.getMessages().send(p, "list.noOther", Map.of("player", args[1]));
                            return true;
                        }

                        targetUuid = ownerUuid;
                        targetName = getStoredOwnerName(ownerUuid);
                    }
                } else if (args.length == 3) {
                    if (!isAdmin) {
                        this.plugin.getMessages().send(p, "usage.main");
                        return true;
                    }

                    UUID ownerUuid = resolveStoredOwnerUuid(args[1]);
                    if (ownerUuid == null) {
                        this.plugin.getMessages().send(p, "list.noOther", Map.of("player", args[1]));
                        return true;
                    }

                    targetUuid = ownerUuid;
                    targetName = getStoredOwnerName(ownerUuid);

                    Integer maybePage = tryParseInt(args[2]);
                    if (maybePage == null) {
                        this.plugin.getMessages().send(p, "usage.main");
                        return true;
                    }
                    page = maybePage;
                } else if (args.length != 1) {
                    this.plugin.getMessages().send(p, "usage.main");
                    return true;
                }

                boolean isSelf = targetUuid.equals(p.getUniqueId());
                List<Grave> all = getGravesOf(targetUuid);

                if (all.isEmpty()) {
                    if (isSelf) this.plugin.getMessages().send(p, "list.noSelf");
                    else this.plugin.getMessages().send(p, "list.noOther", Map.of("player", targetName));
                    return true;
                }

                int pageSize = plugin.getConfig().getInt("listPageSize", 10);
                if (pageSize <= 0) pageSize = 10;

                int pages = (int) Math.ceil(all.size() / (double) pageSize);
                page = clamp(page, 1, Math.max(1, pages));

                int from = (page - 1) * pageSize;
                int to = Math.min(all.size(), from + pageSize);

                if (isSelf) {
                    this.plugin.getMessages().send(p, "list.titleSelf", Map.of(
                            "count", String.valueOf(all.size()),
                            "page", String.valueOf(page),
                            "pages", String.valueOf(pages)
                    ));
                } else {
                    this.plugin.getMessages().send(p, "list.titleOther", Map.of(
                            "player", targetName,
                            "count", String.valueOf(all.size()),
                            "page", String.valueOf(page),
                            "pages", String.valueOf(pages)
                    ));
                }

                boolean tpAllowed = canTp(p);

                for (int i = from; i < to; i++) {
                    Grave g = all.get(i);

                    World w = this.plugin.getServer().getWorld(g.getWorldUuid());
                    String worldName = (w != null) ? w.getName() : "unknown";

                    String entryLegacy = this.plugin.getMessages().format("list.entry", Map.of(
                            "owner", g.getOwnerName(),
                            "x", String.valueOf(g.getX()),
                            "y", String.valueOf(g.getY()),
                            "z", String.valueOf(g.getZ()),
                            "world", worldName,
                            "player", targetName,
                            "index", String.valueOf(i + 1)
                    ));

                    TextComponent line = tc(entryLegacy);

                    if (tpAllowed) {
                        line.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/graves tp " + g.getId()));
                        line.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(tc("&aClick to teleport").toLegacyText())));
                    }

                    p.spigot().sendMessage(line);
                }

                if (pages > 1) {
                    sendPager(p, targetName, isSelf, page, pages);
                }

                return true;
            }

            case "emergency": {
                Player p;
                if (sender instanceof Player) {
                    p = (Player) sender;
                } else {
                    this.plugin.getMessages().send(sender, "errors.onlyIngame");
                    return true;
                }

                if (args.length != 2) {
                    this.plugin.getMessages().send(p, "emergency.usage");
                    return true;
                }

                if (!this.plugin.isEmergencyPlayer(p.getName())) {
                    this.plugin.getMessages().send(p, "emergency.onlyEmergencyPlayer");
                    return true;
                }

                UUID ownerUuid = resolveStoredOwnerUuid(args[1]);
                if (ownerUuid == null) {
                    this.plugin.getMessages().send(p, "emergency.noGraveFound", Map.of("player", args[1]));
                    return true;
                }

                List<Grave> graves = getGravesOf(ownerUuid);
                Grave newest = graves.isEmpty() ? null : graves.get(0);

                if (newest == null) {
                    this.plugin.getMessages().send(p, "emergency.noGraveFound", Map.of("player", getStoredOwnerName(ownerUuid)));
                    return true;
                }

                this.plugin.getGraveManager().emergencyOpenAsNonOwner(p, newest);
                this.plugin.getMessages().send(p, "emergency.done", Map.of("player", getStoredOwnerName(ownerUuid)));
                return true;
            }

            case "locatorall":
            case "waypointsall":
            case "showall": {
                if (!(sender instanceof Player p)) {
                    this.plugin.getMessages().send(sender, "errors.onlyIngame");
                    return true;
                }

                if (!p.hasPermission(plugin.getConfig().getString("locatorBar.graves.viewAllPermission", "graves.admin"))) {
                    this.plugin.getMessages().send(p, "errors.noPermission");
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
                        enabled = plugin.getLocatorBarManager().toggleViewingAllGraves(p);
                        p.sendMessage(ChatColor.translateAlternateColorCodes('&', enabled
                                ? "&8[&6bGraveStones&8] &aDu siehst jetzt alle Grab-Waypoints."
                                : "&8[&6bGraveStones&8] &7Du siehst jetzt wieder nur deine eigenen Grab-Waypoints."));
                        return true;
                    } else {
                        p.sendMessage(ChatColor.translateAlternateColorCodes('&',
                                "&8[&6bGraveStones&8] &cNutze: /graves locatorall <on|off|toggle>"));
                        return true;
                    }

                    plugin.getLocatorBarManager().setViewingAllGraves(p, enabled);
                } else {
                    enabled = plugin.getLocatorBarManager().toggleViewingAllGraves(p);
                }

                p.sendMessage(ChatColor.translateAlternateColorCodes('&', enabled
                        ? "&8[&6bGraveStones&8] &aDu siehst jetzt alle Grab-Waypoints."
                        : "&8[&6bGraveStones&8] &7Du siehst jetzt wieder nur deine eigenen Grab-Waypoints."));
                return true;
            }

            default: {
                this.plugin.getMessages().send(sender, "usage.main");
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
                options.add("locatorall");
            }

            if (sender instanceof Player p && canTp(p)) {
                options.add("tp");
            }

            if (sender instanceof Player p && this.plugin.isEmergencyPlayer(p.getName())) {
                options.add("emergency");
            }

            return tabOptions(args[0], options);
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        if (args.length == 2) {
            if ("locatorall".equals(sub) || "waypointsall".equals(sub) || "showall".equals(sub)) {
                if (sender.hasPermission(plugin.getConfig().getString("locatorBar.graves.viewAllPermission", "graves.admin"))) {
                    return tabOptions(args[1], List.of("on", "off", "toggle"));
                }
                return Collections.emptyList();
            }

            if ("list".equals(sub)) {
                if (sender instanceof Player p && p.hasPermission("graves.admin")) {
                    return tabStoredOwnerNames(args[1]);
                }
                return Collections.emptyList();
            }

            if ("emergency".equals(sub)) {
                if (sender instanceof Player p && this.plugin.isEmergencyPlayer(p.getName())) {
                    return tabStoredOwnerNames(args[1]);
                }
                return Collections.emptyList();
            }

            if ("tp".equals(sub)) {
                if (!(sender instanceof Player p) || !canTp(p)) {
                    return Collections.emptyList();
                }

                return tabGraveIds(args[1]);
            }
        }

        if (args.length == 3 && "list".equals(sub)) {
            if (sender instanceof Player p && p.hasPermission("graves.admin")) {
                return tabOptions(args[2], List.of("1", "2", "3", "4", "5"));
            }
        }

        return Collections.emptyList();
    }
}
