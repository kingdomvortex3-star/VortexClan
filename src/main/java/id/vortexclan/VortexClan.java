
package id.vortexclan;

import id.vortexclan.database.DatabaseManager;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

public final class VortexClan extends JavaPlugin {

    private static VortexClan instance;
    private DatabaseManager databaseManager;

    @Override
    public void onEnable() {
        instance = this;

        getLogger().info("=================================");
        getLogger().info("       VortexClan v1.0.0");
        getLogger().info("=================================");

        saveDefaultConfig();

        if (!checkDependencies()) {
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        try {
            databaseManager = new DatabaseManager(this);
            databaseManager.initialize();

            getLogger().info("SQLite database connected!");
            getLogger().info("Database tables initialized!");

        } catch (Exception exception) {
            getLogger().log(
                Level.SEVERE,
                "Failed to initialize database!",
                exception
            );

            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        getLogger().info("VortexClan successfully enabled!");
        getLogger().info("=================================");
    }

    private boolean checkDependencies() {
        boolean vault = Bukkit.getPluginManager()
            .getPlugin("Vault") != null;

        boolean placeholderAPI = Bukkit.getPluginManager()
            .getPlugin("PlaceholderAPI") != null;

        getLogger().info("Checking dependencies...");

        if (vault) {
            getLogger().info("Vault detected!");
        } else {
            getLogger().warning(
                "Vault not found! Economy features will be unavailable."
            );
        }

        if (placeholderAPI) {
            getLogger().info("PlaceholderAPI detected!");
        } else {
            getLogger().warning(
                "PlaceholderAPI not found! Placeholders will be unavailable."
            );
        }

        return true;
    }

    @Override
    public void onDisable() {
        if (databaseManager != null) {
            databaseManager.close();
        }

        getLogger().info("VortexClan disabled!");
        instance = null;
    }

    public static VortexClan getInstance() {
        return instance;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }
}
