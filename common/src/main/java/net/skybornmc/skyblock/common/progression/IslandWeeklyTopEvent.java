package net.skybornmc.skyblock.common.progression;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.UUID;

/**
 * A week of the island boards ended (PHASE-5 M5.7), main thread, after its final places were stored
 * ({@code leaderboard_weekly}). This is the hook for the weekly top rewards (owner V1: keys): nothing pays them yet
 * (PG27 is waiting for the owner), so a listener that pays must mark the places rewarded itself and check the members'
 * 3-day / 1-hour rule. {@link #solo()} and {@link #team()} are the final weekly island gain boards (earned points gained),
 * best first, as many places as {@code progression.yml → top.week-places}.
 */
public final class IslandWeeklyTopEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final int weekStart;
    private final List<IslandTop.Entry> solo;
    private final List<IslandTop.Entry> team;

    /** @param weekStart the week that ended: epoch day of its Monday (Europe/Oslo) */
    public IslandWeeklyTopEvent(int weekStart, List<IslandTop.Entry> solo, List<IslandTop.Entry> team) {
        this.weekStart = weekStart;
        this.solo = List.copyOf(solo);
        this.team = List.copyOf(team);
    }

    public int weekStart() {
        return weekStart;
    }

    public List<IslandTop.Entry> solo() {
        return solo;
    }

    public List<IslandTop.Entry> team() {
        return team;
    }

    /** True if the island holds a place on either final board. */
    public boolean placed(UUID islandId) {
        return solo.stream().anyMatch(e -> e.islandId().equals(islandId)) || team.stream().anyMatch(e -> e.islandId().equals(islandId));
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
