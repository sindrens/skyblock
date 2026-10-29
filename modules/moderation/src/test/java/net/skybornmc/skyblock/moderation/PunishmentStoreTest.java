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

import static net.skybornmc.skyblock.moderation.TestData.HOUR;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The atomic issue path and the warn rules against a real (H2, MySQL mode) database: spec §5.5 edge cases and §9
 * items 4-6 (concurrency, never shorten, exempt), plus [Apply]/[Dismiss] first-click-wins.
 */
class PunishmentStoreTest {

    @TempDir
    Path dir;

    private Database db;
    private PunishmentStore store;
    private final PunishDecision decision = PunishDecision.standard();
    private final PunishDecision.Actor alex = TestData.staff(null);
    private final UUID alexId = TestData.ALEX;
    private long now;

    @BeforeEach
    void start() throws Exception {
        DatabaseConfig config = new DatabaseConfig();
        config.type = DatabaseConfig.Type.H2;
        config.h2File = "moderation-test";
        config.poolSize = 8;
        config.slowQueryMs = 60_000;
        db = new Database(config, dir.toFile(), Logger.getLogger("moderation-test"));
        db.start();
        new Migrations(db, Logger.getLogger("moderation-test")).migrate("core", getClass().getClassLoader());
        new Migrations(db, Logger.getLogger("moderation-test")).migrate("moderation", getClass().getClassLoader());
        store = new PunishmentStore(db);
        now = System.currentTimeMillis();
    }

    @AfterEach
    void stop() {
        db.close();
    }

    // ---------------------------------------------------------------- helpers

    private PunishmentStore.RuleHook hook(WarnRules.Mode mode, boolean exempt) {
        PunishDecision.Input template = new PunishDecision.Input(PunishDecision.Actor.system("System (auto)"), Punishment.Type.MUTE,
                null, false, false, exempt, false, false, null, null, now);
        return new PunishmentStore.RuleHook(TestData.rules(mode, true), decision, template, "System (auto)",
                t -> t.repeat() ? "Automatic rule: repeated" : "Automatic rule: " + t.ruleAt() + " warnings");
    }

    private PunishmentStore.IssueResult issue(UUID target, Punishment.Type type, Duration duration, long at,
                                              PunishmentStore.RuleHook hook) {
        return issueAs(alex, target, type, duration, at, hook);
    }

    private PunishmentStore.IssueResult issueAs(PunishDecision.Actor actor, UUID target, Punishment.Type type, Duration duration,
                                                long at, PunishmentStore.RuleHook hook) {
        Punishment draft = new Punishment(0, type, target, "Steve", null, false, "test", null, null, alexId, actor.name(), at,
                null, false, null, null, null, null, null);
        PunishDecision.Input in = new PunishDecision.Input(actor, type, duration, false, false, false, false, true, null, null, at);
        PunishmentStore.RuleHook timed = hook == null ? null : new PunishmentStore.RuleHook(hook.settings(), hook.decision(),
                new PunishDecision.Input(hook.autoTemplate().actor(), Punishment.Type.MUTE, null, false, false,
                        hook.autoTemplate().targetExempt(), false, false, null, null, at), hook.autoIssuerName(), hook.reason());
        return store.issue(draft, in, decision, timed).join();
    }

    private PunishmentStore.HandleResult apply(long id, String by, long at) {
        return store.apply(id, s -> {
            Templates.Step step = s.step();
            return new PunishDecision.Input(new PunishDecision.Actor(by, false, Set.of(Punishment.Type.values()), true, true, null,
                    false), step.type(), step.duration(), step.ipWide(), false, false, false, true, null, null, at);
        }, decision, UUID.nameUUIDFromBytes(by.getBytes()), by, at).join();
    }

    private List<Suggestion> suggestions(UUID target) {
        return store.suggestions(target).join();
    }

    private List<Punishment> history(UUID target) {
        return store.history(target).join();
    }

    // ---------------------------------------------------------------- rule firing through the store

    @Test
    void secondWarnCreatesOnePendingSuggestion() {
        UUID steve = UUID.randomUUID();
        assertNull(issue(steve, Punishment.Type.WARN, null, now, hook(WarnRules.Mode.NOTIFY, false)).trigger());
        PunishmentStore.IssueResult second = issue(steve, Punishment.Type.WARN, null, now + 1, hook(WarnRules.Mode.NOTIFY, false));
        assertNotNull(second.trigger());
        Suggestion s = second.trigger().suggestion();
        assertEquals(Suggestion.Status.PENDING, s.status());
        assertEquals("mute 1h", s.action());
        assertEquals("auto:warn-rule:2", s.source());
        assertEquals(now + 1 + 24 * HOUR, s.expires());
        assertEquals(List.of(s), suggestions(steve));
    }

    @Test
    void twoSimultaneousWarnsFireTheRuleOnce() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            for (int round = 0; round < 15; round++) {
                UUID steve = UUID.randomUUID();
                if (round % 2 == 0) {
                    issue(steve, Punishment.Type.WARN, null, now, hook(WarnRules.Mode.NOTIFY, false)); // 1 → 2/3 at once
                }
                CountDownLatch go = new CountDownLatch(1);
                List<Future<PunishmentStore.IssueResult>> results = new ArrayList<>();
                for (int i = 0; i < 3; i++) {
                    long at = now + 10 + i;
                    Callable<PunishmentStore.IssueResult> task = () -> {
                        go.await();
                        return issue(steve, Punishment.Type.WARN, null, at, hook(WarnRules.Mode.NOTIFY, false));
                    };
                    results.add(pool.submit(task));
                }
                go.countDown();
                int fired = 0;
                for (Future<PunishmentStore.IssueResult> f : results) {
                    if (f.get().trigger() != null) {
                        fired++;
                    }
                }
                assertEquals(1, fired, "round " + round + ": the rule fires exactly once");
                assertEquals(1, suggestions(steve).size());
            }
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void autoModeAppliesAtOnceAsSystem() {
        UUID steve = UUID.randomUUID();
        issue(steve, Punishment.Type.WARN, null, now, hook(WarnRules.Mode.AUTO, false));
        PunishmentStore.IssueResult r = issue(steve, Punishment.Type.WARN, null, now + 1, hook(WarnRules.Mode.AUTO, false));
        Punishment auto = r.trigger().autoPunishment();
        assertNotNull(auto);
        assertEquals(Punishment.Type.MUTE, auto.type());
        assertEquals("System (auto)", auto.issuerName());
        assertNull(auto.issuer());
        assertEquals("auto:warn-rule:2", auto.source());
        assertEquals(now + 1 + HOUR, auto.expires());
        assertEquals(Suggestion.Status.AUTO, r.trigger().suggestion().status());
        assertEquals(auto.id(), r.trigger().suggestion().punishmentId());
        // counter restarted, and the auto mute itself never triggers the repeat rule
        WarnRules.Counter c = WarnRules.counter(history(steve), suggestions(steve), TestData.rules(WarnRules.Mode.AUTO, true), now + 2);
        assertEquals(0, c.count());
        assertEquals(1, suggestions(steve).size());
    }

    @Test
    void alreadyMutedLongerIsNeverShortened() {
        for (WarnRules.Mode mode : WarnRules.Mode.values()) {
            UUID steve = UUID.randomUUID();
            issue(steve, Punishment.Type.MUTE, Duration.ofDays(3), now, null);
            issue(steve, Punishment.Type.WARN, null, now + 1, hook(mode, false));
            PunishmentStore.IssueResult r = issue(steve, Punishment.Type.WARN, null, now + 2, hook(mode, false));
            Suggestion s = r.trigger().suggestion();
            assertEquals(Suggestion.Status.SKIPPED, s.status(), mode.name());
            assertEquals(Suggestion.NOTE_ALREADY, s.note());
            assertNull(r.trigger().autoPunishment());
            assertEquals(1, history(steve).stream().filter(p -> p.type() == Punishment.Type.MUTE).count(), "the 3d mute stays alone");
        }
    }

    @Test
    void exemptPlayerGetsNoAutomaticAction() {
        UUID steve = UUID.randomUUID();
        issue(steve, Punishment.Type.WARN, null, now, hook(WarnRules.Mode.AUTO, true));
        PunishmentStore.IssueResult r = issue(steve, Punishment.Type.WARN, null, now + 1, hook(WarnRules.Mode.AUTO, true));
        assertEquals(Suggestion.Status.SKIPPED, r.trigger().suggestion().status());
        assertEquals(Suggestion.NOTE_EXEMPT, r.trigger().suggestion().note());
        assertEquals("exempt", r.trigger().verdict().stage());
        assertTrue(history(steve).stream().noneMatch(p -> p.type() == Punishment.Type.MUTE));
    }

    @Test
    void manualMuteInsideTheRepeatWindowSuggestsOneDay() {
        UUID steve = UUID.randomUUID();
        issue(steve, Punishment.Type.WARN, null, now, hook(WarnRules.Mode.NOTIFY, false));
        Suggestion first = issue(steve, Punishment.Type.WARN, null, now + 1, hook(WarnRules.Mode.NOTIFY, false)).trigger().suggestion();
        assertEquals(PunishmentStore.HandleKind.DISMISSED, store.dismiss(first.id(), alexId, "Alex", now + 2).join().kind());
        PunishmentStore.IssueResult mute = issue(steve, Punishment.Type.MUTE, Duration.ofMinutes(30), now + 3 * 24 * HOUR,
                hook(WarnRules.Mode.NOTIFY, false));
        assertNotNull(mute.trigger());
        assertTrue(mute.trigger().suggestion().repeat());
        assertEquals("mute 1d", mute.trigger().suggestion().action());
        assertEquals("auto:warn-rule:repeat", mute.trigger().suggestion().source());
    }

    // ---------------------------------------------------------------- [Apply] / [Dismiss]

    private Suggestion pending(UUID steve) {
        issue(steve, Punishment.Type.WARN, null, now, hook(WarnRules.Mode.NOTIFY, false));
        return issue(steve, Punishment.Type.WARN, null, now + 1, hook(WarnRules.Mode.NOTIFY, false)).trigger().suggestion();
    }

    @Test
    void firstApplyWinsTheOthersSeeWhoHandledIt() throws Exception {
        UUID steve = UUID.randomUUID();
        Suggestion s = pending(steve);
        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            CountDownLatch go = new CountDownLatch(1);
            List<Future<PunishmentStore.HandleResult>> clicks = new ArrayList<>();
            for (String staff : List.of("Alex", "Mia", "Kai", "Noor")) {
                clicks.add(pool.submit(() -> {
                    go.await();
                    return staff.equals("Noor") ? store.dismiss(s.id(), null, staff, now + 5).join() : apply(s.id(), staff, now + 5);
                }));
            }
            go.countDown();
            int winners = 0;
            String winner = null;
            List<PunishmentStore.HandleResult> results = new ArrayList<>();
            for (Future<PunishmentStore.HandleResult> f : clicks) {
                PunishmentStore.HandleResult r = f.get();
                results.add(r);
                if (r.kind() == PunishmentStore.HandleKind.APPLIED || r.kind() == PunishmentStore.HandleKind.DISMISSED) {
                    winners++;
                    winner = r.suggestion().handledByName();
                }
            }
            assertEquals(1, winners, "first click wins");
            for (PunishmentStore.HandleResult r : results) {
                if (r.kind() == PunishmentStore.HandleKind.ALREADY_HANDLED) {
                    assertEquals(winner, r.suggestion().handledByName(), "'already handled by <name>'");
                }
            }
            long autoMutes = history(steve).stream().filter(Punishment::automatic).count();
            Suggestion after = suggestions(steve).getFirst();
            assertEquals(after.status() == Suggestion.Status.APPLIED ? 1 : 0, autoMutes, "never two mutes from one suggestion");
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void applyCreatesANormalPunishmentByTheClicker() {
        UUID steve = UUID.randomUUID();
        Suggestion s = pending(steve);
        PunishmentStore.HandleResult r = apply(s.id(), "Mia", now + 10);
        assertEquals(PunishmentStore.HandleKind.APPLIED, r.kind());
        Punishment p = r.punishment();
        assertEquals("Mia", p.issuerName());
        assertEquals("auto:warn-rule:2", p.source());
        assertEquals("Automatic rule: 2 warnings", p.reason());
        assertEquals(now + 10 + HOUR, p.expires());
        assertEquals(Suggestion.Status.APPLIED, r.suggestion().status());
        assertEquals(p.id(), r.suggestion().punishmentId());
        assertEquals(PunishmentStore.HandleKind.ALREADY_HANDLED, apply(s.id(), "Kai", now + 11).kind());
        // /unwarn makes the count drop, but the applied mute stays (nothing is undone automatically)
        store.pardon(Punishment.Type.WARN, steve, null, alexId, "Alex", "mistake", true).join();
        assertTrue(history(steve).stream().anyMatch(x -> x.id() == p.id() && x.active(now + 20)));
    }

    @Test
    void applyWhenAlreadyMutedLongerIsSkipped() {
        UUID steve = UUID.randomUUID();
        Suggestion s = pending(steve);
        issue(steve, Punishment.Type.MUTE, Duration.ofDays(7), now + 3, null);
        PunishmentStore.HandleResult r = apply(s.id(), "Mia", now + 4);
        assertEquals(PunishmentStore.HandleKind.SKIPPED, r.kind());
        assertEquals(Suggestion.Status.SKIPPED, r.suggestion().status());
        assertEquals("error.already-muted", r.verdict().key());
    }

    @Test
    void blockedClickerLeavesItPendingForSomeoneElse() {
        UUID steve = UUID.randomUUID();
        Suggestion s = pending(steve);
        PunishmentStore.HandleResult r = store.apply(s.id(), x -> new PunishDecision.Input(new PunishDecision.Actor("Trainee", false,
                Set.of(Punishment.Type.WARN), false, false, Duration.ofMinutes(30), false), Punishment.Type.MUTE, Duration.ofHours(1),
                false, false, false, false, true, null, null, now + 5), decision, null, "Trainee", now + 5).join();
        assertEquals(PunishmentStore.HandleKind.BLOCKED, r.kind());
        assertEquals(Suggestion.Status.PENDING, suggestions(steve).getFirst().status());
    }

    @Test
    void expiredSuggestionCanNotBeApplied() {
        UUID steve = UUID.randomUUID();
        Suggestion s = pending(steve);
        assertEquals(PunishmentStore.HandleKind.EXPIRED, apply(s.id(), "Mia", now + 25 * HOUR).kind());
        assertEquals(PunishmentStore.HandleKind.EXPIRED, store.dismiss(s.id(), null, "Mia", now + 25 * HOUR).join().kind());
        assertEquals(PunishmentStore.HandleKind.NOT_FOUND, apply(987654, "Mia", now).kind());
    }

    @Test
    void counterSurvivesARestart() {
        UUID steve = UUID.randomUUID();
        issue(steve, Punishment.Type.WARN, null, now, hook(WarnRules.Mode.NOTIFY, false));
        PunishmentStore fresh = new PunishmentStore(db); // e.g. after a server restart: nothing kept in memory
        WarnRules.Counter c = WarnRules.counter(fresh.history(steve).join(), fresh.suggestions(steve).join(),
                TestData.rules(WarnRules.Mode.NOTIFY, true), now + 1);
        assertEquals(1, c.count());
        Punishment draft = new Punishment(0, Punishment.Type.WARN, steve, "Steve", null, false, "x", null, null, alexId, "Alex",
                now + 2, null, false, null, null, null, null, null);
        PunishmentStore.IssueResult r = fresh.issue(draft, new PunishDecision.Input(alex, Punishment.Type.WARN, null, false, false,
                false, false, true, null, null, now + 2), decision, hook(WarnRules.Mode.NOTIFY, false)).join();
        assertNotNull(r.trigger(), "the 2nd warn after a restart still fires");
    }

    @Test
    void blockedIssueStoresNothing() {
        UUID steve = UUID.randomUUID();
        PunishmentStore.IssueResult r = issueAs(TestData.staff(Duration.ofDays(1)), steve, Punishment.Type.BAN, Duration.ofDays(30),
                now, hook(WarnRules.Mode.NOTIFY, false));
        assertEquals(PunishDecision.Outcome.BLOCKED, r.verdict().outcome());
        assertNull(r.punishment());
        assertTrue(history(steve).isEmpty());
    }
}
