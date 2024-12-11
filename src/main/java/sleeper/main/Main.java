package sleeper.main;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.Random;
import java.util.UUID;

import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

public class Main extends JavaPlugin { 
    Voting voting;
    EventHandlers eventhandlers;
    Commands commands;
    
    DecimalFormat dfrmt = new DecimalFormat();
    Random random = new Random();

    // Setting values
    boolean useAnimation = true;
    int skipPercentage = 25;
    int skipSpeed = 100;
    boolean broadcastSleepInfo = false;

    // Variables
    ArrayList<String> skipping = new ArrayList<>();
    ArrayList<String> recentlySkipped = new ArrayList<>();
    HashMap<String, Integer> skipWorlds = new HashMap<>();
    ArrayList<UUID> ignorePlayers = new ArrayList<UUID>();
    ArrayList<UUID> debugPlayers = new ArrayList<UUID>();
    HashMap<String, Float> sleepingWorlds = new HashMap<>();
    HashMap<String, Float> playersOnline = new HashMap<>();
    BossBar bar = Bukkit.createBossBar("", BarColor.GREEN, BarStyle.SOLID);

    // Strings
    String sleepInfo = "&aSleep > &7 %percent% (%count%) out of a minimum of 25% sleeping.";
    List<String> nightSkip = List.of("&aSleep > &7At least 25% of online users sleeping (%count%), skipping the night.");
    String ignored = "&cSleep > &7You are still being ignored for sleep calculations!";
    String sleepHelpList = "&cInvalid command, valid subcommands are: ";
    String noPermission = "&cYou don't have permission for that.";

    public void onDisable() {
        PluginDescriptionFile pdfFile = this.getDescription();
        getLogger().info(pdfFile.getName() + " Has Been Disabled!");
    }

    public void onEnable() {
        PluginDescriptionFile pdfFile = this.getDescription();
        getLogger().info(pdfFile.getName() + " Version " + pdfFile.getVersion() + " Has Been Enabled!");
        int pluginId = 15317;
        Metrics metrics = new Metrics(this, pluginId);
        PluginManager pm = getServer().getPluginManager();
        voting = new Voting(this);
        eventhandlers = new EventHandlers(this, voting);
        commands = new Commands(this, voting);
        getCommand("sleep").setExecutor(commands);
        pm.registerEvents(eventhandlers, this);
        setConfig();
        loadConfig();
        dfrmt.setMaximumFractionDigits(2);
        getServer().getScheduler().scheduleSyncRepeatingTask(this, () -> {
            for (String worldName : new ArrayList<>(skipping)) {
                if (useAnimation) {
                    World world = Bukkit.getWorld(worldName);
                    long time = world.getTime();
                    world.setTime(time + skipSpeed);
                    world.setStorm(false);
                    if (time >= 24000) {
                        world.setTime(0);
                        time = 0;
                    }
                    if (time < 2000) {
                        skipping.remove(worldName);
                        Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(this, () -> { // Force sleeping count to 0 in case it has become wrong
                        	sleepingWorlds.put(worldName, 0f);
                        	recentlySkipped.remove(worldName);
                        }, 20L);
                    }
                }
            }
            voting.tick();
        }, 1L, 1L);
    }

    public void loadConfig() {
        reloadConfig();
        FileConfiguration config = this.getConfig();
        useAnimation = config.getBoolean("UseAnimation");
        skipPercentage = config.getInt("SkipPercentage");
        skipSpeed = config.getInt("SkipSpeed");
        sleepInfo = config.getString("SleepInfo");
        // For backwards compatibility check for both types
        if (config.isList("NightSkip")) {
            nightSkip = config.getStringList("NightSkip");
        } else {
            nightSkip = List.of(config.getString("NightSkip"));
        }
        ignored = config.getString("Ignored");
        sleepHelpList = config.getString("SleepHelpList");
        noPermission = config.getString("NoPermission");
        broadcastSleepInfo = config.getBoolean("BroadcastSleepInfo");
        voting.loadConfig(config);
    }

    public void setConfig() {
        File f = new File(this.getDataFolder() + File.separator + "config.yml");
        if (f.exists()) {
            return;
        }
        FileConfiguration config = YamlConfiguration.loadConfiguration(f);
        try (InputStream defConfigStream = this.getResource("config.yml");
                InputStreamReader reader = new InputStreamReader(defConfigStream, StandardCharsets.UTF_8)) {
            FileConfiguration defconf = YamlConfiguration.loadConfiguration(reader);
            config.addDefaults(defconf);
            config.setDefaults(defconf);
            this.saveDefaultConfig();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    // Message sending system to allow skipping sending blank messages
    public void sendMessage(Player player, String message) {
        if (!message.equals("")) player.sendMessage(message);
    }

    public void sleep(Player player) {
        String pWorld = player.getWorld().getName();
        World world = Bukkit.getWorld(pWorld);
        if (ignorePlayers.contains(player.getUniqueId())) return;
        Main plugin = this;
        Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(this, new Runnable() {
            public void run() {
                if (player.isOnline() && player.isSleeping() == true) {
                    float wonline = onlinePlayers(pWorld);
                    float wsleeping = sleepingWorlds.getOrDefault(player.getWorld().getName(), 0f);
                    // Increase sleeper count
                    wsleeping++;
                    sleepingWorlds.put(pWorld, wsleeping);
                    float percentage = (wsleeping / wonline) * 100;
                    // Replace e.g. infinity percentage with 100%, if ignored players slept
                    if (percentage > 100) percentage = 100;
                    // Debug
                    if (debugPlayers.contains(player.getUniqueId())) {
                        player.sendMessage(ChatColor.YELLOW + "DEBUG: " + ChatColor.GRAY + "eventhandlers.sleeping: ");
                        sleepingWorlds.keySet().forEach(
                                world -> player.sendMessage(ChatColor.GRAY + sleepingWorlds.get(world).toString()));
                        player.sendMessage(
                                ChatColor.YELLOW + "DEBUG: " + ChatColor.GRAY + "eventhandlers.playersOnline: ");
                        playersOnline.keySet().forEach(
                                world -> player.sendMessage(ChatColor.GRAY + playersOnline.get(world).toString()));
                        player.sendMessage(
                                ChatColor.YELLOW + "DEBUG: " + ChatColor.GRAY + "skipping: " + skipping.toString());
                        player.sendMessage(
                                ChatColor.YELLOW + "DEBUG: " + ChatColor.GRAY + "voting: " + voting.votingWorlds.toString());
                    }
                    // Sleepinfo message
                    if (!broadcastSleepInfo) {
                        sendMessage(player, ChatColor.translateAlternateColorCodes('&',
                                sleepInfo.replace("%percent%", dfrmt.format(percentage) + "%")
                                        .replace("%count%", dfrmt.format(wsleeping))
                                        .replace("%player%", player.getName())));
                    } else { // Tell everyone in the world
                        for (Player players : world.getPlayers()) {
                            sendMessage(players, ChatColor.translateAlternateColorCodes('&',
                                    sleepInfo.replace("%percent%", dfrmt.format(percentage) + "%")
                                            .replace("%count%", dfrmt.format(wsleeping))
                                            .replace("%player%", player.getName())));
                        }
                    }
                    // Debug
                    if (debugPlayers.contains(player.getUniqueId())) {
                        player.sendMessage(
                                ChatColor.YELLOW + "DEBUG: " + ChatColor.GRAY + "Checking if should skip....");
                        player.sendMessage(ChatColor.YELLOW + "DEBUG: sleeping/onlineplayers : " + ChatColor.GRAY
                                + (wsleeping / wonline));
                    }
                    // Send a vote message if enabled and not done yet
                    if (voting.useVote) {
                        voting.startVote(player);
                        voting.voteYes(player);
                    }
                    // Check if skip should be done
                    if (percentage >= skipPercentage && !skipping.contains(pWorld)) { // Skip
                        if (debugPlayers.contains(player.getUniqueId())) {
                            player.sendMessage(ChatColor.YELLOW + "DEBUG: " + ChatColor.GRAY + "Skipping...");
                        }
                        String chosenMessage = nightSkip.get(random.nextInt(nightSkip.size()));
                        for (Player players : world.getPlayers()) {
                            sendMessage(players, ChatColor.translateAlternateColorCodes('&',
                                    chosenMessage.replace("%percent%", dfrmt.format(percentage) + "%")
                                            .replace("%count%", dfrmt.format(wsleeping))
                                            .replace("%player%", player.getName())));
                        }
                        skipping.add(pWorld);
                        recentlySkipped.add(pWorld);
                        if (!useAnimation) {
                            Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(plugin, new Runnable() {
                                public void run() {
                                    if (player.isOnline() && debugPlayers.contains(player.getUniqueId())) {
                                        player.sendMessage(
                                                ChatColor.YELLOW + "DEBUG: " + ChatColor.GRAY + "Skipping after delay");
                                    }
                                    world.setTime(0);
                                    world.setStorm(false);
                                    skipping.remove(pWorld);
                                    Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(plugin, new Runnable() {
                                        public void run() {
                                            sleepingWorlds.put(pWorld, 0f);
                                            recentlySkipped.remove(pWorld);
                                            if (player.isOnline() && debugPlayers.contains(player.getUniqueId())) {
                                                player.sendMessage(ChatColor.YELLOW + "DEBUG: " + ChatColor.GRAY
                                                        + "sleeping set to 0");
                                            }
                                        }
                                    }, 20L);
                                }
                            }, 120L);
                        } else {
                            if (debugPlayers.contains(player.getUniqueId())) {
                                player.sendMessage(
                                        ChatColor.YELLOW + "DEBUG: " + ChatColor.GRAY + "Skipping with animation");
                            }
                        }
                    }
                }
            }
        }, 1L);
    }

    public float onlinePlayers(String worldName) {
        // Count players to be ignored, returns it and also updates per world list
        float onlineIgnored = 0;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (ignorePlayers.contains(p.getUniqueId()) || p.getWorld().getName() != worldName
                    || p.getGameMode().equals(GameMode.SPECTATOR) || p.getGameMode().equals(GameMode.CREATIVE)) {
                onlineIgnored++;
            }
        }
        float total = Bukkit.getOnlinePlayers().size() - onlineIgnored;
        playersOnline.put(worldName, total);
        return total;
    }

    public ArrayList<Player> getOnlineIgnorers() { //TODO there are two definitions of "ignored": these in the list or those in spectator mode etc
        ArrayList<Player> players = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if(ignorePlayers.contains(p.getUniqueId())) players.add(p);
        }
        return players;
    }

}
