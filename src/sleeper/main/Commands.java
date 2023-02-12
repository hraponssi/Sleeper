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
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be run by players.");
            return true;
        }
        Player player = (Player) sender;
        switch (cmd.getName().toLowerCase()) {
        case "ignoresleep":
            if (plugin.ignorePlayers.contains(player)) {
                player.sendMessage(ChatColor.GREEN + "You are no longer ignored from sleeping");
                plugin.ignorePlayers.remove(player);
            } else {
                player.sendMessage(ChatColor.RED + "You are now ignored from sleeping");
                plugin.ignorePlayers.add(player);
            }
            break;
        case "sleepdata": // Debug
            if (plugin.debugPlayers.contains(player)) {
                player.sendMessage(ChatColor.YELLOW + "DEBUG: " + ChatColor.GRAY + "Debug disabled");
                plugin.debugPlayers.remove(player);
            } else {
                player.sendMessage(ChatColor.YELLOW + "DEBUG: " + ChatColor.GRAY + "Debug enabled");
                plugin.debugPlayers.add(player);
            }

            // Send all kinds of current data
            player.sendMessage(ChatColor.RED + "Sleep data:");
            player.sendMessage(ChatColor.GREEN + "Sleeping per world: ");
            plugin.sleepingWorlds.keySet().forEach(world -> player
                    .sendMessage(ChatColor.GRAY + world + " - " + plugin.sleepingWorlds.get(world).toString()));
            player.sendMessage(ChatColor.GREEN + "Latest 'online' player count per world: ");
            plugin.playersOnline.keySet().forEach(world -> player
                    .sendMessage(ChatColor.GRAY + world + " - " + plugin.playersOnline.get(world).toString()));
            player.sendMessage(
                    ChatColor.GREEN + "True online player count: " + ChatColor.GRAY + Bukkit.getOnlinePlayers().size());
            player.sendMessage(ChatColor.GREEN + "Skipping: " + ChatColor.GRAY + plugin.skipping.toString());
            int onlineIgnored = 0;
            for (Player ignore : plugin.ignorePlayers) {
                if (Bukkit.getOnlinePlayers().contains(ignore)) {
                    onlineIgnored++;
                }
            }
            player.sendMessage(ChatColor.GREEN + "Ignored player count: " + ChatColor.GRAY + onlineIgnored);
            player.sendMessage(ChatColor.GREEN + "Ignoring players: ");
            plugin.ignorePlayers.forEach(p -> player.sendMessage(ChatColor.GRAY + p.getDisplayName()));

            break;
        case "sleepreload":
            plugin.loadConfig();
            player.sendMessage(ChatColor.GREEN + "Sleep config reloaded.");
            break;
        case "sleep": // TODO make all the other commands be part of this
            if (args.length >= 1) {
                switch (args[0].toLowerCase()) {
                case "yes":
                    plugin.voteYes(player);
                    break;
                case "no":
                    plugin.voteNo(player);
                    break;
                case "votes":
                    plugin.showVotes(player);
                    break;
                default:
                    player.sendMessage(ChatColor.translateAlternateColorCodes('&', plugin.sleepHelp));
                    break;
                }
            } else {
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', plugin.sleepHelp));
            }
            break;
        }
        return true;
    }
}
