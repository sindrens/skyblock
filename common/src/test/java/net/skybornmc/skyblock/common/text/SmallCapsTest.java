package net.skybornmc.skyblock.common.text;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Look v2 (owner 7 Nov 2026): {@code <sc>} small caps on Java, capitals on Bedrock. */
class SmallCapsTest {

    @Test
    void javaGetsSmallCapsBedrockCapitals() {
        assertEquals("<c:label>ᴍᴏɴᴇʏ:</c:label>", Style.smallCaps("<c:label><sc>Money:</sc></c:label>", false));
        assertEquals("<c:label>MONEY:</c:label>", Style.smallCaps("<c:label><sc>Money:</sc></c:label>", true));
        assertEquals("ᴛʜᴇ ꜰᴀʀᴍ", Style.smallCaps("<sc>The Farm</sc>", false));
    }

    @Test
    void tagsInsideStayAndTextWithoutTagIsUntouched() {
        assertEquals("ʟᴠ <level>", Style.smallCaps("<sc>Lv <level></sc>", false));
        assertEquals("Plain text", Style.smallCaps("Plain text", false));
        assertEquals("ᴀ b", Style.smallCaps("<sc>a</sc> b", false));
    }
}
