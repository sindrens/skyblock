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
 * Owner 6 Nov 2026 (plans/TOOLS_V3.md): the player's own grind enchants of one tool. Tools v6 (7 Nov 2026): one row per
 * group with a coloured banner first (its name and how many are open), up to 8 enchants after it, in unlock order:
 * <pre>
 *  . . . H . A . . .      3 the tool and your shards, 5 the buy amount (x1 / x10 / x100, Bedrock-friendly)
 *  ▌ a a a a a . . .      area: Blast, Lightning, Meteor, Nuke, Chain Reaction (hoe: Explode, Row Reaper, Tornado ...)
 *  ▌ s s s s s s s .      shards: Burst, Echo, Compound Interest, Storm, Rain, Booster Find, Jackpot
 *  ▌ l l l l l l l .      loot and finds: Rich Vein, Cash Vein, Golden Touch, Duplicator, Geode, Keyfinder, Crystal Find
 *  ▌ b b b b b b b .      boosts: Scholar, Combo, Quick Study, Motherlode, XP Surge, Frenzy, Lucky Streak
 *  nav bar
 * </pre>
 * A click buys the chosen amount; on Java shift-click buys 10 and right-click 100 as well.
 */
final class EnchantMenu extends Menu {

    static final int SLOT_HEADER = 3;
    static final int SLOT_AMOUNT = 5;
    /** Owner 7 Nov 2026: buy or switch on/off mode (Bedrock-safe: one plain click per mode). */
    static final int SLOT_MODE = 6;
    /** {@link #MAX} = as many levels as your shards pay for (owner 7 Nov 2026: Buy Max). */
    static final int MAX = -1;
    static final int[] AMOUNTS = {1, 10, 100, MAX};

    private final IslandToolsModule m;
    private final Kind kind;
    private int amount = 1;
    private boolean switching;

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

    /** "1", "10", "100" or "Max". */
    private String amountText(int a) {
        return a == MAX ? m.raw("enchant.menu.amount-max") : String.valueOf(a);
    }

    /** Buy Max: how many levels after {@code have} (at most {@code room}) {@code balance} shards pay for. Pure. */
    static int affordable(GrindEnchants.Def d, int have, int room, long balance) {
        int n = 0;
        long sum = 0;
        while (n < room) {
            long next = d.priceOf(have + n + 1);
            if (sum + next > balance) {
                break;
            }
            sum += next;
            n++;
        }
        return n;
    }

    /** The next buy amount after {@code now} (1 → 10 → 100 → Max → 1). Pure. */
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
        set(SLOT_AMOUNT, service.item(Material.HOPPER, viewer, IslandToolsModule.ID, "enchant.menu.amount", Msg.p("amount", amountText(amount)),
                Msg.p("next", amountText(nextAmount(amount)))), click -> {
            amount = nextAmount(amount);
            m.core().sounds().play(click.player(), "click");
            refresh();
        });
        set(SLOT_MODE, service.item(switching ? Material.LEVER : Material.EMERALD, viewer, IslandToolsModule.ID,
                switching ? "enchant.menu.mode-switch" : "enchant.menu.mode-buy"), click -> {
            switching = !switching;
            m.core().sounds().play(click.player(), "click");
            refresh();
        });
        if (!e.loaded(viewer)) {
            return;
        }
        List<List<GrindEnchants.Def>> rows = groups(e.defs().of(kind));
        for (int r = 0; r < rows.size(); r++) {
            if (rows.get(r).isEmpty()) {
                continue;
            }
            header(r, rows.get(r), 9 * (r + 1));
            place(rows.get(r), 9 * (r + 1) + 1);
        }
    }

    static final String[] GROUPS = {"area", "shards", "loot", "boosts"};
    private static final Material[] BANNERS = {Material.RED_BANNER, Material.PURPLE_BANNER, Material.YELLOW_BANNER, Material.LIME_BANNER};

    /** The group's banner: its name, what it does and how many of its enchants are open. */
    private void header(int group, List<GrindEnchants.Def> defs, int slot) {
        EnchantProcs e = m.enchants();
        long open = defs.stream().filter(d -> {
            String no = e.refusal(viewer, d);
            return !"enchant.locked-level".equals(no) && !"enchant.locked-prestige".equals(no);
        }).count();
        set(slot, service.item(BANNERS[group], viewer, IslandToolsModule.ID, "enchant.menu.group." + GROUPS[group],
                Msg.number("open", open), Msg.number("total", defs.size())));
    }

    /** The four rows of the menu: area, shards, loot and finds, boosts (unlock order inside a row, 8 at most). Pure. */
    static List<List<GrindEnchants.Def>> groups(List<GrindEnchants.Def> defs) {
        List<List<GrindEnchants.Def>> out = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            out.add(new ArrayList<>());
        }
        for (GrindEnchants.Def d : defs) {
            out.get(group(d)).add(d);
        }
        out.forEach(row -> row.sort(java.util.Comparator.comparingInt(GrindEnchants.Def::unlockLevel)
                .thenComparingInt(GrindEnchants.Def::unlockPrestige)));
        return out;
    }

    /** The row of an enchant: 0 area, 1 shards, 2 loot and finds, 3 boosts. Pure. */
    static int group(GrindEnchants.Def d) {
        return switch (d.effect()) {
            case AREA, CHAIN -> 0;
            case SHARDS, ECHO, STREAK_SHARDS, STORM, RAIN, FEVER -> 1;
            case EXTRA_ITEMS, CASH, GOLDEN, DUPLICATE, BASKET, KEY, CRYSTALS -> 2;
            case SKILL_XP, STREAK_XP, PROGRESS, XP_SURGE, FRENZY, PITY -> 3;
        };
    }

    private void place(List<GrindEnchants.Def> defs, int rowStart) {
        for (int i = 0; i < defs.size() && i < 8; i++) {
            GrindEnchants.Def d = defs.get(i);
            MenuButton button = MenuButton.of(item(d), click -> {
                if (switching) {
                    m.enchants().toggle(click.player(), d, () -> { if (isOpen()) { refresh(); } });
                } else {
                    buy(click, d, amount);
                }
            }).withShift(click -> buy(click, d, 10)).withRight(click -> buy(click, d, 100));
            set(rowStart + i, button);
        }
    }

    private void buy(MenuClick click, GrindEnchants.Def d, int count) {
        if (count == MAX) {
            EnchantProcs e = m.enchants();
            long balance = m.core().services().optional(EconomyService.class)
                    .map(s -> s.cached(viewer.getUniqueId(), kind.shards()).orElse(0L)).orElse(0L);
            int lv = e.level(viewer, d);
            count = Math.max(1, affordable(d, lv, Math.max(0, e.cap(viewer, d) - lv), balance)); // 1 = the "too poor" message
        }
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
        java.util.OptionalInt nextBoost = d.nextMilestone(lv); // owner 7 Nov 2026: the next extra boost is always in sight
        if (nextBoost.isPresent()) {
            int at = nextBoost.getAsInt();
            lore.addAll(service.lines(viewer, IslandToolsModule.ID, "enchant.menu.milestone",
                    Msg.p("bonus", num(m.config().enchantMilestoneBonus)), Msg.number("level", at), Msg.number("left", at - lv),
                    Msg.mm("bar", bar(lv - d.milestones(lv) * d.step(), d.step())), Msg.p("now", num(d.boost(lv)))));
        } else if (d.boost(lv) > 1) {
            lore.addAll(service.lines(viewer, IslandToolsModule.ID, "enchant.menu.milestone-done", Msg.p("now", num(d.boost(lv)))));
        }
        boolean off = e.off(viewer, d);
        if (off) {
            lore.addAll(service.lines(viewer, IslandToolsModule.ID, "enchant.menu.switched-off"));
        }
        if (switching) {
            lore.addAll(service.lines(viewer, IslandToolsModule.ID, off ? "enchant.menu.switch-on" : "enchant.menu.switch-off"));
            return finish(d, lv, off, lore);
        }
        if (lv >= d.max()) {
            lore.addAll(service.lines(viewer, IslandToolsModule.ID, "enchant.menu.max"));
        } else if (max) {
            lore.addAll(service.lines(viewer, IslandToolsModule.ID, "enchant.menu.capped", e.refusalTags(viewer, d)));
        } else {
            long balance = m.core().services().optional(EconomyService.class)
                    .map(s -> s.cached(viewer.getUniqueId(), kind.shards()).orElse(0L)).orElse(0L);
            int n = amount == MAX ? Math.max(1, affordable(d, lv, cap - lv, balance)) : Math.max(0, Math.min(amount, cap - lv));
            lore.addAll(service.lines(viewer, IslandToolsModule.ID, "enchant.menu.price", Msg.number("amount", n),
                    Msg.mm("cost", m.shardText(kind, d.price(lv, n)))));
            lore.add(Component.empty());
            lore.addAll(service.javaLines(viewer, IslandToolsModule.ID, "enchant.menu.hint",
                    Msg.mm("cost10", m.shardText(kind, d.price(lv, Math.max(0, Math.min(10, cap - lv))))),
                    Msg.mm("cost100", m.shardText(kind, d.price(lv, Math.max(0, Math.min(100, cap - lv)))))));
            lore.addAll(service.lines(viewer, IslandToolsModule.ID, "enchant.menu.buy", Msg.number("amount", n)));
        }
        return finish(d, lv, e.off(viewer, d), lore);
    }

    private ItemStack finish(GrindEnchants.Def d, int lv, boolean off, List<Component> lore) {
        EnchantProcs e = m.enchants();
        Material icon = Material.matchMaterial(m.raw("enchant." + d.id() + ".icon"));
        ItemStack base = new ItemStack(icon == null || !icon.isItem() ? Material.ENCHANTED_BOOK : icon);
        base.setAmount(1);
        return service.item(base, m.msg().component(viewer, off ? "enchant.menu.name-off" : lv > 0 ? "enchant.menu.name" : "enchant.menu.name-new",
                Msg.p("name", e.name(d)), Msg.number("level", lv)), lore);
    }

    /** A 10-part bar to the next milestone ({@code done} of {@code total} levels). Pure. */
    static String bar(int done, int total) {
        int filled = total <= 0 ? 10 : (int) Math.min(10, Math.max(0, Math.floor(done * 10.0 / total)));
        return "<c:success>" + "|".repeat(filled) + "</c:success><c:muted>" + "|".repeat(10 - filled) + "</c:muted>";
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
