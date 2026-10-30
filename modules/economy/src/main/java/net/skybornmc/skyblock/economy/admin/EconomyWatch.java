package net.skybornmc.skyblock.economy.admin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.skybornmc.skyblock.common.Core;
import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.audit.AuditCategory;
import net.skybornmc.skyblock.common.command.Arg;
import net.skybornmc.skyblock.common.command.CommandFailure;
import net.skybornmc.skyblock.common.command.CommandSpec;
import net.skybornmc.skyblock.common.command.Suggest;
import net.skybornmc.skyblock.common.discord.DiscordSink;
import net.skybornmc.skyblock.common.economy.AccountRef;
import net.skybornmc.skyblock.common.economy.Currency;
import net.skybornmc.skyblock.common.menu.Menu;
import net.skybornmc.skyblock.common.moderation.SuggestionService;
import net.skybornmc.skyblock.common.profile.ProfileService;
import net.skybornmc.skyblock.common.sell.SellPrice;
import net.skybornmc.skyblock.common.sell.SellService;
import net.skybornmc.skyblock.common.text.ModuleMessages;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.util.Format;
import net.skybornmc.skyblock.common.util.Scheduler;
import net.skybornmc.skyblock.economy.EconomyModule;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.logging.Level;

/**
 * Economy dashboard + anomaly alerts (M3.8) inside the economy module: the background {@link WatchJob} (incremental,
 * off the main thread), staff alerts in chat (permission {@value #PERM_ANOMALIES}), Discord (off until a webhook is set),
 * the audit log, and {@code /eco admin [alert]} with the dashboard menus (permission {@value #PERM_DASHBOARD}).
 *
 * <p>Never punishes: an alert links to its evidence ({@code /eco admin <id>}) and to {@code /p <player>}; staff may file a
 * punishment suggestion ({@link SuggestionService}, which itself never applies anything) from the evidence screen.</p>
 */
public final class EconomyWatch {

    /** {@code /eco admin}: the dashboard, alert evidence and alert review. */
    public static final String PERM_DASHBOARD = "skyblock.staff.economy.dashboard";
    /** Anomaly alerts in chat. */
    public static final String PERM_ANOMALIES = "skyblock.staff.economy.anomalies";
    /** Owner answer 334: accounts whose big transfers admins are told about. */
    public static final String PERM_FLAGGED = "skyblock.staff.economy.flagged";

    private static final long WARN_EVERY_MS = 600_000L;
    private static final int RECENT_IDS = 20;

    final EconomyModule module;
    final Core core;
    final ModuleMessages msg;
    final WatchConfig config = new WatchConfig();
    private WatchStore store;
    private AnomalyDetector detector;
    private WatchJob job;
    private Scheduler.Task task;
    private volatile long lastWarning;
    private final List<String> recentIds = new CopyOnWriteArrayList<>();
    private final Map<Currency, DashboardData> cache = new EnumMap<>(Currency.class);
    /** Player UUIDs of staff accounts seen online since the start (owner answer 334). */
    private final java.util.Set<UUID> staffAccounts = java.util.concurrent.ConcurrentHashMap.newKeySet();

    public EconomyWatch(EconomyModule module, Core core, ModuleMessages msg) {
        this.module = module;
        this.core = core;
        this.msg = msg;
    }

    // ================================================================== lifecycle

    public void enable() {
        core.configs().load(config);
        store = new WatchStore(core.database());
        detector = new AnomalyDetector(() -> config, this::staffAccount);
        Bukkit.getOnlinePlayers().forEach(this::rememberStaff);
        job = new WatchJob(store, detector, () -> config, System::currentTimeMillis, Format::zone,
                () -> profiles()::ownerOf);
        long period = Math.max(10, config.intervalSeconds) * 20L;
        task = core.scheduler().repeat("economy:watch", 400L, period, this::tick);
        refreshRecentIds();
    }

    public void reload() {
        core.configs().load(config);
        synchronized (cache) {
            cache.clear();
        }
    }

    public void disable() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (core.menus().openMenu(p) instanceof WatchMenu) {
                p.closeInventory();
            }
        }
    }

    ProfileService profiles() {
        return module.profiles();
    }

    /** Called on join (economy module): remembers (or forgets) a staff account for the staff-transfer rule. */
    public void rememberStaff(Player player) {
        if (player.hasPermission(PERM_FLAGGED)) {
            staffAccounts.add(player.getUniqueId());
        } else {
            staffAccounts.remove(player.getUniqueId());
        }
    }

    /** A profile key or player UUID of a staff account (any thread). */
    private boolean staffAccount(UUID id) {
        if (id == null || staffAccounts.isEmpty()) {
            return false;
        }
        if (staffAccounts.contains(id)) {
            return true;
        }
        ProfileService profiles = profiles();
        return profiles != null && staffAccounts.contains(profiles.ownerOf(id));
    }

    WatchStore store() {
        return store;
    }

    WatchJob job() {
        return job;
    }

    /** Main thread: starts a background run if the watch is on. */
    private void tick() {
        if (!config.enabled || job == null || job.busy()) {
            return;
        }
        runJob().exceptionally(e -> null);
    }

    /** Starts a run and delivers its alerts on the main thread. */
    CompletableFuture<WatchJob.Result> runJob() {
        return job.run().whenCompleteAsync((result, error) -> {
            if (error != null) {
                long now = System.currentTimeMillis();
                if (now - lastWarning > WARN_EVERY_MS) {
                    lastWarning = now;
                    core.plugin().getLogger().log(Level.WARNING, "Economy watch: a background run failed (retried every "
                            + config.intervalSeconds + " s)", error);
                }
                return;
            }
            if (!result.alerts().isEmpty()) {
                synchronized (cache) {
                    cache.clear();
                }
                for (WatchAlert alert : result.alerts()) {
                    deliver(alert, result.names());
                }
                refreshRecentIds();
            }
        }, module.mainThread());
    }

    private void refreshRecentIds() {
        store.async(c -> WatchStore.recentIds(c, RECENT_IDS)).thenAccept(ids -> {
            recentIds.clear();
            ids.forEach(id -> recentIds.add(String.valueOf(id)));
        }).exceptionally(e -> null);
    }

    // ================================================================== alerts out

    /** Main thread: tells staff, the console, Discord and the audit log about a new alert. */
    void deliver(WatchAlert alert, Map<UUID, String> names) {
        String player = name(alert.subject(), names);
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("alert_id", alert.id());
        details.put("rule", alert.rule().id());
        details.put("severity", alert.severity().key());
        details.put("currency", alert.currency().key());
        details.put("expected", alert.expected());
        details.put("hits", alert.hits());
        details.put("hour", alert.hour());
        if (alert.other() != null) {
            details.put("other", alert.other().toString());
            details.put("other_name", name(alert.other(), names));
        }
        if (!alert.ledgerIds().isEmpty()) {
            details.put("ledger_ids", alert.ledgerIds().toString());
        }
        UUID target = alert.subject() == null ? null : profiles().ownerOf(alert.subject());
        core.audit().log(AuditCategory.ECONOMY, "eco_anomaly", Actor.system("economy-watch"),
                target == null ? null : target.toString(), alert.subject() == null ? null : player, alert.amount(), null, details);
        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (staff.hasPermission(alert.rule() == Rule.STAFF_TRANSFER ? config.staffAlertPermission : PERM_ANOMALIES)) {
                msg.send(staff, chatKey(alert), chatTags(staff, alert, names));
                core.sounds().play(staff, "notify");
            }
        }
        msg.send(Bukkit.getConsoleSender(), chatKey(alert), chatTags(Bukkit.getConsoleSender(), alert, names));
        discord(alert, names);
        if (alert.subject() != null && config.autoSuggestRules.contains(alert.rule().id())) {
            suggest(null, alert, names);
        }
    }

    private String chatKey(WatchAlert alert) {
        return alert.subject() != null ? "watch.chat.player" : "watch.chat.server";
    }

    private TagResolver[] chatTags(CommandSender viewer, WatchAlert alert, Map<UUID, String> names) {
        return new TagResolver[]{Msg.c("severity", severity(viewer, alert)), Msg.c("rule", ruleName(viewer, alert)),
                Msg.c("details", details(viewer, alert, names)), Msg.p("id", alert.id()),
                Msg.p("player", name(alert.subject(), names))};
    }

    private void discord(WatchAlert alert, Map<UUID, String> names) {
        Optional<DiscordSink> sink = core.services().optional(DiscordSink.class);
        if (sink.isEmpty() || config.discordChannel.isBlank() || !sink.get().enabled(config.discordChannel)) {
            return;
        }
        CommandSender console = Bukkit.getConsoleSender();
        String text = PlainTextComponentSerializer.plainText().serialize(details(console, alert, names));
        List<DiscordSink.Field> fields = List.of(
                new DiscordSink.Field("What", text, false),
                new DiscordSink.Field("Severity", alert.severity().key(), true),
                new DiscordSink.Field("Open", "/eco admin " + alert.id(), true));
        int color = alert.severity() == AlertDraft.Severity.HIGH ? 0xFF5C5C : 0xFFB347;
        sink.get().post(config.discordChannel, new DiscordSink.Embed("Economy alert #" + alert.id() + ": "
                + PlainTextComponentSerializer.plainText().serialize(ruleName(console, alert)), color, fields, "Phoenix Skyblock",
                alert.createdAt()));
    }

    // ================================================================== text

    /** The raw text of a message key (no placeholders). */
    String raw(String key) {
        String value = core.messages().raw(EconomyModule.ID, key);
        return value == null ? key : value;
    }

    String name(UUID profile, Map<UUID, String> names) {
        if (profile == null) {
            return raw("watch.account.server");
        }
        String name = names.get(profile);
        return name != null ? name : profile.toString().substring(0, 8);
    }

    Component severity(CommandSender viewer, WatchAlert alert) {
        return msg.component(viewer, "watch.severity." + alert.severity().key());
    }

    /** The rules.* key of an alert (some rules have a second wording). */
    static String variant(WatchAlert alert) {
        if (alert.rule() == Rule.BIG_TRANSFER && alert.other() == null) {
            return "big-transfer-created";
        }
        if (alert.rule() == Rule.BALANCE_MISMATCH && alert.subject() == null) {
            return "balance-mismatch-account";
        }
        return alert.rule().id();
    }

    Component ruleName(CommandSender viewer, WatchAlert alert) {
        return msg.component(viewer, "watch.rules." + variant(alert) + ".name");
    }

    private static final DateTimeFormatter HOUR = DateTimeFormatter.ofPattern("d MMM HH:00", Locale.ENGLISH);
    static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.ENGLISH);

    static String date(long millis) {
        return DATE.format(Instant.ofEpochMilli(millis).atZone(Format.zone()));
    }

    /** The one-line details of an alert. */
    Component details(CommandSender viewer, WatchAlert alert, Map<UUID, String> names) {
        boolean counts = alert.rule() == Rule.REPEAT_TRANSFER || alert.rule() == Rule.NOTE_REDEEM;
        TagResolver expected = counts || alert.rule() == Rule.SINK_RATIO ? Msg.p("expected", Format.number(alert.expected()))
                : module.amountTag("expected", viewer, alert.currency(), alert.expected());
        ZoneId zone = Format.zone();
        return msg.component(viewer, "watch.rules." + variant(alert) + ".text",
                Msg.p("player", name(alert.subject(), names)),
                Msg.p("other", alert.other() == null ? "-" : name(alert.other(), names)),
                alert.rule() == Rule.SINK_RATIO ? Msg.p("amount", percent(alert.amount() / 100.0))
                        : module.amountTag("amount", viewer, alert.currency(), alert.amount()),
                expected,
                Msg.p("hits", Format.number(alert.hits())),
                Msg.p("ratio", String.format(Locale.ROOT, "%.1f", alert.amount() / 100.0)),
                Msg.p("line", String.format(Locale.ROOT, "%.0f", alert.expected() / 100.0)),
                Msg.p("hour", HOUR.format(Instant.ofEpochMilli(Buckets.start(alert.hour())).atZone(zone))),
                Msg.c("currency", msg.component(viewer, "names." + alert.currency().key())));
    }

    /** {@code 55.2%}, or the no-value text. */
    String percent(double value) {
        return Double.isNaN(value) || Double.isInfinite(value) ? raw("watch.percent-none")
                : String.format(Locale.ROOT, "%.1f%%", value);
    }

    /** {@code +12.5%} / {@code -3.0%}, or the no-value text. */
    String signedPercent(double value) {
        return Double.isNaN(value) || Double.isInfinite(value) ? raw("watch.percent-none")
                : String.format(Locale.ROOT, "%+.1f%%", value);
    }

    TagResolver amount(String tag, Player viewer, Currency currency, long value) {
        return module.amountTag(tag, viewer, currency, value);
    }

    /** A reason or transit group as staff read it. */
    Component reasonLabel(Player viewer, String key, boolean group) {
        String path = group ? "watch.groups." + key : "reasons." + key;
        return msg.has(path) ? msg.component(viewer, path) : Component.text(key.replace('_', ' '));
    }

    /** Who an account is, for lists. */
    String accountLabel(AccountRef account, Map<UUID, String> names) {
        if (account == null) {
            return raw("watch.account.server");
        }
        if (account.isIsland()) {
            return raw("watch.account.island");
        }
        String name = names.get(account.owner());
        if (account.isBank()) {
            return raw("watch.account.bank") + (name == null ? "" : " (" + name + ")");
        }
        return name != null ? name : raw("watch.account.unknown") + " " + account.owner().toString().substring(0, 8);
    }

    // ================================================================== data for menus

    /** The overview numbers of a currency (cached for {@code dashboard.cache-seconds}). */
    CompletableFuture<DashboardData> dashboard(Currency currency, boolean fresh) {
        long now = System.currentTimeMillis();
        synchronized (cache) {
            DashboardData cached = cache.get(currency);
            if (!fresh && cached != null && now - cached.takenAt() < config.cacheSeconds * 1000L) {
                return CompletableFuture.completedFuture(cached);
            }
        }
        return store.async(c -> DashboardData.load(c, currency, config, Format.zone(), now)).thenApply(data -> {
            synchronized (cache) {
                cache.put(currency, data);
            }
            return data;
        });
    }

    /** Player names of profile keys (profile → name). */
    CompletableFuture<Map<UUID, String>> names(Collection<UUID> profiles) {
        List<UUID> list = profiles.stream().filter(java.util.Objects::nonNull).distinct().toList();
        if (list.isEmpty()) {
            return CompletableFuture.completedFuture(Map.of());
        }
        var owners = (java.util.function.UnaryOperator<UUID>) profiles()::ownerOf;
        return store.async(c -> WatchStore.names(c, list, owners));
    }

    /** The account keys (profiles and banks) of ledger rows, for {@link #names}. */
    static List<UUID> accountKeys(Collection<LedgerRow> rows) {
        List<UUID> out = new ArrayList<>();
        for (LedgerRow r : rows) {
            if (r.from() != null && !r.from().isIsland()) {
                out.add(r.from().owner());
            }
            if (r.to() != null && !r.to().isIsland()) {
                out.add(r.to().owner());
            }
        }
        return out;
    }

    /** Today's sell price index (main thread), or {@code null} when selling is not loaded. */
    SellIndex sellIndex() {
        Optional<SellService> sell = core.services().optional(SellService.class);
        if (sell.isEmpty()) {
            return null;
        }
        List<SellPrice> prices = new ArrayList<>();
        for (String key : sell.get().itemKeys()) {
            sell.get().price(key).ifPresent(prices::add);
        }
        return SellIndex.of(prices);
    }

    /** Hops to the main thread; a failure tells the viewer and logs. */
    <T> void then(Player viewer, CompletableFuture<T> future, Consumer<T> action) {
        future.whenCompleteAsync((value, error) -> {
            if (error != null) {
                core.plugin().getLogger().log(Level.WARNING, "Economy watch: request by " + viewer.getName() + " failed", error);
                msg.send(viewer, "error");
                core.sounds().play(viewer, "error");
                return;
            }
            if (viewer.isOnline()) {
                action.accept(value);
            }
        }, module.mainThread());
    }

    // ================================================================== staff actions

    /** Sets an alert's state (audited). */
    void setState(Player staff, WatchAlert alert, WatchAlert.State state, Runnable after) {
        long now = System.currentTimeMillis();
        then(staff, store.async(c -> WatchStore.setState(c, alert.id(), state, staff.getName(), now)), before -> {
            Component label = msg.component(staff, "watch.state." + state.key());
            if (before.isEmpty()) {
                msg.send(staff, "watch.state-unchanged", Msg.p("id", alert.id()), Msg.c("state", label));
                core.sounds().play(staff, "error");
            } else {
                String action = switch (state) {
                    case REVIEWED -> "eco_alert_review";
                    case DISMISSED -> "eco_alert_dismiss";
                    case OPEN -> "eco_alert_reopen";
                };
                core.audit().log(AuditCategory.ECONOMY, action, Actor.player(staff), Map.of("alert_id", alert.id(),
                        "rule", alert.rule().id(), "before", before.get().key(), "after", state.key()));
                msg.send(staff, "watch.state-changed", Msg.p("id", alert.id()), Msg.c("state", label));
                core.sounds().play(staff, "success");
                synchronized (cache) {
                    cache.clear();
                }
            }
            after.run();
        });
    }

    /**
     * Files a punishment suggestion for the alert's player through the moderation module (never applies anything).
     * {@code staff == null}: automatic ({@code alerts.auto-suggest-rules}).
     */
    void suggest(Player staff, WatchAlert alert, Map<UUID, String> names) {
        if (alert.subject() == null) {
            if (staff != null) {
                msg.send(staff, "watch.no-player");
            }
            return;
        }
        Optional<SuggestionService> service = core.services().optional(SuggestionService.class);
        String player = name(alert.subject(), names);
        if (service.isEmpty()) {
            if (staff != null) {
                msg.send(staff, "watch.suggest-unavailable", Msg.p("player", player));
                core.sounds().play(staff, "error");
            }
            return;
        }
        UUID target = profiles().ownerOf(alert.subject());
        String text = PlainTextComponentSerializer.plainText().serialize(details(Bukkit.getConsoleSender(), alert, names));
        String detail = "#" + alert.id() + " " + (text.length() > 200 ? text.substring(0, 200) : text);
        String source = staff == null ? "Economy watch" : "Economy watch (" + staff.getName() + ")";
        CompletableFuture<SuggestionService.Result> future = service.get().suggest(target, config.suggestReason, source, detail);
        core.audit().logPlayer(AuditCategory.ECONOMY, "eco_alert_suggest", staff == null ? Actor.system("economy-watch")
                : Actor.player(staff), target, player, null, null, Map.of("alert_id", alert.id(), "reason", config.suggestReason));
        if (staff == null) {
            future.exceptionally(e -> {
                core.plugin().getLogger().warning("Economy watch: auto suggestion failed: " + e.getMessage());
                return null;
            });
            return;
        }
        then(staff, future, result -> {
            switch (result.status()) {
                case CREATED -> msg.send(staff, "watch.suggested", Msg.p("player", player));
                case REFRESHED -> msg.send(staff, "watch.suggest-refreshed", Msg.p("player", player));
                default -> {
                    msg.send(staff, "watch.suggest-failed", Msg.p("why", result.status().name().toLowerCase(Locale.ROOT)));
                    core.sounds().play(staff, "error");
                    return;
                }
            }
            core.sounds().play(staff, "success");
        });
    }

    /** Opens the punishment menu for the alert's player ({@code /p <player>}; the moderation module checks permissions). */
    void punish(Player staff, WatchAlert alert, Map<UUID, String> names) {
        if (alert.subject() == null || !names.containsKey(alert.subject())) {
            msg.send(staff, "watch.no-player");
            core.sounds().play(staff, "error");
            return;
        }
        staff.closeInventory();
        staff.performCommand("p " + names.get(alert.subject()));
    }

    /** The full ledger of a profile (the players' own /transactions view, opened for staff). */
    void history(Menu from, UUID profile) {
        module.openHistory(from, profile);
    }

    // ================================================================== /eco admin

    /** {@code /eco admin [alert]}: the dashboard, or one alert's evidence. */
    public CommandSpec adminCommand() {
        Arg<Long> alertArg = Arg.word("alert", (sender, raw) -> {
            String clean = raw.startsWith("#") ? raw.substring(1) : raw;
            try {
                long id = Long.parseLong(clean.trim());
                if (id <= 0) {
                    throw new NumberFormatException();
                }
                return id;
            } catch (NumberFormatException e) {
                throw new CommandFailure(EconomyModule.ID, "watch.invalid-alert", Msg.p("input", raw));
            }
        }, Suggest.of(() -> recentIds));
        return CommandSpec.sub("admin")
                .description("Staff: economy dashboard and anomaly alerts")
                .permission(PERM_DASHBOARD)
                .playerOnly()
                .optional(alertArg)
                .example("eco admin")
                .executes(ctx -> {
                    Player player = ctx.player();
                    Long id = ctx.getOr(alertArg, null);
                    if (id == null) {
                        if (!config.enabled) {
                            msg.send(player, "watch.disabled");
                        }
                        core.menus().open(player, new DashboardMenu(this, Currency.MONEY));
                        return;
                    }
                    openAlert(player, id, null);
                });
    }

    /** Loads an alert and opens its evidence screen. */
    void openAlert(Player player, long id, Menu parent) {
        then(player, store.async(c -> WatchStore.alert(c, id)), alert -> {
            if (alert.isEmpty()) {
                msg.send(player, "watch.unknown-alert", Msg.p("id", id));
                core.sounds().play(player, "error");
                return;
            }
            AlertMenu menu = new AlertMenu(this, alert.get());
            if (parent != null) {
                parent.openChild(menu);
            } else {
                core.menus().open(player, menu);
            }
        });
    }
}
