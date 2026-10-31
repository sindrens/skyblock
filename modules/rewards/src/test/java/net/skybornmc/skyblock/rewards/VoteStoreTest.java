package net.skybornmc.skyblock.rewards;

import net.skybornmc.skyblock.common.db.Database;
import net.skybornmc.skyblock.common.db.DatabaseConfig;
import net.skybornmc.skyblock.common.db.Migrations;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Votes on H2 in MySQL mode (same SQL as MariaDB): stored once, offline queue, paid once, 24 h rule, streak, party. */
class VoteStoreTest {

    private static final long DAY = 24 * VoteRules.HOUR;

    @TempDir
    Path dir;
    private Database db;
    private final UUID account = UUID.randomUUID();
    private final UUID profile = UUID.randomUUID();

    @BeforeEach
    void start() throws Exception {
        DatabaseConfig config = new DatabaseConfig();
        config.type = DatabaseConfig.Type.H2;
        config.h2File = "votes-test";
        config.poolSize = 4;
        db = new Database(config, dir.toFile(), Logger.getLogger("votes-test"));
        db.start();
        new Migrations(db, Logger.getLogger("votes-test")).migrate("rewards", getClass().getClassLoader());
    }

    @AfterEach
    void stop() {
        db.close();
    }

    private static VoteStore.Row row(String id, String site, String name, UUID account, long at, String address) {
        return new VoteStore.Row(id, site, site + ".com", name, VoteRules.nameKey(name), account, VoteRules.addressHash(address), 1, 0, at,
                (int) (at / DAY), VoteRules.QUEUED);
    }

    /** Runs one vote payment like the server does (guard in one local transaction). */
    private ClaimEngine.Status pay(VoteStore.Row r, int partySize, AtomicInteger filled) {
        Optional<VoteRules.Streak> before = db.blocking(c -> VoteStore.streak(c, account));
        VoteRules.Streak after = VoteRules.advance(before.orElse(VoteRules.Streak.NONE), r.voteDate());
        RewardStore.GrantRow grant = new RewardStore.GrantRow(UUID.randomUUID(), profile, account, "vote", "vote:" + r.voteId(), Payout.KEY,
                "ember", 1, RewardStore.PENDING, r.receivedAt());
        return ClaimEngine.commit((txs, guard) -> ClaimEngine.local(db, guard), List.of(),
                VoteService.payGuard(r, account, profile, DAY, before, after, partySize, List.of(grant), r.receivedAt(), filled,
                        new AtomicReference<>())).join();
    }

    private int grants() {
        return db.blocking(c -> RewardStore.grants(c, RewardStore.PENDING, 1000)).size();
    }

    @Test
    void theSamePacketIsStoredOnce() {
        VoteStore.Row r = row("v1", "topg", "Steve", null, 1_000, "1.2.3.4");
        assertTrue(db.<Boolean>blockingTransaction(c -> VoteStore.insert(c, r)));
        assertFalse(db.<Boolean>blockingTransaction(c -> VoteStore.insert(c, r)));
        assertEquals(VoteRules.QUEUED, db.blocking(c -> VoteStore.vote(c, "v1")).orElseThrow().state());
    }

    @Test
    void offlineAndUnknownVotesWaitForTheirPlayer() {
        long t = 10 * DAY;
        db.blockingTransaction(c -> VoteStore.insert(c, row("known", "topg", "Steve", account, t, "")));
        db.blockingTransaction(c -> VoteStore.insert(c, row("unknown", "planetminecraft", "Bedrock Guy", null, t + 1, "")));
        db.blockingTransaction(c -> VoteStore.insert(c, row("other", "topg", "Someone", null, t + 2, "")));
        // The Bedrock player ".Bedrock_Guy" joins: the site dropped the prefix, so "bedrock_guy" is theirs too.
        List<VoteStore.Row> mine = db.blocking(c -> VoteStore.queuedFor(c, account, VoteRules.joinNames(".Bedrock_Guy", ".")));
        assertEquals(List.of("known", "unknown"), mine.stream().map(VoteStore.Row::voteId).toList());
        assertEquals(2, (int) db.<Integer>blocking(c -> VoteStore.queuedCount(c, account, List.of("bedrock_guy"))));
        // Paid at the next join; afterwards nothing waits.
        for (VoteStore.Row r : mine) {
            assertEquals(ClaimEngine.Status.OK, pay(r, 0, new AtomicInteger()));
        }
        assertEquals(0, db.blocking(c -> VoteStore.queuedFor(c, account, List.of(".bedrock_guy", "bedrock_guy"))).size());
        assertEquals(2, grants());
        assertEquals(VoteRules.PAID, db.blocking(c -> VoteStore.vote(c, "unknown")).orElseThrow().state());
        assertEquals(account, db.blocking(c -> VoteStore.vote(c, "unknown")).orElseThrow().account());
    }

    @Test
    void aVoteIsPaidOnceEvenWhenTriedTwice() {
        VoteStore.Row r = row("v1", "topg", "Steve", account, 5 * DAY, "");
        db.blockingTransaction(c -> VoteStore.insert(c, r));
        assertEquals(ClaimEngine.Status.OK, pay(r, 0, new AtomicInteger()));
        assertEquals(ClaimEngine.Status.CHANGED, pay(r, 0, new AtomicInteger()));
        assertEquals(1, grants());
    }

    @Test
    void secondVoteOnTheSameSiteWithin24HoursIsNotPaid() {
        long t = 5 * DAY;
        VoteStore.Row first = row("a", "topg", "Steve", account, t, "");
        VoteStore.Row second = row("b", "topg", "Steve", account, t + 20 * VoteRules.HOUR, "");
        VoteStore.Row otherSite = row("c", "planetminecraft", "Steve", account, t + VoteRules.HOUR, "");
        VoteStore.Row nextDay = row("d", "topg", "Steve", account, t + DAY, "");
        for (VoteStore.Row r : List.of(first, second, otherSite, nextDay)) {
            db.blockingTransaction(c -> VoteStore.insert(c, r));
        }
        assertEquals(ClaimEngine.Status.OK, pay(first, 0, new AtomicInteger()));
        // The pre-read finds it ...
        List<Long> same = db.blocking(c -> VoteStore.paidTimes(c, account, "topg", second.receivedAt() - DAY + 1,
                second.receivedAt() + DAY - 1, second.voteId()));
        assertTrue(VoteRules.onCooldown(same, second.receivedAt(), DAY));
        // ... and the guard checks again inside the transaction (nothing paid).
        assertEquals(ClaimEngine.Status.CHANGED, pay(second, 0, new AtomicInteger()));
        assertTrue(db.<Boolean>blockingTransaction(c -> VoteStore.settle(c, "b", account, VoteRules.DUPLICATE, t)));
        assertEquals(ClaimEngine.Status.OK, pay(otherSite, 0, new AtomicInteger()));
        assertEquals(ClaimEngine.Status.OK, pay(nextDay, 0, new AtomicInteger()));
        assertEquals(3, grants());
        assertEquals(2, db.blocking(c -> VoteStore.lastPaidPerSite(c, account)).size());
        assertEquals(t + DAY, db.blocking(c -> VoteStore.lastPaidPerSite(c, account)).get("topg"));
    }

    @Test
    void addressLimitCountsAccountsPerDay() {
        long t = 5 * DAY;
        UUID other = UUID.randomUUID();
        db.blockingTransaction(c -> VoteStore.insert(c, row("a", "topg", "Steve", account, t, "9.9.9.9")));
        db.blockingTransaction(c -> VoteStore.insert(c, row("b", "topg", "Alex", other, t, "9.9.9.9")));
        pay(db.blocking(c -> VoteStore.vote(c, "a")).orElseThrow(), 0, new AtomicInteger());
        Set<UUID> on = db.blocking(c -> VoteStore.accountsOnAddress(c, VoteRules.addressHash("9.9.9.9"), (int) (t / DAY)));
        assertEquals(Set.of(account), on);
        assertEquals(Set.of(), db.blocking(c -> VoteStore.accountsOnAddress(c, "", (int) (t / DAY))));
    }

    @Test
    void streakMovesInsideThePayment() {
        long t = 100 * DAY;
        for (int i = 0; i < 3; i++) {
            VoteStore.Row r = row("s" + i, "topg", "Steve", account, t + i * DAY, "");
            db.blockingTransaction(c -> VoteStore.insert(c, r));
            pay(r, 0, new AtomicInteger());
        }
        assertEquals(new VoteRules.Streak(3, 102, 3), db.blocking(c -> VoteStore.streak(c, account)).orElseThrow());
        // A stale "before" can't overwrite a newer streak.
        assertFalse(db.<Boolean>blockingTransaction(c -> VoteStore.saveStreak(c, account, Optional.of(new VoteRules.Streak(1, 100, 1)),
                new VoteRules.Streak(2, 101, 2), t)));
        assertFalse(db.<Boolean>blockingTransaction(c -> VoteStore.saveStreak(c, account, Optional.empty(), new VoteRules.Streak(1, 1, 1), t)));
    }

    @Test
    void partyFillsOnceAndPaysEveryAccountOnce() {
        AtomicInteger filled = new AtomicInteger();
        int partyNumber = 0;
        for (int i = 0; i < 5; i++) {
            UUID voter = UUID.randomUUID();
            long at = 10 * DAY + i;
            int n = db.blockingTransaction(c -> VoteStore.addPartyVote(c, 3, at));
            if (n > 0) {
                partyNumber = n;
                filled.incrementAndGet();
            }
            assertTrue(voter != null);
        }
        assertEquals(1, filled.get());
        assertEquals(1, partyNumber);
        assertEquals(new VoteStore.Party(2, 1), db.blocking(VoteStore::party));
        int party = partyNumber;
        RewardStore.GrantRow g = new RewardStore.GrantRow(UUID.randomUUID(), profile, account, "vote_party", "vote_party:1", Payout.KEY,
                "blaze", 1, RewardStore.PENDING, 1);
        assertEquals(ClaimEngine.Status.OK, ClaimEngine.commit((txs, guard) -> ClaimEngine.local(db, guard), List.of(),
                VoteService.partyGuard(party, account, profile, List.of(g), 1)).join());
        RewardStore.GrantRow g2 = new RewardStore.GrantRow(UUID.randomUUID(), profile, account, "vote_party", "vote_party:1", Payout.KEY,
                "blaze", 1, RewardStore.PENDING, 1);
        assertEquals(ClaimEngine.Status.CHANGED, ClaimEngine.commit((txs, guard) -> ClaimEngine.local(db, guard), List.of(),
                VoteService.partyGuard(party, account, profile, List.of(g2), 1)).join());
        assertEquals(1, grants());
        // A stop before the party was marked paid: reviewed at the next start, once.
        assertEquals(List.of(1), db.blockingTransaction(VoteStore::reviewInterrupted));
        assertEquals(List.of(), db.blockingTransaction(VoteStore::reviewInterrupted));
    }

    @Test
    void partyCounterMovesWithEveryPaidVote() {
        AtomicInteger filled = new AtomicInteger();
        long t = 20 * DAY;
        for (int i = 0; i < 2; i++) {
            VoteStore.Row r = row("p" + i, i == 0 ? "topg" : "planetminecraft", "Steve", account, t + i, "");
            db.blockingTransaction(c -> VoteStore.insert(c, r));
            assertEquals(ClaimEngine.Status.OK, pay(r, 2, filled));
        }
        assertEquals(1, filled.get());
        assertEquals(new VoteStore.Party(0, 1), db.blocking(VoteStore::party));
    }

    @Test
    void oldVotesExpireAndArePruned() {
        long now = 30 * DAY;
        db.blockingTransaction(c -> VoteStore.insert(c, row("unknown-old", "topg", "Nobody", null, now - 8 * DAY, "")));
        db.blockingTransaction(c -> VoteStore.insert(c, row("unknown-new", "topg", "Nobody2", null, now - 6 * DAY, "")));
        db.blockingTransaction(c -> VoteStore.insert(c, row("known-old", "topg", "Steve", account, now - 8 * DAY, "")));
        int expired = db.blockingTransaction(c -> VoteStore.expire(c, now - 7 * DAY, now - 60 * DAY, now));
        assertEquals(1, expired);
        assertEquals(VoteRules.EXPIRED, db.blocking(c -> VoteStore.vote(c, "unknown-old")).orElseThrow().state());
        assertEquals(VoteRules.QUEUED, db.blocking(c -> VoteStore.vote(c, "unknown-new")).orElseThrow().state());
        assertEquals(VoteRules.QUEUED, db.blocking(c -> VoteStore.vote(c, "known-old")).orElseThrow().state());
        assertEquals(1, (int) db.<Integer>blockingTransaction(c -> VoteStore.prune(c, now)));
        assertTrue(db.blocking(c -> VoteStore.vote(c, "unknown-old")).isEmpty());
        assertTrue(db.blocking(c -> VoteStore.vote(c, "known-old")).isPresent(), "waiting votes are never pruned");
    }
}
