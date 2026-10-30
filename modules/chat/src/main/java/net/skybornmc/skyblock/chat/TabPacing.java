package net.skybornmc.skyblock.chat;

/**
 * How many players the tab list checks per tick so every online player is checked once per {@code tab.update-ticks}
 * (PERF4). The old slice {@code ceil(online / update-ticks)} was at least 1, so with fewer players than update-ticks
 * every player was re-checked (LuckPerms lookups, placeholders, header/footer) on every tick: 20 times a second
 * instead of once per 2 s. A fractional carry spreads the checks evenly: 1 player and 40 ticks = one check every 40
 * ticks, 300 players = 7 or 8 per tick.
 */
final class TabPacing {

    private long carry;

    /** Players to check this tick (never more than {@code online}). */
    int next(int online, int updateTicks) {
        if (online <= 0) {
            carry = 0;
            return 0;
        }
        int ticks = Math.max(1, updateTicks);
        carry += online;
        long due = carry / ticks;
        carry %= ticks;
        return (int) Math.min(due, online);
    }
}
