package net.skybornmc.skyblock.perks;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * Pure decisions of {@code /freerank} (owner RW4, approved command): after {@code freerank.min-playtime-minutes} of
 * total playtime a player claims the lowest rank once, then picks 1 of 3 tags; both are kept forever. No Bukkit types
 * (unit-tested).
 */
final class FreeRankRules {

    /** How many tags the player chooses from. */
    static final int TAG_CHOICES = 3;

    /** What {@code /freerank} does for a player right now. */
    enum State {
        /** Turned off in perks.yml. */
        DISABLED,
        /** Rank claimed, tag not picked yet: open the tag menu again. */
        PICK_TAG,
        /** Rank and tag done: nothing left. */
        DONE,
        /** No rank plugin: can't give the rank. */
        NO_RANKS,
        /** Not played long enough yet. */
        TOO_EARLY,
        /** Can claim now. */
        CLAIM
    }

    /** A stored claim; {@code tag} is null until picked. */
    record Claim(String group, long claimedAt, String tag) {
        boolean tagPicked() {
            return tag != null;
        }
    }

    private FreeRankRules() {
    }

    static State state(boolean enabled, boolean ranksAvailable, long playtimeMillis, int minMinutes, Claim claim) {
        if (claim != null) {
            return claim.tagPicked() ? State.DONE : State.PICK_TAG;
        }
        if (!enabled) {
            return State.DISABLED;
        }
        if (!ranksAvailable) {
            return State.NO_RANKS;
        }
        return remainingMillis(playtimeMillis, minMinutes) > 0 ? State.TOO_EARLY : State.CLAIM;
    }

    /** Playtime still needed (0 when enough). */
    static long remainingMillis(long playtimeMillis, int minMinutes) {
        return Math.max(0, Math.max(0, minMinutes) * 60_000L - Math.max(0, playtimeMillis));
    }

    /** The configured tag ids that exist, lower-case, without duplicates, at most {@link #TAG_CHOICES}. */
    static List<String> tagChoices(List<String> configured, Collection<String> known) {
        List<String> out = new ArrayList<>();
        for (String raw : configured) {
            if (raw == null) {
                continue;
            }
            String id = raw.strip().toLowerCase(Locale.ROOT);
            if (!id.isEmpty() && known.contains(id) && !out.contains(id) && out.size() < TAG_CHOICES) {
                out.add(id);
            }
        }
        return out;
    }

    /** Whether {@code tag} is one of the offered choices (menu clicks are checked again, never trusted). */
    static boolean validChoice(String tag, List<String> choices) {
        return tag != null && choices.contains(tag.toLowerCase(Locale.ROOT));
    }
}
