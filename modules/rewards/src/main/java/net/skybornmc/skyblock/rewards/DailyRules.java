package net.skybornmc.skyblock.rewards;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;

/**
 * Daily calendar rules (PHASE-10 M10.6, owner 128, RX18, RX19), pure and unit tested.
 * <ul>
 *   <li>A day is a calendar date in the server zone (Europe/Oslo): the reset is at 00:00 there, also on DST days.</li>
 *   <li>One claim per profile per day. The calendar day moves forward <b>only on a claim</b>; after the last day it
 *       starts at day 1 again. Missing days never send you back.</li>
 *   <li>The streak counts days in a row with a claim. A missed day breaks it (the claim then starts a new streak of 1);
 *       the lost streak and the day it broke are kept for the streak save (R5, store later).</li>
 * </ul>
 */
final class DailyRules {

    /** "Never claimed" for {@link State#lastClaimDate}. */
    static final int NEVER = -1;

    /**
     * A profile's calendar.
     *
     * @param calendarDay    the last day claimed (0 = none yet)
     * @param lastClaimDate  epoch day of the last claim ({@link #NEVER} = none)
     * @param streak         days in a row up to the last claim
     * @param best           best streak ever
     * @param lostStreak     the streak the last break lost (0 = none)
     * @param brokenOn       epoch day the streak broke ({@link #NEVER} = never)
     * @param total          claims ever
     */
    record State(int calendarDay, int lastClaimDate, int streak, int best, int lostStreak, int brokenOn, int total) {
        static final State NONE = new State(0, NEVER, 0, 0, 0, NEVER, 0);
    }

    /** What today's claim does: the day it gives and the state after it. */
    record Claim(int day, int streakAfter, boolean streakBroken, State after) {
    }

    private DailyRules() {
    }

    /** The epoch day of {@code millis} in {@code zone}. */
    static int today(long millis, ZoneId zone) {
        return (int) LocalDate.ofInstant(Instant.ofEpochMilli(millis), zone).toEpochDay();
    }

    /** Time until the next 00:00 in {@code zone} (the next claim), never negative. */
    static Duration untilReset(long millis, ZoneId zone) {
        Instant now = Instant.ofEpochMilli(millis);
        LocalDate date = LocalDate.ofInstant(now, zone);
        ZonedDateTime next = date.plusDays(1).atStartOfDay(zone);
        Duration d = Duration.between(now, next.toInstant());
        return d.isNegative() ? Duration.ZERO : d;
    }

    static boolean claimedToday(State s, int today) {
        return s.lastClaimDate() >= today;
    }

    /**
     * Today's claim, or empty when already claimed today (also when the last claim lies in the future, e.g. after the
     * clock was turned back: never a second claim).
     */
    static Optional<Claim> claim(State s, int today, int length) {
        if (claimedToday(s, today) || length < 1) {
            return Optional.empty();
        }
        int day = s.calendarDay() < 1 || s.calendarDay() >= length ? 1 : s.calendarDay() + 1;
        boolean continued = s.streak() > 0 && s.lastClaimDate() == today - 1;
        boolean broken = !continued && s.streak() > 0;
        int streak = continued ? s.streak() + 1 : 1;
        State after = new State(day, today, streak, Math.max(s.best(), streak), broken ? s.streak() : s.lostStreak(),
                broken ? today : s.brokenOn(), s.total() + 1);
        return Optional.of(new Claim(day, streak, broken, after));
    }

    /** The calendar day the next claim gives (today's when not claimed yet, else tomorrow's). */
    static int nextDay(State s, int length) {
        return s.calendarDay() < 1 || s.calendarDay() >= length ? 1 : s.calendarDay() + 1;
    }

    /**
     * Days shown as claimed in the menu: up to the last claimed day; when the whole calendar was done and today is not
     * claimed yet, a new round starts and nothing is shown as claimed.
     */
    static int claimedUpTo(State s, int today, int length) {
        if (!claimedToday(s, today) && s.calendarDay() >= length) {
            return 0;
        }
        return Math.max(0, Math.min(length, s.calendarDay()));
    }

    /** The streak that still counts today: the stored one if the last claim was today or yesterday, else 0 (broken). */
    static int liveStreak(State s, int today) {
        return s.lastClaimDate() >= today - 1 ? s.streak() : 0;
    }

    /** Gems for reaching {@code streak}: {@code gems} every {@code every} days (§9.2: 10 every 7). */
    static long streakGems(int streak, int every, long gems) {
        return every > 0 && gems > 0 && streak > 0 && streak % every == 0 ? gems : 0;
    }

    /** Claims until the next streak gems, counted from the live streak (1..every; 0 when gems are off). */
    static int untilGems(int liveStreak, int every) {
        if (every <= 0) {
            return 0;
        }
        return every - (Math.max(0, liveStreak) % every);
    }
}
