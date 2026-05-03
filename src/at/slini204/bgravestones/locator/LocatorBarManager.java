package at.slini204.bgravestones.locator;

import at.slini204.bgravestones.GravePlugin;
import at.slini204.bgravestones.model.Grave;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.attribute.Attributable;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.entity.EntityTeleportEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class LocatorBarManager implements Listener {

    private static final String ATTRIBUTE_WAYPOINT_TRANSMIT_RANGE = "WAYPOINT_TRANSMIT_RANGE";

    private static final String[] PLAYER_UUID_COLOR_PALETTE = {
            "aqua",
            "blue",
            "dark_aqua",
            "dark_blue",
            "dark_purple",
            "dark_red",
            "light_purple",
            "red"
    };

    private static final int[][] PLAYER_UUID_GRADIENT_PAIRS = {
            {0x5555FF, 0x55FFFF}, // blue -> aqua
            {0x0000AA, 0x5555FF}, // dark blue -> blue
            {0x0000AA, 0xAA00AA}, // dark blue -> purple
            {0xAA00AA, 0xFF55FF}, // purple -> pink
            {0xAA0000, 0xFF5555}, // dark red -> red
            {0xAA0000, 0xAA00AA}, // dark red -> purple
            {0x00AAAA, 0x5555FF}, // dark aqua -> blue
            {0x00AAAA, 0xFF55FF}, // dark aqua -> pink
            {0x5555FF, 0xFF55FF}, // blue -> pink
            {0xFF5555, 0xAA00AA}  // red -> purple
    };

    private static final String GRAVE_TAG_PREFIX = "bgs_grave_";
    private static final String GRAVE_MARKER_TAG = "bgs_grave_waypoint_marker";
    private static final String GRAVE_GLOW_TAG = "bgs_grave_glow_display";

    private final GravePlugin plugin;
    private final NamespacedKey graveWaypointKey;
    private final NamespacedKey graveGlowKey;

    private final Map<UUID, Boolean> nearState = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> internalWaypointTeleports = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> glowDisplayIds = new ConcurrentHashMap<>();
    private final Set<UUID> adminsViewingAllGraves = ConcurrentHashMap.newKeySet();

    private BukkitTask graveTask;
    private BukkitTask playerTask;

    private boolean enabled;
    private boolean forceGamerule;
    private boolean silentCommandFeedback;
    private boolean debugWaypointCommands;

    private boolean graveWaypointsEnabled;
    private String graveColor;
    private String graveNearColor;
    private double graveTransmitRange;
    private double graveNearDistance;
    private boolean graveGlowWhenNear;
    private long graveUpdateIntervalTicks;
    private boolean graveMarkerProtectionEnabled;
    private boolean graveMarkerLockPosition;
    private boolean graveMarkerCancelTeleport;
    private boolean graveMarkerRespawnIfKilled;
    private String graveViewAllPermission;

    private boolean playerWaypointsEnabled;
    private PlayerColorMode playerColorMode;
    private PlayerUuidHashStyle playerUuidHashStyle;
    private String playerDefaultColor;
    private double playerTransmitRange;
    private long playerReapplyIntervalTicks;
    private long playerGradientIntervalTicks;
    private int playerGradientSteps;
    private Map<String, String> perPlayerColors = new HashMap<>();

    private boolean warnedMissingWaypointAttribute;
    private boolean warnedSilentNmsFailed;

    public LocatorBarManager(GravePlugin plugin) {
        this.plugin = plugin;
        this.graveWaypointKey = new NamespacedKey((Plugin) plugin, "grave_waypoint_grave_id");
        this.graveGlowKey = new NamespacedKey((Plugin) plugin, "grave_glow_display_grave_id");
        reload();
    }

    public void reload() {
        ConfigurationSection root = plugin.getConfig().getConfigurationSection("locatorBar");
        if (root == null) {
            enabled = false;
            return;
        }

        enabled = root.getBoolean("enabled", false);
        forceGamerule = root.getBoolean("forceGamerule", true);

        ConfigurationSection commands = root.getConfigurationSection("commands");
        silentCommandFeedback = commands == null || commands.getBoolean("silent", true);
        debugWaypointCommands = commands != null && commands.getBoolean("debug", false);

        ConfigurationSection graves = root.getConfigurationSection("graves");
        graveWaypointsEnabled = graves != null && graves.getBoolean("enabled", false);
        graveColor = cleanColor(graves == null ? "gray" : graves.getString("color", "gray"));
        graveNearColor = cleanColor(graves == null ? "gold" : graves.getString("nearColor", "gold"));
        graveTransmitRange = clampDouble(graves == null ? 10000.0D : graves.getDouble("transmitRange", 10000.0D), 1.0D, 60000000.0D);
        graveNearDistance = clampDouble(graves == null ? 24.0D : graves.getDouble("nearGlowDistance", 24.0D), 0.0D, 256.0D);
        graveGlowWhenNear = graves == null || graves.getBoolean("glowWhenNear", true);
        graveUpdateIntervalTicks = clampLong(graves == null ? 40L : graves.getLong("updateIntervalTicks", 40L), 20L, 20L * 60L * 5L);
        graveViewAllPermission = graves == null ? "graves.admin" : graves.getString("viewAllPermission", "graves.admin");

        ConfigurationSection markerProtection = graves == null ? null : graves.getConfigurationSection("markerProtection");
        graveMarkerProtectionEnabled = markerProtection == null || markerProtection.getBoolean("enabled", true);
        graveMarkerLockPosition = markerProtection == null || markerProtection.getBoolean("lockPosition", true);
        graveMarkerCancelTeleport = markerProtection == null || markerProtection.getBoolean("cancelTeleport", true);
        graveMarkerRespawnIfKilled = markerProtection == null || markerProtection.getBoolean("respawnIfKilled", true);

        ConfigurationSection players = root.getConfigurationSection("players");
        playerWaypointsEnabled = players != null && players.getBoolean("enabled", false);
        playerColorMode = PlayerColorMode.fromConfig(players == null ? "vanilla" : players.getString("colorMode", "uuid_hash"));
        playerUuidHashStyle = PlayerUuidHashStyle.fromConfig(players == null ? "solid" : players.getString("uuidHashStyle", "solid"));
        playerDefaultColor = cleanPlayerColor(players == null ? "" : players.getString("defaultColor", ""));
        playerTransmitRange = clampDouble(players == null ? 0.0D : players.getDouble("transmitRange", 0.0D), 0.0D, 60000000.0D);
        playerReapplyIntervalTicks = clampLong(players == null ? 600L : players.getLong("reapplyIntervalTicks", 600L), 20L, 20L * 60L * 10L);

        ConfigurationSection gradient = players == null ? null : players.getConfigurationSection("uuidGradient");
        playerGradientIntervalTicks = clampLong(gradient == null ? 20L : gradient.getLong("intervalTicks", 20L), 5L, 20L * 60L);
        playerGradientSteps = (int) clampLong(gradient == null ? 32L : gradient.getLong("steps", 32L), 2L, 256L);

        perPlayerColors = readStringMap(players == null ? null : players.getConfigurationSection("perPlayerColors"));
        nearState.clear();
    }

    public void start() {
        shutdownTasksOnly();
        reload();

        if (!enabled) {
            return;
        }

        if (forceGamerule) {
            forceLocatorGamerule();
        }

        if (graveWaypointsEnabled) {
            syncAllGraveWaypoints();
            graveTask = Bukkit.getScheduler().runTaskTimer(
                    (Plugin) plugin,
                    this::tickGraveWaypoints,
                    graveUpdateIntervalTicks,
                    graveUpdateIntervalTicks
            );
        }

        if (playerWaypointsEnabled) {
            applyAllPlayerWaypointColors();

            long interval = isAnimatedUuidGradientEnabled()
                    ? playerGradientIntervalTicks
                    : playerReapplyIntervalTicks;

            playerTask = Bukkit.getScheduler().runTaskTimer(
                    (Plugin) plugin,
                    this::applyAllPlayerWaypointColors,
                    interval,
                    interval
            );
        }
    }

    public void shutdown() {
        shutdownTasksOnly();

        for (UUID displayId : glowDisplayIds.values()) {
            Entity entity = Bukkit.getEntity(displayId);
            if (entity != null) {
                entity.remove();
            }
        }

        glowDisplayIds.clear();
        nearState.clear();
        internalWaypointTeleports.clear();
        adminsViewingAllGraves.clear();
    }

    private void shutdownTasksOnly() {
        if (graveTask != null) {
            graveTask.cancel();
            graveTask = null;
        }

        if (playerTask != null) {
            playerTask.cancel();
            playerTask = null;
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isViewingAllGraves(Player player) {
        return player != null && adminsViewingAllGraves.contains(player.getUniqueId());
    }

    public boolean toggleViewingAllGraves(Player player) {
        if (player == null) {
            return false;
        }

        boolean next = !isViewingAllGraves(player);
        setViewingAllGraves(player, next);
        return next;
    }

    public void setViewingAllGraves(Player player, boolean enabled) {
        if (player == null) {
            return;
        }

        if (enabled && !player.hasPermission(graveViewAllPermission)) {
            adminsViewingAllGraves.remove(player.getUniqueId());
            updateWaypointVisibilityForPlayer(player);
            return;
        }

        if (enabled) {
            adminsViewingAllGraves.add(player.getUniqueId());
        } else {
            adminsViewingAllGraves.remove(player.getUniqueId());
        }

        updateWaypointVisibilityForPlayer(player);
    }

    private void runSync(Runnable task) {
        if (task == null || !plugin.isEnabled()) {
            return;
        }

        if (Bukkit.isPrimaryThread()) {
            task.run();
            return;
        }

        Bukkit.getScheduler().runTask((Plugin) plugin, task);
    }

    public void syncAllGraveWaypoints() {
        runSync(() -> {
            if (!enabled || !graveWaypointsEnabled) {
                return;
            }

            for (Grave grave : plugin.getGraveStorage().getAll()) {
                syncGraveWaypoint(grave);
            }

            updateAllWaypointVisibility();
        });
    }

    public void syncGraveWaypoint(Grave grave) {
        runSync(() -> {
            if (!enabled || !graveWaypointsEnabled || grave == null) {
                return;
            }

            World world = Bukkit.getWorld(grave.getWorldUuid());
            if (world == null || plugin.getGraveManager().isWorldDisabled(world)) {
                return;
            }

            int chunkX = grave.getX() >> 4;
            int chunkZ = grave.getZ() >> 4;

            if (!world.isChunkLoaded(chunkX, chunkZ)) {
                return;
            }

            ArmorStand stand = findOrCreateWaypointStand(grave, world);
            if (stand == null) {
                return;
            }

            boolean changed = false;

            if (!stand.getUniqueId().equals(grave.getWaypointEntityId())) {
                grave.setWaypointEntityId(stand.getUniqueId());
                plugin.getGraveStorage().put(grave);
                changed = true;
            }

            configureWaypointStand(stand, grave);

            boolean near = isNearAnyPlayer(grave);
            applyGraveWaypointColor(stand, grave, near);
            applyVisibilityToGraveEntity(grave, stand);

            if (changed) {
                plugin.getGraveStorage().saveAsync();
            }
        });
    }

    public void removeGraveWaypoint(Grave grave) {
        runSync(() -> {
            if (grave == null) {
                return;
            }

            UUID waypointId = grave.getWaypointEntityId();
            if (waypointId != null) {
                Entity entity = Bukkit.getEntity(waypointId);
                if (entity != null) {
                    entity.remove();
                }
            }

            removeGlowDisplay(grave);
            nearState.remove(grave.getId());
            grave.setWaypointEntityId(null);
        });
    }

    public boolean isManagedWaypointEntity(UUID entityId) {
        return findGraveByWaypointEntity(entityId) != null;
    }

    public Grave findGraveByWaypointEntity(UUID entityId) {
        if (entityId == null || plugin.getGraveStorage() == null) {
            return null;
        }

        for (Grave grave : plugin.getGraveStorage().getAll()) {
            if (entityId.equals(grave.getWaypointEntityId())) {
                return grave;
            }
        }

        return null;
    }

    private Grave findGraveByWaypointEntity(Entity entity) {
        if (!(entity instanceof ArmorStand stand)) {
            return null;
        }

        Grave byUuid = findGraveByWaypointEntity(stand.getUniqueId());
        if (byUuid != null) {
            return byUuid;
        }

        String storedGraveId = stand.getPersistentDataContainer().get(graveWaypointKey, PersistentDataType.STRING);
        if (storedGraveId == null || storedGraveId.isBlank()) {
            return null;
        }

        for (Grave grave : plugin.getGraveStorage().getAll()) {
            if (grave.getId().toString().equals(storedGraveId)) {
                return grave;
            }
        }

        return null;
    }

    private void tickGraveWaypoints() {
        if (!enabled || !graveWaypointsEnabled) {
            return;
        }

        for (Grave grave : plugin.getGraveStorage().getAll()) {
            World world = Bukkit.getWorld(grave.getWorldUuid());

            if (world == null || plugin.getGraveManager().isWorldDisabled(world)) {
                removeGlowDisplay(grave);
                continue;
            }

            if (!world.isChunkLoaded(grave.getX() >> 4, grave.getZ() >> 4)) {
                removeGlowDisplay(grave);
                continue;
            }

            ArmorStand stand = findWaypointStand(grave, world);
            if (stand == null) {
                syncGraveWaypoint(grave);
                continue;
            }

            configureWaypointStand(stand, grave);
            applyVisibilityToGraveEntity(grave, stand);

            boolean near = isNearAnyPlayer(grave);
            Boolean old = nearState.put(grave.getId(), near);

            if (old == null || old.booleanValue() != near) {
                applyGraveWaypointColor(stand, grave, near);
            } else if (near && graveGlowWhenNear) {
                updateGlowDisplay(grave, true);
            }
        }
    }

    private ArmorStand findOrCreateWaypointStand(Grave grave, World world) {
        ArmorStand existing = findWaypointStand(grave, world);
        if (existing != null) {
            return existing;
        }

        Location location = getWaypointLocation(grave, world);

        Entity entity = world.spawnEntity(location, EntityType.ARMOR_STAND);
        if (!(entity instanceof ArmorStand stand)) {
            entity.remove();
            return null;
        }

        stand.addScoreboardTag(GRAVE_MARKER_TAG);
        stand.addScoreboardTag(graveTag(grave));

        stand.getPersistentDataContainer().set(
                graveWaypointKey,
                PersistentDataType.STRING,
                grave.getId().toString()
        );

        applyVisibilityToGraveEntity(grave, stand);
        return stand;
    }

    private ArmorStand findWaypointStand(Grave grave, World world) {
        UUID waypointId = grave.getWaypointEntityId();

        if (waypointId != null) {
            Entity entity = Bukkit.getEntity(waypointId);
            if (entity instanceof ArmorStand stand && !stand.isDead()) {
                return stand;
            }
        }

        int chunkX = grave.getX() >> 4;
        int chunkZ = grave.getZ() >> 4;

        if (!world.isChunkLoaded(chunkX, chunkZ)) {
            return null;
        }

        String graveId = grave.getId().toString();
        String tag = graveTag(grave);

        for (Entity entity : world.getChunkAt(chunkX, chunkZ).getEntities()) {
            if (!(entity instanceof ArmorStand stand)) {
                continue;
            }

            if (stand.getScoreboardTags().contains(tag)) {
                return stand;
            }

            String storedGraveId = stand.getPersistentDataContainer().get(
                    graveWaypointKey,
                    PersistentDataType.STRING
            );

            if (graveId.equals(storedGraveId)) {
                stand.addScoreboardTag(GRAVE_MARKER_TAG);
                stand.addScoreboardTag(tag);
                return stand;
            }
        }

        return null;
    }

    private void configureWaypointStand(ArmorStand stand, Grave grave) {
        Location target = getWaypointLocation(grave, stand.getWorld());

        if (graveMarkerLockPosition && stand.getLocation().distanceSquared(target) > 0.01D) {
            teleportWaypointStand(stand, target);
        }

        stand.setVisible(false);
        stand.setGravity(false);
        stand.setInvulnerable(true);
        stand.setSilent(true);
        stand.setMarker(true);
        stand.setSmall(true);
        stand.setBasePlate(false);
        stand.setArms(false);
        stand.setPersistent(true);
        stand.setRemoveWhenFarAway(false);
        stand.setCollidable(false);
        stand.setCanPickupItems(false);
        stand.setAI(false);
        stand.setFireTicks(0);
        stand.setVelocity(new Vector(0.0D, 0.0D, 0.0D));

        stand.addScoreboardTag(GRAVE_MARKER_TAG);
        stand.addScoreboardTag(graveTag(grave));

        stand.getPersistentDataContainer().set(
                graveWaypointKey,
                PersistentDataType.STRING,
                grave.getId().toString()
        );

        setWaypointTransmitRange(stand, graveTransmitRange, graveSelector(grave));
    }

    private Location getWaypointLocation(Grave grave, World world) {
        return new Location(
                world,
                grave.getX() + 0.5D,
                grave.getY() + 1.0D,
                grave.getZ() + 0.5D
        );
    }

    private void teleportWaypointStand(ArmorStand stand, Location target) {
        internalWaypointTeleports.put(stand.getUniqueId(), Boolean.TRUE);

        try {
            stand.teleport(target);
        } finally {
            Bukkit.getScheduler().runTask(
                    (Plugin) plugin,
                    () -> internalWaypointTeleports.remove(stand.getUniqueId())
            );
        }
    }

    private void applyGraveWaypointColor(ArmorStand stand, Grave grave, boolean near) {
        String color = near && !graveNearColor.isEmpty()
                ? graveNearColor
                : graveColor;

        if (!color.isEmpty()) {
            dispatchWaypointColor(graveSelector(grave), color);
        }

        stand.setGlowing(false);
        updateGlowDisplay(grave, near);

        nearState.put(grave.getId(), near);
    }

    private void updateGlowDisplay(Grave grave, boolean near) {
        if (!graveGlowWhenNear || !near || graveNearDistance <= 0.0D) {
            removeGlowDisplay(grave);
            return;
        }

        World world = Bukkit.getWorld(grave.getWorldUuid());
        if (world == null || !world.isChunkLoaded(grave.getX() >> 4, grave.getZ() >> 4)) {
            removeGlowDisplay(grave);
            return;
        }

        Block block = world.getBlockAt(grave.getX(), grave.getY(), grave.getZ());

        UUID existingId = glowDisplayIds.get(grave.getId());
        Entity existing = existingId == null ? null : Bukkit.getEntity(existingId);

        BlockDisplay display;

        if (existing instanceof BlockDisplay blockDisplay && !blockDisplay.isDead()) {
            display = blockDisplay;
        } else {
            Entity spawned = world.spawnEntity(block.getLocation(), EntityType.BLOCK_DISPLAY);

            if (!(spawned instanceof BlockDisplay blockDisplay)) {
                spawned.remove();
                return;
            }

            display = blockDisplay;
            glowDisplayIds.put(grave.getId(), display.getUniqueId());
        }

        display.teleport(block.getLocation());
        display.setBlock(block.getType().isAir()
                ? Material.COBBLESTONE.createBlockData()
                : block.getBlockData());

        display.setGlowing(true);
        display.setPersistent(false);
        display.setSilent(true);
        display.setInvulnerable(true);
        display.setBrightness(new Display.Brightness(15, 15));
        display.setViewRange(64.0F);
        display.setShadowRadius(0.0F);
        display.setShadowStrength(0.0F);

        display.addScoreboardTag(GRAVE_GLOW_TAG);
        display.addScoreboardTag(graveTag(grave));

        display.getPersistentDataContainer().set(
                graveGlowKey,
                PersistentDataType.STRING,
                grave.getId().toString()
        );

        applyVisibilityToGraveEntity(grave, display);
    }

    private void removeGlowDisplay(Grave grave) {
        if (grave == null) {
            return;
        }

        UUID displayId = glowDisplayIds.remove(grave.getId());
        if (displayId == null) {
            return;
        }

        Entity entity = Bukkit.getEntity(displayId);
        if (entity != null) {
            entity.remove();
        }
    }

    private boolean isNearAnyPlayer(Grave grave) {
        if (graveNearDistance <= 0.0D) {
            return false;
        }

        World world = Bukkit.getWorld(grave.getWorldUuid());
        if (world == null) {
            return false;
        }

        double maxDistanceSquared = graveNearDistance * graveNearDistance;

        Location graveLocation = new Location(
                world,
                grave.getX() + 0.5D,
                grave.getY() + 0.5D,
                grave.getZ() + 0.5D
        );

        for (Player player : world.getPlayers()) {
            if (player.isDead() || !player.isOnline()) {
                continue;
            }

            if (player.getLocation().distanceSquared(graveLocation) <= maxDistanceSquared) {
                return true;
            }
        }

        return false;
    }

    private boolean canPlayerSeeGrave(Player player, Grave grave) {
        if (player == null || grave == null) {
            return false;
        }

        UUID ownerUuid = grave.getOwnerUuid();
        if (ownerUuid != null && ownerUuid.equals(player.getUniqueId())) {
            return true;
        }

        return player.hasPermission(graveViewAllPermission)
                && adminsViewingAllGraves.contains(player.getUniqueId());
    }

    private void applyVisibilityToGraveEntity(Grave grave, Entity entity) {
        if (grave == null || entity == null) {
            return;
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            applyVisibilityToGraveEntity(player, grave, entity);
        }
    }

    private void applyVisibilityToGraveEntity(Player player, Grave grave, Entity entity) {
        if (player == null || grave == null || entity == null) {
            return;
        }

        if (canPlayerSeeGrave(player, grave)) {
            player.showEntity((Plugin) plugin, entity);
        } else {
            player.hideEntity((Plugin) plugin, entity);
        }
    }

    public void updateWaypointVisibilityForPlayer(Player player) {
        if (player == null || plugin.getGraveStorage() == null) {
            return;
        }

        for (Grave grave : plugin.getGraveStorage().getAll()) {
            World world = Bukkit.getWorld(grave.getWorldUuid());
            if (world == null || !world.isChunkLoaded(grave.getX() >> 4, grave.getZ() >> 4)) {
                continue;
            }

            ArmorStand stand = findWaypointStand(grave, world);
            if (stand != null) {
                applyVisibilityToGraveEntity(player, grave, stand);
            }

            UUID displayId = glowDisplayIds.get(grave.getId());
            Entity display = displayId == null ? null : Bukkit.getEntity(displayId);
            if (display != null) {
                applyVisibilityToGraveEntity(player, grave, display);
            }
        }
    }

    private void updateAllWaypointVisibility() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            updateWaypointVisibilityForPlayer(player);
        }
    }

    private void applyAllPlayerWaypointColors() {
        if (!enabled || !playerWaypointsEnabled) {
            return;
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            applyPlayerWaypointColor(player);
        }
    }

    private void applyPlayerWaypointColor(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }

        if (playerTransmitRange > 0.0D) {
            setWaypointTransmitRange(player, playerTransmitRange, player.getName());
        }

        String color = findPlayerColor(player);
        if (!color.isEmpty()) {
            dispatchWaypointColor(player.getName(), color);
        }
    }

    private String findPlayerColor(Player player) {
        String override = findPlayerOverride(player, perPlayerColors);
        if (!override.isEmpty()) {
            return override;
        }

        return switch (playerColorMode) {
            case FIXED -> playerDefaultColor;
            case UUID_HASH -> colorFromUuid(player.getUniqueId());
            case VANILLA -> "";
        };
    }

    private String findPlayerOverride(Player player, Map<String, String> overrides) {
        String byUuid = overrides.get(player.getUniqueId().toString().toLowerCase(Locale.ROOT));
        if (byUuid != null) {
            return byUuid;
        }

        String byName = overrides.get(player.getName().toLowerCase(Locale.ROOT));
        return byName == null ? "" : byName;
    }

    private void setWaypointTransmitRange(Entity entity, double range, String targetSelector) {
        boolean appliedViaApi = false;

        if (entity instanceof Attributable attributable) {
            try {
                Attribute attribute = Attribute.valueOf(ATTRIBUTE_WAYPOINT_TRANSMIT_RANGE);
                AttributeInstance instance = attributable.getAttribute(attribute);

                if (instance != null) {
                    instance.setBaseValue(range);
                    appliedViaApi = true;
                }
            } catch (Throwable ex) {
                if (!warnedMissingWaypointAttribute) {
                    warnedMissingWaypointAttribute = true;
                    plugin.getLogger().warning(
                            "[bGraveStones] Could not set WAYPOINT_TRANSMIT_RANGE through Bukkit API. "
                                    + "Trying vanilla attribute command fallback. Cause: "
                                    + ex.getClass().getSimpleName()
                    );
                }
            }
        }

        if (!appliedViaApi && targetSelector != null && !targetSelector.isBlank()) {
            dispatchCommandSilently(
                    "minecraft:attribute "
                            + targetSelector
                            + " minecraft:waypoint_transmit_range base set "
                            + formatNumber(range)
            );
        }
    }

    private void dispatchWaypointColor(String targetSelector, String color) {
        if (targetSelector == null || targetSelector.isBlank() || color == null || color.isBlank()) {
            return;
        }

        String cleaned = cleanColor(color);
        if (cleaned.isEmpty()) {
            return;
        }

        if (cleaned.startsWith("#")) {
            dispatchCommandSilently(
                    "minecraft:waypoint modify "
                            + targetSelector
                            + " color hex "
                            + cleaned.substring(1)
            );
            return;
        }

        if (cleaned.matches("(?i)[0-9a-f]{6}")) {
            dispatchCommandSilently(
                    "minecraft:waypoint modify "
                            + targetSelector
                            + " color hex "
                            + cleaned
            );
            return;
        }

        dispatchCommandSilently(
                "minecraft:waypoint modify "
                        + targetSelector
                        + " color "
                        + cleaned
        );
    }

    private void forceLocatorGamerule() {
        boolean directApplied = false;

        for (World world : Bukkit.getWorlds()) {
            if (setGameruleReflectively(world, "locator_bar", "true")) {
                directApplied = true;
            }
        }

        if (!directApplied) {
            dispatchCommandSilently("minecraft:gamerule locator_bar true");
        }
    }

    private boolean setGameruleReflectively(World world, String rule, String value) {
        if (world == null) {
            return false;
        }

        try {
            Method method = world.getClass().getMethod("setGameRuleValue", String.class, String.class);
            Object result = method.invoke(world, rule, value);

            return !(result instanceof Boolean bool) || bool;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void dispatchCommandSilently(String command) {
        if (command == null || command.isBlank()) {
            return;
        }

        String cleaned = stripLeadingSlash(command);

        if (debugWaypointCommands) {
            plugin.getLogger().info("[bGraveStones] Dispatching command: /" + cleaned);
        }

        try {
            if (silentCommandFeedback && dispatchVanillaCommandWithSuppressedOutput(cleaned)) {
                return;
            }

            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cleaned);
        } catch (Throwable ex) {
            plugin.getLogger().warning(
                    "[bGraveStones] Command failed: /"
                            + cleaned
                            + " | Cause: "
                            + ex.getClass().getSimpleName()
                            + ": "
                            + ex.getMessage()
            );
        }
    }

    private boolean dispatchVanillaCommandWithSuppressedOutput(String command) {
        try {
            Object craftServer = Bukkit.getServer();
            Method getServer = craftServer.getClass().getMethod("getServer");
            Object minecraftServer = getServer.invoke(craftServer);

            Method createStack = minecraftServer.getClass().getMethod("createCommandSourceStack");
            Object sourceStack = createStack.invoke(minecraftServer);

            try {
                Method suppressed = sourceStack.getClass().getMethod("withSuppressedOutput");
                sourceStack = suppressed.invoke(sourceStack);
            } catch (NoSuchMethodException ignored) {
                return false;
            }

            Method getCommands = minecraftServer.getClass().getMethod("getCommands");
            Object commands = getCommands.invoke(minecraftServer);

            Method performPrefixedCommand = null;
            for (Method method : commands.getClass().getMethods()) {
                if (!method.getName().equals("performPrefixedCommand")) {
                    continue;
                }

                Class<?>[] parameterTypes = method.getParameterTypes();
                if (parameterTypes.length == 2 && parameterTypes[1].equals(String.class)) {
                    performPrefixedCommand = method;
                    break;
                }
            }

            if (performPrefixedCommand == null) {
                return false;
            }

            performPrefixedCommand.invoke(commands, sourceStack, command);
            return true;
        } catch (Throwable ex) {
            if (debugWaypointCommands && !warnedSilentNmsFailed) {
                warnedSilentNmsFailed = true;
                plugin.getLogger().warning(
                        "[bGraveStones] Silent vanilla command dispatch is not available. "
                                + "Falling back to console sender. Cause: "
                                + ex.getClass().getSimpleName()
                                + ": "
                                + ex.getMessage()
                );
            }
            return false;
        }
    }

    private static String stripLeadingSlash(String command) {
        String cleaned = command.trim();
        while (cleaned.startsWith("/")) {
            cleaned = cleaned.substring(1);
        }
        return cleaned;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onWaypointDamage(EntityDamageEvent event) {
        if (!shouldProtectGraveMarkers()) {
            return;
        }

        Grave grave = findGraveByWaypointEntity(event.getEntity());
        if (grave == null) {
            return;
        }

        event.setCancelled(true);
        event.setDamage(0.0D);

        if (event.getEntity() instanceof ArmorStand stand) {
            configureWaypointStand(stand, grave);
            applyGraveWaypointColor(stand, grave, isNearAnyPlayer(grave));
            applyVisibilityToGraveEntity(grave, stand);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onWaypointCombust(EntityCombustEvent event) {
        if (!shouldProtectGraveMarkers()) {
            return;
        }

        Grave grave = findGraveByWaypointEntity(event.getEntity());
        if (grave == null) {
            return;
        }

        event.setCancelled(true);
        event.getEntity().setFireTicks(0);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onWaypointPortal(EntityPortalEvent event) {
        if (!shouldProtectGraveMarkers()) {
            return;
        }

        if (findGraveByWaypointEntity(event.getEntity()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onWaypointTeleport(EntityTeleportEvent event) {
        if (!shouldProtectGraveMarkers() || !graveMarkerCancelTeleport) {
            return;
        }

        if (internalWaypointTeleports.containsKey(event.getEntity().getUniqueId())) {
            return;
        }

        Grave grave = findGraveByWaypointEntity(event.getEntity());
        if (grave == null) {
            return;
        }

        event.setCancelled(true);

        if (event.getEntity() instanceof ArmorStand stand) {
            configureWaypointStand(stand, grave);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onWaypointDeath(EntityDeathEvent event) {
        if (!shouldProtectGraveMarkers() || !graveMarkerRespawnIfKilled) {
            return;
        }

        Grave grave = findGraveByWaypointEntity(event.getEntity().getUniqueId());
        if (grave == null) {
            return;
        }

        event.getDrops().clear();
        event.setDroppedExp(0);
        nearState.remove(grave.getId());

        Bukkit.getScheduler().runTaskLater(
                (Plugin) plugin,
                () -> syncGraveWaypoint(grave),
                1L
        );
    }

    private boolean shouldProtectGraveMarkers() {
        return enabled && graveWaypointsEnabled && graveMarkerProtectionEnabled;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (enabled && playerWaypointsEnabled) {
            Bukkit.getScheduler().runTaskLater(
                    (Plugin) plugin,
                    () -> applyPlayerWaypointColor(event.getPlayer()),
                    20L
            );
        }

        if (enabled && graveWaypointsEnabled) {
            Bukkit.getScheduler().runTaskLater(
                    (Plugin) plugin,
                    () -> updateWaypointVisibilityForPlayer(event.getPlayer()),
                    20L
            );
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        if (enabled && playerWaypointsEnabled) {
            Bukkit.getScheduler().runTaskLater(
                    (Plugin) plugin,
                    () -> applyPlayerWaypointColor(event.getPlayer()),
                    10L
            );
        }

        if (enabled && graveWaypointsEnabled) {
            Bukkit.getScheduler().runTaskLater(
                    (Plugin) plugin,
                    () -> updateWaypointVisibilityForPlayer(event.getPlayer()),
                    10L
            );
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChunkLoad(ChunkLoadEvent event) {
        if (!enabled || !graveWaypointsEnabled) {
            return;
        }

        Bukkit.getScheduler().runTaskLater(
                (Plugin) plugin,
                () -> syncLoadedChunk(event),
                1L
        );
    }

    private void syncLoadedChunk(ChunkLoadEvent event) {
        for (Entity entity : event.getChunk().getEntities()) {
            if (!(entity instanceof ArmorStand stand)) {
                continue;
            }

            String graveId = stand.getPersistentDataContainer().get(
                    graveWaypointKey,
                    PersistentDataType.STRING
            );

            if (graveId == null || graveId.isBlank()) {
                continue;
            }

            boolean known = false;

            for (Grave grave : plugin.getGraveStorage().getAll()) {
                if (grave.getId().toString().equals(graveId)) {
                    known = true;
                    stand.addScoreboardTag(GRAVE_MARKER_TAG);
                    stand.addScoreboardTag(graveTag(grave));
                    applyVisibilityToGraveEntity(grave, stand);
                    break;
                }
            }

            if (!known) {
                stand.remove();
            }
        }

        for (Grave grave : plugin.getGraveStorage().getAll()) {
            World world = Bukkit.getWorld(grave.getWorldUuid());

            if (world == null || !world.equals(event.getChunk().getWorld())) {
                continue;
            }

            if ((grave.getX() >> 4) == event.getChunk().getX()
                    && (grave.getZ() >> 4) == event.getChunk().getZ()) {
                syncGraveWaypoint(grave);
            }
        }
    }

    private String colorFromUuid(UUID uuid) {
        if (isAnimatedUuidGradientEnabled()) {
            return gradientColorFromUuid(uuid);
        }

        long mixed = mixUuid(uuid);
        int index = (int) Math.floorMod(mixed, PLAYER_UUID_COLOR_PALETTE.length);

        return PLAYER_UUID_COLOR_PALETTE[index];
    }

    private boolean isAnimatedUuidGradientEnabled() {
        return playerColorMode == PlayerColorMode.UUID_HASH
                && playerUuidHashStyle == PlayerUuidHashStyle.GRADIENT;
    }

    private String gradientColorFromUuid(UUID uuid) {
        long mixed = mixUuid(uuid);

        int pairIndex = (int) Math.floorMod(mixed, PLAYER_UUID_GRADIENT_PAIRS.length);
        int[] pair = PLAYER_UUID_GRADIENT_PAIRS[pairIndex];

        long tick = System.currentTimeMillis() / 50L;
        int offset = (int) Math.floorMod(mixed >>> 8, playerGradientSteps);
        int phase = (int) Math.floorMod(
                (tick / Math.max(1L, playerGradientIntervalTicks)) + offset,
                playerGradientSteps * 2L
        );

        int step = phase <= playerGradientSteps
                ? phase
                : (playerGradientSteps * 2 - phase);

        double progress = playerGradientSteps <= 0
                ? 0.0D
                : (double) step / (double) playerGradientSteps;

        int rgb = lerpRgb(pair[0], pair[1], progress);

        return String.format("#%06X", rgb & 0xFFFFFF);
    }

    private static long mixUuid(UUID uuid) {
        return uuid.getMostSignificantBits()
                ^ Long.rotateLeft(uuid.getLeastSignificantBits(), 32);
    }

    private static int lerpRgb(int from, int to, double progress) {
        progress = Math.max(0.0D, Math.min(1.0D, progress));

        int fromR = (from >> 16) & 0xFF;
        int fromG = (from >> 8) & 0xFF;
        int fromB = from & 0xFF;

        int toR = (to >> 16) & 0xFF;
        int toG = (to >> 8) & 0xFF;
        int toB = to & 0xFF;

        int r = (int) Math.round(fromR + (toR - fromR) * progress);
        int g = (int) Math.round(fromG + (toG - fromG) * progress);
        int b = (int) Math.round(fromB + (toB - fromB) * progress);

        return (r << 16) | (g << 8) | b;
    }

    private static Map<String, String> readStringMap(ConfigurationSection section) {
        Map<String, String> values = new HashMap<>();

        if (section == null) {
            return values;
        }

        for (String key : section.getKeys(false)) {
            String value = cleanPlayerColor(section.getString(key, ""));

            if (!value.isEmpty()) {
                values.put(key.toLowerCase(Locale.ROOT), value);
            }
        }

        return values;
    }

    private String graveSelector(Grave grave) {
        return "@e[type=minecraft:armor_stand,tag=" + graveTag(grave) + ",limit=1]";
    }

    private static String graveTag(Grave grave) {
        return GRAVE_TAG_PREFIX + grave.getId().toString().replace("-", "");
    }

    private static String formatNumber(double value) {
        return String.format(Locale.US, "%.3f", value);
    }

    private static String cleanColor(String raw) {
        if (raw == null) {
            return "";
        }

        String value = raw.trim().toLowerCase(Locale.ROOT);

        if (value.matches("#[0-9a-f]{6}")) {
            return value;
        }

        if (value.matches("[0-9a-f]{6}")) {
            return value;
        }

        return value.matches("[a-z_]+") ? value : "";
    }

    private static String cleanPlayerColor(String raw) {
        String color = cleanColor(raw);
        return isReservedGraveColor(color) ? "" : color;
    }

    private static boolean isReservedGraveColor(String color) {
        if (color == null || color.isBlank()) {
            return false;
        }

        String normalized = color.trim().toLowerCase(Locale.ROOT);

        if (normalized.startsWith("#")) {
            normalized = normalized.substring(1);
        }

        return normalized.equals("gray")
                || normalized.equals("grey")
                || normalized.equals("dark_gray")
                || normalized.equals("dark_grey")
                || normalized.equals("gold")
                || normalized.equals("yellow")
                || normalized.equals("aaaaaa")
                || normalized.equals("555555")
                || normalized.equals("ffaa00")
                || normalized.equals("ffff55");
    }

    private static double clampDouble(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static long clampLong(long value, long min, long max) {
        return Math.max(min, Math.min(max, value));
    }

    private enum PlayerUuidHashStyle {
        SOLID,
        GRADIENT;

        static PlayerUuidHashStyle fromConfig(String raw) {
            if (raw == null || raw.isBlank()) {
                return SOLID;
            }

            String normalized = raw.trim()
                    .replace('-', '_')
                    .toUpperCase(Locale.ROOT);

            for (PlayerUuidHashStyle style : values()) {
                if (style.name().equals(normalized)) {
                    return style;
                }
            }

            return SOLID;
        }
    }

    private enum PlayerColorMode {
        VANILLA,
        FIXED,
        UUID_HASH;

        static PlayerColorMode fromConfig(String raw) {
            if (raw == null || raw.isBlank()) {
                return UUID_HASH;
            }

            String normalized = raw.trim()
                    .replace('-', '_')
                    .toUpperCase(Locale.ROOT);

            for (PlayerColorMode mode : values()) {
                if (mode.name().equals(normalized)) {
                    return mode;
                }
            }

            return UUID_HASH;
        }
    }
}
