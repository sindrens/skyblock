package net.skybornmc.skyblock.common.profile;

import java.util.Optional;
import java.util.regex.Pattern;

/** Rules for the names players give their profiles (owner P10). Pure logic, unit-tested. */
public final class ProfileNames {

    private static final Pattern ALLOWED = Pattern.compile("[A-Za-z0-9 _-]+");

    /** Why a name was refused. The key is used in the messages ({@code rename.<key>}). */
    public enum Problem {
        EMPTY("empty"), TOO_LONG("too-long"), CHARACTERS("characters");

        public final String key;

        Problem(String key) {
            this.key = key;
        }
    }

    private ProfileNames() {
    }

    /** Trims and folds repeated spaces ("  My   farm " → "My farm"). */
    public static String clean(String raw) {
        return raw == null ? "" : raw.trim().replaceAll("\\s+", " ");
    }

    /** Problem with a (cleaned) name, or empty if it can be used. */
    public static Optional<Problem> check(String raw, int maxLength) {
        String name = clean(raw);
        if (name.isEmpty()) {
            return Optional.of(Problem.EMPTY);
        }
        if (name.length() > maxLength) {
            return Optional.of(Problem.TOO_LONG);
        }
        if (!ALLOWED.matcher(name).matches()) {
            return Optional.of(Problem.CHARACTERS);
        }
        return Optional.empty();
    }
}
