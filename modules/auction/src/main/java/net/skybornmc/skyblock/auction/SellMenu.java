package net.skybornmc.skyblock.auction;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.skybornmc.skyblock.common.menu.Menu;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.util.Format;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * 3-row "list this item" dialog: the held item, one button per duration (each lists at once, its lore says exactly what
 * it costs), and Cancel. One-shot: a listing can't be started twice from the same dialog. The item stays in the
 * player's hand until a duration is clicked; it must still be exactly the same stack then.
 */
final class SellMenu extends Menu {

    static final int SLOT_ITEM = 10;
    static final int FIRST_DURATION = 12;
    static final int SLOT_CANCEL = 16;

    private final AuctionModule module;
    private final int slot;
    private final ItemStack expected;
    private final long price;
    private boolean used;

    SellMenu(AuctionModule module, int slot, ItemStack expected, long price) {
        this.module = module;
        this.slot = slot;
        this.expected = expected.clone();
        this.price = price;
    }

    @Override
    public int rows() {
        return 3;
    }

    @Override
    public Component title() {
        return service.title(viewer, service.text(viewer, AuctionModule.ID, "browse.title"),
                service.text(viewer, AuctionModule.ID, "sell.crumb"));
    }

    @Override
    public String messageModule() {
        return AuctionModule.ID;
    }

    @Override
    protected void build() {
        frame();
        set(SLOT_ITEM, ItemCodec.display(expected));
        List<String> durations = Pricing.durations(module.config);
        int shown = Math.min(durations.size(), SLOT_CANCEL - FIRST_DURATION);
        TagResolver[] money = {module.moneyTag("price", viewer, price), module.moneyTag("fee", viewer, Pricing.fee(module.config, price)),
                module.moneyTag("tax", viewer, Pricing.tax(module.config, price)),
                module.moneyTag("net", viewer, Pricing.net(module.config, price)),
                Msg.p("fee-percent", AuctionModule.percent(module.config.feePercent)),
                Msg.p("tax-percent", AuctionModule.percent(module.config.taxPercent)),
                Msg.p("item", ItemCodec.plainName(expected)), Msg.number("amount", expected.getAmount())};
        for (int i = 0; i < shown; i++) {
            String key = durations.get(i);
            java.time.Duration d = Pricing.offered(module.config, key);
            TagResolver[] p = new TagResolver[money.length + 1];
            System.arraycopy(money, 0, p, 0, money.length);
            p[money.length] = Msg.p("time", Format.duration(d));
            Material icon = key.equals(Pricing.defaultDuration(module.config)) ? Material.LIME_CONCRETE : Material.LIME_STAINED_GLASS_PANE;
            set(FIRST_DURATION + i, service.item(icon, viewer, AuctionModule.ID, "sell.duration", p), click -> {
                if (used) {
                    return;
                }
                used = true;
                viewer.closeInventory();
                module.startListing(viewer, slot, expected, price, key);
            });
        }
        set(SLOT_CANCEL, service.item(Material.RED_CONCRETE, viewer, "confirm.no"), click -> {
            used = true;
            back();
        });
    }
}
