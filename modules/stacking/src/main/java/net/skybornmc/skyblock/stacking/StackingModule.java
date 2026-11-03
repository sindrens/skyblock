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
    private SpawnerSteps steps = SpawnerSteps.DEFAULTS;
    private SpawnerDrops drops = SpawnerDrops.EMPTY;
    private SpawnerDropItems dropItems;
    private UnlockTracker unlock;
    private final MobDropGuard dropGuard = new MobDropGuard();
    private final SpawnerMobSafety mobSafety = new SpawnerMobSafety(this::config);
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
        dropItems = new SpawnerDropItems(core.items(), key -> core.messages().raw(ID, key));
        unlock = new UnlockTracker(core.database(), core.plugin().getLogger(), this::countsForUnlock,
                r -> core.scheduler().main("stacking-unlock", r));
        limits = new EntityLimits(this);
        crops = new CropGrowth(this);
        hoppers = new HopperCounter(this);
        applyConfig();
        spawnerItems.register();
        spawners.load();
        for (Listener listener : List.of(this, mobs, items, spawners, limits, crops, hoppers, dropGuard, mobSafety)) {
            Bukkit.getPluginManager().registerEvents(listener, core.plugin());
        }
        indexLoaded();
        core.services().register(ID, StackingService.class, this);
        registerCommand();
        tasks.add(core.scheduler().repeat("stacking-spawners", 20L, 1L, spawners::tick));
        tasks.add(core.scheduler().repeat("stacking-crops", 40L, 1L, crops::tick));
        tasks.add(core.scheduler().repeat("stacking-mob-merge", 40L, Math.max(10, config.naturalMergeIntervalTicks), mobs::mergeMoving));
        tasks.add(core.scheduler().repeat("stacking-perf", 1200L, 1200L, perf::report));
        long save = 20L * Math.max(5, config.unlockSaveSeconds);
        tasks.add(core.scheduler().repeat("stacking-unlock-save", save, save, unlock::flush));
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
        if (unlock != null) {
            unlock.flushBlocking();
        }
        for (Listener listener : java.util.Arrays.asList(this, mobs, items, spawners, limits, crops, hoppers, dropGuard, mobSafety)) {
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
        if (dropItems != null) {
            dropItems.unregister();
        }
    }

    private void applyConfig() {
        worlds = new HashSet<>(config.worlds);
        List<String> problems = new ArrayList<>();
        tiers = SpawnerTiers.load(List.of(config.tier1, config.tier2, config.tier3, config.tier4, config.tier5), config.legacy,
                id -> isMobType(entityType(id)), problems::add);
        steps = SpawnerSteps.parse(config.speedSteps, config.mobSteps, config.lootSteps, config.spawnerIntervalFloorSeconds, problems::add);
        dropItems.load(config.spawnerDropItems, problems::add);
        drops = SpawnerDrops.parse(config.spawnerDrops, id -> isMobType(entityType(id)), id -> dropItems.ids().contains(id), problems::add);
        for (String mob : tiers.sellableTypes()) {
            if (drops.of(mob).isEmpty()) {
                problems.add("stacking.yml → spawners.drops: sold spawner mob '" + mob + "' has no custom drop (it keeps vanilla loot)");
            }
        }
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
        Arg<String> type = Args.oneOf("type", () -> tiers.types()); // legacy and premium types too (staff give and restore)
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

    // ------------------------------------------------------------------ ECON-SPAWNERS helpers

    SpawnerSteps steps() {
        return steps;
    }

    UnlockTracker unlock() {
        return unlock;
    }

    /** Only mobs of the sold tiers 1-4 are counted for unlocks (no map growth). */
    private boolean countsForUnlock(String mob) {
        SpawnerTiers t = tiers;
        if (t == null || !t.sellable(mob)) {
            return false;
        }
        int tier = t.tier(mob).orElse(0);
        return tier >= 1 && tier <= 4;
    }

    /** Records kills of a mob at a location for the island there (main thread; one island lookup per kill batch). */
    void recordKills(org.bukkit.Location at, EntityType type, long killed) {
        if (killed <= 0 || type == null || at == null || unlock == null || !countsForUnlock(type.getKey().getKey())) {
            return;
        }
        islands().flatMap(i -> i.islandAt(at)).ifPresent(info -> unlock.record(info.id(), type.getKey().getKey(), killed));
    }

    /** The custom drop of a spawner mob type, if it has one. */
    Optional<SpawnerDrops.Drop> spawnerDrop(EntityType type) {
        return type == null ? Optional.empty() : drops.of(type.getKey().getKey());
    }

    /** The Mob Loot multiplier of the island at a location (1.0 without an island or upgrade). */
    double lootMultiplier(org.bukkit.Location at) {
        int level = islands().flatMap(i -> i.islandAt(at).map(info -> i.mobLootLevel(info.id()))).orElse(0);
        return steps.loot(level);
    }

    /** The island's cap per tier with the perk bonus (0 = no cap). */
    int spawnerCapFor(java.util.UUID island) {
        int bonus = island == null ? 0 : islands().map(i -> i.spawnerCapBonus(island)).orElse(0);
        return SpawnerRules.cap(config.spawnerMaxPerTier, bonus, config.spawnerCapPerkMax);
    }

    long unlockNeeded(int tier) {
        return switch (tier) {
            case 2 -> config.unlockTier2Kills;
            case 3 -> config.unlockTier3Kills;
            case 4 -> config.unlockTier4Kills;
            case 5 -> config.unlockTier5Kills;
            default -> 0;
        };
    }

    List<String> unlockMobs(int tier) {
        List<String> raw = switch (tier) {
            case 2 -> config.unlockTier2Mobs;
            case 3 -> config.unlockTier3Mobs;
            case 4 -> config.unlockTier4Mobs;
            case 5 -> config.unlockTier5Mobs;
            default -> List.of();
        };
        return raw.stream().map(SpawnerTiers::normalize).filter(m -> !m.isEmpty()).toList();
    }

    /** ECON-SPAWNERS step 9: a disband or reset deletes the island's kill counts (progress is gone; audit keeps the rows). */
    /** Loads the joining player's island kill counts so the first /shop open already shows them. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(org.bukkit.event.player.PlayerJoinEvent event) {
        if (unlock == null) {
            return;
        }
        islands().flatMap(i -> i.islandOf(event.getPlayer().getUniqueId())).map(IslandService.IslandInfo::id).ifPresent(unlock::preload);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onIslandRemoved(net.skybornmc.skyblock.common.island.IslandRemovedEvent event) {
        if (unlock == null || event.islandId() == null || !SpawnerManager.clearsSpawners(event.reason())) {
            return;
        }
        java.util.UUID island = event.islandId();
        java.util.Map<String, Long> before = new java.util.HashMap<>(unlock.kills(island));
        unlock.clear(island).whenComplete((rows, error) -> {
            if (error != null) {
                core.plugin().getLogger().log(java.util.logging.Level.WARNING, "Stacking: could not delete the kill counts of island " + island,
                        net.skybornmc.skyblock.common.db.Database.cause(error));
                return;
            }
            if (rows != null && rows > 0) {
                java.util.Map<String, Object> details = new java.util.LinkedHashMap<>();
                details.put("reason", event.reason());
                details.put("rows", rows);
                details.put("kills", before);
                core.audit().log(net.skybornmc.skyblock.common.audit.AuditCategory.ISLAND, "spawner_unlock_reset", event.actor(),
                        island.toString(), null, (long) rows, null, details);
            }
        });
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

    /** Sold types of a tier (legacy types never: crates and the Black Market pick from these). */
    @Override
    public List<String> spawnerTypes(int tier) {
        SpawnerTiers t = tiers;
        if (t == null) {
            return List.of();
        }
        return t.sellableTypes().stream().filter(type -> t.tier(type).orElse(0) == tier).toList();
    }

    // ------------------------------------------------------------------ ECON-SPAWNERS step 1

    @Override
    public java.util.OptionalInt spawnerCap(java.util.UUID island, int tier) {
        int cap = spawnerCapFor(island);
        return cap <= 0 ? java.util.OptionalInt.empty() : java.util.OptionalInt.of(cap);
    }

    @Override
    public long spawnersOfTier(java.util.UUID island, int tier) {
        SpawnerManager s = spawners;
        return s == null || island == null ? 0 : s.islandCount(island, tier);
    }

    @Override
    public Unlock spawnerUnlock(java.util.UUID island, int tier) {
        SpawnerTiers t = tiers;
        long needed = unlockNeeded(tier);
        if (t == null || tier <= 1 || needed <= 0 || unlock == null) {
            return Unlock.OPEN;
        }
        List<String> previous = t.sellableTypes().stream().filter(type -> t.tier(type).orElse(0) == tier - 1).toList();
        if (island == null) {
            return UnlockRules.status(tier, needed, unlockMobs(tier), previous, java.util.Map.of());
        }
        java.util.Map<String, Long> kills = unlock.kills(island);
        if (!unlock.loaded(island)) {
            // Counts still loading: locked, never a wrong unlock. The shop shows "loading" and the next open is right.
            return new Unlock(false, 0, needed, UnlockRules.LOADING, List.of());
        }
        return UnlockRules.status(tier, needed, unlockMobs(tier), previous, kills);
    }

    /**
     * Money per production hour of one full spawner of {@code mob} at the island's levels (speed, mobs, loot) and today's
     * BASE sell price of its drop (the shop applies its own dynamic factor). 0 when the mob has no sellable drop.
     */
    @Override
    public double spawnerIncomePerHour(java.util.UUID island, String mob) {
        Optional<SpawnerDrops.Drop> drop = drops.of(mob);
        if (drop.isEmpty() || tiers == null || !tiers.sellable(mob)) {
            return 0;
        }
        double base = core.services().optional(net.skybornmc.skyblock.common.sell.SellService.class)
                .flatMap(s -> s.price("custom:" + drop.get().item())).map(p -> (double) p.base()).orElse(0.0);
        if (base <= 0) {
            return 0;
        }
        Optional<IslandService> is = island == null ? Optional.empty() : islands();
        int speed = is.map(i -> i.spawnerSpeedLevel(island)).orElse(0);
        int mobsLevel = is.map(i -> i.spawnerMobsLevel(island)).orElse(0);
        int loot = is.map(i -> i.mobLootLevel(island)).orElse(0);
        return SpawnerIncome.perHour(steps.seconds(speed), config.mobsPerSpawner * steps.mobs(mobsLevel), drop.get().perMob() * steps.loot(loot), base);
    }

    @Override
    public Optional<String> spawnerDropItem(String mob) {
        return drops.of(mob).map(SpawnerDrops.Drop::item);
    }

    @Override
    public boolean spawnerSellable(String mob) {
        SpawnerTiers t = tiers;
        return t != null && t.sellable(mob);
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
