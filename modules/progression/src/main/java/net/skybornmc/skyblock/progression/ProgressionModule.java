package net.skybornmc.skyblock.progression;

import net.skybornmc.skyblock.common.Core;
import net.skybornmc.skyblock.common.db.Migrations;
import net.skybornmc.skyblock.common.island.IslandService;
import net.skybornmc.skyblock.common.module.SkyblockModule;
import net.skybornmc.skyblock.common.progression.Pillar;
import net.skybornmc.skyblock.common.progression.ProgressionService;
import net.skybornmc.skyblock.common.progression.TaskService;
import net.skybornmc.skyblock.common.settings.PlayerSettings;
import net.skybornmc.skyblock.common.settings.SettingCategory;
import net.skybornmc.skyblock.common.settings.SettingToggle;
import net.skybornmc.skyblock.common.text.ModuleMessages;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;

import java.util.Optional;
import java.util.UUID;

/**
 * Island progression (Phase 5). M5.1: the five island pillars (Mining, Foraging, Farming, Combat; Conquest hidden until
 * it has a source), fed by the base skill XP of island members and island actions, with the 1.12 curve, the balance
 * bonus, synergy milestones (island crystals) and the ISLAND_XP family. Publishes {@link ProgressionService}, the
 * placeholders {@code pillar_<id>_level}, {@code pillar_<id>_progress}, {@code pillars_lowest}, {@code pillars_total} and
 * {@code island_xp_bonus}, and the {@code /settings} toggle {@code pillar-notices}. M5.2: the dynamic island level
 * ({@link LevelManager}, 8 sources, {@code progression.yml}), block worth ({@link BlockWorth}, {@code block-values.yml}),
 * {@code /is level} and {@code /is top} (sub-commands registered by the islands module, menus here), the placeholders
 * {@code island_level_progress}, {@code island_level_points} and the NPCHOLO island boards. M5.3: the task engine and
 * challenge book ({@link TaskManager}, {@code tasks.yml}, {@code tasks-settings.yml}), published as {@link TaskService}, the
 * placeholders {@code island_tasks_done}/{@code island_tasks_total} and the {@code /settings} toggle {@code task-notices}. M5.4:
 * island upgrades ({@link UpgradeManager}, {@code upgrades.yml}, {@code upgrades-settings.yml}), {@code /is upgrade} (sub-command
 * of the islands module, menus here), placeholders {@code island_upgrades_bought}, {@code island_upgrades_ready},
 * {@code island_upgrade_<id>}. M5.5: masteries and Ascension ({@link MasteryManager}, {@code masteries.yml},
 * {@code masteries-settings.yml}), {@code /is masteries} (the Masteries tab of {@code /is upgrade}), placeholders
 * {@code island_masteries_bought}, {@code island_ascension}, {@code island_mastery_<id>}. M5.7: leaderboards
 * ({@link TopBoards}: level, value, weekly island gain, weekly pillar gain, per pillar; solo/team), {@code /is top} tabs and
 * the Hall of Heroes board sources {@code island_gain} / {@code pillar_gain}. Texts in {@code messages/progression.yml}.
 */
public final class ProgressionModule implements SkyblockModule {

    static final String ID = "progression";

    private final Core core;
    private final PillarsConfig config = new PillarsConfig();
    private final LevelConfig levelConfig = new LevelConfig();
    private final BlockValuesConfig blockValues = new BlockValuesConfig();
    private final TasksConfig tasksConfig = new TasksConfig();
    private final UpgradesConfig upgradesConfig = new UpgradesConfig();
    private final MasteriesConfig masteriesConfig = new MasteriesConfig();
    private PillarManager pillars;
    private IslandSourcesListener sources;
    private BlockWorth blocks;
    private LevelManager levels;
    private TopBoards boards;
    private TaskManager tasks;
    private TaskSources taskSources;
    private DepthsTaskListener depthsTasks;
    private UpgradeManager upgrades;
    private MasteryManager masteries;

    public ProgressionModule(Core core) {
        this.core = core;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void enable() throws Exception {
        core.configs().load(config);
        core.configs().load(levelConfig);
        core.configs().load(blockValues);
        core.configs().load(tasksConfig);
        core.configs().load(upgradesConfig);
        core.configs().load(masteriesConfig);
        ModuleMessages msg = core.messages(ID);
        new Migrations(core.database(), core.plugin().getLogger()).migrate(ID, getClass().getClassLoader());
        pillars = new PillarManager(core, config, msg);
        Bukkit.getPluginManager().registerEvents(pillars, core.plugin());
        sources = new IslandSourcesListener(core, config, pillars);
        Bukkit.getPluginManager().registerEvents(sources, core.plugin());
        // M5.2 island level: block worth (live + rescans), the level engine, /is level and /is top menus.
        LevelStore levelStore = new LevelStore();
        LevelManager[] ref = new LevelManager[1];
        blocks = new BlockWorth(core, levelConfig, blockValues, levelStore, config.seasonId, island -> {
            if (ref[0] != null) {
                ref[0].request(island);
            }
        });
        levels = new LevelManager(core, levelConfig, msg, pillars, levelStore, blocks, config.seasonId);
        ref[0] = levels;
        Bukkit.getPluginManager().registerEvents(blocks, core.plugin());
        Bukkit.getPluginManager().registerEvents(levels, core.plugin());
        LevelMenus menus = new LevelMenus(core, msg, levels, pillars);
        // M5.7 leaderboards: ranked off the main thread every top.refresh-seconds, weekly boards from Monday 00:00 Oslo.
        boards = new TopBoards(core, levelConfig, levels, pillars, new WeeklyStore(), config.seasonId);
        Bukkit.getPluginManager().registerEvents(boards, core.plugin());
        menus.boards(boards);
        ProgressionFacade facade = new ProgressionFacade(pillars, levels, menus);
        core.services().register(ID, ProgressionService.class, facade);
        // M5.3 task engine: the challenge book (opened from /is level), shared island counters, deliveries.
        tasks = new TaskManager(core, tasksConfig, msg, pillars, levels, config.seasonId);
        TaskMenus taskMenus = new TaskMenus(tasks);
        tasks.menus(taskMenus);
        menus.tasks(taskMenus);
        Bukkit.getPluginManager().registerEvents(tasks, core.plugin());
        taskSources = new TaskSources(core, tasks);
        Bukkit.getPluginManager().registerEvents(taskSources, core.plugin());
        registerDepthsTasks();
        core.services().register(ID, TaskService.class, tasks);
        // M5.4 island upgrades: /is upgrade (sub-command of the islands module), paid from the island bank.
        upgrades = new UpgradeManager(core, upgradesConfig, msg, levels, tasks, config.seasonId);
        UpgradeMenus upgradeMenus = new UpgradeMenus(upgrades, taskMenus);
        upgrades.menus(upgradeMenus);
        menus.upgrades(upgradeMenus);
        facade.upgrades(upgrades, upgradeMenus);
        Bukkit.getPluginManager().registerEvents(upgrades, core.plugin());
        // M5.5 masteries + Ascension: /is masteries (sub-command of the islands module) = the Masteries tab of /is upgrade.
        masteries = new MasteryManager(core, masteriesConfig, upgradesConfig, msg, levels, pillars, tasks, upgrades, config.seasonId);
        upgradeMenus.masteryMenus(new MasteryMenus(masteries, upgradeMenus));
        facade.masteries(masteries);
        Bukkit.getPluginManager().registerEvents(masteries, core.plugin());
        registerPlaceholders();
        registerSetting();
        pillars.start();
        blocks.start();
        boards.start();
        levels.start();
        tasks.start();
        upgrades.start();
        masteries.start();
        upgrades.registerPlaceholders(ID, this::islandOf);
        masteries.registerPlaceholders(ID, this::islandOf);
        // Shop and sell prices may be published by modules enabled after this one: values again once everything is up.
        core.scheduler().later("progression-block-values", 1L, blocks::rebuildValues);
    }

    @Override
    public void reload() {
        core.configs().load(config);
        core.configs().load(levelConfig);
        core.configs().load(blockValues);
        core.configs().load(tasksConfig);
        core.configs().load(upgradesConfig);
        core.configs().load(masteriesConfig);
        core.messages().load(ID);
        pillars.applyConfig();
        sources.configure();
        blocks.applyConfig();
        levels.applyConfig();
        boards.applyConfig();
        tasks.applyConfig();
        taskSources.configure();
        upgrades.applyConfig();
        masteries.applyConfig();
    }

    @Override
    public void disable() {
        if (pillars == null) {
            return;
        }
        HandlerList.unregisterAll(pillars);
        if (masteries != null) {
            HandlerList.unregisterAll(masteries);
            masteries.shutdown();
        }
        if (upgrades != null) {
            HandlerList.unregisterAll(upgrades);
            upgrades.shutdown();
        }
        if (tasks != null) {
            HandlerList.unregisterAll(tasks);
            if (taskSources != null) {
                HandlerList.unregisterAll(taskSources);
            }
            if (depthsTasks != null) {
                HandlerList.unregisterAll(depthsTasks);
            }
            tasks.shutdown();
        }
        if (sources != null) {
            HandlerList.unregisterAll(sources);
        }
        if (boards != null) {
            HandlerList.unregisterAll(boards);
            boards.shutdown();
        }
        if (levels != null) {
            HandlerList.unregisterAll(levels);
            levels.shutdown();
        }
        if (blocks != null) {
            HandlerList.unregisterAll(blocks);
            blocks.shutdown();
        }
        core.services().optional(PlayerSettings.class).ifPresent(s -> s.unregisterAll(ID));
        pillars.shutdown();
    }

    /** Depths mine/chop task progress only if the depths classes are in the jar (see {@link DepthsTaskListener}). */
    private void registerDepthsTasks() {
        try {
            depthsTasks = new DepthsTaskListener(taskSources, tasks);
            Bukkit.getPluginManager().registerEvents(depthsTasks, core.plugin());
        } catch (NoClassDefFoundError e) {
            depthsTasks = null;
            core.plugin().getLogger().info("Progression: the depths module is not in this build; Depths task progress is off.");
        }
    }

    private Optional<UUID> islandOf(Player player) {
        if (player == null) {
            return Optional.empty();
        }
        return core.services().optional(IslandService.class).flatMap(s -> s.islandOf(player.getUniqueId()))
                .map(IslandService.IslandInfo::id);
    }

    private void registerPlaceholders() {
        for (Pillar pillar : Pillar.values()) {
            core.placeholders().registerText(ID, "pillar_" + pillar.id() + "_level", p ->
                    String.valueOf(islandOf(p).map(id -> pillars.pillarLevel(id, pillar)).orElse(0)));
            core.placeholders().registerText(ID, "pillar_" + pillar.id() + "_progress", p ->
                    String.valueOf(islandOf(p).map(id -> PillarManager.percent(pillars.pillarProgress(id, pillar))).orElse(0)));
        }
        core.placeholders().registerText(ID, "pillars_lowest", p ->
                String.valueOf(islandOf(p).map(pillars::lowestPillarLevel).orElse(0)));
        core.placeholders().registerText(ID, "pillars_total", p ->
                String.valueOf(islandOf(p).map(pillars::totalPillarLevels).orElse(0)));
        // M5.2: island_level and island_rank are registered by the islands module (they read ProgressionService).
        core.placeholders().registerText(ID, "island_level_progress", p ->
                String.valueOf(islandOf(p).filter(levels::hasLevel).map(id -> PillarManager.percent(levels.progress(id))).orElse(0)));
        core.placeholders().registerText(ID, "island_level_points", p ->
                islandOf(p).filter(levels::hasLevel).map(id -> LevelMenu.whole(levels.points(id))).orElse("0"));
        core.placeholders().registerText(ID, "island_tasks_done", p ->
                String.valueOf(islandOf(p).map(tasks::completedCount).orElse(0)));
        core.placeholders().registerText(ID, "island_tasks_total", p -> String.valueOf(tasks.catalog().size()));
        core.placeholders().registerText(ID, "island_xp_bonus", p ->
                PillarManager.trim(islandOf(p).map(pillars::islandXpBonusPercent).orElse(0.0)));
    }

    private void registerSetting() {
        core.services().optional(PlayerSettings.class).ifPresent(settings -> settings.register(new SettingToggle(ID,
                PillarManager.TOGGLE, SettingCategory.CHAT, Material.IRON_PICKAXE, true, null, ID,
                "settings." + PillarManager.TOGGLE, false)));
        core.services().optional(PlayerSettings.class).ifPresent(settings -> settings.register(new SettingToggle(ID,
                TaskManager.TOGGLE, SettingCategory.CHAT, Material.WRITABLE_BOOK, true, null, ID,
                "settings." + TaskManager.TOGGLE, false)));
    }
}
