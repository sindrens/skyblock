package net.skybornmc.skyblock.economy;

import net.skybornmc.skyblock.common.db.Database;
import net.skybornmc.skyblock.common.db.Sql;

import java.sql.SQLException;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Owner R3-39: staff play on a separate account, so {@code /eco} can't change the staff member's own account or an
 * account that shares an address with it. Uses the alt/IP history of the social module ({@code social_player_ips},
 * read only) plus the live addresses of online players.
 * <p>
 * Owner, 31 Oct 2026: admins and the owner may change their <b>own</b> account ({@code skyblock.economy.eco.self} and
 * {@code eco.allow-self}). That only lifts {@link Problem#SELF}; other accounts on the same address stay refused (alt
 * funnel), and every other /eco rule (bought credits, approve-above, flagging) still applies.
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

    /** True if a staff member may use /eco on their own account: the feature is on and they have the node. */
    static boolean selfAllowed(boolean featureOn, boolean hasNode) {
        return featureOn && hasNode;
    }

    private final Database db;

    EcoTargets(Database db) {
        this.db = db;
    }

    /** The quick check without the own-account exception (used where a self change is never fine). */
    static Problem quick(UUID staff, UUID target, String staffIp, String targetIp) {
        return quick(staff, target, staffIp, targetIp, false);
    }

    /**
     * The quick check, pure: the same account (unless {@code selfAllowed}), or the same live address (both online). An own
     * account always shares its own address, so a permitted self change is never a same-address problem.
     */
    static Problem quick(UUID staff, UUID target, String staffIp, String targetIp, boolean selfAllowed) {
        if (staff == null) {
            return null; // the console
        }
        if (staff.equals(target)) {
            return selfAllowed ? null : Problem.SELF;
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
