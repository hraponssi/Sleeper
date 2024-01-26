package sleeper.main;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.UUID;

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

import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;

public class Main extends JavaPlugin {
    DecimalFormat dfrmt = new DecimalFormat();
    Commands commands;
    EventHandlers eventhandlers;

    // Setting values
    boolean useAnimation = true;
    int skipPercentage = 25;
    int skipSpeed = 100;
    boolean broadcastSleepInfo = false;

    // Voting setting values
    boolean useVote = false;
    int yesMultiplier = 1;
    int noMultiplier = 1;
    int skipVotePercent = 50;
    boolean blockBedsAfterVoting = false;
    boolean bossbarVoteCount = true;
    boolean sendVotesOnStart = true;
    boolean voteStarts = false;

    // Vote variables
    ArrayList<String> voting = new ArrayList<>();
    HashMap<String, String> yesVotes = new HashMap<>();
    HashMap<String, String> noVotes = new HashMap<>();

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
    String sleepHelpList = "&cInvalid command, valid subcommands are: ";
    String noPermission = "&cYou don't have permission for that.";
    String listVotes = "&aYes: &7%yes% &cNo: &7%no%";
    String skipByVote = "&aSleep > &7The vote has decided to skip the night!";
    String voteNotEnabled = "&cVoting is not enabled.";

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
        eventhandlers = new EventHandlers(this);
        commands = new Commands(this, eventhandlers);
        getCommand("sleep").setExecutor(commands);
        pm.registerEvents(eventhandlers, this);
        setConfig();
        loadConfig();
        dfrmt.setMaximumFractionDigits(2);
        getServer().getScheduler().scheduleSyncRepeatingTask(this, () -> {
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
                        Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(this, new Runnable() {
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
                if (bossbarVoteCount) {
                    bar.setTitle(ChatColor.translateAlternateColorCodes('&',
                            listVotes.replace("%yes%", dfrmt.format(countYes(worldName))).replace("%no%",
                                    dfrmt.format(countNo(worldName)))));
                    for (Player player : world.getPlayers()) {
                        if (bar.getPlayers().contains(player)) continue;
                        bar.addPlayer(player);
                    }
                }
                if (time < 2000) { // End vote, day time.
                    remove.add(worldName);
                    bar.removeAll();
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
                    if (skipFactor >= skipMargin) {
                        skipping.add(worldName);
                        recentlySkipped.add(worldName);
                        world.getPlayers().forEach(
                                player -> player.sendMessage(ChatColor.translateAlternateColorCodes('&', skipByVote)));
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
        sleepHelpList = config.getString("SleepHelpList");
        noPermission = config.getString("NoPermission");
        listVotes = config.getString("ListVotes");
        skipByVote = config.getString("SkipByVote");
        voteNotEnabled = config.getString("VoteNotEnabled");
        broadcastSleepInfo = config.getBoolean("BroadcastSleepInfo");
        blockBedsAfterVoting = config.getBoolean("BlockBedsAfterVoting");
        bossbarVoteCount = config.getBoolean("BossbarVoteCount");
        sendVotesOnStart = config.getBoolean("SendVotesOnStart");
        voteStarts = config.getBoolean("StartWithoutSleep");
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
                                ChatColor.YELLOW + "DEBUG: " + ChatColor.GRAY + "voting: " + voting.toString());
                    }
                    // Sleepinfo message
                    if (!broadcastSleepInfo) {
                        player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                                sleepInfo.replace("%percent%", dfrmt.format((wsleeping / wonline) * 100) + "%")
                                        .replace("%count%", dfrmt.format(wsleeping))
                                        .replace("%player%", player.getName())));
                    } else { // Tell everyone in the world
                        for (Player players : world.getPlayers()) {
                            players.sendMessage(ChatColor.translateAlternateColorCodes('&',
                                    sleepInfo.replace("%percent%", dfrmt.format((wsleeping / wonline) * 100) + "%")
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
                    if (useVote) {
                        startVote(player);
                        voteYes(player);
                    }
                    // Check if skip should be done
                    if ((wsleeping / wonline) * 100 >= skipPercentage && !skipping.contains(pWorld)) { // Skip
                        if (debugPlayers.contains(player.getUniqueId())) {
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
    
    public void startVote(Player player) {
        World world = player.getWorld();
        String pWorld = world.getName();
        onlinePlayers(pWorld); // Update listed count of online players for the world
        if (!voting.contains(pWorld) && world.getTime() >= 12542) { // Bukkit doesn't have chatcomponent, don't use it
            voting.add(pWorld);
            // Send vote message to world
            if (sendVotesOnStart) world.getPlayers().forEach(wPlayer -> sendVoteMsg(wPlayer));
        } else if (world.getTime() >= 12542) { // If a vote is ongoing send just the sleeper the menu
            sendVoteMsg(player);
        }
    }

    public void voteYes(Player player) {
        if (!player.hasPermission("sleeper.vote")) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', noPermission));
            return;
        }
        if (voteStarts && !voting.contains(player.getWorld().getName())) {
            startVote(player);
        }
        if (!useVote) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', voteNotEnabled));
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
        onlinePlayers(player.getWorld().getName());
        showVotes(player);
    }

    public void voteNo(Player player) {
        if (!player.hasPermission("sleeper.vote")) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', noPermission));
            return;
        }
        if (!useVote) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', voteNotEnabled));
            return;
        }
        if (voteStarts && !voting.contains(player.getWorld().getName())) {
            startVote(player);
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
        onlinePlayers(player.getWorld().getName());
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
        if (!useVote) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', voteNotEnabled));
            return;
        }
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
        player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                listVotes.replace("%yes%", dfrmt.format(countYes(player.getWorld().getName()))).replace("%no%",
                        dfrmt.format(countNo(player.getWorld().getName())))));
    }

    public boolean hasVoted(Player player) {
        return (yesVotes.containsKey(player.getName()) || noVotes.containsKey(player.getName()));
    }

}
