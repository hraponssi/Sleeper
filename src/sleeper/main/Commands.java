package sleeper.main;

import java.util.StringJoiner;

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
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be run by players.");
            return true;
        }
        Player player = (Player) sender;
        switch (cmd.getName().toLowerCase()) {
        case "sleep": // TODO: add tab completion
            if (args.length < 1) {
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', playerHelpMsg(player)));
                break;
            }
            switch (args[0].toLowerCase()) {
            case "yes":
            	if (!player.hasPermission("sleeper.vote")) {
            		player.sendMessage(ChatColor.translateAlternateColorCodes('&', plugin.noPermission));
            		break;
            	}
                plugin.voteYes(player);
                break;
            case "no":
            	if (!player.hasPermission("sleeper.vote")) {
            		player.sendMessage(ChatColor.translateAlternateColorCodes('&', plugin.noPermission));
            		break;
            	}
                plugin.voteNo(player);
                break;
            case "votes":
            	if (!player.hasPermission("sleeper.vote")) {
            		player.sendMessage(ChatColor.translateAlternateColorCodes('&', plugin.noPermission));
            		break;
            	}
                plugin.showVotes(player);
                break;
            // TODO: Add a command to list ignored players?
            case "ignore":
            	if (!player.hasPermission("sleeper.ignore")) {
            		player.sendMessage(ChatColor.translateAlternateColorCodes('&', plugin.noPermission));
            		break;
            	}
                if (plugin.ignorePlayers.contains(player.getUniqueId())) {
                    player.sendMessage(ChatColor.GREEN + "You are no longer ignored from sleeping");
                    plugin.ignorePlayers.remove(player.getUniqueId());
                } else {
                    player.sendMessage(ChatColor.RED + "You are now ignored from sleeping");
                    plugin.ignorePlayers.add(player.getUniqueId());
                }
                break;
            case "debug": // A bunch of debug data
            	if (!player.hasPermission("sleeper.data")) {
            		player.sendMessage(ChatColor.translateAlternateColorCodes('&', plugin.noPermission));
            		break;
            	}
                if (plugin.debugPlayers.contains(player.getUniqueId())) {
                    player.sendMessage(ChatColor.YELLOW + "DEBUG: " + ChatColor.GRAY + "Debug disabled");
                    plugin.debugPlayers.remove(player.getUniqueId());
                } else {
                    player.sendMessage(ChatColor.YELLOW + "DEBUG: " + ChatColor.GRAY + "Debug enabled");
                    plugin.debugPlayers.add(player.getUniqueId());
                }

                // Send all kinds of current data
                player.sendMessage(ChatColor.RED + "Sleep data:");
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
            case "reload":
            	if (!player.hasPermission("sleeper.reload")) {
            		player.sendMessage(ChatColor.translateAlternateColorCodes('&', plugin.noPermission));
            		break;
            	}
                plugin.loadConfig();
                player.sendMessage(ChatColor.GREEN + "Sleep config reloaded.");
                break;
            default:
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', playerHelpMsg(player)));
                break;
            }
            break;
        default:
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', playerHelpMsg(player)));
            break;
        }
        return true;
    }
    
    private String playerHelpMsg(Player player) {
        StringJoiner builder = new StringJoiner(", ");
        if (player.hasPermission("sleeper.vote")) {
            builder.add("yes, no, votes");
        }
        if (player.hasPermission("sleeper.ignore")) {
            builder.add("ignore");
        }
        if (player.hasPermission("sleeper.data")) {
            builder.add("debug");
        }
        if (player.hasPermission("sleeper.reload")) {
            builder.add("reload");
        }
        
        return plugin.sleepHelpList + builder.toString();
    }
    
}
