package net.skybornmc.skyblock.achievements;

import net.skybornmc.skyblock.common.item.Rarity;

import java.util.List;
import java.util.Objects;

/**
 * One achievement from {@code achievements.yml}. Validated by {@link AchievementRegistry}.
 *
 * @param name        shown name (MiniMessage, max {@link AchievementRegistry#MAX_NAME} visible characters for Bedrock)
 * @param description 1-3 short lore lines (MiniMessage)
 * @param icon        item id of the menu icon (checked against the server's materials when loading on the server)
 * @param goals       one goal per tier, strictly rising (a single goal = a one-step achievement)
 * @param title       reward title for the <b>last</b> tier, or null for none
 * @param hidden      shown as "???" until its first tier is done
 */
record AchievementDef(String id, String name, List<String> description, String icon, String category, Metric metric,
                      List<Long> goals, Title title, boolean hidden) implements Tiered {

    /**
     * A reward title. {@code display} is optional: when set and the title does not exist in {@code tags-titles.yml}
     * yet, the module adds it there (staff edits of an existing title are never overwritten).
     */
    record Title(String id, String display, Rarity rarity) {
        Title {
            Objects.requireNonNull(id, "id");
            display = display == null || display.isBlank() ? null : display;
            rarity = rarity == null ? Rarity.RARE : rarity;
        }
    }

    AchievementDef {
        description = List.copyOf(description);
        goals = List.copyOf(goals);
    }

    boolean hasTitle() {
        return title != null;
    }
}
