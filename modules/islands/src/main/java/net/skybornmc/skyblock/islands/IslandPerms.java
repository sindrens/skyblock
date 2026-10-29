package net.skybornmc.skyblock.islands;

/**
 * Per-island permission overrides (main thread only): for each {@link PermRole} a bit set of explicitly allowed and
 * explicitly denied {@link IslandPerm}s. Anything not overridden follows the server defaults ({@link RolePermissions}),
 * so changing a default in islands.yml reaches every island that never touched that permission. Stored as rows in
 * {@code island_role_perms}; never allocates when read.
 */
public final class IslandPerms {

    private final long[] allow = new long[PermRole.cached().length];
    private final long[] deny = new long[PermRole.cached().length];

    /** The effective mask of {@code role} given its default mask. The leader always has everything. */
    public long effective(PermRole role, long defaultMask) {
        if (role == PermRole.LEADER) {
            return IslandPerm.all();
        }
        int i = role.ordinal();
        return (defaultMask | allow[i]) & ~deny[i];
    }

    /** The override of one permission: TRUE/FALSE, or null when it follows the default. */
    public Boolean override(PermRole role, IslandPerm perm) {
        int i = role.ordinal();
        if ((allow[i] & perm.bit()) != 0) {
            return Boolean.TRUE;
        }
        if ((deny[i] & perm.bit()) != 0) {
            return Boolean.FALSE;
        }
        return null;
    }

    /** Sets (TRUE/FALSE) or clears (null = use the default) one override. The leader is never overridden. */
    public void set(PermRole role, IslandPerm perm, Boolean allowed) {
        if (!role.editable()) {
            return;
        }
        int i = role.ordinal();
        allow[i] &= ~perm.bit();
        deny[i] &= ~perm.bit();
        if (Boolean.TRUE.equals(allowed)) {
            allow[i] |= perm.bit();
        } else if (Boolean.FALSE.equals(allowed)) {
            deny[i] |= perm.bit();
        }
    }

    /** True if nothing is overridden. */
    public boolean isEmpty() {
        for (int i = 0; i < allow.length; i++) {
            if (allow[i] != 0 || deny[i] != 0) {
                return false;
            }
        }
        return true;
    }
}
