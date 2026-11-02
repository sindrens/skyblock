package net.skybornmc.skyblock.economy.sell;

import net.kyori.adventure.text.Component;
import net.skybornmc.skyblock.common.menu.Menu;
import net.skybornmc.skyblock.common.sell.SellPrice;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.economy.EconomyModule;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * ECON-SELL-SHOP-AH A4: Market Tips, opened from the sell chest (slot 48). Three rows: today's Hot Item on slot 4, up to
 * {@code sell.market-tips.size} tips centred on row 2 (the items furthest above base, {@link MarketTips}), Back (18) to a
 * fresh sell chest and Close (22). Display only: plain taps, Bedrock-safe. The list comes from a cache that is worked out
 * again every {@code sell.market-tips.refresh-seconds}.
 */
final class MarketTipsMenu extends Menu {

    static final int SLOT_HOT = 4;
    static final int ROW_START = 9;
    static final int ROW_WIDTH = 9;

    private final SellFeature feature;
    private final Menu sellParent;

    /** @param sellParent the sell chest's own parent (Back opens a new chest with it), may be null */
    MarketTipsMenu(SellFeature feature, Menu sellParent) {
        this.feature = feature;
        this.sellParent = sellParent;
    }

    @Override
    public int rows() {
        return 3;
    }

    @Override
    public Component title() {
        return service.title(viewer, service.text(viewer, EconomyModule.ID, "sell.tips.title"));
    }

    @Override
    public String messageModule() {
        return EconomyModule.ID;
    }

    @Override
    public String infoKey() {
        return "sell.tips.info";
    }

    @Override
    protected void build() {
        navBar();
        // Back: a new sell chest (the old one is closed; reopening it would show an emptied chest).
        set(SLOT_SMALL_BACK, service.backItem(viewer), click -> feature.service().openSellChest(viewer, sellParent));
        String hot = feature.service().hotKey();
        SellPrice hotPrice = hot == null ? null : feature.service().price(hot).orElse(null);
        if (hotPrice != null) {
            ItemStack tile = service.item(icon(hot), viewer, EconomyModule.ID, "sell.tips.hot", Msg.c("item", SellFeature.keyName(hot)),
                    Msg.p("percent", SellFeature.percentText(feature.config().sellHotPercent)),
                    Msg.c("unit", feature.unitPrice(viewer, hotPrice.unit())));
            tile.editMeta(meta -> meta.setEnchantmentGlintOverride(true));
            set(SLOT_HOT, tile);
        } else {
            set(SLOT_HOT, service.item(Material.GRAY_DYE, viewer, EconomyModule.ID, "sell.tips.no-hot"));
        }
        List<SellPrice> tips = feature.marketTips();
        if (tips.isEmpty()) {
            set(ROW_START + ROW_WIDTH / 2, service.item(Material.COBWEB, viewer, EconomyModule.ID, "sell.tips.empty"));
            return;
        }
        int first = firstSlot(tips.size());
        for (int i = 0; i < tips.size(); i++) {
            SellPrice p = tips.get(i);
            set(first + i, service.item(icon(p.itemKey()), viewer, EconomyModule.ID, "sell.tips.tip",
                    Msg.c("item", SellFeature.keyName(p.itemKey())), Msg.c("unit", feature.unitPrice(viewer, p.unit())),
                    Msg.p("percent", SellFeature.percentText((p.multiplier() - 1.0) * 100.0)),
                    Msg.c("base", feature.unitPrice(viewer, p.base()))));
        }
    }

    /** First slot of {@code count} tiles centred on row 2 (1 → 13, 5 → 11, 7 → 10). */
    static int firstSlot(int count) {
        int n = Math.max(1, Math.min(ROW_WIDTH, count));
        return ROW_START + (ROW_WIDTH - n) / 2;
    }

    private static Material icon(String key) {
        if (key.startsWith(SellCatalog.CUSTOM_PREFIX)) {
            return Material.PAPER;
        }
        Material m = Material.matchMaterial(key);
        return m == null || !m.isItem() || m.isAir() ? Material.PAPER : m;
    }
}
