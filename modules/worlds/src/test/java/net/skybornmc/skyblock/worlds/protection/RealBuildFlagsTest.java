package net.skybornmc.skyblock.worlds.protection;

import net.skybornmc.skyblock.common.builds.BuildBox;
import net.skybornmc.skyblock.common.builds.BuildRegion;
import net.skybornmc.skyblock.common.region.FlagRegion;
import net.skybornmc.skyblock.common.region.RegionFlag;
import net.skybornmc.skyblock.worlds.WorldsConfig;
import net.skybornmc.skyblock.worlds.builds.BuildMeta;
import net.skybornmc.skyblock.worlds.builds.BuildMetaParser;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The shipped flag defaults against the tracked builds in server/builds (what the server applies). */
class RealBuildFlagsTest {

    private static final File FOLDER = new File("../../server/builds");
    private static FlagIndex index;

    @BeforeAll
    static void build() {
        WorldsConfig c = new WorldsConfig();
        FlagRules rules = FlagRules.parse(c.protectionWorldFlags, c.protectionKindFlags, c.protectionRegionKinds, p -> {
            throw new AssertionError(p);
        });
        List<FlagIndex.Source> sources = new ArrayList<>();
        for (String id : List.of("depths_combat_t01_graveyard_v1", "depths_combat_t02_crypt_v1", "depths_gate_v1",
                "depths_mine_t01_coal_v1", "spawn_base_v1", "spawn_forge_quarter_v1")) {
            BuildMeta meta = BuildMetaParser.parse(id, YamlConfiguration.loadConfiguration(new File(FOLDER, id + ".yml")), p -> {
            });
            for (BuildRegion r : meta.regions()) {
                if (r.category().equals("regions")) {
                    sources.add(new FlagIndex.Source(r.build(), new FlagRegion(r.id(), r.kind(), r.world(), r.box())));
                }
            }
        }
        // What the depths module hands in: a combat zone and an ore field.
        sources.add(new FlagIndex.Source("depths", new FlagRegion("combat_t01", "combat_zone", "depths", new BuildBox(-48, 50, -160, 31, 169, -81))));
        sources.add(new FlagIndex.Source("depths", new FlagRegion("mine_t01_coal_field", "field", "depths", new BuildBox(-150, 99, -20, -95, 104, 20))));
        index = FlagIndex.build(rules, sources);
    }

    @Test
    void graveyardSafeZonesStopPveBothWays() {
        // arrival pad (-8,100,-89) in arrival_safe; lychgate rail band; exit path.
        for (int[] p : new int[][]{{-8, 100, -89}, {-8, 100, -93}, {11, 100, -143}}) {
            assertTrue(index.denied("depths", p[0], p[1], p[2], RegionFlag.PVE_DEALT));
            assertTrue(index.denied("depths", p[0], p[1], p[2], RegionFlag.PVE_TAKEN));
            assertTrue(index.denied("depths", p[0], p[1], p[2], RegionFlag.MOB_TARGET));
        }
        // The cemetery floor: fight freely, but no pearls.
        assertFalse(index.denied("depths", -20, 100, -120, RegionFlag.PVE_DEALT));
        assertFalse(index.denied("depths", -20, 100, -120, RegionFlag.MOB_TARGET));
        assertTrue(index.denied("depths", -20, 100, -120, RegionFlag.ENDER_PEARL));
        assertTrue(index.denied("depths", -20, 100, -120, RegionFlag.WIND_CHARGE));
        assertTrue(index.denied("depths", -20, 100, -120, RegionFlag.BUILD));
    }

    @Test
    void cryptSafeZonesOnlyStopTargetsAndBandsStopDamage() {
        // forecourt (arrival_safe): mobs ignore you, but the crypt says no-PvE only on the rail bands.
        assertTrue(index.denied("depths", -8, 100, -200, RegionFlag.MOB_TARGET));
        assertFalse(index.denied("depths", -8, 100, -200, RegionFlag.PVE_DEALT));
        // west rail band (no_pve_damage) below the arrival_safe entrance.
        assertTrue(index.denied("depths", -28, 100, -206, RegionFlag.PVE_DEALT));
        assertTrue(index.denied("depths", -8, 100, -260, RegionFlag.PVE_DEALT), "north band");
    }

    @Test
    void forgeQuarterLocksWorkstationsAndContainers() {
        assertTrue(index.denied("spawn", 74, 99, 11, RegionFlag.WORKSTATIONS), "anvil");
        assertTrue(index.denied("spawn", 66, 100, 32, RegionFlag.WORKSTATIONS), "enchanting table");
        assertTrue(index.denied("spawn", 50, 99, -4, RegionFlag.CONTAINERS), "barrel");
        assertTrue(index.regionsAt("spawn", 74, 99, 11).contains("spawn_forge_quarter_v1:forge_quarter_plot (forge_quarter, 30)"));
        assertFalse(index.denied("spawn", 200, 100, 200, RegionFlag.WORKSTATIONS), "outside spawn_protected: world flags only");
        assertTrue(index.denied("spawn", 200, 100, 200, RegionFlag.INTERACT));
        assertTrue(index.denied("spawn", 0, 100, 0, RegionFlag.BUILD), "spawn stays protected");
        assertTrue(index.denied("spawn", 0, 100, 0, RegionFlag.INTERACT));
    }

    @Test
    void depthsIsProtectedExceptFields() {
        assertTrue(index.denied("depths", 7, 100, 14, RegionFlag.BUILD), "The Gate arrival pad");
        assertTrue(index.denied("depths", 500, 100, 500, RegionFlag.BUILD), "empty void far away");
        assertTrue(index.denied("depths", 7, 100, 14, RegionFlag.CONTAINERS));
        assertFalse(index.denied("depths", -120, 100, 0, RegionFlag.BUILD), "ore field: the engine decides");
        assertTrue(index.denied("depths", -155, 100, 0, RegionFlag.BUILD), "coal zone outside the field");
        assertFalse(index.denied("depths", 7, 100, 14, RegionFlag.ENDER_PEARL), "pearls are fine at The Gate");
        assertFalse(index.denied("islands", 0, 100, 0, RegionFlag.BUILD));
    }
}
