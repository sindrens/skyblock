package net.skybornmc.skyblock.progression;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.skybornmc.skyblock.common.menu.ConfirmMenu;
import net.skybornmc.skyblock.common.menu.MenuService;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.util.Format;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The pieces the mastery screens share (PHASE-5 M5.5): the mastery item of the Masteries tab (state color, rank, now/next,
 * what the next rank needs, cost and state line, like the upgrade item), the Ascension item and the buy confirmation
 * (GUI-1: the details on the middle item; two big buttons for Bedrock).
 */
final class MasteryMenus {

    private final MasteryManager masteries;
    private final UpgradeMenus upgradeMenus;

    MasteryMenus(MasteryManager masteries, UpgradeMenus upgradeMenus) {
        this.masteries = masteries;
        this.upgradeMenus = upgradeMenus;
    }

    MasteryManager masteries() {
        return masteries;
    }

    UpgradeMenus upgradeMenus() {
        return upgradeMenus;
    }

    // ------------------------------------------------------------------ signatures (PERF6)

    /** Every input of {@link #masteryItem}: definition, rank, state, effect inputs, requirement values and task progress. */
    List<Object> masterySignature(Player viewer, UUID islandId, MasteryCatalog.Def def, long bank, boolean clickHint) {
        Object texts = upgradeMenus.textsKey();
        if (texts == null) {
            return null;
        }
        int current = masteries.rank(islandId, def.id());
        List<Object> tasks = new ArrayList<>();
        if (def.rank(current + 1) != null) {
            for (TaskCatalog.TaskDef task : masteries.tasks().catalog().byUnlock(def.unlockKey(current + 1))) {
                tasks.add(task);
                tasks.add(masteries.tasks().completed(islandId, task.id()));
                tasks.add(masteries.tasks().progressOf(islandId, task));
            }
        }
        Object fee = def.effect() == MasteryEffect.TREASURY ? masteries.feeText(def.valueAt(current)) + "/" + masteries.feeText(def.valueAt(current + 1)) : 0;
        return UpgradeMenus.signature(texts, def, current, masteries.status(viewer, islandId, def, bank), clickHint, fee, tasks);
    }

    List<Object> ascensionSignature(Player viewer, UUID islandId, long bank, boolean clickHint) {
        Object texts = upgradeMenus.textsKey();
        if (texts == null) {
            return null;
        }
        return UpgradeMenus.signature(texts, "ascension", masteries.ascension(islandId), masteries.ascensionCost(islandId),
                masteries.ascensionStatus(viewer, islandId, bank), clickHint, masteries.config().ascensionLevelPoints);
    }

    // ------------------------------------------------------------------ items

    /** The mastery item: name in the state color with the next rank, rank, now/next, requirements, tasks, cost, state. */
    ItemStack masteryItem(MenuService service, Player viewer, UUID islandId, MasteryCatalog.Def def, long bank, boolean clickHint) {
        int current = masteries.rank(islandId, def.id());
        MasteryRules.Status status = masteries.status(viewer, islandId, def, bank);
        MasteryCatalog.Rank next = def.rank(current + 1);
        TagResolver[] p = {Msg.c("upgrade", masteries.masteryName(viewer, def)), Msg.p("level", UpgradeRules.roman(current + 1)),
                Msg.p("current", current), Msg.p("max", def.maxRank()), Msg.c("now", masteries.effectText(viewer, def, current)),
                Msg.c("next", next == null ? Component.empty() : masteries.effectText(viewer, def, current + 1)),
                Msg.p("cost", Format.money(next == null ? 0 : next.cost())), Msg.p("island-level", next == null ? 0 : next.islandLevel()),
                Msg.c("pillar", masteries.pillarName(viewer, def)), Msg.p("pillar-level", next == null ? 0 : next.pillarLevel()),
                Msg.c("requires", masteries.requiredUpgrades(viewer, def))};
        List<Component> lore = new ArrayList<>();
        lore.addAll(lines(service, viewer, "upgrades.item.level", p));
        lore.addAll(lines(service, viewer, "upgrades.item.now", p));
        if (next != null) {
            lore.addAll(lines(service, viewer, "upgrades.item.next", p));
            if (!def.requiresMaxed().isEmpty()) {
                lore.addAll(lines(service, viewer, "masteries.item.requires", p));
            }
            if (def.pillar() != null && next.pillarLevel() > 0) {
                lore.addAll(lines(service, viewer, "masteries.item.pillar-level", p));
            }
            if (next.islandLevel() > 0) {
                lore.addAll(lines(service, viewer, "upgrades.item.island-level", p));
            }
            List<Component> taskLines = masteries.tasks().taskLines(viewer, islandId, def.unlockKey(current + 1));
            if (!taskLines.isEmpty()) {
                lore.addAll(lines(service, viewer, "upgrades.item.tasks-header", p));
                lore.addAll(taskLines);
            }
            lore.addAll(lines(service, viewer, "upgrades.item.cost", p));
        }
        lore.add(Component.empty());
        lore.addAll(lines(service, viewer, stateKey(status), p));
        if (clickHint) {
            lore.addAll(lines(service, viewer, "masteries.item.click", p));
        }
        return item(service, def.icon(), status, text(service, viewer, nameKey(status), p), lore);
    }

    /** The Ascension item: levels bought, the next level's price and what it gives, the state. */
    ItemStack ascensionItem(MenuService service, Player viewer, UUID islandId, long bank, boolean clickHint) {
        int current = masteries.ascension(islandId);
        long cost = masteries.ascensionCost(islandId);
        MasteryRules.Status status = masteries.ascensionStatus(viewer, islandId, bank);
        TagResolver[] p = {Msg.c("upgrade", masteries.ascensionName(viewer)), Msg.p("level", current + 1), Msg.p("current", current),
                Msg.p("cost", Format.money(Math.max(0, cost))), Msg.p("points", UpgradeCatalog.trim(masteries.config().ascensionLevelPoints)),
                Msg.p("island-level", 0)};
        List<Component> lore = new ArrayList<>(lines(service, viewer, "masteries.ascension.lore", p));
        if (status != MasteryRules.Status.MAXED && status != MasteryRules.Status.DISABLED) {
            lore.addAll(lines(service, viewer, "upgrades.item.cost", p));
        }
        lore.add(Component.empty());
        lore.addAll(lines(service, viewer, status == MasteryRules.Status.NEEDS_MAXED ? "masteries.state.needs-all" : stateKey(status), p));
        if (clickHint && status != MasteryRules.Status.NEEDS_MAXED) {
            lore.addAll(lines(service, viewer, "masteries.item.click", p));
        }
        String key = status == MasteryRules.Status.MAXED ? "masteries.ascension.name-maxed" : nameKey(status);
        return item(service, masteries.config().ascensionIcon, status, text(service, viewer, key, p), lore);
    }

    static String nameKey(MasteryRules.Status status) {
        return switch (status) {
            case READY -> "upgrades.item.name-ready";
            case TASKS_OPEN, ISLAND_LEVEL, PILLAR_LEVEL, NEEDS_MAXED -> "upgrades.item.name-open";
            case NO_PERMISSION, NO_MONEY -> "upgrades.item.name-blocked";
            case MAXED -> "upgrades.item.name-maxed";
            case DISABLED, COMING_SOON -> "upgrades.item.name-soon";
        };
    }

    /** The state lines of a status: the upgrades' lines, or the mastery-only ones (pillar level, needs an upgrade maxed). */
    static String stateKey(MasteryRules.Status status) {
        return switch (status) {
            case PILLAR_LEVEL -> "masteries.state.pillar-level";
            case NEEDS_MAXED -> "masteries.state.needs-maxed";
            default -> "upgrades.state." + status.name().toLowerCase(java.util.Locale.ROOT).replace('_', '-');
        };
    }

    private ItemStack item(MenuService service, String iconId, MasteryRules.Status status, Component name, List<Component> lore) {
        Material m = Material.matchMaterial(iconId == null ? "" : iconId);
        ItemStack icon = new ItemStack(m == null || !m.isItem() || m.isAir() ? Material.NETHER_STAR : m);
        icon.editMeta(meta -> {
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            if (status == MasteryRules.Status.READY) {
                meta.setEnchantmentGlintOverride(true);
            }
        });
        return service.item(icon, name, lore);
    }

    // ------------------------------------------------------------------ confirm (GUI-1: details on the middle item)

    /**
     * The buy confirmation ("Buy Warrior II for $35,900 from the island bank? Bank after ..."). {@code def} null = the
     * next Ascension level. On confirm the purchase runs with every check again; afterwards {@code reopen} runs.
     */
    ConfirmMenu confirm(MenuService service, Player viewer, UUID islandId, MasteryCatalog.Def def, long bank, Runnable reopen) {
        boolean ascension = def == null;
        String id = ascension ? MasteryRules.ASCENSION_ID : def.id();
        int rank = masteries.rank(islandId, id) + 1;
        long cost = ascension ? Math.max(0, masteries.ascensionCost(islandId)) : def.rank(rank) == null ? 0 : def.rank(rank).cost();
        ItemStack shown = ascension ? ascensionItem(service, viewer, islandId, bank, false) : masteryItem(service, viewer, islandId, def, bank, false);
        return new ConfirmMenu(v -> service.text(v, ProgressionModule.ID, "masteries.confirm.crumb"), shown,
                v -> {
                    Component next = ascension
                            ? service.text(v, ProgressionModule.ID, "masteries.ascension.effect", Msg.p("points", UpgradeCatalog.trim(masteries.config().ascensionLevelPoints)))
                            : masteries.effectText(v, def, rank);
                    TagResolver[] p = {Msg.c("upgrade", ascension ? masteries.ascensionName(v) : masteries.masteryName(v, def)),
                            Msg.p("level", ascension ? String.valueOf(rank) : UpgradeRules.roman(rank)), Msg.p("cost", Format.money(cost)),
                            Msg.c("next", next), Msg.p("bank", bank < 0 ? "?" : Format.money(Math.max(0, bank - cost)))};
                    return service.lines(v, ProgressionModule.ID, "upgrades.confirm.details", p);
                },
                player -> masteries.buy(player, id, rank, outcome -> {
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
