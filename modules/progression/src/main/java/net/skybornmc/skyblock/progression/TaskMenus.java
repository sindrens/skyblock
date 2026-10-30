package net.skybornmc.skyblock.progression;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.skybornmc.skyblock.common.island.IslandService;
import net.skybornmc.skyblock.common.menu.Menu;
import net.skybornmc.skyblock.common.menu.MenuService;
import net.skybornmc.skyblock.common.progression.TaskInfo;
import net.skybornmc.skyblock.common.progression.TaskType;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.progression.TaskCatalog.TaskDef;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Opens the challenge book menus (PHASE-5 M5.3) and builds the task items they share. */
final class TaskMenus {

    private final TaskManager tasks;

    TaskMenus(TaskManager tasks) {
        this.tasks = tasks;
    }

    TaskManager tasks() {
        return tasks;
    }

    Optional<UUID> islandOf(Player player) {
        return tasks.core().services().optional(IslandService.class).flatMap(s -> s.islandOf(player.getUniqueId()))
                .map(IslandService.IslandInfo::id);
    }

    private boolean allowed(Player player) {
        if (!player.hasPermission(TaskManager.PERM_TASKS)) {
            tasks.msg().send(player, "tasks.no-permission");
            return false;
        }
        if (!tasks.config().enabled) {
            tasks.msg().send(player, "tasks.disabled");
            return false;
        }
        return true;
    }

    void openBook(Player player, Menu parent) {
        if (!allowed(player)) {
            return;
        }
        Optional<UUID> island = islandOf(player);
        if (island.isEmpty()) {
            tasks.msg().send(player, "tasks.no-island");
            return;
        }
        tasks.check(island.get());
        tasks.core().menus().open(player, new TaskBookMenu(this, island.get()).parent(parent));
    }

    void openTier(Player player, UUID islandId, int tier, Menu parent) {
        tasks.core().menus().open(player, new TaskTierMenu(this, islandId, tier).parent(parent));
    }

    void openTask(Player player, String taskId, Menu parent) {
        if (!allowed(player)) {
            return;
        }
        Optional<UUID> island = islandOf(player);
        if (island.isEmpty()) {
            tasks.msg().send(player, "tasks.no-island");
            return;
        }
        Optional<TaskDef> def = tasks.catalog().get(taskId);
        if (def.isEmpty()) {
            tasks.msg().send(player, "tasks.deliver.unknown");
            return;
        }
        if (def.get().info().type() == TaskType.DELIVER) {
            tasks.core().menus().open(player, new TaskDeliverMenu(this, island.get(), def.get().id()).parent(parent));
        } else {
            openTier(player, island.get(), def.get().info().tier(), parent);
        }
    }

    // ------------------------------------------------------------------ shared items

    /** The item of one task: state, progress, rewards and (deliver tasks) the click hint. */
    ItemStack taskItem(MenuService service, Player viewer, UUID islandId, TaskDef def, boolean clickHint) {
        TaskInfo info = def.info();
        TaskProgress state = tasks.state();
        boolean done = state.done(islandId, def.id());
        long value = tasks.progressOf(islandId, def);
        TagResolver[] p = {Msg.c("task", tasks.taskName(viewer, def)), Msg.number("progress", value),
                Msg.number("goal", info.amount()), Msg.p("percent", TaskRules.percent(value, info.amount())),
                Msg.p("tier", info.tier())};
        List<Component> lore = new ArrayList<>(lines(service, viewer, "tasks.item.type." + info.type().id(), p));
        lore.addAll(lines(service, viewer, done ? "tasks.item.done" : "tasks.item.progress", p));
        if (info.crystals() > 0 || (info.pillar() != null && info.pillarXp() > 0)) {
            lore.addAll(lines(service, viewer, "tasks.item.rewards"));
            if (info.crystals() > 0) {
                lore.addAll(lines(service, viewer, "tasks.item.reward-crystals", Msg.number("crystals", info.crystals())));
            }
            if (info.pillar() != null && info.pillarXp() > 0) {
                lore.addAll(lines(service, viewer, "tasks.item.reward-pillar", Msg.number("xp", Math.round(info.pillarXp())),
                        Msg.c("pillar", tasks.msg().component(viewer, "pillar." + info.pillar().id() + ".name"))));
            }
        }
        if (clickHint && !done && info.type() == TaskType.DELIVER) {
            lore.add(Component.empty());
            lore.addAll(lines(service, viewer, "tasks.item.click-deliver"));
        }
        ItemStack icon = new ItemStack(done ? Material.LIME_DYE : icon(def));
        icon.editMeta(meta -> meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES));
        return service.item(icon, service.text(viewer, ProgressionModule.ID, done ? "tasks.item.name-done" : "tasks.item.name", p), lore);
    }

    /** The task's icon: tasks.yml {@code icon}, else the target item, else a type default. */
    static Material icon(TaskDef def) {
        Material m = def.icon() == null ? null : Material.matchMaterial(def.icon());
        if (m != null && m.isItem()) {
            return m;
        }
        String target = def.info().target();
        TaskType type = def.info().type();
        if (!target.startsWith("custom:") && !TaskType.ANY.equals(target) && type != TaskType.KILL
                && type != TaskType.SKILL_LEVEL && type != TaskType.PILLAR_LEVEL) {
            Material t = Material.matchMaterial(target);
            if (t != null && t.isItem() && !t.isAir()) {
                return t;
            }
        }
        return switch (type) {
            case MINE -> Material.IRON_PICKAXE;
            case CHOP -> Material.IRON_AXE;
            case FARM -> Material.WHEAT;
            case KILL -> Material.IRON_SWORD;
            case CRAFT -> Material.CRAFTING_TABLE;
            case SELL -> Material.CHEST;
            case EARN -> Material.GOLD_INGOT;
            case DEPOSIT -> Material.GOLD_BLOCK;
            case DELIVER -> Material.HOPPER;
            case SKILL_LEVEL -> Material.EXPERIENCE_BOTTLE;
            case PILLAR_LEVEL -> Material.QUARTZ_PILLAR;
            case ISLAND_LEVEL -> Material.NETHER_STAR;
        };
    }

    static List<Component> lines(MenuService service, Player viewer, String key, TagResolver... p) {
        return service.lines(viewer, ProgressionModule.ID, key, p);
    }
}
