package sleeper.main;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;

public class Main extends JavaPlugin {
    public final Logger logger = getLogger();
    public static Main plugin;
    Commands commands;
    EventHandlers eventhandlers;

    // Setting values
    boolean useAnimation = true;
    int skipPercentage = 25;
    int skipSpeed = 100;

    // Voting setting values
    boolean useVote = false;
    int yesMultiplier = 1;
    int noMultiplier = 1;
    int skipVotePercent = 50;

    // Vote variables
    ArrayList<String> voting = new ArrayList<>();
    HashMap<String, String> yesVotes = new HashMap<>();
    HashMap<String, String> noVotes = new HashMap<>();

    // Variables
    ArrayList<String> skipping = new ArrayList<>();
    ArrayList<String> recentlySkipped = new ArrayList<>();
    HashMap<String, Integer> skipWorlds = new HashMap<>();
    ArrayList<Player> ignorePlayers = new ArrayList<Player>();
    ArrayList<Player> debugPlayers = new ArrayList<Player>();
    HashMap<String, Float> sleepingWorlds = new HashMap<>();
    HashMap<String, Float> playersOnline = new HashMap<>();

    // Strings
    String sleepInfo = "&aSleep > &7 %percent% (%count%) out of a minimum of 25% sleeping.";
    String nightSkip = "&aSleep > &7At least 25% of online users sleeping (%count%), skipping the night.";
    String ignored = "&cSleep > &7You are still being ignored for sleep calculations!";
    String voteTitle = "&aSleep > &7Vote below on skipping the night:";
    String voteYes = "&a&lYes";
    String voteNo = "&c&lNo";
    String votedYes = "&aYou voted to skip the night.";
    String votedNo = "&aYou voted not to skip the night.";
    String noVote = "&cYour world isn't voting on a night skip.";
    String alreadyYes = "&cYou have already voted yes.";
    String alreadyNo = "&cYou have already voted no.";
    String sleepHelp = "&cInvalid command, valid subcommands are yes, no, votes.";
    String noPermission = "&cYou don't have permission for that.";
    String listVotes = "&aYes: &7%yes% &cNo: &7%no%";

    public void onDisable() {
        PluginDescriptionFile pdfFile = this.getDescription();
        this.logger.info(pdfFile.getName() + " Has Been Disabled!");
    }

    public void onEnable() {
        plugin = this;
        PluginDescriptionFile pdfFile = this.getDescription();
        this.logger.info(pdfFile.getName() + " Version " + pdfFile.getVersion() + " Has Been Enabled!");
        int pluginId = 15317;
        Metrics metrics = new Metrics(this, pluginId);
        PluginManager pm = getServer().getPluginManager();
        eventhandlers = new EventHandlers(this);
        commands = new Commands(this, eventhandlers);
        getCommand("ignoresleep").setExecutor(commands);
        getCommand("sleepdata").setExecutor(commands);
        getCommand("sleepreload").setExecutor(commands);
        getCommand("sleep").setExecutor(commands);
        pm.registerEvents(eventhandlers, this);
        setConfig();
        loadConfig();
        plugin.getServer().getScheduler().scheduleSyncRepeatingTask(this, () -> {
            ArrayList<String> remove = new ArrayList<>();
            for (String worldName : skipping) {
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
                        remove.add(worldName);
                        Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(plugin, new Runnable() {
                            public void run() { // Force sleeping count to 0 in case it has become wrong
                                sleepingWorlds.put(worldName, 0f);
                                recentlySkipped.remove(worldName);
                            }
                        }, 20L);
                    }
                }
            }
            remove.forEach(name -> skipping.remove(name));
            remove.clear();
            for (String worldName : voting) {
                World world = Bukkit.getWorld(worldName);
                long time = world.getTime();
                if (skipping.contains(worldName)) continue;
                if (time < 2000) { // End vote, day time.
                    remove.add(worldName);
                    // Clear votes from that world
                    ArrayList<String> removeVotes = new ArrayList<>();
                    for (Entry<String, String> vote : yesVotes.entrySet()) {
                        if (vote.getValue().equals(worldName)) removeVotes.add(vote.getKey());
                    }
                    removeVotes.forEach(name -> yesVotes.remove(name));
                    removeVotes.clear();
                    for (Entry<String, String> vote : noVotes.entrySet()) {
                        if (vote.getValue().equals(worldName)) removeVotes.add(vote.getKey());
                    }
                    removeVotes.forEach(name -> noVotes.remove(name));
                    removeVotes.clear();
                } else { // Check if the votes are enough for a skip
                    int yVotes = countYes(worldName);
                    int nVotes = countNo(worldName);
                    float skipFactor = ((yVotes * yesMultiplier) - (nVotes * noMultiplier))
                            / playersOnline.get(worldName); // Decimal yes votes - no votes divided by world players
                    float skipMargin = skipVotePercent * 0.01f;
                    if (skipFactor >= skipMargin) { //TODO add a message broadcast
                        skipping.add(worldName);
                        recentlySkipped.add(worldName);
                        getLogger().info("Skipping night by vote in " + worldName);
                    }
                }
            }
            remove.forEach(name -> voting.remove(name));
        }, 1L, 1L);
    }

    public void loadConfig() {
        reloadConfig();
        FileConfiguration config = this.getConfig();
        useAnimation = config.getBoolean("UseAnimation");
        skipPercentage = config.getInt("SkipPercentage");
        skipSpeed = config.getInt("SkipSpeed");
        sleepInfo = config.getString("SleepInfo");
        nightSkip = config.getString("NightSkip");
        ignored = config.getString("Ignored");
        useVote = config.getBoolean("VoteSkip");
        yesMultiplier = config.getInt("YesMultiplier");
        noMultiplier = config.getInt("NoMultiplier");
        skipVotePercent = config.getInt("SkipVotePercent");
        voteTitle = config.getString("VoteTitle");
        voteYes = config.getString("VoteYes");
        voteNo = config.getString("VoteNo");
        votedYes = config.getString("VotedYes");
        votedNo = config.getString("VotedNo");
        noVote = config.getString("NoVote");
        alreadyYes = config.getString("AlreadyYes");
        alreadyNo = config.getString("AlreadyNo");
        sleepHelp = config.getString("SleepHelp");
        noPermission = config.getString("NoPermission");
        listVotes = config.getString("ListVotes");
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

    public void sleep(Player player) {
        String pWorld = player.getWorld().getName();
        World world = Bukkit.getWorld(pWorld);
        if (ignorePlayers.contains(player)) return;
        Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(plugin, new Runnable() {
            public void run() {
                if (player.isOnline() && player.isSleeping() == true) {
                    float wonline = onlinePlayers(pWorld);
                    float wsleeping = sleepingWorlds.getOrDefault(player.getWorld().getName(), 0f);
                    // Increase sleeper count
                    wsleeping++;
                    sleepingWorlds.put(pWorld, wsleeping);
                    DecimalFormat dfrmt = new DecimalFormat();
                    dfrmt.setMaximumFractionDigits(2);
                    // Debug
                    if (debugPlayers.contains(player)) {
                        player.sendMessage(ChatColor.YELLOW + "DEBUG: " + ChatColor.GRAY + "eventhandlers.sleeping: ");
                        plugin.sleepingWorlds.keySet().forEach(world -> player
                                .sendMessage(ChatColor.GRAY + plugin.sleepingWorlds.get(world).toString()));
                        player.sendMessage(
                                ChatColor.YELLOW + "DEBUG: " + ChatColor.GRAY + "eventhandlers.playersOnline: ");
                        plugin.playersOnline.keySet().forEach(world -> player
                                .sendMessage(ChatColor.GRAY + plugin.playersOnline.get(world).toString()));
                        player.sendMessage(
                                ChatColor.YELLOW + "DEBUG: " + ChatColor.GRAY + "skipping: " + skipping.toString());
                        player.sendMessage(
                                ChatColor.YELLOW + "DEBUG: " + ChatColor.GRAY + "voting: " + voting.toString());
                    }
                    // Sleepinfo message
                    player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                            sleepInfo.replace("%percent%", dfrmt.format((wsleeping / wonline) * 100) + "%")
                                    .replace("%count%", dfrmt.format(wsleeping))));
                    // Debug
                    if (debugPlayers.contains(player)) {
                        player.sendMessage(
                                ChatColor.YELLOW + "DEBUG: " + ChatColor.GRAY + "Checking if should skip....");
                        player.sendMessage(ChatColor.YELLOW + "DEBUG: sleeping/onlineplayers : " + ChatColor.GRAY
                                + (wsleeping / wonline));
                    }
                    // Send a vote message if enabled and not done yet
                    if (useVote) {
                        if (!voting.contains(pWorld)) { // Pure bukkit doesn't have chatcomponent but you shouldn't be
                                                        // using pure Bukkit
                            voting.add(pWorld);
                            // Send vote message to world
                            world.getPlayers().forEach(player -> sendVoteMsg(player));
                        } else { // If a vote is ongoing send just the sleeper the menu
                            sendVoteMsg(player);
                        }
                    }
                    // Check if skip should be done
                    if ((wsleeping / wonline) * 100 >= skipPercentage && !skipping.contains(pWorld)) { // Skip
                        if (debugPlayers.contains(player)) {
                            player.sendMessage(ChatColor.YELLOW + "DEBUG: " + ChatColor.GRAY + "Skipping...");
                        }
                        for (Player players : world.getPlayers()) {
                            players.sendMessage(ChatColor.translateAlternateColorCodes('&',
                                    nightSkip.replace("%percent%", dfrmt.format((wsleeping / wonline) * 100) + "%")
                                            .replace("%count%", dfrmt.format(wsleeping))
                                            .replace("%player%", player.getName())));
                        }
                        skipping.add(pWorld);
                        recentlySkipped.add(pWorld);
                        if (!useAnimation) {
                            Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(plugin, new Runnable() {
                                public void run() {
                                    if (player.isOnline() && debugPlayers.contains(player)) {
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
                                            if (player.isOnline() && debugPlayers.contains(player)) {
                                                player.sendMessage(ChatColor.YELLOW + "DEBUG: " + ChatColor.GRAY
                                                        + "sleeping set to 0");
                                            }
                                        }
                                    }, 20L);
                                }
                            }, 120L);
                        } else {
                            if (debugPlayers.contains(player)) {
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
        // Count players to be ignored
        float onlineIgnored = 0;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (ignorePlayers.contains(p) || p.getWorld().getName() != worldName) {
                onlineIgnored++;
            }
        }
        float total = Bukkit.getOnlinePlayers().size() - onlineIgnored;
        playersOnline.put(worldName, total);
        return total;
    }

    public void voteYes(Player player) {
        if (!player.hasPermission("sleeper.vote")) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', noPermission));
            return;
        }
        if (!voting.contains(player.getWorld().getName())) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', noVote));
            return;
        }
        if (yesVotes.containsKey(player.getName())) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', alreadyYes));
            return;
        }
        if (noVotes.containsKey(player.getName())) {
            noVotes.remove(player.getName());
        }
        yesVotes.put(player.getName(), player.getWorld().getName());
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', votedYes));
        showVotes(player);
    }

    public void voteNo(Player player) {
        if (!player.hasPermission("sleeper.vote")) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', noPermission));
            return;
        }
        if (!voting.contains(player.getWorld().getName())) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', noVote));
            return;
        }
        if (noVotes.containsKey(player.getName())) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', alreadyNo));
            return;
        }
        if (yesVotes.containsKey(player.getName())) {
            yesVotes.remove(player.getName());
        }
        noVotes.put(player.getName(), player.getWorld().getName());
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', votedNo));
        showVotes(player);
    }

    public int countYes(String worldName) {
        int yVotes = 0;
        for (Entry<String, String> set : yesVotes.entrySet()) {
            if (set.getValue().equals(worldName)) yVotes++;
        }
        return yVotes;
    }

    public int countNo(String worldName) {
        int nVotes = 0;
        for (Entry<String, String> set : noVotes.entrySet()) {
            if (set.getValue().equals(worldName)) nVotes++;
        }
        return nVotes;
    }

    public void sendVoteMsg(Player player) {
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', voteTitle));
        TextComponent yesMessage = new TextComponent(
                TextComponent.fromLegacyText(ChatColor.translateAlternateColorCodes('&', voteYes)));
        yesMessage.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/sleep yes"));
        player.spigot().sendMessage(yesMessage);
        TextComponent noMessage = new TextComponent(
                TextComponent.fromLegacyText(ChatColor.translateAlternateColorCodes('&', voteNo)));
        noMessage.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/sleep no"));
        player.spigot().sendMessage(noMessage);
    }

    public void showVotes(Player player) {
        DecimalFormat dfrmt = new DecimalFormat();
        dfrmt.setMaximumFractionDigits(2);
        player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                listVotes.replace("%yes%", dfrmt.format(countYes(player.getWorld().getName()))).replace("%no%",
                        dfrmt.format(countNo(player.getWorld().getName())))));
    }

}
