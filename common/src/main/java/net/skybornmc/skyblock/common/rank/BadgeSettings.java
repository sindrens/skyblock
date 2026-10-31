package net.skybornmc.skyblock.common.rank;

import net.skybornmc.skyblock.common.settings.SettingCategory;
import net.skybornmc.skyblock.common.settings.SettingToggle;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.List;

/**
 * The /settings entry of the rank badges (DISPLAY tab): the Immortal colour picker. Texts:
 * {@code chat.yml → settings.toggles.<id>}. The pack v1 switches ({@code reduced-motion}, {@code simple-badges},
 * {@code large-badges}) were removed in I7 phase 0; stored values of those keys are ignored.
 */
public final class BadgeSettings {

    public static final String RANK_COLOR = "rank-color";

    /** Needed to see the Rank colour picker (granted to the Immortal group). */
    public static final String PERM_RANK_COLOR = "skyblock.chat.rankcolor";

    public static final String TEXT_MODULE = "chat";

    private BadgeSettings() {
    }

    /**
     * The settings to register: when there are approved gradients, the Rank colour picker for players with
     * {@link #PERM_RANK_COLOR} (default: the first gradient).
     */
    public static List<SettingToggle> toggles(String owner, List<String> gradientIds) {
        List<SettingToggle> out = new ArrayList<>();
        if (gradientIds != null && !gradientIds.isEmpty()) {
            out.add(SettingToggle.choice(owner, RANK_COLOR, SettingCategory.DISPLAY, Material.ORANGE_DYE, gradientIds,
                    gradientIds.getFirst(), PERM_RANK_COLOR, TEXT_MODULE, "settings.toggles." + RANK_COLOR));
        }
        return out;
    }
}
