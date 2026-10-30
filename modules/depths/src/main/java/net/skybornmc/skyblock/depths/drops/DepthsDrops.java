package net.skybornmc.skyblock.depths.drops;

import net.skybornmc.skyblock.common.Core;
import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.bonus.BonusContext;
import net.skybornmc.skyblock.common.bonus.BonusFamily;
import net.skybornmc.skyblock.common.bonus.BonusService;
import net.skybornmc.skyblock.common.box.BoxSource;
import net.skybornmc.skyblock.common.box.ItemBox;
import net.skybornmc.skyblock.common.economy.Currency;
import net.skybornmc.skyblock.common.economy.EconomyService;
import net.skybornmc.skyblock.common.economy.Transaction;
import net.skybornmc.skyblock.common.economy.AccountRef;
import net.skybornmc.skyblock.common.economy.TxResult;
import net.skybornmc.skyblock.common.profile.ProfileService;
import net.skybornmc.skyblock.common.text.ModuleMessages;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.tools.ToolService;
import net.skybornmc.skyblock.common.util.Scheduler;
import net.skybornmc.skyblock.depths.DepthsConfig;
import net.skybornmc.skyblock.depths.engine.ResourceBreakEvent;
import net.skybornmc.skyblock.depths.satchel.SatchelManager;
import net.skybornmc.skyblock.depths.zones.Zone;
import net.skybornmc.skyblock.depths.zones.ZoneService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * Depths drops (M4.9): on every accepted break in a mine/forest zone the tier's table is rolled (fortune, crystal and
 * rare-find bonuses), the items go into the satchel and the crystals into a per-profile batch paid every few seconds
 * (ledger reason {@code depths_drop}, shown on the action bar). R3-22: a full satchel no longer stops mining; the drops
 * go to the inventory, then to /box ({@link DropOverflow}, batched). Only a still-loading satchel, or a full satchel
 * together with a /box at its hard cap (5,000 stacks), refuses the break before anything else happens (no XP, no
 * drops, the block stays). Per break: one zone lookup, a few random numbers and map updates; no database work.
 */
public final class DepthsDrops implements Listener {

    /** Ledger reason (and audit action) of Depths crystals. */
    public static final String REASON = "depths_drop";
    /** Errors before a crystal batch is dropped (with a log line); 1 attempt every batch-seconds. */
    static final int MAX_ATTEMPTS = 20;

    private final Core core;
    private final DepthsConfig config;
    private final ModuleMessages msg;
    private final ZoneService zones;
    private final SatchelManager satchel;
    private final DropItems items;
    private final CrystalBatcher crystals;
    private final DropOverflow overflow = new DropOverflow();
    private volatile DropTable table = DropTable.EMPTY;
    private Scheduler.Task task;
    private Scheduler.Task overflowTask;
    /** The event of the last HIGH check and its table entry, so MONITOR does not look the zone up again. */
    private ResourceBreakEvent lastEvent;
    private Zone lastZone;
    private DropTable.TierDrops lastDrops;

    public DepthsDrops(Core core, DepthsConfig config, ModuleMessages msg, ZoneService zones, SatchelManager satchel, DropItems items) {
        this.core = core;
        this.config = config;
        this.msg = msg;
        this.zones = zones;
        this.satchel = satchel;
        this.items = items;
        this.crystals = new CrystalBatcher(r -> {
            if (core.plugin().isEnabled()) {
                core.scheduler().main("depths-crystals", r);
            }
        }, () -> REASON + ":" + UUID.randomUUID(), MAX_ATTEMPTS);
    }

    public void table(DropTable table) {
        this.table = table == null ? DropTable.EMPTY : table;
    }

    public DropTable table() {
        return table;
    }

    public void start() {
        if (task != null) {
            task.cancel();
        }
        long ticks = Math.max(1, config.crystalsBatchSeconds) * 20L;
        task = core.scheduler().repeat("depths-crystals", ticks, ticks, this::payCrystals);
        if (overflowTask != null) {
            overflowTask.cancel();
        }
        long boxTicks = Math.max(1, config.satchelBoxFlushSeconds) * 20L;
        overflowTask = core.scheduler().repeat("depths-overflow", boxTicks, boxTicks, this::flushOverflow);
    }

    /** Shutdown: pays the open crystals and waits up to 3 s for the payments. */
    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        if (overflowTask != null) {
            overflowTask.cancel();
            overflowTask = null;
        }
        List<CompletableFuture<?>> started = new java.util.ArrayList<>(payCrystals());
        started.addAll(flushOverflow());
        try {
            CompletableFuture.allOf(started.toArray(CompletableFuture[]::new)).get(3, TimeUnit.SECONDS);
        } catch (Exception e) {
            core.plugin().getLogger().log(Level.WARNING, "Depths: crystal payouts did not finish in 3 s", e);
        }
    }

    private Lookup lookup(ResourceBreakEvent event) {
        if (event == lastEvent) {
            return lastZone == null ? null : new Lookup(lastZone, lastDrops);
        }
        Zone zone = zones.zoneAt(event.getLocation());
        DropTable.TierDrops drops = zone == null ? null : table.get(zone.aspect().id(), zone.tier());
        lastEvent = event;
        lastZone = drops == null ? null : zone;
        lastDrops = drops;
        return drops == null ? null : new Lookup(zone, drops);
    }

    private record Lookup(Zone zone, DropTable.TierDrops drops) {
    }

    /**
     * Before XP and tool listeners (MONITOR). R3-22: only a loading satchel, or a full satchel with the /box at its hard
     * cap, refuses the break (throttled warning). Tier locks cancel earlier (LOW).
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCheck(ResourceBreakEvent event) {
        if (!config.satchelEnabled || lookup(event) == null) {
            return;
        }
        Player player = event.getPlayer();
        SatchelManager.Status status = satchel.status(player);
        DropOverflow.Route route = DropOverflow.forBreak(status != SatchelManager.Status.LOADING, status == SatchelManager.Status.FULL,
                status == SatchelManager.Status.FULL && ItemBox.hardFull(core.services(), player));
        switch (route) {
            case REFUSE_LOADING -> {
                event.setCancelled(true);
                satchel.warnLoading(player);
            }
            case REFUSE_BOX_FULL -> {
                event.setCancelled(true);
                satchel.warnBoxFull(player, ItemBox.of(core.services()).map(ItemBox::hardCap).orElse(0));
            }
            default -> {
                // satchel, or inventory → /box: decided again per item on MONITOR
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(ResourceBreakEvent event) {
        if (!config.satchelEnabled) {
            return;
        }
        Lookup found = lookup(event);
        lastEvent = null;
        if (found == null) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack tool = event.getTool();
        String activity = found.zone().aspect().skill().id();
        BonusService bonus = core.services().optional(BonusService.class).orElse(null);
        BonusContext ctx = new BonusContext(player, tool, null, activity);
        Optional<ToolService> tools = core.services().optional(ToolService.class);
        double fortune = tools.map(t -> t.dropMultiplier(tool))
                .orElseGet(() -> bonus == null ? 1.0 : bonus.multiplier(BonusFamily.EXTRA_DROPS, ctx));
        double crystalBonus = bonus == null ? 1.0 : bonus.multiplier(BonusFamily.CRYSTALS, ctx);
        double rareBonus = bonus == null ? 1.0 : bonus.multiplier(BonusFamily.RARE_FINDS, ctx);
        DropRoller.Roll roll = DropRoller.roll(found.drops(), fortune, crystalBonus, rareBonus, config.dropsMaxPerBlock,
                ThreadLocalRandom.current());
        int amount = roll.items();
        long crystalsFound = roll.crystals();
        Map<String, Integer> extra = roll.rare();
        if (DepthsDropEvent.getHandlerList().getRegisteredListeners().length > 0) {
            DepthsDropEvent hook = new DepthsDropEvent(player, event.getBlock(), tool, found.zone().aspect().id(), found.zone().tier(),
                    found.drops().item(), amount, crystalsFound, extra, found.drops().crystalsPerBlock());
            Bukkit.getPluginManager().callEvent(hook);
            amount = hook.getItems();
            crystalsFound = hook.getCrystals();
            extra = hook.getExtra();
        }
        deliver(player, found.drops().item(), amount, false);
        if (crystalsFound > 0 && core.services().optional(EconomyService.class).isPresent()) {
            crystals.add(satchel.profileKey(player), crystalsFound);
            satchel.addCrystals(player, crystalsFound);
        }
        if (!extra.isEmpty()) {
            extra.forEach((id, n) -> {
                if (n != null && n > 0 && items.def(id) != null) {
                    deliver(player, id, n, false);
                    msg.send(player, "drops.rare", Msg.mm("item", items.name(items.def(id))), Msg.number("amount", n));
                }
            });
            if (config.dropsRareSound != null && !config.dropsRareSound.isBlank()) {
                core.sounds().play(player, config.dropsRareSound);
            }
        }
    }

    // ------------------------------------------------------------------ R3-22 drop chain: satchel → inventory → /box

    /**
     * Hands Depths drops to a player (main thread): satchel while it has room; otherwise (full, or for a kill also off or
     * loading) the inventory, and what does not fit waits in a per-profile batch for the /box. A full satchel shows the
     * throttled "drops go to your inventory, then /box" warning.
     *
     * @param kill true for combat drops (always delivered), false for mining (the break was checked on HIGH)
     */
    public void deliver(Player player, String item, long amount, boolean kill) {
        if (amount <= 0 || item == null) {
            return;
        }
        SatchelManager.Status status = satchel.status(player);
        DropOverflow.Route route = kill
                ? DropOverflow.forKill(config.satchelEnabled, status != SatchelManager.Status.LOADING, status == SatchelManager.Status.FULL)
                : DropOverflow.forBreak(status != SatchelManager.Status.LOADING, status == SatchelManager.Status.FULL, false);
        if (route == DropOverflow.Route.SATCHEL) {
            satchel.add(player, item, amount);
            return;
        }
        // OVERFLOW, or REFUSE_LOADING when the satchel was unloaded between the check and the break (profile switch):
        // never lose the drop, it goes to the inventory / box like an overflow.
        if (status == SatchelManager.Status.FULL) {
            satchel.warnFull(player);
        }
        long left = toInventory(player, item, amount);
        satchel.addOverflow(player, item, amount);
        if (left > 0) {
            overflow.add(satchel.profileKey(player), item, left);
        }
    }

    /** Puts as much as fits into the inventory (staff mode and a profile switch go straight to /box). Returns the rest. */
    private long toInventory(Player player, String item, long amount) {
        if (core.items().get(item).isEmpty()) {
            core.plugin().getLogger().warning("Depths: drop item '" + item + "' is not registered; " + amount + " lost for " + player.getName());
            return 0;
        }
        ItemStack proto = core.items().create(item, 1, BoxSource.DEPTHS, null);
        int maxStack = Math.max(1, proto.getMaxStackSize());
        long given = 0;
        for (int size : net.skybornmc.skyblock.depths.satchel.SatchelMath.stacks(amount, maxStack)) {
            ItemStack stack = core.items().create(item, size, BoxSource.DEPTHS, null);
            Map<Integer, ItemStack> rest = player.getInventory().addItem(stack);
            int notGiven = rest.values().stream().mapToInt(ItemStack::getAmount).sum();
            given += size - notGiven;
            if (notGiven > 0) {
                break; // inventory full: the rest goes to the box batch
            }
        }
        return amount - given;
    }

    /** Hands every waiting batch to the /box (one delivery per profile). Returns the started deliveries. */
    private List<CompletableFuture<?>> flushOverflow() {
        if (overflow.isEmpty()) {
            return List.of();
        }
        List<CompletableFuture<?>> started = new java.util.ArrayList<>();
        overflow.drainAll().forEach((profile, waiting) -> {
            CompletableFuture<?> f = toBox(profile, waiting);
            if (f != null) {
                started.add(f);
            }
        });
        return started;
    }

    /** Flushes one profile's batch right away (quit, profile switch). */
    public void flushOverflow(UUID profile) {
        Map<String, Long> waiting = overflow.drain(profile);
        if (!waiting.isEmpty()) {
            toBox(profile, waiting);
        }
    }

    private CompletableFuture<?> toBox(UUID profile, Map<String, Long> waiting) {
        List<ItemStack> stacks = new java.util.ArrayList<>();
        waiting.forEach((item, n) -> {
            if (core.items().get(item).isEmpty()) {
                core.plugin().getLogger().warning("Depths: drop item '" + item + "' is not registered; " + n + " not sent to /box");
                return;
            }
            int maxStack = Math.max(1, core.items().create(item, 1, BoxSource.DEPTHS, null).getMaxStackSize());
            for (int size : net.skybornmc.skyblock.depths.satchel.SatchelMath.stacks(n, maxStack)) {
                stacks.add(core.items().create(item, size, BoxSource.DEPTHS, null));
            }
        });
        if (stacks.isEmpty()) {
            return null;
        }
        Optional<ItemBox> box = ItemBox.of(core.services());
        if (box.isPresent()) {
            return box.get().deliver(profile, stacks, BoxSource.DEPTHS, Actor.system("depths"));
        }
        Optional<Player> online = ProfileService.of(core.services()).onlinePlayer(profile);
        if (online.isPresent()) {
            ItemBox.overflow(core.services(), online.get(), BoxSource.DEPTHS, stacks); // box module off: dropped at the feet
        } else {
            core.plugin().getLogger().warning("Depths: the box module is off and profile " + profile + " is offline; "
                    + stacks.size() + " overflow stack(s) could not be stored");
        }
        return null;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(org.bukkit.event.player.PlayerQuitEvent event) {
        flushOverflow(satchel.profileKey(event.getPlayer()));
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onProfileSwitch(net.skybornmc.skyblock.common.profile.ProfileSwitchedEvent event) {
        if (!overflow.isEmpty()) {
            flushOverflow(); // keyed by profile, so each batch still goes to the profile it was found on
        }
    }

    /**
     * Crystals found outside a block break (M4.8 combat kills): added to the player's profile batch (paid with the next
     * payout, ledger reason {@code depths_drop}) and to the satchel's batched audit row. Main thread.
     */
    public void credit(Player player, long crystalsFound) {
        if (crystalsFound <= 0 || core.services().optional(EconomyService.class).isEmpty()) {
            return;
        }
        crystals.add(satchel.profileKey(player), crystalsFound);
        satchel.addCrystals(player, crystalsFound);
    }

    private List<CompletableFuture<?>> payCrystals() {
        Optional<EconomyService> economy = core.services().optional(EconomyService.class);
        if (economy.isEmpty() || crystals.idle()) {
            return List.of();
        }
        EconomyService eco = economy.get();
        return crystals.flush((profile, amount, key) -> eco.execute(
                Transaction.deposit(AccountRef.player(profile), Currency.CRYSTALS, amount, REASON)
                        .actor(Actor.system("depths"))
                        .reference("depths")
                        .idempotencyKey(key)
                        .detail("target_name", nameOf(profile))), this::settled);
    }

    private String nameOf(UUID profile) {
        UUID owner = ProfileService.of(core.services()).ownerOf(profile);
        Player p = Bukkit.getPlayer(owner);
        return p == null ? owner.toString().substring(0, 8) : p.getName();
    }

    private void settled(UUID profile, long amount, CrystalBatcher.Outcome outcome, TxResult.Status status) {
        switch (outcome) {
            case PAID -> {
                if (!config.crystalsActionBar) {
                    return;
                }
                ProfileService.of(core.services()).onlinePlayer(profile).ifPresent(p ->
                        msg.actionBar(p, "drops.crystals", Msg.number("amount", amount)));
            }
            case ALREADY_PAID -> {
                // an earlier attempt with the same key went through: nothing to do
            }
            case DROPPED -> core.plugin().getLogger().warning("Depths: " + amount + " crystals of profile " + profile
                    + " could not be paid (" + status + ") and were dropped.");
        }
    }
}
