package tjt.winsanmwtv.basicThrottle;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.tc.attachments.control.CartAttachmentSeat;
import com.bergerkiller.bukkit.tc.events.seat.MemberSeatEnterEvent;
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
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.configuration.file.FileConfiguration;

import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class Throttle implements Listener, CommandExecutor {

    private final Map<Player, Byte> modeHashMap = new HashMap<>();
    private final Map<Player, ItemStack[]> invHashMap = new HashMap<>();
    private final Map<Player, Boolean> isDriving = new HashMap<>();

    record ThrottleSlot(String label, ChatColor color, double speedChange) {}
    private final Map<Integer, ThrottleSlot> throttleMap = Map.of(
            0, new ThrottleSlot("EB", ChatColor.DARK_RED, -0.012),   // Emergency Brake (strong but not extreme)
            1, new ThrottleSlot("B4", ChatColor.RED, -0.008),        // Full service brake (reduced)
            2, new ThrottleSlot("B3", ChatColor.RED, -0.006),       // Medium brake
            3, new ThrottleSlot("B2", ChatColor.GOLD, -0.004),       // Light brake
            4, new ThrottleSlot("B1", ChatColor.GOLD, -0.002),      // Minimal brake
            5, new ThrottleSlot("N", ChatColor.YELLOW, 0.0),         // Neutral
            6, new ThrottleSlot("P1", ChatColor.GREEN, 0.001),       // Low power
            7, new ThrottleSlot("P2", ChatColor.GREEN, 0.003),       // Medium power
            8, new ThrottleSlot("P3", ChatColor.DARK_GREEN, 0.005)        // High power (slightly reduced)
    );

    private final BasicThrottle plugin;
    private final FileConfiguration invConfig;
    private final File invFile;

    public Throttle(BasicThrottle plugin, FileConfiguration invConfig, File invFile) {
        this.plugin = plugin;
        this.invConfig = invConfig;
        this.invFile = invFile;
    }

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

            ItemStack[] hotbar = new ItemStack[9];
            for (int i = 0; i < 9; i++) hotbar[i] = player.getInventory().getItem(i);
            invHashMap.put(player, hotbar);

            for (int i = 0; i < 9; i++) invConfig.set("savedHotbars." + player.getUniqueId() + "." + i, hotbar[i]);
            saveInvConfig();

            modeHashMap.put(player, (byte)0);
            player.sendMessage(ChatColor.AQUA + "Do not forget to do" +
                    ChatColor.YELLOW + " /train launch " + ChatColor.AQUA + "to start.");
            inventoryHotbar(player);
            return true;
        }

        if (arg.equals("off") || arg.equals("disable")) {
            if (!modeHashMap.containsKey(player)) {
                player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                        TextComponent.fromLegacyText(ChatColor.RED + "You're not in driving mode."));
                return true;
            }
            emergencyBrake(player);
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                    TextComponent.fromLegacyText(ChatColor.AQUA + "Driving mode deactivated."));
            return true;
        }

        player.sendMessage(ChatColor.YELLOW + "Usage: /traindrive <on|off>");
        return true;
    }

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

    public void emergencyBrake(Player player) {
        CartProperties cartProperties = CartProperties.getEditing(player);
        double speed = 0.0;
        if (cartProperties != null) {
            TrainProperties properties = cartProperties.getTrainProperties();
            if (properties != null) {
                speed = properties.getSpeedLimit();
                properties.setSpeedLimit(0);
            }
        }

        // Remove from maps
        modeHashMap.remove(player);
        isDriving.remove(player);

        // Restore hotbar
        ItemStack[] hotbar = invHashMap.remove(player);
        if (hotbar != null) {
            for (int i = 0; i < 9; i++) player.getInventory().setItem(i, hotbar[i]);
        }

        // Remove from inventory.yml
        invConfig.set("savedHotbars." + player.getUniqueId(), null);
        saveInvConfig();

        // Action bar feedback
        if (speed == 0.0) {
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                    TextComponent.fromLegacyText(ChatColor.AQUA + "Driving mode deactivated."));
        } else {
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                    TextComponent.fromLegacyText(ChatColor.RED + "Emergency brake applied!"));
        }
    }


    private void saveInvConfig() {
        try {
            invConfig.save(invFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save inventory.yml: " + e.getMessage());
        }
    }

    // Event: Player enters a seat
    @EventHandler
    public void onDriverSeatEnter(MemberSeatEnterEvent event) {
        if (event.getEntity() instanceof Player player) {
            CartAttachmentSeat seat = event.getSeat();
            checkDriverSeat(player, seat);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player && modeHashMap.containsKey(player)) {
            // Cancel moving items in the hotbar
            event.setCancelled(true);
        }
    }

    // --- Check if this seat is driver_seat ---
    public void checkDriverSeat(Player player, CartAttachmentSeat seat) {
        if (seat.getEntity() != player) return; // Must be the player in the seat

        // Check ONLY this seat's config
        ConfigurationNode node = seat.getConfig();
        boolean isDriverSeat = false;

        List<String> names = node.getList("names", String.class);
        if (names != null) {
            for (String name : names) {
                if (name.equalsIgnoreCase("driver_seat")) {
                    isDriverSeat = true;
                    break;
                }
            }
        }

        // Sync model (still fine here, optional)
        seat.getMember().getProperties().getModel().sync();

        if (isDriverSeat) {
            if (modeHashMap.containsKey(player)) {
                player.sendMessage(ChatColor.RED + "You're already in driving mode.");
            } else {
                player.sendMessage(ChatColor.AQUA + "Do not forget to do" +
                        ChatColor.YELLOW + " /train launch " + ChatColor.AQUA + "to start.");

                // Save hotbar in memory
                ItemStack[] hotbar = new ItemStack[9];
                for (int i = 0; i < 9; i++) hotbar[i] = player.getInventory().getItem(i);
                invHashMap.put(player, hotbar);

                // Save hotbar to inventory.yml
                for (int i = 0; i < 9; i++) {
                    invConfig.set("savedHotbars." + player.getUniqueId() + "." + i, hotbar[i]);
                }
                try {
                    invConfig.save(invFile);
                } catch (IOException e) {
                    plugin.getLogger().warning("Failed to save inventory.yml: " + e.getMessage());
                }

                // Enable driving mode
                modeHashMap.put(player, (byte) 0);
                inventoryHotbar(player);
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (modeHashMap.containsKey(player)) emergencyBrake(player);
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

    public void throttleLoop() {
        for (Player player : modeHashMap.keySet()) {
            CartProperties cartProperties = CartProperties.getEditing(player);
            TrainProperties properties = cartProperties != null ? cartProperties.getTrainProperties() : null;
            if (properties == null) continue;

            if (!properties.hasOwners() || !properties.getOwners().contains(player.getName().toLowerCase())) {
                player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                        TextComponent.fromLegacyText(ChatColor.GREEN + "Please claim this train to start"));
                continue;
            }

            int slot = player.getInventory().getHeldItemSlot();

            ThrottleSlot throttle = throttleMap.getOrDefault(slot, new ThrottleSlot("Unknown", ChatColor.WHITE, 0.0));

            if (slot <= 4) isDriving.put(player, true);
            if (!isDriving.getOrDefault(player, false)) {
                player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                        TextComponent.fromLegacyText(ChatColor.GRAY + "Please hold brake to start"));
                continue;
            }

            double newSpeedBT = Math.max(properties.getSpeedLimit() + throttle.speedChange, 0);

            if (slot > 5) {
                double speed = properties.getSpeedLimit();
                double step = 0;

                if (speed > 1.6) step = 0.006; // no reduction
                else if (speed > 1.2) step = 0.004;
                else if (speed > 0.6) step = 0.002;
                // Clamp reducedSpeed to >= 0
                double reducedSpeed = Math.max(speed - step, 0);
                // Clamp final result to >= 0
                newSpeedBT = Math.max(reducedSpeed + throttle.speedChange, 0);
            }

            properties.setSpeedLimit(newSpeedBT);
            double newSpeedKMH = newSpeedBT * 72.0;

            player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                    TextComponent.fromLegacyText(throttle.color + throttle.label
                            + ChatColor.AQUA + " | Speed: " + ChatColor.YELLOW + String.format("%.2f", newSpeedBT) + ChatColor.AQUA + " b/t"
                            + ChatColor.BLUE + " (" + ChatColor.GOLD + String.format("%.0f", newSpeedKMH) + ChatColor.BLUE + " km/h)"));
        }
    }

    public void shutdown() {
        for (Player player : new ArrayList<>(modeHashMap.keySet())) {
            emergencyBrake(player);
        }
    }
}
