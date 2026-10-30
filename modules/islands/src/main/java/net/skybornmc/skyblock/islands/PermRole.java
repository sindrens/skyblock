package net.skybornmc.skyblock.islands;

import net.skybornmc.skyblock.common.island.IslandService.Role;

import java.util.Locale;
import java.util.Optional;

/**
 * Who a permission set belongs to: the five island roles (owner decisions; Admin since ISL2) plus {@link #VISITOR} for
 * everyone else. The leader always has every permission; the others are configurable per island.
 */
public enum PermRole {
    LEADER, ADMIN, MOD, MEMBER, COOP, VISITOR;

    private static final PermRole[] VALUES = values();

    /** Stored key: {@code leader}, {@code admin}, {@code mod}, {@code member}, {@code coop}, {@code visitor}. */
    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** False for the leader (always everything); M2.3's matrix GUI shows the leader column locked. */
    public boolean editable() {
        return this != LEADER;
    }

    /** The permission role of an island role; {@code null} (not on the island) = visitor. */
    public static PermRole of(Role role) {
        if (role == null) {
            return VISITOR;
        }
        return switch (role) {
            case LEADER -> LEADER;
            case ADMIN -> ADMIN;
            case MOD -> MOD;
            case MEMBER -> MEMBER;
            case COOP -> COOP;
        };
    }

    public static Optional<PermRole> byKey(String key) {
        if (key == null) {
            return Optional.empty();
        }
        String k = key.trim().toLowerCase(Locale.ROOT);
        for (PermRole role : VALUES) {
            if (role.key().equals(k)) {
                return Optional.of(role);
            }
        }
        return Optional.empty();
    }

    /** Stored island members (leader, admin, mod, member): the only roles that can hold management permissions. */
    public boolean stored() {
        return this == LEADER || this == ADMIN || this == MOD || this == MEMBER;
    }

    static PermRole[] cached() {
        return VALUES;
    }
}
