package sleeper.main;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Random;
import java.util.StringJoiner;
import java.util.UUID;

import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import com.earth2me.essentials.Essentials;

import io.papermc.paper.plugin.configuration.PluginMeta;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import sleeper.integrations.GSitHandler;
import sleeper.integrations.AFKPlus;

public class Main extends JavaPlugin {
    MessageHandler messageHandler;
    Voting voting;
    EventHandlers eventhandlers;
    Commands commands;
    
    // Soft dependency integrations with other plugin apis
    GSitHandler gSitHandler;
    AFKPlus afkPlus;
    Essentials essentials;

    DecimalFormat dfrmt = new DecimalFormat();
    Random random = new Random();

    // Setting values
    boolean useAnimation = true;
    public int skipPercentage = 25;
    int skipSpeed = 100;
    boolean broadcastSleepInfo = false;
    boolean delaySleep = false;
    public long delaySeconds = 0;
    boolean actionbarMessages = false;
    boolean persistentSleepInfo = false;
    int persistenceTime = 30;
    
    boolean ignoreAFKPlayers = true;

    boolean checkUpdates = true;

    // Variables
    ArrayList<String> skipping = new ArrayList<>();
    ArrayList<String> recentlySkipped = new ArrayList<>();
    HashMap<String, Integer> skipWorlds = new HashMap<>();
    ArrayList<UUID> ignorePlayers = new ArrayList<UUID>();
    ArrayList<UUID> debugPlayers = new ArrayList<UUID>();
    HashMap<String, Float> sleepingWorlds = new HashMap<>();
    HashMap<String, Float> playersOnline = new HashMap<>();
    HashMap<String, ArrayList<UUID>> worldSleepers = new HashMap<>();
    HashMap<String, Integer> worldLatestSleepAge = new HashMap<>();
    HashMap<String, String> worldLatestSleepMessage = new HashMap<>();
    BossBar bar = Bukkit.createBossBar("", BarColor.GREEN, BarStyle.SOLID);

    // Strings
    public String sleepInfo = "&aSleep > &7 %percent% (%count%) out of a minimum of 25% sleeping.";
    List<String> nightSkip = List
            .of("&aSleep > &7At least 25% of online users sleeping (%count%), skipping the night.");
    String ignored = "&cSleep > &7You are still being ignored for sleep calculations!";
    String noPermission = "&cYou don't have permission for that.";

    public void onDisable() {
        PluginMeta pdfFile = this.getPluginMeta();
        getLogger().info(pdfFile.getName() + " Has Been Disabled!");
    }

    public void onEnable() {
        PluginMeta pdfFile = this.getPluginMeta();
        getLogger().info(pdfFile.getName() + " Version " + pdfFile.getVersion() + " Has Been Enabled!");
        int pluginId = 15317;
        Metrics metrics = new Metrics(this, pluginId);
        PluginManager pm = getServer().getPluginManager();
        messageHandler = new MessageHandler(this);
        voting = new Voting(this, messageHandler);
        eventhandlers = new EventHandlers(this, voting, messageHandler);
        commands = new Commands(this, voting, messageHandler);
        getCommand("sleep").setExecutor(commands);
        getCommand("sleep").setTabCompleter(new CommandCompletion());
        pm.registerEvents(eventhandlers, this);
        StringJoiner integrationsFound = new StringJoiner(", ", "", ".");
        if (pm.getPlugin("GSit") != null) {
            gSitHandler = new GSitHandler(this, voting, messageHandler);
            pm.registerEvents(gSitHandler, this);
            integrationsFound.add("GSit");
        }
        if (pm.getPlugin("AFKPlus") != null) {
            afkPlus = new AFKPlus();
            integrationsFound.add("AFKPlus");
        }
        if (pm.getPlugin("Essentials") != null) {
            essentials = (Essentials) pm.getPlugin("Essentials");
            integrationsFound.add("Essentials");
        }
        if (integrationsFound.length() != 0) {
            getLogger().info("Loaded integrations for: " + integrationsFound.toString());
        } else {
            getLogger().info("Found no plugin integrations.");
        }
        setConfig();
        loadConfig();
        dfrmt.setMaximumFractionDigits(2);
        // Every tick
        getServer().getGlobalRegionScheduler().runAtFixedRate(this, task -> {
            for (String worldName : new ArrayList<>(skipping)) {
                if (useAnimation) {
                    World world = Bukkit.getWorld(worldName);
                    long time = world.getTime();
                    world.setTime(time + skipSpeed);
                    world.setStorm(false);
                    if (time%24000 < 2000) {
                        /*
                        world.setTime(0);
                        time = 0;
                        */
                        messageHandler.broadcastDebug("Looks like it's time < 2000, stop the animation.");
                        skipping.remove(worldName);
                        Bukkit.getServer().getGlobalRegionScheduler().runDelayed(this, delayTask -> { // Force sleeping count
                                                                                                // to 0 in case it has
                                                                                                // become wrong
                            sleepingWorlds.put(worldName, 0f);
                            recentlySkipped.remove(worldName);
                        }, 20L);
                    }
                }
            }
            for (String worldName : worldLatestSleepAge.keySet()) {
                int age = worldLatestSleepAge.get(worldName);
                if (age < persistenceTime*20) {
                    worldLatestSleepAge.replace(worldName, age+1);
                } else {
                    worldLatestSleepAge.remove(worldName);
                    worldLatestSleepMessage.remove(worldName);
                }
            }
            voting.tick();
        }, 1L, 1L);
        // Every second
        getServer().getGlobalRegionScheduler().runAtFixedRate(this, task -> {
            // Go over every world
            for (String worldName : worldSleepers.keySet()) {
                World world = Bukkit.getWorld(worldName);
                long time = world.getTime();
                // Just in case, clear world sleeper lists if it is the morning
                if (time%24000 < 2000) {
                    worldSleepers.get(worldName).clear();
                    worldLatestSleepAge.remove(worldName);
                    worldLatestSleepMessage.remove(worldName);
                }
            }
            for (String worldName : worldLatestSleepAge.keySet()) {
                if (!worldLatestSleepMessage.containsKey(worldName)) return;
                if (!persistentSleepInfo) return;
                if (!actionbarMessages) return;
                String msg = worldLatestSleepMessage.get(worldName);
                if (!broadcastSleepInfo) {
                    for (UUID uuid : worldSleepers.get(worldName)) {
                        messageHandler.sendMessage(Bukkit.getPlayer(uuid), msg);
                    }
                } else {
                    for (Player player : Bukkit.getWorld(worldName).getPlayers()) {
                        messageHandler.sendMessage(player, msg);
                    }
                }
            }
        }, 2L, 20L);
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
        noPermission = config.getString("NoPermission");
        broadcastSleepInfo = config.getBoolean("BroadcastSleepInfo");
        delaySeconds = config.getLong("DelaySeconds");
        delaySleep = config.getBoolean("DelaySleep");
        actionbarMessages = config.getBoolean("ActionbarMessages");
        persistentSleepInfo = config.getBoolean("PersistentSleepInfo");
        persistenceTime = config.getInt("PersistenceTime");
        if (!delaySleep) {
            delaySeconds = 0;
        }
        checkUpdates = config.getBoolean("CheckForUpdates");
        ignoreAFKPlayers = config.getBoolean("IgnoreAFKPlayers");
        messageHandler.loadConfig(config);
        voting.loadConfig(config);
        commands.loadConfig(config);
        if (gSitHandler != null) gSitHandler.loadConfig(config);
        if (checkUpdates) updateChecker();
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

    public void updateChecker() {
        new UpdateChecker(this, 102406).getVersion(version -> {
            if (!this.getPluginMeta().getVersion().equals(version)) {
                getLogger().warning("There is a new update available. New version is " + version + " and you are on "
                        + this.getPluginMeta().getVersion() + ".");
            }
        });
    }

    public void sleep(Player player, boolean skipSleepCheck) {
        World world = player.getWorld();
        String pWorld = world.getName();
        Main plugin = this;
        if (!worldSleepers.keySet().contains(pWorld)) worldSleepers.put(pWorld, new ArrayList<>());
        if (ignorePlayers.contains(player.getUniqueId())) return;
        Bukkit.getServer().getGlobalRegionScheduler().runDelayed(this, ScheduledTask -> {
            if (player.isOnline() && (player.isSleeping() == true || skipSleepCheck)) {
                float wonline = onlinePlayers(pWorld);
                float wsleeping = sleepingWorlds.getOrDefault(player.getWorld().getName(), 0f);
                // Increase sleeper count
                wsleeping++;
                sleepingWorlds.put(pWorld, wsleeping);
                worldSleepers.get(pWorld).add(player.getUniqueId());
                worldLatestSleepAge.put(pWorld, 0);
                float percentage = (wsleeping / wonline) * 100;
                int countNeeded = (int) Math.ceil(wonline * (skipPercentage / 100d));
                // Replace e.g. infinity percentage with 100%, if ignored players slept
                if (percentage > 100) percentage = 100;
                // Debug
                if (debugPlayers.contains(player.getUniqueId())) {
                    player.sendMessage(Component.text("DEBUG: ").color(NamedTextColor.YELLOW)
                            .append(Component.text("eventhandlers.sleeping: ").color(NamedTextColor.GRAY)));
                    sleepingWorlds.keySet().forEach(
                            lworld -> player.sendMessage(Component.text(sleepingWorlds.get(lworld).toString()).color(NamedTextColor.GRAY)));
                    player.sendMessage(Component.text("DEBUG: ").color(NamedTextColor.YELLOW)
                            .append(Component.text("eventhandlers.playersOnline: ").color(NamedTextColor.GRAY)));
                    playersOnline.keySet().forEach(
                            lworld -> player.sendMessage(Component.text(playersOnline.get(lworld).toString()).color(NamedTextColor.GRAY)));
                    player.sendMessage(Component.text("DEBUG: ").color(NamedTextColor.YELLOW)
                            .append(Component.text("skipping: " + skipping.toString()).color(NamedTextColor.GRAY)));
                    player.sendMessage(Component.text("DEBUG: ").color(NamedTextColor.YELLOW)
                            .append(Component.text("voting: " + voting.votingWorlds.toString()).color(NamedTextColor.GRAY)));
                }
                // Sleepinfo message
                String sleepInfoMsg = sleepInfo.replace("%percent%", dfrmt.format(percentage) + "%")
                        .replace("%count%", dfrmt.format(wsleeping))
                        .replace("%count_needed%", dfrmt.format(countNeeded))
                        .replace("%player%", player.getName());
                worldLatestSleepMessage.put(pWorld, sleepInfoMsg);
                if (!broadcastSleepInfo) {
                    messageHandler.sendMessage(player, sleepInfoMsg);
                } else { // Tell everyone in the world
                    for (Player players : world.getPlayers()) {
                        messageHandler.sendMessage(players, sleepInfoMsg);
                    }
                }
                // Debug
                if (debugPlayers.contains(player.getUniqueId())) {
                    messageHandler.sendDebug(player, "Checking if should skip....");
                    messageHandler.sendDebug(player, "sleeping/onlineplayers : " + (wsleeping / wonline));
                }
                // Send a vote message if enabled and not done yet
                if (voting.useVote) {
                    voting.startVote(player);
                    voting.voteYes(player);
                }
                // Check if skip should be done
                if (percentage >= skipPercentage && !skipping.contains(pWorld)) { // Skip
                    messageHandler.broadcastDebug("Skipping...");
                    worldLatestSleepAge.remove(pWorld);
                    String chosenMessage = nightSkip.get(random.nextInt(nightSkip.size()));
                    for (Player players : world.getPlayers()) {
                        messageHandler.sendMessage(players,
                                chosenMessage.replace("%percent%", dfrmt.format(percentage) + "%")
                                .replace("%count%", dfrmt.format(wsleeping))
                                .replace("%count_needed%", dfrmt.format(countNeeded))
                                .replace("%player%", player.getName()));
                    }

                    worldSleepers.get(pWorld).clear();
                    skipping.add(pWorld);
                    recentlySkipped.add(pWorld);
                    if (!useAnimation) {
                        Bukkit.getServer().getGlobalRegionScheduler().runDelayed(plugin, delayTask -> {
                            messageHandler.broadcastDebug("Skipping after delay");
                            world.setTime(world.getTime()+24000-(world.getTime()%24000));
                            world.setStorm(false);
                            skipping.remove(pWorld);
                            Bukkit.getServer().getGlobalRegionScheduler().runDelayed(plugin, lastTask -> {
                                sleepingWorlds.put(pWorld, 0f);
                                recentlySkipped.remove(pWorld);
                                if (player.isOnline() && debugPlayers.contains(player.getUniqueId())) {
                                    messageHandler.sendDebug(player, "sleeping set to 0");
                                }
                            }, 20L);
                        }, 120L);
                    } else {
                        messageHandler.broadcastDebug("Skipping with animation");
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
                    || p.getGameMode().equals(GameMode.SPECTATOR) || p.getGameMode().equals(GameMode.CREATIVE)
                    || (ignoreAFKPlayers && isAFK(p))) {
                onlineIgnored++;
            }
        }
        float total = Bukkit.getOnlinePlayers().size() - onlineIgnored;
        playersOnline.put(worldName, total);
        return total;
    }
    
    public boolean isAFK(Player player) {
        if (afkPlus != null && afkPlus.IsPlayerAFK(player)) return true;
        if (essentials != null && essentials.getUser(player).isAfk()) return true;
        return false;
    }
    
    public HashMap<String, Float> getPlayersOnline() {
        return playersOnline;
    }
    
    public HashMap<String, Float> getSleepingWorlds() {
        return sleepingWorlds;
    }
    
    public ArrayList<String> getRecentlySkipped() {
        return recentlySkipped;
    }
    
    public ArrayList<UUID> getWorldSleepers(String worldName) {
        return worldSleepers.getOrDefault(worldName, new ArrayList<>());
    }

    public ArrayList<Player> getOnlineIgnorers() { // TODO there are two definitions of "ignored": these in the list or
                                                   // those in spectator mode etc
        ArrayList<Player> players = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (ignorePlayers.contains(p.getUniqueId()))
                players.add(p);
        }
        return players;
    }

}
