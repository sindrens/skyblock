package net.skybornmc.skyblock.economy;

import net.skybornmc.skyblock.common.db.Database;
import net.skybornmc.skyblock.common.db.Sql;
import net.skybornmc.skyblock.common.economy.AccountType;
import net.skybornmc.skyblock.common.economy.Currency;
import net.skybornmc.skyblock.common.economy.SendGate;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiPredicate;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * Owner F1: a profile can send money away only after it earned {@code send-gate.required} itself; sending to a member of
 * its own island is always allowed. Earned = the sum of the profile's ledger deposits (purse and personal bank) with an
 * income reason ({@code send-gate.earned-reasons}): money from other players, staff, notes and refunds never counts.
 * Once a profile reached the requirement it is remembered ({@code eco_send_unlocked} + memory), so the sum runs at most
 * until then.
 */
final class SendGateImpl implements SendGate {

    private final Database db;
    private final Supplier<EconomyConfig> config;
    /** (from profile, to profile) → both are stored members of the same island. Main thread. */
    private final BiPredicate<UUID, UUID> sameIsland;
    private final LongSupplier clock;
    private final Set<UUID> unlocked = ConcurrentHashMap.newKeySet();

    SendGateImpl(Database db, Supplier<EconomyConfig> config, BiPredicate<UUID, UUID> sameIsland, LongSupplier clock) {
        this.db = db;
        this.config = config;
        this.sameIsland = sameIsland;
        this.clock = clock;
    }

    /**
     * The decision, pure: open when the rule is off, the currency is not covered, the receiver is on the sender's island
     * or the profile already earned enough.
     */
    static Check decide(long required, boolean gated, boolean sameIsland, boolean unlocked, long earned) {
        if (required <= 0 || !gated || sameIsland || unlocked) {
            return Check.OPEN;
        }
        return new Check(earned >= required, earned, required);
    }

    @Override
    public CompletableFuture<Check> check(UUID fromProfile, UUID toProfile, Currency currency) {
        EconomyConfig cfg = config.get();
        long required = cfg.sendGateRequired;
        boolean island = toProfile != null && fromProfile != null && sameIsland.test(fromProfile, toProfile);
        Check quick = decide(required, cfg.sendGated(currency), island, fromProfile != null && unlocked.contains(fromProfile), 0);
        if (quick == Check.OPEN || fromProfile == null) {
            return CompletableFuture.completedFuture(quick == Check.OPEN ? quick : new Check(false, 0, required));
        }
        List<String> reasons = List.copyOf(cfg.sendGateEarnedReasons);
        return db.async(c -> {
            if (isUnlocked(c, fromProfile)) {
                return Check.OPEN;
            }
            long earned = earned(c, fromProfile, reasons);
            Check result = decide(required, true, false, false, earned);
            if (result.allowed()) {
                remember(c, fromProfile, earned);
            }
            return result;
        }).thenApply(result -> {
            if (result.allowed()) {
                unlocked.add(fromProfile);
            }
            return result;
        });
    }

    private static boolean isUnlocked(Connection c, UUID profile) throws SQLException {
        return Sql.queryOne(c, "SELECT 1 FROM eco_send_unlocked WHERE profile_id = ?", rs -> 1, profile).isPresent();
    }

    /** Income of a profile in money: ledger deposits to its purse or personal bank with one of {@code reasons}. */
    static long earned(Connection c, UUID profile, List<String> reasons) throws SQLException {
        if (reasons.isEmpty()) {
            return 0;
        }
        List<Object> params = new ArrayList<>();
        params.add(profile);
        params.add(AccountType.PLAYER.key());
        params.add(AccountType.BANK.key());
        params.add(Currency.MONEY.key());
        params.addAll(reasons);
        String in = "?,".repeat(reasons.size() - 1) + "?";
        return Sql.queryOne(c, "SELECT COALESCE(SUM(amount), 0) FROM eco_ledger WHERE to_id = ? AND to_type IN (?, ?) "
                + "AND currency = ? AND from_id IS NULL AND reason IN (" + in + ")", rs -> rs.getLong(1), params.toArray()).orElse(0L);
    }

    private void remember(Connection c, UUID profile, long earned) {
        try {
            if (!isUnlocked(c, profile)) {
                Sql.update(c, "INSERT INTO eco_send_unlocked (profile_id, earned, unlocked_at) VALUES (?, ?, ?)", profile, earned,
                        clock.getAsLong());
            }
        } catch (SQLException e) {
            // a parallel check inserted it first: same result
        }
    }

    /** Forgets the memory copy (profile deleted). The row stays harmless: the profile id is never reused. */
    void forget(UUID profile) {
        unlocked.remove(profile);
    }
}
