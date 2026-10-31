package net.skybornmc.skyblock.production;

import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.island.IslandService;
import net.skybornmc.skyblock.common.item.ItemDefinition;
import net.skybornmc.skyblock.common.item.ItemType;
import net.skybornmc.skyblock.common.item.LoreBuilder;
import net.skybornmc.skyblock.common.item.Rarity;
import net.skybornmc.skyblock.common.production.GeneratorOutput;
import net.skybornmc.skyblock.common.production.GeneratorService;
import net.skybornmc.skyblock.common.production.IslandGatherEvent;
import net.skybornmc.skyblock.common.progression.Pillar;
import net.skybornmc.skyblock.common.progression.ProgressionService;
import net.skybornmc.skyblock.common.util.Scheduler;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.EntityBlockFormEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Custom island generators (PHASE-7-PRODUCTION.md M7.2; owner V4, PD6–PD9). Two listeners:
 * <ul>
 *   <li><b>The generator</b> (only while {@code generators.yml → enabled}): a lava + water generator on an island that forms
 *       cobblestone or stone forms a block rolled from the island's Generator level instead ({@code BlockFormEvent},
 *       priority HIGH, after the border check). Generator level = the Generator upgrade's value (Phase 5,
 *       {@code ProgressionService#upgradeEffectValue(island, "generator")}). Lag guard: at most
 *       {@code lag-guard.max-rolls-per-second} rolls per island, then the plain block; an Asleep island (Island Fuel on)
 *       forms the plain block too. Made blocks are remembered per island ({@link GeneratorMemory}); mining one gives
 *       Mining pillar XP at 25% of the matching Depths ore (no Mining skill XP) and fires {@link IslandGatherEvent} for the
 *       enchants module's books.</li>
 *   <li><b>The custom-ore guard</b> (always, so a switch-off never turns Ashstone blocks into a loophole): a custom ore block
 *       on an island always drops exactly one custom item (also with Silk Touch), can't be placed by players outside
 *       creative, survives explosions and can't be changed by mobs.</li>
 * </ul>
 * Vanilla ore blocks drop their vanilla items (sell.yml island prices). Per event: a set lookup, one island lookup and a few
 * map operations; no scans, no scheduling.
 */
final class Generators implements GeneratorService {

    static final String SOURCE = "generator";

    private final ProductionModule m;
    private final GeneratorSettings s;
    private final RollLimiter limiter;
    private final GeneratorMemory memory;
    private final Guard guard = new Guard();
    private final Engine engine = new Engine();
    private final List<String> registeredItems = new ArrayList<>();

    private volatile GeneratorTable table = GeneratorTable.parse(List.of(), List.of(), p -> { });
    private Set<Material> formBlocks = EnumSet.noneOf(Material.class);
    /** Ore id → block (kept block: absent). */
    private Map<String, Material> blockOf = Map.of();
    /** Block → ore (custom and vanilla ores, not the kept block). */
    private Map<Material, GeneratorTable.Ore> oreByBlock = new EnumMap<>(Material.class);
    /** Block → custom ore whose item was registered. */
    private Map<Material, GeneratorTable.Ore> customByBlock = new EnumMap<>(Material.class);
    private Scheduler.Task pruneTask;
    private boolean guardOn;
    private boolean running;

    Generators(ProductionModule module, GeneratorSettings settings) {
        this.m = module;
        this.s = settings;
        this.limiter = new RollLimiter(settings.maxRollsPerSecond);
        this.memory = new GeneratorMemory(settings.memoryPerIsland);
    }

    // ================================================================== lifecycle

    /** (Re)reads the table, (re)registers the custom ore items and turns the generator on or off. */
    void apply() {
        GeneratorTable t = GeneratorTable.parse(s.ores, s.levels, p -> m.core.plugin().getLogger().warning(p));
        Map<String, Material> blocks = new java.util.HashMap<>();
        Map<Material, GeneratorTable.Ore> ores = new EnumMap<>(Material.class);
        for (GeneratorTable.Ore ore : t.ores().values()) {
            if (ore.keep()) {
                continue;
            }
            Material block = Material.matchMaterial(ore.block());
            if (block == null || !block.isBlock() || block.isAir()) {
                m.core.plugin().getLogger().warning("generators.yml → ores." + ore.id() + ": '" + ore.block() + "' is not a block (it forms plain cobble)");
                continue;
            }
            if (ores.containsKey(block)) {
                m.core.plugin().getLogger().warning("generators.yml → ores." + ore.id() + ": block " + ore.block() + " is used twice (the first one counts)");
                continue;
            }
            blocks.put(ore.id(), block);
            ores.put(block, ore);
        }
        Set<Material> forms = EnumSet.noneOf(Material.class);
        for (String id : s.formBlocks) {
            Material f = Material.matchMaterial(id.trim());
            if (f == null || !f.isBlock()) {
                m.core.plugin().getLogger().warning("generators.yml → form-blocks: '" + id + "' is not a block (skipped)");
            } else {
                forms.add(f);
            }
        }
        Map<Material, GeneratorTable.Ore> custom = registerItems(t, blocks);
        // A custom ore whose item could not be registered is never formed (its block would drop vanilla items).
        Set<String> customOk = new java.util.HashSet<>();
        custom.values().forEach(o -> customOk.add(o.id()));
        blocks.keySet().removeIf(id -> t.ores().get(id).custom() && !customOk.contains(id));
        ores.values().removeIf(o -> o.custom() && !customOk.contains(o.id()));
        table = t;
        blockOf = Map.copyOf(blocks);
        oreByBlock = ores;
        formBlocks = forms;
        customByBlock = custom;
        limiter.max(s.maxRollsPerSecond);
        memory.capacity(s.memoryPerIsland);
        if (!guardOn) {
            Bukkit.getPluginManager().registerEvents(guard, m.core.plugin());
            guardOn = true;
        }
        if (s.enabled && !running) {
            start();
        } else if (!s.enabled && running) {
            stop();
            m.core.plugin().getLogger().info("Production: custom generators switched off; generators are vanilla again.");
        } else if (running) {
            GeneratorOutput.set(oreByBlock.keySet());
        }
        if (!s.enabled && !running) {
            m.core.plugin().getLogger().info("Production: custom generators are off (generators.yml → enabled: false).");
        }
    }

    private void start() {
        Bukkit.getPluginManager().registerEvents(engine, m.core.plugin());
        GeneratorOutput.set(oreByBlock.keySet());
        m.core.services().register(ProductionModule.ID, GeneratorService.class, this);
        pruneTask = m.core.scheduler().repeat("production:generator-prune", 1_200L, 1_200L, () -> limiter.prune(System.currentTimeMillis()));
        running = true;
        m.core.plugin().getLogger().info("Production: custom generators are ON (levels 0-" + table.maxLevel()
                + (s.levelOverride >= 0 ? ", TEST OVERRIDE level " + s.levelOverride : "") + ").");
    }

    private void stop() {
        if (!running) {
            return;
        }
        running = false;
        HandlerList.unregisterAll(engine);
        GeneratorOutput.set(null);
        m.core.services().unregister(ProductionModule.ID, GeneratorService.class);
        if (pruneTask != null) {
            pruneTask.cancel();
            pruneTask = null;
        }
        memory.clear();
    }

    /** Module disable: everything off, items unregistered. */
    void shutdown() {
        stop();
        if (guardOn) {
            HandlerList.unregisterAll(guard);
            guardOn = false;
        }
        registeredItems.forEach(m.core.items()::unregister);
        registeredItems.clear();
    }

    void islandRemoved(UUID islandId) {
        memory.clear(islandId);
        limiter.forget(islandId);
    }

    boolean running() {
        return running;
    }

    // ================================================================== custom ore items

    private Map<Material, GeneratorTable.Ore> registerItems(GeneratorTable t, Map<String, Material> blocks) {
        registeredItems.forEach(m.core.items()::unregister);
        registeredItems.clear();
        Map<Material, GeneratorTable.Ore> out = new EnumMap<>(Material.class);
        for (GeneratorTable.Ore ore : t.ores().values()) {
            Material block = blocks.get(ore.id());
            if (!ore.custom() || block == null) {
                continue;
            }
            Material item = Material.matchMaterial(ore.item());
            if (item == null || !item.isItem() || item.isAir()) {
                m.core.plugin().getLogger().warning("generators.yml → ores." + ore.id() + ": item '" + ore.item() + "' is not an item (custom ore skipped)");
                continue;
            }
            if (!ItemDefinition.isValidId(ore.id()) || m.core.items().get(ore.id()).isPresent()) {
                m.core.plugin().getLogger().warning("generators.yml → ores." + ore.id() + ": the item id is invalid or used by another module (custom ore skipped)");
                continue;
            }
            Rarity rarity = rarity(ore.rarity());
            String name = LoreBuilder.fill(m.raw("generator.item.name"), Map.of("rarity", rarity.id(), "name", plainName(ore.id())));
            String description = m.raw("generator.items." + ore.id() + ".description");
            m.core.items().register(ItemDefinition.builder(ore.id(), item, ItemType.MATERIAL, rarity)
                    .name(name)
                    .subtitle(m.raw("generator.item.subtitle"))
                    .description(description.isEmpty() ? m.raw("generator.item.description") : description)
                    .build());
            registeredItems.add(ore.id());
            out.put(block, ore);
        }
        return out;
    }

    private Rarity rarity(String id) {
        try {
            return Rarity.valueOf(id.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            m.core.plugin().getLogger().warning("generators.yml → ores: rarity '" + id + "' is unknown, using common");
            return Rarity.COMMON;
        }
    }

    /** "Ashstone" (messages generator.items.&lt;id&gt;.name, else the id). */
    String plainName(String oreId) {
        String name = m.raw("generator.items." + oreId + ".name");
        return name.isEmpty() ? oreId : name;
    }

    // ================================================================== GeneratorService

    @Override
    public int generatorLevel(UUID islandId) {
        if (s.levelOverride >= 0) {
            return s.levelOverride;
        }
        double v = m.core.services().optional(ProgressionService.class).map(p -> p.upgradeEffectValue(islandId, "generator")).orElse(0.0);
        return v > 0 ? (int) Math.min(Integer.MAX_VALUE, Math.floor(v)) : 0;
    }

    @Override
    public int maxGeneratorLevel() {
        return table.maxLevel();
    }

    // ================================================================== helpers

    private Optional<IslandService> islands() {
        return m.islands();
    }

    private boolean islandWorld(World world) {
        return islands().map(i -> i.isIslandWorld(world)).orElse(false);
    }

    private boolean asleep(UUID islandId) {
        if (!s.plainWhenAsleep || !m.running()) {
            return false;
        }
        FuelEngine e = m.engine();
        return e != null && e.paused(islandId);
    }

    /** A stored member on their own island in survival/adventure (never coop players or visitors). */
    private static boolean counts(IslandService islands, Player player, UUID islandId) {
        GameMode mode = player.getGameMode();
        if (mode == GameMode.CREATIVE || mode == GameMode.SPECTATOR) {
            return false;
        }
        UUID uuid = player.getUniqueId();
        return islands.islandOf(uuid).map(i -> i.id().equals(islandId)).orElse(false) && islands.countsForIsland(islandId, uuid);
    }

    // ================================================================== the generator

    private final class Engine implements Listener {

        @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
        public void onForm(BlockFormEvent event) {
            if (event instanceof EntityBlockFormEvent) {
                return; // frost walker, snow golems: not a generator
            }
            BlockState formed = event.getNewState();
            if (!formBlocks.contains(formed.getType())) {
                return;
            }
            Block b = event.getBlock();
            IslandService islands = islands().orElse(null);
            if (islands == null || !islands.isIslandWorld(b.getWorld())) {
                return;
            }
            UUID id = islands.islandAt(b.getLocation()).map(IslandService.IslandInfo::id).orElse(null);
            if (id == null) {
                return;
            }
            GeneratorTable t = table;
            int level = generatorLevel(id);
            GeneratorRules.Form form = GeneratorRules.form(t.rolls(level), asleep(id), () -> limiter.tryRoll(id, System.currentTimeMillis()));
            Material target = null;
            String oreId = null;
            if (form == GeneratorRules.Form.ROLL) {
                GeneratorTable.Ore ore = t.roll(level, ThreadLocalRandom.current().nextDouble());
                target = ore.keep() ? null : blockOf.get(ore.id());
                oreId = ore.id();
            }
            if (target == null) {
                memory.forget(id, b.getX(), b.getY(), b.getZ());
                return;
            }
            formed.setType(target);
            memory.remember(id, b.getX(), b.getY(), b.getZ(), oreId);
        }

        /** A placed block is never generator-made, whatever was remembered there. */
        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void onPlace(BlockPlaceEvent event) {
            Block b = event.getBlockPlaced();
            if (!islandWorld(b.getWorld())) {
                return;
            }
            islands().flatMap(i -> i.islandInSlot(b.getLocation())).ifPresent(i -> memory.forget(i.id(), b.getX(), b.getY(), b.getZ()));
        }

        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void onBreak(BlockBreakEvent event) {
            Block b = event.getBlock();
            GeneratorTable.Ore ore = oreByBlock.get(b.getType());
            if (ore == null || !ore.ore()) {
                return;
            }
            IslandService islands = islands().orElse(null);
            if (islands == null || !islands.isIslandWorld(b.getWorld())) {
                return;
            }
            UUID id = islands.islandAt(b.getLocation()).map(IslandService.IslandInfo::id).orElse(null);
            if (id == null) {
                return;
            }
            String made = memory.take(id, b.getX(), b.getY(), b.getZ());
            boolean generatorMade = ore.id().equals(made);
            Player player = event.getPlayer();
            if (!counts(islands, player, id)) {
                return; // coop players and visitors add nothing (rule 8)
            }
            double xp = GeneratorRules.pillarXp(ore, generatorMade, true, s.pillarXpPercent);
            if (xp > 0) {
                m.core.services().optional(ProgressionService.class)
                        .ifPresent(p -> p.addPillarXp(id, Pillar.MINING, xp, SOURCE, Actor.system(ProductionModule.ID)));
            }
            Bukkit.getPluginManager().callEvent(new IslandGatherEvent(player, b, id, ore.id(), ore.tier(), generatorMade));
        }
    }

    // ================================================================== the custom-ore guard

    private final class Guard implements Listener {

        /** One custom item instead of the block's vanilla drops (gilded blackstone, gold nuggets ...), Silk Touch too. */
        @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
        public void onDrops(BlockDropItemEvent event) {
            GeneratorTable.Ore ore = customByBlock.get(event.getBlockState().getType());
            if (ore == null || event.getItems().isEmpty() || !islandWorld(event.getBlock().getWorld())) {
                return; // no drops = the tool can't harvest it (vanilla rule), nothing to replace
            }
            ItemStack drop;
            try {
                drop = m.core.items().create(ore.id(), 1, SOURCE, null);
            } catch (IllegalArgumentException e) {
                return;
            }
            List<Item> items = event.getItems();
            items.getFirst().setItemStack(drop);
            for (int i = items.size() - 1; i > 0; i--) {
                items.remove(i);
            }
        }

        /** Custom ore blocks only come from the generator (else a bought or silk-touched block would print money). */
        @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
        public void onPlace(BlockPlaceEvent event) {
            if (!customByBlock.containsKey(event.getBlockPlaced().getType()) || event.getPlayer().getGameMode() == GameMode.CREATIVE
                    || !islandWorld(event.getBlockPlaced().getWorld())) {
                return;
            }
            event.setCancelled(true);
            m.msg.send(event.getPlayer(), "generator.cant-place");
        }

        @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
        public void onEntityExplode(EntityExplodeEvent event) {
            if (!customByBlock.isEmpty() && islandWorld(event.getLocation().getWorld())) {
                event.blockList().removeIf(b -> customByBlock.containsKey(b.getType()));
            }
        }

        @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
        public void onBlockExplode(BlockExplodeEvent event) {
            if (!customByBlock.isEmpty() && islandWorld(event.getBlock().getWorld())) {
                event.blockList().removeIf(b -> customByBlock.containsKey(b.getType()));
            }
        }

        /** Withers and other mobs don't break or take custom ore blocks (their drops would be vanilla). */
        @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
        public void onEntityChange(EntityChangeBlockEvent event) {
            if (customByBlock.containsKey(event.getBlock().getType()) && islandWorld(event.getBlock().getWorld())) {
                event.setCancelled(true);
            }
        }
    }
}
