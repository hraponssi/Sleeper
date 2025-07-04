package sleeper.main;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.util.StringUtil;

public class CommandCompletion implements TabCompleter {

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String commandLable, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            if (sender.hasPermission("sleeper.ignore")) completions.add("ignore");
            if (sender.hasPermission("sleeper.reload")) completions.add("reload");
            if (sender.hasPermission("sleeper.vote")) completions.add("yes");
            if (sender.hasPermission("sleeper.vote")) completions.add("no");
            if (sender.hasPermission("sleeper.vote")) completions.add("votes");
            return StringUtil.copyPartialMatches(args[0], completions, new ArrayList<>());
        } else if (args.length == 2) {
            if (args[0].equalsIgnoreCase("ignore")) {
                List<String> names = Bukkit.getOnlinePlayers().stream().map(player -> player.getName()).toList();
                if (sender.hasPermission("sleeper.ignore")) completions.addAll(names);
            }
            return StringUtil.copyPartialMatches(args[1], completions, new ArrayList<>());
        } else if (args.length == 3) {
            if (args[0].equalsIgnoreCase("ignore")) {
                if (sender.hasPermission("sleeper.ignore")) completions.add("TRUE");
                if (sender.hasPermission("sleeper.ignore")) completions.add("FALSE");
            }
            return StringUtil.copyPartialMatches(args[2], completions, new ArrayList<>());
        }
        return null;
    }

}
