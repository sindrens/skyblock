package net.skybornmc.skyblock.common.visibility;

import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;

/**
 * The visibility decision and the diff, without Bukkit (unit-tested). One rule set for every feature (REVIEW3 D4):
 * {@code hidden = vanished-for-this-viewer || (not always shown && (a module rule hides || the viewer's setting hides))}.
 */
public final class VisibilityRules {

    private VisibilityRules() {
    }

    /**
     * What matters about the viewer.
     *
     * @param seesVanished  may see vanished staff ({@link VisibilityService#SEE_VANISHED})
     * @param mode          their {@code Show players} choice
     * @param inSettingZone standing where the setting applies ({@code visibility.yml → setting.worlds / setting.regions})
     * @param world         current world name
     */
    public record Viewer(boolean seesVanished, ShowMode mode, boolean inSettingZone, String world) {
        public Viewer {
            mode = mode == null ? ShowMode.EVERYONE : mode;
        }
    }

    /**
     * What matters about the target.
     *
     * @param vanished      vanished staff
     * @param alwaysShown   staff ({@link VisibilityService#ALWAYS_SHOWN}): never hidden by rules or settings
     * @param inSettingZone standing where the setting applies
     * @param world         current world name
     */
    public record Target(boolean vanished, boolean alwaysShown, boolean inSettingZone, String world) {
    }

    /**
     * True when the viewer must not see the target. {@code ruleHides} (module rules, e.g. the AFK pool) and
     * {@code related} (island member / coop / friend) are asked only when they can change the answer.
     */
    public static boolean hidden(Viewer viewer, Target target, BooleanSupplier ruleHides, BooleanSupplier related) {
        if (target.vanished()) {
            return !viewer.seesVanished(); // vanished staff follow vanish only
        }
        if (target.alwaysShown()) {
            return false;
        }
        if (settingHides(viewer, target, related)) {
            return true;
        }
        return ruleHides.getAsBoolean();
    }

    /** The {@code Show players} part: both stand where it applies, in the same world, and the choice excludes the target. */
    static boolean settingHides(Viewer viewer, Target target, BooleanSupplier related) {
        if (viewer.mode() == ShowMode.EVERYONE || !viewer.inSettingZone() || !target.inSettingZone()
                || !Objects.equals(viewer.world(), target.world())) {
            return false;
        }
        return viewer.mode() == ShowMode.NOBODY || !related.getAsBoolean();
    }

    /** The changes to apply for one viewer. */
    public record Changes(Set<UUID> hide, Set<UUID> show) {
        public boolean isEmpty() {
            return hide.isEmpty() && show.isEmpty();
        }
    }

    /**
     * What to hide and show so that what is applied matches what is wanted. {@code applied} = targets this viewer has
     * hidden by us now; {@code wanted} = target → should be hidden. Targets missing from {@code wanted} (offline) are left
     * alone. With {@code force}, every wanted state is applied again (on join, so a stale Bukkit state is corrected).
     */
    public static Changes diff(Set<UUID> applied, Map<UUID, Boolean> wanted, boolean force) {
        Set<UUID> hide = new HashSet<>();
        Set<UUID> show = new HashSet<>();
        for (Map.Entry<UUID, Boolean> e : wanted.entrySet()) {
            boolean isHidden = applied.contains(e.getKey());
            if (e.getValue()) {
                if (!isHidden || force) {
                    hide.add(e.getKey());
                }
            } else if (isHidden || force) {
                show.add(e.getKey());
            }
        }
        return new Changes(hide, show);
    }
}
