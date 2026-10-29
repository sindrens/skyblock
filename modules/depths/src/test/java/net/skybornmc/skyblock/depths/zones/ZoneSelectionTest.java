package net.skybornmc.skyblock.depths.zones;

import net.skybornmc.skyblock.common.skills.SkillType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZoneSelectionTest {

    private final ZoneSet set = new ZoneLayout(ZoneLayout.Params.defaults()).build("depths");

    private static ZoneSet withBuilt(ZoneSet set, String id) {
        List<Zone> zones = new ArrayList<>();
        for (Zone z : set.zones()) {
            zones.add(!z.id().equals(id) ? z : new Zone(z.id(), z.aspect(), z.tier(), z.name(), z.resourceName(), z.requiredLevel(),
                    true, z.region(), z.spawn(), z.gate(), z.exit(), z.causeway(), z.fields(), z.arena(), z.biome(), z.palette()));
        }
        Hub h = set.hub();
        Hub builtHub = new Hub(h.name(), true, h.region(), h.spawn(), h.wingGates(), h.palette());
        return new ZoneSet(set.world(), set.floorY(), builtHub, set.arena(), zones);
    }

    @Test
    void allSelectsEverythingNotBuilt() {
        ZoneCommands.Selection all = ZoneCommands.select(set, "all");
        // hub + 33 zones with causeways + arena
        assertEquals(1 + 33 * 2 + 1, all.parts().size());
        assertTrue(all.skipped().isEmpty());

        ZoneSet built = withBuilt(set, "mine_t04");
        ZoneCommands.Selection sel = ZoneCommands.select(built, "ALL");
        assertEquals(List.of("gate", "mine_t04"), sel.skipped());
        assertEquals(32 * 2 + 1, sel.parts().size());
        assertFalse(sel.parts().stream().anyMatch(p -> p.label().startsWith("mine_t04")), "builder work never touched");
        assertFalse(sel.parts().stream().anyMatch(p -> p.label().equals("gate")));
    }

    @Test
    void oneTarget() {
        ZoneCommands.Selection one = ZoneCommands.select(set, "forest_t03");
        assertEquals(List.of("forest_t03 causeway", "forest_t03"), one.parts().stream().map(Shapes.Part::label).toList());
        assertEquals("spruce_log", one.parts().getLast().resource());
        assertEquals("snowy_taiga", one.parts().getLast().biome());
        assertEquals(List.of("boss_arena"), ZoneCommands.select(set, "boss_arena").parts().stream().map(Shapes.Part::label).toList());
        assertEquals(List.of("gate"), ZoneCommands.select(set, "gate").parts().stream().map(Shapes.Part::label).toList());
        assertTrue(ZoneCommands.select(withBuilt(set, "mine_t01"), "mine_t01").parts().isEmpty());
    }

    @Test
    void accessFollowsTheSkillLevel() {
        Zone gold = set.zone("mine_t04").orElseThrow();
        assertEquals(15, gold.requiredLevel());
        ZoneAccess low = ZoneAccess.of(gold, 9);
        assertFalse(low.allowed());
        assertEquals(SkillType.MINING, low.skill());
        assertEquals(9, low.level());
        assertTrue(ZoneAccess.of(gold, 15).allowed());
        ZoneAccess noSkills = ZoneAccess.of(gold, null);
        assertTrue(noSkills.allowed(), "no skill system: zones are open");
        assertFalse(noSkills.checked());
        assertEquals(SkillType.FORAGING, ZoneAccess.of(set.zone("forest_t01").orElseThrow(), 0).skill());
        assertEquals(SkillType.COMBAT, ZoneAccess.of(set.zone("combat_t01").orElseThrow(), 0).skill());
    }
}
