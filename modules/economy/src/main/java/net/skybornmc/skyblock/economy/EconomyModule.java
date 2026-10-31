package net.skybornmc.skyblock.economy;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.skybornmc.skyblock.common.Core;
import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.command.Arg;
import net.skybornmc.skyblock.common.command.Args;
import net.skybornmc.skyblock.common.command.CommandFailure;
import net.skybornmc.skyblock.common.command.CommandSpec;
import net.skybornmc.skyblock.common.command.Suggest;
import net.skybornmc.skyblock.common.db.MainThread;
import net.skybornmc.skyblock.common.db.Migrations;
import net.skybornmc.skyblock.common.economy.AccountRef;
import net.skybornmc.skyblock.common.economy.Currency;
import net.skybornmc.skyblock.common.economy.EconomyService;
import net.skybornmc.skyblock.common.economy.Transaction;
import net.skybornmc.skyblock.common.economy.TxReason;
import net.skybornmc.skyblock.common.economy.TxResult;
import net.skybornmc.skyblock.common.module.SkyblockModule;
import net.skybornmc.skyblock.common.player.PlayerData;
import net.skybornmc.skyblock.common.audit.AuditCategory;
import net.skybornmc.skyblock.common.profile.ProfileDeletedEvent;
import net.skybornmc.skyblock.common.profile.ProfileService;
import net.skybornmc.skyblock.common.profile.ProfileSwitchedEvent;
import net.skybornmc.skyblock.common.text.ModuleMessages;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.util.Format;
import net.skybornmc.skyblock.common.util.Scheduler;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Economy core (M3.1): currencies and accounts, the atomic transaction ledger, {@link EconomyService} for other
 * modules, placeholders for the HUD, and {@code /bal}, {@code /baltop}, {@code /pay}, {@code /transactions}, {@code /eco}.
 * Money notes (M3.2): {@code /withdraw}, {@code /eco note} and the redeemable note item ({@link MoneyNotes}).
 * Selling (M3.4): {@code /sell}, dynamic prices and {@code SellService} (package {@code sell}).
 * Banks (M3.3): {@code /bank}, personal and island banks with interest and {@code BankService} (package {@code bank}).
 * Economy dashboard + anomaly alerts (M3.8): {@code /eco admin [alert]} (package {@code admin}).
 * Settings: {@code economy.yml}; texts: {@code messages/economy.yml}; data: {@code db/economy}.
 */
public final class EconomyModule implements SkyblockModule, Listener {

    public static final String ID = "economy";

    static final String PERM_BALANCE = "skyblock.economy.balance";
    static final String PERM_BALANCE_OTHERS = "skyblock.economy.balance.others";
    static final String PERM_BALTOP = "skyblock.economy.baltop";
    /** How often the background task checks whether a /baltop list is older than refresh-seconds (5 s). */
    private static final long BALTOP_CHECK_TICKS = 100L;
    static final String PERM_PAY = "skyblock.economy.pay";
    static final String PERM_TRANSACTIONS = "skyblock.economy.transactions";
    /** {@code /eco give|take|set|reset|note}: admins and the owner (owner decision #47). */
    static final String PERM_ECO = "skyblock.admin.economy";
    static final String PERM_ALERTS = "skyblock.staff.economy.alerts";
    /** Owner R3-6: allow /eco changes above the limit (owner and admin; never your own request). */
    static final String PERM_APPROVE = "skyblock.admin.economy.approve";
    static final String PERM_APPROVE_OWN = "skyblock.admin.economy.approve-own";

    /** Tab hints for the reason of {@code /eco note}. */
    private static final List<String> NOTE_REASONS = List.of("Event prize", "Compensation for a bug", "Giveaway", "Refund",
            "Testing");

    private static final List<String> STAFF_REASONS = List.of("Compensation for a bug", "Event prize", "Refund", "Dupe removal",
            "Testing");

    final Core core;
    final ModuleMessages msg;
    final EconomyConfig config = new EconomyConfig();
    private EconomyServiceImpl service;
    private MoneyNotes notes;
    private net.skybornmc.skyblock.economy.sell.SellFeature sell;
    private net.skybornmc.skyblock.economy.bank.BankFeature bank;
    private net.skybornmc.skyblock.economy.admin.EconomyWatch watch;
    private Baltop baltop;
    private SendGateImpl sendGate;
    private final EcoApprovals approvals = new EcoApprovals();
    private EcoTargets ecoTargets;
    private Scheduler.Task approvalTask;
    private Executor main;
    private Scheduler.Task baltopTask;

    public EconomyModule(Core core) {
        this.core = core;
        this.msg = core.messages(ID);
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void enable() throws Exception {
        new Migrations(core.database(), core.plugin().getLogger()).migrate(ID, getClass().getClassLoader());
        core.configs().load(config);
        main = new MainThread(core.plugin());
        // Season id: 0 until the season service exists (Phase 10); the ledger and start-balance keys already carry it.
        EconomyStore store = new EconomyStore(core.database(), () -> 0, System::currentTimeMillis);
        store.owners(key -> profiles().ownerOf(key));
        service = new EconomyServiceImpl(store, new BalanceCache(), core.audit(), () -> config,
                uuid -> core.players().get(uuid).map(PlayerData::name).orElse(null),
                currency -> core.style().symbol(currency.key()), () -> 0, core.plugin().getLogger(), this::profiles);
        // P7: one line per player, from their best profile.
        baltop = new Baltop((currency, size) -> store.topBest(currency, size, profiles()), System::currentTimeMillis);
        // P7 (spec 13.1): every transaction is checked against active sanctions (moderation module), a safety net behind
        // the entry checks of /pay, /withdraw, the bank, the shop, the auction house and trades
        service.sanctions(() -> core.services().optional(net.skybornmc.skyblock.common.moderation.SanctionService.class).orElse(null));
        core.services().register(ID, EconomyService.class, service);
        // Owner F1: money can be sent away only after the profile earned send-gate.required itself (island members excepted).
        sendGate = new SendGateImpl(core.database(), () -> config, this::sameIsland, System::currentTimeMillis);
        core.services().register(ID, net.skybornmc.skyblock.common.economy.SendGate.class, sendGate);
        ecoTargets = new EcoTargets(core.database());
        notes = new MoneyNotes(this, new NoteService(core.database(), service, System::currentTimeMillis, core.plugin().getLogger(),
                (createdBy, redeemer, actor) -> profiles().blockOwnTransfer(AuditCategory.ECONOMY, "note_redeem", createdBy, redeemer, actor)));
        notes.enable();
        // M3.4 selling: price table, dynamic prices, SellService, /sell.
        sell = new net.skybornmc.skyblock.economy.sell.SellFeature(this, core, msg, config, service);
        sell.enable();
        // M3.3 banks: /bank, personal (per profile) and island banks, interest, BankService.
        bank = new net.skybornmc.skyblock.economy.bank.BankFeature(this, core, msg, service);
        bank.enable();
        // M3.8 economy watch: summary tables, anomaly alerts, /eco admin.
        watch = new net.skybornmc.skyblock.economy.admin.EconomyWatch(this, core, msg);
        watch.enable();
        registerPlaceholders();
        registerCommands();
        core.plugin().getServer().getPluginManager().registerEvents(this, core.plugin());
        for (Player p : Bukkit.getOnlinePlayers()) {
            load(p);
        }
        baltop.refreshIfOlder(0, config.baltopSize);
        // R3-6: waiting /eco changes are dropped after eco.pending-minutes (memory only, every 10 s).
        approvalTask = core.scheduler().repeat("economy:eco-pending", 200L, 200L, this::expireApprovals);
        // Checks every 5 s (a timestamp compare); a list is only read again once it is older than refresh-seconds.
        // SMALL2: off the main thread ('economy:baltop' was logged at 5.3 ms): it only compares times and hands queries to
        // the database executor (one per currency), Baltop is thread-safe and nothing here touches the world.
        baltopTask = core.scheduler().asyncRepeat(BALTOP_CHECK_TICKS, BALTOP_CHECK_TICKS,
                () -> {
                    try {
                        baltop.refreshIfOlder(baltopMaxAge(), config.baltopSize);
                    } catch (RuntimeException e) {
                        core.plugin().getLogger().log(java.util.logging.Level.WARNING, "Baltop refresh could not start; trying again in 5 s", e);
                    }
                });
    }

    @Override
    public void reload() {
        core.configs().load(config);
        core.messages().load(ID);
        if (sell != null) {
            sell.reload();
        }
        if (bank != null) {
            bank.reload();
        }
        if (watch != null) {
            watch.reload();
        }
    }

    @Override
    public void disable() {
        if (baltopTask != null) {
            baltopTask.cancel();
        }
        if (approvalTask != null) {
            approvalTask.cancel();
        }
        if (notes != null) {
            notes.disable();
        }
        if (sell != null) {
            sell.disable();
        }
        if (bank != null) {
            bank.disable();
        }
        if (watch != null) {
            watch.disable();
        }
        HandlerList.unregisterAll(this);
    }

    EconomyServiceImpl service() {
        return service;
    }

    /** Island profiles (PROF): money, crystals and shards are per profile; gems per player. */
    public ProfileService profiles() {
        return ProfileService.of(core.services());
    }

    /** The account key of a player for money/crystals/shards: the active (offline: last active) profile. */
    CompletableFuture<UUID> accountKey(UUID player) {
        return profiles().profileKeyAsync(player);
    }

    public Executor mainThread() {
        return main;
    }

    /** Owner F1: the send gate (also published as {@link net.skybornmc.skyblock.common.economy.SendGate}). */
    net.skybornmc.skyblock.common.economy.SendGate sendGate() {
        return sendGate;
    }

    EcoApprovals approvals() {
        return approvals;
    }

    /** True if both profiles are stored members (any role, coop excluded) of the same island. Main thread. */
    private boolean sameIsland(UUID fromProfile, UUID toProfile) {
        return core.services().optional(net.skybornmc.skyblock.common.island.IslandService.class).map(is -> {
            Optional<net.skybornmc.skyblock.common.island.IslandService.IslandInfo> mine = is.islandOfProfile(fromProfile);
            if (mine.isEmpty()) {
                return false;
            }
            return is.islandOfProfile(toProfile).map(other -> other.id().equals(mine.get().id())).orElse(false)
                    && is.role(mine.get().id(), toProfile).map(r -> r != net.skybornmc.skyblock.common.island.IslandService.Role.COOP)
                    .orElse(false);
        }).orElse(false);
    }

    /** Tells a player the F1 rule blocked a send: how much more they must earn. {@code key}: pay.send-gate / note.send-gate. */
    void tellSendGate(Player player, net.skybornmc.skyblock.common.economy.SendGate.Check check, String key) {
        msg.send(player, key, exactAmountTag("required", player, Currency.MONEY, check.required()),
                exactAmountTag("earned", player, Currency.MONEY, check.earned()),
                exactAmountTag("missing", player, Currency.MONEY, check.missing()));
        sound(player, "error");
    }

    /**
     * Opens a profile's full ledger (the players' own {@code /transactions} view) for staff, as a child of {@code from}
     * (M3.8 dashboard: top earners, alert evidence). Gem rows come from the profile's player.
     */
    public void openHistory(net.skybornmc.skyblock.common.menu.Menu from, UUID profileKey) {
        from.openChild(new TransactionsMenu(this, AccountRef.player(profileKey), AccountRef.player(profiles().ownerOf(profileKey))));
    }

    // ================================================================== players

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        load(event.getPlayer());
        if (watch != null) {
            watch.rememberStaff(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        service.forgetPlayer(event.getPlayer().getUniqueId());
    }

    /** A switch to another profile: show that profile's money, crystals and shards. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onProfileSwitched(ProfileSwitchedEvent event) {
        Player player = event.getPlayer();
        service.switchedProfile(player.getUniqueId(), event.to().id()).exceptionally(e -> {
            core.plugin().getLogger().warning("Economy: could not load balances of " + player.getName() + " after a profile switch: "
                    + e.getMessage());
            return null;
        });
    }

    /** A deleted profile's money, crystals, shards and personal bank are gone with it (owner P9); gems stay on the player. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onProfileDeleted(ProfileDeletedEvent event) {
        if (sendGate != null) {
            sendGate.forget(event.profile().id());
        }
        AccountRef bankAccount = AccountRef.bank(event.profile().id());
        service.balance(bankAccount, Currency.MONEY).thenAccept(balance -> {
            if (balance > 0) {
                service.set(bankAccount, Currency.MONEY, 0, "profile_delete", Actor.system("profiles"),
                        Map.of("profile", event.profile().id().toString(), "slot", event.profile().slot(), "bank", true));
            }
        }).exceptionally(e -> {
            core.plugin().getLogger().warning("Economy: could not clear the bank of deleted profile " + event.profile().id() + ": "
                    + e.getMessage());
            return null;
        });
        AccountRef account = AccountRef.player(event.profile().id());
        for (Currency currency : Currency.values()) {
            if (currency == Currency.GEMS) {
                continue;
            }
            service.balance(account, currency).thenAccept(balance -> {
                if (balance > 0) {
                    service.set(account, currency, 0, "profile_delete", Actor.system("profiles"),
                            Map.of("profile", event.profile().id().toString(), "slot", event.profile().slot()));
                }
            }).exceptionally(e -> {
                core.plugin().getLogger().warning("Economy: could not clear balances of deleted profile " + event.profile().id()
                        + ": " + e.getMessage());
                return null;
            });
        }
    }

    private void load(Player player) {
        UUID id = player.getUniqueId();
        service.loadPlayer(id, player.getName()).exceptionally(e -> {
            core.plugin().getLogger().warning("Economy: could not load balances of " + player.getName() + ": " + e.getMessage());
            return null;
        }).thenRun(() -> {
            if (Bukkit.getPlayer(id) == null) {
                service.cache().forget(id); // left while loading
            }
        });
    }

    private void registerPlaceholders() {
        core.placeholders().registerText(ID, "balance", p -> raw(p, Currency.MONEY));
        core.placeholders().registerText(ID, "balance_formatted", p -> formatted(p, Currency.MONEY));
        for (Currency currency : List.of(Currency.CRYSTALS, Currency.GEMS, Currency.SHARDS)) {
            core.placeholders().registerText(ID, currency.key(), p -> raw(p, currency));
            core.placeholders().registerText(ID, currency.key() + "_formatted", p -> formatted(p, currency));
            // GUI-4 (owner U4): empty while the balance is 0, so a scoreboard "if:crystals_owned|" line only shows once you have some.
            core.placeholders().registerText(ID, currency.key() + "_owned", p -> owned(p, currency));
        }
        // Deprecated aliases: tokens were replaced by crystals (O8); old formats keep showing the crystals balance.
        core.placeholders().registerText(ID, Currency.LEGACY_TOKENS_KEY, p -> raw(p, Currency.CRYSTALS));
        core.placeholders().registerText(ID, Currency.LEGACY_TOKENS_KEY + "_formatted", p -> formatted(p, Currency.CRYSTALS));
    }

    private String raw(Player p, Currency currency) {
        if (p == null) {
            return "";
        }
        var value = service.cached(p.getUniqueId(), currency);
        return value.isPresent() ? String.valueOf(value.getAsLong()) : "";
    }

    private String owned(Player p, Currency currency) {
        if (p == null) {
            return "";
        }
        var value = service.cached(p.getUniqueId(), currency);
        return ownedText(value.isPresent() ? value.getAsLong() : null);
    }

    /** The {@code <currency>_owned} placeholder: the plain amount, or empty for no balance (0 or not loaded). Pure. */
    static String ownedText(Long balance) {
        return balance == null || balance <= 0 ? "" : Amounts.plain(balance);
    }

    private String formatted(Player p, Currency currency) {
        if (p == null) {
            return "";
        }
        var value = service.cached(p.getUniqueId(), currency);
        return value.isPresent() ? Amounts.plain(value.getAsLong()) : core.messages().raw(ID, "placeholder.loading");
    }

    // ================================================================== text helpers

    /** An amount in its currency's color and symbol ({@code format.<currency>}). */
    public Component amount(CommandSender viewer, Currency currency, long value) {
        return msg.component(viewer, "format." + currency.key(), Msg.p("value", Amounts.plain(value)),
                Msg.p("exact", Format.number(value)));
    }

    /** Every digit ({@code $1,250,000}): confirmations and transaction history (UI_STYLE §1.3). */
    TagResolver exactAmountTag(String name, CommandSender viewer, Currency currency, long value) {
        return Msg.c(name, msg.component(viewer, "format." + currency.key(), Msg.p("value", Format.number(value)),
                Msg.p("exact", Format.number(value))));
    }

    public TagResolver amountTag(String name, CommandSender viewer, Currency currency, long value) {
        return Msg.c(name, amount(viewer, currency, value));
    }

    Component currencyName(CommandSender viewer, Currency currency) {
        return msg.component(viewer, "names." + currency.key());
    }

    private CommandFailure fail(String key, TagResolver... placeholders) {
        return new CommandFailure(ID, key, placeholders);
    }

    private void sound(CommandSender sender, String key) {
        if (sender instanceof Player p) {
            core.sounds().play(p, key);
        }
    }

    private record Found(UUID uuid, String name) {
    }

    /** Online player or offline snapshot by name. */
    private CompletableFuture<Optional<Found>> resolve(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return CompletableFuture.completedFuture(Optional.of(new Found(online.getUniqueId(), online.getName())));
        }
        return core.players().offline(name).thenApply(s -> s.map(snap -> new Found(snap.uuid(), snap.name())));
    }

    /** The cached money of an online player's active profile (display only), 0 while loading. */
    public long cachedMoney(Player player) {
        return service.cached(player.getUniqueId(), Currency.MONEY).orElse(0);
    }

    public boolean frozen(Player player) {
        return core.placeholders().has("frozen")
                && core.placeholders().text(player, "frozen").map("true"::equalsIgnoreCase).orElse(false);
    }

    /** Hops to the main thread and logs failures, so a database problem always gives the player an answer. */
    <T> void then(CommandSender sender, CompletableFuture<T> future, java.util.function.Consumer<T> action) {
        future.whenCompleteAsync((value, error) -> {
            if (error != null) {
                core.plugin().getLogger().warning("Economy command by " + sender.getName() + " failed: " + error);
                msg.send(sender, "error");
                sound(sender, "error");
                return;
            }
            action.accept(value);
        }, main);
    }

    // ================================================================== commands

    private void registerCommands() {
        Arg<String> balTarget = Args.offlinePlayer("player");
        core.commands().register(CommandSpec.command("bal")
                .description("See your balances (or another player's)")
                .category("Economy")
                .permission(PERM_BALANCE)
                .aliases("balance", "money")
                .optional(balTarget)
                .example("bal Steve")
                .executes(ctx -> balance(ctx.sender(), ctx.getOr(balTarget, null))));

        Arg<Currency> topCurrency = Args.choice("currency", Currency.class);
        core.commands().register(CommandSpec.command("baltop")
                .description("The richest players")
                .category("Economy")
                .permission(PERM_BALTOP)
                .aliases("balancetop", "moneytop")
                .optional(topCurrency)
                .example("baltop shards")
                .executes(ctx -> baltop(ctx.sender(), ctx.getOr(topCurrency, Currency.MONEY))));

        Arg<String> payTarget = Args.offlinePlayer("player");
        Arg<Long> payAmount = Args.amount("amount");
        Arg<Currency> payCurrency = Arg.word("currency", (sender, raw) -> Currency.byKey(raw).orElseThrow(() ->
                        CommandFailure.of("invalid-choice", Msg.p("input", raw), Msg.p("choices", String.join(", ", tradeableKeys())))),
                Suggest.of(this::tradeableKeys));
        core.commands().register(CommandSpec.command("pay")
                .description("Send money (or shards) to a player, online or offline")
                .category("Economy")
                .permission(PERM_PAY)
                .playerOnly()
                .arg(payTarget).arg(payAmount).optional(payCurrency)
                .cooldown(Duration.ofSeconds(3))
                .confirmIf(config.payConfirmSeconds, ctx -> {
                    Currency currency = ctx.getOr(payCurrency, Currency.MONEY);
                    long amount = ctx.get(payAmount);
                    Player p = (Player) ctx.sender();
                    return PayRules.check(config, p.getName(), ctx.get(payTarget), amount, currency, frozen(p))
                            .isEmpty()
                            && PayRules.needsConfirm(config, amount, currency);
                })
                .confirmMessage(ID, "pay.confirm", ctx -> new TagResolver[]{
                        exactAmountTag("amount", ctx.sender(), ctx.getOr(payCurrency, Currency.MONEY), ctx.get(payAmount)),
                        Msg.p("player", ctx.get(payTarget))})
                .example("pay Steve 1.5k")
                .executes(ctx -> pay(ctx.player(), ctx.get(payTarget), ctx.get(payAmount), ctx.getOr(payCurrency, Currency.MONEY))));

        core.commands().register(CommandSpec.command("transactions")
                .description("Your money history: payments, sales, purchases")
                .category("Economy")
                .permission(PERM_TRANSACTIONS)
                .playerOnly()
                .aliases("txs")
                .executes(ctx -> core.menus().open(ctx.player(), new TransactionsMenu(this,
                        AccountRef.player(profiles().profileKey(ctx.player())), AccountRef.player(ctx.player().getUniqueId())))));

        core.commands().register(notes.withdrawCommand());

        registerEco();
    }

    private List<String> tradeableKeys() {
        return Arrays.stream(Currency.values()).filter(config::tradeable).map(Currency::key).toList();
    }

    private void registerEco() {
        Arg<String> player = Args.offlinePlayer("player");
        Arg<Currency> currency = Args.choice("currency", Currency.class);
        Arg<Long> amount = Args.amount("amount");
        Arg<Long> setAmount = Arg.word("amount", (sender, raw) -> {
            if (raw.trim().equals("0")) {
                return 0L;
            }
            try {
                return Format.parseAmount(raw);
            } catch (IllegalArgumentException e) {
                throw CommandFailure.of("invalid-amount", Msg.p("input", raw));
            }
        }, Suggest.hint("amount"));
        Arg<String> resetCurrency = Args.oneOf("currency", () -> {
            List<String> values = new ArrayList<>(Arrays.stream(Currency.values()).map(Currency::key).toList());
            values.add("all");
            return values;
        });
        String[] reasons = STAFF_REASONS.toArray(String[]::new);
        Arg<String> reasonGive = Args.text("reason", reasons);
        Arg<String> reasonTake = Args.text("reason", reasons);
        Arg<String> reasonSet = Args.text("reason", reasons);
        Arg<String> reasonReset = Args.text("reason", reasons);
        String[] noteReasons = NOTE_REASONS.toArray(String[]::new);
        Arg<Integer> pendingId = Arg.word("id", (sender, raw) -> {
            try {
                return Integer.parseInt(raw.trim().startsWith("#") ? raw.trim().substring(1) : raw.trim());
            } catch (NumberFormatException e) {
                throw new CommandFailure(ID, "eco.approve.unknown", Msg.p("id", raw));
            }
        }, Suggest.of(() -> approvals.ids(System.currentTimeMillis())));

        // /eco needs the dashboard node (admins get it as a child of skyblock.admin.economy); every balance-changing
        // sub-command needs skyblock.admin.economy itself (owner decision #47), /eco admin only the dashboard node (M3.8).
        core.commands().register(CommandSpec.command("eco")
                .description("Staff: economy dashboard, change a player's balance or give a money note (always logged)")
                .category("Staff")
                .permission(net.skybornmc.skyblock.economy.admin.EconomyWatch.PERM_DASHBOARD)
                .aliases("economy")
                .sub(watch.adminCommand())
                .sub(CommandSpec.sub("give")
                        .description("Give a player currency")
                        .permission(PERM_ECO)
                        .arg(player).arg(currency).arg(amount).arg(reasonGive)
                        .example("eco give Steve money 10k Event prize")
                        .executes(ctx -> eco(ctx.sender(), EcoAction.GIVE, ctx.get(player), List.of(ctx.get(currency)),
                                ctx.get(amount), ctx.get(reasonGive))))
                .sub(CommandSpec.sub("take")
                        .description("Take currency from a player")
                        .permission(PERM_ECO)
                        .arg(player).arg(currency).arg(amount).arg(reasonTake)
                        .example("eco take Steve money 10k Dupe removal")
                        .executes(ctx -> eco(ctx.sender(), EcoAction.TAKE, ctx.get(player), List.of(ctx.get(currency)),
                                ctx.get(amount), ctx.get(reasonTake))))
                .sub(CommandSpec.sub("set")
                        .description("Set a player's balance")
                        .permission(PERM_ECO)
                        .arg(player).arg(currency).arg(setAmount).arg(reasonSet)
                        .example("eco set Steve money 0 Dupe removal")
                        .executes(ctx -> eco(ctx.sender(), EcoAction.SET, ctx.get(player), List.of(ctx.get(currency)),
                                ctx.get(setAmount), ctx.get(reasonSet))))
                .sub(CommandSpec.sub("reset")
                        .description("Reset a balance to the starting amount")
                        .permission(PERM_ECO)
                        .arg(player).arg(resetCurrency).arg(reasonReset)
                        .example("eco reset Steve all Testing")
                        .executes(ctx -> {
                            String which = ctx.get(resetCurrency);
                            List<Currency> list = which.equalsIgnoreCase("all") ? List.of(Currency.values())
                                    : List.of(Currency.byKey(which).orElseThrow());
                            eco(ctx.sender(), EcoAction.RESET, ctx.get(player), list, 0, ctx.get(reasonReset));
                        }))
                .sub(notes.ecoNoteCommand(noteReasons).permission(PERM_ECO))
                .sub(CommandSpec.sub("allow")
                        .description("Allow a waiting /eco change above the limit (owner/admin, not your own)")
                        .permission(PERM_APPROVE)
                        .arg(pendingId)
                        .example("eco allow 3")
                        .executes(ctx -> decide(ctx.sender(), ctx.get(pendingId), true)))
                .sub(CommandSpec.sub("deny")
                        .description("Deny a waiting /eco change above the limit")
                        .permission(PERM_APPROVE)
                        .arg(pendingId)
                        .example("eco deny 3")
                        .executes(ctx -> decide(ctx.sender(), ctx.get(pendingId), false))));
    }

    // ================================================================== /bal

    private void balance(CommandSender sender, String name) {
        if (name == null || (sender instanceof Player p && name.equalsIgnoreCase(p.getName()))) {
            if (!(sender instanceof Player p)) {
                throw CommandFailure.of("players-only");
            }
            then(sender, service.balances(AccountRef.player(profiles().profileKey(p))), balances -> {
                service.refreshCached(p.getUniqueId(), balances);
                sendBalances(sender, null, balances);
            });
            return;
        }
        if (!sender.hasPermission(PERM_BALANCE_OTHERS)) {
            throw fail("bal.no-permission-others");
        }
        then(sender, resolve(name), found -> {
            if (found.isEmpty()) {
                msg.send(sender, "unknown-player", Msg.p("player", name));
                sound(sender, "error");
                return;
            }
            then(sender, accountKey(found.get().uuid()).thenCompose(key -> service.balances(AccountRef.player(key))),
                    balances -> sendBalances(sender, found.get().name(), balances));
        });
    }

    private void sendBalances(CommandSender sender, String other, Map<Currency, Long> balances) {
        msg.send(sender, other == null ? "bal.header-self" : "bal.header-other", Msg.p("player", other == null ? "" : other));
        for (Currency currency : Currency.values()) {
            long value = balances.getOrDefault(currency, 0L);
            msg.send(sender, "bal.line", Msg.c("currency", currencyName(sender, currency)), amountTag("amount", sender, currency, value),
                    Msg.p("exact", Format.number(value)));
        }
    }

    // ================================================================== /baltop

    private long baltopMaxAge() {
        return config.baltopRefreshSeconds * 1000L;
    }

    /**
     * Shows the cached list right away (never waits for the database). An older list starts a background refresh;
     * only the very first /baltop after a restart waits for it, and then gets the list sent when it is ready.
     */
    private void baltop(CommandSender sender, Currency currency) {
        Baltop.Snapshot top = baltop.get(currency);
        CompletableFuture<Baltop.Snapshot> refresh = baltop.refreshIfOlder(currency, baltopMaxAge(), config.baltopSize);
        if (top != null) {
            showBaltop(sender, currency, top);
            return;
        }
        msg.send(sender, "baltop.loading");
        if (refresh != null) {
            refresh.thenAcceptAsync(fresh -> {
                if (fresh != null && (!(sender instanceof Player p) || p.isOnline())) {
                    showBaltop(sender, currency, fresh);
                }
            }, main);
        }
    }

    private void showBaltop(CommandSender sender, Currency currency, Baltop.Snapshot cached) {
        Baltop.Snapshot top = cached;
        OptionalLong mine = OptionalLong.empty();
        if (sender instanceof Player p) {
            mine = service.cached(p.getUniqueId(), currency);
            // P7: the list shows each player's best profile; the live balance only replaces it when it is higher.
            long listed = cached.entries().stream().filter(e -> e.player().equals(p.getUniqueId()))
                    .mapToLong(EconomyStore.TopEntry::balance).findFirst().orElse(-1L);
            if (mine.isPresent() && (currency == Currency.GEMS || mine.getAsLong() > listed)) {
                top = cached.withLive(p.getUniqueId(), p.getName(), mine.getAsLong(), config.baltopSize);
            }
        }
        long ago = Math.max(0, System.currentTimeMillis() - top.time());
        msg.send(sender, "baltop.header", Msg.c("currency", currencyName(sender, currency)),
                Msg.p("ago", Format.duration(Duration.ofMillis(ago))),
                Msg.p("refresh", Format.duration(Duration.ofSeconds(config.baltopRefreshSeconds))));
        if (top.entries().isEmpty()) {
            msg.send(sender, "baltop.empty");
            return;
        }
        for (int i = 0; i < top.entries().size(); i++) {
            EconomyStore.TopEntry e = top.entries().get(i);
            String name = e.name() == null ? e.player().toString().substring(0, 8) : e.name();
            boolean you = sender instanceof Player p && p.getUniqueId().equals(e.player());
            msg.send(sender, baltopKey(i + 1, you), Msg.number("rank", i + 1), Msg.p("player", name),
                    amountTag("amount", sender, currency, e.balance()), Msg.p("exact", Format.number(e.balance())));
        }
        if (sender instanceof Player p && top.rank(p.getUniqueId()) == 0) {
            msg.send(sender, "baltop.not-listed", Msg.number("size", top.entries().size()),
                    amountTag("amount", sender, currency, mine.orElse(0)));
        }
    }

    // ================================================================== /pay

    private void pay(Player payer, String targetName, long amount, Currency currency) {
        if (net.skybornmc.skyblock.common.moderation.SanctionService.denied(core.services(), payer, net.skybornmc.skyblock.common.moderation.SanctionService.Use.PAY)) {
            return; // P7: frozen balance or blocked trading
        }
        if (net.skybornmc.skyblock.common.tutorial.TutorialGate.denied(core.services(), payer, net.skybornmc.skyblock.common.tutorial.Gates.PAY)) {
            return; // TUTORIAL §4: /pay opens after the bank step
        }
        Optional<PayRules.Problem> problem = PayRules.check(config, payer.getName(), targetName, amount, currency, frozen(payer));
        if (problem.isPresent()) {
            throw fail("pay." + problem.get().key, Msg.c("currency", currencyName(payer, currency)),
                    amountTag("min", payer, currency, config.payMin(currency)));
        }
        then(payer, resolve(targetName), found -> {
            if (found.isEmpty()) {
                msg.send(payer, "unknown-player", Msg.p("player", targetName));
                sound(payer, "error");
                return;
            }
            Found target = found.get();
            if (target.uuid().equals(payer.getUniqueId())) {
                msg.send(payer, "pay.self");
                sound(payer, "error");
                return;
            }
            UUID from = profiles().profileKey(payer);
            if (net.skybornmc.skyblock.common.moderation.SanctionService.of(core.services()).flatMap(s -> s.block(target.uuid(), net.skybornmc.skyblock.common.moderation.SanctionService.Use.PAY)).isPresent()) {
                net.skybornmc.skyblock.common.moderation.SanctionService.of(core.services()).ifPresent(s -> s.tellOther(payer, target.name(), net.skybornmc.skyblock.common.moderation.SanctionService.Use.PAY));
                return; // P7: the receiver's trading is blocked
            }
            then(payer, accountKey(target.uuid()), to -> {
                // P4: never between two profiles of the same player (also checked when the names differ).
                if (profiles().blockOwnTransfer(AuditCategory.ECONOMY, "pay", from, to, Actor.player(payer))) {
                    msg.send(payer, "pay.own-profile");
                    sound(payer, "error");
                    return;
                }
                // Owner F1: only after this profile earned send-gate.required itself, unless the receiver is on its island.
                then(payer, sendGate.check(from, to, currency), check -> {
                    if (!check.allowed()) {
                        tellSendGate(payer, check, "pay.send-gate");
                        return;
                    }
                    Transaction tx = Transaction.transfer(AccountRef.player(from), AccountRef.player(to), currency,
                            amount, TxReason.PAY).actor(Actor.player(payer)).detail("target_name", target.name());
                    then(payer, service.execute(tx), result -> payResult(payer, target, amount, currency, result));
                });
            });
        });
    }

    private void payResult(Player payer, Found target, long amount, Currency currency, TxResult result) {
        switch (result.status()) {
            case SUCCESS -> {
                msg.send(payer, "pay.sent", amountTag("amount", payer, currency, amount), Msg.p("player", target.name()),
                        amountTag("balance", payer, currency, result.fromBalance()));
                sound(payer, "success");
                Player online = Bukkit.getPlayer(target.uuid());
                if (online != null) {
                    msg.send(online, "pay.received", amountTag("amount", online, currency, amount), Msg.p("player", payer.getName()),
                            amountTag("balance", online, currency, result.toBalance()));
                    core.sounds().play(online, "money");
                }
            }
            case INSUFFICIENT_FUNDS -> {
                long have = service.cached(payer.getUniqueId(), currency).orElse(0);
                msg.send(payer, "pay.insufficient", amountTag("amount", payer, currency, amount), amountTag("balance", payer, currency, have));
                sound(payer, "error");
            }
            case OVERFLOW -> {
                msg.send(payer, "pay.overflow", Msg.p("player", target.name()));
                sound(payer, "error");
            }
            default -> {
                msg.send(payer, "error");
                sound(payer, "error");
            }
        }
    }

    // ================================================================== /eco

    /**
     * {@code /eco give|take|set|reset}. R3-39: never your own account or one that shares an address with yours. R3-6:
     * a change up to {@code eco.approve-above} applies at once (flagged: staff alert, audit, Discord when set up); a bigger
     * one waits for an owner/admin's Allow. The console is trusted and never waits.
     */
    private void eco(CommandSender sender, EcoAction action, String name, List<Currency> currencies, long amount, String note) {
        then(sender, resolve(name), found -> {
            if (found.isEmpty()) {
                msg.send(sender, "unknown-player", Msg.p("player", name));
                sound(sender, "error");
                return;
            }
            Found target = found.get();
            then(sender, targetProblem(sender, target.uuid()), problem -> {
                if (problem != null) {
                    refuseTarget(sender, target, problem, action, currencies, amount, note);
                    return;
                }
                then(sender, accountKey(target.uuid()), key -> {
                    for (Currency currency : currencies) {
                        ecoChecked(sender, action, target, key, currency, amount, note);
                    }
                });
            });
        });
    }

    /** R3-39: why {@code sender} may not change {@code target} (null = fine; the console is never refused). */
    CompletableFuture<EcoTargets.Problem> targetProblem(CommandSender sender, UUID target) {
        if (!(sender instanceof Player staff)) {
            return CompletableFuture.completedFuture(null);
        }
        Player online = Bukkit.getPlayer(target);
        EcoTargets.Problem quick = EcoTargets.quick(staff.getUniqueId(), target, ip(staff), online == null ? null : ip(online));
        if (quick == EcoTargets.Problem.SELF || (quick != null && config.ecoBlockSameIp)) {
            return CompletableFuture.completedFuture(quick);
        }
        if (!config.ecoBlockSameIp) {
            return CompletableFuture.completedFuture(null);
        }
        return ecoTargets.sharedAddress(staff.getUniqueId(), target).thenApply(shared -> shared ? EcoTargets.Problem.SAME_IP : null);
    }

    private static String ip(Player p) {
        java.net.InetSocketAddress address = p.getAddress();
        return address == null || address.getAddress() == null ? null : address.getAddress().getHostAddress();
    }

    private void refuseTarget(CommandSender sender, Found target, EcoTargets.Problem problem, EcoAction action,
                              List<Currency> currencies, long amount, String note) {
        msg.send(sender, "eco.target." + problem.key, Msg.p("player", target.name()));
        sound(sender, "error");
        Map<String, Object> details = new java.util.LinkedHashMap<>();
        details.put("problem", problem.key);
        details.put("action", action.key());
        details.put("currencies", currencies.stream().map(Currency::key).toList());
        details.put("amount", amount);
        details.put("note", note);
        core.audit().log(AuditCategory.ECONOMY, "eco_refused", Actor.of(sender), target.uuid().toString(), target.name(), amount,
                sender instanceof Player p ? p.getLocation() : null, details);
        String key = "eco.target.alert-" + problem.key;
        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (!staff.equals(sender) && staff.hasPermission(PERM_ALERTS)) {
                msg.send(staff, key, Msg.p("staff", sender.getName()), Msg.p("player", target.name()));
            }
        }
    }

    /** One currency: applies at once (flagged) or, above the limit, waits for an Allow. */
    private void ecoChecked(CommandSender sender, EcoAction action, Found target, UUID profileKey, Currency currency, long amount,
                            String note) {
        long value = action == EcoAction.RESET ? config.start(currency) : amount;
        long limit = config.ecoApproveAbove(currency);
        if (!(sender instanceof Player staff) || limit <= 0) {
            ecoOne(sender, Actor.of(sender), sender.getName(), action, target, profileKey, currency, value, note, null);
            return;
        }
        CompletableFuture<Long> balance = action == EcoAction.SET || action == EcoAction.RESET
                ? service.balance(AccountRef.player(profileKey), currency) : CompletableFuture.completedFuture(0L);
        then(sender, balance, now -> {
            long change = EcoApprovals.change(action, value, now);
            if (!EcoApprovals.needsApproval(change, limit)) {
                ecoOne(sender, Actor.of(sender), sender.getName(), action, target, profileKey, currency, value, note, null);
                return;
            }
            EcoApprovals.Pending p = approvals.add(staff.getUniqueId(), staff.getName(), action, target.uuid(), target.name(),
                    profileKey, currency, value, change, note, System.currentTimeMillis(), config.ecoPendingMinutes * 60_000L);
            Map<String, Object> details = pendingDetails(p);
            core.audit().log(AuditCategory.ECONOMY, "eco_pending", Actor.player(staff), target.uuid().toString(), target.name(),
                    change, staff.getLocation(), details);
            msg.send(sender, "eco.approve.waiting", pendingTags(sender, p));
            sound(sender, "notify");
            int told = 0;
            for (Player other : Bukkit.getOnlinePlayers()) {
                if (other.equals(sender)) {
                    continue;
                }
                if (other.hasPermission(PERM_APPROVE)) {
                    TagResolver[] tags = pendingTags(other, p);
                    int id = p.id();
                    // GUI-5: Java clicks [Allow]/[Deny] in chat; Bedrock staff also get a popup with the same choices.
                    core.menus().offers().offer(other, net.skybornmc.skyblock.common.menu.Offer.builder(msg.module(), "eco.approve.popup")
                            .id("eco-approve:" + id)
                            .icon(org.bukkit.Material.GOLD_INGOT)
                            .placeholders(tags)
                            .expiresAt(p.expires())
                            .validWhile(() -> approvals.get(id, System.currentTimeMillis()).isPresent())
                            .button(net.skybornmc.skyblock.common.menu.Offer.Button.allow("eco allow " + id))
                            .button(net.skybornmc.skyblock.common.menu.Offer.Button.deny("eco deny " + id))
                            .chat(viewer -> msg.send(viewer, "eco.approve.alert", tags))
                            .build());
                    core.sounds().play(other, "notify");
                    told++;
                } else if (other.hasPermission(PERM_ALERTS)) {
                    msg.send(other, "eco.approve.alert-info", pendingTags(other, p));
                }
            }
            msg.send(Bukkit.getConsoleSender(), "eco.approve.alert-info", pendingTags(Bukkit.getConsoleSender(), p));
            if (told == 0) {
                msg.send(sender, "eco.approve.nobody-online", pendingTags(sender, p));
            }
            discord("eco_pending", p.staffName(), p.targetName(), p.currency(), p.change(), p.note(), null);
        });
    }

    private Map<String, Object> pendingDetails(EcoApprovals.Pending p) {
        Map<String, Object> details = new java.util.LinkedHashMap<>();
        details.put("id", p.id());
        details.put("action", p.action().key());
        details.put("currency", p.currency().key());
        details.put("amount", p.amount());
        details.put("change", p.change());
        details.put("note", p.note());
        details.put("profile", p.profileKey().toString());
        details.put("requested_by", p.staffName());
        return details;
    }

    private TagResolver[] pendingTags(CommandSender viewer, EcoApprovals.Pending p) {
        long left = Math.max(0, p.expires() - System.currentTimeMillis());
        return new TagResolver[]{Msg.number("id", p.id()), Msg.p("staff", p.staffName()), Msg.p("player", p.targetName()),
                Msg.p("action", p.action().key()), exactAmountTag("amount", viewer, p.currency(), p.amount()),
                exactAmountTag("change", viewer, p.currency(), p.change()), Msg.c("currency", currencyName(viewer, p.currency())),
                Msg.p("reason", p.note()), Msg.p("left", Format.duration(Duration.ofMillis(left)))};
    }

    /**
     * {@code /eco allow|deny <id>} and the pending menu: an owner/admin (not the requester) decides a waiting change. The
     * first decision wins; the target checks (R3-39) run again for the approver.
     */
    void decide(CommandSender sender, int id, boolean allow) {
        long now = System.currentTimeMillis();
        Optional<EcoApprovals.Pending> found = approvals.get(id, now);
        UUID approver = sender instanceof Player p ? p.getUniqueId() : null;
        Optional<EcoApprovals.Problem> problem = EcoApprovals.canDecide(found.orElse(null), approver, sender.hasPermission(PERM_APPROVE),
                sender.hasPermission(PERM_APPROVE_OWN));
        if (problem.isPresent()) {
            msg.send(sender, "eco.approve." + problem.get().key, Msg.number("id", id));
            sound(sender, "error");
            return;
        }
        EcoApprovals.Pending p = found.get();
        if (!allow) {
            if (approvals.take(id, now).isEmpty()) {
                msg.send(sender, "eco.approve.unknown", Msg.number("id", id));
                return;
            }
            Map<String, Object> details = pendingDetails(p);
            details.put("decided_by", sender.getName());
            core.audit().log(AuditCategory.ECONOMY, "eco_denied", Actor.of(sender), p.target().toString(), p.targetName(), p.change(),
                    null, details);
            msg.send(sender, "eco.approve.denied", pendingTags(sender, p));
            Player requester = Bukkit.getPlayer(p.staff());
            if (requester != null) {
                msg.send(requester, "eco.approve.denied-you", Msg.number("id", p.id()), Msg.p("by", sender.getName()));
            }
            sound(sender, "success");
            return;
        }
        then(sender, targetProblem(sender, p.target()), targetProblem -> {
            if (targetProblem != null) {
                msg.send(sender, "eco.target." + targetProblem.key, Msg.p("player", p.targetName()));
                sound(sender, "error");
                return;
            }
            if (approvals.take(id, System.currentTimeMillis()).isEmpty()) {
                msg.send(sender, "eco.approve.unknown", Msg.number("id", id));
                sound(sender, "error");
                return;
            }
            Map<String, Object> details = pendingDetails(p);
            details.put("decided_by", sender.getName());
            core.audit().log(AuditCategory.ECONOMY, "eco_allowed", Actor.of(sender), p.target().toString(), p.targetName(), p.change(),
                    null, details);
            ecoOne(sender, Actor.player(p.staff(), p.staffName()), p.staffName(), p.action(), new Found(p.target(), p.targetName()),
                    p.profileKey(), p.currency(), p.amount(), p.note(), sender.getName());
            Player requester = Bukkit.getPlayer(p.staff());
            if (requester != null && !requester.equals(sender)) {
                msg.send(requester, "eco.approve.allowed-you", Msg.number("id", p.id()), Msg.p("by", sender.getName()));
            }
        });
    }

    /** Drops waiting changes older than eco.pending-minutes and tells the requester. Main thread. */
    private void expireApprovals() {
        for (EcoApprovals.Pending p : approvals.expire(System.currentTimeMillis())) {
            core.audit().log(AuditCategory.ECONOMY, "eco_expired", Actor.system("economy"), p.target().toString(), p.targetName(),
                    p.change(), null, pendingDetails(p));
            Player requester = Bukkit.getPlayer(p.staff());
            if (requester != null) {
                msg.send(requester, "eco.approve.expired", Msg.number("id", p.id()), Msg.p("player", p.targetName()));
            }
        }
    }

    /** Opens the waiting changes (Bedrock path of R3-6). */
    public void openPending(Player player, net.skybornmc.skyblock.common.menu.Menu from) {
        if (from == null) {
            core.menus().open(player, new PendingEcoMenu(this));
        } else {
            from.openChild(new PendingEcoMenu(this));
        }
    }

    /** How many changes wait for an Allow (dashboard button). */
    public int pendingCount() {
        return approvals.list(System.currentTimeMillis()).size();
    }

    /** True if the player may allow waiting changes. */
    public static boolean canApprove(Player player) {
        return player.hasPermission(PERM_APPROVE);
    }

    /** R3-6: the Discord hook (channel "economy"; off until a webhook is set up, then every flagged change is posted). */
    private void discord(String what, String staff, String target, Currency currency, long amount, String note, String approvedBy) {
        core.services().optional(net.skybornmc.skyblock.common.discord.DiscordSink.class).ifPresent(d -> {
            if (!d.enabled("economy")) {
                return;
            }
            List<net.skybornmc.skyblock.common.discord.DiscordSink.Field> fields = new ArrayList<>();
            fields.add(new net.skybornmc.skyblock.common.discord.DiscordSink.Field("Staff", staff, true));
            fields.add(new net.skybornmc.skyblock.common.discord.DiscordSink.Field("Player", target, true));
            fields.add(new net.skybornmc.skyblock.common.discord.DiscordSink.Field("Amount", service.format(currency, amount), true));
            fields.add(new net.skybornmc.skyblock.common.discord.DiscordSink.Field("Reason", note, false));
            if (approvedBy != null) {
                fields.add(new net.skybornmc.skyblock.common.discord.DiscordSink.Field("Allowed by", approvedBy, true));
            }
            d.post("economy", new net.skybornmc.skyblock.common.discord.DiscordSink.Embed(what, 0xFFC21A, fields, "Phoenix Skyblock",
                    System.currentTimeMillis()));
        });
    }

    /**
     * Applies one change. {@code key}: the player's profile (money, crystals, shards); gems go to the player account
     * automatically. {@code actor}/{@code staffName}: who asked for it; {@code approvedBy}: who allowed it (R3-6), or null.
     */
    private void ecoOne(CommandSender sender, Actor actor, String staffName, EcoAction action, Found target, UUID profileKey,
                        Currency currency, long amount, String note, String approvedBy) {
        AccountRef account = AccountRef.player(profileKey);
        String reference = note.length() > 64 ? note.substring(0, 64) : note;
        Map<String, Object> details = new java.util.LinkedHashMap<>();
        details.put("note", note);
        details.put("target_name", target.name());
        if (approvedBy != null) {
            details.put("approved_by", approvedBy);
        }
        Transaction base = switch (action) {
            case GIVE -> Transaction.deposit(account, currency, amount, TxReason.ADMIN_GIVE);
            case TAKE -> Transaction.withdraw(account, currency, amount, TxReason.ADMIN_TAKE);
            default -> null;
        };
        CompletableFuture<TxResult> future;
        if (base != null) {
            Transaction tx = base.actor(actor).reference(reference);
            for (Map.Entry<String, Object> e : details.entrySet()) {
                tx = tx.detail(e.getKey(), e.getValue());
            }
            future = service.execute(tx);
        } else {
            future = service.set(account, currency, amount, action == EcoAction.SET ? TxReason.ADMIN_SET : TxReason.ADMIN_RESET, actor,
                    details);
        }
        String key = action.key();
        then(sender, future, result -> {
            if (!result.ok()) {
                if (result.status() == TxResult.Status.INSUFFICIENT_FUNDS) {
                    then(sender, service.balance(account, currency), have -> {
                        msg.send(sender, "eco.insufficient", Msg.p("player", target.name()), amountTag("balance", sender, currency, have));
                        sound(sender, "error");
                    });
                } else {
                    msg.send(sender, result.status() == TxResult.Status.OVERFLOW ? "eco.overflow" : "error",
                            Msg.p("player", target.name()));
                    sound(sender, "error");
                }
                return;
            }
            long now = result.toBalance() != null ? result.toBalance() : result.fromBalance() != null ? result.fromBalance() : 0;
            // R3-6: every applied change is flagged: its own audit row (searchable as /logs eco_flagged), staff alert, Discord.
            Map<String, Object> flag = new java.util.LinkedHashMap<>(details);
            flag.put("action", key);
            flag.put("currency", currency.key());
            flag.put("ledger_id", result.ledgerId());
            flag.put("requested_by", staffName);
            core.audit().log(AuditCategory.ECONOMY, "eco_flagged", actor, target.uuid().toString(), target.name(), amount, null, flag);
            discord("eco_" + key, staffName, target.name(), currency, amount, note, approvedBy);
            TagResolver[] p = {Msg.p("player", target.name()), Msg.p("staff", staffName), Msg.p("action", key),
                    amountTag("amount", sender, currency, amount), amountTag("balance", sender, currency, now),
                    Msg.c("currency", currencyName(sender, currency)), Msg.p("reason", note)};
            msg.send(sender, "eco.done-" + key, p);
            sound(sender, "success");
            for (Player staff : Bukkit.getOnlinePlayers()) {
                if (!staff.equals(sender) && staff.hasPermission(PERM_ALERTS)) {
                    msg.send(staff, "eco.alert-" + key, Msg.p("player", target.name()), Msg.p("staff", staffName),
                            amountTag("amount", staff, currency, amount), amountTag("balance", staff, currency, now),
                            Msg.c("currency", currencyName(staff, currency)), Msg.p("reason", note));
                    if (approvedBy != null) {
                        msg.send(staff, "eco.approve.allowed-alert", Msg.p("by", approvedBy));
                    }
                }
            }
            if (!(sender instanceof org.bukkit.command.ConsoleCommandSender)) {
                msg.send(Bukkit.getConsoleSender(), "eco.alert-" + key, p);
            }
            Player online = Bukkit.getPlayer(target.uuid());
            if (online != null && !online.equals(sender) && result.ledgerId() > 0) {
                msg.send(online, "eco.notify", amountTag("balance", online, currency, now), Msg.c("currency", currencyName(online, currency)));
            }
        });
    }

    /** STYLE2: places 1-3 use the top1/top2/top3 colors ({@code baltop.line-top<n>}); your own line keeps its highlight. */
    static String baltopKey(int rank, boolean you) {
        if (you) {
            return "baltop.line-you";
        }
        return rank >= 1 && rank <= 3 ? "baltop.line-top" + rank : "baltop.line";
    }
}
