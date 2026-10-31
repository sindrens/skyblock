package net.skybornmc.skyblock.islands;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Island permission presets (GUI-4, owner answer U8): three one-click setups instead of flipping ~115 cells.
 * <ul>
 *   <li><b>Normal</b> = the server defaults ({@link RolePermissions}): every override removed.</li>
 *   <li><b>Strict</b> = the defaults minus a deny list per role ({@code islands.yml → permissions.presets.strict.<role>}).</li>
 *   <li><b>Open</b> = the defaults plus an allow list per role ({@code permissions.presets.open.<role>}).</li>
 * </ul>
 * Because Strict and Open are relative to the defaults, a changed default reaches the presets too. The full matrix stays
 * behind "Advanced". Pure (no Bukkit), unit-tested.
 */
public final class PermPresets {

    /** The three presets, in menu order. */
    public enum Preset {
        STRICT, NORMAL, OPEN;

        /** Message and config key: {@code strict}, {@code normal}, {@code open}. */
        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** One cell a preset changes: the new override ({@code null} = follow the server default). */
    public record Change(PermRole role, IslandPerm perm, Boolean override) {
    }

    /** Parse result: the presets and readable problems (unknown keys) for the console. */
    public record Result(PermPresets presets, List<String> problems) {
    }

    /** Per role: permissions Strict takes away / Open adds (only roles that can have them). */
    private final long[] strictDeny = new long[PermRole.cached().length];
    private final long[] openAllow = new long[PermRole.cached().length];

    private PermPresets() {
    }

    /** No changes: all three presets equal the server defaults. */
    public static PermPresets none() {
        return new PermPresets();
    }

    /**
     * @param strict per editable role: the permission keys Strict denies
     * @param open   per editable role: the permission keys Open allows
     */
    public static Result parse(Map<PermRole, List<String>> strict, Map<PermRole, List<String>> open) {
        PermPresets presets = new PermPresets();
        List<String> problems = new ArrayList<>();
        for (PermRole role : PermRole.cached()) {
            if (!role.editable()) {
                continue;
            }
            presets.strictDeny[role.ordinal()] = mask("strict", role, strict == null ? null : strict.get(role), problems);
            presets.openAllow[role.ordinal()] = mask("open", role, open == null ? null : open.get(role), problems);
        }
        return new Result(presets, List.copyOf(problems));
    }

    private static long mask(String preset, PermRole role, List<String> keys, List<String> problems) {
        long mask = 0;
        if (keys == null) {
            return 0;
        }
        for (String key : keys) {
            if (key == null || key.isBlank()) {
                continue;
            }
            var perm = IslandPerm.byKey(key);
            if (perm.isEmpty()) {
                problems.add("permissions.presets." + preset + "." + role.key() + ": unknown permission '" + key + "' (skipped)");
            } else if (!perm.get().appliesTo(role)) {
                problems.add("permissions.presets." + preset + "." + role.key() + ": " + role.key() + " can't have '" + key + "' (skipped)");
            } else {
                mask |= perm.get().bit();
            }
        }
        return mask;
    }

    /** The permissions {@code role} has under {@code preset}. The leader always has everything. */
    public long target(Preset preset, PermRole role, RolePermissions defaults) {
        if (!role.editable()) {
            return IslandPerm.all();
        }
        long base = defaults.defaultMask(role);
        long mask = switch (preset) {
            case NORMAL -> base;
            case STRICT -> base & ~strictDeny[role.ordinal()];
            case OPEN -> base | openAllow[role.ordinal()];
        };
        return mask & ~IslandPerm.notApplicable(role);
    }

    /**
     * The cells to write so the island matches {@code preset}, limited to the cells {@code editable} allows (an admin
     * can't change the Admin column, …). Cells already right are left out, so applying twice writes nothing.
     */
    public List<Change> changes(Preset preset, IslandPerms current, RolePermissions defaults, CellFilter editable) {
        List<Change> out = new ArrayList<>();
        for (PermRole role : PermRole.cached()) {
            if (!role.editable()) {
                continue;
            }
            long target = target(preset, role, defaults);
            for (IslandPerm perm : IslandPerm.cached()) {
                if (!perm.appliesTo(role) || !editable.test(role, perm)) {
                    continue;
                }
                boolean allowed = (target & perm.bit()) != 0;
                boolean byDefault = defaults.defaultAllows(role, perm);
                Boolean wanted = allowed == byDefault ? null : allowed;
                Boolean now = current == null ? null : current.override(role, perm);
                if (!java.util.Objects.equals(now, wanted)) {
                    out.add(new Change(role, perm, wanted));
                }
            }
        }
        return out;
    }

    /** The preset the island matches exactly (every role), or empty for a custom setup. Normal wins a tie. */
    public Optional<Preset> active(IslandPerms current, RolePermissions defaults) {
        Map<Preset, Boolean> matches = new EnumMap<>(Preset.class);
        for (Preset preset : Preset.values()) {
            matches.put(preset, changes(preset, current, defaults, (role, perm) -> true).isEmpty());
        }
        if (matches.get(Preset.NORMAL)) {
            return Optional.of(Preset.NORMAL);
        }
        for (Preset preset : Preset.values()) {
            if (matches.get(preset)) {
                return Optional.of(preset);
            }
        }
        return Optional.empty();
    }

    /** Which cells the viewer may change. */
    @FunctionalInterface
    public interface CellFilter {
        boolean test(PermRole role, IslandPerm perm);
    }
}
