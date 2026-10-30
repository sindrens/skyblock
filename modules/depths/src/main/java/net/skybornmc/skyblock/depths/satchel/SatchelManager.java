package net.skybornmc.skyblock.depths.satchel;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.skybornmc.skyblock.common.Core;
import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.audit.AuditCategory;
import net.skybornmc.skyblock.common.economy.AccountRef;
import net.skybornmc.skyblock.common.economy.Currency;
import net.skybornmc.skyblock.common.economy.EconomyService;
import net.skybornmc.skyblock.common.economy.Transaction;
import net.skybornmc.skyblock.common.economy.TxResult;
import net.skybornmc.skyblock.common.profile.ProfileDeletedEvent;
import net.skybornmc.skyblock.common.profile.ProfileService;
import net.skybornmc.skyblock.common.profile.ProfileSwitchedEvent;
import net.skybornmc.skyblock.common.satchel.SatchelService;
import net.skybornmc.skyblock.common.sell.SellPrice;
import net.skybornmc.skyblock.common.sell.SellQuote;
import net.skybornmc.skyblock.common.sell.SellRequest;
import net.skybornmc.skyblock.common.sell.SellResult;
import net.skybornmc.skyblock.common.sell.SellService;
import net.skybornmc.skyblock.common.skills.SkillService;
import net.skybornmc.skyblock.common.skills.SkillType;
import net.skybornmc.skyblock.common.text.ModuleMessages;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.util.Format;
import net.skybornmc.skyblock.common.util.Scheduler;
import net.skybornmc.skyblock.depths.DepthsConfig;
import net.skybornmc.skyblock.depths.DepthsPerms;
import net.skybornmc.skyblock.depths.drops.DropItems;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.ToIntFunction;
import java.util.logging.Level;

/**
 * The satchel at runtime (M4.4), published as {@link SatchelService}: per-profile state, loading on join / profile
 * switch, batched saves and audit rows, the satchel full check for the drops listener, and the player actions (sell
 * all, sell one item, take out, buy an upgrade). Main thread only.
 */
public final class SatchelManager implements SatchelService, Listener {

    /** Ledger reason of satchel upgrades. */
    public static final String REASON_UPGRADE = "satchel_upgrade";

    private final Core core;
    private final DepthsConfig config;
    private final ModuleMessages msg;
    private final DropItems items;
    private final SatchelLedger ledger;
    private final Map<UUID, SatchelState> states = new HashMap<>();
    /** Online player → the profile key their satchel belongs to. */
    private final Map<UUID, UUID> keys = new HashMap<>();
    private final Map<String, ToIntFunction<Player>> bonuses = new ConcurrentHashMap<>();
    private List<SatchelMath.Upgrade> upgrades = List.of();
    private Scheduler.Task saveTask;
    private Scheduler.Task auditTask;

    public SatchelManager(Core core, DepthsConfig config, ModuleMessages msg, DropItems items) {
        this.core = core;
        this.config = config;
        this.msg = msg;
        this.items = items;
        Executor main = r -> {
            if (core.plugin().isEnabled()) {
                core.scheduler().main("depths-satchel", r);
            }
        };
        this.ledger = new SatchelLedger(core.database(), main, System::currentTimeMillis, core.plugin().getLogger());
    }

    // ------------------------------------------------------------------ lifecycle

    public void start() {
        reload();
        for (Player player : Bukkit.getOnlinePlayers()) {
            attach(player);
        }
    }

    public void reload() {
        upgrades = SatchelMath.upgrades(config.satchelUpgrades, w -> core.plugin().getLogger().warning("depths.yml → " + w));
        if (saveTask != null) {
            saveTask.cancel();
        }
        if (auditTask != null) {
            auditTask.cancel();
        }
        long save = Math.max(1, config.satchelSaveSeconds) * 20L;
        saveTask = core.scheduler().repeat("depths-satchel-save", save, save, this::saveAll);
        long audit = Math.max(10, config.satchelAuditSeconds) * 20L;
        auditTask = core.scheduler().repeat("depths-satchel-audit", audit, audit, this::auditAll);
    }

    /** Shutdown: audit rows and every unwritten change are written right now (blocking, on this thread). */
    public void stop() {
        if (saveTask != null) {
            saveTask.cancel();
        }
        if (auditTask != null) {
            auditTask.cancel();
        }
        auditAll();
        for (SatchelState state : states.values()) {
            ledger.flushBlocking(state);
        }
        states.clear();
        keys.clear();
    }

    // ------------------------------------------------------------------ players and profiles

    private void attach(Player player) {
        UUID key = ProfileService.of(core.services()).profileKey(player);
        keys.put(player.getUniqueId(), key);
        SatchelState state = states.computeIfAbsent(key, SatchelState::new);
        state.owner = player.getUniqueId();
        state.ownerName = player.getName();
        if (!state.loaded() && !state.loading()) {
            ledger.load(state).thenAccept(ok -> evictIfIdle(state));
        }
    }

    private void detach(UUID player) {
        UUID key = keys.remove(player);
        SatchelState state = key == null ? null : states.get(key);
        if (state == null) {
            return;
        }
        audit(state);
        ledger.flush(state).thenAccept(ok -> evictIfIdle(state));
    }

    private boolean attached(UUID profile) {
        return keys.containsValue(profile);
    }

    private void evictIfIdle(SatchelState state) {
        if (!attached(state.profile) && state.idle() && states.get(state.profile) == state) {
            states.remove(state.profile);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        attach(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        detach(event.getPlayer().getUniqueId());
    }

    /** PROF: the satchel is per profile; the old one is saved, the new one loaded (or reused if still in memory). */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onProfileSwitched(ProfileSwitchedEvent event) {
        detach(event.getPlayer().getUniqueId());
        attach(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onProfileDeleted(ProfileDeletedEvent event) {
        UUID key = event.profile().id();
        SatchelState state = states.computeIfAbsent(key, SatchelState::new);
        state.takePending();
        state.takeGains();
        state.takeGainedCrystals();
        ledger.delete(state).whenComplete((v, e) -> {
            if (e != null) {
                core.plugin().getLogger().log(Level.WARNING, "Satchel: could not remove the satchel of deleted profile " + key, e);
            }
            if (!attached(key) && states.get(key) == state && state.inFlight == 0) {
                states.remove(key);
            }
        });
    }

    /** The loaded-or-loading state of a player's active profile (null for unknown players). */
    SatchelState state(Player player) {
        UUID key = keys.get(player.getUniqueId());
        return key == null ? null : states.get(key);
    }

    // ------------------------------------------------------------------ batched saves and audit

    private void saveAll() {
        for (SatchelState state : new ArrayList<>(states.values())) {
            if (state.hasPending()) {
                ledger.flush(state).thenAccept(ok -> evictIfIdle(state));
            } else if (!state.loaded() && !state.loading() && attached(state.profile)) {
                ledger.load(state); // a failed load is retried here
            } else {
                evictIfIdle(state);
            }
        }
    }

    private void auditAll() {
        for (SatchelState state : new ArrayList<>(states.values())) {
            audit(state);
        }
    }

    /** One audit row with every item and crystal found since the last one (not one row per block). */
    private void audit(SatchelState state) {
        if (!state.hasGains()) {
            return;
        }
        Map<String, Long> gained = state.takeGains();
        long crystals = state.takeGainedCrystals();
        long total = gained.values().stream().mapToLong(Long::longValue).sum();
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("items", gained);
        details.put("crystals", crystals);
        details.put("profile", state.profile.toString());
        core.audit().logPlayer(AuditCategory.ITEM, "depths_drops", Actor.system("depths"), state.owner == null ? state.profile : state.owner,
                state.ownerName, total, null, details);
    }

    // ------------------------------------------------------------------ drops (called by the drops listener)

    /**
     * Whether a break may give drops (main thread, before the break is accepted). Shows the warning and returns false when
     * the satchel is full or still loading.
     */
    public boolean accepts(Player player) {
        SatchelState state = state(player);
        if (state == null || !state.loaded()) {
            if (state != null && !state.loading()) {
                ledger.load(state);
            }
            msg.actionBar(player, "satchel.loading");
            return false;
        }
        long capacity = capacity(player);
        if (SatchelMath.full(state.used(), capacity)) {
            long now = System.currentTimeMillis();
            if (now - state.lastFullWarning >= config.satchelFullWarningMs) {
                state.lastFullWarning = now;
                msg.actionBar(player, "satchel.full", Msg.number("used", state.used()), Msg.number("max", capacity));
                sound(player, config.satchelFullSound);
            }
            return false;
        }
        return true;
    }

    /** Puts drops into the player's satchel (O(1)). The capacity was checked by {@link #accepts}. */
    public void add(Player player, String item, long amount) {
        SatchelState state = state(player);
        if (state != null && amount > 0) {
            state.add(item, amount);
        }
    }

    /** Remembers crystals for the batched audit row (the money side is the crystal batcher's). */
    public void addCrystals(Player player, long crystals) {
        SatchelState state = state(player);
        if (state != null) {
            state.addCrystals(crystals);
        }
    }

    /** The profile key a player's drops belong to right now. */
    public UUID profileKey(Player player) {
        UUID key = keys.get(player.getUniqueId());
        return key == null ? ProfileService.of(core.services()).profileKey(player) : key;
    }

    // ------------------------------------------------------------------ SatchelService

    @Override
    public boolean loaded(Player player) {
        SatchelState state = state(player);
        return state != null && state.loaded();
    }

    @Override
    public long amount(Player player, String itemId) {
        SatchelState state = state(player);
        return state == null || !state.loaded() ? 0 : state.amount(itemId);
    }

    @Override
    public Map<String, Long> contents(Player player) {
        SatchelState state = state(player);
        return state == null || !state.loaded() ? Map.of() : Map.copyOf(state.amounts());
    }

    @Override
    public long used(Player player) {
        SatchelState state = state(player);
        return state == null ? 0 : state.used();
    }

    @Override
    public long capacity(Player player) {
        SatchelState state = state(player);
        int mining = 0;
        int foraging = 0;
        Optional<SkillService> skills = core.services().optional(SkillService.class);
        if (skills.isPresent()) {
            mining = skills.get().level(player, SkillType.MINING);
            foraging = skills.get().level(player, SkillType.FORAGING);
        }
        long bonus = 0;
        for (ToIntFunction<Player> source : bonuses.values()) {
            try {
                bonus += Math.max(0, source.applyAsInt(player));
            } catch (RuntimeException e) {
                core.plugin().getLogger().log(Level.WARNING, "Satchel: a capacity bonus failed", e);
            }
        }
        return SatchelMath.capacity(config.satchelBaseCapacity, config.satchelPerMiningLevel, mining, config.satchelPerForagingLevel,
                foraging, upgrades, state == null ? 0 : state.upgradeLevel(), bonus, config.satchelMaxCapacity);
    }

    @Override
    public boolean consume(Player player, Map<String, Integer> amounts, String reason, Actor actor) {
        SatchelState state = state(player);
        if (state == null || !state.loaded() || state.busy) {
            return false;
        }
        Map<String, Long> wanted = new LinkedHashMap<>();
        amounts.forEach((id, n) -> {
            if (n != null && n > 0) {
                wanted.merge(id, (long) n, Long::sum);
            }
        });
        if (wanted.isEmpty()) {
            return true;
        }
        if (!state.consume(wanted)) {
            return false;
        }
        ledger.flush(state); // written right away, not with the next batch
        core.audit().logPlayer(AuditCategory.ITEM, "satchel_use", actor, player.getUniqueId(), player.getName(),
                wanted.values().stream().mapToLong(Long::longValue).sum(), player.getLocation(),
                Map.of("items", wanted, "reason", reason == null ? "other" : reason, "profile", state.profile.toString()));
        return true;
    }

    @Override
    public void capacityBonus(String id, ToIntFunction<Player> bonus) {
        bonuses.put(id, bonus);
    }

    @Override
    public void removeCapacityBonus(String id) {
        bonuses.remove(id);
    }

    // ------------------------------------------------------------------ prices

    public Optional<SellService> sellService() {
        return core.services().optional(SellService.class);
    }

    /** Today's price of one item, or empty if it can't be sold. */
    public Optional<SellPrice> price(String itemId) {
        return sellService().flatMap(s -> s.price("custom:" + itemId));
    }

    /** About what these items sell for now, before the sell bonus (menus show it with ≈). */
    public long value(String itemId, long amount) {
        return price(itemId).map(p -> (long) Math.floor(p.unit() * amount)).orElse(0L);
    }

    /** The player's sell bonus after the cap, in percent. */
    public double bonusPercent(Player player) {
        return sellService().map(s -> Math.max(-100, Math.min(s.bonusCapPercent(), s.bonusPercent(player)))).orElse(0.0);
    }

    public String money(long amount) {
        return core.services().optional(EconomyService.class).map(e -> e.format(Currency.MONEY, amount)).orElse(Format.money(amount));
    }

    // ------------------------------------------------------------------ actions

    /** Sells everything sellable in the satchel. */
    public void sellAll(Player player) {
        SatchelState state = state(player);
        if (!ready(player, state)) {
            return;
        }
        Map<String, Long> wanted = new LinkedHashMap<>();
        for (String id : items.order()) {
            long n = state.amount(id);
            if (n > 0 && price(id).isPresent()) {
                wanted.put(id, n);
            }
        }
        state.amounts().forEach((id, n) -> {
            if (!wanted.containsKey(id) && n > 0 && price(id).isPresent()) {
                wanted.put(id, n);
            }
        });
        sell(player, state, wanted);
    }

    /** Sells all of one item. */
    public void sellItem(Player player, String itemId) {
        SatchelState state = state(player);
        if (!ready(player, state)) {
            return;
        }
        long n = state.amount(itemId);
        if (n <= 0 || price(itemId).isEmpty()) {
            msg.send(player, "satchel.sell-none");
            sound(player, "error");
            return;
        }
        sell(player, state, Map.of(itemId, n));
    }

    private boolean ready(Player player, SatchelState state) {
        if (state == null || !state.loaded()) {
            msg.send(player, "satchel.not-loaded");
            sound(player, "error");
            return false;
        }
        if (state.busy) {
            msg.send(player, "satchel.busy");
            sound(player, "error");
            return false;
        }
        if (ProfileService.of(core.services()).isSwitching(player.getUniqueId())) {
            msg.send(player, "satchel.busy");
            return false;
        }
        return true;
    }

    private void sell(Player player, SatchelState state, Map<String, Long> wanted) {
        Optional<SellService> service = sellService();
        if (service.isEmpty()) {
            msg.send(player, "satchel.sell-off");
            sound(player, "error");
            return;
        }
        if (wanted.isEmpty()) {
            msg.send(player, "satchel.sell-none");
            sound(player, "error");
            return;
        }
        SellService sell = service.get();
        List<ItemStack> stacks = new ArrayList<>();
        for (Map.Entry<String, Long> e : wanted.entrySet()) {
            int max = items.def(e.getKey()) == null ? 64 : Math.max(1, items.def(e.getKey()).material().getMaxStackSize());
            for (int size : SatchelMath.stacks(e.getValue(), max)) {
                stacks.add(core.items().create(e.getKey(), size, "satchel", null));
            }
        }
        double bonus = sell.bonusPercent(player);
        SellQuote quote = sell.quote(stacks, bonus);
        if (quote.isEmpty()) {
            msg.send(player, "satchel.sell-none");
            sound(player, "error");
            return;
        }
        if (quote.total() <= 0) {
            msg.send(player, "satchel.too-cheap");
            sound(player, "error");
            return;
        }
        if (!state.escrow(wanted)) {
            msg.send(player, "satchel.changed");
            sound(player, "error");
            return;
        }
        state.busy = true;
        UUID playerId = player.getUniqueId();
        String name = player.getName();
        SellRequest request = new SellRequest(playerId, name, state.profile, stacks, bonus, "satchel", Actor.player(player));
        ledger.guarded(state, wanted, guard -> sell.sell(request, guard), SellResult::sold).whenComplete((result, error) -> {
            state.busy = false;
            Player online = Bukkit.getPlayer(playerId);
            if (result != null && result.sold()) {
                // Stacks the economy could not sell after all were removed in the same transaction: they come back.
                for (ItemStack back : result.giveBack()) {
                    net.skybornmc.skyblock.common.item.ItemRegistry.idOf(back).ifPresent(id -> state.credit(id, back.getAmount()));
                }
                if (online != null) {
                    SellQuote q = result.quote();
                    TagResolver[] tags = {Msg.number("items", q.items()), Msg.p("money", money(q.total())),
                            Msg.p("bonus", Format.number(Math.round(q.cappedBonusPercent())))};
                    msg.send(online, q.cappedBonusPercent() > 0 ? "satchel.sold-bonus" : "satchel.sold", tags);
                    sound(online, config.satchelSellSound);
                    refreshMenu(online);
                }
            } else if (online != null) {
                String key = result == null ? "satchel.failed" : switch (result.status()) {
                    case TOO_CHEAP -> "satchel.too-cheap";
                    case NOTHING_SELLABLE -> "satchel.sell-none";
                    case OVERFLOW -> "satchel.overflow";
                    default -> "satchel.failed";
                };
                msg.send(online, key);
                sound(online, "error");
                refreshMenu(online);
            }
            evictIfIdle(state);
        });
    }

    /**
     * Takes items out into the inventory: one stack, or as many as fit. One database transaction removes them before
     * they are given; what no longer fits when they arrive goes back into the satchel.
     */
    public void takeOut(Player player, String itemId, boolean all) {
        SatchelState state = state(player);
        if (!config.satchelTakeOut) {
            msg.send(player, "satchel.take-off");
            sound(player, "error");
            return;
        }
        if (!ready(player, state)) {
            return;
        }
        long held = state.amount(itemId);
        if (held <= 0 || core.items().get(itemId).isEmpty()) {
            msg.send(player, "satchel.take-none");
            sound(player, "error");
            return;
        }
        ItemStack proto = core.items().create(itemId, 1, "satchel", null);
        int maxStack = Math.max(1, proto.getMaxStackSize());
        long fits = fits(player.getInventory(), proto, maxStack);
        long n = SatchelMath.takeAmount(all ? held : maxStack, held, fits);
        if (n <= 0) {
            msg.send(player, "satchel.inventory-full");
            sound(player, "error");
            return;
        }
        Map<String, Long> wanted = Map.of(itemId, n);
        if (!state.escrow(wanted)) {
            msg.send(player, "satchel.changed");
            return;
        }
        state.busy = true;
        UUID playerId = player.getUniqueId();
        ledger.takeOut(state, wanted).whenComplete((ok, error) -> {
            state.busy = false;
            Player online = Bukkit.getPlayer(playerId);
            boolean committed = Boolean.TRUE.equals(ok);
            if (!committed) {
                if (online != null) {
                    msg.send(online, "satchel.failed");
                    sound(online, "error");
                    refreshMenu(online);
                }
                evictIfIdle(state);
                return;
            }
            long given = 0;
            if (online != null && state.profile.equals(keys.get(playerId))) {
                for (int size : SatchelMath.stacks(n, maxStack)) {
                    ItemStack stack = core.items().create(itemId, size, "satchel", null);
                    Map<Integer, ItemStack> rest = online.getInventory().addItem(stack);
                    int left = rest.values().stream().mapToInt(ItemStack::getAmount).sum();
                    given += size - left;
                }
            }
            if (given < n) {
                state.credit(itemId, n - given); // no room any more (or offline/switched): back into the satchel
            }
            core.audit().logPlayer(AuditCategory.ITEM, "satchel_take", Actor.player(playerId, state.ownerName), playerId, state.ownerName,
                    given, online == null ? null : online.getLocation(), Map.of("item", itemId, "amount", given, "returned", n - given,
                            "profile", state.profile.toString()));
            if (online != null) {
                if (given > 0) {
                    msg.send(online, "satchel.taken", Msg.number("amount", given), Msg.mm("item", items.def(itemId) == null
                            ? itemId : items.name(items.def(itemId))));
                    sound(online, "success");
                }
                if (given < n) {
                    msg.send(online, "satchel.taken-rest", Msg.number("amount", n - given));
                }
                refreshMenu(online);
            }
            evictIfIdle(state);
        });
    }

    /** How many of {@code proto} fit in the storage slots (empty slots and room on similar stacks). */
    static long fits(PlayerInventory inv, ItemStack proto, int maxStack) {
        int empty = 0;
        long partial = 0;
        for (ItemStack stack : inv.getStorageContents()) {
            if (stack == null || stack.isEmpty()) {
                empty++;
            } else if (stack.isSimilar(proto)) {
                partial += Math.max(0, maxStack - stack.getAmount());
            }
        }
        return SatchelMath.fit(empty, maxStack, partial);
    }

    /** The next upgrade (null when all are bought). */
    public SatchelMath.Upgrade nextUpgrade(Player player) {
        SatchelState state = state(player);
        int level = state == null ? 0 : state.upgradeLevel();
        return level < upgrades.size() ? upgrades.get(level) : null;
    }

    public int upgradeLevel(Player player) {
        SatchelState state = state(player);
        return state == null ? 0 : state.upgradeLevel();
    }

    public int upgradeCount() {
        return upgrades.size();
    }

    /** Buys the next upgrade: the money and the new level in one transaction. */
    public void buyUpgrade(Player player) {
        SatchelState state = state(player);
        if (!ready(player, state)) {
            return;
        }
        Optional<EconomyService> economy = core.services().optional(EconomyService.class);
        SatchelMath.Upgrade next = nextUpgrade(player);
        if (next == null) {
            msg.send(player, "satchel.upgrade-max");
            sound(player, "error");
            return;
        }
        if (economy.isEmpty()) {
            msg.send(player, "satchel.sell-off");
            return;
        }
        int from = state.upgradeLevel();
        int to = from + 1;
        state.busy = true;
        UUID playerId = player.getUniqueId();
        String name = player.getName();
        Transaction tx = Transaction.withdraw(AccountRef.player(state.profile), Currency.MONEY, next.price(), REASON_UPGRADE)
                .actor(Actor.player(player))
                .reference("satchel:" + to)
                .idempotencyKey("satchel_upgrade:" + state.profile + ":" + to)
                .detail("target_name", name)
                .detail("level", to)
                .detail("capacity", next.capacity());
        if (next.price() <= 0) {
            ledger.upgrade(state, from, to, guard -> core.database().transaction(c -> guard.holds(c)
                    ? TxResult.of(TxResult.Status.SUCCESS) : TxResult.of(TxResult.Status.DUPLICATE)))
                    .whenComplete((r, e) -> upgraded(state, playerId, to, next, e == null ? r : null));
            return;
        }
        ledger.upgrade(state, from, to, guard -> economy.get().executeGuarded(tx, guard))
                .whenComplete((r, e) -> upgraded(state, playerId, to, next, e == null ? r : null));
    }

    private void upgraded(SatchelState state, UUID playerId, int to, SatchelMath.Upgrade next, TxResult result) {
        state.busy = false;
        Player online = Bukkit.getPlayer(playerId);
        if (result != null && result.ok()) {
            state.upgradeLevel(to);
            core.audit().logPlayer(AuditCategory.ITEM, "satchel_upgrade", Actor.player(playerId, state.ownerName), playerId,
                    state.ownerName, next.price(), null, Map.of("level", to, "capacity", next.capacity(), "profile", state.profile.toString()));
            if (online != null) {
                msg.send(online, "satchel.upgraded", Msg.p("level", to), Msg.number("capacity", next.capacity()),
                        Msg.number("max", capacity(online)));
                sound(online, "unlock");
            }
        } else if (online != null) {
            String key = result != null && result.status() == TxResult.Status.INSUFFICIENT_FUNDS ? "satchel.upgrade-money" : "satchel.failed";
            msg.send(online, key, Msg.p("price", money(next.price())));
            sound(online, "error");
        }
        if (online != null) {
            refreshMenu(online);
        }
        evictIfIdle(state);
    }

    // ------------------------------------------------------------------ menu helpers

    /** A new satchel menu (the /dungeon button opens it as a child, so Back returns there). */
    public net.skybornmc.skyblock.common.menu.Menu menu() {
        return new SatchelMenu(this);
    }

    public void open(Player player) {
        if (!player.hasPermission(DepthsPerms.SATCHEL)) {
            msg.send(player, "satchel.no-permission");
            return;
        }
        core.menus().open(player, new SatchelMenu(this));
    }

    private void refreshMenu(Player player) {
        if (core.menus().openMenu(player) instanceof SatchelMenu menu) {
            menu.refresh();
        } else if (core.menus().openMenu(player) instanceof SatchelItemMenu menu) {
            menu.refresh();
        }
    }

    Core core() {
        return core;
    }

    DepthsConfig config() {
        return config;
    }

    DropItems items() {
        return items;
    }

    ModuleMessages messages() {
        return msg;
    }

    /** Placeholder text: {@code satchel_used}, {@code satchel_max}. */
    public Component placeholder(Player player, boolean max) {
        if (player == null) {
            return msg.component(null, "placeholders.none");
        }
        SatchelState state = state(player);
        if (state == null || !state.loaded()) {
            return msg.component(player, "placeholders.none");
        }
        long value = max ? capacity(player) : state.used();
        return msg.component(player, max ? "placeholders.satchel-max" : "placeholders.satchel-used", Msg.number("value", value),
                Msg.p("percent", SatchelMath.percent(state.used(), capacity(player))));
    }

    void sound(Player player, String key) {
        if (key != null && !key.isBlank()) {
            core.sounds().play(player, key.toLowerCase(Locale.ROOT));
        }
    }
}
