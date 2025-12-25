package at.slini.crayonsmp.graves.listener;

import at.slini.crayonsmp.graves.GraveManager;
import at.slini.crayonsmp.graves.model.Grave;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;

import java.util.Optional;

public class BlockListener implements Listener {

    private final GraveManager graveManager;

    public BlockListener(GraveManager graveManager) {
        this.graveManager = graveManager;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
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
            p.sendMessage("§aGrabstein entfernt.");
            return;
        }

        // Otherwise prevent breaking to avoid dupes / grief
        e.setCancelled(true);
        p.sendMessage("§cDu kannst diesen Grabstein nicht abbauen. Öffne ihn stattdessen.");
    }
}
