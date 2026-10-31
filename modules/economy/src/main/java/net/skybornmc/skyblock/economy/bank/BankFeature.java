package net.skybornmc.skyblock.economy.bank;

import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.skybornmc.skyblock.common.Core;
import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.command.CommandSpec;
import net.skybornmc.skyblock.common.economy.AccountRef;
import net.skybornmc.skyblock.common.economy.BankService;
import net.skybornmc.skyblock.common.economy.Currency;
import net.skybornmc.skyblock.common.economy.EconomyService;
import net.skybornmc.skyblock.common.island.IslandRemovedEvent;
import net.skybornmc.skyblock.common.island.IslandService;
import net.skybornmc.skyblock.common.island.IslandService.Role;
import net.skybornmc.skyblock.common.menu.Menu;
import net.skybornmc.skyblock.common.profile.ProfileService;
import net.skybornmc.skyblock.common.text.ModuleMessages;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.util.Format;
import net.skybornmc.skyblock.common.util.Scheduler;
import net.skybornmc.skyblock.economy.EconomyModule;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

/**
 * Banks (M3.3) inside the economy module: {@code /bank} (personal and island bank side by side), deposits and
 * withdrawals with the number picker, personal tiers, island bank history, interest on a schedule computed off the main
 * thread, the activity rule and {@link BankService}.
 *
 * <p><b>Safety.</b> Every balance change is a ledger transaction run by {@link BankOps}. Who may touch which bank is
 * decided on the main thread right before the transaction (active profile, island membership and role permission),
 * and the amount limits (capacity, bank balance, daily island limit) are checked again inside the transaction. P4: a
 * personal bank is only reachable from its own profile; an island bank only from the profile that is a member of it.</p>
 */
public final class BankFeature implements Listener, BankService {

    public static final String PERM_BANK = "skyblock.economy.bank";

    final EconomyModule module;
    final Core core;
    final ModuleMessages msg;
    private final EconomyService economy;
    final BankConfig config = new BankConfig();
    private volatile BankTiers tiers;
    private volatile WithdrawFee fees;
    private BankOps ops;
    /** False until bank states of an older period length are converted (BANK2); the bank stays closed meanwhile. */
    private final AtomicBoolean ready = new AtomicBoolean();
    private final List<Scheduler.Task> tasks = new ArrayList<>();
    /** Online player → when their online time was last counted for the activity rule. */
    private final Map<UUID, Long> counted = new ConcurrentHashMap<>();
    private final AtomicBoolean sweeping = new AtomicBoolean();
    /** Players with a bank action on its way (no double clicks). */
    private final Set<UUID> busy = ConcurrentHashMap.newKeySet();

    public BankFeature(EconomyModule module, Core core, ModuleMessages msg, EconomyService economy) {
        this.module = module;
        this.core = core;
        this.msg = msg;
        this.economy = economy;
    }

    // ================================================================== lifecycle

    public void enable() {
        loadConfig();
        BankStore store = new BankStore(core.database(), System::currentTimeMillis, config.periodMillis());
        ops = new BankOps(economy, store, () -> config, () -> tiers, this::profiles, System::currentTimeMillis, BankFeature::today,
                core.plugin().getLogger());
        core.services().register(EconomyModule.ID, BankService.class, this);
        core.plugin().getServer().getPluginManager().registerEvents(this, core.plugin());
        core.commands().register(CommandSpec.command("bank")
                .description("Your personal bank and your island bank, with interest")
                .category("Economy")
                .permission(PERM_BANK)
                .playerOnly()
                .executes(ctx -> open(ctx.player(), null)));
        long now = System.currentTimeMillis();
        for (Player p : Bukkit.getOnlinePlayers()) {
            counted.put(p.getUniqueId(), now);
        }
        convertPeriods(store, 1);
        long flush = config.activityFlushMinutes * 1_200L;
        tasks.add(core.scheduler().repeat("bank:activity", flush, flush, this::countActivity));
        long sweep = config.sweepMinutes * 1_200L;
        tasks.add(core.scheduler().asyncRepeat(200L, sweep, this::sweep));
        // Housekeeping once a day: old activity days and withdraw counters are never read again.
        tasks.add(core.scheduler().asyncRepeat(6_000L, 1_728_000L, () -> {
            int day = today();
            ops.store().purgeActivity(day - Math.max(config.activityDays, 7) - 1);
            ops.store().purgeWithdrawn(day - 2);
        }));
    }

    public void reload() {
        loadConfig();
        if (ops != null && config.periodMillis() != ops.periodMillis()) {
            core.plugin().getLogger().warning("bank.yml: interest.period-hours " + config.periodHours + " takes effect after a "
                    + "restart (still paying every " + ops.periodMillis() / 3_600_000d + " h)");
        }
    }

    private void loadConfig() {
        upgradeConfigFile();
        core.configs().load(config);
        BankTiers parsed = BankTiers.parse(config);
        for (String problem : parsed.problems()) {
            core.plugin().getLogger().warning("bank.yml: " + problem + " (the built-in table is used instead)");
        }
        tiers = parsed;
        WithdrawFee fee = WithdrawFee.of(config);
        for (String problem : fee.problems()) {
            core.plugin().getLogger().warning("bank.yml: " + problem);
        }
        fees = fee;
    }

    /** BANK2/UX3 value upgrade of an existing bank.yml ({@link BankConfig#upgradeValues}); never touches a broken file. */
    private void upgradeConfigFile() {
        java.io.File file = new java.io.File(core.plugin().getDataFolder(), config.fileName());
        if (!file.isFile()) {
            return;
        }
        org.bukkit.configuration.file.YamlConfiguration yaml = new org.bukkit.configuration.file.YamlConfiguration();
        try {
            yaml.load(file);
            List<String> changed = BankConfig.upgradeValues(yaml);
            if (!changed.isEmpty()) {
                yaml.save(file);
                core.plugin().getLogger().info(config.fileName() + ": new bank defaults applied (BANK2 interest, UX3 tier "
                        + "tables): " + String.join(", ", changed));
                core.audit().log(net.skybornmc.skyblock.common.audit.AuditCategory.ECONOMY, "bank_config_upgrade",
                        Actor.system("bank"), Map.of("keys", String.join(",", changed), "version", config.version()));
            }
        } catch (java.io.IOException | org.bukkit.configuration.InvalidConfigurationException e) {
            // The config manager reports broken files and keeps every value; nothing to do here.
        }
    }

    /**
     * BANK2: converts bank states of another period length (M3.3: 3 h) before the bank opens. The bank stays closed
     * until every row is converted (one retry), so no payout is ever computed with the wrong period numbers.
     */
    private void convertPeriods(BankStore store, int attempt) {
        store.convertPeriods(500).thenCompose(result -> store.unconverted().thenApply(left -> {
            if (result.rows() > 0) {
                core.plugin().getLogger().info("Bank: " + result.rows() + " bank state(s) converted to "
                        + store.periodMillis() / 3_600_000d + " h interest periods " + result.fromMinutes());
                core.audit().log(net.skybornmc.skyblock.common.audit.AuditCategory.ECONOMY, "bank_period_convert",
                        Actor.system("bank"), Map.of("rows", result.rows(), "to_minutes", store.periodMinutes(),
                                "from_minutes", result.fromMinutes().toString()));
            }
            return left;
        })).whenComplete((left, error) -> {
            if (error == null && left == 0) {
                ready.set(true);
                return;
            }
            if (attempt < 2) {
                core.plugin().getLogger().warning("Bank: converting interest periods is not finished ("
                        + (error != null ? error.getMessage() : left + " left") + "), trying again");
                convertPeriods(store, attempt + 1);
                return;
            }
            core.plugin().getLogger().log(Level.SEVERE, "Bank: interest periods could not be converted (" + left
                    + " left); banks stay closed until the next restart", error);
        });
    }

    /** True once the bank may be used (enabled and period conversion done). */
    boolean ready() {
        return ready.get();
    }

    public void disable() {
        countActivity();
        tasks.forEach(Scheduler.Task::cancel);
        tasks.clear();
        for (Player p : Bukkit.getOnlinePlayers()) {
            Menu open = core.menus().openMenu(p);
            if (open instanceof BankMenu || open instanceof BankTierMenu || open instanceof BankHistoryMenu) {
                p.closeInventory();
            }
        }
        HandlerList.unregisterAll(this);
    }

    BankOps ops() {
        return ops;
    }

    BankTiers tiers() {
        return tiers;
    }

    ProfileService profiles() {
        return ProfileService.of(core.services());
    }

    Optional<IslandService> islands() {
        return core.services().optional(IslandService.class);
    }

    static int today() {
        return (int) LocalDate.now(Format.zone()).toEpochDay();
    }

    // ================================================================== activity rule

    /** Main thread: adds the whole minutes each online player played since the last count to their active profile. */
    private void countActivity() {
        if (ops == null) {
            return;
        }
        long now = System.currentTimeMillis();
        Map<UUID, Integer> minutes = new HashMap<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            addMinutes(p, now, minutes);
        }
        ops.store().addActivity(minutes, today()).exceptionally(e -> {
            core.plugin().getLogger().log(Level.WARNING, "Bank: could not save activity minutes", e);
            return null;
        });
    }

    private void addMinutes(Player p, long now, Map<UUID, Integer> into) {
        long since = counted.getOrDefault(p.getUniqueId(), now);
        int whole = (int) Math.min(Integer.MAX_VALUE, Math.max(0, (now - since) / 60_000L));
        if (whole > 0) {
            counted.put(p.getUniqueId(), since + whole * 60_000L);
            into.merge(profiles().profileKey(p), whole, Integer::sum);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        counted.put(player.getUniqueId(), System.currentTimeMillis());
        if (config.enabled) {
            // Lazy settlement: pay what became due while the player was away (the sweep also does it).
            core.scheduler().later("bank:join", 40L, () -> {
                if (player.isOnline()) {
                    settleFor(player);
                }
            });
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Map<UUID, Integer> minutes = new HashMap<>();
        addMinutes(event.getPlayer(), System.currentTimeMillis(), minutes);
        counted.remove(event.getPlayer().getUniqueId());
        busy.remove(event.getPlayer().getUniqueId());
        ops.store().addActivity(minutes, today());
    }

    // ================================================================== island removal

    /**
     * M2.7 removal (owner answer 263): a disband deletes what is left in the island bank (it belongs to the island, not
     * to players); staff deletes and resets keep the money in the closed bank for staff to decide.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onIslandRemoved(IslandRemovedEvent event) {
        AccountRef bank = AccountRef.island(event.islandId());
        if (BankRules.onRemoved(event.reason()) == BankRules.Removal.KEEP_CLOSED) {
            ops.store().deleteState(bank).whenComplete((v, error) -> {
                if (error != null) {
                    core.plugin().getLogger().log(Level.SEVERE, "Bank: closing the bank of island " + event.islandId() + " failed", error);
                }
            });
            return;
        }
        ops.deleteMoney(bank, event.actor()).thenCompose(amount -> ops.store().deleteState(bank).thenApply(v -> amount))
                .whenCompleteAsync((amount, error) -> {
                    if (error != null) {
                        core.plugin().getLogger().log(Level.SEVERE, "Bank: deleting the bank of island " + event.islandId() + " failed", error);
                        return;
                    }
                    if (amount > 0 && event.owner() != null) {
                        profiles().onlinePlayer(event.owner()).ifPresent(p ->
                                msg.send(p, "bank.island.deleted", module.amountTag("amount", p, Currency.MONEY, amount)));
                    }
                }, module.mainThread());
    }

    // ================================================================== interest sweep

    /** Async: settles accounts with due periods in one small batch, one after another (never floods the pool). */
    private void sweep() {
        if (!config.enabled || ops == null || !ready.get() || !sweeping.compareAndSet(false, true)) {
            return;
        }
        ops.store().due(ops.currentPeriod() - 1, config.sweepBatch).thenCompose(accounts -> {
            CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
            for (AccountRef account : accounts) {
                chain = chain.thenCompose(v -> settleAccount(account).handle((s, e) -> {
                    if (e != null) {
                        core.plugin().getLogger().log(Level.WARNING, "Bank: interest for " + account + " failed (retried later)", e);
                    }
                    return null;
                }));
            }
            return chain;
        }).whenComplete((v, e) -> {
            sweeping.set(false);
            if (e != null) {
                core.plugin().getLogger().log(Level.WARNING, "Bank: interest run failed", e);
            }
        });
    }

    /** Settles one account with its current terms (island data is read on the main thread). */
    CompletableFuture<BankOps.Settled> settleAccount(AccountRef account) {
        if (account.isBank()) {
            return ops.store().state(account).thenCompose(state -> {
                int tier = state.map(InterestMath.State::tier).orElse(1);
                return ops.active(List.of(account.owner())).thenCompose(active -> ops.settle(account, ops.personalTerms(tier, active)));
            });
        }
        if (account.isIsland()) {
            return core.scheduler().supplyMain("bank:island-terms", () -> islandContext(account.owner()))
                    .thenCompose(ctx -> {
                        if (ctx == null) {
                            return ops.settle(account, new BankOps.Terms(Long.MAX_VALUE, 0, 0, false));
                        }
                        return ops.active(ctx.members()).thenCompose(active -> ops.settle(account,
                                ops.islandTerms(ctx.capacityLevel(), ctx.interestLevel(), active)));
                    });
        }
        return CompletableFuture.completedFuture(BankOps.Settled.NONE);
    }

    /** Main thread: settles the player's personal bank and their island's bank. */
    private void settleFor(Player player) {
        if (!ready.get()) {
            return;
        }
        UUID key = profiles().profileKey(player);
        UUID island = islands().flatMap(i -> i.islandOfProfile(key)).map(IslandService.IslandInfo::id).orElse(null);
        // PERF6: the main thread only reads the two keys. Starting the settle chains (database futures, their first-run
        // class loading and lambda linking) happens off the main thread; the island terms still come from the main thread
        // ("bank:island-terms"). Settling is idempotent per period, so nothing about what is paid changes.
        core.scheduler().async(() -> {
            settleAccount(AccountRef.bank(key));
            if (island != null) {
                settleAccount(AccountRef.island(island));
            }
        });
    }

    // ================================================================== island context (main thread)

    /** What the bank needs to know about an island (main thread snapshot). */
    record IslandContext(UUID islandId, String name, int capacityLevel, int interestLevel, Set<UUID> members) {
    }

    /** Snapshot of an island by id, or null if it is gone (main thread). */
    IslandContext islandContext(UUID islandId) {
        Optional<IslandService> service = islands();
        if (service.isEmpty()) {
            return null;
        }
        IslandService is = service.get();
        return is.island(islandId).map(info -> new IslandContext(info.id(), info.name(), is.bankCapacityLevel(info.id()),
                is.bankInterestLevel(info.id()), Set.copyOf(is.members(info.id()).keySet()))).orElse(null);
    }

    /** A player's view of their island's bank: rights from their role (main thread), or null without an island. */
    record IslandAccess(IslandContext island, Role role, boolean deposit, boolean withdraw, boolean history, int limitPercent) {
    }

    IslandAccess islandAccess(UUID profileKey) {
        Optional<IslandService> service = islands();
        if (service.isEmpty()) {
            return null;
        }
        IslandService is = service.get();
        Optional<IslandService.IslandInfo> info = is.islandOfProfile(profileKey);
        if (info.isEmpty()) {
            return null;
        }
        UUID id = info.get().id();
        Role role = is.members(id).get(profileKey);
        if (role == null) {
            return null; // coop players and visitors have no island bank (REVIEW A4)
        }
        IslandContext ctx = islandContext(id);
        if (ctx == null) {
            return null;
        }
        return new IslandAccess(ctx, role,
                BankRules.island(id, id, role, is.hasIslandPermission(id, profileKey, IslandService.PERM_BANK_DEPOSIT)) == BankRules.Access.OK,
                BankRules.island(id, id, role, is.hasIslandPermission(id, profileKey, IslandService.PERM_BANK_WITHDRAW)) == BankRules.Access.OK,
                BankRules.island(id, id, role, is.hasIslandPermission(id, profileKey, IslandService.PERM_BANK_HISTORY)) == BankRules.Access.OK,
                config.limitPercent(role));
    }

    // ================================================================== withdrawal fee (BANK2)

    /** The fee steps from bank.yml. */
    WithdrawFee fees() {
        return fees;
    }

    /**
     * The bank-fee mastery level that applies to a withdrawal from {@code bank} by the profile {@code profileKey} (main
     * thread): an island bank uses its island, a personal bank the island of the profile (0 without one).
     */
    int feeMasteryLevel(UUID profileKey, AccountRef bank) {
        Optional<IslandService> service = islands();
        if (service.isEmpty()) {
            return 0;
        }
        IslandService is = service.get();
        UUID islandId = bank.isIsland() ? bank.owner() : is.islandOfProfile(profileKey).map(IslandService.IslandInfo::id).orElse(null);
        return islandId == null ? 0 : Math.max(0, is.bankFeeMasteryLevel(islandId));
    }

    /** The fee percent for a withdrawal from {@code bank} by {@code profileKey} right now, masteries included (main thread). */
    double feePercent(UUID profileKey, AccountRef bank) {
        return fees.percent(feeMasteryLevel(profileKey, bank));
    }

    // ================================================================== views (what the menu shows)

    /** A bank as the menu shows it, read right after settling it. */
    record Account(AccountRef ref, long balance, BankOps.Terms terms, int tier, boolean forfeited, int activityMinutes) {
    }

    /** Settles and reads a profile's personal bank. */
    CompletableFuture<Account> personal(UUID profileKey) {
        AccountRef ref = AccountRef.bank(profileKey);
        return ops.activityMinutes(List.of(profileKey)).thenCompose(minutes -> {
            boolean active = config.activityMinutes <= 0 || minutes >= config.activityMinutes;
            return ops.store().state(ref).thenCompose(state -> {
                BankOps.Terms terms = ops.personalTerms(state.map(InterestMath.State::tier).orElse(1), active);
                return ops.settle(ref, terms).thenCompose(s -> read(ref, terms, minutes));
            });
        });
    }

    /** Settles and reads an island bank. */
    CompletableFuture<Account> island(IslandContext ctx) {
        AccountRef ref = AccountRef.island(ctx.islandId());
        return ops.activityMinutes(ctx.members()).thenCompose(minutes -> {
            boolean active = config.activityMinutes <= 0 || minutes >= config.activityMinutes;
            BankOps.Terms terms = ops.islandTerms(ctx.capacityLevel(), ctx.interestLevel(), active);
            return ops.settle(ref, terms).thenCompose(s -> read(ref, terms, minutes));
        });
    }

    private CompletableFuture<Account> read(AccountRef ref, BankOps.Terms terms, int minutes) {
        return ops.store().db().async(c -> {
            Optional<InterestMath.State> state = BankStore.loadState(c, ref);
            long balance = BankStore.balance(c, ref);
            long period = ops.currentPeriod();
            boolean forfeited = state.map(s -> s.cycle() == period && s.forfeited()).orElse(false);
            return new Account(ref, balance, terms, state.map(InterestMath.State::tier).orElse(1), forfeited, minutes);
        });
    }

    // ================================================================== BankService

    @Override
    public void open(Player player, Menu parent) {
        if (!config.enabled) {
            msg.send(player, "bank.disabled");
            core.sounds().play(player, "error");
            return;
        }
        if (!ready.get()) {
            msg.send(player, "bank.starting");
            core.sounds().play(player, "error");
            return;
        }
        BankMenu menu = new BankMenu(this);
        if (parent != null) {
            menu.parent(parent);
        }
        core.menus().open(player, menu);
    }

    @Override
    public CompletableFuture<Integer> personalTier(UUID profileKey) {
        return ops.store().state(AccountRef.bank(profileKey)).thenApply(s -> s.map(InterestMath.State::tier).orElse(1));
    }

    @Override
    public CompletableFuture<Long> personalBalance(UUID profileKey) {
        return economy.balance(AccountRef.bank(profileKey), Currency.MONEY);
    }

    @Override
    public CompletableFuture<Long> islandBalance(UUID islandId) {
        return economy.balance(AccountRef.island(islandId), Currency.MONEY);
    }

    @Override
    public long islandCapacity(int capacityLevel) {
        BankTiers t = tiers;
        return t == null ? -1 : t.islandCapacity(capacityLevel);
    }

    @Override
    public long islandPayoutCap(int interestLevel) {
        BankTiers t = tiers;
        return t == null ? -1 : t.islandCap(interestLevel);
    }

    @Override
    public int interestPeriodHours() {
        return config.periodHours;
    }

    // ================================================================== actions (main thread)

    /** Checks done before every action: still the same profile, not switching, not frozen, not busy. */
    boolean canAct(Player player, UUID profileKey) {
        if (!ready.get()) {
            fail(player, "bank.starting");
            return false;
        }
        if (!profiles().profileKey(player).equals(profileKey) || profiles().isSwitching(player.getUniqueId())) {
            fail(player, "bank.profile-changed");
            return false;
        }
        if (module.frozen(player)) {
            fail(player, "bank.frozen");
            return false;
        }
        if (busy.contains(player.getUniqueId())) {
            fail(player, "bank.busy");
            return false;
        }
        return true;
    }

    void deposit(Player player, UUID profileKey, AccountRef bank, long amount, BankOps.Terms terms, Runnable after) {
        if (net.skybornmc.skyblock.common.moderation.SanctionService.denied(core.services(), player, net.skybornmc.skyblock.common.moderation.SanctionService.Use.BANK)) {
            return; // P7: frozen balance
        }
        if (!canAct(player, profileKey) || !stillAllowed(player, profileKey, bank, IslandService.PERM_BANK_DEPOSIT)) {
            return;
        }
        busy.add(player.getUniqueId());
        ops.deposit(profileKey, bank, amount, terms, Actor.player(player)).whenCompleteAsync((out, error) -> {
            busy.remove(player.getUniqueId());
            BankOps.Outcome o = error != null ? BankOps.Outcome.of(BankOps.Status.ERROR) : out;
            if (o.ok()) {
                msg.send(player, bank.isBank() ? "bank.deposited" : "bank.island.deposited", amountTag(player, "amount", o.amount()),
                        amountTag(player, "bank", o.bank()), amountTag(player, "balance", o.purse()));
                core.sounds().play(player, "money");
                if (bank.isIsland()) {
                    notifyIsland(player, bank, "bank.island.notify-deposit", o.amount());
                }
                if (player.isOnline()) { // TUTORIAL §9.2: the tutorial's bank step listens for this
                    org.bukkit.Bukkit.getPluginManager().callEvent(new net.skybornmc.skyblock.common.economy.BankDepositEvent(player,
                            o.amount(), bank.isIsland()));
                }
            } else {
                problem(player, o.status(), bank, amount);
            }
            if (player.isOnline() && after != null) {
                after.run();
            }
        }, module.mainThread());
    }

    void withdraw(Player player, UUID profileKey, AccountRef bank, long amount, BankOps.Terms terms, BankOps.Limit limit, Runnable after) {
        if (net.skybornmc.skyblock.common.moderation.SanctionService.denied(core.services(), player, net.skybornmc.skyblock.common.moderation.SanctionService.Use.BANK)) {
            return; // P7: frozen balance
        }
        if (!canAct(player, profileKey) || !stillAllowed(player, profileKey, bank, IslandService.PERM_BANK_WITHDRAW)) {
            return;
        }
        double feePercent = feePercent(profileKey, bank);
        busy.add(player.getUniqueId());
        ops.withdraw(profileKey, bank, amount, terms, limit, feePercent, Actor.player(player)).whenCompleteAsync((out, error) -> {
            busy.remove(player.getUniqueId());
            BankOps.Outcome o = error != null ? BankOps.Outcome.of(BankOps.Status.ERROR) : out;
            if (o.ok()) {
                String key = (bank.isBank() ? "bank." : "bank.island.") + (o.fee() > 0 ? "withdrawn-fee" : "withdrawn");
                msg.send(player, key, amountTag(player, "amount", o.amount() - o.fee()), amountTag(player, "fee", o.fee()),
                        amountTag(player, "bank", o.bank()), amountTag(player, "balance", o.purse()),
                        Msg.p("percent", BankMenu.percent(feePercent)));
                core.sounds().play(player, "money");
                if (bank.isIsland()) {
                    notifyIsland(player, bank, "bank.island.notify-withdraw", o.amount());
                }
            } else {
                problem(player, o.status(), bank, amount);
            }
            if (player.isOnline() && after != null) {
                after.run();
            }
        }, module.mainThread());
    }

    void buyTier(Player player, UUID profileKey, int currentTier, Runnable after) {
        if (!canAct(player, profileKey)) {
            return;
        }
        busy.add(player.getUniqueId());
        ops.buyTier(profileKey, currentTier, Actor.player(player)).whenCompleteAsync((out, error) -> {
            busy.remove(player.getUniqueId());
            BankOps.Outcome o = error != null ? BankOps.Outcome.of(BankOps.Status.ERROR) : out;
            if (o.ok()) {
                BankTiers.PersonalTier t = tiers.tier(currentTier + 1);
                msg.send(player, "bank.tier.bought", Msg.number("tier", t.tier()), amountTag(player, "capacity", t.capacity()),
                        amountTag(player, "amount", o.amount()), amountTag(player, "balance", o.purse()));
                core.sounds().play(player, "success");
            } else if (o.status() == BankOps.Status.NO_MONEY) {
                fail(player, "bank.tier.no-money", amountTag(player, "amount", tiers.tier(currentTier + 1).cost()));
            } else {
                problem(player, o.status(), AccountRef.bank(profileKey), 0);
            }
            if (player.isOnline() && after != null) {
                after.run();
            }
        }, module.mainThread());
    }

    /** Island banks: membership and the role permission again right before the money moves (main thread). */
    private boolean stillAllowed(Player player, UUID profileKey, AccountRef bank, String permission) {
        if (!bank.isIsland()) {
            return true;
        }
        IslandAccess access = islandAccess(profileKey);
        BankRules.Access result = access == null ? BankRules.Access.NOT_MEMBER
                : BankRules.island(access.island().islandId(), bank.owner(), access.role(),
                permission.equals(IslandService.PERM_BANK_DEPOSIT) ? access.deposit() : access.withdraw());
        if (result == BankRules.Access.OK) {
            return true;
        }
        fail(player, result == BankRules.Access.NO_PERMISSION ? "bank.island.no-permission" : "bank.island.not-member");
        return false;
    }

    private void notifyIsland(Player actor, AccountRef bank, String key, long amount) {
        islands().ifPresent(is -> {
            for (UUID member : is.members(bank.owner()).keySet()) {
                profiles().onlinePlayer(member).filter(p -> !p.equals(actor)).ifPresent(p ->
                        msg.send(p, key, Msg.p("player", actor.getName()), amountTag(p, "amount", amount)));
            }
        });
    }

    private void problem(Player player, BankOps.Status status, AccountRef bank, long amount) {
        switch (status) {
            case NO_MONEY -> fail(player, "bank.no-money", amountTag(player, "amount", amount));
            case BANK_EMPTY -> fail(player, "bank.bank-empty");
            case FULL -> fail(player, "bank.full");
            case LIMIT -> fail(player, "bank.island.limit");
            case BELOW_MIN -> fail(player, "bank.below-min", amountTag(player, "deposit", config.depositMin),
                    amountTag(player, "withdraw", config.withdrawMin));
            case OWN_PROFILE -> fail(player, "bank.own-profile");
            case NOT_YOURS -> fail(player, "bank.not-yours");
            case MAX_TIER -> fail(player, "bank.tier.max");
            case BUSY -> fail(player, "bank.try-again");
            default -> fail(player, "error");
        }
    }

    void fail(Player player, String key, TagResolver... placeholders) {
        msg.send(player, key, placeholders);
        core.sounds().play(player, "error");
    }

    TagResolver amountTag(Player viewer, String name, Long value) {
        return module.amountTag(name, viewer, Currency.MONEY, value == null ? 0 : value);
    }

    static String time(long millis) {
        return Format.duration(Duration.ofMillis(Math.max(0, millis)));
    }
}
