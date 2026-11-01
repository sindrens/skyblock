package net.skybornmc.skyblock.tutorial;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * The guide trail (A3-R0-02, owner 31 Oct "replace the fire trail"): a soft dotted line of coloured dust on the ground
 * that starts where the player stands and follows the walkable route ({@link GuidePath}) to the step's target, fading
 * with distance and slowly moving toward the target; one colour per kind of step (travel, island, NPC visit). Only the
 * player sees it. The route is searched once per step change, when the target moves or when the player leaves the trail
 * (at most {@code guide.trail.paths-per-run} searches per guide update for everyone, a node budget per search, shared
 * with players starting from the same spot); drawing reuses preallocated dust options and arrays (no allocation per
 * dot). Main thread.
 */
final class Trail {

    /** Per-player trail state (lives in the guide view). */
    static final class State {
        Route route;
        String step;
        String world;
        double goalX;
        double goalY;
        double goalZ;
        long lastSearch;
        int cursor;
        long frame;
        final double[] tmp = new double[3];
    }

    private record Cached(Route route, long until) {
    }

    /** Fade levels of the dust size (near to far). */
    private static final int LEVELS = 4;
    private static final double GOAL_RADIUS = 2.0;

    private final TutorialModule module;
    private int searchesThisRun;
    private List<String> colorSource;
    private String defaultColorSource;
    private double sizeSource = -1;
    private Map<String, Particle.DustOptions[]> dust = Map.of();
    private Particle.DustOptions[] defaultDust;
    private List<String> nodeSource;
    private List<TargetSpec.Point> nodes = List.of();
    private final Map<String, Cached> shared = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Cached> eldest) {
            return size() > 64;
        }
    };

    Trail(TutorialModule module) {
        this.module = module;
    }

    /** Start of a guide update: resets the search budget. */
    void newRun() {
        searchesThisRun = 0;
    }

    /** True when this step draws a trail at all. */
    boolean wanted(String step) {
        return step != null && module.config().trailSteps.stream().anyMatch(s -> s.strip().equalsIgnoreCase(step));
    }

    /**
     * Keeps the player's route up to date for the step's target (searched only when needed and within the budget);
     * clears it when the step has no trail, the target is in another world, too close or too far.
     */
    void update(Player player, String step, Optional<Location> target, State s) {
        TutorialConfig c = module.config();
        Location at = player.getLocation();
        if (!wanted(step) || target.isEmpty() || target.get().getWorld() == null || !target.get().getWorld().equals(at.getWorld())) {
            clear(s, step);
            return;
        }
        Location to = target.get();
        double dx = to.getX() - at.getX();
        double dz = to.getZ() - at.getZ();
        double flat = Math.sqrt(dx * dx + dz * dz);
        if (flat < c.trailMinDistance || flat > c.trailMaxDistance) {
            clear(s, step);
            return;
        }
        long now = System.currentTimeMillis();
        boolean stepChanged = !step.equals(s.step) || !at.getWorld().getName().equals(s.world);
        boolean goalMoved = s.route != null && (Math.abs(s.goalX - to.getX()) > 2 || Math.abs(s.goalY - to.getY()) > 2
                || Math.abs(s.goalZ - to.getZ()) > 2);
        boolean offRoute = false;
        if (s.route != null && !stepChanged) {
            s.cursor = s.route.nearest(at.getX(), at.getY(), at.getZ(), s.cursor, s.tmp);
            offRoute = s.tmp[0] > c.trailRepathDistance * c.trailRepathDistance;
        }
        boolean need = s.route == null || stepChanged || goalMoved || offRoute;
        if (!need) {
            return;
        }
        if (!stepChanged && now - s.lastSearch < c.trailRepathSeconds * 1000L) {
            return; // searched a moment ago: keep the old route (or none) until the wait is over
        }
        if (searchesThisRun >= c.trailPathsPerRun) {
            return; // the budget of this update is used; tried again on the next one
        }
        s.step = step;
        s.world = at.getWorld().getName();
        s.goalX = to.getX();
        s.goalY = to.getY();
        s.goalZ = to.getZ();
        s.lastSearch = now;
        s.cursor = 0;
        searchesThisRun++;
        s.route = module.core().scheduler().timer().time("tutorial-guides-path", () -> search(at, to, step));
        if (s.route != null) {
            s.cursor = s.route.nearest(at.getX(), at.getY(), at.getZ(), 0, s.tmp);
        }
    }

    private static void clear(State s, String step) {
        s.route = null;
        s.step = step;
        s.cursor = 0;
    }

    private Route search(Location at, Location to, String step) {
        World world = at.getWorld();
        GuidePath.Terrain terrain = terrain(world);
        int[] start = GuidePath.standing(terrain, at.getX(), at.getY(), at.getZ());
        double[] goal = {to.getX(), to.getY(), to.getZ()};
        Route route = null;
        if (start != null) {
            String key = world.getName() + '|' + start[0] + ',' + start[1] + ',' + start[2] + '|' + to.getBlockX() + ','
                    + to.getBlockY() + ',' + to.getBlockZ();
            long now = System.currentTimeMillis();
            Cached hit = shared.get(key);
            if (hit != null && hit.until() > now) {
                return hit.route();
            }
            route = GuidePath.find(start, goal, GOAL_RADIUS, terrain, module.config().trailMaxNodes);
            if (route != null && module.config().trailCacheSeconds > 0) {
                shared.put(key, new Cached(route, now + module.config().trailCacheSeconds * 1000L));
            }
        }
        if (route == null && Steps.PORTAL.equals(step)) {
            route = fallback(at); // the designer's road (trail.nodes) when no walkable path was found
        }
        return route;
    }

    /** The configured portal road, from the node nearest to the player on, when the player is near it. */
    private Route fallback(Location at) {
        List<TargetSpec.Point> pts = nodes();
        List<double[]> out = new java.util.ArrayList<>();
        for (TargetSpec.Point p : pts) {
            if (p.world() != null && p.world().equalsIgnoreCase(at.getWorld().getName())) {
                out.add(new double[]{p.x(), p.y(), p.z()});
            }
        }
        if (out.size() < 2) {
            return null;
        }
        Route road = Route.of(out);
        double[] d = new double[1];
        road.nearest(at.getX(), at.getY(), at.getZ(), 0, d);
        return d[0] > 40 * 40 ? null : road;
    }

    private List<TargetSpec.Point> nodes() {
        List<String> raw = module.config().trailNodes;
        if (raw != nodeSource) {
            List<TargetSpec.Point> out = new java.util.ArrayList<>();
            for (String node : raw) {
                TargetSpec.parse(node).ifPresent(spec -> {
                    if (spec instanceof TargetSpec.Point pt) {
                        out.add(pt);
                    }
                });
            }
            nodes = List.copyOf(out);
            nodeSource = raw;
        }
        return nodes;
    }

    /** The world for the path search: unloaded chunks are unknown (never loaded for a trail). */
    static GuidePath.Terrain terrain(World world) {
        int min = world.getMinHeight();
        int max = world.getMaxHeight();
        return (x, y, z) -> {
            if (y < min) {
                return GuidePath.BLOCKED;
            }
            if (y >= max) {
                return GuidePath.OPEN;
            }
            if (!world.isChunkLoaded(x >> 4, z >> 4)) {
                return GuidePath.UNKNOWN;
            }
            Block b = world.getBlockAt(x, y, z);
            if (b.isLiquid()) {
                return GuidePath.BLOCKED;
            }
            if (b.isPassable()) {
                return GuidePath.OPEN;
            }
            double h = b.getBoundingBox().getHeight();
            if (h <= 0.0001) {
                return GuidePath.OPEN;
            }
            if (h <= 0.5001) {
                return GuidePath.LOW;
            }
            return h <= 1.0001 ? GuidePath.SOLID : GuidePath.BLOCKED;
        };
    }

    /** Draws the dots from the player's spot on the route forward ({@code guide.trail.*}). No allocation per dot. */
    void draw(Player player, String step, State s) {
        Route r = s.route;
        if (r == null || !step.equals(s.step)) {
            return;
        }
        TutorialConfig c = module.config();
        Location at = player.getLocation();
        if (!at.getWorld().getName().equals(s.world)) {
            return;
        }
        s.cursor = r.nearest(at.getX(), at.getY(), at.getZ(), s.cursor, s.tmp);
        double start = r.alongAt(s.cursor);
        double spacing = Math.max(1, c.trailSpacing);
        double reach = c.trailAhead;
        // The dots creep forward a quarter spacing per draw, so the line seems to flow toward the target.
        double phase = c.trailAnimate ? (s.frame++ % 4) * spacing / 4 : 0;
        Particle.DustOptions[] colours = dust(step);
        int drawn = 0;
        for (double ahead = spacing * 0.5 + phase; ahead <= reach && drawn < c.trailMaxParticles; ahead += spacing) {
            double pos = start + ahead;
            if (pos > r.length()) {
                break;
            }
            r.pointAt(pos, s.tmp);
            int level = (int) Math.min(LEVELS - 1, ahead / reach * LEVELS);
            player.spawnParticle(Particle.DUST, s.tmp[0], s.tmp[1] + 0.15, s.tmp[2], 1, 0, 0, 0, 0, colours[level]);
            drawn++;
        }
    }

    /** The step's dust (nearest size first), rebuilt only when the colour or size settings change. */
    Particle.DustOptions[] dust(String step) {
        TutorialConfig c = module.config();
        if (c.trailColors != colorSource || !c.trailColor.equals(defaultColorSource) || c.trailDotSize != sizeSource) {
            float size = (float) c.trailDotSize;
            defaultDust = levels(parse(c.trailColor, Color.fromRGB(0xFFD21A)), size);
            Map<String, Particle.DustOptions[]> out = new HashMap<>();
            for (String line : c.trailColors) {
                int colon = line.indexOf(':');
                if (colon <= 0) {
                    continue;
                }
                Color colour = parse(line.substring(colon + 1).strip(), null);
                if (colour != null) {
                    out.put(line.substring(0, colon).strip().toLowerCase(Locale.ROOT), levels(colour, size));
                }
            }
            dust = Map.copyOf(out);
            colorSource = c.trailColors;
            defaultColorSource = c.trailColor;
            sizeSource = c.trailDotSize;
        }
        Particle.DustOptions[] found = step == null ? null : dust.get(step.toLowerCase(Locale.ROOT));
        return found != null ? found : defaultDust;
    }

    private static Particle.DustOptions[] levels(Color colour, float size) {
        Particle.DustOptions[] out = new Particle.DustOptions[LEVELS];
        for (int i = 0; i < LEVELS; i++) {
            out[i] = new Particle.DustOptions(colour, Route.fade((i + 0.5) / LEVELS, 1, size));
        }
        return out;
    }

    /** {@code #RRGGBB} (or {@code RRGGBB}) to a colour; {@code fallback} when it can't be read. */
    static Color parse(String raw, Color fallback) {
        if (raw == null) {
            return fallback;
        }
        String hex = raw.strip();
        if (hex.startsWith("#")) {
            hex = hex.substring(1);
        }
        if (hex.length() != 6) {
            return fallback;
        }
        try {
            return Color.fromRGB(Integer.parseInt(hex, 16));
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }
}
