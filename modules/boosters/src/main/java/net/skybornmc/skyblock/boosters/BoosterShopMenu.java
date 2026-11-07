package net.skybornmc.skyblock.boosters;

import net.kyori.adventure.text.Component;
import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.booster.BoosterType;
import net.skybornmc.skyblock.common.box.BoxSource;
import net.skybornmc.skyblock.common.box.ItemBox;
import net.skybornmc.skyblock.common.economy.AccountRef;
import net.skybornmc.skyblock.common.economy.Currency;
import net.skybornmc.skyblock.common.economy.EconomyService;
import net.skybornmc.skyblock.common.economy.Transaction;
import net.skybornmc.skyblock.common.economy.TxReason;
import net.skybornmc.skyblock.common.economy.TxResult;
import net.skybornmc.skyblock.common.menu.ConfirmMenu;
import net.skybornmc.skyblock.common.menu.Menu;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.util.Format;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * {@code /boosters} → Shop (owner 7 Nov 2026, plans/STORE_FULL_PLAN.md 3): personal boosters for money and island boosters
 * for crystals, never credits (Mojang's rules: what helps you or your island is not sold for real money). 4 rows: the
 * personal offers on row 1, the island offers on row 2. Every buy has a confirm screen; the item is bound to the buyer's
 * profile and goes to the inventory (else /box).
 */
final class BoosterShopMenu extends Menu {

    /** One offer of {@code shop.offers}. */
    record Offer(boolean island, BoosterType type, int percent, int minutes, Currency currency, long price) {
    }

    /** Pure: the offers; a bad line is reported and skipped. */
    static List<Offer> parse(List<String> lines, Consumer<String> problems) {
        List<Offer> out = new ArrayList<>();
        for (String raw : lines == null ? List.<String>of() : lines) {
            String[] p = raw == null ? new String[0] : raw.trim().toLowerCase(Locale.ROOT).split(":");
            try {
                if (p.length != 6 || !(p[0].equals("personal") || p[0].equals("island"))) {
                    throw new IllegalArgumentException();
                }
                BoosterType type = BoosterType.byId(p[1]).orElseThrow(IllegalArgumentException::new);
                Currency currency = switch (p[4]) {
                    case "money" -> Currency.MONEY;
                    case "crystals" -> Currency.CRYSTALS;
                    default -> throw new IllegalArgumentException();
                };
                Offer o = new Offer(p[0].equals("island"), type, Integer.parseInt(p[2]), Integer.parseInt(p[3]), currency, Long.parseLong(p[5]));
                if (o.percent() <= 0 || o.minutes() <= 0 || o.price() <= 0) {
                    throw new IllegalArgumentException();
                }
                out.add(o);
            } catch (IllegalArgumentException e) {
                problems.accept("boosters.yml → shop.offers: '" + raw + "' is not <personal|island>:<type>:<percent>:<minutes>:<money|crystals>:<price> (skipped)");
            }
        }
        return out;
    }

    private final BoostersModule m;

    BoosterShopMenu(BoostersModule module) {
        this.m = module;
    }

    @Override
    public int rows() {
        return 4;
    }

    @Override
    public Component title() {
        return service.title(viewer, service.text(viewer, BoostersModule.ID, "menu.title"), service.text(viewer, BoostersModule.ID, "shop.title"));
    }

    @Override
    public String messageModule() {
        return BoostersModule.ID;
    }

    @Override
    protected void build() {
        navBar();
        List<Offer> offers = parse(m.settings.shopOffers, problem -> { });
        int personal = 10;
        int island = 19;
        for (Offer o : offers) {
            int slot;
            if (o.island()) {
                if (island > 25) {
                    continue;
                }
                slot = island++;
            } else {
                if (personal > 16) {
                    continue;
                }
                slot = personal++;
            }
            set(slot, tile(o), click -> confirm(click.player(), o));
        }
        set(0, service.item(Material.PLAYER_HEAD, viewer, BoostersModule.ID, "shop.row-personal"));
        set(18, service.item(Material.GRASS_BLOCK, viewer, BoostersModule.ID, "shop.row-island"));
    }

    private ItemStack tile(Offer o) {
        Material icon = o.island() ? Material.HEART_OF_THE_SEA : m.material(o.type());
        return service.item(icon, viewer, BoostersModule.ID, o.island() ? "shop.island" : "shop.personal", Msg.p("type", m.typeName(o.type())),
                Msg.number("percent", o.percent()), Msg.p("time", BoosterRules.time(o.minutes() * BoosterRules.MINUTE)), price(o));
    }

    private net.kyori.adventure.text.minimessage.tag.resolver.TagResolver price(Offer o) {
        return Msg.mm("price", o.currency() == Currency.MONEY ? "<cur:money>$" + Format.number(o.price()) + "</cur:money>"
                : "<cur:crystals>" + Format.number(o.price()) + " crystals</cur:crystals>");
    }

    private void confirm(Player p, Offer o) {
        ConfirmMenu c = new ConfirmMenu(v -> service.text(v, BoostersModule.ID, "shop.crumb"), tile(o),
                v -> service.lines(v, BoostersModule.ID, "shop.confirm", price(o)), pl -> buy(pl, o), pl -> { });
        c.parent(this);
        service.open(p, c);
    }

    private void buy(Player p, Offer o) {
        boolean ok = o.island() ? m.island().valid(o.percent(), o.minutes()) && m.settings.islandEnabled
                : m.settings.valid(o.percent(), o.minutes(), false);
        Optional<EconomyService> eco = m.core.services().optional(EconomyService.class);
        if (!ok || eco.isEmpty()) {
            m.msg.send(p, "shop.unavailable");
            return;
        }
        UUID profile = m.profiles().profileKey(p);
        String key = "bshop:" + UUID.randomUUID().toString().replace("-", "").substring(0, 24);
        eco.get().execute(Transaction.withdraw(AccountRef.player(profile), o.currency(), o.price(), TxReason.BOOSTER_SHOP)
                .actor(Actor.player(p)).reference("booster_shop:" + (o.island() ? "island_" : "") + o.type().id()).idempotencyKey(key))
                .whenCompleteAsync((r, e) -> {
                    if (e != null || r == null || !r.ok()) {
                        m.msg.send(p, r != null && r.status() == TxResult.Status.INSUFFICIENT_FUNDS ? "shop.not-enough" : "shop.failed", price(o));
                        m.sound(p, "error");
                        return;
                    }
                    ItemStack item = o.island() ? m.island().create(o.type(), o.percent(), o.minutes(), profile, true, "booster_shop")
                            : m.items().create(o.type(), o.percent(), o.minutes(), false, profile, true, "booster_shop");
                    ItemBox.give(m.core.services(), p, BoxSource.BOOSTERS, item);
                    m.audit("booster_shop_buy", Actor.player(p), p.getUniqueId(), p.getName(), o.price(),
                            Map.of("type", o.type().id(), "island", o.island(), "percent", o.percent(), "minutes", o.minutes(),
                                    "currency", o.currency().key()));
                    m.msg.send(p, "shop.bought", Msg.p("type", m.typeName(o.type())), Msg.number("percent", o.percent()));
                    m.sound(p, m.settings.startSound);
                    m.core.menus().open(p, new BoosterShopMenu(m));
                }, m.main());
    }
}
