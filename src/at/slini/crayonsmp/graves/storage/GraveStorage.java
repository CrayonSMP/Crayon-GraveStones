package at.slini.crayonsmp.graves.storage;

import at.slini.crayonsmp.graves.GravePlugin;
import at.slini.crayonsmp.graves.model.Grave;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

public class GraveStorage implements IGraveStorage {
    private final GravePlugin plugin;

    private final File file;

    private final Map<UUID, Grave> graves = new ConcurrentHashMap<>();

    public GraveStorage(GravePlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "graves.yml");
    }

    @Override
    public boolean isLimitedStorage() {
        return true;
    }

    public void load() {
        if (!this.file.exists()) {
            if (!this.plugin.getDataFolder().exists() && !this.plugin.getDataFolder().mkdirs()) {
                this.plugin.getLogger().severe("Failed to create plugin data folder: " + this.plugin.getDataFolder().getAbsolutePath());
                return;
            }
            try {
                if (!this.file.createNewFile()) {
                    this.plugin.getLogger().severe("Failed to create graves.yml (createNewFile returned false).");
                    return;
                }
            } catch (IOException e) {
                this.plugin.getLogger().severe("Failed to create graves.yml: " + e.getMessage());
                return;
            }
        }

        YamlConfiguration yml = YamlConfiguration.loadConfiguration(this.file);
        ConfigurationSection root = yml.getConfigurationSection("graves");

        if (root == null) {
            root = yml.getConfigurationSection("graves");
        }

        if (root == null) {
            boolean looksLikeRootUuids = false;
            for (String k : yml.getKeys(false)) {
                try {
                    UUID.fromString(k);
                    looksLikeRootUuids = true;
                    break;
                } catch (IllegalArgumentException ignored) {
                }
            }
            if (looksLikeRootUuids) {
                root = yml;
            }
        }

        if (root == null) {
            this.graves.clear();
            this.plugin.getLogger().info("Loaded 0 graves (no graves section/root UUID keys).");
            return;
        }
        this.graves.clear();

        for (String key : root.getKeys(false)) {
            ConfigurationSection g = root.getConfigurationSection(key);
            if (g == null) {
                continue;
            }

            UUID id;
            try {
                id = UUID.fromString(key);
            } catch (IllegalArgumentException ex) {
                this.plugin.getLogger().warning("Skipping grave with invalid UUID key: " + key);
                continue;
            }

            try {
                String ownerUuidStr = g.getString("ownerUuid");
                String worldUuidStr = g.getString("worldUuid");
                if (ownerUuidStr == null || ownerUuidStr.isBlank() || worldUuidStr == null || worldUuidStr.isBlank()) {
                    this.plugin.getLogger().warning("Skipping grave " + key + " (missing ownerUuid/worldUuid).");
                    continue;
                }

                UUID owner = UUID.fromString(ownerUuidStr);
                UUID world = UUID.fromString(worldUuidStr);

                String ownerName = g.getString("ownerName", "Unknown");

                int x = g.getInt("x");
                int y = g.getInt("y");
                int z = g.getInt("z");
                long createdAt = g.getLong("createdAt");
                int totalExp = g.getInt("totalExp");

                Map<Integer, ItemStack> slotItems = new HashMap<>();

                ConfigurationSection si = g.getConfigurationSection("slotItems");
                if (si != null) {
                    for (String slotKey : si.getKeys(false)) {
                        int slot;
                        try {
                            slot = Integer.parseInt(slotKey);
                        } catch (NumberFormatException ignored) {
                            continue;
                        }

                        ItemStack it = si.getItemStack(slotKey);
                        if (it != null) {
                            slotItems.put(slot, it);
                        }
                    }
                }

                if (slotItems.isEmpty()) {
                    List<?> legacyItemsRaw = g.getList("items");
                    if (legacyItemsRaw != null) {
                        int slot = 0;
                        for (Object o : legacyItemsRaw) {
                            if (!(o instanceof ItemStack it)) {
                                continue;
                            }
                            while (slotItems.containsKey(slot)) {
                                slot++;
                            }
                            slotItems.put(slot, it);
                            slot++;
                        }
                    }
                }

                ItemStack[] armor = new ItemStack[0];
                List<?> armorRaw = g.getList("armor");
                if (armorRaw != null && !armorRaw.isEmpty()) {
                    List<ItemStack> armorList = new java.util.ArrayList<>(armorRaw.size());
                    for (Object o : armorRaw) {
                        if (o instanceof ItemStack it) {
                            armorList.add(it);
                        }
                    }
                    armor = armorList.toArray(new ItemStack[0]);
                }

                ItemStack offHand = g.getItemStack("offHand");

                Grave grave = new Grave(id, owner, ownerName, world, x, y, z, createdAt, totalExp, slotItems, armor, offHand);

                String holo = g.getString("hologramEntityId");
                if (holo != null && !holo.isBlank()) {
                    try {
                        grave.setHologramEntityId(UUID.fromString(holo));
                    } catch (IllegalArgumentException ex) {
                        this.plugin.getLogger().warning("Invalid hologramEntityId for grave " + key + ": " + holo);
                    }
                }

                this.graves.put(id, grave);

            } catch (Exception ex) {
                this.plugin.getLogger().warning("Failed to load grave " + key + ": " + ex.getMessage());
            }
        }
    }


    public void save() {
        YamlConfiguration yml = new YamlConfiguration();
        ConfigurationSection root = yml.createSection("graves");
        for (Grave grave : this.graves.values()) {
            ConfigurationSection g = root.createSection(grave.getId().toString());
            g.set("ownerUuid", grave.getOwnerUuid().toString());
            g.set("ownerName", grave.getOwnerName());
            g.set("worldUuid", grave.getWorldUuid().toString());
            g.set("x", Integer.valueOf(grave.getX()));
            g.set("y", Integer.valueOf(grave.getY()));
            g.set("z", Integer.valueOf(grave.getZ()));
            g.set("createdAt", Long.valueOf(grave.getCreatedAtEpochMs()));
            g.set("totalExp", Integer.valueOf(grave.getTotalExp()));
            g.set("slotItems", null);
            ConfigurationSection si = g.createSection("slotItems");
            Map<Integer, ItemStack> slotItems = grave.getSlotItems();
            if (slotItems != null) for (Map.Entry<Integer, ItemStack> e : slotItems.entrySet())
                si.set(String.valueOf(e.getKey()), e.getValue());
            ItemStack[] armor = grave.getArmor();
            if (armor != null && armor.length > 0) {
                g.set("armor", Arrays.asList(armor));
            } else {
                g.set("armor", new ArrayList());
            }
            g.set("offHand", grave.getOffHand());
            if (grave.getHologramEntityId() != null) g.set("hologramEntityId", grave.getHologramEntityId().toString());
        }
        try {
            yml.save(this.file);
        } catch (IOException e) {
            this.plugin.getLogger().severe("Failed to save graves.yml: " + e.getMessage());
        }
    }

    public void saveAsync() {
        Bukkit.getScheduler().runTaskAsynchronously((Plugin) this.plugin, this::save);
    }

    public void put(Grave grave) {
        this.graves.put(grave.getId(), grave);
    }

    public Optional<Grave> get(UUID id) {
        return Optional.ofNullable(this.graves.get(id));
    }

    public Collection<Grave> getAll() {
        return Collections.unmodifiableCollection(this.graves.values());
    }

    public void remove(UUID id) {
        this.graves.remove(id);
    }

    public Optional<Grave> findByLocation(UUID worldUuid, int x, int y, int z) {
        for (Grave g : this.graves.values()) {
            if (g.getWorldUuid().equals(worldUuid) && g.getX() == x && g.getY() == y && g.getZ() == z)
                return Optional.of(g);
        }
        return Optional.empty();
    }
}
