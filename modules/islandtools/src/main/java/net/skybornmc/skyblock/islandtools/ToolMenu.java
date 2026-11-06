package net.skybornmc.skyblock.islandtools;

import net.kyori.adventure.text.Component;
import net.skybornmc.skyblock.common.menu.ConfirmMenu;
import net.skybornmc.skyblock.common.menu.Menu;
import net.skybornmc.skyblock.common.menu.MenuButton;
import net.skybornmc.skyblock.common.menu.MenuClick;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.util.Format;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * One island tool (owner 4 Nov 2026: vanilla items, no glass, spacious, contiguous groups, mockup lore):
 * <pre>
 *  . . . S T P . . .      3 the tool's storage, 4 the tool (level, XP, prestige), 5 prestige
 *  . . E E E E . . .      row 1: enchant-like perks and one-time unlocks, centred
 *  . . . . X . . . .      22 your own grind enchants (owner 6 Nov 2026)
 *  . L L L L L L . .      rows 3-4: the long tracks, centred
 *  . . L L L L L . .
 *  nav bar                47 get your handle (only without one)
 * </pre>
 * Plain click buys 1 level, shift-click 10, right-click 100 (Java), at the summed price.
 */
final class ToolMenu extends Menu {

    static final int SLOT_STORAGE = 3;
    static final int SLOT_TOOL = 4;
    static final int SLOT_PRESTIGE = 5;
    static final int SLOT_HANDLE = 47;
    static final int SLOT_ENCHANTS = 22;

    private final IslandToolsModule m;
    private final Kind kind;

    ToolMenu(IslandToolsModule m, Kind kind) {
        this.m = m;
        this.kind = kind;
    }

    @Override
    public int rows() {
        return 6;
    }

    @Override
    public Component title() {
        return service.title(viewer, text("menu.crumb"), text("item.name." + kind.id()));
    }

    @Override
    public String messageModule() {
        return IslandToolsModule.ID;
    }

    @Override
    public String infoKey() {
        return "menu.tool-info";
    }

    @Override
    public String helpTopic() {
        return "tools";
    }

    /** The first slot of {@code n} contiguous buttons centred in a row of 9 (pure). */
    static int start(int n) {
        return Math.max(0, (9 - Math.min(9, n)) / 2);
    }

    @Override
    protected void build() {
        navBar();
        UUID island = m.island(viewer);
        ToolStore.Prog prog = m.view(viewer, kind); // the island's perks with your own level and prestige
        if (island == null || prog == null) {
            return;
        }
        ToolRules.Level lv = m.level(prog);
        if (!m.carries(viewer, kind, island)) {
            set(SLOT_HANDLE, service.item(Material.CHEST, viewer, IslandToolsModule.ID, "menu.handle",
                    Msg.p("tool", m.raw("item.name." + kind.id()))), click -> {
                m.giveHandle(click.player(), kind);
                refreshIfOpen();
            });
        }
        String stars = ToolRules.stars(prog.prestige(), m.config().maxStars);
        List<Component> info = new ArrayList<>(service.lines(viewer, IslandToolsModule.ID, lv.maxed() ? "menu.tool-max" : "menu.tool",
                Msg.number("level", lv.level()), Msg.number("max", m.config().maxLevel), Msg.number("xp", lv.into()),
                Msg.number("need", lv.need()), Msg.number("percent", (long) Math.floor(lv.progress() * 100)),
                Msg.number("prestige", prog.prestige()), Msg.p("stars", stars.isEmpty() ? "-" : stars),
                Msg.p("bonus", pct(prog.prestige() * m.config().prestigeBonus + prog.rebirth() * m.config().rebirthBonus)),
                Msg.p("discount", pct(100 - 100 * m.priceFactor(prog))),
                Msg.p("boost", pct(m.islandBoost(viewer, kind))), Msg.p("shards", m.raw("shards.name." + kind.id()))));
        set(SLOT_TOOL, service.item(m.items().create(kind, island, m.profile(viewer)),
                m.msg().component(viewer, "menu.tool-name", Msg.p("tool", m.raw("item.name." + kind.id())), Msg.p("stars", stars)), info));
        set(SLOT_PRESTIGE, prestigeButton(prog, lv), click -> prestige(click.player()));
        long used = m.storage().used(m.profile(viewer), kind);
        long cap = m.storage().capacity(viewer, kind);
        set(SLOT_STORAGE, service.item(Material.BARREL, viewer, IslandToolsModule.ID, "menu.storage",
                Msg.p("tool", m.raw("item.name." + kind.id())), Msg.p("used", Format.number(used)), Msg.p("cap", Format.number(cap)),
                Msg.mm("bar", StorageMenu.bar(used, cap))), click -> openChild(new StorageMenu(m, kind)));
        List<GrindEnchants.Def> grind = m.enchants().defs().of(kind);
        if (m.config().enchantsEnabled && !grind.isEmpty()) {
            long owned = grind.stream().filter(d -> m.enchants().level(viewer, d) > 0).count();
            set(SLOT_ENCHANTS, service.item(Material.ENCHANTING_TABLE, viewer, IslandToolsModule.ID, "menu.enchants",
                    Msg.p("area", m.raw("enchant.menu.area." + kind.id())), Msg.number("owned", owned), Msg.number("total", grind.size())),
                    click -> openChild(new EnchantMenu(m, kind)));
        }

        List<Upgrades.Def> once = new ArrayList<>();
        List<Upgrades.Def> tracks = new ArrayList<>();
        for (Upgrades.Def d : m.upgrades().of(kind)) {
            (d.track() && d.max() > 1 ? tracks : once).add(d);
        }
        place(once, 9, prog);
        int top = (tracks.size() + 1) / 2;
        place(tracks.subList(0, top), 27, prog);
        place(tracks.subList(top, tracks.size()), 36, prog);
    }

    private void place(List<Upgrades.Def> defs, int rowStart, ToolStore.Prog prog) {
        int first = start(defs.size());
        for (int i = 0; i < defs.size() && i < 9; i++) {
            Upgrades.Def d = defs.get(i);
            MenuButton button = MenuButton.of(perkItem(prog, d), click -> buy(click, d, 1));
            if (d.max() > 1 && d.track()) {
                button = button.withShift(click -> buy(click, d, 10)).withRight(click -> buy(click, d, 100));
            }
            set(rowStart + first + i, button);
        }
    }

    private void buy(MenuClick click, Upgrades.Def d, int count) {
        m.buy(click.player(), kind, d.id(), count, this::refreshIfOpen);
    }

    private ItemStack perkItem(ToolStore.Prog prog, Upgrades.Def d) {
        int lv = prog.level(d.id());
        boolean max = lv >= d.max();
        IslandToolsModule.Refusal no = m.refusal(viewer, kind, d);
        List<Component> lore = new ArrayList<>(service.lines(viewer, IslandToolsModule.ID, "upgrade." + d.id() + ".lore"));
        lore.add(Component.empty());
        lore.addAll(service.lines(viewer, IslandToolsModule.ID, "menu.perk-level", Msg.mm("level", lv == 0 ? "-" : m.levelText(d, lv)),
                Msg.p("now", effectText(prog, d, lv))));
        if (max) {
            lore.addAll(service.lines(viewer, IslandToolsModule.ID, "menu.perk-max"));
        } else if (no != null && (no.key().equals("buy.locked-level") || no.key().equals("buy.locked-prestige"))) {
            lore.addAll(service.lines(viewer, IslandToolsModule.ID, no.key().equals("buy.locked-level") ? "menu.perk-locked-level"
                    : "menu.perk-locked-prestige", Msg.number("level", d.unlock()), Msg.number("prestige", Math.max(1, d.prestige()))));
        } else {
            lore.addAll(service.lines(viewer, IslandToolsModule.ID, "menu.perk-price",
                    Msg.p("next", effectText(prog, d, lv + 1)),
                    Msg.mm("cost", m.shardText(kind, m.price(prog, d, 1)))));
            lore.add(Component.empty());
            if (d.track() && d.max() > 1) {
                lore.addAll(service.javaLines(viewer, IslandToolsModule.ID, "menu.perk-hint",
                        Msg.mm("cost10", m.shardText(kind, m.price(prog, d, 10))), Msg.mm("cost100", m.shardText(kind, m.price(prog, d, 100)))));
            }
            lore.addAll(service.lines(viewer, IslandToolsModule.ID, "menu.perk-buy"));
        }
        Material icon = Material.matchMaterial(m.raw("upgrade." + d.id() + ".icon"));
        boolean locked = !max && no != null && no.key().startsWith("buy.locked");
        ItemStack base = new ItemStack(locked ? Material.GRAY_DYE : icon == null || !icon.isItem() ? Material.ENCHANTED_BOOK : icon);
        return service.item(base, m.msg().component(viewer, locked ? "menu.perk-name-locked" : "menu.perk-name",
                Msg.p("name", m.raw("upgrade." + d.id() + ".name"))), lore);
    }

    private ItemStack prestigeButton(ToolStore.Prog prog, ToolRules.Level lv) {
        if (lv.maxed() && m.rebirthNext(prog)) { // owner 6-7 Nov 2026: after prestige 50, rebirths
            int rebirth = prog.rebirth() + 1;
            return service.item(Material.TOTEM_OF_UNDYING, viewer, IslandToolsModule.ID, "menu.rebirth-ready", Msg.number("rebirth", rebirth),
                    Msg.p("bonus", pct(rebirth * m.config().rebirthBonus)));
        }
        int next = prog.prestige() + 1;
        String key = lv.maxed() ? "menu.prestige-ready" : "menu.prestige-locked";
        return service.item(lv.maxed() ? Material.NETHER_STAR : Material.GRAY_DYE, viewer, IslandToolsModule.ID, key,
                Msg.number("level", lv.level()), Msg.number("max", m.config().maxLevel), Msg.number("next", next),
                Msg.p("stars", ToolRules.stars(next, m.config().maxStars)), Msg.p("bonus", pct(next * m.config().prestigeBonus + prog.rebirth() * m.config().rebirthBonus)),
                Msg.p("shards", m.raw("shards.name." + kind.id())));
    }

    private void prestige(Player p) {
        IslandToolsModule.Refusal no = m.prestigeRefusal(p, kind);
        if (no != null) {
            m.msg().send(p, no.key(), no.tags());
            m.core().sounds().play(p, "error");
            return;
        }
        String tool = m.raw("item.name." + kind.id());
        String step = m.rebirthNext(m.view(p, kind)) ? "rebirth" : "prestige";
        openChild(new ConfirmMenu(v -> service.text(v, IslandToolsModule.ID, step + ".confirm-crumb"), null,
                v -> service.lines(v, IslandToolsModule.ID, step + ".confirm-details", Msg.p("tool", tool),
                        Msg.p("shards", m.raw("shards.name." + kind.id()))),
                pl -> m.prestige(pl, kind, () -> m.core().menus().open(pl, new ToolMenu(m, kind))), pl -> { }));
    }

    /** What a level does, in plain words (Bedrock-safe: everything in the lore). */
    private String effectText(ToolStore.Prog prog, Upgrades.Def d, int lv) {
        IslandToolsConfig c = m.config();
        if (lv <= 0) {
            return "-";
        }
        return switch (d.effect()) {
            case ENCHANT, HASTE -> m.raw("upgrade." + d.id() + ".name") + " " + IslandToolsModule.roman(lv);
            case SHARDS -> "+" + pct(lv * c.shardFinderPerLevel) + " " + m.raw("effect.shards"); // owner 7 Nov 2026: the number again
            case DROPS -> "+" + pct(lv * c.dropsPerLevel) + " " + m.raw("effect.drops");
            case GROWTH -> "+" + pct(lv * c.growthPerLevel) + " " + m.raw("effect.growth");
            case STORAGE -> Format.number(ToolRules.storage(lv, c.storageBase, c.storagePerLevel)) + " " + m.raw("effect.items");
            case SMELT -> pct(lv * c.smeltPerLevel) + " " + m.raw("effect.smelt");
            case WISDOM -> "+" + pct(lv * c.wisdomPerLevel) + " " + m.raw("effect.wisdom");
            case SELL -> "+" + pct(lv * c.sellPerLevel) + " " + m.raw("effect.sell");
            case LUCKY -> pct(lv * c.luckyPerLevel) + " " + m.raw("effect.lucky");
            case AUTOSELL -> m.raw("effect.auto-sell");
            case COMPACT -> m.raw("effect.compact");
            case SWEEP -> {
                ToolRules.Band band = m.band(viewer, kind);
                yield band == null ? "-" : pct(ToolRules.sweepChance(band, lv, d.max())) + " " + m.raw("effect.sweep")
                        .replace("{blocks}", String.valueOf(band.blocks()));
            }
        };
    }

    /** {@code 12.5%}: up to two decimals, no trailing zeros. */
    static String pct(double v) {
        String s = String.format(Locale.ROOT, "%.2f", Math.max(0, v));
        s = s.replaceAll("0+$", "").replaceAll("\\.$", "");
        return s + "%";
    }

    private void refreshIfOpen() {
        if (isOpen()) {
            refresh();
        }
    }

    private Component text(String key) {
        return service.text(viewer, IslandToolsModule.ID, key);
    }
}
