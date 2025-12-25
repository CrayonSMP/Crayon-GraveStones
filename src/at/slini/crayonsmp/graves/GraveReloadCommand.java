package at.slini.crayonsmp.graves;

import at.slini.crayonsmp.graves.model.Grave;
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
            if (!sender.hasPermission("graves.admin")) {
                sender.sendMessage("§cYou don't have the needed Permissions to do that. You believe this is an error? Look at the permissions-table.");
                return true;
            }
            plugin.reloadConfig();
            plugin.getGraveManager().reload();
            plugin.getGraveManager().refreshAllHolograms();
            sender.sendMessage("§aCrayon-GraveStones reloaded.");
            return true;
        }

        if (args.length == 1 && args[0].equalsIgnoreCase("list")) {
            if (!(sender instanceof Player p)) {
                sender.sendMessage("§conly ingame available.");
                return true;
            }

            UUID uuid = p.getUniqueId();
            List<Grave> graves = plugin.getGraveStorage().getAll().stream()
                    .filter(g -> g.getOwnerUuid().equals(uuid))
                    .sorted(Comparator.comparingLong(Grave::getCreatedAtEpochMs).reversed())
                    .collect(Collectors.toList());

            if (graves.isEmpty()) {
                p.sendMessage("§7You don't have any Gravestones in the Save-Table.");
                return true;
            }

            p.sendMessage("§aYour available Gravestones (§f" + graves.size() + "§a):");
            for (Grave g : graves) {
                p.sendMessage("§7- §f" + g.getOwnerName() + " §7@ §e" + g.getX() + " " + g.getY() + " " + g.getZ()
                        + " §7(" + plugin.getServer().getWorld(g.getWorldUuid()).getName() + ")");
            }
            return true;
        }

        sender.sendMessage("§eUsage: /graves reload §7(Admin) §e| /graves list");
        return true;
    }
}
