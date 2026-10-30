package net.skybornmc.skyblock.moderation;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Appeal rules (spec 13.10), pure: who may appeal what, the text limits, the cooldown after a denied appeal, which
 * punishment {@code /appeal <text>} means, and the Reduce range. No server, no database.
 */
final class AppealRules {

    private AppealRules() {
    }

    /** Why an appeal is refused (message keys {@code appeal.problem.<kebab>}). */
    enum Problem {
        DISABLED, NOTHING, NOT_FOUND, NOT_YOURS, NOT_ACTIVE, TYPE, REASON, ALREADY_OPEN, COOLDOWN, TOO_SHORT, TOO_LONG;

        String key() {
            return "appeal.problem." + name().toLowerCase(Locale.ROOT).replace('_', '-');
        }
    }

    /**
     * Whether {@code player} may appeal {@code p} now.
     *
     * @param previous          earlier appeals of this punishment (any status)
     * @param reasonAppealable  the punishment's reason allows appeals ({@code appealable:} in punishments.yml)
     */
    static Optional<Problem> canAppeal(Punishment p, UUID player, List<Appeal> previous, boolean reasonAppealable,
                                       PunishConfig.Appeals cfg, long now) {
        if (!cfg.enabled()) {
            return Optional.of(Problem.DISABLED);
        }
        if (p == null) {
            return Optional.of(Problem.NOT_FOUND);
        }
        if (p.target() == null || !p.target().equals(player)) {
            return Optional.of(Problem.NOT_YOURS);
        }
        if (!cfg.types().contains(p.type())) {
            return Optional.of(Problem.TYPE);
        }
        if (!p.active(now)) {
            return Optional.of(Problem.NOT_ACTIVE);
        }
        if (!reasonAppealable) {
            return Optional.of(Problem.REASON);
        }
        if (previous.stream().anyMatch(Appeal::open)) {
            return Optional.of(Problem.ALREADY_OPEN);
        }
        Long until = cooldownUntil(previous, cfg.cooldown());
        if (until != null && until > now) {
            return Optional.of(Problem.COOLDOWN);
        }
        return Optional.empty();
    }

    /**
     * End of the cooldown after the newest denied (or reduced) appeal, or {@code null}. Reduced counts too: staff already
     * looked at it and decided, so a new appeal the next minute would only spam the queue.
     */
    static Long cooldownUntil(List<Appeal> previous, Duration cooldown) {
        Long newest = null;
        for (Appeal a : previous) {
            if ((a.status() == Appeal.Status.DENIED || a.status() == Appeal.Status.REDUCED) && a.handled() != null
                    && (newest == null || a.handled() > newest)) {
                newest = a.handled();
            }
        }
        return newest == null ? null : newest + cooldown.toMillis();
    }

    /** Text limits (characters after trimming and joining whitespace). */
    static Optional<Problem> checkText(String text, PunishConfig.Appeals cfg) {
        int length = clean(text).length();
        if (length < cfg.minLength()) {
            return Optional.of(Problem.TOO_SHORT);
        }
        if (length > cfg.maxLength()) {
            return Optional.of(Problem.TOO_LONG);
        }
        return Optional.empty();
    }

    /** One line: control characters and runs of whitespace become one space. */
    static String clean(String text) {
        return text == null ? "" : text.replaceAll("[\\p{Cntrl}\\u2028\\u2029]+", " ").replaceAll("\\s+", " ").strip();
    }

    /**
     * {@code /appeal <id> <text>} or {@code /appeal <text>}: a first word {@code 12} / {@code #12} followed by more text is
     * the punishment id.
     */
    record Args(Long id, String text) {
    }

    static Args parse(String raw) {
        String s = raw == null ? "" : raw.strip();
        int space = s.indexOf(' ');
        if (space > 0) {
            Long id = LogEvidence.parseId(s.substring(0, space));
            if (id != null) {
                return new Args(id, s.substring(space + 1).strip());
            }
        }
        return new Args(null, s);
    }

    /**
     * The punishment {@code /appeal <text>} is about: the player's active mute (the longest), else their newest active
     * appealable punishment, or {@code null}.
     */
    static Punishment pick(List<Punishment> history, UUID player, PunishConfig.Appeals cfg, Predicate<String> reasonAppealable,
                           long now) {
        List<Punishment> candidates = history.stream().filter(p -> player.equals(p.target()) && p.active(now)
                && cfg.types().contains(p.type()) && reasonAppealable.test(p.template())).toList();
        Punishment mute = PunishmentStore.longest(candidates.stream().filter(p -> p.type() == Punishment.Type.MUTE).toList());
        if (mute != null) {
            return mute;
        }
        Punishment best = null;
        for (Punishment p : candidates) {
            if (best == null || p.created() > best.created()) {
                best = p;
            }
        }
        return best;
    }

    /** The unit of the Reduce picker. */
    enum Unit {
        MINUTES(60_000L), HOURS(3_600_000L), DAYS(86_400_000L);

        final long millis;

        Unit(long millis) {
            this.millis = millis;
        }

        String key() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /**
     * What the Reduce picker offers: the new <b>total</b> length (from when it was given) in {@code unit}, from
     * {@code min} to {@code max}, always shorter than now. {@code null} = can't be reduced (a 1-minute punishment).
     */
    record ReduceRange(Unit unit, long min, long max, long initial) {
    }

    static ReduceRange reduceRange(Punishment p, int reduceMaxDays) {
        if (p.expires() == null) {
            return new ReduceRange(Unit.DAYS, 1, reduceMaxDays, Math.min(30, reduceMaxDays));
        }
        long length = p.expires() - p.created();
        Unit unit = length >= 2 * Unit.DAYS.millis ? Unit.DAYS : length >= 2 * Unit.HOURS.millis ? Unit.HOURS : Unit.MINUTES;
        long max = (length - 1) / unit.millis; // strictly shorter than now
        if (max < 1) {
            return null;
        }
        return new ReduceRange(unit, 1, max, Math.max(1, max / 2));
    }

    /**
     * The new end for a total length of {@code amount} {@code unit}s, or {@code null} if that is not shorter (refused).
     * An end already in the past means the punishment ends now.
     */
    static Long reducedEnd(Punishment p, Unit unit, long amount, long now) {
        if (amount < 1) {
            return null;
        }
        long end = p.created() + amount * unit.millis;
        if (p.expires() != null && end >= p.expires()) {
            return null;
        }
        return Math.max(end, now);
    }

    /** "2 bans, 1 mute, 3 warns" counts for the appeal card and the Discord summary (never IPs). */
    record HistorySummary(long bans, long mutes, long warns, long kicks) {
    }

    static HistorySummary summary(List<Punishment> history) {
        long b = 0;
        long m = 0;
        long w = 0;
        long k = 0;
        for (Punishment p : history) {
            switch (p.type()) {
                case BAN -> b++;
                case MUTE -> m++;
                case WARN -> w++;
                case KICK -> k++;
            }
        }
        return new HistorySummary(b, m, w, k);
    }
}
