package at.slini.crayonsmp.graves.listener;

import at.slini.crayonsmp.graves.GraveManager;
import at.slini.crayonsmp.graves.model.Grave;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;

import java.util.Optional;

import static org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK;

public class InteractListener implements Listener {

    private final GraveManager graveManager;

    public InteractListener(GraveManager graveManager) {
        this.graveManager = graveManager;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getAction() != RIGHT_CLICK_BLOCK) return;

        Block b = e.getClickedBlock();
        if (b == null) return;

        Optional<Grave> opt = graveManager.getGraveAt(b);
        if (opt.isEmpty()) return;

        e.setCancelled(true);

        Player viewer = e.getPlayer();
        graveManager.lootGrave(viewer, opt.get());
    }
}
