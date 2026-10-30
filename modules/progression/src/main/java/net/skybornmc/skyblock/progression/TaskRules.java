package net.skybornmc.skyblock.progression;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** Pure rules of the task engine (PHASE-5 M5.3); unit-tested. */
final class TaskRules {

    private TaskRules() {
    }

    /**
     * The island an action counts for, or null (anti-abuse, same as the pillars):
     *
     * @param ownIsland     the player's own island (null = none)
     * @param counts        the player counts for it: a stored member, never a coop player ({@code countsForIsland})
     * @param inIslandWorld the action happened in the islands world
     * @param islandHere    the island at the action's place (islands world only)
     * @return {@code ownIsland} when the action counts: outside the islands world (Depths) any counting member, inside it
     *         only on their own island (visitors on other islands never count)
     */
    static java.util.UUID countingIsland(UUID ownIsland, boolean counts, boolean inIslandWorld, UUID islandHere) {
        if (ownIsland == null || !counts) {
            return null;
        }
        if (inIslandWorld && !ownIsland.equals(islandHere)) {
            return null;
        }
        return ownIsland;
    }

    /** Counter keys an action of {@code type} on {@code object} adds to: the object's and the {@code any} counter. */
    static String[] keys(String typeId, String object) {
        String o = object == null ? "any" : object.toLowerCase(Locale.ROOT);
        return "any".equals(o) ? new String[]{typeId + ":any"} : new String[]{typeId + ":" + o, typeId + ":any"};
    }

    /** True for sell sources that never count (AFK pool by default). */
    static boolean excludedSource(String source, Set<String> excluded) {
        return source != null && excluded.contains(source.toLowerCase(Locale.ROOT));
    }

    /**
     * {@code task:<season>:<island hex>:<member hex>:<12 hex of sha-256(task id)>}, at most ~86 characters (limit 96).
     * One key per member and task, so a reward can never be paid twice even if the done row were lost.
     */
    static String idempotencyKey(int season, UUID island, String taskId, UUID member) {
        return "task:" + season + ":" + hex(island) + ":" + hex(member) + ":" + shortHash(taskId);
    }

    static String shortHash(String value) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(d, 0, 6);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String hex(UUID id) {
        return id.toString().replace("-", "");
    }

    /**
     * How many of each stack to take for a hand-in: walks the matching stacks in order until {@code needed} is met.
     *
     * @param amounts amount of every slot (0 = not matching)
     * @return amount to take from each slot (same length)
     */
    static int[] plan(int[] amounts, long needed) {
        int[] take = new int[amounts.length];
        long left = Math.max(0, needed);
        for (int i = 0; i < amounts.length && left > 0; i++) {
            int n = (int) Math.min(Math.max(0, amounts[i]), left);
            take[i] = n;
            left -= n;
        }
        return take;
    }

    static long sum(int[] values) {
        long s = 0;
        for (int v : values) {
            s += Math.max(0, v);
        }
        return s;
    }

    /** "coal_ore" → "Coal Ore", "custom:depths_coal" → "Depths Coal" (fallback names; tasks.yml can set a name). */
    static String pretty(String id) {
        if (id == null || id.isBlank()) {
            return "";
        }
        String s = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
        StringBuilder out = new StringBuilder();
        for (String part : s.split("_")) {
            if (part.isEmpty()) {
                continue;
            }
            if (!out.isEmpty()) {
                out.append(' ');
            }
            out.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return out.toString();
    }

    /** Simple English plural of a display name ("Zombie" → "Zombies", "Witch" → "Witches", "Drowned" stays). */
    static String plural(String name) {
        if (name == null || name.isEmpty()) {
            return "";
        }
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.endsWith("drowned") || lower.endsWith("sheep") || lower.endsWith("fish") || lower.endsWith("s")) {
            return name;
        }
        if (lower.endsWith("ch") || lower.endsWith("sh") || lower.endsWith("x")) {
            return name + "es";
        }
        if (lower.endsWith("y") && lower.length() > 1 && "aeiou".indexOf(lower.charAt(lower.length() - 2)) < 0) {
            return name.substring(0, name.length() - 1) + "ies";
        }
        return name + "s";
    }

    /** Whole percent 0-100. */
    static int percent(long value, long goal) {
        if (goal <= 0) {
            return 100;
        }
        return (int) Math.max(0, Math.min(100, Math.floor(value * 100.0 / goal)));
    }
}
