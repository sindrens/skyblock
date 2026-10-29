package net.skybornmc.skyblock.staff;

import java.util.Collection;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Pure decisions of {@code /freeze} (no Bukkit types, unit-tested): which commands a frozen player may run, whether a
 * position counts as "moved", and which teleports may move a frozen player.
 */
final class FreezeRules {

    /** Movement smaller than this (blocks, per axis) is floating-point noise, not a move. */
    static final double EPSILON = 1.0E-4;

    private FreezeRules() {
    }

    /** The command label of a chat command line: {@code "/Minecraft:MSG Bob hi"} → {@code "msg"}. Empty if none. */
    static String commandLabel(String message) {
        if (message == null) {
            return "";
        }
        String text = message.strip();
        while (text.startsWith("/")) {
            text = text.substring(1).stripLeading();
        }
        int space = indexOfWhitespace(text);
        String label = (space < 0 ? text : text.substring(0, space)).toLowerCase(Locale.ROOT);
        int colon = label.lastIndexOf(':');
        return colon >= 0 ? label.substring(colon + 1) : label;
    }

    private static int indexOfWhitespace(String text) {
        for (int i = 0; i < text.length(); i++) {
            if (Character.isWhitespace(text.charAt(i))) {
                return i;
            }
        }
        return -1;
    }

    /** Lower-case labels without a leading slash or namespace; blanks dropped. */
    static Set<String> normalize(Collection<String> labels) {
        return labels.stream().map(FreezeRules::commandLabel).filter(s -> !s.isEmpty()).collect(Collectors.toUnmodifiableSet());
    }

    /** Whether a frozen player may run this command line. Only labels on the whitelist pass; an empty whitelist blocks all. */
    static boolean commandAllowed(String message, Collection<String> whitelist) {
        String label = commandLabel(message);
        return !label.isEmpty() && normalize(whitelist).contains(label);
    }

    /** True if {@code to} is a different position than {@code anchor} (rotation is not compared). */
    static boolean moved(double ax, double ay, double az, double tx, double ty, double tz) {
        return Math.abs(ax - tx) > EPSILON || Math.abs(ay - ty) > EPSILON || Math.abs(az - tz) > EPSILON;
    }

    /** Teleport cause name as written in staff.yml: {@code ENDER_PEARL} → {@code ender-pearl}. */
    static String causeKey(String cause) {
        return cause == null ? "" : cause.strip().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    /**
     * Whether a teleport may move a frozen player. Our own snap-back to the freeze spot always passes; otherwise only the
     * configured causes (staff/plugin teleports by default). Player-made teleports (ender pearl, chorus fruit, portals,
     * spectating, beds, dismounting) are refused unless the owner adds them.
     */
    static boolean teleportAllowed(String cause, Collection<String> allowedCauses, boolean toFreezeSpot) {
        if (toFreezeSpot) {
            return true;
        }
        String key = causeKey(cause);
        return allowedCauses.stream().map(FreezeRules::causeKey).anyMatch(key::equals);
    }
}
