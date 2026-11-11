
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
                databaseManager,
                clanManager
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

        clanLeaderboardGUI = new ClanLeaderboardGUI(
                this
        );
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
            getLogger().info("Sistem pajak clan sedang dinonaktifkan.");
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

    public ClanLeaderboardGUI getClanLeaderboardGUI() {
        return clanLeaderboardGUI;
    }
}
