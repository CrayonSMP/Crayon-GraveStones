package at.slini.crayonsmp.graves.storage;

import at.slini.crayonsmp.graves.GravePlugin;
import at.slini.crayonsmp.graves.model.Grave;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

public class MySqlGraveStorage implements IGraveStorage {
    private static Connection conn;
    private final GravePlugin plugin;
    private final MySqlConfig cfg;
    private final Map<UUID, Grave> cache = new HashMap<>();

    public MySqlGraveStorage(GravePlugin plugin, MySqlConfig cfg) {
        this.plugin = plugin;
        this.cfg = cfg;
    }

    public static void close() {
        try {
            if (conn != null) conn.close();
        } catch (SQLException ignored) {
        }
    }

    private String tn(String base) {
        if (this.cfg != null && this.cfg.useTablePrefix) {
            String p = (this.cfg.tablePrefix == null) ? "" : this.cfg.tablePrefix.trim();
            if (!p.isEmpty()) {
                return p + base;
            }
        }
        return base;
    }

    public void connect() throws SQLException {
        String url = "jdbc:mysql://" + this.cfg.host + ":" + this.cfg.port + "/" + this.cfg.database + "?useUnicode=true&characterEncoding=utf8&useSSL=" + this.cfg.useSSL + "&serverTimezone=UTC";
        conn = DriverManager.getConnection(url, this.cfg.username, this.cfg.password);
        ensureSchema();
    }

    public boolean isConnected() {
        try {
            return (conn != null && !conn.isClosed());
        } catch (SQLException e) {
            return false;
        }
    }

    private void ensureSchema() throws SQLException {
        String table = tn("graves");

        try (Statement st = conn.createStatement()) {
            st.executeUpdate("CREATE TABLE IF NOT EXISTS " + table + " (" + "id VARCHAR(36) PRIMARY KEY," + "owner_uuid VARCHAR(36) NOT NULL," + "owner_name VARCHAR(64) NOT NULL," + "world_uuid VARCHAR(36) NOT NULL," + "x INT NOT NULL," + "y INT NOT NULL," + "z INT NOT NULL," + "created_at BIGINT NOT NULL," + "total_exp INT NOT NULL," + "slot_items LONGBLOB NULL," + "armor LONGBLOB NULL," + "offhand BLOB NULL," + "hologram_entity_id VARCHAR(36) NULL" + ")");

            try {
                st.executeUpdate("ALTER TABLE " + table + " ADD COLUMN hologram_entity_id VARCHAR(36) NULL");
            } catch (SQLException ignored) {
                // column already exists
            }

            String idxOwner = "idx_" + table + "_owner";
            String idxWorld = "idx_" + table + "_world_xyz";

            try {
                st.executeUpdate("CREATE INDEX " + idxOwner + " ON " + table + "(owner_uuid)");
            } catch (SQLException ignored) {
            }

            try {
                st.executeUpdate("CREATE INDEX " + idxWorld + " ON " + table + "(world_uuid,x,y,z)");
            } catch (SQLException ignored) {
            }
        }
    }

    public void load() {
        this.cache.clear();
        if (!isConnected()) return;

        String table = tn("graves");
        String sql = "SELECT id, owner_uuid, owner_name, world_uuid, x, y, z, created_at, total_exp, slot_items, armor, offhand, hologram_entity_id FROM " + table;

        try (PreparedStatement ps = conn.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                UUID id = UUID.fromString(rs.getString("id"));
                UUID ownerUuid = UUID.fromString(rs.getString("owner_uuid"));
                String ownerName = rs.getString("owner_name");
                UUID worldUuid = UUID.fromString(rs.getString("world_uuid"));
                int x = rs.getInt("x");
                int y = rs.getInt("y");
                int z = rs.getInt("z");
                long createdAt = rs.getLong("created_at");
                int totalExp = rs.getInt("total_exp");

                Map<Integer, ItemStack> slotItems = SerializationUtil.<Map<Integer, ItemStack>>fromBytes(rs.getBytes("slot_items"));
                ItemStack[] armor = SerializationUtil.<ItemStack[]>fromBytes(rs.getBytes("armor"));
                ItemStack offhand = SerializationUtil.<ItemStack>fromBytes(rs.getBytes("offhand"));

                if (slotItems == null) slotItems = new HashMap<>();
                if (armor == null) armor = new ItemStack[0];

                Grave g = new Grave(id, ownerUuid, ownerName, worldUuid, x, y, z, createdAt, totalExp, slotItems, armor, offhand);
                String holo = rs.getString("hologram_entity_id");
                if (holo != null && !holo.isEmpty()) {
                    try {
                        g.setHologramEntityId(UUID.fromString(holo));
                    } catch (IllegalArgumentException ignored) {
                        // If old/bad data exists, ignore it safely
                    }
                }
                this.cache.put(id, g);
            }
        } catch (Throwable t) {
            this.plugin.getLogger().severe("[bGraveStones] Failed to load graves from MySQL: " + t.getMessage());
        }
    }

    public void save() {
        if (!isConnected()) return;

        String table = tn("graves");

        String sql = "INSERT INTO " + table + " " + "(id, owner_uuid, owner_name, world_uuid, x, y, z, created_at, total_exp, slot_items, armor, offhand, hologram_entity_id) " + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?) " + "ON DUPLICATE KEY UPDATE " + "owner_uuid=VALUES(owner_uuid), " + "owner_name=VALUES(owner_name), " + "world_uuid=VALUES(world_uuid), " + "x=VALUES(x), y=VALUES(y), z=VALUES(z), " + "created_at=VALUES(created_at), " + "total_exp=VALUES(total_exp), " + "slot_items=VALUES(slot_items), " + "armor=VALUES(armor), " + "offhand=VALUES(offhand), " + "hologram_entity_id=VALUES(hologram_entity_id)";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (Grave g : this.cache.values()) {
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

                UUID holo = g.getHologramEntityId();
                ps.setString(13, holo == null ? null : holo.toString());

                ps.addBatch();
            }
            ps.executeBatch();
        } catch (Exception e) {
            this.plugin.getLogger().severe("[bGraveStones] Failed to save graves to MySQL: " + e.getMessage());
        }
    }


    public void saveAsync() {
        Bukkit.getScheduler().runTaskAsynchronously((Plugin) this.plugin, this::save);
    }

    public void put(Grave grave) {
        if (grave == null) return;
        this.cache.put(grave.getId(), grave);
    }

    public void remove(UUID graveId) {
        if (graveId == null) return;

        this.cache.remove(graveId);
        if (!isConnected()) return;

        String table = tn("graves");
        Bukkit.getScheduler().runTaskAsynchronously((Plugin) this.plugin, () -> {
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM " + table + " WHERE id=?")) {
                ps.setString(1, graveId.toString());
                ps.executeUpdate();
            } catch (SQLException e) {
                this.plugin.getLogger().warning("[bGraveStones] Failed to delete grave from MySQL: " + e.getMessage());
            }
        });
    }

    public Collection<Grave> getAll() {
        return this.cache.values();
    }

    public Optional<Grave> findByLocation(UUID worldUuid, int x, int y, int z) {
        if (worldUuid == null) return Optional.empty();
        return this.cache.values().stream().filter(g -> (worldUuid.equals(g.getWorldUuid()) && g.getX() == x && g.getY() == y && g.getZ() == z)).findFirst();
    }

    public int importFrom(Collection<Grave> graves) {
        if (graves == null || graves.isEmpty()) return 0;

        int imported = 0;
        for (Grave g : graves) {
            this.cache.put(g.getId(), g);
            imported++;
        }
        save();
        return imported;
    }

    public int migrateFrom(GraveStorage yamlStorage) {
        if (yamlStorage == null) return 0;

        Collection<Grave> yamlGraves = yamlStorage.getAll();
        if (yamlGraves == null || yamlGraves.isEmpty()) return 0;

        int imported = 0;
        for (Grave g : yamlGraves) {
            if (g == null || g.getId() == null || this.cache.containsKey(g.getId())) continue;
            this.cache.put(g.getId(), g);
            imported++;
        }

        if (imported <= 0) return 0;

        try {
            save();
        } catch (Throwable t) {
            this.plugin.getLogger().warning("[bGraveStones] YAML->MySQL migration failed (kept YAML data): " + t.getMessage());
            return 0;
        }

        try {
            // keep current cleaning logic for now; later will replaced with "clear to Graves: {}"
            for (Grave g : yamlGraves) {
                if (g == null || g.getId() == null) continue;
                yamlStorage.remove(g.getId());
            }
            yamlStorage.saveAsync();
        } catch (Throwable t) {
            this.plugin.getLogger().warning("[bGraveStones] Migrated to MySQL, but could not clean YAML storage: " + t.getMessage());
        }

        return imported;
    }
}
