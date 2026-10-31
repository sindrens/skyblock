package net.skybornmc.skyblock.stacking;

import com.destroystokyo.paper.event.entity.EntityAddToWorldEvent;
import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent;
import net.skybornmc.skyblock.common.Core;
import net.skybornmc.skyblock.common.command.Arg;
import net.skybornmc.skyblock.common.command.Args;
import net.skybornmc.skyblock.common.command.CommandSpec;
import net.skybornmc.skyblock.common.db.Migrations;
import net.skybornmc.skyblock.common.island.IslandService;
import net.skybornmc.skyblock.common.module.SkyblockModule;
import net.skybornmc.skyblock.common.stacking.StackingService;
import net.skybornmc.skyblock.common.text.ModuleMessages;
import net.skybornmc.skyblock.common.util.Scheduler;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Stacking (owner decisions 2026-10-30, Stacking S1–S28 and SK1–SK10): stacked mobs ({@link MobStacker}), dropped items
 * ({@link ItemStacker}) and spawner blocks ({@link SpawnerManager}) in the worlds of {@code stacking.yml → worlds},
 * island entity limits ({@link EntityLimits}), crops that grow in the dark ({@link CropGrowth}) and the per-chunk hopper
 * counter with staff alerts ({@link HopperCounter}, owner F13). Stacking can't be
 * turned off (owner S11). Publishes {@link StackingService}; reads {@link IslandService} upgrade hooks and the optional
 * {@code StackKillProvider} (weapon multi-kills and per-mob bonuses), offers the batch kill API
 * ({@link StackingService#killFromStack}, fires {@code StackKillEvent}). Command: {@code /spawner give} (staff).
 */
public final class StackingModule implements SkyblockModule, StackingService, Listener {

    static final String ID = "stacking";
    static final String PERM_GIVE = "skyblock.spawner.give";
    /** R3-13b: restore frozen spawners from /inspect. */
    static final String PERM_RESTORE = "skyblock.spawner.restore";

    private final Core core;
    private final StackingConfig config = new StackingConfig();
    private ModuleMessages msg;
    private Perf perf;
    private Set<String> worlds = Set.of();
    private SpawnerTiers tiers;
    private SpawnerEfficiency efficiency = SpawnerEfficiency.defaults();
    private List<SpawnerTiers.RareDrop> rareDrops = List.of();
    private MobStacker mobs;
    private ItemStacker items;
    private SpawnerManager spawners;
    private SpawnerItems spawnerItems;
    private EntityLimits limits;
    private CropGrowth crops;
    private HopperCounter hoppers;
    private final List<Scheduler.Task> tasks = new ArrayList<>();

    public StackingModule(Core core) {
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
        perf = new Perf(core.plugin().getLogger(), () -> config.perfWarnMsPerSecond);
        mobs = new MobStacker(this);
        items = new ItemStacker(this);
        spawners = new SpawnerManager(this);
        spawnerItems = new SpawnerItems(this);
        limits = new EntityLimits(this);
        crops = new CropGrowth(this);
        hoppers = new HopperCounter(this);
        applyConfig();
        spawnerItems.register();
        spawners.load();
        for (Listener listener : List.of(this, mobs, items, spawners, limits, crops, hoppers)) {
            Bukkit.getPluginManager().registerEvents(listener, core.plugin());
        }
        indexLoaded();
        core.services().register(ID, StackingService.class, this);
        registerCommand();
        tasks.add(core.scheduler().repeat("stacking-spawners", 20L, 1L, spawners::tick));
        tasks.add(core.scheduler().repeat("stacking-crops", 40L, 1L, crops::tick));
        tasks.add(core.scheduler().repeat("stacking-mob-merge", 40L, Math.max(10, config.naturalMergeIntervalTicks), mobs::mergeMoving));
        tasks.add(core.scheduler().repeat("stacking-perf", 1200L, 1200L, perf::report));
    }

    @Override
    public void reload() {
        core.configs().load(config);
        core.messages().load(ID);
        applyConfig();
        spawners.recount(); // F2: the tier lists may have changed
        hoppers.loadAll(); // the counter may have been switched on, or the worlds changed
    }

    @Override
    public void disable() {
        tasks.forEach(Scheduler.Task::cancel);
        tasks.clear();
        for (Listener listener : java.util.Arrays.asList(this, mobs, items, spawners, limits, crops, hoppers)) {
            if (listener != null) {
                HandlerList.unregisterAll(listener);
            }
        }
        if (crops != null) {
            crops.saveAll();
        }
        if (mobs != null) {
            mobs.index().clear();
        }
        if (limits != null) {
            limits.clear();
        }
        if (hoppers != null) {
            hoppers.clear();
        }
        if (spawnerItems != null) {
            spawnerItems.unregister();
        }
    }

    private void applyConfig() {
        worlds = new HashSet<>(config.worlds);
        List<String> problems = new ArrayList<>();
        tiers = SpawnerTiers.load(List.of(config.tier1, config.tier2, config.tier3, config.tier4, config.tier5),
                id -> isMobType(entityType(id)), problems::add);
        efficiency = SpawnerEfficiency.parse(config.spawnerEfficiency, problems::add);
        rareDrops = SpawnerTiers.rareDrops(config.rareDrops, id -> isMobType(entityType(id)),
                id -> Material.matchMaterial(id) != null && Material.matchMaterial(id).isItem(), problems::add);
        if (config.spawnerStackStart > config.spawnerStackMax) {
            problems.add("stacking.yml → spawners.stack-start (" + config.spawnerStackStart + ") is above spawners.stack-max ("
                    + config.spawnerStackMax + "); the max is used");
        }
        problems.forEach(core.plugin().getLogger()::warning);
        mobs.applyConfig();
        crops.applyConfig();
    }

    /** Indexes the mobs, limit entities and crop chunks that are already loaded (server reload / late enable). */
    private void indexLoaded() {
        for (World world : Bukkit.getWorlds()) {
            if (!eligible(world)) {
                continue;
            }
            for (Entity entity : world.getEntities()) {
                added(entity);
            }
            if (config.cropsEnabled) {
                for (Chunk chunk : world.getLoadedChunks()) {
                    crops.loadChunk(chunk);
                }
            }
        }
        hoppers.loadAll();
    }

    private void added(Entity entity) {
        if (entity instanceof LivingEntity living && !(entity instanceof Player)) {
            mobs.track(living);
        }
        limits.added(entity);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onAdd(EntityAddToWorldEvent event) {
        if (eligible(event.getWorld())) {
            added(event.getEntity());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRemove(EntityRemoveFromWorldEvent event) {
        Entity entity = event.getEntity();
        if (entity instanceof LivingEntity living) {
            mobs.untrack(living);
        }
        limits.removed(entity);
    }

    // ------------------------------------------------------------------ command

    private void registerCommand() {
        Arg<Player> player = Args.onlinePlayer("player");
        Arg<String> type = Args.oneOf("type", () -> tiers.types());
        Arg<Integer> amount = Args.integer("amount", 1, 2304, "1", "8", "64");
        core.commands().register(CommandSpec.command("spawner")
                .description("Stacked spawners (staff tools)").category("Staff")
                .permission(PERM_GIVE)
                .sub(CommandSpec.sub("give")
                        .description("Give a player spawner items (staff)")
                        .arg(player).arg(type).optional(amount)
                        .example("spawner give Steve zombie 8")
                        .executes(ctx -> spawners.staffGive(ctx.sender(), ctx.get(player), ctx.get(type), ctx.getOr(amount, 1)))));
    }

    // ------------------------------------------------------------------ shared helpers

    boolean eligible(World world) {
        return world != null && worlds.contains(world.getName());
    }

    Optional<IslandService> islands() {
        return core.services().optional(IslandService.class);
    }

    /** An entity type by id ({@code zombie}, {@code minecraft:iron_golem}), or null. */
    EntityType entityType(String id) {
        NamespacedKey key = NamespacedKey.fromString(SpawnerTiers.normalize(id));
        return key == null ? null : Registry.ENTITY_TYPE.get(key);
    }

    private static boolean isMobType(EntityType type) {
        return type != null && type.isSpawnable() && type.isAlive() && type.getEntityClass() != null;
    }

    Core core() {
        return core;
    }

    StackingConfig config() {
        return config;
    }

    ModuleMessages msg() {
        return msg;
    }

    Perf perf() {
        return perf;
    }

    SpawnerTiers tiers() {
        return tiers;
    }

    SpawnerEfficiency efficiency() {
        return efficiency;
    }

    List<SpawnerTiers.RareDrop> rareDrops() {
        return rareDrops;
    }

    MobStacker mobs() {
        return mobs;
    }

    ItemStacker items() {
        return items;
    }

    SpawnerManager spawners() {
        return spawners;
    }

    SpawnerItems spawnerItems() {
        return spawnerItems;
    }

    HopperCounter hoppers() {
        return hoppers;
    }

    // ------------------------------------------------------------------ StackingService

    @Override
    public void openFrozenSpawners(Player staff, java.util.UUID player, String name, net.skybornmc.skyblock.common.menu.Menu back) {
        if (!staff.hasPermission(PERM_RESTORE)) {
            msg.send(staff, "frozen.no-permission");
            return;
        }
        FrozenSpawnerMenu.open(this, staff, player, name, back);
    }

    @Override
    public int mobCount(Entity entity) {
        return MobStacker.count(entity);
    }

    @Override
    public long itemCount(Item item) {
        return item.isValid() ? ItemStacker.count(item) : 0;
    }

    @Override
    public int hopperCount(org.bukkit.Location location) {
        HopperCounter h = hoppers;
        return h == null || location == null ? 0 : h.count(location);
    }

    @Override
    public java.util.Optional<HopperHotspot> busiestHopperChunk(String world, int minX, int minZ, int maxX, int maxZ) {
        HopperCounter h = hoppers;
        if (h == null || world == null) {
            return java.util.Optional.empty();
        }
        return h.busiest(world, minX, minZ, maxX, maxZ)
                .map(s -> new HopperHotspot(s.chunkX(), s.chunkZ(), s.count(), config.hopperAlertAbove));
    }

    @Override
    public int spawnerCount(Block block) {
        return spawners.count(block);
    }

    @Override
    public long spawnersOnIsland(java.util.UUID islandId) {
        SpawnerManager s = spawners;
        return s == null ? 0 : s.onIsland(islandId);
    }

    @Override
    public java.util.OptionalInt spawnerTier(String type) {
        SpawnerTiers t = tiers;
        if (t == null || type == null) {
            return java.util.OptionalInt.empty();
        }
        return t.tier(type).map(java.util.OptionalInt::of).orElse(java.util.OptionalInt.empty());
    }

    @Override
    public boolean fromStackedSpawner(Entity entity) {
        return MobStacker.isSpawnerMob(entity);
    }

    @Override
    public List<LivingEntity> stacksNear(org.bukkit.Location at, double radius) {
        return mobs.stacksNear(at, radius);
    }

    @Override
    public int killFromStack(LivingEntity stack, int count, Player killer) {
        return mobs.killBatch(stack, count, killer);
    }

    @Override
    public List<String> spawnerTypes(int tier) {
        SpawnerTiers t = tiers;
        if (t == null) {
            return List.of();
        }
        return t.types().stream().filter(type -> t.tier(type).orElse(0) == tier).toList();
    }

    /**
     * Owner F2: how well the next spawner of {@code type} works on the player's island (their active profile's island),
     * from the in-memory counts of that tier. 100 without an island (the first spawner always works fully).
     */
    @Override
    public int nextSpawnerOutputPercent(Player player, String type) {
        SpawnerTiers t = tiers;
        SpawnerManager s = spawners;
        if (t == null || s == null || player == null || type == null) {
            return 100;
        }
        int tier = t.tier(type).orElse(0);
        java.util.UUID island = islands().flatMap(i -> i.islandOf(player.getUniqueId())).map(IslandService.IslandInfo::id).orElse(null);
        return nextPercent(efficiency, island == null ? 0 : s.islandCount(island, tier), tier);
    }

    /** The shop hook's value: the next spawner's percent, rounded; 100 for an unknown tier. */
    static int nextPercent(SpawnerEfficiency curve, long owned, int tier) {
        if (tier < 1) {
            return 100;
        }
        return (int) Math.round(curve.nextPercent(owned));
    }

    @Override
    public java.util.Optional<org.bukkit.inventory.ItemStack> createSpawner(String type, int amount, String source) {
        SpawnerItems made = spawnerItems;
        return made == null ? java.util.Optional.empty() : made.create(type, amount, source);
    }
}
