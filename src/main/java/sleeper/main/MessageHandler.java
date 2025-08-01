package sleeper.main;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.Set;
import java.util.UUID;

public class MessageHandler {

    Main plugin;

    public MessageHandler(Main plugin) {
        this.plugin = plugin;
    }

    private Set<String> allowedTypes = Set.of("MINECRAFT", "MINIMESSAGE");

    // Setting value
    String formattingType = "MINECRAFT";

    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public Component parseMessage(String message) {
        switch (formattingType) {
        case "MINECRAFT":
            return LegacyComponentSerializer.legacyAmpersand().deserialize(message);
        case "MINIMESSAGE":
            Component component = miniMessage.deserialize(message);
            return component;
        default:
            return Component.text(message);
        }
    }
    
    public String parseMessageString(String message) {
        switch (formattingType) {
        case "MINECRAFT":
            return ChatColor.translateAlternateColorCodes('&', message);
        case "MINIMESSAGE":
            Component component = miniMessage.deserialize(message);
            return LegacyComponentSerializer.legacySection().serialize(component);
        default:
            return message;
        }
    }
    
    // Message sending system to allow skipping sending blank messages
    public void sendMessage(Player player, String message) {
        if (message.equals("")) return;
        if (plugin.actionbarMessages) {
            sendActionbarMessage(player, message);
        } else {
            Audience audience = plugin.adventure().player(player);
            audience.sendMessage(parseMessage(message));
        }
    }
    
    // Message sending system to allow skipping sending blank messages, without parsing colors
    public void sendMessageUnparsed(Player player, String message) {
        if (message.equals("")) return;
        if (plugin.actionbarMessages) {
            sendActionbarMessage(player, message);
        } else {
            player.sendMessage(message);
        }
    }
    
    public void sendActionbarMessage(Player player, String message) {
        Audience audience = plugin.adventure().player(player);
        audience.sendActionBar(parseMessage(message));
    }
    
    // Broadcast a debug message to all debug players
    public void broadcastDebug(String message) {
        for (UUID debugUUID : plugin.debugPlayers) {
            Player player = Bukkit.getPlayer(debugUUID);
            if (!player.isOnline()) continue;
            player.sendMessage(ChatColor.YELLOW + "DEBUG: " + ChatColor.GRAY + message);
        }
    }
    
    // Send a debug message to a player, if they are in the debugPlayers list
    public void sendDebug(Player player, String message) {
        if (!plugin.debugPlayers.contains(player.getUniqueId())) return;
        player.sendMessage(ChatColor.YELLOW + "DEBUG: " + ChatColor.GRAY + message);
    }

    public void loadConfig(FileConfiguration config) {
        String configFormattingType = config.getString("FormattingType");
        if (configFormattingType != null && allowedTypes.contains(configFormattingType.toUpperCase()))
            formattingType = configFormattingType.toUpperCase();
        else {
            plugin.getLogger().warning("FormattingType value is not appropriate! Reset to default.");
        }
    }
    
}
