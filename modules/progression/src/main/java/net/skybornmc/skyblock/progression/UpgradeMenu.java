package net.skybornmc.skyblock.progression;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.skybornmc.skyblock.common.island.IslandService;
import net.skybornmc.skyblock.common.menu.Menu;
import net.skybornmc.skyblock.common.menu.MenuButton;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.util.Format;
import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.UUID;

/**
 * {@code /is upgrade} (PHASE-5 M5.4 mockup): tabs on top, the upgrades of the tab, the island bank and the island level.
 * One plain click each (Bedrock-safe, no right or shift clicks). Refreshes while open (task progress, bank balance).
 * <pre>
 *  R O ⚒ $ ⛏ ▼ B O R      2-5 tabs: Island, Economy, Depths, Production (Masteries, M5.5, stays hidden until built)
 *  B u u u u u u u B      10-16 upgrades of the tab
 *  B u u u u u u u B      19-25
 *  B . . . . . . . B
 *  B . $ . ✦ . 📖 . B      38 island bank, 40 island level, 42 challenge book
 *  ← O B ? ✘ H B O R      45 back, 48 info, 49 close, 50 help
 * </pre>
 */
final class UpgradeMenu extends Menu {

    static final int[] TAB_SLOTS = {2, 3, 4, 5};
    static final int[] UPGRADE_SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25};
    static final int SLOT_BANK = 38;
    static final int SLOT_LEVEL = 40;
    static final int SLOT_BOOK = 42;

    private final UpgradeMenus menus;
    private final UUID islandId;
    private UpgradeTab tab;
    private long bank;
    private boolean fetching;
    /** PERF6: built items per slot, reused while their inputs are unchanged (refreshes rebuild only what changed). */
    private final ItemMemo<ItemStack> memo = new ItemMemo<>();

    UpgradeMenu(UpgradeMenus menus, UUID islandId, UpgradeTab tab) {
        this.menus = menus;
        this.islandId = islandId;
        this.tab = tab;
        // PERF6: open with the balance read last (refreshed right away); the arriving value then usually changes nothing.
        this.bank = menus.upgrades().knownBank(islandId);
    }

    @Override
    public int rows() {
        return 6;
    }

    @Override
    public Component title() {
        return service.title(viewer, text("upgrades.menu.crumb-island"), text("upgrades.menu.crumb"));
    }

    @Override
    public String messageModule() {
        return ProgressionModule.ID;
    }

    @Override
    public String infoKey() {
        return "upgrades.menu.info";
    }

    @Override
    public String helpTopic() {
        return "island-upgrades";
    }

    @Override
    public int tickInterval() {
        return menus.upgrades().config().refreshTicks;
    }

    @Override
    protected void tick() {
        IslandService islands = menus.upgrades().islands().orElse(null);
        if (islands == null || islands.island(islandId).isEmpty() || !menus.upgrades().available()) {
            viewer.closeInventory();
            return;
        }
        fetchBank();
        refresh();
    }

    /** Reads the island bank (async); refreshes the menu when the value changed. */
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
        if (bank < 0 && !fetching) {
            fetchBank();
        }
        UpgradeTab[] tabs = UpgradeTab.values();
        for (int i = 0; i < tabs.length && i < TAB_SLOTS.length; i++) {
            set(TAB_SLOTS[i], tabButton(tabs[i]));
        }
        List<UpgradeCatalog.Def> defs = menus.upgrades().catalog().tab(tab);
        for (int i = 0; i < defs.size() && i < UPGRADE_SLOTS.length; i++) {
            UpgradeCatalog.Def def = defs.get(i);
            ItemStack item = memo.get(UPGRADE_SLOTS[i], menus.upgradeSignature(viewer, islandId, def, bank, def.buyable()),
                    () -> service.mark(menus.upgradeItem(service, viewer, islandId, def, bank, def.buyable())));
            if (!def.buyable()) {
                set(UPGRADE_SLOTS[i], MenuButton.display(item));
            } else {
                set(UPGRADE_SLOTS[i], MenuButton.of(item, click -> openChild(new UpgradeDetailMenu(menus, islandId, def.id(), bank))));
            }
        }
        set(SLOT_BANK, MenuButton.display(bankItem()));
        set(SLOT_LEVEL, levelButton());
        set(SLOT_BOOK, bookButton());
    }

    private MenuButton tabButton(UpgradeTab t) {
        boolean selected = t == tab;
        ItemStack icon = new ItemStack(tabIcon(t));
        icon.editMeta(meta -> {
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            if (selected) {
                meta.setEnchantmentGlintOverride(true);
            }
        });
        int size = menus.upgrades().catalog().tab(t).size();
        ItemStack item = memo.get("tab:" + t.id(), UpgradeMenus.signature(menus.textsKey(), selected, size), () -> {
            TagResolver count = Msg.p("count", size);
            return service.mark(service.item(icon, text(selected ? "upgrades.tab.name-selected" : "upgrades.tab.name",
                    Msg.c("tab", text("upgrades.tab." + t.id() + ".crumb"))), lines(selected ? "upgrades.tab.lore-selected" : "upgrades.tab.lore", count)));
        });
        if (selected) {
            return MenuButton.display(item);
        }
        return MenuButton.of(item, click -> {
            tab = t;
            refresh();
        });
    }

    static Material tabIcon(UpgradeTab t) {
        return switch (t) {
            case ISLAND -> Material.ANVIL;
            case ECONOMY -> Material.GOLD_INGOT;
            case DEPTHS -> Material.IRON_PICKAXE;
            case PRODUCTION -> Material.HOPPER;
        };
    }

    private ItemStack bankItem() {
        long shown = bank;
        return memo.get("bank", UpgradeMenus.signature(menus.textsKey(), shown), () -> service.mark(buildBankItem()));
    }

    private ItemStack buildBankItem() {
        String key = bank < 0 ? "upgrades.menu.bank.lore-loading" : "upgrades.menu.bank.lore";
        return service.item(new ItemStack(Material.ENCHANTED_BOOK), text("upgrades.menu.bank.name", Msg.p("bank", bank < 0 ? "..." : Format.money(bank))),
                lines(key, Msg.p("bank", bank < 0 ? "..." : Format.money(bank))));
    }

    private MenuButton levelButton() {
        LevelManager levels = menus.upgrades().levels();
        int level = levels.level(islandId);
        int percent = PillarManager.percent(levels.progress(islandId));
        String points = LevelMenu.whole(menus.upgrades().points(islandId));
        ItemStack item = memo.get("level", UpgradeMenus.signature(menus.textsKey(), level, percent, points), () -> {
            TagResolver[] p = {Msg.p("level", level), Msg.p("next", level + 1), Msg.p("percent", percent), Msg.p("points", points)};
            return service.mark(service.item(new ItemStack(Material.PAPER), text("upgrades.menu.level.name", p), lines("upgrades.menu.level.lore", p)));
        });
        return MenuButton.of(item, click -> menus.upgrades().core().services()
                .optional(net.skybornmc.skyblock.common.progression.ProgressionService.class)
                .ifPresent(s -> s.openLevelMenu(viewer, this)));
    }

    private MenuButton bookButton() {
        TaskMenus book = menus.taskMenus();
        if (book == null || !book.tasks().config().enabled || !viewer.hasPermission(TaskManager.PERM_TASKS)) {
            return MenuButton.display(service.item(new ItemStack(Material.BOOK), text("level-menu.tasks.name"), lines("level-menu.tasks.lore-locked")));
        }
        int done = book.tasks().completedCount(islandId);
        int total = book.tasks().catalog().size();
        ItemStack item = memo.get("book", UpgradeMenus.signature(menus.textsKey(), done, total), () -> {
            TagResolver[] p = {Msg.number("done", done), Msg.number("total", total)};
            return service.mark(service.item(new ItemStack(Material.WRITABLE_BOOK), text("level-menu.tasks.name"), lines("level-menu.tasks.lore", p)));
        });
        return MenuButton.of(item, click -> book.openBook(viewer, this));
    }

    private Component text(String key, TagResolver... placeholders) {
        return service.text(viewer, ProgressionModule.ID, key, placeholders);
    }

    private List<Component> lines(String key, TagResolver... placeholders) {
        return service.lines(viewer, ProgressionModule.ID, key, placeholders);
    }
}
