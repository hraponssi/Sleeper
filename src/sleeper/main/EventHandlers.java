package sleeper.main;

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
	float sleeping;
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
		player.sendMessage(ChatColor.translateAlternateColorCodes('&', plugin.sleepInfo.replace("%percent%", (sleeping/playersOnline)*100 + "%").replace("%count%", Float.toString(sleeping))));
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
			player.sendMessage(ChatColor.translateAlternateColorCodes('&', plugin.ignored.replace("%percent%", (sleeping/playersOnline)*100 + "%").replace("%count%", Float.toString(sleeping))));
		}
	}
}
