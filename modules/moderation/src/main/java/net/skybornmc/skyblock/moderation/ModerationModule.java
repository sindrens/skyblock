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

    @Override
    public void enable() throws Exception {
        new Migrations(core.database(), plugin.getLogger()).migrate(ID, getClass().getClassLoader());
        core.configs().load(config);
        loadTemplates();
        main = new MainThread(plugin);
        service = new PunishmentService(core, msg, new PunishmentStore(core.database()), () -> config);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        core.placeholders().registerText(ID, "muted", p -> p != null && service.mute(p.getUniqueId()).isPresent() ? "true" : "false");
        registerCommands();
        // Mutes of players already online (reload) + banned-name cache for /unban tab completion.
        for (Player p : Bukkit.getOnlinePlayers()) {
            service.store().activeMute(p.getUniqueId(), ip(p)).thenAccept(m -> service.cacheMute(p.getUniqueId(), m.orElse(null)));
        }
        bannedNamesTask = core.scheduler().asyncRepeat(20L, 600L, this::refreshBannedNames);
    }

    @Override
    public void reload() {
        core.configs().load(config);
        core.messages().load(ID);
        loadTemplates();
    }

    @Override
    public void disable() {
        if (bannedNamesTask != null) {
            bannedNamesTask.cancel();
        }
        HandlerList.unregisterAll(this);
    }

    private void loadTemplates() {
        File file = new File(plugin.getDataFolder(), "punishments.yml");
        if (!file.exists()) {
            plugin.saveResource("punishments.yml", false);
        }
        templates.load(file).forEach(plugin.getLogger()::warning);
    }

    private void refreshBannedNames() {
        service.store().bannedNames().thenAccept(names -> bannedNames = List.copyOf(names))
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
        Arg<String> template = Args.oneOf("reason", templates::templateIds);
        Arg<String> flags = Args.text("flags", "-s");
        commands.register(CommandSpec.command("punish")
                .description("Punish with a template: opens the reason menu, or applies a reason directly").category("Staff")
                .permission(PERM_GUI).arg(punishTarget).optional(template).optional(flags).example("punish Steve toxicity")
                .executes(ctx -> punishCommand(ctx.sender(), ctx.get(punishTarget), ctx.getOr(template, null), ctx.getOr(flags, ""))));

        Arg<String> historyTarget = player;
        Arg<String> mode = Args.oneOf("view", () -> List.of("chat", "menu"));
        commands.register(CommandSpec.command("history")
                .description("All punishments of a player (menu, or chat)").category("Staff").permission(PERM_HISTORY)
                .aliases("punishments").arg(historyTarget).optional(mode).example("history Steve")
                .executes(ctx -> history(ctx.sender(), ctx.get(historyTarget), ctx.getOr(mode, null))));
        Arg<String> altsTarget = player;
        commands.register(CommandSpec.command("alts")
                .description("Accounts that share an IP address with a player").category("Staff").permission(PERM_ALTS)
                .arg(altsTarget).example("alts Steve")
                .executes(ctx -> alts(ctx.sender(), ctx.get(altsTarget))));
    }

    /** Parsed reason with flags removed. */
    record Reason(String text, boolean silent, boolean ipWide) {
        static Reason parse(String raw) {
            boolean silent = false;
            boolean ip = false;
            List<String> words = new ArrayList<>();
            for (String w : raw.trim().split("\\s+")) {
                if (w.equalsIgnoreCase("-s")) {
                    silent = true;
                } else if (w.equalsIgnoreCase("-ip")) {
                    ip = true;
                } else if (!w.isEmpty()) {
                    words.add(w);
                }
            }
            return new Reason(String.join(" ", words), silent, ip);
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
    private static String permissionFor(Punishment.Type type, boolean ipWide) {
        return switch (type) {
            case BAN -> ipWide ? PERM_IPBAN : PERM_BAN;
            case MUTE -> PERM_MUTE;
            case WARN -> PERM_WARN;
            case KICK -> PERM_KICK;
        };
    }

    boolean canApply(CommandSender sender, Templates.Step step) {
        if (!sender.hasPermission(permissionFor(step.type(), step.ipWide()))) {
            return false;
        }
        if (step.type() == Punishment.Type.BAN || step.type() == Punishment.Type.MUTE) {
            return service.allowed(sender, step.duration());
        }
        return true;
    }

    /** Shared checks for every way of punishing. */
    private void check(CommandSender sender, Punishment.Type type, Duration duration, Reason reason, String targetName) {
        if (reason.silent() && !sender.hasPermission(PERM_SILENT)) {
            throw fail("error.no-silent");
        }
        if (reason.ipWide() && !sender.hasPermission(PERM_IPBAN)) {
            throw fail("error.no-ip");
        }
        if ((type == Punishment.Type.BAN || type == Punishment.Type.MUTE) && !service.allowed(sender, duration)) {
            throw fail("error.too-long", Msg.p("max", service.describeMax(sender)));
        }
        if (sender instanceof Player self && self.getName().equalsIgnoreCase(targetName)) {
            throw fail("error.self");
        }
        Player online = Bukkit.getPlayerExact(targetName);
        if (online != null && online.hasPermission(PERM_EXEMPT) && !sender.hasPermission(PERM_EXEMPT_BYPASS)) {
            throw fail("error.exempt", Msg.p("player", online.getName()));
        }
    }

    private void direct(CommandSender sender, Punishment.Type type, String targetName, Duration duration, String rawReason) {
        Reason reason = Reason.parse(rawReason);
        check(sender, type, duration, reason, targetName);
        if (type == Punishment.Type.KICK && Bukkit.getPlayerExact(targetName) == null) {
            throw CommandFailure.of("player-offline", Msg.p("input", targetName));
        }
        String text = reason.text().isEmpty() ? service.text(sender instanceof Player p ? p : null, "words.no-reason") : reason.text();
        resolve(targetName).thenComposeAsync(found -> {
            if (found.isEmpty()) {
                msg.send(sender, "error.unknown", Msg.p("player", targetName));
                return CompletableFuture.completedFuture(null);
            }
            return service.issue(sender, new PunishmentService.Request(type, found.get().uuid(), found.get().name(), duration,
                    text, reason.silent(), reason.ipWide(), null, null, null));
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
            if (reason.silent() && !sender.hasPermission(PERM_SILENT)) {
                throw fail("error.no-silent");
            }
            if (!service.allowed(sender, null)) {
                throw fail("error.too-long", Msg.p("max", service.describeMax(sender)));
            }
            service.issue(sender, new PunishmentService.Request(Punishment.Type.BAN, null, target, null, text, reason.silent(),
                    true, target, null, null)).exceptionally(e -> failed(sender, e));
            return;
        }
        Reason withIp = new Reason(reason.text(), reason.silent(), true);
        check(sender, Punishment.Type.BAN, null, withIp, target);
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

    private CompletableFuture<Optional<PunishMenus.Target>> target(String name) {
        return resolve(name).thenCompose(found -> found.isEmpty()
                ? CompletableFuture.completedFuture(Optional.<PunishMenus.Target>empty())
                : service.store().history(found.get().uuid())
                .thenApply(h -> Optional.of(new PunishMenus.Target(found.get().uuid(), found.get().name(), h))));
    }

    private void punishCommand(CommandSender sender, String name, String templateId, String flags) {
        if (templateId == null && !(sender instanceof Player)) {
            throw CommandFailure.of("players-only");
        }
        target(name).thenAcceptAsync(t -> {
            if (t.isEmpty()) {
                msg.send(sender, "error.unknown", Msg.p("player", name));
                return;
            }
            if (templateId == null) {
                core.menus().open((Player) sender, new PunishMenus.CategoryMenu(this, t.get()));
                return;
            }
            Templates.Template template = templates.template(templateId).orElse(null);
            if (template == null) {
                msg.send(sender, "error.unknown-template", Msg.p("reason", templateId));
                return;
            }
            punishWithTemplate(sender, t.get(), template, t.get().previous(template.id()), Reason.parse(flags).silent());
        }, main).exceptionally(e -> failed(sender, e));
    }

    /** Applies the ladder step for this offence (called from the confirm menu and from /punish <player> <reason>). */
    void punishWithTemplate(CommandSender sender, PunishMenus.Target target, Templates.Template template, int previous, boolean silent) {
        Templates.Step step = template.stepFor(previous);
        try {
            check(sender, step.type(), step.duration(), new Reason(template.name(), silent, step.ipWide()), target.name());
            if (!canApply(sender, step)) {
                throw fail("error.too-long", Msg.p("max", service.describeMax(sender)));
            }
        } catch (CommandFailure f) {
            core.messages().send(sender, f.module(), f.key(), f.placeholders());
            return;
        }
        if (step.type() == Punishment.Type.KICK && Bukkit.getPlayer(target.uuid()) == null) {
            msg.send(sender, "error.kick-offline", Msg.p("player", target.name()));
            return;
        }
        Player online = Bukkit.getPlayer(target.uuid());
        service.issue(sender, new PunishmentService.Request(step.type(), target.uuid(), target.name(), step.duration(),
                        template.name(), silent, step.ipWide(), step.ipWide() && online != null ? ip(online) : null,
                        template.id(), Math.min(previous, template.ladder().size() - 1)))
                .exceptionally(e -> failed(sender, e));
    }

    private void history(CommandSender sender, String name, String mode) {
        boolean chat = "chat".equals(mode) || !(sender instanceof Player);
        target(name).thenAcceptAsync(t -> {
            if (t.isEmpty()) {
                msg.send(sender, "error.unknown", Msg.p("player", name));
                return;
            }
            if (!chat) {
                core.menus().open((Player) sender, new PunishMenus.HistoryMenu(t.get()));
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
        }
    }

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

    static String when(long millis) {
        return PunishmentService.DATE.format(Instant.ofEpochMilli(millis));
    }
}
