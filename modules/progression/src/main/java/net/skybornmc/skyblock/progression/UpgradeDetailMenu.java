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
 * One upgrade (PHASE-5 M5.4 "Upgrade detail"): the upgrade, the tasks of its next level (deliver tasks open their hand-in
 * screen), every level with its effect and price, and the Buy button (gray with the reason when it can't be bought).
 * <pre>
 *  R O B B ◆ B B O R      4 the upgrade
 *  B t t t t t t t B      10-16 tasks of the next level
 *  B l l l l l l l B      19-25 levels 1-7
 *  B l l l l l l l B      28-34 levels 8-14
 *  B . . . $ . . . B      40 buy
 *  ← O B ? ✘ H B O R
 * </pre>
 */
final class UpgradeDetailMenu extends Menu {

    static final int SLOT_HEADER = 4;
    static final int[] TASK_SLOTS = {10, 11, 12, 13, 14, 15, 16};
    static final int[] LEVEL_SLOTS = {19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};
    static final int SLOT_BUY = 40;

    private final UpgradeMenus menus;
    private final UUID islandId;
    private final String upgradeId;
    private long bank;
    private boolean fetching;
    /** PERF6: built items per slot, reused while their inputs are unchanged. */
    private final ItemMemo<ItemStack> memo = new ItemMemo<>();

    UpgradeDetailMenu(UpgradeMenus menus, UUID islandId, String upgradeId, long bank) {
        this.menus = menus;
        this.islandId = islandId;
        this.upgradeId = upgradeId;
        this.bank = bank >= 0 ? bank : menus.upgrades().knownBank(islandId);
    }

    @Override
    public int rows() {
        return 6;
    }

    @Override
    public Component title() {
        UpgradeCatalog.Def def = def();
        return service.title(viewer, text("upgrades.menu.crumb"), def == null ? Component.text(upgradeId) : menus.upgrades().upgradeName(viewer, def));
    }

    @Override
    public String messageModule() {
        return ProgressionModule.ID;
    }

    @Override
    public String infoKey() {
        return "upgrades.detail.info";
    }

    @Override
    public String helpTopic() {
        return "island-upgrades";
    }

    @Override
    public int tickInterval() {
        return menus.upgrades().config().refreshTicks;
    }

    private UpgradeCatalog.Def def() {
        return menus.upgrades().catalog().get(upgradeId).orElse(null);
    }

    @Override
    protected void tick() {
        IslandService islands = menus.upgrades().islands().orElse(null);
        if (islands == null || islands.island(islandId).isEmpty() || def() == null || !menus.upgrades().available()) {
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
        menus.upgrades().bankBalance(islandId).whenComplete((value, error) -> menus.upgrades().core().scheduler()
                .main("progression-upgrade-bank", () -> {
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
        UpgradeCatalog.Def def = def();
        if (def == null) {
            return;
        }
        if (bank < 0 && !fetching) {
            fetchBank();
        }
        UpgradeManager upgrades = menus.upgrades();
        int current = upgrades.level(islandId, def.id());
        set(SLOT_HEADER, MenuButton.display(memo.get(SLOT_HEADER, menus.upgradeSignature(viewer, islandId, def, bank, false),
                () -> service.mark(menus.upgradeItem(service, viewer, islandId, def, bank, false)))));
        List<TaskCatalog.TaskDef> taskDefs = current >= def.maxLevel() ? List.of() : upgrades.tasks().catalog().byUnlock(def.unlockKey(current + 1));
        for (int i = 0; i < taskDefs.size() && i < TASK_SLOTS.length; i++) {
            set(TASK_SLOTS[i], task(TASK_SLOTS[i], taskDefs.get(i)));
        }
        for (int n = 1; n <= def.maxLevel() && n <= LEVEL_SLOTS.length; n++) {
            int level = n;
            String state = n <= current ? "bought" : n == current + 1 ? "next" : "later";
            set(LEVEL_SLOTS[n - 1], MenuButton.display(memo.get(LEVEL_SLOTS[n - 1], UpgradeMenus.signature(menus.textsKey(), def, n, state,
                    upgrades.effectInputs(def, n, islandId)), () -> service.mark(levelItem(def, level, current)))));
        }
        set(SLOT_BUY, buyButton(def, current));
    }

    private MenuButton task(int slot, TaskCatalog.TaskDef taskDef) {
        TaskMenus book = menus.taskMenus();
        boolean done = menus.upgrades().tasks().completed(islandId, taskDef.id());
        boolean deliver = taskDef.info().type() == TaskType.DELIVER && !done;
        ItemStack item = memo.get(slot, UpgradeMenus.signature(menus.textsKey(), taskDef, done, menus.upgrades().tasks().progressOf(islandId, taskDef)),
                () -> service.mark(book.taskItem(service, viewer, islandId, taskDef, true)));
        if (!deliver) {
            return MenuButton.display(item);
        }
        return MenuButton.of(item, click -> book.openTask(viewer, taskDef.id(), this));
    }

    private ItemStack levelItem(UpgradeCatalog.Def def, int n, int current) {
        UpgradeCatalog.Level level = def.level(n);
        TagResolver[] p = {Msg.p("level", UpgradeRules.roman(n)), Msg.c("effect", menus.upgrades().effectText(viewer, def, n, islandId)),
                Msg.p("cost", Format.money(level.cost())), Msg.p("tier", level.tier()), Msg.p("island-level", level.minIslandLevel())};
        String state = n <= current ? "bought" : n == current + 1 ? "next" : "later";
        Material icon = switch (state) {
            case "bought" -> Material.LIME_STAINED_GLASS_PANE;
            case "next" -> Material.YELLOW_STAINED_GLASS_PANE;
            default -> Material.GRAY_STAINED_GLASS_PANE;
        };
        List<Component> lore = new ArrayList<>(lines("upgrades.detail.level.lore", p));
        if (level.minIslandLevel() > 0) {
            lore.addAll(lines("upgrades.detail.level.island-level", p));
        }
        lore.addAll(lines("upgrades.detail.level." + state, p));
        return service.item(new ItemStack(icon), text("upgrades.detail.level.name-" + state, p), lore);
    }

    private MenuButton buyButton(UpgradeCatalog.Def def, int current) {
        UpgradeManager upgrades = menus.upgrades();
        UpgradeRules.Status status = upgrades.status(viewer, islandId, def, bank);
        UpgradeCatalog.Level next = def.level(current + 1);
        TagResolver[] p = {Msg.p("cost", Format.money(next == null ? 0 : next.cost())), Msg.p("island-level", next == null ? 0 : next.minIslandLevel()),
                Msg.p("bank", bank < 0 ? "..." : Format.money(bank))};
        Object signature = UpgradeMenus.signature(menus.textsKey(), def, current, status, bank);
        if (status != UpgradeRules.Status.READY) {
            String reason = UpgradeMenus.stateKey(status);
            return MenuButton.display(memo.get(SLOT_BUY, signature, () -> service.mark(service.item(new ItemStack(Material.GRAY_DYE),
                    text("upgrades.detail.buy.name-locked", p), lines("upgrades.state." + reason, p)))));
        }
        ItemStack item = memo.get(SLOT_BUY, signature, () -> service.mark(service.item(new ItemStack(Material.LIME_CONCRETE),
                text("upgrades.detail.buy.name", p), lines("upgrades.detail.buy.lore", p))));
        return MenuButton.of(item, click -> {
            Menu back = parent();
            openChild(menus.confirm(service, viewer, islandId, def, bank, () -> upgrades.core().menus()
                    .open(viewer, new UpgradeDetailMenu(menus, islandId, upgradeId, -1).parent(back))));
        });
    }

    private Component text(String key, TagResolver... placeholders) {
        return service.text(viewer, ProgressionModule.ID, key, placeholders);
    }

    private List<Component> lines(String key, TagResolver... placeholders) {
        return service.lines(viewer, ProgressionModule.ID, key, placeholders);
    }
}
