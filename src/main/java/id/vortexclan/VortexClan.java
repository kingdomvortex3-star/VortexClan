
package id.vortexclan;

import id.vortexclan.clan.ClanBankManager;
import id.vortexclan.clan.ClanManager;
import id.vortexclan.clan.ClanMemberManager;
import id.vortexclan.clan.ClanTaxManager;
import id.vortexclan.commands.ClanCommand;
import id.vortexclan.database.DatabaseManager;
import id.vortexclan.economy.VaultManager;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.logging.Level;

public final class VortexClan extends JavaPlugin {

    private static VortexClan instance;

    private DatabaseManager databaseManager;
    private VaultManager vaultManager;
    private ClanManager clanManager;
    private ClanMemberManager clanMemberManager;
    private ClanBankManager clanBankManager;
    private ClanTaxManager clanTaxManager;

    private BukkitTask taxTask;

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

            clanBankManager = new ClanBankManager(
                    this,
                    clanManager,
                    vaultManager.getEconomy()
            );

            clanTaxManager = new ClanTaxManager(this);

            if (getCommand("clan") == null) {
                throw new IllegalStateException(
                        "Clan command missing from plugin.yml"
                );
            }

            getCommand("clan").setExecutor(
                    new ClanCommand(this)
            );

            startTaxTask();

            getLogger().info("Database initialized!");
            getLogger().info("ClanManager initialized!");
            getLogger().info("ClanMemberManager initialized!");
            getLogger().info("ClanBankManager initialized!");
            getLogger().info("ClanTaxManager initialized!");
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

    private void startTaxTask() {
        if (!getConfig().getBoolean(
                "clan.tax.enabled", true)) {
            return;
        }

        clanTaxManager.processDueTaxes();

        taxTask = Bukkit.getScheduler().runTaskTimer(
                this,
                () -> clanTaxManager.processDueTaxes(),
                6000L,
                6000L
        );
    }

    @Override
    public void onDisable() {
        if (taxTask != null) {
            taxTask.cancel();
        }

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

    public ClanBankManager getClanBankManager() {
        return clanBankManager;
    }

    public ClanTaxManager getClanTaxManager() {
        return clanTaxManager;
    }
}
