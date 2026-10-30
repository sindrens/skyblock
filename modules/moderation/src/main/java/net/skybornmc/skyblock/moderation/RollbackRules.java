package net.skybornmc.skyblock.moderation;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * Rollback of one staff member's recent punishments (spec 14.1), pure: which rows a rollback touches and the preview
 * numbers. Used after abuse or a hacked account: {@code /prollback <staff> <period>} shows the preview, a confirm (click
 * or typed) within {@code confirm-window} applies exactly the previewed rows that are still not reversed.
 *
 * <p>Selected: punishments <b>issued by that staff member</b> (never anyone else's, also not ones they only approved)
 * created inside the period and not pardoned/undone yet: bans, mutes, warns, island bans/coop blocks and report blocks.
 * Kicks, island kicks and coop removals happened once and are never touched. Rows that already ended by time are marked
 * reversed too (so they stop counting as offences), but there is nothing to lift. Sanctions by that staff member in the
 * period: active freeze/trade blocks are lifted, held money and items are given back.</p>
 */
final class RollbackRules {

    private RollbackRules() {
    }

    /**
     * {@code extras.rollback}.
     *
     * @param minPeriod     shortest period (15m)
     * @param maxPeriod     longest period (7d)
     * @param confirmWindow how long a preview can be confirmed
     */
    record Settings(Duration minPeriod, Duration maxPeriod, Duration confirmWindow) {
        static final Settings DEFAULT = new Settings(Duration.ofMinutes(15), Duration.ofDays(7), Duration.ofSeconds(60));
    }

    /** Why a period is refused, or {@code null}. */
    enum PeriodProblem { TOO_SHORT, TOO_LONG }

    static PeriodProblem check(Settings s, Duration period) {
        if (period == null || period.compareTo(s.minPeriod()) < 0) {
            return PeriodProblem.TOO_SHORT;
        }
        if (period.compareTo(s.maxPeriod()) > 0) {
            return PeriodProblem.TOO_LONG;
        }
        return null;
    }

    /** True if a rollback of {@code staff} for {@code [since, until]} touches this punishment. */
    static boolean selects(Punishment p, UUID staff, long since, long until) {
        return p != null && staff != null && staff.equals(p.issuer()) && p.created() >= since && p.created() <= until
                && !p.removed() && !p.type().instant();
    }

    /** True if a rollback of {@code staff} for {@code [since, until]} gives back / lifts this sanction right now. */
    static boolean selects(Sanction s, UUID staff, long since, long until, long now) {
        return s != null && staff != null && staff.equals(s.issuer()) && s.created() >= since && s.created() <= until
                && (s.blocksAt(now) || s.restorable());
    }

    /** The preview numbers: what will be reversed, and how many of those are still in force (lifted now). */
    record Counts(int bans, int mutes, int warns, int other, int sanctions, int active) {

        int punishments() {
            return bans + mutes + warns + other;
        }

        int total() {
            return punishments() + sanctions;
        }
    }

    static Counts count(List<Punishment> punishments, List<Sanction> sanctions, long now) {
        int b = 0;
        int m = 0;
        int w = 0;
        int o = 0;
        int active = 0;
        for (Punishment p : punishments) {
            switch (p.type()) {
                case BAN -> b++;
                case MUTE -> m++;
                case WARN -> w++;
                default -> o++;
            }
            if (p.active(now)) {
                active++;
            }
        }
        return new Counts(b, m, w, o, sanctions.size(), active);
    }

    /** {@code punishments} filtered to the ones a rollback selects (the store pre-filters with SQL; this is the rule). */
    static List<Punishment> select(List<Punishment> punishments, UUID staff, long since, long until) {
        return punishments.stream().filter(p -> selects(p, staff, since, until)).toList();
    }

    static List<Sanction> selectSanctions(List<Sanction> sanctions, UUID staff, long since, long until, long now) {
        return sanctions.stream().filter(s -> selects(s, staff, since, until, now)).toList();
    }

    /**
     * True if a confirm matches the preview: same staff member and period, within the confirm window. A confirm without
     * a matching preview only shows the preview again.
     */
    static boolean confirms(UUID previewStaff, Duration previewPeriod, long previewAt, UUID staff, Duration period, long now,
                            Duration window) {
        return previewStaff != null && previewStaff.equals(staff) && previewPeriod != null && previewPeriod.equals(period)
                && now >= previewAt && now - previewAt <= window.toMillis();
    }
}
