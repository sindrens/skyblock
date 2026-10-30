package net.skybornmc.skyblock.moderation;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static net.skybornmc.skyblock.moderation.TestData.DAY;
import static net.skybornmc.skyblock.moderation.TestData.HOUR;
import static net.skybornmc.skyblock.moderation.TestData.mute;
import static net.skybornmc.skyblock.moderation.TestData.rules;
import static net.skybornmc.skyblock.moderation.TestData.suggestion;
import static net.skybornmc.skyblock.moderation.TestData.warn;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Spec §5 and §9 items 1-3: counting, rule firing, reset, repeat window and the §5.5 edge cases (pure logic). */
class WarnRulesTest {

    private static final long T0 = 1_000L * DAY; // an arbitrary "day 0"
    private final WarnRules.Settings notify = rules(WarnRules.Mode.NOTIFY, true);

    private static Optional<WarnRules.Trigger> fire(Punishment event, List<Punishment> before, List<Suggestion> suggestions,
                                                    WarnRules.Settings s) {
        List<Punishment> history = new ArrayList<>(before);
        history.add(event);
        return WarnRules.evaluate(event, history, suggestions, s, event.created());
    }

    // ---------------------------------------------------------------- §9.1 counting

    @Test
    void onlyActiveWarnsCount() {
        long now = T0 + 40 * DAY;
        List<Punishment> history = List.of(
                warn(1, now - 31 * DAY),                                                   // expired (30d)
                TestData.punishment(2, Punishment.Type.WARN, now - DAY, null, now - HOUR, null), // removed with /unwarn
                warn(3, now - 29 * DAY),                                                   // active
                warn(4, now - HOUR),                                                       // active
                mute(5, now - HOUR, now + HOUR, null));                                    // not a warn
        assertEquals(2, WarnRules.counter(history, List.of(), notify, now).count());
        WarnRules.Settings permWarns = new WarnRules.Settings(true, WarnRules.Mode.NOTIFY, null, true, Duration.ofHours(24),
                notify.rules(), notify.repeatWindow(), notify.repeatAction());
        assertEquals(3, WarnRules.counter(history, List.of(), permWarns, now).count(), "warn-expire: perm = never expires");
    }

    // ---------------------------------------------------------------- §9.2 rule firing

    @Test
    void firstWarnNothingSecondSuggestsMuteOneHour() {
        Punishment w1 = warn(1, T0);
        assertTrue(fire(w1, List.of(), List.of(), notify).isEmpty(), "1st warn = only the warning");
        Optional<WarnRules.Trigger> t = fire(warn(2, T0 + HOUR), List.of(w1), List.of(), notify);
        assertTrue(t.isPresent());
        assertEquals(2, t.get().ruleAt());
        assertFalse(t.get().repeat());
        assertEquals(Templates.Step.parse("mute 1h"), t.get().action());
        assertEquals(2, t.get().count());
        assertEquals("auto:warn-rule:2", t.get().source());
    }

    @Test
    void applyAndDismissBothResetTheCounter() {
        List<Punishment> warns = List.of(warn(1, T0), warn(2, T0 + HOUR));
        for (Suggestion.Status status : List.of(Suggestion.Status.APPLIED, Suggestion.Status.DISMISSED)) {
            Suggestion handled = suggestion(10, 2, false, T0 + HOUR, status, T0 + 2 * HOUR);
            WarnRules.Counter c = WarnRules.counter(warns, List.of(handled), notify, T0 + 3 * HOUR);
            assertEquals(0, c.count(), status + " restarts the counter");
            assertNotNull(c.repeatUntil(), status + " opens the repeat window");
        }
    }

    @Test
    void repeatWithinAWeekSuggestsMuteOneDay() {
        List<Punishment> before = List.of(warn(1, T0), warn(2, T0 + HOUR));
        List<Suggestion> handled = List.of(suggestion(10, 2, false, T0 + HOUR, Suggestion.Status.APPLIED, T0 + 2 * HOUR));
        // spec test: warn at day 3 → 1d
        Optional<WarnRules.Trigger> day3 = fire(warn(3, T0 + 3 * DAY), before, handled, notify);
        assertTrue(day3.isPresent());
        assertTrue(day3.get().repeat());
        assertEquals(Templates.Step.parse("mute 1d"), day3.get().action());
        assertEquals("auto:warn-rule:repeat", day3.get().source());
        // spec test: (manual) mute at day 6 → 1d
        Optional<WarnRules.Trigger> day6 = fire(mute(4, T0 + 6 * DAY, T0 + 6 * DAY + HOUR, null), before, handled, notify);
        assertTrue(day6.isPresent() && day6.get().repeat());
        // spec test: warn at day 8 → normal rule again (1 warn = nothing, 2 warns = mute 1h)
        Punishment day8 = warn(5, T0 + 8 * DAY);
        assertTrue(fire(day8, before, handled, notify).isEmpty());
        List<Punishment> withDay8 = new ArrayList<>(before);
        withDay8.add(day8);
        Optional<WarnRules.Trigger> day9 = fire(warn(6, T0 + 9 * DAY), withDay8, handled, notify);
        assertTrue(day9.isPresent());
        assertFalse(day9.get().repeat());
        assertEquals(Templates.Step.parse("mute 1h"), day9.get().action());
    }

    @Test
    void automaticPunishmentsNeverTriggerAgain() {
        List<Suggestion> handled = List.of(suggestion(10, 2, false, T0, Suggestion.Status.APPLIED, T0));
        // The mute created by [Apply] itself (source auto:...) inside the window must not suggest another mute.
        assertTrue(fire(mute(3, T0 + HOUR, T0 + 2 * HOUR, "auto:warn-rule:2"), List.of(), handled, notify).isEmpty());
        assertTrue(fire(TestData.punishment(4, Punishment.Type.KICK, T0 + HOUR, null, null, null), List.of(), handled, notify)
                .isEmpty(), "kicks never count");
    }

    @Test
    void manualMuteOutsideWindowDoesNothing() {
        List<Suggestion> handled = List.of(suggestion(10, 2, false, T0, Suggestion.Status.APPLIED, T0));
        assertTrue(fire(mute(3, T0 + 8 * DAY, null, null), List.of(), handled, notify).isEmpty());
        assertTrue(fire(mute(3, T0, null, null), List.of(), List.of(), notify).isEmpty(), "no earlier trigger: no repeat");
    }

    // ---------------------------------------------------------------- §9.3 reset-after-trigger true vs false

    @Test
    void resetAfterTriggerTrueVsFalse() {
        WarnRules.Settings noRepeat = new WarnRules.Settings(true, WarnRules.Mode.NOTIFY, Duration.ofDays(30), true,
                Duration.ofHours(24), notify.rules(), null, null);
        WarnRules.Settings keepGrowing = new WarnRules.Settings(true, WarnRules.Mode.NOTIFY, Duration.ofDays(30), false,
                Duration.ofHours(24), notify.rules(), null, null);
        List<Punishment> warns = List.of(warn(1, T0), warn(2, T0 + HOUR));
        List<Suggestion> handled = List.of(suggestion(10, 2, false, T0 + HOUR, Suggestion.Status.DISMISSED, T0 + 2 * HOUR));
        assertEquals(0, WarnRules.counter(warns, handled, noRepeat, T0 + 3 * HOUR).count());
        assertEquals(2, WarnRules.counter(warns, handled, keepGrowing, T0 + 3 * HOUR).count());
        // reset on: a 3rd warn counts 1, nothing fires; reset off: counts 3, rule 2 already fired for these warns
        assertTrue(fire(warn(3, T0 + DAY), warns, handled, noRepeat).isEmpty());
        assertTrue(fire(warn(3, T0 + DAY), warns, handled, keepGrowing).isEmpty());
        assertEquals(3, WarnRules.counter(List.of(warn(1, T0), warn(2, T0 + HOUR), warn(3, T0 + DAY)), handled, keepGrowing,
                T0 + DAY).count());
        // reset off: once the counted warns expired the rule can fire again
        List<Punishment> later = List.of(warn(1, T0), warn(2, T0 + HOUR), warn(3, T0 + 40 * DAY));
        Optional<WarnRules.Trigger> again = fire(warn(4, T0 + 41 * DAY), later, handled, keepGrowing);
        assertTrue(again.isPresent());
        assertEquals(2, again.get().ruleAt());
    }

    // ---------------------------------------------------------------- §5.5 edge cases

    @Test
    void pendingSuggestionBlocksNewOnes() {
        List<Punishment> warns = List.of(warn(1, T0), warn(2, T0 + HOUR));
        List<Suggestion> pending = List.of(suggestion(10, 2, false, T0 + HOUR, Suggestion.Status.PENDING, null));
        assertTrue(fire(warn(3, T0 + 2 * HOUR), warns, pending, notify).isEmpty());
        WarnRules.Counter c = WarnRules.counter(warns, pending, notify, T0 + 2 * HOUR);
        assertNotNull(c.pending());
        assertEquals(2, c.count(), "the counter only restarts once staff handle it");
    }

    @Test
    void expiredPendingDoesNotCountAsFired() {
        List<Punishment> warns = List.of(warn(1, T0), warn(2, T0 + HOUR));
        List<Suggestion> expired = List.of(suggestion(10, 2, false, T0 + HOUR, Suggestion.Status.PENDING, null));
        long later = T0 + 2 * DAY; // pending-expire 24h passed
        assertNull(WarnRules.counter(warns, expired, notify, later).pending());
        Optional<WarnRules.Trigger> t = WarnRules.evaluate(warn(3, later), List.of(warn(1, T0), warn(2, T0 + HOUR), warn(3, later)),
                expired, notify, later);
        assertTrue(t.isPresent(), "an ignored suggestion reminds staff on the next warn");
        assertEquals(3, t.get().count());
    }

    @Test
    void countJumpFiresHighestRuleNotFired() {
        WarnRules.Settings two = new WarnRules.Settings(true, WarnRules.Mode.NOTIFY, Duration.ofDays(30), false,
                Duration.ofHours(24), List.of(new WarnRules.Rule(2, Templates.Step.parse("mute 1h")),
                new WarnRules.Rule(4, Templates.Step.parse("ban 1d"))), null, null);
        List<Punishment> imported = List.of(warn(1, T0), warn(2, T0 + 1), warn(3, T0 + 2), warn(4, T0 + 3));
        Optional<WarnRules.Trigger> t = fire(warn(5, T0 + 4), imported, List.of(), two);
        assertTrue(t.isPresent());
        assertEquals(4, t.get().ruleAt(), "count jumped from 1 to 5: the highest rule fires");
        // rule 4 fired; rule 2 (lower) never fires afterwards for the same warns
        List<Suggestion> fired = List.of(suggestion(10, 4, false, T0 + 4, Suggestion.Status.APPLIED, T0 + 5));
        List<Punishment> five = new ArrayList<>(imported);
        five.add(warn(5, T0 + 4));
        assertTrue(fire(warn(6, T0 + 6), five, fired, two).isEmpty());
        // rule 2 fired; reaching 4 fires rule 4
        List<Suggestion> firedTwo = List.of(suggestion(11, 2, false, T0 + 1, Suggestion.Status.APPLIED, T0 + 1));
        Optional<WarnRules.Trigger> four = fire(warn(4, T0 + 3), imported.subList(0, 3), firedTwo, two);
        assertTrue(four.isPresent());
        assertEquals(4, four.get().ruleAt());
    }

    @Test
    void unwarnLowersTheCounterButUndoesNothing() {
        long now = T0 + DAY;
        List<Punishment> warns = List.of(warn(1, T0),
                TestData.punishment(2, Punishment.Type.WARN, T0 + HOUR, null, T0 + 2 * HOUR, null));
        List<Suggestion> applied = List.of(suggestion(10, 2, false, T0 + HOUR, Suggestion.Status.APPLIED, T0 + HOUR));
        WarnRules.Settings off = new WarnRules.Settings(true, WarnRules.Mode.NOTIFY, Duration.ofDays(30), false,
                Duration.ofHours(24), notify.rules(), null, null);
        assertEquals(1, WarnRules.counter(warns, applied, off, now).count());
        assertEquals(Suggestion.Status.APPLIED, applied.getFirst().status(), "the applied suggestion (and its mute) stays");
    }

    @Test
    void disabledRulesNeverFire() {
        WarnRules.Settings disabled = WarnRules.Settings.DISABLED;
        assertTrue(fire(warn(2, T0 + HOUR), List.of(warn(1, T0)), List.of(), disabled).isEmpty());
    }

    @Test
    void counterShowsNextRuleExpiriesAndRepeatWindow() {
        long now = T0 + HOUR;
        WarnRules.Counter c = WarnRules.counter(List.of(warn(1, T0)), List.of(), notify, now);
        assertEquals(1, c.count());
        assertEquals(2, c.next().at());
        assertEquals(List.of(T0 + 30 * DAY), c.expiries());
        assertNull(c.repeatUntil());
        WarnRules.Counter after = WarnRules.counter(List.of(warn(1, T0)),
                List.of(suggestion(10, 2, false, T0, Suggestion.Status.APPLIED, T0)), notify, now);
        assertEquals(T0 + 7 * DAY, after.repeatUntil());
    }
}
