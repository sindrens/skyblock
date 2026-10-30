package net.skybornmc.skyblock.wardrobe;

import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.skybornmc.skyblock.common.Core;
import net.skybornmc.skyblock.common.bonus.BonusService;
import net.skybornmc.skyblock.common.command.CommandSpec;
import net.skybornmc.skyblock.common.db.Migrations;
import net.skybornmc.skyblock.common.economy.EconomyService;
import net.skybornmc.skyblock.common.economy.TxResult;
import net.skybornmc.skyblock.common.module.SkyblockModule;
import net.skybornmc.skyblock.common.text.ModuleMessages;
import net.skybornmc.skyblock.common.wardrobe.WardrobeService;
import org.bukkit.Bukkit;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

/**
 * The wardrobe, W1 (docs/plans/WARDROBE.md): the 16 sets ({@code sets.yml}), per-profile wardrobe entries shown as
 * locked real armor, the Wardrobe Chest (escrow), buying with money/crystals/gems (account Sigils + one claim per profile
 * per season), Set Tokens, {@code /wardrobe} (alias {@code /sets}), set bonuses through the {@link BonusService} and the
 * staff view in {@code /inspect}. W2: set levels 0-50 with set XP from the boosted activity, Wardrobe Tomes, merging two
 * max-level sets, warzone normalization. Publishes {@link WardrobeService}. Loadouts come with W3.
 *
 * <p>Settings: {@code wardrobe.yml}; sets: {@code sets.yml}; texts: {@code messages/wardrobe.yml}; data: {@code db/wardrobe}.</p>
 */
public final class WardrobeModule implements SkyblockModule {

    static final String ID = "wardrobe";
    static final String PERM_USE = "skyblock.wardrobe";
    static final String PERM_STAFF = "skyblock.wardrobe.staff";
    static final String PERM_ADMIN = "skyblock.wardrobe.admin";
    static final String SETS_FILE = "sets.yml";

    final Core core;
    private final WardrobeConfig config = new WardrobeConfig();
    private final WardrobeStore store = new WardrobeStore();
    private volatile SetRegistry registry = SetRegistry.empty();
    private ModuleMessages msg;
    private WardrobeText text;
    private WardrobeItems items;
    private WardrobeOps ops;
    private Equipment equipment;
    private Shop shop;
    private Chest chest;
    private StaffActions staff;
    private Leveling leveling;
    private Merging merging;
    private Tomes tomes;
    private WardrobeMenus menus;
    private LockListener lock;
    private boolean dupeHandler;

    public WardrobeModule(Core core) {
        this.core = core;
    }

    @Override
    public String id() {
        return ID;
    }

    /** Buying needs the economy; the rest works without it. */
    @Override
    public Set<String> dependencies() {
        return Set.of("economy");
    }

    @Override
    public void enable() throws Exception {
        loadConfig();
        msg = core.messages(ID);
        text = new WardrobeText(core.messages());
        new Migrations(core.database(), core.plugin().getLogger()).migrate(ID, getClass().getClassLoader());
        registry = loadRegistry();
        items = new WardrobeItems(this);
        items.register();
        ops = new WardrobeOps(core.database(), store, (tx, guard) -> {
            EconomyService eco = core.services().optional(EconomyService.class).orElse(null);
            return eco == null ? CompletableFuture.completedFuture(TxResult.of(TxResult.Status.ERROR)) : eco.executeGuarded(tx, guard);
        }, System::currentTimeMillis, core.plugin().getLogger());
        equipment = new Equipment(this);
        shop = new Shop(this);
        chest = new Chest(this);
        staff = new StaffActions(this);
        leveling = new Leveling(this);
        merging = new Merging(this);
        tomes = new Tomes(this);
        menus = new WardrobeMenus(this);
        lock = new LockListener(this);
        Bukkit.getPluginManager().registerEvents(leveling, core.plugin());
        Bukkit.getPluginManager().registerEvents(equipment, core.plugin());
        Bukkit.getPluginManager().registerEvents(lock, core.plugin());
        if (!dupeHandler) {
            // DupeDetector has no way to remove a handler: add one for the plugin's lifetime that asks the live module.
            dupeHandler = true;
            core.dupes().addHandler(report -> {
                Equipment e = equipment;
                if (e != null) {
                    e.onDupe(report);
                }
            });
        }
        core.services().register(ID, WardrobeService.class, new WardrobeServiceImpl(this));
        core.services().optional(BonusService.class).ifPresent(b -> b.register(WardrobeService.BONUS_SOURCE, new WardrobeBonusSource(this)));
        registerCommands();
        equipment.start();
        leveling.start();
    }

    @Override
    public void reload() {
        loadConfig();
        core.messages().load(ID);
        registry = loadRegistry();
        if (leveling != null) {
            leveling.reload();
        }
        if (equipment != null) {
            equipment.refreshAll();
        }
    }

    @Override
    public void disable() {
        if (leveling != null) {
            leveling.stop(); // saves earned set XP (blocking) while the sessions still exist
            HandlerList.unregisterAll(leveling);
            leveling = null;
        }
        if (equipment != null) {
            equipment.stop();
            HandlerList.unregisterAll(equipment);
            equipment = null;
        }
        if (lock != null) {
            HandlerList.unregisterAll(lock);
        }
        core.services().optional(BonusService.class).ifPresent(b -> b.unregister(WardrobeService.BONUS_SOURCE));
        if (items != null) {
            items.unregister();
        }
    }

    private void loadConfig() {
        core.configs().load(config);
        for (String problem : config.validate()) {
            core.plugin().getLogger().warning(problem);
        }
    }

    // ------------------------------------------------------------------ sets.yml

    /**
     * Reads {@code sets.yml} from the data folder. A missing file is written from the bundled one; sets that are new in
     * the bundled file are added to an existing file (turn one off with {@code enabled: false} instead of deleting it).
     */
    private SetRegistry loadRegistry() {
        File file = new File(core.plugin().getDataFolder(), SETS_FILE);
        YamlConfiguration bundled = bundled();
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            if (file.exists()) {
                yaml.load(file);
            }
        } catch (IOException | InvalidConfigurationException e) {
            core.plugin().getLogger().warning("sets.yml is not valid YAML, using the bundled sets. "
                    + String.valueOf(e.getMessage()).lines().findFirst().orElse(""));
            return SetRegistry.load(bundled, w -> core.plugin().getLogger().warning(w));
        }
        boolean changed = false;
        if (!file.exists()) {
            try (InputStream in = getClass().getClassLoader().getResourceAsStream(SETS_FILE)) {
                file.getParentFile().mkdirs();
                if (in != null) {
                    java.nio.file.Files.copy(in, file.toPath());
                }
            } catch (IOException e) {
                core.plugin().getLogger().log(Level.WARNING, "Could not write sets.yml", e);
            }
            yaml = bundled;
        } else if (bundled.getConfigurationSection(SetRegistry.ROOT) != null) {
            for (String id : bundled.getConfigurationSection(SetRegistry.ROOT).getKeys(false)) {
                String path = SetRegistry.ROOT + "." + id;
                if (!yaml.contains(path)) {
                    yaml.set(path, bundled.get(path));
                    changed = true;
                }
            }
        }
        if (changed) {
            try {
                yaml.save(file);
            } catch (IOException e) {
                core.plugin().getLogger().log(Level.WARNING, "Could not write sets.yml", e);
            }
        }
        SetRegistry loaded = SetRegistry.load(yaml, w -> core.plugin().getLogger().warning(w));
        core.plugin().getLogger().info("Wardrobe: " + loaded.size() + " set(s) loaded.");
        return loaded;
    }

    private YamlConfiguration bundled() {
        YamlConfiguration yaml = new YamlConfiguration();
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(SETS_FILE)) {
            if (in != null) {
                yaml.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            }
        } catch (IOException | InvalidConfigurationException e) {
            core.plugin().getLogger().log(Level.SEVERE, "sets.yml in the jar is broken", e);
        }
        return yaml;
    }

    // ------------------------------------------------------------------ commands

    private void registerCommands() {
        core.commands().register(CommandSpec.command("wardrobe")
                .description("Your wardrobe: every set, buy and wear them, set bonuses, the Wardrobe Chest")
                .category("Wardrobe").permission(PERM_USE).playerOnly().aliases("sets").example("wardrobe")
                .executes(ctx -> menus.openMain(ctx.player())));
    }

    // ------------------------------------------------------------------ helpers

    /** Bonus multiplier of a set level (fixed while set levels are off, W2). */
    double bonusMultiplier(int level) {
        return SetBonuses.multiplier(level, config.levelsEnabled, config.fixedMultiplier, config.maxLevel, config.maxMultiplier);
    }

    /** An error message plus the error sound. */
    void fail(Player player, String key, TagResolver... placeholders) {
        msg.send(player, key, placeholders);
        core.sounds().play(player, "error");
    }

    WardrobeConfig config() {
        return config;
    }

    WardrobeStore store() {
        return store;
    }

    SetRegistry registry() {
        return registry;
    }

    ModuleMessages msg() {
        return msg;
    }

    WardrobeText text() {
        return text;
    }

    WardrobeItems items() {
        return items;
    }

    WardrobeOps ops() {
        return ops;
    }

    Equipment equipment() {
        return equipment;
    }

    Shop shop() {
        return shop;
    }

    Chest chest() {
        return chest;
    }

    StaffActions staff() {
        return staff;
    }

    WardrobeMenus menus() {
        return menus;
    }

    Leveling leveling() {
        return leveling;
    }

    Merging merging() {
        return merging;
    }

    Tomes tomes() {
        return tomes;
    }
}
