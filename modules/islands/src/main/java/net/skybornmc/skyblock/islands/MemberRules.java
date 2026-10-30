package net.skybornmc.skyblock.islands;

import net.skybornmc.skyblock.common.island.IslandService.Role;

/**
 * Who may do what with island members (M2.3, ISL2; pure, no Bukkit). Ranks: Leader &gt; Admin &gt; Mod &gt; Member &gt; Coop.
 * Fixed rules (the build/use actions are the configurable part, see {@link RolePermissions}):
 * <ul>
 *   <li>Leader: everything; the only one who transfers and disbands; promotes Member → Mod → Admin.</li>
 *   <li>Admin (ISL2, owner #23): every Mod right, plus edits the Mod row of the permission matrix and promotes/demotes
 *       Member ↔ Mod; kicks mods and members. The old leader becomes Admin after a transfer.</li>
 *   <li>Mod: invites, kicks members (not mods), coops/uncoops, sets the home, edits the Member/Coop/Visitor permissions.</li>
 *   <li>Member: can see the member list and the permission matrix, can leave.</li>
 *   <li>Coop: temporary trust, never a stored member; nothing to manage.</li>
 * </ul>
 * A {@code null} role means "not on the island" and is never allowed anything.
 */
public final class MemberRules {

    /** The old leader's role after {@code /is transfer} (owner #23). */
    public static final Role AFTER_TRANSFER = Role.ADMIN;

    private MemberRules() {
    }

    /** Higher = more power (leader 4, admin 3, mod 2, member 1, coop 0, none -1). */
    static int rank(Role role) {
        if (role == null) {
            return -1;
        }
        return switch (role) {
            case LEADER -> 4;
            case ADMIN -> 3;
            case MOD -> 2;
            case MEMBER -> 1;
            case COOP -> 0;
        };
    }

    /** Leader, admins and mods manage the island's people (invite, kick, coop) and its home. */
    public static boolean isManager(Role role) {
        return role == Role.LEADER || role == Role.ADMIN || role == Role.MOD;
    }

    public static boolean canInvite(Role actor) {
        return isManager(actor);
    }

    /** Only a higher rank kicks: leader kicks admins/mods/members, admin kicks mods/members, mod kicks members. */
    public static boolean canKick(Role actor, Role target) {
        return isManager(actor) && target != null && target != Role.LEADER && target != Role.COOP && rank(actor) > rank(target);
    }

    /** Leader: Member → Mod and Mod → Admin. Admin: Member → Mod. */
    public static boolean canPromote(Role actor, Role target) {
        if (actor == Role.LEADER) {
            return target == Role.MEMBER || target == Role.MOD;
        }
        return actor == Role.ADMIN && target == Role.MEMBER;
    }

    /** Leader: Admin → Mod and Mod → Member. Admin: Mod → Member. */
    public static boolean canDemote(Role actor, Role target) {
        if (actor == Role.LEADER) {
            return target == Role.ADMIN || target == Role.MOD;
        }
        return actor == Role.ADMIN && target == Role.MOD;
    }

    /** The leader hands the island to a stored admin, mod or member. */
    public static boolean canTransfer(Role actor, Role target) {
        return actor == Role.LEADER && (target == Role.ADMIN || target == Role.MOD || target == Role.MEMBER);
    }

    public static boolean canCoop(Role actor) {
        return isManager(actor);
    }

    /** {@code /is sethome}: Leader, Admin and Mod. */
    public static boolean canSetHome(Role actor) {
        return isManager(actor);
    }

    /** The leader can't leave (transfer first, or disband in M2.7); coop is not a stored membership. */
    public static boolean canLeave(Role actor) {
        return actor == Role.ADMIN || actor == Role.MOD || actor == Role.MEMBER;
    }

    /** Every stored member can look at the member list and the permission matrix. */
    public static boolean canView(Role actor) {
        return actor == Role.LEADER || actor == Role.ADMIN || actor == Role.MOD || actor == Role.MEMBER;
    }

    /**
     * Permission matrix rows: the leader edits every editable row (Admin, Mod, Member, Coop, Visitor); an admin edits
     * Mod, Member, Coop and Visitor; a mod edits Member, Coop and Visitor; nobody edits the leader (always everything).
     */
    public static boolean canEditPerms(Role editor, PermRole row) {
        if (row == null || !row.editable()) {
            return false;
        }
        return switch (editor) {
            case LEADER -> true;
            case ADMIN -> row != PermRole.ADMIN;
            case MOD -> row != PermRole.ADMIN && row != PermRole.MOD;
            case null, default -> false;
        };
    }

    /**
     * One matrix cell: the row must be editable for the editor, the permission must exist for that row (management
     * rights only for stored members), and a management right (settings) can only be handed out by someone who has it
     * ({@code editorHasPerm}; the leader always has it).
     */
    public static boolean canEditCell(Role editor, PermRole row, IslandPerm perm, boolean editorHasPerm) {
        if (!canEditPerms(editor, row) || !perm.appliesTo(row)) {
            return false;
        }
        return !perm.management() || editor == Role.LEADER || editorHasPerm;
    }

    /**
     * {@code /is settings} (ISL2): stored members whose role has the {@code settings} permission (default Leader +
     * Admin; the leader always). Coop players and visitors never.
     */
    public static boolean canChangeSettings(Role actor, boolean hasSettingsPerm) {
        if (actor == Role.LEADER) {
            return true;
        }
        return canLeave(actor) && hasSettingsPerm;
    }

    /** The role after a promotion (Member → Mod → Admin), or the same role when it can't go higher. */
    public static Role promoted(Role role) {
        return switch (role) {
            case MEMBER -> Role.MOD;
            case MOD -> Role.ADMIN;
            case null, default -> role;
        };
    }

    /** The role after a demotion (Admin → Mod → Member), or the same role. */
    public static Role demoted(Role role) {
        return switch (role) {
            case ADMIN -> Role.MOD;
            case MOD -> Role.MEMBER;
            case null, default -> role;
        };
    }

    /**
     * The override to store when a matrix cell is toggled: the new effective value, or {@code null} (follow the server
     * default again) when the new value equals the default. Keeps {@code island_role_perms} minimal and lets Bedrock
     * players reset with a plain click.
     */
    public static Boolean toggledOverride(boolean currentlyAllowed, boolean defaultAllowed) {
        boolean next = !currentlyAllowed;
        return next == defaultAllowed ? null : next;
    }

    /** Effective member slots: the island's own value ({@code null} = start value), between 1 and the max. */
    public static int slots(Integer islandSlots, int startSlots, int maxSlots) {
        int max = Math.max(1, maxSlots);
        int value = islandSlots == null ? startSlots : islandSlots;
        return Math.max(1, Math.min(max, value));
    }
}
