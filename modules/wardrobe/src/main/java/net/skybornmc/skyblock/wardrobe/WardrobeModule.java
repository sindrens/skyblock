package net.skybornmc.skyblock.wardrobe;

import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.skybornmc.skyblock.common.Core;
import net.skybornmc.skyblock.common.bonus.BonusService;
import net.skybornmc.skyblock.common.combat.CombatTagService;
import net.skybornmc.skyblock.common.command.Arg;
import net.skybornmc.skyblock.common.command.Args;
import net.skybornmc.skyblock.common.command.CommandSpec;
import net.skybornmc.skyblock.common.command.Suggest;
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
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

/**
 * The wardrobe, W1 (docs/plans/WARDROBE.md): the 16 sets ({@code sets.yml}), per-profile wardrobe entries shown as
 * locked real armor, the Wardrobe Chest (escrow), buying with money/crystals/credits (account Sigils + one claim per profile
 * per season), Set Tokens, {@code /wardrobe} (alias {@code /sets}), set bonuses through the {@link BonusService} and the
 * staff view in {@code /inspect}. W2: set levels 0-50 with set XP from the boosted activity, Wardrobe Tomes, merging two
 * max-level sets, warzone normalization. W3: {@code /loadout} presets (armor, pet hook, hotbar tools) applied atomically
 * through the chest escrow, combat/warzone/freeze guards. Publishes {@link WardrobeService}.
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
    private final LoadoutStore loadoutStore = new LoadoutStore();
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
    private LoadoutOps loadoutOps;
    private Loadouts loadouts;
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
            return eco == null ? CompletableFuture.completedFuture(TxResult.of(TxResult.Status.ERROR)) : net.skybornmc.skyblock.common.economy.CreditSpend.execute(eco, tx, guard); // credits: earned first, then bought (C1)
        }, System::currentTimeMillis, core.plugin().getLogger());
        loadoutOps = new LoadoutOps(core.database(), store, loadoutStore, System::currentTimeMillis, core.plugin().getLogger());
        loadouts = new Loadouts(this);
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
        // W3: /loadout [name] (WARDROBE §7.5). One optional argument, no sub-commands (framework rule).
        Arg<String> name = Args.word("name", Suggest.bySender(sender -> sender instanceof Player p ? loadouts.names(p) : List.of()));
        core.commands().register(CommandSpec.command("loadout")
                .description("Your loadouts: swap set pieces and tools in one click, or /loadout <name>")
                .category("Wardrobe").permission(Loadouts.PERM_USE).playerOnly().optional(name).example("loadout Mining")
                .executes(ctx -> loadouts.command(ctx.player(), ctx.has(name) ? ctx.get(name) : null)));
    }

    // ------------------------------------------------------------------ helpers

    /** Bonus multiplier of a set level (fixed while set levels are off, W2). */
    double bonusMultiplier(int level) {
        return SetBonuses.multiplier(level, config.levelsEnabled, config.fixedMultiplier, config.maxLevel, config.maxMultiplier);
    }

    /** Frozen by staff (the staff module publishes the {@code frozen} placeholder). */
    boolean frozen(Player player) {
        return core.placeholders().has("frozen")
                && core.placeholders().text(player, "frozen").map("true"::equalsIgnoreCase).orElse(false);
    }

    /** Milliseconds left of the player's combat tag for a tag of {@code seconds} (0 = not in combat or the rule is off). */
    long combatLeft(Player player, int seconds) {
        if (seconds <= 0) {
            return 0;
        }
        return core.services().optional(CombatTagService.class).orElse(CombatTagService.NONE).remainingMillis(player.getUniqueId(), seconds);
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

    LoadoutStore loadoutStore() {
        return loadoutStore;
    }

    LoadoutOps loadoutOps() {
        return loadoutOps;
    }

    Loadouts loadouts() {
        return loadouts;
    }
}
