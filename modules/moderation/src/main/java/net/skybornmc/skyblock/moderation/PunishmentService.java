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
import java.time.ZoneId;
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

    static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.ENGLISH)
            .withZone(ZoneId.systemDefault());

    /** What a staff member asked for. {@code duration == null} = permanent (ban/mute). */
    record Request(Punishment.Type type, UUID target, String targetName, Duration duration, String reason, boolean silent,
                   boolean ipWide, String ip, String template, Integer ladderStep, String source) {

        Request(Punishment.Type type, UUID target, String targetName, Duration duration, String reason, boolean silent,
                boolean ipWide, String ip, String template, Integer ladderStep) {
            this(type, target, targetName, duration, reason, silent, ipWide, ip, template, ladderStep, null);
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
    }

    PunishmentStore store() {
        return store;
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

    private final PunishDecision decision = PunishDecision.standard();
    private java.util.function.Supplier<WarnRules.Settings> warnRules = () -> WarnRules.Settings.DISABLED;

    void warnRules(java.util.function.Supplier<WarnRules.Settings> source) {
        this.warnRules = source;
    }

    PunishDecision decision() {
        return decision;
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
                maxDuration(sender), sender.hasPermission(ModerationModule.PERM_EXEMPT_BYPASS));
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
        return decision.decide(input(viewer, target, targetName, step.type(), step.duration(), step.ipWide(), silent,
                System.currentTimeMillis()).withActive(activeMute, activeBan));
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
        return new PunishmentStore.RuleHook(settings, decision, template, auto, trigger -> trigger.repeat()
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
        PunishDecision.Input in = input(sender, r.target(), r.targetName(), r.type(), r.duration(), r.ipWide(), r.silent(), now);
        PunishmentStore.RuleHook hook = ruleHook(r.target(), now);
        CompletableFuture<String> ipFuture = r.ipWide() && r.ip() == null && r.target() != null
                ? store.lastIp(r.target()).thenApply(o -> o.orElse(null))
                : CompletableFuture.completedFuture(r.ip());
        return ipFuture.thenCompose(ip -> {
            if (r.ipWide() && ip == null && in.actor().ipBan()) {
                throw new IllegalStateException("no-ip");
            }
            Punishment draft = new Punishment(0, r.type(), r.target(), r.targetName(), ip, r.ipWide(), r.reason(), r.template(),
                    r.ladderStep(), issuer, sender.getName(), now, null, r.silent(), null, null, null, null, r.source());
            return store.issue(draft, in, decision, hook);
        }).thenApplyAsync(result -> {
            handle(sender, r.targetName(), result);
            return result;
        }, main);
    }

    private void handle(CommandSender sender, String targetName, PunishmentStore.IssueResult result) {
        PunishDecision.Verdict v = result.verdict();
        if (!v.applied()) {
            tell(sender, v, targetName);
            return;
        }
        Punishment p = result.punishment();
        apply(p);
        announce(sender, p);
        audit(sender, p);
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
        details.put("count", s.warnCount());
        details.put("action", s.action());
        details.put("status", s.status().name().toLowerCase(Locale.ROOT));
        if (s.note() != null) {
            details.put("note", s.note());
        }
        switch (s.status()) {
            case PENDING -> {
                notifyStaff(null, s.repeat() ? "suggest.notify-repeat" : "suggest.notify", suggestionParts(null, s));
                for (Player staff : Bukkit.getOnlinePlayers()) {
                    if (staff.hasPermission(ModerationModule.PERM_NOTIFY)) {
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
                notifyStaff(null, s.repeat() ? "suggest.auto-repeat" : "suggest.auto", suggestionParts(null, s),
                        Msg.p("id", auto.id()));
                core.audit().log(AuditCategory.PUNISHMENT, "warn_rule_auto", Actor.system("warn-rule"), s.target().toString(),
                        s.targetName(), auto.expires() == null ? null : auto.expires() - auto.created(), null, details);
                auditPunishment(Actor.system("warn-rule"), null, auto);
            }
            case SKIPPED -> {
                PunishDecision.Verdict v = t.verdict();
                String key = Suggestion.NOTE_EXEMPT.equals(s.note()) ? "suggest.skipped-exempt" : "suggest.skipped-already";
                notifyStaff(null, key, suggestionParts(null, s), Msg.p("left", v == null ? "?" : v.args().getOrDefault("left", "?")));
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
                Msg.p("count", s.warnCount()), Msg.p("action", stepText(viewer, step)), Msg.p("reason", s.reason()),
                Msg.p("window", settings.repeatWindow() == null ? "-" : PunishConfig.text(settings.repeatWindow())),
                Msg.p("date", DATE.format(Instant.ofEpochMilli(s.created()))),
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
            return store.apply(id, x -> in, decision, by, sender.getName(), now);
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
            case SKIPPED -> {
                msg.send(sender, "suggest.skipped-already", suggestionParts(viewer, s),
                        Msg.p("left", r.verdict().args().getOrDefault("left", "?")));
                core.audit().log(AuditCategory.PUNISHMENT, "suggestion_skip", Actor.of(sender), s.target().toString(),
                        s.targetName(), null, null, Map.of("suggestion", s.id(), "action", s.action(), "note", Suggestion.NOTE_ALREADY));
            }
            case DISMISSED -> {
                notifyStaff(sender, "suggest.dismissed", suggestionParts(viewer, s), Msg.p("staff", sender.getName()));
                core.audit().log(AuditCategory.PUNISHMENT, "suggestion_dismiss", Actor.of(sender), s.target().toString(),
                        s.targetName(), null, null, Map.of("suggestion", s.id(), "action", s.action(),
                                "rule", s.repeat() ? "repeat" : String.valueOf(s.ruleAt())));
            }
            case APPLIED -> {
                Punishment p = r.punishment();
                apply(p);
                announce(sender, p);
                audit(sender, p);
                notifyStaff(sender, "suggest.applied", suggestionParts(viewer, s), Msg.p("staff", sender.getName()));
                core.audit().log(AuditCategory.PUNISHMENT, "suggestion_apply", Actor.of(sender), s.target().toString(),
                        s.targetName(), null, null, Map.of("suggestion", s.id(), "action", s.action(), "id", p.id()));
            }
        }
        if (viewer != null && r.kind() != PunishmentStore.HandleKind.APPLIED && r.kind() != PunishmentStore.HandleKind.DISMISSED) {
            core.sounds().play(viewer, "error");
        }
    }

    /** "Mute 1h", "Ban permanent", "Warning" for a step (messages steps.*). */
    String stepText(Player viewer, Templates.Step step) {
        String kind = step.ipWide() ? "ipban" : step.type().name().toLowerCase(Locale.ROOT);
        String length = step.duration() == null ? text(viewer, "words.permanent") : Format.duration(step.duration());
        return text(viewer, "steps." + kind, Msg.p("length", length));
    }

    /** Kicks/bans online players hit by the punishment, updates mute caches and the vanilla ban list. */
    private void apply(Punishment p) {
        for (Player online : affected(p)) {
            switch (p.type()) {
                case BAN -> online.kick(banScreen(online, p));
                case KICK -> online.kick(msg.component(online, "screen.kick", parts(online, p)));
                case MUTE -> {
                    cacheMute(online.getUniqueId(), longer(mutes.get(online.getUniqueId()), p));
                    msg.send(online, "muted.you", parts(online, p));
                    if (p.automatic()) {
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
                }
            }
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
                        mutes.remove(target);
                        Player online = Bukkit.getPlayer(target);
                        if (online != null) {
                            msg.send(online, "muted.unmuted");
                        }
                    }
                    if (type == Punishment.Type.BAN && config.get().vanillaSync) {
                        if (target != null) {
                            Bukkit.getBanList(BanListType.PROFILE).pardon(Bukkit.createProfile(target, targetName));
                        }
                        for (Punishment p : list) {
                            if (p.ipWide() && p.ip() != null) {
                                try {
                                    Bukkit.getBanList(BanListType.IP).pardon(InetAddress.getByName(p.ip()));
                                } catch (Exception ignored) {
                                    // not a valid address
                                }
                            }
                        }
                        if (ip != null) {
                            try {
                                Bukkit.getBanList(BanListType.IP).pardon(InetAddress.getByName(ip));
                            } catch (Exception ignored) {
                                // not a valid address
                            }
                        }
                    }
                    String action = switch (type) {
                        case BAN -> "unban";
                        case MUTE -> "unmute";
                        case WARN -> "unwarn";
                        case KICK -> "unkick";
                    };
                    notifyStaff(sender, "notify." + action, Msg.p("player", targetName), Msg.p("reason", reason),
                            Msg.p("staff", sender.getName()), pre("target", targetName));
                    Map<String, Object> details = new LinkedHashMap<>();
                    details.put("reason", reason);
                    details.put("ids", list.stream().map(Punishment::id).toList());
                    core.audit().log(AuditCategory.PUNISHMENT, action, Actor.of(sender), target == null ? ip : target.toString(),
                            targetName, null, sender instanceof Player p ? p.getLocation() : null, details);
                    return list;
                }, main);
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
                Msg.p("date", DATE.format(Instant.ofEpochMilli(p.created()))),
                Msg.p("type", text(viewer, "types." + p.type().name().toLowerCase(Locale.ROOT))),
                pre("target", p.targetName()), pre("appeal", appealUrl()));
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
        String type = p.type().name().toLowerCase(Locale.ROOT);
        String key = "notify." + (p.expires() != null && (p.type() == Punishment.Type.BAN || p.type() == Punishment.Type.MUTE)
                ? "temp" + type : (p.ipWide() ? "ip" + type : type));
        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (staff.hasPermission(ModerationModule.PERM_NOTIFY) || staff.equals(sender)) {
                msg.send(staff, key, parts(staff, p), Msg.p("staff", p.issuerName()), Msg.p("silent", p.silent() ? text(staff, "words.silent") : ""));
            }
        }
        if (!(sender instanceof Player)) {
            msg.send(sender, key, parts(null, p), Msg.p("staff", p.issuerName()), Msg.p("silent", p.silent() ? "(silent)" : ""));
        }
        boolean publicType = p.type() == Punishment.Type.BAN || p.type() == Punishment.Type.MUTE;
        if (config.get().announcePublic && !p.silent() && publicType) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (!player.hasPermission(ModerationModule.PERM_NOTIFY)) {
                    msg.send(player, "public." + (p.type() == Punishment.Type.BAN ? "ban" : "mute"), parts(player, p));
                }
            }
        }
    }

    /** To staff with skyblock.staff.notify, plus the sender (console included). {@code sender == null}: console log. */
    void notifyStaff(CommandSender sender, String key, TagResolver... placeholders) {
        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (staff.hasPermission(ModerationModule.PERM_NOTIFY) || staff.equals(sender)) {
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
        if (p.ip() != null) {
            details.put("ip", p.ip());
        }
        if (p.source() != null) {
            details.put("source", p.source());
        }
        core.audit().log(AuditCategory.PUNISHMENT, action, actor,
                p.target() == null ? p.ip() : p.target().toString(), p.targetName(),
                p.expires() == null ? null : p.expires() - p.created(), where, details);
    }
}
