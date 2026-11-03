package net.skybornmc.skyblock.islandtools;

import net.kyori.adventure.text.Component;
import net.skybornmc.skyblock.common.menu.ConfirmMenu;
import net.skybornmc.skyblock.common.menu.Menu;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.util.Format;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * One island tool: the tool (4), the next tier (13), its 7 upgrades with level dots, now/next effect and price (28-34,
 * centred), a new copy (49 area via 40). Upgrades are crystals only (T7); a confirm only above {@code confirm-above} (T8).
 */
final class ToolMenu extends Menu {

    static final int SLOT_TOOL = 4;
    static final int SLOT_TIER = 13;
    static final int[] UPGRADE_SLOTS = {28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43};
    static final int SLOT_COPY = 22;

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

    @Override
    protected void build() {
        navBar();
        ToolStore.Rec r = m.rec(viewer, kind);
        if (r == null) {
            return;
        }
        set(SLOT_TOOL, m.items().create(r));
        IslandToolsModule.Purchase tier = m.tierUp(viewer, kind);
        if (tier == null) {
            set(SLOT_TIER, service.item(Material.NETHERITE_INGOT, viewer, IslandToolsModule.ID, "menu.tier-max"));
        } else {
            Material next = Material.matchMaterial(ToolRules.material(kind, r.tier() + 1));
            set(SLOT_TIER, service.item(next == null ? Material.ANVIL : next, viewer, IslandToolsModule.ID, "menu.tier-next",
                    Msg.p("from", m.raw("tier." + ToolRules.TIERS.get(r.tier()))),
                    Msg.p("to", m.raw("tier." + ToolRules.TIERS.get(r.tier() + 1))), Msg.p("cost", Format.number(tier.cost())),
                    Msg.p("money", Format.money(tier.money()))),
                    click -> purchase(click.player(), tier));
        }
        set(SLOT_COPY, service.item(Material.NAME_TAG, viewer, IslandToolsModule.ID, "menu.copy"), click -> {
            m.newCopy(click.player(), kind);
            refresh();
        });
        List<Upgrades.Def> defs = m.upgrades().of(kind);
        for (int i = 0; i < defs.size() && i < UPGRADE_SLOTS.length; i++) {
            Upgrades.Def d = defs.get(i);
            set(UPGRADE_SLOTS[i], upgradeItem(r, d), click -> {
                IslandToolsModule.Purchase p = m.upgrade(click.player(), kind, d.id());
                if (p != null) {
                    purchase(click.player(), p);
                }
            });
        }
    }

    private ItemStack upgradeItem(ToolStore.Rec r, Upgrades.Def d) {
        int lv = r.level(d.id());
        boolean max = lv >= d.max();
        List<Component> lore = new ArrayList<>(service.lines(viewer, IslandToolsModule.ID, "upgrade." + d.id() + ".lore"));
        StringBuilder dots = new StringBuilder();
        for (int i = 1; i <= d.max(); i++) {
            dots.append(i <= lv ? "<c:flame>●</c:flame>" : "<c:locked>●</c:locked>");
        }
        lore.add(m.msg().component(viewer, "menu.level-line", Msg.mm("dots", dots.toString()), Msg.number("level", lv), Msg.number("max", d.max())));
        lore.add(m.msg().component(viewer, "menu.effect-line", Msg.p("now", effectText(d, lv)), Msg.p("next", max ? "-" : effectText(d, lv + 1))));
        lore.add(Component.empty());
        if (max) {
            lore.addAll(service.lines(viewer, IslandToolsModule.ID, "menu.upgrade-max"));
        } else {
            // Owner 3 Nov 2026: shards + the island's task for this tool.
            long task = m.islandOf(m.profile(viewer)) == null ? 0 : m.taskTarget(lv);
            long have = Math.min(task, m.taskProgress(viewer, kind));
            if (task > 0) {
                lore.addAll(service.lines(viewer, IslandToolsModule.ID, have >= task ? "menu.task-done" : "menu.task",
                        Msg.p("action", m.raw("task." + kind.id())), Msg.p("have", Format.number(have)), Msg.p("task", Format.number(task)),
                        Msg.mm("bar", bar(have, task))));
            }
            lore.addAll(service.lines(viewer, IslandToolsModule.ID, "menu.upgrade-buy", Msg.p("cost", Format.number(d.cost(lv)))));
        }
        Material icon = Material.matchMaterial(m.raw("upgrade." + d.id() + ".icon"));
        return service.item(new ItemStack(icon == null || !icon.isItem() ? Material.ENCHANTED_BOOK : icon),
                m.msg().component(viewer, "menu.upgrade-name", Msg.p("name", m.raw("upgrade." + d.id() + ".name"))), lore);
    }

    /** 10 dots (Bedrock-safe), filled up to have/task; MiniMessage. */
    static String bar(long have, long task) {
        int filled = task <= 0 ? 10 : (int) Math.min(10, have * 10 / task);
        return "<c:flame>" + "●".repeat(filled) + "</c:flame><c:locked>" + "●".repeat(10 - filled) + "</c:locked>";
    }

    /** What a level does, in plain words (Bedrock-safe: everything in the lore). */
    private String effectText(Upgrades.Def d, int lv) {
        IslandToolsConfig c = m.config();
        return switch (d.effect()) {
            case ENCHANT -> lv == 0 ? "-" : m.raw("upgrade." + d.id() + ".name") + " " + lv;
            case HASTE -> lv == 0 ? "-" : "Haste " + lv;
            case MAGNET -> lv == 0 ? "-" : m.raw("effect.magnet");
            case SMELT -> Format.number(Math.round(lv * c.smeltPerLevel)) + "%";
            case DROPS -> "+" + Format.number(Math.round(lv * c.dropsPerLevel)) + "%";
            case TIMBER -> "+" + (lv * c.timberPerLevel) + " logs";
            case EXPERIENCE -> "+" + Format.number(Math.round(lv * c.experiencePerLevel)) + "% XP";
            case LIFESTEAL -> Format.number(Math.round(lv * c.lifestealPerLevel)) + "%";
            case ROW -> ToolRules.row(lv, c.rowLengths) + " crops";
            case STORAGE -> Format.number(ToolRules.storage(lv, c.storageBase, c.storageFactor)) + " items";
            case SHARDS -> "+" + String.format(java.util.Locale.ROOT, "%.1f", lv * c.shardFinderPerLevel) + "% shards";
            case AUTOSELL -> lv == 0 ? "-" : m.raw("effect.auto-sell");
        };
    }

    private void purchase(Player p, IslandToolsModule.Purchase purchase) {
        if (ToolRules.needsConfirm(purchase.cost(), m.config().confirmAbove)) {
            openChild(new ConfirmMenu(v -> service.text(v, IslandToolsModule.ID, "menu.confirm-crumb"), null,
                    v -> service.lines(v, IslandToolsModule.ID, "menu.confirm-details", Msg.p("cost", Format.number(purchase.cost()))),
                    pl -> m.buy(pl, purchase, () -> m.core().menus().open(pl, new ToolMenu(m, kind))), pl -> { }));
            return;
        }
        m.buy(p, purchase, this::refreshIfOpen);
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
