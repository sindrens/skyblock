package net.skybornmc.skyblock.islands;

import net.skybornmc.skyblock.common.island.IslandService.Role;

/**
 * Who may do what with island members (M2.3, pure, no Bukkit). Ranks: Leader &gt; Mod &gt; Member &gt; Coop. Fixed rules
 * (the 14 build/use actions are the configurable part, see {@link RolePermissions}):
 * <ul>
 *   <li>Leader: everything; the only one who promotes, demotes and transfers.</li>
 *   <li>Mod: invites, kicks members (not mods), coops/uncoops, sets the home, edits the Member/Coop/Visitor permissions.</li>
 *   <li>Member: can see the member list and the permission matrix, can leave.</li>
 *   <li>Coop: temporary trust, never a stored member; nothing to manage.</li>
 * </ul>
 * A {@code null} role means "not on the island" and is never allowed anything.
 */
public final class MemberRules {

    private MemberRules() {
    }

    /** Higher = more power (leader 3, mod 2, member 1, coop 0, none -1). */
    static int rank(Role role) {
        if (role == null) {
            return -1;
        }
        return switch (role) {
            case LEADER -> 3;
            case MOD -> 2;
            case MEMBER -> 1;
            case COOP -> 0;
        };
    }

    /** Leader and mods manage the island's people (invite, kick, coop) and its home. */
    public static boolean isManager(Role role) {
        return role == Role.LEADER || role == Role.MOD;
    }

    public static boolean canInvite(Role actor) {
        return isManager(actor);
    }

    /** Leader kicks mods and members; a mod kicks members. Nobody kicks the leader or themselves (same rank). */
    public static boolean canKick(Role actor, Role target) {
        return isManager(actor) && target != null && target != Role.LEADER && target != Role.COOP && rank(actor) > rank(target);
    }

    /** Only the leader promotes, and only Member → Mod. */
    public static boolean canPromote(Role actor, Role target) {
        return actor == Role.LEADER && target == Role.MEMBER;
    }

    /** Only the leader demotes, and only Mod → Member. */
    public static boolean canDemote(Role actor, Role target) {
        return actor == Role.LEADER && target == Role.MOD;
    }

    /** The leader hands the island to a stored mod or member. */
    public static boolean canTransfer(Role actor, Role target) {
        return actor == Role.LEADER && (target == Role.MOD || target == Role.MEMBER);
    }

    public static boolean canCoop(Role actor) {
        return isManager(actor);
    }

    /** {@code /is sethome}: Leader + Mod (M2.1 was leader-only). */
    public static boolean canSetHome(Role actor) {
        return isManager(actor);
    }

    /** The leader can't leave (transfer first, or disband in M2.7); coop is not a stored membership. */
    public static boolean canLeave(Role actor) {
        return actor == Role.MOD || actor == Role.MEMBER;
    }

    /** Every stored member can look at the member list and the permission matrix. */
    public static boolean canView(Role actor) {
        return actor == Role.LEADER || actor == Role.MOD || actor == Role.MEMBER;
    }

    /**
     * Permission matrix: the leader edits every editable row (Mod, Member, Coop, Visitor); a mod edits Member, Coop and
     * Visitor but never the Mod or Leader rows; nobody edits the leader (always everything).
     */
    public static boolean canEditPerms(Role editor, PermRole row) {
        if (row == null || !row.editable()) {
            return false;
        }
        if (editor == Role.LEADER) {
            return true;
        }
        return editor == Role.MOD && row != PermRole.MOD;
    }

    /** The role after a promotion (Member → Mod), or the same role when it can't go higher. */
    public static Role promoted(Role role) {
        return role == Role.MEMBER ? Role.MOD : role;
    }

    /** The role after a demotion (Mod → Member), or the same role. */
    public static Role demoted(Role role) {
        return role == Role.MOD ? Role.MEMBER : role;
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
