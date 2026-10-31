package net.skybornmc.skyblock.rewards;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Voting rules: sites, ids, Bedrock names, 24 h per site, address limit, streak over Oslo midnights, vote party. */
class VoteRulesTest {

    private static final ZoneId OSLO = ZoneId.of("Europe/Oslo");
    private static final long H = VoteRules.HOUR;

    private static long at(int y, int mo, int d, int h, int mi) {
        return LocalDateTime.of(y, mo, d, h, mi).atZone(OSLO).toInstant().toEpochMilli();
    }

    @Test
    void defaultSitesParseAndMatchTheirServiceNames() {
        List<String> problems = new ArrayList<>();
        List<VoteRules.Site> sites = VoteRules.sites(new VotesSettings().sites, problems::add);
        assertEquals(List.of(), problems);
        assertEquals(5, sites.size());
        assertEquals("planetminecraft", VoteRules.site(sites, "PlanetMinecraft.com").orElseThrow().id());
        assertEquals("planetminecraft", VoteRules.site(sites, "www.planetminecraft.com").orElseThrow().id());
        assertEquals("minecraft-mp", VoteRules.site(sites, "minecraft-mp.com").orElseThrow().id());
        assertEquals("topg", VoteRules.site(sites, "TOPG.ORG").orElseThrow().id());
        assertEquals("minecraft-server-list", VoteRules.site(sites, "Minecraft-Server-List.com").orElseThrow().id());
        assertTrue(VoteRules.site(sites, "evil.example").isEmpty());
        assertTrue(VoteRules.site(sites, "").isEmpty());
    }

    @Test
    void brokenSiteLinesAreSkipped() {
        List<String> problems = new ArrayList<>();
        List<VoteRules.Site> sites = VoteRules.sites(List.of("a | A | a.com | https://a.com", "a | Again | b.com | https://b.com",
                "only | three | parts", "Bad Id | X | x.com | https://x.com", "b | B | b.com | ftp://nope"), problems::add);
        assertEquals(1, sites.size());
        assertEquals(4, problems.size());
    }

    @Test
    void voteIdIsStableForTheSamePacket() {
        String a = VoteRules.voteId("PlanetMinecraft.com", "Steve", 1000, 5_000);
        assertEquals(a, VoteRules.voteId("planetminecraft.com", "steve", 1000, 99_999_999));
        assertNotEquals(a, VoteRules.voteId("PlanetMinecraft.com", "Steve", 1001, 5_000));
        assertNotEquals(a, VoteRules.voteId("TopG.org", "Steve", 1000, 5_000));
        assertEquals(40, a.length());
        // No usable site timestamp: keyed by the arrival minute.
        assertEquals(VoteRules.voteId("s", "x", 0, 60_000), VoteRules.voteId("s", "x", 0, 119_999));
        assertNotEquals(VoteRules.voteId("s", "x", 0, 60_000), VoteRules.voteId("s", "x", 0, 120_000));
    }

    @Test
    void bedrockNamesAreLookedUpWithAndWithoutThePrefix() {
        assertEquals(List.of("Steve", ".Steve"), VoteRules.voteNames("Steve", "."));
        assertEquals(List.of(".Steve"), VoteRules.voteNames(".Steve", "."));
        assertEquals(List.of("Big_Bob", ".Big_Bob"), VoteRules.voteNames("Big Bob", "."));
        assertEquals(List.of("Steve"), VoteRules.voteNames("Steve", ""));
        // Joining: a Bedrock player takes votes sent without the prefix; a Java player never takes ".Name" votes.
        assertEquals(List.of(".steve", "steve"), VoteRules.joinNames(".Steve", "."));
        assertEquals(List.of("steve"), VoteRules.joinNames("Steve", "."));
        assertEquals(List.of("big_bob"), VoteRules.joinNames("Big Bob", null));
        assertEquals("big_bob", VoteRules.nameKey(" Big Bob "));
    }

    @Test
    void oneRewardPerSitePer24Hours() {
        long t = at(2026, 10, 31, 23, 0);
        long day = 24 * H;
        assertFalse(VoteRules.onCooldown(List.of(), t, day));
        assertTrue(VoteRules.onCooldown(List.of(t - day + 1), t, day));
        assertFalse(VoteRules.onCooldown(List.of(t - day), t, day));
        // A late offline vote: a paid vote 2 hours after it blocks it too.
        assertTrue(VoteRules.onCooldown(List.of(t + 2 * H), t, day));
        assertEquals(0, VoteRules.untilNext(0, t, day));
        assertEquals(5 * H, VoteRules.untilNext(t - 19 * H, t, day));
        assertEquals(0, VoteRules.untilNext(t - 30 * H, t, day));
    }

    @Test
    void addressLimit() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();
        UUID d = UUID.randomUUID();
        assertTrue(VoteRules.addressAllowed(Set.of(a, b), c, 3));
        assertFalse(VoteRules.addressAllowed(Set.of(a, b, c), d, 3));
        assertTrue(VoteRules.addressAllowed(Set.of(a, b, c), a, 3));
        assertTrue(VoteRules.addressAllowed(Set.of(a, b, c), d, 0));
        assertEquals("", VoteRules.addressHash(" "));
        assertEquals(VoteRules.addressHash("1.2.3.4"), VoteRules.addressHash(" 1.2.3.4 "));
        assertFalse(VoteRules.addressHash("1.2.3.4").contains("1.2.3.4"));
    }

    @Test
    void streakFollowsOsloMidnight() {
        // 23:59 and 00:01 Oslo time are two days, even though only 2 minutes apart.
        int d1 = VoteRules.date(at(2026, 10, 30, 23, 59), OSLO);
        int d2 = VoteRules.date(at(2026, 10, 31, 0, 1), OSLO);
        assertEquals(d1 + 1, d2);
        assertEquals(LocalDate.of(2026, 10, 31).toEpochDay(), d2);
        VoteRules.Streak s = VoteRules.advance(VoteRules.Streak.NONE, d1);
        assertEquals(new VoteRules.Streak(1, d1, 1), s);
        s = VoteRules.advance(s, d2);
        assertEquals(2, s.days());
        // Same day again: no change. An older (late offline) vote: no change.
        assertEquals(s, VoteRules.advance(s, d2));
        assertEquals(s, VoteRules.advance(s, d1));
        // A missed day resets; best is kept.
        VoteRules.Streak reset = VoteRules.advance(s, d2 + 2);
        assertEquals(new VoteRules.Streak(1, d2 + 2, 2), reset);
        // Shown streak: alive today and tomorrow, 0 after a whole missed day.
        assertEquals(2, VoteRules.liveStreak(s, d2));
        assertEquals(2, VoteRules.liveStreak(s, d2 + 1));
        assertEquals(0, VoteRules.liveStreak(s, d2 + 2));
        assertTrue(VoteRules.votedOn(s, d2));
    }

    @Test
    void streakAcrossTheDstChange() {
        // 25 Oct 2026: clocks go back in Oslo (a 25-hour day). Votes at 00:30 on the 25th and 23:30 on the 25th are one day.
        int a = VoteRules.date(at(2026, 10, 25, 0, 30), OSLO);
        int b = VoteRules.date(at(2026, 10, 25, 23, 30), OSLO);
        int c = VoteRules.date(at(2026, 10, 26, 0, 30), OSLO);
        assertEquals(a, b);
        assertEquals(a + 1, c);
    }

    @Test
    void streakRewardsCycleEvery30Days() {
        NavigableMap<Integer, String> r = VoteRules.streakRewards(new VotesSettings().streakRewards, p -> {
            throw new AssertionError(p);
        });
        assertEquals(Optional.of("cinder"), VoteRules.streakReward(7, r, 30));
        assertEquals(Optional.of("blaze"), VoteRules.streakReward(14, r, 30));
        assertEquals(Optional.of("cinder"), VoteRules.streakReward(21, r, 30));
        assertEquals(Optional.of("inferno"), VoteRules.streakReward(30, r, 30));
        assertEquals(Optional.empty(), VoteRules.streakReward(31, r, 30));
        assertEquals(Optional.of("cinder"), VoteRules.streakReward(37, r, 30));
        assertEquals(Optional.of("inferno"), VoteRules.streakReward(60, r, 30));
        assertEquals(Optional.empty(), VoteRules.streakReward(37, r, 0));
        assertEquals(Optional.empty(), VoteRules.streakReward(0, r, 30));
        assertArrayEquals(new int[]{7, 7}, VoteRules.nextStreakReward(0, r, 30).orElseThrow());
        assertArrayEquals(new int[]{1, 7}, VoteRules.nextStreakReward(6, r, 30).orElseThrow());
        assertArrayEquals(new int[]{7, 14}, VoteRules.nextStreakReward(7, r, 30).orElseThrow());
        assertArrayEquals(new int[]{7, 7}, VoteRules.nextStreakReward(30, r, 30).orElseThrow());
        assertTrue(VoteRules.nextStreakReward(30, r, 0).isEmpty());
        List<String> problems = new ArrayList<>();
        VoteRules.streakRewards(List.of("x: cinder", "7 cinder", "0: blaze", "5: two words"), problems::add);
        assertEquals(4, problems.size());
    }

    @Test
    void partyFillsAndStartsOver() {
        assertEquals(new VoteRules.PartyStep(1, false), VoteRules.addVote(0, 500));
        assertEquals(new VoteRules.PartyStep(499, false), VoteRules.addVote(498, 500));
        assertEquals(new VoteRules.PartyStep(0, true), VoteRules.addVote(499, 500));
        // The size was lowered below the counter: fills at once, the rest carries over.
        assertEquals(new VoteRules.PartyStep(101, true), VoteRules.addVote(600, 500));
        assertEquals(new VoteRules.PartyStep(0, true), VoteRules.addVote(0, 1));
        assertEquals(10, VoteRules.filledCells(250, 500, 20));
        assertEquals(20, VoteRules.filledCells(900, 500, 20));
        assertEquals(0, VoteRules.filledCells(5, 0, 20));
    }

    @Test
    void partyOnlyForActiveVotersOfToday() {
        assertTrue(VoteRules.partyEligible(true, true, true, false));
        assertFalse(VoteRules.partyEligible(true, false, true, false), "did not vote today");
        assertFalse(VoteRules.partyEligible(true, true, false, false), "tutorial not finished");
        assertFalse(VoteRules.partyEligible(true, true, true, true), "AFK");
        assertFalse(VoteRules.partyEligible(false, true, true, false), "no permission");
    }
}
