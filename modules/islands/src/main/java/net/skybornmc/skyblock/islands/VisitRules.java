package net.skybornmc.skyblock.islands;

import net.skybornmc.skyblock.common.island.IslandService.Role;

import java.time.Instant;
import java.time.ZoneId;

/**
 * Who may enter, visit, rate and like an island, and who may lock, expel and ban (M2.4, pure, no Bukkit).
 * <ul>
 *   <li>Members and coop players always enter their island; staff with the visit bypass too.</li>
 *   <li>Everyone else: not when banned (checked first), not when the island is locked.</li>
 *   <li>Leader and mods lock/unlock, expel and ban (members must be kicked first, coop players uncooped first).</li>
 *   <li>Ratings: 1-5 stars, one per player per island per season (changeable), never on your own island.</li>
 *   <li>Likes: one per player per island per day (owner O7), never on your own island.</li>
 *   <li>Rating and liking need a visit first ({@code visiting.require-visit}) so nobody rates islands they never saw.</li>
 *   <li>Owner answer 292: rating and liking need 1 hour of playtime, and at most 3 accounts from the same address may
 *       rate (or like) the same island.</li>
 * </ul>
 */
public final class VisitRules {

    private VisitRules() {
    }

    /** Result of an entry check. */
    public enum Access {
        /** A member or coop player of the island. */
        MEMBER,
        /** Staff bypass (toggle or permission): enters anything, never counted as a visitor. */
        BYPASS,
        /** A visitor who may enter. */
        VISITOR,
        BANNED,
        LOCKED,
        /** Owner P3/P4: another profile of this player is a member or coop of the island. */
        OWN_PROFILE,
        /** Owner answer 522: the island's leader is banned, so it is closed to visitors (the team keeps playing). */
        CLOSED;

        public boolean allowed() {
            return this == MEMBER || this == BYPASS || this == VISITOR;
        }
    }

    /**
     * May a player with island role {@code role} ({@code null} = not on the island, coop counts as member) enter?
     * Ban beats lock so a banned player always hears they are banned.
     */
    public static Access access(Role role, boolean banned, boolean locked, boolean bypass) {
        return access(role, banned, locked, bypass, false);
    }

    /**
     * {@link #access(Role, boolean, boolean, boolean)} with owner P3/P4: a player whose other profile belongs to the island
     * never enters it (not even with the staff bypass), so nothing can move between their profiles.
     */
    public static Access access(Role role, boolean banned, boolean locked, boolean bypass, boolean ownOtherProfile) {
        return access(role, banned, locked, bypass, ownOtherProfile, false);
    }

    /**
     * Same, with a staff island ban (Phase P7, spec 13.2 {@code island-ban}): it keeps even members off the island; only
     * the staff bypass ignores it.
     */
    public static Access access(Role role, boolean banned, boolean locked, boolean bypass, boolean ownOtherProfile,
                                boolean staffBanned) {
        return access(role, banned, locked, bypass, ownOtherProfile, staffBanned, false);
    }

    /**
     * Same, with owner answer 522: when the island's leader is banned ({@code leaderBanned}) the island is closed to
     * visitors; members and coop players keep playing and the staff bypass still enters. An island ban on the visitor is
     * told first.
     */
    public static Access access(Role role, boolean banned, boolean locked, boolean bypass, boolean ownOtherProfile,
                                boolean staffBanned, boolean leaderBanned) {
        if (staffBanned && !bypass) {
            return Access.BANNED;
        }
        if (role != null) {
            return Access.MEMBER;
        }
        if (ownOtherProfile) {
            return Access.OWN_PROFILE;
        }
        if (bypass) {
            return Access.BYPASS;
        }
        if (banned) {
            return Access.BANNED;
        }
        if (leaderBanned) {
            return Access.CLOSED;
        }
        return locked ? Access.LOCKED : Access.VISITOR;
    }

    /** Leader and mods manage visitors: lock, unlock, expel, ban, unban (and warps). */
    public static boolean canManageVisitors(Role actor) {
        return MemberRules.isManager(actor);
    }

    /** Why a ban can't be done, or OK. */
    public enum BanCheck {
        OK, NOT_ALLOWED, SELF, MEMBER, COOP, ALREADY, LIMIT
    }

    /**
     * @param actor      the banning player's role on their island
     * @param targetRole the target's role on that island ({@code null} = visitor)
     * @param self       the target is the actor
     * @param banned     already banned
     * @param bans       current number of bans of the island
     * @param maxBans    {@code visiting.max-bans}
     */
    public static BanCheck canBan(Role actor, Role targetRole, boolean self, boolean banned, int bans, int maxBans) {
        if (!canManageVisitors(actor)) {
            return BanCheck.NOT_ALLOWED;
        }
        if (self) {
            return BanCheck.SELF;
        }
        if (targetRole == Role.COOP) {
            return BanCheck.COOP;
        }
        if (targetRole != null) {
            return BanCheck.MEMBER;
        }
        if (banned) {
            return BanCheck.ALREADY;
        }
        return bans >= maxBans ? BanCheck.LIMIT : BanCheck.OK;
    }

    /** Why an expel can't be done, or OK. */
    public enum ExpelCheck {
        OK, NOT_ALLOWED, SELF, MEMBER, NOT_HERE, PROTECTED
    }

    /**
     * @param present      the target stands in the island's slot
     * @param targetBypass the target has the staff visit bypass (can't be expelled)
     */
    public static ExpelCheck canExpel(Role actor, Role targetRole, boolean self, boolean present, boolean targetBypass) {
        if (!canManageVisitors(actor)) {
            return ExpelCheck.NOT_ALLOWED;
        }
        if (self) {
            return ExpelCheck.SELF;
        }
        if (targetRole != null) {
            return ExpelCheck.MEMBER;
        }
        if (!present) {
            return ExpelCheck.NOT_HERE;
        }
        return targetBypass ? ExpelCheck.PROTECTED : ExpelCheck.OK;
    }

    /** Why a rating or like can't be given, or OK. */
    public enum VoteCheck {
        OK, INVALID_STARS, OWN_ISLAND, NOT_VISITED, ALREADY,
        /** Owner answer 292: not enough playtime yet. */
        NEW_PLAYER,
        /** Owner answer 292: enough other accounts from the same address already voted for this island. */
        IP_LIMIT
    }

    /**
     * Owner answer 292, checked before the database: {@code playtimeMillis} below {@code minMinutes} is too new
     * (0 minutes = off).
     */
    public static boolean tooNew(long playtimeMillis, int minMinutes) {
        return minMinutes > 0 && playtimeMillis < minMinutes * 60_000L;
    }

    /**
     * Owner answer 292: {@code otherAccounts} = other accounts from the voter's address that already voted for this island
     * (the voter not counted). The voter may vote while fewer than {@code maxAccounts} - 1 others did (0 = no limit).
     */
    public static boolean ipLimitReached(int otherAccounts, int maxAccounts) {
        return maxAccounts > 0 && otherAccounts >= maxAccounts - 1;
    }

    /** Rating with the owner answer 292 checks: {@code otherAccounts} from the same address, {@code maxAccounts} allowed. */
    public static VoteCheck canRate(int stars, int maxStars, Role raterRole, boolean visited, boolean requireVisit, Integer current,
                                    int otherAccounts, int maxAccounts) {
        VoteCheck check = canRate(stars, maxStars, raterRole, visited, requireVisit, current);
        // Changing your own rating is always fine: the address limit only stops new accounts from adding ratings.
        if (check == VoteCheck.OK && current == null && ipLimitReached(otherAccounts, maxAccounts)) {
            return VoteCheck.IP_LIMIT;
        }
        return check;
    }

    /** Liking with the owner answer 292 address limit. */
    public static VoteCheck canLike(Role likerRole, boolean visited, boolean requireVisit, boolean likedToday, int otherAccounts,
                                    int maxAccounts) {
        VoteCheck check = canLike(likerRole, visited, requireVisit, likedToday);
        if (check == VoteCheck.OK && ipLimitReached(otherAccounts, maxAccounts)) {
            return VoteCheck.IP_LIMIT;
        }
        return check;
    }

    /**
     * {@code /is rate}: {@code current} = the player's rating this season (null = none). Rating the same value again is
     * {@link VoteCheck#ALREADY}; another value changes it.
     */
    public static VoteCheck canRate(int stars, int maxStars, Role raterRole, boolean visited, boolean requireVisit, Integer current) {
        if (stars < 1 || stars > maxStars) {
            return VoteCheck.INVALID_STARS;
        }
        if (raterRole != null && raterRole != Role.COOP) {
            return VoteCheck.OWN_ISLAND;
        }
        if (requireVisit && !visited) {
            return VoteCheck.NOT_VISITED;
        }
        if (current != null && current == stars) {
            return VoteCheck.ALREADY;
        }
        return VoteCheck.OK;
    }

    /** {@code /is like}: once per player per island per day. */
    public static VoteCheck canLike(Role likerRole, boolean visited, boolean requireVisit, boolean likedToday) {
        if (likerRole != null && likerRole != Role.COOP) {
            return VoteCheck.OWN_ISLAND;
        }
        if (requireVisit && !visited) {
            return VoteCheck.NOT_VISITED;
        }
        return likedToday ? VoteCheck.ALREADY : VoteCheck.OK;
    }

    /** SHA-256 hex of an address (owner answer 292 stores this, never the address); {@code null} for no address. */
    public static String ipHash(String address) {
        if (address == null || address.isBlank()) {
            return null;
        }
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(("phoenix-island-vote:" + address.strip()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 missing", e);
        }
    }

    /** The day number used for "once per day" (epoch day in {@code zone}). */
    public static int day(long epochMillis, ZoneId zone) {
        return (int) Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate().toEpochDay();
    }

    /** {@code visiting.timezone}, or UTC when invalid. */
    public static ZoneId zone(String id) {
        try {
            return ZoneId.of(id);
        } catch (RuntimeException e) {
            return ZoneId.of("UTC");
        }
    }

    /** Average stars with one decimal (0 when unrated). */
    public static double average(long sum, int count) {
        return count <= 0 ? 0 : Math.round(sum * 10.0 / count) / 10.0;
    }
}
