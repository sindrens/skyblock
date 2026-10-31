package net.skybornmc.skyblock.progression;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * PERF11: the stored island levels, block worth and bank samples, turned into the in-memory objects <b>off the main
 * thread</b> (on the database thread that read them). The main thread only hands them over ({@link #adoptStates},
 * {@link #adoptBanks}, {@link BlockWorthLedger#adopt}), which gives exactly the same result as putting the rows back one
 * by one: an island that already has an object (created by an event before the load finished) keeps it and gets the
 * stored values written into it; every other island gets the prepared object as it is. Pure, unit-tested.
 */
final class LevelLoad {

    private LevelLoad() {
    }

    /** Everything the load read, ready to be handed over. {@code levels}: number of stored level rows (for the log line). */
    record Prepared(Map<UUID, LevelManager.State> states, BlockWorthLedger.Restored worth, Map<UUID, BankSamples> banks,
                    int levels) {
    }

    /** Any thread: builds the objects of the stored rows (later rows of the same island win, as before). */
    static Prepared prepare(List<LevelStore.LevelRow> levels, List<LevelStore.WorthRow> worth, List<LevelStore.BankRow> bank) {
        Map<UUID, LevelManager.State> states = new HashMap<>(Math.max(16, levels.size() * 2));
        for (LevelStore.LevelRow row : levels) {
            stored(states.computeIfAbsent(row.island(), k -> new LevelManager.State()), row);
        }
        Map<UUID, BankSamples> banks = new HashMap<>();
        for (LevelStore.BankRow row : bank) {
            banks.computeIfAbsent(row.island(), k -> new BankSamples()).restore(row.at(), row.balance());
        }
        return new Prepared(states, BlockWorthLedger.prepare(worth), banks, levels.size());
    }

    /** The stored values of one row written into a state (the fields the load always set). */
    static void stored(LevelManager.State s, LevelStore.LevelRow row) {
        s.points = new EnumMap<>(row.points());
        s.total = row.total();
        s.earned = row.earned();
        s.level = row.level();
        s.bestLevel = Math.max(row.bestLevel(), row.level());
        s.reachedAt = row.reachedAt();
        s.updatedAt = row.updatedAt();
        s.scannedAt = row.scannedAt();
        s.bankLow = row.bankLow();
        s.blockWorth = row.blockWorth();
        s.calculated = true;
    }

    /** Main thread: hands the loaded states over. An existing state keeps its object and gets the stored values. */
    static void adoptStates(Map<UUID, LevelManager.State> target, Map<UUID, LevelManager.State> loaded) {
        for (Map.Entry<UUID, LevelManager.State> e : loaded.entrySet()) {
            LevelManager.State existing = target.putIfAbsent(e.getKey(), e.getValue());
            if (existing != null) {
                copyStored(e.getValue(), existing);
            }
        }
    }

    /** The stored fields of {@code from} (set by {@link #stored}) copied into {@code to}; every other field of {@code to} stays. */
    static void copyStored(LevelManager.State from, LevelManager.State to) {
        to.points = from.points;
        to.total = from.total;
        to.earned = from.earned;
        to.level = from.level;
        to.bestLevel = from.bestLevel;
        to.reachedAt = from.reachedAt;
        to.updatedAt = from.updatedAt;
        to.scannedAt = from.scannedAt;
        to.bankLow = from.bankLow;
        to.blockWorth = from.blockWorth;
        to.calculated = true;
    }

    /** Main thread: hands the loaded bank samples over. An island with samples already gets the stored ones added. */
    static void adoptBanks(Map<UUID, BankSamples> target, Map<UUID, BankSamples> loaded) {
        for (Map.Entry<UUID, BankSamples> e : loaded.entrySet()) {
            BankSamples existing = target.putIfAbsent(e.getKey(), e.getValue());
            if (existing != null) {
                existing.restoreAll(e.getValue());
            }
        }
    }
}
