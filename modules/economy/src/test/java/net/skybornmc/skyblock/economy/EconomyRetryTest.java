package net.skybornmc.skyblock.economy;

import org.junit.jupiter.api.Test;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Which database errors a balance change retries (every try is a full rollback). */
class EconomyRetryTest {

    @Test
    void conflictsAreRetriedOtherErrorsAreNot() {
        assertTrue(EconomyStore.retryable(new SQLException("deadlock", "40001", 1213)));
        assertTrue(EconomyStore.retryable(new SQLException("lock wait", "HY000", 1205)));
        assertTrue(EconomyStore.retryable(new SQLException("Record has changed since last read in table 'eco_accounts'", "HY000", 1020)),
                "MariaDB 11.8 snapshot isolation (seen live 7 Nov 2026)");
        assertFalse(EconomyStore.retryable(new SQLException("duplicate", "23000", 1062)));
    }
}
