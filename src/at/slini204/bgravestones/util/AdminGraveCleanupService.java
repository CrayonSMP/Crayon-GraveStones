package at.slini204.bgravestones.util;

import at.slini204.bgravestones.GravePlugin;
import at.slini204.bgravestones.model.Grave;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class AdminGraveCleanupService {

    private static final String TAG_GRAVE_PREFIX = "bgs_grave_";
    private static final String TAG_GRAVE_WAYPOINT_MARKER = "bgs_grave_waypoint_marker";
    private static final String TAG_GRAVE_GLOW_DISPLAY = "bgs_grave_glow_display";

    private final GravePlugin plugin;
    private final NamespacedKey hologramGraveIdKey;
    private final NamespacedKey waypointGraveIdKey;
    private final NamespacedKey glowDisplayGraveIdKey;

    public AdminGraveCleanupService(GravePlugin plugin) {
        this.plugin = plugin;
        this.hologramGraveIdKey = new NamespacedKey((Plugin) plugin, "grave_holo_grave_id");
        this.waypointGraveIdKey = new NamespacedKey((Plugin) plugin, "grave_waypoint_grave_id");
        this.glowDisplayGraveIdKey = new NamespacedKey((Plugin) plugin, "grave_glow_display_grave_id");
    }

    public void inspectNearby(Player player, int radius) {
        if (player == null) {
            return;
        }

        int effectiveRadius = clampRadius(radius);
        List<ManagedEntityInfo> found = findManagedEntitiesNear(player, effectiveRadius);
        int maxResults = getMaxInspectResults();

        send(player, "&8[&6bGraveStones&8] &7Found &f" + found.size() + "&7 plugin-owned visual entit(y/ies) within &f" + effectiveRadius + "&7 blocks.");

        if (found.isEmpty()) {
            return;
        }

        int shown = 0;
        for (ManagedEntityInfo info : found) {
            if (shown >= maxResults) {
                send(player, "&8[&6bGraveStones&8] &7...and &f" + (found.size() - shown) + "&7 more. Use a smaller radius if needed.");
                break;
            }

            send(player, "&8- &e" + info.kind()
                    + " &7type=&f" + info.entity().getType()
                    + " &7grave=&f" + shortId(info.graveId())
                    + " &7stored=&f" + info.hasStoredGrave()
                    + " &7dist=&f" + String.format(Locale.US, "%.1f", info.distance())
                    + " &7at=&f" + formatLocation(info.entity().getLocation())
            );
            shown++;
        }
    }

    public void cleanupNearby(Player player, int radius) {
        if (player == null) {
            return;
        }

        int effectiveRadius = clampRadius(radius);
        List<ManagedEntityInfo> found = findManagedEntitiesNear(player, effectiveRadius);
        int removed = removeEntities(found);

        resyncVisuals();

        send(player, "&8[&6bGraveStones&8] &aRemoved &f" + removed + "&a nearby plugin-owned visual entit(y/ies) within &f" + effectiveRadius + "&a blocks and resynced visuals.");
        plugin.getLogger().warning("[bGraveStones] " + player.getName() + " used sudo cleanup-nearby and removed " + removed + " visual entities within " + effectiveRadius + " blocks.");
    }

    public void cleanupOrphans(CommandSender sender) {
        List<ManagedEntityInfo> orphans = new ArrayList<>();

        for (World world : Bukkit.getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                for (Entity entity : chunk.getEntities()) {
                    ManagedEntityInfo info = inspectManagedEntity(entity, null);
                    if (info != null && !info.hasStoredGrave()) {
                        orphans.add(info);
                    }
                }
            }
        }

        int removed = removeEntities(orphans);
        resyncVisuals();

        send(sender, "&8[&6bGraveStones&8] &aRemoved &f" + removed + "&a orphaned plugin-owned visual entit(y/ies) from loaded chunks and resynced visuals.");
        plugin.getLogger().warning("[bGraveStones] " + sender.getName() + " used sudo cleanup-orphans and removed " + removed + " orphaned visual entities from loaded chunks.");
    }

    public void resyncVisuals(CommandSender sender) {
        resyncVisuals();
        send(sender, "&8[&6bGraveStones&8] &aGrave blocks, holograms and locator waypoints were resynced.");
    }

    private void resyncVisuals() {
        if (plugin.getGraveManager() != null) {
            plugin.getGraveManager().restoreMissingGraveBlocks();
            plugin.getGraveManager().refreshAllHolograms();
            plugin.getGraveManager().bootstrapVisuals();
        }

        if (plugin.getLocatorBarManager() != null) {
            plugin.getLocatorBarManager().syncAllGraveWaypoints();
        }
    }

    private List<ManagedEntityInfo> findManagedEntitiesNear(Player player, int radius) {
        List<ManagedEntityInfo> result = new ArrayList<>();
        Location base = player.getLocation();
        double maxDistanceSquared = radius * (double) radius;

        for (Entity entity : player.getNearbyEntities(radius, radius, radius)) {
            if (entity == null || entity.isDead()) {
                continue;
            }

            Location location = entity.getLocation();
            if (location.getWorld() == null || !location.getWorld().equals(base.getWorld())) {
                continue;
            }

            double distanceSquared = location.distanceSquared(base);
            if (distanceSquared > maxDistanceSquared) {
                continue;
            }

            ManagedEntityInfo info = inspectManagedEntity(entity, Math.sqrt(distanceSquared));
            if (info != null) {
                result.add(info);
            }
        }

        return result;
    }

    private ManagedEntityInfo inspectManagedEntity(Entity entity, Double distance) {
        if (entity == null) {
            return null;
        }

        String graveId = linkedGraveId(entity);
        boolean tagOwned = hasPluginScoreboardTag(entity);
        String kind = kindOf(entity, graveId, tagOwned);

        if (kind == null) {
            return null;
        }

        return new ManagedEntityInfo(entity, kind, graveId, graveExists(graveId), distance == null ? 0.0D : distance);
    }

    private String linkedGraveId(Entity entity) {
        PersistentDataContainer pdc = entity.getPersistentDataContainer();

        String graveId = pdc.get(hologramGraveIdKey, PersistentDataType.STRING);
        if (graveId != null && !graveId.isBlank()) {
            return graveId;
        }

        graveId = pdc.get(waypointGraveIdKey, PersistentDataType.STRING);
        if (graveId != null && !graveId.isBlank()) {
            return graveId;
        }

        graveId = pdc.get(glowDisplayGraveIdKey, PersistentDataType.STRING);
        if (graveId != null && !graveId.isBlank()) {
            return graveId;
        }

        for (String tag : entity.getScoreboardTags()) {
            if (tag != null && tag.startsWith(TAG_GRAVE_PREFIX) && tag.length() > TAG_GRAVE_PREFIX.length()) {
                String compact = tag.substring(TAG_GRAVE_PREFIX.length());
                if (compact.matches("(?i)[0-9a-f]{32}")) {
                    return compact.substring(0, 8)
                            + "-" + compact.substring(8, 12)
                            + "-" + compact.substring(12, 16)
                            + "-" + compact.substring(16, 20)
                            + "-" + compact.substring(20);
                }
            }
        }

        return null;
    }

    private boolean hasPluginScoreboardTag(Entity entity) {
        for (String tag : entity.getScoreboardTags()) {
            if (TAG_GRAVE_WAYPOINT_MARKER.equals(tag) || TAG_GRAVE_GLOW_DISPLAY.equals(tag)) {
                return true;
            }

            if (tag != null && tag.startsWith(TAG_GRAVE_PREFIX)) {
                return true;
            }
        }
        return false;
    }

    private String kindOf(Entity entity, String graveId, boolean tagOwned) {
        if (entity instanceof TextDisplay && graveId != null) {
            return "hologram";
        }

        if (entity instanceof ArmorStand && (graveId != null || tagOwned)) {
            return "locator-marker";
        }

        if (entity instanceof BlockDisplay && (graveId != null || tagOwned)) {
            return "locator-glow";
        }

        if (tagOwned) {
            return "plugin-visual";
        }

        return null;
    }

    private boolean graveExists(String graveId) {
        if (graveId == null || graveId.isBlank()) {
            return false;
        }

        UUID uuid;
        try {
            uuid = UUID.fromString(graveId);
        } catch (IllegalArgumentException ignored) {
            return false;
        }

        Collection<Grave> graves = plugin.getGraveStorage() == null ? List.of() : plugin.getGraveStorage().getAll();
        if (graves == null) {
            return false;
        }

        for (Grave grave : graves) {
            if (grave != null && uuid.equals(grave.getId())) {
                return true;
            }
        }

        return false;
    }

    private int removeEntities(List<ManagedEntityInfo> infos) {
        int removed = 0;
        for (ManagedEntityInfo info : infos) {
            Entity entity = info.entity();
            if (entity == null || entity.isDead()) {
                continue;
            }

            entity.remove();
            removed++;
        }
        return removed;
    }

    private int clampRadius(int radius) {
        int defaultRadius = plugin.getConfig().getInt("adminSudo.defaultRadius", 8);
        int maxRadius = plugin.getConfig().getInt("adminSudo.maxRadius", 64);

        if (defaultRadius < 1) {
            defaultRadius = 8;
        }
        if (maxRadius < defaultRadius) {
            maxRadius = defaultRadius;
        }

        if (radius <= 0) {
            radius = defaultRadius;
        }

        return Math.max(1, Math.min(maxRadius, radius));
    }

    private int getMaxInspectResults() {
        int max = plugin.getConfig().getInt("adminSudo.maxInspectResults", 20);
        return Math.max(1, Math.min(100, max));
    }

    private String shortId(String graveId) {
        if (graveId == null || graveId.isBlank()) {
            return "none";
        }
        return graveId.length() <= 8 ? graveId : graveId.substring(0, 8);
    }

    private String formatLocation(Location location) {
        if (location == null || location.getWorld() == null) {
            return "unknown";
        }
        return location.getWorld().getName()
                + " " + location.getBlockX()
                + " " + location.getBlockY()
                + " " + location.getBlockZ();
    }

    private void send(CommandSender sender, String message) {
        if (sender == null || message == null) {
            return;
        }
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
    }

    private record ManagedEntityInfo(Entity entity, String kind, String graveId, boolean hasStoredGrave, double distance) {
    }
}
