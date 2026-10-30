package net.skybornmc.skyblock.logs;

import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.audit.AuditCategory;
import net.skybornmc.skyblock.common.audit.AuditEntry;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Punish from logs (Phase P spec 13.11), the logs side: which entries open the punish menu and with which command. */
class LogPunishTest {

    private static final UUID STEVE = UUID.fromString("00000000-0000-0000-0000-00000000000a");

    private static AuditEntry entry(long id, AuditCategory category, String action, Actor actor) {
        return new AuditEntry(id, 1L, 0, "phoenix", category, action, actor, null, null, null, null, null, null, null,
                Map.of("message", "hello"));
    }

    @Test
    void chatLinesOfPlayersOpenThePunishMenuWithEvidence() {
        AuditEntry chat = entry(1234, AuditCategory.CHAT, "chat", new Actor(STEVE, "Steve"));
        assertTrue(LogPunish.punishable(chat));
        assertEquals("p Steve --evidence 1234", LogPunish.command(chat));
        AuditEntry blocked = entry(55, AuditCategory.CHAT, "chat_blocked", new Actor(STEVE, ".BedrockSteve"));
        assertEquals("p .BedrockSteve --evidence 55", LogPunish.command(blocked), "Bedrock names work");
    }

    @Test
    void everythingElseDoesNot() {
        assertNull(LogPunish.command(entry(1, AuditCategory.ECONOMY, "pay", new Actor(STEVE, "Steve"))), "not chat");
        assertNull(LogPunish.command(entry(2, AuditCategory.CHAT, "msg", new Actor(STEVE, "Steve"))), "only chat lines");
        assertNull(LogPunish.command(entry(3, AuditCategory.CHAT, "chat", new Actor(null, "CONSOLE"))), "no player");
        assertNull(LogPunish.command(entry(4, AuditCategory.CHAT, "chat", new Actor(STEVE, "Steve evil"))),
                "a name with a space could inject a second argument");
        assertFalse(LogPunish.punishable(null));
    }
}
