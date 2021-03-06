package sleeper.main;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class Commands implements CommandExecutor {
	Main plugin;
	EventHandlers eventhandlers;
    public Commands(Main plugin, EventHandlers eventhandler) {
        super();
        this.plugin = plugin;
        this.eventhandlers = eventhandler;
    }
	@Override
	public boolean onCommand(CommandSender sender, Command cmd, String command, String[] args) {
		if(sender instanceof Player) {
			Player player = (Player) sender;
			if(command.equalsIgnoreCase("ignoresleep")) {
				if(plugin.ignorePlayers.contains(player)) {
					player.sendMessage(ChatColor.GREEN + "You are no longer ignored from sleeping");
					plugin.ignorePlayers.remove(player);
				}else {
					player.sendMessage(ChatColor.RED + "You are now ignored from sleeping");
					plugin.ignorePlayers.add(player);
				}
				return true;
			}else if(command.equalsIgnoreCase("sleepdata")) { //Debug
				if(plugin.debugPlayers.contains(player)) {
					player.sendMessage(ChatColor.YELLOW + "DEBUG: " + ChatColor.GRAY + "Debug disabled");
					plugin.debugPlayers.remove(player);
				}else {
					player.sendMessage(ChatColor.YELLOW + "DEBUG: " + ChatColor.GRAY + "Debug enabled");
					plugin.debugPlayers.add(player);
				}
				player.sendMessage(ChatColor.RED + "Sleep data:");
				player.sendMessage(ChatColor.GREEN + "Sleeping: " + ChatColor.GRAY + eventhandlers.sleeping);
				player.sendMessage(ChatColor.GREEN + "Latest 'online' player count: "+ ChatColor.GRAY + eventhandlers.playersOnline);
				player.sendMessage(ChatColor.GREEN + "True online player count: "+ ChatColor.GRAY + Bukkit.getOnlinePlayers().size());
				player.sendMessage(ChatColor.GREEN + "Skipping: "+ ChatColor.GRAY + plugin.skipping);
				int onlineIgnored = 0;
				for(Player ignore : plugin.ignorePlayers) {
					if(Bukkit.getOnlinePlayers().contains(ignore)) {
						onlineIgnored++;
					}
				}
				player.sendMessage(ChatColor.GREEN + "Ignored player count: " + ChatColor.GRAY + onlineIgnored);
				player.sendMessage(ChatColor.GREEN + "Ignoring players: ");
				for(Player p : plugin.ignorePlayers) {
					player.sendMessage(ChatColor.GRAY + p.getDisplayName());
				}
				return true;
			}else if(command.equalsIgnoreCase("sleepreload")) {
				plugin.loadConfig();
				player.sendMessage(ChatColor.GREEN + "Sleep config reloaded.");
				return true;
			}
		}
		return false;
	}
}
