package net.skybornmc.skyblock.common.settings;

import org.bukkit.Material;

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
 */
public record SettingToggle(String owner, String id, SettingCategory category, Material icon, boolean defaultValue,
                            String permission, String textModule, String textKey, boolean comingSoon) {

    public SettingToggle {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(icon, "icon");
        Objects.requireNonNull(textModule, "textModule");
        Objects.requireNonNull(textKey, "textKey");
        if (id == null || !id.matches("[a-z0-9-]+")) {
            throw new IllegalArgumentException("Invalid setting id '" + id + "' (use a-z, 0-9, -)");
        }
    }

    /** The key in the player's stored settings. */
    public String storageKey() {
        return "setting." + id;
    }
}
