package net.skybornmc.skyblock.enchants;

import net.kyori.adventure.text.Component;
import net.skybornmc.skyblock.common.item.Rarity;
import net.skybornmc.skyblock.common.menu.ConfirmMenu;
import net.skybornmc.skyblock.common.menu.Menu;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.util.Format;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Book shop (ENCHANTS §3.5, §4.6): one row per pool with the three rolls, and Disenchant Scrolls. Each roll shows its
 * price, odds, the tier it needs and how many are left today. Rolls never give Mythics.
 * <pre>
 *  row 2:  [Mining]   [Apprentice] [Adept] [Master]    [Scroll]
 *  row 3:  [Foraging] [Apprentice] [Adept] [Master]
 *  row 4:  [Combat]   [Apprentice] [Adept] [Master]
 * </pre>
 */
final class ShopMenu extends Menu {

    static final int SLOT_SCROLL = 16;

    private final EnchantsModule module;
    private final Map<String, Integer> usedToday;

    ShopMenu(EnchantsModule module, Map<String, Integer> usedToday) {
        this.module = module;
        this.usedToday = usedToday == null ? Map.of() : Map.copyOf(usedToday);
    }

    @Override
    public int rows() {
        return 6;
    }

    @Override
    public Component title() {
        return service.title(viewer, module.menus().text(viewer, "menu.title"), module.menus().text(viewer, "menu.shop.crumb"));
    }

    @Override
    public String messageModule() {
        return EnchantsModule.ID;
    }

    @Override
    public String infoKey() {
        return "menu.shop.info";
    }

    @Override
    public String helpTopic() {
        return "enchants";
    }

    @Override
    protected void build() {
        frame();
        List<Pool> pools = module.config().rollPools();
        for (int i = 0; i < Math.min(4, pools.size()); i++) {
            Pool pool = pools.get(i);
            int row = (i + 1) * 9;
            int tier = module.ops().tier(viewer, pool);
            set(row + 1, service.item(icon(pool), viewer, EnchantsModule.ID, "menu.shop.pool",
                    Msg.p("pool", module.text().plain("pool." + pool.id())), Msg.p("tier", tier)));
            RollType[] types = RollType.values();
            for (int t = 0; t < types.length; t++) {
                set(row + 3 + t, rollButton(types[t], pool, tier));
            }
        }
        long price = module.config().scrollPrice;
        set(SLOT_SCROLL, service.item(Material.PAPER, viewer, EnchantsModule.ID, "menu.shop.scroll", EnchantMenus.cost(price)),
                click -> openChild(new ConfirmMenu(v -> module.menus().text(v, "menu.confirm.scroll.crumb"), null,
                        v -> module.menus().lines(v, "menu.confirm.scroll.details", EnchantMenus.cost(price)),
                        p -> module.ops().buyScroll(p), p -> { })));
    }

    private net.skybornmc.skyblock.common.menu.MenuButton rollButton(RollType type, Pool pool, int tier) {
        RollType.Settings s = module.config().roll(type);
        int used = usedToday.getOrDefault(type.id(), 0);
        int left = Math.max(0, s.daily() - used);
        double total = s.odds().values().stream().mapToDouble(Double::doubleValue).sum();
        List<Component> lore = new ArrayList<>(module.menus().lines(viewer, "menu.shop.roll.lore", EnchantMenus.cost(s.price()),
                Msg.p("left", left), Msg.p("daily", s.daily())));
        for (Map.Entry<Rarity, Double> odd : s.odds().entrySet()) {
            lore.add(module.menus().text(viewer, "menu.shop.roll.odd", Msg.mm("rarity", "<rarity:" + odd.getKey().id() + ">"
                    + module.text().plain("rarity." + odd.getKey().id())), Msg.p("percent", Format.compact(total <= 0 ? 0 : odd.getValue() * 100 / total))));
        }
        String poolName = module.text().plain("pool." + pool.id());
        boolean tierOk = tier >= s.minTier();
        lore.add(module.menus().text(viewer, tierOk ? "menu.shop.roll.tier-ok" : "menu.shop.roll.tier-low",
                Msg.p("tier", s.minTier()), Msg.p("have", tier), Msg.p("pool", poolName)));
        boolean ok = tierOk && left > 0 && !s.odds().isEmpty();
        if (left <= 0) {
            lore.addAll(module.menus().lines(viewer, "menu.shop.roll.none-left"));
        }
        if (ok) {
            lore.addAll(module.menus().lines(viewer, "menu.click"));
        }
        Material material = switch (type) {
            case APPRENTICE -> Material.BOOK;
            case ADEPT -> Material.WRITABLE_BOOK;
            case MASTER -> Material.ENCHANTED_BOOK;
        };
        var item = service.item(ok ? material : Material.GRAY_DYE, module.menus().text(viewer, "menu.shop.roll." + type.id(),
                Msg.p("pool", poolName)), lore);
        if (!ok) {
            return net.skybornmc.skyblock.common.menu.MenuButton.of(item, click -> service.sound(viewer, "error"));
        }
        return net.skybornmc.skyblock.common.menu.MenuButton.of(item, click -> openChild(new ConfirmMenu(
                v -> module.menus().text(v, "menu.confirm.roll.crumb"), null,
                v -> module.menus().lines(v, "menu.confirm.roll.details", Msg.mm("roll", module.text().raw("menu.shop.roll." + type.id())
                        .replace("<pool>", poolName)), EnchantMenus.cost(s.price())),
                p -> module.ops().roll(p, type, pool), p -> { })));
    }

    private static Material icon(Pool pool) {
        return switch (pool) {
            case MINING -> Material.IRON_PICKAXE;
            case FORAGING -> Material.IRON_AXE;
            case FARMING -> Material.IRON_HOE;
            case COMBAT, STACKS -> Material.IRON_SWORD;
            case FISHING -> Material.FISHING_ROD;
        };
    }
}
