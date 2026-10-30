package net.skybornmc.skyblock.worlds.portals;

import net.skybornmc.skyblock.common.builds.BuildBox;
import net.skybornmc.skyblock.common.builds.BuildPoint;
import net.skybornmc.skyblock.common.builds.BuildPoints;
import net.skybornmc.skyblock.common.builds.BuildRegion;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reads the portals of the applied builds from {@link BuildPoints} (pure, unit-tested). */
public final class Portals {

    private static final Pattern NUMBER_ENTRY = Pattern.compile("([a-z_]+)\\s*:\\s*(-?[0-9]+(?:\\.[0-9]+)?)");
    /** A particle counts for a portal when it lies this many blocks around its trigger (ids not matching). */
    static final int PARTICLE_REACH = 2;

    private Portals() {
    }

    /** Every portal of every applied build, sorted by build and id. */
    public static List<Portal> from(BuildPoints points) {
        List<BuildPoint> particles = points.points("particles");
        List<Portal> out = new ArrayList<>();
        for (BuildRegion r : points.regions("portals")) {
            out.add(of(r, particles));
        }
        out.sort(Comparator.comparing(Portal::build).thenComparing(Portal::id));
        return List.copyOf(out);
    }

    static Portal of(BuildRegion r, List<BuildPoint> particles) {
        String target = r.extra().getOrDefault("target", "").trim().toLowerCase(Locale.ROOT);
        boolean open = !"closed".equalsIgnoreCase(r.extra().getOrDefault("status", "open").trim());
        return new Portal(r.build(), r.id(), r.world(), r.box(), target, open, landing(r), particle(r, particles));
    }

    /** {@code landing_tp} (exact feet position) or {@code landing} (a block: its center); facing away from the trigger. */
    static Portal.Pos landing(BuildRegion r) {
        Map<String, Double> tp = numbers(r.extra().get("landing_tp"));
        if (tp.containsKey("x") && tp.containsKey("y") && tp.containsKey("z")) {
            double x = tp.get("x");
            double z = tp.get("z");
            float yaw = tp.containsKey("yaw") ? tp.get("yaw").floatValue() : awayYaw(r.box(), x, z);
            float pitch = tp.containsKey("pitch") ? tp.get("pitch").floatValue() : 0f;
            return new Portal.Pos(x, tp.get("y"), z, yaw, pitch);
        }
        Map<String, Double> block = numbers(r.extra().get("landing"));
        if (block.containsKey("x") && block.containsKey("y") && block.containsKey("z")) {
            double x = Math.floor(block.get("x")) + 0.5;
            double z = Math.floor(block.get("z")) + 0.5;
            float yaw = block.containsKey("yaw") ? block.get("yaw").floatValue() : awayYaw(r.box(), x, z);
            return new Portal.Pos(x, Math.floor(block.get("y")), z, yaw, 0f);
        }
        return null;
    }

    /** Yaw looking from the trigger's center toward (x, z): players arrive facing away from the portal. */
    static float awayYaw(BuildBox box, double x, double z) {
        double cx = (box.minX() + box.maxX() + 1) / 2.0;
        double cz = (box.minZ() + box.maxZ() + 1) / 2.0;
        double dx = x - cx;
        double dz = z - cz;
        if (Math.abs(dx) < 1e-6 && Math.abs(dz) < 1e-6) {
            return 0f;
        }
        return (float) Math.toDegrees(Math.atan2(-dx, dz));
    }

    /**
     * The portal's particle: same build, id {@code portal_<portal id>} or the portal's {@code text} id, else a particle
     * whose id starts with {@code portal} within {@link #PARTICLE_REACH} blocks of the trigger.
     */
    static Portal.Particle particle(BuildRegion r, List<BuildPoint> particles) {
        String text = r.extra().get("text");
        BuildPoint near = null;
        for (BuildPoint p : particles) {
            if (!p.build().equals(r.build())) {
                continue;
            }
            if (p.id().equals("portal_" + r.id()) || p.id().equals(text)) {
                return toParticle(p);
            }
            if (near == null && p.id().startsWith("portal") && near(r.box(), p)) {
                near = p;
            }
        }
        return near == null ? null : toParticle(near);
    }

    private static boolean near(BuildBox b, BuildPoint p) {
        return p.blockX() >= b.minX() - PARTICLE_REACH && p.blockX() <= b.maxX() + PARTICLE_REACH
                && p.blockY() >= b.minY() - PARTICLE_REACH && p.blockY() <= b.maxY() + PARTICLE_REACH
                && p.blockZ() >= b.minZ() - PARTICLE_REACH && p.blockZ() <= b.maxZ() + PARTICLE_REACH;
    }

    private static Portal.Particle toParticle(BuildPoint p) {
        String type = p.extra("type", "").trim().toLowerCase(Locale.ROOT);
        String color = p.extra().get("color");
        boolean enabled = !"false".equalsIgnoreCase(p.extra("enabled", "true").trim());
        return new Portal.Particle(p.id(), p.x(), p.y(), p.z(), type, color, enabled && !type.isEmpty());
    }

    /** Numbers of a flat map text ({@code {x: -21.5, y: 102.5, yaw: -67}}); empty for {@code null}. */
    static Map<String, Double> numbers(String flat) {
        Map<String, Double> out = new LinkedHashMap<>();
        if (flat == null) {
            return out;
        }
        Matcher m = NUMBER_ENTRY.matcher(flat);
        while (m.find()) {
            out.putIfAbsent(m.group(1), Double.parseDouble(m.group(2)));
        }
        return out;
    }
}
