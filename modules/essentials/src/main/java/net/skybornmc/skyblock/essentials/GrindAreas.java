package net.skybornmc.skyblock.essentials;

import net.skybornmc.skyblock.common.Core;
import net.skybornmc.skyblock.common.command.CommandSpec;
import net.skybornmc.skyblock.common.teleport.TeleportOptions;
import net.skybornmc.skyblock.common.teleport.TeleportReason;
import net.skybornmc.skyblock.common.teleport.TeleportService;
import net.skybornmc.skyblock.common.text.ModuleMessages;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerInteractEvent;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Owner 3 Nov 2026 ("a temporary big /farm with wheat so I can use the hoe, and /mine and /dungeon ... just flat and
 * simple"): one flat world {@code grind.world} with three areas, built once on the first start:
 * <ul>
 *   <li><b>Farm</b> around x 0: ripe wheat on farmland; a broken crop is ripe again after {@code grind.farm-regrow-seconds}.</li>
 *   <li><b>Mine</b> around x 1000: a pit of stone and ores; a mined block comes back after {@code grind.mine-regen-seconds}.</li>
 *   <li><b>Dungeon</b> around x 2000: a walled arena where zombies, skeletons and spiders spawn while players are inside.</li>
 * </ul>
 * Everything else in the world is protected (creative staff can build). Temporary until the grind areas redesign
 * ({@code docs/planning/TOOLS_AREAS_REDESIGN.md}).
 */
final class GrindAreas extends CommandGroup implements Listener {

    static final int FARM_X = 0;
    static final int MINE_X = 1000;
    static final int DUNGEON_X = 2000;
    static final int FARM_HALF = 30;
    static final int MINE_HALF = 20;
    static final int MINE_DEPTH = 6;
    static final int DUNGEON_HALF = 20;

    private static final List<Material> ORES = List.of(Material.STONE, Material.STONE, Material.STONE, Material.STONE,
            Material.STONE, Material.STONE, Material.COAL_ORE, Material.COAL_ORE, Material.COPPER_ORE, Material.IRON_ORE,
            Material.IRON_ORE, Material.GOLD_ORE, Material.REDSTONE_ORE, Material.LAPIS_ORE, Material.DIAMOND_ORE,
            Material.EMERALD_ORE);
    private static final List<EntityType> MOBS = List.of(EntityType.ZOMBIE, EntityType.ZOMBIE, EntityType.SKELETON, EntityType.SPIDER);

    private final TeleportService teleports;
    private World world;
    private int base; // y of the ground surface
    private boolean spawningOurs;

    GrindAreas(Core core, EssentialsConfig config, ModuleMessages msg, TeleportService teleports) {
        super(core, config, msg);
        this.teleports = teleports;
    }

    @Override
    void register() {
        if (!config.grindEnabled) {
            return;
        }
        core.commands().register(CommandSpec.command("farm").description("Teleport to the wheat farm").category("Teleport")
                .permission(Perms.SPAWN).playerOnly().executes(ctx -> go(ctx.player(), "farm")));
        core.commands().register(CommandSpec.command("mine").description("Teleport to the mine").category("Teleport")
                .permission(Perms.SPAWN).playerOnly().executes(ctx -> go(ctx.player(), "mine")));
        core.commands().register(CommandSpec.command("dungeon").description("Teleport to the dungeon arena").category("Teleport")
                .permission(Perms.SPAWN).playerOnly().executes(ctx -> go(ctx.player(), "dungeon")));
    }

    /** After enable: the world (created by the worlds module from worlds.yml), the areas built once, the dungeon spawner. */
    void start() {
        if (!config.grindEnabled) {
            return;
        }
        core.scheduler().later("essentials:grind", 40L, () -> {
            world = Bukkit.getWorld(config.grindWorld);
            if (world == null) {
                core.plugin().getLogger().warning("Essentials: the grind world '" + config.grindWorld
                        + "' is not loaded (add it to worlds.yml); /farm, /mine and /dungeon are off.");
                return;
            }
            base = config.grindY;
            if (world.getBlockAt(FARM_X, base, 0).getType() != Material.FARMLAND) {
                build();
            }
            core.scheduler().repeat("essentials:grind-dungeon", 100L, 100L, this::dungeonTick);
        });
    }

    private void go(Player player, String area) {
        if (world == null) {
            msg.send(player, "grind.missing");
            return;
        }
        int x = switch (area) {
            case "mine" -> MINE_X;
            case "dungeon" -> DUNGEON_X;
            default -> FARM_X;
        };
        int half = switch (area) {
            case "mine" -> MINE_HALF;
            case "dungeon" -> DUNGEON_HALF;
            default -> FARM_HALF;
        };
        Location to = new Location(world, x + 0.5, base + 1, -half - 3 + 0.5, 0, 0);
        teleports.request(player, to, TeleportReason.SPAWN, TeleportOptions.to(area,
                () -> msg.send(player, "grind.arrived." + area)));
    }

    // ------------------------------------------------------------------ building (once)

    private void build() {
        for (int cx : new int[]{FARM_X, MINE_X, DUNGEON_X}) { // a landing pad in front of each area (the world is void)
            int half = cx == FARM_X ? FARM_HALF : cx == MINE_X ? MINE_HALF : DUNGEON_HALF;
            for (int x = -3; x <= 3; x++) {
                for (int z = -half - 6; z <= -half - 1; z++) {
                    world.getBlockAt(cx + x, base, z).setType(Material.POLISHED_ANDESITE, false);
                }
            }
        }
        for (int x = -FARM_HALF; x <= FARM_HALF; x++) {
            for (int z = -FARM_HALF; z <= FARM_HALF; z++) {
                world.getBlockAt(FARM_X + x, base, z).setType(Material.FARMLAND, false);
                ripe(world.getBlockAt(FARM_X + x, base + 1, z));
            }
        }
        for (int x = -MINE_HALF; x <= MINE_HALF; x++) {
            for (int z = -MINE_HALF; z <= MINE_HALF; z++) {
                for (int d = 0; d < MINE_DEPTH; d++) {
                    world.getBlockAt(MINE_X + x, base - d, z).setType(randomOre(), false);
                }
                world.getBlockAt(MINE_X + x, base - MINE_DEPTH, z).setType(Material.BEDROCK, false);
            }
        }
        for (int x = -DUNGEON_HALF; x <= DUNGEON_HALF; x++) {
            for (int z = -DUNGEON_HALF; z <= DUNGEON_HALF; z++) {
                world.getBlockAt(DUNGEON_X + x, base, z).setType(Material.STONE_BRICKS, false);
                boolean wall = Math.abs(x) == DUNGEON_HALF || Math.abs(z) == DUNGEON_HALF;
                if (wall && !(z == -DUNGEON_HALF && Math.abs(x) <= 1)) {
                    for (int y = 1; y <= 4; y++) {
                        world.getBlockAt(DUNGEON_X + x, base + y, z).setType(Material.DEEPSLATE_BRICKS, false);
                    }
                }
            }
        }
        core.plugin().getLogger().info("Essentials: built the farm, mine and dungeon in " + world.getName());
    }

    private static Material randomOre() {
        return ORES.get(ThreadLocalRandom.current().nextInt(ORES.size()));
    }

    private static void ripe(Block crop) {
        crop.setType(Material.WHEAT, false);
        if (crop.getBlockData() instanceof Ageable a) {
            a.setAge(a.getMaximumAge());
            crop.setBlockData(a, false);
        }
    }

    // ------------------------------------------------------------------ areas

    private boolean in(Block b, int cx, int half) {
        return b.getWorld().equals(world) && Math.abs(b.getX() - cx) <= half && Math.abs(b.getZ()) <= half;
    }

    private boolean staff(Player p) {
        return p.getGameMode() == GameMode.CREATIVE;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block b = event.getBlock();
        if (world == null || !b.getWorld().equals(world) || staff(event.getPlayer())) {
            return;
        }
        if (in(b, FARM_X, FARM_HALF) && b.getY() == base + 1 && b.getType() == Material.WHEAT) {
            core.scheduler().later("essentials:grind-regrow", config.grindFarmRegrowSeconds * 20L, () -> {
                if (b.getType().isAir() && b.getRelative(0, -1, 0).getType() == Material.FARMLAND) {
                    ripe(b);
                }
            });
            return;
        }
        if (in(b, MINE_X, MINE_HALF) && b.getY() <= base && b.getY() > base - MINE_DEPTH && b.getType() != Material.BEDROCK) {
            core.scheduler().later("essentials:grind-regen", config.grindMineRegenSeconds * 20L, () -> {
                if (b.getType().isAir()) {
                    b.setType(randomOre(), false);
                }
            });
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (world != null && event.getBlock().getWorld().equals(world) && !staff(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucket(PlayerBucketEmptyEvent event) {
        if (world != null && event.getBlock().getWorld().equals(world) && !staff(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    /** No trampling the farm. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onTrample(PlayerInteractEvent event) {
        if (world != null && event.getAction() == Action.PHYSICAL && event.getClickedBlock() != null
                && event.getClickedBlock().getWorld().equals(world) && event.getClickedBlock().getType() == Material.FARMLAND) {
            event.setCancelled(true);
        }
    }

    /** Only the dungeon spawner makes mobs here. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent event) {
        if (world != null && event.getLocation().getWorld().equals(world) && !spawningOurs
                && event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.CUSTOM
                && event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.COMMAND) {
            event.setCancelled(true);
        }
    }

    /** Every 5 s: while players are in the arena, top it up to {@code grind.dungeon-mobs} monsters. */
    private void dungeonTick() {
        if (world == null) {
            return;
        }
        Location center = new Location(world, DUNGEON_X, base + 1, 0);
        boolean someone = world.getPlayers().stream().anyMatch(p -> p.getGameMode() != GameMode.SPECTATOR
                && Math.abs(p.getLocation().getBlockX() - DUNGEON_X) <= DUNGEON_HALF && Math.abs(p.getLocation().getBlockZ()) <= DUNGEON_HALF);
        if (!someone) {
            return;
        }
        long alive = center.getNearbyEntitiesByType(Monster.class, DUNGEON_HALF + 2).size();
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (long i = alive; i < config.grindDungeonMobs && i < alive + 3; i++) {
            Location at = new Location(world, DUNGEON_X + r.nextInt(-DUNGEON_HALF + 3, DUNGEON_HALF - 2) + 0.5, base + 1,
                    r.nextInt(-DUNGEON_HALF + 3, DUNGEON_HALF - 2) + 0.5);
            spawningOurs = true;
            try {
                world.spawnEntity(at, MOBS.get(r.nextInt(MOBS.size())), CreatureSpawnEvent.SpawnReason.CUSTOM);
            } finally {
                spawningOurs = false;
            }
        }
    }

}
