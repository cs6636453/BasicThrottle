package tjt.winsanmwtv.basicThrottle;

import com.bergerkiller.bukkit.tc.properties.CartProperties;
import com.bergerkiller.bukkit.tc.properties.TrainProperties;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

public class Throttle implements Listener, CommandExecutor {

    // Driving mode & saved hotbar
    private final Map<Player, Byte> modeHashMap = new HashMap<>();
    private final Map<Player, ItemStack[]> invHashMap = new HashMap<>();
    private final Map<Player, Boolean> isDriving = new HashMap<>();

    // Throttle configuration
    private record ThrottleSlot(String label, ChatColor color, double speedChange) {}
    private final Map<Integer, ThrottleSlot> throttleMap = Map.of(
            0, new ThrottleSlot("EB", ChatColor.DARK_RED, -0.020),
            1, new ThrottleSlot("B4", ChatColor.RED, -0.008),
            2, new ThrottleSlot("B3", ChatColor.RED, -0.006),
            3, new ThrottleSlot("B2", ChatColor.GOLD, -0.004),
            4, new ThrottleSlot("B1", ChatColor.GOLD, -0.002),
            5, new ThrottleSlot("N", ChatColor.YELLOW, 0.0),
            6, new ThrottleSlot("P1", ChatColor.GREEN, 0.001),
            7, new ThrottleSlot("P2", ChatColor.GREEN, 0.003),
            8, new ThrottleSlot("P3", ChatColor.GREEN, 0.005)
    );

    // --- Command handling ---
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Only players can use this command.");
            return true;
        }
        if (!player.hasPermission("basicthrottle.traindrive")) {
            player.sendMessage(ChatColor.RED + "You don't have permission.");
            return true;
        }
        if (args.length != 1) {
            player.sendMessage(ChatColor.YELLOW + "Usage: /traindrive <on|off>");
            return true;
        }

        String arg = args[0].toLowerCase();

        if (arg.equals("on") || arg.equals("enable")) {
            if (modeHashMap.containsKey(player)) {
                player.sendMessage(ChatColor.RED + "You're already in driving mode.");
                return true;
            }

            // Save hotbar
            ItemStack[] hotbar = new ItemStack[9];
            for (int i = 0; i < 9; i++) hotbar[i] = player.getInventory().getItem(i);
            invHashMap.put(player, hotbar);

            // Enable driving
            modeHashMap.put(player, (byte)0);
            player.sendMessage(ChatColor.AQUA + "Driving mode activated.");
            inventoryHotbar(player);
            return true;
        }

        if (arg.equals("off") || arg.equals("disable")) {
            if (!modeHashMap.containsKey(player)) {
                player.sendMessage(ChatColor.RED + "You are not in driving mode.");
                return true;
            }
            emergencyBrake(player);
            player.sendMessage(ChatColor.AQUA + "Driving mode deactivated.");
            return true;
        }

        player.sendMessage(ChatColor.YELLOW + "Usage: /traindrive <on|off>");
        return true;
    }

    // --- Inventory setup ---
    public void inventoryHotbar(Player player) {
        Object[][] items = {
                {Material.BARRIER, "§4EB"},
                {Material.RED_CONCRETE_POWDER, "§cB4"},
                {Material.RED_WOOL, "§cB3"},
                {Material.ORANGE_CONCRETE_POWDER, "§6B2"},
                {Material.ORANGE_WOOL, "§6B1"},
                {Material.YELLOW_WOOL, "§eN"},
                {Material.LIME_WOOL, "§aP1"},
                {Material.LIME_CONCRETE_POWDER, "§aP2"},
                {Material.GREEN_CONCRETE_POWDER, "§2P3"}
        };
        for (int i = 0; i < items.length; i++) {
            player.getInventory().setItem(i, createItem((Material)items[i][0], (String)items[i][1]));
        }
    }

    private ItemStack createItem(Material material, String name) {
        ItemStack item = new ItemStack(material, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            item.setItemMeta(meta);
        }
        return item;
    }

    // --- Emergency brake ---
    public void emergencyBrake(Player player) {
        CartProperties cartProperties = CartProperties.getEditing(player);
        if (cartProperties != null) {
            TrainProperties properties = cartProperties.getTrainProperties();
            if (properties != null) properties.setSpeedLimit(0);
        }

        modeHashMap.remove(player);
        isDriving.remove(player);

        ItemStack[] hotbar = invHashMap.remove(player);
        if (hotbar != null) {
            for (int i = 0; i < 9; i++) player.getInventory().setItem(i, hotbar[i]);
        }
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                TextComponent.fromLegacyText(ChatColor.RED + "Emergency brake applied!"));
    }

    // --- Event handlers ---
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (modeHashMap.containsKey(event.getPlayer())) emergencyBrake(event.getPlayer());
    }

    @EventHandler
    public void onDismount(EntityDismountEvent event) {
        if (event.getEntity() instanceof Player player && modeHashMap.containsKey(player)) {
            emergencyBrake(player);
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        if (modeHashMap.containsKey(event.getEntity())) emergencyBrake(event.getEntity());
    }

    @EventHandler
    public void cancelDrop(PlayerDropItemEvent event) {
        if (modeHashMap.containsKey(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!modeHashMap.containsKey(player)) return;
        Action action = event.getAction();
        if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK
                || action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK) {
            event.setCancelled(true);
        }
    }

    // --- Main throttle loop ---
    public void throttleLoop() {
        for (Player player : modeHashMap.keySet()) {
            CartProperties cartProperties = CartProperties.getEditing(player);
            TrainProperties properties = cartProperties != null ? cartProperties.getTrainProperties() : null;
            if (properties == null) continue;

            // Must own train
            if (!properties.hasOwners() || !properties.getOwners().contains(player.getName().toLowerCase())) {
                player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                        TextComponent.fromLegacyText(ChatColor.AQUA + "Please claim this train to start"));
                continue;
            }

            int slot = player.getInventory().getHeldItemSlot();
            ThrottleSlot throttle = throttleMap.getOrDefault(slot, new ThrottleSlot("Unknown", ChatColor.WHITE, 0.0));

            // Require brake to start
            if (slot <= 4) isDriving.put(player, true);
            if (!isDriving.getOrDefault(player, false)) {
                player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                        TextComponent.fromLegacyText(ChatColor.AQUA + "Please hold brake to start"));
                continue;
            }

            // Update speed
            double newSpeed = Math.max(properties.getSpeedLimit() + throttle.speedChange, 0);
            properties.setSpeedLimit(newSpeed);

            // Show action bar
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                    TextComponent.fromLegacyText(throttle.color + throttle.label
                            + ChatColor.AQUA + " | Speed: " + String.format("%.2f", newSpeed) + " b/t"));
        }
    }
}
