
package id.vortexclan;

import id.vortexclan.clan.ClanAllianceChatManager;
import id.vortexclan.clan.ClanAllianceManager;
import id.vortexclan.clan.ClanBankManager;
import id.vortexclan.clan.ClanChatManager;
import id.vortexclan.clan.ClanHomeManager;
import id.vortexclan.clan.ClanLeaderboardManager;
import id.vortexclan.clan.ClanLevelManager;
import id.vortexclan.clan.ClanManager;
import id.vortexclan.clan.ClanMemberManager;
import id.vortexclan.clan.ClanPvPManager;
import id.vortexclan.clan.ClanTaxManager;
import id.vortexclan.clan.ClanWarLeaderboardManager;
import id.vortexclan.clan.ClanWarManager;
import id.vortexclan.clan.ClanWarRecoveryManager;

import id.vortexclan.commands.ClanCommand;
import id.vortexclan.database.DatabaseManager;
import id.vortexclan.economy.VaultManager;

import id.vortexclan.gui.ClanAllianceGUI;
import id.vortexclan.gui.ClanLeaderboardGUI;
import id.vortexclan.gui.ClanWarGUI;
import id.vortexclan.gui.ClanWarLeaderboardGUI;

import id.vortexclan.listeners.ClanAllianceChatListener;
import id.vortexclan.listeners.ClanAllianceListener;
import id.vortexclan.listeners.ClanChatListener;
import id.vortexclan.listeners.ClanPvPListener;
import id.vortexclan.listeners.ClanWarListener;

import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
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
    private ClanHomeManager clanHomeManager;

    private ClanAllianceManager clanAllianceManager;
    private ClanAllianceChatManager clanAllianceChatManager;

    private ClanWarManager clanWarManager;
    private ClanWarRecoveryManager clanWarRecoveryManager;
    private ClanWarLeaderboardManager clanWarLeaderboardManager;

    private ClanLeaderboardGUI clanLeaderboardGUI;
    private ClanAllianceGUI clanAllianceGUI;
    private ClanWarGUI clanWarGUI;
    private ClanWarLeaderboardGUI clanWarLeaderboardGUI;

    private BukkitTask taxTask;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        try {
            initializeManagers();
            registerListeners();
            registerCommands();

            clanWarRecoveryManager.recover();
            startTaxTask();

            getLogger().info("--------------------------------");
            getLogger().info("VortexClan berhasil diaktifkan!");
            getLogger().info("Clan System: Enabled");
            getLogger().info("Clan Alliance: Enabled");
            getLogger().info("Clan War: Enabled");
            getLogger().info("Clan Leaderboard: Enabled");
            getLogger().info("--------------------------------");

        } catch (Exception exception) {
            getLogger().severe("Gagal mengaktifkan VortexClan!");
            exception.printStackTrace();
            Bukkit.getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        if (taxTask != null) {
            taxTask.cancel();
            taxTask = null;
        }

        if (clanWarManager != null) {
            clanWarManager.cancelTasks();
        }

        if (clanChatManager != null) {
            clanChatManager.clear();
        }

        if (clanAllianceChatManager != null) {
            clanAllianceChatManager.clear();
        }

        if (clanAllianceGUI != null) {
            clanAllianceGUI.clearWaitingInputs();
        }

        if (clanHomeManager != null) {
            clanHomeManager.clearCooldowns();
        }

        if (databaseManager != null) {
            databaseManager.close();
        }

        instance = null;
        getLogger().info("VortexClan berhasil dinonaktifkan!");
    }

    private void initializeManagers() {
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
                databaseManager
        );

        clanLeaderboardManager = new ClanLeaderboardManager(
                databaseManager
        );

        clanPvPManager = new ClanPvPManager(
                databaseManager
        );

        clanChatManager = new ClanChatManager(
                this
        );

        clanHomeManager = new ClanHomeManager(
                this,
                databaseManager
        );

        clanAllianceManager = new ClanAllianceManager(
                this,
                databaseManager,
                clanManager
        );

        clanAllianceChatManager = new ClanAllianceChatManager(
                this,
                clanAllianceManager
        );

        clanWarManager = new ClanWarManager(
                this,
                databaseManager
        );

        clanWarRecoveryManager = new ClanWarRecoveryManager(
                this,
                databaseManager,
                clanWarManager
        );

        clanWarLeaderboardManager = new ClanWarLeaderboardManager(
                databaseManager
        );

        clanLeaderboardGUI = new ClanLeaderboardGUI(
                this,
                clanLeaderboardManager
        );

        clanAllianceGUI = new ClanAllianceGUI(
                this,
                clanAllianceManager
        );

        clanWarGUI = new ClanWarGUI(
                this,
                clanWarManager
        );

        clanWarLeaderboardGUI = new ClanWarLeaderboardGUI(
                this,
                clanWarLeaderboardManager
        );
    }

    private void registerListeners() {
        Bukkit.getPluginManager().registerEvents(
                clanLeaderboardGUI,
                this
        );

        Bukkit.getPluginManager().registerEvents(
                clanAllianceGUI,
                this
        );

        Bukkit.getPluginManager().registerEvents(
                clanWarGUI,
                this
        );

        Bukkit.getPluginManager().registerEvents(
                clanWarLeaderboardGUI,
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

        Bukkit.getPluginManager().registerEvents(
                new ClanAllianceChatListener(
                        this,
                        clanAllianceChatManager
                ),
                this
        );

        Bukkit.getPluginManager().registerEvents(
                new ClanAllianceListener(
                        this,
                        clanAllianceManager
                ),
                this
        );

        Bukkit.getPluginManager().registerEvents(
                new ClanWarListener(
                        this,
                        clanWarManager
                ),
                this
        );
    }

    private void registerCommands() {
        ClanCommand clanCommand = new ClanCommand(this);

        PluginCommand clan = getCommand("clan");

        if (clan == null) {
            throw new IllegalStateException(
                    "Command 'clan' tidak ditemukan di plugin.yml!"
            );
        }

        clan.setExecutor(clanCommand);
        clan.setTabCompleter(clanCommand);

        PluginCommand clanChat = getCommand("cc");

        if (clanChat == null) {
            throw new IllegalStateException(
                    "Command 'cc' tidak ditemukan di plugin.yml!"
            );
        }

        clanChat.setExecutor(clanCommand);
        clanChat.setTabCompleter(clanCommand);
    }

    private void startTaxTask() {
        if (!getConfig().getBoolean("clan.tax.enabled", true)) {
            getLogger().info(
                    "Sistem pajak clan sedang dinonaktifkan."
            );
            return;
        }

        clanTaxManager.processDueTaxes();

        taxTask = Bukkit.getScheduler().runTaskTimer(
                this,
                () -> {
                    if (clanTaxManager != null) {
                        clanTaxManager.processDueTaxes();
                    }
                },
                6000L,
                6000L
        );
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

    public ClanHomeManager getClanHomeManager() {
        return clanHomeManager;
    }

    public ClanAllianceManager getClanAllianceManager() {
        return clanAllianceManager;
    }

    public ClanAllianceChatManager getClanAllianceChatManager() {
        return clanAllianceChatManager;
    }

    public ClanWarManager getClanWarManager() {
        return clanWarManager;
    }

    public ClanWarRecoveryManager getClanWarRecoveryManager() {
        return clanWarRecoveryManager;
    }

    public ClanWarLeaderboardManager getClanWarLeaderboardManager() {
        return clanWarLeaderboardManager;
    }

    public ClanLeaderboardGUI getClanLeaderboardGUI() {
        return clanLeaderboardGUI;
    }

    public ClanAllianceGUI getClanAllianceGUI() {
        return clanAllianceGUI;
    }

    public ClanWarGUI getClanWarGUI() {
        return clanWarGUI;
    }

    public ClanWarLeaderboardGUI getClanWarLeaderboardGUI() {
        return clanWarLeaderboardGUI;
    }
}
