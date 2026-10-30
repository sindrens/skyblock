package net.skybornmc.skyblock.moderation;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P8 without a server: rule numbers (14.2: mapping, validation, placeholders in the player messages), notification
 * routing (14.9: permission, switches, always-on, Discord mapping and sensitive types) and training mode (14.6: trainee
 * actions become requests, warns stay direct, the dry run reports the real verdict without changing anything).
 */
class P8LogicTest {

    private static final Path BUNDLED = Path.of("src/main/resources/punishments.yml");
    private static final Path TRACKED = Path.of("../../server/plugins/PhoenixSkyblock/punishments.yml");
    private static final Path MESSAGES = Path.of("../../plugin/src/main/resources/messages/moderation.yml");
    private static final Path TRACKED_MESSAGES = Path.of("../../server/plugins/PhoenixSkyblock/messages/moderation.yml");
    private static final UUID ALEX = TestData.ALEX;

    private static PunishConfigParser.Result parse(String yaml) {
        return PunishConfigParser.parse(yaml, TestData.ICONS);
    }

    // ================================================================ 14.2 rule numbers

    @Test
    void shippedConfigMapsReasonsToExistingRules() throws Exception {
        for (Path file : List.of(BUNDLED, TRACKED)) {
            PunishConfigParser.Result r = parse(Files.readString(file));
            assertTrue(r.ok(), file + ": " + r.errors());
            RuleBook book = r.config().rules();
            assertEquals(Set.of(1, 2, 3, 4, 5, 6), book.rules().keySet());
            assertEquals(List.of(4), book.numbers("spam"));
            assertEquals(List.of(2), book.numbers("hacking"));
            assertEquals(List.of(5), book.numbers("rmt"));
            assertEquals(List.of(1), book.numbers("warn-language"), "warn buttons have rules too");
            assertEquals(List.of(4), book.numbers("warn-caps-spam"));
            // every reference points to a rule that exists
            book.refs().values().forEach(list -> list.forEach(n -> assertTrue(book.rules().containsKey(n), file + ": " + n)));
            // reasons without a rule fall back to extras.rules-link (allowed, listed in OPEN_QUESTIONS)
            Templates t = new Templates();
            t.use(r.config());
            assertEquals(List.of("griefing", "afk-farming", "coop-griefing", "warn-rules", "warn-other"),
                    book.withoutRule(t.reasonIds()));
            assertEquals("https://skybornmc.net/rules", book.link("griefing", r.config().extras().rulesLink()));
        }
    }

    @Test
    void ruleBookTextsAndLinks() {
        RuleBook book = new RuleBook(Map.of(1, new RuleBook.Rule(1, null, null), 3, new RuleBook.Rule(3, "https://x.net/r#3", "Old")),
                Map.of("Spam", List.of(3), "both", List.of(1, 3)));
        assertEquals("3", book.numberText("spam"), "case-insensitive reason ids");
        assertEquals("1, 3", book.numberText("both"));
        assertEquals("", book.numberText("unknown"));
        assertEquals("", book.numberText(null), "direct commands have no reason id");
        assertEquals("https://x.net/r#3", book.link("spam", "https://fallback"));
        assertEquals("https://x.net/r#3", book.link("both", "https://fallback"), "first rule with its own link");
        assertEquals("https://fallback", book.link("unknown", "https://fallback"));
        assertEquals("R1 / Old", book.titleText("both", r -> r.yamlTitle() == null ? "R" + r.number() : r.yamlTitle()));
        assertEquals(List.of(1, 3), book.rules().keySet().stream().toList(), "ordered by number");
    }

    @Test
    void unknownRuleNumberIsAConfigErrorWithFileLineAndReason() throws Exception {
        String base = Files.readString(BUNDLED);
        PunishConfigParser.Result r = parse(base.replaceFirst("(?m)^        rule: 4$", "        rule: 9"));
        assertFalse(r.ok(), "a reason may only point to a rule that exists");
        ConfigProblem p = r.errors().getFirst();
        assertEquals("config.unknown-rule", p.key());
        assertEquals("9", p.arg("value"));
        assertEquals("spam", p.arg("id"));
        assertTrue(p.path().endsWith("spam.rule"), p.path());
        assertTrue(p.line() > 0, "with a line number");
        assertEquals("1, 2, 3, 4, 5, 6", p.arg("rules"));

        PunishConfigParser.Result list = parse(base.replace("warn-caps-spam: { slot: 28, icon: WRITABLE_BOOK, rule: 4 }",
                "warn-caps-spam: { slot: 28, icon: WRITABLE_BOOK, rule: [4, 7] }"));
        assertFalse(list.ok(), "every number of a list is checked, warn buttons too");
        assertEquals("warn-caps-spam", list.errors().getFirst().arg("id"));

        assertFalse(parse(base.replaceFirst("(?m)^        rule: 4$", "        rule: spam")).ok(), "not a number");
        assertFalse(parse(base.replace("  6: { link:", "  0: { link:")).ok(), "rule numbers start at 1");
        assertFalse(parse(base.replace("  6: { link: \"https://skybornmc.net/rules\" }",
                "  6: { link: \"https://skybornmc.net/rules page\" }")).ok(), "a link with a space is not valid");
        assertFalse(parse(base.replace("  6: { link:", "  6: { titel: \"x\", link:")).ok(), "unknown key under a rule");
    }

    @Test
    void rulesAreOptionalAndAListWorks() {
        String yaml = """
                categories:
                  chat:
                    reasons:
                      spam: { ladder: ["warn"] }
                      flood: { ladder: ["warn"], rule: [2, 1, 2] }
                rules:
                  1: "https://a.net/1"
                  2: { title: "No spam" }
                """;
        PunishConfigParser.Result r = parse(yaml);
        assertTrue(r.ok(), r.errors().toString());
        RuleBook book = r.config().rules();
        assertEquals(List.of(2, 1), book.numbers("flood"), "written order, duplicates removed");
        assertEquals(List.of(), book.numbers("spam"), "a reason without a rule is allowed");
        assertEquals("https://a.net/1", book.link("flood", "f"), "rule 2 has no link, rule 1 has");
        assertEquals("No spam", book.rules().get(2).yamlTitle(), "legacy YAML title kept as a fallback");

        PunishConfigParser.Result none = parse("""
                categories:
                  chat:
                    reasons:
                      spam: { ladder: ["warn"], rule: 1 }
                """);
        assertFalse(none.ok(), "no rules: block = no number exists");
        assertEquals("-", none.errors().getFirst().arg("rules"));
    }

    @Test
    void playerMessagesShowTheRule() throws Exception {
        for (Path file : List.of(MESSAGES, TRACKED_MESSAGES)) {
            YamlConfiguration m = YamlConfiguration.loadConfiguration(file.toFile());
            // spec 14.2: the warn message, the mute message, the ban screen, /mypunishments, appeals; staff: lore + /history
            for (String key : List.of("warned.you", "muted.you", "mine.mute", "mine.entry", "mine.entry-staff", "mine.warning",
                    "appeal.status-line", "history.line", "punish.reason.rule", "menu.history.rule")) {
                assertTrue(String.valueOf(m.get(key)).contains("<rule-text>"), file + " " + key);
            }
            for (String key : List.of("screen.ban", "screen.tempban")) {
                assertTrue(m.getStringList(key).stream().anyMatch(l -> l.contains("<rule-text>")), file + " " + key);
            }
            assertTrue(m.getString("muted.you").contains("<rule-link>"));
            for (int n = 1; n <= 6; n++) {
                assertTrue(m.contains("rules.titles." + n), "title of rule " + n);
            }
            assertTrue(m.getString("rules.text").contains("<rule>") && m.getString("rules.text").contains("<rule-title>"));
            for (NotifyRules.Type t : NotifyRules.Type.values()) {
                assertTrue(m.contains("notify-settings.types." + t.id() + ".name"), t.id());
            }
            for (DryRun.Outcome o : DryRun.Outcome.values()) {
                String id = o.name().toLowerCase(java.util.Locale.ROOT);
                assertTrue(m.contains("dry-run.result." + id) && m.contains("dry-run.outcome." + id), id);
            }
            for (String why : List.of("trainee", "approval", "limit", "self", "exempt", "offline", "permission", "length-limit",
                    "duplicate", "never-shorten")) {
                assertTrue(m.contains("dry-run.why." + why), why);
            }
            assertTrue(m.getString("approval.notify").contains("<trainee>"), "requests show who is a trainee");
        }
        assertEquals(Files.readString(MESSAGES), Files.readString(TRACKED_MESSAGES), "bundled and tracked messages identical");
        assertEquals(Files.readString(BUNDLED), Files.readString(TRACKED), "bundled and tracked punishments.yml identical");
    }

    // ================================================================ 14.9 notification routing

    @Test
    void onlyPermittedTypesAreDeliveredAndSwitchesWork() {
        NotifyRules.Settings s = NotifyRules.Settings.DEFAULT;
        assertFalse(NotifyRules.receives(NotifyRules.Type.APPEALS, false, null, s), "no permission = never");
        assertFalse(NotifyRules.receives(NotifyRules.Type.APPEALS, false, true, s), "a stored 'on' never beats the permission");
        assertTrue(NotifyRules.receives(NotifyRules.Type.APPEALS, true, null, s), "nothing stored = on (default)");
        assertFalse(NotifyRules.receives(NotifyRules.Type.APPEALS, true, false, s), "switched off");
        assertTrue(NotifyRules.receives(NotifyRules.Type.APPEALS, true, true, s));
        assertTrue(NotifyRules.receives(NotifyRules.Type.GUARD, true, false, s), "always types ignore a stored off");
        assertFalse(NotifyRules.receives(NotifyRules.Type.GUARD, false, null, s), "always still needs the permission");
    }

    @Test
    void alwaysTypesCanNotBeTurnedOff() {
        NotifyRules.Settings s = new NotifyRules.Settings(EnumSet.of(NotifyRules.Type.GUARD, NotifyRules.Type.RAID), Map.of());
        assertEquals(NotifyRules.Change.ALWAYS_ON, NotifyRules.canChange(NotifyRules.Type.RAID, true, false, s));
        assertEquals(NotifyRules.Change.OK, NotifyRules.canChange(NotifyRules.Type.RAID, true, true, s), "on is fine");
        assertEquals(NotifyRules.Change.NO_PERMISSION, NotifyRules.canChange(NotifyRules.Type.REPORT, false, true, s));
        assertEquals(NotifyRules.Change.OK, NotifyRules.canChange(NotifyRules.Type.WATCHLIST, true, false, s));
    }

    @Test
    void discordMappingIsOffByDefaultAndSensitiveTypesNeedAPrivateChannel() {
        NotifyRules.Settings none = NotifyRules.Settings.DEFAULT;
        assertTrue(none.channels().isEmpty(), "spec 14.9: nothing mapped by default");
        assertEquals("punishments", NotifyRules.discordChannel(NotifyRules.Type.PUNISHMENTS, none, "punishments"));
        assertEquals("punishments", NotifyRules.discordChannel(NotifyRules.Type.RAID, none, "punishments"));
        for (NotifyRules.Type t : NotifyRules.Type.SENSITIVE) {
            assertNull(NotifyRules.discordChannel(t, none, "punishments"), t + " never goes to the main log");
        }
        assertEquals(EnumSet.of(NotifyRules.Type.WATCHLIST, NotifyRules.Type.APPEALS, NotifyRules.Type.GUARD,
                NotifyRules.Type.APPROVALS), NotifyRules.Type.SENSITIVE);
        NotifyRules.Settings mapped = new NotifyRules.Settings(EnumSet.of(NotifyRules.Type.GUARD),
                Map.of(NotifyRules.Type.GUARD, "admin-alerts", NotifyRules.Type.PUNISHMENTS, "staff-log"));
        assertEquals("admin-alerts", NotifyRules.discordChannel(NotifyRules.Type.GUARD, mapped, "punishments"));
        assertEquals("staff-log", NotifyRules.discordChannel(NotifyRules.Type.PUNISHMENTS, mapped, "punishments"));
        assertTrue(NotifyRules.mapped("admin-alerts", mapped));
        assertFalse(NotifyRules.mapped("punishments", mapped));
    }

    @Test
    void notifyConfigIsValidated() throws Exception {
        String base = Files.readString(BUNDLED);
        PunishConfigParser.Result ok = parse(base);
        assertEquals(EnumSet.of(NotifyRules.Type.GUARD), ok.config().extras().notifications().always());
        assertTrue(ok.config().extras().notifications().channels().isEmpty());
        PunishConfigParser.Result mapped = parse(base.replace("    channels: {}", "    channels: { appeals: appeals, guard: admin-alerts }"));
        assertTrue(mapped.ok(), mapped.errors().toString());
        assertEquals("admin-alerts", mapped.config().extras().notifications().channels().get(NotifyRules.Type.GUARD));
        assertFalse(parse(base.replace("    channels: {}", "    channels: { gard: admin }")).ok(), "unknown type");
        PunishConfigParser.Result url = parse(base.replace("    channels: {}", "    channels: { guard: \"https://discord.com/api/webhooks/1\" }"));
        assertFalse(url.ok(), "webhook URLs are secrets");
        assertEquals("config.webhook-in-config", url.errors().getFirst().key());
        assertFalse(parse(base.replace("    always: [guard]", "    always: [gard]")).ok());
        assertEquals("guard", parse(base.replace("    always: [guard]", "    always: [gard]")).errors().getFirst().arg("suggestion"));
    }

    // ================================================================ 14.6 training

    private static PunishDecision.Actor staff(boolean trainee, boolean approver) {
        return new PunishDecision.Actor("Alex", false, Set.of(Punishment.Type.values()), true, true, Duration.ofDays(30), false,
                ALEX, false, approver, trainee, false);
    }

    private static PunishDecision.Input in(PunishDecision.Actor a, Punishment.Type type, Duration d) {
        return new PunishDecision.Input(a, type, d, false, false, false, false, true, null, null, 1_000_000L);
    }

    private static final PunishDecision PIPELINE = PunishDecision.full(Duration.ofSeconds(15), Approval.Settings.DEFAULT,
            Training.Settings.DEFAULT);

    @Test
    void traineeActionsBecomeRequestsWarnsApplyDirectly() {
        PunishDecision.Actor trainee = staff(true, false);
        for (Punishment.Type t : List.of(Punishment.Type.MUTE, Punishment.Type.BAN, Punishment.Type.KICK,
                Punishment.Type.ISLAND_BAN, Punishment.Type.COOP_REMOVE)) {
            PunishDecision.Verdict v = PIPELINE.decide(in(trainee, t, t.timed() ? Duration.ofHours(1) : null));
            assertEquals(PunishDecision.Outcome.REQUEST, v.outcome(), t + ": even small actions go to a mentor");
            assertEquals("training", v.stage());
        }
        assertEquals(PunishDecision.Outcome.APPLY, PIPELINE.decide(in(trainee, Punishment.Type.WARN, null)).outcome(),
                "warn is not in requests-for: applied directly");
        assertEquals(PunishDecision.Outcome.APPLY, PIPELINE.decide(in(staff(false, false), Punishment.Type.MUTE,
                Duration.ofHours(1))).outcome(), "a normal staff member is not affected");
        assertEquals(PunishDecision.Outcome.REQUEST, PIPELINE.decide(in(staff(true, true), Punishment.Type.MUTE,
                Duration.ofHours(1))).outcome(), "the trainee mark wins over the approve permission");
        assertEquals(PunishDecision.Outcome.APPLY, PIPELINE.decide(in(PunishDecision.Actor.system("System").withTrainee(true),
                Punishment.Type.BAN, null)).outcome(), "the system is never a trainee");
    }

    @Test
    void trainingRespectsTheListBulkAndNeverShorten() {
        PunishDecision.Actor trainee = staff(true, false);
        PunishDecision.Verdict bulkWarn = PIPELINE.decide(in(trainee.inBulk(true), Punishment.Type.WARN, null));
        assertEquals(PunishDecision.Outcome.REQUEST, bulkWarn.outcome(), "bulk in requests-for: every player is a request");
        PunishDecision onlyBans = PunishDecision.full(null, Approval.Settings.DISABLED, new Training.Settings(EnumSet.of(
                Training.Kind.BAN)));
        assertEquals(PunishDecision.Outcome.APPLY, onlyBans.decide(in(trainee, Punishment.Type.MUTE, Duration.ofHours(1))).outcome());
        assertEquals(PunishDecision.Outcome.REQUEST, onlyBans.decide(in(trainee, Punishment.Type.BAN, Duration.ofDays(1))).outcome(),
                "training works even with approval switched off");
        PunishDecision withWarn = PunishDecision.full(null, null, new Training.Settings(EnumSet.of(Training.Kind.WARN)));
        assertEquals(PunishDecision.Outcome.REQUEST, withWarn.decide(in(trainee, Punishment.Type.WARN, null)).outcome(),
                "the owner can add warn to the list");
        // already muted for longer: nothing to ask for
        Punishment perm = new Punishment(5, Punishment.Type.MUTE, TestData.STEVE, "Steve", null, false, "x", null, null, ALEX,
                "Alex", 0, null, false, null, null, null, null, null);
        PunishDecision.Verdict skip = PIPELINE.decide(in(trainee, Punishment.Type.MUTE, Duration.ofHours(1)).withActive(perm, null));
        assertEquals(PunishDecision.Outcome.SKIPPED, skip.outcome());
        assertEquals(PunishDecision.Slot.TRAINING, PIPELINE.slots().get(PIPELINE.slots().indexOf(PunishDecision.Slot.APPROVAL) - 1),
                "training runs right before approval");
        assertTrue(Training.sanctionRefused(Training.Settings.DEFAULT, true));
        assertFalse(Training.sanctionRefused(Training.Settings.DEFAULT, false));
        assertFalse(Training.sanctionRefused(new Training.Settings(EnumSet.of(Training.Kind.BAN)), true));
    }

    @Test
    void trainingConfigIsValidated() throws Exception {
        String base = Files.readString(BUNDLED);
        assertEquals(Training.Settings.DEFAULT, parse(base).config().extras().training());
        PunishConfigParser.Result bad = parse(base.replace("requests-for: [ban, mute, kick, sanction, island, bulk]",
                "requests-for: [ban, mutes]"));
        assertFalse(bad.ok());
        assertEquals("mute", bad.errors().getFirst().arg("suggestion"));
        PunishConfigParser.Result warn = parse(base.replace("requests-for: [ban, mute, kick, sanction, island, bulk]",
                "requests-for: [ban, warn]"));
        assertEquals(new Training.Settings(EnumSet.of(Training.Kind.BAN, Training.Kind.WARN)), warn.config().extras().training());
    }

    @Test
    void dryRunReportsTheRealVerdict() {
        // a trainee's mute: the dry run says "would send a request ... you're in training" (same pipeline as the click)
        Templates.Step mute = Templates.Step.parse("mute 1d");
        PunishDecision.Verdict v = PIPELINE.decide(in(staff(true, false), Punishment.Type.MUTE, Duration.ofDays(1)));
        DryRun.Report r = DryRun.of(v, mute, 1, 1);
        assertEquals(DryRun.Outcome.REQUEST, r.outcome());
        assertEquals("trainee", r.why());
        assertEquals(2, r.offence(), "offence #2");
        assertEquals("mute 1d", r.step().describe());

        // a normal staff member: would apply
        DryRun.Report apply = DryRun.of(PIPELINE.decide(in(staff(false, false), Punishment.Type.MUTE, Duration.ofDays(1))),
                mute, 0, 0);
        assertEquals(DryRun.Outcome.APPLY, apply.outcome());
        assertNull(apply.why());

        // probation raises the step: the report shows the raised step and the bonus
        List<Templates.Step> ladder = List.of(Templates.Step.parse("warn"), Templates.Step.parse("mute 30m"),
                Templates.Step.parse("mute 6h"));
        PunishDecision.Escalation e = new PunishDecision.Escalation(ladder, 1, false, 2_000_000L, 1);
        PunishDecision.Verdict raised = PIPELINE.decide(in(staff(false, false), Punishment.Type.MUTE, Duration.ofMinutes(30))
                .withEscalation(e));
        DryRun.Report p = DryRun.of(raised, ladder.get(1), 1, 1);
        assertEquals(1, p.probationBonus());
        assertEquals("mute 6h", p.step().describe());

        // a ban over the rank limit for a non-approver: request because of the limit
        DryRun.Report limit = DryRun.of(PIPELINE.decide(in(staff(false, false), Punishment.Type.BAN, Duration.ofDays(60))),
                Templates.Step.parse("ban 60d"), 0, 0);
        assertEquals("approval", limit.why(), "ban >= 30d is an approval rule");

        // blocked for yourself; warn buttons have no offence number
        PunishDecision.Input self = new PunishDecision.Input(staff(false, false), Punishment.Type.WARN, null, false, false, false,
                true, true, null, null, 1L);
        DryRun.Report blocked = DryRun.of(PIPELINE.decide(self), Templates.Step.parse("warn"), -1, -1);
        assertEquals(DryRun.Outcome.BLOCKED, blocked.outcome());
        assertEquals("self", blocked.why());
        assertEquals(0, blocked.offence());
    }

    @Test
    void dryRunFlagIsParsed() {
        assertTrue(ModerationModule.Reason.parse("-dry").dry());
        assertTrue(ModerationModule.Reason.parse("-s -DRY").dry());
        assertFalse(ModerationModule.Reason.parse("-s").dry());
        assertEquals("", ModerationModule.Reason.parse("-dry").text(), "never part of a reason text");
    }
}
