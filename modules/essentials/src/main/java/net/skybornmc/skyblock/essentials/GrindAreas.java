package net.skybornmc.skyblock.essentials;

import net.skybornmc.skyblock.common.Core;
import net.skybornmc.skyblock.common.command.CommandSpec;
import net.skybornmc.skyblock.common.grind.GrindBreakEvent;
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
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
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
final class GrindAreas extends CommandGroup implements Listener, net.skybornmc.skyblock.common.grind.GrindProgress {

    static final int FARM_X = 0;
    static final int MINE_X = 1000;
    static final int DUNGEON_X = 2000;
    static final int FARM_HALF = 30;
    static final int MINE_HALF = 20;
    static final int MINE_DEPTH = 6;
    static final int DUNGEON_HALF = 20;
    /** Owner 6 Nov 2026: the /fish pond (inside the 6000-block world border). */
    static final int FISH_X = 2600;
    static final int FISH_HALF = 14;
    private static final org.bukkit.NamespacedKey FISHED = net.skybornmc.skyblock.common.item.ItemKeys.key("grind_fished");
    private volatile List<FishRules.Water> waters = List.of();
    private final java.util.Map<java.util.UUID, FishRules.Guard> fishGuards = new java.util.concurrent.ConcurrentHashMap<>();
    /** Owner 6 Nov 2026: the /pve arena. */
    private final PveArena pve = new PveArena(this);

    /** Farm crops in unlock order: block, item it drops, wheat broken in total to unlock it (feedback 3 Nov 2026). */
    record Crop(Material block, Material item, long unlockAt, long bonus) {
    }

    /** {@code bonus}: money paid per harvest on top of the crop (the Farm pays better than an island farm). */
    static final List<Crop> CROPS = List.of(new Crop(Material.WHEAT, Material.WHEAT, 0, 600), new Crop(Material.CARROTS, Material.CARROT, 3_000, 900),
            new Crop(Material.POTATOES, Material.POTATO, 12_000, 1200), new Crop(Material.BEETROOTS, Material.BEETROOT, 40_000, 1600), // owner 5 Nov: carrots take much longer
            new Crop(Material.NETHER_WART, Material.NETHER_WART, 60_000, 30000)); // 7 Nov 2026 (batch 3): a fifth crop that sells for more
    private static final org.bukkit.NamespacedKey FARMED = net.skybornmc.skyblock.common.item.ItemKeys.key("grind_farmed");

    /** Pure: the crop a player with {@code farmed} harvests has unlocked ({@code unlocks}: grind.farm-unlocks). */
    static Crop cropFor(long farmed, List<Long> unlocks) {
        Crop out = CROPS.getFirst();
        for (int i = 0; i < CROPS.size(); i++) {
            if (farmed >= cropUnlockAt(i, unlocks)) {
                out = CROPS.get(i);
            }
        }
        return out;
    }

    /** Pure: harvests to unlock crop {@code index} (configured, else the built-in default). */
    static long cropUnlockAt(int index, List<Long> unlocks) {
        if (index <= 0) {
            return 0;
        }
        Long v = index < unlocks.size() ? unlocks.get(index) : null;
        return v == null || v < 0 ? CROPS.get(index).unlockAt() : v;
    }

    /** The crop at {@code farmed} with the configured thresholds (FX-09). */
    private Crop cropFor(long farmed) {
        return cropFor(farmed, farmUnlocks());
    }

    /** {@code grind.farm-unlocks} as longs (a broken entry falls back to the built-in threshold). */
    private List<Long> farmUnlocks() {
        List<Long> out = new ArrayList<>();
        for (Object o : config.grindFarmUnlocks) {
            try {
                out.add(o instanceof Number n ? n.longValue() : Long.parseLong(String.valueOf(o).trim().replace(",", "")));
            } catch (NumberFormatException e) {
                out.add(-1L);
            }
        }
        return out;
    }

    private static final List<EntityType> MOBS = List.of(EntityType.ZOMBIE, EntityType.ZOMBIE, EntityType.SKELETON, EntityType.SPIDER);

    private final TeleportService teleports;
    /** Bonus money waiting to be paid (main thread); paid every 3 s in one transaction per player. */
    private final java.util.Map<java.util.UUID, Long> pendingBonus = new java.util.HashMap<>();
    private World world;
    /** Owner 6 Nov 2026: the grind-area events (published as GrindEvents). */
    private GrindEventsImpl events;

    GrindEventsImpl events() {
        return events;
    }
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
        core.commands().register(CommandSpec.command("fish").description("Teleport to the fishing pond").category("Teleport")
                .permission(Perms.SPAWN).playerOnly().executes(ctx -> go(ctx.player(), "fish")));
        core.commands().register(CommandSpec.command("pve").description("Teleport to the PvE arena").category("Teleport")
                .permission(Perms.SPAWN).playerOnly().executes(ctx -> go(ctx.player(), "pve"))
                .sub(CommandSpec.sub("boss").description("Summon a ring's boss with a Boss Sigil (in the arena)").playerOnly()
                        .example("pve boss").executes(ctx -> pve.bosses.open(ctx.player()))));
        reloadFish();
        // Owner 3 Nov 2026 23:05: no dungeon (the arena stays built but has no command and no monsters).
        // Owner 4 Nov 2026 (plan A item 8): the /farm and /mine scoreboard shows the tier, the progress and this visit's bonus.
        core.placeholders().registerText(MODULE, "grind_area", p -> area(p));
        core.placeholders().registerText(MODULE, "grind_tier", p -> switch (area(p)) {
            case "Farm" -> name(cropFor(farmed(p)).item());
            case "Mine" -> name(oreTier(mined(p), mineUnlocks()).block());
            case "Fish" -> waterName(p, water(p));
            case "PvE" -> pve.tierText(p);
            default -> "";
        });
        core.placeholders().registerText(MODULE, "grind_progress", p -> progressText(p));
        core.placeholders().registerText(MODULE, "grind_bonus_visit", p -> net.skybornmc.skyblock.common.util.Format.money(
                visitBonus.getOrDefault(p.getUniqueId(), 0L)));
    }

    /** Bonus money paid since the player came to /farm or /mine (reset when they leave the grind world). */
    private final java.util.Map<java.util.UUID, Long> visitBonus = new java.util.concurrent.ConcurrentHashMap<>();

    /** "Farm" or "Mine" where the player stands in the grind world, "" elsewhere (the HUD picks its layout by this). */
    String area(Player p) {
        if (p == null || world == null || !p.getWorld().equals(world)) {
            return "";
        }
        int x = p.getLocation().getBlockX();
        if (Math.abs(x - MINE_X) <= MINE_HALF + 40) {
            return "Mine";
        }
        if (Math.abs(x - FISH_X) <= FISH_HALF + 40) {
            return "Fish";
        }
        if (Math.abs(x - PveArena.X) <= PveArena.HALF + 20) {
            return "PvE";
        }
        return Math.abs(x - FARM_X) <= FARM_HALF + 40 ? "Farm" : "";
    }

    /** "1,234 / 3,000" toward the next crop or ore, "Max" at the last one. */
    private String progressText(Player p) {
        String a = area(p);
        if (a.equals("Farm")) {
            long have = farmed(p);
            Crop crop = cropFor(have);
            int i = CROPS.indexOf(crop);
            return i + 1 >= CROPS.size() ? "Max" : net.skybornmc.skyblock.common.util.Format.number(have) + " / "
                    + net.skybornmc.skyblock.common.util.Format.number(cropUnlockAt(i + 1, farmUnlocks()));
        }
        if (a.equals("PvE")) {
            return pve.progressText(p);
        }
        if (a.equals("Fish")) {
            long have = fished(p);
            FishRules.Water next = FishRules.next(waters, water(p));
            return next == null ? "Max" : net.skybornmc.skyblock.common.util.Format.number(have) + " / "
                    + net.skybornmc.skyblock.common.util.Format.number(next.unlockAt());
        }
        if (a.equals("Mine")) {
            long have = mined(p);
            List<Long> unlocks = mineUnlocks();
            int i = ORE_TIERS.indexOf(oreTier(have, unlocks));
            return i + 1 >= ORE_TIERS.size() ? "Max" : net.skybornmc.skyblock.common.util.Format.number(have) + " / "
                    + net.skybornmc.skyblock.common.util.Format.number(unlockAt(i + 1, unlocks));
        }
        return "";
    }

    /** After enable: the world (created by the worlds module from worlds.yml), the areas built once, the dungeon spawner. */
    void start() {
        events = new GrindEventsImpl(core, msg, () -> config, () -> world);
        core.services().register(MODULE, net.skybornmc.skyblock.common.grind.GrindEvents.class, events);
        core.services().register(MODULE, net.skybornmc.skyblock.common.grind.GrindProgress.class, this); // owner 6 Nov 2026: Quick Study
        events.start();
        core.placeholders().registerText(MODULE, "grind_event", p -> {
            if (p == null || world == null || !p.getWorld().equals(world) || !config.grindEventsEnabled) {
                return "";
            }
            String key = events.active().isEmpty() ? "grind.events.scoreboard-next" : "grind.events.scoreboard-live";
            return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(
                    msg.component(p, key, net.skybornmc.skyblock.common.text.Msg.number("seconds", events.secondsLeft())));
        });
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
            if (world.getBlockAt(FISH_X, base, 0).getType() != Material.WATER) {
                buildPond(); // owner 6 Nov 2026: /fish (also on worlds built before it)
            }
            if (world.getBlockAt(PveArena.X, base, 0).getType() != Material.SEA_LANTERN) {
                pve.build(world, base); // owner 6 Nov 2026: /pve
                core.plugin().getLogger().info("Essentials: built the /pve arena in " + world.getName());
            }
            Bukkit.getPluginManager().registerEvents(pve, core.plugin());
            Bukkit.getPluginManager().registerEvents(pve.bosses, core.plugin());
            core.scheduler().repeat("essentials:pve", 40L, 20L, () -> pve.tick(world, base, Math.max(0, config.pveMobsPerRing)));
            wetFarm(); // owner 5 Nov 2026: every farmland at /farm is wet, as if it had water
            fillMine(); // owner 4 Nov 2026: the real mine is all coal ore; higher tiers are each player's own view
            core.scheduler().repeat("essentials:grind-pay", 60L, 60L, this::payBonus);
            // Chunk reloads show the real (wheat) blocks again: refresh the own-crop view of players at the farm.
            // FX-07: every 30 s (chunk loads and regrowth already resend their part)
            core.scheduler().repeat("essentials:grind-view", 600L, 600L, () -> {
                for (Player p : world.getPlayers()) {
                    if (Math.abs(p.getLocation().getBlockX() - FARM_X) <= FARM_HALF + 40) {
                        sendFarm(p);
                    } else if (Math.abs(p.getLocation().getBlockX() - MINE_X) <= MINE_HALF + 40) {
                        sendMine(p);
                    }
                }
            });
        });
    }

    private void go(Player player, String area) {
        if (world == null) {
            msg.send(player, "grind.missing");
            return;
        }
        if (area.equals("pve")) {
            teleports.request(player, pve.start(world, base), TeleportReason.SPAWN, TeleportOptions.to(area,
                    () -> welcome(player, "pve")));
            return;
        }
        int x = switch (area) {
            case "mine" -> MINE_X;
            case "dungeon" -> DUNGEON_X;
            case "fish" -> FISH_X;
            default -> FARM_X;
        };
        int half = switch (area) {
            case "mine" -> MINE_HALF;
            case "dungeon" -> DUNGEON_HALF;
            case "fish" -> FISH_HALF + 3; // land on the rim, not in the water
            default -> FARM_HALF;
        };
        Location to = new Location(world, x + 0.5, base + 1, -half - 3 + 0.5, 0, 0);
        teleports.request(player, to, TeleportReason.SPAWN, TeleportOptions.to(area,
                () -> welcome(player, area)));
    }

    /** Owner 7 Nov 2026 ("no spam"): the welcome line once per area and session. */
    private final java.util.Set<String> welcomed = java.util.concurrent.ConcurrentHashMap.newKeySet();

    private void welcome(Player player, String area) {
        if (welcomed.add(player.getUniqueId() + ":" + area)) {
            msg.send(player, "grind.arrived." + area);
        }
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
                wet(world.getBlockAt(FARM_X + x, base, z));
                ripe(world.getBlockAt(FARM_X + x, base + 1, z));
            }
        }
        for (int x = -MINE_HALF; x <= MINE_HALF; x++) {
            for (int z = -MINE_HALF; z <= MINE_HALF; z++) {
                for (int d = 0; d < MINE_DEPTH; d++) {
                    world.getBlockAt(MINE_X + x, base - d, z).setType(Material.STONE, false);
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

    /** Owner 6 Nov 2026: the /fish pond: water 3 deep, a 3-block stone-brick rim to stand on and a landing pad. */
    private void buildPond() {
        int rim = FISH_HALF + 3;
        for (int x = -rim; x <= rim; x++) {
            for (int z = -rim; z <= rim; z++) {
                boolean water = Math.abs(x) <= FISH_HALF && Math.abs(z) <= FISH_HALF;
                for (int d = 0; d <= 3; d++) {
                    world.getBlockAt(FISH_X + x, base - d, z).setType(water && d < 3 ? Material.WATER : Material.STONE_BRICKS, false);
                }
                if (!water && (Math.abs(x) == rim || Math.abs(z) == rim)) {
                    world.getBlockAt(FISH_X + x, base + 1, z).setType(Material.STONE_BRICK_WALL, false);
                }
            }
        }
        for (int x = -3; x <= 3; x++) { // the entrance in the rim wall and a landing pad in front of it
            world.getBlockAt(FISH_X + x, base + 1, -rim).setType(Material.AIR, false);
            for (int z = -rim - 6; z <= -rim - 1; z++) {
                world.getBlockAt(FISH_X + x, base, z).setType(Material.POLISHED_ANDESITE, false);
            }
        }
        core.plugin().getLogger().info("Essentials: built the /fish pond in " + world.getName());
    }

    /** Owner 4 Nov 2026: every block of the pit is the first ore (coal); an older build had stone and random ores. */
    private void fillMine() {
        for (int x = -MINE_HALF; x <= MINE_HALF; x++) {
            for (int z = -MINE_HALF; z <= MINE_HALF; z++) {
                for (int d = 0; d < MINE_DEPTH; d++) {
                    Block b = world.getBlockAt(MINE_X + x, base - d, z);
                    if (b.getType() != BASE_ORE) {
                        b.setType(BASE_ORE, false);
                    }
                }
            }
        }
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
            Player p = event.getPlayer();
            if (!net.skybornmc.skyblock.common.grind.GrindProgress.dropsOnly(p)) {
                countFarm(p, b);
            }
            core.scheduler().later("essentials:grind-regrow", regrowTicks(p, config.grindFarmRegrowSeconds), () -> {
                // Also a crop the hoe replanted (age 0): it would never grow here (no random ticks).
                if ((b.getType().isAir() || b.getType() == Material.WHEAT) && b.getRelative(0, -1, 0).getType() == Material.FARMLAND) {
                    ripe(b);
                    showLater(b);
                }
            });
            // The island hoe replants the crop next tick (young real wheat): show it in each player's own crop.
            core.scheduler().later("essentials:grind-view", 2L, () -> showLater(b));
            return;
        }
        if (in(b, MINE_X, MINE_HALF) && b.getY() <= base && b.getY() > base - MINE_DEPTH && b.getType() != Material.BEDROCK) {
            Player p = event.getPlayer();
            if (!net.skybornmc.skyblock.common.grind.GrindProgress.dropsOnly(p)) {
                countMine(p, b);
            }
            core.scheduler().later("essentials:grind-regen", regrowTicks(p, config.grindMineRegenSeconds), () -> {
                if (b.getType().isAir()) {
                    b.setType(BASE_ORE, false);
                    showLater(b);
                }
            });
            return;
        }
        event.setCancelled(true);
    }

    /** A counted /farm harvest: progress, the money bonus roll, the grind event (skills, quests), the unlock. */
    private void countFarm(Player p, Block b) {
        // The player's own crop: carrots, potatoes ... drop instead of wheat once unlocked (per player).
        long farmed = farmed(p) + 1;
        Crop before = cropFor(farmed - 1);
        Crop crop = cropFor(farmed);
        p.getPersistentDataContainer().set(FARMED, PersistentDataType.LONG, farmed);
        rollBonus(p, farmBonus(CROPS.indexOf(before)), b);
        Bukkit.getPluginManager().callEvent(new GrindBreakEvent(p, GrindBreakEvent.FARM, crop.block(), crop.item(), 1, b.getLocation()));
        // The drops become the player's crop in onDrop (so tools, auto-sell and shards still see normal drops).
        if (crop != before) {
            msg.send(p, "grind.unlocked", net.skybornmc.skyblock.common.text.Msg.p("crop", name(crop.item())));
            core.sounds().play(p, "unlock");
            sendFarm(p);
        } else if (CROPS.indexOf(crop) + 1 < CROPS.size()) {
            Crop next = CROPS.get(CROPS.indexOf(crop) + 1);
            if (farmed % 50 == 0) {
                net.skybornmc.skyblock.common.hud.ActionBars.show(p, "progress", msg.component(p, "grind.progress", net.skybornmc.skyblock.common.text.Msg.number("have", farmed),
                        net.skybornmc.skyblock.common.text.Msg.number("need", cropUnlockAt(CROPS.indexOf(next), farmUnlocks())),
                        net.skybornmc.skyblock.common.text.Msg.p("crop", name(next.item()))), net.skybornmc.skyblock.common.hud.ActionBars.INFO, 2000);
            }
        }
    }

    /** A counted /mine break: progress, the money bonus roll, the grind event (skills, quests, contracts), the unlock. */
    private void countMine(Player p, Block b) {
        List<Long> unlocks = mineUnlocks();
        Ore before = oreTier(mined(p), unlocks);
        long mined = mined(p) + 1;
        p.getPersistentDataContainer().set(MINED, PersistentDataType.LONG, mined);
        Ore tier = oreTier(mined, unlocks);
        rollBonus(p, mineBonus(ORE_TIERS.indexOf(before)), b);
        Bukkit.getPluginManager().callEvent(new GrindBreakEvent(p, GrindBreakEvent.MINE, before.block(), before.drop(), before.amount(),
                b.getLocation()));
        if (tier != before) {
            msg.send(p, "grind.unlocked-ore", net.skybornmc.skyblock.common.text.Msg.p("ore", name(tier.block())));
            core.sounds().play(p, "unlock");
            core.scheduler().later("essentials:grind-view", 1L, () -> sendMine(p));
        } else if (mined % 50 == 0) {
            int next = ORE_TIERS.indexOf(tier) + 1;
            if (next < ORE_TIERS.size()) {
                net.skybornmc.skyblock.common.hud.ActionBars.show(p, "progress", msg.component(p, "grind.progress-ore",
                        net.skybornmc.skyblock.common.text.Msg.number("have", mined),
                        net.skybornmc.skyblock.common.text.Msg.number("need", unlockAt(next, unlocks)),
                        net.skybornmc.skyblock.common.text.Msg.p("ore", name(ORE_TIERS.get(next).block()))),
                        net.skybornmc.skyblock.common.hud.ActionBars.INFO, 2000);
            }
        }
    }

    /** Owner 6 Nov 2026, Geode / Harvest Basket (GrindProgress): the drops of every unlocked crop or ore. */
    @Override
    public List<Material> unlockedDrops(Player p, String area) {
        List<Material> out = new java.util.ArrayList<>();
        if (net.skybornmc.skyblock.common.grind.GrindBreakEvent.FISH.equals(area)) {
            long fished = fished(p);
            for (FishRules.Water w : waters) {
                if (w.unlockAt() <= fished && !w.catches().isEmpty()) {
                    out.add(w.catches().getFirst().item()); // the water's main catch
                }
            }
            return out;
        }
        if (net.skybornmc.skyblock.common.grind.GrindBreakEvent.FARM.equals(area)) {
            long farmed = farmed(p);
            List<Long> unlocks = farmUnlocks();
            for (int i = 0; i < CROPS.size(); i++) {
                if (cropUnlockAt(i, unlocks) <= farmed) {
                    out.add(CROPS.get(i).item());
                }
            }
            return out;
        }
        List<Long> unlocks = mineUnlocks();
        long mined = mined(p);
        for (int i = 0; i < ORE_TIERS.size(); i++) {
            if (unlockAt(i, unlocks) <= mined) {
                out.add(ORE_TIERS.get(i).drop());
            }
        }
        return out;
    }

    /** Owner 6 Nov 2026, Quick Study (GrindProgress): extra unlock progress at /farm or /mine, with the unlock message. */
    @Override
    public void addProgress(Player p, String area, long blocks) {
        if (p == null || blocks <= 0 || world == null || !p.getWorld().equals(world)) {
            return;
        }
        if (net.skybornmc.skyblock.common.grind.GrindBreakEvent.FISH.equals(area)) {
            FishRules.Water before = water(p);
            long fished = fished(p) + blocks;
            p.getPersistentDataContainer().set(FISHED, PersistentDataType.LONG, fished);
            FishRules.Water after = FishRules.waterFor(waters, fished);
            if (after != before) {
                msg.send(p, "grind.fish.unlocked", net.skybornmc.skyblock.common.text.Msg.p("water", waterName(p, after)));
                core.sounds().play(p, "unlock");
            }
            return;
        }
        if (net.skybornmc.skyblock.common.grind.GrindBreakEvent.FARM.equals(area)) {
            Crop before = cropFor(farmed(p));
            long farmed = farmed(p) + blocks;
            p.getPersistentDataContainer().set(FARMED, PersistentDataType.LONG, farmed);
            Crop crop = cropFor(farmed);
            if (crop != before) {
                msg.send(p, "grind.unlocked", net.skybornmc.skyblock.common.text.Msg.p("crop", name(crop.item())));
                core.sounds().play(p, "unlock");
                sendFarm(p);
            }
            return;
        }
        List<Long> unlocks = mineUnlocks();
        Ore before = oreTier(mined(p), unlocks);
        long mined = mined(p) + blocks;
        p.getPersistentDataContainer().set(MINED, PersistentDataType.LONG, mined);
        Ore tier = oreTier(mined, unlocks);
        if (tier != before) {
            msg.send(p, "grind.unlocked-ore", net.skybornmc.skyblock.common.text.Msg.p("ore", name(tier.block())));
            core.sounds().play(p, "unlock");
            core.scheduler().later("essentials:grind-view", 1L, () -> sendMine(p));
        }
    }

    /** Owner 5 Nov 2026: farmland at /farm is always fully wet (looks watered, never dries to dirt). */
    private void wetFarm() {
        for (int x = -FARM_HALF; x <= FARM_HALF; x++) {
            for (int z = -FARM_HALF; z <= FARM_HALF; z++) {
                wet(world.getBlockAt(FARM_X + x, base, z));
            }
        }
    }

    private static void wet(org.bukkit.block.Block b) {
        if (b.getBlockData() instanceof org.bukkit.block.data.type.Farmland land && land.getMoisture() != land.getMaximumMoisture()) {
            land.setMoisture(land.getMaximumMoisture());
            b.setBlockData(land, false);
        }
    }

    private boolean inFarm(org.bukkit.block.Block b) {
        return world != null && b.getWorld().equals(world) && Math.abs(b.getX() - FARM_X) <= FARM_HALF && Math.abs(b.getZ()) <= FARM_HALF;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMoisture(org.bukkit.event.block.MoistureChangeEvent event) {
        if (inFarm(event.getBlock())) {
            event.setCancelled(true); // stays wet
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFade(org.bukkit.event.block.BlockFadeEvent event) {
        if (event.getBlock().getType() == Material.FARMLAND && inFarm(event.getBlock())) {
            event.setCancelled(true); // never turns back into dirt
        }
    }

    // ------------------------------------------------------------------ mine: ores per player

    /** One ore tier: the block the whole mine shows, what it drops, mined blocks to unlock it (default), money bonus (default). */
    record Ore(Material block, Material drop, int amount, long unlockAt, long bonus) {
    }

    /**
     * Owner 4 Nov 2026: one ore fills the whole mine per tier, like the farm's crops: all coal at the start, then iron
     * (owner: "break enough coal to get it FULL iron ore"), gold ... emerald. The real blocks are coal ore; a higher tier is each player's own client-side view.
     */
    static final List<Ore> ORE_TIERS = List.of(
            new Ore(Material.COAL_ORE, Material.COAL, 1, 0, 3000),
            new Ore(Material.IRON_ORE, Material.RAW_IRON, 1, 400, 6000),
            new Ore(Material.GOLD_ORE, Material.RAW_GOLD, 1, 1_500, 12000),
            new Ore(Material.REDSTONE_ORE, Material.REDSTONE, 4, 4_000, 16000),
            new Ore(Material.LAPIS_ORE, Material.LAPIS_LAZULI, 4, 8_000, 24000),
            new Ore(Material.DIAMOND_ORE, Material.DIAMOND, 1, 15_000, 45000),
            new Ore(Material.EMERALD_ORE, Material.EMERALD, 1, 25_000, 80000));
    static final Material BASE_ORE = Material.COAL_ORE;
    private static final org.bukkit.NamespacedKey MINED = net.skybornmc.skyblock.common.item.ItemKeys.key("grind_mined");

    private static long mined(Player p) {
        Long v = p.getPersistentDataContainer().get(MINED, PersistentDataType.LONG);
        return v == null ? 0 : v;
    }

    /** {@code grind.mine-unlocks} as longs (a broken or missing entry falls back to the built-in threshold). */
    private List<Long> mineUnlocks() {
        List<Long> out = new ArrayList<>();
        for (Object o : config.grindMineUnlocks) {
            try {
                out.add(o instanceof Number n ? n.longValue() : Long.parseLong(String.valueOf(o).trim().replace(",", "")));
            } catch (NumberFormatException e) {
                out.add(-1L);
            }
        }
        return out;
    }

    /** Pure: blocks mined to unlock tier {@code index} (configured, else the built-in default). */
    static long unlockAt(int index, List<Long> unlocks) {
        if (index <= 0) {
            return 0;
        }
        Long v = index < unlocks.size() ? unlocks.get(index) : null;
        return v == null || v < 0 ? ORE_TIERS.get(index).unlockAt() : v;
    }

    /** Pure: the newest ore tier unlocked at {@code mined} blocks. */
    static Ore oreTier(long mined, List<Long> unlocks) {
        Ore out = ORE_TIERS.getFirst();
        for (int i = 0; i < ORE_TIERS.size(); i++) {
            if (mined >= unlockAt(i, unlocks)) {
                out = ORE_TIERS.get(i);
            }
        }
        return out;
    }

    /** Pure: a configured bonus amount for tier {@code index}, else {@code fallback}. */
    static long tierAmount(List<?> list, int index, long fallback) {
        if (index < 0 || list == null || index >= list.size()) {
            return fallback;
        }
        Object o = list.get(index);
        try {
            return o instanceof Number n ? n.longValue() : Long.parseLong(String.valueOf(o).trim().replace(",", ""));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private long farmBonus(int tier) {
        return tierAmount(config.grindFarmBonus, tier, tier >= 0 && tier < CROPS.size() ? CROPS.get(tier).bonus() : 0);
    }

    private long mineBonus(int tier) {
        return tierAmount(config.grindMineBonus, tier, tier >= 0 && tier < ORE_TIERS.size() ? ORE_TIERS.get(tier).bonus() : 0);
    }

    /** Owner 4 Nov 2026: the money bonus comes now and then ({@code grind.bonus-chance-percent}) and grows with the tier. */
    private void rollBonus(Player p, long amount, Block at) {
        // Owner 6 Nov 2026: Golden Blocks makes the bonus come every time and worth more.
        boolean golden = events != null && events.active(p, net.skybornmc.skyblock.common.grind.GrindEvents.Kind.GOLDEN_BLOCKS);
        double chance = config.grindBonusChance * (golden ? config.grindEventGoldenChance : 1); // owner 7 Nov 2026: still rare
        if (amount > 0 && ThreadLocalRandom.current().nextDouble(100) < chance) {
            // owner 7 Nov 2026: the bonus floats up from the crop or ore that gave it (0.5 s), not the action bar
            moneyPopup(p, at == null ? null : at.getLocation(), addBonus(p, golden ? Math.round(amount * config.grindEventGolden) : amount));
        }
    }

    /** Ticks until a broken crop or ore comes back: one tick during Instant Regrow (owner 6 Nov 2026). */
    private long regrowTicks(Player p, int seconds) {
        return events != null && events.active(p, net.skybornmc.skyblock.common.grind.GrindEvents.Kind.INSTANT_REGROW) ? 2L : seconds * 20L;
    }

    /** The mine as the player sees it: their unlocked ores on the real stone (client side only). */
    private void sendMine(Player p) {
        sendMine(p, MINE_X - MINE_HALF, MINE_X + MINE_HALF, -MINE_HALF, MINE_HALF);
    }

    /** {@link #sendMine(Player)} for the part of the mine inside x/z (one chunk after it was sent to the player). */
    private void sendMine(Player p, int minX, int maxX, int minZ, int maxZ) {
        if (world == null || !p.getWorld().equals(world)) {
            return;
        }
        Ore tier = oreTier(mined(p), mineUnlocks());
        if (tier.block() == BASE_ORE) {
            return; // the real blocks are this tier
        }
        org.bukkit.block.data.BlockData view = tier.block().createBlockData();
        // FX-07: no BlockState per block (thousands per player): one multi-block packet from positions.
        java.util.Map<io.papermc.paper.math.Position, org.bukkit.block.data.BlockData> changes = new java.util.HashMap<>();
        for (int x = Math.max(minX, MINE_X - MINE_HALF); x <= Math.min(maxX, MINE_X + MINE_HALF); x++) {
            for (int z = Math.max(minZ, -MINE_HALF); z <= Math.min(maxZ, MINE_HALF); z++) {
                for (int d = 0; d < MINE_DEPTH; d++) {
                    if (world.getType(x, base - d, z) == BASE_ORE) {
                        changes.put(io.papermc.paper.math.Position.block(x, base - d, z), view);
                    }
                }
            }
        }
        if (!changes.isEmpty()) {
            p.sendMultiBlockChange(changes);
        }
    }

    /** Drops follow what the player sees: their crop at the farm, their ore at the mine. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onDrop(org.bukkit.event.block.BlockDropItemEvent event) {
        Block b = event.getBlock();
        if (world == null || !b.getWorld().equals(world)) {
            return;
        }
        Player p = event.getPlayer();
        if (in(b, FARM_X, FARM_HALF) && event.getBlockState().getType() == Material.WHEAT) {
            Crop crop = cropFor(farmed(p));
            if (crop == Crop_WHEAT()) {
                return;
            }
            for (var it = event.getItems().iterator(); it.hasNext(); ) {
                org.bukkit.entity.Item item = it.next();
                Material t = item.getItemStack().getType();
                if (t == Material.WHEAT) {
                    item.setItemStack(new ItemStack(crop.item(), 1 + ThreadLocalRandom.current().nextInt(2)));
                } else {
                    it.remove(); // seeds
                }
            }
            return;
        }
        if (in(b, MINE_X, MINE_HALF) && event.getBlockState().getType() == BASE_ORE) {
            // the tier the player saw (onBreak already counted this block, a drops-only break did not)
            Ore ore = oreTier(net.skybornmc.skyblock.common.grind.GrindProgress.dropsOnly(p) ? mined(p) : mined(p) - 1, mineUnlocks());
            if (ore.block() == BASE_ORE) {
                return; // coal: vanilla drops (Fortune counts)
            }
            boolean first = true;
            for (var it = event.getItems().iterator(); it.hasNext(); ) {
                org.bukkit.entity.Item item = it.next();
                if (first) {
                    // the vanilla coal count (Fortune) carries over to the player's ore
                    int times = Math.max(1, item.getItemStack().getAmount());
                    item.setItemStack(new ItemStack(ore.drop(), Math.min(ore.drop().getMaxStackSize(), ore.amount() * times)));
                    first = false;
                } else {
                    it.remove();
                }
            }
        }
    }

    private long addBonus(Player p, long amount) {
        long scaled = Math.round(amount * config.grindBonusPercent / 100.0);
        if (scaled > 0) {
            // Owner 4 Nov 2026: farm/mine progress and bonus are per profile, so the bonus is owed to this profile.
            pendingBonus.merge(net.skybornmc.skyblock.common.profile.ProfileService.of(core.services()).profileKey(p), scaled, Long::sum);
        }
        return scaled;
    }

    /** Pays the bonus money still waiting (module stop). */
    void flush() {
        payBonus();
    }

    private void payBonus() {
        if (pendingBonus.isEmpty()) {
            return;
        }
        var economy = core.services().optional(net.skybornmc.skyblock.common.economy.EconomyService.class).orElse(null);
        var profiles = net.skybornmc.skyblock.common.profile.ProfileService.of(core.services());
        java.util.Map<java.util.UUID, Long> due = new java.util.HashMap<>(pendingBonus);
        pendingBonus.clear();
        if (economy == null) {
            return;
        }
        due.forEach((id, amount) -> {
            // id = the profile key that earned it (paid there even after a switch or quit)
            var account = net.skybornmc.skyblock.common.economy.AccountRef.player(id);
            economy.execute(net.skybornmc.skyblock.common.economy.Transaction.deposit(account,
                            net.skybornmc.skyblock.common.economy.Currency.MONEY, amount, "grind_bonus")
                    .actor(net.skybornmc.skyblock.common.audit.Actor.system("grind")).reference("farm/mine bonus")
                    .idempotencyKey("grind_bonus:" + java.util.UUID.randomUUID()));
            profiles.onlinePlayer(id).ifPresent(p -> visitBonus.merge(p.getUniqueId(), amount, Long::sum));
            // owner 7 Nov 2026: each bonus already floated up from its block (popup); no action bar line
        });
    }

    private static Crop Crop_WHEAT() {
        return CROPS.getFirst();
    }

    // ------------------------------------------------------------------ /fish (owner 6 Nov 2026)

    // ------------------------------------------------------------------ for the /pve arena

    World grindWorld() {
        return world;
    }

    int base() {
        return base;
    }

    ModuleMessages msg() {
        return msg;
    }

    Core core() {
        return core;
    }

    /** Money for a /pve kill (Golden Blocks x5 like the other areas), paid with the grind bonus batch. */
    void pay(Player p, long amount) {
        pay(p, amount, null);
    }

    /** {@link #pay(Player, long)} with the spot it floats up from (the mob; owner 7 Nov 2026: popups, no action bar). */
    void pay(Player p, long amount, org.bukkit.Location at) {
        boolean golden = events != null && events.active(p, net.skybornmc.skyblock.common.grind.GrindEvents.Kind.GOLDEN_BLOCKS);
        moneyPopup(p, at, addBonus(p, golden ? Math.round(amount * config.grindEventGolden) : amount));
    }

    /** Owner 7 Nov 2026: money from /farm, /mine, /fish and /pve floats up from where it came from (0.5 s). */
    private void moneyPopup(Player p, org.bukkit.Location at, long paid) {
        if (paid > 0 && at != null) {
            net.skybornmc.skyblock.common.hud.Popups.show(core.plugin(), p, at, msg.component(p, "grind.bonus-popup",
                    net.skybornmc.skyblock.common.text.Msg.p("amount", net.skybornmc.skyblock.common.util.Format.money(paid))));
        }
    }

    int pveSigilEvery() {
        return Math.max(1, config.pveSigilEvery);
    }

    /** Module stop: the arena mobs go. */
    void stopPve() {
        org.bukkit.event.HandlerList.unregisterAll(pve);
        org.bukkit.event.HandlerList.unregisterAll(pve.bosses);
        pve.clear();
    }

    /** /skyblock reload essentials: the waters again. */
    void reloadFish() {
        waters = FishRules.parse(config.fishWaters, w -> core.plugin().getLogger().warning(w));
    }

    static long fished(Player p) {
        Long v = p.getPersistentDataContainer().get(FISHED, PersistentDataType.LONG);
        return v == null ? 0 : v;
    }

    private FishRules.Water water(Player p) {
        return FishRules.waterFor(waters, fished(p));
    }

    private String waterName(Player p, FishRules.Water w) {
        return w == null ? "" : net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(
                msg.component(p, "grind.fish.water." + w.id()));
    }

    private boolean inPond(org.bukkit.Location at) {
        return world != null && at.getWorld().equals(world) && Math.abs(at.getBlockX() - FISH_X) <= FISH_HALF && Math.abs(at.getBlockZ()) <= FISH_HALF;
    }

    /**
     * A cast and a catch at the pond: the bite comes faster (Bite Frenzy: Instant Regrow makes it nearly instant), the
     * catch becomes the player's water's catch, pays its money, counts toward the next water and fires the grind event
     * (skills, quests, island tools). Auto-clickers and more than {@code grind.fish-per-minute} catches get nothing.
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onFish(org.bukkit.event.player.PlayerFishEvent event) {
        Player p = event.getPlayer();
        if (world == null || !p.getWorld().equals(world) || staff(p) || waters.isEmpty()) {
            return;
        }
        org.bukkit.entity.FishHook hook = event.getHook();
        FishRules.Guard guard = fishGuards.computeIfAbsent(p.getUniqueId(), k -> new FishRules.Guard());
        if (event.getState() == org.bukkit.event.player.PlayerFishEvent.State.FISHING) {
            org.bukkit.Location l = p.getLocation();
            guard.cast(l.getX(), l.getY(), l.getZ(), l.getYaw(), l.getPitch(), config.fishFrozenCasts);
            int[] wait = waitTicks();
            boolean frenzy = events != null && events.active(p, net.skybornmc.skyblock.common.grind.GrindEvents.Kind.INSTANT_REGROW);
            hook.setWaitTime(frenzy ? Math.max(10, wait[0] / 3) : wait[0], frenzy ? Math.max(20, wait[1] / 3) : wait[1]);
            return;
        }
        if (event.getState() != org.bukkit.event.player.PlayerFishEvent.State.CAUGHT_FISH || !(event.getCaught() instanceof org.bukkit.entity.Item item)) {
            return;
        }
        if (!inPond(hook.getLocation())) {
            return; // water somewhere else in the grind world (there is none): vanilla
        }
        if (guard.frozen(config.fishFrozenCasts) || !guard.allow(System.currentTimeMillis(), config.fishPerMinute)) {
            item.remove();
            event.setExpToDrop(0);
            net.skybornmc.skyblock.common.hud.ActionBars.show(p, "fish-guard", msg.component(p, "grind.fish.guard"),
                    net.skybornmc.skyblock.common.hud.ActionBars.INFO, 2500);
            return;
        }
        FishRules.Water before = water(p);
        FishRules.Catch c = FishRules.roll(before, ThreadLocalRandom.current().nextDouble());
        item.setItemStack(new ItemStack(c.item(), c.amount()));
        event.setExpToDrop(1);
        long fished = fished(p) + 1;
        p.getPersistentDataContainer().set(FISHED, PersistentDataType.LONG, fished);
        boolean golden = events != null && events.active(p, net.skybornmc.skyblock.common.grind.GrindEvents.Kind.GOLDEN_BLOCKS);
        moneyPopup(p, hook.getLocation().getBlock().getLocation(),
                addBonus(p, golden ? Math.round(before.pay() * config.grindEventGolden) : before.pay())); // every catch pays
        Bukkit.getPluginManager().callEvent(new GrindBreakEvent(p, GrindBreakEvent.FISH, Material.WATER, c.item(), c.amount(), hook.getLocation()));
        FishRules.Water after = FishRules.waterFor(waters, fished);
        if (after != before) {
            msg.send(p, "grind.fish.unlocked", net.skybornmc.skyblock.common.text.Msg.p("water", waterName(p, after)));
            core.sounds().play(p, "unlock");
        } else if (fished % 25 == 0) {
            FishRules.Water next = FishRules.next(waters, before);
            if (next != null) {
                net.skybornmc.skyblock.common.hud.ActionBars.show(p, "progress", msg.component(p, "grind.fish.progress",
                        net.skybornmc.skyblock.common.text.Msg.number("have", fished), net.skybornmc.skyblock.common.text.Msg.number("need", next.unlockAt()),
                        net.skybornmc.skyblock.common.text.Msg.p("water", waterName(p, next))), net.skybornmc.skyblock.common.hud.ActionBars.INFO, 2000);
            }
        }
    }

    /** {@code grind.fish-wait-ticks} as {min, max}. */
    private int[] waitTicks() {
        try {
            String[] parts = config.fishWait.split("-", 2);
            int lo = Math.max(1, Integer.parseInt(parts[0].strip()));
            int hi = Math.max(lo, Integer.parseInt(parts[1].strip()));
            return new int[]{lo, hi};
        } catch (RuntimeException e) {
            return new int[]{60, 160};
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onFishQuit(org.bukkit.event.player.PlayerQuitEvent event) {
        fishGuards.remove(event.getPlayer().getUniqueId());
    }

    private static long farmed(Player p) {
        Long v = p.getPersistentDataContainer().get(FARMED, PersistentDataType.LONG);
        return v == null ? 0 : v;
    }

    private static String name(Material m) {
        String id = m.getKey().getKey().replace('_', ' ');
        return Character.toUpperCase(id.charAt(0)) + id.substring(1);
    }

    /** Pure: the age the player's own crop shows for a real wheat block of {@code realAge} (ripe stays ripe). */
    static int viewAge(int realAge, int realMax, int viewMax) {
        if (realAge >= realMax) {
            return viewMax;
        }
        return Math.max(0, Math.min(viewMax, (int) Math.floor(realAge * (double) viewMax / Math.max(1, realMax))));
    }

    /** Pure: does chunk cx/cz overlap the square area centred on x {@code centerX}, z 0, with half size {@code half}? */
    static boolean chunkOverlaps(int chunkX, int chunkZ, int centerX, int half) {
        int minX = chunkX << 4;
        int minZ = chunkZ << 4;
        return minX + 15 >= centerX - half && minX <= centerX + half && minZ + 15 >= -half && minZ <= half;
    }

    /**
     * What player {@code p} should see at {@code b} instead of the real block, or null when the real block is right.
     * Farm: their own crop (same growth stage) on real wheat. Mine: their own ore on real stone.
     */
    private org.bukkit.block.data.BlockData viewOf(Player p, Block b) {
        if (in(b, FARM_X, FARM_HALF) && b.getY() == base + 1 && b.getType() == Material.WHEAT) {
            Crop crop = cropFor(farmed(p));
            if (crop == Crop_WHEAT()) {
                return null;
            }
            org.bukkit.block.data.BlockData d = crop.block().createBlockData();
            if (d instanceof Ageable view && b.getBlockData() instanceof Ageable real) {
                view.setAge(viewAge(real.getAge(), real.getMaximumAge(), view.getMaximumAge()));
            }
            return d;
        }
        if (in(b, MINE_X, MINE_HALF) && b.getY() <= base && b.getY() > base - MINE_DEPTH && b.getType() == BASE_ORE) {
            Material view = oreTier(mined(p), mineUnlocks()).block();
            return view == BASE_ORE ? null : view.createBlockData();
        }
        return null;
    }

    private void show(Player p, Block b) {
        org.bukkit.block.data.BlockData view = viewOf(p, b);
        if (view != null && p.isOnline() && p.getWorld().equals(b.getWorld())) {
            p.sendBlockChange(b.getLocation(), view);
        }
    }

    /**
     * Feedback 4 Nov 2026 (C): one changed block, shown in each nearby player's own view, one tick later. The server
     * sends its own block update (real wheat / stone) at the end of the tick the block changed, and that overwrote a
     * view sent at once: that is why wheat showed up after carrots.
     */
    private void showLater(Block b) {
        tickEnd.add(b);
        core.scheduler().later("essentials:grind-view", 1L, () -> {
            for (Player p : b.getLocation().getNearbyPlayers(64)) {
                show(p, b);
            }
        });
    }

    /** Blocks changed this tick whose personal views go out at the end of the tick. */
    private final java.util.Set<Block> tickEnd = new java.util.LinkedHashSet<>();

    /**
     * Owner 4 Nov 2026 ("a respawned crop still shows wheat for a split second"): the server sends the real block (wheat,
     * coal) while it ticks the world; a view sent at the end of the same tick goes out right after it in the same network
     * flush, so the client never draws the real block. The view one tick later stays as a safety net.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onTickEnd(com.destroystokyo.paper.event.server.ServerTickEndEvent event) {
        if (tickEnd.isEmpty()) {
            return;
        }
        List<Block> blocks = new ArrayList<>(tickEnd);
        tickEnd.clear();
        for (Block b : blocks) {
            for (Player p : b.getLocation().getNearbyPlayers(64)) {
                show(p, b);
            }
        }
    }

    /** The whole farm in the player's own crop (client side only; the real blocks stay wheat). */
    private void sendFarm(Player p) {
        sendFarm(p, FARM_X - FARM_HALF, FARM_X + FARM_HALF, -FARM_HALF, FARM_HALF);
    }

    /** {@link #sendFarm(Player)} for the part of the farm inside x/z (one chunk after it was sent to the player). */
    private void sendFarm(Player p, int minX, int maxX, int minZ, int maxZ) {
        if (world == null || !p.getWorld().equals(world) || cropFor(farmed(p)) == Crop_WHEAT()) {
            return;
        }
        // FX-07: no BlockState per block: one multi-block packet from positions.
        java.util.Map<io.papermc.paper.math.Position, org.bukkit.block.data.BlockData> changes = new java.util.HashMap<>();
        for (int x = Math.max(minX, FARM_X - FARM_HALF); x <= Math.min(maxX, FARM_X + FARM_HALF); x++) {
            for (int z = Math.max(minZ, -FARM_HALF); z <= Math.min(maxZ, FARM_HALF); z++) {
                Block b = world.getBlockAt(x, base + 1, z);
                org.bukkit.block.data.BlockData view = viewOf(p, b);
                if (view != null) {
                    changes.put(io.papermc.paper.math.Position.block(x, base + 1, z), view);
                }
            }
        }
        if (!changes.isEmpty()) {
            p.sendMultiBlockChange(changes);
        }
    }

    /**
     * Feedback 4 Nov 2026 (C, "right-click turns a crop into wheat"): a right-click makes the server re-send the real
     * blocks around the click. Farm crops can't be used (bone meal etc.), and the clicked block and the one in front of
     * it get the player's view again next tick; the same on the mine's ores. Runs for clicks cancelled elsewhere too.
     */
    @EventHandler(priority = EventPriority.LOW)
    public void onUse(PlayerInteractEvent event) {
        Block b = event.getClickedBlock();
        if (world == null || b == null || event.getAction() != Action.RIGHT_CLICK_BLOCK || !b.getWorld().equals(world)
                || staff(event.getPlayer())) {
            return;
        }
        boolean farm = in(b, FARM_X, FARM_HALF) && b.getY() == base + 1 && b.getType() == Material.WHEAT;
        boolean mine = in(b, MINE_X, MINE_HALF) && b.getY() <= base && b.getY() > base - MINE_DEPTH;
        if (!farm && !mine) {
            return;
        }
        if (farm) {
            event.setCancelled(true);
        }
        Player p = event.getPlayer();
        Block front = b.getRelative(event.getBlockFace());
        core.scheduler().later("essentials:grind-view", 1L, () -> {
            show(p, b);
            show(p, front);
        });
    }

    /** A break that something cancelled re-sends the real block too: the player's view again. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onBreakCancelled(BlockBreakEvent event) {
        if (event.isCancelled() && world != null && event.getBlock().getWorld().equals(world)) {
            Player p = event.getPlayer();
            Block b = event.getBlock();
            core.scheduler().later("essentials:grind-view", 1L, () -> show(p, b));
        }
    }

    /** A chunk sent to a player (walking in, teleport, respawn, a chunk resend) has the real blocks: their view again. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkSent(io.papermc.paper.event.packet.PlayerChunkLoadEvent event) {
        if (world == null || !event.getWorld().equals(world)) {
            return;
        }
        int cx = event.getChunk().getX();
        int cz = event.getChunk().getZ();
        boolean farm = chunkOverlaps(cx, cz, FARM_X, FARM_HALF);
        boolean mine = chunkOverlaps(cx, cz, MINE_X, MINE_HALF);
        if (!farm && !mine) {
            return;
        }
        Player p = event.getPlayer();
        int minX = cx << 4;
        int minZ = cz << 4;
        core.scheduler().later("essentials:grind-view", 1L, () -> {
            if (!p.isOnline()) {
                return;
            }
            if (farm) {
                sendFarm(p, minX, minX + 15, minZ, minZ + 15);
            }
            if (mine) {
                sendMine(p, minX, minX + 15, minZ, minZ + 15);
            }
        });
    }

    /** Owner 4 Nov 2026: the crop/ore tier belongs to the profile (profiles module swaps it): show the new profile's view. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onProfileSwitched(net.skybornmc.skyblock.common.profile.ProfileSwitchedEvent event) {
        Player p = event.getPlayer();
        core.scheduler().later("essentials:grind-view", 20L, () -> {
            if (p.isOnline()) {
                sendFarm(p);
                sendMine(p);
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        Player p = event.getPlayer();
        core.scheduler().later("essentials:grind-view", 20L, () -> { sendFarm(p); sendMine(p); });
    }

    /** Arriving in the farm world (teleport, portal, join): the farm in your crop, again after the chunks loaded. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player p = event.getPlayer();
        visitBonus.remove(p.getUniqueId()); // a new visit starts at $0
        core.scheduler().later("essentials:grind-view", 20L, () -> { sendFarm(p); sendMine(p); });
        core.scheduler().later("essentials:grind-view", 60L, () -> { sendFarm(p); sendMine(p); });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoinGrind(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        // The Depths are gone: someone who logged out down there comes back at spawn.
        if (p.getWorld().getName().equalsIgnoreCase(config.removedWorld)) {
            World spawn = Bukkit.getWorld(config.spawnWorld);
            if (spawn != null) {
                core.scheduler().later("essentials:grind-out", 5L, () -> p.teleport(spawn.getSpawnLocation().toCenterLocation()));
            }
        }
        core.scheduler().later("essentials:grind-view", 40L, () -> { sendFarm(p); sendMine(p); });
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
