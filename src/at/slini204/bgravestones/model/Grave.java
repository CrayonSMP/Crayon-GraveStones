package at.slini204.bgravestones.model;

import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;

public final class Grave {
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
    private UUID waypointEntityId;

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

    public UUID getWaypointEntityId() {
        return waypointEntityId;
    }

    public void setWaypointEntityId(UUID waypointEntityId) {
        this.waypointEntityId = waypointEntityId;
    }
}
