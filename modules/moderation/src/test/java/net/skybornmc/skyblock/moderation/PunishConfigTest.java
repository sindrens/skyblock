package net.skybornmc.skyblock.moderation;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Spec §6.2 + §9 items 7-9: validation, safe reload, and the build-time check of the tracked punishments.yml. */
class PunishConfigTest {

    private static final Path TRACKED = Path.of("../../server/plugins/PhoenixSkyblock/punishments.yml");
    private static final Path BUNDLED = Path.of("src/main/resources/punishments.yml");
    private static final Path MESSAGES = Path.of("../../plugin/src/main/resources/messages/moderation.yml");
    private static final Path TRACKED_MESSAGES = Path.of("../../server/plugins/PhoenixSkyblock/messages/moderation.yml");

    /** A small valid file the error tests break on purpose. */
    private static final String BASE = """
            categories:
              chat:
                icon: WRITABLE_BOOK
                reasons:
                  spam:
                    icon: PAPER
                    ladder: ["warn", "mute 30m", "ban 3d"]
                  toxicity:
                    ladder: ["warn", "mute 1h"]
            menu:
              confirm: false
              summary-slot: 4
              warn-counter-slot: 31
              divider-slots: [13, 22, 40]
              buttons:
                kick: { slot: 46, icon: LEATHER_BOOTS }
                history: 48
              mutes:
                spam: 9
                toxicity: 10
              warns:
                warn-language: { slot: 27, icon: PAPER }
            warn-rules:
              enabled: true
              mode: notify
              warn-expire: 30d
              reset-after-trigger: true
              pending-expire: 24h
              rules:
                - at: 2
                  action: "mute 1h"
              repeat-window: 7d
              repeat-action: "mute 1d"
            """;

    private static PunishConfigParser.Result parse(String text) {
        return PunishConfigParser.parse(text, TestData.ICONS);
    }

    private static ConfigProblem only(PunishConfigParser.Result r, String key) {
        assertFalse(r.ok(), "should be rejected");
        assertNull(r.config(), "a rejected file yields no config");
        List<ConfigProblem> matching = r.errors().stream().filter(p -> p.key().equals(key)).toList();
        assertEquals(1, matching.size(), "expected one " + key + " in " + r.errors());
        return matching.getFirst();
    }

    // ---------------------------------------------------------------- §9.9 the tracked file and the bundled default

    @Test
    void trackedAndBundledFilesParseCleanly() throws Exception {
        for (Path file : List.of(TRACKED, BUNDLED)) {
            PunishConfigParser.Result r = parse(Files.readString(file));
            assertTrue(r.ok(), file + " must parse without errors: " + r.errors());
            assertTrue(r.warnings().isEmpty(), file + " has warnings: " + r.warnings());
            PunishConfig c = r.config();
            assertEquals(4, c.categories().size());
            // Owner decisions (spec §7/§11 + notes): quarters, warn buttons, bottom row, warn rules.
            PunishConfig.MenuLayout m = c.menu();
            assertFalse(m.confirm());
            assertTrue(m.keepOpen());
            assertEquals(4, m.summarySlot());
            assertEquals(31, m.warnCounterSlot());
            assertEquals(Map.of("sanctions", 45, "kick", 46, "bulk", 47, "history", 48, "close", 49, "more-reasons", 50,
                    "dry-run", 52), Map.copyOf(m.buttons().entrySet().stream().collect(java.util.stream.Collectors.toMap(
                    Map.Entry::getKey, e -> e.getValue().slot()))));
            assertEquals(9, m.reasonSlots().get("toxicity"));
            assertEquals("mutes", m.quarterOf().get("toxicity"));
            assertEquals("bans", m.quarterOf().get("hacking"));
            assertEquals("small", m.quarterOf().get("afk-farming"));
            assertEquals(List.of("warn-language", "warn-caps-spam", "warn-rules", "warn-other"),
                    m.warns().stream().map(PunishConfig.WarnButton::id).toList());
            WarnRules.Settings w = c.warnRules();
            assertTrue(w.enabled());
            assertEquals(WarnRules.Mode.NOTIFY, w.mode());
            assertEquals(Duration.ofDays(30), w.warnExpire());
            assertTrue(w.resetAfterTrigger());
            assertEquals(Duration.ofHours(24), w.pendingExpire());
            assertEquals(List.of(new WarnRules.Rule(2, Templates.Step.parse("mute 1h"))), w.rules());
            assertEquals(Duration.ofDays(7), w.repeatWindow());
            assertEquals(Templates.Step.parse("mute 1d"), w.repeatAction());
        }
    }

    @Test
    void everyReasonIsPlacedOrReachable() throws Exception {
        PunishConfig c = parse(Files.readString(TRACKED)).config();
        Templates holder = new Templates();
        holder.use(c);
        for (String id : holder.templateIds()) {
            assertTrue(c.menu().reasonSlots().containsKey(id), id + " is not on the /p screen (only via More reasons)");
        }
        assertTrue(holder.reasonIds().containsAll(List.of("spam", "warn-language")), "/p <player> <reason> accepts warn buttons");
    }

    @Test
    void everyTextLivesInTheMessagesFile() throws Exception {
        PunishConfig c = parse(Files.readString(TRACKED)).config();
        for (Path file : List.of(MESSAGES, TRACKED_MESSAGES)) {
            YamlConfiguration msg = YamlConfiguration.loadConfiguration(file.toFile());
            for (Templates.Category cat : c.categories()) {
                assertTrue(msg.isString("categories." + cat.id()), file + ": categories." + cat.id());
                assertNull(cat.yamlName(), "names live in messages, not punishments.yml");
                for (Templates.Template t : cat.templates()) {
                    assertTrue(msg.isString("reasons." + t.id()), file + ": reasons." + t.id());
                    assertNull(t.yamlName(), "names live in messages, not punishments.yml");
                }
            }
            for (PunishConfig.WarnButton b : c.menu().warns()) {
                assertTrue(msg.isString("warn-buttons." + b.id() + ".name"), file + ": warn-buttons." + b.id() + ".name");
                assertTrue(msg.isString("warn-buttons." + b.id() + ".reason"), file + ": warn-buttons." + b.id() + ".reason");
            }
            for (String button : PunishConfig.BUTTONS) {
                assertTrue(msg.contains("punish.buttons." + button + ".name"), file + ": punish.buttons." + button);
            }
            for (String stage : List.of("self", "exempt", "offline", "permission", "length-limit", "never-shorten")) {
                assertTrue(msg.contains("punish.blocked." + stage), file + ": punish.blocked." + stage);
            }
            for (String kind : Templates.Step.KINDS) {
                assertTrue(msg.isString("steps." + kind), file + ": steps." + kind);
            }
            for (Suggestion.Status status : Suggestion.Status.values()) {
                assertTrue(msg.isString("suggest.status." + status.name().toLowerCase(java.util.Locale.ROOT)), file + ": " + status);
            }
            for (String key : List.of("config.yaml-invalid", "config.missing-section", "config.missing-optional",
                    "config.not-a-section", "config.bad-id", "config.duplicate-id", "config.empty-ladder", "config.bad-step",
                    "config.bad-step-time", "config.bad-icon", "config.unknown-reason", "config.unknown-button",
                    "config.reason-placed-twice", "config.bad-slot", "config.duplicate-slot", "config.bad-boolean",
                    "config.bad-mode", "config.bad-duration", "config.bad-rules", "config.bad-rule-at",
                    "config.duplicate-rule-at", "config.bad-rule-action", "config.repeat-incomplete", "config.hint",
                    "error.already-muted", "error.already-banned", "error.no-permission-type", "warn-rules.reason",
                    "warn-rules.repeat-reason", "words.system-auto", "muted.auto", "muted.auto-repeat", "muted.join")) {
                assertTrue(msg.contains(key), file + ": missing " + key);
            }
        }
    }

    // ---------------------------------------------------------------- §9.7 config validation

    @Test
    void baseFileIsValid() {
        PunishConfigParser.Result r = parse(BASE);
        assertTrue(r.ok(), String.valueOf(r.errors()));
    }

    @Test
    void badStepIsRejectedWithLineAndSuggestion() {
        String text = BASE.replace("\"ban 3d\"", "\"bann 3d\"");
        ConfigProblem p = only(parse(text), "config.bad-step");
        assertEquals("categories.chat.reasons.spam.ladder", p.path());
        assertEquals(7, p.line());
        assertEquals("bann 3d", p.arg("value"));
        assertEquals("ban 3d", p.arg("suggestion"));
        String message = render(p);
        assertTrue(message.startsWith("punishments.yml line 7:"), message);
        assertTrue(message.contains("unknown step \"bann 3d\" (did you mean \"ban 3d\"?)"), message);
    }

    @Test
    void eachValidationRuleIsRejected() {
        record Bad(String find, String replace, String key) {
        }
        List<Bad> cases = List.of(
                new Bad("\"mute 30m\"", "\"mute 30x\"", "config.bad-step-time"),
                new Bad("\"warn\", \"mute 1h\"]", "\"warn 1h\"]", "config.bad-step-time"),
                new Bad("ladder: [\"warn\", \"mute 1h\"]", "ladder: []", "config.empty-ladder"),
                new Bad("icon: PAPER\n        ladder", "icon: PAPPER\n        ladder", "config.bad-icon"),
                new Bad("  toxicity:\n        ladder", "  Toxicity:\n        ladder", "config.bad-id"),
                new Bad("    spam: 9", "    spamm: 9", "config.unknown-reason"),
                new Bad("    toxicity: 10", "    toxicity: 9", "config.duplicate-slot"),
                new Bad("    toxicity: 10", "    toxicity: 54", "config.bad-slot"),
                new Bad("    toxicity: 10", "    toxicity: -1", "config.bad-slot"),
                new Bad("    toxicity: 10", "    toxicity: ten", "config.bad-slot"),
                new Bad("warn-counter-slot: 31", "warn-counter-slot: 4", "config.duplicate-slot"),
                new Bad("history: 48", "histroy: 48", "config.unknown-button"),
                new Bad("divider-slots: [13, 22, 40]", "divider-slots: [13, 22, 46]", "config.duplicate-slot"),
                new Bad("warn-language: { slot", "spam: { slot", "config.duplicate-id"),
                new Bad("confirm: false", "confirm: maybe", "config.bad-boolean"),
                new Bad("mode: notify", "mode: notfy", "config.bad-mode"),
                new Bad("warn-expire: 30d", "warn-expire: 30 days", "config.bad-duration"),
                new Bad("pending-expire: 24h", "pending-expire: soon", "config.bad-duration"),
                new Bad("- at: 2", "- at: 0", "config.bad-rule-at"),
                new Bad("- at: 2", "- at: two", "config.bad-rule-at"),
                new Bad("action: \"mute 1h\"", "action: \"warn\"", "config.bad-rule-action"),
                new Bad("action: \"mute 1h\"", "action: \"kick\"", "config.bad-rule-action"),
                new Bad("repeat-action: \"mute 1d\"", "repeat-action: \"warn\"", "config.bad-rule-action"),
                new Bad("repeat-action: \"mute 1d\"", "repeat-action: \"mute 1dd\"", "config.bad-step-time"),
                new Bad("categories:", "kategories:", "config.missing-section"));
        for (Bad bad : cases) {
            String text = BASE.replace(bad.find(), bad.replace());
            assertFalse(text.equals(BASE), "test setup: '" + bad.find() + "' not found");
            ConfigProblem p = only(parse(text), bad.key());
            assertTrue(p.line() > 0 || bad.key().equals("config.missing-section"), bad.key() + " has a line: " + p);
            String message = render(p);
            assertFalse(message.contains("<"), "every placeholder filled: " + message);
        }
    }

    @Test
    void duplicateRuleAtAndHints() {
        String text = BASE.replace("""
                    - at: 2
                      action: "mute 1h"
                """, """
                    - at: 2
                      action: "mute 1h"
                    - at: 2
                      action: "ban 1d"
                """);
        ConfigProblem p = only(parse(text), "config.duplicate-rule-at");
        assertEquals("warn-rules.rules.1.at", p.path());
        assertEquals(32, p.line());
        assertEquals("PAPER", only(parse(BASE.replace("icon: PAPER\n        ladder", "icon: PAPPER\n        ladder")),
                "config.bad-icon").arg("suggestion"));
        assertEquals("spam", only(parse(BASE.replace("    spam: 9", "    spamm: 9")), "config.unknown-reason").arg("suggestion"));
        assertEquals("notify", only(parse(BASE.replace("mode: notify", "mode: notfy")), "config.bad-mode").arg("suggestion"));
    }

    @Test
    void invalidYamlIsRejectedWithLine() {
        PunishConfigParser.Result r = parse(BASE.replace("    spam: 9", "    spam: 9\n   broken: [unclosed"));
        ConfigProblem p = only(r, "config.yaml-invalid");
        assertTrue(p.line() > 0, "SnakeYAML line kept: " + p);
    }

    @Test
    void reasonPlacedTwiceIsRejected() {
        String text = BASE.replace("  warns:\n", "  bans:\n    spam: 14\n  warns:\n");
        only(parse(text), "config.reason-placed-twice");
    }

    @Test
    void permAndOptionalSectionsAreAccepted() {
        PunishConfigParser.Result r = parse(BASE.replace("warn-expire: 30d", "warn-expire: perm"));
        assertTrue(r.ok());
        assertNull(r.config().warnRules().warnExpire());
        PunishConfigParser.Result noMenu = parse(BASE.substring(0, BASE.indexOf("menu:")));
        assertTrue(noMenu.ok(), "an old file without menu/warn-rules still loads (with defaults)");
        assertEquals(2, noMenu.warnings().size());
        assertFalse(noMenu.config().warnRules().enabled());
    }

    // ---------------------------------------------------------------- safe reload: the old config stays

    @Test
    void brokenReloadKeepsTheLastGoodConfig() {
        Templates holder = new Templates();
        assertTrue(holder.offer(parse(BASE)));
        PunishConfig good = holder.config();
        assertFalse(holder.offer(parse(BASE.replace("\"ban 3d\"", "\"bann 3d\""))));
        assertSame(good, holder.config(), "a bad edit never replaces the working config");
        assertTrue(holder.template("spam").isPresent());
        assertTrue(holder.offer(parse(BASE.replace("- at: 2", "- at: 3"))), "a fixed file is taken");
        assertEquals(3, holder.config().warnRules().rules().getFirst().at());
    }

    // ---------------------------------------------------------------- §9.8 layout (covered above) + line index

    @Test
    void yamlLinesFindNestedKeysAndListItems() {
        YamlLines lines = YamlLines.index(BASE);
        assertEquals(1, lines.line("categories"));
        assertEquals(7, lines.line("categories.chat.reasons.spam.ladder"));
        assertEquals(19, lines.line("menu.mutes.spam"));
        assertEquals(30, lines.line("warn-rules.rules.0.at"));
        assertEquals(31, lines.line("warn-rules.rules.0.action"));
        assertEquals(19, lines.line("menu.mutes.spam.slot"), "falls back to the parent");
        assertEquals(0, lines.line("nothing.here"));
    }

    // ---------------------------------------------------------------- helpers

    /** Renders a problem with the template from messages/moderation.yml (placeholders filled, style tags removed). */
    private static String render(ConfigProblem p) {
        YamlConfiguration msg = YamlConfiguration.loadConfiguration(MESSAGES.toFile());
        String template = msg.getString(p.key());
        assertNotNull(template, "message " + p.key());
        java.util.Map<String, String> values = new java.util.HashMap<>(p.args());
        values.put("file", "punishments.yml");
        values.put("line", p.line() > 0 ? String.valueOf(p.line()) : "?");
        values.put("path", p.path());
        String suggestion = p.arg("suggestion");
        values.put("hint", suggestion.isEmpty() ? "" : msg.getString("config.hint").replace("<suggestion>", suggestion));
        Matcher m = Pattern.compile("<([a-z_]+)>").matcher(template);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            m.appendReplacement(out, Matcher.quoteReplacement(values.getOrDefault(m.group(1), m.group())));
        }
        m.appendTail(out);
        return out.toString();
    }

    @SuppressWarnings("unused")
    private static List<String> keys(List<ConfigProblem> problems) {
        List<String> out = new ArrayList<>();
        problems.forEach(p -> out.add(p.key()));
        return out;
    }
}
