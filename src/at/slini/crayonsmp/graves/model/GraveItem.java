package at.slini.crayonsmp.graves.model;

import org.bukkit.inventory.ItemStack;

public class GraveItem {
    private int slot;
    private ItemStack item;

    public GraveItem() {}

    public GraveItem(int slot, ItemStack item) {
        this.slot = slot;
        this.item = item;
    }

    public int getSlot() { return slot; }
    public ItemStack getItem() { return item; }

    public void setSlot(int slot) { this.slot = slot; }
    public void setItem(ItemStack item) { this.item = item; }
}
