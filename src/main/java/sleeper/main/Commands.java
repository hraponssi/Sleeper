package sleeper.main;

import java.util.StringJoiner;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class Commands implements CommandExecutor {
    Main plugin;
    Voting voting;

    public Commands(Main plugin, Voting voting) {
        super();
        this.plugin = plugin;
        this.voting = voting;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String command, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be run by players.");
            return true;
        }
        switch (cmd.getName().toLowerCase()) {
        case "sleep": // TODO: add tab completion
            if (args.length < 1) {
                sender.sendMessage(ChatColor.translateAlternateColorCodes('&', playerHelpMsg(sender)));
                break;
            }
            // Console compatible commands
            switch (args[0].toLowerCase()) {
            case "ignore":
            	if (!hasPermission(sender, "sleeper.ignore")) break;
            	if (args.length < 2) { // Self
            		if (!isPlayer(sender)) return true;
            		UUID uuid = ((Player) sender).getUniqueId();
	                if (plugin.ignorePlayers.contains(uuid)) {
	                    sender.sendMessage(ChatColor.GREEN + "You are no longer ignored from sleeping.");
	                    plugin.ignorePlayers.remove(uuid);
	                } else {
	                    sender.sendMessage(ChatColor.RED + "You are now ignored from sleeping.");
	                    plugin.ignorePlayers.add(uuid);
	                }
            	} else if (args.length < 3) { // Another player
            		String targetName = args[1];
            		Player target = Bukkit.getPlayer(targetName);
            		if (target == null) {
            			sender.sendMessage(ChatColor.RED + "Player " + targetName + " not found.");
            		} else {
            			if (plugin.ignorePlayers.contains(target.getUniqueId())) {
    	                    sender.sendMessage(ChatColor.GREEN + targetName + " is no longer ignored from sleeping.");
    	                    plugin.ignorePlayers.remove(target.getUniqueId());
    	                } else {
    	                	sender.sendMessage(ChatColor.RED + targetName + " is now ignored from sleeping.");
    	                    plugin.ignorePlayers.add(target.getUniqueId());
    	                }
            		}
            	} else { // Another player, and a specific state true or false
            		String targetName = args[1];
            		String stateString = args[2].toUpperCase();
            		Player target = Bukkit.getPlayer(targetName);
            		if (target == null) {
            			sender.sendMessage(ChatColor.RED + "Player " + targetName + " not found.");
            		} else if (!stateString.equals("TRUE") && !stateString.equals("FALSE")) {
            			sender.sendMessage(ChatColor.RED + stateString + " is not TRUE or FALSE.");
            		} else {
            			if (stateString.equals("TRUE")) {
            				if (!plugin.ignorePlayers.contains(target.getUniqueId())) {
            					sender.sendMessage(ChatColor.RED + targetName + " is now ignored from sleeping.");
        	                    plugin.ignorePlayers.add(target.getUniqueId());
            				} else {
            					sender.sendMessage(ChatColor.RED + targetName + " is already ignored from sleeping.");
            				}
            			} else {
            				sender.sendMessage(ChatColor.GREEN + targetName + " is no longer ignored from sleeping.");
    	                    plugin.ignorePlayers.remove(target.getUniqueId());
    	                }
            		}
            	}
                return true;
            case "reload":
            	if (!hasPermission(sender, "sleeper.reload")) break;
                plugin.loadConfig();
                sender.sendMessage(ChatColor.GREEN + "Sleep config reloaded.");
                return true;
            }
            if (!isPlayer(sender)) return true;
            // Player only commands
            Player player = (Player) sender;
            switch (args[0].toLowerCase()) {
            case "yes":
            	if (!hasPermission(player, "sleeper.vote")) break;
            	voting.voteYes(player);
                break;
            case "no":
            	if (!hasPermission(player, "sleeper.vote")) break;
            	voting.voteNo(player);
                break;
            case "votes":
            	if (!hasPermission(player, "sleeper.vote")) break;
            	voting.showVotes(player);
                break;
            // TODO: Add a command to list ignored players?
            case "debug": // A bunch of debug data
            	if (!hasPermission(sender, "sleeper.data")) break;
                if (plugin.debugPlayers.contains(player.getUniqueId())) {
                    player.sendMessage(ChatColor.YELLOW + "DEBUG: " + ChatColor.GRAY + "Debug disabled");
                    plugin.debugPlayers.remove(player.getUniqueId());
                } else {
                    player.sendMessage(ChatColor.YELLOW + "DEBUG: " + ChatColor.GRAY + "Debug enabled");
                    plugin.debugPlayers.add(player.getUniqueId());
                }

                // Send all kinds of current data
                player.sendMessage(ChatColor.RED + "Sleep data:");
                if (player.getGameMode().equals(GameMode.SPECTATOR) || player.getGameMode().equals(GameMode.CREATIVE)) {
                	player.sendMessage(ChatColor.GRAY + "Note: you will be ignored from sleep calculations in spectator or creative mode.");
                }
                player.sendMessage(ChatColor.GREEN + "Sleeping per world: ");
                plugin.sleepingWorlds.keySet().forEach(world -> player
                        .sendMessage(ChatColor.GRAY + world + " - " + plugin.sleepingWorlds.get(world).toString()));
                player.sendMessage(ChatColor.GREEN + "Latest 'online' player count per world: ");
                plugin.playersOnline.keySet().forEach(world -> player
                        .sendMessage(ChatColor.GRAY + world + " - " + plugin.playersOnline.get(world).toString()));
                player.sendMessage(ChatColor.GREEN + "True online player count: " + ChatColor.GRAY
                        + Bukkit.getOnlinePlayers().size());
                player.sendMessage(ChatColor.GREEN + "Skipping: " + ChatColor.GRAY + plugin.skipping.toString());
                int onlineIgnored = plugin.getOnlineIgnorers().size();
                player.sendMessage(ChatColor.GREEN + "Ignored player count: " + ChatColor.GRAY + onlineIgnored);
                player.sendMessage(ChatColor.GREEN + "Ignoring players: ");
                plugin.getOnlineIgnorers().forEach(p -> player.sendMessage(ChatColor.GRAY + p.getDisplayName()));
                break;
            default:
                sender.sendMessage(ChatColor.translateAlternateColorCodes('&', playerHelpMsg(player)));
                break;
            }
            break;
        default:
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', playerHelpMsg(sender)));
            break;
        }
        return true;
    }
    
    private boolean isPlayer(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be run by players.");
            return false;
        }
        return true;
    }
    
    private boolean hasPermission(CommandSender player, String permission) {
    	if (!player.hasPermission(permission)) {
    		player.sendMessage(ChatColor.translateAlternateColorCodes('&', plugin.noPermission));
    		return false;
    	}
    	return true;
    }
    
    private String playerHelpMsg(CommandSender player) {
        StringJoiner builder = new StringJoiner(", ");
        if (player.hasPermission("sleeper.vote")) {
            builder.add("yes, no, votes");
        }
        if (player.hasPermission("sleeper.ignore")) {
            builder.add("ignore");
        }
        /*
        if (player.hasPermission("sleeper.data")) {
            builder.add("debug");
        }
        */
        if (player.hasPermission("sleeper.reload")) {
            builder.add("reload");
        }
        
        return plugin.sleepHelpList + builder.toString();
    }
    
}
