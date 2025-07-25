package sleeper.main;

import org.bukkit.Bukkit;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Scanner;
import java.util.function.Consumer;

// From: https://www.spigotmc.org/wiki/creating-an-update-checker-that-checks-for-updates
public class UpdateChecker {

    private final Main plugin;
    private final int resourceId;

    public UpdateChecker(Main plugin, int resourceId) {
        this.plugin = plugin;
        this.resourceId = resourceId;
    }

    public void getVersion(final Consumer<String> consumer) {
        Bukkit.getServer().getAsyncScheduler().runNow(this.plugin, ScheduledTask -> {
            try (InputStream is = new URL(
                    "https://api.spigotmc.org/legacy/update.php?resource=" + this.resourceId + "/~").openStream();
                    Scanner scann = new Scanner(is)) {
                if (scann.hasNext()) {
                    consumer.accept(scann.next());
                }
            } catch (IOException e) {
                plugin.getLogger().info("Unable to check for updates.");
            }
        });
    }
}
