package net.skybornmc.skyblock.common.economy;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Extra SQL that must commit or roll back together with an economy {@link Transaction}
 * ({@link EconomyService#executeGuarded}). Runs inside the same database transaction, before the account rows are
 * locked, and may run more than once (deadlock retries), so it must only use the connection it gets.
 *
 * <p>Example (enchants): lock the book row, check it is still active and mark it consumed, store the tool's new perks.
 * If the player has too few crystals afterwards, all of it is rolled back.</p>
 */
@FunctionalInterface
public interface TxGuard {

    /**
     * @return false to abort: everything (this work and the balance change) is rolled back and the result is
     *         {@link TxResult.Status#DUPLICATE}. Throwing an {@link SQLException} rolls back too ({@link TxResult.Status#ERROR}).
     */
    boolean holds(Connection connection) throws SQLException;
}
