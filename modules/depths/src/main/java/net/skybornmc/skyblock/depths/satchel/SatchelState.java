package net.skybornmc.skyblock.depths.satchel;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * The satchel of one profile in memory (M4.4). Main thread only.
 *
 * <ul>
 *   <li>{@code amounts}: what the profile holds right now (authoritative while loaded, like an inventory).</li>
 *   <li>{@code pending}: changes not written yet (drops add here, the Forge removes here); written in batches, or inside
 *       the transaction of the next take-out/sale.</li>
 *   <li>Take-outs and sales take items out of {@code amounts} first (escrow) and put them back if the database refused.</li>
 * </ul>
 * One instance per profile for as long as anything refers to it (players, queued database work, unsaved changes), so a
 * result always lands in the same instance.
 */
final class SatchelState {

    final UUID profile;
    private final Map<String, Long> amounts = new HashMap<>();
    private final Map<String, Long> pending = new HashMap<>();
    private final Map<String, Long> gained = new LinkedHashMap<>();
    /** R3-22: drops that went past a full satchel (inventory, then /box), for the same batched audit row. */
    private final Map<String, Long> overflow = new LinkedHashMap<>();
    private long gainedCrystals;
    private long used;
    private boolean loaded;
    private boolean loading;
    private int upgradeLevel;
    /** Database work for this profile runs one after another on this chain (main thread appends). */
    CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
    /** Queued database operations not finished yet. */
    int inFlight;
    /** One take-out/sale/upgrade at a time. */
    boolean busy;
    /** Last "satchel full" warning (ms). */
    long lastFullWarning;
    /** Last "/box full, mining stopped" warning (ms). */
    long lastBoxWarning;
    /** Player name for audit rows. */
    String ownerName = "?";
    UUID owner;

    SatchelState(UUID profile) {
        this.profile = profile;
    }

    boolean loaded() {
        return loaded;
    }

    boolean loading() {
        return loading;
    }

    void loading(boolean value) {
        loading = value;
    }

    long used() {
        return used;
    }

    int upgradeLevel() {
        return upgradeLevel;
    }

    void upgradeLevel(int level) {
        upgradeLevel = Math.max(0, level);
    }

    long amount(String item) {
        return amounts.getOrDefault(item, 0L);
    }

    Map<String, Long> amounts() {
        return Collections.unmodifiableMap(amounts);
    }

    /** New drops: held right away, written with the next batch, counted for the audit row. */
    void add(String item, long n) {
        if (n <= 0) {
            return;
        }
        amounts.merge(item, n, Long::sum);
        used += n;
        pending.merge(item, n, Long::sum);
        gained.merge(item, n, Long::sum);
    }

    /** Items coming back (unsold stacks, a take-out that no longer fits): held again and written, not counted as gains. */
    void credit(String item, long n) {
        if (n <= 0) {
            return;
        }
        amounts.merge(item, n, Long::sum);
        used += n;
        pending.merge(item, n, Long::sum);
        pending.values().removeIf(v -> v == 0);
    }

    /** R3-22: drops that did not go into the (full) satchel but to the inventory or /box; audit only. */
    void addOverflow(String item, long n) {
        if (n > 0) {
            overflow.merge(item, n, Long::sum);
        }
    }

    /** Overflow drops since the last audit row, cleared here. */
    Map<String, Long> takeOverflow() {
        Map<String, Long> out = new LinkedHashMap<>(overflow);
        overflow.clear();
        return out;
    }

    void addCrystals(long n) {
        if (n > 0) {
            gainedCrystals += n;
        }
    }

    /** True if every amount is held. */
    boolean has(Map<String, Long> wanted) {
        for (Map.Entry<String, Long> e : wanted.entrySet()) {
            if (e.getValue() < 0 || amount(e.getKey()) < e.getValue()) {
                return false;
            }
        }
        return true;
    }

    /** Escrow for a take-out or sale: takes the items out of memory (all or nothing). The database is not touched. */
    boolean escrow(Map<String, Long> wanted) {
        if (!loaded || !has(wanted)) {
            return false;
        }
        wanted.forEach(this::subtract);
        return true;
    }

    /** The database refused a take-out/sale: the escrowed items are held again. */
    void release(Map<String, Long> escrowed) {
        escrowed.forEach((item, n) -> {
            if (n > 0) {
                amounts.merge(item, n, Long::sum);
                used += n;
            }
        });
    }

    /** Items used up (the Forge): out of memory now, a negative change for the next write. All or nothing. */
    boolean consume(Map<String, Long> wanted) {
        if (!escrow(wanted)) {
            return false;
        }
        wanted.forEach((item, n) -> {
            if (n > 0) {
                pending.merge(item, -n, Long::sum);
            }
        });
        pending.values().removeIf(v -> v == 0);
        return true;
    }

    private void subtract(String item, long n) {
        if (n <= 0) {
            return;
        }
        long left = amount(item) - n;
        if (left > 0) {
            amounts.put(item, left);
        } else {
            amounts.remove(item);
        }
        used -= n;
    }

    boolean hasPending() {
        return !pending.isEmpty();
    }

    /** Unwritten changes, cleared here; hand them back with {@link #returnPending} if the write fails. */
    Map<String, Long> takePending() {
        if (pending.isEmpty()) {
            return Map.of();
        }
        Map<String, Long> out = new HashMap<>(pending);
        pending.clear();
        return out;
    }

    /** A write failed: its changes are unwritten again (memory already holds them). */
    void returnPending(Map<String, Long> deltas) {
        deltas.forEach((item, n) -> pending.merge(item, n, Long::sum));
        pending.values().removeIf(v -> v == 0);
    }

    /** The database rows arrived: memory = rows + changes not written yet. */
    void loaded(Map<String, Long> rows, int upgrades) {
        amounts.clear();
        used = 0;
        rows.forEach((item, n) -> {
            if (n > 0) {
                amounts.put(item, n);
                used += n;
            }
        });
        pending.forEach((item, n) -> {
            long now = amounts.getOrDefault(item, 0L) + n;
            used += n;
            if (now > 0) {
                amounts.put(item, now);
            } else {
                amounts.remove(item);
            }
        });
        if (used < 0) {
            used = 0;
        }
        upgradeLevel = Math.max(0, upgrades);
        loaded = true;
        loading = false;
    }

    /** True if the audit accumulator holds something. */
    boolean hasGains() {
        return !gained.isEmpty() || !overflow.isEmpty() || gainedCrystals > 0;
    }

    /** Items and crystals gained since the last audit row, cleared here. */
    Map<String, Long> takeGains() {
        Map<String, Long> out = new LinkedHashMap<>(gained);
        gained.clear();
        return out;
    }

    long takeGainedCrystals() {
        long out = gainedCrystals;
        gainedCrystals = 0;
        return out;
    }

    /** Nothing refers to this state any more: no queued work, nothing unwritten, nothing to audit. */
    boolean idle() {
        return inFlight == 0 && pending.isEmpty() && !hasGains() && !busy;
    }
}
