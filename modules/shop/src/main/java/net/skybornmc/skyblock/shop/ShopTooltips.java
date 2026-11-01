package net.skybornmc.skyblock.shop;

import net.kyori.adventure.text.Component;
import net.skybornmc.skyblock.common.item.LoreBuilder;
import net.skybornmc.skyblock.common.sell.SellPrice;
import net.skybornmc.skyblock.common.util.Format;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.function.LongFunction;

/**
 * Shop tile tooltips (GUI-P3 5.5, I9 sections 6 and 12): {@code | Buy Price} (bold green), {@code | Sell Price} (bold red,
 * or a red {@code Not sellable}), one live price note, the spawner Information section, Java-only muted hints and the
 * yellow {@code » CLICK to buy} line last. Bedrock viewers get no click words and no hints. Labels: {@code tooltip.*} in
 * {@code messages/shop.yml}; values are escaped. The decisions are pure and unit-testable.
 */
final class ShopTooltips {

    /** Kind of the live price note. */
    enum NoteKind { NONE, HOT, RISING, FALLING }

    /** The live note: its kind and the whole percent it shows. */
    record LiveNote(NoteKind kind, long percent) {
        static final LiveNote NONE = new LiveNote(NoteKind.NONE, 0);
    }

    private ShopTooltips() {
    }

    /** {@code $2} from one money unit up (rounded), two decimals below ({@code $0.40}), so cheap items still show a price. */
    static String sellPriceText(double unit, LongFunction<String> money) {
        if (unit >= 1) {
            return money.apply(Math.round(unit));
        }
        return "$" + String.format(Locale.ROOT, "%.2f", Math.max(0, unit));
    }

    /**
     * HOT when it is today's Hot Item (shows the Hot factor percent), else RISING / FALLING when the price is at least
     * {@code minPercent} from base (whole percent), else NONE.
     */
    static LiveNote liveNote(double percentFromBase, boolean hot, double hotFactor, double minPercent) {
        if (hot) {
            return new LiveNote(NoteKind.HOT, Math.round((hotFactor - 1.0) * 100.0));
        }
        if (Double.isNaN(percentFromBase) || Math.abs(percentFromBase) < Math.max(0, minPercent) || Math.round(percentFromBase) == 0) {
            return LiveNote.NONE;
        }
        long pct = Math.abs(Math.round(percentFromBase));
        return new LiveNote(percentFromBase > 0 ? NoteKind.RISING : NoteKind.FALLING, pct);
    }

    static LiveNote liveNote(SellPrice price, double minPercent) {
        return price == null ? LiveNote.NONE : liveNote(price.percentFromBase(), price.hot(), price.hotFactor(), minPercent);
    }

    /** The label key of a note, or null for NONE. */
    static String noteKey(NoteKind kind) {
        return switch (kind) {
            case HOT -> "tooltip.hot";
            case RISING -> "tooltip.rising";
            case FALLING -> "tooltip.falling";
            case NONE -> null;
        };
    }

    /**
     * The grid tile lore.
     *
     * @param java      a Java viewer (click words and hints)
     * @param openState the item can be bought now (the action line; locked/unavailable items get their reasons instead)
     */
    static List<Component> grid(ShopModule module, Player viewer, ShopCatalog.Item item, SellInfo info, boolean java, boolean openState) {
        LoreBuilder lore = LoreBuilder.forMessages(module.core.messages());
        prices(lore, module, item, info, java);
        if (item.spawner()) {
            spawnerSection(lore, module, viewer, item);
        }
        if (java) {
            if (info.sellable()) {
                lore.hint(module.raw("tooltip.hint-sell-all"));
            }
            if (openState) {
                lore.hint(module.raw("tooltip.hint-stack"));
            }
        }
        if (openState) {
            lore.action(module.raw("tooltip.action-input"), module.raw("tooltip.action"));
        }
        return lore.components(module.core.messages(), viewer);
    }

    /** The buy GUI item tile: the price lines without click words, then stack price, how many you can afford, spawner rows. */
    static List<Component> buyTile(ShopModule module, Player viewer, ShopCatalog.Item item, SellInfo info) {
        LoreBuilder lore = LoreBuilder.forMessages(module.core.messages());
        prices(lore, module, item, info, false);
        int stack = module.maxStack(item);
        LoreBuilder.Section section = lore.section(module.raw("lore.info-title"));
        if (stack > 1) {
            long stackPrice = ShopMath.total(item.price(), stack).orElse(Long.MAX_VALUE);
            section.row(LoreBuilder.fill(module.raw("lore.stack"), Map.of("amount", Format.number(stack))), module.moneyText(stackPrice));
        }
        section.row(module.raw("lore.afford"), Format.number(ShopMath.affordable(item.price(), module.balance(viewer))));
        if (item.spawner()) {
            spawnerRows(section, module, viewer, item);
        }
        return lore.components(module.core.messages(), viewer);
    }

    /** {@code | Buy Price}, {@code | Sell Price} or Not sellable, and the live note (one block, no blank lines between). */
    private static void prices(LoreBuilder lore, ShopModule module, ShopCatalog.Item item, SellInfo info, boolean java) {
        lore.priceLine(LoreBuilder.fill(module.raw(java ? "tooltip.buy-java" : "tooltip.buy"),
                Map.of("price", LoreBuilder.escape(module.moneyText(item.price())))));
        if (info.sellable() && info.price().isPresent()) {
            SellPrice price = info.price().get();
            lore.priceLine(LoreBuilder.fill(module.raw(java ? "tooltip.sell-java" : "tooltip.sell"),
                    Map.of("price", LoreBuilder.escape(sellPriceText(price.unit(), module::moneyText)))));
            LiveNote note = liveNote(price, module.settings.noteMinPercent);
            String key = noteKey(note.kind());
            if (key != null) {
                lore.priceLine(LoreBuilder.fill(module.raw(key), Map.of("pct", String.valueOf(note.percent()))));
            }
        } else {
            lore.priceLine(module.raw("tooltip.not-sellable"));
        }
    }

    /** The built spawner | Information section (next works at X%, payback). */
    private static void spawnerSection(LoreBuilder lore, ShopModule module, Player viewer, ShopCatalog.Item item) {
        spawnerRows(lore.section(module.raw("lore.info-title")), module, viewer, item);
    }

    private static void spawnerRows(LoreBuilder.Section info, ShopModule module, Player viewer, ShopCatalog.Item item) {
        int percent = module.nextOutputPercent(viewer, item);
        info.row(module.raw("lore.next-works"), LoreBuilder.fill(module.raw("lore.percent"), Map.of("percent", String.valueOf(percent))));
        OptionalDouble hours = ShopMath.paybackHours(item.price(), module.incomePerHour(item), percent);
        // Owner F2: green up to 100 h, yellow up to 300 h, red "Poor deal" above (shop-settings.yml spawners.payback-*).
        ShopMath.Payback grade = ShopMath.payback(hours, module.settings.paybackGoodHours, module.settings.paybackOkHours);
        String hoursText = hours.isPresent() ? Format.number(Math.max(1, Math.round(hours.getAsDouble()))) : "";
        info.rowMini(LoreBuilder.escape(module.raw("lore.payback")), hours.isPresent()
                ? LoreBuilder.fill(module.raw(switch (grade) {
                    case GOOD -> "lore.payback-good";
                    case OK -> "lore.payback-ok";
                    case POOR -> "lore.payback-poor";
                }), Map.of("hours", hoursText))
                : module.raw("lore.never"));
        module.spawnerRows(info, viewer, item); // ECON-SPAWNERS step 13 (the one seam in this file)
    }
}
