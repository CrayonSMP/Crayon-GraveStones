package at.slini204.bgravestones.listener;

import at.slini204.bgravestones.GraveManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;

public class EntityProtectionListener implements Listener {

    private final GraveManager graveManager;

    public EntityProtectionListener(GraveManager graveManager) {
        this.graveManager = graveManager;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (graveManager.isGraveVisualEntity(event.getEntity().getUniqueId())) {
            event.setCancelled(true);
        }
    }
}
