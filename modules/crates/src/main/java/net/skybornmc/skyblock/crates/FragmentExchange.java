package net.skybornmc.skyblock.crates;

import net.skybornmc.skyblock.common.player.PlayerSaves;

import java.util.concurrent.CompletableFuture;

/**
 * Key fragments to Phoenix keys (REVIEW3 R3-18): {@code perKey} fragments (3) from the inventory become one key. The
 * order follows SECFIX1 ({@link PlayerSaves}) so a crash loses at worst and never duplicates:
 * <ol>
 *   <li>take exactly {@code keys × perKey} fragments out of the inventory (all or nothing),</li>
 *   <li>save the player file, and only then</li>
 *   <li>add the keys in one database transaction.</li>
 * </ol>
 * A failed save or transaction gives every taken fragment back. Pure: the Bukkit and database steps are passed in, so
 * every path is unit-tested.
 */
final class FragmentExchange {

    /** The steps of one exchange (main thread except the future). */
    interface Steps {
        /** Removes up to {@code fragments} from the inventory; returns how many were really removed. */
        int take(int fragments);

        /** Writes the player file (true = written). */
        boolean save();

        /** Adds {@code keys} keys in one transaction; completes with true when stored. */
        CompletableFuture<Boolean> grant(int keys);

        /** Gives {@code fragments} back (inventory, then /box). */
        void refund(int fragments);
    }

    /** What happened. */
    enum Status {
        DONE, NOT_ENOUGH, FAILED
    }

    /** @param keys keys added (0 unless DONE) @param fragments fragments used (0 unless DONE) */
    record Result(Status status, int keys, int fragments) {
        static final Result NOT_ENOUGH = new Result(Status.NOT_ENOUGH, 0, 0);
        static final Result FAILED = new Result(Status.FAILED, 0, 0);
    }

    private FragmentExchange() {
    }

    /** Keys that {@code have} fragments make, at most {@code maxKeys} (0 when perKey or maxKeys is below 1). */
    static int keysFor(int have, int perKey, int maxKeys) {
        if (perKey < 1 || maxKeys < 1 || have < perKey) {
            return 0;
        }
        return Math.min(maxKeys, have / perKey);
    }

    /**
     * Runs one exchange of up to {@code wantKeys} keys from {@code have} fragments.
     */
    static CompletableFuture<Result> run(int have, int perKey, int wantKeys, Steps steps) {
        int keys = keysFor(have, perKey, wantKeys);
        if (keys < 1) {
            return CompletableFuture.completedFuture(Result.NOT_ENOUGH);
        }
        int need = Math.multiplyExact(keys, perKey);
        int taken;
        try {
            taken = steps.take(need);
        } catch (RuntimeException e) {
            return CompletableFuture.completedFuture(Result.FAILED);
        }
        if (taken != need) {
            if (taken > 0) {
                steps.refund(taken);
            }
            return CompletableFuture.completedFuture(taken < need ? Result.NOT_ENOUGH : Result.FAILED);
        }
        return PlayerSaves.commitAfterSave(steps::save, "key fragment exchange", () -> steps.grant(keys))
                .handle((ok, error) -> {
                    if (error == null && Boolean.TRUE.equals(ok)) {
                        return new Result(Status.DONE, keys, need);
                    }
                    steps.refund(need);
                    return Result.FAILED;
                });
    }
}
