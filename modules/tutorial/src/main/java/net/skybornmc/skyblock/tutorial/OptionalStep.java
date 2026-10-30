package net.skybornmc.skyblock.tutorial;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * An optional step (TUTORIAL.md §3.1, O1-O9), from {@code tutorial.yml → optional.steps}:
 * {@code "<id>: <kind> <args...> [key=value...]"}. Kinds:
 * <ul>
 *   <li>{@code menu <menu ids...>}: one of these menus opened ({@code MenuOpenedEvent})</li>
 *   <li>{@code npc <roles...>}: an NPC with one of these roles clicked</li>
 *   <li>{@code break <block> count=<n> [where=island]}: blocks broken (on your own island)</li>
 *   <li>{@code visit <targets...> [radius=<r>]}: every target visited (walk near it, or click it when it is an NPC)</li>
 *   <li>{@code near <target> [radius=<r>]}: walk near one place</li>
 * </ul>
 * Keys: {@code reward} (crystals, default {@code optional.reward-crystals}), {@code tip} (where the tip shows: none, any,
 * island, spawn, depths, near), {@code goto} (Show me teleports there), {@code camera} (Show me plays it),
 * {@code if-command} (hidden while that command doesn't exist), {@code enabled=false} (hidden). Pure, unit-tested.
 */
record OptionalStep(String id, Kind kind, List<String> args, int count, double radius, long reward, Tip tip, String gotoSpec,
                    String camera, String ifCommand, boolean enabled, boolean islandOnly) {

    enum Kind { MENU, NPC, BREAK, VISIT, NEAR }

    /** Where the first-visit tip shows (TQ26). */
    enum Tip { NONE, ANY, ISLAND, SPAWN, DEPTHS, NEAR }

    /** At most this many targets in a visit step (the progress is a bit mask). */
    static final int MAX_VISIT = 16;

    private static final Pattern ID = Pattern.compile("[a-z0-9_]{1,24}");

    record Parsed(List<OptionalStep> steps, List<String> problems) {

        Map<String, OptionalStep> byId() {
            Map<String, OptionalStep> out = new LinkedHashMap<>();
            steps.forEach(s -> out.put(s.id(), s));
            return out;
        }
    }

    /** How many units finish it: blocks for break, targets for visit, 1 otherwise. */
    int needed() {
        return switch (kind) {
            case BREAK -> count;
            case VISIT -> args.size();
            default -> 1;
        };
    }

    /** The target the tip "near" and the compass refer to: the first visit/near target, or {@code role:<npc role>}. */
    String firstTarget() {
        return switch (kind) {
            case VISIT, NEAR -> args.getFirst();
            case NPC -> "role:" + args.getFirst();
            default -> null;
        };
    }

    static Parsed parse(List<String> lines, long defaultReward) {
        List<OptionalStep> out = new ArrayList<>();
        List<String> problems = new ArrayList<>();
        List<String> seen = new ArrayList<>();
        for (String raw : lines == null ? List.<String>of() : lines) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            String where = "optional.steps: '" + raw.strip() + "'";
            int colon = raw.indexOf(':');
            if (colon <= 0) {
                problems.add(where + " needs '<id>: <kind> ...' (skipped).");
                continue;
            }
            String id = raw.substring(0, colon).strip().toLowerCase(Locale.ROOT);
            if (!ID.matcher(id).matches()) {
                problems.add(where + ": the id must be a-z, 0-9 or _ (skipped).");
                continue;
            }
            if (seen.contains(id)) {
                problems.add(where + ": '" + id + "' is listed twice (the second one is skipped).");
                continue;
            }
            String[] tokens = raw.substring(colon + 1).strip().split("\\s+");
            if (tokens.length == 0 || tokens[0].isBlank()) {
                problems.add(where + ": no kind (menu, npc, break, visit, near; skipped).");
                continue;
            }
            Kind kind;
            try {
                kind = Kind.valueOf(tokens[0].toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                problems.add(where + ": unknown kind '" + tokens[0] + "' (menu, npc, break, visit, near; skipped).");
                continue;
            }
            List<String> args = new ArrayList<>();
            int count = 1;
            double radius = 4;
            long reward = defaultReward;
            Tip tip = Tip.NONE;
            String gotoSpec = null;
            String camera = null;
            String ifCommand = null;
            boolean enabled = true;
            boolean island = false;
            boolean bad = false;
            for (int i = 1; i < tokens.length; i++) {
                String t = tokens[i];
                int eq = t.indexOf('=');
                if (eq < 0) {
                    args.add(kind == Kind.VISIT || kind == Kind.NEAR ? t : t.toLowerCase(Locale.ROOT));
                    continue;
                }
                String key = t.substring(0, eq).toLowerCase(Locale.ROOT);
                String value = t.substring(eq + 1);
                try {
                    switch (key) {
                        case "count" -> count = Integer.parseInt(value);
                        case "radius" -> radius = Double.parseDouble(value);
                        case "reward" -> reward = Long.parseLong(value);
                        case "tip" -> tip = Tip.valueOf(value.toUpperCase(Locale.ROOT));
                        case "goto" -> gotoSpec = value;
                        case "camera" -> camera = value.toLowerCase(Locale.ROOT);
                        case "if-command" -> ifCommand = value.toLowerCase(Locale.ROOT);
                        case "enabled" -> enabled = Boolean.parseBoolean(value);
                        case "where" -> {
                            if (!value.equalsIgnoreCase("island") && !value.equalsIgnoreCase("any")) {
                                throw new IllegalArgumentException(value);
                            }
                            island = value.equalsIgnoreCase("island");
                        }
                        default -> throw new IllegalArgumentException(key);
                    }
                } catch (IllegalArgumentException e) {
                    problems.add(where + ": bad '" + t + "' (skipped).");
                    bad = true;
                }
            }
            if (bad) {
                continue;
            }
            String problem = check(kind, args, count, radius, reward, gotoSpec);
            if (problem != null) {
                problems.add(where + ": " + problem + " (skipped).");
                continue;
            }
            seen.add(id);
            out.add(new OptionalStep(id, kind, List.copyOf(args), kind == Kind.BREAK ? count : 1, radius, reward, tip, gotoSpec,
                    camera, ifCommand, enabled, island));
        }
        return new Parsed(List.copyOf(out), List.copyOf(problems));
    }

    private static String check(Kind kind, List<String> args, int count, double radius, long reward, String gotoSpec) {
        if (args.isEmpty()) {
            return "names nothing to do";
        }
        if (reward < 0 || reward > 1_000_000) {
            return "reward must be 0-1000000";
        }
        if (radius <= 0 || radius > 64) {
            return "radius must be above 0 and at most 64";
        }
        if (gotoSpec != null && TargetSpec.parse(gotoSpec).isEmpty()) {
            return "goto is not a target ('world:x,y,z', 'npc:<id>', 'role:<role>', 'build:<category>:<id>')";
        }
        switch (kind) {
            case BREAK -> {
                if (args.size() != 1) {
                    return "break names exactly one block";
                }
                if (count < 1 || count > 1000) {
                    return "count must be 1-1000";
                }
            }
            case VISIT, NEAR -> {
                if (kind == Kind.NEAR && args.size() != 1) {
                    return "near names exactly one place";
                }
                if (args.size() > MAX_VISIT) {
                    return "visit names at most " + MAX_VISIT + " places";
                }
                for (String a : args) {
                    if (TargetSpec.parse(a).isEmpty()) {
                        return "'" + a + "' is not a target";
                    }
                }
            }
            default -> {
                // menu ids and npc roles are free text
            }
        }
        return null;
    }
}
