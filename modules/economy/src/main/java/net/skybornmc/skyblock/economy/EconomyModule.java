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
        upgradeConfigFile();
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
        // Checks every 5 s (a timestamp compare); a list is only read again once it is older than refresh-seconds.
        baltopTask = core.scheduler().repeat("economy:baltop", BALTOP_CHECK_TICKS, BALTOP_CHECK_TICKS,
                () -> baltop.refreshIfOlder(baltopMaxAge(), config.baltopSize));
    }

    @Override
    public void reload() {
        upgradeConfigFile();
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

    /** Value changes between config versions ({@link EconomyConfig#upgradeValues}); never touches a broken file. */
    private void upgradeConfigFile() {
        java.io.File file = new java.io.File(core.plugin().getDataFolder(), config.fileName());
        if (!file.isFile()) {
            return;
        }
        org.bukkit.configuration.file.YamlConfiguration yaml = new org.bukkit.configuration.file.YamlConfiguration();
        try {
            yaml.load(file);
            if (EconomyConfig.upgradeValues(yaml)) {
                yaml.save(file);
                core.plugin().getLogger().info(config.fileName() + ": new-account limit changed from the old default "
                        + EconomyConfig.OLD_NOTE_MINUTES_DEFAULT + " to 30 minutes (owner decision #40)");
            }
        } catch (java.io.IOException | org.bukkit.configuration.InvalidConfigurationException e) {
            // The config manager reports broken files and keeps every value; nothing to do here.
        }
    }

    @Override
    public void disable() {
        if (baltopTask != null) {
            baltopTask.cancel();
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
                    return PayRules.check(config, p.getName(), ctx.get(payTarget), amount, currency, frozen(p), playtime(p))
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
                .sub(notes.ecoNoteCommand(noteReasons).permission(PERM_ECO)));
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
            msg.send(sender, you ? "baltop.line-you" : "baltop.line", Msg.number("rank", i + 1), Msg.p("player", name),
                    amountTag("amount", sender, currency, e.balance()), Msg.p("exact", Format.number(e.balance())));
        }
        if (sender instanceof Player p && top.rank(p.getUniqueId()) == 0) {
            msg.send(sender, "baltop.not-listed", Msg.number("size", top.entries().size()),
                    amountTag("amount", sender, currency, mine.orElse(0)));
        }
    }

    // ================================================================== /pay

    /** Total playtime of an online player, including the current session. */
    long playtime(Player player) {
        return core.players().get(player.getUniqueId()).map(PlayerData::playtimeMillis).orElse(0L);
    }

    private void pay(Player payer, String targetName, long amount, Currency currency) {
        if (net.skybornmc.skyblock.common.moderation.SanctionService.denied(core.services(), payer, net.skybornmc.skyblock.common.moderation.SanctionService.Use.PAY)) {
            return; // P7: frozen balance or blocked trading
        }
        long played = playtime(payer);
        Optional<PayRules.Problem> problem = PayRules.check(config, payer.getName(), targetName, amount, currency, frozen(payer),
                played);
        if (problem.isPresent()) {
            throw fail("pay." + problem.get().key, Msg.c("currency", currencyName(payer, currency)),
                    amountTag("min", payer, currency, config.payMin(currency)),
                    Msg.p("time", Format.duration(Duration.ofMillis(PayRules.playtimeLeft(config, played)))));
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
                Transaction tx = Transaction.transfer(AccountRef.player(from), AccountRef.player(to), currency,
                        amount, TxReason.PAY).actor(Actor.player(payer)).detail("target_name", target.name());
                then(payer, service.execute(tx), result -> payResult(payer, target, amount, currency, result));
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

    private enum EcoAction { GIVE, TAKE, SET, RESET }

    private void eco(CommandSender sender, EcoAction action, String name, List<Currency> currencies, long amount, String note) {
        then(sender, resolve(name), found -> {
            if (found.isEmpty()) {
                msg.send(sender, "unknown-player", Msg.p("player", name));
                sound(sender, "error");
                return;
            }
            Found target = found.get();
            then(sender, accountKey(target.uuid()), key -> {
                for (Currency currency : currencies) {
                    ecoOne(sender, action, target, key, currency, amount, note);
                }
            });
        });
    }

    /** {@code key}: the player's active profile (money, crystals, shards); gems go to the player account automatically. */
    private void ecoOne(CommandSender sender, EcoAction action, Found target, UUID profileKey, Currency currency, long amount,
                        String note) {
        AccountRef account = AccountRef.player(profileKey);
        Actor actor = Actor.of(sender);
        String reference = note.length() > 64 ? note.substring(0, 64) : note;
        Map<String, Object> details = Map.of("note", note, "target_name", target.name());
        CompletableFuture<TxResult> future = switch (action) {
            case GIVE -> service.execute(Transaction.deposit(account, currency, amount, TxReason.ADMIN_GIVE)
                    .actor(actor).reference(reference).detail("note", note).detail("target_name", target.name()));
            case TAKE -> service.execute(Transaction.withdraw(account, currency, amount, TxReason.ADMIN_TAKE)
                    .actor(actor).reference(reference).detail("note", note).detail("target_name", target.name()));
            case SET -> service.set(account, currency, amount, TxReason.ADMIN_SET, actor, details);
            case RESET -> service.set(account, currency, config.start(currency), TxReason.ADMIN_RESET, actor, details);
        };
        String key = action.name().toLowerCase(Locale.ROOT);
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
            long shown = action == EcoAction.RESET ? config.start(currency) : amount;
            TagResolver[] p = {Msg.p("player", target.name()), Msg.p("staff", sender.getName()), Msg.p("action", key),
                    amountTag("amount", sender, currency, shown), amountTag("balance", sender, currency, now),
                    Msg.c("currency", currencyName(sender, currency)), Msg.p("reason", note)};
            msg.send(sender, "eco.done-" + key, p);
            sound(sender, "success");
            for (Player staff : Bukkit.getOnlinePlayers()) {
                if (!staff.equals(sender) && staff.hasPermission(PERM_ALERTS)) {
                    msg.send(staff, "eco.alert-" + key, Msg.p("player", target.name()), Msg.p("staff", sender.getName()),
                            amountTag("amount", staff, currency, shown), amountTag("balance", staff, currency, now),
                            Msg.c("currency", currencyName(staff, currency)), Msg.p("reason", note));
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
}
