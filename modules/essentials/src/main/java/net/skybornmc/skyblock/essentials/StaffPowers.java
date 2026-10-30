package net.skybornmc.skyblock.essentials;

import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.skybornmc.skyblock.common.Core;
import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.audit.AuditCategory;
import net.skybornmc.skyblock.common.flight.FlightGrants;
import net.skybornmc.skyblock.common.item.ItemKeys;
import net.skybornmc.skyblock.common.item.StaffItems;
import net.skybornmc.skyblock.common.text.ModuleMessages;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.util.Scheduler;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.FurnaceSmeltEvent;
import org.bukkit.event.inventory.InventoryCreativeEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * Staff powers and staff items (owner answers 331-334), main thread:
 * <ul>
 *   <li>Creative mode: every switch to creative (our {@code /gm}, vanilla {@code /gamemode}, staff mode restoring an old
 *       mode) is checked by {@link StaffPowerRules#creative}; leaving the creative worlds or joining outside them puts a
 *       creative player back into survival.</li>
 *   <li>God mode and {@code /fly}: kept in {@link #god} and {@link #flying}; a light check every
 *       {@code staff-powers.check-seconds} takes them away when the player is neither in staff mode nor in build mode.</li>
 *   <li>Staff items: items taken in creative get the hidden staff tag ({@link StaffItems}); the tag follows them into
 *       crafting and smelting results and into blocks placed in survival (remembered in the chunk's data and put back on
 *       the drops when the block is mined).</li>
 * </ul>
 */
final class StaffPowers implements Listener {

    private final Core core;
    private final EssentialsConfig config;
    private final ModuleMessages msg;
    private final Predicate<Player> buildMode;
    private final Set<UUID> god = ConcurrentHashMap.newKeySet();
    /** Players whose flight was turned on with {@code /fly} (only that flight is ever taken away here). */
    private final Set<UUID> flying = ConcurrentHashMap.newKeySet();
    /** Leftover flight removed on join while in the air: the next fall is soft until then (epoch millis). */
    private final Map<UUID, Long> softLanding = new ConcurrentHashMap<>();
    private static final long SOFT_LANDING_MILLIS = 15_000L;
    /** Staff blocks broken this tick, waiting for their drop event ({@code world/x/y/z}). */
    private final Set<String> brokenStaffBlocks = ConcurrentHashMap.newKeySet();
    private Scheduler.Task task;

    StaffPowers(Core core, EssentialsConfig config, ModuleMessages msg, Predicate<Player> buildMode) {
        this.core = core;
        this.config = config;
        this.msg = msg;
        this.buildMode = buildMode;
    }

    void start() {
        long period = Math.max(10L, config.staffPowersCheckSeconds * 20L);
        task = core.scheduler().repeat("essentials:staff-powers", period, period, this::check);
    }

    void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    // ------------------------------------------------------------------ modes

    /** Staff mode (staff module, placeholder {@code staff_mode}) or build mode ({@code /build}). */
    boolean inMode(Player player) {
        if (buildMode.test(player)) {
            return true;
        }
        return core.placeholders().has("staff_mode")
                && core.placeholders().text(player, "staff_mode").map("true"::equalsIgnoreCase).orElse(false);
    }

    /**
     * Checks a power for {@code sender} on {@code target}; throws the explaining failure when it is not allowed (owner
     * answer 333).
     */
    void require(StaffPowerRules.Power power, CommandSender sender, Player target) {
        boolean console = !(sender instanceof Player);
        boolean self = sender instanceof Player p && p.equals(target);
        boolean senderInMode = sender instanceof Player p && inMode(p);
        StaffPowerRules.Check check = StaffPowerRules.power(power, config.staffPowersEnabled, console, senderInMode, self,
                self ? senderInMode : inMode(target));
        if (check != StaffPowerRules.Check.OK) {
            throw CommandGroup.fail("powers." + check.key(), Msg.p("power", powerName(power)), Msg.p("player", target.getName()));
        }
    }

    private String powerName(StaffPowerRules.Power power) {
        String raw = core.messages().raw(CommandGroup.MODULE, "powers.names." + power.key());
        return raw == null ? power.key() : raw;
    }

    // ------------------------------------------------------------------ god / fly state

    boolean isGod(UUID player) {
        return god.contains(player);
    }

    /** Toggles god mode; true if it is on now. */
    boolean toggleGod(UUID player) {
        if (god.add(player)) {
            return true;
        }
        god.remove(player);
        return false;
    }

    /** Is {@code /fly} on for this player (its own grant, not island fly or staff mode)? */
    boolean flyOn(UUID player) {
        return flying.contains(player);
    }

    private static boolean survivalLike(Player player) {
        return player.getGameMode() == GameMode.SURVIVAL || player.getGameMode() == GameMode.ADVENTURE;
    }

    /** Gives {@code /fly}: recorded in {@link FlightGrants} so island fly and staff mode never take it away. */
    void grantFly(Player player) {
        flying.add(player.getUniqueId());
        FlightGrants.grant(player.getUniqueId(), FlightGrants.FLY);
        if (survivalLike(player) && !player.getAllowFlight()) {
            player.setAllowFlight(true);
        }
    }

    /** Takes {@code /fly} away; flight stays when another source (island fly, staff mode, freeze) still gives it. */
    void revokeFly(Player player) {
        flying.remove(player.getUniqueId());
        boolean nobodyElse = FlightGrants.revoke(player.getUniqueId(), FlightGrants.FLY);
        if (nobodyElse && survivalLike(player)) {
            player.setFlying(false);
            player.setAllowFlight(false);
        }
    }

    /** Name of the other feature that still gives flight ({@code powers.sources.<id>}), for "you can still fly" texts. */
    String flightSourceName(Player player) {
        String source = FlightGrants.otherHolder(player.getUniqueId(), FlightGrants.FLY);
        if (source == null) {
            return "";
        }
        String raw = core.messages().raw(CommandGroup.MODULE, "powers.sources." + source);
        return raw == null ? source : raw;
    }

    /**
     * Vanilla resets {@code allowFlight} on respawn, game mode and some world changes: a {@code /fly} that is still on
     * (and still allowed) is given back, so the toggle never says "on" while the player can't fly.
     */
    private void reapply(Player player) {
        if (player.isOnline() && flying.contains(player.getUniqueId()) && survivalLike(player) && !player.getAllowFlight()
                && StaffPowerRules.keep(config.staffPowersEnabled, inMode(player))) {
            player.setAllowFlight(true);
        }
    }

    private void reapplyLater(Player player) {
        if (flying.contains(player.getUniqueId())) {
            core.scheduler().later("essentials:fly-reapply", 1L, () -> reapply(player));
        }
    }

    /** Owner answer 334: powers are only on while in staff or build mode; take them away otherwise. */
    private void check() {
        if (god.isEmpty() && flying.isEmpty()) {
            return;
        }
        for (UUID id : List.copyOf(god.isEmpty() ? flying : union())) {
            Player player = Bukkit.getPlayer(id);
            if (player == null) {
                god.remove(id);
                flying.remove(id);
                FlightGrants.revoke(id, FlightGrants.FLY);
                continue;
            }
            if (StaffPowerRules.keep(config.staffPowersEnabled, inMode(player))) {
                reapply(player);
                continue;
            }
            List<String> removed = new ArrayList<>();
            if (god.remove(id)) {
                removed.add(powerName(StaffPowerRules.Power.GOD));
            }
            if (flying.contains(id)) {
                removed.add(powerName(StaffPowerRules.Power.FLY));
                revokeFly(player);
            }
            if (!removed.isEmpty()) {
                msg.send(player, "powers.removed", Msg.p("powers", String.join(", ", removed)));
                core.audit().logPlayer(AuditCategory.STAFF, "staff_powers_off", Actor.system("staff-powers"), id, player.getName(),
                        null, player.getLocation(), Map.of("removed", removed));
            }
        }
    }

    private Set<UUID> union() {
        Set<UUID> all = new java.util.HashSet<>(god);
        all.addAll(flying);
        return all;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        god.remove(id);
        flying.remove(id);
        softLanding.remove(id);
        FlightGrants.revoke(id, FlightGrants.FLY);
    }

    /** Last listener on quit: nothing about this player's flight sources outlives the session. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuitLast(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        core.scheduler().later("essentials:flight-forget", 1L, () -> {
            if (Bukkit.getPlayer(id) == null) {
                FlightGrants.forget(id);
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        reapplyLater(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGameModeChanged(PlayerGameModeChangeEvent event) {
        reapplyLater(event.getPlayer());
    }

    // ------------------------------------------------------------------ creative (owner answer 331)

    StaffPowerRules.Creative creative(Player player) {
        return StaffPowerRules.creative(config.staffPowersEnabled, player.hasPermission(Perms.gamemode(GameMode.CREATIVE)),
                player.getWorld().getName(), config.creativeWorlds);
    }

    /** Placeholders of the creative texts: the worlds where creative works. */
    TagResolver worlds() {
        return Msg.p("worlds", String.join(", ", config.creativeWorlds));
    }

    /** The one gate for creative: every game mode change to creative, from any source. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onGameModeChange(PlayerGameModeChangeEvent event) {
        if (event.getNewGameMode() != GameMode.CREATIVE) {
            return;
        }
        Player player = event.getPlayer();
        StaffPowerRules.Creative check = creative(player);
        if (check == StaffPowerRules.Creative.OK) {
            if (config.tagStaffItems) {
                core.scheduler().later("essentials:creative-warning", 1L, () -> {
                    if (player.isOnline() && player.getGameMode() == GameMode.CREATIVE) {
                        msg.send(player, "creative.items-warning");
                    }
                });
            }
            return;
        }
        event.setCancelled(true);
        msg.send(player, "creative." + check.key(), worlds());
        core.audit().logPlayer(AuditCategory.STAFF, "creative_refused", Actor.player(player), player.getUniqueId(),
                player.getName(), null, player.getLocation(), Map.of("reason", check.key(), "cause", event.getCause().name()));
        // A refused restore (e.g. leaving staff mode outside the creative worlds) must not leave creative flight behind.
        core.scheduler().later("essentials:creative-refused", 1L, () -> {
            if (player.isOnline() && (player.getGameMode() == GameMode.SURVIVAL || player.getGameMode() == GameMode.ADVENTURE)
                    && !inMode(player) && !FlightGrants.any(player.getUniqueId())) {
                player.setFlying(false);
                player.setAllowFlight(false);
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        leaveCreativeIfNotAllowed(event.getPlayer(), "creative.left-area");
        reapplyLater(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        core.scheduler().later("essentials:creative-join", 2L, () -> leaveCreativeIfNotAllowed(player, "creative.left-area"));
        // FIXES1: vanilla saves allowFlight with the player. Flight that nothing gives any more (saved while staff mode,
        // /fly or island fly was on) is taken away once every join handler ran (island fly 2 ticks, freeze, staff
        // mode recovery), so a survival player never keeps an ownerless flight. The landing is soft.
        core.scheduler().later("essentials:flight-join", 20L, () -> {
            if (player.isOnline() && survivalLike(player) && player.getAllowFlight()
                    && !FlightGrants.any(player.getUniqueId()) && !inMode(player)) {
                boolean wasFlying = player.isFlying();
                player.setFlying(false);
                player.setAllowFlight(false);
                if (wasFlying) {
                    softLanding.put(player.getUniqueId(), System.currentTimeMillis() + SOFT_LANDING_MILLIS);
                }
            }
        });
    }

    /** No fall damage for a player whose leftover flight was just taken away (until the given epoch millis). */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onFall(org.bukkit.event.entity.EntityDamageEvent event) {
        if (softLanding.isEmpty() || event.getCause() != org.bukkit.event.entity.EntityDamageEvent.DamageCause.FALL
                || !(event.getEntity() instanceof Player player)) {
            return;
        }
        Long until = softLanding.remove(player.getUniqueId());
        if (until != null && until >= System.currentTimeMillis()) {
            event.setCancelled(true);
        }
    }

    private void leaveCreativeIfNotAllowed(Player player, String key) {
        if (!player.isOnline() || player.getGameMode() != GameMode.CREATIVE || creative(player) == StaffPowerRules.Creative.OK) {
            return;
        }
        player.setGameMode(GameMode.SURVIVAL);
        msg.send(player, key, worlds());
        core.audit().logPlayer(AuditCategory.STAFF, "creative_ended", Actor.system("staff-powers"), player.getUniqueId(),
                player.getName(), null, player.getLocation(), Map.of("world", player.getWorld().getName()));
    }

    // ------------------------------------------------------------------ staff items (owner answer 332)

    /** Everything taken, moved or made in the creative inventory becomes a staff item. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCreativeItem(InventoryCreativeEvent event) {
        if (!config.tagStaffItems || !(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        ItemStack item = event.getCursor();
        if (item.isEmpty() || StaffItems.isMarked(item)) {
            return;
        }
        ItemStack tagged = item.clone();
        StaffItems.mark(tagged, "creative:" + player.getName());
        event.setCursor(tagged);
    }

    /** Items thrown out of the creative inventory are staff items too. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCreativeDrop(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        if (!config.tagStaffItems || player.getGameMode() != GameMode.CREATIVE) {
            return;
        }
        ItemStack stack = event.getItemDrop().getItemStack();
        if (!StaffItems.isMarked(stack)) {
            StaffItems.mark(stack, "creative:" + player.getName());
            event.getItemDrop().setItemStack(stack);
        }
    }

    /** A crafting result made with a staff item is a staff item (diamonds to a block and back keep the tag). */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        if (!config.tagStaffItems) {
            return;
        }
        markResult(event.getInventory());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        if (!config.tagStaffItems) {
            return;
        }
        ItemStack result = markResult(event.getInventory());
        if (result != null) {
            event.setCurrentItem(result);
        }
    }

    /** Tags the result when the grid holds a staff item; returns the tagged result, or null when nothing changed. */
    private static ItemStack markResult(CraftingInventory inventory) {
        ItemStack result = inventory.getResult();
        if (result == null || result.isEmpty() || StaffItems.isMarked(result)) {
            return null;
        }
        for (ItemStack ingredient : inventory.getMatrix()) {
            if (StaffItems.isMarked(ingredient)) {
                ItemStack tagged = result.clone();
                StaffItems.mark(tagged, StaffItems.source(ingredient));
                inventory.setResult(tagged);
                return tagged;
            }
        }
        return null;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSmelt(FurnaceSmeltEvent event) {
        if (config.tagStaffItems && StaffItems.isMarked(event.getSource()) && !StaffItems.isMarked(event.getResult())) {
            ItemStack result = event.getResult().clone();
            StaffItems.mark(result, StaffItems.source(event.getSource()));
            event.setResult(result);
        }
    }

    private static NamespacedKey blockKey(Block block) {
        return new NamespacedKey(ItemKeys.NAMESPACE, StaffItems.blockKeyName(block.getX(), block.getY(), block.getZ()));
    }

    private static String pending(Block block) {
        return block.getWorld().getName() + "/" + block.getX() + "/" + block.getY() + "/" + block.getZ();
    }

    /**
     * A staff item placed in survival is remembered in the chunk (so mining it gives staff items back); placing a normal
     * item clears an old mark. Creative placements are builds in protected areas and are not tracked.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (!config.trackStaffBlocks) {
            return;
        }
        Block block = event.getBlockPlaced();
        PersistentDataContainer pdc = block.getChunk().getPersistentDataContainer();
        NamespacedKey key = blockKey(block);
        if (StaffItems.isMarked(event.getItemInHand()) && event.getPlayer().getGameMode() != GameMode.CREATIVE) {
            pdc.set(key, PersistentDataType.BYTE, (byte) 1);
        } else if (pdc.has(key)) {
            pdc.remove(key);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (!config.trackStaffBlocks) {
            return;
        }
        Block block = event.getBlock();
        PersistentDataContainer pdc = block.getChunk().getPersistentDataContainer();
        NamespacedKey key = blockKey(block);
        if (!pdc.has(key)) {
            return;
        }
        pdc.remove(key);
        String id = pending(block);
        brokenStaffBlocks.add(id);
        core.scheduler().later("essentials:staff-block", 1L, () -> brokenStaffBlocks.remove(id));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockDrops(BlockDropItemEvent event) {
        if (brokenStaffBlocks.isEmpty() || !brokenStaffBlocks.remove(pending(event.getBlock()))) {
            return;
        }
        for (Item item : event.getItems()) {
            ItemStack stack = item.getItemStack();
            if (!StaffItems.isMarked(stack)) {
                StaffItems.mark(stack, "block:" + event.getPlayer().getName());
                item.setItemStack(stack);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        clearMarks(event.blockList());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        clearMarks(event.blockList());
    }

    private void clearMarks(List<Block> blocks) {
        if (!config.trackStaffBlocks) {
            return;
        }
        for (Block block : blocks) {
            PersistentDataContainer pdc = block.getChunk().getPersistentDataContainer();
            NamespacedKey key = blockKey(block);
            if (pdc.has(key)) {
                pdc.remove(key);
            }
        }
    }
}
