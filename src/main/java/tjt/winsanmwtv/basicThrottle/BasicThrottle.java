package tjt.winsanmwtv.basicThrottle;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.Objects;

public final class BasicThrottle extends JavaPlugin {

    private Throttle throttle;
    private File inventoryFile;
    private FileConfiguration inventoryConfig;

    @Override
    public void onEnable() {
        getServer().getConsoleSender().sendMessage(ChatColor.AQUA + "[BasicThrottle] Plugin loaded.");

        getConfig().options().copyDefaults(true);
        saveConfig();

        // Setup inventory.yml
        inventoryFile = new File(getDataFolder(), "inventory.yml");
        if (!inventoryFile.exists()) {
            if (!inventoryFile.getParentFile().exists() && !inventoryFile.getParentFile().mkdirs()) {
                getLogger().warning("Failed to create plugin folder!");
            }
            try {
                if (!inventoryFile.createNewFile()) {
                    getLogger().warning("Failed to create inventory.yml!");
                }
            } catch (IOException e) {
                getLogger().warning("Error creating inventory.yml: " + e.getMessage());
            }
        }
        inventoryConfig = YamlConfiguration.loadConfiguration(inventoryFile);

        // Restore hotbars if server crashed
        restoreHotbars();

        throttle = new Throttle(this, inventoryConfig, inventoryFile);
        Objects.requireNonNull(getCommand("traindrive")).setExecutor(throttle);
        getServer().getPluginManager().registerEvents(throttle, this);
        getServer().getScheduler().scheduleSyncRepeatingTask(this, throttle::throttleLoop, 0L, 1L);
    }

    @Override
    public void onDisable() {
        getServer().getConsoleSender().sendMessage(ChatColor.AQUA + "[BasicThrottle] Plugin unloading...");
        if (throttle != null) {
            throttle.shutdown();
        }
        getServer().getConsoleSender().sendMessage(ChatColor.AQUA + "[BasicThrottle] Plugin unloaded.");
    }

    private void restoreHotbars() {
        var section = inventoryConfig.getConfigurationSection("savedHotbars");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                var player = Bukkit.getPlayer(java.util.UUID.fromString(key));
                if (player != null) {
                    for (int i = 0; i < 9; i++) {
                        var obj = inventoryConfig.get("savedHotbars." + key + "." + i);
                        if (obj instanceof org.bukkit.inventory.ItemStack item) {
                            player.getInventory().setItem(i, item);
                        }
                    }
                }
            }
            inventoryConfig.set("savedHotbars", null);
            try {
                inventoryConfig.save(inventoryFile);
            } catch (IOException e) {
                getLogger().warning("Failed to save inventory.yml: " + e.getMessage());
            }
        }
    }
}
