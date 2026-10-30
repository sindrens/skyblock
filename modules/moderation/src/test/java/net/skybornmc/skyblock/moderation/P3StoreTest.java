package net.skybornmc.skyblock.moderation;

import net.skybornmc.skyblock.common.db.Database;
import net.skybornmc.skyblock.common.db.DatabaseConfig;
import net.skybornmc.skyblock.common.db.Migrations;
import net.skybornmc.skyblock.common.db.Sql;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P3 against a real (H2, MySQL mode) database: weights stored and counted (12.7), evidence saved/read/purged (12.4),
 * notes add/list/remove kept (12.5), acknowledgement + the one join reminder (12.6), the suggestion API dedupe and
 * "never applies" (12.8).
 */
class P3StoreTest {

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
        config.h2File = "moderation-p3-test";
        config.poolSize = 8;
        config.slowQueryMs = 60_000;
        db = new Database(config, dir.toFile(), Logger.getLogger("moderation-p3-test"));
        db.start();
        new Migrations(db, Logger.getLogger("moderation-p3-test")).migrate("core", getClass().getClassLoader());
        new Migrations(db, Logger.getLogger("moderation-p3-test")).migrate("moderation", getClass().getClassLoader());
        store = new PunishmentStore(db);
        now = System.currentTimeMillis();
    }

    @AfterEach
    void stop() {
        db.close();
    }

    private static PunishDecision.Actor staff(String name, UUID id) {
        return new PunishDecision.Actor(name, false, Set.of(Punishment.Type.values()), true, true, null, false, id, false);
    }

    private PunishmentStore.RuleHook hook(long at) {
        PunishDecision.Input template = new PunishDecision.Input(PunishDecision.Actor.system("System (auto)"), Punishment.Type.MUTE,
                null, false, false, false, false, false, null, null, at);
        return new PunishmentStore.RuleHook(TestData.rules(WarnRules.Mode.NOTIFY, true), decision, template, "System (auto)",
                t -> "Automatic rule: " + t.ruleAt() + " warnings");
    }

    private PunishmentStore.IssueResult warn(UUID target, int weight, long at) {
        PunishDecision.Actor actor = staff("Bob", BOB);
        Punishment draft = new Punishment(0, Punishment.Type.WARN, target, "Steve", null, false, "test", null, null, actor.id(),
                actor.name(), at, null, false, null, null, null, null, null, weight, null);
        PunishDecision.Input in = new PunishDecision.Input(actor, Punishment.Type.WARN, null, false, false, false, false, true,
                null, null, at);
        return store.issue(draft, in, decision, hook(at)).join();
    }

    // ================================================================ 12.7 weights (item 8)

    @Test
    void weightsAreStoredAndTheRuleFiresAtTheWeightedSum() {
        UUID steve = UUID.randomUUID();
        assertNull(warn(steve, 5, now).trigger(), "0.5");
        assertNull(warn(steve, 5, now + 1).trigger(), "0.5 + 0.5 = 1");
        PunishmentStore.IssueResult third = warn(steve, 10, now + 2);
        assertNotNull(third.trigger(), "0.5 + 0.5 + 1 = 2 fires the rule at 2");
        assertEquals(20, third.trigger().trigger().tenths());
        Suggestion s = store.suggestion(third.trigger().suggestion().id()).join().orElseThrow();
        assertEquals(20, s.warnTenths());
        assertEquals("2", s.countText());
        List<Punishment> history = store.history(steve).join();
        assertEquals(List.of(10, 5, 5), history.stream().map(Punishment::weight).toList(), "read back newest first");
        // a mute never stores a weight; a row without one counts 1
        db.blocking(c -> Sql.update(c, "UPDATE punishments SET weight = NULL WHERE target_uuid = ?", Sql.toBytes(steve)));
        assertTrue(store.history(steve).join().stream().allMatch(p -> p.weight() == Weights.ONE));
    }

    // ================================================================ 12.4 evidence (item 5)

    @Test
    void evidenceIsSavedReadAndDeletedAfterTheRetention() {
        UUID steve = UUID.randomUUID();
        Punishment w = warn(steve, 10, now).punishment();
        store.saveEvidence(w.id(), now, List.of("[10:00:00] Steve: hi", "[10:00:01] Steve: [IP hidden]")).join();
        assertEquals(List.of("[10:00:00] Steve: hi", "[10:00:01] Steve: [IP hidden]"), store.evidence(w.id()).join());
        assertEquals(Set.of(w.id()), store.withEvidence(steve).join());
        Punishment old = warn(steve, 10, now + 1).punishment();
        long longAgo = now - Duration.ofDays(181).toMillis();
        store.saveEvidence(old.id(), longAgo, List.of("old line")).join();
        assertEquals(1, store.purgeEvidence(now - Duration.ofDays(180).toMillis()).join(), "only the 181-day-old one");
        assertEquals(List.of(), store.evidence(old.id()).join());
        assertEquals(2, store.evidence(w.id()).join().size(), "the young one stays");
        assertEquals(List.of(), store.evidence(999_999).join(), "no evidence = empty");
    }

    // ================================================================ 12.5 notes (item 6)

    @Test
    void notesAreAddedListedAndRemovedButKept() {
        UUID steve = UUID.randomUUID();
        Notes.Note a = store.addNote(steve, "Steve", BOB, "Bob", "Watch for alts", now).join();
        Notes.Note b = store.addNote(steve, "Steve", null, "CONSOLE", "Second", now + 1).join();
        assertEquals(List.of(b.id(), a.id()), store.notes(steve, 10).join().stream().map(Notes.Note::id).toList());
        assertEquals(2, store.noteCount(steve).join());
        PunishmentStore.NoteRemoval r = store.removeNote(a.id(), TestData.ALEX, "Alex", now + 2).join().orElseThrow();
        assertTrue(r.changed());
        assertEquals("Watch for alts", r.note().text());
        assertEquals(List.of(b.id()), store.notes(steve, 10).join().stream().map(Notes.Note::id).toList());
        Notes.Note kept = store.note(a.id()).join().orElseThrow();
        assertTrue(kept.removed(), "kept in the database as removed");
        assertEquals("Alex", kept.removedByName());
        assertEquals(TestData.ALEX, kept.removedBy());
        assertFalse(store.removeNote(a.id(), BOB, "Bob", now + 3).join().orElseThrow().changed(), "already removed");
        assertTrue(store.removeNote(999_999, BOB, "Bob", now).join().isEmpty());
        assertEquals(List.of(String.valueOf(b.id())), store.recentNoteIds(steve).join());
        assertEquals(1, store.notes(steve, 1).join().size(), "limit");
    }

    // ================================================================ 12.6 acknowledgement (item 7)

    @Test
    void ackMarksOpenWarningsAndTheJoinReminderIsSentOnce() {
        UUID steve = UUID.randomUUID();
        Punishment w1 = warn(steve, 10, now).punishment();
        Punishment w2 = warn(steve, 10, now + 1).punishment();
        long since = now - Duration.ofDays(30).toMillis();
        PunishmentStore.JoinAck first = store.joinAck(steve, since).join();
        assertEquals(2, first.owing().size());
        assertEquals(2, first.remindNow().size(), "first join: remind");
        PunishmentStore.JoinAck second = store.joinAck(steve, since).join();
        assertEquals(2, second.owing().size(), "still owed");
        assertTrue(second.remindNow().isEmpty(), "but only one join reminder");

        List<Long> acked = store.acknowledge(steve, now + 10).join();
        assertEquals(Set.of(w1.id(), w2.id()), Set.copyOf(acked));
        assertTrue(store.history(steve).join().stream().allMatch(p -> p.acknowledged() && p.acknowledgedAt() == now + 10));
        assertTrue(store.acknowledge(steve, now + 20).join().isEmpty(), "nothing left to confirm");
        assertTrue(store.joinAck(steve, since).join().owing().isEmpty());

        // a pardoned warning is never owed
        UUID alex = UUID.randomUUID();
        warn(alex, 10, now);
        store.pardon(Punishment.Type.WARN, alex, null, BOB, "Bob", "mistake", true).join();
        assertTrue(store.joinAck(alex, since).join().owing().isEmpty());
        assertTrue(store.acknowledge(alex, now).join().isEmpty());
    }

    // ================================================================ 12.8 suggestion API (item 9)

    @Test
    void apiSuggestionIsDedupedPerTargetAndReasonAndNeverApplies() throws Exception {
        UUID steve = UUID.randomUUID();
        Templates.Step mute = Templates.Step.parse("mute 30m");
        Long expires = now + Duration.ofHours(24).toMillis();
        PunishmentStore.ApiSuggestion a = store.suggestApi(steve, "Steve", "spam", mute, "Spam / flooding",
                "chat filter: 3 spam hits in 10s", now, expires).join();
        assertFalse(a.refreshed());
        PunishmentStore.ApiSuggestion b = store.suggestApi(steve, "Steve", "spam", mute, "Spam / flooding",
                "chat filter: 5 spam hits in 10s", now + 1000, expires + 1000).join();
        assertTrue(b.refreshed(), "same target + reason: refreshed");
        assertEquals(a.suggestion().id(), b.suggestion().id());
        assertEquals("chat filter: 5 spam hits in 10s", b.suggestion().details());
        assertEquals(1, store.apiSuggestions(steve).join().size(), "never duplicated");
        PunishmentStore.ApiSuggestion other = store.suggestApi(steve, "Steve", "advertising", Templates.Step.parse("mute 1d"),
                "Advertising", "chat filter: link", now, expires).join();
        assertFalse(other.refreshed(), "another reason is its own suggestion");
        assertEquals(2, store.apiSuggestions(steve).join().size());
        assertTrue(store.history(steve).join().isEmpty(), "a suggestion never applies by itself");
        assertTrue(store.suggestions(steve).join().isEmpty(), "API suggestions never feed the warn rules");
        assertTrue(store.pendingIds().join().contains(String.valueOf(a.suggestion().id())), "[Apply]/[Dismiss] by id");

        // two detectors at the same moment: still one row
        UUID alex = UUID.randomUUID();
        ExecutorService pool = Executors.newFixedThreadPool(4);
        CountDownLatch go = new CountDownLatch(1);
        List<Callable<PunishmentStore.ApiSuggestion>> calls = new java.util.ArrayList<>();
        for (int i = 0; i < 4; i++) {
            calls.add(() -> {
                go.await();
                return store.suggestApi(alex, "Alex", "spam", mute, "Spam", "x", now, expires).join();
            });
        }
        List<Future<PunishmentStore.ApiSuggestion>> futures = new java.util.ArrayList<>();
        for (Callable<PunishmentStore.ApiSuggestion> c : calls) {
            futures.add(pool.submit(c));
        }
        go.countDown();
        int created = 0;
        for (Future<PunishmentStore.ApiSuggestion> f : futures) {
            if (!f.get().refreshed()) {
                created++;
            }
        }
        pool.shutdown();
        assertEquals(1, created);
        assertEquals(1, store.apiSuggestions(alex).join().size());

        // [Apply]: the punishment counts as an offence of that reason (template = reason id)
        PunishDecision.Input in = new PunishDecision.Input(staff("Bob", BOB), Punishment.Type.MUTE, Duration.ofMinutes(30), false,
                false, false, false, true, null, null, now + 2000);
        PunishmentStore.HandleResult applied = store.apply(a.suggestion().id(), s -> in, decision, BOB, "Bob", now + 2000).join();
        assertEquals(PunishmentStore.HandleKind.APPLIED, applied.kind());
        assertEquals("spam", applied.punishment().template());
        assertTrue(applied.punishment().automatic(), "counts as applied from a suggestion in /pstats");
        assertEquals(1, new PunishMenus.Target(steve, "Steve", store.history(steve).join()).previous("spam", Long.MIN_VALUE));
        PunishmentStore.ApiSuggestion again = store.suggestApi(steve, "Steve", "spam", mute, "Spam", "y", now + 3000,
                expires).join();
        assertFalse(again.refreshed(), "after it was handled, a new one may be suggested");
    }
}
