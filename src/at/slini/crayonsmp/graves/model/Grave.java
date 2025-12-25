package at.slini.crayonsmp.graves.model;

import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;

public class Grave {

    private final UUID id;
    private final UUID ownerUuid;
    private final String ownerName;

    private final UUID worldUuid;
    private final int x;
    private final int y;
    private final int z;

    private final long createdAtEpochMs;

    // total XP POINTS (no Level!)
    private final int totalExp;

    // Slot-based Items: Slot -> ItemStack
    private final Map<Integer, ItemStack> slotItems;

    // Armor + Offhand
    private final ItemStack[] armor;   // getArmorContents() (meist 4)
    private final ItemStack offHand;

    private UUID hologramEntityId;

    public Grave(UUID id,
                 UUID ownerUuid,
                 String ownerName,
                 UUID worldUuid,
                 int x,
                 int y,
                 int z,
                 long createdAtEpochMs,
                 int totalExp,
                 Map<Integer, ItemStack> slotItems,
                 ItemStack[] armor,
                 ItemStack offHand) {

        this.id = id;
        this.ownerUuid = ownerUuid;
        this.ownerName = ownerName;

        this.worldUuid = worldUuid;
        this.x = x;
        this.y = y;
        this.z = z;

        this.createdAtEpochMs = createdAtEpochMs;
        this.totalExp = totalExp;

        this.slotItems = slotItems;
        this.armor = armor;
        this.offHand = offHand;
    }

    public UUID getId() {
        return id;
    }

    public UUID getOwnerUuid() {
        return ownerUuid;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public UUID getWorldUuid() {
        return worldUuid;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getZ() {
        return z;
    }

    public long getCreatedAtEpochMs() {
        return createdAtEpochMs;
    }

    public int getTotalExp() {
        return totalExp;
    }

    public Map<Integer, ItemStack> getSlotItems() {
        return slotItems;
    }

    public ItemStack[] getArmor() {
        return armor;
    }

    public ItemStack getOffHand() {
        return offHand;
    }

    public UUID getHologramEntityId() {
        return hologramEntityId;
    }

    public void setHologramEntityId(UUID hologramEntityId) {
        this.hologramEntityId = hologramEntityId;
    }
}
