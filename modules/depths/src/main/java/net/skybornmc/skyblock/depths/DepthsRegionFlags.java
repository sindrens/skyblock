package net.skybornmc.skyblock.depths;

import net.skybornmc.skyblock.common.builds.BuildBox;
import net.skybornmc.skyblock.common.region.FlagRegion;
import net.skybornmc.skyblock.depths.engine.DepthsRegion;
import net.skybornmc.skyblock.depths.zones.BossArena;
import net.skybornmc.skyblock.depths.zones.Box;
import net.skybornmc.skyblock.depths.zones.Hub;
import net.skybornmc.skyblock.depths.zones.Zone;

import java.util.ArrayList;
import java.util.List;

/**
 * The Depths regions handed to the worlds module's region flags (DEPTHSPROT, pure, unit-tested). The Depths world is
 * protected as a whole ({@code worlds.yml → protection.world-flags}); every engine region (ore/log fields) becomes kind
 * {@value #FIELD} (allowed there: the per-player engine decides what breaks), every tier zone {@code <wing>_zone}
 * ({@code combat_zone}: no ender pearls or wind charges), The Gate {@value #HUB} and the boss arena
 * {@value #BOSS_ARENA}. Which flags a kind sets lives in {@code protection.kind-flags}.
 */
public final class DepthsRegionFlags {

    public static final String OWNER = "depths";
    public static final String FIELD = "field";
    public static final String HUB = "depths_hub";
    public static final String BOSS_ARENA = "boss_arena";

    private DepthsRegionFlags() {
    }

    /** Kind of a tier zone: {@code mine_zone}, {@code forest_zone}, {@code combat_zone}. */
    public static String zoneKind(Zone zone) {
        return zone.aspect().id() + "_zone";
    }

    public static List<FlagRegion> of(String world, List<DepthsRegion> fields, List<Zone> zones, Hub hub, BossArena arena) {
        List<FlagRegion> out = new ArrayList<>();
        for (DepthsRegion r : fields) {
            out.add(new FlagRegion(r.id(), FIELD, r.world(), new BuildBox(r.minX(), r.minY(), r.minZ(), r.maxX(), r.maxY(), r.maxZ())));
        }
        for (Zone z : zones) {
            out.add(new FlagRegion(z.id(), zoneKind(z), world, box(z.region())));
        }
        if (hub != null) {
            out.add(new FlagRegion(Hub.ID, HUB, world, box(hub.region())));
        }
        if (arena != null) {
            out.add(new FlagRegion(BossArena.ID, BOSS_ARENA, world, box(arena.region())));
        }
        return out;
    }

    static BuildBox box(Box b) {
        return new BuildBox(b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ());
    }
}
