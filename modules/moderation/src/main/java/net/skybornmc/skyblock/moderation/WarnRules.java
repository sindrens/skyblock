package net.skybornmc.skyblock.moderation;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * The warn counter and the automatic warn rules (spec §5). Pure logic: everything is derived from the stored punishments
 * and suggestion rows, never from a separately kept number, so it survives restarts and can't get out of sync.
 *
 * <p>Definitions (owner decisions 2026-10-29):</p>
 * <ul>
 *   <li><b>Active warn</b>: type WARN, not pardoned, younger than {@code warn-expire} ({@code perm} = never expires).</li>
 *   <li><b>Trigger</b>: a suggestion row. A <i>handled</i> trigger (applied, dismissed, auto-applied or skipped) restarts
 *       the counter when {@code reset-after-trigger} is on: warns created up to the moment it was handled no longer
 *       count. A pending trigger blocks new triggers (one at a time). An expired pending trigger (nobody clicked within
 *       {@code pending-expire}) is ignored as if it never fired, so the next warn reminds staff again.</li>
 *   <li><b>Repeat</b>: a new warn, or a new manual mute, within {@code repeat-window} of the last handled trigger
 *       (counted from the trigger's creation) suggests {@code repeat-action} instead. Automatic punishments never count
 *       as such an event, so a rule can never feed itself.</li>
 *   <li><b>Jumps</b>: the highest rule with {@code at <= count} that has not fired since the last reset fires; a rule
 *       lower than one that already fired never fires afterwards.</li>
 * </ul>
 */
final class WarnRules {

    enum Mode { NOTIFY, AUTO }

    /** One rule: at {@code at} active warns, suggest {@code action} (always a mute or ban, checked at load). */
    record Rule(int at, Templates.Step action) {
    }

    /**
     * {@code warn-rules} of punishments.yml.
     *
     * @param warnExpire    {@code null} = warns never expire
     * @param repeatWindow  {@code null} = no repeat rule
     * @param repeatAction  {@code null} = no repeat rule
     */
    record Settings(boolean enabled, Mode mode, Duration warnExpire, boolean resetAfterTrigger, Duration pendingExpire,
                    List<Rule> rules, Duration repeatWindow, Templates.Step repeatAction) {

        static final Settings DISABLED = new Settings(false, Mode.NOTIFY, Duration.ofDays(30), true, Duration.ofHours(24),
                List.of(), null, null);

        boolean repeatEnabled() {
            return repeatWindow != null && repeatAction != null;
        }
    }

    /** What fired: a rule ({@code ruleAt}) or the repeat rule ({@code repeat}). */
    record Trigger(Integer ruleAt, boolean repeat, Templates.Step action, int count) {

        String source() {
            return Punishment.AUTO_PREFIX + Suggestion.KIND_WARN_RULE + ":" + (repeat ? "repeat" : String.valueOf(ruleAt));
        }
    }

    /**
     * The counter shown in {@code /p} and {@code /warns}.
     *
     * @param next         the next rule that can fire, or {@code null}
     * @param expiries     when each counted warn stops counting (empty entries for perm warns are left out)
     * @param repeatUntil  end of the open repeat window, or {@code null}
     * @param pending      the waiting suggestion, or {@code null}
     */
    record Counter(int count, Rule next, List<Long> expiries, Long repeatUntil, Suggestion pending) {
    }

    private WarnRules() {
    }

    /** Active warn at {@code now} (not pardoned, not older than warn-expire). */
    static boolean activeWarn(Punishment p, Settings s, long now) {
        if (p.type() != Punishment.Type.WARN || p.removed()) {
            return false;
        }
        return s.warnExpire() == null || p.created() + s.warnExpire().toMillis() > now;
    }

    static Optional<Suggestion> pending(List<Suggestion> suggestions, long now) {
        return suggestions.stream().filter(x -> x.pending(now)).max(Comparator.comparingLong(Suggestion::created));
    }

    /** The newest handled trigger (opens the repeat window). */
    static Optional<Suggestion> lastHandled(List<Suggestion> suggestions) {
        return suggestions.stream().filter(Suggestion::handled).max(Comparator.comparingLong(Suggestion::created));
    }

    /** Warns created at or before this moment no longer count ({@code Long.MIN_VALUE} = no reset yet / reset off). */
    static long resetPoint(List<Suggestion> suggestions, Settings s) {
        if (!s.resetAfterTrigger()) {
            return Long.MIN_VALUE;
        }
        long point = Long.MIN_VALUE;
        for (Suggestion x : suggestions) {
            if (x.handled()) {
                point = Math.max(point, x.handledTime());
            }
        }
        return point;
    }

    /** The warns that count right now, oldest first. */
    static List<Punishment> counted(List<Punishment> history, List<Suggestion> suggestions, Settings s, long now) {
        long reset = resetPoint(suggestions, s);
        List<Punishment> out = new ArrayList<>();
        for (Punishment p : history) {
            if (activeWarn(p, s, now) && p.created() > reset) {
                out.add(p);
            }
        }
        out.sort(Comparator.comparingLong(Punishment::created));
        return out;
    }

    /** Highest rule 'at' that already fired since the last reset (0 = none). */
    static int highestFired(List<Punishment> counted, List<Suggestion> suggestions, Settings s, long now) {
        long since;
        if (s.resetAfterTrigger()) {
            since = resetPoint(suggestions, s);
        } else {
            // Without resets a rule counts as fired while the warns it counted are still active.
            since = counted.isEmpty() ? Long.MAX_VALUE : counted.getFirst().created();
        }
        int highest = 0;
        for (Suggestion x : suggestions) {
            if (x.ruleAt() == null || x.expired(now)) {
                continue;
            }
            boolean after = s.resetAfterTrigger() ? x.created() > since : x.created() >= since;
            if (after) {
                highest = Math.max(highest, x.ruleAt());
            }
        }
        return highest;
    }

    /**
     * Decides whether {@code event} (a punishment that was just saved and is included in {@code history}) fires a rule.
     * Call this in the same transaction that saved it (two simultaneous warns must fire once).
     */
    static Optional<Trigger> evaluate(Punishment event, List<Punishment> history, List<Suggestion> suggestions, Settings s,
                                      long now) {
        if (!s.enabled() || event.automatic()) {
            return Optional.empty();
        }
        if (event.type() != Punishment.Type.WARN && event.type() != Punishment.Type.MUTE) {
            return Optional.empty();
        }
        if (pending(suggestions, now).isPresent()) {
            return Optional.empty(); // one waiting suggestion at a time; it is shown in /p and /warns
        }
        Optional<Suggestion> last = lastHandled(suggestions);
        List<Punishment> counted = counted(history, suggestions, s, now);
        if (s.repeatEnabled() && last.isPresent()) {
            long since = event.created() - last.get().created();
            if (since >= 0 && since <= s.repeatWindow().toMillis()) {
                return Optional.of(new Trigger(null, true, s.repeatAction(), counted.size()));
            }
        }
        if (event.type() != Punishment.Type.WARN) {
            return Optional.empty();
        }
        int count = counted.size();
        int fired = highestFired(counted, suggestions, s, now);
        Rule best = null;
        for (Rule rule : s.rules()) {
            if (rule.at() <= count && rule.at() > fired && (best == null || rule.at() > best.at())) {
                best = rule;
            }
        }
        return best == null ? Optional.empty() : Optional.of(new Trigger(best.at(), false, best.action(), count));
    }

    /** The counter for display. */
    static Counter counter(List<Punishment> history, List<Suggestion> suggestions, Settings s, long now) {
        List<Punishment> counted = counted(history, suggestions, s, now);
        int count = counted.size();
        int fired = highestFired(counted, suggestions, s, now);
        Rule next = null;
        for (Rule rule : s.rules()) {
            if (rule.at() > fired && rule.at() > count && (next == null || rule.at() < next.at())) {
                next = rule;
            }
        }
        List<Long> expiries = new ArrayList<>();
        if (s.warnExpire() != null) {
            for (Punishment p : counted) {
                expiries.add(p.created() + s.warnExpire().toMillis());
            }
        }
        Long repeatUntil = null;
        Optional<Suggestion> last = lastHandled(suggestions);
        if (s.repeatEnabled() && last.isPresent()) {
            long end = last.get().created() + s.repeatWindow().toMillis();
            if (end > now) {
                repeatUntil = end;
            }
        }
        return new Counter(count, next, List.copyOf(expiries), repeatUntil, pending(suggestions, now).orElse(null));
    }
}
