package net.skybornmc.skyblock.common.command;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommandParsingTest {

    enum Mode { ROTATE, RANDOM_ORDER }

    @ParameterizedTest
    @CsvSource({"30s,30", "30m,1800", "2h30m,9000", "1d,86400", "1w2d,777600", "7D,604800"})
    void parsesDurations(String input, long seconds) {
        assertEquals(Duration.ofSeconds(seconds), Durations.parse(input));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "abc", "10", "1x", "h1", "0m", "2h 30m", "99999w", "1h-5m"})
    void rejectsBadDurations(String input) {
        assertThrows(IllegalArgumentException.class, () -> Durations.parse(input));
    }

    @Test
    void filtersSuggestionsCaseInsensitiveInOrder() {
        List<String> names = List.of("Steve", "steven", "Alex", "STEPH");
        assertEquals(List.of("Steve", "steven", "STEPH"), Suggest.filter(names, "ste"));
        assertEquals(names, Suggest.filter(names, ""));
        assertEquals(List.of(), Suggest.filter(names, "z"));
    }

    @Test
    void suggestionsAreCapped() {
        List<String> many = java.util.stream.IntStream.range(0, 500).mapToObj(i -> "p" + i).toList();
        assertEquals(Suggest.LIMIT, Suggest.filter(many, "p").size());
    }

    @Test
    void unitSuggestionsFollowTypedNumber() {
        assertEquals(List.of("2m", "2h", "2d"), Suggest.withUnits("2", List.of("m", "h", "d"), List.of("1h")));
        assertEquals(List.of("1h", "12h"), Suggest.withUnits("", List.of("m"), List.of("1h", "12h")));
        assertEquals(List.of("1h"), Suggest.withUnits("1h", List.of("m"), List.of("1h", "12h", "2h")));
    }

    @Test
    void amountHintHasNoNumberSuggestions() {
        assertEquals(List.of("<amount>"), Suggest.hintFor("amount", ""));
        assertEquals(List.of("<price>"), Suggest.hintFor("price", null));
        assertEquals(List.of(), Suggest.hintFor("amount", "10"));
        assertEquals(List.of(), Suggest.hintFor("amount", "1"));
    }

    @Test
    void amountAndDurationArgsGiveFrameworkErrors() {
        CommandFailure amount = assertThrows(CommandFailure.class, () -> Args.amount("amount").parse(null, "lots"));
        assertEquals("invalid-amount", amount.key());
        assertEquals(1_500L, Args.amount("amount").parse(null, "1.5k"));
        CommandFailure duration = assertThrows(CommandFailure.class, () -> Args.duration("time").parse(null, "soon"));
        assertEquals("invalid-duration", duration.key());
    }

    @Test
    void enumChoicesUseKebabCase() {
        Arg<Mode> mode = Args.choice("mode", Mode.class);
        assertEquals(Mode.RANDOM_ORDER, mode.parse(null, "random-order"));
        assertEquals("invalid-choice", assertThrows(CommandFailure.class, () -> mode.parse(null, "nope")).key());
    }

    @Test
    void argumentWithoutSuggestionsIsRefused() {
        assertThrows(NullPointerException.class, () -> Arg.word("x", (s, raw) -> raw, null));
        assertThrows(IllegalArgumentException.class, () -> Args.text("reason"));
    }

    @Test
    void specValidationCatchesMistakes() {
        Arg<String> a = Args.text("reason", "spam");
        assertThrows(IllegalStateException.class, () -> CommandSpec.command("x").arg(a).arg(Args.amount("n")));
        assertThrows(IllegalStateException.class, () -> CommandSpec.command("x").optional(Args.amount("n")).arg(Args.amount("m")));
        assertThrows(IllegalStateException.class, () -> CommandSpec.command("x").validate("x"));
        assertThrows(IllegalStateException.class, () -> CommandSpec.command("x").arg(a).confirm(5).executes(c -> { }).validate("x"));
    }

    @Test
    void usageShowsRequiredOptionalAndChildren() {
        CommandSpec pay = CommandSpec.command("pay").arg(Args.word("player", Suggest.of("Steve")))
                .optional(Args.amount("amount")).executes(c -> { });
        assertEquals("/pay <player> [amount]", pay.usage("pay"));
        CommandSpec tree = CommandSpec.command("sb")
                .sub(CommandSpec.sub("reload").executes(c -> { }))
                .sub(CommandSpec.sub("worlds").executes(c -> { }));
        tree.validate("sb");
        assertTrue(tree.usage("sb").endsWith("<reload|worlds>"));
    }
}
