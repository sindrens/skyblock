package net.skybornmc.skyblock.common.hud;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * One clean action bar line (plan T section 1, owner 4 Nov 2026 png 1: "a lot more clean over the hotbar when grinding").
 * Several modules write to the action bar while a player grinds (skill XP, shards, the /farm and /mine bonus, tier
 * progress, warnings) and used to overwrite each other every few ticks. Now each one says how important its line is and
 * how long it should stay:
 * <ul>
 *   <li>a line of <b>higher</b> priority that is still showing wins: the lower one is dropped (it comes again later);</li>
 *   <li>lines of the <b>same</b> priority while one shows are joined into one line ({@code +12 Farming Shards · +$40 bonus}),
 *       each part kept by its key, so a new shard batch replaces the old shard part;</li>
 *   <li>a lower or expired line is replaced.</li>
 * </ul>
 * Main thread only (pure apart from sending; the decision logic is unit-tested through {@link #decide}).
 */
public final class ActionBars {

    /** Skill XP gains, the default grind line. */
    public static final int XP = 10;
    /** Tier progress, small hints. */
    public static final int INFO = 15;
    /** Money and shard payouts, Auto-Sell summaries. */
    public static final int PAYOUT = 20;
    /** Storage full, inventory full, rare finds, refusals. */
    public static final int WARNING = 30;

    private static final Component SEPARATOR = Component.text("  ·  ", net.kyori.adventure.text.format.NamedTextColor.DARK_GRAY);

    /** What a player's action bar shows right now. */
    static final class Hold {
        final int priority;
        long until;
        final Map<String, Component> parts = new LinkedHashMap<>();

        Hold(int priority, long until) {
            this.priority = priority;
            this.until = until;
        }
    }

    /** What to do with a new line. */
    enum Decision { DROP, MERGE, REPLACE }

    private static final Map<UUID, Hold> HOLDS = new ConcurrentHashMap<>();

    private ActionBars() {
    }

    /** Pure: what happens to a line of {@code priority} at {@code now} while {@code current} shows. */
    static Decision decide(Hold current, int priority, long now) {
        if (current == null || current.until <= now || priority > current.priority) {
            return Decision.REPLACE;
        }
        return priority == current.priority ? Decision.MERGE : Decision.DROP;
    }

    /**
     * Shows a line part on the player's action bar.
     *
     * @param key      what the part is ({@code shards}, {@code bonus}, {@code xp}); a new part with the same key replaces it
     * @param priority {@link #XP}, {@link #INFO}, {@link #PAYOUT}, {@link #WARNING} (or anything in between)
     * @param millis   how long the line stays before a lower one may take its place (the client fades it after ~3 s)
     */
    public static void show(Player player, String key, Component line, int priority, long millis) {
        if (player == null || line == null) {
            return;
        }
        long now = System.currentTimeMillis();
        UUID id = player.getUniqueId();
        Hold current = HOLDS.get(id);
        Hold hold;
        switch (decide(current, priority, now)) {
            case DROP -> {
                return;
            }
            case MERGE -> {
                hold = current;
                hold.until = Math.max(hold.until, now + millis);
            }
            default -> {
                hold = new Hold(priority, now + millis);
                HOLDS.put(id, hold);
            }
        }
        hold.parts.remove(key);
        hold.parts.put(key, line);
        player.sendActionBar(join(hold));
    }

    /** Forgets a player (quit). */
    public static void forget(UUID player) {
        HOLDS.remove(player);
    }

    private static Component join(Hold hold) {
        Component out = Component.empty();
        boolean first = true;
        for (Component part : hold.parts.values()) {
            if (!first) {
                out = out.append(SEPARATOR);
            }
            out = out.append(part);
            first = false;
        }
        return out;
    }
}
