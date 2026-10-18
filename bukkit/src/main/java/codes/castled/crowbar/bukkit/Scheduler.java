package codes.castled.crowbar.bukkit;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

/**
 * Runs tasks on the main thread, or Folia's global region where there is no main thread.
 *
 * <p>Folia's scheduler methods exist in the Paper API but only work on Folia, so they are called
 * only once Folia is detected; Spigot and Paper never reach them.
 */
public final class Scheduler {
    private static final boolean FOLIA = classExists("io.papermc.paper.threadedregions.RegionizedServer");

    public interface Task {
        void cancel();
    }

    private Scheduler() {
    }

    public static Task repeat(Plugin plugin, Runnable task, long delayTicks, long periodTicks) {
        if (FOLIA) {
            var handle = Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, t -> task.run(), Math.max(1, delayTicks), periodTicks);
            return handle::cancel;
        }
        var handle = Bukkit.getScheduler().runTaskTimer(plugin, task, delayTicks, periodTicks);
        return handle::cancel;
    }

    public static void later(Plugin plugin, Runnable task, long delayTicks) {
        if (FOLIA) {
            Bukkit.getGlobalRegionScheduler().runDelayed(plugin, t -> task.run(), Math.max(1, delayTicks));
        } else {
            Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks);
        }
    }

    private static boolean classExists(String name) {
        try {
            Class.forName(name);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
