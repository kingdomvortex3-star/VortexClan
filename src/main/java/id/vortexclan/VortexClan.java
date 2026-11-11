
package id.vortexclan;

import id.vortexclan.clan.ClanManager;
import id.vortexclan.clan.ClanMemberManager;
import id.vortexclan.commands.ClanCommand;
import id.vortexclan.database.DatabaseManager;
import id.vortexclan.economy.VaultManager;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

public final class VortexClan extends JavaPlugin {

    private static VortexClan instance;

    private DatabaseManager databaseManager;
    private VaultManager vaultManager;
    private ClanManager clanManager;
    private ClanMemberManager clanMemberManager;

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();

        try {
            databaseManager = new DatabaseManager(this);
            databaseManager.initialize();

            vaultManager = new VaultManager(this);
            vaultManager.setupEconomy();

            clanManager = new ClanManager(
                    this,
                    vaultManager.getEconomy()
            );

            clanMemberManager = new ClanMemberManager(
                    this,
                    clanManager
            );

            if (getCommand("clan") == null) {
                throw new IllegalStateException(
                        "Clan command missing from plugin.yml"
                );
            }

            getCommand("clan").setExecutor(
                    new ClanCommand(this)
            );

            getLogger().info("VortexClan successfully enabled!");

        } catch (Exception exception) {
            getLogger().log(
                    Level.SEVERE,
                    "Failed to enable VortexClan!",
                    exception
            );

            Bukkit.getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        if (databaseManager != null) {
            databaseManager.close();
        }

        instance = null;
    }

    public static VortexClan getInstance() {
        return instance;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public VaultManager getVaultManager() {
        return vaultManager;
    }

    public ClanManager getClanManager() {
        return clanManager;
    }

    public ClanMemberManager getClanMemberManager() {
        return clanMemberManager;
    }
}
