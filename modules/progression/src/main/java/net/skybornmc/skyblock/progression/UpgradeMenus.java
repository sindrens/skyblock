package net.skybornmc.skyblock.progression;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.skybornmc.skyblock.common.island.IslandService;
import net.skybornmc.skyblock.common.menu.ConfirmMenu;
import net.skybornmc.skyblock.common.menu.Menu;
import net.skybornmc.skyblock.common.menu.MenuService;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.util.Format;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Opens {@code /is upgrade} (PHASE-5 M5.4) and builds the pieces its menus share: the upgrade item (state colors and
 * lore of the mockup) and the buy confirmation (cost, bank after, what you get; two big buttons for Bedrock).
 */
final class UpgradeMenus {

    private final UpgradeManager upgrades;
    private final TaskMenus taskMenus;
    private MasteryMenus masteryMenus;

    UpgradeMenus(UpgradeManager upgrades, TaskMenus taskMenus) {
        this.upgrades = upgrades;
        this.taskMenus = taskMenus;
    }

    UpgradeManager upgrades() {
        return upgrades;
    }

    TaskMenus taskMenus() {
        return taskMenus;
    }

    /** M5.5: the Masteries tab, set once masteries exist (null before). */
    MasteryMenus masteryMenus() {
        return masteryMenus;
    }

    void masteryMenus(MasteryMenus masteryMenus) {
        this.masteryMenus = masteryMenus;
    }

    Optional<IslandService.IslandInfo> islandOf(Player player) {
        return upgrades.islands().flatMap(s -> s.islandOf(player.getUniqueId()));
    }

    /** {@code /is upgrade}, the island menu's Upgrades button and the Upgrades source in {@code /is level}. */
    void open(Player player, Menu parent) {
        open(player, parent, UpgradeTab.ISLAND);
    }

    /** {@code /is masteries} (M5.5): the same menu on the Masteries tab (DESIGN §3.5). */
    void openMasteries(Player player, Menu parent) {
        if (masteryMenus == null || !masteryMenus.masteries().available()) {
            upgrades.msg().send(player, "masteries.unavailable");
            return;
        }
        open(player, parent, UpgradeTab.MASTERIES);
    }

    void open(Player player, Menu parent, UpgradeTab tab) {
        if (!player.hasPermission(UpgradeManager.PERM_UPGRADE)) {
            upgrades.msg().send(player, "upgrades.no-permission");
            return;
        }
        if (!upgrades.available()) {
            upgrades.msg().send(player, "upgrades.unavailable");
            return;
        }
        Optional<IslandService.IslandInfo> island = islandOf(player);
        if (island.isEmpty()) {
            upgrades.msg().send(player, "upgrades.no-island");
            return;
        }
        upgrades.core().menus().open(player, new UpgradeMenu(this, island.get().id(), tab).parent(parent));
    }

    // ------------------------------------------------------------------ item signatures (PERF6)

    private long phGeneration = Long.MIN_VALUE;
    private boolean phTexts;

    /**
     * The part of every item signature that covers texts: the messages/style generation. {@code null} (never cache)
     * while a progression text uses a player placeholder, whose value can change without any input we know.
     */
    Object textsKey() {
        long generation = upgrades.core().messages().generation();
        if (generation != phGeneration) {
            phTexts = upgrades.core().messages().usesPlayerPlaceholders(ProgressionModule.ID);
            phGeneration = generation;
        }
        return phTexts ? null : generation;
    }

    /** Signature from parts; null when any part is null (not cacheable). */
    static List<Object> signature(Object... parts) {
        for (Object part : parts) {
            if (part == null) {
                return null;
            }
        }
        return java.util.Arrays.asList(parts);
    }

    /**
     * Every input of {@link #upgradeItem}: the definition, bought level, state (which already covers the bank, island
     * level, tasks and the viewer's right to buy), both effect texts' inputs and each next-level task's progress.
     */
    List<Object> upgradeSignature(Player viewer, UUID islandId, UpgradeCatalog.Def def, long bank, boolean clickHint) {
        Object texts = textsKey();
        if (texts == null) {
            return null;
        }
        int current = upgrades.level(islandId, def.id());
        List<Object> tasks = new ArrayList<>();
        if (def.level(current + 1) != null) {
            for (TaskCatalog.TaskDef task : upgrades.tasks().catalog().byUnlock(def.unlockKey(current + 1))) {
                tasks.add(task);
                tasks.add(upgrades.tasks().completed(islandId, task.id()));
                tasks.add(upgrades.tasks().progressOf(islandId, task));
            }
        }
        return signature(texts, def, current, upgrades.status(viewer, islandId, def, bank), clickHint,
                upgrades.effectInputs(def, current, islandId), upgrades.effectInputs(def, current + 1, islandId), tasks);
    }

    // ------------------------------------------------------------------ shared items

    /**
     * The upgrade item of the mockup: name in the state color with the next level, level, now/next effect, the next
     * level's tasks, the cost and the state line. {@code clickHint} adds "» CLICK to see the tasks".
     */
    ItemStack upgradeItem(MenuService service, Player viewer, UUID islandId, UpgradeCatalog.Def def, long bank, boolean clickHint) {
        int current = upgrades.level(islandId, def.id());
        UpgradeRules.Status status = upgrades.status(viewer, islandId, def, bank);
        UpgradeCatalog.Level next = def.level(current + 1);
        Component name = upgrades.upgradeName(viewer, def);
        TagResolver[] p = {Msg.c("upgrade", name), Msg.p("level", UpgradeRules.roman(current + 1)), Msg.p("current", current),
                Msg.p("max", def.maxLevel()), Msg.c("now", upgrades.effectText(viewer, def, current, islandId)),
                Msg.c("next", next == null ? Component.empty() : upgrades.effectText(viewer, def, current + 1, islandId)),
                Msg.p("cost", Format.money(next == null ? 0 : next.cost())), Msg.p("tier", next == null ? 0 : next.tier()),
                Msg.p("island-level", next == null ? 0 : next.minIslandLevel())};
        String nameKey = switch (status) {
            case READY -> "upgrades.item.name-ready";
            case TASKS_OPEN, ISLAND_LEVEL -> "upgrades.item.name-open";
            case NO_PERMISSION, NO_MONEY -> "upgrades.item.name-blocked";
            case MAXED -> "upgrades.item.name-maxed";
            case DISABLED, COMING_SOON -> "upgrades.item.name-soon";
        };
        List<Component> lore = new ArrayList<>();
        if (status == UpgradeRules.Status.COMING_SOON || status == UpgradeRules.Status.DISABLED) {
            lore.addAll(lines(service, viewer, "upgrades.item.soon", p));
            return item(service, def, status, text(service, viewer, nameKey, p), lore);
        }
        lore.addAll(lines(service, viewer, "upgrades.item.level", p));
        lore.addAll(lines(service, viewer, "upgrades.item.now", p));
        if (next != null) {
            lore.addAll(lines(service, viewer, "upgrades.item.next", p));
            lore.addAll(lines(service, viewer, "upgrades.item.tasks-header", p));
            List<Component> taskLines = upgrades.tasks().taskLines(viewer, islandId, def.unlockKey(current + 1));
            lore.addAll(taskLines.isEmpty() ? lines(service, viewer, "upgrades.item.no-tasks", p) : taskLines);
            if (next.minIslandLevel() > 0) {
                lore.addAll(lines(service, viewer, "upgrades.item.island-level", p));
            }
            lore.addAll(lines(service, viewer, "upgrades.item.cost", p));
        }
        lore.add(Component.empty());
        lore.addAll(lines(service, viewer, "upgrades.state." + stateKey(status), p));
        if (clickHint) {
            lore.addAll(lines(service, viewer, "upgrades.item.click", p));
        }
        return item(service, def, status, text(service, viewer, nameKey, p), lore);
    }

    private ItemStack item(MenuService service, UpgradeCatalog.Def def, UpgradeRules.Status status, Component name, List<Component> lore) {
        Material m = Material.matchMaterial(def.icon());
        ItemStack icon = new ItemStack(m == null || !m.isItem() || m.isAir() ? Material.PAPER : m);
        icon.editMeta(meta -> {
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            if (status == UpgradeRules.Status.READY) {
                meta.setEnchantmentGlintOverride(true);
            }
        });
        return service.item(icon, name, lore);
    }

    static String stateKey(UpgradeRules.Status status) {
        return status.name().toLowerCase(java.util.Locale.ROOT).replace('_', '-');
    }

    /**
     * The buy confirmation ("Buy Island Size II for $22,907 from the island bank? Bank after: $1.18M"). On confirm the
     * purchase runs with every check again; afterwards {@code reopen} shows the menu the player came from.
     */
    ConfirmMenu confirm(MenuService service, Player viewer, UUID islandId, UpgradeCatalog.Def def, long bank, Runnable reopen) {
        int level = upgrades.level(islandId, def.id()) + 1;
        UpgradeCatalog.Level next = def.level(level);
        long cost = next == null ? 0 : next.cost();
        ItemStack shown = upgradeItem(service, viewer, islandId, def, bank, false);
        return new ConfirmMenu(v -> service.text(v, ProgressionModule.ID, "upgrades.confirm.crumb"), shown,
                v -> {
                    TagResolver[] p = {Msg.c("upgrade", upgrades.upgradeName(v, def)), Msg.p("level", UpgradeRules.roman(level)),
                            Msg.p("cost", Format.money(cost)), Msg.c("next", upgrades.effectText(v, def, level, islandId)),
                            Msg.p("bank", bank < 0 ? "?" : Format.money(Math.max(0, bank - cost)))};
                    return service.lines(v, ProgressionModule.ID, "upgrades.confirm.details", p);
                },
                player -> upgrades.buy(player, def.id(), level, outcome -> {
                    if (player.isOnline()) {
                        reopen.run();
                    }
                }),
                player -> {
                });
    }

    static Component text(MenuService service, Player viewer, String key, TagResolver... p) {
        return service.text(viewer, ProgressionModule.ID, key, p);
    }

    static List<Component> lines(MenuService service, Player viewer, String key, TagResolver... p) {
        return service.lines(viewer, ProgressionModule.ID, key, p);
    }
}
