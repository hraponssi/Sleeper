package sleeper.main;

import org.bukkit.Bukkit;
import org.jetbrains.annotations.NotNull;

public class Scheduler {

    Main plugin;
    
    private boolean isFolia = false;
    
    public Scheduler(Main plugin) {
        this.plugin = plugin;
        // Check if the server has the Folia scheduler by trying to get the class
        try {
            Class.forName("io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler");
            isFolia = true;
        } catch (ClassNotFoundException e) {
            isFolia = false;
        }
    }
    
    public void runRepeatingTask(@NotNull Runnable task, long delay, long period) {
        if (isFolia) {
            Bukkit.getServer().getGlobalRegionScheduler().runAtFixedRate(plugin, scheduledTask -> task.run(), delay, period);
        } else {
            Bukkit.getServer().getScheduler().scheduleSyncRepeatingTask(this.plugin, task, delay, period);
        }
    }
    
    public void runDelayedTask(@NotNull Runnable task, long delay) {
        if (isFolia) {
            Bukkit.getServer().getGlobalRegionScheduler().runDelayed(plugin, scheduledTask -> task.run(), delay);
        } else {
            Bukkit.getServer().getScheduler().runTaskLater(this.plugin, task, delay);
        }
    }
    
    public void runAsyncTask(@NotNull Runnable task) {
        if (isFolia) {
            Bukkit.getServer().getAsyncScheduler().runNow(plugin, scheduledTask -> task.run());
        } else {
            Bukkit.getServer().getScheduler().runTaskAsynchronously(this.plugin, task);
        }
    }
    
}
