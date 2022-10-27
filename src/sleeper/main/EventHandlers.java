package sleeper.main;

import java.text.DecimalFormat;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerBedLeaveEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import net.md_5.bungee.api.ChatColor;



public class EventHandlers implements Listener{
	//Main main;
	int sleeping;
	float playersOnline;

	Main plugin;
    public EventHandlers(Main plugin) {
        super();
        this.plugin = plugin;
    }
	
	@EventHandler
	public void onBedEnter(PlayerBedEnterEvent event) {
		Player player = event.getPlayer();
		Main.plugin.sleep(player);
	}
	
	@EventHandler
	public void onBedExit(PlayerBedLeaveEvent event) {
		Player player = event.getPlayer();
		if(sleeping > 0) {sleeping--;}
		DecimalFormat dfrmt = new DecimalFormat();
		dfrmt.setMaximumFractionDigits(2);
		player.sendMessage(ChatColor.translateAlternateColorCodes('&', plugin.sleepInfo.replace("%percent%", dfrmt.format((sleeping/playersOnline)*100) + "%").replace("%count%", Integer.toString(sleeping))));
	}
	
	@EventHandler
	public void onPlayerLeave(PlayerQuitEvent event) {
		Player player = event.getPlayer();
		if(player.isSleeping()) {
			sleeping--;
		}
	}
	@EventHandler
	public void onPlayerJoin(PlayerJoinEvent event) {
		Player player = event.getPlayer();
		if(plugin.ignorePlayers.contains(player)) {
			DecimalFormat dfrmt = new DecimalFormat();
			dfrmt.setMaximumFractionDigits(2);
			player.sendMessage(ChatColor.translateAlternateColorCodes('&', plugin.ignored.replace("%percent%", dfrmt.format((sleeping/playersOnline)*100) + "%").replace("%count%", Integer.toString(sleeping))));
		}
	}
}
