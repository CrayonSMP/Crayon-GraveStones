package at.slini.crayonsmp.graves.listener;

import at.slini.crayonsmp.graves.GraveManager;
import at.slini.crayonsmp.graves.model.Grave;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;

import java.util.Iterator;
import java.util.Optional;

public class ExplosionListener implements Listener {

    private final GraveManager graveManager;

    public ExplosionListener(GraveManager graveManager) {
        this.graveManager = graveManager;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent e) {
        filterGraves(e.blockList().iterator());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent e) {
        filterGraves(e.blockList().iterator());
    }

    private void filterGraves(Iterator<Block> it) {
        while (it.hasNext()) {
            Block b = it.next();
            Optional<Grave> opt = graveManager.getGraveAt(b);
            if (opt.isPresent()) {
                it.remove();
            }
        }
    }
}
