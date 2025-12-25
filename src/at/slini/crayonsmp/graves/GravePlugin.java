package at.slini.crayonsmp.graves;

import at.slini.crayonsmp.graves.listener.BlockListener;
import at.slini.crayonsmp.graves.listener.DeathListener;
import at.slini.crayonsmp.graves.listener.PickupListener;
import at.slini.crayonsmp.graves.listener.EntityProtectionListener;
import at.slini.crayonsmp.graves.listener.InteractListener;
import at.slini.crayonsmp.graves.storage.GraveStorage;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class GravePlugin extends JavaPlugin {

    private GraveManager graveManager;
    private GraveStorage graveStorage;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.graveStorage = new GraveStorage(this);
        this.graveStorage.load();

        this.graveManager = new GraveManager(this, graveStorage);

        // Re-spawn missing holograms on startup (safe no-op if disabled)
        this.graveManager.bootstrapVisuals();

        getServer().getPluginManager().registerEvents(new DeathListener(graveManager), this);
        getServer().getPluginManager().registerEvents(new InteractListener(graveManager), this);
        getServer().getPluginManager().registerEvents(new PickupListener(graveManager), this);
        getServer().getPluginManager().registerEvents(new EntityProtectionListener(graveManager), this);
        getServer().getPluginManager().registerEvents(new BlockListener(graveManager), this);


        PluginCommand cmd = getCommand("graves");
        if (cmd != null) {
            cmd.setExecutor(new GraveReloadCommand(this));
        }

        getLogger().info("Crayon-GraveStones enabled.");
    }

    @Override
    public void onDisable() {
        if (graveManager != null) {
            graveManager.shutdown();
        }
        if (graveStorage != null) {
            graveStorage.save();
        }
        getLogger().info("Crayon-GraveStones disabled.");
    }

    public GraveManager getGraveManager() {
        return graveManager;
    }

    public GraveStorage getGraveStorage() {
        return graveStorage;
    }
}
