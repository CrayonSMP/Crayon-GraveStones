package at.slini204.bgravestones.storage;

import at.slini204.bgravestones.GravePlugin;
import at.slini204.bgravestones.model.Grave;
import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class MySqlGraveStorage implements IGraveStorage {

    private final GravePlugin plugin;
    private final MySqlConfig cfg;
    private final Map<UUID, Grave> cache = new ConcurrentHashMap<>();

    private Connection connection;
    private BukkitTask keepAliveTask;

    public MySqlGraveStorage(GravePlugin plugin, MySqlConfig cfg) {
        this.plugin = plugin;
        this.cfg = cfg;
    }

    public synchronized void connect() throws SQLException {
        closeConnectionOnly();
        this.connection = DriverManager.getConnection(cfg.jdbcUrl(), cfg.username, cfg.password);
        ensureSchema();
        startKeepAliveTask();
    }

    public synchronized boolean isConnected() {
        try {
            return connection != null && !connection.isClosed() && connection.isValid(cfg.validationTimeoutSeconds);
        } catch (SQLException ignored) {
            return false;
        }
    }

    private synchronized Connection requireConnection() throws SQLException {
        if (!isConnected()) {
            plugin.debugMysql("MySQL connection is closed or invalid. Reconnecting...");
            connect();
        }
        return connection;
    }

    private String tableName(String base) {
        return cfg.table(base);
    }

    private void ensureSchema() throws SQLException {
        String table = tableName("graves");

        try (Statement st = requireConnection().createStatement()) {
            st.executeUpdate("CREATE TABLE IF NOT EXISTS " + table + " ("
                    + "id VARCHAR(36) PRIMARY KEY,"
                    + "owner_uuid VARCHAR(36) NOT NULL,"
                    + "owner_name VARCHAR(64) NOT NULL,"
                    + "world_uuid VARCHAR(36) NOT NULL,"
                    + "x INT NOT NULL,"
                    + "y INT NOT NULL,"
                    + "z INT NOT NULL,"
                    + "created_at BIGINT NOT NULL,"
                    + "total_exp INT NOT NULL,"
                    + "slot_items LONGBLOB NULL,"
                    + "armor LONGBLOB NULL,"
                    + "offhand BLOB NULL,"
                    + "hologram_entity_id VARCHAR(36) NULL,"
                    + "waypoint_entity_id VARCHAR(36) NULL"
                    + ")");

            addColumnIfMissing(st, table, "hologram_entity_id", "VARCHAR(36) NULL");
            addColumnIfMissing(st, table, "waypoint_entity_id", "VARCHAR(36) NULL");
            createIndexIfMissing(st, "idx_" + table + "_owner", table, "owner_uuid");
            createIndexIfMissing(st, "idx_" + table + "_world_xyz", table, "world_uuid,x,y,z");
        }
    }

    private void addColumnIfMissing(Statement st, String table, String column, String definition) {
        try {
            st.executeUpdate("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
        } catch (SQLException ignored) {
            // Column already exists.
        }
    }

    private void createIndexIfMissing(Statement st, String indexName, String table, String columns) {
        try {
            st.executeUpdate("CREATE INDEX " + indexName + " ON " + table + "(" + columns + ")");
        } catch (SQLException ignored) {
            // Index already exists.
        }
    }

    @Override
    public void load() {
        cache.clear();

        String table = tableName("graves");
        String sql = "SELECT id, owner_uuid, owner_name, world_uuid, x, y, z, created_at, total_exp, "
                + "slot_items, armor, offhand, hologram_entity_id, waypoint_entity_id FROM " + table;

        try (PreparedStatement ps = requireConnection().prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                Grave grave = readGrave(rs);
                cache.put(grave.getId(), grave);
            }
        } catch (Throwable t) {
            plugin.getLogger().severe("Failed to load graves from MySQL: " + t.getMessage());
        }
    }

    private Grave readGrave(ResultSet rs) throws Exception {
        UUID id = UUID.fromString(rs.getString("id"));
        UUID ownerUuid = UUID.fromString(rs.getString("owner_uuid"));
        String ownerName = rs.getString("owner_name");
        UUID worldUuid = UUID.fromString(rs.getString("world_uuid"));
        int x = rs.getInt("x");
        int y = rs.getInt("y");
        int z = rs.getInt("z");
        long createdAt = rs.getLong("created_at");
        int totalExp = rs.getInt("total_exp");

        Map<Integer, ItemStack> slotItems = SerializationUtil.fromBytes(rs.getBytes("slot_items"));
        ItemStack[] armor = SerializationUtil.fromBytes(rs.getBytes("armor"));
        ItemStack offhand = SerializationUtil.fromBytes(rs.getBytes("offhand"));

        Grave grave = new Grave(
                id,
                ownerUuid,
                ownerName,
                worldUuid,
                x,
                y,
                z,
                createdAt,
                totalExp,
                slotItems == null ? new HashMap<>() : slotItems,
                armor == null ? new ItemStack[0] : armor,
                offhand
        );

        grave.setHologramEntityId(parseUuid(rs.getString("hologram_entity_id")));
        grave.setWaypointEntityId(parseUuid(rs.getString("waypoint_entity_id")));
        return grave;
    }

    @Override
    public void save() {
        String table = tableName("graves");
        String sql = "INSERT INTO " + table + " "
                + "(id, owner_uuid, owner_name, world_uuid, x, y, z, created_at, total_exp, slot_items, armor, offhand, hologram_entity_id, waypoint_entity_id) "
                + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?) "
                + "ON DUPLICATE KEY UPDATE "
                + "owner_uuid=VALUES(owner_uuid), "
                + "owner_name=VALUES(owner_name), "
                + "world_uuid=VALUES(world_uuid), "
                + "x=VALUES(x), y=VALUES(y), z=VALUES(z), "
                + "created_at=VALUES(created_at), "
                + "total_exp=VALUES(total_exp), "
                + "slot_items=VALUES(slot_items), "
                + "armor=VALUES(armor), "
                + "offhand=VALUES(offhand), "
                + "hologram_entity_id=VALUES(hologram_entity_id), "
                + "waypoint_entity_id=VALUES(waypoint_entity_id)";

        try (PreparedStatement ps = requireConnection().prepareStatement(sql)) {
            for (Grave grave : new ArrayList<>(cache.values())) {
                bindGrave(ps, grave);
                ps.addBatch();
            }
            ps.executeBatch();
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to save graves to MySQL: " + e.getMessage());
        }
    }

    private void bindGrave(PreparedStatement ps, Grave g) throws Exception {
        ps.setString(1, g.getId().toString());
        ps.setString(2, g.getOwnerUuid().toString());
        ps.setString(3, g.getOwnerName());
        ps.setString(4, g.getWorldUuid().toString());
        ps.setInt(5, g.getX());
        ps.setInt(6, g.getY());
        ps.setInt(7, g.getZ());
        ps.setLong(8, g.getCreatedAtEpochMs());
        ps.setInt(9, g.getTotalExp());
        ps.setBytes(10, SerializationUtil.toBytes(g.getSlotItems()));
        ps.setBytes(11, SerializationUtil.toBytes(g.getArmor()));
        ps.setBytes(12, SerializationUtil.toBytes(g.getOffHand()));
        ps.setString(13, asString(g.getHologramEntityId()));
        ps.setString(14, asString(g.getWaypointEntityId()));
    }

    @Override
    public void saveAsync() {
        Bukkit.getScheduler().runTaskAsynchronously((Plugin) plugin, this::save);
    }

    @Override
    public void put(Grave grave) {
        if (grave == null || grave.getId() == null) return;
        cache.put(grave.getId(), grave);
    }

    @Override
    public void remove(UUID graveId) {
        if (graveId == null) return;
        cache.remove(graveId);

        String table = tableName("graves");
        Bukkit.getScheduler().runTaskAsynchronously((Plugin) plugin, () -> {
            try (PreparedStatement ps = requireConnection().prepareStatement("DELETE FROM " + table + " WHERE id=?")) {
                ps.setString(1, graveId.toString());
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().warning("Failed to delete grave from MySQL: " + e.getMessage());
            }
        });
    }

    @Override
    public Collection<Grave> getAll() {
        return Collections.unmodifiableCollection(new ArrayList<>(cache.values()));
    }

    @Override
    public Optional<Grave> findByLocation(UUID worldUuid, int x, int y, int z) {
        if (worldUuid == null) return Optional.empty();
        return cache.values().stream()
                .filter(g -> worldUuid.equals(g.getWorldUuid()) && g.getX() == x && g.getY() == y && g.getZ() == z)
                .findFirst();
    }

    public int importFrom(Collection<Grave> graves) {
        if (graves == null || graves.isEmpty()) return 0;
        graves.stream().filter(g -> g != null && g.getId() != null).forEach(g -> cache.put(g.getId(), g));
        save();
        return graves.size();
    }

    public int migrateFrom(GraveStorage yamlStorage) {
        if (yamlStorage == null) return 0;

        Collection<Grave> yamlGraves = yamlStorage.getAll();
        if (yamlGraves == null || yamlGraves.isEmpty()) return 0;

        int imported = 0;
        for (Grave grave : yamlGraves) {
            if (grave == null || grave.getId() == null || cache.containsKey(grave.getId())) continue;
            cache.put(grave.getId(), grave);
            imported++;
        }

        if (imported <= 0) return 0;

        try {
            save();
        } catch (Throwable t) {
            plugin.getLogger().warning("YAML->MySQL migration failed; YAML data was kept: " + t.getMessage());
            return 0;
        }

        try {
            for (Grave grave : yamlGraves) {
                if (grave == null || grave.getId() == null) continue;
                yamlStorage.remove(grave.getId());
            }
            yamlStorage.saveAsync();
        } catch (Throwable t) {
            plugin.getLogger().warning("Migrated to MySQL, but could not clean YAML storage: " + t.getMessage());
        }

        return imported;
    }

    private void startKeepAliveTask() {
        if (!cfg.keepAliveEnabled) return;
        if (keepAliveTask != null) keepAliveTask.cancel();

        long intervalTicks = Math.max(20L, cfg.keepAliveIntervalSeconds * 20L);
        keepAliveTask = Bukkit.getScheduler().runTaskTimerAsynchronously(
                (Plugin) plugin,
                this::keepAlive,
                intervalTicks,
                intervalTicks
        );
    }

    private void keepAlive() {
        try (PreparedStatement ps = requireConnection().prepareStatement("SELECT 1")) {
            ps.executeQuery();
            plugin.debugMysql("MySQL keepalive ok.");
        } catch (SQLException e) {
            plugin.getLogger().warning("MySQL keepalive failed: " + e.getMessage());
            try {
                connect();
            } catch (SQLException reconnectError) {
                plugin.getLogger().warning("MySQL reconnect failed: " + reconnectError.getMessage());
            }
        }
    }

    @Override
    public synchronized void close() {
        if (keepAliveTask != null) {
            keepAliveTask.cancel();
            keepAliveTask = null;
        }
        closeConnectionOnly();
    }

    private synchronized void closeConnectionOnly() {
        if (connection == null) return;
        try {
            connection.close();
        } catch (SQLException ignored) {
        } finally {
            connection = null;
        }
    }

    private static UUID parseUuid(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static String asString(UUID uuid) {
        return uuid == null ? null : uuid.toString();
    }
}
