package net.skybornmc.skyblock.common.command;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/** FIXES1: input Brigadier would answer with "Incorrect argument for command ... <--[HERE]" gets our usage instead. */
class InputCheckTest {

    private static CommandSpec island() {
        return CommandSpec.command("is").executes(c -> { })
                .sub(CommandSpec.sub("create").executes(c -> { }))
                .sub(CommandSpec.sub("invite").arg(Args.word("player", Suggest.hint("player"))).executes(c -> { }))
                .sub(CommandSpec.sub("chat").optional(Args.text("message", "hello")).executes(c -> { }))
                .sub(CommandSpec.sub("disband").confirm(30).executes(c -> { }))
                .sub(CommandSpec.sub("admin").permission("x.admin").executes(c -> { }));
    }

    private static InputCheck.Problem check(CommandSpec spec, String line, String... perms) {
        List<String> words = InputCheck.words(line);
        return InputCheck.check(spec, words.subList(1, words.size()), Set.of(perms)::contains);
    }

    @Test
    void ownerScreenshotIsCreateWithAWordAfterIt() {
        InputCheck.Problem p = check(island(), "/is create something");
        assertNotNull(p);
        assertEquals("is create", p.path());
        assertEquals("something", p.input());
    }

    @Test
    void validLinesPass() {
        assertNull(check(island(), "/is"));
        assertNull(check(island(), "/is create"));
        assertNull(check(island(), "/is invite Steve"));
        assertNull(check(island(), "/is invite"), "missing arguments: the framework's usage text handles it");
        assertNull(check(island(), "/is chat hello there everyone"), "a text argument takes the rest of the line");
        assertNull(check(island(), "/is disband confirm"));
        assertNull(check(island(), "/is admin", "x.admin"));
    }

    @Test
    void unknownSubCommandAndExtraWordsAreCaught() {
        assertEquals("nope", check(island(), "/is nope").input());
        assertEquals("is", check(island(), "/is nope").path());
        assertEquals("extra", check(island(), "/is invite Steve extra").input());
        assertEquals("CREATE", check(island(), "/is CREATE").input(), "Brigadier literals are case-sensitive");
        assertEquals("admin", check(island(), "/is admin").input(), "a sub-command without permission does not exist");
    }

    @Test
    void optionalArgumentsNextToSubCommands() {
        CommandSpec skills = CommandSpec.command("skills").optional(Args.word("player", Suggest.hint("player"))).executes(c -> { })
                .sub(CommandSpec.sub("set").executes(c -> { }));
        assertNull(check(skills, "/skills Steve"));
        assertNull(check(skills, "/skills set"));
        assertEquals("x", check(skills, "/skills Steve x").input());
    }

    @Test
    void wordsSplitLikeTheClient() {
        assertEquals(List.of("is", "create"), InputCheck.words("/is create "));
        assertEquals(List.of(), InputCheck.words("/"));
    }
}
