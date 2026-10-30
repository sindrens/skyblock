package net.skybornmc.skyblock.common.settings;

import org.bukkit.Material;

import java.util.List;
import java.util.Objects;

/**
 * One on/off setting shown in {@code /settings}.
 *
 * @param owner        module id that registered it (cleanup on disable)
 * @param id           stable key, lowercase kebab-case, e.g. {@code private-messages}; stored as {@code setting.<id>}
 * @param category     group in the settings menu
 * @param icon         item shown next to the switch in the lore preview (the switch itself is a lime/red dye)
 * @param defaultValue value for players who never changed it
 * @param permission   needed to see/change it, or {@code null} for everyone
 * @param textModule   messages module holding {@code <textKey>.name} and {@code <textKey>.lore}
 * @param textKey      message key prefix of the name/lore
 * @param comingSoon   true = shown locked as "Coming soon" (feature not built yet); a module that implements the
 *                     feature later registers the same id with {@code comingSoon = false} and takes it over
 * @param choices      empty for an on/off switch; otherwise the ids of a multiple-choice setting (VIS, e.g.
 *                     {@code everyone, friends, nobody}), shown as one button that moves to the next choice on click.
 *                     Texts: {@code <textKey>.choices.<choice>}
 * @param defaultChoice the choice for players who never changed it (choice settings only)
 */
public record SettingToggle(String owner, String id, SettingCategory category, Material icon, boolean defaultValue,
                            String permission, String textModule, String textKey, boolean comingSoon,
                            List<String> choices, String defaultChoice) {

    public SettingToggle {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(icon, "icon");
        Objects.requireNonNull(textModule, "textModule");
        Objects.requireNonNull(textKey, "textKey");
        if (id == null || !id.matches("[a-z0-9-]+")) {
            throw new IllegalArgumentException("Invalid setting id '" + id + "' (use a-z, 0-9, -)");
        }
        choices = choices == null ? List.of() : List.copyOf(choices);
        for (String choice : choices) {
            if (choice == null || !choice.matches("[a-z0-9-]+")) {
                throw new IllegalArgumentException("Invalid choice '" + choice + "' of setting '" + id + "' (use a-z, 0-9, -)");
            }
        }
        if (choices.isEmpty()) {
            defaultChoice = null;
        } else if (!choices.contains(defaultChoice)) {
            throw new IllegalArgumentException("Default '" + defaultChoice + "' of setting '" + id + "' is not one of " + choices);
        }
    }

    /** An on/off switch. */
    public SettingToggle(String owner, String id, SettingCategory category, Material icon, boolean defaultValue,
                         String permission, String textModule, String textKey, boolean comingSoon) {
        this(owner, id, category, icon, defaultValue, permission, textModule, textKey, comingSoon, List.of(), null);
    }

    /** A multiple-choice setting (VIS): one button that moves to the next choice on click (Bedrock-safe). */
    public static SettingToggle choice(String owner, String id, SettingCategory category, Material icon, List<String> choices,
                                       String defaultChoice, String permission, String textModule, String textKey) {
        return new SettingToggle(owner, id, category, icon, false, permission, textModule, textKey, false, choices, defaultChoice);
    }

    /** True for a multiple-choice setting, false for an on/off switch. */
    public boolean isChoice() {
        return !choices.isEmpty();
    }

    /** The choice after {@code current} (wraps around; the first one for an unknown value). */
    public String nextChoice(String current) {
        int i = choices.indexOf(current);
        return choices.get((i + 1) % choices.size());
    }

    /** The key in the player's stored settings. */
    public String storageKey() {
        return "setting." + id;
    }
}
