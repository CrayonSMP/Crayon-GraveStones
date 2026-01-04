package at.slini.crayonsmp.graves.listener;

import at.slini.crayonsmp.graves.GraveManager;
import at.slini.crayonsmp.graves.model.Grave;
import at.slini.crayonsmp.graves.util.ExpUtil;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class DeathListener implements Listener {

    private final GraveManager graveManager;

    public DeathListener(GraveManager graveManager) {
        this.graveManager = graveManager;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(PlayerDeathEvent e) {
        Player p = e.getEntity();

        if (graveManager.isWorldDisabled(p.getWorld())) {
            return;
        }
        boolean capture = graveManager.isCaptureDrops();

        Map<Integer, ItemStack> slotItems = new HashMap<>();
        ItemStack[] armor = new ItemStack[0];
        ItemStack offHand = null;
        int totalXpPoints = 0;

        if (capture) {
            ItemStack[] contents = p.getInventory().getStorageContents();
            for (int slot = 0; slot < contents.length; slot++) {
                ItemStack it = contents[slot];
                if (it != null && it.getType() != Material.AIR) {
                    slotItems.put(slot, it.clone());
                }
            }

            armor = p.getInventory().getArmorContents().clone();

            offHand = p.getInventory().getItemInOffHand();
            if (offHand != null) {
                offHand = offHand.clone();
                if (offHand.getType() == Material.AIR) offHand = null;
            }

            totalXpPoints = ExpUtil.getTotalExperiencePoints(p);
        }

        Optional<Grave> created = graveManager.createGrave(
                p,
                p.getLocation(),
                slotItems,
                armor,
                offHand,
                totalXpPoints
        );

        if (capture && created.isPresent()) {
            e.getDrops().clear();
            e.setDroppedExp(0);
        }
    }
}
