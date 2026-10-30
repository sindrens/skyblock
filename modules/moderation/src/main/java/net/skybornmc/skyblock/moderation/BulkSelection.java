package net.skybornmc.skyblock.moderation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Bulk punish (spec 13.8), the pure parts: which players a bulk action is about, what happens to each of them (from the
 * same decision pipeline as a single punishment) and the summary. No server, no database.
 *
 * <p>Rules: at most {@code bulk.max} players (more is refused, never cut silently); names are case-insensitive and each
 * player counts once; every player gets their own next ladder step (own offences, probation, active punishments); a
 * verdict that would not apply is a skip with its reason (exempt, already muted/banned longer, just punished, no
 * permission ...); a REQUEST verdict (spec 13.4, e.g. {@code ban perm}) is one approval request for that player.</p>
 */
final class BulkSelection {

    /** Source prefix of punishments from a bulk action ({@code bulk:<group>}): one group per action. */
    static final String SOURCE_PREFIX = "bulk:";

    private BulkSelection() {
    }

    /**
     * The names typed after the reason.
     *
     * @param names      unique names, in typed order (first spelling kept)
     * @param duplicates names typed more than once (each listed once)
     * @param tooMany    more unique names than {@code max}: the action is refused
     */
    record Names(List<String> names, List<String> duplicates, boolean tooMany, int max) {

        boolean empty() {
            return names.isEmpty();
        }
    }

    /** Splits {@code raw} on spaces and commas, drops empty parts and repeats (case-insensitive). */
    static Names parse(String raw, int max) {
        Map<String, String> unique = new LinkedHashMap<>();
        Set<String> dup = new LinkedHashSet<>();
        if (raw != null) {
            for (String part : raw.split("[\\s,]+")) {
                String name = part.strip();
                if (name.isEmpty()) {
                    continue;
                }
                String key = name.toLowerCase(Locale.ROOT);
                if (unique.putIfAbsent(key, name) != null) {
                    dup.add(unique.get(key));
                }
            }
        }
        List<String> names = List.copyOf(unique.values());
        return new Names(names, List.copyOf(dup), names.size() > max, max);
    }

    /** What happens to one player. */
    enum Kind {
        /** The punishment is applied. */
        APPLY,
        /** An approval request is created (13.4). */
        REQUEST,
        /** Nothing happens; {@code reason} says why. */
        SKIP
    }

    /** Skip reason of a name nobody knows (never joined). */
    static final String UNKNOWN = "unknown";

    /**
     * One player of the plan or the result.
     *
     * @param reason the pipeline stage that stopped it ({@code exempt}, {@code never-shorten}, {@code duplicate},
     *               {@code permission}, {@code length-limit}, {@code self}, {@code offline}) or {@link #UNKNOWN};
     *               {@code null} unless skipped
     */
    record Line(String name, Kind kind, Templates.Step step, String reason) {
    }

    /** Maps a pipeline verdict to what bulk does with it. */
    static Kind kind(PunishDecision.Verdict v) {
        return switch (v.outcome()) {
            case APPLY -> Kind.APPLY;
            case REQUEST -> Kind.REQUEST;
            case BLOCKED, SKIPPED -> Kind.SKIP;
        };
    }

    /** The line for a player from the verdict (the step is the one that will really be used, probation included). */
    static Line line(String name, PunishDecision.Verdict v, Templates.Step planned) {
        Kind kind = kind(v);
        Templates.Step step = planned;
        if (v.input() != null && kind != Kind.SKIP) {
            PunishDecision.Escalation e = v.input().escalation();
            step = e != null && e.raised() ? e.step() : new Templates.Step(v.input().type(), v.input().duration(),
                    v.input().ipWide(), planned.weight());
        }
        return new Line(name, kind, step, kind == Kind.SKIP ? v.stage() : null);
    }

    /** Counts of a plan or result. */
    record Summary(int apply, int request, int skip) {

        int total() {
            return apply + request + skip;
        }
    }

    static Summary summary(List<Line> lines) {
        int a = 0;
        int r = 0;
        int s = 0;
        for (Line l : lines) {
            switch (l.kind()) {
                case APPLY -> a++;
                case REQUEST -> r++;
                case SKIP -> s++;
            }
        }
        return new Summary(a, r, s);
    }

    /** Picker toggle: adds {@code key} if there is room (returns false when full), removes it if selected. */
    static <T> boolean toggle(Set<T> selected, T key, int max) {
        if (selected.remove(key)) {
            return true;
        }
        if (selected.size() >= max) {
            return false;
        }
        selected.add(key);
        return true;
    }

    /** {@code bulk:<base36 time><2 random chars>}: the group of one bulk action (stored as the punishments' source). */
    static String group(long now, int random) {
        return SOURCE_PREFIX + Long.toString(now, 36) + Integer.toString(Math.floorMod(random, 1296), 36);
    }

    /** True for punishments created by a bulk action. */
    static boolean isBulk(String source) {
        return source != null && source.startsWith(SOURCE_PREFIX);
    }

    /**
     * Tab completion of the player list: online and known names not typed yet, for the word being typed (the text before it
     * is kept, like the flags of {@code /p}).
     */
    static List<String> suggestions(String typed, List<String> known, int max) {
        String text = typed == null ? "" : typed;
        int cut = text.lastIndexOf(' ') + 1;
        String done = text.substring(0, cut);
        String current = text.substring(cut).toLowerCase(Locale.ROOT);
        Set<String> used = new LinkedHashSet<>();
        for (String word : done.trim().split("[\\s,]+")) {
            if (!word.isEmpty()) {
                used.add(word.toLowerCase(Locale.ROOT));
            }
        }
        List<String> out = new ArrayList<>();
        if (used.size() >= max) {
            return out;
        }
        for (String name : known) {
            String lower = name.toLowerCase(Locale.ROOT);
            if (!used.contains(lower) && lower.startsWith(current)) {
                out.add(done + name);
                if (out.size() >= 100) {
                    break;
                }
            }
        }
        return out;
    }
}
