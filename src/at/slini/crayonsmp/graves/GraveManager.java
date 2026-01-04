package at.slini.crayonsmp.graves;

import at.slini.crayonsmp.graves.model.Grave;
import at.slini.crayonsmp.graves.storage.IGraveStorage;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;
import org.joml.Vector3f;

public class GraveManager {

    private final GravePlugin plugin;
    private final NamespacedKey itemLooterKey;
    private final NamespacedKey itemExpiresKey;
    private final NamespacedKey hologramGraveIdKey;
    private IGraveStorage storage;
    private boolean soundOpenEnabled;
    private Sound soundOpen;
    private float soundOpenVolume;
    private float soundOpenPitch;
    private boolean soundLootEnabled;
    private Sound soundLoot;
    private float soundLootVolume;
    private float soundLootPitch;
    private boolean soundErrorEnabled;
    private Sound soundError;
    private float soundErrorVolume;
    private float soundErrorPitch;
    private boolean ownerOnly;
    private boolean allowOthersToLoot;
    private boolean protectDropsForOpener;
    private int protectedDropTimeoutSeconds;
    private boolean captureDrops;
    private boolean hologramEnabled;
    private double hologramOffsetY;
    private double hologramOffsetX;
    private double hologramOffsetZ;
    private double hologramFaceOffset;
    private String hologramFormat;
    private BlockFace hologramFacing;
    private float hologramTextScale;
    private double xpRestoreFraction;
    private boolean xpStealable;
    private boolean xpDropAsOrbsForNonOwner;
    private Material graveBlock;
    private int placementSearchUp;
    private int placementSearchDown;
    private Set<String> disabledWorlds;
    private boolean adminCanBreak;
    private DateTimeFormatter hologramDateFormatter;
    private int graveLimit;

    public GraveManager(GravePlugin plugin, IGraveStorage storage) {
        this.plugin = plugin;
        this.storage = storage;
        this.itemLooterKey = new NamespacedKey((Plugin) plugin, "grave_looter");
        this.itemExpiresKey = new NamespacedKey((Plugin) plugin, "grave_lock_expires");
        this.hologramGraveIdKey = new NamespacedKey((Plugin) plugin, "grave_holo_grave_id");
        reload();
    }

    public void setStorage(IGraveStorage storage) {
        if (storage == null) return;
        this.storage = storage;
    }

    public MessageManager messages() {
        return this.plugin.getMessages();
    }

    public void reload() {
        FileConfiguration c = this.plugin.getConfig();
        this.ownerOnly = c.getBoolean("ownerOnly", false);
        this.allowOthersToLoot = c.getBoolean("allowOthersToLoot", true);
        this.protectDropsForOpener = c.getBoolean("protectDropsForOpener", true);
        this.protectedDropTimeoutSeconds = c.getInt("protectedDropTimeoutSeconds", 180);
        this.captureDrops = c.getBoolean("captureDrops", true);
        this.hologramEnabled = c.getBoolean("hologramEnabled", true);
        this.hologramTextScale = (float) c.getDouble("hologramTextScale", 0.55);
        this.hologramOffsetY = c.getDouble("hologramOffsetY", 0.375);
        this.hologramOffsetX = c.getDouble("hologramOffsetX", 0.0);
        this.hologramOffsetZ = c.getDouble("hologramOffsetZ", 0.0);
        this.hologramFaceOffset = c.getDouble("hologramFaceOffset", 0.25);
        this.hologramFormat = c.getString("hologramFormat", "{player}\n{date})");
        this.xpRestoreFraction = c.getDouble("xpRestoreFraction", 0.33);
        this.xpStealable = c.getBoolean("xpStealable", true);
        this.xpDropAsOrbsForNonOwner = c.getBoolean("xpDropAsOrbsForNonOwner", false);
        this.placementSearchUp = c.getInt("placementSearchUp", 65);
        this.placementSearchDown = c.getInt("placementSearchDown", 0);
        if (this.placementSearchDown < 0) this.placementSearchDown = 0;
        if (this.placementSearchDown > 256) this.placementSearchDown = 256;
        this.adminCanBreak = c.getBoolean("adminCanBreak", true);
        this.graveLimit = c.getInt("graveLimit", 10);
        if (this.graveLimit < 0) this.graveLimit = 0;
        List<String> dw = c.getStringList("disabledWorlds");
        this.disabledWorlds = new HashSet<>();
        for (String s : dw) this.disabledWorlds.add(s.toLowerCase(Locale.ROOT));
        String matName = c.getString("graveBlock", "ANDESITE_WALL");
        Material m = Material.matchMaterial((matName == null) ? "" : matName);
        if (m == null || !m.isBlock()) m = Material.ANDESITE_WALL;
        this.graveBlock = m;
        ConfigurationSection sc = c.getConfigurationSection("sounds");
        if (sc != null) {
            ConfigurationSection open = sc.getConfigurationSection("graveOpen");
            if (open != null) {
                this.soundOpenEnabled = open.getBoolean("enabled", true);
                this.soundOpen = Sound.valueOf(open.getString("sound", "BLOCK_CHEST_OPEN"));
                this.soundOpenVolume = (float) open.getDouble("volume", 1.0);
                this.soundOpenPitch = (float) open.getDouble("pitch", 1.0);
            }
            ConfigurationSection loot = sc.getConfigurationSection("graveLooted");
            if (loot != null) {
                this.soundLootEnabled = loot.getBoolean("enabled", true);
                this.soundLoot = Sound.valueOf(loot.getString("sound", "ITEM_ARMOR_EQUIP_NETHERITE"));
                this.soundLootVolume = (float) loot.getDouble("volume", 1.0);
                this.soundLootPitch = (float) loot.getDouble("pitch", 1.0);
            }
            ConfigurationSection err = sc.getConfigurationSection("graveError");
            if (err != null) {
                this.soundErrorEnabled = err.getBoolean("enabled", true);
                this.soundError = Sound.valueOf(err.getString("sound", "BLOCK_NOTE_BLOCK_BASS"));
                this.soundErrorVolume = (float) err.getDouble("volume", 1.0);
                this.soundErrorPitch = (float) err.getDouble("pitch", 0.6);
            }
        }
        String facingRaw = c.getString("hologramFacing", "SOUTH").toUpperCase(Locale.ROOT);
        try {
            this.hologramFacing = BlockFace.valueOf(facingRaw);
        } catch (IllegalArgumentException ex) {
            this.plugin.getLogger().warning("[bGraveStones] Invalid hologramFacing '" + facingRaw + "', falling back to SOUTH");
            this.hologramFacing = BlockFace.SOUTH;
        }
        String dateFormat = c.getString("hologramDateFormat", "yyyy-MM-dd HH:mm");
        try {
            this.hologramDateFormatter = DateTimeFormatter.ofPattern(dateFormat).withZone(ZoneId.systemDefault());
        } catch (IllegalArgumentException ex) {
            this.plugin.getLogger().warning("[bGraveStones] Invalid hologramDateFormat '" + dateFormat + "', falling back to yyyy-MM-dd HH:mm");
            this.hologramDateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());
        }
    }

    public void refreshAllHolograms() {
        if (!this.hologramEnabled) {
            boolean changed = false;
            for (Grave g : this.storage.getAll()) {
                if (g.getHologramEntityId() != null) {
                    removeHologramEntity(g);
                    g.setHologramEntityId(null);
                    this.storage.put(g);
                    changed = true;
                }
            }
            if (changed) this.storage.saveAsync();
            return;
        }
        boolean changed = false;
        for (Grave g : this.storage.getAll()) {
            World w = Bukkit.getWorld(g.getWorldUuid());
            if (w == null) continue;
            if (isWorldDisabled(w)) {
                continue;
            }
            UUID newId = spawnOrReplaceHologram(g);
            if (newId != null && !newId.equals(g.getHologramEntityId())) {
                g.setHologramEntityId(newId);
                this.storage.put(g);
                changed = true;
            }
        }
        if (changed) this.storage.saveAsync();
    }


    public int purgeGravesOverLimitPerPlayer(int maxPerPlayer) {
        if (maxPerPlayer < 0) maxPerPlayer = 0;
        Map<UUID, List<Grave>> byOwner = new HashMap<>();
        for (Grave g : this.storage.getAll()) {
            ((List<Grave>) byOwner.computeIfAbsent(g.getOwnerUuid(), k -> new ArrayList<>())).add(g);
        }

        int removed = 0;
        for (Map.Entry<UUID, List<Grave>> entry : byOwner.entrySet()) {
            List<Grave> list = entry.getValue();
            if (list.size() <= maxPerPlayer) continue;
            list.sort(Comparator.comparingLong(Grave::getCreatedAtEpochMs));
            int toRemove = list.size() - maxPerPlayer;
            for (int i = 0; i < toRemove; i++) {
                Grave g = list.get(i);
                removeGrave(g, g.getOwnerUuid());
                removed++;
            }
        }
        this.storage.saveAsync();
        return removed;
    }

    public void restoreMissingGraveBlocks() {
        for (Grave g : this.storage.getAll()) {
            World w = Bukkit.getWorld(g.getWorldUuid());
            if (w == null) continue;
            Block b = w.getBlockAt(g.getX(), g.getY(), g.getZ());
            if (b.getType() == this.graveBlock) continue;

            if (b.isEmpty() || b.isLiquid() || b.isPassable()) {
                b.setType(this.graveBlock, false);
            }
        }
    }

    private void ensureChunkLoaded(World w, int cx, int cz) {
        if (w == null) return;
        if (w.isChunkLoaded(cx, cz)) return;
        w.getChunkAt(cx, cz);
    }

    private void removeHologramEntity(Grave grave) {
        if (grave == null) return;

        World w = Bukkit.getWorld(grave.getWorldUuid());
        if (w == null) return;

        int cx = grave.getX() >> 4;
        int cz = grave.getZ() >> 4;

        if (Bukkit.isPrimaryThread()) {
            ensureChunkLoaded(w, cx, cz);
        } else {
            Bukkit.getScheduler().runTask(this.plugin, () -> {
                ensureChunkLoaded(w, cx, cz);
                removeHologramEntity(grave);
            });
            return;
        }

        UUID holoId = grave.getHologramEntityId();
        String graveIdStr = grave.getId().toString();

        for (Entity e : w.getChunkAt(cx, cz).getEntities()) {
            if (!(e instanceof TextDisplay td)) continue;

            boolean matchUuid = (holoId != null && td.getUniqueId().equals(holoId));

            String tagged = td.getPersistentDataContainer().get(this.hologramGraveIdKey, PersistentDataType.STRING);
            boolean matchTag = (tagged != null && tagged.equals(graveIdStr));

            if (matchUuid || matchTag) {
                td.remove();
            }
        }
    }


    public void shutdown() {
    }

    public boolean isWorldDisabled(World world) {
        return (world != null && this.disabledWorlds.contains(world.getName().toLowerCase(Locale.ROOT)));
    }

    public boolean isCaptureDrops() {
        return this.captureDrops;
    }

    public boolean isAdminCanBreak() {
        return this.adminCanBreak;
    }

    public Material getGraveBlock() {
        return this.graveBlock;
    }

    public Collection<Grave> getAllGraves() {
        return this.storage.getAll();
    }

    public Optional<Grave> createGrave(Player owner, Location deathLoc, Map<Integer, ItemStack> slotItems, ItemStack[] armor, ItemStack offHand, int totalXpPoints) {

        if (owner == null || deathLoc == null || deathLoc.getWorld() == null) return Optional.empty();
        if (isWorldDisabled(deathLoc.getWorld())) return Optional.empty();

        int existing = (int) this.storage.getAll().stream().filter(g -> g.getOwnerUuid().equals(owner.getUniqueId())).count();

        int limit = this.plugin.getConfig().getInt("graveLimit", 10);

        if (this.storage.isLimitedStorage() && existing >= limit) {

            this.plugin.getMessages().send(owner, "warnings.storageNearLimit.player", Map.of("player", owner.getName(), "existing", String.valueOf(existing), "now", String.valueOf(existing + 1), "limit", String.valueOf(limit)));

            Bukkit.getOnlinePlayers().stream().filter(p -> p.hasPermission("graves.admin")).forEach(p -> this.plugin.getMessages().send(p, "warnings.storageNearLimit.staff", Map.of("player", owner.getName(), "now", String.valueOf(existing + 1), "limit", String.valueOf(limit))));

            this.plugin.getLogger().warning("[bGraveStones] Player " + owner.getName() + " reached grave limit (" + (existing + 1) + " / " + limit + ")");
        }
        Location placeAt = findPlaceableLocation(deathLoc);
        if (placeAt == null) return Optional.empty();
        UUID graveId = UUID.randomUUID();

        Grave grave = new Grave(graveId, owner.getUniqueId(), owner.getName(), placeAt.getWorld().getUID(), placeAt.getBlockX(), placeAt.getBlockY(), placeAt.getBlockZ(), Instant.now().toEpochMilli(), totalXpPoints, (slotItems == null) ? new HashMap<>() : new HashMap<>(slotItems), (armor == null) ? new ItemStack[0] : armor.clone(), (offHand == null) ? null : offHand.clone());

        Block b = placeAt.getBlock();
        b.setType(this.graveBlock, false);

        this.storage.put(grave);

        if (this.hologramEnabled) {
            UUID holoId = spawnOrReplaceHologram(grave);
            grave.setHologramEntityId(holoId);
            this.storage.put(grave);
        }

        this.storage.saveAsync();

        this.plugin.getMessages().send(owner, "grave.created", Map.of("x", String.valueOf(grave.getX()), "y", String.valueOf(grave.getY()), "z", String.valueOf(grave.getZ()), "world", placeAt.getWorld().getName()));

        return Optional.of(grave);
    }


    private Location findPlaceableLocation(Location base) {
        World w = base.getWorld();
        if (w == null) return null;
        int x0 = base.getBlockX();
        int z0 = base.getBlockZ();
        int y0 = base.getBlockY();
        int searchDown = Math.max(2, this.placementSearchDown);
        Location direct = findPlaceableInColumn(w, x0, z0, y0, searchDown);
        if (direct != null) return direct;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) continue;

                Location loc = findPlaceableInColumn(w, x0 + dx, z0 + dz, y0, searchDown);
                if (loc != null) return loc;
            }
        }

        int startY = Math.min(w.getMaxHeight() - 2, Math.max(w.getMinHeight(), y0));
        for (int y = startY; y >= w.getMinHeight(); y--) {
            Block ground = w.getBlockAt(x0, y, z0);
            if (!ground.getType().isSolid()) continue;

            Block above = w.getBlockAt(x0, y + 1, z0);
            if (above.isEmpty() || above.isPassable() || above.isLiquid()) {
                return new Location(w, x0, y + 1, z0);
            }
        }
        return w.getSpawnLocation();
    }

    private Location findPlaceableInColumn(World w, int x, int z, int y0, int searchDown) {
        for (int dy = -searchDown; dy <= this.placementSearchUp; dy++) {
            int y = y0 + dy;
            if (y < w.getMinHeight() || y > w.getMaxHeight() - 1) continue;

            Block b = w.getBlockAt(x, y, z);
            boolean replaceable = (b.isEmpty() || b.isLiquid() || b.isPassable());
            if (!replaceable) continue;

            Block below = b.getRelative(BlockFace.DOWN);
            if (below.getType().isSolid()) {
                return new Location(w, x, y, z);
            }
        }
        return null;
    }

    public Optional<Grave> getGraveAt(Block block) {
        if (block == null || block.getWorld() == null) return Optional.empty();
        return this.storage.findByLocation(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
    }

    public void lootGrave(Player looter, Grave grave) {
        if (looter == null || grave == null) return;
        boolean isOwner = looter.getUniqueId().equals(grave.getOwnerUuid());
        if (this.ownerOnly && !isOwner) {
            this.plugin.getMessages().send((CommandSender) looter, "grave.notYours");
            if (this.soundErrorEnabled) {
                looter.playSound(looter.getLocation(), this.soundError, this.soundErrorVolume, this.soundErrorPitch);
            }
            return;
        }
        if (!isOwner && !this.allowOthersToLoot) {
            this.plugin.getMessages().send((CommandSender) looter, "grave.cantLootOthers");
            if (this.soundErrorEnabled) {
                looter.playSound(looter.getLocation(), this.soundError, this.soundErrorVolume, this.soundErrorPitch);
            }
            return;
        }

        if (this.soundOpenEnabled) {
            looter.playSound(looter.getLocation(), this.soundOpen, this.soundOpenVolume, this.soundOpenPitch);
        }
        Location baseLoc = getGraveLocation(grave);
        if (baseLoc == null || baseLoc.getWorld() == null) return;
        Location dropLoc = baseLoc.clone().add(0.5D, 1.0D, 0.5D);
        if (isOwner) {
            restoreInventoryForOwner(looter, grave, dropLoc);
            int xp = calcXpFraction(grave.getTotalExp());
            if (xp > 0) {
                int xpFinal = xp;
                Bukkit.getScheduler().runTask((Plugin) this.plugin, () -> looter.giveExp(xpFinal));
            }

            if (this.soundLootEnabled) {
                looter.playSound(looter.getLocation(), this.soundLoot, this.soundLootVolume, this.soundLootPitch);
            }
            removeGrave(grave, looter.getUniqueId());
            this.plugin.getMessages().send((CommandSender) looter, "grave.pickedUpSelf");
            return;
        }
        dropItemsForLooter(grave.getSlotItems(), dropLoc, looter.getUniqueId());
        dropArmorForLooter(grave.getArmor(), dropLoc, looter.getUniqueId());
        dropSingleItemForLooter(grave.getOffHand(), dropLoc, looter.getUniqueId());
        if (this.xpStealable) {
            int xp = calcXpFraction(grave.getTotalExp());
            if (xp > 0) {
                int xpFinal = xp;
                Bukkit.getScheduler().runTask((Plugin) this.plugin, () -> {
                    if (this.xpDropAsOrbsForNonOwner) {
                        spawnXpOrbs(dropLoc, xpFinal);
                    } else {
                        looter.giveExp(xpFinal);
                    }
                });
            }
        }

        if (this.soundLootEnabled) {
            looter.playSound(looter.getLocation(), this.soundLoot, this.soundLootVolume, this.soundLootPitch);
        }
        removeGrave(grave, looter.getUniqueId());
        this.plugin.getMessages().send((CommandSender) looter, "grave.pickedUpOther", Map.of("owner", grave.getOwnerName()));
    }

    public void emergencyOpenAsNonOwner(Player opener, Grave grave) {
        if (opener == null || grave == null) return;
        Location baseLoc = getGraveLocation(grave);
        if (baseLoc == null || baseLoc.getWorld() == null) return;
        Location dropLoc = baseLoc.clone().add(0.5D, 1.0D, 0.5D);

        if (this.soundOpenEnabled) {
            opener.playSound(opener.getLocation(), this.soundOpen, this.soundOpenVolume, this.soundOpenPitch);
        }
        dropItemsForLooter(grave.getSlotItems(), dropLoc, opener.getUniqueId());
        dropArmorForLooter(grave.getArmor(), dropLoc, opener.getUniqueId());
        dropSingleItemForLooter(grave.getOffHand(), dropLoc, opener.getUniqueId());
        if (this.xpStealable) {
            int xp = calcXpFraction(grave.getTotalExp());
            if (xp > 0) {
                int xpFinal = xp;
                Bukkit.getScheduler().runTask((Plugin) this.plugin, () -> spawnXpOrbs(dropLoc, xpFinal));
            }
        }

        if (this.soundLootEnabled) {
            opener.playSound(opener.getLocation(), this.soundLoot, this.soundLootVolume, this.soundLootPitch);
        }
        removeGrave(grave, opener.getUniqueId());
    }

    private int calcXpFraction(int total) {
        if (total <= 0) return 0;
        double f = Math.max(0.0D, Math.min(1.0D, this.xpRestoreFraction));
        return (int) Math.round(total * f);
    }

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

    private void dropItemsForLooter(Map<Integer, ItemStack> slotItems, Location loc, UUID looterUuid) {
        if (slotItems == null || slotItems.isEmpty()) return;
        World w = loc.getWorld();
        if (w == null) return;
        long expiresAt = 0L;
        if (this.protectDropsForOpener && this.protectedDropTimeoutSeconds > 0) {
            expiresAt = System.currentTimeMillis() + this.protectedDropTimeoutSeconds * 1000L;
        }
        for (ItemStack it : slotItems.values()) {
            if (it == null || it.getType() == Material.AIR) continue;
            Item ent = w.dropItemNaturally(loc, it);
            if (this.protectDropsForOpener) {
                PersistentDataContainer pdc = ent.getPersistentDataContainer();
                pdc.set(this.itemLooterKey, PersistentDataType.STRING, looterUuid.toString());
                if (expiresAt > 0L) pdc.set(this.itemExpiresKey, PersistentDataType.LONG, expiresAt);
            }
        }
    }

    private void dropArmorForLooter(ItemStack[] armor, Location loc, UUID looterUuid) {
        if (armor == null || armor.length == 0) return;
        for (ItemStack it : armor) dropSingleItemForLooter(it, loc, looterUuid);
    }

    private void dropSingleItemForLooter(ItemStack it, Location loc, UUID looterUuid) {
        if (it == null || it.getType() == Material.AIR) return;
        World w = loc.getWorld();
        if (w == null) return;
        long expiresAt = 0L;
        if (this.protectDropsForOpener && this.protectedDropTimeoutSeconds > 0) {
            expiresAt = System.currentTimeMillis() + this.protectedDropTimeoutSeconds * 1000L;
        }
        Item ent = w.dropItemNaturally(loc, it);
        if (this.protectDropsForOpener) {
            PersistentDataContainer pdc = ent.getPersistentDataContainer();
            pdc.set(this.itemLooterKey, PersistentDataType.STRING, looterUuid.toString());
            if (expiresAt > 0L) pdc.set(this.itemExpiresKey, PersistentDataType.LONG, expiresAt);
        }
    }

    private void spawnXpOrbs(Location loc, int xp) {
        if (xp <= 0 || loc == null) return;
        World w = loc.getWorld();
        if (w == null) return;
        Location orbLoc = loc.clone().add(0.0D, 0.25D, 0.0D);
        int remaining = xp;
        while (remaining > 0) {
            int chunk = Math.min(10, remaining);
            ExperienceOrb orb = (ExperienceOrb) w.spawnEntity(orbLoc, EntityType.EXPERIENCE_ORB);
            orb.setExperience(chunk);
            remaining -= chunk;
        }
    }

    public boolean isPickupRestricted(Item item, Player player) {
        if (item == null || player == null) return false;
        PersistentDataContainer pdc = item.getPersistentDataContainer();
        String looter = pdc.get(this.itemLooterKey, PersistentDataType.STRING);
        if (looter == null || looter.isBlank()) return false;
        Long expiresAt = pdc.get(this.itemExpiresKey, PersistentDataType.LONG);
        if (expiresAt != null && expiresAt > 0L && System.currentTimeMillis() > expiresAt) {
            pdc.remove(this.itemLooterKey);
            pdc.remove(this.itemExpiresKey);
            return false;
        }
        return !player.getUniqueId().toString().equals(looter);
    }

    public boolean isGraveHologram(UUID entityId) {
        if (entityId == null) return false;
        for (Grave g : this.storage.getAll()) {
            if (g.getHologramEntityId() != null && g.getHologramEntityId().equals(entityId)) return true;
        }
        return false;
    }

    public void removeGrave(Grave grave, UUID removerUuid) {
        if (grave == null) return;
        World w = Bukkit.getWorld(grave.getWorldUuid());
        if (w != null) {
            Block b = w.getBlockAt(grave.getX(), grave.getY(), grave.getZ());
            if (b.getType() == this.graveBlock) {
                b.setType(Material.AIR, false);
            }
        }
        if (grave.getHologramEntityId() != null) {
            removeHologramEntity(grave);
        }

        this.storage.remove(grave.getId());
        this.storage.saveAsync();
    }

    private Location getGraveLocation(Grave grave) {
        World w = Bukkit.getWorld(grave.getWorldUuid());
        if (w == null) return null;
        return new Location(w, grave.getX(), grave.getY(), grave.getZ());
    }

    public UUID spawnOrReplaceHologram(Grave grave) {
        if (grave == null) return null;
        World w = Bukkit.getWorld(grave.getWorldUuid());
        if (w == null) return null;

        // In disabled worlds NICHT löschen/ersetzen – sonst verschwinden sie bei Reload/Restart.
        // Einfach den aktuellen Wert zurückgeben (kann auch null sein, dann ist es halt so).
        if (isWorldDisabled(w)) {
            return grave.getHologramEntityId();
        }

        // Nur in aktiven Welten entfernen und neu erstellen
        removeHologramEntity(grave);

        Location base = new Location(w, grave.getX(), grave.getY(), grave.getZ());
        BlockFace face = this.hologramFacing;

        Location textLoc = base.clone().add(
                0.5D + this.hologramOffsetX,
                this.hologramOffsetY,
                0.5D + this.hologramOffsetZ
        );

        double faceOffset = this.hologramFaceOffset;
        if (faceOffset < 0.0D) faceOffset = 0.0D;
        if (faceOffset > 1.5D) faceOffset = 1.5D;
        switch (face) {
            case NORTH -> textLoc.add(0.0D, 0.0D, -faceOffset);
            case SOUTH -> textLoc.add(0.0D, 0.0D,  faceOffset);
            case WEST  -> textLoc.add(-faceOffset, 0.0D, 0.0D);
            case EAST  -> textLoc.add( faceOffset, 0.0D, 0.0D);
            default -> { }
        }

        TextDisplay td = (TextDisplay) w.spawnEntity(textLoc, EntityType.TEXT_DISPLAY);

        td.getPersistentDataContainer().set(
                this.hologramGraveIdKey,
                PersistentDataType.STRING,
                grave.getId().toString()
        );

        String date = this.hologramDateFormatter.format(Instant.ofEpochMilli(grave.getCreatedAtEpochMs()));
        String text = this.hologramFormat
                .replace("{player}", grave.getOwnerName())
                .replace("{date}", date);

        td.setText(text);
        td.setBillboard(Display.Billboard.FIXED);
        float yaw;
        switch (face) {
            case NORTH -> yaw = 180.0F;
            case SOUTH -> yaw = 0.0F;
            case WEST  -> yaw = 90.0F;
            case EAST  -> yaw = -90.0F;
            default    -> yaw = 0.0F;
        }
        td.setRotation(yaw, 0.0F);
        td.setSeeThrough(false);
        td.setDefaultBackground(false);
        td.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
        td.setShadowed(false);
        td.setAlignment(TextDisplay.TextAlignment.CENTER);
        float scale = this.hologramTextScale;
        if (scale < 0.05F) scale = 0.05F;
        if (scale > 5.0F) scale = 5.0F;
        td.setLineWidth(Math.max(40, Math.min(300, (int) (200.0F / Math.max(0.25F, scale)))));
        try {
            Transformation t = td.getTransformation();
            td.setTransformation(new Transformation(
                    t.getTranslation(),
                    t.getLeftRotation(),
                    new Vector3f(scale, scale, scale),
                    t.getRightRotation()
            ));
        } catch (Throwable ex) {
            this.plugin.getLogger().warning("[bGraveStones] Could not apply hologramTextScale via Transformation API: " + ex.getMessage());
        }
        float boxScale = Math.max(0.25F, Math.min(2.0F, scale));
        td.setDisplayHeight(0.35F * boxScale);
        td.setDisplayWidth(0.9F * boxScale);
        td.setPersistent(true);
        td.setInvulnerable(true);
        return td.getUniqueId();
    }

    public void bootstrapVisuals() {
        if (!this.hologramEnabled) return;

        boolean changed = false;

        for (Grave grave : this.storage.getAll()) {
            World w = Bukkit.getWorld(grave.getWorldUuid());
            if (w == null) continue;
            if (isWorldDisabled(w)) {
                if (grave.getHologramEntityId() != null) {
                    removeHologramEntity(grave);
                    grave.setHologramEntityId(null);
                    storage.put(grave);
                    changed = true;
                }
                continue;
            }

            Block b = w.getBlockAt(grave.getX(), grave.getY(), grave.getZ());
            if (b.getType() != this.graveBlock) continue;

            int cx = grave.getX() >> 4;
            int cz = grave.getZ() >> 4;

            if (!w.isChunkLoaded(cx, cz)) {
                continue;
            }

            UUID holoId = grave.getHologramEntityId();
            String graveIdStr = grave.getId().toString();

            TextDisplay found = null;

            for (Entity e : w.getChunkAt(cx, cz).getEntities()) {
                if (!(e instanceof TextDisplay td)) continue;

                boolean matchUuid = (holoId != null && td.getUniqueId().equals(holoId));
                String tagged = td.getPersistentDataContainer().get(this.hologramGraveIdKey, PersistentDataType.STRING);
                boolean matchTag = (tagged != null && tagged.equals(graveIdStr));

                if (matchUuid || matchTag) {
                    found = td;
                    break;
                }
            }

            if (found != null) {
                if (holoId == null || !found.getUniqueId().equals(holoId)) {
                    grave.setHologramEntityId(found.getUniqueId());
                    this.storage.put(grave);
                    changed = true;
                }
                continue;
            }

            UUID newId = spawnOrReplaceHologram(grave);
            grave.setHologramEntityId(newId);
            this.storage.put(grave);
            changed = true;
        }

        if (changed) {
            this.storage.saveAsync();
        }
    }
}
