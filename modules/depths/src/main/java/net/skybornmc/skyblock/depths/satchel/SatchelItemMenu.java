package net.skybornmc.skyblock.depths.satchel;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.skybornmc.skyblock.common.menu.Menu;
import net.skybornmc.skyblock.common.menu.MenuService;
import net.skybornmc.skyblock.common.sell.SellPrice;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.util.Format;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * One satchel item (M4.4): sell all of it, take one stack or as many as fit into the inventory. A 3-row menu with big
 * single-click buttons (Bedrock); Back returns to the satchel.
 */
final class SatchelItemMenu extends Menu {

    static final int SLOT_SELL = 12;
    static final int SLOT_ITEM = 13;
    static final int SLOT_TAKE_STACK = 14;
    static final int SLOT_TAKE_ALL = 16;

    private final SatchelManager satchel;
    private final String id;

    SatchelItemMenu(SatchelManager satchel, String id) {
        this.satchel = satchel;
        this.id = id;
    }

    @Override
    public int rows() {
        return 3;
    }

    @Override
    public Component title() {
        return service.title(viewer, service.text(viewer, SatchelMenu.MODULE, "satchel.menu.title"),
                service.messages().parse(viewer, SatchelMenu.name(satchel, id)));
    }

    @Override
    public String messageModule() {
        return SatchelMenu.MODULE;
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
    protected void build() {
        frame();
        long amount = satchel.amount(viewer, id);
        set(SLOT_ITEM, display(satchel, service, viewer, id, amount, null));
        if (amount <= 0) {
            set(SLOT_SELL, service.item(new ItemStack(Material.GRAY_DYE), text("satchel.item.gone.name"), lines("satchel.item.gone.lore")));
            return;
        }
        Optional<SellPrice> price = satchel.price(id);
        TagResolver[] tags = {Msg.number("amount", amount), Msg.p("worth", satchel.money(satchel.value(id, amount))),
                Msg.mm("item", SatchelMenu.name(satchel, id))};
        if (price.isPresent()) {
            set(SLOT_SELL, service.item(new ItemStack(Material.GOLD_INGOT), text("satchel.item.sell.name", tags), lines("satchel.item.sell.lore", tags)),
                    click -> satchel.sellItem(click.player(), id));
        } else {
            set(SLOT_SELL, service.item(new ItemStack(Material.BARRIER), text("satchel.item.no-sell.name", tags), lines("satchel.item.no-sell.lore", tags)));
        }
        if (!satchel.config().satchelTakeOut) {
            set(SLOT_TAKE_STACK, service.item(new ItemStack(Material.BARRIER), text("satchel.item.take-off.name"), lines("satchel.item.take-off.lore")));
            return;
        }
        set(SLOT_TAKE_STACK, service.item(new ItemStack(Material.CHEST), text("satchel.item.take-stack.name", tags), lines("satchel.item.take-stack.lore", tags)),
                click -> satchel.takeOut(click.player(), id, false));
        set(SLOT_TAKE_ALL, service.item(new ItemStack(Material.ENDER_CHEST), text("satchel.item.take-all.name", tags), lines("satchel.item.take-all.lore", tags)),
                click -> satchel.takeOut(click.player(), id, true));
    }

    /** The drop item's look with amount, unit price and value ({@code clickKey}: extra action line, or null). */
    static ItemStack display(SatchelManager satchel, MenuService service, Player viewer, String id, long amount, String clickKey) {
        // A plain look-alike, never the real custom item (menu items must not count as drop items anywhere).
        net.skybornmc.skyblock.depths.drops.DropItems.Def def = satchel.items().def(id);
        ItemStack base = new ItemStack(def == null ? Material.PAPER : def.material(), (int) Math.max(1, Math.min(64, amount)));
        Optional<SellPrice> price = satchel.price(id);
        TagResolver[] tags = {Msg.number("amount", amount), Msg.mm("item", SatchelMenu.name(satchel, id)),
                Msg.p("price", price.map(p -> satchel.money(Math.max(0, Math.round(p.unit())))).orElse("-")),
                Msg.p("worth", satchel.money(satchel.value(id, amount))),
                Msg.p("trend", price.map(p -> Format.number(Math.round(p.percentFromBase()))).orElse("0"))};
        List<Component> lore = new ArrayList<>(service.lines(viewer, SatchelMenu.MODULE, "satchel.menu.entry.lore", tags));
        if (price.isEmpty()) {
            lore.addAll(service.lines(viewer, SatchelMenu.MODULE, "satchel.menu.entry.no-price", tags));
        }
        if (clickKey != null) {
            lore.add(Component.empty());
            lore.addAll(service.lines(viewer, SatchelMenu.MODULE, clickKey, tags));
        }
        return service.item(base, service.text(viewer, SatchelMenu.MODULE, "satchel.menu.entry.name", tags), lore);
    }

    private Component text(String key, TagResolver... tags) {
        return service.text(viewer, SatchelMenu.MODULE, key, tags);
    }

    private List<Component> lines(String key, TagResolver... tags) {
        return service.lines(viewer, SatchelMenu.MODULE, key, tags);
    }
}
