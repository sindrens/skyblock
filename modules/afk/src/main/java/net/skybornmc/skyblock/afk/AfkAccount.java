package net.skybornmc.skyblock.afk;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * AFK pool state of one account (player UUID; the daily cap is per account, owner O10). Changed on the main thread
 * only; database writes are queued on {@link #chain} so they reach the database in the order they were made.
 *
 * <ul>
 *   <li>{@code day}/{@code creditedMillis}: time credited on that day (persisted per account per day).</li>
 *   <li>{@code progressMillis}: credited time since the last reward. It survives leaving the pool and midnight, so no
 *       credited minute is ever lost.</li>
 *   <li>{@code rewardSeq}: rewards given so far (all days); the next reward's number is the idempotency key, so a
 *       replay after a crash can never pay twice.</li>
 *   <li>{@code sessionMillis}: credited time since entering the pool; leaving resets it (reward steps).</li>
 * </ul>
 */
final class AfkAccount {

    final UUID player;
    int day;
    long creditedMillis;
    long progressMillis;
    long rewardSeq;
    long sessionMillis;

    /** Main-thread view: inside the pool right now, when they came in, the last time they were credited. */
    boolean inside;
    long enteredAt;
    long lastCheck;
    /** Last state shown, so chat notices (limit reached) go out once. */
    AfkState shown = AfkState.OUTSIDE;
    /** Unsaved credit since the last save. */
    boolean dirty;
    boolean loaded;
    /** Known undelivered grants (keys, fuel) of this account; delivery is tried when true. */
    boolean pendingGrants;
    /** Owner R3-35: when not-earning in the pool started (0 = earning or outside), see {@link IdleHome}. */
    long idleSince;
    /** True while being sent home for not earning, so leaving the pool doesn't send a second chat line. */
    boolean sentHome;

    CompletableFuture<?> chain = CompletableFuture.completedFuture(null);

    AfkAccount(UUID player) {
        this.player = player;
    }

    /** A copy of the persisted part (what a save writes). */
    Snapshot snapshot() {
        return new Snapshot(player, day, creditedMillis, progressMillis, rewardSeq);
    }

    void restore(Snapshot s) {
        day = s.day();
        creditedMillis = s.creditedMillis();
        progressMillis = s.progressMillis();
        rewardSeq = s.rewardSeq();
        loaded = true;
    }

    record Snapshot(UUID player, int day, long creditedMillis, long progressMillis, long rewardSeq) {
    }
}
