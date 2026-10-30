package net.skybornmc.skyblock.moderation;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static net.skybornmc.skyblock.moderation.TestData.DAY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** P2 pure logic: spec 12.14 items 1 (undo rules), 2 (duplicate stage), 3 (never shorten), 4 (offence expiry), 11 (stats). */
class P2LogicTest {

    private static final long NOW = 1_800_000_000_000L;
    private static final UUID BOB = UUID.fromString("00000000-0000-0000-0000-00000000000c");
    private static final Duration WINDOW = Duration.ofSeconds(15);

    private static Punishment withTemplate(long id, Punishment.Type type, String template, long created, UUID issuer,
                                           String issuerName, Long removedAt, String source) {
        return new Punishment(id, type, TestData.STEVE, "Steve", null, false, "Spam / flooding", template, 0, issuer,
                issuerName, created, type == Punishment.Type.MUTE ? created + DAY : null, false, removedAt,
                removedAt == null ? null : issuer, removedAt == null ? null : issuerName, removedAt == null ? null : "x", source);
    }

    private static PunishDecision.Actor actor(String name, UUID id, boolean force) {
        return new PunishDecision.Actor(name, false, Set.of(Punishment.Type.values()), true, true, null, false, id, force);
    }

    private static PunishDecision.Input input(PunishDecision.Actor actor, String reason, boolean force, boolean confirmed,
                                              Punishment recent, long now) {
        return new PunishDecision.Input(actor, Punishment.Type.MUTE, Duration.ofMinutes(30), false, false, false, false, true,
                null, null, now).withReason(reason, force, confirmed).withRecent(recent);
    }

    // ================================================================ 12.2 duplicate stage (item 2)

    @Test
    void duplicateBlocksAnotherStaffInsideTheWindowOnly() {
        PunishDecision d = PunishDecision.withDuplicates(WINDOW);
        Punishment alexSpam = withTemplate(1, Punishment.Type.MUTE, "spam", NOW - 6_000, TestData.ALEX, "Alex", null, null);
        PunishDecision.Actor bob = actor("Bob", BOB, false);

        PunishDecision.Verdict blocked = d.decide(input(bob, "spam", false, false, alexSpam, NOW));
        assertEquals(PunishDecision.Outcome.BLOCKED, blocked.outcome());
        assertEquals("duplicate", blocked.stage());
        assertEquals("error.duplicate", blocked.key());
        assertEquals("Alex", blocked.args().get("staff"));
        assertEquals("6s", blocked.args().get("ago"));

        // after the window
        assertTrue(d.decide(input(bob, "spam", false, false, alexSpam, NOW + 9_000)).applied(), "15s passed");
        // another reason, an undone one, no reason key, the system: never a duplicate
        assertTrue(d.decide(input(bob, "toxicity", false, false, alexSpam, NOW)).applied());
        Punishment undone = withTemplate(1, Punishment.Type.MUTE, "spam", NOW - 6_000, TestData.ALEX, "Alex", NOW - 1, null);
        assertTrue(d.decide(input(bob, "spam", false, false, undone, NOW)).applied());
        assertTrue(d.decide(input(bob, null, false, false, alexSpam, NOW)).applied());
        PunishDecision.Input system = new PunishDecision.Input(PunishDecision.Actor.system("System (auto)"), Punishment.Type.MUTE,
                Duration.ofMinutes(30), false, false, false, false, true, null, null, NOW).withReason("spam", false, false)
                .withRecent(alexSpam);
        assertTrue(d.decide(system).applied());
    }

    @Test
    void duplicateClickAgainAndForce() {
        PunishDecision d = PunishDecision.withDuplicates(WINDOW);
        Punishment alexSpam = withTemplate(1, Punishment.Type.MUTE, "spam", NOW - 1_000, TestData.ALEX, "Alex", null, null);
        assertTrue(d.decide(input(actor("Bob", BOB, false), "spam", false, true, alexSpam, NOW)).applied(), "confirmed click");
        assertTrue(d.decide(input(actor("Bob", BOB, true), "spam", true, false, alexSpam, NOW)).applied(), "-force");
        PunishDecision.Verdict noPerm = d.decide(input(actor("Bob", BOB, false), "spam", true, false, alexSpam, NOW));
        assertEquals(PunishDecision.Outcome.BLOCKED, noPerm.outcome());
        assertEquals("error.no-force", noPerm.key());
    }

    @Test
    void duplicateOfYourOwnClickSaysSo() {
        PunishDecision d = PunishDecision.withDuplicates(WINDOW);
        Punishment mine = withTemplate(1, Punishment.Type.WARN, "warn-language", NOW - 2_000, BOB, "Bob", null, null);
        PunishDecision.Verdict v = d.decide(input(actor("Bob", BOB, false), "warn-language", false, false, mine, NOW));
        assertEquals("error.duplicate-self", v.key());
    }

    @Test
    void standardPipelineHasNoDuplicateStageAndP2FillsTheSlot() {
        assertFalse(PunishDecision.standard().slots().contains(PunishDecision.Slot.DUPLICATE));
        assertEquals(List.of(PunishDecision.Slot.EXEMPT, PunishDecision.Slot.PERMISSION, PunishDecision.Slot.LENGTH_LIMIT,
                PunishDecision.Slot.DUPLICATE, PunishDecision.Slot.NEVER_SHORTEN), PunishDecision.withDuplicates(WINDOW).slots());
        Punishment alexSpam = withTemplate(1, Punishment.Type.MUTE, "spam", NOW - 1_000, TestData.ALEX, "Alex", null, null);
        assertTrue(PunishDecision.withDuplicates(null).decide(input(actor("Bob", BOB, false), "spam", false, false, alexSpam, NOW))
                .applied(), "window off");
    }

    @Test
    void duplicateGuardArmsOnceForTheConfirmWindow() {
        DuplicateGuard g = new DuplicateGuard();
        String key = DuplicateGuard.key("bob", TestData.STEVE, "Spam");
        assertEquals(key, DuplicateGuard.key("bob", TestData.STEVE, "spam"));
        assertFalse(g.consume(key, NOW, Duration.ofSeconds(5)), "not armed");
        g.arm(key, NOW, Duration.ofSeconds(5));
        assertTrue(g.consume(key, NOW + 4_000, Duration.ofSeconds(5)));
        assertFalse(g.consume(key, NOW + 4_100, Duration.ofSeconds(5)), "one use");
        g.arm(key, NOW, Duration.ofSeconds(5));
        assertFalse(g.consume(key, NOW + 6_000, Duration.ofSeconds(5)), "too late");
    }

    // ================================================================ 12.2 never shorten (item 3)

    @Test
    void neverShortensALongerMuteOrBan() {
        PunishDecision d = PunishDecision.withDuplicates(WINDOW);
        Punishment mute3d = new Punishment(5, Punishment.Type.MUTE, TestData.STEVE, "Steve", null, false, "x", null, null,
                TestData.ALEX, "Alex", NOW - DAY, NOW + 2 * DAY, false, null, null, null, null, null);
        PunishDecision.Input oneHour = new PunishDecision.Input(actor("Bob", BOB, false), Punishment.Type.MUTE, Duration.ofHours(1),
                false, false, false, false, true, mute3d, null, NOW);
        PunishDecision.Verdict v = d.decide(oneHour);
        assertEquals(PunishDecision.Outcome.SKIPPED, v.outcome());
        assertEquals("error.already-muted", v.key());
        assertEquals("2d", v.args().get("left"));
        // a longer one is applied
        assertTrue(d.decide(oneHour.withStep(Punishment.Type.MUTE, Duration.ofDays(7), false)).applied());
        // perm ban: nothing timed replaces it
        Punishment permBan = new Punishment(6, Punishment.Type.BAN, TestData.STEVE, "Steve", null, false, "x", null, null,
                TestData.ALEX, "Alex", NOW - DAY, null, false, null, null, null, null, null);
        assertEquals(PunishDecision.Outcome.SKIPPED, d.decide(new PunishDecision.Input(actor("Bob", BOB, false), Punishment.Type.BAN,
                Duration.ofDays(30), false, false, false, false, true, null, permBan, NOW)).outcome());
    }

    // ================================================================ 12.1 undo rules (item 1)

    @Test
    void undoWithinAndAfterTheWindow() {
        Duration w = Duration.ofMinutes(2);
        Punishment mine = withTemplate(1, Punishment.Type.MUTE, "spam", NOW - 60_000, BOB, "Bob", null, null);
        assertEquals(Undo.Result.OK, Undo.check(mine, BOB, false, w, NOW));
        assertEquals(Undo.Result.EXPIRED, Undo.check(mine, BOB, false, w, NOW + 61_000), "2m passed");
        assertEquals(Undo.Result.NOT_YOURS, Undo.check(mine, TestData.ALEX, false, w, NOW));
        assertEquals(Undo.Result.OK, Undo.check(mine, TestData.ALEX, true, w, NOW), "undo.others");
        Punishment kick = withTemplate(2, Punishment.Type.KICK, null, NOW - 1_000, BOB, "Bob", null, null);
        assertEquals(Undo.Result.NOT_UNDOABLE, Undo.check(kick, BOB, true, w, NOW));
        Punishment removed = withTemplate(3, Punishment.Type.WARN, null, NOW - 1_000, BOB, "Bob", NOW - 500, null);
        assertEquals(Undo.Result.ALREADY_REMOVED, Undo.check(removed, BOB, true, w, NOW));
        assertEquals(Undo.Result.NOT_FOUND, Undo.check(null, BOB, true, w, NOW));
        Punishment auto = withTemplate(4, Punishment.Type.MUTE, null, NOW - 1_000, null, "System (auto)", null, "auto:warn-rule:2");
        assertEquals(Undo.Result.NOT_YOURS, Undo.check(auto, BOB, false, w, NOW), "system rows: undo.others only");
        assertEquals(Undo.Result.OK, Undo.check(auto, BOB, true, w, NOW));
    }

    @Test
    void headShowsTheNewestUndoableAction() {
        Duration w = Duration.ofMinutes(2);
        Punishment older = withTemplate(1, Punishment.Type.WARN, null, NOW - 90_000, BOB, "Bob", null, null);
        Punishment newer = withTemplate(2, Punishment.Type.MUTE, "spam", NOW - 10_000, BOB, "Bob", null, null);
        Punishment alexs = withTemplate(3, Punishment.Type.WARN, null, NOW - 5_000, TestData.ALEX, "Alex", null, null);
        Punishment kick = withTemplate(4, Punishment.Type.KICK, null, NOW - 1_000, BOB, "Bob", null, null);
        List<Punishment> history = List.of(kick, alexs, newer, older);
        assertSame(newer, Undo.latestUndoable(history, BOB, false, w, NOW));
        assertSame(alexs, Undo.latestUndoable(history, BOB, true, w, NOW));
        assertNull(Undo.latestUndoable(history, BOB, false, w, NOW + 200_000));
    }

    // ================================================================ 12.3 offence expiry (item 4)

    @Test
    void offencesExpire89CountsAnd91DoesNot() {
        Duration expire = Duration.ofDays(90);
        List<Punishment> history = List.of(
                withTemplate(1, Punishment.Type.MUTE, "spam", NOW - 89 * DAY, TestData.ALEX, "Alex", null, null),
                withTemplate(2, Punishment.Type.MUTE, "spam", NOW - 91 * DAY, TestData.ALEX, "Alex", null, null),
                withTemplate(3, Punishment.Type.MUTE, "spam", NOW - DAY, TestData.ALEX, "Alex", NOW, null),
                withTemplate(4, Punishment.Type.MUTE, "toxicity", NOW - DAY, TestData.ALEX, "Alex", null, null));
        long cutoff = Offences.cutoff(expire, NOW);
        assertEquals(1, Offences.counting(history, "spam", cutoff), "89d counts, 91d not, undone never");
        assertEquals(1, Offences.expired(history, "spam", cutoff), "the 91d one no longer counts");
        assertEquals(2, Offences.counting(history, "spam", Offences.cutoff(null, NOW)), "perm: all not-undone count");
        assertEquals(0, Offences.expired(history, "spam", Offences.cutoff(null, NOW)));

        PunishMenus.Target target = new PunishMenus.Target(TestData.STEVE, "Steve", history);
        Templates.Template spam = new Templates.Template("spam", "chat", null, org.bukkit.Material.PAPER,
                List.of(Templates.Step.parse("warn"), Templates.Step.parse("mute 30m"), Templates.Step.parse("mute 6h")), expire);
        int previous = target.previous("spam", spam.cutoff(NOW));
        assertEquals(1, previous);
        assertEquals("mute 30m", spam.stepFor(previous).describe(), "offence #2 → 2nd step");
        assertEquals(1, target.expiredOffences("spam", spam.cutoff(NOW)));
        assertEquals(1, target.recent("spam").id(), "newest not-undone spam");
    }

    @Test
    void cheatingReasonsNeverExpireAndOverridesWork() throws Exception {
        String yaml = java.nio.file.Files.readString(java.nio.file.Path.of("src/main/resources/punishments.yml"));
        PunishConfigParser.Result r = PunishConfigParser.parse(yaml, TestData.ICONS);
        assertTrue(r.ok(), String.valueOf(r.errors()));
        Templates holder = new Templates();
        holder.use(r.config());
        for (String cheat : List.of("hacking", "xray", "macro", "alt-abuse", "rmt")) {
            assertNull(holder.template(cheat).orElseThrow().offenceExpire(), cheat + " never expires");
        }
        assertEquals(Duration.ofDays(90), holder.template("spam").orElseThrow().offenceExpire(), "default 90d");
        PunishConfig.Extras x = r.config().extras();
        assertEquals(Duration.ofMinutes(2), x.undoWindow());
        assertEquals(Duration.ofSeconds(15), x.duplicateWindow());
        assertEquals(Duration.ofSeconds(5), x.duplicateConfirm());
        assertEquals(ZoneId.of("Europe/Oslo"), x.statsZone());
        assertEquals(PunishStats.Period.D7, x.statsPeriod());

        String custom = """
                categories:
                  chat:
                    reasons:
                      spam:
                        ladder: ["warn"]
                        offence-expire: 180d
                      toxicity:
                        ladder: ["warn"]
                extras:
                  offence-expire: 30d
                """;
        PunishConfigParser.Result c = PunishConfigParser.parse(custom, TestData.ICONS);
        assertTrue(c.ok(), String.valueOf(c.errors()));
        Templates t = new Templates();
        t.use(c.config());
        assertEquals(Duration.ofDays(180), t.template("spam").orElseThrow().offenceExpire(), "per-reason override");
        assertEquals(Duration.ofDays(30), t.template("toxicity").orElseThrow().offenceExpire(), "extras default");
    }

    @Test
    void badExtrasAreRejectedWithTheirKey() {
        String base = """
                categories:
                  chat:
                    reasons:
                      spam:
                        ladder: ["warn"]
                        offence-expire: soon
                extras:
                  undo-window: perm
                  duplicate-window: 0s
                  stats:
                    timezone: Mars/Olympus
                    default-period: 7dd
                    top: 0
                """;
        PunishConfigParser.Result r = PunishConfigParser.parse(base, TestData.ICONS);
        assertFalse(r.ok());
        List<String> keys = r.errors().stream().map(ConfigProblem::key).toList();
        assertTrue(keys.contains("config.bad-zone"), keys.toString());
        assertTrue(keys.contains("config.bad-period"), keys.toString());
        assertTrue(keys.contains("config.bad-number"), keys.toString());
        assertEquals(3, keys.stream().filter("config.bad-duration"::equals).count(), keys.toString());
        assertTrue(r.errors().stream().anyMatch(p -> p.key().equals("config.bad-period") && "7d".equals(p.arg("suggestion"))));
        assertTrue(r.errors().stream().filter(p -> p.path().equals("categories.chat.reasons.spam.offence-expire"))
                .allMatch(p -> p.line() == 6), () -> "line numbers for per-reason keys: " + r.errors());
    }

    // ================================================================ 12.10 stats (item 11)

    @Test
    void statsCountsPerTypeReversedPercentAndNet() {
        PunishStats.Counts c = PunishStats.Counts.of(List.of(new PunishStats.Row(Punishment.Type.WARN, 12, 1, 0),
                new PunishStats.Row(Punishment.Type.MUTE, 8, 1, 6), new PunishStats.Row(Punishment.Type.BAN, 3, 0, 0),
                new PunishStats.Row(Punishment.Type.KICK, 2, 0, 0)));
        assertEquals(25, c.given());
        assertEquals(2, c.reversed());
        assertEquals(8, c.reversedPercent(), "2 of 25 = 8%");
        assertEquals(23, c.net());
        assertEquals(6, c.fromSuggestions());
        assertEquals(0, PunishStats.Counts.ZERO.reversedPercent(), "nothing given: 0%, no division by zero");
    }

    @Test
    void statsPeriodsUseWholeDaysInTheTimeZone() {
        ZoneId oslo = ZoneId.of("Europe/Oslo");
        long now = ZonedDateTime.of(2026, 10, 30, 0, 30, 0, 0, oslo).toInstant().toEpochMilli();
        assertEquals(now - 24 * 3_600_000L, PunishStats.Period.H24.start(now, oslo));
        assertEquals(ZonedDateTime.of(2026, 10, 24, 0, 0, 0, 0, oslo).toInstant().toEpochMilli(),
                PunishStats.Period.D7.start(now, oslo), "today + 6 days before, from midnight Oslo");
        // across the DST change (25 Oct 2026): still local midnight
        assertEquals(ZonedDateTime.of(2026, 10, 1, 0, 0, 0, 0, oslo).toInstant().toEpochMilli(),
                PunishStats.Period.D30.start(now, oslo));
        assertEquals(0L, PunishStats.Period.ALL.start(now, oslo));
        assertEquals(PunishStats.Period.D30, PunishStats.Period.parse("30D").orElseThrow());
        assertTrue(PunishStats.Period.parse("week").isEmpty());
    }

    @Test
    void statsPermissionRulesAreCheckedPerCall() {
        PunishStats.Period def = PunishStats.Period.D7;
        assertEquals(PunishStats.Kind.OWN, PunishStats.parse(null, null, "Bob", false, def).kind());
        assertEquals(PunishStats.Period.D30, PunishStats.parse("30d", null, "Bob", false, def).period());
        assertEquals(PunishStats.Kind.OWN, PunishStats.parse("bob", "all", "Bob", false, def).kind(), "own name is fine");
        assertEquals("stats.no-permission-others", PunishStats.parse("Alex", null, "Bob", false, def).error());
        assertEquals("stats.no-permission-others", PunishStats.parse("top", null, "Bob", false, def).error());
        PunishStats.Query top = PunishStats.parse("top", "24h", "Bob", true, def);
        assertEquals(PunishStats.Kind.TOP, top.kind());
        assertEquals(PunishStats.Period.H24, top.period());
        PunishStats.Query other = PunishStats.parse("Alex", null, "Bob", true, def);
        assertEquals(PunishStats.Kind.OTHER, other.kind());
        assertEquals("Alex", other.staff());
        assertEquals(def, other.period());
        assertEquals("stats.bad-period", PunishStats.parse("Alex", "week", "Bob", true, def).error());
        assertEquals("stats.bad-period", PunishStats.parse("7d", "30d", "Bob", true, def).error());
    }

    @Test
    void statsRankingAndCache() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        List<PunishStats.Ranked> top = PunishStats.rank(Map.of(a, "Alex", b, "Bob"),
                Map.of(a, List.of(new PunishStats.Row(Punishment.Type.WARN, 3, 0, 0)),
                        b, List.of(new PunishStats.Row(Punishment.Type.MUTE, 5, 1, 0))), 10);
        assertEquals(List.of("Bob", "Alex"), top.stream().map(PunishStats.Ranked::name).toList());
        assertEquals(1, PunishStats.rank(Map.of(a, "Alex", b, "Bob"), Map.of(a, List.of(), b, List.of()), 1).size());

        AtomicLong clock = new AtomicLong(NOW);
        AtomicInteger loads = new AtomicInteger();
        PunishStats.Cache<String, Integer> cache = new PunishStats.Cache<>(Duration.ofSeconds(60), clock::get);
        assertEquals(1, cache.get("k", loads::incrementAndGet));
        clock.addAndGet(59_000);
        assertEquals(1, cache.get("k", loads::incrementAndGet), "cached for 60s");
        clock.addAndGet(1_000);
        assertEquals(2, cache.get("k", loads::incrementAndGet), "reloaded after 60s");
        cache.invalidate("k");
        assertEquals(3, cache.get("k", loads::incrementAndGet));
    }
}
