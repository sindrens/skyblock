package net.skybornmc.skyblock.enchants;

import net.skybornmc.skyblock.common.bonus.BonusContext;
import net.skybornmc.skyblock.common.bonus.BonusFamily;
import net.skybornmc.skyblock.common.bonus.BonusService;
import net.skybornmc.skyblock.common.profile.ProfileService;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.tools.ToolInfo;
import net.skybornmc.skyblock.common.tools.ToolKind;
import net.skybornmc.skyblock.common.util.Format;
import net.skybornmc.skyblock.depths.combat.DepthsMobKillEvent;
import net.skybornmc.skyblock.depths.drops.DepthsDropEvent;
import net.skybornmc.skyblock.depths.engine.Depths;
import net.skybornmc.skyblock.depths.engine.DepthsRegion;
import net.skybornmc.skyblock.depths.engine.ResourceBlock;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.LongPredicate;

/**
 * E2 Depths hooks (ENCHANTS §3, §6.1–§6.3, §6.5). The <b>only</b> class of the enchants module that touches the depths
 * module ({@code compileOnly}, created inside a {@code NoClassDefFoundError} guard like the tools module's DepthsHooks).
 *
 * <ul>
 *   <li>{@link DepthsDropEvent} (every accepted Depths block/log): Gilded Touch, Crystal Geode (Geode Sense), Amber Resin
 *       (Amber Sap), speed bursts, area enchants and book drops.</li>
 *   <li>{@link DepthsMobKillEvent} (every rewarded Depths combat kill): Plunder and Combat book drops (mini-boss books).</li>
 * </ul>
 *
 * <p><b>Area enchants</b> break only the player's own visible copies of resource blocks of the <b>same region</b> through
 * the block engine ({@link Depths#deplete}): the real world never changes and other players see nothing. Engine depletes
 * skip the fast-break check (they are enchant breaks, §10), so every proc is limited server-side by the enchant, by
 * {@code area.max-blocks-per-proc} and by a per-player budget of {@code area.max-blocks-per-second}. Their drops are the
 * main block's roll × yield × blocks (randomly rounded, one satchel add with the main drop) and they count for book drops
 * at their yield. No database work per block.</p>
 */
final class DepthsEnchants implements Listener {

    static final String GILDED = "gilded_touch";
    static final String GEODE = "geode_sense";
    static final String AMBER = "amber_sap";
    static final String VEIN = "vein_rend";
    static final String DRAGONFIRE = "dragonfire_blast";
    static final String WORLDBREAKER = "worldbreaker";
    static final String TREEFELLER = "treefeller";
    static final String WILDFIRE = "wildfire";
    static final String ASHEN = "ashen_grove";
    static final String PLUNDER = "plunder";
    static final String WING_MINE = "mine";
    static final String WING_FOREST = "forest";

    /** Per-player counters (profile key for the Mythic counters, as they are progress). */
    private static final class State {
        int worldbreaker;
        int ashen;
        long treefellerReady;
        GatherMath.Budget budget;
    }

    private final EnchantsModule module;
    private final BookDrops books;
    private final SpeedBursts bursts;
    private final ProcBar bar;
    private final StackPerf perf;
    private final Map<UUID, State> states = new HashMap<>();

    DepthsEnchants(EnchantsModule module, BookDrops books, SpeedBursts bursts, ProcBar bar, StackPerf perf) {
        this.module = module;
        this.books = books;
        this.bursts = bursts;
        this.bar = bar;
        this.perf = perf;
    }

    // ================================================================== blocks and logs

    @EventHandler(priority = EventPriority.NORMAL)
    public void onDrop(DepthsDropEvent event) {
        Pool pool = WING_MINE.equals(event.getWing()) ? Pool.MINING : WING_FOREST.equals(event.getWing()) ? Pool.FORAGING : null;
        if (pool == null) {
            return;
        }
        long start = perf.start();
        try {
            Player player = event.getPlayer();
            ToolInfo tool = module.tool(event.getTool());
            ToolKind needed = pool == Pool.MINING ? ToolKind.PICKAXE : ToolKind.AXE;
            if (tool == null || tool.kind() != needed) {
                return; // books and effects need the wing's custom tool (§3.1)
            }
            Map<String, Integer> enchants = module.working(tool);
            long now = System.currentTimeMillis();
            ThreadLocalRandom random = ThreadLocalRandom.current();
            int baseItems = event.getItems();
            long baseCrystals = event.getCrystals();
            double actions = 1;
            if (!enchants.isEmpty()) {
                bursts.action(player, tool, enchants, now);
                BonusService bonus = module.core.services().optional(BonusService.class).orElse(null);
                BonusContext ctx = new BonusContext(player, event.getTool(), tool, pool.skill().id());
                gilded(event, player, enchants, baseItems, bonus, random);
                geode(event, player, pool, enchants, ctx, bonus, random);
                actions += area(event, player, pool, enchants, baseItems, baseCrystals, now, random);
            }
            books.action(player, pool, event.getTier(), actions, event.getBlock().getLocation());
        } finally {
            perf.end(start);
        }
    }

    /** Gilded Touch: the drop counts twice (its value doubles), inside the sell family cap. */
    private void gilded(DepthsDropEvent event, Player player, Map<String, Integer> enchants, int baseItems, BonusService bonus,
                        ThreadLocalRandom random) {
        int level = enchants.getOrDefault(GILDED, 0);
        if (level <= 0 || baseItems <= 0) {
            return;
        }
        double chance = module.num(GILDED, "chance", level);
        double multiplier = Math.max(1, module.num(GILDED, "multiplier", level));
        double effective = chance;
        if (bonus != null) {
            effective = GatherMath.cappedChance(chance, chance * (multiplier - 1),
                    bonus.total(BonusFamily.SELL, BonusContext.of(player).withActivity("sell")), bonus.cap(BonusFamily.SELL));
        }
        if (GatherMath.roll(effective, random)) {
            event.setItems(event.getItems() + (int) Math.round(baseItems * (multiplier - 1)));
            bar.show(player, "proc.gilded", module.config().soundGatherProc, Msg.p("enchant", module.text().displayName(GILDED, level)));
        }
    }

    /** Geode Sense (mine) / Amber Sap (forest): a find worth N× the tier's average crystals per block, inside the crystals cap. */
    private void geode(DepthsDropEvent event, Player player, Pool pool, Map<String, Integer> enchants, BonusContext ctx,
                       BonusService bonus, ThreadLocalRandom random) {
        String id = pool == Pool.MINING ? GEODE : AMBER;
        int level = enchants.getOrDefault(id, 0);
        if (level <= 0) {
            return;
        }
        double chance = module.num(id, "chance", level);
        double worth = module.num(id, "worth", level);
        double effective = chance;
        if (bonus != null) {
            effective = GatherMath.cappedChance(chance, chance * worth, bonus.total(BonusFamily.CRYSTALS, ctx), bonus.cap(BonusFamily.CRYSTALS));
        }
        if (GatherMath.roll(effective, random)) {
            long crystals = GatherMath.geodeCrystals(event.getCrystalsPerBlock(), worth, module.config().geodeMinCrystals);
            event.setCrystals(event.getCrystals() + crystals);
            bar.show(player, "proc.geode", module.config().soundGatherProc, Msg.p("enchant", module.text().displayName(id, level)),
                    Msg.p("count", Format.number(crystals)));
        }
    }

    // ================================================================== area enchants

    /** Runs the tool's area enchants; returns the extra actions for book drops (blocks × yield). */
    private double area(DepthsDropEvent event, Player player, Pool pool, Map<String, Integer> enchants, int baseItems,
                        long baseCrystals, long now, ThreadLocalRandom random) {
        Depths depths = module.core.services().optional(Depths.class).orElse(null);
        if (depths == null) {
            return 0;
        }
        Block origin = event.getBlock();
        DepthsRegion region = depths.regionAt(origin.getLocation());
        if (region == null) {
            return 0;
        }
        State state = state(player);
        Area area = new Area(depths, region, player, origin, state, now);
        double actions = 0;
        if (pool == Pool.MINING) {
            int vein = enchants.getOrDefault(VEIN, 0);
            if (vein > 0 && GatherMath.roll(module.num(VEIN, "chance", vein), random)) {
                actions += proc(event, area, VEIN, vein, area.vein((int) module.num(VEIN, "count", vein), (int) module.num(VEIN, "radius", vein)),
                        baseItems, baseCrystals, random);
            }
            int blast = enchants.getOrDefault(DRAGONFIRE, 0);
            if (blast > 0 && GatherMath.roll(module.num(DRAGONFIRE, "chance", blast), random)) {
                actions += proc(event, area, DRAGONFIRE, blast, area.blast((int) module.num(DRAGONFIRE, "size", blast),
                        (int) module.num(DRAGONFIRE, "depth", blast)), baseItems, baseCrystals, random);
            }
            int mythic = enchants.getOrDefault(WORLDBREAKER, 0);
            if (mythic > 0) {
                state.worldbreaker = GatherMath.everyNth(state.worldbreaker, (int) module.num(WORLDBREAKER, "every", mythic));
                if (state.worldbreaker < 0) {
                    state.worldbreaker = 0;
                    actions += proc(event, area, WORLDBREAKER, mythic, area.blast((int) module.num(WORLDBREAKER, "size", mythic),
                            (int) module.num(WORLDBREAKER, "depth", mythic)), baseItems, baseCrystals, random);
                    long crystals = GatherMath.geodeCrystals(event.getCrystalsPerBlock(), module.num(GEODE, "worth", 1),
                            module.config().geodeMinCrystals);
                    event.setCrystals(event.getCrystals() + crystals); // the guaranteed Crystal Geode
                }
            }
        } else {
            int feller = enchants.getOrDefault(TREEFELLER, 0);
            if (feller > 0 && now >= state.treefellerReady) {
                state.treefellerReady = now + Math.round(module.num(TREEFELLER, "cooldown", feller) * 1000);
                actions += proc(event, area, TREEFELLER, feller, area.ownTree((int) module.num(TREEFELLER, "count", feller)),
                        baseItems, baseCrystals, random);
            }
            int fire = enchants.getOrDefault(WILDFIRE, 0);
            if (fire > 0 && GatherMath.roll(module.num(WILDFIRE, "chance", fire), random)) {
                actions += proc(event, area, WILDFIRE, fire, area.grove((int) module.num(WILDFIRE, "trees", fire),
                        (int) module.num(WILDFIRE, "radius", fire)), baseItems, baseCrystals, random);
            }
            int mythic = enchants.getOrDefault(ASHEN, 0);
            if (mythic > 0) {
                state.ashen = GatherMath.everyNth(state.ashen, (int) module.num(ASHEN, "every", mythic));
                if (state.ashen < 0) {
                    state.ashen = 0;
                    actions += proc(event, area, ASHEN, mythic, area.grove(Integer.MAX_VALUE, (int) module.num(ASHEN, "radius", mythic)),
                            baseItems, baseCrystals, random);
                }
            }
        }
        return actions;
    }

    /** Breaks the candidates (within the budget), adds their yield to the event and shows the proc. */
    private double proc(DepthsDropEvent event, Area area, String id, int level, List<Block> candidates, int baseItems, long baseCrystals,
                        ThreadLocalRandom random) {
        int broken = area.breakAll(candidates);
        if (broken <= 0) {
            return 0;
        }
        double yield = module.num(id, "yield", level);
        long items = GatherMath.areaYield(baseItems, yield, broken, random);
        if (items > 0) {
            event.setItems((int) Math.min(Integer.MAX_VALUE, event.getItems() + items));
        }
        long crystals = GatherMath.areaYield(baseCrystals, yield, broken, random);
        if (crystals > 0) {
            event.setCrystals(event.getCrystals() + crystals);
        }
        bar.show(event.getPlayer(), "proc.area", module.config().soundAreaProc, Msg.p("enchant", module.text().displayName(id, level)),
                Msg.p("count", Format.number(broken)));
        return GatherMath.areaActions(broken, yield);
    }

    /** One break's view of the area: the player's own, not yet depleted resource blocks of the same region. */
    private final class Area {
        private final Depths depths;
        private final DepthsRegion region;
        private final Player player;
        private final Block origin;
        private final World world;
        private final State state;
        private final long now;
        private final int max;

        Area(Depths depths, DepthsRegion region, Player player, Block origin, State state, long now) {
            this.depths = depths;
            this.region = region;
            this.player = player;
            this.origin = origin;
            this.world = origin.getWorld();
            this.state = state;
            this.now = now;
            this.max = Math.max(1, module.config().areaMaxBlocks);
        }

        /** A resource block of this region at x/y/z that this player still sees (not the origin). */
        boolean member(int x, int y, int z, Material only) {
            if (x == origin.getX() && y == origin.getY() && z == origin.getZ()) {
                return false;
            }
            Material type = world.getType(x, y, z);
            if ((only != null && type != only) || region.resource(type) == null
                    || depths.regionAt(world.getName(), x, y, z) != region) {
                return false;
            }
            return !depths.isDepleted(player, world.getBlockAt(x, y, z));
        }

        LongPredicate members() {
            return k -> member(AreaShapes.x(k), AreaShapes.y(k), AreaShapes.z(k), null);
        }

        /** Vein Rend: the nearest same ores within the radius. */
        List<Block> vein(int count, int radius) {
            List<Block> out = new ArrayList<>();
            Material type = origin.getType();
            int limit = Math.min(count, max);
            for (int[] o : AreaShapes.nearest(radius)) {
                if (out.size() >= limit) {
                    break;
                }
                int x = origin.getX() + o[0];
                int y = origin.getY() + o[1];
                int z = origin.getZ() + o[2];
                if (member(x, y, z, type)) {
                    out.add(world.getBlockAt(x, y, z));
                }
            }
            return out;
        }

        /** Dragonfire Blast / Worldbreaker: size × size × depth away from the player. */
        List<Block> blast(int size, int depth) {
            Vector look = player.getEyeLocation().getDirection();
            int[] f = AreaShapes.facing(look.getX(), look.getY(), look.getZ());
            List<Block> out = new ArrayList<>();
            for (int[] o : AreaShapes.blast(size, depth, f[0], f[1], f[2])) {
                if (out.size() >= max) {
                    break;
                }
                int x = origin.getX() + o[0];
                int y = origin.getY() + o[1];
                int z = origin.getZ() + o[2];
                if (member(x, y, z, null)) {
                    out.add(world.getBlockAt(x, y, z));
                }
            }
            return out;
        }

        /** Treefeller: up to {@code count} more logs of the tree that was cut. */
        List<Block> ownTree(int count) {
            long start = AreaShapes.key(origin.getX(), origin.getY(), origin.getZ());
            return blocks(AreaShapes.fill(start, Math.min(count, max), members()));
        }

        /** Wildfire / Ashen Grove: the cut tree and up to {@code trees} nearest other trees within the radius. */
        List<Block> grove(int trees, int radius) {
            EnchanterConfig c = module.config();
            long start = AreaShapes.key(origin.getX(), origin.getY(), origin.getZ());
            Set<Long> visited = new HashSet<>();
            LongPredicate members = members();
            List<Long> keys = new ArrayList<>(AreaShapes.tree(start, Math.min(c.areaTreeMaxLogs, max), k -> k == start || members.test(k), visited));
            keys.remove(Long.valueOf(start));
            int left = max - keys.size();
            if (trees > 0 && left > 0) {
                for (List<Long> tree : AreaShapes.trees(origin.getX(), origin.getY(), origin.getZ(), radius, c.areaScanDown, c.areaScanUp,
                        trees, c.areaTreeMaxLogs, left, members, visited)) {
                    keys.addAll(tree);
                }
            }
            return blocks(keys);
        }

        private List<Block> blocks(List<Long> keys) {
            List<Block> out = new ArrayList<>(keys.size());
            for (long k : keys) {
                out.add(world.getBlockAt(AreaShapes.x(k), AreaShapes.y(k), AreaShapes.z(k)));
            }
            return out;
        }

        /** Depletes the candidates for this player only, within the budget; returns how many were broken. */
        int breakAll(List<Block> candidates) {
            if (candidates.isEmpty()) {
                return 0;
            }
            EnchanterConfig c = module.config();
            if (state.budget == null) {
                state.budget = new GatherMath.Budget(c.areaMaxBlocks, now);
            }
            int allowed = state.budget.take(Math.min(candidates.size(), max), now, c.areaMaxPerSecond, c.areaMaxBlocks);
            int broken = 0;
            for (int i = 0; i < allowed; i++) {
                Block block = candidates.get(i);
                ResourceBlock resource = region.resource(block.getType());
                if (resource != null && depths.deplete(player, block, resource.respawnTicks())) {
                    broken++;
                }
            }
            return broken;
        }
    }

    // ================================================================== combat kills

    @EventHandler(priority = EventPriority.NORMAL)
    public void onKill(DepthsMobKillEvent event) {
        long start = perf.start();
        try {
            Player player = event.getPlayer();
            ToolInfo tool = module.tool(player.getInventory().getItemInMainHand());
            if (tool == null || tool.kind() != ToolKind.SWORD) {
                return; // Combat books and Plunder need a custom sword (§3.1)
            }
            Location at = event.getEntity().getLocation();
            if (event.isMiniBoss()) {
                books.miniBoss(player, event.getTier(), at);
            } else {
                books.action(player, Pool.COMBAT, event.getTier(), 1, at);
            }
            plunder(event, player, tool);
        } finally {
            perf.end(start);
        }
    }

    /** Plunder: a chance for one extra loot roll (the same items again), inside the extra drops family cap. */
    private void plunder(DepthsMobKillEvent event, Player player, ToolInfo tool) {
        int level = module.working(tool).getOrDefault(PLUNDER, 0);
        if (level <= 0 || event.getItems().isEmpty()) {
            return;
        }
        double chance = module.num(PLUNDER, "chance", level);
        BonusService bonus = module.core.services().optional(BonusService.class).orElse(null);
        double effective = chance;
        if (bonus != null) {
            BonusContext ctx = new BonusContext(player, player.getInventory().getItemInMainHand(), tool, Pool.COMBAT.skill().id());
            effective = GatherMath.cappedChance(chance, chance, bonus.total(BonusFamily.EXTRA_DROPS, ctx), bonus.cap(BonusFamily.EXTRA_DROPS));
        }
        if (GatherMath.roll(effective, ThreadLocalRandom.current())) {
            event.getItems().replaceAll((id, n) -> n == null ? 0 : (int) Math.min(Integer.MAX_VALUE, (long) n * 2));
        }
    }

    // ================================================================== state

    private State state(Player player) {
        return states.computeIfAbsent(ProfileService.of(module.core.services()).profileKey(player), k -> new State());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        states.remove(ProfileService.of(module.core.services()).profileKey(event.getPlayer()));
    }
}
