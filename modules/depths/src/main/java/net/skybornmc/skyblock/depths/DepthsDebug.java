package net.skybornmc.skyblock.depths;

import net.skybornmc.skyblock.common.text.ModuleMessages;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.depths.engine.DepthsEngine;
import net.skybornmc.skyblock.depths.engine.DepthsRegion;
import net.skybornmc.skyblock.depths.engine.ResourceBlock;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.Locale;

/** {@code /depths debug [player]}: region at the target's feet, the block they look at, depleted counts, engine numbers. */
final class DepthsDebug {

    private static final int LOOK_RANGE = 6;

    private DepthsDebug() {
    }

    static void show(DepthsEngine engine, ModuleMessages msg, Player viewer, Player target) {
        msg.send(viewer, "debug.header", Msg.p("player", target.getName()));
        DepthsRegion region = engine.regionAt(target.getLocation());
        if (region == null) {
            msg.send(viewer, "debug.no-region", Msg.p("world", target.getWorld().getName()));
        } else {
            msg.send(viewer, "debug.region", Msg.p("region", region.id()), Msg.p("world", region.world()),
                    Msg.p("min", region.minX() + " " + region.minY() + " " + region.minZ()),
                    Msg.p("max", region.maxX() + " " + region.maxY() + " " + region.maxZ()),
                    Msg.p("resources", region.resources().size()), Msg.p("source", region.source()));
        }
        Block looked = target.getTargetBlockExact(LOOK_RANGE);
        if (looked != null) {
            DepthsRegion at = engine.regionAt(looked.getLocation());
            ResourceBlock res = at == null ? null : at.resource(looked.getType());
            String block = looked.getType().name().toLowerCase(Locale.ROOT);
            if (res == null) {
                msg.send(viewer, "debug.target-plain", Msg.p("block", block));
            } else {
                msg.send(viewer, engine.isDepleted(target, looked) ? "debug.target-depleted" : "debug.target-resource",
                        Msg.p("block", block), Msg.p("depleted", res.depleted().getAsString()),
                        Msg.p("seconds", res.respawnTicks() / 20.0));
            }
        }
        DepthsEngine.Stats s = engine.stats();
        int[] flags = engine.flags(target);
        msg.send(viewer, "debug.depleted", Msg.p("player", target.getName()), Msg.p("count", engine.depletedCount(target)),
                Msg.p("total", s.depletedTotal()), Msg.p("players", s.players()));
        msg.send(viewer, "debug.timers", Msg.p("timers", s.timers()), Msg.p("capacity", s.timerCapacity()),
                Msg.p("slots", s.timerSlots()), Msg.p("regions", s.regions()));
        msg.send(viewer, "debug.perf", Msg.p("avg", String.format(Locale.ROOT, "%.4f", s.avgBreakMillis())),
                Msg.p("max", String.format(Locale.ROOT, "%.4f", s.maxBreakMillis())), Msg.p("breaks", s.breaks()));
        msg.send(viewer, "debug.flags", Msg.p("player", target.getName()), Msg.p("window", flags[0]),
                Msg.p("total", flags[1]), Msg.p("all", s.flagsTotal()));
    }

    /** DEPTHSPROT: the region flags at the target's feet (regions there and the flags they deny). */
    static void flags(net.skybornmc.skyblock.common.region.RegionFlags flags, ModuleMessages msg, Player viewer, Player target) {
        if (flags == null) {
            msg.send(viewer, "debug.region-flags-off");
            return;
        }
        org.bukkit.Location l = target.getLocation();
        String world = l.getWorld().getName();
        java.util.List<String> regions = flags.regionsAt(world, l.getBlockX(), l.getBlockY(), l.getBlockZ());
        java.util.List<String> denied = flags.deniedAt(world, l.getBlockX(), l.getBlockY(), l.getBlockZ());
        msg.send(viewer, "debug.region-flags", Msg.p("regions", regions.isEmpty() ? "-" : String.join(", ", regions)),
                Msg.p("denied", denied.isEmpty() ? "-" : String.join(", ", denied)),
                Msg.p("bypass", msg.messages().raw("depths", flags.bypasses(target, world) ? "debug.yes" : "debug.no")));
    }
}
