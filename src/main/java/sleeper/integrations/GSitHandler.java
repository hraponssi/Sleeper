package sleeper.integrations;

import java.text.DecimalFormat;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.entity.Pose;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.scheduler.BukkitScheduler;

import dev.geco.gsit.api.GSitAPI;
import dev.geco.gsit.api.event.PlayerPoseEvent;
import dev.geco.gsit.api.event.PlayerStopPoseEvent;

import sleeper.main.Main;
import sleeper.main.MessageHandler;
import sleeper.main.Voting;

public class GSitHandler implements Listener {
    DecimalFormat dfrmt = new DecimalFormat();

    Main plugin;
    Voting voting;
    MessageHandler messageFormatting;
    
    public GSitHandler (Main plugin, Voting voting, MessageHandler messageFormatting) {
        this.plugin = plugin;
        this.voting = voting;
        this.messageFormatting = messageFormatting;
        dfrmt.setMaximumFractionDigits(2);
    }
    
    boolean poseToSleep = false;
    
    @EventHandler
    public void onGSitPoseStart(PlayerPoseEvent event) {
        if (!poseToSleep) return;
        Player player = event.getPlayer();
        World world = player.getWorld();
        // Manually check time as you can sleep pose at any time
        if (world.getTime() < 12000) return;
        Pose pose = event.getPose().getPose();
        if (pose != Pose.SLEEPING) return;
        // The player entered a sleep pose, do the same as with normal sleeping
        BukkitScheduler scheduler = Bukkit.getScheduler();
        // Delay sleep if configured to do so
        scheduler.runTaskLater(plugin, () -> {
            var latestPose = GSitAPI.getPoseByPlayer(player).getPose();
            if (latestPose == null || latestPose != Pose.SLEEPING) return;
            if (voting.blockBedsAfterVoting && voting.getVotingWorlds().contains(player.getWorld().getName())
                    && voting.hasVoted(player)) {
                voting.voteYes(player);
                return;
            }
            plugin.sleep(player, true);
        }, 20L * plugin.delaySeconds);
    }
    
    @EventHandler
    public void onGSitPoseStop(PlayerStopPoseEvent event) {
        if (!poseToSleep) return;
        Player player = event.getPlayer();
        Pose pose = event.getPose().getPose();
        if (pose != Pose.SLEEPING) return;
        // The player left a sleep pose, do the same as with leaving a bed
        String worldName = player.getWorld().getName();
        float wsleeping = plugin.getSleepingWorlds().getOrDefault(worldName, 0f);
        float wonline = plugin.getPlayersOnline().getOrDefault(worldName, 0f);
        int countNeeded = (int) Math.ceil(wonline * (plugin.skipPercentage / 100d));
        if (wsleeping > 0) plugin.getSleepingWorlds().put(worldName, wsleeping - 1);
        // An additional check on worldsleepers is done so the message only comes if you stop during that night
        if (!plugin.getRecentlySkipped().contains(worldName) 
                && plugin.getWorldSleepers(worldName).contains(player.getUniqueId())) {
            player.sendMessage(messageFormatting.parseMessage(plugin.sleepInfo
                    .replace("%percent%", dfrmt.format((wsleeping / wonline) * 100) + "%")
                    .replace("%count_needed%", dfrmt.format(countNeeded)).replace("%count%", dfrmt.format(wsleeping))));
        }
        plugin.getWorldSleepers(worldName).remove(player.getUniqueId());
    }
    
    public void loadConfig(FileConfiguration config) {
        poseToSleep = config.getBoolean("GSitPoseToSleep");
    }
    
}
