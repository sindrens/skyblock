package net.skybornmc.skyblock.logs;

import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.audit.AuditCategory;
import net.skybornmc.skyblock.common.audit.AuditEntry;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** R3-9b / owner answer 318: helpers read chat but never DMs; mods and up keep them. */
class DmPrivacyTest {

    private static final List<String> DM = new LogsConfig().dmCommands;

    private static AuditEntry command(String line) {
        return new AuditEntry(1, 1L, 0, "phoenix", AuditCategory.COMMAND, "command", new Actor(UUID.randomUUID(), "Steve"), null, null,
                null, null, null, null, null, Map.of("line", line, "cancelled", false));
    }

    @Test
    void dmsHaveTheirOwnCategory() {
        assertEquals("dm", AuditCategory.DM.key());
        assertEquals(AuditCategory.DM, AuditCategory.byKey("dm").orElseThrow());
    }

    @Test
    void dmCommandLinesAreHidden() {
        assertEquals("/msg (hidden)", LogText.hideDm(command("/msg Alex meet me at 100 64 -20"), DM).details().get("line"));
        assertEquals("/tell (hidden)", LogText.hideDm(command("/minecraft:tell Alex hi"), DM).details().get("line"));
        assertEquals("/r (hidden)", LogText.hideDm(command("/R secret"), DM).details().get("line"));
        assertEquals(false, LogText.hideDm(command("/msg Alex hi"), DM).details().get("cancelled"), "other details stay");
    }

    @Test
    void otherCommandsAndCategoriesAreUnchanged() {
        AuditEntry home = command("/is home");
        assertSame(home, LogText.hideDm(home, DM));
        AuditEntry message = command("/msgtoggle");
        assertSame(message, LogText.hideDm(message, DM), "only exact labels");
        AuditEntry chat = new AuditEntry(2, 1L, 0, "phoenix", AuditCategory.CHAT, "chat", new Actor(UUID.randomUUID(), "Steve"), null,
                null, null, null, null, null, null, Map.of("message", "/msg looks like a command"));
        assertSame(chat, LogText.hideDm(chat, DM));
    }

    @Test
    void labelParsing() {
        assertEquals("msg", LogText.commandLabel("/skyblock:MSG bob hi"));
        assertEquals("is", LogText.commandLabel("is home"));
        assertEquals("", LogText.commandLabel(null));
    }

    @Test
    void helpersAreNotGrantedDmsButModsAre() throws IOException {
        List<String> lines = Files.readAllLines(Path.of("../../server/permissions/logs.txt"));
        assertFalse(lines.stream().anyMatch(l -> l.startsWith("lp group helper permission set skyblock.staff.logs.category.dm")));
        assertFalse(lines.stream().anyMatch(l -> l.startsWith("lp group helper permission set skyblock.staff.logs.category.*")));
        assertTrue(lines.contains("lp group helper permission set skyblock.staff.logs.category.chat true"), "helpers keep chat");
        assertTrue(lines.contains("lp group mod permission set skyblock.staff.logs.category.dm true"), "mods and up keep DMs");
    }
}
