package at.slini204.bgravestones.util;

import at.slini204.bgravestones.GraveManager;
import at.slini204.bgravestones.GravePlugin;
import at.slini204.bgravestones.model.Grave;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class CraftEngineGraveBlockSupport {

    private static final String CRAFT_ENGINE_BLOCKS_CLASS = "net.momirealms.craftengine.bukkit.api.CraftEngineBlocks";
    private static final String CRAFT_ENGINE_FURNITURE_CLASS = "net.momirealms.craftengine.bukkit.api.CraftEngineFurniture";
    private static final String CRAFT_ENGINE_KEY_CLASS = "net.momirealms.craftengine.core.util.Key";
    private static final String CRAFT_ENGINE_FURNITURE_BASE_CLASS = "net.momirealms.craftengine.core.entity.furniture.Furniture";

    private final GravePlugin plugin;
    private final GraveManager graveManager;
    private final NamespacedKey furnitureGraveIdKey;
    private final Listener dynamicListener = new Listener() { };

    private boolean enabled;
    private boolean logWarnings;
    private String mode;
    private String configuredId;
    private String normalizedConfiguredId;
    private int retryAfterTicks;

    private boolean apiAvailable;
    private boolean eventsRegistered;
    private int retryAttempts;

    private Class<?> keyClass;
    private Object configuredKey;
    private Method keyAsStringMethod;

    private Method blockPlaceMethod;
    private Method blockRemoveMethod;
    private Method blockIsCustomBlockMethod;
    private Method blockGetStateMethod;
    private Method blockStateOwnerMethod;
    private Method holderValueMethod;
    private Method blockDefinitionIdMethod;

    private Method furnitureByIdMethod;
    private Method furniturePlaceMethod;
    private Method furnitureRemoveMethod;
    private Method furnitureGetByMetaEntityMethod;
    private Method furnitureGetBySeatMethod;
    private Method furnitureGetByColliderMethod;
    private Method furnitureIdMethod;
    private Method furnitureLocationMethod;
    private Method furnitureBukkitEntityMethod;

    private boolean warnedUnavailable;
    private boolean warnedMissingBlock;
    private boolean warnedPlaceFailed;
    private boolean warnedRemoveFailed;
    private boolean warnedInspectFailed;
    private boolean warnedDelayedRetry;
    private boolean warnedEventsFailed;

    public CraftEngineGraveBlockSupport(GravePlugin plugin, GraveManager graveManager) {
        this.plugin = plugin;
        this.graveManager = graveManager;
        this.furnitureGraveIdKey = new NamespacedKey((Plugin) plugin, "craftengine_furniture_grave_id");
    }

    public void reload() {
        this.enabled = plugin.getConfig().getBoolean("craftEngine.enabled", false);
        this.logWarnings = plugin.getConfig().getBoolean("craftEngine.logFallbackWarnings", true);
        this.mode = plugin.getConfig().getString("craftEngine.mode", "furniture");
        if (this.mode == null || this.mode.isBlank()) {
            this.mode = "furniture";
        }
        this.mode = this.mode.trim().toLowerCase(Locale.ROOT);

        this.configuredId = plugin.getConfig().getString("craftEngine.furniture", "");
        if (this.configuredId == null || this.configuredId.isBlank()) {
            this.configuredId = plugin.getConfig().getString("craftEngine.graveBlock", "");
        }
        if (this.configuredId == null) {
            this.configuredId = "";
        }
        this.configuredId = this.configuredId.trim();
        this.retryAfterTicks = Math.max(0, plugin.getConfig().getInt("craftEngine.retryAfterTicks", 80));

        this.normalizedConfiguredId = "";
        this.apiAvailable = false;
        this.configuredKey = null;
        this.keyClass = null;
        this.keyAsStringMethod = null;
        this.blockPlaceMethod = null;
        this.blockRemoveMethod = null;
        this.blockIsCustomBlockMethod = null;
        this.blockGetStateMethod = null;
        this.blockStateOwnerMethod = null;
        this.holderValueMethod = null;
        this.blockDefinitionIdMethod = null;
        this.furnitureByIdMethod = null;
        this.furniturePlaceMethod = null;
        this.furnitureRemoveMethod = null;
        this.furnitureGetByMetaEntityMethod = null;
        this.furnitureGetBySeatMethod = null;
        this.furnitureGetByColliderMethod = null;
        this.furnitureIdMethod = null;
        this.furnitureLocationMethod = null;
        this.furnitureBukkitEntityMethod = null;
        this.warnedUnavailable = false;
        this.warnedMissingBlock = false;
        this.warnedPlaceFailed = false;
        this.warnedRemoveFailed = false;
        this.warnedInspectFailed = false;
        this.warnedDelayedRetry = false;
        this.warnedEventsFailed = false;

        if (!this.enabled || this.configuredId.isBlank()) {
            return;
        }

        if (!Bukkit.getPluginManager().isPluginEnabled("CraftEngine")) {
            warnUnavailable(new IllegalStateException("CraftEngine plugin is not enabled yet"));
            scheduleDelayedRefresh();
            return;
        }

        try {
            this.keyClass = Class.forName(CRAFT_ENGINE_KEY_CLASS);
            Method keyFromMethod = this.keyClass.getMethod("from", String.class);
            this.configuredKey = keyFromMethod.invoke(null, this.configuredId);
            this.keyAsStringMethod = this.keyClass.getMethod("asString");
            this.normalizedConfiguredId = String.valueOf(this.keyAsStringMethod.invoke(this.configuredKey));

            if (isFurnitureMode()) {
                loadFurnitureApi();
            } else {
                loadBlockApi();
            }

            this.apiAvailable = true;
            this.retryAttempts = 0;
            registerCraftEngineEvents();
            plugin.getLogger().info("[bGraveStones] CraftEngine grave visual enabled (" + this.mode + "): " + this.normalizedConfiguredId + ".");
        } catch (MissingDefinitionException ex) {
            warnMissingBlock();
            scheduleDelayedRefresh();
        } catch (Throwable ex) {
            this.apiAvailable = false;
            warnUnavailable(ex);
            scheduleDelayedRefresh();
        }
    }

    private boolean isFurnitureMode() {
        return this.mode.equals("furniture") || this.mode.equals("furnitures");
    }

    private void loadFurnitureApi() throws ReflectiveOperationException, MissingDefinitionException {
        Class<?> furnitureApiClass = Class.forName(CRAFT_ENGINE_FURNITURE_CLASS);
        Class<?> furnitureBaseClass = Class.forName(CRAFT_ENGINE_FURNITURE_BASE_CLASS);

        this.furnitureByIdMethod = furnitureApiClass.getMethod("byId", this.keyClass);
        Object definition = this.furnitureByIdMethod.invoke(null, this.configuredKey);
        if (definition == null) {
            throw new MissingDefinitionException();
        }

        this.furniturePlaceMethod = furnitureApiClass.getMethod("place", Location.class, this.keyClass);
        this.furnitureRemoveMethod = furnitureApiClass.getMethod("remove", furnitureBaseClass, boolean.class, boolean.class);
        this.furnitureGetByMetaEntityMethod = furnitureApiClass.getMethod("getLoadedFurnitureByMetaEntity", Entity.class);
        this.furnitureGetBySeatMethod = furnitureApiClass.getMethod("getLoadedFurnitureBySeat", Entity.class);
        this.furnitureGetByColliderMethod = furnitureApiClass.getMethod("getLoadedFurnitureByCollider", Entity.class);
        this.furnitureIdMethod = furnitureBaseClass.getMethod("id");

        Class<?> bukkitFurnitureClass = Class.forName("net.momirealms.craftengine.bukkit.entity.furniture.BukkitFurniture");
        this.furnitureLocationMethod = bukkitFurnitureClass.getMethod("location");
        this.furnitureBukkitEntityMethod = bukkitFurnitureClass.getMethod("bukkitEntity");
    }

    private void loadBlockApi() throws ReflectiveOperationException, MissingDefinitionException {
        Class<?> blocksClass = Class.forName(CRAFT_ENGINE_BLOCKS_CLASS);
        this.blockPlaceMethod = blocksClass.getMethod("place", Location.class, this.keyClass, boolean.class);
        this.blockRemoveMethod = blocksClass.getMethod("remove", Block.class, boolean.class);
        this.blockIsCustomBlockMethod = blocksClass.getMethod("isCustomBlock", Block.class);
        this.blockGetStateMethod = blocksClass.getMethod("getCustomBlockState", Block.class);

        Object definition = blocksClass.getMethod("byId", this.keyClass).invoke(null, this.configuredKey);
        if (definition == null) {
            throw new MissingDefinitionException();
        }

        Class<?> stateClass = Class.forName("net.momirealms.craftengine.core.block.ImmutableBlockState");
        Class<?> holderClass = Class.forName("net.momirealms.craftengine.core.registry.Holder");
        Class<?> blockDefinitionClass = Class.forName("net.momirealms.craftengine.core.block.BlockDefinition");

        this.blockStateOwnerMethod = stateClass.getMethod("owner");
        this.holderValueMethod = holderClass.getMethod("value");
        this.blockDefinitionIdMethod = blockDefinitionClass.getMethod("id");
    }

    public boolean canUseCustomVisual() {
        return this.enabled && this.apiAvailable && this.configuredKey != null && !this.normalizedConfiguredId.isBlank();
    }

    public boolean shouldSuppressVanillaGlowDisplay() {
        return canUseCustomVisual()
                && isFurnitureMode()
                && plugin.getConfig().getBoolean("craftEngine.suppressVanillaGlowDisplay", true);
    }

    public boolean placeCustomGraveVisual(Grave grave, Block block) {
        if (grave == null || block == null || !canUseCustomVisual()) {
            return false;
        }

        return isFurnitureMode()
                ? placeFurnitureGrave(grave, block)
                : placeCustomBlockGrave(block);
    }

    public boolean removeCustomGraveVisual(Grave grave, Block block) {
        if (block == null || !this.enabled) {
            return false;
        }

        boolean removed = false;
        if (grave != null && isFurnitureMode()) {
            removed = removeFurnitureGrave(grave, block);
        }

        if (removeCustomBlock(block)) {
            removed = true;
        }

        return removed;
    }

    public boolean isConfiguredCustomGraveVisual(Grave grave, Block block) {
        if (block == null || !canUseCustomVisual()) {
            return false;
        }

        if (isFurnitureMode()) {
            return hasFurnitureGrave(grave, block);
        }

        return isConfiguredCustomBlock(block);
    }

    public boolean isAnyCustomVisualBlock(Block block) {
        return isAnyCustomBlock(block);
    }

    public boolean isManagedFurnitureEntity(UUID entityId) {
        if (entityId == null || !this.enabled || !isFurnitureMode()) {
            return false;
        }

        Entity entity = Bukkit.getEntity(entityId);
        if (entity == null) {
            return false;
        }

        Object furniture = resolveFurniture(entity);
        if (furniture == null) {
            return false;
        }

        for (Grave grave : this.graveManager.getAllGraves()) {
            World world = Bukkit.getWorld(grave.getWorldUuid());
            if (world == null) {
                continue;
            }

            Block block = world.getBlockAt(grave.getX(), grave.getY(), grave.getZ());
            if (matchesConfiguredFurniture(furniture, grave, block)) {
                return true;
            }
        }

        return false;
    }

    private boolean placeFurnitureGrave(Grave grave, Block block) {
        try {
            removeFurnitureGrave(grave, block);

            Material oldType = block.getType();
            if (!block.isEmpty()) {
                block.setType(Material.AIR, false);
            }

            Location placeAt = block.getLocation().add(0.5D, 0.0D, 0.5D);
            Object furniture = this.furniturePlaceMethod.invoke(null, placeAt, this.configuredKey);
            if (furniture == null) {
                if (oldType != Material.AIR && block.isEmpty()) {
                    block.setType(oldType, false);
                }
                return false;
            }

            tagFurniture(furniture, grave);
            return true;
        } catch (Throwable ex) {
            warnPlaceFailed(ex);
            return false;
        }
    }

    private boolean placeCustomBlockGrave(Block block) {
        try {
            Object result = this.blockPlaceMethod.invoke(null, block.getLocation(), this.configuredKey, false);
            return result instanceof Boolean bool && bool;
        } catch (Throwable ex) {
            warnPlaceFailed(ex);
            return false;
        }
    }

    private boolean removeFurnitureGrave(Grave grave, Block block) {
        try {
            Set<Object> matches = findMatchingFurniture(grave, block);
            if (matches.isEmpty()) {
                return false;
            }

            for (Object furniture : matches) {
                this.furnitureRemoveMethod.invoke(null, furniture, false, false);
            }
            return true;
        } catch (Throwable ex) {
            warnRemoveFailed(ex);
            return false;
        }
    }

    private boolean removeCustomBlock(Block block) {
        if (block == null || !this.enabled || this.blockRemoveMethod == null) {
            return false;
        }

        if (!isAnyCustomBlock(block)) {
            return false;
        }

        try {
            Object result = this.blockRemoveMethod.invoke(null, block, false);
            return result instanceof Boolean bool && bool;
        } catch (Throwable ex) {
            warnRemoveFailed(ex);
            return false;
        }
    }

    private boolean hasFurnitureGrave(Grave grave, Block block) {
        return !findMatchingFurniture(grave, block).isEmpty();
    }

    private Set<Object> findMatchingFurniture(Grave grave, Block block) {
        Set<Object> result = new HashSet<>();
        if (!canUseCustomVisual() || !isFurnitureMode() || block == null || block.getWorld() == null) {
            return result;
        }

        try {
            int cx = block.getX() >> 4;
            int cz = block.getZ() >> 4;
            if (!block.getWorld().isChunkLoaded(cx, cz)) {
                return result;
            }

            for (Entity entity : block.getWorld().getChunkAt(cx, cz).getEntities()) {
                Object furniture = resolveFurniture(entity);
                if (matchesConfiguredFurniture(furniture, grave, block)) {
                    result.add(furniture);
                }
            }
        } catch (Throwable ex) {
            warnInspectFailed(ex);
        }

        return result;
    }

    private Object resolveFurniture(Entity entity) {
        if (entity == null || !canUseCustomVisual() || !isFurnitureMode()) {
            return null;
        }

        try {
            Object furniture = this.furnitureGetByMetaEntityMethod.invoke(null, entity);
            if (furniture != null) return furniture;

            furniture = this.furnitureGetBySeatMethod.invoke(null, entity);
            if (furniture != null) return furniture;

            return this.furnitureGetByColliderMethod.invoke(null, entity);
        } catch (Throwable ex) {
            warnInspectFailed(ex);
            return null;
        }
    }

    private boolean matchesConfiguredFurniture(Object furniture, Grave grave, Block block) {
        if (furniture == null || !canUseCustomVisual() || !isFurnitureMode()) {
            return false;
        }

        String id = getFurnitureId(furniture);
        if (id == null || !normalizeId(id).equals(normalizeId(this.normalizedConfiguredId))) {
            return false;
        }

        if (grave != null && isTaggedFurniture(furniture, grave.getId())) {
            return true;
        }

        if (block == null) {
            return true;
        }

        Location location = getFurnitureLocation(furniture);
        if (location == null || location.getWorld() == null || block.getWorld() == null) {
            return false;
        }

        return location.getWorld().equals(block.getWorld())
                && location.getBlockX() == block.getX()
                && location.getBlockY() == block.getY()
                && location.getBlockZ() == block.getZ();
    }

    private void tagFurniture(Object furniture, Grave grave) {
        if (furniture == null || grave == null) {
            return;
        }

        Entity entity = getFurnitureEntity(furniture);
        if (entity == null) {
            return;
        }

        entity.getPersistentDataContainer().set(
                this.furnitureGraveIdKey,
                org.bukkit.persistence.PersistentDataType.STRING,
                grave.getId().toString()
        );
    }

    private boolean isTaggedFurniture(Object furniture, UUID graveId) {
        if (furniture == null || graveId == null) {
            return false;
        }

        Entity entity = getFurnitureEntity(furniture);
        if (entity == null) {
            return false;
        }

        String tagged = entity.getPersistentDataContainer().get(
                this.furnitureGraveIdKey,
                org.bukkit.persistence.PersistentDataType.STRING
        );

        return graveId.toString().equals(tagged);
    }

    private Entity getFurnitureEntity(Object furniture) {
        if (furniture == null || this.furnitureBukkitEntityMethod == null) {
            return null;
        }

        try {
            Object entity = this.furnitureBukkitEntityMethod.invoke(furniture);
            return entity instanceof Entity bukkitEntity ? bukkitEntity : null;
        } catch (Throwable ex) {
            warnInspectFailed(ex);
            return null;
        }
    }

    private Location getFurnitureLocation(Object furniture) {
        if (furniture == null || this.furnitureLocationMethod == null) {
            return null;
        }

        try {
            Object location = this.furnitureLocationMethod.invoke(furniture);
            return location instanceof Location bukkitLocation ? bukkitLocation : null;
        } catch (Throwable ex) {
            warnInspectFailed(ex);
            return null;
        }
    }

    private String getFurnitureId(Object furniture) {
        if (furniture == null || this.furnitureIdMethod == null || this.keyAsStringMethod == null) {
            return null;
        }

        try {
            Object key = this.furnitureIdMethod.invoke(furniture);
            return key == null ? null : String.valueOf(this.keyAsStringMethod.invoke(key));
        } catch (Throwable ex) {
            warnInspectFailed(ex);
            return null;
        }
    }

    private boolean isConfiguredCustomBlock(Block block) {
        String placedId = getCustomBlockId(block);
        return placedId != null && normalizeId(placedId).equals(normalizeId(this.normalizedConfiguredId));
    }

    private boolean isAnyCustomBlock(Block block) {
        if (block == null || !this.enabled || this.blockIsCustomBlockMethod == null) {
            return false;
        }

        try {
            Object result = this.blockIsCustomBlockMethod.invoke(null, block);
            return result instanceof Boolean bool && bool;
        } catch (Throwable ex) {
            warnInspectFailed(ex);
            return false;
        }
    }

    private String getCustomBlockId(Block block) {
        if (block == null || !canUseCustomVisual() || this.blockGetStateMethod == null) {
            return null;
        }

        try {
            Object state = this.blockGetStateMethod.invoke(null, block);
            if (state == null) return null;

            Object holder = this.blockStateOwnerMethod.invoke(state);
            if (holder == null) return null;

            Object definition = this.holderValueMethod.invoke(holder);
            if (definition == null) return null;

            Object key = this.blockDefinitionIdMethod.invoke(definition);
            if (key == null) return null;

            return String.valueOf(this.keyAsStringMethod.invoke(key));
        } catch (Throwable ex) {
            warnInspectFailed(ex);
            return null;
        }
    }

    private void registerCraftEngineEvents() {
        if (this.eventsRegistered) {
            return;
        }

        try {
            registerEvent("net.momirealms.craftengine.bukkit.api.event.FurnitureInteractEvent", this::handleFurnitureInteractEvent);
            registerEvent("net.momirealms.craftengine.bukkit.api.event.FurnitureBreakEvent", this::handleFurnitureBreakEvent);
            registerEvent("net.momirealms.craftengine.bukkit.api.event.CraftEngineReloadEvent", this::handleCraftEngineReloadEvent);
            this.eventsRegistered = true;
        } catch (Throwable ex) {
            if (!this.warnedEventsFailed) {
                this.warnedEventsFailed = true;
                plugin.getLogger().warning("[bGraveStones] Could not register CraftEngine furniture events. Furniture graves may only work through fallback block interaction. Cause: "
                        + ex.getClass().getSimpleName()
                        + ": "
                        + ex.getMessage());
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void registerEvent(String eventClassName, EventHandler handler) throws ClassNotFoundException {
        Class<?> rawClass = Class.forName(eventClassName);
        Class<? extends Event> eventClass = (Class<? extends Event>) rawClass.asSubclass(Event.class);
        EventExecutor executor = (listener, event) -> handler.handle(event);
        plugin.getServer().getPluginManager().registerEvent(
                eventClass,
                this.dynamicListener,
                EventPriority.HIGHEST,
                executor,
                plugin,
                true
        );
    }

    private void handleFurnitureInteractEvent(Event event) {
        Player player = getEventPlayer(event);
        Location location = getEventLocation(event);
        if (player == null || location == null) {
            return;
        }

        Optional<Grave> grave = this.graveManager.getGraveAt(location.getBlock());
        if (grave.isEmpty()) {
            return;
        }

        setCancelled(event, true);
        this.graveManager.lootGrave(player, grave.get());
    }

    private void handleFurnitureBreakEvent(Event event) {
        Player player = getEventPlayer(event);
        Location location = getEventLocation(event);
        if (player == null || location == null) {
            return;
        }

        Optional<Grave> grave = this.graveManager.getGraveAt(location.getBlock());
        if (grave.isEmpty()) {
            return;
        }

        if (this.graveManager.isAdminCanBreak() && player.hasPermission("graves.admin")) {
            invokeIfPresent(event, "setDropItems", false);
            this.graveManager.removeGrave(grave.get(), player.getUniqueId());
            this.graveManager.messages().send((CommandSender) player, "gravestone.removed");
            return;
        }

        setCancelled(event, true);
        this.graveManager.messages().send((CommandSender) player, "gravestone.breakDenied");
    }

    private void handleCraftEngineReloadEvent(Event event) {
        if (!this.enabled) {
            return;
        }

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            reload();
            this.graveManager.restoreMissingGraveBlocks();
            this.graveManager.bootstrapVisuals();
        }, Math.max(1L, this.retryAfterTicks));
    }

    private Player getEventPlayer(Event event) {
        Object player = invokeIfPresent(event, "player");
        if (player == null) {
            player = invokeIfPresent(event, "getPlayer");
        }
        return player instanceof Player bukkitPlayer ? bukkitPlayer : null;
    }

    private Location getEventLocation(Event event) {
        Object location = invokeIfPresent(event, "location");
        if (location instanceof Location bukkitLocation) {
            return bukkitLocation;
        }

        Object furniture = invokeIfPresent(event, "furniture");
        Location furnitureLocation = getFurnitureLocation(furniture);
        if (furnitureLocation != null) {
            return furnitureLocation;
        }

        return null;
    }

    private void setCancelled(Event event, boolean cancelled) {
        invokeIfPresent(event, "setCancelled", cancelled);
    }

    private Object invokeIfPresent(Object target, String methodName, Object... args) {
        if (target == null || methodName == null) {
            return null;
        }

        try {
            Class<?>[] parameterTypes = new Class<?>[args.length];
            for (int i = 0; i < args.length; i++) {
                parameterTypes[i] = args[i] instanceof Boolean ? boolean.class : args[i].getClass();
            }
            Method method = target.getClass().getMethod(methodName, parameterTypes);
            return method.invoke(target, args);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private void scheduleDelayedRefresh() {
        if (!this.enabled || this.retryAfterTicks <= 0 || this.retryAttempts >= 3) {
            return;
        }

        this.retryAttempts++;
        if (!this.warnedDelayedRetry && this.logWarnings) {
            this.warnedDelayedRetry = true;
            plugin.getLogger().info("[bGraveStones] CraftEngine grave visual will be checked again in " + this.retryAfterTicks + " ticks.");
        }

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            reload();
            if (canUseCustomVisual()) {
                this.graveManager.restoreMissingGraveBlocks();
                this.graveManager.bootstrapVisuals();
            }
        }, this.retryAfterTicks);
    }

    private String normalizeId(String raw) {
        return raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
    }

    private void warnUnavailable(Throwable ex) {
        if (!this.logWarnings || this.warnedUnavailable) return;
        this.warnedUnavailable = true;
        plugin.getLogger().warning("[bGraveStones] CraftEngine grave visual is enabled, but the CraftEngine API is not available yet. Falling back to graveBlock. Cause: "
                + ex.getClass().getSimpleName()
                + ": "
                + ex.getMessage());
    }

    private void warnMissingBlock() {
        if (!this.logWarnings || this.warnedMissingBlock) return;
        this.warnedMissingBlock = true;
        plugin.getLogger().warning("[bGraveStones] CraftEngine grave visual '" + this.configuredId + "' was not found. Falling back to graveBlock.");
    }

    private void warnPlaceFailed(Throwable ex) {
        if (!this.logWarnings || this.warnedPlaceFailed) return;
        this.warnedPlaceFailed = true;
        plugin.getLogger().warning("[bGraveStones] Could not place CraftEngine grave visual '"
                + this.configuredId
                + "'. Falling back to graveBlock. Cause: "
                + ex.getClass().getSimpleName()
                + ": "
                + ex.getMessage());
    }

    private void warnRemoveFailed(Throwable ex) {
        if (!this.logWarnings || this.warnedRemoveFailed) return;
        this.warnedRemoveFailed = true;
        plugin.getLogger().warning("[bGraveStones] Could not remove CraftEngine grave visual. Falling back to vanilla cleanup where possible. Cause: "
                + ex.getClass().getSimpleName()
                + ": "
                + ex.getMessage());
    }

    private void warnInspectFailed(Throwable ex) {
        if (!this.logWarnings || this.warnedInspectFailed) return;
        this.warnedInspectFailed = true;
        plugin.getLogger().warning("[bGraveStones] Could not inspect CraftEngine grave visual. Falling back to vanilla grave checks. Cause: "
                + ex.getClass().getSimpleName()
                + ": "
                + ex.getMessage());
    }

    private interface EventHandler {
        void handle(Event event);
    }

    private static final class MissingDefinitionException extends Exception {
    }
}
