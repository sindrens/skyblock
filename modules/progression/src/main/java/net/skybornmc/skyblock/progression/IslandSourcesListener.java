package net.skybornmc.skyblock.progression;

import net.skybornmc.skyblock.common.Core;
import net.skybornmc.skyblock.common.island.IslandService;
import net.skybornmc.skyblock.common.progression.Pillar;
import net.skybornmc.skyblock.common.skills.SkillService;
import net.skybornmc.skyblock.common.skills.SkillType;
import net.skybornmc.skyblock.common.stacking.StackingService;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.world.WorldUnloadEvent;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Pillar XP from island actions that are not skill XP yet (PHASE-5 M5.1 source table), all on the player's <b>own</b>
 * island and only for stored members in survival/adventure (never coop players or visitors):
 * <ul>
 *   <li>Farming: fully grown crops ({@code island-sources.crops.xp}); paused while the Farming skill pays (Phase 7);</li>
 *   <li>Foraging: logs, except logs a player placed there recently ({@link PlacedBlocks});</li>
 *   <li>Combat: spawner mobs killed by the player's own hit or arrow. Fall, lava and other deaths give 0, and stacked
 *       batch kills ({@code StackKillEvent}) are never counted (owner P5-7).</li>
 * </ul>
 * Every handler is O(1) and runs at MONITOR after protection, so cancelled actions never count.
 */
final class IslandSourcesListener implements Listener {

    private final Core core;
    private final PillarsConfig config;
    private final PillarManager pillars;
    private final Map<UUID, PlacedBlocks> placed = new HashMap<>();
    private Map<Material, Double> cropXp = new EnumMap<>(Material.class);
    private Map<EntityType, Double> mobXp = new EnumMap<>(EntityType.class);
    private Set<String> causes = Set.of();
    private int placedMemory = -1;

    IslandSourcesListener(Core core, PillarsConfig config, PillarManager pillars) {
        this.core = core;
        this.config = config;
        this.pillars = pillars;
        configure();
    }

    void configure() {
        java.util.function.Consumer<String> problem = p -> core.plugin().getLogger().warning(p);
        Map<Material, Double> crops = new EnumMap<>(Material.class);
        PillarRules.numbers(config.cropXp, "island-sources.crops.xp", problem).forEach((id, xp) -> {
            Material m = Material.matchMaterial(id);
            if (m == null || !m.isBlock() || !(m.createBlockData() instanceof Ageable)) {
                problem.accept("pillars.yml → island-sources.crops.xp: '" + id + "' is not a crop block with a growth age (skipped)");
            } else {
                crops.put(m, xp);
            }
        });
        cropXp = crops;
        Map<EntityType, Double> mobs = new EnumMap<>(EntityType.class);
        PillarRules.numbers(config.spawnerMobXp, "island-sources.spawner-kills.xp-per-mob", problem).forEach((id, xp) -> {
            NamespacedKey key = NamespacedKey.fromString(id);
            EntityType type = key == null ? null : Registry.ENTITY_TYPE.get(key);
            if (type == null) {
                problem.accept("pillars.yml → island-sources.spawner-kills.xp-per-mob: '" + id + "' is not an entity (skipped)");
            } else {
                mobs.put(type, xp);
            }
        });
        mobXp = mobs;
        causes = PillarRules.upperSet(config.spawnerKillCauses);
        if (placedMemory != config.placedMemory) {
            placed.clear();
            placedMemory = config.placedMemory;
        }
    }

    // ------------------------------------------------------------------ helpers

    private IslandService islands() {
        return core.services().optional(IslandService.class).orElse(null);
    }

    /** The island at {@code location} when {@code player} is a counting member of it (own island, survival). */
    private UUID ownIslandAt(Player player, Location location) {
        if (player == null || location == null) {
            return null;
        }
        GameMode mode = player.getGameMode();
        if (mode == GameMode.CREATIVE || mode == GameMode.SPECTATOR) {
            return null;
        }
        IslandService islands = islands();
        if (islands == null || location.getWorld() == null || !islands.isIslandWorld(location.getWorld())) {
            return null;
        }
        Optional<IslandService.IslandInfo> here = islands.islandAt(location);
        if (here.isEmpty()) {
            return null;
        }
        UUID id = here.get().id();
        Optional<IslandService.IslandInfo> own = islands.islandOf(player.getUniqueId());
        if (own.isEmpty() || !own.get().id().equals(id) || !islands.countsForIsland(id, player.getUniqueId())) {
            return null;
        }
        return id;
    }

    private PlacedBlocks placed(UUID island) {
        return placed.computeIfAbsent(island, k -> new PlacedBlocks(config.placedMemory));
    }

    // ------------------------------------------------------------------ blocks

    /** Remembers logs placed on islands (by anyone), so breaking them again gives nothing. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Block block = event.getBlockPlaced();
        if (!config.logsEnabled || !Tag.LOGS.isTagged(block.getType())) {
            return;
        }
        IslandService islands = islands();
        if (islands == null || !islands.isIslandWorld(block.getWorld())) {
            return;
        }
        islands.islandInSlot(block.getLocation()).ifPresent(island -> placed(island.id()).placed(block.getX(), block.getY(), block.getZ()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        Material type = block.getType();
        boolean log = config.logsEnabled && Tag.LOGS.isTagged(type);
        Double crop = config.cropsEnabled ? cropXp.get(type) : null;
        if (!log && crop == null) {
            return;
        }
        Player player = event.getPlayer();
        UUID island = ownIslandAt(player, block.getLocation());
        if (log) {
            IslandService islands = islands();
            // Forget placed logs even when the breaker doesn't count (a visitor breaking it must not leave it "natural").
            UUID slot = island != null ? island : islands == null ? null
                    : islands.islandInSlot(block.getLocation()).map(IslandService.IslandInfo::id).orElse(null);
            boolean wasPlaced = slot != null && placed(slot).brokePlaced(block.getX(), block.getY(), block.getZ());
            if (island != null && !wasPlaced && config.logXp > 0) {
                pillars.gain(island, Pillar.FORAGING, config.logXp, cause(player), "island_log", null);
            }
            return;
        }
        if (island == null || !ripe(block.getBlockData()) || cropsPaused()) {
            return;
        }
        if (crop > 0) {
            pillars.gain(island, Pillar.FARMING, crop, cause(player), "island_crop", null);
        }
    }

    private static boolean ripe(BlockData data) {
        return data instanceof Ageable ageable && ageable.getAge() >= ageable.getMaximumAge();
    }

    /** Crops pause while the Farming skill gives XP itself (its base XP then feeds the pillar). */
    private boolean cropsPaused() {
        return config.cropsPauseWithFarmingSkill && core.services().optional(SkillService.class)
                .map(s -> s.available(SkillType.FARMING)).orElse(false);
    }

    // ------------------------------------------------------------------ spawner kills

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onKill(EntityDeathEvent event) {
        if (!config.spawnerKillsEnabled) {
            return;
        }
        LivingEntity entity = event.getEntity();
        Player killer = entity.getKiller();
        if (entity instanceof Player || killer == null) {
            return;
        }
        boolean spawner = entity.fromMobSpawner() || core.services().optional(StackingService.class)
                .map(s -> s.fromStackedSpawner(entity)).orElse(false);
        if (!spawner) {
            return;
        }
        EntityDamageEvent last = entity.getLastDamageCause();
        boolean byKiller = last instanceof EntityDamageByEntityEvent hit && killer.getUniqueId().equals(source(hit.getDamager()));
        if (!PillarRules.killCounts(last == null ? null : last.getCause().name(), byKiller, causes)) {
            return;
        }
        UUID island = ownIslandAt(killer, entity.getLocation());
        double xp = mobXp.getOrDefault(entity.getType(), config.spawnerKillXp);
        if (island != null && xp > 0) {
            pillars.gain(island, Pillar.COMBAT, xp, cause(killer), "island_spawner_kill", null);
        }
    }

    /** The entity that really dealt the damage: the shooter of a projectile, else the damager. */
    private static UUID source(Entity damager) {
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter) {
            return shooter.getUniqueId();
        }
        return damager == null ? null : damager.getUniqueId();
    }

    private UUID cause(Player player) {
        return net.skybornmc.skyblock.common.profile.ProfileService.of(core.services()).profileKey(player);
    }

    /** Drops the placed-log memory when the islands world unloads. */
    @EventHandler
    public void onUnload(WorldUnloadEvent event) {
        IslandService islands = islands();
        if (islands != null && islands.isIslandWorld(event.getWorld())) {
            placed.clear();
        }
    }
}
