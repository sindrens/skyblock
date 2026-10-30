package net.skybornmc.skyblock.moderation;

import net.skybornmc.skyblock.common.db.Database;
import net.skybornmc.skyblock.common.db.DatabaseConfig;
import net.skybornmc.skyblock.common.db.Migrations;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P2 against a real (H2, MySQL mode) database: spec 12.14 items 1 (undo), 2 (duplicate protection, two clicks in the
 * same tick), 3 (never shorten) and 11 (stats queries: per type, reversed, system excluded, suggestions, periods).
 */
class P2StoreTest {

    private static final Duration UNDO = Duration.ofMinutes(2);
    private static final UUID BOB = UUID.fromString("00000000-0000-0000-0000-00000000000c");

    @TempDir
    Path dir;

    private Database db;
    private PunishmentStore store;
    private final PunishDecision decision = PunishDecision.withDuplicates(Duration.ofSeconds(15));
    private long now;

    @BeforeEach
    void start() throws Exception {
        DatabaseConfig config = new DatabaseConfig();
        config.type = DatabaseConfig.Type.H2;
        config.h2File = "moderation-p2-test";
        config.poolSize = 8;
        config.slowQueryMs = 60_000;
        db = new Database(config, dir.toFile(), Logger.getLogger("moderation-p2-test"));
        db.start();
        new Migrations(db, Logger.getLogger("moderation-p2-test")).migrate("core", getClass().getClassLoader());
        new Migrations(db, Logger.getLogger("moderation-p2-test")).migrate("moderation", getClass().getClassLoader());
        store = new PunishmentStore(db);
        now = System.currentTimeMillis();
    }

    @AfterEach
    void stop() {
        db.close();
    }

    // ---------------------------------------------------------------- helpers

    private static PunishDecision.Actor staff(String name, UUID id) {
        return new PunishDecision.Actor(name, false, Set.of(Punishment.Type.values()), true, true, null, false, id, false);
    }

    private PunishmentStore.RuleHook hook(long at) {
        PunishDecision.Input template = new PunishDecision.Input(PunishDecision.Actor.system("System (auto)"), Punishment.Type.MUTE,
                null, false, false, false, false, false, null, null, at);
        return new PunishmentStore.RuleHook(TestData.rules(WarnRules.Mode.NOTIFY, true), decision, template, "System (auto)",
                t -> "Automatic rule: " + t.ruleAt() + " warnings");
    }

    private PunishmentStore.IssueResult issue(PunishDecision.Actor actor, UUID target, Punishment.Type type, Duration duration,
                                              String reasonKey, long at, String source, boolean rules) {
        Punishment draft = new Punishment(0, type, target, "Steve", null, false, reasonKey == null ? "test" : reasonKey,
                reasonKey, reasonKey == null ? null : 0, actor.id(), actor.name(), at, null, false, null, null, null, null, source);
        PunishDecision.Input in = new PunishDecision.Input(actor, type, duration, false, false, false, false, true, null, null, at)
                .withReason(reasonKey, false, false);
        return store.issue(draft, in, decision, rules ? hook(at) : null).join();
    }

    private PunishmentStore.IssueResult warn(PunishDecision.Actor actor, UUID target, String reasonKey, long at) {
        return issue(actor, target, Punishment.Type.WARN, null, reasonKey, at, null, true);
    }

    // ================================================================ 12.1 undo (item 1)

    @Test
    void undoWithinTheWindowPardonsAndAfterItDoesNot() {
        UUID steve = UUID.randomUUID();
        Punishment mute = issue(staff("Bob", BOB), steve, Punishment.Type.MUTE, Duration.ofHours(1), "spam", now, null, false)
                .punishment();
        PunishmentStore.UndoResult late = store.undo(mute.id(), BOB, "Bob", false, UNDO, "Undone by Bob (mistake)",
                now + UNDO.toMillis() + 1).join();
        assertEquals(Undo.Result.EXPIRED, late.result());
        assertEquals(Undo.Result.NOT_YOURS, store.undo(mute.id(), TestData.ALEX, "Alex", false, UNDO, "x", now + 1000).join().result());

        PunishmentStore.UndoResult ok = store.undo(mute.id(), BOB, "Bob", false, UNDO, "Undone by Bob (mistake)", now + 1000).join();
        assertEquals(Undo.Result.OK, ok.result());
        Punishment after = store.history(steve).join().getFirst();
        assertTrue(after.removed());
        assertEquals("Undone by Bob (mistake)", after.removedReason());
        assertTrue(store.activeMute(steve, null).join().isEmpty(), "lifted at once");
        assertEquals(Undo.Result.ALREADY_REMOVED, store.undo(mute.id(), BOB, "Bob", false, UNDO, "x", now + 2000).join().result());
        assertEquals(Undo.Result.NOT_FOUND, store.undo(999_999, BOB, "Bob", true, UNDO, "x", now).join().result());

        // undone = not an offence for the ladder
        PunishMenus.Target target = new PunishMenus.Target(steve, "Steve", store.history(steve).join());
        assertEquals(0, target.previous("spam", Long.MIN_VALUE));
    }

    @Test
    void undoOfAWarnRemovesItsPendingSuggestion() {
        UUID steve = UUID.randomUUID();
        warn(staff("Alex", TestData.ALEX), steve, null, now);
        PunishmentStore.IssueResult second = warn(staff("Bob", BOB), steve, null, now + 1);
        assertNotNull(second.trigger(), "2 warns → pending suggestion");
        Suggestion pending = second.trigger().suggestion();
        assertEquals(Suggestion.Status.PENDING, pending.status());

        PunishmentStore.UndoResult r = store.undo(second.punishment().id(), BOB, "Bob", false, UNDO, "Undone by Bob (mistake)",
                now + 5_000).join();
        assertEquals(Undo.Result.OK, r.result());
        assertEquals(List.of(pending.id()), r.removedSuggestions());
        assertTrue(store.suggestions(steve).join().isEmpty(), "the removed suggestion is gone for the rules and /warns");
        assertEquals(Suggestion.Status.REMOVED, store.suggestion(pending.id()).join().orElseThrow().status());
        assertTrue(store.pendingIds().join().stream().noneMatch(id -> id.equals(String.valueOf(pending.id()))));

        WarnRules.Settings rules = TestData.rules(WarnRules.Mode.NOTIFY, true);
        WarnRules.Counter counter = WarnRules.counter(store.history(steve).join(), store.suggestions(steve).join(), rules,
                now + 6_000);
        assertEquals(1, counter.count(), "counter updates: one warn left");
        assertNull(counter.pending());
        assertNull(counter.repeatUntil(), "a removed suggestion never opens the repeat window");

        // the next warn fires the rule again (the removed one counts as never fired)
        PunishmentStore.IssueResult again = warn(staff("Bob", BOB), steve, null, now + 7_000);
        assertNotNull(again.trigger());
        assertEquals(Suggestion.Status.PENDING, again.trigger().suggestion().status());
        // [Apply] on the removed one does nothing
        PunishmentStore.HandleResult click = store.apply(pending.id(), s -> new PunishDecision.Input(staff("Bob", BOB),
                Punishment.Type.MUTE, Duration.ofHours(1), false, false, false, false, true, null, null, now + 8_000),
                decision, BOB, "Bob", now + 8_000).join();
        assertEquals(PunishmentStore.HandleKind.ALREADY_HANDLED, click.kind());
    }

    // ================================================================ 12.2 duplicates (items 2, 3)

    @Test
    void secondStaffIsBlockedInsideTheWindowAndAllowedAfter() {
        UUID steve = UUID.randomUUID();
        assertTrue(issue(staff("Alex", TestData.ALEX), steve, Punishment.Type.WARN, null, "warn-language", now, null, false)
                .verdict().applied());
        PunishmentStore.IssueResult blocked = issue(staff("Bob", BOB), steve, Punishment.Type.WARN, null, "warn-language",
                now + 6_000, null, false);
        assertEquals("duplicate", blocked.verdict().stage());
        assertNull(blocked.punishment());
        assertTrue(issue(staff("Bob", BOB), steve, Punishment.Type.WARN, null, "warn-other", now + 6_000, null, false)
                .verdict().applied(), "another reason is fine");
        assertTrue(issue(staff("Bob", BOB), steve, Punishment.Type.WARN, null, "warn-language", now + 16_000, null, false)
                .verdict().applied(), "after 15s");
        assertEquals(3, store.history(steve).join().size());
    }

    @Test
    void clickAgainAndForceGoThrough() {
        UUID steve = UUID.randomUUID();
        issue(staff("Alex", TestData.ALEX), steve, Punishment.Type.WARN, null, "warn-rules", now, null, false);
        PunishDecision.Actor bob = staff("Bob", BOB);
        Punishment draft = new Punishment(0, Punishment.Type.WARN, steve, "Steve", null, false, "x", "warn-rules", null, BOB,
                "Bob", now + 1000, null, false, null, null, null, null, null);
        PunishDecision.Input confirmed = new PunishDecision.Input(bob, Punishment.Type.WARN, null, false, false, false, false,
                true, null, null, now + 1000).withReason("warn-rules", false, true);
        assertTrue(store.issue(draft, confirmed, decision, null).join().verdict().applied());
        PunishDecision.Actor forcer = new PunishDecision.Actor("Owner", false, Set.of(Punishment.Type.values()), true, true, null,
                false, UUID.randomUUID(), true);
        PunishDecision.Input forced = new PunishDecision.Input(forcer, Punishment.Type.WARN, null, false, false, false, false,
                true, null, null, now + 2000).withReason("warn-rules", true, false);
        assertTrue(store.issue(draft, forced, decision, null).join().verdict().applied());
        assertEquals(3, store.history(steve).join().size());
    }

    @Test
    void twoClicksInTheSameTickApplyOnce() throws Exception {
        UUID steve = UUID.randomUUID();
        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<PunishmentStore.IssueResult>> results = new ArrayList<>();
        try {
            for (int i = 0; i < threads; i++) {
                PunishDecision.Actor actor = staff("Staff" + i, UUID.randomUUID());
                Callable<PunishmentStore.IssueResult> task = () -> {
                    go.await();
                    return issue(actor, steve, Punishment.Type.WARN, null, "warn-language", now, null, false);
                };
                results.add(pool.submit(task));
            }
            go.countDown();
            int applied = 0;
            for (Future<PunishmentStore.IssueResult> f : results) {
                if (f.get().verdict().applied()) {
                    applied++;
                }
            }
            assertEquals(1, applied, "only one of the simultaneous clicks applies");
            assertEquals(1, store.history(steve).join().size());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void neverShortensAnActiveLongerMute() {
        UUID steve = UUID.randomUUID();
        issue(staff("Alex", TestData.ALEX), steve, Punishment.Type.MUTE, Duration.ofDays(3), null, now, null, false);
        PunishmentStore.IssueResult shorter = issue(staff("Bob", BOB), steve, Punishment.Type.MUTE, Duration.ofHours(1), "toxicity",
                now + 60_000, null, false);
        assertEquals(PunishDecision.Outcome.SKIPPED, shorter.verdict().outcome());
        assertEquals("never-shorten", shorter.verdict().stage());
        assertEquals(1, store.history(steve).join().size());
    }

    // ================================================================ 12.10 stats queries (item 11)

    @Test
    void statsCountPerTypeReversedAndSuggestionsButNeverTheSystem() {
        PunishDecision.Actor bob = staff("Bob", BOB);
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();
        issue(bob, a, Punishment.Type.WARN, null, null, now - 1000, null, false);
        issue(bob, b, Punishment.Type.WARN, null, null, now - 1000, null, false);
        Punishment mute = issue(bob, c, Punishment.Type.MUTE, Duration.ofHours(1), null, now - 1000, null, false).punishment();
        issue(bob, a, Punishment.Type.MUTE, Duration.ofHours(1), null, now - 500, "auto:warn-rule:2", false); // applied suggestion
        issue(bob, b, Punishment.Type.BAN, Duration.ofDays(1), null, now - 10 * TestData.DAY, null, false); // 10 days ago
        issue(staff("Alex", TestData.ALEX), c, Punishment.Type.KICK, null, null, now - 1000, null, false);
        issue(PunishDecision.Actor.system("System (auto)"), b, Punishment.Type.MUTE, Duration.ofDays(2), null, now - 200,
                "auto:warn-rule:repeat", false);
        store.undo(mute.id(), BOB, "Bob", false, UNDO, "Undone by Bob (mistake)", now).join();

        PunishStats.Counts week = PunishStats.Counts.of(store.statRows(BOB, now - 7 * TestData.DAY).join());
        assertEquals(2, week.warns());
        assertEquals(2, week.mutes());
        assertEquals(0, week.bans(), "the 10-day-old ban is outside 7d");
        assertEquals(1, week.reversed(), "undone mute");
        assertEquals(1, week.fromSuggestions());
        assertEquals(4, week.given());
        assertEquals(25, week.reversedPercent());

        PunishStats.Counts all = PunishStats.Counts.of(store.statRows(BOB, 0).join());
        assertEquals(1, all.bans());

        PunishmentStore.TopRows top = store.topRows(0).join();
        assertEquals(Set.of(BOB, TestData.ALEX), top.rows().keySet(), "System (auto) is never a staff member");
        List<PunishStats.Ranked> ranked = PunishStats.rank(top.names(), top.rows(), 10);
        assertEquals("Bob", ranked.getFirst().name());
        assertEquals(5, ranked.getFirst().counts().given());
        assertEquals(List.of("Alex", "Bob"), store.staffNames().join());
        assertEquals(BOB, store.staffByName("bob").join().orElseThrow().getKey());
        assertTrue(store.staffByName("Nobody").join().isEmpty());
    }
}
