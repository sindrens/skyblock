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
    static final String PERM_WATCH = "skyblock.staff.watch";
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
        loadPunishments(null);
        // P3: Discord sink (off without a webhook secret), side effects, suggestion API
        loadWebhooks();
        discord = WebhookDiscordSink.create(() -> webhooks,
                channel -> templates.config().extras().discord().enabled()
                        && channel.equals(templates.config().extras().discord().channel()),
                () -> templates.config().extras().discord(), plugin.getLogger());
        try {
            core.services().register(ID, net.skybornmc.skyblock.common.discord.DiscordSink.class, discord);
        } catch (IllegalStateException e) {
            plugin.getLogger().info("A Discord sink is already provided by another module; moderation uses that one.");
        }
        extras = new PunishExtras(core, msg, service.store(), () -> templates.config().extras(),
                () -> templates.config().warnRules(), () -> core.services().optional(net.skybornmc.skyblock.common.discord.DiscordSink.class));
        service.hooks(extras);
        core.services().register(ID, net.skybornmc.skyblock.common.moderation.SuggestionService.class,
                new ApiSuggestions(core, service, templates, () -> templates.config().warnRules(), t -> reasonName(null, t)));
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        core.placeholders().registerText(ID, "muted", p -> p != null && service.mute(p.getUniqueId()).isPresent() ? "true" : "false");
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
        purgeTask = core.scheduler().asyncRepeat(200L, 20L * 60 * 60 * 6, extras::purgeEvidence);
        for (Player p : Bukkit.getOnlinePlayers()) {
            extras.joined(p);
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
        Arg<String> template = Args.oneOf("reason", templates::reasonIds);
        Arg<String> flags = Arg.greedy("flags", (sender, raw) -> raw.strip(), (ctx, builder) -> {
            Flags.suggestions(builder.getRemaining(), Flags.allowed(ctx.getSource().getSender()::hasPermission))
                    .forEach(builder::suggest);
            return builder.buildFuture();
        });
        commands.register(CommandSpec.command("punish")
                .description("Punishment menu for a player, or apply a reason directly (-s silent, -ip IP-wide, -force)")
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

        Arg<String> altsTarget = player;
        commands.register(CommandSpec.command("alts")
                .description("Accounts that share an IP address with a player").category("Staff").permission(PERM_ALTS)
                .arg(altsTarget).example("alts Steve")
                .executes(ctx -> alts(ctx.sender(), ctx.get(altsTarget))));
    }

    /** Parsed reason with flags removed ({@code -s} silent, {@code -ip} IP-wide, {@code -force} skip the duplicate check). */
    record Reason(String text, boolean silent, boolean ipWide, boolean force) {
        static Reason parse(String raw) {
            boolean silent = false;
            boolean ip = false;
            boolean force = false;
            List<String> words = new ArrayList<>();
            for (String w : raw.trim().split("\\s+")) {
                if (w.equalsIgnoreCase("-s")) {
                    silent = true;
                } else if (w.equalsIgnoreCase("-ip")) {
                    ip = true;
                } else if (w.equalsIgnoreCase("-force")) {
                    force = true;
                } else if (!w.isEmpty()) {
                    words.add(w);
                }
            }
            return new Reason(String.join(" ", words), silent, ip, force);
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
        };
    }

    /**
     * Direct commands (/ban, /tempmute, /warn, /kick ...). Everything (permission, -s/-ip flags, length limit, exempt,
     * never shorten) is decided by the shared pipeline in {@link PunishmentService#issue}.
     */
    private void direct(CommandSender sender, Punishment.Type type, String targetName, Duration duration, String rawReason) {
        Reason reason = Reason.parse(rawReason);
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
        return CompletableFuture.allOf(history, suggestions, api, notes, noteCount, requests, watch).thenApply(v ->
                new PunishMenus.Target(uuid, name, history.join(), suggestions.join(), api.join(), notes.join(), noteCount.join(),
                        requests.join(), watch.join().orElse(null)));
    }

    /** Opens the single-screen /p menu for {@code viewer} (loads the target first). */
    void openPunishMenu(Player viewer, String name) {
        target(name).thenAcceptAsync(t -> {
            if (t.isEmpty()) {
                msg.send(viewer, "error.unknown", Msg.p("player", name));
                return;
            }
            core.menus().open(viewer, new PunishMenus.PunishMenu(this, t.get()));
        }, main).exceptionally(e -> failed(viewer, e));
    }

    private void punishCommand(CommandSender sender, String name, String reasonId, String flags) {
        if (reasonId == null && !(sender instanceof Player)) {
            throw CommandFailure.of("players-only");
        }
        if (reasonId == null) {
            openPunishMenu((Player) sender, name);
            return;
        }
        Reason parsed = Reason.parse(flags);
        target(name).thenAcceptAsync(t -> {
            if (t.isEmpty()) {
                msg.send(sender, "error.unknown", Msg.p("player", name));
                return;
            }
            Optional<Templates.Template> template = templates.template(reasonId);
            if (template.isPresent()) {
                punishWithTemplate(sender, t.get(), template.get(), parsed.silent(), parsed.ipWide(), parsed.force());
                return;
            }
            Optional<PunishConfig.WarnButton> button = templates.warnButton(reasonId);
            if (button.isPresent()) {
                punishWithWarnButton(sender, t.get(), button.get(), parsed.silent(), parsed.force());
                return;
            }
            msg.send(sender, "error.unknown-template", Msg.p("reason", reasonId));
        }, main).exceptionally(e -> failed(sender, e));
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
    CompletableFuture<PunishmentStore.IssueResult> punishWithTemplate(CommandSender sender, PunishMenus.Target target,
                                                                     Templates.Template template, boolean silent, boolean ipFlag) {
        return punishWithTemplate(sender, target, template, silent, ipFlag, false);
    }

    /** Same, with {@code -force} (skip the duplicate check, needs skyblock.staff.punish.force). */
    CompletableFuture<PunishmentStore.IssueResult> punishWithTemplate(CommandSender sender, PunishMenus.Target target,
                                                                     Templates.Template template, boolean silent, boolean ipFlag,
                                                                     boolean force) {
        // spec 12.3: only offences younger than the reason's offence-expire count for the ladder
        int previous = target.previous(template.id(), template.cutoff(System.currentTimeMillis()));
        Templates.Step step = template.stepFor(previous);
        boolean ipWide = step.ipWide() || (ipFlag && (step.type() == Punishment.Type.BAN || step.type() == Punishment.Type.MUTE));
        Player online = Bukkit.getPlayer(target.uuid());
        // spec 13.7: the pipeline's probation stage may raise the step (it sees the ladder and the probation end)
        return service.issue(sender, new PunishmentService.Request(step.type(), target.uuid(), target.name(), step.duration(),
                        reasonName(null, template), silent, ipWide, online != null && (ipWide || template.ladder().stream()
                                .anyMatch(Templates.Step::ipWide)) ? ip(online) : null,
                        template.id(), template.stepIndex(previous), null, force, step.weight(),
                        escalation(target, template, previous, ipFlag)))
                .exceptionally(e -> failed(sender, e));
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
        return service.issue(sender, new PunishmentService.Request(Punishment.Type.WARN, target.uuid(), target.name(), null,
                        warnButtonText(null, button, "reason"), silent, false, null, button.id(), null, force, button.weight()))
                .exceptionally(e -> failed(sender, e));
    }

    /** Kick from the /p menu with the default kick reason. */
    CompletableFuture<PunishmentStore.IssueResult> kickFromMenu(Player sender, PunishMenus.Target target) {
        return service.issue(sender, new PunishmentService.Request(Punishment.Type.KICK, target.uuid(), target.name(), null,
                service.text(null, "punish.kick-reason"), false, false, null, null, null))
                .exceptionally(e -> failed(sender, e));
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
                        .thenAcceptAsync(c -> sendStats(sender, viewer.getName(), periodText, c.getKey(), c.getValue()), main)
                        .exceptionally(e -> failed(sender, e));
            }
            case OTHER -> service.store().staffByName(q.staff()).thenCompose(found -> found.isPresent()
                            ? CompletableFuture.completedFuture(found)
                            : core.players().offline(q.staff()).thenApply(o -> o.map(snap -> Map.entry(snap.uuid(), snap.name()))))
                    .thenCompose(found -> found.isEmpty() ? CompletableFuture.completedFuture(null)
                            : service.stats(found.get().getKey(), q.period())
                            .thenCombine(requestCounts(found.get().getKey(), q.period()), (c, rc) -> new StaffStats(found.get().getValue(), c, rc)))
                    .thenAcceptAsync(result -> {
                        if (result == null) {
                            msg.send(sender, "error.unknown", Msg.p("player", q.staff()));
                            return;
                        }
                        sendStats(sender, result.name(), periodText, result.counts(), result.requests());
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

    private record StaffStats(String name, PunishStats.Counts counts, PunishmentStore.RequestCounts requests) {
    }

    /** Approval request numbers of a staff member for a period (spec 13.4: made / approved / denied). */
    private CompletableFuture<PunishmentStore.RequestCounts> requestCounts(UUID staff, PunishStats.Period period) {
        long now = System.currentTimeMillis();
        return service.store().requestCounts(staff, period.start(now, templates.config().extras().statsZone()), now)
                .exceptionally(e -> PunishmentStore.RequestCounts.NONE);
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

    private void sendHistory(CommandSender sender, PunishMenus.Target t) {
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
        watchOnJoin(player); // spec 13.5: staff only, never the player
        service.requestsOnJoin(player); // spec 13.4: outcomes for requesters, waiting requests for approvers
        extras.joined(player); // spec 12.6: unacknowledged warnings are reminded once
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
            if (staff.hasPermission(PERM_NOTIFY)) {
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
        if (extras.blocks(event.getPlayer())) {
            // spec 12.6 ack.block-until-ack (off by default): chat waits for [I understand] / /ack
            event.setCancelled(true);
            msg.send(event.getPlayer(), "ack.blocked");
        }
    }

    /** {@code ack.block-until-ack} (off by default): only /ack, /rules and /mypunishments work until acknowledged. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommand(org.bukkit.event.player.PlayerCommandPreprocessEvent event) {
        if (!extras.blocks(event.getPlayer())) {
            return;
        }
        String label = event.getMessage().substring(1).split(" ", 2)[0].toLowerCase(Locale.ROOT);
        if (label.contains(":")) {
            label = label.substring(label.indexOf(':') + 1);
        }
        if (!ACK_ALLOWED.contains(label)) {
            event.setCancelled(true);
            msg.send(event.getPlayer(), "ack.blocked");
        }
    }

    private static final java.util.Set<String> ACK_ALLOWED = java.util.Set.of("ack", "rules", "mypunishments");

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onSign(SignChangeEvent event) {
        if (blockIfMuted(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBook(PlayerEditBookEvent event) {
        if (blockIfMuted(event.getPlayer())) {
            event.setCancelled(true);
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
                    PunishmentService.pre("appeal_url", PlayerView.link(service.appealUrl())));
        }
        WarnRules.Settings rules = templates.config().warnRules();
        WarnRules.Counter counter = WarnRules.counter(own, suggestions, rules, now);
        List<Punishment> counted = WarnRules.counted(own, suggestions, rules, now);
        msg.send(player, "mine.warnings", Msg.p("count", counter.text()), Msg.p("next", counterLimit(counter, rules)));
        for (Punishment w : counted) {
            msg.send(player, "mine.warning", Msg.p("reason", w.reason()), Msg.p("date", when(w.created())),
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
                    Msg.p("staff", PlayerView.staff(p, x.playerView().showStaff(), hidden, system)));
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
                    .map(p -> new Watchlist.Viewer(p.getUniqueId(), p.hasPermission(PERM_NOTIFY))).toList();
            for (UUID staffId : Watchlist.recipients(online, id)) {
                Player staff = Bukkit.getPlayer(staffId);
                if (staff != null) {
                    msg.send(staff, "watch.notify", watchParts(staff, entry.get()));
                }
            }
        }, main).exceptionally(e -> null);
    }
}
