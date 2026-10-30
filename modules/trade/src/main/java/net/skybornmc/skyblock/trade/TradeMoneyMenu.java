package net.skybornmc.skyblock.trade;

import net.kyori.adventure.text.Component;
import net.skybornmc.skyblock.common.economy.Currency;
import net.skybornmc.skyblock.common.menu.Menu;
import net.skybornmc.skyblock.common.menu.NumberPickerMenu;
import net.skybornmc.skyblock.common.text.Msg;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Pick which currency to offer (only shown when more than one can be traded, e.g. money and shards), then the amount in
 * the standard number picker (taps or a typed amount like 1.5k). 3 rows, taps only (Bedrock).
 */
final class TradeMoneyMenu extends Menu {

    private static final int[] SLOTS = {11, 13, 15, 12, 14};

    private final TradeModule module;
    private final TradeSession session;

    TradeMoneyMenu(TradeModule module, TradeSession session) {
        this.module = module;
        this.session = session;
    }

    @Override
    public int rows() {
        return 3;
    }

    @Override
    public Component title() {
        return service.title(viewer, module.msg.component(viewer, "money-menu.title"));
    }

    @Override
    public String messageModule() {
        return TradeModule.ID;
    }

    @Override
    public String helpTopic() {
        return "trade";
    }

    @Override
    protected void build() {
        frame();
        List<Currency> list = module.currencies();
        TradeSession.Side me = session.side(viewer.getUniqueId());
        for (int i = 0; i < list.size() && i < SLOTS.length; i++) {
            Currency c = list.get(i);
            long balance = module.economy().cached(viewer.getUniqueId(), c).orElse(0L);
            set(SLOTS[i], service.item(module.config.icon("money-currency", Material.SUNFLOWER), viewer, TradeModule.ID,
                    "money-menu.currency", Msg.c("currency", module.msg.component(viewer, "currency." + c.key())),
                    module.amountTag("offer", c, me.money(c)), module.amountTag("balance", c, balance)),
                    click -> openChild(picker(module, session, click.player(), c)));
        }
        set(18, service.navItem("back", Material.ARROW, viewer, "frame.back"), click -> back());
    }

    /** The amount picker for one currency: 0 to the balance. */
    static NumberPickerMenu picker(TradeModule module, TradeSession session, Player player, Currency currency) {
        long current = session.side(player.getUniqueId()).money(currency);
        long balance = module.economy().cached(player.getUniqueId(), currency).orElse(0L);
        long max = Math.max(current, balance);
        return new NumberPickerMenu(v -> module.msg.component(v, "money-menu.picker-title",
                Msg.c("currency", module.msg.component(v, "currency." + currency.key()))), 0, max, current, module.config.steps(currency), (p, value) -> {
            module.setMoney(p, currency, value);
            TradeSession s = module.session(p.getUniqueId());
            if (s != null) {
                module.openTradeMenu(p, s);
            }
        });
    }
}
