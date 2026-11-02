package net.skybornmc.skyblock.milestones;

import net.skybornmc.skyblock.common.Core;
import net.skybornmc.skyblock.common.bonus.BonusFamily;
import net.skybornmc.skyblock.common.db.MainThread;
import net.skybornmc.skyblock.common.db.Migrations;
import net.skybornmc.skyblock.common.island.IslandService;
import net.skybornmc.skyblock.common.module.SkyblockModule;
import net.skybornmc.skyblock.common.profile.ProfileService;
import net.skybornmc.skyblock.common.text.ModuleMessages;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.HandlerList;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executor;

/**
 * Milestones (Complete Developer Spec §7-9, {@code /milestones} and {@code /ms}). This slice: the chains from
 * {@code milestones.yml} (copied to the data folder once, then the owner's copy is read), counting of player actions and
 * the tier-ready notices. Everything is off until {@code milestones-settings.yml enabled: true}. Menus, claims, perks,
 * boards and resets follow in the next slices (docs/planning/MILESTONES_BUILD_PLAN.md).
 */
public final class MilestonesModule implements SkyblockModule {

    static final String ID = "milestones";
    static final String CATALOG_FILE = "milestones.yml";

    private final Core core;
    private final MilestonesConfig config = new MilestonesConfig();
    private ModuleMessages msg;
    private ProgressBar bar;
    private Executor main;
    private volatile MilestoneCatalog catalog;
    private MilestoneEngine engine;
    private MilestoneClaims claims;
    static final String PERM_USE = "skyblock.milestones.use";
    static final String BONUS_SOURCE = "milestones";

    public MilestonesModule(Core core) {
        this.core = core;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public Set<String> dependencies() {
        return Set.of("islands", "profiles");
    }

    @Override
    public void enable() throws Exception {
        new Migrations(core.database(), core.plugin().getLogger()).migrate(ID, getClass().getClassLoader());
        core.configs().load(config);
        msg = core.messages(ID);
        main = new MainThread(core.plugin());
        catalog = loadCatalog();
        bar = new ProgressBar(this);
        engine = new MilestoneEngine(this);
        claims = new MilestoneClaims(this);
        Bukkit.getPluginManager().registerEvents(engine, core.plugin());
        engine.start();
        // Spec §9.1: one bonus source "milestones" (highest claimed tier per chain, target match only; caps apply on top).
        core.services().optional(net.skybornmc.skyblock.common.bonus.BonusService.class).ifPresent(b -> b.register(BONUS_SOURCE, claims));
        // Slice 4: /rewards shows a Milestones card and counts ready tiers as one source.
        core.services().register(ID, net.skybornmc.skyblock.common.milestones.MilestoneRewards.class, claims);
        for (org.bukkit.entity.Player p : Bukkit.getOnlinePlayers()) {
            claims.load(p);
        }
        levelPoints = new IslandLevelPoints(this);
        levelPoints.start();
        registerCommand();
        // Slice 4: the action bar progress can be turned off per player (default on).
        core.services().optional(net.skybornmc.skyblock.common.settings.PlayerSettings.class).ifPresent(s -> s.register(
                new net.skybornmc.skyblock.common.settings.SettingToggle(ID, ProgressBar.SETTING,
                        net.skybornmc.skyblock.common.settings.SettingCategory.GAMEPLAY, org.bukkit.Material.EXPERIENCE_BOTTLE, true, null,
                        ID, "settings." + ProgressBar.SETTING, false)));
    }

    private IslandLevelPoints levelPoints;

    ProgressBar bar() {
        return bar;
    }

    /** {@code /milestones} (owner C2: the only alias is {@code /ms}). No arguments, so nothing to tab-complete. */
    private void registerCommand() {
        core.commands().register(net.skybornmc.skyblock.common.command.CommandSpec.command("milestones")
                .description("Your island and personal milestones: grind an activity, claim perks for it")
                .category("Progress")
                .permission(PERM_USE)
                .aliases("ms")
                .playerOnly()
                .example("ms")
                .executes(ctx -> {
                    if (!config.enabled) {
                        throw new net.skybornmc.skyblock.common.command.CommandFailure(ID, "disabled");
                    }
                    open(ctx.player());
                }));
    }

    void open(org.bukkit.entity.Player player) {
        claims.load(player);
        core.menus().open(player, new MilestonesMenu(this, null));
    }

    @Override
    public void reload() {
        core.configs().load(config);
        core.messages().load(ID);
        catalog = loadCatalog();
    }

    @Override
    public void disable() {
        core.services().optional(net.skybornmc.skyblock.common.bonus.BonusService.class).ifPresent(b -> b.unregister(BONUS_SOURCE));
        if (levelPoints != null) {
            levelPoints.stop();
            levelPoints = null;
        }
        if (engine != null) {
            HandlerList.unregisterAll(engine);
            engine.stop();
        }
        if (bar != null) {
            bar.clear();
        }
        core.services().optional(net.skybornmc.skyblock.common.settings.PlayerSettings.class).ifPresent(s -> s.unregisterAll(ID));
    }

    // ------------------------------------------------------------------ catalog

    /** Reads the owner's milestones.yml (the bundled one is copied there once); a broken file falls back to the bundled one. */
    private MilestoneCatalog loadCatalog() {
        File file = new File(core.plugin().getDataFolder(), CATALOG_FILE);
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            if (!file.exists()) {
                file.getParentFile().mkdirs();
                try (InputStream in = getClass().getClassLoader().getResourceAsStream(CATALOG_FILE)) {
                    if (in != null) {
                        java.nio.file.Files.copy(in, file.toPath());
                    }
                }
            }
            yaml.load(file);
        } catch (IOException | InvalidConfigurationException e) {
            core.plugin().getLogger().warning("milestones.yml is not valid YAML, using the bundled chains. "
                    + String.valueOf(e.getMessage()).lines().findFirst().orElse(""));
            yaml = bundled();
        }
        List<String> problems = new ArrayList<>();
        MilestoneCatalog parsed = MilestoneCatalog.parse(plain(yaml), caps(), problems::add);
        problems.forEach(p -> core.plugin().getLogger().warning(p));
        core.plugin().getLogger().info("Milestones: " + parsed.size() + " chains loaded" + (config.enabled ? "" : " (counting is off)"));
        return parsed;
    }

    private YamlConfiguration bundled() {
        YamlConfiguration yaml = new YamlConfiguration();
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(CATALOG_FILE)) {
            if (in != null) {
                yaml.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            }
        } catch (IOException | InvalidConfigurationException e) {
            core.plugin().getLogger().severe("milestones.yml in the jar is broken: " + e.getMessage());
        }
        return yaml;
    }

    /** A config section as plain maps and lists (the catalog parser is Bukkit-free). */
    static Map<String, Object> plain(ConfigurationSection section) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (String key : section.getKeys(false)) {
            out.put(key, plainValue(section.get(key)));
        }
        return out;
    }

    private static Object plainValue(Object v) {
        if (v instanceof ConfigurationSection s) {
            return plain(s);
        }
        if (v instanceof List<?> l) {
            List<Object> out = new ArrayList<>(l.size());
            for (Object o : l) {
                out.add(plainValue(o));
            }
            return out;
        }
        if (v instanceof Map<?, ?> mp) {
            Map<String, Object> out = new LinkedHashMap<>();
            mp.forEach((k, x) -> out.put(String.valueOf(k), plainValue(x)));
            return out;
        }
        return v;
    }

    /**
     * Family caps for the 15% budget check (spec §9.2: the live caps, never the numbers in the spec): from the bonus
     * service when it is there, else the defaults of {@link BonusFamily}.
     */
    private Map<String, Double> caps() {
        Optional<net.skybornmc.skyblock.common.bonus.BonusService> bonus =
                core.services().optional(net.skybornmc.skyblock.common.bonus.BonusService.class);
        Map<String, Double> out = new HashMap<>();
        for (BonusFamily f : BonusFamily.values()) {
            out.put(f.id(), bonus.map(b -> b.cap(f)).orElse(f.defaultCap()));
        }
        return out;
    }

    // ------------------------------------------------------------------ for the engine and later the menus

    Core core() {
        return core;
    }

    MilestonesConfig config() {
        return config;
    }

    MilestoneCatalog catalog() {
        return catalog;
    }

    MilestoneEngine engine() {
        return engine;
    }

    MilestoneClaims claims() {
        return claims;
    }

    ModuleMessages msg() {
        return msg;
    }

    Executor main() {
        return main;
    }

    Optional<ProfileService> profiles() {
        return core.services().optional(ProfileService.class);
    }

    Optional<IslandService> islands() {
        return core.services().optional(IslandService.class);
    }
}
