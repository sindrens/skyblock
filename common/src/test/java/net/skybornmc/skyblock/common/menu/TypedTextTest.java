package net.skybornmc.skyblock.common.menu;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** GUI-4b: typed text from a chat prompt or a Bedrock form. */
class TypedTextTest {

    @Test
    void cleansColourCodesAndSpaces() {
        TypedText.Result r = TypedText.clean("  §aBuy §lwheat&r here ", 40);
        assertEquals(TypedText.Check.OK, r.check());
        assertEquals("Buy wheat here", r.text());
    }

    @Test
    void emptyAndTooLong() {
        assertEquals(TypedText.Check.EMPTY, TypedText.clean(null, 10).check());
        assertEquals(TypedText.Check.EMPTY, TypedText.clean("   ", 10).check());
        assertEquals(TypedText.Check.EMPTY, TypedText.clean("§c§l", 10).check(), "only codes = nothing typed");
        TypedText.Result long1 = TypedText.clean("abcdefghijk", 10);
        assertEquals(TypedText.Check.TOO_LONG, long1.check());
        assertEquals("abcdefghijk", long1.text(), "kept so the form can ask again with it");
        assertEquals(TypedText.Check.OK, TypedText.clean("abcdefghij", 10).check(), "exactly the limit");
        assertEquals(TypedText.Check.OK, TypedText.clean("a", 0).check(), "the limit is at least 1");
    }

    @Test
    void formOnlyForBedrockWhenOnAndAvailable() {
        assertTrue(TypedText.useForm(true, true, true));
        assertFalse(TypedText.useForm(false, true, true), "turned off: chat");
        assertFalse(TypedText.useForm(true, false, true), "Java: chat");
        assertFalse(TypedText.useForm(true, true, false), "no Floodgate: chat");
    }
}
