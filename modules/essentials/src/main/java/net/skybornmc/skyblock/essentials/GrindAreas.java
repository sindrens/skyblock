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
final class GrindAreas extends CommandGroup implements Listener {

    static final int FARM_X = 0;
    static final int MINE_X = 1000;
    static final int DUNGEON_X = 2000;
    static final int FARM_HALF = 30;
    static final int MINE_HALF = 20;
    static final int MINE_DEPTH = 6;
    static final int DUNGEON_HALF = 20;

    /** Farm crops in unlock order: block, item it drops, wheat broken in total to unlock it (feedback 3 Nov 2026). */
    record Crop(Material block, Material item, long unlockAt, long bonus) {
    }

    /** {@code bonus}: money paid per harvest on top of the crop (the Farm pays better than an island farm). */
    static final List<Crop> CROPS = List.of(new Crop(Material.WHEAT, Material.WHEAT, 0, 6), new Crop(Material.CARROTS, Material.CARROT, 3_000, 9),
            new Crop(Material.POTATOES, Material.POTATO, 12_000, 12), new Crop(Material.BEETROOTS, Material.BEETROOT, 40_000, 16)); // owner 5 Nov: carrots take much longer
    private static final org.bukkit.NamespacedKey FARMED = net.skybornmc.skyblock.common.item.ItemKeys.key("grind_farmed");

    /** Pure: the crop a player with {@code farmed} harvests has unlocked. */
    static Crop cropFor(long farmed) {
        Crop out = CROPS.getFirst();
        for (Crop c : CROPS) {
            if (farmed >= c.unlockAt()) {
                out = c;
            }
        }
        return out;
    }

    private static final List<EntityType> MOBS = List.of(EntityType.ZOMBIE, EntityType.ZOMBIE, EntityType.SKELETON, EntityType.SPIDER);

    private final TeleportService teleports;
    /** Bonus money waiting to be paid (main thread); paid every 3 s in one transaction per player. */
    private final java.util.Map<java.util.UUID, Long> pendingBonus = new java.util.HashMap<>();
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
        // Owner 3 Nov 2026 23:05: no dungeon (the arena stays built but has no command and no monsters).
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
            wetFarm(); // owner 5 Nov 2026: every farmland at /farm is wet, as if it had water
            fillMine(); // owner 4 Nov 2026: the real mine is all coal ore; higher tiers are each player's own view
            core.scheduler().repeat("essentials:grind-pay", 60L, 60L, this::payBonus);
            // Chunk reloads show the real (wheat) blocks again: refresh the own-crop view of players at the farm.
            core.scheduler().repeat("essentials:grind-view", 200L, 200L, () -> {
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
            // The player's own crop: carrots, potatoes ... drop instead of wheat once unlocked (per player).
            long farmed = farmed(p) + 1;
            Crop before = cropFor(farmed - 1);
            Crop crop = cropFor(farmed);
            p.getPersistentDataContainer().set(FARMED, PersistentDataType.LONG, farmed);
            rollBonus(p, farmBonus(CROPS.indexOf(before)));
            Bukkit.getPluginManager().callEvent(new GrindBreakEvent(p, GrindBreakEvent.FARM, crop.block(), crop.item(), 1, b.getLocation()));
            // The drops become the player's crop in onDrop (so tools, auto-sell and shards still see normal drops).
            if (crop != before) {
                msg.send(p, "grind.unlocked", net.skybornmc.skyblock.common.text.Msg.p("crop", name(crop.item())));
                core.sounds().play(p, "unlock");
                sendFarm(p);
            } else if (crop.unlockAt() < CROPS.getLast().unlockAt()) {
                Crop next = CROPS.get(CROPS.indexOf(crop) + 1);
                if (farmed % 50 == 0) {
                    net.skybornmc.skyblock.common.hud.ActionBars.show(p, "progress", msg.component(p, "grind.progress", net.skybornmc.skyblock.common.text.Msg.number("have", farmed),
                            net.skybornmc.skyblock.common.text.Msg.number("need", next.unlockAt()),
                            net.skybornmc.skyblock.common.text.Msg.p("crop", name(next.item()))), net.skybornmc.skyblock.common.hud.ActionBars.INFO, 2000);
                }
            }
            core.scheduler().later("essentials:grind-regrow", config.grindFarmRegrowSeconds * 20L, () -> {
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
            List<Long> unlocks = mineUnlocks();
            Ore before = oreTier(mined(p), unlocks);
            long mined = mined(p) + 1;
            p.getPersistentDataContainer().set(MINED, PersistentDataType.LONG, mined);
            Ore tier = oreTier(mined, unlocks);
            rollBonus(p, mineBonus(ORE_TIERS.indexOf(before)));
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
            core.scheduler().later("essentials:grind-regen", config.grindMineRegenSeconds * 20L, () -> {
                if (b.getType().isAir()) {
                    b.setType(BASE_ORE, false);
                    showLater(b);
                }
            });
            return;
        }
        event.setCancelled(true);
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
            new Ore(Material.COAL_ORE, Material.COAL, 1, 0, 30),
            new Ore(Material.IRON_ORE, Material.RAW_IRON, 1, 400, 60),
            new Ore(Material.GOLD_ORE, Material.RAW_GOLD, 1, 1_500, 120),
            new Ore(Material.REDSTONE_ORE, Material.REDSTONE, 4, 4_000, 160),
            new Ore(Material.LAPIS_ORE, Material.LAPIS_LAZULI, 4, 8_000, 240),
            new Ore(Material.DIAMOND_ORE, Material.DIAMOND, 1, 15_000, 450),
            new Ore(Material.EMERALD_ORE, Material.EMERALD, 1, 25_000, 800));
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
    private void rollBonus(Player p, long amount) {
        if (amount > 0 && ThreadLocalRandom.current().nextDouble(100) < config.grindBonusChance) {
            addBonus(p, amount);
        }
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
        List<org.bukkit.block.BlockState> states = new ArrayList<>();
        for (int x = Math.max(minX, MINE_X - MINE_HALF); x <= Math.min(maxX, MINE_X + MINE_HALF); x++) {
            for (int z = Math.max(minZ, -MINE_HALF); z <= Math.min(maxZ, MINE_HALF); z++) {
                for (int d = 0; d < MINE_DEPTH; d++) {
                    Block b = world.getBlockAt(x, base - d, z);
                    if (b.getType() == BASE_ORE) {
                        org.bukkit.block.BlockState st = b.getState();
                        st.setBlockData(view);
                        states.add(st);
                    }
                }
            }
        }
        if (!states.isEmpty()) {
            p.sendBlockChanges(states);
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
            Ore ore = oreTier(mined(p) - 1, mineUnlocks()); // the tier the player saw (onBreak already counted this block)
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

    private void addBonus(Player p, long amount) {
        long scaled = Math.round(amount * config.grindBonusPercent / 100.0);
        if (scaled > 0) {
            // Owner 4 Nov 2026: farm/mine progress and bonus are per profile, so the bonus is owed to this profile.
            pendingBonus.merge(net.skybornmc.skyblock.common.profile.ProfileService.of(core.services()).profileKey(p), scaled, Long::sum);
        }
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
            profiles.onlinePlayer(id).ifPresent(p -> net.skybornmc.skyblock.common.hud.ActionBars.show(p, "bonus",
                    msg.component(p, "grind.bonus", net.skybornmc.skyblock.common.text.Msg.p("amount",
                            net.skybornmc.skyblock.common.util.Format.money(amount))), net.skybornmc.skyblock.common.hud.ActionBars.PAYOUT, 2500));
        });
    }

    private static Crop Crop_WHEAT() {
        return CROPS.getFirst();
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
        List<org.bukkit.block.BlockState> states = new ArrayList<>();
        for (int x = Math.max(minX, FARM_X - FARM_HALF); x <= Math.min(maxX, FARM_X + FARM_HALF); x++) {
            for (int z = Math.max(minZ, -FARM_HALF); z <= Math.min(maxZ, FARM_HALF); z++) {
                Block b = world.getBlockAt(x, base + 1, z);
                org.bukkit.block.data.BlockData view = viewOf(p, b);
                if (view != null) {
                    org.bukkit.block.BlockState st = b.getState();
                    st.setBlockData(view);
                    states.add(st);
                }
            }
        }
        if (!states.isEmpty()) {
            p.sendBlockChanges(states);
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
