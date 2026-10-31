package net.skybornmc.skyblock.common.menu;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.ShadowColor;
import net.kyori.adventure.text.format.TextColor;
import net.skybornmc.skyblock.common.pack.PackIds;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The chest title for pack viewers (RESOURCE_PACK_GUI §2): {@code space(-8) · background glyph · space(-(width+1)) ·
 * space(+offset)} followed by the normal title recoloured to parchment. The spaces use font {@code phoenix:space}, the
 * glyph font {@code phoenix:menu} (white, no shadow). Pure: no server needed.
 */
public final class PackTitles {

    public static final Key SPACE_FONT = Key.key("phoenix", "space");
    public static final Key MENU_FONT = Key.key("phoenix", "menu");
    /** Moves the cursor left of the title origin so the glyph starts at the window edge. */
    public static final int LEFT_PAD = 8;

    /**
     * Menu classes that exist in code but are not in the generated {@code pack-ids.yml → menu.classes} (the art list
     * predates them). They get the sensible area frame of their size here; the generator list wins when it learns them.
     */
    public static final Map<String, String> CODE_BACKGROUNDS = Map.ofEntries(
            Map.entry("blackmarket/BlackMarketMenu", "frame_economy_6"),
            Map.entry("collection/CollectionMenu", "frame_progress_6"),
            Map.entry("crates/SpinnerMenu", "frame_rewards_3"),
            Map.entry("islands/PermPresetsMenu", "frame_core_4"),
            Map.entry("logs/LogEntryMenu", "frame_staff_3"),
            Map.entry("menus/TopMenu", "top"),
            Map.entry("pass/PassMenu", "frame_rewards_6"),
            Map.entry("progression/MasteryDetailMenu", "frame_island_6"),
            Map.entry("quests/QuestsMenu", "frame_core_6"),
            Map.entry("rewards/VoteMenu", "frame_rewards_6"),
            Map.entry("shop/BuyMenu", "shop_buy"));

    /** Menus whose size varies or is a popup: they use the core frame of their row count on purpose. */
    public static final Set<String> CORE_FRAME_BY_ROWS = Set.of("common/PickListMenu", "common/OfferMenu");

    private PackTitles() {
    }

    /** The module of a menu class: {@code net.skybornmc.skyblock.<module>...}, {@code common} for the framework. */
    public static String moduleOf(Class<?> menuClass) {
        return moduleOf(menuClass.getName());
    }

    public static String moduleOf(String className) {
        String prefix = "net.skybornmc.skyblock.";
        if (className.startsWith(prefix)) {
            String rest = className.substring(prefix.length());
            int dot = rest.indexOf('.');
            return dot < 0 ? rest : rest.substring(0, dot);
        }
        return "common";
    }

    /** The background for a menu class, with the {@code frame_core_<rows>} fallback (core 6 when there is no such size). */
    public static Optional<PackIds.Background> background(PackIds ids, Class<?> menuClass, int rows) {
        return background(ids, moduleOf(menuClass), menuClass.getSimpleName(), rows);
    }

    public static Optional<PackIds.Background> background(PackIds ids, String module, String simpleName, int rows) {
        Optional<PackIds.Background> bg = ids.menu().backgroundFor(module, simpleName);
        if (bg.isPresent()) {
            return bg;
        }
        String mapped = CODE_BACKGROUNDS.get(module + "/" + simpleName);
        if (mapped != null && ids.menu().backgrounds().containsKey(mapped)) {
            return Optional.of(ids.menu().backgrounds().get(mapped));
        }
        PackIds.Background fallback = ids.menu().backgrounds().get("frame_core_" + rows);
        if (fallback == null) {
            fallback = ids.menu().backgrounds().get("frame_core_6");
        }
        return Optional.ofNullable(fallback);
    }

    /** The prefix components: negative pad, background glyph, back to the origin, then the title offset. */
    public static Component prefix(PackIds ids, PackIds.Background background) {
        int width = ids.menu().width();
        return Component.empty()
                .append(space(ids, -LEFT_PAD))
                .append(Component.text(background.glyph()).font(MENU_FONT).color(NamedTextColor.WHITE)
                        .shadowColor(ShadowColor.none()))
                .append(space(ids, -(width + 1)))
                .append(space(ids, ids.menu().titleOffset()));
    }

    /** Full pack title: prefix plus {@code title} in the parchment colour. */
    public static Component build(PackIds ids, PackIds.Background background, Component title) {
        TextColor parchment = TextColor.fromHexString(ids.menu().titleColor());
        return Component.empty().append(prefix(ids, background)).append(parchment == null ? title : recolor(title, parchment));
    }

    private static Component space(PackIds ids, int pixels) {
        return Component.text(ids.space(pixels)).font(SPACE_FONT);
    }

    /** Sets {@code color} on the whole tree (the client draws unset titles in dark grey, unreadable on the band). */
    static Component recolor(Component component, TextColor color) {
        Component colored = component.color(color);
        return colored.children(colored.children().stream().map(c -> recolor(c, color)).toList());
    }
}
