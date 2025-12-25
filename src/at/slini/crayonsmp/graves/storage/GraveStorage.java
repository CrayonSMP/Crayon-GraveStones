package at.slini.crayonsmp.graves.storage;

import at.slini.crayonsmp.graves.GravePlugin;
import at.slini.crayonsmp.graves.model.Grave;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class GraveStorage {

    private final GravePlugin plugin;
    private final File file;

    private final Map<UUID, Grave> graves = new ConcurrentHashMap<>();

    public GraveStorage(GravePlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "graves.yml");
    }

    public void load() {
        if (!file.exists()) {
            plugin.getDataFolder().mkdirs();
            try {
                file.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Failed to create graves.yml: " + e.getMessage());
            }
        }

        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yml.getConfigurationSection("graves");
        if (root == null) return;

        graves.clear();

        for (String key : root.getKeys(false)) {
            try {
                UUID id = UUID.fromString(key);
                ConfigurationSection g = root.getConfigurationSection(key);
                if (g == null) continue;

                UUID owner = UUID.fromString(Objects.requireNonNull(g.getString("ownerUuid")));
                String ownerName = g.getString("ownerName", "Unknown");

                UUID world = UUID.fromString(Objects.requireNonNull(g.getString("worldUuid")));
                int x = g.getInt("x");
                int y = g.getInt("y");
                int z = g.getInt("z");

                long createdAt = g.getLong("createdAt");
                int totalExp = g.getInt("totalExp");

                // slotItems.<slot> = ItemStack
                Map<Integer, ItemStack> slotItems = new HashMap<>();
                ConfigurationSection si = g.getConfigurationSection("slotItems");
                if (si != null) {
                    for (String slotKey : si.getKeys(false)) {
                        try {
                            int slot = Integer.parseInt(slotKey);
                            ItemStack it = si.getItemStack(slotKey);
                            if (it != null) {
                                slotItems.put(slot, it);
                            }
                        } catch (NumberFormatException ignored) {
                        }
                    }
                }

                if (slotItems.isEmpty()) {
                    @SuppressWarnings("unchecked")
                    List<ItemStack> legacyItems = (List<ItemStack>) g.getList("items");
                    if (legacyItems != null) {
                        int slot = 0;
                        for (ItemStack it : legacyItems) {
                            if (it == null) continue;
                            while (slotItems.containsKey(slot)) slot++;
                            slotItems.put(slot, it);
                            slot++;
                        }
                    }
                }

                // Armor: saved as list -> array
                @SuppressWarnings("unchecked")
                List<ItemStack> armorList = (List<ItemStack>) g.getList("armor");
                ItemStack[] armor = armorList == null ? new ItemStack[0] : armorList.toArray(new ItemStack[0]);

                // Offhand
                ItemStack offHand = g.getItemStack("offHand");

                Grave grave = new Grave(
                        id, owner, ownerName, world,
                        x, y, z, createdAt, totalExp,
                        slotItems,
                        armor,
                        offHand
                );

                String holo = g.getString("hologramEntityId", null);
                if (holo != null && !holo.isBlank()) {
                    grave.setHologramEntityId(UUID.fromString(holo));
                }

                graves.put(id, grave);
            } catch (Exception ex) {
                plugin.getLogger().warning("Failed to load grave " + key + ": " + ex.getMessage());
            }
        }

        plugin.getLogger().info("Loaded " + graves.size() + " graves.");
    }

    public void save() {
        YamlConfiguration yml = new YamlConfiguration();
        ConfigurationSection root = yml.createSection("graves");

        for (Grave grave : graves.values()) {
            ConfigurationSection g = root.createSection(grave.getId().toString());
            g.set("ownerUuid", grave.getOwnerUuid().toString());
            g.set("ownerName", grave.getOwnerName());
            g.set("worldUuid", grave.getWorldUuid().toString());
            g.set("x", grave.getX());
            g.set("y", grave.getY());
            g.set("z", grave.getZ());
            g.set("createdAt", grave.getCreatedAtEpochMs());
            g.set("totalExp", grave.getTotalExp());

            g.set("slotItems", null);
            ConfigurationSection si = g.createSection("slotItems");
            Map<Integer, ItemStack> slotItems = grave.getSlotItems();
            if (slotItems != null) {
                for (Map.Entry<Integer, ItemStack> e : slotItems.entrySet()) {
                    si.set(String.valueOf(e.getKey()), e.getValue());
                }
            }

            ItemStack[] armor = grave.getArmor();
            if (armor != null && armor.length > 0) {
                g.set("armor", Arrays.asList(armor));
            } else {
                g.set("armor", new ArrayList<>());
            }

            g.set("offHand", grave.getOffHand());

            if (grave.getHologramEntityId() != null) {
                g.set("hologramEntityId", grave.getHologramEntityId().toString());
            }
        }

        try {
            yml.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save graves.yml: " + e.getMessage());
        }
    }

    public void saveAsync() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, this::save);
    }

    public void put(Grave grave) {
        graves.put(grave.getId(), grave);
    }

    public Optional<Grave> get(UUID id) {
        return Optional.ofNullable(graves.get(id));
    }

    public Collection<Grave> getAll() {
        return Collections.unmodifiableCollection(graves.values());
    }

    public void remove(UUID id) {
        graves.remove(id);
    }

    public Optional<Grave> findByLocation(UUID worldUuid, int x, int y, int z) {
        for (Grave g : graves.values()) {
            if (g.getWorldUuid().equals(worldUuid) && g.getX() == x && g.getY() == y && g.getZ() == z) {
                return Optional.of(g);
            }
        }
        return Optional.empty();
    }
}
