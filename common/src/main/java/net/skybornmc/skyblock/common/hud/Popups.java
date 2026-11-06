package net.skybornmc.skyblock.common.hud;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Small floating texts on the block or mob that gave something ("+2 shards", "+$3,500"), owner 7 Nov 2026: they float
 * up and are gone after half a second, instead of an action bar line. Main thread only.
 *
 * <p>Safe by design: one {@link TextDisplay} per popup, seen only by the player who earned it, never persistent (never
 * saved, never counted by island entity limits), removed by a task after {@link #TICKS} and all at once by
 * {@link #clear} on plugin stop. At most {@link #MAX_ALIVE} per player (a new one removes the oldest) and none when the
 * spot is more than {@link #RANGE} blocks away or in another world.</p>
 */
public final class Popups {

    /** How long a popup lives (10 ticks = 0.5 s). */
    public static final int TICKS = 10;
    /** Popups alive per player at once. */
    public static final int MAX_ALIVE = 8;
    /** No popup farther away than this (blocks). */
    public static final double RANGE = 24;
    /** How far a popup floats up while it lives (blocks). */
    static final float RISE = 0.6f;

    private static final Map<UUID, Deque<TextDisplay>> ALIVE = new ConcurrentHashMap<>();

    private Popups() {
    }

    /** Shows {@code text} above {@code block} (a block location; the popup starts on its top face) for {@code player}. */
    public static void show(Plugin plugin, Player player, Location block, Component text) {
        if (plugin == null || player == null || block == null || text == null || !player.isOnline() || block.getWorld() == null
                || !block.getWorld().equals(player.getWorld()) || block.distanceSquared(player.getLocation()) > RANGE * RANGE) {
            return;
        }
        ThreadLocalRandom r = ThreadLocalRandom.current();
        Location at = block.getBlock().getLocation().add(0.5 + r.nextDouble(-0.2, 0.2), 1.0, 0.5 + r.nextDouble(-0.2, 0.2));
        TextDisplay display;
        try {
            display = at.getWorld().spawn(at, TextDisplay.class, d -> {
                d.setPersistent(false);
                d.setVisibleByDefault(false);
                d.text(text);
                d.setBillboard(Display.Billboard.CENTER);
                d.setShadowed(true);
                d.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
                d.setSeeThrough(false);
                d.setTeleportDuration(TICKS - 1);
                d.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(0.8f, 0.8f, 0.8f), new AxisAngle4f()));
            });
        } catch (RuntimeException e) {
            return; // a chunk that refuses entities: no popup, nothing else changes
        }
        player.showEntity(plugin, display);
        Deque<TextDisplay> mine = ALIVE.computeIfAbsent(player.getUniqueId(), k -> new ArrayDeque<>());
        mine.addLast(display);
        while (mine.size() > MAX_ALIVE) {
            TextDisplay old = mine.pollFirst();
            if (old != null) {
                old.remove();
            }
        }
        UUID owner = player.getUniqueId();
        // one tick later: move to the top; the client slides it there over the teleport duration
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (display.isValid()) {
                display.teleport(display.getLocation().add(0, RISE, 0));
            }
        }, 1L);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            display.remove();
            Deque<TextDisplay> list = ALIVE.get(owner);
            if (list != null) {
                list.remove(display);
                if (list.isEmpty()) {
                    ALIVE.remove(owner, list);
                }
            }
        }, TICKS);
    }

    /** Removes every popup (plugin stop). */
    public static void clear() {
        for (Deque<TextDisplay> list : ALIVE.values()) {
            for (TextDisplay d : list) {
                d.remove();
            }
        }
        ALIVE.clear();
    }

    /** Popups alive for a player (tests, staff). */
    public static int alive(UUID player) {
        Deque<TextDisplay> list = ALIVE.get(player);
        return list == null ? 0 : list.size();
    }
}
