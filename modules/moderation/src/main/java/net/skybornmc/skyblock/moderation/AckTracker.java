package net.skybornmc.skyblock.moderation;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Warning acknowledgement (spec 12.6), in memory for online players. Pure (the clock is passed in):
 * <ul>
 *   <li>{@link #warned}: the player got a warning; they now owe an acknowledgement and get a reminder every
 *       {@code repeat} up to {@code max-repeats} times ({@link #due}).</li>
 *   <li>{@link #acknowledged}: [I understand] or {@code /ack}; reminders stop.</li>
 *   <li>{@link #pending}: still owes one (used only when {@code block-until-ack} is on; off by default, so a warning
 *       never blocks gameplay).</li>
 * </ul>
 * The reminder on the next join is decided by the database ({@code ack_join_sent}), not here.
 */
final class AckTracker {

    private record State(int sent, long nextAt) {
    }

    private final Map<UUID, State> reminders = new ConcurrentHashMap<>();
    private final Set<UUID> owing = ConcurrentHashMap.newKeySet();

    /** A new warning: owe an acknowledgement, restart the reminders. */
    void warned(UUID player, long now, Duration repeat) {
        owing.add(player);
        reminders.put(player, new State(0, now + repeat.toMillis()));
    }

    /** Owes an acknowledgement without reminders (found on join). */
    void owe(UUID player) {
        owing.add(player);
    }

    /** True when no reminder is waiting (the 1 s timer then does nothing). */
    boolean idle() {
        return reminders.isEmpty();
    }

    /**
     * Players whose next reminder is due at {@code now}; each counts as sent. A player is dropped after
     * {@code maxRepeats} reminders (they still owe it: the join reminder and /mypunishments remain).
     */
    List<UUID> due(long now, Duration repeat, int maxRepeats) {
        List<UUID> out = new ArrayList<>();
        reminders.forEach((player, state) -> {
            if (state.sent() >= maxRepeats) {
                reminders.remove(player, state);
                return;
            }
            if (state.nextAt() <= now) {
                State next = new State(state.sent() + 1, now + repeat.toMillis());
                if (reminders.replace(player, state, next)) {
                    out.add(player);
                    if (next.sent() >= maxRepeats) {
                        reminders.remove(player, next);
                    }
                }
            }
        });
        return out;
    }

    /** [I understand] / {@code /ack}. Returns whether the player owed one. */
    boolean acknowledged(UUID player) {
        reminders.remove(player);
        return owing.remove(player);
    }

    boolean pending(UUID player) {
        return owing.contains(player);
    }

    /** Reminders still scheduled for this player. */
    boolean reminding(UUID player) {
        return reminders.containsKey(player);
    }

    /** Quit: forget everything (the database keeps the truth). */
    void forget(UUID player) {
        reminders.remove(player);
        owing.remove(player);
    }
}
