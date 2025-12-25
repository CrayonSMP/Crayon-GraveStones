package at.slini.crayonsmp.graves;

import at.slini.crayonsmp.graves.model.Grave;
import at.slini.crayonsmp.graves.storage.GraveStorage;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class GraveManager {

    private final GravePlugin plugin;
    private final GraveStorage storage;

    // Persistent keys
    private final NamespacedKey itemLooterKey;
    private final NamespacedKey itemExpiresKey;

    // Cached config values
    private boolean ownerOnly;
    private boolean allowOthersToLoot;
    private boolean protectDropsForOpener;
    private int protectedDropTimeoutSeconds;
    private boolean captureDrops;
    private boolean hologramEnabled;
    private double hologramOffsetY;
    private String hologramFormat;
    private double xpRestoreFraction;
    private boolean xpStealable;
    private boolean xpDropAsOrbsForNonOwner;
    private Material graveBlock;
    private int placementSearchUp;
    private Set<String> disabledWorlds;
    private boolean adminCanBreak;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneId.systemDefault());

    public GraveManager(GravePlugin plugin, GraveStorage storage) {
        this.plugin = plugin;
        this.storage = storage;
        this.itemLooterKey = new NamespacedKey(plugin, "grave_looter");
        this.itemExpiresKey = new NamespacedKey(plugin, "grave_lock_expires");
        reload();
    }

    public void reload() {
        FileConfiguration c = plugin.getConfig();
        this.ownerOnly = c.getBoolean("ownerOnly", true);
        this.allowOthersToLoot = c.getBoolean("allowOthersToLoot", true);
        this.protectDropsForOpener = c.getBoolean("protectDropsForOpener", true);
        this.protectedDropTimeoutSeconds = c.getInt("protectedDropTimeoutSeconds", 180);

        this.captureDrops = c.getBoolean("captureDrops", true);
        this.hologramEnabled = c.getBoolean("hologramEnabled", true);
        this.hologramOffsetY = c.getDouble("hologramOffsetY", 1.2);
        this.hologramFormat = c.getString("hologramFormat", "§7✝ §f{player} §7({date})");

        this.xpRestoreFraction = c.getDouble("xpRestoreFraction", 0.33);
        this.xpStealable = c.getBoolean("xpStealable", false);
        this.xpDropAsOrbsForNonOwner = c.getBoolean("xpDropAsOrbsForNonOwner", true);

        this.placementSearchUp = c.getInt("placementSearchUp", 4);
        this.adminCanBreak = c.getBoolean("adminCanBreak", true);

        List<String> dw = c.getStringList("disabledWorlds");
        this.disabledWorlds = new HashSet<>();
        for (String s : dw) disabledWorlds.add(s.toLowerCase(Locale.ROOT));

        String matName = c.getString("graveBlock", "POLISHED_ANDESITE_WALL");
        Material m = Material.matchMaterial(matName == null ? "" : matName);
        if (m == null || !m.isBlock()) m = Material.ANDESITE_WALL;
        this.graveBlock = m;
    }

    public void shutdown() {
        // nothing special yet
    }

    public boolean isWorldDisabled(World world) {
        return world != null && disabledWorlds.contains(world.getName().toLowerCase(Locale.ROOT));
    }

    public boolean isCaptureDrops() {
        return captureDrops;
    }

    public boolean isAdminCanBreak() {
        return adminCanBreak;
    }

    public Material getGraveBlock() {
        return graveBlock;
    }

    public Collection<Grave> getAllGraves() {
        return storage.getAll();
    }

    public Optional<Grave> createGrave(
            Player owner,
            Location deathLoc,
            Map<Integer, ItemStack> slotItems,
            ItemStack[] armor,
            ItemStack offHand,
            int totalXpPoints
    ) {
        if (owner == null || deathLoc == null || deathLoc.getWorld() == null) return Optional.empty();
        if (isWorldDisabled(deathLoc.getWorld())) return Optional.empty();

        Location placeAt = findPlaceableLocation(deathLoc);
        if (placeAt == null) return Optional.empty();

        UUID graveId = UUID.randomUUID();

        Grave grave = new Grave(
                graveId,
                owner.getUniqueId(),
                owner.getName(),
                placeAt.getWorld().getUID(),
                placeAt.getBlockX(),
                placeAt.getBlockY(),
                placeAt.getBlockZ(),
                Instant.now().toEpochMilli(),
                totalXpPoints,
                slotItems == null ? new HashMap<>() : new HashMap<>(slotItems),
                armor == null ? new ItemStack[0] : armor.clone(),
                offHand == null ? null : offHand.clone()
        );

        Block b = placeAt.getBlock();
        b.setType(graveBlock, false);

        if (hologramEnabled) {
            UUID holoId = spawnOrReplaceHologram(grave);
            grave.setHologramEntityId(holoId);
        }

        storage.put(grave);
        storage.saveAsync();

        owner.sendMessage("§7[§bCrayon-GraveStones§7] §fDein Grabstein ist bei §e" +
                grave.getX() + " " + grave.getY() + " " + grave.getZ() +
                " §7in §f" + placeAt.getWorld().getName() + "§7.");

        return Optional.of(grave);
    }

    private Location findPlaceableLocation(Location base) {
        World w = base.getWorld();
        int x = base.getBlockX();
        int z = base.getBlockZ();
        int y0 = base.getBlockY();

        for (int dy = 0; dy <= placementSearchUp; dy++) {
            int y = y0 + dy;
            if (y < w.getMinHeight() || y > w.getMaxHeight() - 1) continue;
            Block b = w.getBlockAt(x, y, z);
            if (b.isEmpty() || b.isLiquid() || b.isPassable()) {
                return new Location(w, x, y, z);
            }
        }
        return null;
    }

    public Optional<Grave> getGraveAt(Block block) {
        if (block == null || block.getWorld() == null) return Optional.empty();
        return storage.findByLocation(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
    }

    public void lootGrave(Player looter, Grave grave) {
        if (looter == null || grave == null) return;

        boolean isOwner = looter.getUniqueId().equals(grave.getOwnerUuid());

        if (ownerOnly && !isOwner) {
            looter.sendMessage("§cDieser Grabstein gehört nicht dir.");
            return;
        }

        if (!isOwner && !allowOthersToLoot) {
            looter.sendMessage("§cDu darfst fremde Grabsteine nicht plündern.");
            return;
        }

        Location baseLoc = getGraveLocation(grave);
        if (baseLoc == null || baseLoc.getWorld() == null) return;
        Location dropLoc = baseLoc.clone().add(0.5, 1.0, 0.5);

        if (isOwner) {
            restoreInventoryForOwner(looter, grave, dropLoc);

            int xp = calcXpFraction(grave.getTotalExp());
            if (xp > 0) looter.giveExp(xp);

            removeGrave(grave, looter.getUniqueId());
            looter.sendMessage("§aDu hast deinen Grabstein geplündert.");
            return;
        }

        // Non-owner loot: drop items on ground, optionally protected
        dropItemsForLooter(grave.getSlotItems(), dropLoc, looter.getUniqueId());

        if (xpStealable) {
            int xp = calcXpFraction(grave.getTotalExp());
            if (xp > 0) {
                if (xpDropAsOrbsForNonOwner) {
                    spawnXpOrbs(dropLoc, xp);
                } else {
                    looter.giveExp(xp);
                }
            }
        }

        removeGrave(grave, looter.getUniqueId());
        looter.sendMessage("§eDu hast den Grabstein von §6" + grave.getOwnerName() + " §egeplündert.");
    }

    private int calcXpFraction(int total) {
        if (total <= 0) return 0;
        double f = Math.max(0.0, Math.min(1.0, xpRestoreFraction));
        return (int) Math.round(total * f);
    }

    // ----------------------------
    // Owner inventory restore logic
    // ----------------------------

    private void restoreInventoryForOwner(Player p, Grave grave, Location overflowDropLoc) {
        Map<Integer, ItemStack> slotItems = grave.getSlotItems();
        if (slotItems != null && !slotItems.isEmpty()) {
            for (Map.Entry<Integer, ItemStack> entry : slotItems.entrySet()) {
                int slot = entry.getKey();
                ItemStack item = entry.getValue();
                placeOrFallback(p, slot, item, overflowDropLoc);
            }
        }

        equipArmorOrFallback(p, grave.getArmor(), overflowDropLoc);
        equipOffhandOrFallback(p, grave.getOffHand(), overflowDropLoc);

        p.updateInventory();
    }

    private void placeOrFallback(Player p, int slot, ItemStack item, Location overflowDropLoc) {
        if (item == null || item.getType() == Material.AIR) return;

        ItemStack current = p.getInventory().getItem(slot);
        if (current == null || current.getType() == Material.AIR) {
            p.getInventory().setItem(slot, item);
            return;
        }

        Map<Integer, ItemStack> leftover = p.getInventory().addItem(item);
        if (!leftover.isEmpty()) {
            for (ItemStack lf : leftover.values()) {
                overflowDropLoc.getWorld().dropItemNaturally(overflowDropLoc, lf);
            }
        }
    }

    private void equipArmorOrFallback(Player p, ItemStack[] armor, Location overflowDropLoc) {
        if (armor == null || armor.length == 0) return;

        ItemStack[] current = p.getInventory().getArmorContents();

        for (int i = 0; i < armor.length; i++) {
            ItemStack it = armor[i];
            if (it == null || it.getType() == Material.AIR) continue;

            if (i < current.length && (current[i] == null || current[i].getType() == Material.AIR)) {
                current[i] = it;
            } else {
                Map<Integer, ItemStack> leftover = p.getInventory().addItem(it);
                if (!leftover.isEmpty()) {
                    for (ItemStack lf : leftover.values()) {
                        overflowDropLoc.getWorld().dropItemNaturally(overflowDropLoc, lf);
                    }
                }
            }
        }

        p.getInventory().setArmorContents(current);
    }

    private void equipOffhandOrFallback(Player p, ItemStack offHand, Location overflowDropLoc) {
        if (offHand == null || offHand.getType() == Material.AIR) return;

        ItemStack cur = p.getInventory().getItemInOffHand();
        if (cur == null || cur.getType() == Material.AIR) {
            p.getInventory().setItemInOffHand(offHand);
            return;
        }

        Map<Integer, ItemStack> leftover = p.getInventory().addItem(offHand);
        if (!leftover.isEmpty()) {
            for (ItemStack lf : leftover.values()) {
                overflowDropLoc.getWorld().dropItemNaturally(overflowDropLoc, lf);
            }
        }
    }

    // ----------------------------
    // Non-owner drop logic
    // ----------------------------

    private void dropItemsForLooter(Map<Integer, ItemStack> slotItems, Location loc, UUID looterUuid) {
        if (slotItems == null || slotItems.isEmpty()) return;
        World w = loc.getWorld();
        if (w == null) return;

        long expiresAt = 0L;
        if (protectDropsForOpener && protectedDropTimeoutSeconds > 0) {
            expiresAt = System.currentTimeMillis() + (protectedDropTimeoutSeconds * 1000L);
        }

        for (ItemStack it : slotItems.values()) {
            if (it == null || it.getType() == Material.AIR) continue;
            Item ent = w.dropItemNaturally(loc, it);

            if (protectDropsForOpener) {
                PersistentDataContainer pdc = ent.getPersistentDataContainer();
                pdc.set(itemLooterKey, PersistentDataType.STRING, looterUuid.toString());
                if (expiresAt > 0) {
                    pdc.set(itemExpiresKey, PersistentDataType.LONG, expiresAt);
                }
            }
        }
    }

    private void spawnXpOrbs(Location loc, int xp) {
        World w = loc.getWorld();
        if (w == null) return;

        int remaining = xp;
        while (remaining > 0) {
            int chunk = Math.min(10, remaining);
            ExperienceOrb orb = (ExperienceOrb) w.spawnEntity(loc, EntityType.EXPERIENCE_ORB);
            orb.setExperience(chunk);
            remaining -= chunk;
        }
    }

    public boolean isPickupRestricted(Item item, Player player) {
        if (item == null || player == null) return false;

        PersistentDataContainer pdc = item.getPersistentDataContainer();
        String looter = pdc.get(itemLooterKey, PersistentDataType.STRING);
        if (looter == null || looter.isBlank()) return false;

        Long expiresAt = pdc.get(itemExpiresKey, PersistentDataType.LONG);
        if (expiresAt != null && expiresAt > 0 && System.currentTimeMillis() > expiresAt) {
            pdc.remove(itemLooterKey);
            pdc.remove(itemExpiresKey);
            return false;
        }

        return !player.getUniqueId().toString().equals(looter);
    }

    public boolean isGraveHologram(UUID entityId) {
        if (entityId == null) return false;
        for (Grave g : storage.getAll()) {
            if (g.getHologramEntityId() != null && g.getHologramEntityId().equals(entityId)) return true;
        }
        return false;
    }

    public void removeGrave(Grave grave, UUID removerUuid) {
        World w = Bukkit.getWorld(grave.getWorldUuid());
        if (w != null) {
            Block b = w.getBlockAt(grave.getX(), grave.getY(), grave.getZ());
            if (b.getType() == graveBlock) {
                b.setType(Material.AIR, false);
            }
        }

        if (grave.getHologramEntityId() != null) {
            for (World world : Bukkit.getWorlds()) {
                world.getEntitiesByClass(ArmorStand.class).stream()
                        .filter(a -> a.getUniqueId().equals(grave.getHologramEntityId()))
                        .findFirst()
                        .ifPresent(ArmorStand::remove);
            }
        }

        storage.remove(grave.getId());
        storage.saveAsync();
    }

    private Location getGraveLocation(Grave grave) {
        World w = Bukkit.getWorld(grave.getWorldUuid());
        if (w == null) return null;
        return new Location(w, grave.getX(), grave.getY(), grave.getZ());
    }

    public UUID spawnOrReplaceHologram(Grave grave) {
        World w = Bukkit.getWorld(grave.getWorldUuid());
        if (w == null) return null;

        if (grave.getHologramEntityId() != null) {
            w.getEntitiesByClass(ArmorStand.class).stream()
                    .filter(a -> a.getUniqueId().equals(grave.getHologramEntityId()))
                    .findFirst()
                    .ifPresent(ArmorStand::remove);
        }

        Location loc = new Location(w, grave.getX() + 0.5, grave.getY() + hologramOffsetY, grave.getZ() + 0.5);
        ArmorStand as = (ArmorStand) w.spawnEntity(loc, EntityType.ARMOR_STAND);

        as.setInvisible(true);
        as.setMarker(true);
        as.setGravity(false);
        as.setCustomNameVisible(true);
        as.setCanPickupItems(false);
        as.setPersistent(true);
        as.setInvulnerable(true);
        as.setCollidable(false);
        as.setSilent(true);
        as.setRemoveWhenFarAway(false);

        as.addEquipmentLock(EquipmentSlot.HEAD, ArmorStand.LockType.REMOVING_OR_CHANGING);
        as.addEquipmentLock(EquipmentSlot.CHEST, ArmorStand.LockType.REMOVING_OR_CHANGING);
        as.addEquipmentLock(EquipmentSlot.LEGS, ArmorStand.LockType.REMOVING_OR_CHANGING);
        as.addEquipmentLock(EquipmentSlot.FEET, ArmorStand.LockType.REMOVING_OR_CHANGING);
        as.addEquipmentLock(EquipmentSlot.HAND, ArmorStand.LockType.REMOVING_OR_CHANGING);
        as.addEquipmentLock(EquipmentSlot.OFF_HAND, ArmorStand.LockType.REMOVING_OR_CHANGING);

        String date = DATE_FMT.format(Instant.ofEpochMilli(grave.getCreatedAtEpochMs()));
        String name = hologramFormat
                .replace("{player}", grave.getOwnerName())
                .replace("{date}", date);

        as.setCustomName(name);

        return as.getUniqueId();
    }

    public void bootstrapVisuals() {
        if (!hologramEnabled) return;
        for (Grave grave : storage.getAll()) {
            World w = Bukkit.getWorld(grave.getWorldUuid());
            if (w == null) continue;

            Block b = w.getBlockAt(grave.getX(), grave.getY(), grave.getZ());
            if (b.getType() != graveBlock) continue;

            boolean exists = false;
            if (grave.getHologramEntityId() != null) {
                exists = w.getEntitiesByClass(ArmorStand.class).stream()
                        .anyMatch(a -> a.getUniqueId().equals(grave.getHologramEntityId()));
            }
            if (!exists) {
                UUID newId = spawnOrReplaceHologram(grave);
                grave.setHologramEntityId(newId);
                storage.put(grave);
            }
        }
        storage.saveAsync();
    }
}
