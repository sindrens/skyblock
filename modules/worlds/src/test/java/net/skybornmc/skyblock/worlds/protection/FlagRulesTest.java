package net.skybornmc.skyblock.worlds.protection;

import net.skybornmc.skyblock.common.region.RegionFlag;
import net.skybornmc.skyblock.worlds.WorldsConfig;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlagRulesTest {

    @Test
    void parsesWorldKindAndOverrideLines() {
        List<String> warnings = new ArrayList<>();
        FlagRules rules = FlagRules.parse(
                List.of("Depths: build, interact"),
                List.of("safe 20: pve-taken, mob-target", "field 100: allow build", "mixed: build, allow fire, deny explosions", "plain: build"),
                List.of("spawn_forge_quarter_v1:forge_quarter_plot=safe", "arrival_safe=field"),
                warnings::add);
        assertTrue(warnings.isEmpty(), warnings.toString());
        byte[] depths = rules.world("depths");
        assertEquals(FlagRules.DENY, depths[RegionFlag.BUILD.ordinal()]);
        assertEquals(FlagRules.DENY, depths[RegionFlag.INTERACT.ordinal()]);
        assertEquals(FlagRules.NONE, depths[RegionFlag.FIRE.ordinal()]);
        assertNull(rules.world("islands"));

        FlagRules.KindRule safe = rules.kind("safe").orElseThrow();
        assertEquals(20, safe.priority());
        assertEquals(FlagRules.DENY, safe.get(RegionFlag.MOB_TARGET));
        assertEquals(FlagRules.NONE, safe.get(RegionFlag.BUILD));
        assertEquals(FlagRules.ALLOW, rules.kind("field").orElseThrow().get(RegionFlag.BUILD));
        FlagRules.KindRule mixed = rules.kind("mixed").orElseThrow();
        assertEquals(FlagRules.DENY, mixed.get(RegionFlag.BUILD));
        assertEquals(FlagRules.ALLOW, mixed.get(RegionFlag.FIRE));
        assertEquals(FlagRules.DENY, mixed.get(RegionFlag.EXPLOSIONS));
        assertEquals(FlagRules.DEFAULT_PRIORITY, rules.kind("plain").orElseThrow().priority());

        assertEquals("safe", rules.kindOf("spawn_forge_quarter_v1", "forge_quarter_plot", "plot"));
        assertEquals("plot", rules.kindOf("other_build", "forge_quarter_plot", "plot"));
        assertEquals("field", rules.kindOf("any_build", "arrival_safe", "safe"));
        assertEquals("protected", rules.kindOf("b", "zone", "Protected"));
        assertEquals("", rules.kindOf("b", "zone", null));
    }

    @Test
    void reportsBrokenLines() {
        List<String> warnings = new ArrayList<>();
        FlagRules rules = FlagRules.parse(List.of("no colon here", "spawn: build, flying"), List.of("bad x: build", ": build"),
                List.of("nokind=", "zone=unknown_kind"), warnings::add);
        assertEquals(6, warnings.size(), warnings.toString());
        assertEquals(FlagRules.DENY, rules.world("spawn")[RegionFlag.BUILD.ordinal()]);
        assertEquals(FlagRules.DEFAULT_PRIORITY, rules.kind("bad").orElseThrow().priority());
    }

    @Test
    void flagIdsAcceptUnderscores() {
        assertEquals(RegionFlag.PVE_DEALT, RegionFlag.byId("PVE_DEALT").orElseThrow());
        assertEquals(RegionFlag.ENTITY_EDIT, RegionFlag.byId(" entity-edit ").orElseThrow());
        assertTrue(RegionFlag.byId("nope").isEmpty());
        assertTrue(RegionFlag.BUILD.bypassable());
        assertTrue(!RegionFlag.ENDER_PEARL.bypassable());
    }

    @Test
    void shippedDefaultsParseWithoutWarnings() {
        WorldsConfig c = new WorldsConfig();
        List<String> warnings = new ArrayList<>();
        FlagRules rules = FlagRules.parse(c.protectionWorldFlags, c.protectionKindFlags, c.protectionRegionKinds, warnings::add);
        BypassRule.parse(c.protectionBypassPermission, c.protectionWorldBypass, c.protectionBypassNeedsCreative, warnings::add);
        assertTrue(warnings.isEmpty(), warnings.toString());
        assertEquals(FlagRules.DENY, rules.world("depths")[RegionFlag.CONTAINERS.ordinal()]);
        assertEquals(FlagRules.DENY, rules.world("warzone")[RegionFlag.BUILD.ordinal()]);
        assertNull(rules.world("islands"), "islands are protected by the islands module");
        // Gameplay flags are never world-wide by default.
        for (String w : List.of("spawn", "depths", "warzone")) {
            assertEquals(FlagRules.NONE, rules.world(w)[RegionFlag.ENDER_PEARL.ordinal()]);
            assertEquals(FlagRules.NONE, rules.world(w)[RegionFlag.PVE_DEALT.ordinal()]);
        }
    }
}
