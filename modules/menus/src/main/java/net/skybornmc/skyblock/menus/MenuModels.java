package net.skybornmc.skyblock.menus;

import net.skybornmc.skyblock.common.menu.MenuIcons;

import java.util.Map;

/**
 * Pack icon of each hub, You and Travel button (RESOURCE_PACK_GUI §5.2). {@code menu.yml} can set {@code model:} on an
 * entry; without it the entry's id decides, so servers with an older menu.yml get the icons too. Entries without an
 * icon (Quests, Collection, staff entries) keep their vanilla item for everyone.
 */
public final class MenuModels {

    /** {@code hub.<id>} → icon. */
    public static final Map<String, String> HUB = Map.ofEntries(
            Map.entry("home", MenuIcons.feature("home")),
            Map.entry("island", MenuIcons.feature("island")),
            Map.entry("depths", MenuIcons.feature("depths")),
            Map.entry("travel", MenuIcons.feature("travel")),
            Map.entry("sell", MenuIcons.feature("sell")),
            Map.entry("shop", MenuIcons.feature("shop")),
            Map.entry("bank", MenuIcons.feature("bank")),
            Map.entry("auction", MenuIcons.feature("auction")),
            Map.entry("forge", MenuIcons.feature("forge")),
            Map.entry("skills", MenuIcons.feature("skills")),
            Map.entry("enchanter", MenuIcons.feature("enchanter")),
            Map.entry("wardrobe", MenuIcons.feature("wardrobe")),
            Map.entry("rewards", MenuIcons.feature("rewards")),
            Map.entry("top", MenuIcons.feature("top")));

    /** {@code you.<id>} → icon. */
    public static final Map<String, String> YOU = Map.of(
            "profiles", MenuIcons.feature("profiles"),
            "titles", MenuIcons.feature("titles"),
            "achievements", MenuIcons.feature("achievements"),
            "settings", MenuIcons.feature("settings"),
            "store", MenuIcons.feature("store"),
            "rules", MenuIcons.feature("rules"));

    /** {@code travel.<id>} → icon (the same feature icon everywhere, §5.2). */
    public static final Map<String, String> TRAVEL = Map.of(
            "spawn", MenuIcons.feature("spawn"),
            "my-island", MenuIcons.feature("home"),
            "depths-gate", MenuIcons.feature("depths"),
            "crate-yard", MenuIcons.feature("crates"),
            "market", MenuIcons.feature("shop"),
            "forge-quarter", MenuIcons.feature("forge"),
            "visit-islands", MenuIcons.feature("visit"));

    private MenuModels() {
    }

    /** The configured {@code model} (trimmed, empty = none) or the default of {@code section.id}. */
    static String resolve(String section, String id, String configured) {
        if (configured != null && !configured.isBlank()) {
            return MenuIcons.clean(configured);
        }
        Map<String, String> defaults = switch (section) {
            case "hub" -> HUB;
            case "you" -> YOU;
            case "travel" -> TRAVEL;
            default -> Map.of();
        };
        return defaults.get(id);
    }
}
