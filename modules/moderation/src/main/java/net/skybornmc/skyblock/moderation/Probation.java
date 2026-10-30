package net.skybornmc.skyblock.moderation;

import java.time.Duration;
import java.util.List;
import java.util.function.Predicate;

/**
 * Probation (spec 13.7), pure. After a mute or ban ends naturally (its time ran out) the player is on probation for
 * {@code duration}; during probation a new offence uses the ladder step + {@code stepBonus} (never past the last step).
 * Pardoned or undone punishments never start probation, nor do permanent ones (they never end). Probation is derived
 * from the history every time (nothing stored), so it can never get out of sync. Independent of offence expiry (12.3)
 * and warn weights (12.7).
 */
final class Probation {

    private Probation() {
    }

    /**
     * {@code extras.probation}.
     *
     * @param enabled   off = no probation at all
     * @param duration  how long probation lasts after the natural end
     * @param stepBonus ladder steps added during probation (0 = none)
     */
    record Settings(boolean enabled, Duration duration, int stepBonus) {
        static final Settings DEFAULT = new Settings(true, Duration.ofDays(14), 1);
        static final Settings DISABLED = new Settings(false, Duration.ofDays(14), 0);
    }

    /** A mute or ban that ended because its time ran out (not pardoned, not undone, not permanent). */
    static boolean endedNaturally(Punishment p, long now) {
        return (p.type() == Punishment.Type.MUTE || p.type() == Punishment.Type.BAN) && !p.removed()
                && p.expires() != null && p.expires() <= now;
    }

    /**
     * Until when the player is on probation, or {@code null} if not. {@code starts} says whether a punishment's reason id
     * (its template, {@code null} for direct commands) starts probation: reasons in categories with {@code probation: false}
     * do not.
     */
    static Long until(List<Punishment> history, Predicate<String> starts, Settings settings, long now) {
        if (!settings.enabled() || settings.duration() == null || settings.duration().isZero()) {
            return null;
        }
        Long best = null;
        for (Punishment p : history) {
            if (!endedNaturally(p, now) || !starts.test(p.template())) {
                continue;
            }
            long end = p.expires() + settings.duration().toMillis();
            if (end > now && (best == null || end > best)) {
                best = end;
            }
        }
        return best;
    }

    /** The ladder index during probation: {@code index + bonus}, never past the last step. */
    static int raise(int index, int bonus, int ladderSize) {
        if (ladderSize <= 0) {
            return 0;
        }
        return Math.min(Math.max(0, index) + Math.max(0, bonus), ladderSize - 1);
    }

    /** True if the bonus applies to this request: on probation, bonus above 0 and the reason has probation on. */
    static boolean applies(Long until, int bonus, boolean reasonProbation, long now) {
        return until != null && until > now && bonus > 0 && reasonProbation;
    }
}
