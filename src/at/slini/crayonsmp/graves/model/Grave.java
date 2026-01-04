package at.slini.crayonsmp.graves.model;

import java.util.Map;
import java.util.UUID;

import org.bukkit.inventory.ItemStack;

public class Grave {
    private final UUID id;
    private final UUID ownerUuid;
    private final String ownerName;
    private final UUID worldUuid;
    private final int x;
    private final int y;
    private final int z;
    private final long createdAtEpochMs;
    private final int totalExp;
    private final Map<Integer, ItemStack> slotItems;
    private final ItemStack[] armor;
    private final ItemStack offHand;
    private UUID hologramEntityId;

    public Grave(UUID id, UUID ownerUuid, String ownerName, UUID worldUuid, int x, int y, int z, long createdAtEpochMs, int totalExp, Map<Integer, ItemStack> slotItems, ItemStack[] armor, ItemStack offHand) {
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
        return this.id;
    }

    public UUID getOwnerUuid() {
        return this.ownerUuid;
    }

    public String getOwnerName() {
        return this.ownerName;
    }

    public UUID getWorldUuid() {
        return this.worldUuid;
    }

    public int getX() {
        return this.x;
    }

    public int getY() {
        return this.y;
    }

    public int getZ() {
        return this.z;
    }

    public long getCreatedAtEpochMs() {
        return this.createdAtEpochMs;
    }

    public int getTotalExp() {
        return this.totalExp;
    }

    public Map<Integer, ItemStack> getSlotItems() {
        return this.slotItems;
    }

    public ItemStack[] getArmor() {
        return this.armor;
    }

    public ItemStack getOffHand() {
        return this.offHand;
    }

    public UUID getHologramEntityId() {
        return this.hologramEntityId;
    }

    public void setHologramEntityId(UUID hologramEntityId) {
        this.hologramEntityId = hologramEntityId;
    }
}
