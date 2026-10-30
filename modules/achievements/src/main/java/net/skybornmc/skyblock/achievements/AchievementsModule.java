package net.skybornmc.skyblock.achievements;

import net.skybornmc.skyblock.common.Core;
import net.skybornmc.skyblock.common.achievements.AchievementService;
import net.skybornmc.skyblock.common.achievements.IslandBoost;
import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.bonus.BonusContext;
import net.skybornmc.skyblock.common.bonus.BonusFamily;
import net.skybornmc.skyblock.common.bonus.BonusService;
import net.skybornmc.skyblock.common.command.CommandSpec;
import net.skybornmc.skyblock.common.cosmetics.Cosmetics;
import net.skybornmc.skyblock.common.db.Migrations;
import net.skybornmc.skyblock.common.menu.Menu;
import net.skybornmc.skyblock.common.module.SkyblockModule;
import net.skybornmc.skyblock.common.placeholder.Placeholder;
import net.skybornmc.skyblock.common.profile.ProfileService;
import net.skybornmc.skyblock.common.text.ModuleMessages;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

/**
 * Achievements (F3, DESIGN §9; AC1-AC4 of RANKS_TITLES_ACHIEVEMENTS §C): about 30 personal achievements in categories
 * ({@code achievements.yml}), progress per <b>profile</b> from existing events and services ({@link Tracker}), reward
 * titles granted to the <b>account</b> through {@code Cosmetics#grant} (owner decision PR1), an unlock toast, and:
 * <ul>
 *   <li>{@code /achievements} (owner 356) with Personal, Island and History tabs ({@link AchievementsMenu}); also from
 *       {@code /tags} → Achievements.</li>
 *   <li>Boost points per tier (1/2/3/5/8): personal categories feed {@code BonusService} families with their own caps
 *       (skill XP, tool XP, mob damage, rare finds, crystals) and extra auction slots. Never sell or money (RTA-39).</li>
 *   <li>Island achievements ({@link IslandTracker}): shared by the island's members, island boosts (island XP through
 *       {@code ProgressionService#islandXpBoost}; production, island crystals and fuel published for their systems) and
 *       the island level source {@code achievements}.</li>
 *   <li>Season reset ({@link #seasonReset}): history rows, then progress wiped; titles stay.</li>
 * </ul>
 * Placeholders {@code achievements_done}, {@code achievements_total}, {@code achievements_points},
 * {@code achievements_island_points}.
 *
 * <p>Settings: {@code achievements-settings.yml}; achievements: {@code achievements.yml}; texts:
 * {@code messages/achievements.yml}; data: {@code db/achievements}.</p>
 */
public final class AchievementsModule implements SkyblockModule, AchievementService {

    static final String ID = "achievements";
    static final String PERM_MENU = "skyblock.achievements";

    private final Core core;
    private final AchievementsConfig config = new AchievementsConfig();
    private ModuleMessages msg;
    private volatile AchievementRegistry registry = AchievementRegistry.empty();
    private Tracker tracker;
    private Toasts toasts;

    public AchievementsModule(Core core) {
        this.core = core;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void enable() throws Exception {
        core.configs().load(config);
        msg = core.messages(ID);
        new Migrations(core.database(), core.plugin().getLogger()).migrate(ID, getClass().getClassLoader());
        registry = loadRegistry();
        defineTitles();
        toasts = new Toasts(this);
        tracker = new Tracker(this);
        core.services().register(ID, AchievementService.class, this);
        Bukkit.getPluginManager().registerEvents(tracker, core.plugin());
        Bukkit.getPluginManager().registerEvents(tracker.islands(), core.plugin());
        core.services().optional(BonusService.class).ifPresent(b -> b.register(IslandTracker.SOURCE, this::bonus));
        registerPlaceholders();
        registerCommands();
        tracker.start();
    }

    @Override
    public void reload() {
        core.configs().load(config);
        core.messages().load(ID);
        registry = loadRegistry();
        defineTitles();
        tracker.schedule();
        tracker.reevaluateAll();
        tracker.islands().hook();
    }

    @Override
    public void disable() {
        if (tracker == null) {
            return;
        }
        HandlerList.unregisterAll(tracker);
        HandlerList.unregisterAll(tracker.islands());
        core.services().optional(BonusService.class).ifPresent(b -> b.unregister(IslandTracker.SOURCE));
        tracker.shutdown();
    }

    // ------------------------------------------------------------------ AchievementService

    @Override
    public void open(Player player, Menu parent) {
        open(player, parent, AchievementsMenu.Tab.PERSONAL);
    }

    @Override
    public void openIsland(Player player, Menu parent) {
        open(player, parent, AchievementsMenu.Tab.ISLAND);
    }

    private void open(Player player, Menu parent, AchievementsMenu.Tab tab) {
        if (!player.hasPermission(PERM_MENU)) {
            msg.send(player, "no-permission");
            core.sounds().play(player, "error");
            return;
        }
        AchievementsMenu menu = new AchievementsMenu(this, tab);
        if (parent != null) {
            menu.parent(parent);
        }
        core.menus().open(player, menu);
    }

    @Override
    public int extraAuctionSlots(UUID player) {
        if (tracker == null || !config.boostsEnabled) {
            return 0;
        }
        ProfileProgress p = tracker.progress(player).filter(ProfileProgress::loaded).orElse(null);
        return p == null ? 0 : BoostMath.slots(p.boostPoints(PersonalBoost.AH_SLOTS), config.ahSlotFirst, config.ahSlotSecond);
    }

    @Override
    public double islandBoostPercent(UUID islandId, IslandBoost boost) {
        return tracker == null || boost == null ? 0 : tracker.islands().percent(islandId, boost);
    }

    @Override
    public int islandPoints(UUID islandId) {
        return tracker == null ? 0 : tracker.islands().points(islandId);
    }

    /**
     * Season reset (AC4, owner 354 / RTA-44), for the season script (PLAN12): writes every profile's summary of
     * {@code endedSeason} to the History tab, then wipes all personal and island progress in one transaction. Titles stay.
     * Completes with the history rows written, or -1 when that season was already reset (nothing wiped). Main thread.
     */
    @Override
    public CompletableFuture<Integer> seasonReset(int endedSeason, Actor actor) {
        if (tracker == null) {
            return CompletableFuture.completedFuture(0);
        }
        return tracker.seasonReset(endedSeason, actor).thenApply(Tracker.ResetResult::written);
    }

    /** BonusService source (AC2): a profile's personal boost in a family, after this boost's own cap. */
    private double bonus(BonusFamily family, BonusContext context) {
        if (!config.boostsEnabled || context == null || context.player() == null || tracker == null) {
            return 0;
        }
        PersonalBoost boost = PersonalBoost.byFamily(family);
        if (boost == null) {
            return 0;
        }
        ProfileProgress p = tracker.progressOrNull(context.player().getUniqueId());
        return p == null || !p.loaded() ? 0 : BoostMath.percent(p.boostPoints(boost), config.step(boost), config.cap(boost));
    }

    @Override
    public int completed(UUID player) {
        return tracker == null ? 0 : tracker.completed(player);
    }

    @Override
    public int total() {
        return registry.size();
    }

    // ------------------------------------------------------------------ shared helpers

    Core core() {
        return core;
    }

    AchievementsConfig config() {
        return config;
    }

    ModuleMessages msg() {
        return msg;
    }

    AchievementRegistry registry() {
        return registry;
    }

    Tracker tracker() {
        return tracker;
    }

    Toasts toasts() {
        return toasts;
    }

    /** An item id as a material, or the fallback when it is not an item. */
    Material material(String id, Material fallback) {
        Material m = id == null ? null : Material.matchMaterial(id);
        return m != null && m.isItem() && !m.isAir() ? m : fallback;
    }

    /** Progress bar as MiniMessage ({@code menu.bar.filled/empty} segments, {@code menu.bar-length} long). */
    String bar(double fraction) {
        int length = config.barLength;
        int filled = ProgressMath.filled(fraction, length);
        String full = core.messages().raw(ID, "menu.bar.filled");
        String empty = core.messages().raw(ID, "menu.bar.empty");
        return (full == null ? "" : full).repeat(filled) + (empty == null ? "" : empty).repeat(length - filled);
    }

    String profileName(Player player) {
        return ProfileService.of(core.services()).activeName(player);
    }

    private void registerPlaceholders() {
        core.placeholders().register(ID, "achievements_done", Placeholder.text(p -> p == null ? "0" : String.valueOf(completed(p.getUniqueId()))));
        core.placeholders().register(ID, "achievements_total", Placeholder.text(p -> String.valueOf(total())));
        core.placeholders().register(ID, "achievements_points", Placeholder.text(p -> p == null || tracker == null ? "0"
                : String.valueOf(tracker.progress(p.getUniqueId()).filter(ProfileProgress::loaded).map(ProfileProgress::totalPoints).orElse(0))));
        core.placeholders().register(ID, "achievements_island_points", Placeholder.text(p -> {
            if (p == null || tracker == null) {
                return "0";
            }
            UUID key = ProfileService.of(core.services()).profileKey(p.getUniqueId());
            return String.valueOf(core.services().optional(net.skybornmc.skyblock.common.island.IslandService.class)
                    .flatMap(s -> s.islandOfProfile(key)).map(i -> islandPoints(i.id())).orElse(0));
        }));
    }

    /** {@code /achievements} (owner 356): no arguments (the tabs are in the menu). */
    private void registerCommands() {
        core.commands().register(CommandSpec.command("achievements")
                .description("Your achievements: personal, island and season history, with their boosts")
                .category("Achievements").permission(PERM_MENU).playerOnly().example("achievements")
                .executes(ctx -> open(ctx.player(), null)));
    }

    // ------------------------------------------------------------------ achievements.yml and titles

    /**
     * Reads {@code achievements.yml} from the data folder. A missing file is written from the bundled one; categories,
     * achievements and island achievements that are new in the bundled file are added to an existing file (turn one off
     * with {@code enabled: false} instead of deleting it, or it comes back after an update), and new fields of existing
     * categories (e.g. {@code boost}) are filled in.
     */
    private AchievementRegistry loadRegistry() {
        File file = new File(core.plugin().getDataFolder(), AchievementRegistry.FILE);
        YamlConfiguration bundled = bundled();
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            if (file.exists()) {
                yaml.load(file);
            }
        } catch (IOException | InvalidConfigurationException e) {
            core.plugin().getLogger().warning(AchievementRegistry.FILE + " is not valid YAML, using the bundled achievements. "
                    + String.valueOf(e.getMessage()).lines().findFirst().orElse(""));
            return AchievementRegistry.load(bundled, this::isItem, w -> core.plugin().getLogger().warning(w));
        }
        boolean changed = false;
        if (!file.exists()) {
            try (InputStream in = getClass().getClassLoader().getResourceAsStream(AchievementRegistry.FILE)) {
                file.getParentFile().mkdirs();
                if (in != null) {
                    java.nio.file.Files.copy(in, file.toPath());
                }
            } catch (IOException e) {
                core.plugin().getLogger().log(Level.WARNING, "Could not write " + AchievementRegistry.FILE, e);
            }
            yaml = bundled;
        } else {
            for (String root : new String[]{AchievementRegistry.CATEGORIES, AchievementRegistry.ACHIEVEMENTS, AchievementRegistry.ISLAND_ACHIEVEMENTS}) {
                if (bundled.getConfigurationSection(root) == null) {
                    continue;
                }
                for (String id : bundled.getConfigurationSection(root).getKeys(false)) {
                    String path = root + "." + id;
                    if (!yaml.contains(path)) {
                        yaml.set(path, bundled.get(path));
                        changed = true;
                    } else if (root.equals(AchievementRegistry.CATEGORIES) && yaml.isConfigurationSection(path)
                            && bundled.isConfigurationSection(path)) {
                        // New category fields from an update (AC2: boost) are added; values staff set are never changed.
                        for (String field : bundled.getConfigurationSection(path).getKeys(false)) {
                            if (!yaml.contains(path + "." + field)) {
                                yaml.set(path + "." + field, bundled.get(path + "." + field));
                                changed = true;
                            }
                        }
                    }
                }
            }
        }
        if (changed) {
            try {
                yaml.save(file);
            } catch (IOException e) {
                core.plugin().getLogger().log(Level.WARNING, "Could not write " + AchievementRegistry.FILE, e);
            }
        }
        AchievementRegistry loaded = AchievementRegistry.load(yaml, this::isItem, w -> core.plugin().getLogger().warning(w));
        core.plugin().getLogger().info("Achievements: " + loaded.size() + " achievement(s) in " + loaded.categories().size() + " categories, "
                + loaded.island().size() + " island achievement(s).");
        return loaded;
    }

    private boolean isItem(String id) {
        Material m = Material.matchMaterial(id);
        return m != null && m.isItem() && !m.isAir();
    }

    private YamlConfiguration bundled() {
        YamlConfiguration yaml = new YamlConfiguration();
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(AchievementRegistry.FILE)) {
            if (in != null) {
                yaml.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            }
        } catch (IOException | InvalidConfigurationException e) {
            core.plugin().getLogger().log(Level.SEVERE, AchievementRegistry.FILE + " in the jar is broken", e);
        }
        return yaml;
    }

    /**
     * Reward titles must exist in the cosmetics module. Titles with a display in achievements.yml are added to
     * tags-titles.yml when missing ({@code titles.add-missing}); missing titles without one are reported.
     */
    private void defineTitles() {
        Cosmetics cosmetics = core.services().optional(Cosmetics.class).orElse(null);
        if (cosmetics == null) {
            core.plugin().getLogger().info("Achievements: the cosmetics module is off, reward titles are not granted.");
            return;
        }
        String sourceTemplate = core.messages().raw(ID, "title-source");
        for (AchievementDef def : registry.all()) {
            if (def.hasTitle()) {
                defineTitle(cosmetics, def.title(), def.name(), def.hidden(), sourceTemplate, AchievementRegistry.ACHIEVEMENTS + "." + def.id());
            }
        }
        String islandTemplate = core.messages().raw(ID, "title-source-island");
        for (IslandAchievementDef def : registry.island()) {
            if (def.hasTitle()) {
                defineTitle(cosmetics, def.title(), def.name(), def.hidden(), islandTemplate, AchievementRegistry.ISLAND_ACHIEVEMENTS + "." + def.id());
            }
        }
    }

    private void defineTitle(Cosmetics cosmetics, AchievementDef.Title title, String name, boolean hidden, String template, String where) {
        if (cosmetics.ids(Cosmetics.Kind.TITLE).contains(title.id())) {
            return;
        }
        if (config.addMissingTitles && title.display() != null) {
            String source = (template == null ? "<name>" : template).replace("<name>", AchievementRegistry.visible(name));
            if (cosmetics.defineTitle(title.id(), title.display(), title.rarity(), source, hidden)) {
                core.plugin().getLogger().info("Achievements: added title '" + title.id() + "' to tags-titles.yml.");
                return;
            }
        }
        core.plugin().getLogger().warning(AchievementRegistry.FILE + " → " + where + ": reward title '"
                + title.id() + "' does not exist in tags-titles.yml (add it there or give it a display here).");
    }
}
