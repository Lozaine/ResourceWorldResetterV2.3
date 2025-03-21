package com.lozaine.ResourceWorldResetter;

import com.lozaine.ResourceWorldResetter.gui.AdminGUI;
import com.lozaine.ResourceWorldResetter.gui.AdminGUIListener;
import com.lozaine.ResourceWorldResetter.utils.LogUtil;
import com.onarandombox.MultiverseCore.MultiverseCore;
import com.onarandombox.MultiverseCore.api.MVWorldManager;
import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

import static com.onarandombox.MultiverseCore.utils.FileUtils.deleteFolder;

public class ResourceWorldResetter extends JavaPlugin {
    private String worldName;
    private MultiverseCore core;
    private int restartTime;
    private int resetWarningTime;
    private String resetType;
    private int resetDay;
    private AdminGUI adminGUI;

    public String getWorldName() { return this.worldName; }
    public String getResetType() { return this.resetType; }
    public int getRestartTime() { return this.restartTime; }
    public int getResetWarningTime() { return this.resetWarningTime; }
    public int getResetDay() { return this.resetDay; }

    public void setWorldName(String name) {
        this.worldName = name;
        getConfig().set("worldName", name);
        saveConfig();
        ensureResourceWorldExists();
    }

    public void setResetType(String type) {
        this.resetType = type;
        getConfig().set("resetType", type);
        saveConfig();
        scheduleDailyReset(); // Reschedule after changing type
    }

    public void setResetDay(int day) {
        this.resetDay = day;
        getConfig().set("resetDay", day);
        saveConfig();
        scheduleDailyReset(); // Reschedule after changing day
    }

    public void setRestartTime(int hour) {
        if (hour >= 0 && hour <= 23) {
            this.restartTime = hour;
            getConfig().set("restartTime", hour);
            saveConfig();
            scheduleDailyReset(); // Reschedule after changing time

            LogUtil.log(getLogger(), "Restart time set to " + hour + ":00", Level.INFO);
        }
    }

    public void setResetWarningTime(int minutes) {
        if (minutes >= 0) {
            this.resetWarningTime = minutes;
            getConfig().set("resetWarningTime", minutes);
            saveConfig();

            LogUtil.log(getLogger(), "Reset warning time set to " + minutes + " minutes", Level.INFO);
        }
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        LogUtil.init(this);
        core = (MultiverseCore) Bukkit.getPluginManager().getPlugin("Multiverse-Core");

        if (core == null) {
            LogUtil.log(getLogger(), "Multiverse-Core not found! Disabling plugin.", Level.SEVERE);
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        loadConfig();
        adminGUI = new AdminGUI(this);
        getServer().getPluginManager().registerEvents(new AdminGUIListener(this, adminGUI), this);

        ensureResourceWorldExists();
        scheduleDailyReset();
        LogUtil.log(getLogger(), "ResourcesWorldResetter v" + getDescription().getVersion() + " enabled successfully!", Level.INFO);
    }

    @Override
    public void onDisable() {
        Bukkit.getScheduler().cancelTasks(this);
        LogUtil.log(getLogger(), "ResourceWorldResetter disabled.", Level.INFO);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender.hasPermission("resourceworldresetter.admin")) {
            switch (command.getName().toLowerCase()) {
                case "rwrgui":
                    if (sender instanceof Player player) {
                        adminGUI.openMainMenu(player);
                        return true;
                    } else {
                        sender.sendMessage(ChatColor.RED + "This command can only be used by players.");
                        return true;
                    }

                case "reloadrwr":
                    reloadConfig();
                    loadConfig();
                    scheduleDailyReset(); // Re-schedule resets after reload
                    sender.sendMessage(ChatColor.GREEN + "ResourcesWorldResetter configuration reloaded!");
                    return true;

                // Keeping resetworld for backward compatibility
                case "resetworld":
                    sender.sendMessage(ChatColor.GREEN + "Forcing resource world reset...");
                    resetResourceWorld();
                    return true;
            }
        } else {
            sender.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
            return true;
        }
        return false;
    }

    private void scheduleDailyReset() {
        Bukkit.getScheduler().cancelTasks(this);

        // For daily, weekly, monthly resets
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime nextReset = now.withHour(restartTime).withMinute(0).withSecond(0);

        // If current time is past reset time, schedule for next occurrence
        if (now.compareTo(nextReset) >= 0) {
            nextReset = nextReset.plusDays(1);
        }

        // Handle weekly resets
        if ("weekly".equals(resetType)) {
            int currentDay = now.getDayOfWeek().getValue(); // 1 (Monday) to 7 (Sunday)
            int daysUntilReset = (resetDay - currentDay + 7) % 7;
            if (daysUntilReset == 0 && now.compareTo(nextReset) >= 0) {
                daysUntilReset = 7;
            }
            nextReset = nextReset.plusDays(daysUntilReset);
            LogUtil.log(getLogger(), "Scheduled weekly reset for " + nextReset, Level.INFO);
        }
        // Handle monthly resets
        else if ("monthly".equals(resetType)) {
            LocalDateTime nextMonth = now;
            if (now.getDayOfMonth() > resetDay || (now.getDayOfMonth() == resetDay && now.compareTo(nextReset) >= 0)) {
                nextMonth = now.plusMonths(1);
            }

            int maxDay = nextMonth.getMonth().length(nextMonth.toLocalDate().isLeapYear());
            int actualResetDay = Math.min(resetDay, maxDay);
            nextReset = nextMonth.withDayOfMonth(actualResetDay).withHour(restartTime).withMinute(0).withSecond(0);
            LogUtil.log(getLogger(), "Scheduled monthly reset for " + nextReset, Level.INFO);
        }
        // Default to daily reset
        else {
            LogUtil.log(getLogger(), "Scheduled daily reset for " + nextReset, Level.INFO);
        }

        // Calculate delay in ticks and schedule the reset task
        long initialDelayTicks = Math.max(1, ChronoUnit.SECONDS.between(now, nextReset) * 20);

        // Improved logging for debug purposes
        LogUtil.log(getLogger(), "Next reset scheduled in " + (initialDelayTicks/20/60) + " minutes (" +
                (initialDelayTicks/20/60/60) + " hours)", Level.INFO);

        Bukkit.getScheduler().runTaskLater(this, () -> {
            LogUtil.log(getLogger(), "Executing scheduled reset task", Level.INFO);
            resetResourceWorld();

            // Reschedule next reset after completion
            Bukkit.getScheduler().runTaskLater(this, this::scheduleDailyReset, 100);
        }, initialDelayTicks);
    }

    public void resetResourceWorld() {
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            LogUtil.log(getLogger(), "World '" + worldName + "' not found! Attempting to create it...", Level.WARNING);
            ensureResourceWorldExists();
            world = Bukkit.getWorld(worldName);

            if (world == null) {
                LogUtil.log(getLogger(), "Failed to create world '" + worldName + "'! Reset aborted.", Level.SEVERE);
                return;
            }
        }

        // Warn players before reset if warning time is set
        if (resetWarningTime > 0) {
            Bukkit.broadcastMessage(ChatColor.YELLOW + "Resource world will reset in " + resetWarningTime + " minutes!");

            // Schedule actual reset after warning time
            Bukkit.getScheduler().runTaskLater(this, () -> {
                performReset(world);
            }, resetWarningTime * 60 * 20); // Convert minutes to ticks

            LogUtil.log(getLogger(), "Reset scheduled after " + resetWarningTime + " minute warning", Level.INFO);
        } else {
            performReset(world);
        }
    }

    private void performReset(World world) {
        double tpsBefore = getServerTPS();
        long startTime = System.currentTimeMillis();

        LogUtil.log(getLogger(), "Starting world reset process for " + worldName, Level.INFO);
        teleportPlayersSafely(world);

        MVWorldManager worldManager = core.getMVWorldManager();
        if (!worldManager.unloadWorld(worldName)) {
            LogUtil.log(getLogger(), "Failed to unload world: " + worldName + ". Retrying with forced unload.", Level.WARNING);

            // Try forcing world unload if normal unload fails
            if (!worldManager.unloadWorld(worldName, true)) {
                LogUtil.log(getLogger(), "Forced unload also failed. Aborting reset.", Level.SEVERE);
                return;
            }
        }

        CompletableFuture.runAsync(() -> {
            File worldFolder = new File(Bukkit.getWorldContainer(), worldName);
            LogUtil.log(getLogger(), "Deleting world folder: " + worldFolder.getAbsolutePath(), Level.INFO);

            if (deleteFolder(worldFolder)) {
                Bukkit.getScheduler().runTask(this, () -> {
                    LogUtil.log(getLogger(), "World folder deleted, recreating world", Level.INFO);
                    recreateWorld(worldManager);
                    long duration = System.currentTimeMillis() - startTime;
                    double tpsAfter = getServerTPS();
                    Bukkit.broadcastMessage(ChatColor.GREEN + "Resource world reset completed in " + duration + "ms (TPS: " + String.format("%.2f", tpsBefore) + " → " + String.format("%.2f", tpsAfter) + ").");
                    LogUtil.log(getLogger(), "Resource world reset completed in " + duration + "ms", Level.INFO);
                });
            } else {
                LogUtil.log(getLogger(), "Failed to delete world folder: " + worldName, Level.SEVERE);
                Bukkit.getScheduler().runTask(this, () -> {
                    Bukkit.broadcastMessage(ChatColor.RED + "Resource world reset failed! Check server logs for details.");
                });
            }
        });
    }

    public double getServerTPS() {
        try {
            Object mcServer = Bukkit.getServer().getClass().getMethod("getServer").invoke(Bukkit.getServer());
            double[] recentTps = (double[]) mcServer.getClass().getField("recentTps").get(mcServer);
            return recentTps[0];
        } catch (Exception e) {
            getLogger().warning("Failed to get server TPS. Defaulting to 20.0");
            return 20.0;
        }
    }

    public void teleportPlayersSafely(World world) {
        World defaultWorld = Bukkit.getWorlds().get(0); // Get server's default world
        Location spawn = defaultWorld.getSpawnLocation();

        for (Player player : world.getPlayers()) {
            player.teleport(spawn);
            player.sendMessage(ChatColor.GREEN + "You have been teleported to safety - the resource world is being reset.");
            LogUtil.log(getLogger(), "Teleported " + player.getName() + " out of resource world", Level.INFO);
        }
    }

    public void recreateWorld(MVWorldManager worldManager) {
        boolean success = worldManager.addWorld(
                worldName,
                World.Environment.NORMAL,
                null,
                WorldType.NORMAL,
                true,
                "DEFAULT"
        );

        if (success) {
            Bukkit.broadcastMessage(ChatColor.GREEN + "The resource world has been reset!");
            LogUtil.log(getLogger(), "World recreation successful", Level.INFO);
        } else {
            Bukkit.broadcastMessage(ChatColor.RED + "Failed to recreate the resource world!");
            LogUtil.log(getLogger(), "Failed to recreate world: " + worldName, Level.SEVERE);
        }
    }

    public void ensureResourceWorldExists() {
        MVWorldManager worldManager = core.getMVWorldManager();
        if (!worldManager.isMVWorld(worldName)) {
            LogUtil.log(getLogger(), "Resource world doesn't exist, creating: " + worldName, Level.INFO);
            boolean success = worldManager.addWorld(
                    worldName,
                    World.Environment.NORMAL,
                    null,
                    WorldType.NORMAL,
                    true,
                    "DEFAULT"
            );
            LogUtil.log(getLogger(), "Created resource world: " + worldName + ", Success: " + success, Level.INFO);
        } else {
            LogUtil.log(getLogger(), "Resource world exists: " + worldName, Level.INFO);
        }
    }

    public void loadConfig() {
        reloadConfig();
        worldName = getConfig().getString("worldName", "Resources");
        restartTime = getConfig().getInt("restartTime", 3);
        resetWarningTime = getConfig().getInt("resetWarningTime", 5);
        resetType = getConfig().getString("resetType", "daily");
        resetDay = getConfig().getInt("resetDay", 1);

        LogUtil.log(getLogger(), "Configuration loaded: worldName=" + worldName +
                ", resetType=" + resetType + ", restartTime=" + restartTime, Level.INFO);
    }
}
