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
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P8 against a real (H2, MySQL mode) database: notification switches per staff member (stored, updated, loaded back),
 * a trainee's punishment is a request marked as a trainee's and nothing is applied, an island step keeps its length as a
 * request, approving applies it as the trainee, and the dry-run log never touches punishments, requests or suggestions.
 */
class P8StoreTest {

    private static final UUID ALEX = TestData.ALEX;
    private static final UUID BOB = UUID.fromString("00000000-0000-0000-0000-00000000000c");

    @TempDir
    Path dir;

    private Database db;
    private PunishmentStore store;
    private NotifyStore notify;
    private long now;
    private final PunishDecision decision = PunishDecision.full(Duration.ofSeconds(15), Approval.Settings.DEFAULT,
            Training.Settings.DEFAULT);

    @BeforeEach
    void start() throws Exception {
        DatabaseConfig config = new DatabaseConfig();
        config.type = DatabaseConfig.Type.H2;
        config.h2File = "moderation-p8-test";
        config.poolSize = 4;
        config.slowQueryMs = 60_000;
        db = new Database(config, dir.toFile(), Logger.getLogger("moderation-p8-test"));
        db.start();
        new Migrations(db, Logger.getLogger("moderation-p8-test")).migrate("core", getClass().getClassLoader());
        new Migrations(db, Logger.getLogger("moderation-p8-test")).migrate("moderation", getClass().getClassLoader());
        store = new PunishmentStore(db);
        notify = new NotifyStore(db);
        now = System.currentTimeMillis();
    }

    @AfterEach
    void stop() {
        db.close();
    }

    private static PunishDecision.Actor staff(String name, UUID id, boolean approver, boolean trainee) {
        return new PunishDecision.Actor(name, false, Set.of(Punishment.Type.values()), true, true, null, false, id, false,
                approver, trainee, false);
    }

    private PunishmentStore.IssueResult issue(UUID target, PunishDecision.Actor actor, Punishment.Type type, Duration length,
                                              String template) {
        Punishment draft = new Punishment(0, type, target, "Steve", null, false, "Spam", template, 0, actor.id(), actor.name(),
                now, null, false, null, null, null, null, null);
        PunishDecision.Input in = new PunishDecision.Input(actor, type, length, false, false, false, false, true, null, null, now);
        return store.issue(draft, in, decision, null, Duration.ofHours(24)).join();
    }

    private long count(String table) {
        return db.async(c -> Sql.queryOne(c, "SELECT COUNT(*) AS n FROM " + table, rs -> rs.getLong("n")).orElse(0L)).join();
    }

    // ================================================================ 14.9

    @Test
    void notificationSwitchesAreStoredPerStaffMember() {
        assertTrue(notify.loadAll().join().isEmpty(), "nothing stored = every type on");
        assertNull(notify.set(ALEX, NotifyRules.Type.WATCHLIST, false, now).join(), "was the default before");
        assertEquals(Boolean.FALSE, notify.set(ALEX, NotifyRules.Type.WATCHLIST, true, now + 1).join(), "update returns the old value");
        notify.set(ALEX, NotifyRules.Type.APPEALS, false, now + 2).join();
        notify.set(BOB, NotifyRules.Type.APPEALS, true, now + 3).join();
        Map<NotifyRules.Type, Boolean> alex = notify.load(ALEX).join();
        assertEquals(Map.of(NotifyRules.Type.WATCHLIST, true, NotifyRules.Type.APPEALS, false), alex);
        Map<UUID, Map<NotifyRules.Type, Boolean>> all = notify.loadAll().join();
        assertEquals(2, all.size());
        assertEquals(Boolean.TRUE, all.get(BOB).get(NotifyRules.Type.APPEALS));
        assertEquals(1, db.async(c -> Sql.queryOne(c, "SELECT COUNT(*) AS n FROM staff_notify_settings WHERE notify_type = 'watchlist'",
                rs -> rs.getLong("n")).orElse(0L)).join(), "one row per staff member and type");
        // routing with the stored values
        assertFalse(NotifyRules.receives(NotifyRules.Type.APPEALS, true, alex.get(NotifyRules.Type.APPEALS), NotifyRules.Settings.DEFAULT));
        assertTrue(NotifyRules.receives(NotifyRules.Type.RAID, true, alex.get(NotifyRules.Type.RAID), NotifyRules.Settings.DEFAULT));
    }

    // ================================================================ 14.6

    @Test
    void traineeMuteIsARequestNothingApplied() {
        UUID steve = UUID.randomUUID();
        PunishmentStore.IssueResult r = issue(steve, staff("Alex", ALEX, false, true), Punishment.Type.MUTE, Duration.ofMinutes(30), "spam");
        assertEquals(PunishDecision.Outcome.REQUEST, r.verdict().outcome());
        assertEquals("training", r.verdict().stage());
        assertTrue(r.request().requesterTrainee(), "shown as 'Alex (trainee) requests'");
        assertTrue(store.history(steve).join().isEmpty(), "the target is not punished");
        assertEquals(0, count("punishments"));

        // approved by a mentor: applied with by = the trainee
        PunishDecision.Actor mentor = staff("Bob", BOB, true, false);
        PunishmentStore.RequestResult ok = store.approve(r.request().id(), q -> new PunishDecision.Input(mentor, q.type(), q.duration(),
                q.ipWide(), q.silent(), false, false, true, null, null, now + 5), decision, BOB, "Bob", false, now + 5).join();
        assertEquals(PunishmentStore.RequestKind.APPROVED, ok.kind());
        assertEquals(ALEX, ok.punishment().issuer());
        assertEquals("Bob", ok.punishment().approvedByName());

        // a non-trainee request keeps requester_trainee false
        PunishmentStore.IssueResult normal = issue(UUID.randomUUID(), staff("Bob", BOB, false, false), Punishment.Type.BAN, null, "hacking");
        assertFalse(normal.request().requesterTrainee());
    }

    @Test
    void traineeWarnAppliesDirectly() {
        UUID steve = UUID.randomUUID();
        PunishmentStore.IssueResult r = issue(steve, staff("Alex", ALEX, false, true), Punishment.Type.WARN, null, "warn-other");
        assertEquals(PunishDecision.Outcome.APPLY, r.verdict().outcome());
        assertEquals(1, store.history(steve).join().size());
        // any approver can undo it (the [Undo] of the trainee alert: others = true, window = approval.expire)
        PunishmentStore.UndoResult undo = store.undo(r.punishment().id(), BOB, "Bob", true, Duration.ofHours(24), "Undone by Bob",
                now + 60_000).join();
        assertEquals(Undo.Result.OK, undo.result());
    }

    @Test
    void islandStepRequestKeepsItsLength() {
        UUID steve = UUID.randomUUID();
        PunishmentStore.IssueResult r = issue(steve, staff("Alex", ALEX, false, true), Punishment.Type.ISLAND_BAN,
                Duration.ofDays(1), "coop-griefing");
        assertEquals(PunishDecision.Outcome.REQUEST, r.verdict().outcome());
        assertEquals(Duration.ofDays(1).toMillis(), r.request().durationMs(), "timed island steps keep their length");
        assertEquals(Punishment.Type.ISLAND_BAN, r.request().type());
    }

    @Test
    void dryRunLogNeverTouchesRealState() {
        UUID steve = UUID.randomUUID();
        long punishments = count("punishments");
        long requests = count("punishment_requests");
        long suggestions = count("punishment_suggestions");
        // the dry run: decide with the pure pipeline (no store call), then only the training log is written
        PunishDecision.Input in = new PunishDecision.Input(staff("Alex", ALEX, false, true), Punishment.Type.BAN, Duration.ofDays(7),
                false, false, false, false, true, null, null, now);
        DryRun.Report report = DryRun.of(decision.decide(in), Templates.Step.parse("ban 7d"), 0, 0);
        assertEquals(DryRun.Outcome.REQUEST, report.outcome());
        notify.logDryRun(new NotifyStore.DryRunRow(ALEX, "Alex", true, steve, "Steve", "exploiting", "REQUEST",
                report.step().describe(), now)).join();
        notify.logDryRun(new NotifyStore.DryRunRow(ALEX, "Alex", true, steve, "Steve", "spam", "APPLY", "mute 30m", now + 1)).join();
        assertEquals(punishments, count("punishments"), "no punishment written");
        assertEquals(requests, count("punishment_requests"), "no request written");
        assertEquals(suggestions, count("punishment_suggestions"), "no suggestion written");
        assertTrue(store.history(steve).join().isEmpty(), "the target is untouched");
        assertEquals(2L, notify.dryRunCount(ALEX, now - 1).join());
        assertEquals(0L, notify.dryRunCount(BOB, now - 1).join());
        assertEquals(1L, notify.dryRunCount(ALEX, now + 1).join(), "since a moment");
        NotifyStore.DryRunRow newest = notify.dryRuns(ALEX, 5).join().getFirst();
        assertEquals("spam", newest.reasonKey());
        assertTrue(newest.trainee());
    }
}
