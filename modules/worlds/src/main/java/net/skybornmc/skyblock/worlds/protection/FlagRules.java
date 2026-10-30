package net.skybornmc.skyblock.worlds.protection;

import net.skybornmc.skyblock.common.region.RegionFlag;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * The flag configuration of {@code worlds.yml → protection} (pure, unit-tested): flags per world
 * ({@code protection.world-flags}), flags and priority per region kind ({@code protection.kind-flags}) and kind
 * overrides for single regions ({@code protection.region-kinds}). Immutable.
 *
 * <p>Line formats: {@code "depths: build, interact, fire"}, {@code "safe 20: pve-taken, mob-target"},
 * {@code "field 100: allow build"} (a flag list starts in deny mode; {@code allow x} / {@code deny x} switch the mode
 * for that flag and the ones after it), {@code "spawn_forge_quarter_v1:forge_quarter_plot=forge_quarter"} (build or
 * module id, colon, region id = kind; the build id may be left out).</p>
 */
public final class FlagRules {

    public static final byte NONE = 0;
    public static final byte ALLOW = 1;
    public static final byte DENY = 2;
    /** Priority of a kind line without a number. */
    public static final int DEFAULT_PRIORITY = 10;

    /** Flags and priority of one region kind. {@code flags} is indexed by {@link RegionFlag#ordinal()}. */
    public record KindRule(String kind, int priority, byte[] flags) {

        public byte get(RegionFlag flag) {
            return flags[flag.ordinal()];
        }

        public boolean isEmpty() {
            for (byte b : flags) {
                if (b != NONE) {
                    return false;
                }
            }
            return true;
        }
    }

    public static final FlagRules EMPTY = new FlagRules(Map.of(), Map.of(), Map.of());

    private final Map<String, byte[]> worlds;
    private final Map<String, KindRule> kinds;
    private final Map<String, String> overrides;

    private FlagRules(Map<String, byte[]> worlds, Map<String, KindRule> kinds, Map<String, String> overrides) {
        this.worlds = Map.copyOf(worlds);
        this.kinds = Map.copyOf(kinds);
        this.overrides = Map.copyOf(overrides);
    }

    /** Parses the three setting lists; broken lines are reported to {@code warn} and skipped. */
    public static FlagRules parse(List<String> worldLines, List<String> kindLines, List<String> overrideLines, Consumer<String> warn) {
        Map<String, byte[]> worlds = new HashMap<>();
        for (String line : worldLines) {
            int colon = line == null ? -1 : line.indexOf(':');
            if (colon <= 0) {
                warn.accept("protection.world-flags: '" + line + "' is not 'world: flag, flag'");
                continue;
            }
            String world = line.substring(0, colon).trim().toLowerCase(Locale.ROOT);
            worlds.put(world, flags(line.substring(colon + 1), "protection.world-flags (" + world + ")", warn));
        }
        Map<String, KindRule> kinds = new HashMap<>();
        for (String line : kindLines) {
            int colon = line == null ? -1 : line.indexOf(':');
            if (colon <= 0) {
                warn.accept("protection.kind-flags: '" + line + "' is not 'kind [priority]: flag, flag'");
                continue;
            }
            String[] head = line.substring(0, colon).trim().split("\\s+");
            String kind = head[0].toLowerCase(Locale.ROOT);
            int priority = DEFAULT_PRIORITY;
            if (head.length > 1) {
                try {
                    priority = Integer.parseInt(head[1]);
                } catch (NumberFormatException e) {
                    warn.accept("protection.kind-flags (" + kind + "): priority '" + head[1] + "' is not a number, using " + DEFAULT_PRIORITY);
                }
            }
            kinds.put(kind, new KindRule(kind, priority, flags(line.substring(colon + 1), "protection.kind-flags (" + kind + ")", warn)));
        }
        Map<String, String> overrides = new HashMap<>();
        for (String line : overrideLines) {
            int eq = line == null ? -1 : line.lastIndexOf('=');
            if (eq <= 0 || eq == line.length() - 1) {
                warn.accept("protection.region-kinds: '" + line + "' is not 'build:region=kind'");
                continue;
            }
            String key = line.substring(0, eq).trim().toLowerCase(Locale.ROOT);
            String kind = line.substring(eq + 1).trim().toLowerCase(Locale.ROOT);
            if (!kinds.containsKey(kind)) {
                warn.accept("protection.region-kinds: kind '" + kind + "' of '" + key + "' has no line in protection.kind-flags (no flags)");
            }
            overrides.put(key, kind);
        }
        return new FlagRules(worlds, kinds, overrides);
    }

    /** Parses {@code "build, allow fire, deny x"} into a flag array. */
    static byte[] flags(String list, String where, Consumer<String> warn) {
        byte[] out = new byte[RegionFlag.count()];
        byte mode = DENY;
        for (String raw : list.split(",")) {
            String token = raw.trim().toLowerCase(Locale.ROOT);
            if (token.isEmpty()) {
                continue;
            }
            if (token.startsWith("allow ")) {
                mode = ALLOW;
                token = token.substring(6).trim();
            } else if (token.startsWith("deny ")) {
                mode = DENY;
                token = token.substring(5).trim();
            }
            Optional<RegionFlag> flag = RegionFlag.byId(token);
            if (flag.isEmpty()) {
                warn.accept(where + ": unknown flag '" + token + "' (flags: " + ids() + ")");
                continue;
            }
            out[flag.get().ordinal()] = mode;
        }
        return out;
    }

    /** Every flag id, comma separated (for messages). */
    public static String ids() {
        StringBuilder sb = new StringBuilder();
        for (RegionFlag f : RegionFlag.values()) {
            if (!sb.isEmpty()) {
                sb.append(", ");
            }
            sb.append(f.id());
        }
        return sb.toString();
    }

    /** World flags (indexed by ordinal), or {@code null} if the world has none. */
    public byte[] world(String world) {
        byte[] f = worlds.get(world.toLowerCase(Locale.ROOT));
        return f == null ? null : f.clone();
    }

    /** The rule of a kind, if configured. */
    public Optional<KindRule> kind(String kind) {
        return Optional.ofNullable(kinds.get(kind == null ? "" : kind.toLowerCase(Locale.ROOT)));
    }

    /**
     * The kind that applies to one region: an override for {@code owner:id}, else one for {@code id}, else the region's
     * own kind (lowercase, empty if none).
     */
    public String kindOf(String owner, String id, String kind) {
        String o = overrides.get((owner + ":" + id).toLowerCase(Locale.ROOT));
        if (o == null) {
            o = overrides.get(id.toLowerCase(Locale.ROOT));
        }
        return o != null ? o : kind == null ? "" : kind.toLowerCase(Locale.ROOT);
    }

    /** Configured world names. */
    public java.util.Set<String> worlds() {
        return worlds.keySet();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("FlagRules{worlds=");
        worlds.forEach((w, f) -> sb.append(w).append(Arrays.toString(f)).append(' '));
        return sb.append("kinds=").append(kinds.keySet()).append('}').toString();
    }
}
