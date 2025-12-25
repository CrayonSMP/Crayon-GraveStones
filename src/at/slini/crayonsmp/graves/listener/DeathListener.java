package at.slini.crayonsmp.graves.listener;

import at.slini.crayonsmp.graves.GraveManager;
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

public class DeathListener implements Listener {

    private final GraveManager graveManager;

    public DeathListener(GraveManager graveManager) {
        this.graveManager = graveManager;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(PlayerDeathEvent e) {
        Player p = e.getEntity();

        if (!graveManager.isCaptureDrops()) {
            return;
        }

        // INVENTAR SLOTS sichern (Slot -> ItemStack)
        Map<Integer, ItemStack> slotItems = new HashMap<>();
        ItemStack[] contents = p.getInventory().getStorageContents(); // NUR Inventar+Hotbar (ohne Armor/Offhand)
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack it = contents[slot];
            if (it != null && it.getType() != Material.AIR) {
                slotItems.put(slot, it.clone());
            }
        }


        // ARMOR + OFFHAND sichern
        ItemStack[] armor = p.getInventory().getArmorContents().clone();
        ItemStack offHand = p.getInventory().getItemInOffHand();
        if (offHand != null) {
            offHand = offHand.clone();
            if (offHand.getType() == Material.AIR) offHand = null;
        }

        // VANILLA DROPS + XP UNTERBINDEN
        e.getDrops().clear();
        e.setDroppedExp(0);

        // ECHTE GESAMT-XP (XP-POINTS) BERECHNEN
        int totalXpPoints = ExpUtil.getTotalExperiencePoints(p);

        // GRAB ERSTELLEN
        graveManager.createGrave(p, p.getLocation(), slotItems, armor, offHand, totalXpPoints);
    }
}
