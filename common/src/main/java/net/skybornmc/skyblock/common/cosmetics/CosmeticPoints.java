package net.skybornmc.skyblock.common.cosmetics;

import net.skybornmc.skyblock.common.service.ServiceRegistry;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Cosmetic points (owner 7 Nov 2026, plans/STORE_FULL_PLAN.md 2 and 6): an account balance earned by playing (playtime,
 * votes, the monthly rank chest {@code /reclaim}) and spent to upgrade cosmetics. Never traded, never bought with money.
 * Published by the cosmetics module. Every change carries a unique {@code key}: the same key twice changes nothing
 * (a retry after a crash can't pay twice).
 */
public interface CosmeticPoints {

    /** The published service, if the cosmetics module runs. */
    static Optional<CosmeticPoints> of(ServiceRegistry services) {
        return services.optional(CosmeticPoints.class);
    }

    /** The cached balance of an online player (0 while not loaded). */
    long cached(UUID account);

    /** Adds points; completes true when added now or earlier with the same key. */
    CompletableFuture<Boolean> add(UUID account, long amount, String reason, String key);

    /** Spends points; completes false when the balance is too small (nothing changes). */
    CompletableFuture<Boolean> spend(UUID account, long amount, String reason, String key);
}
