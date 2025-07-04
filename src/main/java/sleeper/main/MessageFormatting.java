package sleeper.main;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.Set;

public class MessageFormatting {
    
    Main plugin;
    
    public MessageFormatting(Main plugin) {
        this.plugin = plugin;
    }
    
    Set<String> allowedTypes = Set.of("LEGACY_AMPERSAND", "LEGACY_SECTION", "MINIMESSAGE");
    
    // Setting value
    String formattingType = "MINIMESSAGE";
    
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    
    public String parseMessage(String message) {
        switch (formattingType) {
            case "LEGACY_AMPERSAND":
                return ChatColor.translateAlternateColorCodes('&', message);
            case "LEGACY_SECTION":
                return ChatColor.translateAlternateColorCodes('§', message);
            case "MINIMESSAGE":
                Component component = miniMessage.deserialize(message);
                return LegacyComponentSerializer.legacySection().serialize(component);
            default:
                return message;
        }
    }
    
    public void loadConfig(FileConfiguration config) {
    	String configFormattingType = config.getString("FormattingType");
        if (allowedTypes.contains(configFormattingType.toUpperCase()))
            formattingType = configFormattingType.toUpperCase();
        else
        {
            plugin.getLogger().warning("FormattingType value is not appropriate! Reset to default.");
        }
    }
}