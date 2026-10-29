package net.skybornmc.skyblock.islands;

import java.util.ArrayList;
import java.util.List;

/**
 * The server-wide default permission set of every role (pure, no Bukkit), parsed from {@code islands.yml →
 * permissions.defaults.<role>} lists of {@link IslandPerm} keys ({@code "*"} = everything). The leader always has
 * everything; visitors default to nothing (walk and look only). Resolution is one array read and two bit operations.
 */
public final class RolePermissions {

    /** Parse result: the defaults and human-readable problems (unknown keys) for the console. */
    public record Result(RolePermissions permissions, List<String> problems) {
    }

    private final long[] defaults = new long[PermRole.cached().length];

    private RolePermissions() {
        defaults[PermRole.LEADER.ordinal()] = IslandPerm.all();
    }

    /** Nothing allowed except for the leader (the safest fallback). */
    public static RolePermissions none() {
        return new RolePermissions();
    }

    /**
     * Builds the defaults from one key list per editable role ({@code mod}, {@code member}, {@code coop},
     * {@code visitor}). Unknown keys are skipped and reported.
     */
    public static Result parse(List<String> mod, List<String> member, List<String> coop, List<String> visitor) {
        RolePermissions perms = new RolePermissions();
        List<String> problems = new ArrayList<>();
        perms.defaults[PermRole.MOD.ordinal()] = mask(PermRole.MOD, mod, problems);
        perms.defaults[PermRole.MEMBER.ordinal()] = mask(PermRole.MEMBER, member, problems);
        perms.defaults[PermRole.COOP.ordinal()] = mask(PermRole.COOP, coop, problems);
        perms.defaults[PermRole.VISITOR.ordinal()] = mask(PermRole.VISITOR, visitor, problems);
        return new Result(perms, List.copyOf(problems));
    }

    static long mask(PermRole role, List<String> keys, List<String> problems) {
        long mask = 0;
        if (keys == null) {
            return 0;
        }
        for (String key : keys) {
            if (key == null || key.isBlank()) {
                continue;
            }
            if (key.trim().equals("*")) {
                mask |= IslandPerm.all();
                continue;
            }
            var perm = IslandPerm.byKey(key);
            if (perm.isPresent()) {
                mask |= perm.get().bit();
            } else {
                problems.add("permissions.defaults." + role.key() + ": unknown permission '" + key + "' (skipped)");
            }
        }
        return mask;
    }

    /** Default mask of a role. */
    public long defaultMask(PermRole role) {
        return defaults[role.ordinal()];
    }

    public boolean defaultAllows(PermRole role, IslandPerm perm) {
        return (defaults[role.ordinal()] & perm.bit()) != 0;
    }

    /** The decision: {@code role} may do {@code perm} on an island with these overrides ({@code null} = none). */
    public boolean allows(IslandPerms overrides, PermRole role, IslandPerm perm) {
        long mask = overrides == null
                ? (role == PermRole.LEADER ? IslandPerm.all() : defaults[role.ordinal()])
                : overrides.effective(role, defaults[role.ordinal()]);
        return (mask & perm.bit()) != 0;
    }
}
