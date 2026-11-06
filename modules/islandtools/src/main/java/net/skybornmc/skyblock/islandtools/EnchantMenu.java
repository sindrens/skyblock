package net.skybornmc.skyblock.islandtools;

import net.kyori.adventure.text.Component;
import net.skybornmc.skyblock.common.economy.EconomyService;
import net.skybornmc.skyblock.common.menu.Menu;
import net.skybornmc.skyblock.common.menu.MenuButton;
import net.skybornmc.skyblock.common.menu.MenuClick;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.util.Format;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Owner 6 Nov 2026 (plans/TOOLS_V3.md): the player's own grind enchants of one tool. No glass, two centred rows:
 * <pre>
 *  . . . H . A . . .      3 the tool and your shards, 5 the buy amount (x1 / x10 / x100, Bedrock-friendly)
 *  . . a a a a . . .      area: Blast, Lightning, Meteor, Nuke (hoe: Explode, Row Reaper, Tornado, Meteor Shower)
 *  . . s s s s . . .      shards: Burst, Echo, Compound Interest, Jackpot
 *  . . l l l l l . .      loot: Rich Vein, Cash Vein, Quick Study, Motherlode, Geode
 *  . . b b b b . . .      boosts: Scholar, Combo, Lucky Streak, Keyfinder
 *  nav bar
 * </pre>
 * A click buys the chosen amount; on Java shift-click buys 10 and right-click 100 as well.
 */
final class EnchantMenu extends Menu {

    static final int SLOT_HEADER = 3;
    static final int SLOT_AMOUNT = 5;
    static final int[] AMOUNTS = {1, 10, 100};

    private final IslandToolsModule m;
    private final Kind kind;
    private int amount = 1;

    EnchantMenu(IslandToolsModule m, Kind kind) {
        this.m = m;
        this.kind = kind;
    }

    @Override
    public int rows() {
        return 6;
    }

    @Override
    public Component title() {
        return service.title(viewer, service.text(viewer, IslandToolsModule.ID, "menu.crumb"),
                service.text(viewer, IslandToolsModule.ID, "item.name." + kind.id()),
                service.text(viewer, IslandToolsModule.ID, "enchant.menu.crumb"));
    }

    @Override
    public String messageModule() {
        return IslandToolsModule.ID;
    }

    @Override
    public String infoKey() {
        return "enchant.menu.info";
    }

    @Override
    public String helpTopic() {
        return "tools";
    }

    /** The next buy amount after {@code now} (1 → 10 → 100 → 1). Pure. */
    static int nextAmount(int now) {
        for (int i = 0; i < AMOUNTS.length; i++) {
            if (AMOUNTS[i] == now) {
                return AMOUNTS[(i + 1) % AMOUNTS.length];
            }
        }
        return AMOUNTS[0];
    }

    @Override
    protected void build() {
        navBar();
        EnchantProcs e = m.enchants();
        long balance = m.core().services().optional(EconomyService.class)
                .map(s -> s.cached(viewer.getUniqueId(), kind.shards()).orElse(0L)).orElse(0L);
        String area = m.raw("enchant.menu.area." + kind.id());
        set(SLOT_HEADER, service.item(m.items().create(kind, m.island(viewer), m.profile(viewer)),
                m.msg().component(viewer, "enchant.menu.header.name", Msg.p("tool", m.raw("item.name." + kind.id()))),
                service.lines(viewer, IslandToolsModule.ID, "enchant.menu.header.lore", Msg.p("area", area),
                        Msg.p("shards", m.raw("shards.name." + kind.id())), Msg.mm("balance", m.shardText(kind, balance)))));
        set(SLOT_AMOUNT, service.item(Material.HOPPER, viewer, IslandToolsModule.ID, "enchant.menu.amount", Msg.number("amount", amount),
                Msg.number("next", nextAmount(amount))), click -> {
            amount = nextAmount(amount);
            m.core().sounds().play(click.player(), "click");
            refresh();
        });
        if (!e.loaded(viewer)) {
            return;
        }
        List<List<GrindEnchants.Def>> rows = groups(e.defs().of(kind));
        for (int r = 0; r < rows.size(); r++) {
            place(rows.get(r), 9 * (r + 1));
        }
    }

    /** The four rows of the menu: area, shards, loot, boosts (config order inside a row). Pure. */
    static List<List<GrindEnchants.Def>> groups(List<GrindEnchants.Def> defs) {
        List<List<GrindEnchants.Def>> out = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            out.add(new ArrayList<>());
        }
        for (GrindEnchants.Def d : defs) {
            int row = switch (d.effect()) {
                case AREA -> 0;
                case SHARDS, ECHO, STREAK_SHARDS -> 1;
                case EXTRA_ITEMS, CASH, PROGRESS, BASKET -> 2;
                case SKILL_XP, STREAK_XP, PITY, KEY -> 3;
            };
            out.get(row).add(d);
        }
        return out;
    }

    private void place(List<GrindEnchants.Def> defs, int rowStart) {
        int first = ToolMenu.start(defs.size());
        for (int i = 0; i < defs.size() && i < 9; i++) {
            GrindEnchants.Def d = defs.get(i);
            MenuButton button = MenuButton.of(item(d), click -> buy(click, d, amount))
                    .withShift(click -> buy(click, d, 10)).withRight(click -> buy(click, d, 100));
            set(rowStart + first + i, button);
        }
    }

    private void buy(MenuClick click, GrindEnchants.Def d, int count) {
        m.enchants().buy(click.player(), kind, d.id(), count, () -> {
            if (isOpen()) {
                refresh();
            }
        });
    }

    private ItemStack item(GrindEnchants.Def d) {
        EnchantProcs e = m.enchants();
        int lv = e.level(viewer, d);
        int cap = e.cap(viewer, d);
        String no = e.refusal(viewer, d);
        boolean locked = "enchant.locked-level".equals(no) || "enchant.locked-prestige".equals(no);
        boolean max = lv >= d.max() || "enchant.capped".equals(no);
        List<Component> lore = new ArrayList<>(service.lines(viewer, IslandToolsModule.ID, "enchant." + d.id() + ".lore"));
        lore.add(Component.empty());
        lore.addAll(service.lines(viewer, IslandToolsModule.ID, cap < d.max() ? "enchant.menu.level-cap" : "enchant.menu.level",
                Msg.number("level", lv), Msg.number("max", d.max()), Msg.number("cap", cap)));
        if (locked) {
            lore.addAll(service.lines(viewer, IslandToolsModule.ID, "enchant.menu." + no.substring("enchant.".length()), e.refusalTags(viewer, d)));
            return service.item(new ItemStack(Material.GRAY_DYE), m.msg().component(viewer, "enchant.menu.name-locked",
                    Msg.p("name", e.name(d))), lore);
        }
        String nextChance = max ? "" : chance(d, lv + 1);
        if (!d.effect().passive()) {
            lore.addAll(service.lines(viewer, IslandToolsModule.ID, max ? "enchant.menu.chance-max" : "enchant.menu.chance",
                    Msg.p("now", chance(d, lv)), Msg.p("next", nextChance)));
        }
        lore.addAll(service.lines(viewer, IslandToolsModule.ID, max ? "enchant.menu.effect-max" : "enchant.menu.effect",
                Msg.p("now", effect(d, lv)), Msg.p("next", max ? "" : effect(d, lv + 1))));
        if (d.cooldownSeconds() > 0) {
            lore.addAll(service.lines(viewer, IslandToolsModule.ID, "enchant.menu.cooldown", Msg.number("seconds", d.cooldownSeconds())));
        }
        if (lv >= d.max()) {
            lore.addAll(service.lines(viewer, IslandToolsModule.ID, "enchant.menu.max"));
        } else if (max) {
            lore.addAll(service.lines(viewer, IslandToolsModule.ID, "enchant.menu.capped", e.refusalTags(viewer, d)));
        } else {
            int n = Math.max(0, Math.min(amount, cap - lv));
            lore.addAll(service.lines(viewer, IslandToolsModule.ID, "enchant.menu.price", Msg.number("amount", n),
                    Msg.mm("cost", m.shardText(kind, d.price(lv, n)))));
            lore.add(Component.empty());
            lore.addAll(service.javaLines(viewer, IslandToolsModule.ID, "enchant.menu.hint",
                    Msg.mm("cost10", m.shardText(kind, d.price(lv, Math.max(0, Math.min(10, cap - lv))))),
                    Msg.mm("cost100", m.shardText(kind, d.price(lv, Math.max(0, Math.min(100, cap - lv)))))));
            lore.addAll(service.lines(viewer, IslandToolsModule.ID, "enchant.menu.buy", Msg.number("amount", n)));
        }
        Material icon = Material.matchMaterial(m.raw("enchant." + d.id() + ".icon"));
        ItemStack base = new ItemStack(icon == null || !icon.isItem() ? Material.ENCHANTED_BOOK : icon);
        base.setAmount(1);
        return service.item(base, m.msg().component(viewer, lv > 0 ? "enchant.menu.name" : "enchant.menu.name-new",
                Msg.p("name", e.name(d)), Msg.number("level", lv)), lore);
    }

    private String chance(GrindEnchants.Def d, int lv) {
        return lv <= 0 ? "-" : ToolMenu.pct(d.chance(lv));
    }

    /** What a level gives, in plain words (Bedrock-safe: everything in the lore). */
    private String effect(GrindEnchants.Def d, int lv) {
        if (lv <= 0) {
            return "-";
        }
        String v = num(d.value(lv));
        String text = m.raw("enchant.menu.gives." + d.effect().name().toLowerCase(Locale.ROOT));
        return text.replace("{value}", v).replace("{crate}", m.raw("enchant.crate." + m.config().enchantKeyCrate));
    }

    /** {@code 4}, {@code 4.5}, {@code 1,250}: at most one decimal. Pure. */
    static String num(double v) {
        double r = Math.round(v * 10) / 10.0;
        if (r == Math.rint(r)) {
            return Format.number((long) r);
        }
        return String.format(Locale.ROOT, "%.1f", r);
    }
}
