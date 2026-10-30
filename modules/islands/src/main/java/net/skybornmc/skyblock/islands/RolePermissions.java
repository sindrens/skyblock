package net.skybornmc.skyblock.islands;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The server-wide default permission set of every role (pure, no Bukkit), parsed from {@code islands.yml →
 * permissions.defaults.<role>} lists of {@link IslandPerm} keys ({@code "*"} = every permission without own defaults)
 * plus, for the permissions added later ({@link IslandPerm#ownDefaults()}: hurt-monsters, settings), one list of roles
 * per permission ({@code permissions.role-defaults.<perm>}). The leader always has everything; management rights
 * ({@link IslandPerm#management()}) never reach coop players or visitors. Resolution is one array read and two bit
 * operations.
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
     * Builds the defaults from one key list per editable role ({@code admin}, {@code mod}, {@code member}, {@code coop},
     * {@code visitor}) and one role list per permission with own defaults. Unknown keys are skipped and reported.
     */
    public static Result parse(Map<PermRole, List<String>> perRole, Map<IslandPerm, List<String>> perPermission) {
        RolePermissions perms = new RolePermissions();
        List<String> problems = new ArrayList<>();
        for (PermRole role : PermRole.cached()) {
            if (role.editable()) {
                perms.defaults[role.ordinal()] = mask(role, perRole == null ? null : perRole.get(role), problems);
            }
        }
        for (IslandPerm perm : IslandPerm.cached()) {
            if (!perm.ownDefaults() || perPermission == null) {
                continue;
            }
            List<String> roles = perPermission.get(perm);
            if (roles == null) {
                continue;
            }
            for (String key : roles) {
                if (key == null || key.isBlank()) {
                    continue;
                }
                var role = PermRole.byKey(key);
                if (role.isEmpty()) {
                    problems.add("permissions.role-defaults." + perm.key() + ": unknown role '" + key + "' (skipped)");
                } else if (!role.get().editable()) {
                    continue; // the leader always has everything
                } else if (!perm.appliesTo(role.get())) {
                    problems.add("permissions.role-defaults." + perm.key() + ": " + role.get().key() + " can't have this permission (skipped)");
                } else {
                    perms.defaults[role.get().ordinal()] |= perm.bit();
                }
            }
        }
        return new Result(perms, List.copyOf(problems));
    }

    /** {@link #parse(Map, Map)} with the five role lists in order (admin, mod, member, coop, visitor). */
    public static Result parse(List<String> admin, List<String> mod, List<String> member, List<String> coop, List<String> visitor,
                               Map<IslandPerm, List<String>> perPermission) {
        Map<PermRole, List<String>> lists = new EnumMap<>(PermRole.class);
        lists.put(PermRole.ADMIN, admin);
        lists.put(PermRole.MOD, mod);
        lists.put(PermRole.MEMBER, member);
        lists.put(PermRole.COOP, coop);
        lists.put(PermRole.VISITOR, visitor);
        return parse(lists, perPermission);
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
                mask |= IslandPerm.wildcard();
                continue;
            }
            var perm = IslandPerm.byKey(key);
            if (perm.isEmpty()) {
                problems.add("permissions.defaults." + role.key() + ": unknown permission '" + key + "' (skipped)");
            } else if (!perm.get().appliesTo(role)) {
                problems.add("permissions.defaults." + role.key() + ": " + role.key() + " can't have '" + key + "' (skipped)");
            } else {
                mask |= perm.get().bit();
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
