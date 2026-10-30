package net.skybornmc.skyblock.depths.satchel;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.skybornmc.skyblock.common.menu.MenuButton;
import net.skybornmc.skyblock.common.menu.PagedMenu;
import net.skybornmc.skyblock.common.sell.SellPrice;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.util.Format;
import net.skybornmc.skyblock.depths.drops.DropItems;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The satchel menu (M4.4, UI_STYLE §5): the header shows used/capacity and the total value, the content lists every item
 * with its amount and value (click → sell it or take it out), Sell all and Upgrade sit in the bottom row, and (SELL2)
 * a Sell chest button (46) opens the {@code /sell} chest for inventory items (all selling goes through SellService).
 * Single left clicks only (Bedrock). Refreshes live while open.
 */
final class SatchelMenu extends PagedMenu<String> {

    static final int SLOT_SUMMARY = 4;
    static final int SLOT_SELL_CHEST = 46;
    static final int SLOT_SELL_ALL = 47;
    static final int SLOT_UPGRADE = 51;
    static final String MODULE = "depths";

    private final SatchelManager satchel;

    SatchelMenu(SatchelManager satchel) {
        this.satchel = satchel;
    }

    @Override
    public String messageModule() {
        return MODULE;
    }

    @Override
    public String infoKey() {
        return "satchel.menu.info";
    }

    @Override
    public String helpTopic() {
        return "satchel";
    }

    @Override
    public int tickInterval() {
        return satchel.config().satchelRefreshTicks;
    }

    @Override
    protected void tick() {
        refresh();
    }

    @Override
    protected List<Component> titleCrumbs() {
        return List.of(text("satchel.menu.title"));
    }

    @Override
    protected String emptyKey() {
        return "satchel.menu.empty";
    }

    @Override
    protected List<String> entries() {
        Map<String, Long> held = satchel.contents(viewer);
        List<String> out = new ArrayList<>();
        for (String id : satchel.items().order()) {
            if (held.getOrDefault(id, 0L) > 0) {
                out.add(id);
            }
        }
        held.keySet().stream().filter(id -> !out.contains(id)).sorted().forEach(out::add);
        return out;
    }

    @Override
    protected void decorate() {
        long used = satchel.used(viewer);
        long max = satchel.capacity(viewer);
        long worth = 0;
        for (Map.Entry<String, Long> e : satchel.contents(viewer).entrySet()) {
            worth += satchel.value(e.getKey(), e.getValue());
        }
        double bonus = satchel.bonusPercent(viewer);
        long withBonus = (long) Math.floor(worth * (1 + bonus / 100.0));
        TagResolver[] tags = {Msg.number("used", used), Msg.number("max", max), Msg.p("percent", SatchelMath.percent(used, max)),
                Msg.p("worth", satchel.money(withBonus)), Msg.p("bonus", Format.number(Math.round(bonus)))};
        List<Component> lore = new ArrayList<>(lines(satchel.loaded(viewer) ? "satchel.menu.summary.lore" : "satchel.menu.summary.loading", tags));
        set(SLOT_SUMMARY, service.item(new ItemStack(icon()), text("satchel.menu.summary.name", tags), lore));

        set(SLOT_SELL_ALL, service.item(new ItemStack(Material.GOLD_INGOT), text("satchel.menu.sell-all.name", tags),
                lines(worth > 0 ? "satchel.menu.sell-all.lore" : "satchel.menu.sell-all.empty", tags)), click -> {
            if (worth(click) > 0) {
                satchel.sellAll(click.player());
            } else {
                service.sound(click.player(), "error");
            }
        });

        if (satchel.sellService().isPresent()) {
            set(SLOT_SELL_CHEST, service.item(new ItemStack(Material.CHEST), text("satchel.menu.sell-chest.name"),
                    lines("satchel.menu.sell-chest.lore")),
                    click -> satchel.sellService().ifPresent(s -> s.openSellChest(click.player(), this)));
        }

        SatchelMath.Upgrade next = satchel.nextUpgrade(viewer);
        int level = satchel.upgradeLevel(viewer);
        if (next == null) {
            set(SLOT_UPGRADE, service.item(new ItemStack(Material.ANVIL), text("satchel.menu.upgrade.name-max"),
                    lines("satchel.menu.upgrade.max", Msg.p("level", level), Msg.p("levels", satchel.upgradeCount()))));
        } else {
            TagResolver[] up = {Msg.p("level", level), Msg.p("next", level + 1), Msg.p("levels", satchel.upgradeCount()),
                    Msg.number("capacity", next.capacity()), Msg.p("price", satchel.money(next.price()))};
            set(SLOT_UPGRADE, service.item(new ItemStack(Material.ANVIL), text("satchel.menu.upgrade.name", up),
                    lines("satchel.menu.upgrade.lore", up)), click -> satchel.buyUpgrade(click.player()));
        }
    }

    private long worth(net.skybornmc.skyblock.common.menu.MenuClick click) {
        long worth = 0;
        for (Map.Entry<String, Long> e : satchel.contents(click.player()).entrySet()) {
            worth += satchel.value(e.getKey(), e.getValue());
        }
        return worth;
    }

    @Override
    protected MenuButton entry(String id) {
        return MenuButton.of(entryItem(id, satchel.amount(viewer, id)), click -> openChild(new SatchelItemMenu(satchel, id)));
    }

    /** The shown item: the drop item's look with amount, price and value. */
    ItemStack entryItem(String id, long amount) {
        return SatchelItemMenu.display(satchel, service, viewer, id, amount, "satchel.menu.entry.click");
    }

    private Material icon() {
        Material m = Material.matchMaterial(satchel.config().satchelIcon);
        return m == null || !m.isItem() ? Material.BUNDLE : m;
    }

    private Component text(String key, TagResolver... tags) {
        return service.text(viewer, MODULE, key, tags);
    }

    private List<Component> lines(String key, TagResolver... tags) {
        return service.lines(viewer, MODULE, key, tags);
    }

    static Optional<SellPrice> price(SatchelManager satchel, String id) {
        return satchel.price(id);
    }

    static String name(SatchelManager satchel, String id) {
        DropItems.Def def = satchel.items().def(id);
        return def == null ? id : satchel.items().name(def);
    }
}
