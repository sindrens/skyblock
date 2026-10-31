package net.skybornmc.skyblock.progression;

import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.menu.Menu;
import net.skybornmc.skyblock.common.progression.IslandTop;
import net.skybornmc.skyblock.common.progression.LevelSource;
import net.skybornmc.skyblock.common.progression.Pillar;
import net.skybornmc.skyblock.common.progression.ProgressionService;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.ToDoubleFunction;

/** The published {@link ProgressionService}: pillars from {@link PillarManager}, the island level from {@link LevelManager}. */
final class ProgressionFacade implements ProgressionService {

    private final PillarManager pillars;
    private final LevelManager levels;
    private final LevelMenus menus;
    private UpgradeManager upgrades;
    private UpgradeMenus upgradeMenus;
    private MasteryManager masteries;

    ProgressionFacade(PillarManager pillars, LevelManager levels, LevelMenus menus) {
        this.pillars = pillars;
        this.levels = levels;
        this.menus = menus;
    }

    /** M5.4: the upgrades, set once they exist. */
    void upgrades(UpgradeManager upgrades, UpgradeMenus menus) {
        this.upgrades = upgrades;
        this.upgradeMenus = menus;
    }

    /** M5.5: the masteries, set once they exist. */
    void masteries(MasteryManager masteries) {
        this.masteries = masteries;
    }

    // ------------------------------------------------------------------ pillars (M5.1)

    @Override
    public List<Pillar> shownPillars() {
        return pillars.shownPillars();
    }

    @Override
    public boolean ready() {
        return pillars.ready();
    }

    @Override
    public int pillarLevel(UUID islandId, Pillar pillar) {
        return pillars.pillarLevel(islandId, pillar);
    }

    @Override
    public double pillarXp(UUID islandId, Pillar pillar) {
        return pillars.pillarXp(islandId, pillar);
    }

    @Override
    public double pillarProgress(UUID islandId, Pillar pillar) {
        return pillars.pillarProgress(islandId, pillar);
    }

    @Override
    public Map<Pillar, Integer> pillarLevels(UUID islandId) {
        return pillars.pillarLevels(islandId);
    }

    @Override
    public int lowestPillarLevel(UUID islandId) {
        return pillars.lowestPillarLevel(islandId);
    }

    @Override
    public int totalPillarLevels(UUID islandId) {
        return pillars.totalPillarLevels(islandId);
    }

    @Override
    public double xpForPillarLevel(int level) {
        return pillars.xpForPillarLevel(level);
    }

    @Override
    public int pillarLevelFor(double totalXp) {
        return pillars.pillarLevelFor(totalXp);
    }

    @Override
    public double balanceBonusPercent(UUID islandId) {
        return pillars.balanceBonusPercent(islandId);
    }

    @Override
    public double islandXpBonusPercent(UUID islandId) {
        return pillars.islandXpBonusPercent(islandId);
    }

    @Override
    public Set<Integer> milestonesReached(UUID islandId) {
        return pillars.milestonesReached(islandId);
    }

    @Override
    public List<Integer> milestones() {
        return pillars.milestones();
    }

    @Override
    public double addPillarXp(UUID islandId, Pillar pillar, double baseXp, String source, Actor actor) {
        return pillars.addPillarXp(islandId, pillar, baseXp, source, actor);
    }

    @Override
    public void islandXpBoost(String id, ToDoubleFunction<UUID> percentByIsland) {
        pillars.islandXpBoost(id, percentByIsland);
    }

    // ------------------------------------------------------------------ island level (M5.2)

    @Override
    public int islandLevel(UUID islandId) {
        return levels.level(islandId);
    }

    @Override
    public boolean hasIslandLevel(UUID islandId) {
        return levels.hasLevel(islandId);
    }

    @Override
    public double islandLevelProgress(UUID islandId) {
        return levels.progress(islandId);
    }

    @Override
    public double islandLevelPoints(UUID islandId) {
        return levels.points(islandId);
    }

    @Override
    public Map<LevelSource, Double> islandLevelBreakdown(UUID islandId) {
        return levels.breakdown(islandId);
    }

    @Override
    public int islandRank(UUID islandId) {
        return menus.boards().place(islandId);
    }

    @Override
    public void levelPointsProvider(LevelSource source, ToDoubleFunction<UUID> pointsByIsland) {
        if (source == LevelSource.UPGRADES || source == LevelSource.ACHIEVEMENTS || source == LevelSource.COLLECTIONS) {
            levels.provider(source, pointsByIsland);
        }
    }

    @Override
    public void requestLevelUpdate(UUID islandId) {
        levels.request(islandId);
    }

    @Override
    public List<IslandTop.Entry> islandTop(IslandTop.Kind kind, IslandTop.Board board, int limit) {
        return menus.boards().top(kind, board, limit);
    }

    @Override
    public List<IslandTop.Entry> pillarTop(Pillar pillar, IslandTop.Board board, int limit) {
        return menus.boards().pillarTop(pillar, board, limit);
    }

    @Override
    public int boardCycleSeconds() {
        return menus.boards().cycleSeconds();
    }

    @Override
    public void openLevelMenu(Player player, Menu parent) {
        menus.openLevel(player, parent);
    }

    @Override
    public void openTopMenu(Player player, Menu parent) {
        menus.openTop(player, parent);
    }

    // ------------------------------------------------------------------ island upgrades (M5.4)

    @Override
    public boolean upgradesAvailable() {
        return upgrades != null && upgrades.available();
    }

    @Override
    public void openUpgradeMenu(Player player, Menu parent) {
        if (upgradeMenus != null) {
            upgradeMenus.open(player, parent);
        }
    }

    @Override
    public int upgradeLevel(UUID islandId, String upgradeId) {
        return upgrades == null ? 0 : upgrades.level(islandId, upgradeId);
    }

    @Override
    public double upgradeEffectValue(UUID islandId, String effectId) {
        return upgrades == null ? 0 : UpgradeEffect.byId(effectId).map(e -> upgrades.effectValue(islandId, e)).orElse(0.0);
    }

    // ------------------------------------------------------------------ island masteries (M5.5)

    @Override
    public boolean masteriesAvailable() {
        return masteries != null && masteries.available();
    }

    @Override
    public void openMasteryMenu(Player player, Menu parent) {
        if (upgradeMenus != null) {
            upgradeMenus.openMasteries(player, parent);
        }
    }

    @Override
    public int masteryRank(UUID islandId, String masteryId) {
        return masteries == null ? 0 : masteries.rank(islandId, masteryId);
    }

    @Override
    public double masteryEffectValue(UUID islandId, String effectId) {
        return masteries == null ? 0 : MasteryEffect.byId(effectId).map(e -> masteries.effectValue(islandId, e)).orElse(0.0);
    }
}
