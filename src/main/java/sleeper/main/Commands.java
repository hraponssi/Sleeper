package sleeper.main;

import java.util.StringJoiner;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public class Commands implements CommandExecutor {
    Main plugin;
    MessageHandler messageHandler;
    Voting voting;

    String msgPlayerNotFound = "&c%player% not found.";
    String msgSelfIgnoreOff = "&aYou are no longer ignored from sleeping.";
    String msgSelfIgnoreOn = "&cYou are now ignored from sleeping.";
    String msgOtherIgnoreOff = "&a%player% is no longer ignored from sleeping.";
    String msgOtherIgnoreOn = "&c%player% is now ignored from sleeping.";
    String msgOtherAlreadyIgnored = "&c%player% is already ignored from sleeping.";
    String msgInvalidState = "&c%input% is not TRUE or FALSE.";
    String msgConfigReloaded = "&aSleep config reloaded.";
    String msgOnlyPlayers = "&cThis command can only be run by players.";
    String sleepHelpList = "&cInvalid command, valid subcommands are: ";
    
    public Commands(Main plugin, Voting voting, MessageHandler messageFormatting) {
        super();
        this.plugin = plugin;
        this.voting = voting;
        this.messageHandler = messageFormatting;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String command, String[] args) {
        switch (cmd.getName().toLowerCase()) {
        case "sleep":
            if (args.length < 1) {
                sender.sendMessage(messageHandler.parseMessage(playerHelpMsg(sender)));
                break;
            }
            // Console compatible commands
            switch (args[0].toLowerCase()) {
            case "ignore":
                if (!hasPermission(sender, "sleeper.ignore")) return true;
                if (args.length < 2) { // Self
                    if (!isPlayer(sender))  return true;
                    UUID uuid = ((Player) sender).getUniqueId();
                    if (plugin.ignorePlayers.contains(uuid)) {
                        sender.sendMessage(messageHandler.parseMessage(msgSelfIgnoreOff));
                        plugin.ignorePlayers.remove(uuid);
                    } else {
                        sender.sendMessage(messageHandler.parseMessage(msgSelfIgnoreOn));
                        plugin.ignorePlayers.add(uuid);
                    }
                } else if (args.length < 3) { // Another player
                    String targetName = args[1];
                    Player target = Bukkit.getPlayer(targetName);
                    if (target == null) {
                        sender.sendMessage(messageHandler
                                .parseMessage(msgPlayerNotFound.replaceAll("%player%", targetName)));
                    } else {
                        if (plugin.ignorePlayers.contains(target.getUniqueId())) {
                            sender.sendMessage(messageHandler
                                    .parseMessage(msgOtherIgnoreOff.replaceAll("%player%", target.getName())));
                            plugin.ignorePlayers.remove(target.getUniqueId());
                        } else {
                            sender.sendMessage(messageHandler
                                    .parseMessage(msgOtherIgnoreOn.replaceAll("%player%", target.getName())));
                            plugin.ignorePlayers.add(target.getUniqueId());
                        }
                    }
                } else { // Another player, and a specific state true or false
                    String targetName = args[1];
                    String stateString = args[2].toUpperCase();
                    Player target = Bukkit.getPlayer(targetName);
                    if (target == null) {
                        sender.sendMessage(messageHandler
                                .parseMessage(msgPlayerNotFound.replaceAll("%player%", targetName)));
                    } else if (!stateString.equals("TRUE") && !stateString.equals("FALSE")) {
                        sender.sendMessage(messageHandler
                                .parseMessage(msgInvalidState.replaceAll("%input%", stateString)));
                    } else {
                        if (stateString.equals("TRUE")) {
                            if (!plugin.ignorePlayers.contains(target.getUniqueId())) {
                                sender.sendMessage(messageHandler
                                        .parseMessage(msgOtherIgnoreOn.replaceAll("%player%", target.getName())));
                                plugin.ignorePlayers.add(target.getUniqueId());
                            } else {
                                sender.sendMessage(messageHandler
                                        .parseMessage(msgOtherAlreadyIgnored.replaceAll("%player%", target.getName())));
                            }
                        } else {
                            sender.sendMessage(messageHandler
                                    .parseMessage(msgOtherIgnoreOff.replaceAll("%player%", target.getName())));
                            plugin.ignorePlayers.remove(target.getUniqueId());
                        }
                    }
                }
                return true;
            case "reload":
                if (!hasPermission(sender, "sleeper.reload")) return true;
                plugin.loadConfig();
                sender.sendMessage(messageHandler.parseMessage(msgConfigReloaded));
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
                    player.sendMessage(Component.text("DEBUG: ").color(NamedTextColor.YELLOW)
                            .append(Component.text("Debug disabled").color(NamedTextColor.GRAY)));
                    plugin.debugPlayers.remove(player.getUniqueId());
                } else {
                    player.sendMessage(Component.text("DEBUG: ").color(NamedTextColor.YELLOW)
                            .append(Component.text("Debug enabled").color(NamedTextColor.GRAY)));
                    plugin.debugPlayers.add(player.getUniqueId());
                }

                // Send all kinds of current data
                player.sendMessage(Component.text("Sleep data:").color(NamedTextColor.RED));
                if (player.getGameMode().equals(GameMode.SPECTATOR) || player.getGameMode().equals(GameMode.CREATIVE)) {
                    player.sendMessage(Component
                            .text("Note: you will be ignored from sleep calculations in spectator or creative mode.")
                            .color(NamedTextColor.GRAY));
                }
                player.sendMessage(Component.text("Sleeping per world: ").color(NamedTextColor.GREEN));
                plugin.sleepingWorlds.keySet().forEach(world -> player
                        .sendMessage(Component.text(world + " - " + plugin.sleepingWorlds.get(world).toString()).color(NamedTextColor.GRAY)));
                player.sendMessage(Component.text("Latest 'online' player count per world: ").color(NamedTextColor.GREEN));
                plugin.playersOnline.keySet().forEach(world -> player
                        .sendMessage(Component.text(world + " - " + plugin.playersOnline.get(world).toString()).color(NamedTextColor.GRAY)));
                player.sendMessage(Component.text("True online player count: ").color(NamedTextColor.GREEN)
                        .append(Component.text(Bukkit.getOnlinePlayers().size()).color(NamedTextColor.GRAY)));
                player.sendMessage(Component.text("Skipping: ").color(NamedTextColor.GREEN)
                        .append(Component.text(plugin.skipping.toString()).color(NamedTextColor.GRAY)));
                int onlineIgnored = plugin.getOnlineIgnorers().size();
                player.sendMessage(Component.text("Ignored player count: ").color(NamedTextColor.GREEN)
                        .append(Component.text(onlineIgnored).color(NamedTextColor.GRAY)));
                player.sendMessage(Component.text("Ignoring players: ").color(NamedTextColor.GREEN));
                plugin.getOnlineIgnorers().forEach(p -> player.sendMessage(Component.text(p.getName()).color(NamedTextColor.GRAY)));
                break;
            default:
                sender.sendMessage(messageHandler.parseMessage(playerHelpMsg(player)));
                break;
            }
            break;
        default:
            sender.sendMessage(messageHandler.parseMessage(playerHelpMsg(sender)));
            break;
        }
        return true;
    }

    private boolean isPlayer(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(messageHandler.parseMessage(msgOnlyPlayers));
            return false;
        }
        return true;
    }

    private boolean hasPermission(CommandSender player, String permission) {
        if (!player.hasPermission(permission)) {
            player.sendMessage(messageHandler.parseMessage(plugin.noPermission));
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
         * if (player.hasPermission("sleeper.data")) { builder.add("debug"); }
         */
        if (player.hasPermission("sleeper.reload")) {
            builder.add("reload");
        }

        return sleepHelpList + builder.toString();
    }

    public void loadConfig(FileConfiguration config) {
        msgPlayerNotFound = config.getString("PlayerNotFound");
        msgSelfIgnoreOff = config.getString("SelfIgnoreOff");
        msgSelfIgnoreOn = config.getString("SelfIgnoreOn");
        msgOtherIgnoreOff = config.getString("OtherIgnoreOff");
        msgOtherIgnoreOn = config.getString("OtherIgnoreOn");
        msgOtherAlreadyIgnored = config.getString("OtherAlreadyIgnored");
        msgInvalidState = config.getString("InvalidState");
        msgConfigReloaded = config.getString("ConfigReloaded");
        msgOnlyPlayers = config.getString("OnlyPlayers");
        sleepHelpList = config.getString("SleepHelpList");
    }

}
