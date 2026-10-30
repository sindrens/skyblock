package net.skybornmc.skyblock.common.command;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Optional arguments may sit next to sub-commands (/skills [player] + /skills set|add); required ones may not. */
class MixedSpecTest {

    @Test
    void optionalArgumentNextToSubCommandsIsAllowed() {
        CommandSpec spec = CommandSpec.command("skills").optional(Args.word("player", Suggest.hint("player"))).executes(c -> { })
                .sub(CommandSpec.sub("set").executes(c -> { }));
        assertDoesNotThrow(() -> spec.validate("skills"));
    }

    @Test
    void requiredArgumentNextToSubCommandsIsRefused() {
        CommandSpec spec = CommandSpec.command("x").arg(Args.word("player", Suggest.hint("player"))).executes(c -> { })
                .sub(CommandSpec.sub("set").executes(c -> { }));
        assertThrows(IllegalStateException.class, () -> spec.validate("x"));
    }
}
