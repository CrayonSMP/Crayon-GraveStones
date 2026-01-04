package at.slini.crayonsmp.graves.listener;

import at.slini.crayonsmp.graves.GraveManager;
import at.slini.crayonsmp.graves.model.Grave;

import java.util.Optional;

import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;

public class BlockListener implements Listener {
    private final GraveManager graveManager;

    public BlockListener(GraveManager graveManager) {
        this.graveManager = graveManager;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Block b = e.getBlock();
        Optional<Grave> opt = this.graveManager.getGraveAt(b);
        if (opt.isEmpty()) return;
        Player p = e.getPlayer();
        Grave grave = opt.get();
        if (this.graveManager.isAdminCanBreak() && p.hasPermission("graves.admin")) {
            e.setDropItems(false);
            this.graveManager.removeGrave(grave, p.getUniqueId());
            this.graveManager.messages().send((CommandSender) p, "gravestone.removed");
            return;
        }
        e.setCancelled(true);
        this.graveManager.messages().send((CommandSender) p, "gravestone.breakDenied");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPhysics(BlockPhysicsEvent e) {
        Block b = e.getBlock();
        if (this.graveManager.getGraveAt(b).isPresent()) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFluidFlow(BlockFromToEvent e) {
        Block to = e.getToBlock();
        if (this.graveManager.getGraveAt(to).isPresent()) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent e) {
        for (Block moved : e.getBlocks()) {
            if (this.graveManager.getGraveAt(moved).isPresent()) {
                e.setCancelled(true);
                return;
            }
        }
        Block front = e.getBlock().getRelative(e.getDirection());
        if (this.graveManager.getGraveAt(front).isPresent()) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent e) {
        for (Block moved : e.getBlocks()) {
            if (this.graveManager.getGraveAt(moved).isPresent()) {
                e.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        Block placed = e.getBlockPlaced();
        if (this.graveManager.getGraveAt(placed).isPresent()) {
            e.setCancelled(true);
            this.graveManager.messages().send((CommandSender) e.getPlayer(), "gravestone.placeDenied");
            return;
        }
    }
}
