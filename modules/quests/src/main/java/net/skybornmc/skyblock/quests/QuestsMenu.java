package net.skybornmc.skyblock.quests;

import net.kyori.adventure.text.Component;
import net.skybornmc.skyblock.common.menu.ConfirmMenu;
import net.skybornmc.skyblock.common.menu.Menu;
import net.skybornmc.skyblock.common.menu.PickListLayout;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.util.Format;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * {@code /quests} (PHASE-10 mockup, GUI-1 framework): one screen with three tabs in row 0 that switch in place (Daily,
 * Weekly, Island). The quests sit in row 2 (progress bar and rewards in the lore), the board's summary on 40 (all-done key
 * or helpers, reset countdown). A daily quest that is still open swaps on click after a confirm (free swaps per day).
 * Rewards pay by themselves, so there are no claim buttons. Updates every second; every action is a plain tap (Bedrock).
 */
final class QuestsMenu extends Menu {

    static final int SLOT_TAB_DAILY = 2;
    static final int SLOT_TAB_WEEKLY = 4;
    static final int SLOT_TAB_ISLAND = 6;
    static final int SLOT_SUMMARY = 40;
    static final int SLOT_STATE = 22;

    private final QuestsModule m;
    private QuestKind tab;

    QuestsMenu(QuestsModule module, QuestKind tab) {
        this.m = module;
        this.tab = tab == null ? QuestKind.DAILY : tab;
    }

    QuestKind tab() {
        return tab;
    }

    /** Slots of {@code count} quests in row 2: spaced out up to 4, side by side for 5, the pick-list rows above that. */
    static int[] slots(int count) {
        return switch (count) {
            case 0 -> new int[0];
            case 1 -> new int[]{22};
            case 2 -> new int[]{21, 23};
            case 3 -> new int[]{20, 22, 24};
            case 4 -> new int[]{19, 21, 23, 25};
            default -> {
                int n = Math.min(count, 7);
                int[] out = new int[n];
                for (int i = 0; i < n; i++) {
                    out[i] = PickListLayout.slot(i, n) + 9;
                }
                yield out;
            }
        };
    }

    @Override
    public int rows() {
        return 6;
    }

    @Override
    public Component title() {
        return service.title(viewer, service.text(viewer, QuestsModule.ID, "menu.title"),
                service.text(viewer, QuestsModule.ID, "menu.tabs." + tab.id() + ".crumb"));
    }

    @Override
    public String messageModule() {
        return QuestsModule.ID;
    }

    @Override
    public String infoKey() {
        return "menu.info." + tab.id();
    }

    @Override
    public String helpTopic() {
        return "quests";
    }

    @Override
    public int tickInterval() {
        return 20;
    }

    @Override
    protected void tick() {
        refresh();
    }

    @Override
    protected void build() {
        frame();
        tab(SLOT_TAB_DAILY, QuestKind.DAILY, Material.SUNFLOWER);
        tab(SLOT_TAB_WEEKLY, QuestKind.WEEKLY, Material.CLOCK);
        tab(SLOT_TAB_ISLAND, QuestKind.ISLAND, Material.GRASS_BLOCK);
        if (!m.settings.enabled) {
            set(SLOT_STATE, service.item(Material.GRAY_DYE, viewer, QuestsModule.ID, "menu.disabled"));
            return;
        }
        if (m.count(tab) <= 0) {
            setEmpty(QuestsModule.ID, "menu.board-off");
            return;
        }
        UUID owner = tab == QuestKind.ISLAND ? m.countingIsland(viewer) : m.profileKey(viewer);
        if (owner == null) {
            setEmpty(QuestsModule.ID, "menu.island.no-island");
            return;
        }
        Optional<QuestSet> found = tab == QuestKind.ISLAND ? m.engine().island(owner) : m.engine().set(owner, tab);
        if (found.isEmpty()) {
            if (m.engine().loading(tab, owner)) {
                setLoading(SLOT_STATE);
            } else {
                m.engine().load(viewer);
                setEmpty(QuestsModule.ID, "menu.nothing-yet");
            }
            return;
        }
        QuestSet set = found.get();
        int[] slots = slots(set.quests.size());
        for (int i = 0; i < slots.length; i++) {
            Quest q = set.quests.get(i);
            set(slots[i], questItem(set, q), click -> clicked(click.player(), set, q));
        }
        set(SLOT_SUMMARY, summary(set));
    }

    private void tab(int slot, QuestKind kind, Material icon) {
        boolean selected = kind == tab;
        set(slot, service.tab(viewer, new ItemStack(icon), service.text(viewer, QuestsModule.ID, "menu.tabs." + kind.id() + ".name"),
                service.lines(viewer, QuestsModule.ID, "menu.tabs." + kind.id() + ".lore"), selected, () -> tab = kind));
    }

    static Material icon(QuestType type) {
        return switch (type) {
            case MINE -> Material.IRON_PICKAXE;
            case CHOP -> Material.IRON_AXE;
            case KILL -> Material.IRON_SWORD;
            case SELL -> Material.GOLD_NUGGET;
            case TOOL_LEVELS -> Material.EXPERIENCE_BOTTLE;
            case FORGE -> Material.SMITHING_TABLE;
            case ENCHANT -> Material.ENCHANTED_BOOK;
            case VISIT -> Material.COMPASS;
            case LIKE -> Material.POPPY;
        };
    }

    private ItemStack questItem(QuestSet set, Quest q) {
        Component name = m.text().name(viewer, q);
        List<Component> lore = new ArrayList<>(m.text().lore(viewer, set.kind, q));
        if (q.done()) {
            return service.item(Material.LIME_DYE, service.text(viewer, QuestsModule.ID, "menu.quest.done-name", Msg.c("quest", name)), lore);
        }
        if (set.kind == QuestKind.DAILY && m.settings.dailyRerolls > 0) {
            int left = m.engine().swapsLeft(set);
            lore.add(Component.empty());
            lore.addAll(service.lines(viewer, QuestsModule.ID, left > 0 ? "menu.quest.swap" : "menu.quest.no-swap", Msg.number("left", left)));
        }
        ItemStack item = service.item(icon(q.type), service.text(viewer, QuestsModule.ID, "menu.quest.name", Msg.c("quest", name)), lore);
        return item;
    }

    private void clicked(Player player, QuestSet set, Quest q) {
        if (set.kind != QuestKind.DAILY || q.done() || m.settings.dailyRerolls <= 0) {
            return;
        }
        if (m.engine().swapsLeft(set) <= 0) {
            m.fail(player, "swap.none-left");
            return;
        }
        int slot = q.slot;
        ItemStack shown = service.item(icon(q.type), m.text().name(player, q), List.of());
        openChild(new ConfirmMenu(v -> service.text(v, QuestsModule.ID, "swap.crumb"), shown,
                v -> service.lines(v, QuestsModule.ID, "swap.details", Msg.c("quest", m.text().name(v, q)),
                        Msg.p("progress", m.text().amount(q, q.progress)), Msg.p("target", m.text().amount(q, q.target)),
                        Msg.number("left", Math.max(0, m.engine().swapsLeft(set) - 1))),
                p -> {
                    m.engine().swap(p, slot);
                    m.open(p, QuestKind.DAILY, null);
                },
                p -> { }));
    }

    private ItemStack summary(QuestSet set) {
        String reset = Format.duration(Duration.ofMillis(QuestWindows.untilReset(set.kind, System.currentTimeMillis(), m.zone())));
        List<Component> lore = new ArrayList<>();
        if (set.kind == QuestKind.ISLAND) {
            lore.addAll(service.lines(viewer, QuestsModule.ID, "menu.summary.island", Msg.number("min", Math.round(m.settings.helperMinPercent)),
                    Msg.p("time", reset), Msg.number("done", set.doneCount()), Msg.number("total", set.quests.size())));
            return service.item(Material.GRASS_BLOCK, service.text(viewer, QuestsModule.ID, "menu.summary.island-name"), lore);
        }
        String crate = set.kind == QuestKind.DAILY ? m.settings.dailyBonusCrate : m.settings.weeklyBonusCrate;
        boolean hasBonus = crate != null && !crate.isBlank();
        String key = !hasBonus ? "menu.summary.no-bonus" : set.bonusPaid ? "menu.summary.bonus-paid" : "menu.summary.bonus";
        lore.addAll(service.lines(viewer, QuestsModule.ID, key, Msg.p("crate", m.text().crateName(crate)), Msg.number("done", set.doneCount()),
                Msg.number("total", set.quests.size()), Msg.p("time", reset)));
        ItemStack item = service.item(set.bonusPaid ? Material.LIME_DYE : Material.TRIPWIRE_HOOK,
                service.text(viewer, QuestsModule.ID, "menu.summary." + set.kind.id() + "-name", Msg.number("done", set.doneCount()),
                        Msg.number("total", set.quests.size())), lore);
        if (set.allDone()) {
            item.editMeta(meta -> meta.setEnchantmentGlintOverride(true));
        }
        return item;
    }
}
