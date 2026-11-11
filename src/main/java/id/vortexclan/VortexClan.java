
package id.vortexclan;

import id.vortexclan.clan.ClanBankManager;
import id.vortexclan.clan.ClanChatManager;
import id.vortexclan.clan.ClanLeaderboardManager;
import id.vortexclan.clan.ClanLevelManager;
import id.vortexclan.clan.ClanManager;
import id.vortexclan.clan.ClanMemberManager;
import id.vortexclan.clan.ClanPvPManager;
import id.vortexclan.clan.ClanTaxManager;
import id.vortexclan.commands.ClanCommand;
import id.vortexclan.database.DatabaseManager;
import id.vortexclan.economy.VaultManager;
import id.vortexclan.gui.ClanLeaderboardGUI;
import id.vortexclan.listeners.ClanChatListener;
import id.vortexclan.listeners.ClanPvPListener;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public final class VortexClan extends JavaPlugin {

    private static VortexClan instance;

    private DatabaseManager databaseManager;
    private VaultManager vaultManager;

    private ClanManager clanManager;
    private ClanMemberManager clanMemberManager;
    private ClanBankManager clanBankManager;
    private ClanTaxManager clanTaxManager;
    private ClanLevelManager clanLevelManager;
    private ClanLeaderboardManager clanLeaderboardManager;
    private ClanPvPManager clanPvPManager;
    private ClanChatManager clanChatManager;

    private ClanLeaderboardGUI clanLeaderboardGUI;

    private BukkitTask taxTask;

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();

        try {
            initializeManagers();
            registerListeners();
            registerCommands();
            startTaxTask();

            getLogger().info("VortexClan berhasil diaktifkan!");

        } catch (Exception exception) {
            getLogger().severe("Gagal mengaktifkan VortexClan!");
            exception.printStackTrace();

            Bukkit.getPluginManager().disablePlugin(this);
        }
    }

    private void initializeManagers() throws Exception {
        databaseManager = new DatabaseManager(this);
        databaseManager.initialize();

        vaultManager = new VaultManager(this);
        vaultManager.setupEconomy();

        clanManager = new ClanManager(
                this,
                databaseManager,
                vaultManager.getEconomy()
        );

        clanMemberManager = new ClanMemberManager(
                this,
                databaseManager,
                clanManager
        );

        clanBankManager = new ClanBankManager(
                this,
                databaseManager,
                clanManager,
                vaultManager
        );

        clanTaxManager = new ClanTaxManager(
                this,
                databaseManager
        );

        clanLevelManager = new ClanLevelManager(
                this,
                databaseManager,
                clanManager
        );

        clanLeaderboardManager = new ClanLeaderboardManager(
                databaseManager
        );

        clanPvPManager = new ClanPvPManager(
                databaseManager
        );

        clanChatManager = new ClanChatManager(this);

        clanLeaderboardGUI = new ClanLeaderboardGUI(this);
    }

    private void registerListeners() {
        Bukkit.getPluginManager().registerEvents(
                clanLeaderboardGUI,
                this
        );

        Bukkit.getPluginManager().registerEvents(
                new ClanPvPListener(this),
                this
        );

        Bukkit.getPluginManager().registerEvents(
                new ClanChatListener(this, clanChatManager),
                this
        );
    }

    private void registerCommands() {
        if (getCommand("clan") == null) {
            throw new IllegalStateException(
                    "Command clan tidak ditemukan di plugin.yml!"
            );
        }

        ClanCommand clanCommand = new ClanCommand(this);

        getCommand("clan").setExecutor(clanCommand);
        getCommand("clan").setTabCompleter(clanCommand);
    }

    private void startTaxTask() {
        if (!getConfig().getBoolean("clan.tax.enabled", true)) {
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
            taxTask = null;
        }

        if (clanChatManager != null) {
            clanChatManager.clear();
        }

        if (databaseManager != null) {
            databaseManager.close();
        }

        instance = null;

        getLogger().info("VortexClan berhasil dinonaktifkan!");
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

    public ClanLevelManager getClanLevelManager() {
        return clanLevelManager;
    }

    public ClanLeaderboardManager getClanLeaderboardManager() {
        return clanLeaderboardManager;
    }

    public ClanPvPManager getClanPvPManager() {
        return clanPvPManager;
    }

    public ClanChatManager getClanChatManager() {
        return clanChatManager;
    }

    public ClanLeaderboardGUI getClanLeaderboardGUI() {
        return clanLeaderboardGUI;
    }
}
