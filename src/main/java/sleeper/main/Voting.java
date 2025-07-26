package sleeper.main;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map.Entry;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;

public class Voting {

    Main plugin;

    MessageHandler messageHandler;

    DecimalFormat dfrmt = new DecimalFormat();

    public Voting(Main plugin, MessageHandler messageFormatting) {
        this.plugin = plugin;
        this.messageHandler = messageFormatting;
        dfrmt.setMaximumFractionDigits(2);
    }

    // Vote variables
    ArrayList<String> votingWorlds = new ArrayList<>();
    HashMap<String, Integer> votingWorldTimes = new HashMap<>();
    HashMap<String, String> yesVotes = new HashMap<>();
    HashMap<String, String> noVotes = new HashMap<>();

    private int tickCounter = 0;

    // Voting setting values
    boolean useVote = false;
    int yesMultiplier = 1;
    int noMultiplier = 1;
    int skipVotePercent = 50;
    public boolean blockBedsAfterVoting = false;
    boolean bossbarVoteCount = true;
    boolean actionVoteCount = true;
    boolean sendVotesOnStart = true;
    boolean voteStarts = false;
    int maxVoteTime = 60;
    boolean limitedVoteTime = false;

    // Message Strings
    String voteTitle = "&aSleep > &7Vote below on skipping the night:";
    String voteYes = "&a&lYes";
    String voteNo = "&c&lNo";
    String votedYes = "&aYou voted to skip the night.";
    String votedNo = "&aYou voted not to skip the night.";
    String noVote = "&cYour world isn't voting on a night skip.";
    String alreadyYes = "&cYou have already voted yes.";
    String alreadyNo = "&cYou have already voted no.";
    String listVotes = "&aYes: &7%yes% &cNo: &7%no%";
    String skipByVote = "&aSleep > &7The vote has decided to skip the night!";
    String voteNotEnabled = "&cVoting is not enabled.";
    String voteTimedOut = "&aSleep > &cThe vote ended without skipping the night.";

    public void startVote(Player player) {
        World world = player.getWorld();
        String pWorld = world.getName();
        plugin.onlinePlayers(pWorld); // Update listed count of online players for the world
        if (!votingWorlds.contains(pWorld) && world.getTime() >= 12542) { // Bukkit doesn't have chatcomponent, don't use it
            votingWorlds.add(pWorld);
            if (limitedVoteTime) votingWorldTimes.put(pWorld, maxVoteTime * 20); // Config time in seconds but var in ticks
            // Send vote message to world
            if (sendVotesOnStart) world.getPlayers().forEach(wPlayer -> sendVoteMsg(wPlayer));
        } else if (world.getTime() >= 12542) { // If a vote is ongoing send just the sleeper the menu
            sendVoteMsg(player);
        }
    }

    public void voteYes(Player player) {
        if (!player.hasPermission("sleeper.vote")) {
            messageHandler.sendMessage(player, plugin.noPermission);
            return;
        }
        if (!useVote) {
            messageHandler.sendMessage(player, voteNotEnabled);
            return;
        }
        if (voteStarts && !votingWorlds.contains(player.getWorld().getName())) {
            startVote(player);
        }
        if (!votingWorlds.contains(player.getWorld().getName())) {
            messageHandler.sendMessage(player, noVote);
            return;
        }
        if (yesVotes.containsKey(player.getName())) {
            messageHandler.sendMessage(player, alreadyYes);
            return;
        }
        noVotes.remove(player.getName());
        yesVotes.put(player.getName(), player.getWorld().getName());
        messageHandler.sendMessage(player, votedYes);
        plugin.onlinePlayers(player.getWorld().getName());
        showVotes(player);
    }

    public void voteNo(Player player) {
        if (!player.hasPermission("sleeper.vote")) {
            messageHandler.sendMessage(player, plugin.noPermission);
            return;
        }
        if (!useVote) {
            messageHandler.sendMessage(player, voteNotEnabled);
            return;
        }
        if (voteStarts && !votingWorlds.contains(player.getWorld().getName())) {
            startVote(player);
        }
        if (!votingWorlds.contains(player.getWorld().getName())) {
            messageHandler.sendMessage(player, noVote);
            return;
        }
        if (noVotes.containsKey(player.getName())) {
            messageHandler.sendMessage(player, alreadyNo);
            return;
        }
        yesVotes.remove(player.getName());
        noVotes.put(player.getName(), player.getWorld().getName());
        messageHandler.sendMessage(player, votedNo);
        plugin.onlinePlayers(player.getWorld().getName());
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
            messageHandler.sendMessage(player, voteNotEnabled);
            return;
        }
        Audience audience = plugin.adventure().player(player);
        audience.sendMessage(messageHandler.parseMessage(voteTitle));
        Component yesMessage = messageHandler.parseMessage(voteYes)
                .clickEvent(ClickEvent.runCommand("/sleep yes"));
        audience.sendMessage(yesMessage);
        Component noMessage = messageHandler.parseMessage(voteNo)
                .clickEvent(ClickEvent.runCommand("/sleep no"));
        audience.sendMessage(noMessage);
    }

    public void showVotes(Player player) {
        messageHandler.sendMessage(player, 
                listVotes.replace("%yes%", dfrmt.format(countYes(player.getWorld().getName())))
                        .replace("%no%", dfrmt.format(countNo(player.getWorld().getName()))));
    }

    public boolean hasVoted(Player player) {
        return (yesVotes.containsKey(player.getName()) || noVotes.containsKey(player.getName()));
    }

    public void endVote(String worldName) {
        votingWorlds.remove(worldName);
        plugin.bar.removeAll();
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
    }

    public void tick() {
        for (String worldName : new ArrayList<String>(votingWorlds)) {
            World world = Bukkit.getWorld(worldName);
            long time = world.getTime();
            if (plugin.skipping.contains(worldName)) continue;
            if (bossbarVoteCount) {
                plugin.bar.setTitle(
                        messageHandler.parseMessageString(listVotes.replace("%yes%", dfrmt.format(countYes(worldName)))
                                .replace("%no%", dfrmt.format(countNo(worldName)))));
                for (Player player : world.getPlayers()) {
                    if (plugin.bar.getPlayers().contains(player)) continue;
                    plugin.bar.addPlayer(player);
                }
            }
            if (actionVoteCount) {
                tickCounter++;
                if (tickCounter % 20 != 0) return;
                for (Player player : world.getPlayers()) {
                    messageHandler.sendActionbarMessage(player, listVotes.replace("%yes%", dfrmt.format(countYes(worldName)))
                                            .replace("%no%", dfrmt.format(countNo(worldName))));
                }
            }
            if (votingWorldTimes.containsKey(worldName)) {
                int timeLeft = votingWorldTimes.get(worldName) - 1;
                // If time ran out end the vote
                if (timeLeft <= 0) {
                    votingWorldTimes.remove(worldName);
                    world.getPlayers().forEach(
                            player -> messageHandler.sendMessage(player, voteTimedOut));
                    endVote(worldName);
                }
                votingWorldTimes.replace(worldName, timeLeft);
            }
            if (time < 2000) { // End vote, day time.
                endVote(worldName);
            } else { // Check if the votes are enough for a skip
                int yVotes = countYes(worldName);
                int nVotes = countNo(worldName);
                float skipFactor = ((yVotes * yesMultiplier) - (nVotes * noMultiplier))
                        / plugin.playersOnline.get(worldName); // Decimal yes votes - no votes divided by world players
                float skipMargin = skipVotePercent * 0.01f;
                if (skipFactor >= skipMargin) {
                    plugin.skipping.add(worldName);
                    plugin.recentlySkipped.add(worldName);
                    world.getPlayers()
                            .forEach(player -> messageHandler.sendMessage(player, skipByVote));
                    plugin.getLogger().info("Skipping night by vote in " + worldName);
                }
            }
        }
    }

    public void loadConfig(FileConfiguration config) {
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
        listVotes = config.getString("ListVotes");
        skipByVote = config.getString("SkipByVote");
        voteNotEnabled = config.getString("VoteNotEnabled");
        blockBedsAfterVoting = config.getBoolean("BlockBedsAfterVoting");
        bossbarVoteCount = config.getBoolean("BossbarVoteCount");
        actionVoteCount = config.getBoolean("ActionbarVoteCount");
        sendVotesOnStart = config.getBoolean("SendVotesOnStart");
        voteStarts = config.getBoolean("StartWithoutSleep");
        maxVoteTime = config.getInt("MaxVoteTime");
        limitedVoteTime = config.getBoolean("LimitedVoteTime");
        voteTimedOut = config.getString("VoteTimedOut");
    }
    
    public ArrayList<String> getVotingWorlds() {
        return votingWorlds;
    }

}
