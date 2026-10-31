package net.skybornmc.skyblock.quests;

import java.util.UUID;

/**
 * One quest of a set (a row of {@code quest_progress}). Identity and rewards never change (a swap replaces the whole
 * object); progress, done and the in-flight flags are changed on the main thread only.
 *
 * @param uid one id per quest instance: the idempotency key of its payout, so a quest can never pay twice
 */
final class Quest {

    final UUID uid;
    final int slot;
    final QuestType type;
    /** Depths tier from which mine/chop/kill count (that tier or deeper); 0 for other types. */
    final int tierFrom;
    final long target;
    /** Money paid on completion (daily/weekly; 0 for island missions). */
    final long rewardMoney;
    /** Crystals paid on completion (island missions: per helper). */
    final long rewardCrystals;
    /** Battle pass XP on completion (island missions: per helper). */
    final int rewardXp;

    long progress;
    /** Epoch millis of the completion, 0 = open. */
    long doneAt;
    /** Progress the database has (only the difference is written). */
    long saved;
    /** A payout transaction is running. */
    boolean completing;

    Quest(UUID uid, int slot, QuestType type, int tierFrom, long target, long rewardMoney, long rewardCrystals, int rewardXp,
          long progress, long doneAt) {
        this.uid = uid;
        this.slot = slot;
        this.type = type;
        this.tierFrom = tierFrom;
        this.target = Math.max(1, target);
        this.rewardMoney = Math.max(0, rewardMoney);
        this.rewardCrystals = Math.max(0, rewardCrystals);
        this.rewardXp = Math.max(0, rewardXp);
        this.progress = Math.max(0, Math.min(this.target, progress));
        this.doneAt = doneAt;
        this.saved = this.progress;
    }

    boolean done() {
        return doneAt > 0;
    }

    boolean dirty() {
        return !done() && progress != saved;
    }

    /** True if an action of {@code type} in Depths tier {@code tier} (0 = not a Depths action) counts for this quest. */
    boolean counts(QuestType action, int tier) {
        if (done() || action != type) {
            return false;
        }
        return !type.depths() || tier >= tierFrom;
    }

    /** Adds progress (capped at the target); returns the progress before. */
    long add(long amount) {
        long before = progress;
        progress = Math.min(target, progress + Math.max(0, amount));
        return before;
    }

    boolean reached() {
        return progress >= target;
    }
}
