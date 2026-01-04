package at.slini.crayonsmp.graves;

import at.slini.crayonsmp.graves.model.Grave;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;

public class GraveReloadCommand implements CommandExecutor {
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

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (args.length == 0) {
            this.plugin.getMessages().send(sender, "usage.main");
            return true;
        }

        String sub = args[0].toLowerCase(java.util.Locale.ROOT);

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

                Grave g = plugin.getGraveStorage().getAll().stream().filter(x -> x.getId().equals(graveId)).findFirst().orElse(null);

                if (g == null) {
                    // TODO make message key (tp.notFound)
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
                        Player t = this.plugin.getServer().getPlayerExact(args[1]);
                        if (t == null) {
                            this.plugin.getMessages().send(p, "errors.playerMustBeOnline", Map.of("player", args[1]));
                            return true;
                        }
                        targetUuid = t.getUniqueId();
                        targetName = t.getName();
                    }
                } else if (args.length == 3) {
                    if (!isAdmin) {
                        this.plugin.getMessages().send(p, "usage.main");
                        return true;
                    }
                    Player t = this.plugin.getServer().getPlayerExact(args[1]);
                    if (t == null) {
                        this.plugin.getMessages().send(p, "errors.playerMustBeOnline", Map.of("player", args[1]));
                        return true;
                    }
                    targetUuid = t.getUniqueId();
                    targetName = t.getName();

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

                UUID finalTargetUuid = targetUuid;
                List<Grave> all = this.plugin.getGraveStorage().getAll().stream().filter(g -> g.getOwnerUuid().equals(finalTargetUuid)).sorted(Comparator.comparingLong(Grave::getCreatedAtEpochMs).reversed()).collect(Collectors.toList());

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
                    this.plugin.getMessages().send(p, "list.titleSelf", Map.of("count", String.valueOf(all.size()), "page", String.valueOf(page), "pages", String.valueOf(pages)));
                } else {
                    this.plugin.getMessages().send(p, "list.titleOther", Map.of("player", targetName, "count", String.valueOf(all.size()), "page", String.valueOf(page), "pages", String.valueOf(pages)));
                }

                boolean tpAllowed = canTp(p);

                for (int i = from; i < to; i++) {
                    Grave g = all.get(i);

                    World w = this.plugin.getServer().getWorld(g.getWorldUuid());
                    String worldName = (w != null) ? w.getName() : "unknown";

                    String entryLegacy = this.plugin.getMessages().format("list.entry", Map.of("owner", g.getOwnerName(), "x", String.valueOf(g.getX()), "y", String.valueOf(g.getY()), "z", String.valueOf(g.getZ()), "world", worldName, "player", targetName, "index", String.valueOf(i + 1)));

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

                String targetName = args[1];
                Player target = this.plugin.getServer().getPlayerExact(targetName);
                if (target == null) {
                    this.plugin.getMessages().send(p, "errors.playerMustBeOnline", Map.of("player", targetName));
                    return true;
                }

                UUID targetUuid = target.getUniqueId();
                Grave newest = this.plugin.getGraveStorage().getAll().stream().filter(g -> g.getOwnerUuid().equals(targetUuid)).max(Comparator.comparingLong(Grave::getCreatedAtEpochMs)).orElse(null);

                if (newest == null) {
                    this.plugin.getMessages().send(p, "emergency.noGraveFound", Map.of("player", target.getName()));
                    return true;
                }

                this.plugin.getGraveManager().emergencyOpenAsNonOwner(p, newest);
                this.plugin.getMessages().send(p, "emergency.done", Map.of("player", target.getName()));
                return true;
            }

            default: {
                this.plugin.getMessages().send(sender, "usage.main");
                return true;
            }
        }
    }
}
