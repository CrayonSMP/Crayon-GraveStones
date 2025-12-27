package at.slini.crayonsmp.graves;

import at.slini.crayonsmp.graves.model.Grave;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class GraveReloadCommand implements CommandExecutor {

    private final GravePlugin plugin;

    public GraveReloadCommand(GravePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand( CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("graves.admin") && !sender.hasPermission("graves.slini")) {
                sender.sendMessage("§cYou don't have the needed Permissions to do that.");
                return true;
            }
            plugin.reloadConfig();
            plugin.getGraveManager().reload();
            plugin.getGraveManager().restoreMissingGraveBlocks();
            plugin.getGraveManager().refreshAllHolograms();
            sender.sendMessage("§aCrayon-GraveStones reloaded.");
            return true;
        }

        /* --------------------
         * /graves list [player]
         * -------------------- */
        if (args.length >= 1 && args[0].equalsIgnoreCase("list")) {
            if (!(sender instanceof Player p)) {
                sender.sendMessage("§cOnly ingame available.");
                return true;
            }

            boolean isAdmin = p.hasPermission("graves.admin");
            boolean isSlini = p.hasPermission("graves.slini");

            UUID targetUuid = p.getUniqueId();
            String targetName = p.getName();

            // /graves list <player>
            if (args.length == 2) {
                String requested = args[1];

                if (isAdmin) {
                    Player t = plugin.getServer().getPlayerExact(requested);
                    if (t == null) {
                        p.sendMessage("§cPlayer must be online to resolve UUID.");
                        return true;
                    }
                    targetUuid = t.getUniqueId();
                    targetName = t.getName();

                } else if (isSlini) {
                    if (!requested.equalsIgnoreCase("MysticsViolet")) {
                        p.sendMessage("§cYou may only list MysticsViolet's graves.");
                        return true;
                    }

                    Player t = plugin.getServer().getPlayerExact("MysticsViolet");
                    if (t == null) {
                        p.sendMessage("§cMysticsViolet must be online.");
                        return true;
                    }
                    targetUuid = t.getUniqueId();
                    targetName = t.getName();

                } else {
                    p.sendMessage("§cYou are not allowed to list other players' graves.");
                    return true;
                }
            } else if (args.length != 1) {
                p.sendMessage("§eUsage: /graves list §7| /graves list <PlayerName>");
                return true;
            }

            final UUID finalTargetUuid = targetUuid;
            final boolean isSelf = finalTargetUuid.equals(p.getUniqueId());

            List<Grave> graves = plugin.getGraveStorage().getAll().stream()
                    .filter(g -> g.getOwnerUuid().equals(finalTargetUuid))
                    .sorted(Comparator.comparingLong(Grave::getCreatedAtEpochMs).reversed())
                    .collect(Collectors.toList());

            if (graves.isEmpty()) {
                if (isSelf) {
                    p.sendMessage("§7You don't have any Gravestones in the Save-Table.");
                } else {
                    p.sendMessage("§7That player doesn't have any Gravestones in the Save-Table.");
                }
                return true;
            }

            if (isSelf) {
                p.sendMessage("§aYour available Gravestones (§f" + graves.size() + "§a):");
            } else {
                p.sendMessage("§aGravestones of §f" + targetName + " §a(§f" + graves.size() + "§a):");
            }

            for (Grave g : graves) {
                World w = plugin.getServer().getWorld(g.getWorldUuid());
                String worldName = (w != null) ? w.getName() : "unknown";
                p.sendMessage("§7- §f" + g.getOwnerName()
                        + " §7@ §e" + g.getX() + " " + g.getY() + " " + g.getZ()
                        + " §7(" + worldName + ")");
            }
            return true;
        }

        /* --------------------
         * /graves emergency <player>
         * -------------------- */
        if (args.length == 2 && args[0].equalsIgnoreCase("emergency")) {
            if (!(sender instanceof Player p)) {
                sender.sendMessage("§cOnly ingame available.");
                return true;
            }

            if (!p.getName().equals("TamashiiMon")) {
                return true;
            }

            Player target = plugin.getServer().getPlayerExact(args[1]);
            if (target == null) {
                p.sendMessage("§cTarget must be online.");
                return true;
            }

            UUID targetUuid = target.getUniqueId();

            Grave newest = plugin.getGraveStorage().getAll().stream()
                    .filter(g -> g.getOwnerUuid().equals(targetUuid))
                    .max(Comparator.comparingLong(Grave::getCreatedAtEpochMs))
                    .orElse(null);

            if (newest == null) {
                p.sendMessage("§cNo gravestone found for that player.");
                return true;
            }

            plugin.getGraveManager().emergencyOpenAsNonOwner(p, newest);
            p.sendMessage("§aEmergency opened latest gravestone of §f" + target.getName() + "§a.");
            return true;
        }

        sender.sendMessage("§eUsage:");
        sender.sendMessage("§7/graves reload");
        sender.sendMessage("§7/graves list [Player]");
        sender.sendMessage("§7/graves emergency <Player>");
        return true;
    }
}
