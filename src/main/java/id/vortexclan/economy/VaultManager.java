
package id.vortexclan.economy;

import id.vortexclan.VortexClan;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.UUID;

public final class VaultManager {

    private final VortexClan plugin;

    private Economy economy;

    public VaultManager(VortexClan plugin) {
        this.plugin = plugin;
    }

    public boolean setupEconomy() {
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
            plugin.getLogger().warning("Vault plugin not found!");
            return false;
        }

        RegisteredServiceProvider<Economy> provider =
                Bukkit.getServicesManager()
                        .getRegistration(Economy.class);

        if (provider == null) {
            plugin.getLogger().warning(
                    "No Economy provider registered!"
            );
            return false;
        }

        economy = provider.getProvider();

        if (economy == null) {
            plugin.getLogger().warning(
                    "Economy provider is unavailable!"
            );
            return false;
        }

        plugin.getLogger().info(
                "Economy connected: " + economy.getName()
        );

        return true;
    }

    public boolean isAvailable() {
        return economy != null;
    }

    public Economy getEconomy() {
        return economy;
    }

    public double getBalance(UUID uuid) {
        if (!isAvailable()) {
            return 0;
        }

        OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);

        return economy.getBalance(player);
    }

    public boolean has(UUID uuid, double amount) {
        if (!isAvailable() || !isValidAmount(amount)) {
            return false;
        }

        OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);

        return economy.has(player, amount);
    }

    public boolean withdraw(UUID uuid, double amount) {
        if (!isAvailable() || !isValidAmount(amount)) {
            return false;
        }

        OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);

        EconomyResponse response =
                economy.withdrawPlayer(player, amount);

        return response.transactionSuccess();
    }

    public boolean deposit(UUID uuid, double amount) {
        if (!isAvailable() || !isValidAmount(amount)) {
            return false;
        }

        OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);

        EconomyResponse response =
                economy.depositPlayer(player, amount);

        return response.transactionSuccess();
    }

    public String format(double amount) {
        if (!isAvailable()) {
            return String.valueOf(amount);
        }

        return economy.format(amount);
    }

    private boolean isValidAmount(double amount) {
        return Double.isFinite(amount) && amount >= 0;
    }
}
