package net.skybornmc.skyblock.npcs;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoleRulesTest {

    @Test
    void defaultsParseWithoutProblems() {
        NpcsConfig c = new NpcsConfig();
        RoleRules rules = RoleRules.parse(c.npcRoles, c.roleActions);
        assertEquals(List.of(), rules.problems());
        assertEquals("forge", rules.roleOf("blacksmith", null));
        assertEquals("auction", rules.roleOf("ah", null));
        assertEquals("bank", rules.roleOf("bank", null), "an id without a mapping is its own role");
        assertEquals(RoleRules.Type.COMMAND, rules.actionOf("bank").type());
        assertEquals("bank", rules.actionOf("bank").command());
        assertEquals("ah", rules.actionOf("auction").command());
    }

    @Test
    void metadataRoleWins() {
        RoleRules rules = RoleRules.parse(List.of("bank: shop"), List.of());
        assertEquals("sell", rules.roleOf("bank", "Sell"));
        assertEquals("shop", rules.roleOf("bank", ""));
        assertEquals("shop", rules.roleOf("bank", "bad role!"), "invalid metadata role falls back");
    }

    @Test
    void unknownRoleIsHelpOnly() {
        RoleRules.Action a = RoleRules.parse(List.of(), List.of()).actionOf("mystery");
        assertEquals(RoleRules.Type.HELP, a.type());
        assertFalse(a.hasCommand());
    }

    @Test
    void helpWithOpenCommandAndSlashStripped() {
        RoleRules rules = RoleRules.parse(List.of(), List.of("guide: help /menu", "bank: command /bank"));
        assertEquals(new RoleRules.Action("guide", RoleRules.Type.HELP, "menu"), rules.actionOf("guide"));
        assertTrue(rules.actionOf("guide").hasCommand());
        assertEquals("bank", rules.actionOf("bank").command());
        assertEquals(List.of(), rules.problems());
    }

    @Test
    void brokenLinesAreReportedAndSkipped() {
        RoleRules rules = RoleRules.parse(List.of("nocolon", "x: Bad Role"),
                List.of("a: command", "b: teleport spawn", "c: command op Steve; stop", "d: command minecraft:give",
                        "e: command shop", "e: command sell"));
        assertEquals(7, rules.problems().size(), rules.problems().toString());
        assertEquals(RoleRules.Type.HELP, rules.actionOf("a").type());
        assertEquals(RoleRules.Type.HELP, rules.actionOf("c").type(), "unsafe command refused");
        assertEquals(RoleRules.Type.HELP, rules.actionOf("d").type(), "namespaced command refused");
        assertEquals("sell", rules.actionOf("e").command(), "last line wins");
    }

    @Test
    void decide() {
        RoleRules.Action run = new RoleRules.Action("bank", RoleRules.Type.COMMAND, "bank");
        RoleRules.Action help = new RoleRules.Action("guide", RoleRules.Type.HELP, "menu");
        assertEquals(RoleRules.Decision.RUN, RoleRules.decide(run, false, true));
        assertEquals(RoleRules.Decision.HELP, RoleRules.decide(run, true, true), "sneak = help");
        assertEquals(RoleRules.Decision.RUN, RoleRules.decide(run, true, false), "sneak-for-help off");
        assertEquals(RoleRules.Decision.HELP, RoleRules.decide(help, false, true));
    }
}
