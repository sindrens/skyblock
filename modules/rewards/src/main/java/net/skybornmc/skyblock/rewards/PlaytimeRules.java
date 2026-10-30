package net.skybornmc.skyblock.rewards;

import java.util.List;
import java.util.NavigableMap;

/**
 * Playtime reward rules (PHASE-10 M10.6, owner R6, RX20), pure and unit tested. Only <b>active</b> time counts: the
 * player did something in the last few minutes, is not marked AFK and is not in the AFK pool. Every full hour pays the
 * hourly reward, every {@code keyEvery} paid hours a key, and titles at their hour thresholds.
 */
final class PlaytimeRules {

    static final long HOUR = 3_600;

    /** A profile's stored playtime and the milestones paid. */
    record Row(long activeSeconds, int hoursPaid, int tensPaid, int titleHours) {
        static final Row NONE = new Row(0, 0, 0, 0);

        long hours() {
            return activeSeconds / HOUR;
        }
    }

    /**
     * Milestones to pay now.
     *
     * @param hours  hourly rewards
     * @param keys   10-hour keys
     * @param titles title thresholds (hours) newly reached
     */
    record Due(int hours, int keys, List<Integer> titles, Row after) {
        boolean any() {
            return hours > 0 || keys > 0 || !titles.isEmpty();
        }
    }

    private PlaytimeRules() {
    }

    /** True when this moment counts as active play. */
    static boolean active(long lastAction, long now, long idleMillis, boolean markedAfk, boolean inPool, boolean blocked) {
        return !markedAfk && !inPool && !blocked && lastAction > 0 && now - lastAction < idleMillis;
    }

    /**
     * What to pay for a row: hours earned but not paid (at most {@code maxHours} at once; the rest comes with the next
     * payout), a key for every {@code keyEvery} paid hours, and every title threshold up to the paid hours.
     */
    static Due due(Row row, int keyEvery, NavigableMap<Integer, String> titles, int maxHours) {
        long earned = row.hours();
        int hours = (int) Math.max(0, Math.min(Math.max(0, maxHours), earned - row.hoursPaid()));
        int paid = row.hoursPaid() + hours;
        int keys = keyEvery > 0 ? Math.max(0, paid / keyEvery - row.tensPaid()) : 0;
        List<Integer> reached = RewardTables.crossed(titles, row.titleHours(), paid);
        int titleHours = reached.isEmpty() ? row.titleHours() : reached.getLast();
        return new Due(hours, keys, reached, new Row(row.activeSeconds(), paid, row.tensPaid() + keys, titleHours));
    }

    /** Seconds until the next full hour of active play (0..3600). */
    static long untilNextHour(long activeSeconds) {
        return HOUR - Math.floorMod(Math.max(0, activeSeconds), HOUR);
    }

    /** Hours until the next key (1..keyEvery; 0 when keys are off). */
    static long untilNextKey(long activeSeconds, int keyEvery) {
        if (keyEvery <= 0) {
            return 0;
        }
        long hours = Math.max(0, activeSeconds) / HOUR;
        return keyEvery - (hours % keyEvery);
    }
}
