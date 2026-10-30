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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P9 against a real (H2, MySQL mode) database: a rollback reverses exactly the selected rows of that staff member (never
 * other staff, kicks or rows pardoned meanwhile), removes pending suggestions of reversed warns, marks the rows for
 * /pstats and makes them no offences; a second confirm changes nothing. The capped false-report suggestion (one per week,
 * a waiting one is refreshed). The purge: preview equals what is removed, the ledger keeps the punishment in force with
 * the account id instead of the name, the IP of an IP ban in force stays, the audit log is untouched; the retention
 * deletes purged ledgers past keep-ledger. The guard store keeps blocks across restarts.
 */
class P9StoreTest {

    private static final UUID STEVE = TestData.STEVE;
    private static final UUID ALEX = TestData.ALEX;
    private static final UUID BOB = UUID.fromString("00000000-0000-0000-0000-00000000000c");
    private static final UUID ADMIN = UUID.fromString("00000000-0000-0000-0000-00000000000d");
    private static final long MIN = 60_000L;

    @TempDir
    Path dir;

    private Database db;
    private PunishmentStore store;
    private long now;

    @BeforeEach
    void start() throws Exception {
        DatabaseConfig config = new DatabaseConfig();
        config.type = DatabaseConfig.Type.H2;
        config.h2File = "moderation-p9-test";
        config.poolSize = 4;
        config.slowQueryMs = 60_000;
        db = new Database(config, dir.toFile(), Logger.getLogger("moderation-p9-test"));
        db.start();
        new Migrations(db, Logger.getLogger("moderation-p9-test")).migrate("core", getClass().getClassLoader());
        new Migrations(db, Logger.getLogger("moderation-p9-test")).migrate("moderation", getClass().getClassLoader());
        store = new PunishmentStore(db);
        now = System.currentTimeMillis();
    }

    @AfterEach
    void stop() {
        db.close();
    }

    private static PunishDecision.Actor staff(String name, UUID id) {
        return new PunishDecision.Actor(name, false, Set.of(Punishment.Type.values()), true, true, null, false, id, false,
                true, false, false);
    }

    private Punishment give(UUID target, String targetName, UUID issuer, String issuerName, Punishment.Type type,
                            Duration length, long created, String template, String ip, boolean ipWide) {
        Punishment draft = new Punishment(0, type, target, targetName, ip, ipWide, "Reason text of " + targetName, template, 0,
                issuer, issuerName, created, null, false, null, null, null, null, null);
        PunishDecision.Input in = new PunishDecision.Input(staff(issuerName, issuer), type, length, ipWide, false, false, false,
                true, null, null, created);
        PunishmentStore.IssueResult r = store.issue(draft, in, PunishDecision.standard(), null, Duration.ofHours(24)).join();
        assertTrue(r.verdict().applied(), r.verdict().toString());
        return r.punishment();
    }

    private Punishment give(UUID target, UUID issuer, String issuerName, Punishment.Type type, Duration length, long created) {
        return give(target, "Steve", issuer, issuerName, type, length, created, "spam", null, false);
    }

    private long count(String sql, Object... params) {
        return db.async(c -> Sql.queryOne(c, sql, rs -> rs.getLong(1), params).orElse(0L)).join();
    }

    // ================================================================ 14.1 rollback

    @Test
    void rollbackReversesOnlyTheSelectedRowsOfThatStaffMember() {
        long since = now - 60 * MIN;
        Punishment ban = give(STEVE, ALEX, "Alex", Punishment.Type.BAN, null, now - 10 * MIN);
        Punishment mute = give(BOB, ALEX, "Alex", Punishment.Type.MUTE, Duration.ofDays(1), now - 20 * MIN);
        Punishment warn = give(ADMIN, ALEX, "Alex", Punishment.Type.WARN, null, now - 5 * MIN);
        Punishment kick = give(STEVE, ALEX, "Alex", Punishment.Type.KICK, null, now - 4 * MIN);
        Punishment older = give(STEVE, ALEX, "Alex", Punishment.Type.MUTE, Duration.ofDays(1), now - 90 * MIN);
        Punishment bobs = give(STEVE, BOB, "Bob", Punishment.Type.MUTE, Duration.ofDays(1), now - 10 * MIN);
        // a pending suggestion fired by the warn
        db.run(c -> Sql.update(c, "INSERT INTO punishment_suggestions (target_uuid, target_name, kind, action, reason, source, "
                        + "created, status, cause_id) VALUES (?, 'Admin', 'warn-rule', 'mute 1h', 'auto', 'auto:warn-rule:2', ?, "
                        + "'PENDING', ?)", Sql.toBytes(ADMIN), now - 5 * MIN, warn.id())).join();

        List<Punishment> candidates = store.rollbackCandidates(ALEX, since, now).join();
        assertEquals(List.of(mute.id(), ban.id(), warn.id()), candidates.stream().map(Punishment::id).toList(),
                "only Alex's, in the period, never the kick");

        // pardoned meanwhile (after the preview): left alone
        store.pardon(Punishment.Type.MUTE, BOB, null, BOB, "Bob", "manual", false).join();

        List<Long> ids = new ArrayList<>(candidates.stream().map(Punishment::id).toList());
        ids.add(bobs.id()); // a forged id of another staff member's punishment must never be touched
        PunishmentStore.RollbackResult r = store.rollback(ids, ALEX, "Alex", since, now, ADMIN, "Admin",
                "Rollback: Alex (account problem)", now).join();
        assertEquals(List.of(ban.id(), warn.id()), r.reversed().stream().map(Punishment::id).sorted().toList());
        assertEquals(1, r.removedSuggestions().size());
        assertEquals(0, count("SELECT COUNT(*) FROM punishment_suggestions WHERE status = 'PENDING'"));
        assertEquals("rollback", db.async(c -> Sql.queryOne(c, "SELECT note FROM punishment_suggestions", rs -> rs.getString(1))
                .orElse(null)).join());

        Punishment banNow = store.punishment(ban.id()).join().orElseThrow();
        assertTrue(banNow.removed());
        assertEquals("Rollback: Alex (account problem)", banNow.removedReason());
        assertEquals("Admin", banNow.removedByName());
        assertNull(store.punishment(kick.id()).join().orElseThrow().removedAt(), "kicks untouched");
        assertNull(store.punishment(older.id()).join().orElseThrow().removedAt(), "outside the period untouched");
        assertNull(store.punishment(bobs.id()).join().orElseThrow().removedAt(), "other staff untouched");
        assertEquals("manual", store.punishment(mute.id()).join().orElseThrow().removedReason(), "pardoned meanwhile: kept as it was");
        assertEquals(r.rollbackId(), count("SELECT rollback_id FROM punishments WHERE id = ?", ban.id()));
        assertEquals(2, count("SELECT punishment_count FROM punish_rollbacks WHERE id = ?", r.rollbackId()));

        // no offence afterwards; /pstats shows them as reversed (rollback)
        List<Punishment> history = store.history(STEVE).join();
        assertEquals(3, Offences.counting(history, "spam", Long.MIN_VALUE),
                "the rolled back ban is no offence; the kick, the older mute and Bob's mute still count");
        assertTrue(history.stream().filter(p -> p.id() == ban.id()).allMatch(Punishment::removed));
        PunishStats.Counts stats = PunishStats.Counts.of(store.statRows(ALEX, since).join());
        assertEquals(2, stats.rolledBack());
        assertEquals(3, stats.reversed(), "the 2 rolled back plus the mute Bob pardoned by hand");

        // a second confirm changes nothing
        PunishmentStore.RollbackResult again = store.rollback(ids, ALEX, "Alex", since, now, ADMIN, "Admin", "x", now).join();
        assertTrue(again.reversed().isEmpty());
    }

    // ================================================================ 14.5 capped suggestion

    @Test
    void falseReportSuggestionIsCappedPerWeekAndAWaitingOneIsRefreshed() {
        Templates.Step warn = Templates.Step.parse("warn");
        long cap = now - Duration.ofDays(7).toMillis();
        Optional<PunishmentStore.ApiSuggestion> first = store.suggestApiCapped(STEVE, "Steve", "report-abuse", warn,
                "False reports", "Reports: 3 invalid reports in 30d", now, now + Duration.ofHours(24).toMillis(), cap).join();
        assertTrue(first.isPresent());
        assertFalse(first.get().refreshed());
        Optional<PunishmentStore.ApiSuggestion> second = store.suggestApiCapped(STEVE, "Steve", "report-abuse", warn,
                "False reports", "Reports: 4 invalid reports in 30d", now + 1, now + Duration.ofHours(24).toMillis(), cap).join();
        assertTrue(second.isPresent() && second.get().refreshed(), "a waiting suggestion is refreshed, not a second one");
        assertEquals("Reports: 4 invalid reports in 30d", second.get().suggestion().details());
        // dismissed: the next one within the week is capped
        store.dismiss(first.get().suggestion().id(), ALEX, "Alex", now + 2).join();
        assertTrue(store.suggestApiCapped(STEVE, "Steve", "report-abuse", warn, "False reports", "x", now + 3, null, cap)
                .join().isEmpty());
        // a week later: allowed again
        long later = now + Duration.ofDays(8).toMillis();
        assertTrue(store.suggestApiCapped(STEVE, "Steve", "report-abuse", warn, "False reports", "x", later, null,
                later - Duration.ofDays(7).toMillis()).join().isPresent());
        // another reason is independent
        assertTrue(store.suggestApiCapped(STEVE, "Steve", "spam", warn, "Spam", "x", now + 4, null, cap).join().isPresent());
        assertEquals(1, count("SELECT COUNT(*) FROM punishment_suggestions WHERE reason_id = 'spam'"));
    }

    // ================================================================ 14.10 purge

    @Test
    void purgeRemovesPersonalDataKeepsTheLedgerAndNeverTouchesTheAuditLog() {
        PurgeRules.Settings s = PurgeRules.Settings.DEFAULT;
        long day = TestData.DAY;
        Punishment ipban = give(STEVE, "Steve", ALEX, "Alex", Punishment.Type.BAN, null, now - day, "alt-abuse", "1.2.3.4", true);
        Punishment warn = give(STEVE, ALEX, "Alex", Punishment.Type.WARN, null, now - 30 * day);
        Punishment ancient = give(STEVE, ALEX, "Alex", Punishment.Type.MUTE, Duration.ofDays(1), now - 500 * day);
        Punishment others = give(BOB, "Bob", ALEX, "Alex", Punishment.Type.WARN, null, now - day, "spam", null, false);
        db.run(c -> {
            Sql.update(c, "CREATE TABLE social_player_ips (uuid BINARY(16) NOT NULL, ip VARCHAR(45) NOT NULL, "
                    + "first_seen BIGINT NOT NULL, last_seen BIGINT NOT NULL)");
            Sql.update(c, "INSERT INTO social_player_ips (uuid, ip, first_seen, last_seen) VALUES (?, '1.2.3.4', 1, 2)", Sql.toBytes(STEVE));
            Sql.update(c, "INSERT INTO social_player_ips (uuid, ip, first_seen, last_seen) VALUES (?, '5.6.7.8', 1, 2)", Sql.toBytes(BOB));
        }).join();
        store.saveEvidence(warn.id(), now, List.of("Steve: secret")).join();
        store.saveEvidence(others.id(), now, List.of("Bob: hi")).join();
        store.addNote(STEVE, "Steve", ALEX, "Alex", "lives in Oslo", now).join();
        store.watch(STEVE, "Steve", ALEX, "Alex", "alt?", now, null).join();
        db.run(c -> Sql.update(c, "INSERT INTO appeals (punishment_id, punishment_type, player_uuid, player_name, appeal_text, "
                + "status, created) VALUES (?, 'BAN', ?, 'Steve', 'please, my name is Steve Smith', 'OPEN', ?)", ipban.id(),
                Sql.toBytes(STEVE), now)).join();
        db.run(c -> Sql.update(c, "INSERT INTO audit_log (created_at, server_id, category, action, actor_name, target, target_name) "
                + "VALUES (?, 'test', 'chat', 'chat', 'Steve', ?, 'Steve')", now, STEVE.toString())).join();
        long auditBefore = count("SELECT COUNT(*) FROM audit_log");

        PunishmentStore.PurgePlan preview = store.purgePreview(STEVE, s, now).join();
        assertEquals(new PurgeRules.Ledger(1, 1, 1, true), preview.ledger());
        assertEquals(1, preview.evidence());
        assertEquals(1, preview.notes());
        assertEquals(1, preview.appeals());
        assertEquals(1, preview.watch());
        assertEquals(1, preview.ips());
        assertTrue(preview.ipTable());

        PunishmentStore.PurgePlan done = store.purge(STEVE, s, now).join();
        assertEquals(preview, done, "the preview equals what is removed");
        String anon = PurgeRules.anonymousName(STEVE);
        Punishment ban = store.punishment(ipban.id()).join().orElseThrow();
        assertTrue(ban.active(now), "the ban stays in force");
        assertEquals(anon, ban.targetName());
        assertEquals("alt-abuse", ban.reason());
        assertEquals("1.2.3.4", ban.ip(), "the IP of an IP ban in force stays until it ends");
        Punishment w = store.punishment(warn.id()).join().orElseThrow();
        assertEquals(anon, w.targetName());
        assertEquals("spam", w.reason());
        assertTrue(store.punishment(ancient.id()).join().isEmpty(), "older than keep-ledger: deleted");
        assertEquals(0, count("SELECT COUNT(*) FROM punishments WHERE target_uuid = ? AND purged_at IS NULL", Sql.toBytes(STEVE)));
        assertEquals(0, count("SELECT COUNT(*) FROM punishment_evidence WHERE punishment_id = ?", warn.id()));
        assertEquals(1, count("SELECT COUNT(*) FROM punishment_evidence WHERE punishment_id = ?", others.id()), "other players untouched");
        assertEquals(0, count("SELECT COUNT(*) FROM staff_notes WHERE target_uuid = ?", Sql.toBytes(STEVE)));
        assertEquals(0, count("SELECT COUNT(*) FROM watchlist WHERE target_uuid = ?", Sql.toBytes(STEVE)));
        assertEquals(0, count("SELECT COUNT(*) FROM appeals WHERE appeal_text <> ''"));
        assertEquals(0, count("SELECT COUNT(*) FROM social_player_ips WHERE uuid = ?", Sql.toBytes(STEVE)));
        assertEquals(1, count("SELECT COUNT(*) FROM social_player_ips WHERE uuid = ?", Sql.toBytes(BOB)));
        assertEquals("Bob", store.punishment(others.id()).join().orElseThrow().targetName());
        assertEquals(auditBefore, count("SELECT COUNT(*) FROM audit_log"), "the audit log is never touched");
        assertNotNull(store.activeBan(STEVE, null).join().orElse(null), "login check still finds the ban");

        // retention: once the ban is lifted and keep-ledger passed, the ledger goes; the IP goes as soon as it is over
        store.pardon(Punishment.Type.BAN, STEVE, null, ADMIN, "Admin", "appeal", false).join();
        assertEquals(0, store.purgeLedgers(s, now).join());
        assertNull(store.punishment(ipban.id()).join().orElseThrow().ip(), "no IP once the ban is not in force");
        long future = now + Duration.ofDays(400).toMillis();
        assertEquals(2, store.purgeLedgers(s, future).join());
        assertEquals(0, count("SELECT COUNT(*) FROM punishments WHERE target_uuid = ?", Sql.toBytes(STEVE)));
        assertEquals(1, count("SELECT COUNT(*) FROM punishments WHERE target_uuid = ?", Sql.toBytes(BOB)), "unpurged rows stay");
    }

    // ================================================================ 14.1 guard store

    @Test
    void guardStateSurvivesARestart() {
        GuardStore g = new GuardStore(db);
        g.save(new Guard.Entry(ALEX, "Alex", Guard.State.BLOCKED, null, "bans 11/10", "guard", now)).join();
        g.save(new Guard.Entry(BOB, "Bob", Guard.State.LOCKED, null, null, "Admin", now)).join();
        g.save(new Guard.Entry(ALEX, "Alex", Guard.State.ALLOWED, now + 30 * MIN, null, "Admin", now + 1)).join();
        List<Guard.Entry> all = new GuardStore(db).all().join();
        assertEquals(2, all.size());
        Guard.Entry alex = all.stream().filter(e -> e.staff().equals(ALEX)).findFirst().orElseThrow();
        assertEquals(Guard.State.ALLOWED, alex.state());
        assertEquals(now + 30 * MIN, alex.until());
        assertTrue(g.delete(BOB).join());
        assertFalse(g.delete(BOB).join());
        assertEquals(1, g.all().join().size());
    }
}
