package net.skybornmc.skyblock.depths.zones;

import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.text.ModuleMessages;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.depths.DepthsConfig;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;

/** {@code /depths generate [zone|all]} and {@code /depths tp <zone>}. */
public final class ZoneCommands {

    /** Value of {@code /depths generate} that builds everything not marked built. */
    public static final String ALL = "all";

    private final ZoneManager zones;
    private final ZoneGenerator generator;
    private final DepthsConfig config;
    private final ModuleMessages msg;
    /** Runs before a staff teleport into a zone (M4.6: a pass through that zone's tier lock). */
    private BiConsumer<Player, String> beforeZoneTeleport = (player, zone) -> {
    };

    public ZoneCommands(ZoneManager zones, ZoneGenerator generator, DepthsConfig config, ModuleMessages msg) {
        this.zones = zones;
        this.generator = generator;
        this.config = config;
        this.msg = msg;
    }

    /** Tab completion / valid values of {@code /depths generate}: all, gate, every zone, boss_arena. */
    public List<String> generateTargets() {
        List<String> ids = new ArrayList<>();
        ids.add(ALL);
        ids.addAll(zones.teleportIds());
        return ids;
    }

    /** What {@code target} selects: the parts to build and the ids skipped because they are built. */
    record Selection(List<Shapes.Part> parts, List<String> skipped) {
    }

    static Selection select(ZoneSet set, String target) {
        List<Shapes.Part> parts = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        boolean all = ALL.equalsIgnoreCase(target);
        if (set.hub() != null && (all || Hub.ID.equalsIgnoreCase(target))) {
            if (set.hub().built()) {
                skipped.add(Hub.ID);
            } else {
                parts.add(Shapes.hub(set.hub()));
            }
        }
        for (Zone z : set.zones()) {
            if (all || z.id().equalsIgnoreCase(target)) {
                if (z.built()) {
                    skipped.add(z.id());
                } else {
                    parts.addAll(Shapes.zone(z, set.arena()));
                }
            }
        }
        if (set.arena() != null && (all || BossArena.ID.equalsIgnoreCase(target))) {
            if (set.arena().built()) {
                skipped.add(BossArena.ID);
            } else {
                parts.add(Shapes.arena(set.arena()));
            }
        }
        return new Selection(parts, skipped);
    }

    public void generate(CommandSender sender, String target) {
        ZoneSet set = zones.set();
        World world = Bukkit.getWorld(set.world());
        if (world == null) {
            msg.send(sender, "zones.no-world", Msg.p("world", set.world()));
            return;
        }
        if (generator.running()) {
            int[] p = generator.progress();
            msg.send(sender, "zones.generate.busy", Msg.p("done", p[0]), Msg.p("total", p[1]), Msg.p("percent", p[2]));
            return;
        }
        Selection sel = select(set, target);
        if (!sel.skipped().isEmpty()) {
            msg.send(sender, "zones.generate.skipped-built", Msg.p("zones", String.join(", ", sel.skipped())));
        }
        if (sel.parts().isEmpty()) {
            msg.send(sender, "zones.generate.nothing");
            return;
        }
        generator.start(sel.parts(), world, sender instanceof Player p ? p : null, Actor.of(sender), target.toLowerCase(java.util.Locale.ROOT));
        msg.send(sender, "zones.generate.start", Msg.p("parts", sel.parts().size()), Msg.p("world", world.getName()),
                Msg.p("blocks", config.generateBlocksPerTick));
    }

    /** {@code /depths tp} ignores tier locks: {@code hook} grants the pass (zone id) right before the teleport. */
    public void beforeZoneTeleport(BiConsumer<Player, String> hook) {
        this.beforeZoneTeleport = hook;
    }

    public void teleport(Player player, String id) {
        Optional<Point> point = zones.spawnOf(id);
        Location target = point.map(zones::location).orElse(null);
        if (target == null) {
            msg.send(player, point.isEmpty() ? "zones.tp.failed" : "zones.no-world", Msg.p("zone", id), Msg.p("world", zones.world()));
            return;
        }
        String name = nameOf(id);
        zones.zone(id).ifPresent(zone -> beforeZoneTeleport.accept(player, zone.id()));
        player.teleportAsync(target).thenAccept(ok -> msg.send(player, ok ? "zones.tp.done" : "zones.tp.failed",
                Msg.p("zone", id), Msg.p("name", name)));
    }

    private String nameOf(String id) {
        ZoneSet set = zones.set();
        if (Hub.ID.equalsIgnoreCase(id) && set.hub() != null) {
            return set.hub().name();
        }
        if (BossArena.ID.equalsIgnoreCase(id) && set.arena() != null) {
            return set.arena().name();
        }
        return set.zone(id).map(z -> z.aspect().id() + " " + z.tier() + " · " + z.name()).orElse(id);
    }
}
