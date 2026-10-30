package net.skybornmc.skyblock.islands;

import net.skybornmc.skyblock.common.island.IslandService.Role;
import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Entry, ban, expel, rating and like rules of M2.4. */
class VisitRulesTest {

    @Test
    void membersAndCoopAlwaysEnterEvenWhenLocked() {
        for (Role role : Role.values()) {
            assertEquals(VisitRules.Access.MEMBER, VisitRules.access(role, true, true, false), role.name());
        }
    }

    @Test
    void banBeatsLockAndBypassBeatsBoth() {
        assertEquals(VisitRules.Access.BANNED, VisitRules.access(null, true, true, false));
        assertEquals(VisitRules.Access.BANNED, VisitRules.access(null, true, false, false));
        assertEquals(VisitRules.Access.LOCKED, VisitRules.access(null, false, true, false));
        assertEquals(VisitRules.Access.VISITOR, VisitRules.access(null, false, false, false));
        assertEquals(VisitRules.Access.BYPASS, VisitRules.access(null, true, true, true));
        assertTrue(VisitRules.Access.VISITOR.allowed());
        assertTrue(VisitRules.Access.BYPASS.allowed());
        assertFalse(VisitRules.Access.BANNED.allowed());
        assertFalse(VisitRules.Access.LOCKED.allowed());
    }

    @Test
    void onlyLeaderAndModsManageVisitors() {
        assertTrue(VisitRules.canManageVisitors(Role.LEADER));
        assertTrue(VisitRules.canManageVisitors(Role.MOD));
        assertFalse(VisitRules.canManageVisitors(Role.MEMBER));
        assertFalse(VisitRules.canManageVisitors(Role.COOP));
        assertFalse(VisitRules.canManageVisitors(null));
    }

    @Test
    void banChecks() {
        assertEquals(VisitRules.BanCheck.OK, VisitRules.canBan(Role.LEADER, null, false, false, 0, 100));
        assertEquals(VisitRules.BanCheck.OK, VisitRules.canBan(Role.MOD, null, false, false, 99, 100));
        assertEquals(VisitRules.BanCheck.NOT_ALLOWED, VisitRules.canBan(Role.MEMBER, null, false, false, 0, 100));
        assertEquals(VisitRules.BanCheck.NOT_ALLOWED, VisitRules.canBan(null, null, false, false, 0, 100));
        assertEquals(VisitRules.BanCheck.SELF, VisitRules.canBan(Role.LEADER, Role.LEADER, true, false, 0, 100));
        assertEquals(VisitRules.BanCheck.MEMBER, VisitRules.canBan(Role.LEADER, Role.MEMBER, false, false, 0, 100));
        assertEquals(VisitRules.BanCheck.MEMBER, VisitRules.canBan(Role.MOD, Role.LEADER, false, false, 0, 100));
        assertEquals(VisitRules.BanCheck.COOP, VisitRules.canBan(Role.LEADER, Role.COOP, false, false, 0, 100));
        assertEquals(VisitRules.BanCheck.ALREADY, VisitRules.canBan(Role.LEADER, null, false, true, 0, 100));
        assertEquals(VisitRules.BanCheck.LIMIT, VisitRules.canBan(Role.LEADER, null, false, false, 100, 100));
    }

    @Test
    void expelChecks() {
        assertEquals(VisitRules.ExpelCheck.OK, VisitRules.canExpel(Role.MOD, null, false, true, false));
        assertEquals(VisitRules.ExpelCheck.NOT_ALLOWED, VisitRules.canExpel(Role.MEMBER, null, false, true, false));
        assertEquals(VisitRules.ExpelCheck.SELF, VisitRules.canExpel(Role.LEADER, Role.LEADER, true, true, false));
        assertEquals(VisitRules.ExpelCheck.MEMBER, VisitRules.canExpel(Role.LEADER, Role.COOP, false, true, false));
        assertEquals(VisitRules.ExpelCheck.NOT_HERE, VisitRules.canExpel(Role.LEADER, null, false, false, false));
        assertEquals(VisitRules.ExpelCheck.PROTECTED, VisitRules.canExpel(Role.LEADER, null, false, true, true));
    }

    @Test
    void ratingRules() {
        assertEquals(VisitRules.VoteCheck.OK, VisitRules.canRate(5, 5, null, true, true, null));
        assertEquals(VisitRules.VoteCheck.OK, VisitRules.canRate(1, 5, Role.COOP, true, true, null), "coop is not the own island");
        assertEquals(VisitRules.VoteCheck.INVALID_STARS, VisitRules.canRate(0, 5, null, true, true, null));
        assertEquals(VisitRules.VoteCheck.INVALID_STARS, VisitRules.canRate(6, 5, null, true, true, null));
        for (Role own : new Role[]{Role.LEADER, Role.MOD, Role.MEMBER}) {
            assertEquals(VisitRules.VoteCheck.OWN_ISLAND, VisitRules.canRate(5, 5, own, true, true, null));
        }
        assertEquals(VisitRules.VoteCheck.NOT_VISITED, VisitRules.canRate(4, 5, null, false, true, null));
        assertEquals(VisitRules.VoteCheck.OK, VisitRules.canRate(4, 5, null, false, false, null), "visit not required");
        assertEquals(VisitRules.VoteCheck.ALREADY, VisitRules.canRate(4, 5, null, true, true, 4));
        assertEquals(VisitRules.VoteCheck.OK, VisitRules.canRate(2, 5, null, true, true, 4), "changing a rating is allowed");
    }

    @Test
    void likeRules() {
        assertEquals(VisitRules.VoteCheck.OK, VisitRules.canLike(null, true, true, false));
        assertEquals(VisitRules.VoteCheck.ALREADY, VisitRules.canLike(null, true, true, true));
        assertEquals(VisitRules.VoteCheck.OWN_ISLAND, VisitRules.canLike(Role.MEMBER, true, true, false));
        assertEquals(VisitRules.VoteCheck.NOT_VISITED, VisitRules.canLike(null, false, true, false));
        assertEquals(VisitRules.VoteCheck.OK, VisitRules.canLike(Role.COOP, false, false, false));
    }

    @Test
    void daysResetAtLocalMidnight() {
        ZoneId oslo = ZoneId.of("Europe/Oslo");
        long beforeMidnight = ZonedDateTime.of(2026, 10, 30, 23, 59, 0, 0, oslo).toInstant().toEpochMilli();
        long afterMidnight = ZonedDateTime.of(2026, 10, 31, 0, 1, 0, 0, oslo).toInstant().toEpochMilli();
        assertEquals(VisitRules.day(beforeMidnight, oslo) + 1, VisitRules.day(afterMidnight, oslo));
        // 23:30 UTC is already the next day in Oslo.
        long lateUtc = ZonedDateTime.of(2026, 10, 30, 23, 30, 0, 0, ZoneId.of("UTC")).toInstant().toEpochMilli();
        assertEquals(VisitRules.day(lateUtc, ZoneId.of("UTC")) + 1, VisitRules.day(lateUtc, oslo));
        assertEquals(ZoneId.of("UTC"), VisitRules.zone("not/a-zone"));
    }

    @Test
    void averages() {
        assertEquals(0.0, VisitRules.average(0, 0));
        assertEquals(4.5, VisitRules.average(9, 2));
        assertEquals(3.3, VisitRules.average(10, 3));
    }

    @Test
    void ownerAnswer292PlaytimeAndAddressLimit() {
        assertTrue(VisitRules.tooNew(59 * 60_000L, 60));
        assertFalse(VisitRules.tooNew(60 * 60_000L, 60), "exactly one hour is enough");
        assertFalse(VisitRules.tooNew(0, 0), "0 = off");
        assertFalse(VisitRules.ipLimitReached(1, 3), "the 2nd and 3rd account may vote");
        assertTrue(VisitRules.ipLimitReached(2, 3), "a 4th account from the same address may not");
        assertFalse(VisitRules.ipLimitReached(50, 0), "0 = no limit");
        assertEquals(VisitRules.VoteCheck.IP_LIMIT, VisitRules.canRate(4, 5, null, true, true, null, 2, 3));
        assertEquals(VisitRules.VoteCheck.OK, VisitRules.canRate(4, 5, null, true, true, 3, 2, 3), "changing your own rating is fine");
        assertEquals(VisitRules.VoteCheck.NOT_VISITED, VisitRules.canRate(4, 5, null, false, true, null, 2, 3), "other checks first");
        assertEquals(VisitRules.VoteCheck.IP_LIMIT, VisitRules.canLike(null, true, true, false, 2, 3));
        assertEquals(VisitRules.VoteCheck.ALREADY, VisitRules.canLike(null, true, true, true, 2, 3));
        assertEquals(VisitRules.VoteCheck.OK, VisitRules.canLike(null, true, true, false, 1, 3));
        String hash = VisitRules.ipHash("1.2.3.4");
        assertEquals(64, hash.length());
        assertEquals(hash, VisitRules.ipHash(" 1.2.3.4 "));
        assertFalse(hash.contains("1.2.3.4"), "never the address itself");
        assertNull(VisitRules.ipHash(null));
    }
}
