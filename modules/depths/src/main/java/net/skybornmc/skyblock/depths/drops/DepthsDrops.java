package net.skybornmc.skyblock.depths.drops;

import net.skybornmc.skyblock.common.Core;
import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.bonus.BonusContext;
import net.skybornmc.skyblock.common.bonus.BonusFamily;
import net.skybornmc.skyblock.common.bonus.BonusService;
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
 * (ledger reason {@code depths_drop}, shown on the action bar). A full or still-loading satchel refuses the break
 * before anything else happens (no XP, no drops, the block stays). Per break: one zone lookup, a few random numbers and
 * map updates; no database work.
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
    private volatile DropTable table = DropTable.EMPTY;
    private Scheduler.Task task;
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
    }

    /** Shutdown: pays the open crystals and waits up to 3 s for the payments. */
    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        List<CompletableFuture<?>> started = payCrystals();
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

    /** Before XP and tool listeners (MONITOR): a full satchel refuses the break. Tier locks cancel earlier (LOW). */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCheck(ResourceBreakEvent event) {
        if (!config.satchelEnabled || lookup(event) == null) {
            return;
        }
        if (!satchel.accepts(event.getPlayer())) {
            event.setCancelled(true);
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
                    found.drops().item(), amount, crystalsFound, extra);
            Bukkit.getPluginManager().callEvent(hook);
            amount = hook.getItems();
            crystalsFound = hook.getCrystals();
            extra = hook.getExtra();
        }
        satchel.add(player, found.drops().item(), amount);
        if (crystalsFound > 0 && core.services().optional(EconomyService.class).isPresent()) {
            crystals.add(satchel.profileKey(player), crystalsFound);
            satchel.addCrystals(player, crystalsFound);
        }
        if (!extra.isEmpty()) {
            extra.forEach((id, n) -> {
                if (n != null && n > 0 && items.def(id) != null) {
                    satchel.add(player, id, n);
                    msg.send(player, "drops.rare", Msg.mm("item", items.name(items.def(id))), Msg.number("amount", n));
                }
            });
            if (config.dropsRareSound != null && !config.dropsRareSound.isBlank()) {
                core.sounds().play(player, config.dropsRareSound);
            }
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
