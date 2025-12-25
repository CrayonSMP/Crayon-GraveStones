package at.slini.crayonsmp.graves;

import at.slini.crayonsmp.graves.model.Grave;
import at.slini.crayonsmp.graves.storage.GraveStorage;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
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
import org.bukkit.util.Transformation;
import org.joml.Vector3f;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class GraveManager {

    private final GravePlugin plugin;
    private final GraveStorage storage;

    // Sound values
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
    private Set<String> disabledWorlds;
    private boolean adminCanBreak;

    private DateTimeFormatter hologramDateFormatter;

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
        this.hologramTextScale = (float) c.getDouble("hologramTextScale", 0.55);
        this.hologramOffsetY = c.getDouble("hologramOffsetY", 0.375);
        this.hologramOffsetX = c.getDouble("hologramOffsetX", 0.00);
        this.hologramOffsetZ = c.getDouble("hologramOffsetX", 0.00);
        this.hologramFaceOffset = c.getDouble("hologramFaceOffset", 0.25);
        this.hologramFormat = c.getString("hologramFormat", "§7✝ §f{player}\n§7({date})");

        this.xpRestoreFraction = c.getDouble("xpRestoreFraction", 0.33);
        this.xpStealable = c.getBoolean("xpStealable", false);
        this.xpDropAsOrbsForNonOwner = c.getBoolean("xpDropAsOrbsForNonOwner", true);

        this.placementSearchUp = c.getInt("placementSearchUp", 4);
        this.adminCanBreak = c.getBoolean("adminCanBreak", true);

        List<String> dw = c.getStringList("disabledWorlds");
        this.disabledWorlds = new HashSet<>();
        for (String s : dw) disabledWorlds.add(s.toLowerCase(Locale.ROOT));

        String matName = c.getString("graveBlock", "ANDESITE_WALL");
        Material m = Material.matchMaterial(matName == null ? "" : matName);
        if (m == null || !m.isBlock()) m = Material.ANDESITE_WALL;
        this.graveBlock = m;

        ConfigurationSection sc = c.getConfigurationSection("sounds");

        if (sc != null) {
            ConfigurationSection open = sc.getConfigurationSection("graveOpen");
            if (open != null) {
                soundOpenEnabled = open.getBoolean("enabled", true);
                soundOpen = Sound.valueOf(open.getString("sound", "BLOCK_CHEST_OPEN"));
                soundOpenVolume = (float) open.getDouble("volume", 1.0);
                soundOpenPitch = (float) open.getDouble("pitch", 1.0);
            }

            ConfigurationSection loot = sc.getConfigurationSection("graveLooted");
            if (loot != null) {
                soundLootEnabled = loot.getBoolean("enabled", true);
                soundLoot = Sound.valueOf(loot.getString("sound", "ITEM_ARMOR_EQUIP_NETHERITE"));
                soundLootVolume = (float) loot.getDouble("volume", 1.0);
                soundLootPitch = (float) loot.getDouble("pitch", 1.0);
            }

            ConfigurationSection err = sc.getConfigurationSection("graveError");
            if (err != null) {
                soundErrorEnabled = err.getBoolean("enabled", true);
                soundError = Sound.valueOf(err.getString("sound", "BLOCK_NOTE_BLOCK_BASS"));
                soundErrorVolume = (float) err.getDouble("volume", 1.0);
                soundErrorPitch = (float) err.getDouble("pitch", 0.6);
            }

        }
        String facingRaw = c.getString("hologramFacing", "SOUTH").toUpperCase(Locale.ROOT);
        try {
            hologramFacing = BlockFace.valueOf(facingRaw);
        } catch (IllegalArgumentException ex) {
            plugin.getLogger().warning(
                    "[Crayon-GraveStones] Invalid hologramFacing '" + facingRaw +
                            "', falling back to SOUTH"
            );
            hologramFacing = BlockFace.SOUTH;
        }
        String dateFormat = c.getString("hologramDateFormat", "yyyy-MM-dd HH:mm");

        try {
            hologramDateFormatter = DateTimeFormatter
                    .ofPattern(dateFormat)
                    .withZone(ZoneId.systemDefault());
        } catch (IllegalArgumentException ex) {
            plugin.getLogger().warning("[Crayon-GraveStones] Invalid hologramDateFormat '" + dateFormat +
                    "', falling back to yyyy-MM-dd HH:mm");
            hologramDateFormatter = DateTimeFormatter
                    .ofPattern("yyyy-MM-dd HH:mm")
                    .withZone(ZoneId.systemDefault());
        }

    }

    public void refreshAllHolograms() {
        if (!hologramEnabled) {
            // wenn hologramme aus: vorhandene entfernen
            for (Grave g : storage.getAll()) {
                if (g.getHologramEntityId() != null) {
                    removeHologramEntity(g);
                    g.setHologramEntityId(null);
                    storage.put(g);
                }
            }
            storage.saveAsync();
            return;
        }

        for (Grave g : storage.getAll()) {
            UUID newId = spawnOrReplaceHologram(g);
            g.setHologramEntityId(newId);
            storage.put(g);
        }
        storage.saveAsync();
    }

    private void removeHologramEntity(Grave grave) {
        if (grave.getHologramEntityId() == null) return;
        for (World world : Bukkit.getWorlds()) {
            world.getEntitiesByClass(TextDisplay.class).stream()
                    .filter(e -> e.getUniqueId().equals(grave.getHologramEntityId()))
                    .findFirst()
                    .ifPresent(Entity::remove);
        }
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

        owner.sendMessage("§7[§bCrayon-GraveStones§7] §fYour Gravestone is at Coordinates: §e" +
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
            looter.sendMessage("§cThis Gravestone is not yours.");
            if (soundErrorEnabled) {
                looter.playSound(looter.getLocation(), soundError, soundErrorVolume, soundErrorPitch);
            }
            return;
        }

        if (!isOwner && !allowOthersToLoot) {
            looter.sendMessage("§cYou can't loot other Gravestones.");
            if (soundErrorEnabled) {
                looter.playSound(looter.getLocation(), soundError, soundErrorVolume, soundErrorPitch);
            }
            return;
        }

        if (soundOpenEnabled) {
            looter.playSound(looter.getLocation(), soundOpen, soundOpenVolume, soundOpenPitch);
        }

        Location baseLoc = getGraveLocation(grave);
        if (baseLoc == null || baseLoc.getWorld() == null) return;
        Location dropLoc = baseLoc.clone().add(0.5, 1.0, 0.5);

        if (isOwner) {
            restoreInventoryForOwner(looter, grave, dropLoc);

            int xp = calcXpFraction(grave.getTotalExp());
            if (xp > 0) {
                int xpFinal = xp;
                Bukkit.getScheduler().runTask(plugin, () -> looter.giveExp(xpFinal));
            }

            if (soundLootEnabled) {
                looter.playSound(looter.getLocation(), soundLoot, soundLootVolume, soundLootPitch);
            }

            removeGrave(grave, looter.getUniqueId());
            looter.sendMessage("§aYou have picked up your Gravestone.");
            return;
        }

        // Non-owner loot: drop items on ground, optionally protected
        dropItemsForLooter(grave.getSlotItems(), dropLoc, looter.getUniqueId());

        if (xpStealable) {
            int xp = calcXpFraction(grave.getTotalExp());
            if (xp > 0) {
                int xpFinal = xp;
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (xpDropAsOrbsForNonOwner) {
                        spawnXpOrbs(dropLoc, xpFinal);
                    } else {
                        looter.giveExp(xpFinal);
                    }
                });
            }
        }

        if (soundLootEnabled) {
            looter.playSound(looter.getLocation(), soundLoot, soundLootVolume, soundLootPitch);
        }

        removeGrave(grave, looter.getUniqueId());
        looter.sendMessage("§eYou have picked up the Gravestone from §6" + grave.getOwnerName());
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
        if (xp <= 0 || loc == null) return;
        World w = loc.getWorld();
        if (w == null) return;

        Location orbLoc = loc.clone().add(0, 0.25, 0);

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

        // TextDisplay entfernen
        if (grave.getHologramEntityId() != null) {
            for (World world : Bukkit.getWorlds()) {
                world.getEntitiesByClass(TextDisplay.class).stream()
                        .filter(e -> e.getUniqueId().equals(grave.getHologramEntityId()))
                        .findFirst()
                        .ifPresent(Entity::remove);
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

        // altes Display entfernen (wenn vorhanden)
        if (grave.getHologramEntityId() != null) {
            w.getEntitiesByClass(TextDisplay.class).stream()
                    .filter(e -> e.getUniqueId().equals(grave.getHologramEntityId()))
                    .findFirst()
                    .ifPresent(Entity::remove);
        }

        Location base = new Location(w, grave.getX(), grave.getY(), grave.getZ());
        BlockFace face = hologramFacing;

// Start: Blockmitte + seitliche Offsets
        Location textLoc = base.clone().add(
                0.5 + hologramOffsetX,
                hologramOffsetY,
                0.5 + hologramOffsetZ
        );

// Abstand VOR der Blockfläche (Tiefe) – das ist der “näher dran”-Wert
        double faceOffset = hologramFaceOffset; // aus config
        if (faceOffset < 0.0) faceOffset = 0.0;
        if (faceOffset > 1.5) faceOffset = 1.5;

        switch (face) {
            case NORTH -> textLoc.add(0, 0, -faceOffset);
            case SOUTH -> textLoc.add(0, 0,  faceOffset);
            case WEST  -> textLoc.add(-faceOffset, 0, 0);
            case EAST  -> textLoc.add( faceOffset, 0, 0);
            default -> {}
        }


        TextDisplay td = (TextDisplay) w.spawnEntity(textLoc, EntityType.TEXT_DISPLAY);

        String date = hologramDateFormatter.format(Instant.ofEpochMilli(grave.getCreatedAtEpochMs()));
        String text = hologramFormat
                .replace("{player}", grave.getOwnerName())
                .replace("{date}", date);

        td.setText(text);

        // statisch (nicht zum Spieler drehen)
        td.setBillboard(Display.Billboard.FIXED);

        // Rotation passend zur Seite
        float yaw;
        switch (face) {
            case NORTH -> yaw = 180f;
            case SOUTH -> yaw = 0f;
            case WEST  -> yaw = 90f;
            case EAST  -> yaw = -90f;
            default -> yaw = 0f;
        }
        td.setRotation(yaw, 0f);

        // -------------------------
        // hologram fixes
        // -------------------------
        td.setSeeThrough(false);
        td.setDefaultBackground(false);
        td.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
        td.setShadowed(false);
        td.setAlignment(TextDisplay.TextAlignment.CENTER);

        // -------------------------
        // Textgröße aus Config anwenden (echte Skalierung via JOML)
        // -------------------------
        float scale = hologramTextScale;
        if (scale < 0.05f) scale = 0.05f;
        if (scale > 5.0f) scale = 5.0f;

        // Optional: LineWidth dynamisch, damit Umbrüche bei kleiner Schrift nicht "zu früh" passieren
        td.setLineWidth(Math.max(40, Math.min(300, (int) (200f / Math.max(0.25f, scale)))));

        try {
            Transformation t = td.getTransformation();
            td.setTransformation(new Transformation(
                    t.getTranslation(),
                    t.getLeftRotation(),
                    new org.joml.Vector3f(scale, scale, scale),
                    t.getRightRotation()
            ));
        } catch (Throwable ex) {
            plugin.getLogger().warning("[Crayon-GraveStones] Could not apply hologramTextScale via Transformation API: " + ex.getMessage());
        }

        // Bounding box (optional)
        float boxScale = Math.max(0.25f, Math.min(2.0f, scale));
        td.setDisplayHeight(0.35f * boxScale);
        td.setDisplayWidth(0.90f * boxScale);

        // robust / persistent
        td.setPersistent(true);
        td.setInvulnerable(true);

        return td.getUniqueId();
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
                exists = w.getEntitiesByClass(TextDisplay.class).stream()
                        .anyMatch(e -> e.getUniqueId().equals(grave.getHologramEntityId()));
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
