package net.skybornmc.skyblock.chat;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Random;

/**
 * Which card comes next: random but fair (a shuffled bag, every card once before any card again) and never the same
 * card twice in a row, also across bags. Cards that are not available right now (their command is missing) are left
 * out; a card that comes back joins the next bag. With a single available card it repeats (nothing else to show).
 * Main thread. Pure, unit-tested.
 */
final class BroadcastRotation {

    private final Random random;
    private final Deque<String> bag = new ArrayDeque<>();
    private String last;

    BroadcastRotation(Random random) {
        this.random = random;
    }

    /** The next card out of {@code available} (in config order), or null when there is none. */
    String next(List<String> available) {
        if (available == null || available.isEmpty()) {
            return null;
        }
        bag.removeIf(id -> !available.contains(id));
        if (bag.isEmpty()) {
            List<String> fresh = new ArrayList<>(new java.util.LinkedHashSet<>(available));
            Collections.shuffle(fresh, random);
            if (fresh.size() > 1 && fresh.getFirst().equals(last)) {
                // never back-to-back across two bags: swap the repeat with a random other card
                int other = 1 + random.nextInt(fresh.size() - 1);
                Collections.swap(fresh, 0, other);
            }
            bag.addAll(fresh);
        }
        String id = bag.pollFirst();
        if (id != null && id.equals(last) && !bag.isEmpty()) {
            // only possible after cards were removed from the bag: take the following one, keep this one for later
            String instead = bag.pollFirst();
            bag.addLast(id);
            id = instead;
        }
        last = id;
        return id;
    }

    /** The last card handed out (null before the first). */
    String last() {
        return last;
    }

    /** Forgets the bag (cards list changed on reload); the last card is kept so it does not repeat right away. */
    void reset() {
        bag.clear();
    }
}
