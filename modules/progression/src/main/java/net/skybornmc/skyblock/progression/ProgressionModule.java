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
 * {@code island_xp_bonus}, and the {@code /settings} toggle {@code pillar-notices}. No commands yet: the pillars menu is
 * part of {@code /is level} (M5.2). Settings in {@code pillars.yml}, texts in {@code messages/progression.yml}.
 */
public final class ProgressionModule implements SkyblockModule {

    static final String ID = "progression";

    private final Core core;
    private final PillarsConfig config = new PillarsConfig();
    private PillarManager pillars;
    private IslandSourcesListener sources;

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
        ModuleMessages msg = core.messages(ID);
        new Migrations(core.database(), core.plugin().getLogger()).migrate(ID, getClass().getClassLoader());
        pillars = new PillarManager(core, config, msg);
        core.services().register(ID, ProgressionService.class, pillars);
        Bukkit.getPluginManager().registerEvents(pillars, core.plugin());
        sources = new IslandSourcesListener(core, config, pillars);
        Bukkit.getPluginManager().registerEvents(sources, core.plugin());
        registerPlaceholders();
        registerSetting();
        pillars.start();
    }

    @Override
    public void reload() {
        core.configs().load(config);
        core.messages().load(ID);
        pillars.applyConfig();
        sources.configure();
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
        core.placeholders().registerText(ID, "island_xp_bonus", p ->
                PillarManager.trim(islandOf(p).map(pillars::islandXpBonusPercent).orElse(0.0)));
    }

    private void registerSetting() {
        core.services().optional(PlayerSettings.class).ifPresent(settings -> settings.register(new SettingToggle(ID,
                PillarManager.TOGGLE, SettingCategory.CHAT, Material.IRON_PICKAXE, true, null, ID,
                "settings." + PillarManager.TOGGLE, false)));
    }
}
