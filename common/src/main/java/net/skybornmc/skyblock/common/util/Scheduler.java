package net.skybornmc.skyblock.common.util;

import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * Short scheduler helpers that return cancellable {@link Task} handles. Main-thread tasks are named and run through
 * the {@link SlowOperationTimer}, so anything slow shows up in the console with its name.
 *
 * <p>Async here means Bukkit's async pool, for short non-world work only. Database work goes through the
 * database executor (virtual threads), never here.</p>
 */
public final class Scheduler {

    /** A scheduled task. */
    public interface Task {
        void cancel();

        boolean cancelled();

        Task NONE = new Task() {
            @Override
            public void cancel() {
            }

            @Override
            public boolean cancelled() {
                return true;
            }
        };
    }

    private record BukkitHandle(BukkitTask task) implements Task {
        @Override
        public void cancel() {
            task.cancel();
        }

        @Override
        public boolean cancelled() {
            return task.isCancelled();
        }
    }

    private final Plugin plugin;
    private final SlowOperationTimer timer;

    public Scheduler(Plugin plugin, SlowOperationTimer timer) {
        this.plugin = plugin;
        this.timer = timer;
    }

    public SlowOperationTimer timer() {
        return timer;
    }

    /** Runs on the main thread next tick. */
    public Task main(String name, Runnable action) {
        return wrap(plugin.getServer().getScheduler().runTask(plugin, timed(name, action)));
    }

    /** Runs on the main thread right now if already on it, otherwise next tick. */
    public void ensureMain(String name, Runnable action) {
        if (plugin.getServer().isPrimaryThread()) {
            timer.time(name, action);
        } else {
            main(name, action);
        }
    }

    public Task later(String name, long delayTicks, Runnable action) {
        return wrap(plugin.getServer().getScheduler().runTaskLater(plugin, timed(name, action), Math.max(0, delayTicks)));
    }

    public Task repeat(String name, long delayTicks, long periodTicks, Runnable action) {
        return wrap(plugin.getServer().getScheduler().runTaskTimer(plugin, timed(name, action),
                Math.max(0, delayTicks), Math.max(1, periodTicks)));
    }

    public Task async(Runnable action) {
        return wrap(plugin.getServer().getScheduler().runTaskAsynchronously(plugin, action));
    }

    public Task asyncLater(long delayTicks, Runnable action) {
        return wrap(plugin.getServer().getScheduler().runTaskLaterAsynchronously(plugin, action, Math.max(0, delayTicks)));
    }

    public Task asyncRepeat(long delayTicks, long periodTicks, Runnable action) {
        return wrap(plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, action,
                Math.max(0, delayTicks), Math.max(1, periodTicks)));
    }

    /** Computes a value on the main thread and completes the future with it (for async code that needs the world). */
    public <T> CompletableFuture<T> supplyMain(String name, Supplier<T> supplier) {
        CompletableFuture<T> future = new CompletableFuture<>();
        ensureMain(name, () -> {
            try {
                future.complete(supplier.get());
            } catch (RuntimeException e) {
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    private Runnable timed(String name, Runnable action) {
        return () -> timer.time(name, action);
    }

    private static Task wrap(BukkitTask task) {
        return task == null ? Task.NONE : new BukkitHandle(task);
    }
}
