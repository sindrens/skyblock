package net.skybornmc.skyblock.enchants;

import net.skybornmc.skyblock.common.Core;
import net.skybornmc.skyblock.common.bonus.BonusService;
import net.skybornmc.skyblock.common.command.CommandSpec;
import net.skybornmc.skyblock.common.db.Migrations;
import net.skybornmc.skyblock.common.economy.EconomyService;
import net.skybornmc.skyblock.common.enchant.EnchantService;
import net.skybornmc.skyblock.common.module.SkyblockModule;
import net.skybornmc.skyblock.common.stacking.StackKillProvider;
import net.skybornmc.skyblock.common.text.ModuleMessages;
import net.skybornmc.skyblock.common.tools.ToolPerk;
import net.skybornmc.skyblock.common.tools.ToolService;
import net.skybornmc.skyblock.common.tools.ToolSlots;
import org.bukkit.Bukkit;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.HandlerList;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;

/**
 * Custom enchants, E1 (docs/plans/ENCHANTS.md): the registry ({@code enchants.yml}, 48 enchants), unique enchant books and
 * Disenchant Scrolls (one database row each, tradeable, auction house category "Enchant books"), the Enchanter
 * ({@code /enchanter}: apply, upgrade, combine, remove/shatter, Book shop rolls, guide; no fail chance), slot / conflict /
 * tool-level gate rules, crystal costs, lore on tools and books, and the passive bonus families. Publishes
 * {@link EnchantService}. E3: the stacked-mob enchants ({@link StackEnchants}: Slaughter, Bloodlust, Plunder and Soul Reaper per
 * mob, Reaper's Sweep, Phoenix Wrath on stacks, island stack books) as the published {@link StackKillProvider}. Depths
 * drops and effects come with E2 through {@link EnchantService#level}/{@link EnchantService#value}.
 *
 * <p>Settings: {@code enchanter.yml}; texts: {@code messages/enchants.yml}; data: {@code db/enchants}.</p>
 */
public final class EnchantsModule implements SkyblockModule {

    static final String ID = "enchants";
    static final String PERM_USE = "skyblock.enchants.use";
    static final String REGISTRY_FILE = "enchants.yml";

    final Core core;
    private final EnchanterConfig config = new EnchanterConfig();
    private final BookStore store = new BookStore();
    private volatile EnchantRegistry registry = EnchantRegistry.empty();
    private volatile EnchantCosts costs = EnchantCosts.defaults();
    private ModuleMessages msg;
    private EnchantText text;
    private BookItems books;
    private OpRunner runner;
    private EnchantOps ops;
    private EnchantMenus menus;
    private EnchantsListener listener;
    private StackEnchants stacks;
    private net.skybornmc.skyblock.common.util.Scheduler.Task perfTask;

    public EnchantsModule(Core core) {
        this.core = core;
    }

    @Override
    public String id() {
        return ID;
    }

    /** Enchanting needs custom tools and crystals. */
    @Override
    public Set<String> dependencies() {
        return Set.of("tools", "economy");
    }

    @Override
    public void enable() throws Exception {
        loadConfig();
        msg = core.messages(ID);
        text = new EnchantText(core.messages(), ID);
        new Migrations(core.database(), core.plugin().getLogger()).migrate(ID, getClass().getClassLoader());
        registry = loadRegistry();
        books = new BookItems(this);
        books.register();
        runner = new OpRunner(core.database(), store,
                (tx, guard) -> {
                    EconomyService eco = economy();
                    return eco == null ? java.util.concurrent.CompletableFuture.completedFuture(
                            net.skybornmc.skyblock.common.economy.TxResult.of(net.skybornmc.skyblock.common.economy.TxResult.Status.ERROR))
                            : eco.executeGuarded(tx, guard);
                },
                (c, toolId, perks) -> {
                    ToolService tools = tools();
                    if (tools == null) {
                        throw new java.sql.SQLException("The tools module is not running");
                    }
                    tools.savePerks(c, toolId, perks);
                },
                System::currentTimeMillis, core.plugin().getLogger());
        ops = new EnchantOps(this);
        menus = new EnchantMenus(this);
        listener = new EnchantsListener(this);
        stacks = new StackEnchants(this);
        Bukkit.getPluginManager().registerEvents(ops, core.plugin());
        Bukkit.getPluginManager().registerEvents(listener, core.plugin());
        Bukkit.getPluginManager().registerEvents(stacks, core.plugin());
        core.services().register(ID, EnchantService.class, new EnchantServiceImpl(this));
        core.services().register(ID, StackKillProvider.class, stacks);
        perfTask = core.scheduler().repeat("enchants-stack-perf", 1200L, 1200L, stacks.perf()::report);
        core.services().optional(BonusService.class).ifPresent(b -> {
            b.register(EnchantBonusSource.ID, new EnchantBonusSource(this));
            b.caps(config.caps());
        });
        ToolService tools = tools();
        if (tools != null) {
            tools.perkNames(this::perkName);
        }
        registerCommands();
    }

    @Override
    public void reload() {
        loadConfig();
        core.messages().load(ID);
        registry = loadRegistry();
        core.services().optional(BonusService.class).ifPresent(b -> b.caps(config.caps()));
    }

    @Override
    public void disable() {
        if (ops != null) {
            HandlerList.unregisterAll(ops);
        }
        if (listener != null) {
            HandlerList.unregisterAll(listener);
        }
        if (stacks != null) {
            HandlerList.unregisterAll(stacks);
            core.services().unregister(ID, StackKillProvider.class);
        }
        if (perfTask != null) {
            perfTask.cancel();
            perfTask = null;
        }
        core.services().optional(BonusService.class).ifPresent(b -> b.unregister(EnchantBonusSource.ID));
        ToolService tools = tools();
        if (tools != null) {
            tools.perkNames(null);
        }
        if (books != null) {
            books.unregister();
        }
    }

    private void loadConfig() {
        core.configs().load(config);
        for (String problem : config.validate()) {
            core.plugin().getLogger().warning(problem);
        }
        costs = config.costs();
    }

    // ------------------------------------------------------------------ registry

    /**
     * Reads {@code enchants.yml} from the data folder. A missing file is written from the bundled one; enchants that are
     * new in the bundled file are added to an existing file (so updates reach old servers; turn one off with
     * {@code enabled: false} instead of deleting it).
     */
    private EnchantRegistry loadRegistry() {
        File file = new File(core.plugin().getDataFolder(), REGISTRY_FILE);
        YamlConfiguration bundled = bundled();
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            if (file.exists()) {
                yaml.load(file);
            }
        } catch (IOException | InvalidConfigurationException e) {
            core.plugin().getLogger().warning("enchants.yml is not valid YAML, using the bundled enchants. "
                    + String.valueOf(e.getMessage()).lines().findFirst().orElse(""));
            return EnchantRegistry.load(bundled, w -> core.plugin().getLogger().warning(w));
        }
        boolean changed = !file.exists();
        if (changed) {
            yaml = bundled;
        } else if (bundled.getConfigurationSection(EnchantRegistry.ROOT) != null) {
            for (String id : bundled.getConfigurationSection(EnchantRegistry.ROOT).getKeys(false)) {
                String path = EnchantRegistry.ROOT + "." + id;
                if (!yaml.contains(path)) {
                    yaml.set(path, bundled.get(path));
                    changed = true;
                }
            }
        }
        if (changed) {
            try {
                file.getParentFile().mkdirs();
                if (!file.exists()) {
                    try (InputStream in = getClass().getClassLoader().getResourceAsStream(REGISTRY_FILE)) {
                        if (in != null) {
                            java.nio.file.Files.copy(in, file.toPath());
                        } else {
                            yaml.save(file);
                        }
                    }
                } else {
                    yaml.save(file);
                }
            } catch (IOException e) {
                core.plugin().getLogger().log(Level.WARNING, "Could not write enchants.yml", e);
            }
        }
        EnchantRegistry loaded = EnchantRegistry.load(yaml, w -> core.plugin().getLogger().warning(w));
        core.plugin().getLogger().info("Enchants: " + loaded.size() + " enchant(s) loaded.");
        return loaded;
    }

    private YamlConfiguration bundled() {
        YamlConfiguration yaml = new YamlConfiguration();
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(REGISTRY_FILE)) {
            if (in != null) {
                yaml.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            }
        } catch (IOException | InvalidConfigurationException e) {
            core.plugin().getLogger().log(Level.SEVERE, "enchants.yml in the jar is broken", e);
        }
        return yaml;
    }

    // ------------------------------------------------------------------ commands

    private void registerCommands() {
        core.commands().register(CommandSpec.command("enchanter")
                .description("The Enchanter: put enchant books on your tools, upgrade, combine, remove, Book shop and guide")
                .category("Enchants").permission(PERM_USE).playerOnly().example("enchanter")
                .executes(ctx -> menus.openMain(ctx.player())));
    }

    // ------------------------------------------------------------------ perk names on tools (lore, tool menu)

    /** {@code Vein Rend II}, {@code ★ Worldbreaker I} in the prestige slot, {@code (off)} for a disabled enchant. */
    String perkName(ToolPerk perk) {
        String name = text.displayName(perk.id(), perk.level());
        if (perk.slot() == ToolSlots.PRESTIGE_SLOT) {
            name = text.plain("prestige-prefix") + name;
        }
        if (registry.get(perk.id()).map(d -> !d.enabled()).orElse(false)) {
            name = name + text.plain("disabled-suffix");
        }
        return name;
    }

    // ------------------------------------------------------------------ accessors

    EnchanterConfig config() {
        return config;
    }

    EnchantRegistry registry() {
        return registry;
    }

    EnchantCosts costs() {
        return costs;
    }

    EnchantRules rules() {
        ToolService tools = tools();
        List<Integer> levels = tools == null ? ToolSlots.DEFAULT_SLOT_LEVELS : tools.slotLevels();
        int max = tools == null ? ToolSlots.DEFAULT_MAX_LEVEL : tools.maxLevel();
        return new EnchantRules(levels, max, config.twoSlotInPrestige);
    }

    ModuleMessages msg() {
        return msg;
    }

    EnchantText text() {
        return text;
    }

    BookItems books() {
        return books;
    }

    BookStore store() {
        return store;
    }

    OpRunner runner() {
        return runner;
    }

    EnchantOps ops() {
        return ops;
    }

    EnchantMenus menus() {
        return menus;
    }

    StackEnchants stacks() {
        return stacks;
    }

    ToolService tools() {
        return core.services().optional(ToolService.class).orElse(null);
    }

    EconomyService economy() {
        return core.services().optional(EconomyService.class).orElse(null);
    }
}
