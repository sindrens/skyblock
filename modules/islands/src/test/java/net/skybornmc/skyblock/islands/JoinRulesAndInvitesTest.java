package net.skybornmc.skyblock.islands;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JoinRulesAndInvitesTest {

    private static final long HOUR = 3_600_000L;
    private static final long NOW = 1_000 * HOUR;

    @Test
    void firstJoinIsAllowed() {
        assertInstanceOf(JoinRules.Check.Ok.class, JoinRules.check(JoinRules.History.NONE, null, NOW, 3));
        assertInstanceOf(JoinRules.Check.Ok.class, JoinRules.check(null, null, NOW, 3));
    }

    @Test
    void maxThreeJoinsPerSeason() {
        JoinRules.History two = new JoinRules.History(2, NOW - 100 * HOUR, NOW - 50 * HOUR);
        assertInstanceOf(JoinRules.Check.Ok.class, JoinRules.check(two, null, NOW, 3));
        JoinRules.History three = new JoinRules.History(3, NOW - 100 * HOUR, NOW - 50 * HOUR);
        assertEquals(3, assertInstanceOf(JoinRules.Check.SeasonLimit.class, JoinRules.check(three, null, NOW, 3)).max());
        assertInstanceOf(JoinRules.Check.Ok.class, JoinRules.check(three, null, NOW, 0), "0 = unlimited");
    }

    @Test
    void noJoinCooldownAfterLeavingOrBeingKicked() {
        // Owner answers 121/161/258/259 (round 1): no join cooldown at all; only the season count stays.
        JoinRules.History joinedThenKicked = new JoinRules.History(1, NOW - 3 * HOUR, NOW - HOUR);
        assertInstanceOf(JoinRules.Check.Ok.class, JoinRules.check(joinedThenKicked, null, NOW, 3), "kicked an hour ago");
        JoinRules.History joinedJustNow = new JoinRules.History(2, NOW - 1, null);
        assertInstanceOf(JoinRules.Check.Ok.class, JoinRules.check(joinedJustNow, null, NOW, 3), "joined a moment ago");
        JoinRules.History leftJustNow = new JoinRules.History(1, NOW - 2, NOW - 1);
        assertInstanceOf(JoinRules.Check.Ok.class, JoinRules.check(leftJustNow, null, NOW, 3));
    }

    @Test
    void staffBlockWinsUntilItEnds() {
        assertEquals(NOW + HOUR, assertInstanceOf(JoinRules.Check.Blocked.class,
                JoinRules.check(JoinRules.History.NONE, NOW + HOUR, NOW, 3)).until());
        assertInstanceOf(JoinRules.Check.Ok.class, JoinRules.check(JoinRules.History.NONE, NOW, NOW, 3), "ended block");
        assertInstanceOf(JoinRules.Check.Blocked.class,
                JoinRules.check(new JoinRules.History(3, null, null), NOW + 1, NOW, 3), "block before the limit");
    }

    @Test
    void invitesExpireAfterFiveMinutes() {
        Invites invites = new Invites();
        UUID island = UUID.randomUUID();
        UUID target = UUID.randomUUID();
        long expiry = 5 * 60_000L;
        invites.put(new Invites.Invite(island, target, UUID.randomUUID(), NOW, NOW + expiry));
        assertTrue(invites.get(island, target, NOW + expiry - 1).isPresent());
        assertTrue(invites.get(island, target, NOW + expiry).isEmpty(), "expired exactly at the end");
        assertEquals(1, invites.pendingFrom(island, NOW));
        assertEquals(0, invites.pendingFrom(island, NOW + expiry));
        assertEquals(List.of(), invites.expire(NOW + expiry - 1));
        List<Invites.Invite> expired = invites.expire(NOW + expiry);
        assertEquals(1, expired.size());
        assertEquals(0, invites.size());
    }

    @Test
    void newInviteReplacesTheOldAndNewestComesFirst() {
        Invites invites = new Invites();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID target = UUID.randomUUID();
        invites.put(new Invites.Invite(a, target, UUID.randomUUID(), NOW, NOW + HOUR));
        invites.put(new Invites.Invite(b, target, UUID.randomUUID(), NOW + 10, NOW + HOUR));
        invites.put(new Invites.Invite(a, target, UUID.randomUUID(), NOW + 20, NOW + 2 * HOUR));
        assertEquals(2, invites.size());
        assertEquals(a, invites.of(target, NOW + 30).getFirst().islandId());
        assertEquals(NOW + 2 * HOUR, invites.get(a, target, NOW).orElseThrow().expiresAt());
        assertTrue(invites.remove(b, target));
        assertEquals(1, invites.removeTarget(target).size());
        assertEquals(0, invites.size());
    }

    @Test
    void removeIslandDropsOnlyItsInvites() {
        Invites invites = new Invites();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        invites.put(new Invites.Invite(a, UUID.randomUUID(), UUID.randomUUID(), NOW, NOW + HOUR));
        invites.put(new Invites.Invite(a, UUID.randomUUID(), UUID.randomUUID(), NOW, NOW + HOUR));
        invites.put(new Invites.Invite(b, UUID.randomUUID(), UUID.randomUUID(), NOW, NOW + HOUR));
        assertEquals(2, invites.removeIsland(a).size());
        assertEquals(1, invites.size());
        assertEquals(1, invites.from(b, NOW).size());
    }
}
