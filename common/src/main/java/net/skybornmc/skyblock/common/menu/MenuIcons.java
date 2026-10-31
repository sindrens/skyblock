package net.skybornmc.skyblock.common.menu;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.CustomModelData;
import io.papermc.paper.datacomponent.item.TooltipDisplay;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.Locale;

/**
 * Pack icons of the menus (docs/plans/RESOURCE_PACK_GUI.md §2, §5). Menus build vanilla items as always and
 * {@link #tag tag} them with the id of the pack icon (a string in the item's PersistentDataContainer). When an item is
 * put into a menu, {@link MenuService#render} swaps it for pack viewers only: the item becomes PAPER with the
 * {@code custom_model_data} string {@code [id]}, so the pack's {@code items/paper.json} picks the model. Frame panes
 * ({@link #BLANK}) keep their material and get a hidden tooltip. Never {@code item_model} (must-have 4). No-pack Java
 * and Bedrock viewers see the vanilla item untouched.
 *
 * <p>Every id used in code must exist in {@code pack-ids.yml → icons} (unit-tested by scanning the sources).</p>
 */
public final class MenuIcons {

    /** PDC key holding the icon id on a tagged item. */
    public static final NamespacedKey KEY = new NamespacedKey("phoenix", "menu_icon");

    public static final String BLANK = "menu/blank";
    public static final String BACK = "menu/back";
    public static final String PREVIOUS = "menu/previous";
    public static final String NEXT = "menu/next";
    public static final String CLOSE = "menu/close";
    public static final String INFO = "menu/info";
    public static final String HELP = "menu/help";
    public static final String MAIN_MENU = "menu/main_menu";
    public static final String CONFIRM = "menu/confirm";
    public static final String CONFIRM_SIDE = "menu/confirm_side";
    public static final String CANCEL = "menu/cancel";
    public static final String CANCEL_SIDE = "menu/cancel_side";
    public static final String TOGGLE_ON = "menu/toggle_on";
    public static final String TOGGLE_OFF = "menu/toggle_off";
    public static final String LOADING = "menu/loading";
    public static final String ERROR = "menu/error";
    public static final String EMPTY = "menu/empty";
    public static final String LOCKED = "menu/locked";
    public static final String MINUS_SMALL = "menu/minus_small";
    public static final String MINUS_MID = "menu/minus_mid";
    public static final String MINUS_BIG = "menu/minus_big";
    public static final String PLUS_SMALL = "menu/plus_small";
    public static final String PLUS_MID = "menu/plus_mid";
    public static final String PLUS_BIG = "menu/plus_big";
    public static final String SET_MIN = "menu/set_min";
    public static final String SET_MAX = "menu/set_max";
    public static final String TYPE_AMOUNT = "menu/type_amount";
    public static final String BUY_1 = "menu/buy_1";
    public static final String BUY_16 = "menu/buy_16";
    public static final String BUY_64 = "menu/buy_64";
    public static final String SEARCH = "menu/search";
    public static final String FILL = "menu/fill";
    public static final String UNDO = "menu/undo";

    private static final String FEATURE = "menu/icon/";
    private static final String TAB = "menu/tab/";

    private MenuIcons() {
    }

    /** The feature icon {@code menu/icon/<name>} (hub, You, Travel, /is). */
    public static String feature(String name) {
        return FEATURE + name;
    }

    /** The shop tab icon {@code menu/tab/<name>}. */
    public static String tab(String name) {
        return TAB + name;
    }

    /** The currency icon {@code currency/<name>} (money, crystals, shards, credits). */
    public static String currency(String name) {
        return "currency/" + name;
    }

    /** The currency icon for a currency key ({@code gems} is the old name of credits); null for an unknown key. */
    public static String currencyOf(String key) {
        return switch (clean(key)) {
            case "money" -> currency("money");
            case "crystals" -> currency("crystals");
            case "shards" -> currency("shards");
            case "gems", "credits" -> currency("credits");
            default -> null;
        };
    }

    /** Minus step icon for the step index (0 = smallest). */
    public static String minus(int index) {
        return index <= 0 ? MINUS_SMALL : index == 1 ? MINUS_MID : MINUS_BIG;
    }

    /** Plus step icon for the step index (0 = smallest). */
    public static String plus(int index) {
        return index <= 0 ? PLUS_SMALL : index == 1 ? PLUS_MID : PLUS_BIG;
    }

    /** True for the glass panes the pack replaces with the invisible {@link #BLANK} model. */
    public static boolean isPane(Material material) {
        return material != null && material.name().endsWith("GLASS_PANE");
    }

    /** Tags {@code item} (in place) with a pack icon id; {@code id == null} leaves it alone. Returns the same stack. */
    public static ItemStack tag(ItemStack item, String id) {
        if (item == null || id == null || id.isBlank() || item.getType().isAir()) {
            return item;
        }
        item.editMeta(meta -> meta.getPersistentDataContainer().set(KEY, PersistentDataType.STRING, id));
        return item;
    }

    /** The icon id on {@code item}, or {@code null}. */
    public static String idOf(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        return item.getItemMeta().getPersistentDataContainer().get(KEY, PersistentDataType.STRING);
    }

    /**
     * The pack form of a tagged item (a copy), or {@code item} itself when it carries no tag. Panes keep their material
     * and hide their tooltip; everything else becomes PAPER. Name, lore, count and glint stay on the item, so "ready"
     * and "selected" states keep working (RESOURCE_PACK_GUI §2).
     */
    public static ItemStack render(ItemStack item) {
        String id = idOf(item);
        if (id == null) {
            return item;
        }
        boolean blank = BLANK.equals(id);
        ItemStack out = blank ? item.clone() : item.withType(Material.PAPER);
        out.setData(DataComponentTypes.CUSTOM_MODEL_DATA, CustomModelData.customModelData().addString(id));
        if (blank) {
            out.setData(DataComponentTypes.TOOLTIP_DISPLAY, TooltipDisplay.tooltipDisplay().hideTooltip(true));
        }
        return out;
    }

    /** Lower-case id helper for config values ({@code "Home"} → {@code "home"}). */
    public static String clean(String raw) {
        return raw == null ? "" : raw.strip().toLowerCase(Locale.ROOT);
    }
}
