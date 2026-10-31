package net.skybornmc.skyblock.tools;

import net.skybornmc.skyblock.common.island.IslandService;
import net.skybornmc.skyblock.common.production.FarmingService;
import net.skybornmc.skyblock.common.production.IslandCropHarvestEvent;
import net.skybornmc.skyblock.common.skills.SkillService;
import net.skybornmc.skyblock.common.skills.SkillType;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.tools.HarvesterHook;
import net.skybornmc.skyblock.common.tools.HarvesterSwingEvent;
import net.skybornmc.skyblock.common.tools.ToolInfo;
import net.skybornmc.skyblock.common.tools.ToolKind;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.player.PlayerHarvestBlockEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

/**
 * The harvester hoe (PHASE-7 M7.4, owner V6 / PD13) and hoe progress, only while farming is on (a {@link FarmingService}
 * is registered):
 * <ul>
 *   <li><b>Area harvest:</b> right-click a crop with a custom hoe on your own island (stored member, survival) → every ripe
 *       crop in the square around it (3×3 / 5×5 / 7×7 by hoe level, + Wide Reap rings) is broken with
 *       {@link Player#breakBlock}, so protection, island roles, Farming XP (PD11), bone-meal rules, Harvest Yield, quests
 *       and stats all run exactly as for a crop broken by hand, once per crop. AGE crops are replanted from one of their
 *       own seeds (drops first, then the inventory) and only when the player's Farming level may plant them (PD10).</li>
 *   <li><b>Tool XP:</b> the only hoe XP source is {@link IslandCropHarvestEvent} (fired once per ripe harvest by the
 *       production module, with a hoe and no bone meal, members on their own island only), so an area swing can never pay
 *       a crop twice.</li>
 *   <li><b>Extra crop drops:</b> +{@code fortune.per-tier-percent} per Forge tier above 1 (and the extra-drops family) on
 *       the drops of those same ripe harvests and on picked ripe sweet berries; seeds excluded; never on placed blocks.</li>
 * </ul>
 * Main thread. A swing is bounded by the max area (81 blocks) and a per-player cooldown.
 */
final class Harvester implements Listener {

    private static final int MAX_COLUMN = 32;
    private static final BlockFace[] SIDES = {BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST};

    /** A crop the harvester knows, resolved to materials. */
    private record Crop(HarvestRules.Crop rule, Material block, Material extra) {
    }

    /** One block of a swing. */
    private record Target(Block block, HarvestRules.Action action, Material seed) {
    }

    /** The seed a replant may take from the drops of the block being broken right now. */
    private static final class PendingSeed {
        final UUID world;
        final int x;
        final int y;
        final int z;
        final Material seed;
        boolean taken;

        PendingSeed(Block b, Material seed) {
            this.world = b.getWorld().getUID();
            this.x = b.getX();
            this.y = b.getY();
            this.z = b.getZ();
            this.seed = seed;
        }

        boolean at(Block b) {
            return b.getX() == x && b.getY() == y && b.getZ() == z && b.getWorld().getUID().equals(world);
        }
    }

    /** Ripe harvest just reported by the production module whose drops get the hoe bonus (same tick only). */
    private record DropMark(UUID world, int x, int y, int z, double multiplier, int tick) {
    }

    private final ToolsModule module;
    private Map<Material, Crop> crops = new EnumMap<>(Material.class);
    private Set<Material> noBonus = EnumSet.noneOf(Material.class);
    private List<Integer> sizeLevels = List.of(1, 15, 30);
    private final Map<UUID, Long> lastSwing = new HashMap<>();
    private PendingSeed pending;
    private DropMark mark;

    Harvester(ToolsModule module) {
        this.module = module;
    }

    /** (Re)reads the harvester lists from tools.yml (enable and reload). */
    void apply() {
        ToolsConfig c = module.config();
        Map<String, HarvestRules.Crop> rules = HarvestRules.crops(c.harvesterReplant, c.harvesterColumns, c.harvesterFruits,
                p -> module.core.plugin().getLogger().warning(p));
        Map<Material, Crop> out = new EnumMap<>(Material.class);
        rules.forEach((id, rule) -> {
            Material block = Material.matchMaterial(id);
            Material extra = rule.extra().isEmpty() ? null : Material.matchMaterial(rule.extra());
            boolean extraOk = switch (rule.shape()) {
                case AGE -> extra != null && extra.isItem();
                case FRUIT -> extra != null && extra.isBlock();
                case COLUMN -> true;
            };
            if (block == null || !block.isBlock() || !extraOk) {
                module.core.plugin().getLogger().warning("tools.yml harvester: '" + id + "' needs a block id"
                        + (rule.shape() == HarvestRules.Shape.COLUMN ? "" : " and a valid " + (rule.shape() == HarvestRules.Shape.AGE ? "seed item" : "stem block"))
                        + "; skipped");
                return;
            }
            out.put(block, new Crop(rule, block, extra));
        });
        Set<Material> skip = EnumSet.noneOf(Material.class);
        for (String id : c.harvesterNoBonusItems) {
            Material m = Material.matchMaterial(String.valueOf(id).trim());
            if (m != null) {
                skip.add(m);
            }
        }
        List<Integer> levels = HarvestRules.levels(c.harvesterSizeLevels);
        crops = out;
        noBonus = skip;
        sizeLevels = levels.isEmpty() ? List.of(1, 15, 30) : levels;
    }

    // ------------------------------------------------------------------ helpers

    private boolean farmingLive() {
        return FarmingService.of(module.core.services()).isPresent();
    }

    /** The island of the block if the player is a stored member there (never coop players or visitors), else empty. */
    private Optional<IslandService.IslandInfo> ownIsland(Player player, Block block) {
        Optional<IslandService> islands = module.core.services().optional(IslandService.class);
        if (islands.isEmpty() || !islands.get().isIslandWorld(block.getWorld())) {
            return Optional.empty();
        }
        Optional<IslandService.IslandInfo> here = islands.get().islandAt(block.getLocation());
        UUID uuid = player.getUniqueId();
        return here.filter(i -> islands.get().islandOf(uuid).map(o -> o.id().equals(i.id())).orElse(false)
                && islands.get().countsForIsland(i.id(), uuid));
    }

    private int farmingLevel(Player player) {
        return module.core.services().optional(SkillService.class).map(s -> s.level(player, SkillType.FARMING)).orElse(0);
    }

    private Optional<HarvesterHook> hook() {
        return module.core.services().optional(HarvesterHook.class);
    }

    private ToolInfo info(ToolData tool) {
        return tool.info(module.config().slotLevels());
    }

    /** Side of the area for this hoe and player (level steps + Wide Reap rings, capped). */
    int size(Player player, ToolData tool) {
        int rings = 0;
        Optional<HarvesterHook> h = hook();
        if (h.isPresent()) {
            try {
                rings = h.get().extraRings(player, info(tool));
            } catch (RuntimeException e) {
                module.core.plugin().getLogger().log(Level.WARNING, "Tools: harvester hook failed (extra rings)", e);
            }
        }
        return HarvestRules.size(tool.level(), sizeLevels, rings, module.config().harvesterMaxSize);
    }

    /** Area side for lore (no player, no enchant rings). */
    int baseSize(ToolData tool) {
        return HarvestRules.size(tool.level(), sizeLevels, 0, module.config().harvesterMaxSize);
    }

    private static boolean sameTool(ItemStack stack, UUID toolId) {
        return ToolItems.read(stack).map(d -> d.id().equals(toolId)).orElse(false);
    }

    // ------------------------------------------------------------------ area harvest

    @EventHandler(priority = EventPriority.HIGH)
    public void onUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getAction() != Action.RIGHT_CLICK_BLOCK
                || event.useInteractedBlock() == Event.Result.DENY || !module.config().harvesterEnabled) {
            return;
        }
        Player player = event.getPlayer();
        Block clicked = event.getClickedBlock();
        if (clicked == null || player.isSneaking() || player.getGameMode() != GameMode.SURVIVAL || !crops.containsKey(clicked.getType())) {
            return;
        }
        Optional<ToolData> tool = ToolItems.read(player.getInventory().getItemInMainHand());
        if (tool.isEmpty() || tool.get().kind() != ToolKind.HOE || !farmingLive()) {
            return;
        }
        Optional<IslandService.IslandInfo> island = ownIsland(player, clicked);
        if (island.isEmpty()) {
            return; // visitors and coop players break crops by hand (vanilla)
        }
        event.setCancelled(true);
        long now = System.currentTimeMillis();
        Long before = lastSwing.get(player.getUniqueId());
        if (before != null && now - before < module.config().harvesterCooldownMs) {
            return;
        }
        lastSwing.put(player.getUniqueId(), now);
        swing(player, tool.get(), clicked, island.get());
    }

    private void swing(Player player, ToolData tool, Block center, IslandService.IslandInfo island) {
        int size = size(player, tool);
        List<Target> targets = plan(player, center, island, size);
        UUID toolId = tool.id();
        int done = 0;
        for (Target t : targets) {
            if (!sameTool(player.getInventory().getItemInMainHand(), toolId) || !player.isOnline()) {
                break; // the hoe left the hand (a level-up never changes the id)
            }
            Block b = t.block();
            if (t.action() == HarvestRules.Action.HARVEST_REPLANT) {
                BlockData before = b.getBlockData().clone();
                PendingSeed seed = new PendingSeed(b, t.seed());
                pending = seed;
                boolean broke;
                try {
                    broke = player.breakBlock(b);
                } finally {
                    pending = null;
                }
                if (!broke) {
                    continue;
                }
                done++;
                if (b.getType().isAir() && (seed.taken || takeSeed(player.getInventory(), t.seed()))) {
                    replant(player, tool, b, before);
                }
            } else if (player.breakBlock(b)) {
                done++;
            }
        }
        if (done <= 0) {
            return;
        }
        ToolData after = ToolItems.read(player.getInventory().getItemInMainHand()).filter(d -> d.id().equals(toolId)).orElse(tool);
        Bukkit.getPluginManager().callEvent(new HarvesterSwingEvent(player, info(after), center, size, done));
        module.msg().actionBar(player, "harvester.reaped", Msg.p("count", done), Msg.p("size", size));
    }

    /** The blocks of one swing (each at most once), centre first. */
    private List<Target> plan(Player player, Block center, IslandService.IslandInfo island, int size) {
        World world = center.getWorld();
        int farming = farmingLevel(player);
        FarmingService farmingService = FarmingService.of(module.core.services()).orElse(null);
        List<Target> out = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        int y = center.getY();
        for (int[] o : HarvestRules.offsets(size)) {
            int x = center.getX() + o[0];
            int z = center.getZ() + o[1];
            if (!island.bounds().contains(x, z)) {
                continue;
            }
            Block b = world.getBlockAt(x, y, z);
            Crop crop = crops.get(b.getType());
            if (crop == null) {
                continue;
            }
            Target target = switch (crop.rule().shape()) {
                case AGE -> ageTarget(b, crop, farming, farmingService);
                case COLUMN -> columnTarget(b, crop);
                case FRUIT -> HarvestRules.decide(crop.rule(), HarvestRules.Cell.fruit(attached(b, crop.extra())), 0, farming)
                        == HarvestRules.Action.HARVEST ? new Target(b, HarvestRules.Action.HARVEST, null) : null;
            };
            if (target != null && seen.add(key(target.block()))) {
                out.add(target);
            }
        }
        return out;
    }

    private Target ageTarget(Block b, Crop crop, int farming, FarmingService farmingService) {
        if (!(b.getBlockData() instanceof Ageable age)) {
            return null;
        }
        int plantLevel = farmingService == null ? 0 : farmingService.plantLevel(b.getType());
        HarvestRules.Action action = HarvestRules.decide(crop.rule(), HarvestRules.Cell.age(age.getAge(), age.getMaximumAge()),
                plantLevel, farming);
        if (action != HarvestRules.Action.HARVEST_REPLANT) {
            return null;
        }
        Ageable fresh = (Ageable) age.clone();
        fresh.setAge(0);
        // Soil gone (trampled farmland, no log for cocoa): harvest it, but don't plant a crop that can't stand there.
        return new Target(b, action, fresh.isSupported(b) ? crop.extra() : null);
    }

    private static Target columnTarget(Block b, Crop crop) {
        Block base = b;
        for (int i = 0; i < MAX_COLUMN && base.getRelative(BlockFace.DOWN).getType() == crop.block(); i++) {
            base = base.getRelative(BlockFace.DOWN);
        }
        int height = 1;
        Block top = base;
        while (height < MAX_COLUMN && top.getRelative(BlockFace.UP).getType() == crop.block()) {
            top = top.getRelative(BlockFace.UP);
            height++;
        }
        return HarvestRules.decide(crop.rule(), HarvestRules.Cell.column(height), 0, 0) == HarvestRules.Action.HARVEST
                ? new Target(base.getRelative(BlockFace.UP), HarvestRules.Action.HARVEST, null) : null;
    }

    /** True if an attached stem next to the fruit points at it. */
    private static boolean attached(Block fruit, Material stem) {
        for (BlockFace face : SIDES) {
            Block side = fruit.getRelative(face);
            if (side.getType() == stem && side.getBlockData() instanceof Directional d && d.getFacing() == face.getOppositeFace()) {
                return true;
            }
        }
        return false;
    }

    private static long key(Block b) {
        return ((long) b.getX() & 0x3FFFFFF) << 38 | ((long) b.getZ() & 0x3FFFFFF) << 12 | ((long) b.getY() & 0xFFF);
    }

    /** One plain seed from the inventory (when the drops had none). */
    private static boolean takeSeed(PlayerInventory inv, Material seed) {
        if (seed == null) {
            return false;
        }
        String id = seed.getKey().getKey();
        if (Forge.count(inv, Set.of(id)).getOrDefault(id, 0) < 1) {
            return false;
        }
        Forge.take(inv, Map.of(id, 1));
        return true;
    }

    private void replant(Player player, ToolData tool, Block b, BlockData before) {
        if (!(before instanceof Ageable age)) {
            return;
        }
        int stages = 0;
        Optional<HarvesterHook> h = hook();
        if (h.isPresent()) {
            try {
                stages = h.get().replantStages(player, info(tool), before.getMaterial(), age.getMaximumAge());
            } catch (RuntimeException e) {
                module.core.plugin().getLogger().log(Level.WARNING, "Tools: harvester hook failed (replant stages)", e);
            }
        }
        age.setAge(HarvestRules.replantAge(stages, age.getMaximumAge()));
        b.setBlockData(age, true);
    }

    /** Takes the replant seed out of the drops of the block being broken (before auto-pickup at HIGHEST). */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSeedDrop(BlockDropItemEvent event) {
        PendingSeed p = pending;
        if (p == null || p.taken || p.seed == null || !p.at(event.getBlock())) {
            return;
        }
        Iterator<Item> it = event.getItems().iterator();
        while (it.hasNext()) {
            Item item = it.next();
            ItemStack stack = item.getItemStack();
            if (stack.getType() != p.seed || !ToolsListener.ItemStackHelper.isPlain(stack)) {
                continue;
            }
            if (stack.getAmount() > 1) {
                stack.setAmount(stack.getAmount() - 1);
                item.setItemStack(stack);
            } else {
                it.remove();
            }
            p.taken = true;
            return;
        }
    }

    // ------------------------------------------------------------------ hoe XP and extra drops

    /** The only hoe tool XP source (once per ripe harvest, area swings included), plus the drop mark for the bonus. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onCropHarvest(IslandCropHarvestEvent event) {
        Player player = event.getPlayer();
        ItemStack hand = player.getInventory().getItemInMainHand();
        Optional<ToolData> held = ToolItems.read(hand);
        Optional<ToolData> used = ToolItems.read(event.tool());
        if (held.isEmpty() || used.isEmpty() || !held.get().id().equals(used.get().id()) || held.get().kind() != ToolKind.HOE) {
            return;
        }
        double multiplier = module.items().fortune(held.get());
        Block b = event.block();
        mark = multiplier > 1 ? new DropMark(b.getWorld().getUID(), b.getX(), b.getY(), b.getZ(), multiplier, Bukkit.getCurrentTick()) : null;
        double xp = HarvestRules.toolXp(event.xpEligible(), held.get().kind(), module.config().tierXp(event.tier()), event.crops(),
                module.config().cropXpFactor);
        if (xp > 0) {
            module.tracker().gain(player, xp, ToolData.STAT_CROPS, event.crops());
        }
    }

    /** Extra crop drops on the block of the harvest just reported (fires right after it, before Harvest Yield at LOW). */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCropDrops(BlockDropItemEvent event) {
        DropMark m = mark;
        mark = null;
        Block b = event.getBlock();
        if (m == null || m.tick() != Bukkit.getCurrentTick() || m.x() != b.getX() || m.y() != b.getY() || m.z() != b.getZ()
                || !m.world().equals(b.getWorld().getUID())) {
            return;
        }
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (Item item : event.getItems()) {
            ItemStack stack = item.getItemStack();
            if (boost(stack, m.multiplier(), random.nextDouble())) {
                item.setItemStack(stack);
            }
        }
    }

    /** Extra drops on ripe sweet berries picked with a custom hoe on your own island (before Harvest Yield at HIGH). */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPick(PlayerHarvestBlockEvent event) {
        Player player = event.getPlayer();
        Block b = event.getHarvestedBlock();
        if (!(b.getBlockData() instanceof Ageable age) || age.getAge() < age.getMaximumAge() || !farmingLive()) {
            return;
        }
        Optional<ToolData> held = ToolItems.read(player.getInventory().getItemInMainHand());
        if (held.isEmpty() || held.get().kind() != ToolKind.HOE || ownIsland(player, b).isEmpty()) {
            return;
        }
        double multiplier = module.items().fortune(held.get());
        if (multiplier <= 1) {
            return;
        }
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (ItemStack stack : event.getItemsHarvested()) {
            boost(stack, multiplier, random.nextDouble());
        }
    }

    /** Raises a plain crop stack by the multiplier; false if it was left alone (seeds, custom items). */
    private boolean boost(ItemStack stack, double multiplier, double roll) {
        if (stack == null || stack.isEmpty() || noBonus.contains(stack.getType()) || !ToolsListener.ItemStackHelper.isPlain(stack)) {
            return false;
        }
        int amount = HarvestRules.boostedAmount(stack.getAmount(), multiplier, module.config().harvesterMaxDropMultiplier,
                stack.getMaxStackSize(), roll);
        if (amount == stack.getAmount()) {
            return false;
        }
        stack.setAmount(amount);
        return true;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        lastSwing.remove(event.getPlayer().getUniqueId());
    }
}
