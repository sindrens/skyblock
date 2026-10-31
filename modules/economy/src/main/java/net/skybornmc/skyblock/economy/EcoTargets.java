package net.skybornmc.skyblock.economy;

import net.skybornmc.skyblock.common.db.Database;
import net.skybornmc.skyblock.common.db.Sql;

import java.sql.SQLException;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Owner R3-39: staff play on a separate account, so {@code /eco} can never change the staff member's own account or an
 * account that shares an address with it. Uses the alt/IP history of the social module ({@code social_player_ips},
 * read only) plus the live addresses of online players.
 */
final class EcoTargets {

    /** Why /eco refuses a target; message key {@code eco.target.<key>}. */
    enum Problem {
        SELF("self"),
        SAME_IP("same-ip");

        final String key;

        Problem(String key) {
            this.key = key;
        }
    }

    private final Database db;

    EcoTargets(Database db) {
        this.db = db;
    }

    /** The quick check, pure: the same account, or the same live address (both online). */
    static Problem quick(UUID staff, UUID target, String staffIp, String targetIp) {
        if (staff == null) {
            return null; // the console
        }
        if (staff.equals(target)) {
            return Problem.SELF;
        }
        if (staffIp != null && !staffIp.isBlank() && Objects.equals(staffIp, targetIp)) {
            return Problem.SAME_IP;
        }
        return null;
    }

    /** True if the two accounts ever joined from the same address (no social module = false). Off the main thread. */
    CompletableFuture<Boolean> sharedAddress(UUID staff, UUID target) {
        return db.async(c -> {
            try {
                return Sql.queryOne(c, "SELECT 1 FROM social_player_ips a JOIN social_player_ips b ON a.ip = b.ip "
                        + "WHERE a.uuid = ? AND b.uuid = ? LIMIT 1", rs -> 1, staff, target).isPresent();
            } catch (SQLException e) {
                return false; // social module not installed: only the live address check applies
            }
        });
    }
}
