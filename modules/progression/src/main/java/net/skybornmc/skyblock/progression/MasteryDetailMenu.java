package net.skybornmc.skyblock.progression;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.skybornmc.skyblock.common.island.IslandService;
import net.skybornmc.skyblock.common.menu.Menu;
import net.skybornmc.skyblock.common.menu.MenuButton;
import net.skybornmc.skyblock.common.progression.TaskType;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.util.Format;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * One mastery (PHASE-5 M5.5), the same screen as an upgrade's: the mastery, the tasks of its next rank (deliver tasks open
 * their hand-in screen), its ranks with effect and price (14 at a time around the next one), and the Buy button (gray with
 * the reason when it can't be bought). Ascension shows only the item and the Buy button. One plain click each (Bedrock).
 * <pre>
 *  R O B B ◆ B B O R      4 the mastery
 *  B t t t t t t t B      10-16 tasks of the next rank
 *  B r r r r r r r B      19-25 ranks
 *  B r r r r r r r B      28-34 ranks
 *  B . . . $ . . . B      40 buy
 *  ← O B ? ✘ H B O R
 * </pre>
 */
final class MasteryDetailMenu extends Menu {

    static final int SLOT_HEADER = 4;
    static final int[] TASK_SLOTS = {10, 11, 12, 13, 14, 15, 16};
    static final int[] RANK_SLOTS = {19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};
    static final int SLOT_BUY = 40;

    private final MasteryMenus menus;
    private final UUID islandId;
    /** The mastery id, or {@link MasteryRules#ASCENSION_ID}. */
    private final String masteryId;
    private long bank;
    private boolean fetching;
    private final ItemMemo<ItemStack> memo = new ItemMemo<>();

    MasteryDetailMenu(MasteryMenus menus, UUID islandId, String masteryId, long bank) {
        this.menus = menus;
        this.islandId = islandId;
        this.masteryId = masteryId;
        this.bank = bank >= 0 ? bank : menus.masteries().upgrades().knownBank(islandId);
    }

    private boolean ascension() {
        return MasteryRules.ASCENSION_ID.equals(masteryId);
    }

    private MasteryCatalog.Def def() {
        return ascension() ? null : menus.masteries().catalog().get(masteryId).orElse(null);
    }

    @Override
    public int rows() {
        return 6;
    }

    @Override
    public Component title() {
        MasteryCatalog.Def def = def();
        Component name = ascension() ? menus.masteries().ascensionName(viewer)
                : def == null ? Component.text(masteryId) : menus.masteries().masteryName(viewer, def);
        return service.title(viewer, text("masteries.menu.crumb"), name);
    }

    @Override
    public String messageModule() {
        return ProgressionModule.ID;
    }

    @Override
    public String infoKey() {
        return ascension() ? "masteries.ascension.info" : "masteries.detail.info";
    }

    @Override
    public String helpTopic() {
        return "island-upgrades";
    }

    @Override
    public int tickInterval() {
        return menus.masteries().upgrades().config().refreshTicks;
    }

    @Override
    protected void tick() {
        IslandService islands = menus.masteries().islands().orElse(null);
        if (islands == null || islands.island(islandId).isEmpty() || (!ascension() && def() == null) || !menus.masteries().available()) {
            viewer.closeInventory();
            return;
        }
        fetchBank();
        refresh();
    }

    private void fetchBank() {
        if (fetching) {
            return;
        }
        fetching = true;
        menus.masteries().upgrades().bankBalance(islandId).whenComplete((value, error) -> menus.masteries().core().scheduler()
                .main("progression-mastery-bank", () -> {
                    fetching = false;
                    long v = error != null || value == null ? -1 : value;
                    if (v != bank) {
                        bank = v;
                        if (isOpen()) {
                            refresh();
                        }
                    }
                }));
    }

    @Override
    protected void build() {
        frame();
        if (bank < 0 && !fetching) {
            fetchBank();
        }
        MasteryManager masteries = menus.masteries();
        if (ascension()) {
            set(SLOT_HEADER, MenuButton.display(memo.get(SLOT_HEADER, menus.ascensionSignature(viewer, islandId, bank, false),
                    () -> service.mark(menus.ascensionItem(service, viewer, islandId, bank, false)))));
            MasteryRules.Status status = masteries.ascensionStatus(viewer, islandId, bank);
            set(SLOT_BUY, buyButton(null, status, Math.max(0, masteries.ascensionCost(islandId))));
            return;
        }
        MasteryCatalog.Def def = def();
        if (def == null) {
            return;
        }
        int current = masteries.rank(islandId, def.id());
        set(SLOT_HEADER, MenuButton.display(memo.get(SLOT_HEADER, menus.masterySignature(viewer, islandId, def, bank, false),
                () -> service.mark(menus.masteryItem(service, viewer, islandId, def, bank, false)))));
        List<TaskCatalog.TaskDef> taskDefs = current >= def.maxRank() ? List.of() : masteries.tasks().catalog().byUnlock(def.unlockKey(current + 1));
        for (int i = 0; i < taskDefs.size() && i < TASK_SLOTS.length; i++) {
            set(TASK_SLOTS[i], task(TASK_SLOTS[i], taskDefs.get(i)));
        }
        int first = firstShownRank(def.maxRank(), current, RANK_SLOTS.length);
        for (int i = 0; i < RANK_SLOTS.length && first + i <= def.maxRank(); i++) {
            int n = first + i;
            String state = n <= current ? "bought" : n == current + 1 ? "next" : "later";
            set(RANK_SLOTS[i], MenuButton.display(memo.get(RANK_SLOTS[i], UpgradeMenus.signature(menus.upgradeMenus().textsKey(), def, n, state),
                    () -> service.mark(rankItem(def, n, state)))));
        }
        MasteryCatalog.Rank next = def.rank(current + 1);
        set(SLOT_BUY, buyButton(def, masteries.status(viewer, islandId, def, bank), next == null ? 0 : next.cost()));
    }

    /** First rank shown when only {@code slots} fit: a window that keeps the next rank in view (3 bought ones before it). */
    static int firstShownRank(int max, int current, int slots) {
        if (max <= slots) {
            return 1;
        }
        int first = Math.max(1, current + 1 - 3);
        return Math.min(first, max - slots + 1);
    }

    private MenuButton task(int slot, TaskCatalog.TaskDef taskDef) {
        TaskMenus book = menus.upgradeMenus().taskMenus();
        boolean done = menus.masteries().tasks().completed(islandId, taskDef.id());
        boolean deliver = taskDef.info().type() == TaskType.DELIVER && !done;
        ItemStack item = memo.get(slot, UpgradeMenus.signature(menus.upgradeMenus().textsKey(), taskDef, done,
                menus.masteries().tasks().progressOf(islandId, taskDef)), () -> service.mark(book.taskItem(service, viewer, islandId, taskDef, true)));
        if (!deliver) {
            return MenuButton.display(item);
        }
        return MenuButton.of(item, click -> book.openTask(viewer, taskDef.id(), this));
    }

    private ItemStack rankItem(MasteryCatalog.Def def, int n, String state) {
        MasteryCatalog.Rank rank = def.rank(n);
        TagResolver[] p = {Msg.p("level", UpgradeRules.roman(n)), Msg.c("effect", menus.masteries().effectText(viewer, def, n)),
                Msg.p("cost", Format.money(rank.cost())), Msg.p("tier", rank.tier()), Msg.p("island-level", rank.islandLevel()),
                Msg.c("pillar", menus.masteries().pillarName(viewer, def)), Msg.p("pillar-level", rank.pillarLevel())};
        Material icon = switch (state) {
            case "bought" -> Material.LIME_STAINED_GLASS_PANE;
            case "next" -> Material.YELLOW_STAINED_GLASS_PANE;
            default -> Material.GRAY_STAINED_GLASS_PANE;
        };
        List<Component> lore = new ArrayList<>(lines("masteries.detail.rank.lore", p));
        if (def.pillar() != null && rank.pillarLevel() > 0) {
            lore.addAll(lines("masteries.item.pillar-level", p));
        }
        if (rank.islandLevel() > 0) {
            lore.addAll(lines("upgrades.detail.level.island-level", p));
        }
        lore.addAll(lines("upgrades.detail.level." + state, p));
        return service.item(new ItemStack(icon), text("masteries.detail.rank.name-" + state, p), lore);
    }

    private MenuButton buyButton(MasteryCatalog.Def def, MasteryRules.Status status, long cost) {
        MasteryManager masteries = menus.masteries();
        MasteryCatalog.Rank next = def == null ? null : def.rank(masteries.rank(islandId, def.id()) + 1);
        TagResolver[] p = {Msg.p("cost", Format.money(cost)), Msg.p("bank", bank < 0 ? "..." : Format.money(bank)),
                Msg.p("island-level", next == null ? 0 : next.islandLevel()), Msg.c("pillar", masteries.pillarName(viewer, def)),
                Msg.p("pillar-level", next == null ? 0 : next.pillarLevel()), Msg.c("requires", masteries.requiredUpgrades(viewer, def))};
        Object signature = UpgradeMenus.signature(menus.upgradeMenus().textsKey(), masteryId, status, cost, bank);
        if (status != MasteryRules.Status.READY) {
            String reason = def == null && status == MasteryRules.Status.NEEDS_MAXED ? "masteries.state.needs-all" : MasteryMenus.stateKey(status);
            List<Component> lore = lines(reason, p);
            return MenuButton.display(memo.get(SLOT_BUY, signature, () -> service.mark(service.item(new ItemStack(Material.GRAY_DYE),
                    text("upgrades.detail.buy.name-locked", p), lore))));
        }
        ItemStack item = memo.get(SLOT_BUY, signature, () -> service.mark(service.item(new ItemStack(Material.LIME_CONCRETE),
                text("upgrades.detail.buy.name", p), lines("upgrades.detail.buy.lore", p))));
        return MenuButton.of(item, click -> {
            Menu back = parent();
            openChild(menus.confirm(service, viewer, islandId, def, bank, () -> menus.masteries().core().menus()
                    .open(viewer, new MasteryDetailMenu(menus, islandId, masteryId, -1).parent(back))));
        });
    }

    private Component text(String key, TagResolver... placeholders) {
        return service.text(viewer, ProgressionModule.ID, key, placeholders);
    }

    private List<Component> lines(String key, TagResolver... placeholders) {
        return service.lines(viewer, ProgressionModule.ID, key, placeholders);
    }
}
