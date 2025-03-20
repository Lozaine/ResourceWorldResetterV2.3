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
    private long resetInterval;
    private int restartTime;
    private int resetWarningTime;
    private String resetType;
    private int resetDay;
    private AdminGUI adminGUI;

    public String getWorldName() { return this.worldName; }
    public String getResetType() { return this.resetType; }
    public long getResetInterval() { return this.resetInterval; }
    public int getRestartTime() { return this.restartTime; }
    public int getResetWarningTime() { return this.resetWarningTime; }

    public void setResetType(String type) {
        this.resetType = type;
        getConfig().set("resetType", type);
        saveConfig();
    }

    public void setResetInterval(int interval) {
        this.resetInterval = interval;
        getConfig().set("resetInterval", interval);
        saveConfig();
        scheduleDailyReset(); // Reschedule after changing interval
    }

    public void setResetDay(int day) {
        this.resetDay = day;
        getConfig().set("resetDay", day);
        saveConfig();
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
        LogUtil.log(getLogger(), "ResourcesWorldResetter enabled successfully!", Level.INFO);
    }

    @Override
    public void onDisable() {
        LogUtil.log(getLogger(), "ResourceWorldResetter disabled.", Level.INFO);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender.hasPermission("resourceworldresetter.admin")) {
            switch (command.getName().toLowerCase()) {
                case "resourcegui":
                case "rwadmin": // Support both command names
                    if (sender instanceof Player player) {
                        adminGUI.openMainMenu(player);
                    } else {
                        sender.sendMessage(ChatColor.RED + "This command can only be used by players.");
                    }
                    return true;

                case "resetworld":
                    sender.sendMessage(ChatColor.GREEN + "Forcing resource world reset...");
                    resetResourceWorld();
                    return true;

                case "reloadworldresetter":
                    reloadConfig();
                    loadConfig();
                    sender.sendMessage(ChatColor.GREEN + "ResourcesWorldResetter configuration reloaded!");
                    return true;
            }
        } else {
            sender.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
        }
        return false;
    }

    private void scheduleDailyReset() {
        Bukkit.getScheduler().cancelTasks(this);
        resetType = getConfig().getString("resetType", "daily");
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime nextReset = now.withHour(restartTime).withMinute(0).withSecond(0);

        if (now.compareTo(nextReset) >= 0) {
            nextReset = nextReset.plusDays(1);
        }

        if (resetInterval > 0 && resetInterval < 86400) {
            long intervalTicks = resetInterval * 20;
            Bukkit.getScheduler().runTaskTimer(this, this::resetResourceWorld, intervalTicks, intervalTicks);
            LogUtil.log(getLogger(), "Scheduled reset every " + (resetInterval / 3600) + " hours", Level.INFO);
            return;
        }

        // Handle daily, weekly, and monthly resets
        if ("weekly".equals(resetType)) {
            int currentDay = now.getDayOfWeek().getValue(); // 1 (Monday) to 7 (Sunday)
            int daysUntilReset = (resetDay - currentDay + 7) % 7;
            if (daysUntilReset == 0 && now.compareTo(nextReset) >= 0) {
                daysUntilReset = 7;
            }
            nextReset = nextReset.plusDays(daysUntilReset);
            LogUtil.log(getLogger(), "Scheduled weekly reset for " + nextReset, Level.INFO);
        } else if ("monthly".equals(resetType)) {
            LocalDateTime nextMonth = now;
            if (now.getDayOfMonth() > resetDay || (now.getDayOfMonth() == resetDay && now.compareTo(nextReset) >= 0)) {
                nextMonth = now.plusMonths(1);
            }

            int maxDay = nextMonth.getMonth().length(nextMonth.toLocalDate().isLeapYear());
            int actualResetDay = Math.min(resetDay, maxDay);
            nextReset = nextMonth.withDayOfMonth(actualResetDay).withHour(restartTime).withMinute(0).withSecond(0);
            LogUtil.log(getLogger(), "Scheduled monthly reset for " + nextReset, Level.INFO);
        } else {
            // Daily reset
            LogUtil.log(getLogger(), "Scheduled daily reset for " + nextReset, Level.INFO);
        }

        long initialDelayTicks = ChronoUnit.SECONDS.between(now, nextReset) * 20;
        Bukkit.getScheduler().runTaskLater(this, () -> {
            resetResourceWorld();
            scheduleDailyReset(); // Reschedule after reset
        }, initialDelayTicks);
    }

    public void resetResourceWorld() {
        World world = Bukkit.getWorld(worldName);
        if (world == null) return;

        // Warn players before reset if warning time is set
        if (resetWarningTime > 0) {
            Bukkit.broadcastMessage(ChatColor.YELLOW + "Resource world will reset in " + resetWarningTime + " minutes!");

            // Schedule actual reset after warning time
            Bukkit.getScheduler().runTaskLater(this, () -> {
                performReset(world);
            }, resetWarningTime * 60 * 20); // Convert minutes to ticks
        } else {
            performReset(world);
        }
    }

    private void performReset(World world) {
        double tpsBefore = getServerTPS();
        long startTime = System.currentTimeMillis();
        teleportPlayersSafely(world);

        MVWorldManager worldManager = core.getMVWorldManager();
        if (!worldManager.unloadWorld(worldName)) {
            LogUtil.log(getLogger(), "Failed to unload world: " + worldName, Level.WARNING);
            return;
        }

        CompletableFuture.runAsync(() -> {
            File worldFolder = new File(Bukkit.getWorldContainer(), worldName);
            if (deleteFolder(worldFolder)) {
                Bukkit.getScheduler().runTask(this, () -> {
                    recreateWorld(worldManager);
                    long duration = System.currentTimeMillis() - startTime;
                    double tpsAfter = getServerTPS();
                    Bukkit.broadcastMessage(ChatColor.GREEN + "Resource world reset completed in " + duration + "ms (TPS: " + tpsBefore + " -> " + tpsAfter + ").");
                    LogUtil.log(getLogger(), "Resource world reset completed in " + duration + "ms", Level.INFO);
                });
            } else {
                LogUtil.log(getLogger(), "Failed to delete world folder: " + worldName, Level.SEVERE);
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
            player.sendMessage(ChatColor.GREEN + "Teleported safely out of resource world.");
            LogUtil.log(getLogger(), "Teleported " + player.getName() + " out of resource world", Level.INFO);
        }
    }

    public void recreateWorld(MVWorldManager worldManager) {
        boolean success = worldManager.addWorld(worldName, World.Environment.NORMAL, null, WorldType.NORMAL, true, "DEFAULT");
        Bukkit.broadcastMessage(success ? ChatColor.GREEN + "The resource world has been reset!" : ChatColor.RED + "Failed to recreate the resource world!");

        if (!success) {
            LogUtil.log(getLogger(), "Failed to recreate world: " + worldName, Level.SEVERE);
        }
    }

    public void ensureResourceWorldExists() {
        MVWorldManager worldManager = core.getMVWorldManager();
        if (!worldManager.isMVWorld(worldName)) {
            boolean success = worldManager.addWorld(worldName, World.Environment.NORMAL, null, WorldType.NORMAL, true, "DEFAULT");
            LogUtil.log(getLogger(), "Created resource world: " + worldName + ", Success: " + success, Level.INFO);
        }
    }

    public void loadConfig() {
        reloadConfig();
        worldName = getConfig().getString("worldName", "Resources");
        resetInterval = getConfig().getLong("resetInterval", 86400);
        restartTime = getConfig().getInt("restartTime", 3);
        resetWarningTime = getConfig().getInt("resetWarningTime", 5);
        resetType = getConfig().getString("resetType", "daily");
        resetDay = getConfig().getInt("resetDay", 1);

        LogUtil.log(getLogger(), "Configuration loaded: worldName=" + worldName +
                ", resetType=" + resetType + ", interval=" + resetInterval +
                ", restartTime=" + restartTime, Level.INFO);
    }
}
