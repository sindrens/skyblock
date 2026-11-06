package net.skybornmc.skyblock.islandtools;

import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.audit.AuditCategory;
import net.skybornmc.skyblock.common.bonus.BonusContext;
import net.skybornmc.skyblock.common.bonus.BonusFamily;
import net.skybornmc.skyblock.common.bonus.BonusService;
import net.skybornmc.skyblock.common.profile.ProfileSwitchedEvent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Everything the island tools do in the world (owner 4 Nov 2026 rework): only a handle of the holder's own island works
 * (others are removed), tool XP / shards / Lucky on relevant natural blocks, the tool's Extra Drops plus the island's
 * farm/mine boost, Smelter, Sweep (hoe) and Vein (pickaxe), Auto-Sell, the hoe storage, Magnet for every drop (built
 * in), Haste and the hoe's Speed while held, kept on death and never dropped.
 */
final class ToolListener implements Listener {

    /** A break by an island tool, waiting for its drops. */
    private record Pending(Player player, Kind kind, UUID island, boolean crop, boolean burst, boolean natural, boolean swept,
                           boolean area) {
    }

    private static final Map<Material, Material> SMELT = Map.of(
            Material.RAW_IRON, Material.IRON_INGOT, Material.RAW_GOLD, Material.GOLD_INGOT, Material.RAW_COPPER, Material.COPPER_INGOT,
            Material.COBBLESTONE, Material.STONE, Material.SAND, Material.GLASS, Material.COBBLED_DEEPSLATE, Material.DEEPSLATE);

    private final IslandToolsModule m;
    /** Blocks players placed in the tool worlds (no bonus for breaking your own blocks). */
    private final net.skybornmc.skyblock.common.world.PlacedBlocks placed;
    private int placeCount;
    private final Map<Location, Pending> pending = new HashMap<>();
    private final Set<UUID> expanding = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Long> lastWarn = new ConcurrentHashMap<>();
    private final Map<UUID, List<ItemStack>> keptOnDeath = new ConcurrentHashMap<>();
    /** Players holding our Speed effect (removed as soon as they stop holding the hoe). */
    private final Set<UUID> speedGiven = ConcurrentHashMap.newKeySet();

    ToolListener(IslandToolsModule m) {
        this.m = m;
        this.placed = new net.skybornmc.skyblock.common.world.PlacedBlocks(m.config().placedHours * 3_600_000L, 200_000);
    }

    private void warn(Player p, String key) {
        long now = System.currentTimeMillis();
        Long last = lastWarn.put(p.getUniqueId(), now);
        if (last == null || now - last > 3000) {
            m.msg().send(p, key);
        }
    }

    /** Islands and the /farm and /mine world (tool-worlds): the tools level up and use their perks there. */
    private boolean toolWorld(Player p) {
        return m.islands().map(i -> i.isIslandWorld(p.getWorld())).orElse(false)
                || m.config().toolWorlds.stream().anyMatch(w -> w.equalsIgnoreCase(p.getWorld().getName()));
    }

    /** The /farm and /mine world (tool-worlds): loot never lands on the ground there. */
    boolean grindWorld(Player p) {
        return m.config().toolWorlds.stream().anyMatch(w -> w.equalsIgnoreCase(p.getWorld().getName()));
    }

    /**
     * The kind of the island tool in hand when it is a working handle of the player's island in a tool world; removes a
     * handle of another island (or an old axe/sword). Null for anything else (a handle outside the tool worlds works as
     * a plain diamond tool).
     */
    private Kind usable(Player p, ItemStack hand, org.bukkit.event.Cancellable event) {
        if (!ToolItems.isTool(hand)) {
            return null;
        }
        Kind kind = ToolItems.kindOf(hand);
        UUID island = m.island(p);
        UUID of = ToolItems.islandOf(hand);
        if (kind == null || island == null || (of != null && !of.equals(island))) {
            if (event != null) {
                event.setCancelled(true);
            }
            m.check(p);
            return null;
        }
        if (of == null) {
            m.check(p); // an item from before the handles: becomes the island's handle
            return null;
        }
        if (!toolWorld(p) || !m.loaded(p)) {
            return null;
        }
        return kind;
    }

    private boolean relevant(Kind kind, String blockId) {
        return kind == Kind.HOE ? Relevant.matches(m.config().relevantHoe, blockId) : Relevant.matches(m.config().relevantPickaxe, blockId);
    }

    /** A ripe crop of the hoe's list (also melons, cane ... which have no age). */
    private boolean ripe(Block b) {
        if (!relevant(Kind.HOE, b.getType().getKey().getKey())) {
            return false;
        }
        return !(b.getBlockData() instanceof Ageable a) || a.getAge() >= a.getMaximumAge();
    }

    // ------------------------------------------------------------------ blocks

    /** True when the storage slots 0-35 (hotbar too) have no empty slot (the drops would not fit in the bag). */
    static boolean bagFull(Player p) {
        org.bukkit.inventory.ItemStack[] storage = p.getInventory().getStorageContents();
        for (org.bukkit.inventory.ItemStack s : storage) {
            if (s == null || s.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Player p = event.getPlayer();
        Kind kind = usable(p, p.getInventory().getItemInMainHand(), event);
        if (kind == null || kind == Kind.ROD || kind == Kind.SWORD) {
            return; // the rod and the sword break blocks like plain items
        }
        Block block = event.getBlock();
        // Owner 7 Nov 2026: at /farm and /mine the drops fill the inventory first, then the tool's storage; only when
        // both are full does the breaking pause (nothing is lost; shards keep coming while there is room).
        if (grindWorld(p) && !expanding.contains(p.getUniqueId()) && bagFull(p)
                && m.storage().used(m.profile(p), kind) >= m.storage().capacity(p, kind)) {
            event.setCancelled(true);
            m.msg().actionBar(p, "storage.paused");
            return;
        }
        // Owner 3 Nov 2026: the island hoe never breaks a crop that is not fully grown.
        if (kind == Kind.HOE && IslandGrowth.CROPS.contains(block.getType())
                && block.getBlockData() instanceof Ageable age && age.getAge() < age.getMaximumAge()) {
            event.setCancelled(true);
            m.msg().actionBar(p, "hoe.not-ripe");
            return;
        }
        UUID island = m.island(p);
        UUID at = m.islandAt(block.getLocation());
        boolean crop = kind == Kind.HOE && island.equals(at) && HoeFarming.ripeCrop(block.getBlockData());
        boolean nested = expanding.contains(p.getUniqueId());
        boolean burst = crop && !nested && ThreadLocalRandom.current().nextDouble(100) < m.farming().burstPercent(island);
        Location key = block.getLocation();
        boolean natural = !placed.isPlaced(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ(), System.currentTimeMillis())
                && !net.skybornmc.skyblock.common.world.ChunkMarks.marked(block); // FX-17: survives restarts
        placed.forget(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
        net.skybornmc.skyblock.common.world.ChunkMarks.unmark(block);
        // Owner 6 Nov 2026: a Blast/Explode block is drops only (no tool XP, shards, enchants or /farm /mine progress).
        boolean area = net.skybornmc.skyblock.common.grind.GrindProgress.dropsOnly(p);
        pending.put(key, new Pending(p, kind, island, crop, burst, natural, nested, area));
        Bukkit.getScheduler().runTask(m.core().plugin(), () -> pending.remove(key)); // no drop event (nothing dropped): forget it
        if (nested) {
            return;
        }
        if (burst) {
            m.farming().burstShow(p, block.getLocation());
        }
        boolean counts = kind == Kind.HOE ? ripe(block) : natural && relevant(kind, block.getType().getKey().getKey());
        double chance = counts ? m.sweepChance(p, kind) * net.skybornmc.skyblock.common.grind.GrindEvents.multiplier(m.core().services(), p,
                net.skybornmc.skyblock.common.grind.GrindEvents.Kind.PROC_FRENZY) : 0; // owner 6 Nov 2026: Proc Frenzy
        if (chance > 0 && ThreadLocalRandom.current().nextDouble(100) < chance) {
            ToolRules.Band band = m.band(p, kind);
            sweep(p, block, kind, band == null ? 0 : band.blocks());
        }
    }

    /**
     * Owner 4 Nov 2026: Sweep (hoe) harvests up to {@code count} nearby ripe crops, Vein (pickaxe) breaks up to
     * {@code count} nearby blocks of the same ore; with the player's own break (protection, milestones and drops apply),
     * normal drops and tool XP but no extra shard rolls ({@code sweep.shards}).
     */
    private void sweep(Player p, Block origin, Kind kind, int count) {
        if (count <= 0) {
            return;
        }
        Material type = origin.getType();
        int r = m.config().sweepRadius;
        List<Block> found = new ArrayList<>();
        java.util.function.Predicate<Block> marks = net.skybornmc.skyblock.common.world.ChunkMarks.reader();
        for (int d = 1; d <= r && found.size() < count; d++) {
            for (int dx = -d; dx <= d && found.size() < count; dx++) {
                for (int dz = -d; dz <= d && found.size() < count; dz++) {
                    for (int dy = kind == Kind.HOE ? 0 : -d; dy <= (kind == Kind.HOE ? 0 : d) && found.size() < count; dy++) {
                        if (Math.max(Math.abs(dx), Math.max(Math.abs(dz), Math.abs(dy))) != d) {
                            continue; // ring by ring, nearest first
                        }
                        Block b = origin.getRelative(dx, dy, dz);
                        boolean ok = kind == Kind.HOE ? ripe(b)
                                : b.getType() == type && !placed.isPlaced(b.getWorld().getUID(), b.getX(), b.getY(), b.getZ(), System.currentTimeMillis())
                                && !marks.test(b);
                        if (ok) {
                            found.add(b);
                        }
                    }
                }
            }
        }
        if (found.isEmpty()) {
            return;
        }
        Bukkit.getScheduler().runTask(m.core().plugin(), () -> {
            if (!p.isOnline() || !expanding.add(p.getUniqueId())) {
                return;
            }
            try {
                for (Block b : found) {
                    if (kind == Kind.HOE ? ripe(b) : b.getType() == type) {
                        p.breakBlock(b);
                    }
                }
            } finally {
                expanding.remove(p.getUniqueId());
            }
            m.msg().actionBar(p, kind == Kind.HOE ? "sweep.hoe" : "sweep.pickaxe", net.skybornmc.skyblock.common.text.Msg.number("amount", found.size()));
        });
    }

    /**
     * Owner 6 Nov 2026, Blast (pickaxe) / Explode (hoe): the player breaks up to {@code count} blocks around
     * {@code origin} next tick, drops only. The pickaxe takes any natural relevant block in a cube, the hoe ripe crops on
     * its layer; the grind areas refuse blocks outside them. Returns how many blocks were found.
     */
    int blast(Player p, Block origin, Kind kind, int count) {
        if (count <= 0) {
            return 0;
        }
        int r = Math.max(2, m.config().sweepRadius);
        long now = System.currentTimeMillis();
        List<Block> found = new ArrayList<>();
        java.util.function.Predicate<Block> marks = net.skybornmc.skyblock.common.world.ChunkMarks.reader();
        for (int d = 1; d <= r && found.size() < count; d++) {
            for (int dx = -d; dx <= d && found.size() < count; dx++) {
                for (int dz = -d; dz <= d && found.size() < count; dz++) {
                    for (int dy = kind == Kind.HOE ? 0 : -d; dy <= (kind == Kind.HOE ? 0 : d) && found.size() < count; dy++) {
                        if (Math.max(Math.abs(dx), Math.max(Math.abs(dz), Math.abs(dy))) != d) {
                            continue;
                        }
                        Block b = origin.getRelative(dx, dy, dz);
                        boolean ok = kind == Kind.HOE ? ripe(b) : !b.getType().isAir() && b.getType() != Material.BEDROCK
                                && relevant(kind, b.getType().getKey().getKey())
                                && !placed.isPlaced(b.getWorld().getUID(), b.getX(), b.getY(), b.getZ(), now) && !marks.test(b);
                        if (ok) {
                            found.add(b);
                        }
                    }
                }
            }
        }
        if (found.isEmpty()) {
            return 0;
        }
        int per = Math.max(1, m.config().enchantAreaPerTick);
        for (int from = 0, tick = 1; from < found.size(); from += per, tick++) {
            List<Block> part = found.subList(from, Math.min(found.size(), from + per));
            Bukkit.getScheduler().runTaskLater(m.core().plugin(), () -> {
                if (!p.isOnline() || !grindWorld(p) || ToolItems.kindOf(p.getInventory().getItemInMainHand()) != kind
                        || !expanding.add(p.getUniqueId())) {
                    return;
                }
                try {
                    net.skybornmc.skyblock.common.grind.GrindProgress.dropsOnly(p, () -> {
                        for (Block b : part) {
                            if (kind == Kind.HOE ? ripe(b) : !b.getType().isAir() && b.getType() != Material.BEDROCK) {
                                p.breakBlock(b);
                            }
                        }
                    });
                } finally {
                    expanding.remove(p.getUniqueId());
                }
            }, tick); // big areas (Nuke) are spread over a few ticks
        }
        return found.size();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrops(BlockDropItemEvent event) {
        Pending pd = pending.remove(event.getBlock().getLocation());
        if (pd == null) {
            return;
        }
        Player p = pd.player();
        Kind kind = pd.kind();
        String blockId = event.getBlockState().getType().getKey().getKey();
        if (!pd.crop() && (!pd.natural() || !relevant(kind, blockId))) {
            magnet(p, event); // owner 4 Nov 2026: Magnet is built in, also for plain blocks
            return;
        }
        if (!pd.swept() && grindWorld(p)) {
            m.enchants().proc(p, kind, event.getBlock(), event.getItems()); // owner 6 Nov 2026: personal grind enchants
        }
        if (!pd.area()) {
            m.action(p, kind, pd.swept(), event.getBlock().getLocation());
        } else {
            m.areaShards(p, kind, event.getBlock().getLocation()); // tools v5: area blocks give shards too (no tool XP)
        }
        UUID island = pd.island();
        int smelt = kind == Kind.PICKAXE ? m.effect(p, kind, Upgrades.Effect.SMELT) : 0;
        double mult = m.core().services().optional(BonusService.class).map(b -> b.multiplier(BonusFamily.EXTRA_DROPS,
                BonusContext.of(p, p.getInventory().getItemInMainHand(), "island_" + kind.id()).withTarget(blockId))).orElse(1.0);
        mult *= 1 + Math.max(0, m.islandBoost(p, kind)) / 100.0; // the island's farm/mine boost, stronger than any perk
        mult *= net.skybornmc.skyblock.common.grind.GrindEvents.multiplier(m.core().services(), p,
                net.skybornmc.skyblock.common.grind.GrindEvents.Kind.DOUBLE_DROPS); // owner 6 Nov 2026: Double Drops
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        for (Iterator<Item> it = event.getItems().iterator(); it.hasNext(); ) {
            Item entity = it.next();
            ItemStack stack = entity.getItemStack();
            if (smelt > 0) {
                Material to = SMELT.get(stack.getType());
                if (to != null && rnd.nextDouble(100) < smelt * m.config().smeltPerLevel) {
                    stack = stack.withType(to);
                }
            }
            if (mult > 1.0) {
                stack.setAmount(Math.max(1, Math.min(stack.getMaxStackSize() * 4, ToolRules.roll(stack.getAmount() * mult, rnd.nextDouble()))));
            }
            if (m.autoSell(p, kind, stack)) {
                it.remove(); // Auto-Sell: sold in the next batch
                continue;
            }
            // Owner 7 Nov 2026: the bag first, then the tool's storage, everywhere; at /farm and /mine never the ground.
            Map<Integer, ItemStack> left = p.getInventory().addItem(stack);
            if (left.isEmpty()) {
                it.remove();
                continue;
            }
            ItemStack rest = left.values().iterator().next();
            long stored = m.storage().add(p, kind, rest.getType().getKey().getKey(), rest.getAmount());
            if (stored >= rest.getAmount()) {
                it.remove();
                continue;
            }
            rest.setAmount(rest.getAmount() - (int) stored);
            warn(p, "storage.full");
            if (grindWorld(p)) {
                it.remove(); // full bag and storage at /farm or /mine: lost, like vanilla, never an item on the ground
            } else {
                entity.setItemStack(rest);
            }
        }
        if (pd.crop()) {
            BlockState before = event.getBlockState();
            m.farming().replant(event.getBlock().getLocation(), before.getType(), p, island, pd.burst());
        }
    }

    /** Magnet: the drops go straight into the bag (what does not fit stays on the ground, never at /farm and /mine). */
    private void magnet(Player p, BlockDropItemEvent event) {
        for (Iterator<Item> it = event.getItems().iterator(); it.hasNext(); ) {
            Item entity = it.next();
            Map<Integer, ItemStack> left = p.getInventory().addItem(entity.getItemStack());
            if (left.isEmpty() || grindWorld(p)) {
                it.remove();
            } else {
                entity.setItemStack(left.values().iterator().next());
            }
        }
    }

    /** Remembers player-placed blocks in the tool worlds (crops and saplings grow, so they are not remembered). */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(org.bukkit.event.block.BlockPlaceEvent event) {
        Block b = event.getBlockPlaced();
        if (!toolWorld(event.getPlayer()) || org.bukkit.Tag.CROPS.isTagged(b.getType()) || org.bukkit.Tag.SAPLINGS.isTagged(b.getType())) {
            return;
        }
        long now = System.currentTimeMillis();
        placed.placed(b.getWorld().getUID(), b.getX(), b.getY(), b.getZ(), now);
        String id = b.getType().getKey().getKey();
        if (relevant(Kind.PICKAXE, id) || relevant(Kind.HOE, id)) {
            net.skybornmc.skyblock.common.world.ChunkMarks.mark(b); // FX-17: only blocks a tool pays for, kept in the chunk
        }
        if (++placeCount % 2000 == 0) {
            placed.prune(now);
        }
    }

    /** FX-17: a placed ore pushed by a piston stays placed (no fresh "natural" block one block further). */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonExtend(org.bukkit.event.block.BlockPistonExtendEvent event) {
        moveMarks(event.getBlocks(), event.getDirection());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonRetract(org.bukkit.event.block.BlockPistonRetractEvent event) {
        moveMarks(event.getBlocks(), event.getDirection());
    }

    private void moveMarks(List<Block> blocks, org.bukkit.block.BlockFace direction) {
        List<Block> marked = new ArrayList<>();
        for (Block b : blocks) {
            if (net.skybornmc.skyblock.common.world.ChunkMarks.marked(b)) {
                marked.add(b);
            }
        }
        for (Block b : marked) {
            net.skybornmc.skyblock.common.world.ChunkMarks.unmark(b);
        }
        for (Block b : marked) {
            net.skybornmc.skyblock.common.world.ChunkMarks.mark(b.getRelative(direction));
        }
    }

    /** FX-17: a block that formed (cobblestone generator) is natural even where a placed block once was. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onForm(org.bukkit.event.block.BlockFormEvent event) {
        net.skybornmc.skyblock.common.world.ChunkMarks.unmark(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onExplode(org.bukkit.event.entity.EntityExplodeEvent event) {
        for (Block b : event.blockList()) {
            net.skybornmc.skyblock.common.world.ChunkMarks.unmark(b);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplode(org.bukkit.event.block.BlockExplodeEvent event) {
        for (Block b : event.blockList()) {
            net.skybornmc.skyblock.common.world.ChunkMarks.unmark(b);
        }
    }

    /** Right-click in the air: that tool's menu (main hand only; on a block the tool works the vanilla way). Bedrock: /tool. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || !ToolItems.isTool(event.getItem())) {
            return;
        }
        if (event.getAction() == org.bukkit.event.block.Action.RIGHT_CLICK_AIR) {
            // Owner 6 Nov 2026: a right-click casts the rod; its menu opens with sneak + right-click (or /tool).
            if (ToolItems.kindOf(event.getItem()) == Kind.ROD && !event.getPlayer().isSneaking()) {
                return;
            }
            event.setCancelled(true);
            m.openHeld(event.getPlayer());
        }
    }

    /**
     * Owner 6 Nov 2026: a catch at /fish with the island rod (after the grind areas made it the player's water's catch):
     * the rod's enchants, tool XP and Fishing Shards, its Extra Drops and Double Drops, then the rod's storage (then the
     * bag, never the ground). A full storage gives nothing until it is sold (like the paused /farm and /mine).
     */
    /** Owner 6 Nov 2026: a /pve kill with the island sword: its enchants, tool XP and Combat Shards. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onArenaKill(net.skybornmc.skyblock.common.grind.GrindBreakEvent event) {
        if (!event.pve()) {
            return;
        }
        Player p = event.getPlayer();
        Kind kind = usable(p, p.getInventory().getItemInMainHand(), null);
        if (kind != Kind.SWORD) {
            return;
        }
        m.enchants().proc(p, kind, event.location().getBlock(), new ArrayList<>());
        m.action(p, kind, false, event.location().getBlock().getLocation());
    }

    /** A full rod storage pauses /fish before anything is counted or paid (owner Q-30: no loss). */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onFishFull(org.bukkit.event.player.PlayerFishEvent event) {
        if (event.getState() != org.bukkit.event.player.PlayerFishEvent.State.CAUGHT_FISH) {
            return;
        }
        Player p = event.getPlayer();
        if (!grindWorld(p) || ToolItems.kindOf(p.getInventory().getItemInMainHand()) != Kind.ROD || !m.loaded(p)) {
            return;
        }
        if (bagFull(p) && m.storage().used(m.profile(p), Kind.ROD) >= m.storage().capacity(p, Kind.ROD)) {
            event.setCancelled(true);
            m.msg().actionBar(p, "storage.paused-rod");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFish(org.bukkit.event.player.PlayerFishEvent event) {
        if (event.getState() != org.bukkit.event.player.PlayerFishEvent.State.CAUGHT_FISH || !(event.getCaught() instanceof Item caught)
                || !caught.isValid()) {
            return;
        }
        Player p = event.getPlayer();
        Kind kind = usable(p, p.getInventory().getItemInMainHand(), null);
        if (kind != Kind.ROD || !grindWorld(p)) {
            return;
        }
        List<Item> drops = new ArrayList<>(List.of(caught));
        m.enchants().proc(p, kind, event.getHook().getLocation().getBlock(), drops);
        m.action(p, kind, false, event.getHook().getLocation().getBlock().getLocation());
        double mult = m.core().services().optional(BonusService.class).map(b -> b.multiplier(BonusFamily.EXTRA_DROPS,
                BonusContext.of(p, p.getInventory().getItemInMainHand(), "island_rod").withTarget(caught.getItemStack().getType().getKey().getKey())))
                .orElse(1.0);
        mult *= net.skybornmc.skyblock.common.grind.GrindEvents.multiplier(m.core().services(), p,
                net.skybornmc.skyblock.common.grind.GrindEvents.Kind.DOUBLE_DROPS);
        ItemStack stack = caught.getItemStack();
        if (mult > 1.0) {
            stack.setAmount(Math.max(1, Math.min(stack.getMaxStackSize(), ToolRules.roll(stack.getAmount() * mult, ThreadLocalRandom.current().nextDouble()))));
        }
        caught.remove(); // nothing flies to the player: the bag first, then the rod's storage (owner 7 Nov 2026)
        if (m.autoSell(p, kind, stack)) {
            return;
        }
        Map<Integer, ItemStack> left = p.getInventory().addItem(stack);
        if (!left.isEmpty()) {
            ItemStack rest = left.values().iterator().next();
            long stored = m.storage().add(p, kind, rest.getType().getKey().getKey(), rest.getAmount());
            if (stored < rest.getAmount()) {
                warn(p, "storage.full");
            }
        }
    }

    // ------------------------------------------------------------------ Haste and Speed (every 2 s)

    void effectsTick() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            ItemStack hand = p.getInventory().getItemInMainHand();
            Kind kind = ToolItems.kindOf(hand);
            UUID island = m.island(p);
            boolean working = kind != null && island != null && island.equals(ToolItems.islandOf(hand)) && toolWorld(p) && m.loaded(p);
            int speed = working && kind == Kind.HOE ? speedLevel(p) : 0;
            if (speed > 0) {
                p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 60, speed - 1, true, false, true));
                speedGiven.add(p.getUniqueId());
            } else {
                removeSpeed(p);
            }
            if (!working) {
                continue;
            }
            int haste = m.effect(p, kind, Upgrades.Effect.HASTE);
            if (haste > 0) {
                p.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 60, haste - 1, true, false, true));
            }
        }
    }

    /** Owner 4 Nov 2026: Speed I while holding the island hoe, Speed II ("Swiftness") from hoe prestige 2. */
    private int speedLevel(Player p) {
        int level = m.config().speedLevel;
        if (level <= 0) {
            return 0;
        }
        ToolStore.Prog hoe = m.view(p, Kind.HOE); // the player's own hoe prestige
        int swift = m.config().swiftnessPrestige;
        return level + (hoe != null && swift > 0 && hoe.prestige() >= swift ? 1 : 0);
    }

    /** Takes our Speed away at once (never a Speed from somewhere else that lasts longer than ours). */
    private void removeSpeed(Player p) {
        if (!speedGiven.remove(p.getUniqueId())) {
            return;
        }
        PotionEffect now = p.getPotionEffect(PotionEffectType.SPEED);
        if (now != null && now.isAmbient() && now.getDuration() <= 60) {
            p.removePotionEffect(PotionEffectType.SPEED);
        }
    }

    void clearEffects() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            removeSpeed(p);
        }
        speedGiven.clear();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHeld(PlayerItemHeldEvent event) {
        Player p = event.getPlayer();
        if (speedGiven.contains(p.getUniqueId()) && ToolItems.kindOf(p.getInventory().getItem(event.getNewSlot())) != Kind.HOE) {
            removeSpeed(p);
        }
    }

    // ------------------------------------------------------------------ keeping the tools tidy

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDropItem(PlayerDropItemEvent event) {
        if (ToolItems.isTool(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
            warn(event.getPlayer(), "tool.no-drop");
            removeSpeed(event.getPlayer());
        }
    }

    /**
     * Owner 4 Nov 2026: opening a container checks the player's tools, and an old island axe or sword (or a tool from
     * before the handles) stored in the container is removed (audit-logged).
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player p)) {
            return;
        }
        m.check(p);
        Inventory top = event.getView().getTopInventory();
        if (top.getType() == org.bukkit.event.inventory.InventoryType.CRAFTING || top.getType() == org.bukkit.event.inventory.InventoryType.PLAYER) {
            return;
        }
        for (int i = 0; i < top.getSize(); i++) {
            ItemStack s = top.getItem(i);
            if (ToolItems.isTool(s) && (ToolItems.kindOf(s) == null || ToolItems.islandOf(s) == null)) {
                top.setItem(i, null);
                m.core().audit().logPlayer(AuditCategory.ITEM, "island_tool_removed", Actor.SYSTEM, p.getUniqueId(), p.getName(), null,
                        p.getLocation(), Map.of("kind", String.valueOf(ToolItems.rawKind(s)), "reason", "old_item_in_container", "slot", i));
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(PlayerDeathEvent event) {
        if (event.getKeepInventory()) {
            return;
        }
        List<ItemStack> kept = new ArrayList<>();
        event.getDrops().removeIf(s -> {
            if (ToolItems.isTool(s)) {
                kept.add(s);
                return true;
            }
            return false;
        });
        if (!kept.isEmpty()) {
            keptOnDeath.put(event.getPlayer().getUniqueId(), kept);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        List<ItemStack> kept = keptOnDeath.remove(event.getPlayer().getUniqueId());
        if (kept != null) {
            Player p = event.getPlayer();
            Bukkit.getScheduler().runTask(m.core().plugin(), () -> {
                p.getInventory().addItem(kept.toArray(new ItemStack[0])).values().forEach(left -> m.giveAll(p, List.of(left)));
                m.check(p);
            });
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorld(PlayerChangedWorldEvent event) {
        removeSpeed(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(m.core().plugin(), () -> {
            if (p.isOnline()) {
                m.load(p);
            }
        }, 40L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Player p = event.getPlayer();
        lastWarn.remove(p.getUniqueId());
        removeSpeed(p);
        List<ItemStack> kept = keptOnDeath.remove(p.getUniqueId());
        if (kept != null) {
            p.getInventory().addItem(kept.toArray(new ItemStack[0])); // saved with the inventory
        }
        m.unload(p);
    }

    /**
     * Feedback 3 Nov 2026 ("when joining an island ... make sure they get the right tools"): the new island's hoe, pickaxe,
     * rod and sword are handed out right after the join (they used to come only at the next login).
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onIslandJoined(net.skybornmc.skyblock.common.island.IslandJoinedEvent event) {
        Player p = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(m.core().plugin(), () -> {
            if (p.isOnline()) {
                m.load(p);
            }
        }, 20L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onProfile(ProfileSwitchedEvent event) {
        Player p = event.getPlayer();
        m.unloadProfile(event.from() == null ? null : event.from().id());
        removeSpeed(p);
        Bukkit.getScheduler().runTaskLater(m.core().plugin(), () -> {
            if (p.isOnline()) {
                m.load(p);
            }
        }, 20L);
    }
}
