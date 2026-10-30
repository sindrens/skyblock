package net.skybornmc.skyblock.common.economy;

import java.util.List;

/**
 * The outcome of {@link EconomyService#executeBatch}: every transaction of the batch committed together, or none did.
 *
 * @param status      {@link TxResult.Status#SUCCESS} when all were done, otherwise the status of the first one that failed
 *                    (nothing changed then)
 * @param failedIndex index of the transaction that failed, -1 on success or when the batch failed as a whole (guard, error)
 * @param results     one result per transaction in batch order (only on success; empty otherwise)
 */
public record BatchResult(TxResult.Status status, int failedIndex, List<TxResult> results) {

    public BatchResult {
        results = results == null ? List.of() : List.copyOf(results);
    }

    public boolean ok() {
        return status == TxResult.Status.SUCCESS;
    }

    public static BatchResult success(List<TxResult> results) {
        return new BatchResult(TxResult.Status.SUCCESS, -1, results);
    }

    public static BatchResult failed(TxResult.Status status, int failedIndex) {
        return new BatchResult(status, failedIndex, List.of());
    }
}
