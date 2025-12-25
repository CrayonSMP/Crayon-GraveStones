package at.slini.crayonsmp.graves.listener;

import at.slini.crayonsmp.graves.GraveManager;
import at.slini.crayonsmp.graves.model.Grave;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockPlaceEvent;

import java.util.Optional;

public class BlockListener implements Listener {

    private final GraveManager graveManager;

    public BlockListener(GraveManager graveManager) {
        this.graveManager = graveManager;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Block b = e.getBlock();
        Optional<Grave> opt = graveManager.getGraveAt(b);
        if (opt.isEmpty()) return;

        Player p = e.getPlayer();
        Grave grave = opt.get();

        // Allow admin break if configured
        if (graveManager.isAdminCanBreak() && p.hasPermission("graves.admin")) {
            e.setDropItems(false);
            graveManager.removeGrave(grave, p.getUniqueId());
            p.sendMessage("§aGravestone removed.");
            return;
        }

        // Otherwise prevent breaking to avoid dupes / grief
        e.setCancelled(true);
        p.sendMessage("§cYou can't break this Gravestone, please open the Gravestone instead. §4Only Admins are permitted to break Gravestones in Emergency cases.");
    }

    // verhindert, dass Walls durch Nachbar-Updates / Physics kaputtgehen
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPhysics(BlockPhysicsEvent e) {
        Block b = e.getBlock();
        if (graveManager.getGraveAt(b).isPresent()) {
            e.setCancelled(true);
        }
    }

    // verhindert Flüssigkeit, die in den Grab-Block fließt und ihn ersetzt
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFluidFlow(BlockFromToEvent e) {
        Block to = e.getToBlock();
        if (graveManager.getGraveAt(to).isPresent()) {
            e.setCancelled(true);
        }
    }

    // verhindert, dass Pistons Grave-Blocks verschieben / zerstören
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent e) {
        // Blocks, die bewegt werden würden
        for (Block moved : e.getBlocks()) {
            if (graveManager.getGraveAt(moved).isPresent()) {
                e.setCancelled(true);
                return;
            }
        }
        // Block direkt vor dem Piston (kann ebenfalls betroffen sein)
        Block front = e.getBlock().getRelative(e.getDirection());
        if (graveManager.getGraveAt(front).isPresent()) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent e) {
        for (Block moved : e.getBlocks()) {
            if (graveManager.getGraveAt(moved).isPresent()) {
                e.setCancelled(true);
                return;
            }
        }
    }

    // verhindert, dass jemand "in" den Grab-Block reinsetzt (edge-case, aber sicher ist sicher)
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        Block placed = e.getBlockPlaced();

        // Wenn der Platz, in den gesetzt wird, ein Grave ist -> cancel
        if (graveManager.getGraveAt(placed).isPresent()) {
            e.setCancelled(true);
            e.getPlayer().sendMessage("§cYou can't place blocks here because a Gravestone is occupying this block.");
            return;
        }

        // Wenn der Block gegen den Grave gesetzt wird, könnte er Physics triggern – das verhindern wir über onPhysics schon.
        // Optional: Wenn du auch "direkt an Grave platzieren" verbieten willst, sag Bescheid.
    }
}
