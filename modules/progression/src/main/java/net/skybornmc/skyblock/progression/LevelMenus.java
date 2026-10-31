package net.skybornmc.skyblock.progression;

import net.skybornmc.skyblock.common.Core;
import net.skybornmc.skyblock.common.island.IslandService;
import net.skybornmc.skyblock.common.menu.Menu;
import net.skybornmc.skyblock.common.text.ModuleMessages;
import net.skybornmc.skyblock.common.text.Msg;
import org.bukkit.entity.Player;

import java.util.Optional;
import java.util.UUID;

/** Opens {@code /is level} and {@code /is top} (PHASE-5 M5.2, M5.7) and handles the Recalculate button. */
final class LevelMenus {

    static final String PERM_LEVEL = "skyblock.progression.level";
    static final String PERM_RECALCULATE = "skyblock.progression.recalculate";
    static final String PERM_TOP = "skyblock.progression.top";

    private final Core core;
    private final ModuleMessages msg;
    private final LevelManager levels;
    private final PillarManager pillars;
    private TopBoards boards;
    private TaskMenus tasks;
    private UpgradeMenus upgrades;

    LevelMenus(Core core, ModuleMessages msg, LevelManager levels, PillarManager pillars) {
        this.core = core;
        this.msg = msg;
        this.levels = levels;
        this.pillars = pillars;
    }

    Core core() {
        return core;
    }

    ModuleMessages msg() {
        return msg;
    }

    LevelManager levels() {
        return levels;
    }

    PillarManager pillars() {
        return pillars;
    }

    /** The island boards (M5.7). */
    void boards(TopBoards boards) {
        this.boards = boards;
    }

    TopBoards boards() {
        return boards;
    }

    /** The challenge book (M5.3), opened from the book button. */
    void tasks(TaskMenus tasks) {
        this.tasks = tasks;
    }

    TaskMenus tasks() {
        return tasks;
    }

    /** The upgrades (M5.4), opened from the Upgrades source button. */
    void upgrades(UpgradeMenus upgrades) {
        this.upgrades = upgrades;
    }

    UpgradeMenus upgrades() {
        return upgrades;
    }

    Optional<IslandService.IslandInfo> islandOf(Player player) {
        return core.services().optional(IslandService.class).flatMap(s -> s.islandOf(player.getUniqueId()));
    }

    void openLevel(Player player, Menu parent) {
        if (!player.hasPermission(PERM_LEVEL)) {
            msg.send(player, "level-menu.no-permission");
            return;
        }
        Optional<IslandService.IslandInfo> island = islandOf(player);
        if (island.isEmpty()) {
            msg.send(player, "level-menu.no-island");
            return;
        }
        levels.request(island.get().id()); // fresh numbers a few seconds after opening
        core.menus().open(player, new LevelMenu(this, island.get().id()).parent(parent));
    }

    void openTop(Player player, Menu parent) {
        if (!player.hasPermission(PERM_TOP)) {
            msg.send(player, "top-menu.no-permission");
            return;
        }
        core.menus().open(player, new TopMenu(this, islandOf(player).map(IslandService.IslandInfo::id).orElse(null)).parent(parent));
    }

    /** The Recalculate button: tells the player what happened. */
    void recalculate(Player player, UUID islandId, Runnable refresh) {
        if (!player.hasPermission(PERM_RECALCULATE)) {
            msg.send(player, "level-menu.recalculate.no-permission");
            return;
        }
        LevelManager.Rescan result = levels.recalculate(player, worth -> {
            if (player.isOnline()) {
                msg.send(player, "level-menu.recalculate.done", Msg.p("worth", net.skybornmc.skyblock.common.util.Format.money(Math.round(worth))));
            }
        });
        switch (result) {
            case STARTED -> {
                msg.send(player, "level-menu.recalculate.started");
                core.sounds().play(player, levels.config().recalculateSound);
            }
            case RUNNING -> msg.send(player, "level-menu.recalculate.running");
            case COOLDOWN -> msg.send(player, "level-menu.recalculate.cooldown", Msg.p("minutes", levels.cooldownMinutesLeft(islandId)));
            case NOT_MEMBER -> msg.send(player, "level-menu.recalculate.not-member");
            case NO_ISLAND -> msg.send(player, "level-menu.no-island");
        }
        refresh.run();
    }
}
