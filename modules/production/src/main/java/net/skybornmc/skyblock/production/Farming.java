package net.skybornmc.skyblock.production;

import net.skybornmc.skyblock.common.bonus.BonusContext;
import net.skybornmc.skyblock.common.bonus.BonusFamily;
import net.skybornmc.skyblock.common.bonus.BonusService;
import net.skybornmc.skyblock.common.island.IslandService;
import net.skybornmc.skyblock.common.production.FarmingService;
import net.skybornmc.skyblock.common.production.IslandCropHarvestEvent;
import net.skybornmc.skyblock.common.progression.ProgressionService;
import net.skybornmc.skyblock.common.quest.GameActionEvent;
import net.skybornmc.skyblock.common.skills.SkillService;
import net.skybornmc.skyblock.common.skills.SkillType;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.util.Scheduler;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Bisected;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.block.BlockFertilizeEvent;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerHarvestBlockEvent;
import org.bukkit.inventory.ItemStack;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Island farming (PHASE-7-PRODUCTION.md M7.3; owner PD10–PD12), only while {@code crops.yml → enabled}:
 * <ul>
 *   <li><b>Planting gate:</b> a seed places its crop only with the crop's Farming level ("Farming 15 needed: 9/15").</li>
 *   <li><b>Farming XP:</b> ripe crops broken (sweet berries picked) <b>with a hoe</b> by a stored member on their own island in
 *       survival; never bone-mealed crops, never placed melons/pumpkins/cane/cactus/torchflowers (only blocks that grew count,
 *       remembered per island), never coop players or visitors. The skill XP feeds the Farming pillar (progression module,
 *       which pauses its own crop source while Farming pays).</li>
 *   <li><b>Growth:</b> every natural growth step on an island (vanilla and the stacking module's dark growth both fire
 *       {@link BlockGrowEvent}) gets extra stages by {@code (1 + Crop Growth%) × (1 + crop growth family%)}; an Asleep island
 *       never grows (the fuel listener cancels the event earlier).</li>
 *   <li><b>Harvest Yield:</b> ripe harvests on the island get extra copies of the crop item by the upgrade's chance.</li>
 *   <li>Hooks: {@link FarmingService} (published while on), {@link IslandCropHarvestEvent}, {@link GameActionEvent#HARVEST_CROP}
 *       for harvest quests.</li>
 * </ul>
 * Per event: one or two map lookups, at most one island lookup; growth bonuses are cached per island and refreshed every
 * {@code growth.refresh-seconds}. Main thread.
 */
final class Farming implements FarmingService {

    static final String SOURCE = "island_crop";
    private static final int MAX_COLUMN = 32;

    private record LastBreak(UUID world, int x, int y, int z, UUID island, boolean ripe) {
    }

    private final ProductionModule m;
    private final FarmingSettings s;
    private final Engine engine = new Engine();
    /** Positions of melons, pumpkins, cane, cactus and torchflowers that grew (only those are ripe). */
    private final GeneratorMemory grown;
    /** Positions of crops that got bone meal (no XP, no quest progress). */
    private final GeneratorMemory boneMealed;
    /** Best "nearby crop growth" bonus of members standing on each island (refreshed). */
    private final Map<UUID, Double> family = new HashMap<>();
    /** Upgrade values per island: [crop growth %, harvest yield %] (cleared at every refresh). */
    private final Map<UUID, double[]> upgrades = new HashMap<>();
    private final Map<UUID, Long> lockedMessage = new HashMap<>();

    private volatile CropCatalog catalog = CropCatalog.parse(List.of(), p -> { });
    private Map<Material, CropCatalog.Crop> byBlock = new EnumMap<>(Material.class);
    private Map<Material, CropCatalog.Crop> byPlant = new EnumMap<>(Material.class);
    private Map<String, Material> items = Map.of();
    private LastBreak last;
    private Scheduler.Task refreshTask;
    private boolean running;

    Farming(ProductionModule module, FarmingSettings settings) {
        this.m = module;
        this.s = settings;
        this.grown = new GeneratorMemory(settings.memoryPerIsland);
        this.boneMealed = new GeneratorMemory(settings.memoryPerIsland);
    }

    // ================================================================== lifecycle

    /** (Re)reads the crop list and turns farming on or off. */
    void apply() {
        CropCatalog c = CropCatalog.parse(s.crops, p -> m.core.plugin().getLogger().warning(p));
        Map<Material, CropCatalog.Crop> blocks = new EnumMap<>(Material.class);
        Map<Material, CropCatalog.Crop> plants = new EnumMap<>(Material.class);
        Map<String, Material> itemMap = new HashMap<>();
        for (CropCatalog.Crop crop : c.crops()) {
            Material block = Material.matchMaterial(crop.block());
            Material plant = Material.matchMaterial(crop.plant());
            Material item = Material.matchMaterial(crop.item());
            if (block == null || !block.isBlock() || plant == null || !plant.isBlock() || item == null || !item.isItem()) {
                m.core.plugin().getLogger().warning("crops.yml → crops." + crop.id() + ": block, plant or item is not valid (skipped)");
                continue;
            }
            blocks.put(block, crop);
            plants.putIfAbsent(plant, crop);
            itemMap.put(crop.id(), item);
        }
        catalog = c;
        byBlock = blocks;
        byPlant = plants;
        items = Map.copyOf(itemMap);
        grown.capacity(s.memoryPerIsland);
        boneMealed.capacity(s.memoryPerIsland);
        if (s.enabled && !running) {
            start();
        } else if (!s.enabled && running) {
            stop();
            m.core.plugin().getLogger().info("Production: farming switched off; crops are vanilla again.");
        } else if (running) {
            scheduleRefresh();
        }
        if (!s.enabled && !running) {
            m.core.plugin().getLogger().info("Production: farming is off (crops.yml → enabled: false).");
        }
    }

    private void start() {
        Bukkit.getPluginManager().registerEvents(engine, m.core.plugin());
        m.core.services().register(ProductionModule.ID, FarmingService.class, this);
        scheduleRefresh();
        running = true;
        m.core.plugin().getLogger().info("Production: farming is ON (" + byBlock.size() + " crops, tiers at Farming " + tierLevels() + ").");
    }

    private void scheduleRefresh() {
        if (refreshTask != null) {
            refreshTask.cancel();
        }
        long period = s.growthRefreshSeconds * 20L;
        refreshTask = m.core.scheduler().repeat("production:farming-refresh", 20L, period, this::refresh);
    }

    private void stop() {
        if (!running) {
            return;
        }
        running = false;
        HandlerList.unregisterAll(engine);
        m.core.services().unregister(ProductionModule.ID, FarmingService.class);
        if (refreshTask != null) {
            refreshTask.cancel();
            refreshTask = null;
        }
        grown.clear();
        boneMealed.clear();
        family.clear();
        upgrades.clear();
        lockedMessage.clear();
        last = null;
    }

    /** Module disable. */
    void shutdown() {
        stop();
    }

    void islandRemoved(UUID islandId) {
        grown.clear(islandId);
        boneMealed.clear(islandId);
        family.remove(islandId);
        upgrades.remove(islandId);
    }

    boolean running() {
        return running;
    }

    // ================================================================== FarmingService

    @Override
    public List<Integer> tierLevels() {
        return catalog.tierLevels();
    }

    @Override
    public int plantLevel(Material plantBlock) {
        CropCatalog.Crop crop = plantBlock == null ? null : byPlant.get(plantBlock);
        return crop == null ? 0 : crop.level();
    }

    // ================================================================== bonuses

    /** Every refresh: the best nearby crop growth bonus of members standing on each island; upgrade values are re-read. */
    private void refresh() {
        upgrades.clear();
        family.clear();
        long now = System.currentTimeMillis();
        lockedMessage.values().removeIf(t -> now - t > s.lockedMessageCooldownMs);
        IslandService islands = m.islands().orElse(null);
        BonusService bonus = m.core.services().optional(BonusService.class).orElse(null);
        if (islands == null || bonus == null) {
            return;
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!islands.isIslandWorld(p.getWorld())) {
                continue;
            }
            UUID id = islands.islandAt(p.getLocation()).map(IslandService.IslandInfo::id).orElse(null);
            if (id == null || !member(islands, p, id)) {
                continue;
            }
            double v = bonus.total(BonusFamily.CROP_GROWTH, BonusContext.of(p, null, s.growthFamilyActivity));
            if (v > 0) {
                family.merge(id, v, Math::max);
            }
        }
    }

    private double[] upgrades(UUID island) {
        return upgrades.computeIfAbsent(island, id -> {
            Optional<ProgressionService> p = m.core.services().optional(ProgressionService.class);
            return new double[]{p.map(x -> x.upgradeEffectValue(id, s.growthUpgradeEffect)).orElse(0.0),
                    p.map(x -> x.upgradeEffectValue(id, s.yieldUpgradeEffect)).orElse(0.0)};
        });
    }

    double growthMultiplier(UUID island) {
        double cap = m.core.services().optional(BonusService.class).map(b -> b.cap(BonusFamily.CROP_GROWTH))
                .orElse(BonusFamily.CROP_GROWTH.defaultCap());
        return FarmingRules.growthMultiplier(upgrades(island)[0], family.getOrDefault(island, 0.0), cap);
    }

    // ================================================================== helpers

    private UUID islandAt(Block b) {
        IslandService islands = m.islands().orElse(null);
        if (islands == null || !islands.isIslandWorld(b.getWorld())) {
            return null;
        }
        return islands.islandAt(b.getLocation()).map(IslandService.IslandInfo::id).orElse(null);
    }

    /** A stored member on their own island in survival/adventure (never coop players or visitors). */
    private static boolean member(IslandService islands, Player player, UUID islandId) {
        GameMode mode = player.getGameMode();
        if (mode == GameMode.CREATIVE || mode == GameMode.SPECTATOR) {
            return false;
        }
        UUID uuid = player.getUniqueId();
        return islands.islandOf(uuid).map(i -> i.id().equals(islandId)).orElse(false) && islands.countsForIsland(islandId, uuid);
    }

    private boolean member(Player player, UUID islandId) {
        return m.islands().map(i -> member(i, player, islandId)).orElse(false);
    }

    private static boolean ripeByAge(BlockData data) {
        return data instanceof Ageable a && a.getAge() >= a.getMaximumAge();
    }

    private static boolean hoe(ItemStack item) {
        return item != null && Tag.ITEMS_HOES.isTagged(item.getType());
    }

    private int farmingLevel(Player p) {
        return m.core.services().optional(SkillService.class).map(sk -> sk.level(p, SkillType.FARMING)).orElse(0);
    }

    String cropName(CropCatalog.Crop crop) {
        String name = m.raw("farming.crops." + crop.id());
        return name.isEmpty() ? crop.id() : name;
    }

    /** Extra copies of the crop item in these drops by the island's Harvest Yield. */
    private void applyYield(UUID island, CropCatalog.Crop crop, List<ItemStack> stacks) {
        int extra = FarmingRules.extraCopies(upgrades(island)[1], ThreadLocalRandom.current().nextDouble());
        Material item = items.get(crop.id());
        if (extra <= 0 || item == null) {
            return;
        }
        for (ItemStack stack : stacks) {
            if (stack != null && stack.getType() == item) {
                stack.setAmount((int) Math.min(stack.getMaxStackSize(), (long) stack.getAmount() * (1 + extra)));
            }
        }
    }

    /** XP, the harvest event and quest progress for a ripe harvest (or nothing). */
    private void harvested(Player player, Block block, UUID island, CropCatalog.Crop crop, int count, boolean ripe, boolean meal) {
        if (!ripe || count <= 0) {
            return;
        }
        boolean member = member(player, island);
        if (!member) {
            return; // coop players and visitors add nothing (rule 8)
        }
        ItemStack hand = player.getInventory().getItemInMainHand();
        FarmingRules.Harvest h = new FarmingRules.Harvest(true, true, hoe(hand), meal);
        Bukkit.getPluginManager().callEvent(new IslandCropHarvestEvent(player, block, island, crop.id(), crop.tier(), count, hand,
                h.withHoe(), meal));
        double xp = FarmingRules.skillXp(h, count, crop.xp(), s.xpNeedsHoe);
        if (xp > 0) {
            m.core.services().optional(SkillService.class).ifPresent(sk -> sk.addXp(player, SkillType.FARMING, xp, SOURCE));
        }
        if (FarmingRules.countsForQuests(h)) {
            Bukkit.getPluginManager().callEvent(new GameActionEvent(player, GameActionEvent.HARVEST_CROP, count));
        }
    }

    // ================================================================== events

    private final class Engine implements Listener {

        /** PD10: planting needs the crop's Farming level. */
        @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
        public void onPlant(BlockPlaceEvent event) {
            if (!s.plantingGate) {
                return;
            }
            Block b = event.getBlockPlaced();
            CropCatalog.Crop crop = byPlant.get(b.getType());
            Player p = event.getPlayer();
            if (crop == null || crop.level() <= 0 || p.getGameMode() == GameMode.CREATIVE
                    || !m.islands().map(i -> i.isIslandWorld(b.getWorld())).orElse(false)) {
                return;
            }
            int have = farmingLevel(p);
            if (FarmingRules.mayPlant(crop.level(), have)) {
                return;
            }
            event.setCancelled(true);
            long now = System.currentTimeMillis();
            Long before = lockedMessage.get(p.getUniqueId());
            if (before == null || now - before >= s.lockedMessageCooldownMs) {
                lockedMessage.put(p.getUniqueId(), now);
                m.msg.send(p, "farming.locked", Msg.p("crop", cropName(crop)), Msg.p("level", crop.level()), Msg.p("have", have));
                m.sound(p, "error");
            }
        }

        /** A placed block is never grown or bone-mealed, whatever was remembered there. */
        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void onPlaced(BlockPlaceEvent event) {
            Block b = event.getBlockPlaced();
            if (!byBlock.containsKey(b.getType()) && !byPlant.containsKey(b.getType())) {
                return;
            }
            UUID island = islandAt(b);
            if (island != null) {
                grown.forget(island, b.getX(), b.getY(), b.getZ());
                boneMealed.forget(island, b.getX(), b.getY(), b.getZ());
            }
        }

        /** PD11: bone-mealed crops give no XP; remembered per position until harvested or replanted. */
        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void onFertilize(BlockFertilizeEvent event) {
            Block b = event.getBlock();
            UUID island = islandAt(b);
            if (island == null) {
                return;
            }
            if (byBlock.containsKey(b.getType()) || byPlant.containsKey(b.getType())) {
                boneMealed.remember(island, b.getX(), b.getY(), b.getZ(), "bone_meal");
            }
            for (BlockState st : event.getBlocks()) {
                if (byBlock.containsKey(st.getType()) || byPlant.containsKey(st.getType())) {
                    boneMealed.remember(island, st.getX(), st.getY(), st.getZ(), "bone_meal");
                }
            }
        }

        /** Growth multiplier (extra stages) and the grown memory. Runs after the fuel pause (HIGH) and protection. */
        @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
        public void onGrow(BlockGrowEvent event) {
            BlockState next = event.getNewState();
            Material type = next.getType();
            CropCatalog.Crop harvest = byBlock.get(type);
            CropCatalog.Crop plant = byPlant.get(type);
            if (harvest == null && plant == null) {
                return;
            }
            Block b = event.getBlock();
            UUID island = islandAt(b);
            if (island == null) {
                return;
            }
            if (harvest != null && harvest.kind() != CropCatalog.Kind.AGE) {
                grown.remember(island, b.getX(), b.getY(), b.getZ(), harvest.id());
                boneMealed.forget(island, b.getX(), b.getY(), b.getZ());
                return;
            }
            CropCatalog.Crop crop = harvest != null ? harvest : plant;
            BlockData data = next.getBlockData();
            if (crop.kind() == CropCatalog.Kind.STACK || !(data instanceof Ageable age) || data instanceof Bisected
                    || age.getAge() >= age.getMaximumAge()) {
                return; // columns grow by new blocks; two-block plants (pitcher) stay vanilla so both halves match
            }
            int extra = FarmingRules.extraStages(growthMultiplier(island), ThreadLocalRandom.current().nextDouble());
            if (extra > 0) {
                age.setAge(FarmingRules.boostedAge(age.getAge(), extra, age.getMaximumAge()));
                next.setBlockData(age);
            }
        }

        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void onBreak(BlockBreakEvent event) {
            last = null;
            Block b = event.getBlock();
            Material type = b.getType();
            CropCatalog.Crop crop = byBlock.get(type);
            if (crop == null) {
                if (byPlant.containsKey(type)) {
                    UUID island = islandAt(b);
                    if (island != null) {
                        boneMealed.forget(island, b.getX(), b.getY(), b.getZ());
                    }
                }
                return;
            }
            UUID island = islandAt(b);
            if (island == null) {
                return;
            }
            boolean meal = boneMealed.take(island, b.getX(), b.getY(), b.getZ()) != null;
            int count;
            switch (crop.kind()) {
                case AGE -> count = ripeByAge(b.getBlockData()) ? 1 : 0;
                case FRUIT -> count = grown.take(island, b.getX(), b.getY(), b.getZ()) != null ? 1 : 0;
                default -> {
                    // A column: the broken block and every grown block of the same type above it (they break with it).
                    int n = 0;
                    World w = b.getWorld();
                    for (int y = b.getY(), i = 0; i < MAX_COLUMN && y < w.getMaxHeight(); y++, i++) {
                        if (w.getBlockAt(b.getX(), y, b.getZ()).getType() != type) {
                            break;
                        }
                        if (grown.take(island, b.getX(), y, b.getZ()) != null) {
                            n++;
                        }
                    }
                    count = n;
                }
            }
            boolean ripe = count > 0;
            last = new LastBreak(b.getWorld().getUID(), b.getX(), b.getY(), b.getZ(), island, ripe);
            harvested(event.getPlayer(), b, island, crop, count, ripe, meal);
        }

        /** Harvest Yield on the drops of the block just broken (fires right after its break event). */
        @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
        public void onDrops(BlockDropItemEvent event) {
            LastBreak l = last;
            last = null;
            Block b = event.getBlock();
            if (l == null || !l.ripe() || l.x() != b.getX() || l.y() != b.getY() || l.z() != b.getZ()
                    || !l.world().equals(b.getWorld().getUID())) {
                return;
            }
            CropCatalog.Crop crop = byBlock.get(event.getBlockState().getType());
            if (crop == null || event.getItems().isEmpty()) {
                return;
            }
            List<ItemStack> stacks = new java.util.ArrayList<>();
            for (Item item : event.getItems()) {
                stacks.add(item.getItemStack());
            }
            applyYield(l.island(), crop, stacks);
            List<Item> entities = event.getItems();
            for (int i = 0; i < entities.size(); i++) {
                entities.get(i).setItemStack(stacks.get(i));
            }
        }

        /** Harvest Yield on picked sweet berries (and other crops harvested by USE). */
        @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
        public void onPickYield(PlayerHarvestBlockEvent event) {
            Block b = event.getHarvestedBlock();
            CropCatalog.Crop crop = byBlock.get(b.getType());
            if (crop == null || !ripeByAge(b.getBlockData())) {
                return;
            }
            UUID island = islandAt(b);
            if (island != null) {
                applyYield(island, crop, event.getItemsHarvested());
            }
        }

        /** XP and quests for picked sweet berries (the bush stays, so it is harvested again later). */
        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void onPick(PlayerHarvestBlockEvent event) {
            Block b = event.getHarvestedBlock();
            CropCatalog.Crop crop = byBlock.get(b.getType());
            if (crop == null || crop.kind() != CropCatalog.Kind.AGE) {
                return;
            }
            UUID island = islandAt(b);
            if (island == null) {
                return;
            }
            boolean ripe = ripeByAge(b.getBlockData());
            boolean meal = boneMealed.take(island, b.getX(), b.getY(), b.getZ()) != null;
            harvested(event.getPlayer(), b, island, crop, 1, ripe, meal);
        }
    }
}
