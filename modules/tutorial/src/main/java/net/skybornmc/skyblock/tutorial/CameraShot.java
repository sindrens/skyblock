package net.skybornmc.skyblock.tutorial;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * One shot of a tutorial camera (TUTORIAL.md §9.4), from {@code tutorial.yml → cameras.<id>}:
 * {@code "<from> <to> look=<point> ticks=<n> [text=<key>]"}. A point is {@code world:x,y,z} or a relative anchor:
 * {@code @home:dx,dy,dz} (the player's island home), {@code @zone:dx,dy,dz} (the spawn of the tutorial's Depths zone)
 * or {@code @self:dx,dy,dz} (where the player stands). {@code text} names a title in
 * {@code messages → cameras.<camera>.<text>}. Pure, unit-tested.
 */
record CameraShot(CamPoint from, CamPoint to, CamPoint look, int ticks, String text) {

    /** Longest single shot (ticks). */
    static final int MAX_SHOT_TICKS = 400;

    /** A point of a shot: an anchor (world name, or {@code @home}, {@code @zone}, {@code @self}) and coordinates. */
    record CamPoint(String anchor, double x, double y, double z) {

        boolean relative() {
            return anchor.startsWith("@");
        }

        static Optional<CamPoint> parse(String raw) {
            return TargetSpec.parse(raw).flatMap(spec -> spec instanceof TargetSpec.Point p
                    ? Optional.of(new CamPoint(p.world().toLowerCase(Locale.ROOT).startsWith("@") ? p.world().toLowerCase(Locale.ROOT) : p.world(),
                    p.x(), p.y(), p.z()))
                    : Optional.empty());
        }
    }

    /** The shots of one camera and what was wrong with its lines. */
    record Parsed(List<CameraShot> shots, List<String> problems) {

        int totalTicks() {
            int sum = 0;
            for (CameraShot s : shots) {
                sum += s.ticks();
            }
            return sum;
        }

        /** Every anchor used by the shots ({@code @home}, a world name...). */
        List<String> anchors() {
            List<String> out = new ArrayList<>();
            for (CameraShot s : shots) {
                for (CamPoint p : List.of(s.from(), s.to(), s.look())) {
                    if (!out.contains(p.anchor())) {
                        out.add(p.anchor());
                    }
                }
            }
            return out;
        }
    }

    /**
     * Parses a camera's lines. Bad lines are skipped with a problem; a camera longer than {@code maxTicks} is cut (the
     * last shot is shortened, later shots dropped) so no camera ever runs longer than the limit (§9.4: max 8 s each).
     */
    static Parsed parse(String camera, List<String> lines, int maxTicks) {
        List<CameraShot> shots = new ArrayList<>();
        List<String> problems = new ArrayList<>();
        int total = 0;
        int index = 0;
        for (String raw : lines == null ? List.<String>of() : lines) {
            index++;
            String where = "cameras." + camera + " line " + index;
            if (raw == null || raw.isBlank()) {
                continue;
            }
            String[] tokens = raw.strip().split("\\s+");
            List<String> points = new ArrayList<>();
            String look = null;
            String text = null;
            Integer ticks = null;
            boolean bad = false;
            boolean unknown = false;
            for (String token : tokens) {
                int eq = token.indexOf('=');
                if (eq < 0) {
                    points.add(token);
                    continue;
                }
                String key = token.substring(0, eq).toLowerCase(Locale.ROOT);
                String value = token.substring(eq + 1);
                switch (key) {
                    case "look" -> look = value;
                    case "text" -> text = value.isBlank() ? null : value.toLowerCase(Locale.ROOT);
                    case "ticks" -> {
                        try {
                            ticks = Integer.parseInt(value);
                        } catch (NumberFormatException e) {
                            bad = true;
                        }
                    }
                    default -> {
                        problems.add(where + ": unknown key '" + key + "' (use look, ticks, text; skipped).");
                        unknown = true;
                    }
                }
            }
            if (unknown) {
                continue;
            }
            if (bad || points.isEmpty() || points.size() > 2 || look == null || ticks == null) {
                problems.add(where + ": needs '<from> [to] look=<point> ticks=<n>' (skipped).");
                continue;
            }
            Optional<CamPoint> from = CamPoint.parse(points.getFirst());
            Optional<CamPoint> to = points.size() == 2 ? CamPoint.parse(points.get(1)) : from;
            Optional<CamPoint> at = CamPoint.parse(look);
            if (from.isEmpty() || to.isEmpty() || at.isEmpty()) {
                problems.add(where + ": a point is not 'world:x,y,z' or '@home/@zone/@self:dx,dy,dz' (skipped).");
                continue;
            }
            if (ticks < 1 || ticks > MAX_SHOT_TICKS) {
                problems.add(where + ": ticks must be 1-" + MAX_SHOT_TICKS + " (skipped).");
                continue;
            }
            int left = maxTicks - total;
            if (left <= 0) {
                problems.add(where + ": the camera is longer than " + maxTicks + " ticks; this shot is dropped.");
                continue;
            }
            if (ticks > left) {
                problems.add(where + ": the camera is longer than " + maxTicks + " ticks; this shot is shortened.");
                ticks = left;
            }
            shots.add(new CameraShot(from.get(), to.get(), at.get(), ticks, text));
            total += ticks;
        }
        return new Parsed(List.copyOf(shots), List.copyOf(problems));
    }
}
