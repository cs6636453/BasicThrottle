package tjt.winsanmwtv.basicThrottle;

import org.bukkit.ChatColor;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

public final class BasicThrottle extends JavaPlugin {

    @Override
    public void onEnable() {
        // Plugin startup logic
        getServer().getConsoleSender().sendMessage(ChatColor.AQUA+"[BasicThrottle] Plugin loaded.");
        getConfig().options().copyDefaults(true);
        saveConfig();
        Throttle throttle = new Throttle();
        Objects.requireNonNull(getCommand("traindrive")).setExecutor(throttle);
        getServer().getPluginManager().registerEvents(throttle, this);
        getServer().getScheduler().scheduleSyncRepeatingTask(this, throttle::throttleLoop, 0L, 1L);
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
        getServer().getConsoleSender().sendMessage(ChatColor.AQUA+"[BasicThrottle] Plugin unloaded.");
    }
}
