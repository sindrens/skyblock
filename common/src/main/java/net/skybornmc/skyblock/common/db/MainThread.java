package net.skybornmc.skyblock.common.db;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.util.concurrent.Executor;

/**
 * Executor that runs tasks on the server main thread (immediately if already there).
 * Usage: {@code db.async(...).thenAcceptAsync(result -> player.sendMessage(...), mainThread)}.
 */
public final class MainThread implements Executor {

    private final Plugin plugin;

    public MainThread(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(Runnable task) {
        if (Bukkit.isPrimaryThread()) {
            task.run();
        } else if (plugin.isEnabled()) {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }
}
