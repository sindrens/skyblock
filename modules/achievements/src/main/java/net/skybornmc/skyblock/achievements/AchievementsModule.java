package net.skybornmc.skyblock.achievements;

import net.skybornmc.skyblock.common.Core;
import net.skybornmc.skyblock.common.achievements.AchievementService;
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
import java.util.logging.Level;

/**
 * Achievements (F3, DESIGN §9): about 30 achievements in categories ({@code achievements.yml}), progress per
 * <b>profile</b> from existing events and services ({@link Tracker}), reward titles granted to the <b>account</b> through
 * {@code Cosmetics#grant} (owner decision PR1), an unlock toast, the achievements menu (reached from {@code /tags} →
 * Achievements and {@code /help achievements}; no own command, see docs/OPEN_QUESTIONS.md) and the placeholders
 * {@code achievements_done} and {@code achievements_total}.
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
        registerPlaceholders();
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
    }

    @Override
    public void disable() {
        if (tracker == null) {
            return;
        }
        HandlerList.unregisterAll(tracker);
        tracker.shutdown();
    }

    // ------------------------------------------------------------------ AchievementService

    @Override
    public void open(Player player, Menu parent) {
        if (!player.hasPermission(PERM_MENU)) {
            msg.send(player, "no-permission");
            core.sounds().play(player, "error");
            return;
        }
        AchievementsMenu menu = new AchievementsMenu(this);
        if (parent != null) {
            menu.parent(parent);
        }
        core.menus().open(player, menu);
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
    }

    // ------------------------------------------------------------------ achievements.yml and titles

    /**
     * Reads {@code achievements.yml} from the data folder. A missing file is written from the bundled one; categories and
     * achievements that are new in the bundled file are added to an existing file (turn one off with
     * {@code enabled: false} instead of deleting it, or it comes back after an update).
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
            for (String root : new String[]{AchievementRegistry.CATEGORIES, AchievementRegistry.ACHIEVEMENTS}) {
                if (bundled.getConfigurationSection(root) == null) {
                    continue;
                }
                for (String id : bundled.getConfigurationSection(root).getKeys(false)) {
                    String path = root + "." + id;
                    if (!yaml.contains(path)) {
                        yaml.set(path, bundled.get(path));
                        changed = true;
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
        core.plugin().getLogger().info("Achievements: " + loaded.size() + " achievement(s) in " + loaded.categories().size() + " categories.");
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
            if (!def.hasTitle() || cosmetics.ids(Cosmetics.Kind.TITLE).contains(def.title().id())) {
                continue;
            }
            if (config.addMissingTitles && def.title().display() != null) {
                String source = (sourceTemplate == null ? "<name>" : sourceTemplate).replace("<name>", AchievementRegistry.visible(def.name()));
                if (cosmetics.defineTitle(def.title().id(), def.title().display(), def.title().rarity(), source, def.hidden())) {
                    core.plugin().getLogger().info("Achievements: added title '" + def.title().id() + "' to tags-titles.yml.");
                    continue;
                }
            }
            core.plugin().getLogger().warning(AchievementRegistry.FILE + " → achievements." + def.id() + ": reward title '"
                    + def.title().id() + "' does not exist in tags-titles.yml (add it there or give it a display here).");
        }
    }
}
