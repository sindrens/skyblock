package net.skybornmc.skyblock.production;

import net.skybornmc.skyblock.common.Core;
import net.skybornmc.skyblock.common.db.MainThread;
import net.skybornmc.skyblock.common.db.Migrations;
import net.skybornmc.skyblock.common.island.IslandRemovedEvent;
import net.skybornmc.skyblock.common.island.IslandService;
import net.skybornmc.skyblock.common.module.SkyblockModule;
import net.skybornmc.skyblock.common.production.FuelView;
import net.skybornmc.skyblock.common.production.ProductionService;
import net.skybornmc.skyblock.common.profile.ProfileService;
import net.skybornmc.skyblock.common.text.ModuleMessages;
import net.skybornmc.skyblock.common.util.Scheduler;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * Island production (PHASE-7-PRODUCTION.md). <b>M7.1 Island Fuel</b> (owner DESIGN §5.4, PD1–PD5, R5 / rule 9):
 * <ul>
 *   <li>States Active / Fueled / Asleep, checked every {@code fuel.check-seconds}: only a stored member on their active
 *       profile's island who did something real in the last 15 min makes it Active (+1 min of fuel per minute for the
 *       island); otherwise it burns (rate 1.0 lowered by Fuel Efficiency, cap +40%); an empty tank sleeps.</li>
 *   <li>Tank 12 h + 2 h per Fuel Tank level (read through {@code ProgressionService#upgradeEffectValue}).</li>
 *   <li>Asleep pauses production: spawners stop (stacking asks {@code IslandService#islandProductionPaused}) and crops
 *       don't grow (every {@link BlockGrowEvent} on the island is cancelled, also the stacking module's dark growth).</li>
 *   <li>Fuel Cells (1 / 3 / 6 h, bound, USE on your island) and grants ({@code IslandService#addIslandFuel}: AFK pool).</li>
 *   <li>Fuel gauge in the island menu, placeholders {@code island_fuel} / {@code island_fuel_state}, join and asleep
 *       messages.</li>
 * </ul>
 * <b>M7.2 custom generators</b> ({@link Generators}, {@code generators.yml}, own switch {@code enabled: false} by default):
 * lava + water on an island forms ores by the island's Generator level; Ashstone / Emberite custom ores; Mining pillar XP at
 * 25% for generator-made ores; {@code IslandGatherEvent} for enchant books.
 * <b>M7.3 farming</b> ({@link Farming}, {@code crops.yml}, own switch {@code enabled: false} by default): planting gates by
 * Farming level, Farming XP for ripe crops broken with a hoe on your own island, growth and yield upgrades, harvest quests.
 * <b>Off by default</b> ({@code fuel.enabled: false}): then nothing is published and nothing changes anywhere; the Fuel
 * Cell item stays defined so existing cells keep their look ("Island Fuel is off" when used).
 */
public final class ProductionModule implements SkyblockModule, Listener {

    static final String ID = "production";
    static final String PERM_MENU = "skyblock.production.menu";
    static final String PERM_CELLS = "skyblock.production.cells";

    final Core core;
    final ModuleMessages msg;
    final ProductionSettings settings = new ProductionSettings();
    final GeneratorSettings generatorSettings = new GeneratorSettings();
    final FarmingSettings farmingSettings = new FarmingSettings();
    private final ActivityTracker activity;
    private final FuelCells cells;
    private final Generators generators;
    private final Farming farming;
    private FuelEngine engine;
    private Executor main;
    private Scheduler.Task checkTask;
    private Scheduler.Task saveTask;
    private boolean running;

    public ProductionModule(Core core) {
        this.core = core;
        this.msg = core.messages(ID);
        this.activity = new ActivityTracker(p -> {
            if (engine != null) {
                engine.action(p);
            }
        });
        this.cells = new FuelCells(this);
        this.generators = new Generators(this, generatorSettings);
        this.farming = new Farming(this, farmingSettings);
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public Set<String> dependencies() {
        return Set.of("islands");
    }

    @Override
    public void enable() throws Exception {
        new Migrations(core.database(), core.plugin().getLogger()).migrate(ID, getClass().getClassLoader());
        core.configs().load(settings);
        main = new MainThread(core.plugin());
        cells.register(cellMaterial());
        activity.walkBlocks(settings.walkBlocks);
        activity.wakeLater(p -> core.scheduler().ensureMain("production:chat-action", () -> {
            if (engine != null) {
                engine.action(p);
            }
        }));
        Bukkit.getPluginManager().registerEvents(this, core.plugin());
        if (settings.enabled) {
            start();
        } else {
            core.plugin().getLogger().info("Production: Island Fuel is off (production.yml → fuel.enabled: false). Nothing is paused.");
        }
        core.configs().load(generatorSettings);
        generators.apply();
        core.configs().load(farmingSettings);
        farming.apply();
    }

    @Override
    public void reload() {
        core.configs().load(settings);
        core.messages().load(ID);
        activity.walkBlocks(settings.walkBlocks);
        if (settings.enabled && !running) {
            start();
        } else if (!settings.enabled && running) {
            stop();
            core.plugin().getLogger().info("Production: Island Fuel switched off; spawners and crops are no longer paused.");
        } else if (running) {
            scheduleTasks();
        }
        core.configs().load(generatorSettings);
        generators.apply();
        core.configs().load(farmingSettings);
        farming.apply();
    }

    @Override
    public void disable() {
        farming.shutdown();
        generators.shutdown();
        stop();
        HandlerList.unregisterAll(this);
        cells.unregister();
    }

    /** Switches Island Fuel on: loads tanks, publishes the service and the fuel provider, starts the checks. */
    private void start() {
        engine = new FuelEngine(this, activity);
        Bukkit.getPluginManager().registerEvents(activity, core.plugin());
        engine.load();
        core.services().register(ID, ProductionService.class, new ProductionServiceImpl(this));
        islands().ifPresent(i -> i.fuelProvider(engine));
        registerPlaceholders();
        scheduleTasks();
        running = true;
        core.plugin().getLogger().info("Production: Island Fuel is ON (pause when asleep: " + settings.pauseWhenAsleep + ").");
    }

    private void stop() {
        if (!running) {
            return;
        }
        running = false;
        cancelTasks();
        HandlerList.unregisterAll(activity);
        islands().ifPresent(i -> i.fuelProvider(null));
        core.services().unregister(ID, ProductionService.class);
        core.placeholders().unregisterAll(ID);
        if (engine != null) {
            engine.stopBlocking();
        }
        engine = null;
    }

    private void scheduleTasks() {
        cancelTasks();
        long check = settings.checkSeconds * 20L;
        long save = settings.saveSeconds * 20L;
        checkTask = core.scheduler().repeat("production:fuel-check", check, check, () -> {
            if (engine != null) {
                engine.tick();
            }
        });
        saveTask = core.scheduler().repeat("production:fuel-save", save, save, () -> {
            if (engine != null) {
                engine.flush();
            }
        });
    }

    private void cancelTasks() {
        if (checkTask != null) {
            checkTask.cancel();
            checkTask = null;
        }
        if (saveTask != null) {
            saveTask.cancel();
            saveTask = null;
        }
    }

    private void registerPlaceholders() {
        // Scoreboard text: "Active", "7h 20m" or "Asleep" (messages/production.yml → placeholder.*). Empty without an island.
        core.placeholders().registerText(ID, "island_fuel", p -> ownView(p).map(v -> switch (v.state()) {
            case ACTIVE -> raw("placeholder.active");
            case FUELED -> FuelMath.time(v.fuelMs());
            case ASLEEP -> raw("placeholder.asleep");
        }).orElse(""));
        core.placeholders().registerText(ID, "island_fuel_state", p -> ownView(p).map(v -> v.state().key()).orElse(""));
    }

    private Optional<FuelView> ownView(Player p) {
        FuelEngine e = engine;
        if (e == null) {
            return Optional.empty();
        }
        return islands().flatMap(i -> i.islandOf(p.getUniqueId())).map(info -> e.view(info.id()));
    }

    // ================================================================== helpers

    FuelEngine engine() {
        return engine;
    }

    FuelCells cells() {
        return cells;
    }

    Executor main() {
        return main;
    }

    boolean running() {
        return running && engine != null;
    }

    Optional<IslandService> islands() {
        return core.services().optional(IslandService.class);
    }

    String raw(String key) {
        String raw = core.messages().raw(ID, key);
        return raw == null ? "" : raw;
    }

    List<String> rawList(String key) {
        String raw = core.messages().raw(ID, key);
        return raw == null || raw.isEmpty() ? List.of() : List.of(raw.split("<newline>", -1));
    }

    void sound(Player p, String key) {
        if (p != null && key != null && !key.isBlank()) {
            core.sounds().play(p, key);
        }
    }

    private boolean flag(Player player, String placeholder) {
        return core.placeholders().has(placeholder)
                && core.placeholders().text(player, placeholder).map("true"::equalsIgnoreCase).orElse(false);
    }

    /** Why this player can't use a Fuel Cell now (message key), or empty. */
    Optional<String> problem(Player player) {
        if (flag(player, "frozen")) {
            return Optional.of("cell.frozen");
        }
        if (flag(player, "staff_mode")) {
            return Optional.of("cell.staff-mode");
        }
        if (ProfileService.of(core.services()).isSwitching(player.getUniqueId())) {
            return Optional.of("cell.switching");
        }
        return Optional.empty();
    }

    private Material cellMaterial() {
        Material m = Material.matchMaterial(settings.cellMaterial.trim().toUpperCase(Locale.ROOT));
        if (m == null || !m.isItem() || m.isAir()) {
            core.plugin().getLogger().warning("production.yml → cells.material: '" + settings.cellMaterial + "' is not an item, using magma_cream");
            return Material.MAGMA_CREAM;
        }
        return m;
    }

    // ================================================================== events

    @EventHandler(priority = EventPriority.LOW)
    public void onUse(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        ItemStack item = event.getItem();
        if (!FuelCells.isCell(item)) {
            return;
        }
        event.setCancelled(true);
        if (event.getHand() != EquipmentSlot.HAND) {
            return; // main hand only, so one click never uses two cells
        }
        if (!running()) {
            msg.send(event.getPlayer(), "cell.turned-off");
            return;
        }
        cells.use(event.getPlayer(), item);
    }

    /** Asleep islands don't grow crops (vanilla growth and the stacking module's dark growth both fire this event). */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onGrow(BlockGrowEvent event) {
        FuelEngine e = engine;
        if (e == null || !settings.pauseWhenAsleep) {
            return;
        }
        Optional<IslandService> islands = islands();
        if (islands.isEmpty() || !islands.get().isIslandWorld(event.getBlock().getWorld())) {
            return;
        }
        islands.get().islandAt(event.getBlock().getLocation()).map(IslandService.IslandInfo::id).filter(e::paused)
                .ifPresent(id -> event.setCancelled(true));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        UUID id = p.getUniqueId();
        core.scheduler().later("production:join", settings.joinDelayTicks, () -> {
            FuelEngine e = engine;
            if (e != null && p.isOnline() && p.getUniqueId().equals(id)) {
                e.tellJoin(p);
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onIslandRemoved(IslandRemovedEvent event) {
        generators.islandRemoved(event.islandId());
        farming.islandRemoved(event.islandId());
        FuelEngine e = engine;
        if (e != null) {
            e.removed(event.islandId());
        }
    }
}
