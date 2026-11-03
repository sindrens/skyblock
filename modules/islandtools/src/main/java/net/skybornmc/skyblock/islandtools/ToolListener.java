package net.skybornmc.skyblock.islandtools;

import net.skybornmc.skyblock.common.bonus.BonusContext;
import net.skybornmc.skyblock.common.bonus.BonusFamily;
import net.skybornmc.skyblock.common.bonus.BonusService;
import net.skybornmc.skyblock.common.island.IslandService;
import net.skybornmc.skyblock.common.profile.ProfileSwitchedEvent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Everything the island tools do in the world: only the current copy works (D1/T6), only on islands (D1), block XP,
 * auto-smelt, extra drops through the bonus service (source {@code island_tool}, family caps on top), magnet, timber,
 * the hoe's rows and storage (with {@link HoeFarming}), lifesteal, Haste while held, put away / given back by world (T1),
 * kept on death and never dropped.
 */
final class ToolListener implements Listener {

    /** A break by an island tool, waiting for its drops. */
    private record Pending(Player player, ToolStore.Rec rec, UUID island, boolean crop, boolean burst, boolean natural) {
    }

    /** Blocks players placed in the island world (owner 3 Nov 2026: no bonus for breaking your own blocks). */
    private final net.skybornmc.skyblock.common.world.PlacedBlocks placed;
    private int placeCount;

    private static final Map<Material, Material> SMELT = Map.of(
            Material.RAW_IRON, Material.IRON_INGOT, Material.RAW_GOLD, Material.GOLD_INGOT, Material.RAW_COPPER, Material.COPPER_INGOT,
            Material.COBBLESTONE, Material.STONE, Material.SAND, Material.GLASS, Material.COBBLED_DEEPSLATE, Material.DEEPSLATE);

    private final IslandToolsModule m;
    private final Map<Location, Pending> pending = new HashMap<>();
    private final Set<UUID> expanding = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Long> lastWarn = new ConcurrentHashMap<>();
    private final Map<UUID, java.util.List<ItemStack>> keptOnDeath = new ConcurrentHashMap<>();

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

    private boolean onIslandWorld(Player p) {
        // Owner 3 Nov 2026: the tools work, level up and use their perks the same at /farm and /mine (tool-worlds).
        return m.islands().map(i -> i.isIslandWorld(p.getWorld())).orElse(false)
                || m.config().toolWorlds.stream().anyMatch(w -> w.equalsIgnoreCase(p.getWorld().getName()));
    }

    /** The tool in hand when it is a usable copy; removes stale copies. Null for anything else. */
    private ToolStore.Rec usable(Player p, ItemStack hand, org.bukkit.event.Cancellable event) {
        if (!ToolItems.isTool(hand)) {
            return null;
        }
        if (!m.valid(p, hand)) {
            event.setCancelled(true);
            if (m.loaded(p)) {
                p.getInventory().setItemInMainHand(null);
                warn(p, "tool.old-copy");
            }
            return null;
        }
        if (!onIslandWorld(p)) {
            // Owner 3 Nov 2026 20:37: island tools work everywhere as normal tools (island upgrades only on islands).
            return null;
        }
        return m.rec(p, ToolItems.kindOf(hand));
    }

    // ------------------------------------------------------------------ blocks

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Player p = event.getPlayer();
        ItemStack hand = p.getInventory().getItemInMainHand();
        ToolStore.Rec rec = usable(p, hand, event);
        if (rec == null) {
            return;
        }
        Block block = event.getBlock();
        // Owner 3 Nov 2026: the island hoe never breaks a crop that is not fully grown.
        if (rec.kind() == Kind.HOE && IslandGrowth.CROPS.contains(block.getType())
                && block.getBlockData() instanceof org.bukkit.block.data.Ageable age && age.getAge() < age.getMaximumAge()) {
            event.setCancelled(true);
            m.msg().actionBar(p, "hoe.not-ripe");
            return;
        }
        int xp = m.effectLevel(rec, Upgrades.Effect.EXPERIENCE);
        if (xp > 0 && event.getExpToDrop() > 0) {
            event.setExpToDrop((int) Math.round(event.getExpToDrop() * (1 + xp * m.config().experiencePerLevel / 100.0)));
        }
        UUID island = m.islands().flatMap(i -> i.islandAt(block.getLocation())).map(IslandService.IslandInfo::id).orElse(null);
        boolean member = island != null && m.islands().map(i -> i.isMember(island, p.getUniqueId())).orElse(false);
        boolean crop = rec.kind() == Kind.HOE && member && HoeFarming.ripeCrop(block.getBlockData());
        boolean nested = expanding.contains(p.getUniqueId());
        boolean burst = false;
        if (crop && !nested) {
            burst = ThreadLocalRandom.current().nextDouble(100) < m.farming().burstPercent(island);
        }
        Location key = block.getLocation();
        boolean natural = !placed.isPlaced(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ(), System.currentTimeMillis());
        placed.forget(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
        pending.put(key, new Pending(p, rec, island, crop, burst, natural));
        Bukkit.getScheduler().runTask(m.core().plugin(), () -> pending.remove(key)); // no drop event (nothing dropped): forget it
        if (nested) {
            return;
        }
        if (crop) {
            int length = ToolRules.row(m.effectLevel(rec, Upgrades.Effect.ROW), m.config().rowLengths);
            if (burst) {
                m.farming().burstShow(p, block.getLocation());
            }
            expand(p, block, ToolRules.rowOffsets(length, p.getLocation().getYaw()), b -> HoeFarming.ripeCrop(b.getBlockData()), burst);
        } else if (rec.kind() == Kind.AXE && Tag.LOGS.isTagged(block.getType())) {
            int extra = m.effectLevel(rec, Upgrades.Effect.TIMBER) * m.config().timberPerLevel;
            if (extra > 0) {
                Material log = block.getType();
                int[][] up = new int[extra][];
                for (int i = 0; i < extra; i++) {
                    up[i] = new int[]{0, i + 1, 0};
                }
                expand3(p, block, up, b -> b.getType() == log);
            }
        }
    }

    private void expand(Player p, Block origin, int[][] offsets, java.util.function.Predicate<Block> ok, boolean burst) {
        int[][] three = new int[offsets.length][];
        for (int i = 0; i < offsets.length; i++) {
            three[i] = new int[]{offsets[i][0], 0, offsets[i][1]};
        }
        expand3(p, origin, three, ok);
    }

    /** Breaks the blocks at the offsets with the player's own break (protection, milestones and drops all apply). */
    private void expand3(Player p, Block origin, int[][] offsets, java.util.function.Predicate<Block> ok) {
        Bukkit.getScheduler().runTask(m.core().plugin(), () -> {
            if (!p.isOnline() || !expanding.add(p.getUniqueId())) {
                return;
            }
            try {
                for (int[] o : offsets) {
                    if (o[0] == 0 && o[1] == 0 && o[2] == 0) {
                        continue;
                    }
                    Block b = origin.getRelative(o[0], o[1], o[2]);
                    if (ok.test(b)) {
                        p.breakBlock(b);
                    }
                }
            } finally {
                expanding.remove(p.getUniqueId());
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrops(BlockDropItemEvent event) {
        Pending pd = pending.remove(event.getBlock().getLocation());
        if (pd == null) {
            return;
        }
        Player p = pd.player();
        ToolStore.Rec rec = pd.rec();
        // Owner 3 Nov 2026: bonuses only on the tool's own blocks and never on blocks a player placed.
        if (!pd.crop() && (!pd.natural() || !relevant(rec.kind(), event.getBlockState().getType().getKey().getKey()))) {
            if (m.effectLevel(rec, Upgrades.Effect.MAGNET) > 0) {
                magnetOnly(p, event);
            }
            return;
        }
        m.action(p, rec); // owner 3 Nov 2026: counts for the island's task and may find a shard
        int smelt = rec.kind() == Kind.PICKAXE ? m.effectLevel(rec, Upgrades.Effect.SMELT) : 0;
        boolean magnet = m.effectLevel(rec, Upgrades.Effect.MAGNET) > 0;
        double mult = m.core().services().optional(BonusService.class).map(b -> b.multiplier(BonusFamily.EXTRA_DROPS,
                BonusContext.of(p, p.getInventory().getItemInMainHand(), "island_" + rec.kind().id())
                        .withTarget(event.getBlockState().getType().getKey().getKey()))).orElse(1.0);
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
            if (m.autoSell(p, rec, stack)) {
                it.remove(); // Auto-Sell: sold in the next batch
                continue;
            }
            if (pd.crop()) {
                long stored = m.storage().add(p, stack.getType().getKey().getKey(), stack.getAmount());
                if (stored >= stack.getAmount()) {
                    it.remove();
                    continue;
                }
                if (stored > 0) {
                    stack.setAmount(stack.getAmount() - (int) stored);
                } else {
                    warn(p, "hoe.storage-full");
                }
            }
            if (magnet) {
                Map<Integer, ItemStack> left = p.getInventory().addItem(stack);
                if (left.isEmpty()) {
                    it.remove();
                    continue;
                }
                stack = left.values().iterator().next();
            }
            entity.setItemStack(stack);
        }
        if (pd.crop()) {
            BlockState before = event.getBlockState();
            m.farming().replant(event.getBlock().getLocation(), before.getType(), p, pd.island(), pd.burst());
        }
    }

    /** The tool's own blocks ({@code islandtools.yml relevant.<kind>}). */
    private boolean relevant(Kind kind, String blockId) {
        var c = m.config();
        return switch (kind) {
            case PICKAXE -> Relevant.matches(c.relevantPickaxe, blockId);
            case AXE -> Relevant.matches(c.relevantAxe, blockId);
            case HOE -> Relevant.matches(c.relevantHoe, blockId);
            default -> false;
        };
    }

    /** No bonus, but Magnet still pulls the plain drops in. */
    private void magnetOnly(Player p, BlockDropItemEvent event) {
        for (Iterator<Item> it = event.getItems().iterator(); it.hasNext(); ) {
            Item entity = it.next();
            Map<Integer, ItemStack> left = p.getInventory().addItem(entity.getItemStack());
            if (left.isEmpty()) {
                it.remove();
            } else {
                entity.setItemStack(left.values().iterator().next());
            }
        }
    }

    /** Remembers player-placed blocks in the island world (crops and saplings grow, so they are not remembered). */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(org.bukkit.event.block.BlockPlaceEvent event) {
        Block b = event.getBlockPlaced();
        if (!onIslandWorld(event.getPlayer()) || org.bukkit.Tag.CROPS.isTagged(b.getType()) || org.bukkit.Tag.SAPLINGS.isTagged(b.getType())) {
            return;
        }
        long now = System.currentTimeMillis();
        placed.placed(b.getWorld().getUID(), b.getX(), b.getY(), b.getZ(), now);
        if (++placeCount % 2000 == 0) {
            placed.prune(now);
        }
    }

    // ------------------------------------------------------------------ combat

    /** A mob killed with the island sword on an island: task progress and a shard roll (players never count). */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onKill(org.bukkit.event.entity.EntityDeathEvent event) {
        if (event.getEntity() instanceof Player || !(event.getEntity().getKiller() instanceof Player p) || !onIslandWorld(p)) {
            return;
        }
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (ToolItems.kindOf(hand) != Kind.SWORD || !m.valid(p, hand)) {
            return;
        }
        ToolStore.Rec rec = m.rec(p, Kind.SWORD);
        if (rec != null) {
            m.action(p, rec);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player p)) {
            return;
        }
        ToolStore.Rec rec = usable(p, p.getInventory().getItemInMainHand(), event);
        if (rec == null || rec.kind() != Kind.SWORD) {
            return;
        }
        int ls = m.effectLevel(rec, Upgrades.Effect.LIFESTEAL);
        if (ls > 0) {
            double heal = event.getFinalDamage() * ls * m.config().lifestealPerLevel / 100.0;
            var max = p.getAttribute(Attribute.MAX_HEALTH);
            p.setHealth(Math.min(max == null ? 20 : max.getValue(), p.getHealth() + heal));
        }
    }

    /** Sneak + right-click in the air: that tool's menu. Not ignoreCancelled: Bukkit fires air clicks as cancelled. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || !ToolItems.isTool(event.getItem())) {
            return;
        }
        Player p = event.getPlayer();
        // Owner 3 Nov 2026: sneak + right-click in the AIR opens the tool's menu; on a block the tool works as usual.
        if (p.isSneaking() && event.getAction() == org.bukkit.event.block.Action.RIGHT_CLICK_AIR) {
            event.setCancelled(true);
            if (m.loaded(p) && m.config().enabled) {
                m.core().menus().open(p, new ToolMenu(m, ToolItems.kindOf(event.getItem())));
            }
        }
    }

    // ------------------------------------------------------------------ haste (every 2 s)

    void hasteTick() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            ItemStack hand = p.getInventory().getItemInMainHand();
            if (!ToolItems.isTool(hand) || !onIslandWorld(p) || !m.valid(p, hand)) {
                continue;
            }
            int lv = m.effectLevel(m.rec(p, ToolItems.kindOf(hand)), Upgrades.Effect.HASTE);
            if (lv > 0) {
                p.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 60, lv - 1, true, false, true));
            }
        }
    }

    // ------------------------------------------------------------------ keeping the tools

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDropItem(PlayerDropItemEvent event) {
        if (ToolItems.isTool(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
            warn(event.getPlayer(), "tool.no-drop");
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(PlayerDeathEvent event) {
        if (event.getKeepInventory()) {
            return;
        }
        java.util.List<ItemStack> kept = new java.util.ArrayList<>();
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
        java.util.List<ItemStack> kept = keptOnDeath.remove(event.getPlayer().getUniqueId());
        if (kept != null) {
            Bukkit.getScheduler().runTask(m.core().plugin(), () -> m.giveAll(event.getPlayer(), kept));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorld(PlayerChangedWorldEvent event) {
        m.placeForWorld(event.getPlayer());
    }

    /** Owner 3 Nov 2026: the Depths has its own inventory; after a swap back to the island set the tools are checked again. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onSwap(net.skybornmc.skyblock.common.player.InventorySwappedEvent event) {
        m.placeForWorld(event.getPlayer());
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
        lastWarn.remove(event.getPlayer().getUniqueId());
        java.util.List<ItemStack> kept = keptOnDeath.remove(event.getPlayer().getUniqueId());
        if (kept != null) {
            event.getPlayer().getInventory().addItem(kept.toArray(new ItemStack[0])); // saved with the inventory
        }
        m.unload(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onProfile(ProfileSwitchedEvent event) {
        Player p = event.getPlayer();
        m.unloadProfile(event.from() == null ? null : event.from().id());
        Bukkit.getScheduler().runTaskLater(m.core().plugin(), () -> {
            if (p.isOnline()) {
                m.load(p);
            }
        }, 20L);
    }

}
