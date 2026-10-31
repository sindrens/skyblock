package net.skybornmc.skyblock.chat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * PERF7: the tab header/footer is only built and sent when its key changes, so the key must change with every input
 * of the texts (and stay equal when none changed).
 */
class TabHeaderKeyTest {

    private static String sent(long gen, boolean bedrock, int visible, int max, String season, String staff) {
        return TabService.sentKey(gen, TabService.headerKey(bedrock, visible, max, season), TabService.footerKey(bedrock, staff));
    }

    @Test
    void equalInputsGiveEqualKeys() {
        assertEquals(sent(1, false, 3, 300, "Season 1", ""), sent(1, false, 3, 300, "Season 1", ""));
        String staff = TabService.staffKey("good", "20.0", 42);
        assertEquals(sent(1, false, 3, 300, "S", staff), sent(1, false, 3, 300, "S", TabService.staffKey("good", "20.0", 42)));
    }

    @Test
    void everyInputChangesTheKey() {
        String base = sent(1, false, 3, 300, "Season 1", "");
        assertNotEquals(base, sent(2, false, 3, 300, "Season 1", ""), "messages reload");
        assertNotEquals(base, sent(1, true, 3, 300, "Season 1", ""), "Bedrock");
        assertNotEquals(base, sent(1, false, 4, 300, "Season 1", ""), "online count");
        assertNotEquals(base, sent(1, false, 3, 200, "Season 1", ""), "max players");
        assertNotEquals(base, sent(1, false, 3, 300, "Season 2", ""), "season");
        assertNotEquals(base, sent(1, false, 3, 300, "Season 1", TabService.staffKey("good", "20.0", 42)), "staff line");
        String staff = TabService.staffKey("good", "20.0", 42);
        assertNotEquals(staff, TabService.staffKey("ok", "20.0", 42), "tps tier");
        assertNotEquals(staff, TabService.staffKey("good", "19.9", 42), "tps value");
        assertNotEquals(staff, TabService.staffKey("good", "20.0", 43), "ping");
    }

    @Test
    void separatorsKeepPartsApart() {
        // "1|23" vs "12|3": a season made of digits must not collide with the count
        assertNotEquals(TabService.headerKey(false, 1, 23, "4"), TabService.headerKey(false, 12, 3, "4"));
        assertNotEquals(TabService.footerKey(false, ""), TabService.footerKey(true, ""));
    }
}
