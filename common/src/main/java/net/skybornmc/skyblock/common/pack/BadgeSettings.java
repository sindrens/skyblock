package net.skybornmc.skyblock.common.pack;

import net.skybornmc.skyblock.common.settings.SettingCategory;
import net.skybornmc.skyblock.common.settings.SettingToggle;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.List;

/** The /settings entries of the rank badges (DISPLAY tab). Texts: {@code chat.yml → settings.toggles.<id>}. */
public final class BadgeSettings {

    public static final String REDUCED_MOTION = "reduced-motion";
    public static final String SIMPLE_BADGES = "simple-badges";
    public static final String LARGE_BADGES = "large-badges";
    public static final String RANK_COLOR = "rank-color";

    /** Needed to see the Rank colour picker (granted to the Immortal group). */
    public static final String PERM_RANK_COLOR = "skyblock.chat.rankcolor";

    public static final String TEXT_MODULE = "chat";

    private BadgeSettings() {
    }

    /**
     * The settings to register: three switches (all off by default) and, when there are approved gradients, the Rank
     * colour picker for players with {@link #PERM_RANK_COLOR} (default: the first gradient).
     */
    public static List<SettingToggle> toggles(String owner, List<String> gradientIds) {
        List<SettingToggle> out = new ArrayList<>();
        out.add(new SettingToggle(owner, REDUCED_MOTION, SettingCategory.DISPLAY, Material.CLOCK, false, null,
                TEXT_MODULE, "settings.toggles." + REDUCED_MOTION, false));
        out.add(new SettingToggle(owner, SIMPLE_BADGES, SettingCategory.DISPLAY, Material.NAME_TAG, false, null,
                TEXT_MODULE, "settings.toggles." + SIMPLE_BADGES, false));
        out.add(new SettingToggle(owner, LARGE_BADGES, SettingCategory.DISPLAY, Material.BOOK, false, null,
                TEXT_MODULE, "settings.toggles." + LARGE_BADGES, false));
        if (gradientIds != null && !gradientIds.isEmpty()) {
            out.add(SettingToggle.choice(owner, RANK_COLOR, SettingCategory.DISPLAY, Material.ORANGE_DYE, gradientIds,
                    gradientIds.getFirst(), PERM_RANK_COLOR, TEXT_MODULE, "settings.toggles." + RANK_COLOR));
        }
        return out;
    }
}
