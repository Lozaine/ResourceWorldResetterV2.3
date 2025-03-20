package com.lozaine.ResourceWorldResetter.gui;

import com.onarandombox.MultiverseCore.display.ColorAlternator;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import com.lozaine.ResourceWorldResetter.ResourceWorldResetter;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class AdminGUI implements Listener {
    private final ResourceWorldResetter plugin;
    private final Map<UUID, GuiType> activeGuis = new HashMap<>();

    public enum GuiType {
        MAIN_MENU,
        RESET_TYPE_MENU,
        RESET_INTERVAL_MENU,
        RESET_DAY_MENU,
        WARNING_TIME_MENU,
        RESTART_TIME_MENU,
        MONTHLY_DAY_MENU
    }

    public AdminGUI(ResourceWorldResetter plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public GuiType getActiveGuiType(UUID playerId) {
        return activeGuis.get(playerId);
    }

    public void openMainMenu(Player player) {
        Inventory gui = Bukkit.createInventory(player, 27, ChatColor.DARK_AQUA + "" + ChatColor.BOLD + "Resource World Admin");

        // Current settings display with colors
        gui.setItem(4, createInfoItem(Material.BOOK, "Current Settings",
                "World: " + ChatColor.AQUA + plugin.getWorldName(),
                "Reset Type: " + ChatColor.YELLOW + capitalizeFirstLetter(plugin.getResetType()),
                "Reset Interval: " + ChatColor.GREEN + formatInterval(plugin.getResetInterval()),
                "Restart Time: " + ChatColor.GOLD + plugin.getRestartTime() + ":00",
                "Warning Time: " + ChatColor.RED + plugin.getResetWarningTime() + " minutes"));

        // Main options with improved icons and descriptions
        gui.setItem(10, createGuiItem(Material.GRASS_BLOCK, "Change World", "Set which world to reset"));
        gui.setItem(12, createGuiItem(Material.CLOCK, "Reset Type", "Daily, weekly, or monthly"));
        gui.setItem(14, createGuiItem(Material.HOPPER, "Reset Interval", "For hourly resets"));
        gui.setItem(16, createGuiItem(Material.SUNFLOWER, "Restart Time", "Hour of daily reset"));

        gui.setItem(19, createGuiItem(Material.BELL, "Warning Time", "Minutes before reset"));
        gui.setItem(21, createGuiItem(Material.TNT, "Force Reset", "Reset world immediately"));
        gui.setItem(23, createGuiItem(Material.REDSTONE, "Reload Config", "Reload all settings"));

        player.openInventory(gui);
        activeGuis.put(player.getUniqueId(), GuiType.MAIN_MENU);
    }

    // Helper method to capitalize first letter
    private String capitalizeFirstLetter(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        return text.substring(0, 1).toUpperCase() + text.substring(1).toLowerCase();
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!activeGuis.containsKey(player.getUniqueId())) return;

        event.setCancelled(true);

        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType() == Material.AIR) return;

        ItemMeta meta = clickedItem.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) return;

        String itemName = ChatColor.stripColor(meta.getDisplayName());

        switch (itemName) {
            case "Change World" -> {
                player.closeInventory();
                player.sendMessage(ChatColor.YELLOW + "Use /setworld <worldname> to set the resource world.");
            }
            case "Reset Type" -> openResetTypeMenu(player);
            case "Reset Interval" -> openResetIntervalMenu(player);
            case "Restart Time" -> {
                player.closeInventory();
                player.sendMessage(ChatColor.YELLOW + "Use /setrestarttime <hour> to set the reset time.");
            }
            case "Warning Time" -> {
                player.closeInventory();
                player.sendMessage(ChatColor.YELLOW + "Use /setwarningtime <minutes> to set the warning time.");
            }
            case "Force Reset" -> {
                player.closeInventory();
                plugin.resetResourceWorld();
                player.sendMessage(ChatColor.GREEN + "World reset initiated!");
            }
            case "Reload Config" -> {
                player.closeInventory();
                plugin.reloadConfig();
                player.sendMessage(ChatColor.GREEN + "Configuration reloaded!");
            }
            case "Back" -> openMainMenu(player);
        }
    }

    public void openResetTypeMenu(Player player) {
        Inventory gui = Bukkit.createInventory(player, 9, ChatColor.DARK_AQUA + "Select Reset Type");

        gui.setItem(2, createGuiItem(Material.PAPER, "Daily Reset", "Reset every day"));
        gui.setItem(4, createGuiItem(Material.BOOK, "Weekly Reset", "Reset on a specific day of the week"));
        gui.setItem(6, createGuiItem(Material.CLOCK, "Monthly Reset", "Reset on a specific day of the month"));

        gui.setItem(8, createGuiItem(Material.BARRIER, "Back", "Return to main menu"));

        player.openInventory(gui);
        activeGuis.put(player.getUniqueId(), GuiType.RESET_TYPE_MENU);
    }

    public void openResetIntervalMenu(Player player) {
        Inventory gui = Bukkit.createInventory(player, 9, ChatColor.DARK_AQUA + "Select Reset Interval");

        gui.setItem(2, createGuiItem(Material.CLOCK, "1 Hour", "Reset every 1 hour"));
        gui.setItem(3, createGuiItem(Material.CLOCK, "2 Hours", "Reset every 2 hours"));
        gui.setItem(4, createGuiItem(Material.CLOCK, "4 Hours", "Reset every 4 hours"));
        gui.setItem(5, createGuiItem(Material.CLOCK, "6 Hours", "Reset every 6 hours"));
        gui.setItem(6, createGuiItem(Material.BARRIER, "Back", "Return to main menu"));

        player.openInventory(gui);
        activeGuis.put(player.getUniqueId(), GuiType.RESET_INTERVAL_MENU);
    }

    public void openResetDayMenu(Player player) {
        Inventory gui = Bukkit.createInventory(player, 9, ChatColor.DARK_AQUA + "Select Reset Day");

        for (int i = 0; i < 7; i++) {
            gui.setItem(i, createGuiItem(Material.PAPER, new String[]{"Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"}[i]));
        }
        gui.setItem(8, createGuiItem(Material.BARRIER, "Back", "Return to main menu"));

        player.openInventory(gui);
        activeGuis.put(player.getUniqueId(), GuiType.RESET_DAY_MENU);
    }

    private ItemStack createGuiItem(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.WHITE + name);
            meta.setLore(Arrays.asList(lore));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createInfoItem(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.GOLD + name);
            meta.setLore(Arrays.asList(Arrays.stream(lore).map(s -> ChatColor.GRAY + s).toArray(String[]::new)));
            item.setItemMeta(meta);
        }
        return item;
    }

    public void openWarningTimeMenu(Player player) {
        Inventory gui = Bukkit.createInventory(player, 18, ChatColor.DARK_AQUA + "Select Warning Time");

        // Common warning times
        gui.setItem(0, createGuiItem(Material.CLOCK, "No Warning", "Reset without warning"));
        gui.setItem(1, createGuiItem(Material.CLOCK, "1 Minute", "Warn 1 minute before reset"));
        gui.setItem(2, createGuiItem(Material.CLOCK, "5 Minutes", "Warn 5 minutes before reset"));
        gui.setItem(3, createGuiItem(Material.CLOCK, "10 Minutes", "Warn 10 minutes before reset"));
        gui.setItem(4, createGuiItem(Material.CLOCK, "15 Minutes", "Warn 15 minutes before reset"));
        gui.setItem(5, createGuiItem(Material.CLOCK, "30 Minutes", "Warn 30 minutes before reset"));

        gui.setItem(17, createGuiItem(Material.BARRIER, "Back", "Return to main menu"));

        player.openInventory(gui);
        activeGuis.put(player.getUniqueId(), GuiType.WARNING_TIME_MENU);
    }
    public void openRestartTimeMenu(Player player) {
        Inventory gui = Bukkit.createInventory(player, 27, ChatColor.DARK_AQUA + "Select Restart Hour");

        // Create slots for each hour (0-23)
        for (int hour = 0; hour < 24; hour++) {
            String hourDisplay = hour + ":00";
            String ampm = (hour < 12) ? "AM" : "PM";
            int displayHour = (hour == 0 || hour == 12) ? 12 : hour % 12;
            String description = displayHour + ":00 " + ampm;

            gui.setItem(hour, createGuiItem(Material.CLOCK, hourDisplay, description));
        }

        gui.setItem(26, createGuiItem(Material.BARRIER, "Back", "Return to main menu"));

        player.openInventory(gui);
        activeGuis.put(player.getUniqueId(), GuiType.RESTART_TIME_MENU);
    }

    // 4. Add monthly day selection menu
    public void openMonthlyDayMenu(Player player) {
        Inventory gui = Bukkit.createInventory(player, 36, ChatColor.DARK_AQUA + "Select Monthly Reset Day");

        // Days 1-31
        for (int day = 1; day <= 31; day++) {
            gui.setItem(day - 1, createGuiItem(Material.PAPER, "Day " + day, "Reset on day " + day + " of each month"));
        }

        gui.setItem(35, createGuiItem(Material.BARRIER, "Back", "Return to main menu"));

        player.openInventory(gui);
        activeGuis.put(player.getUniqueId(), GuiType.MONTHLY_DAY_MENU);
    }

    private String formatInterval(long seconds) {
        return (seconds >= 86400) ? "Daily" : (seconds <= 0) ? "Disabled" : (seconds / 3600) + " Hours";
    }

    public boolean hasActiveGui(UUID playerUuid) {
        return activeGuis.containsKey(playerUuid);
    }

    public void removeActiveGui(UUID playerUuid) {
        activeGuis.remove(playerUuid);
    }
}
