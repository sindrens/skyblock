package net.skybornmc.skyblock.shop;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.skybornmc.skyblock.common.item.LoreBuilder;
import net.skybornmc.skyblock.common.menu.Centered;
import net.skybornmc.skyblock.common.menu.Menu;
import net.skybornmc.skyblock.common.menu.MenuButton;
import net.skybornmc.skyblock.common.menu.NumberPickerMenu;
import net.skybornmc.skyblock.common.sell.SellPrice;
import net.skybornmc.skyblock.common.sell.SellQuote;
import net.skybornmc.skyblock.common.sell.SellService;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.util.Format;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The sell GUI of one shop item (GUI-P3 5.4, I9 section 12): 3 rows, the same layout as the buy GUI.
 * <pre>
 *  4 the item: | Sell Price, the live note, | You carry, | You get (for all you carry, bonus included)
 *  row 1, centred: Sell 1 · Sell 16 · Sell 64 · Sell all · Type an amount     (9 11 13 15 17; non-stackables 11 13 15)
 *  18 Back (to the grid) · 19 Buy (the buy GUI of this item) · 22 Close         (no undo for sales, C4)
 * </pre>
 * Every sale goes through {@link ShopModule#sellAmount} (the /sell rules). Buttons that can't sell (none carried, not
 * sellable, under $1) are grey and only play the error sound.
 */
final class SellItemMenu extends Menu {

    static final int SLOT_ITEM = SLOT_SUMMARY;
    static final int SLOT_SWITCH = 19;

    private final ShopModule module;
    private final ShopCatalog.Item item;
    private final String categoryId;

    SellItemMenu(ShopModule module, ShopCatalog.Item item, String categoryId) {
        this.module = module;
        this.item = item;
        this.categoryId = categoryId;
    }

    @Override
    public int rows() {
        return 3;
    }

    @Override
    public Component title() {
        return service.title(viewer, service.text(viewer, ShopModule.ID, "sell.title"), module.displayName(viewer, item));
    }

    @Override
    public String messageModule() {
        return ShopModule.ID;
    }

    @Override
    public int tickInterval() {
        return 20;
    }

    /** Prices move live: recompute once a second (not while a sale is on its way). */
    @Override
    protected void tick() {
        if (!module.selling(viewer)) {
            refresh();
        }
    }

    @Override
    protected void build() {
        navBar();
        SellInfo info = module.sellInfo(viewer, item, true);
        Optional<SellService> sell = module.sellService();
        double bonus = sell.map(s -> s.bonusPercent(viewer)).orElse(0.0);
        int maxStack = module.maxStack(item);
        long carried = info.carried();

        SellQuote all = quote(sell, carried, maxStack, bonus);
        set(SLOT_ITEM, tile(info, carried, all));

        List<MenuButton> choices = new ArrayList<>();
        for (int amount : ShopMath.quickAmounts(maxStack)) {
            long sold = Math.min(amount, carried);
            SellQuote q = sold == carried ? all : quote(sell, sold, maxStack, bonus);
            choices.add(button(info, amount, carried, q));
        }
        choices.add(button(info, 0, carried, all));
        choices.add(typeButton(info, carried, all));
        int[] columns = Centered.columns(choices.size());
        for (int i = 0; i < choices.size(); i++) {
            set(9 + columns[i], choices.get(i));
        }
        set(SLOT_SWITCH, service.icon(viewer, "shop/buy", service.text(viewer, ShopModule.ID, "sell-switch-buy.name"),
                service.lines(viewer, ShopModule.ID, "sell-switch-buy.lore")), click -> {
                    if (module.refuse(click.player(), item)) {
                        return;
                    }
                    module.switchTo(this, new BuyMenu(module, item, categoryId));
                });
    }

    /** What selling {@code amount} of this item would pay now (null when nothing to quote). */
    private SellQuote quote(Optional<SellService> sell, long amount, int maxStack, double bonus) {
        if (sell.isEmpty() || amount <= 0) {
            return null;
        }
        Optional<ItemStack> template = module.template(item);
        if (template.isEmpty()) {
            return null;
        }
        List<ItemStack> stacks = new ArrayList<>();
        for (int size : ShopMath.stacks(amount, Math.max(1, maxStack))) {
            ItemStack stack = template.get().clone();
            stack.setAmount(size);
            stacks.add(stack);
        }
        return sell.get().quote(stacks, bonus);
    }

    private static long total(SellQuote q) {
        return q == null ? 0 : q.total();
    }

    /** The item tile: sell price (or Not sellable), the live note, how many you carry and what all of them pay. */
    private ItemStack tile(SellInfo info, long carried, SellQuote all) {
        LoreBuilder lore = LoreBuilder.forMessages(module.core.messages());
        if (info.sellable() && info.price().isPresent()) {
            SellPrice price = info.price().get();
            lore.priceLine(LoreBuilder.fill(module.raw("tooltip.sell"),
                    Map.of("price", LoreBuilder.escape(ShopTooltips.sellPriceText(price.unit(), module::moneyText)))));
            ShopTooltips.LiveNote note = ShopTooltips.liveNote(price, module.settings.noteMinPercent);
            String key = ShopTooltips.noteKey(note.kind());
            if (key != null) {
                lore.priceLine(LoreBuilder.fill(module.raw(key), Map.of("pct", String.valueOf(note.percent()))));
            }
        } else {
            lore.priceLine(module.raw("tooltip.not-sellable"));
        }
        List<Component> lines = new ArrayList<>(lore.components(module.core.messages(), viewer));
        lines.add(Component.empty());
        lines.addAll(service.lines(viewer, ShopModule.ID, "sell.item.carry", Msg.number("carried", carried)));
        if (info.sellable()) {
            lines.addAll(service.lines(viewer, ShopModule.ID, "sell.item.total", module.money("total", total(all))));
            if (all != null && all.cappedBonusPercent() > 0) {
                lines.addAll(service.lines(viewer, ShopModule.ID, "sell.item.bonus",
                        Msg.p("bonus", Format.number(Math.round(all.cappedBonusPercent())))));
            }
        }
        ItemStack base = module.icon(item);
        base.setAmount(1);
        return service.item(base, service.text(viewer, ShopModule.ID, "sell.item.name", Msg.c("item", module.displayName(viewer, item))), lines);
    }

    /** One Sell button ({@code amount} 0 = Sell all). */
    private MenuButton button(SellInfo info, int amount, long carried, SellQuote q) {
        SellStates.State state = SellStates.of(info.sellable(), carried, amount, total(q));
        long sold = amount == 0 ? carried : Math.min(amount, carried);
        TagResolver[] tags = {Msg.number("amount", amount), Msg.number("carried", carried), module.money("total", total(q)),
                Msg.c("item", module.displayName(viewer, item))};
        if (!SellStates.clickable(state)) {
            String key = switch (state) {
                case NONE -> "sell.button.none";
                case NOT_SELLABLE -> "sell.button.not-sellable";
                default -> "sell.button.too-cheap";
            };
            ItemStack grey = service.item(Material.GRAY_DYE, viewer, ShopModule.ID, key, amount == 0 ? allTags(carried, tags) : tags);
            return MenuButton.of(grey, click -> service.sound(click.player(), "error"));
        }
        ItemStack shown;
        if (amount == 0) {
            shown = service.icon(viewer, "shop/sell_all", service.text(viewer, ShopModule.ID, "sell.button.all.name", tags),
                    service.lines(viewer, ShopModule.ID, "sell.button.all.lore", tags));
        } else {
            List<Component> lore = state == SellStates.State.PARTIAL
                    ? service.lines(viewer, ShopModule.ID, "sell.button.partial", tags)
                    : service.lines(viewer, ShopModule.ID, "sell.button.sell.lore", tags);
            shown = service.icon(viewer, "shop/sell", service.text(viewer, ShopModule.ID, "sell.button.sell.name", tags), lore);
        }
        shown.setAmount((int) Math.max(1, Math.min(64, sold)));
        return MenuButton.of(shown, click -> sellNow(click.player(), amount));
    }

    /** Grey Sell all shows the carried count as its amount. */
    private static TagResolver[] allTags(long carried, TagResolver[] tags) {
        TagResolver[] out = tags.clone();
        out[0] = Msg.number("amount", carried);
        return out;
    }

    /** Type an amount: Java types it in chat; Bedrock gets the tap picker (1 to what you carry). */
    private MenuButton typeButton(SellInfo info, long carried, SellQuote all) {
        SellStates.State state = SellStates.of(info.sellable(), carried, 0, total(all));
        long max = Math.max(1, Math.min(carried, module.settings.maxAmount));
        TagResolver maxTag = Msg.number("max", max);
        Component name = service.text(viewer, ShopModule.ID, "sell.button.type.name", maxTag);
        List<Component> lore = service.lines(viewer, ShopModule.ID, "sell.button.type.lore", maxTag);
        ItemStack shown = SellStates.clickable(state)
                ? service.icon(viewer, "ui/type", name, lore)
                : service.item(Material.GRAY_DYE, name, lore);
        if (!SellStates.clickable(state)) {
            return MenuButton.of(shown, click -> service.sound(click.player(), "error"));
        }
        return MenuButton.of(shown, click -> askAmount(click.player(), max));
    }

    private void askAmount(Player player, long max) {
        if (module.bedrock(player)) {
            openChild(new NumberPickerMenu(p -> service.text(p, ShopModule.ID, "sell.picker-title",
                    Msg.c("item", module.displayName(p, item))), 1, max, Math.min(max, 1), new long[]{1, 16, 64},
                    (p, value) -> typed(p, value)));
            return;
        }
        service.prompt(player, ShopModule.ID, "sell.ask", 16, text -> {
            long parsed;
            try {
                parsed = Format.parseAmount(text);
            } catch (IllegalArgumentException e) {
                parsed = -1;
            }
            if (parsed < 1 || parsed > max) {
                module.fail(player, "sell.invalid", Msg.number("max", max));
                reopen(player);
                return;
            }
            typed(player, parsed);
        }, () -> reopen(player), Msg.number("max", max));
    }

    private void typed(Player player, long value) {
        if (!player.isOnline()) {
            return;
        }
        reopen(player);
        sellNow(player, (int) Math.min(Integer.MAX_VALUE, Math.max(1, value)));
    }

    private void sellNow(Player player, int amount) {
        module.sellAmount(player, item, amount, () -> {
            if (player.isOnline() && isOpen()) {
                refresh();
            }
        });
    }

    private void reopen(Player player) {
        if (player.isOnline() && module.problem(player).isEmpty() && !isOpen()) {
            service.open(player, this);
        }
    }
}
