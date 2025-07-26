package sleeper.main;

import java.text.DecimalFormat;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerBedLeaveEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class EventHandlers implements Listener {
    DecimalFormat dfrmt = new DecimalFormat();

    Main plugin;
    Scheduler scheduler;
    Voting voting;
    MessageHandler messageHandler;

    public EventHandlers(Main plugin, Scheduler scheduler, Voting voting, MessageHandler messageFormatting) {
        super();
        this.plugin = plugin;
        this.scheduler = scheduler;
        this.voting = voting;
        this.messageHandler = messageFormatting;
        dfrmt.setMaximumFractionDigits(2);
    }

    @EventHandler
    public void onBedEnter(PlayerBedEnterEvent event) {
        Player player = event.getPlayer();
        // Delay sleep if configured to do so
        scheduler.runDelayedTask(() -> {
            if (!player.isSleeping()) return;
            if (voting.blockBedsAfterVoting && voting.votingWorlds.contains(player.getWorld().getName())
                    && voting.hasVoted(player)) {
                event.setCancelled(true);
                voting.voteYes(player);
                return;
            }
            plugin.sleep(player, false);
        }, 20L * plugin.delaySeconds + 1L); // Base of 1 tick delay because 0 isnt accepted by the scheduler
    }

    @EventHandler
    public void onBedExit(PlayerBedLeaveEvent event) {
        Player player = event.getPlayer();
        String worldName = player.getWorld().getName();
        float wsleeping = plugin.sleepingWorlds.getOrDefault(worldName, 0f);
        float wonline = plugin.playersOnline.getOrDefault(worldName, 0f);
        int countNeeded = (int) Math.ceil(wonline * (plugin.skipPercentage / 100d));
        if (wsleeping > 0) plugin.sleepingWorlds.put(worldName, wsleeping - 1);
        if (!plugin.recentlySkipped.contains(worldName)) {
            messageHandler.sendMessage(player, plugin.sleepInfo
                    .replace("%percent%", dfrmt.format((wsleeping / wonline) * 100) + "%")
                    .replace("%count_needed%", dfrmt.format(countNeeded)).replace("%count%", dfrmt.format(wsleeping)));
        }
        plugin.getWorldSleepers(worldName).remove(player.getUniqueId());
    }

    @EventHandler
    public void onPlayerLeave(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        String worldName = player.getWorld().getName();
        if (player.isSleeping()) {
            float wsleeping = plugin.sleepingWorlds.getOrDefault(worldName, 0f);
            if (wsleeping > 0) plugin.sleepingWorlds.put(worldName, wsleeping - 1);
        }
        plugin.getWorldSleepers(worldName).remove(player.getUniqueId());
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        String worldName = player.getWorld().getName();
        if (plugin.ignorePlayers.contains(player.getUniqueId())) {
            float wsleeping = plugin.sleepingWorlds.getOrDefault(worldName, 0f);
            float wonline = plugin.playersOnline.getOrDefault(worldName, 0f);
            int countNeeded = (int) Math.ceil(wonline * (plugin.skipPercentage / 100d));
            messageHandler.sendMessage(player, plugin.ignored
                    .replace("%percent%", dfrmt.format((wsleeping / wonline) * 100) + "%")
                    .replace("%count_needed%", dfrmt.format(countNeeded)).replace("%count%", dfrmt.format(wsleeping)));
        }
    }
    
}
