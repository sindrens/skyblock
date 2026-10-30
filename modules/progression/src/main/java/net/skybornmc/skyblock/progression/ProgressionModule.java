package net.skybornmc.skyblock.progression;

import net.skybornmc.skyblock.common.Core;
import net.skybornmc.skyblock.common.db.Migrations;
import net.skybornmc.skyblock.common.island.IslandService;
import net.skybornmc.skyblock.common.module.SkyblockModule;
import net.skybornmc.skyblock.common.progression.Pillar;
import net.skybornmc.skyblock.common.progression.ProgressionService;
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
 * {@code island_level_progress}, {@code island_level_points} and the NPCHOLO island boards. Texts in
 * {@code messages/progression.yml}.
 */
public final class ProgressionModule implements SkyblockModule {

    static final String ID = "progression";

    private final Core core;
    private final PillarsConfig config = new PillarsConfig();
    private final LevelConfig levelConfig = new LevelConfig();
    private final BlockValuesConfig blockValues = new BlockValuesConfig();
    private PillarManager pillars;
    private IslandSourcesListener sources;
    private BlockWorth blocks;
    private LevelManager levels;

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
        core.services().register(ID, ProgressionService.class, new ProgressionFacade(pillars, levels, menus));
        registerPlaceholders();
        registerSetting();
        pillars.start();
        blocks.start();
        levels.start();
        // Shop and sell prices may be published by modules enabled after this one: values again once everything is up.
        core.scheduler().later("progression-block-values", 1L, blocks::rebuildValues);
    }

    @Override
    public void reload() {
        core.configs().load(config);
        core.configs().load(levelConfig);
        core.configs().load(blockValues);
        core.messages().load(ID);
        pillars.applyConfig();
        sources.configure();
        blocks.applyConfig();
        levels.applyConfig();
    }

    @Override
    public void disable() {
        if (pillars == null) {
            return;
        }
        HandlerList.unregisterAll(pillars);
        if (sources != null) {
            HandlerList.unregisterAll(sources);
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
        core.placeholders().registerText(ID, "island_xp_bonus", p ->
                PillarManager.trim(islandOf(p).map(pillars::islandXpBonusPercent).orElse(0.0)));
    }

    private void registerSetting() {
        core.services().optional(PlayerSettings.class).ifPresent(settings -> settings.register(new SettingToggle(ID,
                PillarManager.TOGGLE, SettingCategory.CHAT, Material.IRON_PICKAXE, true, null, ID,
                "settings." + PillarManager.TOGGLE, false)));
    }
}
