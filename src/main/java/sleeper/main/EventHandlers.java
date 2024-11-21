package sleeper.main;

import java.text.DecimalFormat;

import org.bukkit.ChatColor;
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
    Voting voting;

    public EventHandlers(Main plugin, Voting voting) {
        super();
        this.plugin = plugin;
        this.voting = voting;
        dfrmt.setMaximumFractionDigits(2);
    }

    @EventHandler
    public void onBedEnter(PlayerBedEnterEvent event) {
        Player player = event.getPlayer();
        if(voting.blockBedsAfterVoting && voting.votingWorlds.contains(player.getWorld().getName()) && voting.hasVoted(player)) {
            event.setCancelled(true);
            voting.voteYes(player);
            return;
        }
        plugin.sleep(player);
    }

    @EventHandler
    public void onBedExit(PlayerBedLeaveEvent event) {
        Player player = event.getPlayer();
        float wsleeping = plugin.sleepingWorlds.getOrDefault(player.getWorld().getName(), 0f);
        float wonline = plugin.playersOnline.getOrDefault(player.getWorld().getName(), 0f);
        if (wsleeping > 0) plugin.sleepingWorlds.put(player.getWorld().getName(), wsleeping - 1);
        if (!plugin.recentlySkipped.contains(player.getWorld().getName())) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    plugin.sleepInfo.replace("%percent%", dfrmt.format((wsleeping / wonline) * 100) + "%")
                            .replace("%count%", dfrmt.format(wsleeping))));
        }
    }

    @EventHandler
    public void onPlayerLeave(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (player.isSleeping()) {
            float wsleeping = plugin.sleepingWorlds.getOrDefault((player.getWorld().getName()), 0f);
            if (wsleeping > 0) plugin.sleepingWorlds.put(player.getWorld().getName(), wsleeping - 1);
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (plugin.ignorePlayers.contains(player.getUniqueId())) {
            float wsleeping = plugin.sleepingWorlds.getOrDefault(player.getWorld().getName(), 0f);
            float wonline = plugin.playersOnline.getOrDefault(player.getWorld().getName(), 0f);
            player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    plugin.ignored.replace("%percent%", dfrmt.format((wsleeping / wonline) * 100) + "%")
                            .replace("%count%", dfrmt.format(wsleeping))));
        }
    }
}
