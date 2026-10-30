package net.skybornmc.skyblock.economy.bank;

import net.skybornmc.skyblock.common.island.IslandService.Role;
import net.skybornmc.skyblock.common.profile.ProfileService;

import java.util.UUID;

/** Who may use which bank (M3.3), pure and unit-tested. */
final class BankRules {

    private BankRules() {
    }

    /** Why a bank can't be used from a purse. */
    enum Access {
        OK,
        /** P4: the bank belongs to another profile of the same player. Nothing moves between own profiles. */
        OWN_PROFILE,
        /** Somebody else's bank. */
        NOT_YOURS,
        /** No island, or the purse's profile is not a stored member of that island (coop players and visitors). */
        NOT_MEMBER,
        /** The island role lacks the bank permission. */
        NO_PERMISSION
    }

    /**
     * P4 for personal banks: a personal bank is only ever reachable from the purse of the <b>same profile</b>. Another
     * profile of the same player is refused as {@link Access#OWN_PROFILE} (the caller logs the attempt).
     */
    static Access personal(UUID purseKey, UUID bankOwner, ProfileService profiles) {
        if (purseKey == null || bankOwner == null) {
            return Access.NOT_YOURS;
        }
        if (purseKey.equals(bankOwner)) {
            return Access.OK;
        }
        return profiles.sameOwner(purseKey, bankOwner) ? Access.OWN_PROFILE : Access.NOT_YOURS;
    }

    /**
     * Island banks: the purse's profile must be a stored member of exactly this island ({@code islandOfPurse} = the island
     * of the purse's profile key, so another own profile can never reach it: P3 keeps two own profiles off one island),
     * coop players never (REVIEW A4), and the role needs the permission.
     */
    static Access island(UUID islandOfPurse, UUID islandId, Role role, boolean hasPermission) {
        if (islandId == null || islandOfPurse == null || !islandOfPurse.equals(islandId) || role == null || role == Role.COOP) {
            return Access.NOT_MEMBER;
        }
        return hasPermission ? Access.OK : Access.NO_PERMISSION;
    }

    /**
     * What a member may still withdraw from the island bank today: {@code limitPercent}% of the capacity minus what they
     * took today ({@code Long.MAX_VALUE} = no limit, never negative).
     */
    static long remainingToday(long capacity, int limitPercent, long withdrawnToday) {
        long limit = InterestMath.percentOf(capacity, limitPercent);
        if (limit == Long.MAX_VALUE) {
            return Long.MAX_VALUE;
        }
        return Math.max(0, limit - Math.max(0, withdrawnToday));
    }

    /** Free room in a bank (never negative). */
    static long room(long capacity, long balance) {
        return Math.max(0, capacity - Math.max(0, balance));
    }

    /** The largest deposit possible now: what the purse has, the bank's free room. */
    static long maxDeposit(long purse, long capacity, long bankBalance) {
        return Math.max(0, Math.min(purse, room(capacity, bankBalance)));
    }

    /** The largest withdrawal possible now: the bank balance, the daily limit left. */
    static long maxWithdraw(long bankBalance, long remainingToday) {
        return Math.max(0, Math.min(bankBalance, remainingToday));
    }
}
