package net.skybornmc.skyblock.wardrobe;

import java.util.Collection;
import java.util.Optional;
import java.util.function.IntPredicate;
import java.util.regex.Pattern;

/**
 * The loadout rules (WARDROBE §7.2-§7.3, LD2/LD3) as pure functions: how many presets a player has, which names are
 * allowed and whether a swap may happen now. Unit-tested; the module feeds them live values.
 */
final class LoadoutRules {

    /** Letters, digits, _ and - only: the name is also the argument of {@code /loadout <name>} (one word, Bedrock-safe). */
    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_-]+");

    private LoadoutRules() {
    }

    /**
     * Presets a player may have: {@code max(free, rank slots) + bought (at most boughtMax)}, never above {@code max}.
     *
     * @param free      free presets (L2: 2)
     * @param rankSlots highest {@code skyblock.loadout.slots.<n>} the player has (0 = none)
     * @param bought    slots bought with store credits (account-wide)
     * @param boughtMax most slots that can be bought (+3)
     * @param max       hard cap (8)
     */
    static int slots(int free, int rankSlots, int bought, int boughtMax, int max) {
        int base = Math.max(Math.max(0, free), Math.max(0, rankSlots));
        int extra = Math.max(0, Math.min(bought, Math.max(0, boughtMax)));
        return Math.max(0, Math.min(Math.max(0, max), base + extra));
    }

    /** The highest n (1..max) the player has {@code skyblock.loadout.slots.<n>} for, 0 if none. */
    static int rankSlots(int max, IntPredicate has) {
        for (int n = max; n >= 1; n--) {
            if (has.test(n)) {
                return n;
            }
        }
        return 0;
    }

    /** Why a name was refused; the key is the message {@code loadout.name.<key>}. */
    enum NameProblem {
        EMPTY("empty"), TOO_LONG("too-long"), CHARACTERS("characters"), TAKEN("taken");

        final String key;

        NameProblem(String key) {
            this.key = key;
        }
    }

    /** Trims; inner spaces become {@code -} ("Deep mine" → "Deep-mine"), so typed names stay one word. */
    static String cleanName(String raw) {
        return raw == null ? "" : raw.trim().replaceAll("\\s+", "-");
    }

    /**
     * Problem with a (cleaned) name, or empty if it can be used.
     *
     * @param others names of the profile's other presets (a rename keeps its own name)
     */
    static Optional<NameProblem> checkName(String name, int maxLength, Collection<String> others) {
        if (name == null || name.isEmpty()) {
            return Optional.of(NameProblem.EMPTY);
        }
        if (name.length() > maxLength) {
            return Optional.of(NameProblem.TOO_LONG);
        }
        if (!NAME.matcher(name).matches()) {
            return Optional.of(NameProblem.CHARACTERS);
        }
        String key = Loadout.key(name);
        for (String other : others) {
            if (Loadout.key(other).equals(key)) {
                return Optional.of(NameProblem.TAKEN);
            }
        }
        return Optional.empty();
    }

    /** First free menu place (0-based) below {@code slots}, or -1 if every place is used. */
    static int freeIndex(Collection<Integer> used, int slots) {
        for (int i = 0; i < slots; i++) {
            if (!used.contains(i)) {
                return i;
            }
        }
        return -1;
    }

    /** Everything that decides whether a swap may run now (L4). */
    record State(boolean loaded, boolean busy, boolean switching, boolean frozen, boolean blockedWorld, long combatLeftMillis,
                 long cooldownLeftMillis, boolean cooldownBypass) {
    }

    /** Why a swap can't run now: the message key, or empty. Order: the most useful answer first. */
    static Optional<String> problem(State s) {
        if (!s.loaded()) {
            return Optional.of("loading");
        }
        if (s.busy() || s.switching()) {
            return Optional.of("busy");
        }
        if (s.frozen()) {
            return Optional.of("frozen");
        }
        if (s.blockedWorld()) {
            return Optional.of("loadout.blocked-world");
        }
        if (s.combatLeftMillis() > 0) {
            return Optional.of("in-combat");
        }
        if (s.cooldownLeftMillis() > 0 && !s.cooldownBypass()) {
            return Optional.of("cooldown");
        }
        return Optional.empty();
    }
}
