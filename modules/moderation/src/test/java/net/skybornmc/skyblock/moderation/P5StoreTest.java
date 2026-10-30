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
 * P5 against a real (H2, MySQL mode) database: appeals (one open per punishment, cooldown, accept lifts, reduce, deny,
 * first click wins, closed when already over, told once, /pstats counts), the request keeping its /logs evidence line,
 * and bulk punishments carrying their group.
 */
class P5StoreTest {

    private static final UUID ALEX = TestData.ALEX;
    private static final UUID BOB = UUID.fromString("00000000-0000-0000-0000-00000000000c");
    private static final long DAY = TestData.DAY;
    private static final long HOUR = TestData.HOUR;
    private static final PunishConfig.Appeals CFG = PunishConfig.Appeals.DEFAULT;

    @TempDir
    Path dir;

    private Database db;
    private PunishmentStore store;
    private final PunishDecision decision = PunishDecision.full(Duration.ofSeconds(15), Approval.Settings.DEFAULT);
    private long now;

    @BeforeEach
    void start() throws Exception {
        DatabaseConfig config = new DatabaseConfig();
        config.type = DatabaseConfig.Type.H2;
        config.h2File = "moderation-p5-test";
        config.poolSize = 8;
        config.slowQueryMs = 60_000;
        db = new Database(config, dir.toFile(), Logger.getLogger("moderation-p5-test"));
        db.start();
        new Migrations(db, Logger.getLogger("moderation-p5-test")).migrate("core", getClass().getClassLoader());
        new Migrations(db, Logger.getLogger("moderation-p5-test")).migrate("moderation", getClass().getClassLoader());
        store = new PunishmentStore(db);
        now = System.currentTimeMillis();
    }

    @AfterEach
    void stop() {
        db.close();
    }

    private static PunishDecision.Actor staff(String name, UUID id, boolean approver) {
        return new PunishDecision.Actor(name, false, Set.of(Punishment.Type.values()), true, true, null, false, id, false, approver);
    }

    private Punishment issue(UUID target, Punishment.Type type, Duration length, String template, String source, long at) {
        Punishment draft = new Punishment(0, type, target, "Steve", null, false, "Spam", template, 0, ALEX, "Alex", at, null,
                false, null, null, null, null, source);
        PunishDecision.Input in = new PunishDecision.Input(staff("Alex", ALEX, true), type, length, false, false, false, false,
                true, null, null, at);
        PunishmentStore.IssueResult r = store.issue(draft, in, decision, null, Duration.ofHours(24)).join();
        assertTrue(r.verdict().applied(), () -> r.verdict().toString());
        return r.punishment();
    }

    private Punishment mute(UUID target, Duration length, long at) {
        return issue(target, Punishment.Type.MUTE, length, "spam", null, at);
    }

    private PunishmentStore.Submitted submit(long pid, UUID player, String text, long at) {
        return store.submitAppeal(pid, player, "Steve", text, t -> !"rmt".equals(t), CFG, at).join();
    }

    private static final String TEXT = "I was answering a question, not spamming.";

    // ================================================================ 13.10 appeals

    @Test
    void appealIsStoredOncePerPunishment() {
        UUID steve = UUID.randomUUID();
        Punishment m = mute(steve, Duration.ofDays(1), now);
        PunishmentStore.Submitted ok = submit(m.id(), steve, TEXT, now + 1);
        assertNull(ok.problem());
        Appeal a = ok.appeal();
        assertEquals(Appeal.Status.OPEN, a.status());
        assertEquals(m.id(), a.punishmentId());
        assertEquals(Punishment.Type.MUTE, a.punishmentType());
        assertEquals(ALEX, a.issuer(), "who gave the punishment");
        assertEquals(TEXT, a.text());
        assertEquals(AppealRules.Problem.ALREADY_OPEN, submit(m.id(), steve, TEXT, now + 2).problem());
        assertEquals(AppealRules.Problem.NOT_YOURS, submit(m.id(), UUID.randomUUID(), TEXT, now + 3).problem());
        assertEquals(AppealRules.Problem.NOT_FOUND, submit(999_999, steve, TEXT, now + 4).problem());
        assertEquals(List.of(a.id()), store.openAppeals(50).join().stream().map(Appeal::id).filter(id -> id == a.id()).toList());

        Punishment rmt = issue(steve, Punishment.Type.BAN, Duration.ofDays(30), "rmt", null, now + 5);
        assertEquals(AppealRules.Problem.REASON, submit(rmt.id(), steve, TEXT, now + 6).problem());
        Punishment warn = issue(steve, Punishment.Type.WARN, null, "warn-other", null, now + 7);
        assertEquals(AppealRules.Problem.TYPE, submit(warn.id(), steve, TEXT, now + 8).problem());
    }

    @Test
    void twoQuickAppealsCreateOnlyOneOpenAppeal() throws Exception {
        UUID steve = UUID.randomUUID();
        Punishment m = mute(steve, Duration.ofDays(1), now);
        ExecutorService pool = Executors.newFixedThreadPool(6);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<PunishmentStore.Submitted>> runs = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            runs.add(pool.submit(() -> {
                go.await();
                return submit(m.id(), steve, TEXT, now + 1);
            }));
        }
        go.countDown();
        int created = 0;
        for (Future<PunishmentStore.Submitted> f : runs) {
            if (f.get().appeal() != null) {
                created++;
            }
        }
        pool.shutdown();
        assertEquals(1, created);
        assertEquals(1, store.appealsFor(m.id()).join().size());
    }

    @Test
    void acceptLiftsThePunishmentFirstClickWinsAndThePlayerIsToldOnce() {
        UUID steve = UUID.randomUUID();
        Punishment m = mute(steve, Duration.ofDays(1), now);
        Appeal a = submit(m.id(), steve, TEXT, now + 1).appeal();
        PunishmentStore.AppealResult r = store.acceptAppeal(a.id(), BOB, "Bob", "Appeal accepted", now + 10).join();
        assertEquals(PunishmentStore.AppealKind.ACCEPTED, r.kind());
        assertEquals(Appeal.Status.ACCEPTED, r.appeal().status());
        assertEquals("Bob", r.appeal().handlerName());
        assertTrue(r.punishment().removed());
        Punishment stored = store.history(steve).join().getFirst();
        assertEquals("Appeal accepted", stored.removedReason());
        assertEquals("Bob", stored.removedByName());
        assertFalse(stored.active(now + 11), "lifted");
        assertTrue(store.activeMute(steve, null).join().isEmpty());

        assertEquals(PunishmentStore.AppealKind.ALREADY_HANDLED,
                store.denyAppeal(a.id(), ALEX, "Alex", "no", now + 12).join().kind(), "first click wins");
        assertEquals(PunishmentStore.AppealKind.ALREADY_HANDLED,
                store.acceptAppeal(a.id(), ALEX, "Alex", "Appeal accepted", now + 12).join().kind());

        List<Appeal> told = store.appealUpdates(steve).join();
        assertEquals(List.of(a.id()), told.stream().map(Appeal::id).toList());
        assertTrue(store.appealUpdates(steve).join().isEmpty(), "told once (now or on the next join)");
        assertFalse(store.markAppealNotified(a.id()).join(), "already told");
    }

    @Test
    void reduceShortensAndKeepsTheOldEnd() {
        UUID steve = UUID.randomUUID();
        Punishment m = mute(steve, Duration.ofDays(7), now);
        Appeal a = submit(m.id(), steve, TEXT, now + 1).appeal();
        assertEquals(PunishmentStore.AppealKind.NOT_SHORTER, store.reduceAppeal(a.id(), BOB, "Bob", AppealRules.Unit.DAYS, 7,
                "Reduced to 7d", now + 2).join().kind());
        assertTrue(store.appeal(a.id()).join().orElseThrow().open(), "nothing changed");
        PunishmentStore.AppealResult r = store.reduceAppeal(a.id(), BOB, "Bob", AppealRules.Unit.DAYS, 2, "Reduced to 2d",
                now + 3).join();
        assertEquals(PunishmentStore.AppealKind.REDUCED, r.kind());
        assertEquals(m.created() + 2 * DAY, r.punishment().expires());
        assertEquals(m.expires(), r.appeal().oldExpires());
        assertEquals(m.created() + 2 * DAY, r.appeal().newExpires());
        assertEquals("Reduced to 2d", r.appeal().response());
        assertEquals(m.created() + 2 * DAY, store.activeMute(steve, null).join().orElseThrow().expires());
        assertEquals(AppealRules.Problem.COOLDOWN, submit(m.id(), steve, TEXT, now + 4).problem(),
                "a reduced appeal starts the cooldown too");
    }

    @Test
    void denyStartsTheCooldown() {
        UUID steve = UUID.randomUUID();
        Punishment ban = issue(steve, Punishment.Type.BAN, null, "hacking", null, now);
        Appeal a = submit(ban.id(), steve, TEXT, now + 1).appeal();
        PunishmentStore.AppealResult r = store.denyAppeal(a.id(), BOB, "Bob", "The evidence is clear", now + 2).join();
        assertEquals(PunishmentStore.AppealKind.DENIED, r.kind());
        assertEquals("The evidence is clear", r.appeal().response());
        assertTrue(store.activeBan(steve, null).join().isPresent(), "a deny changes nothing");
        assertEquals(AppealRules.Problem.COOLDOWN, submit(ban.id(), steve, TEXT, now + 3).problem());
        assertNull(submit(ban.id(), steve, TEXT, now + 2 + Duration.ofDays(7).toMillis()).problem(), "after 7 days");
    }

    @Test
    void appealOfAPunishmentThatEndedIsClosed() {
        UUID steve = UUID.randomUUID();
        Punishment m = mute(steve, Duration.ofHours(1), now);
        Appeal a = submit(m.id(), steve, TEXT, now + 1).appeal();
        store.pardon(Punishment.Type.MUTE, steve, null, ALEX, "Alex", "Mistake", false).join();
        PunishmentStore.AppealResult r = store.acceptAppeal(a.id(), BOB, "Bob", "Appeal accepted", now + 2).join();
        assertEquals(PunishmentStore.AppealKind.CLOSED, r.kind());
        assertEquals(Appeal.Status.CLOSED, r.appeal().status());
        assertEquals("Mistake", store.history(steve).join().getFirst().removedReason(), "the pardon is kept");

        Punishment over = mute(steve, Duration.ofHours(1), now - 3 * HOUR);
        assertEquals(AppealRules.Problem.NOT_ACTIVE, submit(over.id(), steve, TEXT, now).problem());
    }

    @Test
    void appealNumbersForPstats() {
        UUID steve = UUID.randomUUID();
        Appeal a1 = submit(mute(steve, Duration.ofDays(1), now).id(), steve, TEXT, now).appeal();
        store.acceptAppeal(a1.id(), BOB, "Bob", "Appeal accepted", now + 1).join();
        UUID other = UUID.randomUUID();
        Appeal a2 = submit(issue(other, Punishment.Type.BAN, null, "hacking", null, now).id(), other, TEXT, now).appeal();
        store.denyAppeal(a2.id(), BOB, "Bob", "no", now + 2).join();
        UUID third = UUID.randomUUID();
        Appeal a3 = submit(mute(third, Duration.ofDays(7), now).id(), third, TEXT, now).appeal();
        store.reduceAppeal(a3.id(), BOB, "Bob", AppealRules.Unit.DAYS, 1, "Reduced to 1d", now + 3).join();

        PunishmentStore.AppealCounts bob = store.appealCounts(BOB, now - DAY).join();
        assertEquals(new PunishmentStore.AppealCounts(1, 1, 1, 0), bob, "handled appeals count for the handler");
        assertEquals(3, bob.handled());
        PunishmentStore.AppealCounts alex = store.appealCounts(ALEX, now - DAY).join();
        assertEquals(1, alex.reversedByAppeal(), "an accepted appeal counts as reversed for who gave it");
        assertEquals(0, alex.handled());
        assertEquals(PunishmentStore.AppealCounts.NONE, store.appealCounts(BOB, now + DAY).join(), "period filter");
    }

    @Test
    void oldOpenAppealsForTheReminder() {
        UUID steve = UUID.randomUUID();
        Appeal old = submit(mute(steve, Duration.ofDays(5), now - 3 * DAY).id(), steve, TEXT, now - 3 * DAY).appeal();
        UUID other = UUID.randomUUID();
        submit(mute(other, Duration.ofDays(5), now).id(), other, TEXT, now);
        List<Long> due = store.openAppealsBefore(now - Duration.ofHours(48).toMillis(), 50).join().stream().map(Appeal::id).toList();
        assertTrue(due.contains(old.id()));
        assertEquals(1, due.size());
        assertEquals(1, store.appealsBy(steve, 10).join().size());
    }

    // ================================================================ 13.11 a request keeps its /logs line

    @Test
    void requestKeepsTheLogEvidenceLine() {
        UUID steve = UUID.randomUUID();
        PunishDecision.Actor requester = new PunishDecision.Actor("Alex", false, Set.of(Punishment.Type.values()), true, true,
                Duration.ofDays(30), false, ALEX, false, false);
        Punishment draft = new Punishment(0, Punishment.Type.BAN, steve, "Steve", null, false, "Advertising", "advertising", 0,
                ALEX, "Alex", now, null, false, null, null, null, null, null);
        PunishDecision.Input in = new PunishDecision.Input(requester, Punishment.Type.BAN, null, false, false, false, false, true,
                null, null, now).withReason("advertising", false, false);
        PunishmentStore.IssueResult first = store.issue(draft, in, decision, null, Duration.ofHours(24), 4242L).join();
        assertEquals(PunishDecision.Outcome.REQUEST, first.verdict().outcome());
        assertEquals(4242L, first.request().evidenceEntry());
        PunishmentStore.IssueResult merged = store.issue(draft, in, decision, null, Duration.ofHours(24), 7777L).join();
        assertTrue(merged.merged());
        assertEquals(4242L, merged.request().evidenceEntry(), "the first line stays");

        UUID other = UUID.randomUUID();
        Punishment d2 = new Punishment(0, Punishment.Type.BAN, other, "Bob", null, false, "Advertising", "advertising", 0, ALEX,
                "Alex", now, null, false, null, null, null, null, null);
        PunishDecision.Input in2 = new PunishDecision.Input(requester, Punishment.Type.BAN, null, false, false, false, false, true,
                null, null, now).withReason("advertising", false, false);
        PunishmentStore.IssueResult plain = store.issue(d2, in2, decision, null, Duration.ofHours(24)).join();
        assertNull(plain.request().evidenceEntry());
        PunishmentStore.IssueResult filled = store.issue(d2, in2, decision, null, Duration.ofHours(24), 99L).join();
        assertEquals(99L, filled.request().evidenceEntry(), "a merged request fills it in when there was none");
    }

    // ================================================================ 13.8 bulk: one group, counted per player

    @Test
    void bulkPunishmentsCarryTheirGroupAndCountPerPlayer() {
        String group = BulkSelection.group(now, 1);
        List<Punishment> applied = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            applied.add(issue(UUID.randomUUID(), Punishment.Type.MUTE, Duration.ofMinutes(30), "spam", group, now + i));
        }
        for (Punishment p : applied) {
            Punishment stored = store.history(p.target()).join().getFirst();
            assertEquals(group, stored.source());
            assertFalse(stored.automatic(), "a bulk action is a staff action, not an automatic rule");
        }
        List<PunishStats.Row> rows = store.statRows(ALEX, now - DAY).join();
        PunishStats.Counts c = PunishStats.Counts.of(rows);
        assertEquals(3, c.mutes(), "each punishment counts in /pstats per player");
        assertEquals(0, c.fromSuggestions());
        // [Undo all] uses the normal undo per punishment
        PunishmentStore.UndoResult u = store.undo(applied.getFirst().id(), ALEX, "Alex", false, Duration.ofMinutes(2),
                "Undone by Alex (mistake)", now + 10).join();
        assertEquals(Undo.Result.OK, u.result());
        assertNotNull(u.punishment());
    }
}
