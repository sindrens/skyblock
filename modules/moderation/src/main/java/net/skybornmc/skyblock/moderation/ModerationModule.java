package net.skybornmc.skyblock.moderation;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.skybornmc.skyblock.common.Core;
import net.skybornmc.skyblock.common.command.Arg;
import net.skybornmc.skyblock.common.command.Args;
import net.skybornmc.skyblock.common.command.CommandFailure;
import net.skybornmc.skyblock.common.command.CommandService;
import net.skybornmc.skyblock.common.command.CommandSpec;
import net.skybornmc.skyblock.common.command.Suggest;
import net.skybornmc.skyblock.common.db.MainThread;
import net.skybornmc.skyblock.common.db.Migrations;
import net.skybornmc.skyblock.common.module.SkyblockModule;
import net.skybornmc.skyblock.common.text.ModuleMessages;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.util.Format;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerEditBookEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.logging.Level;

/**
 * Custom punishments (Phase 1 M1.8): ban/tempban/ipban, mute/tempmute, warn, kick, pardons with reasons, templates
 * with escalation ladders ({@code /punish}), {@code /history}, {@code /alts}, alt alerts on join, mute enforcement
 * (chat, private messages via the {@code muted} placeholder, signs, books). Our database is the source of truth; the
 * vanilla ban list is kept in sync. Our commands replace vanilla {@code /ban /kick /pardon} (owner decision).
 */
public final class ModerationModule implements SkyblockModule, Listener {

    static final String ID = "moderation";

    static final String PERM_UNBAN = "skyblock.staff.unban";
    static final String PERM_NOTIFY = "skyblock.staff.notify";
    static final String PERM_WARN = "skyblock.staff.punish.warn";
    static final String PERM_KICK = "skyblock.staff.punish.kick";
    static final String PERM_MUTE = "skyblock.staff.punish.mute";
    static final String PERM_BAN = "skyblock.staff.punish.ban";
    static final String PERM_IPBAN = "skyblock.staff.punish.ipban";
    static final String PERM_UNMUTE = "skyblock.staff.punish.unmute";
    static final String PERM_UNWARN = "skyblock.staff.punish.unwarn";
    static final String PERM_SILENT = "skyblock.staff.punish.silent";
    static final String PERM_GUI = "skyblock.staff.punish.gui";
    static final String PERM_HISTORY = "skyblock.staff.punish.history";
    static final String PERM_ALTS = "skyblock.staff.punish.alts";
    static final String PERM_EXEMPT = "skyblock.staff.punish.exempt";
    static final String PERM_EXEMPT_BYPASS = "skyblock.staff.punish.exempt.bypass";
    /** P2 (spec 12.1, 12.2, 12.10). */
    static final String PERM_UNDO_OTHERS = "skyblock.staff.punish.undo.others";
    static final String PERM_FORCE = "skyblock.staff.punish.force";
    static final String PERM_STATS = "skyblock.staff.punish.stats";
    static final String PERM_STATS_OTHERS = "skyblock.staff.punish.stats.others";
    /** P3 (spec 12.5, 12.12, 12.6). */
    static final String PERM_NOTES = "skyblock.staff.punish.notes";
    static final String PERM_VIEW_OWN = "skyblock.punish.view.own";
    /** P4 (spec 13.4, 13.5). */
    static final String PERM_APPROVE = "skyblock.staff.punish.approve";
    /** P8, spec 14.6: marks a trainee (given with a LuckPerms group); 14.6 dry run; 14.9 /pnotify. */
    static final String PERM_TRAINEE = "skyblock.staff.punish.trainee";
    static final String PERM_DRY = "skyblock.staff.punish.dry";
    static final String PERM_WATCH = "skyblock.staff.watch";
    /** P5 (spec 13.8, 13.10). */
    static final String PERM_BULK = "skyblock.staff.punish.bulk";
    /** P7, spec 13.2: island ladder steps (island kick/ban, coop remove/block). */
    static final String PERM_ISLAND = "skyblock.staff.punish.island";
    static final String PERM_APPEAL = "skyblock.punish.appeal";
    static final String PERM_APPEALS = "skyblock.staff.appeals";
    /** P6 (spec 13.3; lockdown permissions live in {@link LockdownService}). */
    static final String PERM_SUGGEST = "skyblock.staff.punish.suggest";
    /** Staff told about broken punishments.yml edits (whoever may reload configs). */
    static final String PERM_CONFIG_ALERT = "skyblock.admin.reload";

    private static final String[] REASON_HINTS = {"Hacking", "Toxicity", "Spam", "Advertising", "Exploiting", "-s"};

    private final JavaPlugin plugin;
    private final Core core;
    private final CommandService commands;
    private final ModuleMessages msg;
    private final ModerationConfig config = new ModerationConfig();
    private final Templates templates = new Templates();
    private PunishmentService service;
    private Executor main;
    private volatile List<String> bannedNames = List.of();
    private net.skybornmc.skyblock.common.util.Scheduler.Task bannedNamesTask;
    private final Map<UUID, List<PunishmentStore.Alt>> pendingAltAlerts = new ConcurrentHashMap<>();
    /** Pending suggestion ids for tab completion of /warns <player> apply|dismiss <id>. */
    private volatile List<String> pendingIds = List.of();
    /** Names of staff who ever punished (tab completion of /pstats). */
    private volatile List<String> staffNames = List.of();
    /** Problems of the last punishments.yml load (shown to staff who can reload when they join). */
    private volatile List<ConfigProblem> lastProblems = List.of();
    private volatile boolean lastLoadRejected;
    /** P3: evidence/ack/report link/Discord, the Discord sink, the suggestion API and their timers. */
    private PunishExtras extras;
    private WebhookDiscordSink discord;
    private volatile Map<String, String> webhooks = Map.of();
    private net.skybornmc.skyblock.common.util.Scheduler.Task ackTask;
    private net.skybornmc.skyblock.common.util.Scheduler.Task purgeTask;
    /** Note ids for tab completion of /pnote remove: per sender, the notes of the player they listed last. */
    private final Map<String, List<String>> noteIds = new ConcurrentHashMap<>();
    private volatile List<String> recentNoteIds = List.of();
    /** Names on the watchlist (tab completion of /watch remove). */
    private volatile List<String> watchedNames = List.of();
    /** P5: bulk punish (13.8) and appeals (13.10). */
    private BulkPunish bulk;
    private AppealService appeals;
    /** P6: raid lockdown (13.9) and the suggestion API for /psuggest (13.3). */
    private LockdownService lockdown;
    private ApiSuggestions apiSuggestions;
    /** P7: economy and item sanctions (13.1). */
    private Sanctions sanctions;
    /** P8: notification settings per staff member (14.9). */
    private StaffNotify notify;
    /** P9: guard + rollback (14.1), false reports (14.5), data deletion (14.10). */
    private GuardService guard;
    private RollbackService rollback;
    private PurgeService purge;
    /** P10: monthly report (14.8) and the "Honorable" clean-streak title (14.11). */
    private ReportService reports;
    private CleanStreakService cleanStreak;
    /** False after disable: the dupe detector keeps its handlers, this stops ours. */
    private volatile boolean enabled;
    private boolean dupeHooked;

    public ModerationModule(Core core) {
        this.core = core;
        this.plugin = core.plugin();
        this.commands = core.commands();
        this.msg = core.messages(ID);
    }

    @Override
    public String id() {
        return ID;
    }

    Templates templates() {
        return templates;
    }

    PunishmentService service() {
        return service;
    }

    ModuleMessages messages() {
        return msg;
    }

    Core core() {
        return core;
    }

    BulkPunish bulk() {
        return bulk;
    }

    AppealService appeals() {
        return appeals;
    }

    Sanctions sanctions() {
        return sanctions;
    }

    @Override
    public void enable() throws Exception {
        new Migrations(core.database(), plugin.getLogger()).migrate(ID, getClass().getClassLoader());
        core.configs().load(config);
        main = new MainThread(plugin);
        service = new PunishmentService(core, msg, new PunishmentStore(core.database()), () -> config);
        service.warnRules(() -> templates.config().warnRules());
        service.extras(() -> templates.config().extras());
        service.probationReasons(templates::probation);
        service.denyOpener(this::openDeny);
        // P8: rule numbers (14.2) and notification settings (14.9)
        service.rules(() -> templates.config().rules());
        notify = new StaffNotify(core, new NotifyStore(core.database()), () -> templates.config().extras().notifications());
        service.notifier(notify);
        notify.start();
        // spec 13.12: every new row stores server id + scope; the scope filter follows punishments.yml (and reloads)
        service.store().network(() -> templates.config().extras().network());
        loadPunishments(null);
        // P3: Discord sink (off without a webhook secret), side effects, suggestion API
        loadWebhooks();
        discord = WebhookDiscordSink.create(() -> webhooks,
                channel -> (templates.config().extras().discord().enabled()
                        && channel.equals(templates.config().extras().discord().channel()))
                        // spec 13.10 step 1: the appeals channel has its own switch (off by default)
                        || (templates.config().extras().appeal().discordEnabled()
                        && channel.equals(templates.config().extras().appeal().discordChannel()))
                        // spec 14.9: a channel mapped to a notification type (extras.discord.channels), with the main switch
                        || (templates.config().extras().discord().enabled()
                        && NotifyRules.mapped(channel, templates.config().extras().notifications())),
                () -> templates.config().extras().discord(), plugin.getLogger());
        try {
            core.services().register(ID, net.skybornmc.skyblock.common.discord.DiscordSink.class, discord);
        } catch (IllegalStateException e) {
            plugin.getLogger().info("A Discord sink is already provided by another module; moderation uses that one.");
        }
        extras = new PunishExtras(core, msg, service.store(), () -> templates.config().extras(),
                () -> templates.config().warnRules(), () -> core.services().optional(net.skybornmc.skyblock.common.discord.DiscordSink.class));
        service.hooks(extras);
        bulk = new BulkPunish(this);
        appeals = new AppealService(this);
        service.appealHint(p -> {
            PunishConfig.Appeals a = templates.config().extras().appeal();
            return a.enabled() && a.types().contains(p.type()) && templates.appealable(p.template());
        });
        service.appealWait(this::earliestAppeal);
        apiSuggestions = new ApiSuggestions(core, service, templates, () -> templates.config().warnRules(), t -> reasonName(null, t));
        core.services().register(ID, net.skybornmc.skyblock.common.moderation.SuggestionService.class, apiSuggestions);
        lockdown = new LockdownService(core, msg, service, extras, () -> templates.config().extras());
        // P7, spec 13.1: sanctions (the economy, auction, trade and bank check them through SanctionService)
        sanctions = new Sanctions(core, msg, new SanctionStore(core.database()), () -> templates.config().extras());
        sanctions.start();
        sanctions.notified(p -> service.wants(p, NotifyRules.Type.PUNISHMENTS));
        core.services().register(ID, net.skybornmc.skyblock.common.moderation.SanctionService.class, sanctions);
        // P9, spec 14.1: the guard (pipeline slot GUARD + sanctions) counts what each staff member applied
        guard = new GuardService(core, msg, new GuardStore(core.database()), () -> templates.config().extras().guard(),
                this::rankTiers);
        guard.wire(service, extras);
        guard.start();
        service.guard(guard);
        sanctions.guard(guard);
        // P10, spec 14.11: a new warn/mute/ban/kick hides an earned "Honorable" title
        cleanStreak = new CleanStreakService(core, msg, new CleanStreakStore(core.database()), () -> templates.config().extras());
        service.store().onApplied(p -> {
            UUID who = p.approvedBy() != null ? p.approvedBy() : p.issuer(); // an approved request counts for the approver
            guard.recorded(who, p.approvedBy() != null ? p.approvedByName() : p.issuerName(), Guard.Kind.of(p.type()),
                    System.currentTimeMillis());
            cleanStreak.punished(p);
        });
        rollback = new RollbackService(core, msg, service, sanctions, () -> templates.config().extras());
        // P10, spec 14.8: monthly report (scheduled run off the main thread, /preport)
        reports = new ReportService(core, msg, this, new ReportStore(core.database()), () -> templates.config().extras(),
                () -> core.services().optional(net.skybornmc.skyblock.common.discord.DiscordSink.class));
        purge = new PurgeService(core, msg, service, () -> templates.config().extras(), this::refreshAfterPurge);
        // P9, spec 14.5: the reports module asks this when a report is closed as invalid
        core.services().register(ID, net.skybornmc.skyblock.common.moderation.FalseReports.class,
                new FalseReportService(core, service, templates, () -> templates.config().extras(),
                        () -> templates.config().warnRules()));
        enabled = true;
        if (core.dupes() != null && !dupeHooked) {
            dupeHooked = true; // the detector has no removeHandler: hook once, gated by `enabled`
            core.dupes().addHandler(report -> {
                if (enabled) {
                    sanctions.onDupe(report, apiSuggestions);
                }
            });
        }
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        core.placeholders().registerText(ID, "muted", p -> p != null && service.mute(p.getUniqueId()).isPresent() ? "true" : "false");
        // spec 13.9: lockdown status for the staff HUD (empty for non-staff and when nothing is active)
        core.placeholders().registerText(ID, "lockdown", p -> lockdown.placeholder(p));
        registerCommands();
        // Mutes of players already online (reload) + banned-name cache for /unban tab completion.
        for (Player p : Bukkit.getOnlinePlayers()) {
            // on the main thread, so the "mute ended" timer (13.13) is scheduled too
            service.store().activeMute(p.getUniqueId(), ip(p)).thenAcceptAsync(m -> service.cacheMute(p.getUniqueId(), m.orElse(null)),
                    main);
        }
        bannedNamesTask = core.scheduler().asyncRepeat(20L, 600L, this::refreshBannedNames);
        ackTask = core.scheduler().repeat("moderation:ack", 20L, 20L, extras::tick);
        // spec 12.4: evidence older than the retention is deleted (at start, then every 6 hours)
        purgeTask = core.scheduler().asyncRepeat(200L, 20L * 60 * 60 * 6, () -> {
            extras.purgeEvidence();
            purgeLedgers(); // spec 14.10: purged ledgers past keep-ledger
        });
        lockdown.start();
        reports.start();
        cleanStreak.start();
        for (Player p : Bukkit.getOnlinePlayers()) {
            extras.joined(p);
            cleanStreak.joined(p);
        }
    }

    /** Webhook URLs from {@code secrets/discord.yml} (written on the server by apply-secrets.sh, never tracked). */
    private void loadWebhooks() {
        File file = new File(plugin.getDataFolder(), "secrets/discord.yml");
        if (!file.isFile()) {
            webhooks = Map.of();
            return;
        }
        org.bukkit.configuration.file.YamlConfiguration yaml = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(file);
        org.bukkit.configuration.ConfigurationSection section = yaml.getConfigurationSection("webhooks");
        Map<String, String> out = new java.util.HashMap<>();
        if (section != null) {
            for (String key : section.getKeys(false)) {
                String url = section.getString(key, "");
                if (url != null && !url.isBlank()) {
                    out.put(key, url.strip());
                }
            }
        }
        webhooks = Map.copyOf(out);
    }

    @Override
    public void reload() {
        core.configs().load(config);
        core.messages().load(ID);
        loadPunishments(null);
        loadWebhooks();
        if (cleanStreak != null) {
            cleanStreak.ensureTitle(); // extras.clean-streak.title may have changed
        }
    }

    @Override
    public void disable() {
        if (bannedNamesTask != null) {
            bannedNamesTask.cancel();
        }
        if (ackTask != null) {
            ackTask.cancel();
        }
        if (purgeTask != null) {
            purgeTask.cancel();
        }
        if (reports != null) {
            reports.stop();
        }
        if (cleanStreak != null) {
            cleanStreak.stop();
        }
        enabled = false;
        if (sanctions != null) {
            sanctions.stop();
        }
        if (lockdown != null) {
            lockdown.shutdown(); // spec 13.9: a lockdown never survives a restart (audited + Discord before the sink closes)
        }
        if (discord != null) {
            discord.close();
        }
        if (service != null) {
            service.close();
        }
        core.services().unregisterAll(ID);
        HandlerList.unregisterAll(this);
    }

    /** Icons for the config parser: real item ids only. */
    static final PunishConfigParser.Icons ICONS = new PunishConfigParser.Icons() {
        private volatile List<String> names;

        @Override
        public org.bukkit.Material resolve(String id) {
            org.bukkit.Material m = org.bukkit.Material.matchMaterial(id);
            return m == null || m.isLegacy() || !m.isItem() || m.isAir() ? null : m;
        }

        @Override
        public java.util.Collection<String> names() {
            if (names == null) {
                List<String> out = new ArrayList<>();
                for (org.bukkit.Material m : org.bukkit.Material.values()) {
                    if (!m.isLegacy() && m.isItem() && !m.isAir()) {
                        out.add(m.name());
                    }
                }
                names = List.copyOf(out);
            }
            return names;
        }
    };

    /**
     * Loads {@code punishments.yml} safely (spec §6.2): any error rejects the whole file, keeps the previous working
     * config (or, on first start, the bundled default), prints every problem with file and line to the console and tells
     * online staff who can reload. Never throws, never disables the module.
     *
     * @param extra also told (e.g. whoever ran the reload), may be {@code null}
     */
    void loadPunishments(CommandSender extra) {
        File file = new File(plugin.getDataFolder(), "punishments.yml");
        if (!file.exists()) {
            plugin.saveResource("punishments.yml", false);
        }
        PunishConfigParser.Result result;
        try {
            result = PunishConfigParser.parse(Files.readString(file.toPath(), StandardCharsets.UTF_8), ICONS);
        } catch (Exception e) {
            result = new PunishConfigParser.Result(null, List.of(new ConfigProblem("config.yaml-invalid", "", 0,
                    Map.of("detail", String.valueOf(e.getMessage())))), List.of());
        }
        boolean hadConfig = templates.config() != PunishConfig.EMPTY;
        for (ConfigProblem w : result.warnings()) {
            plugin.getLogger().warning(problemText(null, w));
        }
        if (templates.offer(result)) {
            lastProblems = result.warnings();
            lastLoadRejected = false;
            plugin.getLogger().info(service.text(null, "config.loaded", Msg.number("reasons", templates.templateIds().size()),
                    Msg.number("rules", result.config().warnRules().rules().size()),
                    Msg.p("mode", result.config().warnRules().mode().name().toLowerCase(Locale.ROOT))));
            return;
        }
        for (ConfigProblem e : result.errors()) {
            plugin.getLogger().warning(problemText(null, e));
        }
        String keptKey = hadConfig ? "config.rejected" : "config.rejected-default";
        if (!hadConfig) {
            templates.use(bundledDefault());
        }
        plugin.getLogger().warning(service.text(null, keptKey, Msg.number("count", result.errors().size())));
        lastProblems = result.errors();
        lastLoadRejected = true;
        alertStaff(extra, result.errors(), keptKey);
        core.audit().log(net.skybornmc.skyblock.common.audit.AuditCategory.CONFIG, "punishments_config_rejected",
                extra == null ? net.skybornmc.skyblock.common.audit.Actor.SYSTEM : net.skybornmc.skyblock.common.audit.Actor.of(extra),
                Map.of("errors", result.errors().size(), "first", problemText(null, result.errors().getFirst())));
    }

    /** The punishments.yml inside the jar (the build test guarantees it parses). */
    private PunishConfig bundledDefault() {
        try (java.io.InputStream in = plugin.getResource("punishments.yml")) {
            if (in != null) {
                PunishConfigParser.Result r = PunishConfigParser.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8), ICONS);
                if (r.ok()) {
                    return r.config();
                }
            }
        } catch (java.io.IOException e) {
            plugin.getLogger().log(Level.WARNING, "Could not read the bundled punishments.yml", e);
        }
        return PunishConfig.EMPTY;
    }

    private void alertStaff(CommandSender extra, List<ConfigProblem> problems, String keptKey) {
        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (staff.hasPermission(PERM_CONFIG_ALERT) || staff.equals(extra)) {
                sendProblems(staff, problems, keptKey);
            }
        }
        if (extra != null && !(extra instanceof Player)) {
            sendProblems(extra, problems, keptKey);
        }
    }

    private void sendProblems(CommandSender to, List<ConfigProblem> problems, String keptKey) {
        msg.send(to, keptKey, Msg.number("count", problems.size()));
        int shown = 0;
        for (ConfigProblem p : problems) {
            if (shown++ >= 8) {
                msg.send(to, "config.more", Msg.number("more", problems.size() - 8));
                break;
            }
            msg.send(to, "config.line", Msg.p("problem", problemText(to instanceof Player pl ? pl : null, p)));
        }
    }

    /** One problem as plain text: {@code punishments.yml line 42: ...}. */
    String problemText(Player viewer, ConfigProblem p) {
        List<TagResolver> tags = new ArrayList<>();
        p.args().forEach((k, v) -> tags.add(Msg.p(k, v)));
        tags.add(Msg.p("file", "punishments.yml"));
        tags.add(Msg.p("line", p.line() > 0 ? String.valueOf(p.line()) : "?"));
        tags.add(Msg.p("path", p.path()));
        String suggestion = p.arg("suggestion");
        tags.add(Msg.p("hint", suggestion.isEmpty() ? "" : service.text(viewer, "config.hint", Msg.p("suggestion", suggestion))));
        return service.text(viewer, p.key(), tags.toArray(TagResolver[]::new));
    }

    private void refreshBannedNames() {
        service.store().bannedNames().thenAccept(names -> bannedNames = List.copyOf(names))
                .exceptionally(e -> null);
        service.store().pendingIds().thenAccept(ids -> pendingIds = List.copyOf(ids)).exceptionally(e -> null);
        service.store().staffNames().thenAccept(names -> staffNames = List.copyOf(names)).exceptionally(e -> null);
        service.store().recentNoteIds(null).thenAccept(ids -> recentNoteIds = List.copyOf(ids)).exceptionally(e -> null);
        refreshWatched();
    }

    /** The rank tiers of moderation.yml → limits ({@code helper=1d} → {@code helper}), for the guard tiers (14.1). */
    private List<String> rankTiers() {
        List<String> out = new ArrayList<>();
        for (String entry : config.maxDurations) {
            String name = entry.split("=", 2)[0].strip();
            if (!name.isEmpty()) {
                out.add(name);
            }
        }
        return out;
    }

    /** Spec 14.10: deletes purged ledger rows older than keep-ledger (async, with the evidence purge). */
    private void purgeLedgers() {
        PurgeRules.Settings s = templates.config().extras().purge();
        service.store().purgeLedgers(s, System.currentTimeMillis()).thenAccept(n -> {
            if (n > 0) {
                core.audit().log(net.skybornmc.skyblock.common.audit.AuditCategory.PUNISHMENT, "purge_ledger_expired",
                        net.skybornmc.skyblock.common.audit.Actor.system("purge"), Map.of("rows", n,
                                "keep_ledger", PunishConfig.text(s.keepLedger())));
            }
        }).exceptionally(e -> {
            plugin.getLogger().log(Level.WARNING, "Purged ledger cleanup failed", e);
            return null;
        });
    }

    /** After a purge: names and ids in the tab-completion caches are refreshed. */
    private void refreshAfterPurge() {
        noteIds.clear();
        core.scheduler().async(this::refreshBannedNames);
    }

    private void refreshWatched() {
        service.store().watchlist(System.currentTimeMillis())
                .thenAccept(list -> watchedNames = list.stream().map(Watchlist.Entry::targetName).distinct().toList())
                .exceptionally(e -> null);
    }

    private static String ip(Player p) {
        return p.getAddress() == null ? null : p.getAddress().getAddress().getHostAddress();
    }

    private CommandFailure fail(String key, TagResolver... placeholders) {
        return new CommandFailure(ID, key, placeholders);
    }

    // ================================================================== commands

    private void registerCommands() {
        Arg<String> player = Args.offlinePlayer("player");
        Arg<Duration> duration = Args.duration("duration");
        Arg<String> reason = Args.text("reason", REASON_HINTS);
        Arg<String> pardonReason = Args.text("reason", "Appeal accepted", "Mistake", "Served time");

        commands.register(CommandSpec.command("ban")
                .description("Ban a player permanently (-s silent, -ip IP-wide)").category("Staff").permission(PERM_BAN)
                .arg(player).optional(reason).example("ban Steve Hacking")
                .executes(ctx -> direct(ctx.sender(), Punishment.Type.BAN, ctx.get(player), null, ctx.getOr(reason, ""))));
        commands.register(CommandSpec.command("tempban")
                .description("Ban a player for a time").category("Staff").permission(PERM_BAN)
                .arg(player).arg(duration).optional(reason).example("tempban Steve 7d X-ray")
                .executes(ctx -> direct(ctx.sender(), Punishment.Type.BAN, ctx.get(player), ctx.get(duration), ctx.getOr(reason, ""))));
        Arg<String> ipTarget = Arg.word("player", (sender, raw) -> raw, Suggest.bySender(s -> {
            List<String> names = new ArrayList<>();
            Bukkit.getOnlinePlayers().forEach(p -> names.add(p.getName()));
            names.addAll(net.skybornmc.skyblock.common.command.OfflineNames.names());
            return names;
        }));
        commands.register(CommandSpec.command("ipban")
                .description("Ban a player and their IP address (or an IP)").category("Staff").permission(PERM_IPBAN)
                .aliases("ban-ip", "banip").arg(ipTarget).optional(reason).example("ipban Steve Ban evasion")
                .executes(ctx -> ipban(ctx.sender(), ctx.get(ipTarget), ctx.getOr(reason, ""))));
        commands.register(CommandSpec.command("mute")
                .description("Mute a player permanently").category("Staff").permission(PERM_MUTE)
                .arg(player).optional(reason).example("mute Steve Toxicity")
                .executes(ctx -> direct(ctx.sender(), Punishment.Type.MUTE, ctx.get(player), null, ctx.getOr(reason, ""))));
        commands.register(CommandSpec.command("tempmute")
                .description("Mute a player for a time").category("Staff").permission(PERM_MUTE)
                .arg(player).arg(duration).optional(reason).example("tempmute Steve 1h Spam")
                .executes(ctx -> direct(ctx.sender(), Punishment.Type.MUTE, ctx.get(player), ctx.get(duration), ctx.getOr(reason, ""))));
        commands.register(CommandSpec.command("warn")
                .description("Warn a player").category("Staff").permission(PERM_WARN)
                .arg(player).arg(reason).example("warn Steve Caps spam")
                .executes(ctx -> direct(ctx.sender(), Punishment.Type.WARN, ctx.get(player), null, ctx.get(reason))));
        Arg<Player> online = Args.onlinePlayer("player");
        commands.register(CommandSpec.command("kick")
                .description("Kick a player").category("Staff").permission(PERM_KICK)
                .arg(online).optional(reason).example("kick Steve AFK farming")
                .executes(ctx -> direct(ctx.sender(), Punishment.Type.KICK, ctx.get(online).getName(), null, ctx.getOr(reason, ""))));

        Arg<String> unbanTarget = Arg.word("player", (sender, raw) -> raw, Suggest.of(() -> bannedNames));
        commands.register(CommandSpec.command("unban")
                .description("Unban a player (or an IP address); a reason is required").category("Staff").permission(PERM_UNBAN)
                .aliases("pardon", "pardon-ip", "unbanip").arg(unbanTarget).arg(pardonReason).example("unban Steve Appeal accepted")
                .executes(ctx -> unban(ctx.sender(), ctx.get(unbanTarget), ctx.get(pardonReason))));
        commands.register(CommandSpec.command("unmute")
                .description("Unmute a player; a reason is required").category("Staff").permission(PERM_UNMUTE)
                .arg(player).arg(pardonReason).example("unmute Steve Mistake")
                .executes(ctx -> pardonPlayer(ctx.sender(), Punishment.Type.MUTE, ctx.get(player), ctx.get(pardonReason))));
        commands.register(CommandSpec.command("unwarn")
                .description("Remove a player's latest warning; a reason is required").category("Staff").permission(PERM_UNWARN)
                .arg(player).arg(pardonReason).example("unwarn Steve Mistake")
                .executes(ctx -> pardonPlayer(ctx.sender(), Punishment.Type.WARN, ctx.get(player), ctx.get(pardonReason))));

        Arg<String> punishTarget = player;
        // A reason id, or a flag right away (/p Steve --evidence 123 opens the menu with that log line as evidence)
        Arg<String> template = Arg.word("reason", (sender, raw) -> {
            if (raw.startsWith("-")) {
                return raw;
            }
            for (String id : templates.reasonIds()) {
                if (id.equalsIgnoreCase(raw)) {
                    return id;
                }
            }
            throw CommandFailure.of("invalid-choice", Msg.p("input", raw), Msg.p("choices", String.join(", ", templates.reasonIds())));
        }, Suggest.bySender(sender -> {
            List<String> out = new ArrayList<>(templates.reasonIds());
            out.add(LogEvidence.FLAG);
            return out;
        }));
        Arg<String> flags = Arg.greedy("flags", (sender, raw) -> raw.strip(), (ctx, builder) -> {
            Flags.suggestions(builder.getRemaining(), Flags.allowed(ctx.getSource().getSender()::hasPermission))
                    .forEach(builder::suggest);
            return builder.buildFuture();
        });
        commands.register(CommandSpec.command("punish")
                .description("Punishment menu for a player, or apply a reason directly (-s silent, -ip IP-wide, -force, --evidence <log id>)")
                .category("Staff").permission(PERM_GUI).aliases("p").arg(punishTarget).optional(template).optional(flags)
                .example("p Steve toxicity")
                .executes(ctx -> punishCommand(ctx.sender(), ctx.get(punishTarget), ctx.getOr(template, null), ctx.getOr(flags, ""))));

        Arg<String> warnsTarget = player;
        Arg<String> warnsAction = Args.oneOf("action", () -> List.of("apply", "dismiss"));
        Arg<String> suggestionId = Arg.word("id", (sender, raw) -> raw, Suggest.of(() -> pendingIds));
        commands.register(CommandSpec.command("warns")
                .description("A player's warning counter, the automatic rules and waiting suggestions").category("Staff")
                .permission(PERM_HISTORY).arg(warnsTarget).optional(warnsAction).optional(suggestionId).example("warns Steve")
                .executes(ctx -> warns(ctx.sender(), ctx.get(warnsTarget), ctx.getOr(warnsAction, null), ctx.getOr(suggestionId, null))));

        Arg<String> historyTarget = player;
        Arg<String> mode = Args.oneOf("view", () -> List.of("chat", "menu"));
        commands.register(CommandSpec.command("history")
                .description("All punishments of a player (menu, or chat)").category("Staff").permission(PERM_HISTORY)
                .aliases("punishments").arg(historyTarget).optional(mode).example("history Steve")
                .executes(ctx -> history(ctx.sender(), ctx.get(historyTarget), ctx.getOr(mode, null))));
        Arg<String> statsWho = Arg.word("staff", (sender, raw) -> raw, Suggest.bySender(sender -> {
            List<String> out = new ArrayList<>(PunishStats.Period.ids());
            if (sender.hasPermission(PERM_STATS_OTHERS)) {
                out.add("top");
                out.addAll(staffNames);
            }
            return out;
        }));
        Arg<String> statsPeriod = Args.oneOf("period", PunishStats.Period::ids);
        commands.register(CommandSpec.command("pstats")
                .description("How many punishments you (or another staff member) gave: 24h, 7d, 30d or all; 'top' ranks all staff")
                .category("Staff").permission(PERM_STATS).aliases("mystats").optional(statsWho).optional(statsPeriod)
                .example("pstats 30d")
                .executes(ctx -> pstats(ctx.sender(), ctx.getOr(statsWho, null), ctx.getOr(statsPeriod, null))));

        // ---- P3: /pnote (12.5), /ack (12.6), /mypunishments (12.12)
        Arg<String> noteTarget = player;
        Arg<String> noteText = Args.text("text", "Watch for alt accounts", "Warned in private messages", "Asked about rules");
        Arg<String> noteId = Arg.word("id", (sender, raw) -> raw, Suggest.bySender(sender ->
                noteIds.getOrDefault(senderKey(sender), recentNoteIds)));
        commands.register(CommandSpec.command("pnote")
                .description("Private staff notes on a player: add one, list them, or remove one by id").category("Staff")
                .permission(PERM_NOTES).optional(noteTarget).optional(noteText).example("pnote Steve Watch for alt accounts")
                // The player is optional only because a required argument can't sit next to the "remove" sub-command.
                .executes(ctx -> {
                    if (!ctx.has(noteTarget)) {
                        throw fail("notes.usage");
                    }
                    pnote(ctx.sender(), ctx.get(noteTarget), ctx.getOr(noteText, null));
                })
                .sub(CommandSpec.sub("remove").arg(noteId).executes(ctx -> removeNote(ctx.sender(), ctx.get(noteId)))));
        commands.register(CommandSpec.command("ack")
                .description("Confirm you read your latest warning (same as clicking [I understand])").category("Community")
                .permission(PERM_VIEW_OWN).playerOnly().example("ack")
                .executes(ctx -> extras.acknowledge(ctx.player())));
        commands.register(CommandSpec.command("mypunishments")
                .description("Your own active mute, warnings and recent punishments").category("Community")
                .permission(PERM_VIEW_OWN).playerOnly().example("mypunishments")
                .executes(ctx -> myPunishments(ctx.player())));

        // ---- P4: /watch, /watchlist (13.5)
        Arg<String> watchTarget = player;
        Arg<String> watchReason = Args.text("reason", "Suspected macros", "Possible alt account", "Watch the chat");
        Arg<String> watchedName = Arg.word("player", (sender, raw) -> raw, Suggest.of(() -> watchedNames));
        commands.register(CommandSpec.command("watch")
                .description("Watch a player: staff get one line when they join (the player is not told); /watch remove <player> stops it")
                .category("Staff").permission(PERM_WATCH).optional(watchTarget).optional(watchReason)
                .example("watch Steve Suspected macros")
                // The player is optional only because a required argument can't sit next to the "remove" sub-command.
                .executes(ctx -> {
                    if (!ctx.has(watchTarget)) {
                        throw fail("watch.usage");
                    }
                    watch(ctx.sender(), ctx.get(watchTarget), ctx.getOr(watchReason, null));
                })
                .sub(CommandSpec.sub("remove").arg(watchedName).executes(ctx -> unwatch(ctx.sender(), ctx.get(watchedName)))));
        commands.register(CommandSpec.command("watchlist")
                .description("Everyone on the watchlist: reason, who added them, when it ends (menu; chat from the console)")
                .category("Staff").permission(PERM_WATCH).example("watchlist")
                .executes(ctx -> watchlist(ctx.sender())));

        // ---- P5: /pbulk (13.8), /appeal and /appeals (13.10)
        Arg<String> bulkReason = Args.oneOf("reason", templates::reasonIds);
        Arg<String> bulkPlayers = Arg.greedy("players", (sender, raw) -> raw.strip(), (ctx, builder) -> {
            List<String> known = new ArrayList<>();
            Bukkit.getOnlinePlayers().forEach(p -> known.add(p.getName()));
            known.addAll(net.skybornmc.skyblock.common.command.OfflineNames.names());
            BulkSelection.suggestions(builder.getRemaining(), known.stream().distinct().toList(),
                    templates.config().extras().bulk().max()).forEach(builder::suggest);
            return builder.buildFuture();
        });
        commands.register(CommandSpec.command("pbulk")
                .description("Punish several players for one reason (each gets their own next step); always asks to confirm")
                .category("Staff").permission(PERM_BULK).playerOnly().arg(bulkReason).arg(bulkPlayers)
                .example("pbulk spam Steve Alex Notch")
                .executes(ctx -> pbulk(ctx.player(), ctx.get(bulkReason), ctx.get(bulkPlayers))));
        Arg<String> appealText = Arg.greedy("text", (sender, raw) -> raw.strip(), Suggest.bySender(sender -> {
            List<String> out = new ArrayList<>();
            if (sender instanceof Player p) {
                service.mute(p.getUniqueId()).ifPresent(m -> out.add(String.valueOf(m.id())));
            }
            out.add("I was not spamming, I answered a question");
            return out;
        }));
        commands.register(CommandSpec.command("appeal")
                .description("Appeal your mute or ban: /appeal shows what you can appeal, /appeal <text> sends it (/appeal <id> <text> for another one)")
                .category("Community").permission(PERM_APPEAL).playerOnly().optional(appealText)
                .example("appeal I was not spamming, I answered a question")
                .executes(ctx -> {
                    if (ctx.has(appealText)) {
                        appeals.submit(ctx.player(), ctx.get(appealText));
                    } else {
                        appeals.status(ctx.player());
                    }
                }));
        commands.register(CommandSpec.command("appeals")
                .description("Open appeals, oldest first: accept, reduce or deny them (menu; chat list from the console)")
                .category("Staff").permission(PERM_APPEALS).example("appeals")
                .executes(ctx -> appeals.queue(ctx.sender())));

        // ---- P6: /lockdown (13.9), /psuggest (13.3)
        Arg<Duration> chatLength = lockdownLength(Lockdown.Kind.CHAT);
        Arg<Duration> joinLength = lockdownLength(Lockdown.Kind.JOIN);
        commands.register(CommandSpec.command("lockdown")
                .description("Raid lockdown for new players only: chat [time] (no chat, /msg, signs, books), join [time] (accounts that never joined can't join), off, status")
                .category("Staff").permission(LockdownService.PERM_LOCKDOWN).example("lockdown chat 10m")
                .executes(ctx -> {
                    if (ctx.sender() instanceof Player p) {
                        core.menus().open(p, new LockdownMenu(lockdown));
                    } else {
                        lockdown.status(ctx.sender());
                    }
                })
                .sub(CommandSpec.sub("chat").optional(chatLength)
                        .executes(ctx -> lockdownStart(ctx.sender(), Lockdown.Kind.CHAT, ctx.getOr(chatLength, null))))
                .sub(CommandSpec.sub("join").optional(joinLength)
                        .executes(ctx -> lockdownStart(ctx.sender(), Lockdown.Kind.JOIN, ctx.getOr(joinLength, null))))
                .sub(CommandSpec.sub("off").executes(ctx -> lockdown.end(ctx.sender(), null)))
                .sub(CommandSpec.sub("status").executes(ctx -> lockdown.status(ctx.sender()))));
        Arg<String> suggestTarget = player;
        Arg<String> suggestReason = Args.oneOf("reason", templates::reasonIds);
        Arg<String> suggestDetails = Args.text("details", "Grim: Reach vl 12", "Flying at spawn", "Autoclicker in the Depths");
        commands.register(CommandSpec.command("psuggest")
                .description("Suggest a punishment: staff get [Apply] [Dismiss] [Open /p]; never applies anything (console, GrimAC)")
                .category("Staff").permission(PERM_SUGGEST).arg(suggestTarget).arg(suggestReason).optional(suggestDetails)
                .example("psuggest Steve hacking Grim: Reach vl 12")
                .executes(ctx -> psuggest(ctx.sender(), ctx.get(suggestTarget), ctx.get(suggestReason), ctx.getOr(suggestDetails, ""))));

        // P7, spec 13.1/13.14: /psanction restore <id> (money removed or an item confiscated by a sanction)
        Arg<Long> sanctionId = Arg.word("id", (sender, raw) -> {
            try {
                long id = Long.parseLong(raw.startsWith("#") ? raw.substring(1) : raw);
                if (id <= 0) {
                    throw new NumberFormatException(raw);
                }
                return id;
            } catch (NumberFormatException e) {
                throw fail("sanctions.bad-id", Msg.p("input", raw));
            }
        }, Suggest.of(() -> sanctions.restorableIds()));
        commands.register(CommandSpec.command("psanction")
                .description("Sanctions: restore money or an item taken by a sanction (the rest is in /p → Sanctions)")
                .category("Staff").permission(Sanctions.PERM_RESTORE)
                .example("psanction restore 12")
                .executes(ctx -> msg.send(ctx.sender(), "sanctions.usage"))
                .sub(CommandSpec.sub("restore")
                        .description("Give back removed money or a confiscated item").permission(Sanctions.PERM_RESTORE)
                        .arg(sanctionId).example("psanction restore 12")
                        .executes(ctx -> sanctions.restore(ctx.sender(), ctx.get(sanctionId)))));

        // ---- P8, spec 14.9: /pnotify [type] [on|off] (own alert switches; menu without arguments)
        Arg<String> notifyType = Arg.word("type", (sender, raw) -> NotifyRules.Type.parse(raw).map(NotifyRules.Type::id)
                .orElseThrow(() -> CommandFailure.of("invalid-choice", Msg.p("input", raw),
                        Msg.p("choices", String.join(", ", NotifyRules.Type.ids())))),
                Suggest.bySender(sender -> java.util.Arrays.stream(NotifyRules.Type.values())
                        .filter(t -> sender.hasPermission(t.permission())).map(NotifyRules.Type::id).toList()));
        Arg<String> notifyState = Args.oneOf("state", () -> List.of("on", "off"));
        commands.register(CommandSpec.command("pnotify")
                .description("Your own staff alerts: switch types on or off (menu without arguments); only types your rank gets")
                .category("Staff").permission(PERM_NOTIFY).playerOnly().optional(notifyType).optional(notifyState)
                .example("pnotify watchlist off")
                .executes(ctx -> pnotify(ctx.player(), ctx.getOr(notifyType, null), ctx.getOr(notifyState, null))));

        // ---- P9, spec 14.1: /pguard allow|lock|unlock|status, /prollback <staff> <period> [confirm]; 14.10: /ppurge
        Arg<String> guardAllowWho = staffArg("staff");
        Arg<String> guardLockWho = staffArg("staff");
        Arg<String> guardUnlockWho = Arg.word("staff", (sender, raw) -> raw, Suggest.of(() -> guard.entryNames()));
        Arg<Duration> guardAllowFor = Arg.word("duration", (sender, raw) -> {
            try {
                Duration d = net.skybornmc.skyblock.common.command.Durations.parse(raw);
                if (d.isZero() || d.isNegative() || d.compareTo(Duration.ofDays(1)) > 0) {
                    throw fail("guard.bad-length", Msg.p("input", raw));
                }
                return d;
            } catch (IllegalArgumentException e) {
                throw CommandFailure.of("invalid-duration", Msg.p("input", raw));
            }
        }, Suggest.of(() -> List.of("30m", "1h", "2h", "6h")));
        commands.register(CommandSpec.command("pguard")
                .description("Guard against rogue or hacked staff: allow <staff> [time] (lift a block), lock/unlock <staff>, status")
                .category("Staff").permission(GuardService.PERM_GUARD).example("pguard status")
                .executes(ctx -> guard.status(ctx.sender()))
                .sub(CommandSpec.sub("allow").arg(guardAllowWho).optional(guardAllowFor)
                        .description("Lift a block; limits only alert for this time (default 30m)")
                        .executes(ctx -> guard.allow(ctx.sender(), ctx.get(guardAllowWho), ctx.getOr(guardAllowFor, null))))
                .sub(CommandSpec.sub("lock").arg(guardLockWho)
                        .description("Refuse every punishment of this staff member at once, until unlock")
                        .executes(ctx -> guard.lock(ctx.sender(), ctx.get(guardLockWho))))
                .sub(CommandSpec.sub("unlock").arg(guardUnlockWho)
                        .description("Back to normal (clears a lock, a block or an allowance)")
                        .executes(ctx -> guard.unlock(ctx.sender(), ctx.get(guardUnlockWho))))
                .sub(CommandSpec.sub("status").description("Blocked, locked and allowed staff, and who is near a limit")
                        .executes(ctx -> guard.status(ctx.sender()))));
        Arg<String> rollbackWho = staffArg("staff");
        Arg<Duration> rollbackPeriod = Arg.word("period", (sender, raw) -> {
            try {
                return net.skybornmc.skyblock.common.command.Durations.parse(raw);
            } catch (IllegalArgumentException e) {
                throw CommandFailure.of("invalid-duration", Msg.p("input", raw));
            }
        }, Suggest.of(() -> rollback.periodHints()));
        Arg<String> rollbackConfirm = Args.oneOf("confirm", () -> List.of("confirm"));
        commands.register(CommandSpec.command("prollback")
                .description("Undo a staff member's punishments of the last <period> (15m to 7d) after abuse or a hacked account; shows a preview first")
                .category("Staff").permission(RollbackService.PERM).arg(rollbackWho).arg(rollbackPeriod).optional(rollbackConfirm)
                .example("prollback Alex 1h")
                .executes(ctx -> rollback.run(ctx.sender(), ctx.get(rollbackWho), ctx.get(rollbackPeriod), ctx.has(rollbackConfirm))));
        Arg<String> purgeWho = player;
        Arg<String> purgeAction = Args.oneOf("action", () -> List.of("preview", "confirm"));
        commands.register(CommandSpec.command("ppurge")
                .description("Delete a player's personal data on request: preview shows what is removed and kept, confirm (typed) does it")
                .category("Staff").permission(PurgeService.PERM).arg(purgeWho).optional(purgeAction)
                .example("ppurge Steve preview")
                .executes(ctx -> purge.run(ctx.sender(), ctx.get(purgeWho), "confirm".equals(ctx.getOr(purgeAction, "preview")))));

        // ---- P10, spec 14.8: /preport [month] (the menu, or the chat summary from the console)
        Arg<String> reportMonth = Arg.word("month", (sender, raw) -> raw, Suggest.of(() -> reports.monthHints()));
        commands.register(CommandSpec.command("preport")
                .description("The monthly punishment report (default: last month); staff names need skyblock.staff.punish.report.staff")
                .category("Staff").permission(ReportService.PERM).optional(reportMonth).example("preport 2026-10")
                .executes(ctx -> reports.command(ctx.sender(), ctx.getOr(reportMonth, null))));

        Arg<String> altsTarget = player;
        commands.register(CommandSpec.command("alts")
                .description("Accounts that share an IP address with a player").category("Staff").permission(PERM_ALTS)
                .arg(altsTarget).example("alts Steve")
                .executes(ctx -> alts(ctx.sender(), ctx.get(altsTarget))));
    }

    /** A staff member: online players and everyone who ever punished (also accounts that left). */
    private Arg<String> staffArg(String name) {
        return Arg.word(name, (sender, raw) -> raw, Suggest.of(() -> {
            List<String> out = new ArrayList<>();
            Bukkit.getOnlinePlayers().forEach(p -> {
                if (p.hasPermission(PERM_NOTIFY)) {
                    out.add(p.getName());
                }
            });
            out.addAll(staffNames);
            return out.stream().distinct().toList();
        }));
    }

    // ================================================================== /pnotify (P8, spec 14.9)

    private void pnotify(Player staff, String typeId, String state) {
        if (typeId == null) {
            core.menus().open(staff, new NotifyMenu(this, notify));
            return;
        }
        NotifyRules.Type type = NotifyRules.Type.parse(typeId).orElseThrow();
        String name = service.text(staff, "notify-settings.types." + type.id() + ".name");
        if (!staff.hasPermission(type.permission())) {
            throw fail("notify-settings.no-permission", Msg.p("type", name));
        }
        if (state == null) {
            msg.send(staff, notify.wants(staff, type) ? "notify-settings.state-on" : "notify-settings.state-off",
                    Msg.p("type", name), PunishmentService.pre("type_id", type.id()),
                    Msg.p("always", notify.settings().always().contains(type) ? service.text(staff, "notify-settings.always-tag") : ""));
            return;
        }
        setNotify(staff, type, state.equalsIgnoreCase("on"));
    }

    /** Switches one alert type for {@code staff} (command and menu). Main thread; replies when stored. */
    void setNotify(Player staff, NotifyRules.Type type, boolean on) {
        String name = service.text(staff, "notify-settings.types." + type.id() + ".name");
        notify.set(staff, type, on).thenAcceptAsync(result -> {
            switch (result) {
                case OK -> {
                    msg.send(staff, on ? "notify-settings.turned-on" : "notify-settings.turned-off", Msg.p("type", name));
                    core.sounds().play(staff, "success");
                }
                case ALWAYS_ON -> {
                    msg.send(staff, "notify-settings.always-on", Msg.p("type", name));
                    core.sounds().play(staff, "error");
                }
                case NO_PERMISSION -> {
                    msg.send(staff, "notify-settings.no-permission", Msg.p("type", name));
                    core.sounds().play(staff, "error");
                }
            }
        }, main).exceptionally(e -> failed(staff, e));
    }

    /** A lockdown length: tab completion up to the kind's max (default and max included). */
    private Arg<Duration> lockdownLength(Lockdown.Kind kind) {
        return Arg.word("duration", (sender, raw) -> {
            try {
                return net.skybornmc.skyblock.common.command.Durations.parse(raw);
            } catch (IllegalArgumentException e) {
                throw CommandFailure.of("invalid-duration", Msg.p("input", raw));
            }
        }, Suggest.of(() -> lockdown.lengthHints(kind)));
    }

    private void lockdownStart(CommandSender sender, Lockdown.Kind kind, Duration length) {
        String problem = lockdown.begin(sender, kind, length);
        if (problem != null) {
            lockdown.tellProblem(sender, kind, problem);
        }
    }

    /**
     * {@code /psuggest <player> <reason> [details]} (spec 13.3): a pending suggestion through the suggestion API (one per
     * player + reason; a repeat appends its details, max 5 lines). Never applies anything. Console (GrimAC) and staff.
     */
    private void psuggest(CommandSender sender, String name, String reasonId, String rawDetails) {
        String details = Notes.clean(rawDetails == null ? "" : rawDetails);
        String source = sender instanceof Player p ? p.getName() : "";
        resolve(name).thenCompose(found -> found.isEmpty()
                        ? CompletableFuture.completedFuture((Map.Entry<Found, net.skybornmc.skyblock.common.moderation.SuggestionService.Result>) null)
                        : apiSuggestions.suggest(found.get().uuid(), reasonId, source, details, true,
                                net.skybornmc.skyblock.common.audit.Actor.of(sender)).thenApply(r -> Map.entry(found.get(), r)))
                .thenAcceptAsync(res -> {
                    if (res == null) {
                        msg.send(sender, "error.unknown", Msg.p("player", name));
                        return;
                    }
                    Found who = res.getKey();
                    switch (res.getValue().status()) {
                        case CREATED -> msg.send(sender, "psuggest.created", Msg.p("player", who.name()),
                                Msg.p("reason", reasonId), Msg.p("sid", res.getValue().id()), PunishmentService.pre("target", who.name()));
                        case REFRESHED -> msg.send(sender, "psuggest.refreshed", Msg.p("player", who.name()),
                                Msg.p("reason", reasonId), Msg.p("sid", res.getValue().id()), PunishmentService.pre("target", who.name()));
                        case UNKNOWN_REASON -> msg.send(sender, "error.unknown-template", Msg.p("reason", reasonId));
                        case UNKNOWN_PLAYER -> msg.send(sender, "error.unknown", Msg.p("player", name));
                    }
                }, main).exceptionally(e -> failed(sender, e));
    }

    /**
     * Parsed reason with flags removed ({@code -s} silent, {@code -ip} IP-wide, {@code -force} skip the duplicate check,
     * {@code --evidence <log id>} a {@code /logs} chat line as evidence, spec 13.11). {@code badEvidence}: the flag without a
     * valid id.
     */
    record Reason(String text, boolean silent, boolean ipWide, boolean force, Long evidence, boolean badEvidence, boolean dry) {

        Reason(String text, boolean silent, boolean ipWide, boolean force) {
            this(text, silent, ipWide, force, null, false, false);
        }

        Reason(String text, boolean silent, boolean ipWide, boolean force, Long evidence, boolean badEvidence) {
            this(text, silent, ipWide, force, evidence, badEvidence, false);
        }

        static Reason parse(String raw) {
            boolean silent = false;
            boolean ip = false;
            boolean force = false;
            boolean dry = false;
            Long evidence = null;
            boolean bad = false;
            List<String> words = new ArrayList<>();
            String[] parts = raw.trim().split("\\s+");
            for (int i = 0; i < parts.length; i++) {
                String w = parts[i];
                if (w.equalsIgnoreCase("-s")) {
                    silent = true;
                } else if (w.equalsIgnoreCase("-ip")) {
                    ip = true;
                } else if (w.equalsIgnoreCase("-force")) {
                    force = true;
                } else if (w.equalsIgnoreCase(DryRun.FLAG)) {
                    dry = true; // spec 14.6: only /p <player> <reason> reads it
                } else if (w.equalsIgnoreCase(LogEvidence.FLAG)) {
                    Long id = i + 1 < parts.length ? LogEvidence.parseId(parts[i + 1]) : null;
                    if (id == null || (evidence != null && !evidence.equals(id))) {
                        bad = true;
                    } else {
                        evidence = id;
                        i++;
                    }
                } else if (!w.isEmpty()) {
                    words.add(w);
                }
            }
            return new Reason(String.join(" ", words), silent, ip, force, evidence, bad, dry);
        }
    }

    record Found(UUID uuid, String name) {
    }

    private CompletableFuture<Optional<Found>> resolve(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return CompletableFuture.completedFuture(Optional.of(new Found(online.getUniqueId(), online.getName())));
        }
        return core.players().offline(name).thenApply(s -> s.map(snap -> new Found(snap.uuid(), snap.name())));
    }

    /** Permission for the kind of punishment (ip-wide bans need the ipban permission). */
    static String permissionFor(Punishment.Type type, boolean ipWide) {
        return switch (type) {
            case BAN -> ipWide ? PERM_IPBAN : PERM_BAN;
            case MUTE -> PERM_MUTE;
            case WARN -> PERM_WARN;
            case KICK -> PERM_KICK;
            case ISLAND_KICK, ISLAND_BAN, COOP_REMOVE, COOP_BLOCK -> PERM_ISLAND;
            case REPORT_BLOCK -> PERM_MUTE; // P9, spec 14.5: a communication restriction, given by whoever may mute
        };
    }

    /**
     * Direct commands (/ban, /tempmute, /warn, /kick ...). Everything (permission, -s/-ip flags, length limit, exempt,
     * never shorten) is decided by the shared pipeline in {@link PunishmentService#issue}.
     */
    private void direct(CommandSender sender, Punishment.Type type, String targetName, Duration duration, String rawReason) {
        Reason reason = Reason.parse(rawReason);
        if (reason.evidence() != null || reason.badEvidence()) {
            throw fail("log-evidence.only-p");
        }
        if (type == Punishment.Type.KICK && Bukkit.getPlayerExact(targetName) == null) {
            throw CommandFailure.of("player-offline", Msg.p("input", targetName));
        }
        String text = reason.text().isEmpty() ? service.text(sender instanceof Player p ? p : null, "words.no-reason") : reason.text();
        boolean ipWide = reason.ipWide() && (type == Punishment.Type.BAN || type == Punishment.Type.MUTE);
        resolve(targetName).thenComposeAsync(found -> {
            if (found.isEmpty()) {
                msg.send(sender, "error.unknown", Msg.p("player", targetName));
                return CompletableFuture.completedFuture(null);
            }
            Player online = Bukkit.getPlayer(found.get().uuid());
            return service.issue(sender, new PunishmentService.Request(type, found.get().uuid(), found.get().name(), duration,
                    text, reason.silent(), ipWide, ipWide && online != null ? ip(online) : null, null, null));
        }, main).exceptionally(e -> failed(sender, e));
    }

    private <T> T failed(CommandSender sender, Throwable e) {
        Throwable cause = e instanceof java.util.concurrent.CompletionException && e.getCause() != null ? e.getCause() : e;
        if ("no-ip".equals(cause.getMessage())) {
            core.scheduler().ensureMain("moderation:error", () -> msg.send(sender, "error.no-known-ip"));
        } else {
            plugin.getLogger().log(Level.WARNING, "Punishment failed", cause);
            core.scheduler().ensureMain("moderation:error", () -> msg.send(sender, "error.failed"));
        }
        return null;
    }

    private void ipban(CommandSender sender, String target, String rawReason) {
        Reason reason = Reason.parse(rawReason);
        if (reason.evidence() != null || reason.badEvidence()) {
            throw fail("log-evidence.only-p");
        }
        String text = reason.text().isEmpty() ? service.text(sender instanceof Player p ? p : null, "words.no-reason") : reason.text();
        if (BanTargets.looksLikeIp(target)) {
            service.issue(sender, new PunishmentService.Request(Punishment.Type.BAN, null, target, null, text, reason.silent(),
                    true, target, null, null)).exceptionally(e -> failed(sender, e));
            return;
        }
        resolve(target).thenComposeAsync(found -> {
            if (found.isEmpty()) {
                msg.send(sender, "error.unknown", Msg.p("player", target));
                return CompletableFuture.completedFuture(null);
            }
            Player online = Bukkit.getPlayer(found.get().uuid());
            return service.issue(sender, new PunishmentService.Request(Punishment.Type.BAN, found.get().uuid(), found.get().name(),
                    null, text, reason.silent(), true, online == null ? null : ip(online), null, null));
        }, main).exceptionally(e -> failed(sender, e));
    }

    private void unban(CommandSender sender, String target, String reason) {
        if (BanTargets.looksLikeIp(target)) {
            service.pardon(sender, Punishment.Type.BAN, null, target, target, reason).thenAccept(list -> {
                if (list.isEmpty()) {
                    msg.send(sender, "error.not-banned", Msg.p("player", target));
                }
            });
            return;
        }
        pardonPlayer(sender, Punishment.Type.BAN, target, reason);
    }

    private void pardonPlayer(CommandSender sender, Punishment.Type type, String target, String reason) {
        resolve(target).thenComposeAsync(found -> {
            if (found.isEmpty()) {
                msg.send(sender, "error.unknown", Msg.p("player", target));
                return CompletableFuture.completedFuture(List.<Punishment>of());
            }
            return service.pardon(sender, type, found.get().uuid(), found.get().name(), null, reason).thenApply(list -> {
                if (list.isEmpty()) {
                    msg.send(sender, switch (type) {
                        case BAN -> "error.not-banned";
                        case MUTE -> "error.not-muted";
                        default -> "error.not-warned";
                    }, Msg.p("player", found.get().name()));
                }
                return list;
            });
        }, main).exceptionally(e -> {
            failed(sender, e);
            return List.of();
        });
    }

    // ================================================================== /punish, /history, /alts

    /** Loads a target with history and warn-rule suggestions (off the main thread) for menus and /warns. */
    CompletableFuture<Optional<PunishMenus.Target>> target(String name) {
        return resolve(name).thenCompose(found -> found.isEmpty()
                ? CompletableFuture.completedFuture(Optional.<PunishMenus.Target>empty())
                : target(found.get().uuid(), found.get().name()).thenApply(Optional::of));
    }

    CompletableFuture<PunishMenus.Target> target(UUID uuid, String name) {
        CompletableFuture<List<Punishment>> history = service.store().history(uuid);
        CompletableFuture<List<Suggestion>> suggestions = service.store().suggestions(uuid);
        CompletableFuture<List<Suggestion>> api = service.store().apiSuggestions(uuid);
        int shown = Math.max(1, templates.config().extras().notes().shown());
        CompletableFuture<List<Notes.Note>> notes = service.store().notes(uuid, shown);
        CompletableFuture<Integer> noteCount = service.store().noteCount(uuid);
        long now = System.currentTimeMillis();
        CompletableFuture<List<PunishRequest>> requests = service.store().pendingRequests(uuid, now);
        CompletableFuture<Optional<Watchlist.Entry>> watch = service.store().activeWatch(uuid, now);
        // P7: island context (spec 13.2, resolved on the main thread) and sanctions (13.1)
        CompletableFuture<IslandSteps.Context> island = service.islandPunish().context(uuid)
                .exceptionally(e -> IslandSteps.Context.UNAVAILABLE.withReports(service.islandPunish().reportBlocksAvailable()));
        CompletableFuture<List<Sanction>> sanctionList = sanctions.store().history(uuid);
        return CompletableFuture.allOf(history, suggestions, api, notes, noteCount, requests, watch, island, sanctionList).thenApply(v ->
                new PunishMenus.Target(uuid, name, history.join(), suggestions.join(), api.join(), notes.join(), noteCount.join(),
                        requests.join(), watch.join().orElse(null)).withP7(island.join(), sanctionList.join()));
    }

    /** Opens the single-screen /p menu for {@code viewer} (loads the target first). */
    void openPunishMenu(Player viewer, String name) {
        openPunishMenu(viewer, name, null);
    }

    /** Same, keeping a {@code /logs} chat line as the evidence of every punishment from the menu (spec 13.11). */
    void openPunishMenu(Player viewer, String name, LogEvidence.Anchor evidence) {
        target(name).thenAcceptAsync(t -> {
            if (t.isEmpty()) {
                msg.send(viewer, "error.unknown", Msg.p("player", name));
                return;
            }
            core.menus().open(viewer, new PunishMenus.PunishMenu(this, t.get().withEvidence(evidence)));
        }, main).exceptionally(e -> failed(viewer, e));
    }

    private void punishCommand(CommandSender sender, String name, String reasonWord, String rawFlags) {
        // The reason slot may already hold a flag (/p Steve --evidence 123): then there is no reason and the menu opens.
        String reasonId = reasonWord;
        String flags = rawFlags;
        if (reasonWord != null && reasonWord.startsWith("-")) {
            flags = reasonWord + " " + rawFlags;
            reasonId = null;
        }
        Reason parsed = Reason.parse(flags);
        if (parsed.badEvidence()) {
            throw fail("log-evidence.usage");
        }
        if (reasonId == null && !(sender instanceof Player)) {
            throw CommandFailure.of("players-only");
        }
        if (parsed.dry() && !sender.hasPermission(PERM_DRY)) {
            throw fail("dry-run.no-permission");
        }
        String id = reasonId;
        target(name).thenComposeAsync(t -> {
            if (t.isEmpty()) {
                msg.send(sender, "error.unknown", Msg.p("player", name));
                return CompletableFuture.completedFuture(Optional.<PunishMenus.Target>empty());
            }
            return withLogEvidence(sender, t.get(), parsed.evidence());
        }, main).thenAcceptAsync(found -> {
            if (found.isEmpty()) {
                return;
            }
            PunishMenus.Target t = found.get();
            if (id == null) {
                // spec 14.6: /p <player> -dry opens the menu with the Dry run toggle already on
                core.menus().open((Player) sender, new PunishMenus.PunishMenu(this, t, parsed.dry()));
                if (t.evidence() != null) {
                    msg.send(sender, "log-evidence.attached", Msg.p("player", t.name()), Msg.p("line", t.evidence().line()),
                            Msg.p("id", t.evidence().id()));
                }
                return;
            }
            Optional<Templates.Template> template = templates.template(id);
            if (template.isPresent()) {
                if (parsed.dry()) {
                    dryRunTemplate(sender, t, template.get(), parsed.silent(), parsed.ipWide());
                } else {
                    punishWithTemplate(sender, t, template.get(), parsed.silent(), parsed.ipWide(), parsed.force());
                }
                return;
            }
            Optional<PunishConfig.WarnButton> button = templates.warnButton(id);
            if (button.isPresent()) {
                if (parsed.dry()) {
                    dryRunWarnButton(sender, t, button.get(), parsed.silent());
                } else {
                    punishWithWarnButton(sender, t, button.get(), parsed.silent(), parsed.force());
                }
                return;
            }
            msg.send(sender, "error.unknown-template", Msg.p("reason", id));
        }, main).exceptionally(e -> failed(sender, e));
    }

    /**
     * spec 13.11: {@code t} with the {@code /logs} entry as evidence, after checking it (the logs module says whether the
     * sender may see it; it must be a chat line of this player). Empty (and the sender told why) when it can't be used.
     * Main thread in, main thread out.
     */
    CompletableFuture<Optional<PunishMenus.Target>> withLogEvidence(CommandSender sender, PunishMenus.Target t, Long entryId) {
        if (entryId == null) {
            return CompletableFuture.completedFuture(Optional.of(t));
        }
        Optional<net.skybornmc.skyblock.common.audit.LogLookup> lookup =
                core.services().optional(net.skybornmc.skyblock.common.audit.LogLookup.class);
        if (lookup.isEmpty()) {
            msg.send(sender, "log-evidence.unavailable");
            return CompletableFuture.completedFuture(Optional.empty());
        }
        return lookup.get().entry(sender, entryId).thenApplyAsync(entry -> {
            Optional<LogEvidence.Problem> problem = LogEvidence.check(entry.orElse(null), t.uuid());
            if (problem.isPresent()) {
                msg.send(sender, problem.get().key(), Msg.p("id", entryId), Msg.p("player", t.name()));
                if (sender instanceof Player p) {
                    core.sounds().play(p, "error");
                }
                return Optional.<PunishMenus.Target>empty();
            }
            return Optional.of(t.withEvidence(LogEvidence.anchor(entry.get(), Format.zone(), 60,
                    service.text(null, "evidence.ip-hidden"))));
        }, main);
    }

    /** Display name of a reason: messages reasons.<id>, else the legacy YAML name, else the id. */
    String reasonName(Player viewer, Templates.Template t) {
        if (msg.has("reasons." + t.id())) {
            return service.text(viewer, "reasons." + t.id());
        }
        return t.yamlName() != null ? t.yamlName() : t.id();
    }

    String categoryName(Player viewer, Templates.Category c) {
        if (msg.has("categories." + c.id())) {
            return service.text(viewer, "categories." + c.id());
        }
        return c.yamlName() != null ? c.yamlName() : c.id();
    }

    /** Name and stored reason text of a warn button (messages warn-buttons.<id>.name / .reason). */
    String warnButtonText(Player viewer, PunishConfig.WarnButton b, String part) {
        String key = "warn-buttons." + b.id() + "." + part;
        return msg.has(key) ? service.text(viewer, key) : b.id();
    }

    /**
     * Applies the ladder step for this offence (menu, More reasons, /p <player> <reason>). Completes on the main thread
     * with the result ({@code null} if it failed before the pipeline).
     */
    /** Owner answer 423: first moment {@code p} can be appealed (permanent bans wait), or {@code null} for any time. */
    Long earliestAppeal(Punishment p) {
        String category = p == null || p.template() == null ? null
                : templates.template(p.template()).map(Templates.Template::category).orElse(null);
        ModerationConfig c = config;
        return AppealRules.earliestAppeal(p, category, c.appealWaitPermBanDays, c.appealWaitCheatingDays, c.appealWaitCheatingCategories);
    }

    CompletableFuture<PunishmentStore.IssueResult> punishWithTemplate(CommandSender sender, PunishMenus.Target target,
                                                                     Templates.Template template, boolean silent, boolean ipFlag) {
        return punishWithTemplate(sender, target, template, silent, ipFlag, false);
    }

    /** Same, with {@code -force} (skip the duplicate check, needs skyblock.staff.punish.force). */
    CompletableFuture<PunishmentStore.IssueResult> punishWithTemplate(CommandSender sender, PunishMenus.Target target,
                                                                     Templates.Template template, boolean silent, boolean ipFlag,
                                                                     boolean force) {
        return punishWithTemplate(sender, target, template, silent, ipFlag, force, null);
    }

    /**
     * Same, as part of a bulk action ({@code group} = {@code bulk:<id>}, spec 13.8) or not ({@code null}). The target's
     * {@code /logs} evidence line (spec 13.11) goes with it.
     */
    CompletableFuture<PunishmentStore.IssueResult> punishWithTemplate(CommandSender sender, PunishMenus.Target target,
                                                                     Templates.Template template, boolean silent, boolean ipFlag,
                                                                     boolean force, String group) {
        // P7, spec 13.2: island steps this player can't get are replaced by the next possible step (staff are told)
        Templates.Template original = template;
        template = target.adapt(template);
        // spec 12.3: only offences younger than the reason's offence-expire count for the ladder
        int previous = target.previous(template.id(), template.cutoff(System.currentTimeMillis()));
        Templates.Step step = template.stepFor(previous);
        tellReplaced(sender, target, original, template, previous);
        boolean ipWide = step.ipWide() || (ipFlag && (step.type() == Punishment.Type.BAN || step.type() == Punishment.Type.MUTE));
        Player online = Bukkit.getPlayer(target.uuid());
        // spec 13.7: the pipeline's probation stage may raise the step (it sees the ladder and the probation end)
        return service.issue(sender, new PunishmentService.Request(step.type(), target.uuid(), target.name(), step.duration(),
                        reasonName(null, template), silent, ipWide, online != null && (ipWide || template.ladder().stream()
                                .anyMatch(Templates.Step::ipWide)) ? ip(online) : null,
                        template.id(), template.stepIndex(previous), null, force, step.weight(),
                        escalation(target, template, previous, ipFlag)).with(target.evidenceEntry(), group))
                .exceptionally(e -> failed(sender, e));
    }

    // ================================================================== dry run (P8, spec 14.6)

    /**
     * What clicking this reason would do right now, without doing it: the same adapted ladder, offence count, probation
     * and pipeline (permission, training, approval, length limit, duplicate, never shorten) as
     * {@link #punishWithTemplate}, on the target as loaded. Nothing is written except the training log.
     */
    void dryRunTemplate(CommandSender sender, PunishMenus.Target target, Templates.Template original, boolean silent,
                        boolean ipFlag) {
        Templates.Template template = target.adapt(original);
        int previous = target.previous(template.id(), template.cutoff(System.currentTimeMillis()));
        int normal = template.stepIndex(previous);
        Templates.Step step = template.ladder().get(normal);
        boolean ipWide = step.ipWide() || (ipFlag && (step.type() == Punishment.Type.BAN || step.type() == Punishment.Type.MUTE));
        Templates.Step asked = new Templates.Step(step.type(), step.duration(), ipWide, step.weight());
        PunishDecision.Verdict v = service.preview(sender, target.uuid(), target.name(), asked, silent,
                target.longestActive(Punishment.Type.MUTE), target.longestActive(Punishment.Type.BAN), template.id(),
                target.recent(template.id()), escalation(target, template, previous, ipFlag));
        tellReplaced(sender, target, original, template, previous);
        service.dryRun(sender, target.uuid(), target.name(), template.id(),
                reasonName(sender instanceof Player p ? p : null, template), DryRun.of(v, asked, normal, previous));
    }

    /** Dry run of a plain warn button (no ladder). */
    void dryRunWarnButton(CommandSender sender, PunishMenus.Target target, PunishConfig.WarnButton button, boolean silent) {
        Templates.Step warn = new Templates.Step(Punishment.Type.WARN, null, false, button.weight());
        PunishDecision.Verdict v = service.preview(sender, target.uuid(), target.name(), warn, silent, null, null, button.id(),
                target.recent(button.id()));
        service.dryRun(sender, target.uuid(), target.name(), button.id(),
                warnButtonText(sender instanceof Player p ? p : null, button, "name"), DryRun.of(v, warn, -1, -1));
    }

    /** Dry run of the Kick button. */
    void dryRunKick(Player sender, PunishMenus.Target target) {
        Templates.Step kick = new Templates.Step(Punishment.Type.KICK, null, false);
        PunishDecision.Verdict v = service.preview(sender, target.uuid(), target.name(), kick, false, null, null);
        service.dryRun(sender, target.uuid(), target.name(), null, service.text(sender, "punish.kick-reason"),
                DryRun.of(v, kick, -1, -1));
    }

    /** Tells staff when an island step of the ladder could not be used for this player and what is used instead (13.2). */
    private void tellReplaced(CommandSender sender, PunishMenus.Target target, Templates.Template original,
                              Templates.Template used, int previous) {
        if (original == used) {
            return;
        }
        int index = original.stepIndex(previous);
        IslandSteps.Why why = IslandSteps.replaced(original.ladder(), index, target.island());
        if (why == null) {
            return;
        }
        Player viewer = sender instanceof Player p ? p : null;
        msg.send(sender, "island-steps.replaced", Msg.p("player", target.name()),
                Msg.p("step", service.stepText(viewer, original.ladder().get(index))),
                Msg.p("used", service.stepText(viewer, used.ladder().get(index))),
                Msg.p("why", service.text(viewer, "island-steps.why." + why.name().toLowerCase(Locale.ROOT))));
    }

    /** The ladder + probation information of a reason for this target (spec 13.7), used by the click and the menu lore. */
    PunishDecision.Escalation escalation(PunishMenus.Target target, Templates.Template template, int previous, boolean ipFlag) {
        Probation.Settings ps = templates.config().extras().probation();
        Long until = template.probation() ? service.probationUntil(target.history()) : null;
        return new PunishDecision.Escalation(template.ladder(), template.stepIndex(previous), ipFlag, until,
                ps.enabled() ? ps.stepBonus() : 0);
    }

    /** One plain warn with the button's preset reason. */
    CompletableFuture<PunishmentStore.IssueResult> punishWithWarnButton(CommandSender sender, PunishMenus.Target target,
                                                                       PunishConfig.WarnButton button, boolean silent) {
        return punishWithWarnButton(sender, target, button, silent, false);
    }

    CompletableFuture<PunishmentStore.IssueResult> punishWithWarnButton(CommandSender sender, PunishMenus.Target target,
                                                                       PunishConfig.WarnButton button, boolean silent,
                                                                       boolean force) {
        return punishWithWarnButton(sender, target, button, silent, force, null);
    }

    /** Same, as part of a bulk action ({@code group}, spec 13.8) or not ({@code null}). */
    CompletableFuture<PunishmentStore.IssueResult> punishWithWarnButton(CommandSender sender, PunishMenus.Target target,
                                                                       PunishConfig.WarnButton button, boolean silent,
                                                                       boolean force, String group) {
        return service.issue(sender, new PunishmentService.Request(Punishment.Type.WARN, target.uuid(), target.name(), null,
                        warnButtonText(null, button, "reason"), silent, false, null, button.id(), null, force, button.weight())
                        .with(target.evidenceEntry(), group))
                .exceptionally(e -> failed(sender, e));
    }

    /** Kick from the /p menu with the default kick reason. */
    CompletableFuture<PunishmentStore.IssueResult> kickFromMenu(Player sender, PunishMenus.Target target) {
        return service.issue(sender, new PunishmentService.Request(Punishment.Type.KICK, target.uuid(), target.name(), null,
                service.text(null, "punish.kick-reason"), false, false, null, null, null).with(target.evidenceEntry(), null))
                .exceptionally(e -> failed(sender, e));
    }

    // ================================================================== /pbulk (spec 13.8)

    /** {@code /pbulk <reason> <players...>}: checks the list, previews every player and opens the confirm. */
    private void pbulk(Player staff, String reasonId, String rawPlayers) {
        int max = templates.config().extras().bulk().max();
        BulkSelection.Names names = BulkSelection.parse(rawPlayers, max);
        if (names.empty()) {
            throw fail("bulk.no-players");
        }
        if (names.tooMany()) {
            throw fail("bulk.too-many", Msg.number("count", names.names().size()), Msg.number("max", max));
        }
        if (!names.duplicates().isEmpty()) {
            msg.send(staff, "bulk.duplicates", Msg.p("names", String.join(", ", names.duplicates())));
        }
        bulk.plan(staff, reasonId, names.names()).whenComplete((plan, e) -> core.scheduler().ensureMain("moderation:bulk", () -> {
            if (e != null || plan == null) {
                if (e != null) {
                    plugin.getLogger().log(Level.WARNING, "Bulk preview failed", e);
                }
                msg.send(staff, e != null ? "error.failed" : "error.unknown-template", Msg.p("reason", reasonId));
                return;
            }
            if (staff.isOnline()) {
                bulk.confirm(staff, plan, null);
            }
        }));
    }

    // ================================================================== /warns

    private void warns(CommandSender sender, String name, String action, String id) {
        if (action != null) {
            if (!sender.hasPermission(PERM_GUI)) {
                throw fail("error.no-permission-suggestion");
            }
            long sid;
            try {
                sid = Long.parseLong(id == null ? "" : id.strip());
            } catch (NumberFormatException e) {
                throw fail("suggest.not-found");
            }
            if (action.equals("apply")) {
                service.applySuggestion(sender, sid).exceptionally(e -> failed(sender, e));
            } else {
                service.dismissSuggestion(sender, sid).exceptionally(e -> failed(sender, e));
            }
            return;
        }
        target(name).thenAcceptAsync(t -> {
            if (t.isEmpty()) {
                msg.send(sender, "error.unknown", Msg.p("player", name));
                return;
            }
            sendWarns(sender, t.get());
        }, main).exceptionally(e -> failed(sender, e));
    }

    private void sendWarns(CommandSender sender, PunishMenus.Target t) {
        Player viewer = sender instanceof Player p ? p : null;
        WarnRules.Settings rules = templates.config().warnRules();
        long now = System.currentTimeMillis();
        WarnRules.Counter counter = WarnRules.counter(t.history(), t.suggestions(), rules, now);
        msg.send(sender, "warns.header", Msg.p("player", t.name()), PunishmentService.pre("target", t.name()),
                Msg.p("count", counter.text()), Msg.p("next", counterLimit(counter, rules)));
        if (!rules.enabled()) {
            msg.send(sender, "warns.rules-off");
        } else {
            msg.send(sender, "warns.mode", Msg.p("mode", service.text(viewer, "warns.modes." + rules.mode().name().toLowerCase(Locale.ROOT))),
                    Msg.p("expire", rules.warnExpire() == null ? service.text(viewer, "words.never") : PunishConfig.text(rules.warnExpire())));
            for (WarnRules.Rule rule : rules.rules()) {
                msg.send(sender, "warns.rule", Msg.p("at", rule.at()), Msg.p("action", service.stepText(viewer, rule.action())));
            }
            if (rules.repeatEnabled()) {
                msg.send(sender, "warns.repeat-rule", Msg.p("window", PunishConfig.text(rules.repeatWindow())),
                        Msg.p("action", service.stepText(viewer, rules.repeatAction())));
            }
        }
        List<Punishment> counted = WarnRules.counted(t.history(), t.suggestions(), rules, now);
        if (counted.isEmpty()) {
            msg.send(sender, "warns.none");
        }
        for (Punishment w : counted) {
            msg.send(sender, "warns.line", Msg.p("id", w.id()), Msg.p("reason", w.reason()), Msg.p("staff", w.issuerName()),
                    Msg.p("date", when(w.created())), Msg.p("expires", rules.warnExpire() == null
                            ? service.text(viewer, "words.never") : when(w.created() + rules.warnExpire().toMillis())),
                    Msg.p("weight", Weights.text(w.weight())),
                    Msg.p("ack", service.text(viewer, w.acknowledged() ? "warns.ack-yes" : "warns.ack-no")));
        }
        if (counter.repeatUntil() != null) {
            msg.send(sender, "warns.repeat-open", Msg.p("until", when(counter.repeatUntil())),
                    Msg.p("action", service.stepText(viewer, rules.repeatAction())));
        }
        if (counter.pending() != null) {
            msg.send(sender, "warns.pending", service.suggestionParts(viewer, counter.pending()));
        }
        for (Suggestion api : t.apiSuggestions()) {
            msg.send(sender, "warns.api-pending", service.suggestionParts(viewer, api));
        }
        msg.send(sender, "warns.footer", PunishmentService.pre("target", t.name()));
    }

    /** "2" (the next rule) or "-" when no rule is left. */
    static String counterLimit(WarnRules.Counter counter, WarnRules.Settings rules) {
        if (counter.next() != null) {
            return String.valueOf(counter.next().at());
        }
        return rules.rules().isEmpty() ? "-" : String.valueOf(rules.rules().getFirst().at());
    }

    // ================================================================== /pstats (spec 12.10)

    private void pstats(CommandSender sender, String first, String second) {
        boolean others = sender.hasPermission(PERM_STATS_OTHERS); // checked on every call
        PunishConfig.Extras x = templates.config().extras();
        PunishStats.Query q = PunishStats.parse(first, second, sender.getName(), others, x.statsPeriod());
        if (!q.ok()) {
            throw fail(q.error(), Msg.p("periods", String.join(", ", PunishStats.Period.ids())));
        }
        Player viewer = sender instanceof Player p ? p : null;
        String periodText = service.text(viewer, "stats.periods." + q.period().id);
        switch (q.kind()) {
            case OWN -> {
                if (viewer == null) {
                    throw CommandFailure.of("players-only");
                }
                service.stats(viewer.getUniqueId(), q.period()).thenCombine(requestCounts(viewer.getUniqueId(), q.period()),
                                Map::entry)
                        .thenAcceptAsync(c -> sendStatsFull(sender, viewer.getUniqueId(), viewer.getName(), periodText,
                                c.getKey(), c.getValue(), q.period()), main)
                        .exceptionally(e -> failed(sender, e));
            }
            case OTHER -> service.store().staffByName(q.staff()).thenCompose(found -> found.isPresent()
                            ? CompletableFuture.completedFuture(found)
                            : core.players().offline(q.staff()).thenApply(o -> o.map(snap -> Map.entry(snap.uuid(), snap.name()))))
                    .thenCompose(found -> found.isEmpty() ? CompletableFuture.completedFuture(null)
                            : service.stats(found.get().getKey(), q.period())
                            .thenCombine(requestCounts(found.get().getKey(), q.period()), (c, rc) -> new StaffStats(found.get().getKey(),
                                    found.get().getValue(), c, rc)))
                    .thenAcceptAsync(result -> {
                        if (result == null) {
                            msg.send(sender, "error.unknown", Msg.p("player", q.staff()));
                            return;
                        }
                        sendStatsFull(sender, result.id(), result.name(), periodText, result.counts(), result.requests(), q.period());
                    }, main).exceptionally(e -> failed(sender, e));
            case TOP -> service.statsTop(q.period()).thenAcceptAsync(list -> {
                msg.send(sender, "stats.top-header", Msg.p("period", periodText), Msg.number("count", list.size()));
                if (list.isEmpty()) {
                    msg.send(sender, "stats.top-empty");
                }
                int place = 1;
                for (PunishStats.Ranked r : list) {
                    msg.send(sender, "stats.top-line", Msg.number("place", place++), Msg.p("staff", r.name()),
                            PunishmentService.pre("target", r.name()), Msg.number("given", r.counts().given()),
                            Msg.number("reversed", r.counts().reversed()), Msg.number("percent", r.counts().reversedPercent()),
                            Msg.number("net", r.counts().net()));
                }
            }, main).exceptionally(e -> failed(sender, e));
        }
        if (q.kind() != PunishStats.Kind.OWN) {
            core.audit().log(net.skybornmc.skyblock.common.audit.AuditCategory.STAFF, "pstats_view",
                    net.skybornmc.skyblock.common.audit.Actor.of(sender), null, q.kind() == PunishStats.Kind.TOP ? "top" : q.staff(),
                    null, null, Map.of("period", q.period().id));
        }
    }

    private record StaffStats(UUID id, String name, PunishStats.Counts counts, PunishmentStore.RequestCounts requests) {
    }

    /** Approval request numbers of a staff member for a period (spec 13.4: made / approved / denied). */
    private CompletableFuture<PunishmentStore.RequestCounts> requestCounts(UUID staff, PunishStats.Period period) {
        long now = System.currentTimeMillis();
        return service.store().requestCounts(staff, period.start(now, templates.config().extras().statsZone()), now)
                .exceptionally(e -> PunishmentStore.RequestCounts.NONE);
    }

    /** Appeal numbers of a staff member for a period (spec 13.10: handled appeals, own punishments reversed by appeal). */
    private CompletableFuture<PunishmentStore.AppealCounts> appealCounts(UUID staff, PunishStats.Period period) {
        long now = System.currentTimeMillis();
        return service.store().appealCounts(staff, period.start(now, templates.config().extras().statsZone()))
                .exceptionally(e -> PunishmentStore.AppealCounts.NONE);
    }

    /** /pstats for one staff member, with request (13.4) and appeal (13.10) lines. Main thread. */
    private void sendStatsFull(CommandSender sender, UUID staffId, String staff, String period, PunishStats.Counts c,
                               PunishmentStore.RequestCounts rc, PunishStats.Period p) {
        long now = System.currentTimeMillis();
        CompletableFuture<Long> given = sanctions.store().givenBy(staffId, p.start(now, templates.config().extras().statsZone()), now + 1)
                .exceptionally(e -> 0L);
        // P8, spec 14.6: dry runs (training log); mentors see a trainee's practice next to their requests
        CompletableFuture<Long> dry = notify.store().dryRunCount(staffId, p.start(now, templates.config().extras().statsZone()))
                .exceptionally(e -> 0L);
        appealCounts(staffId, p).thenCombine(given, Map::entry).thenCombine(dry, Map::entry).thenAcceptAsync(all -> {
            Map.Entry<PunishmentStore.AppealCounts, Long> both = all.getKey();
            PunishmentStore.AppealCounts ac = both.getKey();
            sendStats(sender, staff, period, c, rc);
            Player online = Bukkit.getPlayer(staffId);
            boolean isTrainee = online != null && PunishmentService.trainee(online);
            if (isTrainee || all.getValue() > 0) {
                msg.send(sender, isTrainee ? "stats.training-trainee" : "stats.training", Msg.number("dry", all.getValue()),
                        Msg.number("made", rc.made()), Msg.number("approved", rc.approved()), Msg.number("denied", rc.denied()));
            }
            // P7: island punishments (13.2) and sanctions (13.1)
            msg.send(sender, "stats.sanctions", Msg.number("island", c.island()), Msg.number("sanctions", both.getValue()));
            if (ac.handled() + ac.reversedByAppeal() > 0) {
                msg.send(sender, "stats.appeals", Msg.number("accepted", ac.accepted()), Msg.number("reduced", ac.reduced()),
                        Msg.number("denied", ac.denied()), Msg.number("reversed", ac.reversedByAppeal()));
            }
        }, main).exceptionally(e -> failed(sender, e));
    }

    private void sendStats(CommandSender sender, String staff, String period, PunishStats.Counts c,
                           PunishmentStore.RequestCounts rc) {
        sendStats(sender, staff, period, c);
        msg.send(sender, "stats.requests", Msg.number("made", rc.made()), Msg.number("approved", rc.approved()),
                Msg.number("denied", rc.denied()), Msg.number("waiting", rc.waiting()), Msg.number("expired", rc.expired()));
        if (rc.handledApproved() + rc.handledDenied() > 0) {
            msg.send(sender, "stats.approvals", Msg.number("approved", rc.handledApproved()),
                    Msg.number("denied", rc.handledDenied()));
        }
    }

    private void sendStats(CommandSender sender, String staff, String period, PunishStats.Counts c) {
        msg.send(sender, "stats.header", Msg.p("staff", staff), Msg.p("period", period));
        msg.send(sender, "stats.given", Msg.number("warns", c.warns()), Msg.number("mutes", c.mutes()),
                Msg.number("bans", c.bans()), Msg.number("kicks", c.kicks()), Msg.number("given", c.given()));
        msg.send(sender, "stats.reversed", Msg.number("reversed", c.reversed()), Msg.number("percent", c.reversedPercent()),
                Msg.number("net", c.net()));
        if (c.rolledBack() > 0) {
            // P9, spec 14.1: reversed by /prollback (also counted in "reversed"); never offences afterwards
            msg.send(sender, "stats.rolled-back", Msg.number("count", c.rolledBack()));
        }
        if (c.reportBlocks() > 0) {
            msg.send(sender, "stats.report-blocks", Msg.number("count", c.reportBlocks())); // P9, spec 14.5
        }
        msg.send(sender, "stats.suggestions", Msg.number("applied", c.fromSuggestions()));
    }

    private void history(CommandSender sender, String name, String mode) {
        boolean chat = "chat".equals(mode) || !(sender instanceof Player);
        target(name).thenAcceptAsync(t -> {
            if (t.isEmpty()) {
                msg.send(sender, "error.unknown", Msg.p("player", name));
                return;
            }
            if (!chat) {
                core.menus().open((Player) sender, new PunishMenus.HistoryMenu(t.get(), this));
                return;
            }
            sendHistory(sender, t.get());
        }, main).exceptionally(e -> failed(sender, e));
        core.audit().log(net.skybornmc.skyblock.common.audit.AuditCategory.STAFF, "history_view",
                net.skybornmc.skyblock.common.audit.Actor.of(sender), null, name, null, null, Map.of());
    }

    /** P7, spec 13.1: the player's sanctions under their punishments in /history (chat). */
    private void sendSanctionHistory(CommandSender sender, PunishMenus.Target t) {
        if (t.sanctions().isEmpty()) {
            return;
        }
        Player viewer = sender instanceof Player p ? p : null;
        msg.send(sender, "sanctions.history.header", Msg.p("player", t.name()), Msg.number("total", t.sanctions().size()));
        for (Sanction s : t.sanctions().stream().limit(config.historyPageSize).toList()) {
            msg.send(sender, "sanctions.history.line", sanctions.parts(viewer, s));
        }
    }

    private void sendHistory(CommandSender sender, PunishMenus.Target t) {
        sendHistoryPunishments(sender, t);
        sendSanctionHistory(sender, t);
    }

    private void sendHistoryPunishments(CommandSender sender, PunishMenus.Target t) {
        Player viewer = sender instanceof Player p ? p : null;
        msg.send(sender, "history.header", Msg.p("player", t.name()), Msg.number("total", t.history().size()),
                Msg.number("bans", t.count(Punishment.Type.BAN)), Msg.number("mutes", t.count(Punishment.Type.MUTE)),
                Msg.number("warns", t.count(Punishment.Type.WARN)), Msg.number("kicks", t.count(Punishment.Type.KICK)),
                PunishmentService.pre("target", t.name()));
        if (t.history().isEmpty()) {
            msg.send(sender, "history.empty");
            return;
        }
        long now = System.currentTimeMillis();
        for (Punishment p : t.history().stream().limit(config.historyPageSize).toList()) {
            msg.send(sender, "history.line", service.parts(viewer, p), Msg.p("staff_real", p.issuerName()),
                    Msg.p("status", service.text(viewer, "status." + p.status(now).name().toLowerCase(Locale.ROOT))),
                    Msg.p("removed", p.removed() ? service.text(viewer, "words.removed-by") + " " + p.removedByName()
                            + ": " + p.removedReason() : ""));
        }
        if (t.history().size() > config.historyPageSize && viewer != null) {
            msg.send(sender, "history.more", Msg.number("more", t.history().size() - config.historyPageSize),
                    PunishmentService.pre("target", t.name()));
        }
    }

    private void alts(CommandSender sender, String name) {
        resolve(name).thenComposeAsync(found -> {
            if (found.isEmpty()) {
                msg.send(sender, "error.unknown", Msg.p("player", name));
                return CompletableFuture.completedFuture(null);
            }
            return service.store().alts(found.get().uuid(), null).thenAcceptAsync(list -> {
                msg.send(sender, "alts.header", Msg.p("player", found.get().name()), Msg.number("count", list.size()));
                if (list.isEmpty()) {
                    msg.send(sender, "alts.none");
                }
                for (PunishmentStore.Alt alt : list) {
                    sendAltLine(sender, alt);
                }
            }, main);
        }, main).exceptionally(e -> failed(sender, e));
        core.audit().log(net.skybornmc.skyblock.common.audit.AuditCategory.STAFF, "alts_view",
                net.skybornmc.skyblock.common.audit.Actor.of(sender), null, name, null, null, Map.of());
    }

    private void sendAltLine(CommandSender sender, PunishmentStore.Alt alt) {
        String status = alt.ban() != null ? "alts.banned" : alt.mute() != null ? "alts.muted" : "alts.clean";
        msg.send(sender, "alts.line", Msg.p("player", alt.name()), PunishmentService.pre("target", alt.name()),
                Msg.mm("status", core.messages().raw(ID, status) == null ? "" : core.messages().raw(ID, status)),
                Msg.p("seen", Format.duration(Duration.ofMillis(Math.max(0, System.currentTimeMillis() - alt.lastSeen())))));
    }

    // ================================================================== enforcement

    /**
     * spec 13.9 join lockdown: runs before the player data is created (LOWEST), so "never joined before" is still true for
     * a brand-new account. Only looks the account up while a join lockdown is live. Fails open on a database error.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPreLoginLockdown(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED || !lockdown.joinLockActive()) {
            return;
        }
        boolean known;
        try {
            known = core.players().offline(event.getUniqueId()).get(5, java.util.concurrent.TimeUnit.SECONDS).isPresent();
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            plugin.getLogger().log(Level.WARNING, "Join lockdown check failed for " + event.getName() + " (allowed in)", e);
            return;
        }
        if (lockdown.joinBlocked(known)) {
            // counted, and audited once in lockdown_end ("refused"), so a bot flood can't flood the audit log
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, lockdown.joinScreen());
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            return;
        }
        UUID id = event.getUniqueId();
        String ip = event.getAddress() == null ? null : event.getAddress().getHostAddress();
        try {
            Optional<Punishment> ban = service.store().activeBlocking(Punishment.Type.BAN, id, ip);
            if (ban.isPresent()) {
                event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, service.banScreen(ban.get()));
                return;
            }
            service.cacheMute(id, service.store().activeBlocking(Punishment.Type.MUTE, id, ip).orElse(null));
            if (config.altAlert || config.altAutoBan) {
                List<PunishmentStore.Alt> flagged = service.store().altsBlocking(id, ip).stream()
                        .filter(a -> a.ban() != null || a.mute() != null).toList();
                if (!flagged.isEmpty()) {
                    pendingAltAlerts.put(id, flagged);
                }
            }
        } catch (RuntimeException e) {
            // Fail open: a database hiccup must not lock everyone out. Logged for staff.
            plugin.getLogger().log(Level.WARNING, "Punishment check failed for " + event.getName() + " (allowed in)", e);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        // A mute given while offline (e.g. an automatic rule) is explained on join (spec 5.5 "offline player warned").
        Optional<Punishment> muted = service.mute(player.getUniqueId());
        muted.ifPresent(m -> msg.send(player, "muted.join", service.parts(player, m)));
        // spec 13.13: timer for the active mute; "mute ended" / "welcome back" once for what ended while offline
        service.trackMute(player.getUniqueId());
        service.endNotices(player, muted.isEmpty(), true);
        notify.joined(player); // spec 14.9: this staff member's alert switches
        watchOnJoin(player); // spec 13.5: staff only, never the player
        service.requestsOnJoin(player); // spec 13.4: outcomes for requesters, waiting requests for approvers
        extras.joined(player); // spec 12.6: unacknowledged warnings are reminded once
        appeals.joined(player); // spec 13.10: decisions not told yet; staff: appeals waiting longer than appeal.reminder
        cleanStreak.joined(player); // spec 14.11: the "Honorable" title, checked a few seconds after the join
        // spec 13.9: raid detection counts accounts on their very first join; staff see a running lockdown at once
        if (core.players().get(player.getUniqueId()).map(net.skybornmc.skyblock.common.player.PlayerData::isNew).orElse(false)) {
            lockdown.newAccountJoined(player.getUniqueId());
        }
        lockdown.staffJoined(player);
        if (lastLoadRejected && player.hasPermission(PERM_CONFIG_ALERT) && !lastProblems.isEmpty()) {
            sendProblems(player, lastProblems, "config.rejected");
        }
        List<PunishmentStore.Alt> flagged = pendingAltAlerts.remove(player.getUniqueId());
        if (flagged == null) {
            return;
        }
        PunishmentStore.Alt banned = flagged.stream().filter(a -> a.ban() != null).findFirst().orElse(null);
        if (config.altAutoBan && banned != null && !player.hasPermission(PERM_EXEMPT)) {
            service.issue(Bukkit.getConsoleSender(), new PunishmentService.Request(Punishment.Type.BAN, player.getUniqueId(),
                    player.getName(), null, service.text(null, "words.alt-of") + " " + banned.name() + " (#" + banned.ban().id() + ")",
                    false, false, null, null, null));
            return;
        }
        if (!config.altAlert) {
            return;
        }
        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (service.wants(staff, NotifyRules.Type.WATCHLIST)) { // P8: a suspicious join, like a watched player
                msg.send(staff, "alts.alert", Msg.p("player", player.getName()), PunishmentService.pre("target", player.getName()),
                        Msg.number("count", flagged.size()),
                        Msg.p("alts", String.join(", ", flagged.stream().map(PunishmentStore.Alt::name).toList())));
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        service.forget(event.getPlayer().getUniqueId());
        extras.forget(event.getPlayer().getUniqueId());
        noteIds.remove(event.getPlayer().getUniqueId().toString());
        pendingAltAlerts.remove(event.getPlayer().getUniqueId());
    }

    private boolean blockIfMuted(Player player) {
        Optional<Punishment> mute = service.mute(player.getUniqueId());
        if (mute.isEmpty()) {
            return false;
        }
        msg.send(player, "muted.blocked", service.parts(player, mute.get()));
        return true;
    }

    /** Runs before the chat module (LOW) so a muted message never gets formatted, filtered or logged as chat. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        if (blockIfMuted(event.getPlayer())) {
            event.setCancelled(true);
            return;
        }
        if (lockdown.chatBlocked(event.getPlayer())) {
            // spec 13.9: new players during a chat lockdown (staff and ranked players never)
            event.setCancelled(true);
            lockdown.tellChatBlocked(event.getPlayer());
            return;
        }
        if (extras.blocks(event.getPlayer())) {
            // spec 12.6 ack.block-until-ack (off by default): chat waits for [I understand] / /ack
            event.setCancelled(true);
            msg.send(event.getPlayer(), "ack.blocked");
        }
    }

    /** {@code ack.block-until-ack} (off by default): only /ack, /rules and /mypunishments work until acknowledged. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommand(org.bukkit.event.player.PlayerCommandPreprocessEvent event) {
        String raw = event.getMessage().length() > 1 ? event.getMessage().substring(1).split(" ", 2)[0] : "";
        if (lockdown.commandBlocked(event.getPlayer(), raw)) {
            // spec 13.9: private messages and mail are chat too
            event.setCancelled(true);
            lockdown.tellChatBlocked(event.getPlayer());
            return;
        }
        if (!extras.blocks(event.getPlayer())) {
            return;
        }
        String label = raw.toLowerCase(Locale.ROOT);
        if (label.contains(":")) {
            label = label.substring(label.indexOf(':') + 1);
        }
        if (!ACK_ALLOWED.contains(label)) {
            event.setCancelled(true);
            msg.send(event.getPlayer(), "ack.blocked");
        }
    }

    private static final java.util.Set<String> ACK_ALLOWED = java.util.Set.of("ack", "rules", "mypunishments", "appeal");

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onSign(SignChangeEvent event) {
        if (blockIfMuted(event.getPlayer())) {
            event.setCancelled(true);
        } else if (lockdown.chatBlocked(event.getPlayer())) {
            event.setCancelled(true);
            lockdown.tellChatBlocked(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBook(PlayerEditBookEvent event) {
        if (blockIfMuted(event.getPlayer())) {
            event.setCancelled(true);
        } else if (lockdown.chatBlocked(event.getPlayer())) {
            event.setCancelled(true);
            lockdown.tellChatBlocked(event.getPlayer());
        }
    }

    /** "30 Oct" in Europe/Oslo (notes on the /p head). */
    static String day(long millis) {
        return DAY.withZone(Format.zone()).format(Instant.ofEpochMilli(millis));
    }

    private static final java.time.format.DateTimeFormatter DAY = java.time.format.DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH);

    private static String senderKey(CommandSender sender) {
        return sender instanceof Player p ? p.getUniqueId().toString() : "console";
    }

    // ================================================================== /pnote (spec 12.5)

    private void pnote(CommandSender sender, String name, String text) {
        if (text != null) {
            String clean = Notes.clean(text);
            int max = templates.config().extras().notes().maxLength();
            Optional<Notes.Problem> problem = Notes.check(clean, max);
            if (problem.isPresent()) {
                throw fail(problem.get() == Notes.Problem.EMPTY ? "notes.empty" : "notes.too-long",
                        Msg.number("max", max), Msg.number("length", clean.length()));
            }
        }
        resolve(name).thenComposeAsync(found -> {
            if (found.isEmpty()) {
                msg.send(sender, "error.unknown", Msg.p("player", name));
                return CompletableFuture.completedFuture(null);
            }
            UUID target = found.get().uuid();
            String targetName = found.get().name();
            if (text == null) {
                return service.store().notes(target, 50).thenAcceptAsync(list -> listNotes(sender, targetName, list), main);
            }
            UUID author = sender instanceof Player p ? p.getUniqueId() : null;
            return service.store().addNote(target, targetName, author, sender.getName(), Notes.clean(text), System.currentTimeMillis())
                    .thenAcceptAsync(note -> {
                        msg.send(sender, "notes.added", Msg.p("id", note.id()), Msg.p("player", targetName),
                                PunishmentService.pre("target", targetName));
                        if (sender instanceof Player p) {
                            core.sounds().play(p, "success");
                        }
                        core.audit().logPlayer(net.skybornmc.skyblock.common.audit.AuditCategory.STAFF, "note_add",
                                net.skybornmc.skyblock.common.audit.Actor.of(sender), target, targetName, null, null,
                                Map.of("id", note.id(), "text", note.text()));
                        refreshBannedNames();
                    }, main);
        }, main).exceptionally(e -> failed(sender, e));
    }

    private void listNotes(CommandSender sender, String targetName, List<Notes.Note> list) {
        noteIds.put(senderKey(sender), list.stream().map(n -> String.valueOf(n.id())).toList());
        msg.send(sender, "notes.header", Msg.p("player", targetName), Msg.number("count", list.size()),
                PunishmentService.pre("target", targetName));
        if (list.isEmpty()) {
            msg.send(sender, "notes.none");
        }
        for (Notes.Note n : list) {
            msg.send(sender, "notes.line", Msg.p("id", n.id()), Msg.p("author", n.authorName()), Msg.p("date", when(n.created())),
                    Msg.p("text", n.text()), PunishmentService.pre("nid", n.id()));
        }
        core.audit().log(net.skybornmc.skyblock.common.audit.AuditCategory.STAFF, "notes_view",
                net.skybornmc.skyblock.common.audit.Actor.of(sender), null, targetName, null, null, Map.of("count", list.size()));
    }

    private void removeNote(CommandSender sender, String rawId) {
        long id;
        try {
            id = Long.parseLong(rawId.startsWith("#") ? rawId.substring(1) : rawId);
        } catch (NumberFormatException e) {
            throw fail("notes.bad-id", Msg.p("input", rawId));
        }
        UUID by = sender instanceof Player p ? p.getUniqueId() : null;
        service.store().removeNote(id, by, sender.getName(), System.currentTimeMillis()).thenAcceptAsync(r -> {
            if (r.isEmpty()) {
                msg.send(sender, "notes.not-found", Msg.p("id", id));
                return;
            }
            Notes.Note n = r.get().note();
            if (!r.get().changed()) {
                msg.send(sender, "notes.already-removed", Msg.p("id", id), Msg.p("by", n.removedByName() == null ? "?" : n.removedByName()));
                return;
            }
            msg.send(sender, "notes.removed", Msg.p("id", id), Msg.p("player", n.targetName()));
            if (sender instanceof Player p) {
                core.sounds().play(p, "success");
            }
            core.audit().logPlayer(net.skybornmc.skyblock.common.audit.AuditCategory.STAFF, "note_remove",
                    net.skybornmc.skyblock.common.audit.Actor.of(sender), n.target(), n.targetName(), null, null,
                    Map.of("id", id, "text", n.text(), "author", n.authorName()));
            noteIds.computeIfPresent(senderKey(sender), (k, ids) -> ids.stream().filter(x -> !x.equals(String.valueOf(id))).toList());
            refreshBannedNames();
        }, main).exceptionally(e -> failed(sender, e));
    }

    // ================================================================== /mypunishments (spec 12.12)

    private void myPunishments(Player player) {
        UUID self = player.getUniqueId();
        service.store().history(self).thenCombine(service.store().suggestions(self), (h, s) -> Map.entry(h, s))
                .thenAcceptAsync(data -> sendMyPunishments(player, PlayerView.own(data.getKey(), self), data.getValue()), main)
                .exceptionally(e -> failed(player, e));
    }

    private void sendMyPunishments(Player player, List<Punishment> own, List<Suggestion> suggestions) {
        PunishConfig.Extras x = templates.config().extras();
        long now = System.currentTimeMillis();
        msg.send(player, "mine.header");
        Punishment mute = PlayerView.activeMute(own, now);
        if (mute == null) {
            msg.send(player, "mine.no-mute");
        } else {
            msg.send(player, "mine.mute", Msg.p("reason", mute.reason()), Msg.p("id", mute.id()),
                    Msg.p("left", mute.expires() == null ? service.text(player, "words.permanent")
                            : Format.duration(Duration.ofMillis(Math.max(1000, mute.expires() - now)))),
                    PunishmentService.pre("appeal", service.appealUrl()),
                    PunishmentService.pre("appeal_url", PlayerView.link(service.appealUrl())),
                    service.ruleParts(player, mute.template()));
        }
        WarnRules.Settings rules = templates.config().warnRules();
        WarnRules.Counter counter = WarnRules.counter(own, suggestions, rules, now);
        List<Punishment> counted = WarnRules.counted(own, suggestions, rules, now);
        msg.send(player, "mine.warnings", Msg.p("count", counter.text()), Msg.p("next", counterLimit(counter, rules)));
        for (Punishment w : counted) {
            msg.send(player, "mine.warning", service.ruleParts(player, w.template()), Msg.p("reason", w.reason()),
                    Msg.p("date", when(w.created())),
                    Msg.p("expires", rules.warnExpire() == null ? service.text(player, "words.never")
                            : when(w.created() + rules.warnExpire().toMillis())));
        }
        boolean owes = own.stream().anyMatch(p -> p.type() == Punishment.Type.WARN && !p.removed() && !p.acknowledged()
                && p.active(now));
        if (owes) {
            msg.send(player, "mine.ack", Msg.c("ack", extras.ackButton(player)));
        }
        List<Punishment> last = PlayerView.last(own, x.playerView().lastEntries());
        msg.send(player, "mine.recent-header", Msg.number("count", last.size()));
        if (last.isEmpty()) {
            msg.send(player, "mine.none");
        }
        String hidden = service.text(player, "words.staff");
        String system = service.text(player, "words.system");
        for (Punishment p : last) {
            msg.send(player, x.playerView().showStaff() ? "mine.entry-staff" : "mine.entry",
                    Msg.mm("type", core.messages().raw(ID, "types." + p.type().name().toLowerCase(Locale.ROOT)) == null
                            ? p.type().name() : core.messages().raw(ID, "types." + p.type().name().toLowerCase(Locale.ROOT))),
                    Msg.p("reason", p.reason()), Msg.p("date", when(p.created())), Msg.p("id", p.id()),
                    Msg.p("status", service.text(player, "status." + p.status(now).name().toLowerCase(Locale.ROOT))),
                    Msg.p("staff", PlayerView.staff(p, x.playerView().showStaff(), hidden, system)),
                    service.ruleParts(player, p.template()));
        }
    }

    static String when(long millis) {
        return PunishmentService.DATE.withZone(Format.zone()).format(Instant.ofEpochMilli(millis));
    }

    // ================================================================== P4: approval menus (spec 13.4)

    /** Opens the deny-reason menu for request {@code id} (from the chat [Deny] and the request menu). */
    void openDeny(Player staff, long id) {
        service.store().request(id).thenAcceptAsync(found -> {
            if (found.isEmpty()) {
                msg.send(staff, "approval.not-found");
                return;
            }
            if (!found.get().pending(System.currentTimeMillis())) {
                msg.send(staff, found.get().expired(System.currentTimeMillis()) ? "approval.expired" : "approval.already",
                        service.requestParts(staff, found.get()));
                return;
            }
            core.menus().open(staff, new PunishMenus.DenyMenu(this, found.get(), null));
        }, main).exceptionally(e -> failed(staff, e));
    }

    // ================================================================== P4: watchlist (spec 13.5)

    /** Placeholders of a watchlist entry. */
    TagResolver watchParts(Player viewer, Watchlist.Entry e) {
        long now = System.currentTimeMillis();
        return TagResolver.resolver(Msg.p("player", e.targetName()), PunishmentService.pre("target", e.targetName()),
                Msg.p("reason", e.reason() == null ? service.text(viewer, "watch.no-reason") : e.reason()),
                Msg.p("staff", e.authorName()), Msg.p("date", day(e.created())),
                Msg.p("ago", Format.duration(Duration.ofMillis(Math.max(1000, now - e.created())))),
                Msg.p("expires", e.expires() == null ? service.text(viewer, "words.never") : day(e.expires())));
    }

    private void watch(CommandSender sender, String name, String rawReason) {
        PunishConfig.WatchSettings ws = templates.config().extras().watchlist();
        if (rawReason != null && Notes.clean(rawReason).length() > ws.reasonMaxLength()) {
            throw fail("watch.too-long", Msg.number("max", ws.reasonMaxLength()),
                    Msg.number("length", Notes.clean(rawReason).length()));
        }
        String reason = Watchlist.cleanReason(rawReason, ws.reasonMaxLength());
        resolve(name).thenComposeAsync(found -> {
            if (found.isEmpty()) {
                msg.send(sender, "error.unknown", Msg.p("player", name));
                return CompletableFuture.completedFuture(null);
            }
            long now = System.currentTimeMillis();
            Long expires = ws.expire() == null ? null : now + ws.expire().toMillis();
            UUID author = sender instanceof Player p ? p.getUniqueId() : null;
            return service.store().watch(found.get().uuid(), found.get().name(), author, sender.getName(), reason, now, expires)
                    .thenAcceptAsync(saved -> {
                        Player viewer = sender instanceof Player p ? p : null;
                        msg.send(sender, saved.before() == null ? "watch.added" : "watch.updated", watchParts(viewer, saved.entry()));
                        if (viewer != null) {
                            core.sounds().play(viewer, "success");
                        }
                        Map<String, Object> details = new java.util.LinkedHashMap<>();
                        details.put("id", saved.entry().id());
                        details.put("reason", reason == null ? "-" : reason);
                        details.put("expires", expires == null ? "never" : expires);
                        if (saved.before() != null) {
                            details.put("before_reason", saved.before().reason() == null ? "-" : saved.before().reason());
                            details.put("before_author", saved.before().authorName());
                        }
                        core.audit().logPlayer(net.skybornmc.skyblock.common.audit.AuditCategory.STAFF,
                                saved.before() == null ? "watch_add" : "watch_update",
                                net.skybornmc.skyblock.common.audit.Actor.of(sender), found.get().uuid(), found.get().name(),
                                null, null, details);
                        refreshWatched();
                    }, main);
        }, main).exceptionally(e -> failed(sender, e));
    }

    private void unwatch(CommandSender sender, String name) {
        resolve(name).thenComposeAsync(found -> {
            if (found.isEmpty()) {
                msg.send(sender, "error.unknown", Msg.p("player", name));
                return CompletableFuture.completedFuture(null);
            }
            UUID by = sender instanceof Player p ? p.getUniqueId() : null;
            return service.store().unwatch(found.get().uuid(), by, sender.getName(), System.currentTimeMillis())
                    .thenAcceptAsync(removed -> {
                        Player viewer = sender instanceof Player p ? p : null;
                        if (removed.isEmpty()) {
                            msg.send(sender, "watch.not-watched", Msg.p("player", found.get().name()));
                            if (viewer != null) {
                                core.sounds().play(viewer, "error");
                            }
                            return;
                        }
                        msg.send(sender, "watch.removed", watchParts(viewer, removed.get()));
                        if (viewer != null) {
                            core.sounds().play(viewer, "success");
                        }
                        core.audit().logPlayer(net.skybornmc.skyblock.common.audit.AuditCategory.STAFF, "watch_remove",
                                net.skybornmc.skyblock.common.audit.Actor.of(sender), found.get().uuid(), found.get().name(),
                                null, null, Map.of("id", removed.get().id(), "reason",
                                        removed.get().reason() == null ? "-" : removed.get().reason(),
                                        "author", removed.get().authorName()));
                        refreshWatched();
                    }, main);
        }, main).exceptionally(e -> failed(sender, e));
    }

    private void watchlist(CommandSender sender) {
        service.store().watchlist(System.currentTimeMillis()).thenAcceptAsync(list -> {
            if (sender instanceof Player p) {
                core.menus().open(p, new PunishMenus.WatchlistMenu(this, list));
            } else {
                msg.send(sender, "watch.list-header", Msg.number("count", list.size()));
                if (list.isEmpty()) {
                    msg.send(sender, "watch.list-none");
                }
                for (Watchlist.Entry e : list) {
                    msg.send(sender, "watch.list-line", watchParts(null, e));
                }
            }
        }, main).exceptionally(e -> failed(sender, e));
        core.audit().log(net.skybornmc.skyblock.common.audit.AuditCategory.STAFF, "watchlist_view",
                net.skybornmc.skyblock.common.audit.Actor.of(sender), null, null, null, null, Map.of());
    }

    /** A watched player joined: one line to staff with skyblock.staff.notify; the player is told nothing. */
    private void watchOnJoin(Player player) {
        UUID id = player.getUniqueId();
        service.store().activeWatch(id, System.currentTimeMillis()).thenAcceptAsync(entry -> {
            if (entry.isEmpty() || !player.isOnline()) {
                return;
            }
            List<Watchlist.Viewer> online = Bukkit.getOnlinePlayers().stream()
                    .map(p -> new Watchlist.Viewer(p.getUniqueId(), service.wants(p, NotifyRules.Type.WATCHLIST))).toList();
            for (UUID staffId : Watchlist.recipients(online, id)) {
                Player staff = Bukkit.getPlayer(staffId);
                if (staff != null) {
                    msg.send(staff, "watch.notify", watchParts(staff, entry.get()));
                }
            }
        }, main).exceptionally(e -> null);
    }
}
