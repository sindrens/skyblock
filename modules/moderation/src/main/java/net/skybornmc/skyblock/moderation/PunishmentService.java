package net.skybornmc.skyblock.moderation;

import io.papermc.paper.ban.BanListType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.skybornmc.skyblock.common.Core;
import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.audit.AuditCategory;
import net.skybornmc.skyblock.common.command.Durations;
import net.skybornmc.skyblock.common.config.ConfigManager;
import net.skybornmc.skyblock.common.config.ModuleConfig;
import net.skybornmc.skyblock.common.config.SettingInfo;
import net.skybornmc.skyblock.common.db.MainThread;
import net.skybornmc.skyblock.common.text.ModuleMessages;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.util.Format;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.net.InetAddress;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

/**
 * Issues and pardons punishments: stores them, applies them (kick/ban screen, mute cache), syncs the vanilla ban
 * list, notifies staff (clickable), announces publicly and writes the audit log. All database work is async; Bukkit
 * work hops back to the main thread.
 */
final class PunishmentService {

    static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.ENGLISH);

    /**
     * What a staff member asked for. {@code duration == null} = permanent (ban/mute). {@code template} (reason id or
     * warn-button id) also turns on the duplicate check (spec 12.2); {@code force} = the {@code -force} flag.
     */
    record Request(Punishment.Type type, UUID target, String targetName, Duration duration, String reason, boolean silent,
                   boolean ipWide, String ip, String template, Integer ladderStep, String source, boolean force, int weight,
                   PunishDecision.Escalation escalation, Long evidenceEntry, boolean bulk) {

        /**
         * P4 shape: no log evidence, not part of a bulk action. {@code evidenceEntry} (spec 13.11) = the {@code /logs} chat
         * line to save as evidence; {@code bulk} (spec 13.8) = part of a bulk action (no per-player chat lines, [Undo] or
         * Discord embed: the bulk action sends one summary of each).
         */
        Request(Punishment.Type type, UUID target, String targetName, Duration duration, String reason, boolean silent,
                boolean ipWide, String ip, String template, Integer ladderStep, String source, boolean force, int weight,
                PunishDecision.Escalation escalation) {
            this(type, target, targetName, duration, reason, silent, ipWide, ip, template, ladderStep, source, force, weight,
                    escalation, null, false);
        }

        Request(Punishment.Type type, UUID target, String targetName, Duration duration, String reason, boolean silent,
                boolean ipWide, String ip, String template, Integer ladderStep, String source, boolean force, int weight) {
            this(type, target, targetName, duration, reason, silent, ipWide, ip, template, ladderStep, source, force, weight, null);
        }

        Request(Punishment.Type type, UUID target, String targetName, Duration duration, String reason, boolean silent,
                boolean ipWide, String ip, String template, Integer ladderStep) {
            this(type, target, targetName, duration, reason, silent, ipWide, ip, template, ladderStep, null, false, Weights.ONE);
        }

        Request(Punishment.Type type, UUID target, String targetName, Duration duration, String reason, boolean silent,
                boolean ipWide, String ip, String template, Integer ladderStep, boolean force) {
            this(type, target, targetName, duration, reason, silent, ipWide, ip, template, ladderStep, null, force, Weights.ONE);
        }

        /** With a warn weight in tenths (spec 12.7: warn buttons and {@code warn 0.5} ladder steps). */
        Request(Punishment.Type type, UUID target, String targetName, Duration duration, String reason, boolean silent,
                boolean ipWide, String ip, String template, Integer ladderStep, boolean force, int weight) {
            this(type, target, targetName, duration, reason, silent, ipWide, ip, template, ladderStep, null, force, weight);
        }

        /** Same request with log evidence and/or as part of a bulk action ({@code group} = its source, or {@code null}). */
        Request with(Long evidence, String group) {
            return new Request(type, target, targetName, duration, reason, silent, ipWide, ip, template, ladderStep,
                    group != null ? group : source, force, weight, escalation, evidence, group != null);
        }
    }

    private final Core core;
    private final ModuleMessages msg;
    private final PunishmentStore store;
    private final java.util.function.Supplier<ModerationConfig> config;
    private final Executor main;
    /** Active mutes of online players (loaded at login, updated on mute/unmute). */
    private final Map<UUID, Punishment> mutes = new ConcurrentHashMap<>();

    PunishmentService(Core core, ModuleMessages msg, PunishmentStore store, java.util.function.Supplier<ModerationConfig> config) {
        this.core = core;
        this.msg = msg;
        this.store = store;
        this.config = config;
        this.main = new MainThread(core.plugin());
        this.islandPunish = new IslandPunish(core, msg, store);
        // spec 13.13: one timer per online player with a timed mute, at its end (no database polling)
        this.muteTimers = new EndTimers((delay, action) -> {
            net.skybornmc.skyblock.common.util.Scheduler.Task task = core.scheduler().later("moderation:mute-end",
                    Math.max(1L, (delay + 49) / 50), action);
            return task::cancel;
        }, System::currentTimeMillis, (player, id) -> core.scheduler().ensureMain("moderation:mute-end",
                () -> muteEnded(player, id)));
    }

    PunishmentStore store() {
        return store;
    }

    /** Island punishments (P7, spec 13.2). */
    private final IslandPunish islandPunish;

    IslandPunish islandPunish() {
        return islandPunish;
    }

    /** P3 side effects (evidence, acknowledgement, report link, Discord); set by the module. */
    private PunishExtras hooks;

    void hooks(PunishExtras hooks) {
        this.hooks = hooks;
    }

    PunishExtras hooks() {
        return hooks;
    }

    /** Who gets which staff alert (P8, spec 14.9); {@code null} until the module sets it (then: permission only). */
    private StaffNotify notifier;

    void notifier(StaffNotify value) {
        this.notifier = value;
    }

    StaffNotify notifier() {
        return notifier;
    }

    /** Whether {@code staff} gets alerts of {@code type} (permission + their /pnotify switch + always-on types). */
    boolean wants(Player staff, NotifyRules.Type type) {
        if (notifier != null) {
            return notifier.wants(staff, type);
        }
        return staff.hasPermission(type.permission());
    }

    /** Rule numbers of the active punishments.yml (P8, spec 14.2; set by the module). */
    private java.util.function.Supplier<RuleBook> rules = () -> RuleBook.EMPTY;

    void rules(java.util.function.Supplier<RuleBook> source) {
        this.rules = source;
    }

    RuleBook ruleBook() {
        RuleBook b = rules.get();
        return b == null ? RuleBook.EMPTY : b;
    }

    // ------------------------------------------------------------------ limits

    /** Longest duration {@code sender} may give; {@code null} = permanent allowed. Console = unlimited. */
    Duration maxDuration(CommandSender sender) {
        if (!(sender instanceof Player)) {
            return null;
        }
        Duration best = parse(config.get().defaultMaxDuration);
        boolean any = false;
        for (String entry : config.get().maxDurations) {
            String[] kv = entry.split("=", 2);
            if (kv.length != 2 || !sender.hasPermission("skyblock.staff.punish.max." + kv[0].trim())) {
                continue;
            }
            Duration d = parse(kv[1].trim());
            if (!any) {
                best = d;
                any = true;
            } else if (best != null && (d == null || d.compareTo(best) > 0)) {
                best = d;
            }
        }
        return best;
    }

    private static Duration parse(String text) {
        if (text == null || text.isBlank() || text.equalsIgnoreCase("perm") || text.equalsIgnoreCase("permanent")) {
            return null;
        }
        try {
            return Durations.parse(text);
        } catch (IllegalArgumentException e) {
            return Duration.ofDays(1);
        }
    }

    /** True if the requested duration fits the sender's limit. */
    boolean allowed(CommandSender sender, Duration requested) {
        Duration max = maxDuration(sender);
        if (max == null) {
            return true;
        }
        return requested != null && requested.compareTo(max) <= 0;
    }

    String describeMax(CommandSender sender) {
        Duration max = maxDuration(sender);
        return max == null ? text(null, "words.permanent") : Format.duration(max);
    }

    // ------------------------------------------------------------------ issue (the single pipeline)

    private java.util.function.Supplier<WarnRules.Settings> warnRules = () -> WarnRules.Settings.DISABLED;
    private java.util.function.Supplier<PunishConfig.Extras> extras = () -> PunishConfig.Extras.DEFAULT;
    /** "Click again within 5s" armings (spec 12.2). */
    private final DuplicateGuard duplicates = new DuplicateGuard();
    /** /pstats results (spec 12.10: cache 60 s). */
    private final PunishStats.Cache<String, CompletableFuture<?>> statsCache =
            new PunishStats.Cache<>(PunishConfig.Extras.DEFAULT.statsCache(), System::currentTimeMillis);

    void warnRules(java.util.function.Supplier<WarnRules.Settings> source) {
        this.warnRules = source;
    }

    void extras(java.util.function.Supplier<PunishConfig.Extras> source) {
        this.extras = source;
    }

    PunishConfig.Extras extras() {
        return extras.get();
    }

    /** The one pipeline: duplicate window (P2), probation and approval (P4) from the current config. */
    PunishDecision decision() {
        PunishConfig.Extras x = extras.get();
        return PunishDecision.full(x.duplicateWindow(), x.approval(), x.training());
    }

    /** True if {@code sender} is a trainee (spec 14.6, {@code skyblock.staff.punish.trainee}). Console never is. */
    static boolean trainee(CommandSender sender) {
        return sender instanceof Player p && p.hasPermission(ModerationModule.PERM_TRAINEE);
    }

    /** Permissions of {@code sender} as a pipeline actor. Main thread (reads permissions). */
    PunishDecision.Actor actor(CommandSender sender) {
        java.util.Set<Punishment.Type> types = java.util.EnumSet.noneOf(Punishment.Type.class);
        for (Punishment.Type type : Punishment.Type.values()) {
            if (sender.hasPermission(ModerationModule.permissionFor(type, false))) {
                types.add(type);
            }
        }
        return new PunishDecision.Actor(sender.getName(), false, java.util.Set.copyOf(types),
                sender.hasPermission(ModerationModule.PERM_IPBAN), sender.hasPermission(ModerationModule.PERM_SILENT),
                maxDuration(sender), sender.hasPermission(ModerationModule.PERM_EXEMPT_BYPASS),
                sender instanceof Player p ? p.getUniqueId() : null, sender.hasPermission(ModerationModule.PERM_FORCE),
                sender.hasPermission(ModerationModule.PERM_APPROVE), trainee(sender), false);
    }

    /** The pipeline input for {@code sender} punishing {@code target}. Main thread. Active punishments are filled in later. */
    PunishDecision.Input input(CommandSender sender, UUID target, String targetName, Punishment.Type type, Duration duration,
                               boolean ipWide, boolean silent, long now) {
        Player online = target == null ? null : Bukkit.getPlayer(target);
        boolean exempt = online != null && online.hasPermission(ModerationModule.PERM_EXEMPT);
        boolean self = sender instanceof Player p && (target != null ? p.getUniqueId().equals(target)
                : p.getName().equalsIgnoreCase(targetName));
        return new PunishDecision.Input(actor(sender), type, duration, ipWide, silent, exempt, self, online != null, null, null, now);
    }

    /** What {@code viewer} would get for this step right now (menu lore), using the active mute/ban already loaded. */
    PunishDecision.Verdict preview(CommandSender viewer, UUID target, String targetName, Templates.Step step, boolean silent,
                                   Punishment activeMute, Punishment activeBan) {
        return preview(viewer, target, targetName, step, silent, activeMute, activeBan, null, null);
    }

    /** Same, with the duplicate check ({@code recent} = newest punishment for {@code reasonKey} from the loaded history). */
    PunishDecision.Verdict preview(CommandSender viewer, UUID target, String targetName, Templates.Step step, boolean silent,
                                   Punishment activeMute, Punishment activeBan, String reasonKey, Punishment recent) {
        return preview(viewer, target, targetName, step, silent, activeMute, activeBan, reasonKey, recent, null);
    }

    /** Same, with the ladder and probation (spec 13.7): the verdict's input shows the step that will really be applied. */
    PunishDecision.Verdict preview(CommandSender viewer, UUID target, String targetName, Templates.Step step, boolean silent,
                                   Punishment activeMute, Punishment activeBan, String reasonKey, Punishment recent,
                                   PunishDecision.Escalation escalation) {
        return preview(viewer, target, targetName, step, silent, activeMute, activeBan, reasonKey, recent, escalation, false);
    }

    /** Same, as part of a bulk action or not (spec 14.6: a trainee's bulk action is one request per player). */
    PunishDecision.Verdict preview(CommandSender viewer, UUID target, String targetName, Templates.Step step, boolean silent,
                                   Punishment activeMute, Punishment activeBan, String reasonKey, Punishment recent,
                                   PunishDecision.Escalation escalation, boolean bulk) {
        PunishDecision.Input in = input(viewer, target, targetName, step.type(), step.duration(), step.ipWide(), silent,
                System.currentTimeMillis());
        return decision().decide(in.withActor(in.actor().inBulk(bulk)).withActive(activeMute, activeBan)
                .withReason(reasonKey, false, false).withRecent(recent).withEscalation(escalation));
    }

    private PunishmentStore.RuleHook ruleHook(UUID target, long now) {
        WarnRules.Settings settings = warnRules.get();
        if (!settings.enabled() || target == null) {
            return null;
        }
        String auto = text(null, "words.system-auto");
        PunishDecision.Input template = input(Bukkit.getConsoleSender(), target, null, Punishment.Type.MUTE, null, false,
                false, now);
        template = new PunishDecision.Input(PunishDecision.Actor.system(auto), template.type(), null, false, false,
                template.targetExempt(), false, template.targetOnline(), null, null, now);
        return new PunishmentStore.RuleHook(settings, decision(), template, auto, trigger -> trigger.repeat()
                ? text(null, "warn-rules.repeat-reason", Msg.p("window", PunishConfig.text(settings.repeatWindow())))
                : text(null, "warn-rules.reason", Msg.p("count", trigger.ruleAt())));
    }

    /**
     * Decides, stores and applies a punishment (main thread in, main thread out). Every way of punishing ends here:
     * BLOCKED/SKIPPED verdicts are told to the sender, applied ones are enforced, announced and audited, and a warn rule
     * that fires in the same transaction is reported to staff.
     */
    CompletableFuture<PunishmentStore.IssueResult> issue(CommandSender sender, Request r) {
        long now = System.currentTimeMillis();
        UUID issuer = sender instanceof Player p ? p.getUniqueId() : null;
        PunishConfig.Extras x = extras.get();
        String guardKey = r.template() == null || r.target() == null ? null
                : DuplicateGuard.key(issuer == null ? sender.getName() : issuer.toString(), r.target(), r.template());
        boolean confirmed = guardKey != null && duplicates.consume(guardKey, now, x.duplicateConfirm());
        PunishDecision.Input base = input(sender, r.target(), r.targetName(), r.type(), r.duration(), r.ipWide(), r.silent(), now);
        // 14.6: a trainee's bulk action is one request per player when "bulk" is in training.requests-for
        PunishDecision.Input in = base.withActor(base.actor().inBulk(r.bulk()))
                .withReason(r.template(), r.force(), confirmed).withEscalation(r.escalation());
        PunishDecision decision = decision();
        PunishmentStore.RuleHook hook = ruleHook(r.target(), now);
        // probation (13.7) may raise the step to an IP-wide one: know the address in case
        boolean mayNeedIp = r.ipWide() || (r.escalation() != null && r.escalation().ladder().stream()
                .anyMatch(Templates.Step::ipWide));
        CompletableFuture<String> ipFuture = mayNeedIp && r.ip() == null && r.target() != null
                ? store.lastIp(r.target()).thenApply(o -> o.orElse(null))
                : CompletableFuture.completedFuture(r.ip());
        return ipFuture.thenCompose(ip -> {
            if (r.ipWide() && ip == null && in.actor().ipBan()) {
                throw new IllegalStateException("no-ip");
            }
            Punishment draft = new Punishment(0, r.type(), r.target(), r.targetName(), ip, r.ipWide(), r.reason(), r.template(),
                    r.ladderStep(), issuer, sender.getName(), now, null, r.silent(), null, null, null, null, r.source(),
                    r.weight(), null);
            return store.issue(draft, in, decision, hook, x.approval().expire(), r.evidenceEntry());
        }).thenApplyAsync(result -> {
            if (guardKey != null && "duplicate".equals(result.verdict().stage())) {
                duplicates.arm(guardKey, now, x.duplicateConfirm());
                core.audit().log(AuditCategory.PUNISHMENT, "duplicate_blocked", Actor.of(sender), String.valueOf(r.target()),
                        r.targetName(), null, null, Map.of("reason", r.template(), "recent",
                                result.verdict().args().getOrDefault("id", "?")));
            }
            handle(sender, r, result);
            return result;
        }, main);
    }

    private void handle(CommandSender sender, Request r, PunishmentStore.IssueResult result) {
        PunishDecision.Verdict v = result.verdict();
        if (v.outcome() == PunishDecision.Outcome.REQUEST && result.request() != null) {
            // bulk (13.8): the requester gets one summary; approvers still get each request with its own buttons
            requested(sender, result.request(), result.merged(), !r.bulk());
            return;
        }
        if (!v.applied()) {
            if (!r.bulk()) {
                tell(sender, v, r.targetName());
            }
            return;
        }
        Punishment p = result.punishment();
        apply(p, r.evidenceEntry(), !r.bulk());
        if (r.bulk()) {
            announcePublic(p); // the staff line, [Undo all] and the Discord embed come once, from the bulk action
        } else {
            announce(sender, p);
        }
        audit(sender, p);
        if (!r.bulk()) {
            offerUndo(sender, p);
        }
        if (trainee(sender)) {
            traineeApplied(sender, p); // 14.6: mentors see what a trainee applied directly, with [Undo]
        }
        if (hooks != null) {
            hooks.linkReports(sender, p);
        }
        if (result.trigger() != null) {
            reportTrigger(result.trigger());
        }
    }

    /** Tells the actor why nothing was applied. */
    void tell(CommandSender sender, PunishDecision.Verdict v, String targetName) {
        java.util.List<TagResolver> tags = new java.util.ArrayList<>();
        v.args().forEach((k, val) -> tags.add(Msg.p(k, val)));
        tags.add(Msg.p("player", targetName == null ? "?" : targetName));
        if ("permission".equals(v.stage()) && v.args().containsKey("type")) {
            tags.add(Msg.p("type_name", text(sender instanceof Player pl ? pl : null, "types." + v.args().get("type"))));
        }
        tags.add(Msg.p("confirm", PunishConfig.text(extras.get().duplicateConfirm())));
        msg.send(sender, v.key(), tags.toArray(TagResolver[]::new));
        if (sender instanceof Player pl) {
            core.sounds().play(pl, "error");
        }
    }

    // ------------------------------------------------------------------ warn rules: staff side

    /** A rule fired in the issue transaction: tell staff, enforce an auto punishment, audit. Main thread. */
    void reportTrigger(PunishmentStore.TriggerResult t) {
        Suggestion s = t.suggestion();
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("suggestion", s.id());
        details.put("rule", s.repeat() ? "repeat" : s.ruleAt());
        details.put("count", s.countText());
        details.put("action", s.action());
        details.put("status", s.status().name().toLowerCase(Locale.ROOT));
        if (s.note() != null) {
            details.put("note", s.note());
        }
        switch (s.status()) {
            case PENDING -> {
                notifyStaff(NotifyRules.Type.SUGGESTIONS, null, s.repeat() ? "suggest.notify-repeat" : "suggest.notify",
                        suggestionParts(null, s));
                for (Player staff : Bukkit.getOnlinePlayers()) {
                    if (wants(staff, NotifyRules.Type.SUGGESTIONS)) {
                        core.sounds().play(staff, "notify");
                    }
                }
                core.audit().log(AuditCategory.PUNISHMENT, "warn_rule_trigger", Actor.system("warn-rule"),
                        s.target().toString(), s.targetName(), null, null, details);
            }
            case AUTO -> {
                Punishment auto = t.autoPunishment();
                details.put("id", auto.id());
                apply(auto);
                notifyStaff(NotifyRules.Type.AUTO, null, s.repeat() ? "suggest.auto-repeat" : "suggest.auto",
                        suggestionParts(null, s), Msg.p("id", auto.id()));
                core.audit().log(AuditCategory.PUNISHMENT, "warn_rule_auto", Actor.system("warn-rule"), s.target().toString(),
                        s.targetName(), auto.expires() == null ? null : auto.expires() - auto.created(), null, details);
                auditPunishment(Actor.system("warn-rule"), null, auto);
            }
            case SKIPPED -> {
                PunishDecision.Verdict v = t.verdict();
                String key = Suggestion.NOTE_EXEMPT.equals(s.note()) ? "suggest.skipped-exempt" : "suggest.skipped-already";
                notifyStaff(NotifyRules.Type.AUTO, null, key, suggestionParts(null, s),
                        Msg.p("left", v == null ? "?" : v.args().getOrDefault("left", "?")));
                core.audit().log(AuditCategory.PUNISHMENT, "warn_rule_skipped", Actor.system("warn-rule"), s.target().toString(),
                        s.targetName(), null, null, details);
            }
            default -> {
                // APPLIED/DISMISSED never come from a fresh trigger
            }
        }
    }

    /** Placeholders of a suggestion for chat lines (clickable [Apply] / [Dismiss] / [Open /p]). */
    TagResolver suggestionParts(Player viewer, Suggestion s) {
        Templates.Step step = s.step();
        WarnRules.Settings settings = warnRules.get();
        return TagResolver.resolver(Msg.p("player", s.targetName()), pre("target", s.targetName()), pre("sid", s.id()),
                Msg.p("count", s.countText()), Msg.p("action", stepText(viewer, step)), Msg.p("reason", s.reason()),
                Msg.p("details", s.details() == null ? "-" : s.details()),
                Msg.p("window", settings.repeatWindow() == null ? "-" : PunishConfig.text(settings.repeatWindow())),
                Msg.p("date", DATE.withZone(Format.zone()).format(Instant.ofEpochMilli(s.created()))),
                Msg.p("expires", s.expires() == null ? text(viewer, "words.never")
                        : Format.duration(Duration.ofMillis(Math.max(1000, s.expires() - System.currentTimeMillis())))),
                Msg.p("staff", s.handledByName() == null ? text(viewer, "words.system") : s.handledByName()),
                Msg.p("status", text(viewer, "suggest.status." + s.status().name().toLowerCase(Locale.ROOT))));
    }

    /** [Apply] on a suggestion as {@code sender} (first click wins). Main thread in and out. */
    CompletableFuture<PunishmentStore.HandleResult> applySuggestion(CommandSender sender, long id) {
        UUID by = sender instanceof Player p ? p.getUniqueId() : null;
        return store.suggestion(id).thenComposeAsync(found -> {
            if (found.isEmpty()) {
                return CompletableFuture.completedFuture(new PunishmentStore.HandleResult(PunishmentStore.HandleKind.NOT_FOUND,
                        null, null, null));
            }
            Suggestion s = found.get();
            Templates.Step step = s.step();
            long now = System.currentTimeMillis();
            PunishDecision.Input in = input(sender, s.target(), s.targetName(), step.type(), step.duration(), step.ipWide(),
                    false, now);
            return store.apply(id, x -> in, decision(), by, sender.getName(), now, extras.get().approval().expire());
        }, main).thenApplyAsync(r -> {
            handleClick(sender, r);
            return r;
        }, main);
    }

    /** [Dismiss] on a suggestion as {@code sender} (first click wins). */
    CompletableFuture<PunishmentStore.HandleResult> dismissSuggestion(CommandSender sender, long id) {
        UUID by = sender instanceof Player p ? p.getUniqueId() : null;
        return store.dismiss(id, by, sender.getName(), System.currentTimeMillis()).thenApplyAsync(r -> {
            handleClick(sender, r);
            return r;
        }, main);
    }

    private void handleClick(CommandSender sender, PunishmentStore.HandleResult r) {
        Player viewer = sender instanceof Player p ? p : null;
        Suggestion s = r.suggestion();
        switch (r.kind()) {
            case NOT_FOUND -> msg.send(sender, "suggest.not-found");
            case EXPIRED -> msg.send(sender, "suggest.expired", suggestionParts(viewer, s));
            case ALREADY_HANDLED -> msg.send(sender, "suggest.already-handled", suggestionParts(viewer, s));
            case BLOCKED -> tell(sender, r.verdict(), s.targetName());
            case REQUESTED -> {
                requested(sender, r.request(), r.merged());
                core.audit().log(AuditCategory.PUNISHMENT, "suggestion_request", Actor.of(sender), s.target().toString(),
                        s.targetName(), null, null, Map.of("suggestion", s.id(), "action", s.action(), "request", r.request().id()));
            }
            case SKIPPED -> {
                msg.send(sender, "suggest.skipped-already", suggestionParts(viewer, s),
                        Msg.p("left", r.verdict().args().getOrDefault("left", "?")));
                core.audit().log(AuditCategory.PUNISHMENT, "suggestion_skip", Actor.of(sender), s.target().toString(),
                        s.targetName(), null, null, Map.of("suggestion", s.id(), "action", s.action(), "note", Suggestion.NOTE_ALREADY));
            }
            case DISMISSED -> {
                notifyStaff(NotifyRules.Type.SUGGESTIONS, sender, "suggest.dismissed", suggestionParts(viewer, s),
                        Msg.p("staff", sender.getName()));
                core.audit().log(AuditCategory.PUNISHMENT, "suggestion_dismiss", Actor.of(sender), s.target().toString(),
                        s.targetName(), null, null, Map.of("suggestion", s.id(), "action", s.action(),
                                "rule", s.repeat() ? "repeat" : String.valueOf(s.ruleAt())));
            }
            case APPLIED -> {
                Punishment p = r.punishment();
                apply(p);
                announce(sender, p);
                audit(sender, p);
                offerUndo(sender, p);
                if (hooks != null) {
                    hooks.linkReports(sender, p);
                }
                notifyStaff(NotifyRules.Type.SUGGESTIONS, sender, "suggest.applied", suggestionParts(viewer, s),
                        Msg.p("staff", sender.getName()));
                core.audit().log(AuditCategory.PUNISHMENT, "suggestion_apply", Actor.of(sender), s.target().toString(),
                        s.targetName(), null, null, Map.of("suggestion", s.id(), "action", s.action(), "id", p.id()));
            }
        }
        if (viewer != null && r.kind() != PunishmentStore.HandleKind.APPLIED && r.kind() != PunishmentStore.HandleKind.DISMISSED
                && r.kind() != PunishmentStore.HandleKind.REQUESTED) {
            core.sounds().play(viewer, "error");
        }
    }

    /** "Mute 1h", "Ban permanent", "Warning" for a step (messages steps.*). */
    String stepText(Player viewer, Templates.Step step) {
        String kind = step.ipWide() ? "ipban" : step.kindId();
        if (step.type() == Punishment.Type.WARN && step.weight() != Weights.ONE) {
            return text(viewer, "steps.warn-weighted", Msg.p("weight", Weights.text(step.weight())));
        }
        String length = step.duration() == null ? text(viewer, "words.permanent") : Format.duration(step.duration());
        return text(viewer, "steps." + kind, Msg.p("length", length));
    }

    /** Kicks/bans online players hit by the punishment, updates mute caches and the vanilla ban list. */
    private void apply(Punishment p) {
        apply(p, null, true);
    }

    /**
     * Same, with the {@code /logs} line to save as evidence ({@code null} = the player's last lines, spec 13.11) and
     * whether this punishment gets its own Discord embed ({@code false} inside a bulk action: one embed for all).
     */
    private void apply(Punishment p, Long evidenceEntry, boolean discord) {
        List<Player> affected = affected(p);
        if (p.type().island()) {
            islandPunish.apply(p, this); // P7, spec 13.2: enforced by the islands module (the player is told there)
            affected = List.of();
        }
        if (p.type() == Punishment.Type.BAN) {
            // Before the kick, so listeners (staff: a ban clears the freeze) act before quit handlers run.
            Map<UUID, String> online = new LinkedHashMap<>();
            affected.forEach(o -> online.put(o.getUniqueId(), o.getName()));
            bannedAccounts(p, online).forEach((id, name) -> Bukkit.getPluginManager().callEvent(
                    new net.skybornmc.skyblock.common.moderation.PlayerBannedEvent(id, name, p.issuerName(), p.id())));
        }
        for (Player online : affected) {
            switch (p.type()) {
                case BAN -> online.kick(banScreen(online, p));
                case KICK -> online.kick(msg.component(online, "screen.kick", parts(online, p)));
                case MUTE -> {
                    cacheMute(online.getUniqueId(), longer(mutes.get(online.getUniqueId()), p));
                    msg.send(online, "muted.you", parts(online, p));
                    if (appealHint.test(p)) {
                        msg.send(online, "appeal.hint", parts(online, p)); // spec 13.10: muted players can /appeal
                    }
                    if (p.automatic() && p.source().startsWith(Punishment.AUTO_PREFIX + Suggestion.KIND_WARN_RULE + ":")) {
                        String rule = p.source().substring(p.source().lastIndexOf(':') + 1);
                        msg.send(online, "repeat".equals(rule) ? "muted.auto-repeat" : "muted.auto", parts(online, p),
                                Msg.p("count", rule));
                    }
                    core.sounds().play(online, "error");
                }
                case WARN -> {
                    msg.send(online, "warned.you", parts(online, p));
                    online.showTitle(net.kyori.adventure.title.Title.title(msg.component(online, "warned.title"),
                            msg.component(online, "warned.subtitle", parts(online, p))));
                    core.sounds().play(online, "warning");
                    if (hooks != null && online.getUniqueId().equals(p.target())) {
                        hooks.warned(online, p); // spec 12.6: [I understand] + rules link
                    }
                }
            }
        }
        if (hooks != null) {
            hooks.applied(p, evidenceEntry, discord); // spec 12.4 evidence (13.11 from a log line), 12.11 Discord
        }
        if (p.type() == Punishment.Type.BAN && config.get().vanillaSync) {
            Date until = p.expires() == null ? null : new Date(p.expires());
            String reason = "Phoenix #" + p.id() + ": " + p.reason();
            if (p.target() != null) {
                Bukkit.getBanList(BanListType.PROFILE).addBan(Bukkit.createProfile(p.target(), p.targetName()), reason, until, p.issuerName());
            }
            if (p.ipWide() && p.ip() != null) {
                try {
                    Bukkit.getBanList(BanListType.IP).addBan(InetAddress.getByName(p.ip()), reason, until, p.issuerName());
                } catch (Exception ignored) {
                    // invalid address: our own table still has the ban
                }
            }
        }
    }

    /**
     * Accounts a ban hits, each once: the target (when known), then the online players it also hits (IP-wide bans).
     * Empty for every other type.
     */
    static Map<UUID, String> bannedAccounts(Punishment p, Map<UUID, String> affectedOnline) {
        Map<UUID, String> out = new LinkedHashMap<>();
        if (p.type() != Punishment.Type.BAN) {
            return out;
        }
        if (p.target() != null) {
            out.put(p.target(), p.targetName());
        }
        affectedOnline.forEach(out::putIfAbsent);
        return out;
    }

    /** Online players affected: the target, plus everyone on the same address for IP-wide punishments. */
    private List<Player> affected(Punishment p) {
        return Bukkit.getOnlinePlayers().stream().map(Player.class::cast).filter(o -> (p.target() != null && o.getUniqueId().equals(p.target()))
                || (p.ipWide() && p.ip() != null && o.getAddress() != null
                && o.getAddress().getAddress().getHostAddress().equals(p.ip()))).toList();
    }

    // ------------------------------------------------------------------ pardon

    /** Pardons active punishments; completes (main thread) with what was pardoned (empty = nothing active). */
    CompletableFuture<List<Punishment>> pardon(CommandSender sender, Punishment.Type type, UUID target, String targetName,
                                               String ip, String reason) {
        UUID by = sender instanceof Player p ? p.getUniqueId() : null;
        return store.pardon(type, target, ip, by, sender.getName(), reason, type == Punishment.Type.WARN)
                .thenApplyAsync(list -> {
                    if (list.isEmpty()) {
                        return list;
                    }
                    if (type == Punishment.Type.MUTE && target != null) {
                        cacheMute(target, null); // also stops the "mute ended" timer (13.13: a pardon is no natural end)
                        Player online = Bukkit.getPlayer(target);
                        if (online != null) {
                            msg.send(online, "muted.unmuted");
                        }
                    }
                    if (type == Punishment.Type.BAN) {
                        liftVanillaBans(target, targetName, list, ip);
                    }
                    String action = switch (type) {
                        case BAN -> "unban";
                        case MUTE -> "unmute";
                        case WARN -> "unwarn";
                        case KICK -> "unkick";
                        default -> "un" + type.key();
                    };
                    notifyStaff(sender, "notify." + action, Msg.p("player", targetName), Msg.p("reason", reason),
                            Msg.p("staff", sender.getName()), pre("target", targetName));
                    if (hooks != null) {
                        hooks.pardoned(list, targetName, sender.getName(), reason);
                    }
                    Map<String, Object> details = new LinkedHashMap<>();
                    details.put("reason", reason);
                    details.put("ids", list.stream().map(Punishment::id).toList());
                    core.audit().log(AuditCategory.PUNISHMENT, action, Actor.of(sender), target == null ? ip : target.toString(),
                            targetName, null, sender instanceof Player p ? p.getLocation() : null, details);
                    return list;
                }, main);
    }

    /**
     * After a ban's end changed (appeal reduced, spec 13.10): the vanilla ban list gets the new end, or the entry is
     * removed when the ban is over and no other ban is active. Main thread.
     */
    void resyncVanillaBan(Punishment p) {
        if (p.type() != Punishment.Type.BAN || !config.get().vanillaSync || p.target() == null) {
            return;
        }
        if (p.active(System.currentTimeMillis())) {
            Date until = p.expires() == null ? null : new Date(p.expires());
            Bukkit.getBanList(BanListType.PROFILE).addBan(Bukkit.createProfile(p.target(), p.targetName()),
                    "Phoenix #" + p.id() + ": " + p.reason(), until, p.issuerName());
            return;
        }
        store.activeBan(p.target(), null).thenAcceptAsync(other -> {
            if (other.isEmpty()) {
                liftVanillaBans(p.target(), p.targetName(), List.of(p), null);
            }
        }, main);
    }

    /** Removes pardoned bans from the vanilla ban list (account and IP-wide entries). Main thread. */
    void liftVanillaBans(UUID target, String targetName, List<Punishment> pardoned, String ip) {
        if (!config.get().vanillaSync) {
            return;
        }
        if (target != null) {
            Bukkit.getBanList(BanListType.PROFILE).pardon(Bukkit.createProfile(target, targetName));
        }
        java.util.Set<String> ips = new java.util.LinkedHashSet<>();
        for (Punishment p : pardoned) {
            if (p.ipWide() && p.ip() != null) {
                ips.add(p.ip());
            }
        }
        if (ip != null) {
            ips.add(ip);
        }
        for (String address : ips) {
            try {
                Bukkit.getBanList(BanListType.IP).pardon(InetAddress.getByName(address));
            } catch (Exception ignored) {
                // not a valid address
            }
        }
    }

    // ------------------------------------------------------------------ undo (spec 12.1)

    /**
     * Sends the staff member who just applied {@code p} a chat line with a clickable [Undo] (works for
     * {@code undo-window}). The click is a server-side callback (no extra command); after the window it says "expired".
     * Bedrock players use the same action from the head of {@code /p} instead. Kicks and console get no line.
     */
    void offerUndo(CommandSender sender, Punishment p) {
        if (!(sender instanceof Player staff) || p.type().instant() || p.target() == null) {
            return;
        }
        Duration window = extras.get().undoWindow();
        Component button = msg.component(staff, "undo.button", Msg.p("window", PunishConfig.text(window)))
                .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(msg.component(staff, "undo.hover",
                        Msg.p("window", PunishConfig.text(window)))))
                .clickEvent(net.kyori.adventure.text.event.ClickEvent.callback(audience -> {
                    if (audience instanceof Player clicker) {
                        undo(clicker, p.id());
                    }
                }, options -> options.uses(net.kyori.adventure.text.event.ClickCallback.UNLIMITED_USES)
                        .lifetime(window.plus(Duration.ofMinutes(10)))));
        msg.send(staff, "undo.offer", parts(staff, p), Msg.c("undo", button), Msg.p("window", PunishConfig.text(window)));
    }

    /** What {@code viewer} could undo on this target right now (head of /p), or {@code null}. */
    Punishment undoable(Player viewer, List<Punishment> history) {
        return Undo.latestUndoable(history, viewer.getUniqueId(), viewer.hasPermission(ModerationModule.PERM_UNDO_OTHERS),
                extras.get().undoWindow(), System.currentTimeMillis());
    }

    /**
     * [Undo] (chat click or the /p head): a normal pardon with the reason "Undone by <staff> (mistake)", allowed for
     * whoever applied it or with {@code skyblock.staff.punish.undo.others}, only inside {@code undo-window}. Main thread
     * in and out.
     */
    CompletableFuture<PunishmentStore.UndoResult> undo(Player staff, long id) {
        long now = System.currentTimeMillis();
        Duration window = extras.get().undoWindow();
        String reason = text(null, "undo.reason", Msg.p("staff", staff.getName()));
        return store.undo(id, staff.getUniqueId(), staff.getName(), staff.hasPermission(ModerationModule.PERM_UNDO_OTHERS),
                window, reason, now).thenApplyAsync(r -> {
                    handleUndo(staff, r, reason, window);
                    return r;
                }, main).exceptionally(e -> {
                    core.plugin().getLogger().log(java.util.logging.Level.WARNING, "Undo failed", e);
                    core.scheduler().ensureMain("moderation:undo", () -> msg.send(staff, "error.failed"));
                    return null;
                });
    }

    private void handleUndo(Player staff, PunishmentStore.UndoResult r, String reason, Duration window) {
        Punishment p = r.punishment();
        switch (r.result()) {
            case NOT_FOUND -> msg.send(staff, "undo.not-found");
            case NOT_UNDOABLE -> msg.send(staff, "undo.not-undoable", parts(staff, p));
            case ALREADY_REMOVED -> msg.send(staff, "undo.already", parts(staff, p),
                    Msg.p("by", p.removedByName() == null ? "?" : p.removedByName()));
            case EXPIRED -> msg.send(staff, "undo.expired", parts(staff, p), Msg.p("window", PunishConfig.text(window)));
            case NOT_YOURS -> msg.send(staff, "undo.not-yours", parts(staff, p));
            case OK -> {
                lift(p);
                notifyStaff(staff, "undo.notify", parts(staff, p), Msg.p("staff", staff.getName()),
                        Msg.p("by", p.issuerName()));
                Map<String, Object> details = new LinkedHashMap<>();
                details.put("id", p.id());
                details.put("type", p.type().name().toLowerCase(Locale.ROOT));
                details.put("reason", reason);
                details.put("issuer", p.issuerName());
                if (!r.removedSuggestions().isEmpty()) {
                    details.put("removed_suggestions", r.removedSuggestions());
                }
                core.audit().log(AuditCategory.PUNISHMENT, "undo", Actor.of(staff), p.target() == null ? p.ip() : p.target().toString(),
                        p.targetName(), null, staff.getLocation(), details);
                if (hooks != null) {
                    hooks.undone(p, staff.getName(), reason);
                }
                core.sounds().play(staff, "success");
                return;
            }
        }
        core.sounds().play(staff, "error");
    }

    /**
     * One punishment of a bulk action undone with [Undo all] (spec 13.8): the same checks and pardon as [Undo], the effects
     * end at once and it is audited, but there is no chat line per punishment (the bulk action sends one summary).
     */
    CompletableFuture<PunishmentStore.UndoResult> undoQuiet(Player staff, long id, String group) {
        long now = System.currentTimeMillis();
        Duration window = extras.get().undoWindow();
        String reason = text(null, "undo.reason", Msg.p("staff", staff.getName()));
        return store.undo(id, staff.getUniqueId(), staff.getName(), staff.hasPermission(ModerationModule.PERM_UNDO_OTHERS),
                window, reason, now).thenApplyAsync(r -> {
                    if (r.result() == Undo.Result.OK) {
                        Punishment p = r.punishment();
                        lift(p);
                        Map<String, Object> details = new LinkedHashMap<>();
                        details.put("id", p.id());
                        details.put("type", p.type().name().toLowerCase(Locale.ROOT));
                        details.put("reason", reason);
                        details.put("issuer", p.issuerName());
                        details.put("bulk", group);
                        if (!r.removedSuggestions().isEmpty()) {
                            details.put("removed_suggestions", r.removedSuggestions());
                        }
                        core.audit().log(AuditCategory.PUNISHMENT, "undo", Actor.of(staff), p.target() == null ? p.ip()
                                : p.target().toString(), p.targetName(), null, staff.getLocation(), details);
                    }
                    return r;
                }, main);
    }

    /** Ends the effects of an undone punishment at once: unmute/unban (unless another one is still active), tell the player. */
    void lift(Punishment p) {
        UUID target = p.target();
        switch (p.type()) {
            case MUTE -> store.activeMute(target, null).thenAcceptAsync(other -> {
                cacheMute(target, other.orElse(null));
                Player online = Bukkit.getPlayer(target);
                if (online != null) {
                    msg.send(online, other.isPresent() ? "undo.player-mute-still" : "undo.player-mute", parts(online, p));
                }
            }, main);
            case BAN -> store.activeBan(target, null).thenAcceptAsync(other -> {
                if (other.isEmpty()) {
                    liftVanillaBans(target, p.targetName(), List.of(p), null);
                }
            }, main);
            case WARN -> {
                Player online = Bukkit.getPlayer(target);
                if (online != null) {
                    msg.send(online, "undo.player-warn", parts(online, p));
                }
            }
            case ISLAND_BAN, COOP_BLOCK -> {
                islandPunish.lift(p, Actor.SYSTEM);
                Player online = Bukkit.getPlayer(target);
                if (online != null) {
                    msg.send(online, "undo.player-island", parts(online, p));
                }
            }
            default -> {
                // kicks, island kicks and coop removals happen once and are never undone
            }
        }
    }

    // ------------------------------------------------------------------ /pstats (spec 12.10)

    /** Counts of one staff member for a period (cached {@code stats.cache}). */
    @SuppressWarnings("unchecked")
    CompletableFuture<PunishStats.Counts> stats(UUID staff, PunishStats.Period period) {
        PunishConfig.Extras x = extras.get();
        statsCache.ttl(x.statsCache());
        String key = "own:" + staff + ":" + period.id;
        CompletableFuture<PunishStats.Counts> f = (CompletableFuture<PunishStats.Counts>) statsCache.get(key, () ->
                store.statRows(staff, period.start(System.currentTimeMillis(), x.statsZone())).thenApply(PunishStats.Counts::of));
        f.whenComplete((v, e) -> {
            if (e != null) {
                statsCache.invalidate(key);
            }
        });
        return f;
    }

    /** The staff ranking for a period (cached {@code stats.cache}). */
    @SuppressWarnings("unchecked")
    CompletableFuture<List<PunishStats.Ranked>> statsTop(PunishStats.Period period) {
        PunishConfig.Extras x = extras.get();
        statsCache.ttl(x.statsCache());
        String key = "top:" + period.id + ":" + x.statsTop();
        CompletableFuture<List<PunishStats.Ranked>> f = (CompletableFuture<List<PunishStats.Ranked>>) statsCache.get(key, () ->
                store.topRows(period.start(System.currentTimeMillis(), x.statsZone()))
                        .thenApply(t -> PunishStats.rank(t.names(), t.rows(), x.statsTop())));
        f.whenComplete((v, e) -> {
            if (e != null) {
                statsCache.invalidate(key);
            }
        });
        return f;
    }

    // ------------------------------------------------------------------ mutes

    Optional<Punishment> mute(UUID player) {
        Punishment p = mutes.get(player);
        if (p != null && !p.active(System.currentTimeMillis())) {
            mutes.remove(player);
            return Optional.empty();
        }
        return Optional.ofNullable(p);
    }

    void cacheMute(UUID player, Punishment p) {
        if (p == null) {
            mutes.remove(player);
        } else {
            mutes.put(player, p);
        }
        // spec 13.13: (re)schedule the "mute ended" timer for online players (the pre-login check runs before they are)
        if (Bukkit.isPrimaryThread() && Bukkit.getPlayer(player) != null) {
            trackMute(player);
        }
    }

    /** The one that lasts longer (an inactive one never wins). */
    static Punishment longer(Punishment current, Punishment candidate) {
        long now = System.currentTimeMillis();
        if (current == null || !current.active(now)) {
            return candidate;
        }
        return PunishDecision.covers(current.expires(), candidate.expires()) ? current : candidate;
    }

    void forget(UUID player) {
        mutes.remove(player);
        muteTimers.cancel(player);
    }

    // ------------------------------------------------------------------ texts

    /** Placeholders describing a punishment for {@code viewer}. */
    TagResolver parts(org.bukkit.entity.Player viewer, Punishment p) {
        long now = System.currentTimeMillis();
        String expires = p.expires() == null ? text(viewer, "words.permanent")
                : Format.duration(Duration.ofMillis(Math.max(1000, p.expires() - now)));
        String length = p.expires() == null ? text(viewer, "words.permanent")
                : Format.duration(Duration.ofMillis(p.expires() - p.created()));
        return TagResolver.resolver(
                Msg.p("id", p.id()), Msg.p("reason", p.reason()), Msg.p("player", p.targetName()),
                Msg.p("staff", config.get().showStaff ? p.issuerName() : text(viewer, "words.staff")),
                Msg.p("staff_real", p.issuerName()),
                Msg.p("expires", expires), Msg.p("length", length),
                Msg.p("date", DATE.withZone(Format.zone()).format(Instant.ofEpochMilli(p.created()))),
                Msg.p("type", text(viewer, "types." + p.type().name().toLowerCase(Locale.ROOT))),
                pre("target", p.targetName()), pre("appeal", appealUrl()), ruleParts(viewer, p.template()));
    }

    // ------------------------------------------------------------------ training (P8, spec 14.6)

    /**
     * A trainee applied something directly (a warning, or any kind not in training.requests-for): approvers who get
     * {@code approvals} alerts see it with an [Undo] that works for any approver for {@code approval.expire} (spec: "can be
     * undone by any approver"). Kicks can't be undone, so they get the line without the button.
     */
    private void traineeApplied(CommandSender sender, Punishment p) {
        Duration window = extras.get().approval().expire();
        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (staff.equals(sender) || !staff.hasPermission(ModerationModule.PERM_APPROVE) || trainee(staff)
                    || !wants(staff, NotifyRules.Type.APPROVALS)) {
                continue;
            }
            Component button = p.type().instant() ? Component.empty() : msg.component(staff, "training.undo-button")
                    .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(msg.component(staff, "training.undo-hover",
                            parts(staff, p), Msg.p("window", PunishConfig.text(window)))))
                    .clickEvent(net.kyori.adventure.text.event.ClickEvent.callback(audience -> {
                        if (audience instanceof Player clicker) {
                            undoTrainee(clicker, p.id());
                        }
                    }, options -> options.uses(net.kyori.adventure.text.event.ClickCallback.UNLIMITED_USES)
                            .lifetime(window.plus(Duration.ofMinutes(10)))));
            msg.send(staff, "training.applied", parts(staff, p), Msg.p("staff", p.issuerName()), Msg.c("undo", button));
        }
    }

    /** [Undo] on a trainee's punishment: any approver (not a trainee), within {@code approval.expire}. */
    CompletableFuture<PunishmentStore.UndoResult> undoTrainee(Player staff, long id) {
        if (!staff.hasPermission(ModerationModule.PERM_APPROVE) || trainee(staff)) {
            msg.send(staff, "approval.no-permission");
            core.sounds().play(staff, "error");
            return CompletableFuture.completedFuture(null);
        }
        long now = System.currentTimeMillis();
        Duration window = extras.get().approval().expire();
        String reason = text(null, "undo.reason", Msg.p("staff", staff.getName()));
        return store.undo(id, staff.getUniqueId(), staff.getName(), true, window, reason, now).thenApplyAsync(r -> {
            handleUndo(staff, r, reason, window);
            return r;
        }, main).exceptionally(e -> {
            core.plugin().getLogger().log(java.util.logging.Level.WARNING, "Undo failed", e);
            core.scheduler().ensureMain("moderation:undo", () -> msg.send(staff, "error.failed"));
            return null;
        });
    }

    /**
     * A dry run (spec 14.6): tells {@code sender} what the pipeline would do, logs it as training (audit
     * {@code dry_run} + {@code punish_dry_runs}) and shows trainees' dry runs to approvers. Applies nothing: no
     * punishment, request or suggestion is written and the target is never told. Main thread.
     */
    void dryRun(CommandSender sender, UUID target, String targetName, String reasonKey, String reasonName, DryRun.Report report) {
        Player viewer = sender instanceof Player p ? p : null;
        boolean isTrainee = trainee(sender);
        TagResolver tags = TagResolver.resolver(Msg.p("player", targetName), pre("target", targetName),
                Msg.p("reason", reasonName), Msg.p("action", stepText(viewer, report.step())),
                Msg.number("offence", report.offence()), Msg.number("bonus", report.probationBonus()),
                Msg.p("offence-text", report.offence() <= 0 ? "" : text(viewer, "dry-run.offence",
                        Msg.number("offence", report.offence()))),
                Msg.p("why", report.why() == null ? "" : text(viewer, "dry-run.why." + report.why())),
                Msg.p("staff", sender.getName()), ruleParts(viewer, reasonKey));
        msg.send(sender, "dry-run.result." + report.outcomeId(), tags);
        if (report.probationBonus() > 0) {
            msg.send(sender, "dry-run.probation", tags);
        }
        msg.send(sender, isTrainee ? "dry-run.footer-trainee" : "dry-run.footer", tags);
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("outcome", report.outcomeId());
        details.put("action", report.step().describe());
        details.put("offence", report.offence());
        if (reasonKey != null) {
            details.put("template", reasonKey);
        }
        if (report.why() != null) {
            details.put("why", report.why());
        }
        details.put("trainee", isTrainee);
        core.audit().log(AuditCategory.PUNISHMENT, "dry_run", Actor.of(sender), target == null ? null : target.toString(),
                targetName, null, viewer == null ? null : viewer.getLocation(), details);
        if (notifier != null) {
            notifier.store().logDryRun(new NotifyStore.DryRunRow(viewer == null ? null : viewer.getUniqueId(), sender.getName(),
                    isTrainee, target, targetName, reasonKey, report.outcomeId().toUpperCase(Locale.ROOT),
                    report.step().describe(), System.currentTimeMillis())).exceptionally(e -> null);
        }
        if (isTrainee) {
            // "visible to seniors": mentors who get approvals alerts see a trainee's practice too
            for (Player staff : Bukkit.getOnlinePlayers()) {
                if (!staff.equals(sender) && staff.hasPermission(ModerationModule.PERM_APPROVE) && !trainee(staff)
                        && wants(staff, NotifyRules.Type.APPROVALS)) {
                    msg.send(staff, "dry-run.mentor", tags, Msg.p("outcome", text(staff, "dry-run.outcome." + report.outcomeId())));
                }
            }
        }
    }

    // ------------------------------------------------------------------ rule numbers (P8, spec 14.2)

    /**
     * {@code <rule>} ("3" or "1, 3", empty without a rule), {@code <rule-title>} (titles joined with " / "),
     * {@code <rule-link>} (the rule's link, else extras.rules-link; safe inside click tags) and {@code <rule-text>}
     * ("Rule 3: No spam or advertising", or the rules.none text) for a stored reason id / warn-button id.
     */
    TagResolver ruleParts(Player viewer, String reasonId) {
        RuleBook book = ruleBook();
        String number = book.numberText(reasonId);
        String title = book.titleText(reasonId, r -> ruleTitle(viewer, r));
        String link = book.link(reasonId, extras.get().rulesLink());
        String ruleText = number.isEmpty() ? text(viewer, "rules.none")
                : text(viewer, book.numbers(reasonId).size() == 1 ? "rules.text" : "rules.text-many",
                Msg.p("rule", number), Msg.p("rule-title", title));
        return TagResolver.resolver(Msg.p("rule", number), Msg.p("rule-title", title), pre("rule-link", link),
                Msg.p("rule-text", ruleText));
    }

    /** One rule's title: messages rules.titles.<n>, else the legacy YAML title, else "Rule n". */
    String ruleTitle(Player viewer, RuleBook.Rule r) {
        String key = "rules.titles." + r.number();
        if (msg.has(key)) {
            return text(viewer, key);
        }
        return r.yamlTitle() != null ? r.yamlTitle() : text(viewer, "rules.untitled", Msg.p("rule", r.number()));
    }

    Component banScreen(Player viewer, Punishment p) {
        return msg.component(viewer, p.expires() == null ? "screen.ban" : "screen.tempban", parts(viewer, p));
    }

    /** Ban screen for the pre-login check (no Player object yet). */
    Component banScreen(Punishment p) {
        return msg.component(Bukkit.getConsoleSender(), p.expires() == null ? "screen.ban" : "screen.tempban", parts(null, p));
    }

    String text(Player viewer, String key, TagResolver... placeholders) {
        return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(msg.component(viewer == null ? Bukkit.getConsoleSender() : viewer, key, placeholders));
    }

    /** Appeal link: moderation.yml appeal-url, else social.yml links.discord. */
    String appealUrl() {
        String own = config.get().appealUrl;
        if (own != null && !own.isBlank()) {
            return own;
        }
        ModuleConfig social = core.configs().loaded().get("social.yml");
        if (social != null) {
            for (SettingInfo info : core.configs().describe(social)) {
                if (info.path().equals("links.discord")) {
                    return ConfigManager.display(info.value());
                }
            }
        }
        return "discord.skybornmc.net";
    }

    /** Placeholder replaced before parsing (works inside click/hover arguments). Safe values only (names, ids, links). */
    static TagResolver pre(String name, Object value) {
        String safe = String.valueOf(value).replace("'", "").replace("<", "").replace(">", "").replace("\\", "");
        return TagResolver.resolver(name, net.kyori.adventure.text.minimessage.tag.Tag.preProcessParsed(safe));
    }

    // ------------------------------------------------------------------ notifications

    private void announce(CommandSender sender, Punishment p) {
        String type = p.type().key();
        String key = p.type().island() ? "notify." + type
                : "notify." + (p.expires() != null && (p.type() == Punishment.Type.BAN || p.type() == Punishment.Type.MUTE)
                ? "temp" + type : (p.ipWide() ? "ip" + type : type));
        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (wants(staff, NotifyRules.Type.PUNISHMENTS) || staff.equals(sender)) {
                msg.send(staff, key, parts(staff, p), Msg.p("staff", p.issuerName()), Msg.p("silent", p.silent() ? text(staff, "words.silent") : ""));
            }
        }
        if (!(sender instanceof Player)) {
            msg.send(sender, key, parts(null, p), Msg.p("staff", p.issuerName()), Msg.p("silent", p.silent() ? "(silent)" : ""));
        }
        announcePublic(p);
    }

    /** The public "Steve was muted" line (bans and mutes, not silent, {@code announce.public}). */
    private void announcePublic(Punishment p) {
        boolean publicType = p.type() == Punishment.Type.BAN || p.type() == Punishment.Type.MUTE;
        if (config.get().announcePublic && !p.silent() && publicType) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (!player.hasPermission(ModerationModule.PERM_NOTIFY)) {
                    msg.send(player, "public." + (p.type() == Punishment.Type.BAN ? "ban" : "mute"), parts(player, p));
                }
            }
        }
    }

    /** A punishment-feed line (type {@code punishments}, spec 14.9), plus the sender (console included). */
    void notifyStaff(CommandSender sender, String key, TagResolver... placeholders) {
        notifyStaff(NotifyRules.Type.PUNISHMENTS, sender, key, placeholders);
    }

    /**
     * To staff who get alerts of {@code type} (permission {@code skyblock.staff.notify.<type>} and their /pnotify switch,
     * spec 14.9), plus the sender (console included). {@code sender == null}: console log.
     */
    void notifyStaff(NotifyRules.Type type, CommandSender sender, String key, TagResolver... placeholders) {
        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (wants(staff, type) || staff.equals(sender)) {
                msg.send(staff, key, placeholders);
            }
        }
        if (!(sender instanceof Player)) {
            msg.send(sender == null ? Bukkit.getConsoleSender() : sender, key, placeholders);
        }
    }

    private void audit(CommandSender sender, Punishment p) {
        auditPunishment(Actor.of(sender), sender instanceof Player pl ? pl.getLocation() : null, p);
    }

    private void auditPunishment(Actor actor, org.bukkit.Location where, Punishment p) {
        String action = switch (p.type()) {
            case BAN -> p.ipWide() ? "ipban" : p.expires() == null ? "ban" : "tempban";
            case MUTE -> p.expires() == null ? "mute" : "tempmute";
            case WARN -> "warn";
            case KICK -> "kick";
            case ISLAND_KICK -> "island_kick";
            case ISLAND_BAN -> "island_ban";
            case COOP_REMOVE -> "coop_remove";
            case COOP_BLOCK -> "coop_block";
        };
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("id", p.id());
        details.put("reason", p.reason());
        if (p.expires() != null) {
            details.put("expires", p.expires());
        }
        if (p.template() != null) {
            details.put("template", p.template());
            details.put("step", p.ladderStep());
        }
        if (p.silent()) {
            details.put("silent", true);
        }
        if (p.type() == Punishment.Type.WARN && p.weight() != Weights.ONE) {
            details.put("weight", Weights.text(p.weight()));
        }
        if (p.ip() != null) {
            details.put("ip", p.ip());
        }
        if (p.source() != null) {
            details.put("source", p.source());
        }
        if (p.approvedByName() != null) {
            details.put("issuer", p.issuerName());
            details.put("approved_by", p.approvedByName());
        }
        core.audit().log(AuditCategory.PUNISHMENT, action, actor,
                p.target() == null ? p.ip() : p.target().toString(), p.targetName(),
                p.expires() == null ? null : p.expires() - p.created(), where, details);
    }

    // ================================================================== P4: approval (spec 13.4)

    /** Opens the deny-reason menu for an approver (set by the module: menus live there). */
    private java.util.function.BiConsumer<Player, Long> denyOpener = (p, id) -> { };

    void denyOpener(java.util.function.BiConsumer<Player, Long> opener) {
        this.denyOpener = opener;
    }

    /** Placeholders of a request for chat lines and menus. */
    TagResolver requestParts(Player viewer, PunishRequest r) {
        long now = System.currentTimeMillis();
        return TagResolver.resolver(Msg.p("player", r.targetName()), pre("target", r.targetName()), Msg.p("rid", r.id()),
                Msg.p("action", stepText(viewer, r.step())), Msg.p("reason", r.reason()), Msg.p("staff", r.requesterName()),
                Msg.p("handler", r.handlerName() == null ? text(viewer, "words.system") : r.handlerName()),
                Msg.p("deny_reason", r.denyReason() == null ? "-" : r.denyReason()),
                Msg.p("ago", Format.duration(Duration.ofMillis(Math.max(1000, now - r.created())))),
                Msg.p("expires", Format.duration(Duration.ofMillis(Math.max(1000, r.expires() - now)))),
                Msg.number("merges", r.merges()),
                Msg.p("trainee", r.requesterTrainee() ? text(viewer, "training.tag") : ""),
                Msg.p("status", text(viewer, "approval.status." + (r.expired(now) ? "expired"
                        : r.status().name().toLowerCase(Locale.ROOT)))),
                Msg.p("date", DATE.withZone(Format.zone()).format(Instant.ofEpochMilli(r.created()))));
    }

    /** A request was created (or merged): tell the requester, audit, and tell the approvers about a new one. */
    private void requested(CommandSender sender, PunishRequest r, boolean merged) {
        requested(sender, r, merged, true);
    }

    private void requested(CommandSender sender, PunishRequest r, boolean merged, boolean tellRequester) {
        Player viewer = sender instanceof Player p ? p : null;
        if (tellRequester) {
            msg.send(sender, merged ? "approval.merged" : "approval.requested", requestParts(viewer, r));
            if (viewer != null) {
                core.sounds().play(viewer, "success");
            }
        }
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("request", r.id());
        details.put("action", r.step().describe());
        details.put("reason", r.reason());
        if (r.template() != null) {
            details.put("template", r.template());
        }
        if (merged) {
            details.put("merges", r.merges());
        }
        core.audit().log(AuditCategory.PUNISHMENT, merged ? "approval_merge" : "approval_request", Actor.of(sender),
                r.target() == null ? r.ip() : r.target().toString(), r.targetName(), r.durationMs(),
                viewer == null ? null : viewer.getLocation(), details);
        if (!merged) {
            notifyApprovers(r);
        }
    }

    /** Who may approve {@code r}: approvers, not the requester (unless {@code approval.self-approve}). */
    boolean mayApprove(Player staff, PunishRequest r) {
        return staff.hasPermission(ModerationModule.PERM_APPROVE) && !trainee(staff) // 14.6: trainees never approve
                && (extras.get().approval().selfApprove() || !staff.getUniqueId().equals(r.requester()));
    }

    /** "Alex requests: ban perm for Steve (Hacked client). [Approve] [Deny] [Open /p]" to every approver online. */
    void notifyApprovers(PunishRequest r) {
        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (mayApprove(staff, r) && wants(staff, NotifyRules.Type.APPROVALS)) {
                msg.send(staff, "approval.notify", requestParts(staff, r), Msg.c("approve", approveButton(staff, r)),
                        Msg.c("deny", denyButton(staff, r)));
                core.sounds().play(staff, "notify");
            }
        }
        msg.send(Bukkit.getConsoleSender(), "approval.notify", requestParts(null, r), Msg.p("approve", ""), Msg.p("deny", ""));
    }

    private net.kyori.adventure.text.event.ClickEvent<?> callback(java.util.function.Consumer<Player> action) {
        return net.kyori.adventure.text.event.ClickEvent.callback(audience -> {
            if (audience instanceof Player clicker) {
                action.accept(clicker);
            }
        }, options -> options.uses(net.kyori.adventure.text.event.ClickCallback.UNLIMITED_USES)
                .lifetime(extras.get().approval().expire().plus(Duration.ofMinutes(10))));
    }

    /** Clickable [Approve] (a server-side callback, no command; the /p head is the Bedrock path). */
    Component approveButton(Player staff, PunishRequest r) {
        return msg.component(staff, "approval.approve-button")
                .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(msg.component(staff, "approval.approve-hover",
                        requestParts(staff, r))))
                .clickEvent(callback(p -> approve(p, r.id())));
    }

    /** Clickable [Deny]: opens the deny-reason menu. */
    Component denyButton(Player staff, PunishRequest r) {
        return msg.component(staff, "approval.deny-button")
                .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(msg.component(staff, "approval.deny-hover",
                        requestParts(staff, r))))
                .clickEvent(callback(p -> {
                    if (p.hasPermission(ModerationModule.PERM_APPROVE)) {
                        denyOpener.accept(p, r.id());
                    } else {
                        msg.send(p, "approval.no-permission");
                    }
                }));
    }

    /**
     * [Approve] (chat click or the request menu): applies the request as the requester, approved by {@code approver}, with
     * the approver's own limits as the hard maximum. Main thread in and out.
     */
    CompletableFuture<PunishmentStore.RequestResult> approve(Player approver, long id) {
        if (!approver.hasPermission(ModerationModule.PERM_APPROVE) || trainee(approver)) {
            msg.send(approver, "approval.no-permission");
            core.sounds().play(approver, "error");
            return CompletableFuture.completedFuture(null);
        }
        long now = System.currentTimeMillis();
        PunishConfig.Extras x = extras.get();
        return store.request(id).thenComposeAsync(found -> {
            if (found.isEmpty()) {
                return CompletableFuture.completedFuture(new PunishmentStore.RequestResult(PunishmentStore.RequestKind.NOT_FOUND,
                        null, null, null));
            }
            PunishRequest r = found.get();
            PunishDecision.Input in = input(approver, r.target(), r.targetName(), r.type(), r.duration(), r.ipWide(), r.silent(), now);
            return store.approve(id, q -> in, decision(), approver.getUniqueId(), approver.getName(), x.approval().selfApprove(), now);
        }, main).thenApplyAsync(res -> {
            handleRequest(approver, res);
            return res;
        }, main).exceptionally(e -> {
            core.plugin().getLogger().log(java.util.logging.Level.WARNING, "Approve failed", e);
            core.scheduler().ensureMain("moderation:approve", () -> msg.send(approver, "error.failed"));
            return null;
        });
    }

    /** [Deny] with a reason (preset or typed). The requester is told. Main thread in and out. */
    CompletableFuture<PunishmentStore.RequestResult> deny(Player staff, long id, String reason) {
        if (!staff.hasPermission(ModerationModule.PERM_APPROVE) || trainee(staff)) {
            msg.send(staff, "approval.no-permission");
            core.sounds().play(staff, "error");
            return CompletableFuture.completedFuture(null);
        }
        String clean = reason == null ? "" : Notes.clean(reason);
        if (clean.isEmpty()) {
            msg.send(staff, "approval.deny-empty");
            core.sounds().play(staff, "error");
            return CompletableFuture.completedFuture(null);
        }
        return store.deny(id, staff.getUniqueId(), staff.getName(), clean.length() <= 200 ? clean : clean.substring(0, 200),
                System.currentTimeMillis()).thenApplyAsync(res -> {
                    handleRequest(staff, res);
                    return res;
                }, main).exceptionally(e -> {
                    core.plugin().getLogger().log(java.util.logging.Level.WARNING, "Deny failed", e);
                    core.scheduler().ensureMain("moderation:deny", () -> msg.send(staff, "error.failed"));
                    return null;
                });
    }

    private void handleRequest(Player staff, PunishmentStore.RequestResult res) {
        PunishRequest r = res.request();
        switch (res.kind()) {
            case NOT_FOUND -> msg.send(staff, "approval.not-found");
            case EXPIRED -> msg.send(staff, "approval.expired", requestParts(staff, r));
            case ALREADY_HANDLED -> msg.send(staff, "approval.already", requestParts(staff, r));
            case OWN_REQUEST -> msg.send(staff, "approval.own", requestParts(staff, r));
            case BLOCKED -> tell(staff, res.verdict(), r.targetName());
            case SKIPPED -> {
                msg.send(staff, "approval.skipped", requestParts(staff, r),
                        Msg.p("left", res.verdict() == null ? "?" : res.verdict().args().getOrDefault("left", "?")));
                auditRequest(staff, "approval_skip", r, null);
                tellRequester(r);
                core.sounds().play(staff, "success");
                return;
            }
            case APPROVED -> {
                Punishment p = res.punishment();
                apply(p, r.evidenceEntry(), true); // spec 13.11: a request made from a /logs line keeps that line
                announce(staff, p);
                audit(staff, p);
                offerUndo(staff, p);
                if (hooks != null) {
                    hooks.linkReports(staff, p);
                }
                notifyStaff(NotifyRules.Type.APPROVALS, staff, "approval.approved", requestParts(staff, r), Msg.p("id", p.id()));
                auditRequest(staff, "approval_approve", r, p.id());
                tellRequester(r);
                core.sounds().play(staff, "success");
                return;
            }
            case DENIED -> {
                for (Player other : Bukkit.getOnlinePlayers()) {
                    if ((other.hasPermission(ModerationModule.PERM_APPROVE) && wants(other, NotifyRules.Type.APPROVALS))
                            || other.equals(staff)) {
                        msg.send(other, "approval.denied", requestParts(other, r));
                    }
                }
                auditRequest(staff, "approval_deny", r, null);
                tellRequester(r);
                core.sounds().play(staff, "success");
                return;
            }
        }
        core.sounds().play(staff, "error");
    }

    private void auditRequest(Player staff, String action, PunishRequest r, Long punishmentId) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("request", r.id());
        details.put("action", r.step().describe());
        details.put("reason", r.reason());
        details.put("requester", r.requesterName());
        if (r.denyReason() != null) {
            details.put("deny_reason", r.denyReason());
        }
        if (punishmentId != null) {
            details.put("id", punishmentId);
        }
        core.audit().log(AuditCategory.PUNISHMENT, action, Actor.of(staff), r.target() == null ? r.ip() : r.target().toString(),
                r.targetName(), r.durationMs(), staff.getLocation(), details);
    }

    /** Message key of a handled (or expired) request for the requester. */
    static String resultKey(PunishRequest r, long now) {
        if (r.expired(now)) {
            return "approval.result.expired";
        }
        return "approval.result." + r.status().name().toLowerCase(Locale.ROOT);
    }

    /** Tells the requester the outcome now if online (marked told), else they get it on their next join. */
    private void tellRequester(PunishRequest r) {
        Player requester = r.requester() == null ? null : Bukkit.getPlayer(r.requester());
        if (requester == null) {
            return;
        }
        store.markNotified(r.id()).thenAcceptAsync(first -> {
            if (Boolean.TRUE.equals(first) && requester.isOnline()) {
                msg.send(requester, resultKey(r, System.currentTimeMillis()), requestParts(requester, r));
            }
        }, main);
    }

    /** On join: outcomes of your requests you were not told yet; for approvers, the requests waiting. */
    void requestsOnJoin(Player player) {
        long now = System.currentTimeMillis();
        store.requesterUpdates(player.getUniqueId(), now).thenAcceptAsync(list -> {
            for (PunishRequest r : list) {
                if (player.isOnline()) {
                    msg.send(player, resultKey(r, now), requestParts(player, r));
                }
            }
        }, main).exceptionally(e -> null);
        if (!player.hasPermission(ModerationModule.PERM_APPROVE)) {
            return;
        }
        int shown = 5;
        store.pendingRequests(now, 50).thenAcceptAsync(all -> {
            List<PunishRequest> mine = all.stream().filter(r -> mayApprove(player, r)).toList();
            if (mine.isEmpty() || !player.isOnline()) {
                return;
            }
            msg.send(player, "approval.join-header", Msg.number("count", mine.size()));
            for (PunishRequest r : mine.stream().limit(shown).toList()) {
                msg.send(player, "approval.join-line", requestParts(player, r), Msg.c("approve", approveButton(player, r)),
                        Msg.c("deny", denyButton(player, r)));
            }
            if (mine.size() > shown) {
                msg.send(player, "approval.join-more", Msg.number("more", mine.size() - shown));
            }
        }, main).exceptionally(e -> null);
    }

    // ================================================================== P4: "mute ended" (13.13) + probation notice (13.7)

    /** Whether a newly muted player is told they can /appeal (spec 13.10; set by the module). */
    private java.util.function.Predicate<Punishment> appealHint = p -> false;

    void appealHint(java.util.function.Predicate<Punishment> source) {
        this.appealHint = source;
    }

    /** Whether a stored reason id starts probation (set by the module from punishments.yml). */
    private java.util.function.Predicate<String> probationReasons = t -> true;

    void probationReasons(java.util.function.Predicate<String> source) {
        this.probationReasons = source;
    }

    /** Until when the player is on probation ({@code null} = not), from their loaded history. */
    Long probationUntil(List<Punishment> history) {
        return Probation.until(history, probationReasons, extras.get().probation(), System.currentTimeMillis());
    }

    /** One timer per online player with a timed mute, at its end (no database polling). */
    private final EndTimers muteTimers;

    EndTimers muteTimers() {
        return muteTimers;
    }

    /** (Re)schedules the player's "mute ended" timer from the cached mute. Main thread. */
    void trackMute(UUID player) {
        Punishment p = mutes.get(player);
        if (p == null || p.expires() == null) {
            muteTimers.cancel(player);
            return;
        }
        muteTimers.track(player, p.id(), p.expires());
    }

    /** Stops every timer (module disabled). */
    void close() {
        muteTimers.clear();
    }

    /** A timed mute of an online player reached its end. Main thread. */
    private void muteEnded(UUID player, long muteId) {
        long now = System.currentTimeMillis();
        Punishment cached = mutes.get(player);
        if (cached != null && cached.id() != muteId && cached.active(now)) {
            trackMute(player); // another, longer mute is in the cache: it has its own timer
            return;
        }
        mutes.remove(player);
        Player online = Bukkit.getPlayer(player);
        if (online == null) {
            return;
        }
        String ip = online.getAddress() == null ? null : online.getAddress().getAddress().getHostAddress();
        store.activeMute(player, ip).thenAcceptAsync(other -> {
            if (other.isPresent()) {
                cacheMute(player, other.get()); // still muted by another one (e.g. IP-wide): no message yet
                return;
            }
            if (online.isOnline()) {
                endNotices(online, true, false);
            }
        }, main).exceptionally(e -> null);
    }

    /**
     * Sends the "Your mute has ended" / "Welcome back" line once (claimed in the database, so it never repeats), combined
     * with the probation notice when probation is running (spec 13.13: one message). {@code mutes}: claim ended mutes
     * (only when no mute is active), {@code bans}: claim ended bans (on join).
     */
    void endNotices(Player player, boolean mutes, boolean bans) {
        PunishConfig.Extras x = extras.get();
        if (!x.muteEnded().enabled()) {
            return;
        }
        UUID id = player.getUniqueId();
        long now = System.currentTimeMillis();
        store.claimEndNotices(id, now, mutes, bans).thenCompose(claimed -> claimed.isEmpty()
                        ? CompletableFuture.completedFuture(Map.entry(claimed, List.<Punishment>of()))
                        : store.history(id).thenApply(h -> Map.entry(claimed, h)))
                .thenAcceptAsync(data -> {
                    List<Punishment> claimed = data.getKey();
                    if (claimed.isEmpty()) {
                        return;
                    }
                    boolean ban = claimed.stream().anyMatch(p -> p.type() == Punishment.Type.BAN);
                    Long until = Probation.until(data.getValue(), probationReasons, x.probation(), now);
                    boolean probation = until != null && x.probation().stepBonus() > 0;
                    if (player.isOnline()) {
                        msg.send(player, "ended." + (ban ? "ban" : "mute") + (probation ? "-probation" : ""),
                                Msg.p("until", probation ? ModerationModule.day(until) : "-"), pre("rules", x.rulesLink()));
                        if (!x.muteEnded().sound().isEmpty()) {
                            core.sounds().play(player, x.muteEnded().sound());
                        }
                    }
                    Map<String, Object> details = new LinkedHashMap<>();
                    details.put("ids", claimed.stream().map(Punishment::id).toList());
                    if (probation) {
                        details.put("probation_until", until);
                    }
                    core.audit().log(AuditCategory.PUNISHMENT, ban ? "ban_end_notice" : "mute_end_notice", Actor.SYSTEM,
                            id.toString(), player.getName(), null, null, details);
                }, main).exceptionally(e -> {
                    core.plugin().getLogger().log(java.util.logging.Level.WARNING, "End notice failed", e);
                    return null;
                });
    }
}
