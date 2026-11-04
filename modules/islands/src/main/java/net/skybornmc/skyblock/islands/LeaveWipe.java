package net.skybornmc.skyblock.islands;

import net.skybornmc.skyblock.common.island.IslandService.Role;
import net.skybornmc.skyblock.common.profile.WipeReason;

/**
 * Owner 4 Nov 2026 ("island = profile"): which island changes wipe the profile, and the "joining needs an empty profile"
 * rule. Pure, unit-tested. The wipe itself is {@link net.skybornmc.skyblock.common.profile.ProfileService#wipe}.
 */
final class LeaveWipe {

    private LeaveWipe() {
    }

    /** What accepting an invite needs from the player's current profile. */
    enum JoinNeed {
        /** No island on this profile: it is empty, join right away. */
        JOIN,
        /** Alone on their own island: joining replaces (removes) it, which wipes this profile (confirm screen). */
        REPLACE,
        /** On an island with others: use an empty profile slot or leave the island first. */
        NEED_EMPTY
    }

    static JoinNeed joinNeed(boolean hasIsland, Role role, int memberCount) {
        if (!hasIsland) {
            return JoinNeed.JOIN;
        }
        return role == Role.LEADER && memberCount <= 1 ? JoinNeed.REPLACE : JoinNeed.NEED_EMPTY;
    }

    /**
     * The wipe reason of a member removal ({@link MemberStore#LEFT_LEAVE}, {@code LEFT_KICK}, {@code LEFT_REMOVED}), or null
     * when the removal must not wipe: the profile itself was deleted or retired already ({@link KickBackups#NOTE_PROFILE_DELETED})
     * or the removal is something else.
     */
    static WipeReason forRemoval(String leftReason, String note) {
        if (KickBackups.NOTE_PROFILE_DELETED.equals(note) || leftReason == null) {
            return null;
        }
        return switch (leftReason) {
            case MemberStore.LEFT_LEAVE -> WipeReason.LEAVE;
            case MemberStore.LEFT_KICK -> WipeReason.KICK;
            case MemberStore.LEFT_REMOVED -> WipeReason.REMOVED;
            default -> null;
        };
    }

    /**
     * The wipe reason for the members of a removed island, or null: replacing your solo island to join another one
     * ({@link IslandStore#REASON_JOIN}) is wiped by the join flow itself, which needs the fresh profile key to accept.
     */
    static WipeReason forIslandRemoval(String reason) {
        if (reason == null) {
            return null;
        }
        return switch (reason) {
            case IslandStore.REASON_DISBAND -> WipeReason.DISBAND;
            case IslandStore.REASON_DELETE -> WipeReason.ISLAND_DELETED;
            case IslandStore.REASON_RESET -> WipeReason.ISLAND_RESET;
            default -> null;
        };
    }

    /** Coop players are guests, not members: ending a coop never wipes. */
    static boolean wipesRole(Role role) {
        return role != null && role != Role.COOP;
    }
}
