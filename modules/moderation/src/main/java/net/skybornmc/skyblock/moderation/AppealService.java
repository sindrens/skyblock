package net.skybornmc.skyblock.moderation;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.audit.AuditCategory;
import net.skybornmc.skyblock.common.db.MainThread;
import net.skybornmc.skyblock.common.menu.Menu;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.util.Format;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.logging.Level;

/**
 * Appeals (spec 13.10), in-game part and the Discord webhook (step 1). Players use {@code /appeal} (a command, not a
 * text-input menu, so it works the same on Java and Bedrock); staff use the {@code /appeals} queue menu. Every change is
 * atomic in the database (first click wins) and audited; the player is told the decision at once, or on the next join.
 */
final class AppealService {

    private final ModerationModule module;
    private final Executor main;

    AppealService(ModerationModule module) {
        this.module = module;
        this.main = new MainThread(module.core().plugin());
    }

    PunishConfig.Appeals cfg() {
        return module.templates().config().extras().appeal();
    }

    private PunishmentService service() {
        return module.service();
    }

    private PunishmentStore store() {
        return module.service().store();
    }

    private void send(CommandSender to, String key, TagResolver... tags) {
        module.messages().send(to, key, tags);
    }

    private void sound(CommandSender to, String key) {
        if (to instanceof Player p) {
            module.core().sounds().play(p, key);
        }
    }

    // ================================================================== texts

    private static String left(Punishment p, long now) {
        return p.expires() == null ? null : Format.duration(Duration.ofMillis(Math.max(1000, p.expires() - now)));
    }

    /** Placeholders of a punishment for appeal lines: <pid>, <type>, <reason>, <left>, <length>, <staff>, <date>. */
    TagResolver punishmentParts(Player viewer, Punishment p) {
        long now = System.currentTimeMillis();
        String permanent = service().text(viewer, "words.permanent");
        String type = service().text(viewer, "types." + p.type().name().toLowerCase(Locale.ROOT));
        String length = p.expires() == null ? permanent : Format.duration(Duration.ofMillis(Math.max(1000, p.expires() - p.created())));
        return TagResolver.resolver(Msg.p("pid", p.id()), Msg.p("type", type), Msg.p("reason", p.reason()),
                Msg.p("left", p.active(now) ? (left(p, now) == null ? permanent : left(p, now)) : service().text(viewer, "appeal.ended")),
                Msg.p("length", length), Msg.p("staff", p.issuerName()), Msg.p("date", ModerationModule.when(p.created())),
                service().ruleParts(viewer, p.template())); // P8, spec 14.2
    }

    /** Placeholders of an appeal: <aid>, <player>, <target>, <text>, <status>, <handler>, <response>, <ago>, <given_by>, ... */
    TagResolver parts(Player viewer, Appeal a) {
        long now = System.currentTimeMillis();
        String type = service().text(viewer, "types." + a.punishmentType().name().toLowerCase(Locale.ROOT));
        return TagResolver.resolver(Msg.p("aid", a.id()), Msg.p("pid", a.punishmentId()), Msg.p("player", a.playerName()),
                PunishmentService.pre("target", a.playerName()), Msg.p("text", a.text()),
                Msg.p("status", service().text(viewer, "appeal.status." + a.status().key())),
                Msg.p("handler", a.handlerName() == null ? service().text(viewer, "words.system") : a.handlerName()),
                Msg.p("response", a.response() == null ? "-" : a.response()),
                Msg.p("ago", Format.duration(Duration.ofMillis(Math.max(1000, now - a.created())))),
                Msg.p("given_by", a.issuerName() == null ? service().text(viewer, "words.system") : a.issuerName()),
                Msg.p("type_name", type), Msg.p("date", ModerationModule.when(a.created())),
                Msg.p("new_end", a.newExpires() == null ? "-" : ModerationModule.when(a.newExpires())));
    }

    // ================================================================== players: /appeal

    /** {@code /appeal} without text: what can be appealed, how, and the state of earlier appeals. */
    void status(Player player) {
        UUID self = player.getUniqueId();
        PunishConfig.Appeals cfg = cfg();
        if (!cfg.enabled()) {
            send(player, AppealRules.Problem.DISABLED.key());
            return;
        }
        store().history(self).thenCombine(store().appealsBy(self, 20), Map::entry).thenAcceptAsync(data -> {
            long now = System.currentTimeMillis();
            List<Punishment> active = data.getKey().stream().filter(p -> self.equals(p.target()) && p.active(now)
                    && cfg.types().contains(p.type())).toList();
            Map<Long, List<Appeal>> byPunishment = new HashMap<>();
            for (Appeal a : data.getValue()) {
                byPunishment.computeIfAbsent(a.punishmentId(), k -> new ArrayList<>()).add(a);
            }
            send(player, "appeal.status-header");
            if (active.isEmpty()) {
                send(player, "appeal.none");
            }
            for (Punishment p : active) {
                List<Appeal> previous = byPunishment.getOrDefault(p.id(), List.of());
                Optional<AppealRules.Problem> problem = AppealRules.canAppeal(p, self, previous,
                        module.templates().appealable(p.template()), cfg, now);
                String state;
                if (problem.isEmpty()) {
                    state = "appeal.state.can";
                } else {
                    state = switch (problem.get()) {
                        case ALREADY_OPEN -> "appeal.state.open";
                        case COOLDOWN -> "appeal.state.cooldown";
                        default -> "appeal.state.not";
                    };
                }
                Long until = AppealRules.cooldownUntil(previous, cfg.cooldown());
                send(player, "appeal.status-line", punishmentParts(player, p), Msg.mm("state", module.core().messages()
                        .raw(ModerationModule.ID, state) == null ? state : module.core().messages().raw(ModerationModule.ID, state)),
                        Msg.p("until", until == null ? "-" : ModerationModule.when(until)));
            }
            if (!active.isEmpty()) {
                send(player, active.size() == 1 ? "appeal.how-one" : "appeal.how-many", Msg.number("min", cfg.minLength()),
                        Msg.number("max", cfg.maxLength()));
            }
            List<Appeal> recent = data.getValue().stream().limit(3).toList();
            if (!recent.isEmpty()) {
                send(player, "appeal.recent-header");
                for (Appeal a : recent) {
                    send(player, "appeal.recent-line", parts(player, a));
                }
            }
        }, main).exceptionally(e -> failed(player, e));
    }

    /** {@code /appeal <text>} or {@code /appeal <id> <text>}. */
    void submit(Player player, String raw) {
        PunishConfig.Appeals cfg = cfg();
        if (!cfg.enabled()) {
            send(player, AppealRules.Problem.DISABLED.key());
            sound(player, "error");
            return;
        }
        AppealRules.Args args = AppealRules.parse(raw);
        String text = AppealRules.clean(args.text());
        Optional<AppealRules.Problem> textProblem = AppealRules.checkText(text, cfg);
        if (textProblem.isPresent()) {
            send(player, textProblem.get().key(), Msg.number("min", cfg.minLength()), Msg.number("max", cfg.maxLength()),
                    Msg.number("length", text.length()));
            sound(player, "error");
            return;
        }
        UUID self = player.getUniqueId();
        String name = player.getName();
        store().history(self).thenCompose(history -> {
            long now = System.currentTimeMillis();
            Punishment target = args.id() != null ? history.stream().filter(p -> p.id() == args.id()).findFirst().orElse(null)
                    : AppealRules.pick(history, self, cfg, module.templates()::appealable, now);
            if (target == null && args.id() == null) {
                return CompletableFuture.completedFuture(new Submitted(new PunishmentStore.Submitted(null,
                        AppealRules.Problem.NOTHING, null), history));
            }
            long pid = target != null ? target.id() : args.id();
            return store().submitAppeal(pid, self, name, text, module.templates()::appealable, cfg, now)
                    .thenApply(r -> new Submitted(r, history));
        }).thenAcceptAsync(result -> submitted(player, result.result(), result.history()), main)
                .exceptionally(e -> failed(player, e));
    }

    private record Submitted(PunishmentStore.Submitted result, List<Punishment> history) {
    }

    private void submitted(Player player, PunishmentStore.Submitted r, List<Punishment> history) {
        PunishConfig.Appeals cfg = cfg();
        if (r.problem() != null) {
            if (r.problem() == AppealRules.Problem.COOLDOWN && r.punishment() != null) {
                long pid = r.punishment().id();
                store().appealsFor(pid).thenAcceptAsync(list -> {
                    Long end = AppealRules.cooldownUntil(list, cfg.cooldown());
                    send(player, r.problem().key(), Msg.p("until", end == null ? "-" : ModerationModule.when(end)),
                            Msg.p("pid", pid));
                }, main);
                sound(player, "error");
                return;
            }
            send(player, r.problem().key(), Msg.p("until", "-"), Msg.p("pid", r.punishment() == null ? "?" : r.punishment().id()));
            sound(player, "error");
            return;
        }
        Appeal a = r.appeal();
        Punishment p = r.punishment();
        send(player, "appeal.sent", parts(player, a), punishmentParts(player, p));
        sound(player, "success");
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("appeal", a.id());
        details.put("punishment", p.id());
        details.put("type", p.type().name().toLowerCase(Locale.ROOT));
        details.put("length", a.text().length());
        module.core().audit().logPlayer(AuditCategory.PUNISHMENT, "appeal_create", Actor.player(player), player.getUniqueId(),
                player.getName(), null, null, details);
        notifyNew(a, p);
        AppealRules.HistorySummary summary = AppealRules.summary(history);
        if (service().hooks() != null) {
            service().hooks().appealPosted(() -> PunishEmbeds.appealNew(a, p, summary,
                    module.templates().config().extras().discord(), service().hooks()::discordText));
        }
    }

    /** "New appeal #14 by Steve (mute 1d). [Open]" to staff who get appeals alerts (spec 14.9) and the console. */
    private void notifyNew(Appeal a, Punishment p) {
        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (service().wants(staff, NotifyRules.Type.APPEALS)) {
                send(staff, "appeal.notify", parts(staff, a), punishmentParts(staff, p), Msg.c("open", openButton(staff, a)));
                module.core().sounds().play(staff, "notify");
            }
        }
        send(Bukkit.getConsoleSender(), "appeal.notify", parts(null, a), punishmentParts(null, p), Msg.p("open", ""));
    }

    /** Clickable [Open] (server-side callback; Bedrock staff use /appeals). */
    Component openButton(Player staff, Appeal a) {
        return module.messages().component(staff, "appeal.open-button")
                .hoverEvent(HoverEvent.showText(module.messages().component(staff, "appeal.open-hover", parts(staff, a))))
                .clickEvent(ClickEvent.callback(audience -> {
                    if (audience instanceof Player clicker) {
                        open(clicker, a.id(), null);
                    }
                }, options -> options.uses(ClickCallback.UNLIMITED_USES).lifetime(Duration.ofDays(7))));
    }

    // ================================================================== staff: queue and one appeal

    /** {@code /appeals}: open appeals, oldest first. Menu for players, chat for the console. */
    void queue(CommandSender sender) {
        store().openAppeals(200).thenCompose(list -> punishments(list).thenApply(map -> Map.entry(list, map)))
                .thenAcceptAsync(data -> {
                    if (sender instanceof Player staff) {
                        module.core().menus().open(staff, new AppealMenus.QueueMenu(module, this, data.getKey(), data.getValue()));
                    } else {
                        send(sender, "appeal.queue-header", Msg.number("count", data.getKey().size()));
                        if (data.getKey().isEmpty()) {
                            send(sender, "appeal.queue-none");
                        }
                        for (Appeal a : data.getKey()) {
                            Punishment p = data.getValue().get(a.punishmentId());
                            send(sender, "appeal.queue-line", parts(null, a), p == null ? Msg.p("reason", "?") : punishmentParts(null, p));
                        }
                    }
                }, main).exceptionally(e -> failed(sender, e));
        module.core().audit().log(AuditCategory.STAFF, "appeals_view", Actor.of(sender), null, null, null, null, Map.of());
    }

    /** The punishments of these appeals by id. */
    CompletableFuture<Map<Long, Punishment>> punishments(List<Appeal> appeals) {
        List<CompletableFuture<Optional<Punishment>>> loads = new ArrayList<>();
        for (Appeal a : appeals) {
            loads.add(store().punishment(a.punishmentId()));
        }
        return CompletableFuture.allOf(loads.toArray(CompletableFuture[]::new)).thenApply(v -> {
            Map<Long, Punishment> out = new HashMap<>();
            loads.forEach(f -> f.join().ifPresent(p -> out.put(p.id(), p)));
            return out;
        });
    }

    /** Opens one appeal for {@code staff}: punishment, evidence (12.4), history, the player's text, the actions. */
    void open(Player staff, long id, Menu parent) {
        if (!staff.hasPermission(ModerationModule.PERM_APPEALS)) {
            send(staff, "appeal.no-permission");
            sound(staff, "error");
            return;
        }
        store().appeal(id).thenCompose(found -> {
            if (found.isEmpty()) {
                return CompletableFuture.completedFuture(null);
            }
            Appeal a = found.get();
            CompletableFuture<Optional<Punishment>> p = store().punishment(a.punishmentId());
            CompletableFuture<List<String>> evidence = store().evidence(a.punishmentId());
            CompletableFuture<List<Punishment>> history = store().history(a.player());
            return CompletableFuture.allOf(p, evidence, history).thenApply(v ->
                    new AppealMenus.View(a, p.join().orElse(null), evidence.join(), history.join()));
        }).thenAcceptAsync(view -> {
            if (view == null) {
                send(staff, "appeal.not-found");
                return;
            }
            AppealMenus.AppealMenu menu = new AppealMenus.AppealMenu(module, this, view);
            if (parent != null) {
                parent.openChild(menu);
            } else {
                module.core().menus().open(staff, menu);
            }
            module.core().audit().log(AuditCategory.STAFF, "appeal_view", Actor.player(staff), String.valueOf(view.appeal().player()),
                    view.appeal().playerName(), null, null, Map.of("appeal", view.appeal().id()));
        }, main).exceptionally(e -> failed(staff, e));
    }

    // ================================================================== staff: decisions

    /** Accept: lifts the punishment (pardon reason "Appeal accepted"). Main thread in and out. */
    CompletableFuture<PunishmentStore.AppealResult> accept(Player staff, Appeal a) {
        if (!allowed(staff)) {
            return CompletableFuture.completedFuture(null);
        }
        String reason = service().text(null, "appeal.pardon-reason");
        return store().acceptAppeal(a.id(), staff.getUniqueId(), staff.getName(), reason, System.currentTimeMillis())
                .thenApplyAsync(r -> {
                    handled(staff, r);
                    return r;
                }, main).exceptionally(e -> failed(staff, e));
    }

    /** Reduce: the punishment's total length becomes {@code amount unit} (always shorter). */
    CompletableFuture<PunishmentStore.AppealResult> reduce(Player staff, Appeal a, AppealRules.Unit unit, long amount) {
        if (!allowed(staff)) {
            return CompletableFuture.completedFuture(null);
        }
        String response = service().text(null, "appeal.reduce-response", Msg.p("length",
                Format.duration(Duration.ofMillis(amount * unit.millis))));
        return store().reduceAppeal(a.id(), staff.getUniqueId(), staff.getName(), unit, amount, response, System.currentTimeMillis())
                .thenApplyAsync(r -> {
                    handled(staff, r);
                    return r;
                }, main).exceptionally(e -> failed(staff, e));
    }

    /** Deny with a reason (required). */
    CompletableFuture<PunishmentStore.AppealResult> deny(Player staff, Appeal a, String reason) {
        if (!allowed(staff)) {
            return CompletableFuture.completedFuture(null);
        }
        String clean = reason == null ? "" : Notes.clean(reason);
        if (clean.isEmpty()) {
            send(staff, "appeal.deny-empty");
            sound(staff, "error");
            return CompletableFuture.completedFuture(null);
        }
        String cut = clean.length() <= 200 ? clean : clean.substring(0, 200);
        return store().denyAppeal(a.id(), staff.getUniqueId(), staff.getName(), cut, System.currentTimeMillis())
                .thenApplyAsync(r -> {
                    handled(staff, r);
                    return r;
                }, main).exceptionally(e -> failed(staff, e));
    }

    private boolean allowed(Player staff) {
        if (staff.hasPermission(ModerationModule.PERM_APPEALS)) {
            return true;
        }
        send(staff, "appeal.no-permission");
        sound(staff, "error");
        return false;
    }

    /** Effects, messages, audit and Discord after a decision. Main thread. */
    private void handled(Player staff, PunishmentStore.AppealResult r) {
        Appeal a = r.appeal();
        switch (r.kind()) {
            case NOT_FOUND -> {
                send(staff, "appeal.not-found");
                sound(staff, "error");
                return;
            }
            case ALREADY_HANDLED -> {
                send(staff, "appeal.already", parts(staff, a));
                sound(staff, "error");
                return;
            }
            case NOT_SHORTER -> {
                send(staff, "appeal.not-shorter", parts(staff, a));
                sound(staff, "error");
                return;
            }
            case ACCEPTED -> lifted(r.punishment());
            case REDUCED -> reduced(r.punishment());
            case DENIED, CLOSED -> {
                // nothing changes for the punishment
            }
        }
        Punishment p = r.punishment();
        TagResolver pp = p == null ? Msg.p("reason", "?") : punishmentParts(staff, p);
        send(staff, "appeal.done." + a.status().key(), parts(staff, a), pp);
        sound(staff, "success");
        if (a.status() != Appeal.Status.CLOSED) {
            // spec: accepting another staff member's punishment shows their name, so staff can talk
            service().notifyStaff(NotifyRules.Type.APPEALS, staff, a.otherStaff(staff.getUniqueId()) ? "appeal.notify-done-other" : "appeal.notify-done",
                    parts(null, a), pp, Msg.p("staff_name", staff.getName()));
        }
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("appeal", a.id());
        details.put("punishment", a.punishmentId());
        details.put("issuer", a.issuerName() == null ? "-" : a.issuerName());
        if (a.response() != null) {
            details.put("response", a.response());
        }
        if (a.status() == Appeal.Status.REDUCED) {
            details.put("old_expires", a.oldExpires() == null ? "perm" : a.oldExpires());
            details.put("new_expires", a.newExpires());
        }
        module.core().audit().logPlayer(AuditCategory.PUNISHMENT, "appeal_" + a.status().key(), Actor.player(staff), a.player(),
                a.playerName(), null, staff.getLocation(), details);
        if (service().hooks() != null) {
            service().hooks().appealPosted(() -> PunishEmbeds.appealDecision(a, module.templates().config().extras().discord(),
                    service().hooks()::discordText));
            if (a.status() == Appeal.Status.ACCEPTED && p != null) {
                service().hooks().pardoned(List.of(p), a.playerName(), staff.getName(), a.response());
            }
        }
        tellPlayer(a);
    }

    /** Accepted: the effects end at once (unmute unless another mute is active, vanilla ban list). */
    private void lifted(Punishment p) {
        if (p == null || p.target() == null) {
            return;
        }
        UUID target = p.target();
        switch (p.type()) {
            case MUTE -> store().activeMute(target, null).thenAcceptAsync(other -> service().cacheMute(target, other.orElse(null)), main);
            case BAN -> store().activeBan(target, null).thenAcceptAsync(other -> {
                if (other.isEmpty()) {
                    service().liftVanillaBans(target, p.targetName(), List.of(p), null);
                }
            }, main);
            default -> {
                // warnings simply stop counting (removed)
            }
        }
    }

    /** Reduced: the mute timer / vanilla ban use the new end; a mute that is over now says "mute ended". */
    private void reduced(Punishment p) {
        if (p == null || p.target() == null) {
            return;
        }
        UUID target = p.target();
        if (p.type() == Punishment.Type.MUTE) {
            store().activeMute(target, null).thenAcceptAsync(other -> {
                service().cacheMute(target, other.orElse(null));
                Player online = Bukkit.getPlayer(target);
                if (other.isEmpty() && online != null) {
                    service().endNotices(online, true, false);
                }
            }, main);
        } else if (p.type() == Punishment.Type.BAN) {
            service().resyncVanillaBan(p);
        }
    }

    /** The decision to the player now if online (claimed once), else on the next join. */
    private void tellPlayer(Appeal a) {
        Player online = Bukkit.getPlayer(a.player());
        if (online == null) {
            return;
        }
        store().markAppealNotified(a.id()).thenAcceptAsync(first -> {
            if (Boolean.TRUE.equals(first) && online.isOnline()) {
                send(online, "appeal.result." + a.status().key(), parts(online, a));
                module.core().sounds().play(online, a.status() == Appeal.Status.ACCEPTED || a.status() == Appeal.Status.REDUCED
                        ? "success" : "notify");
            }
        }, main).exceptionally(e -> null);
    }

    // ================================================================== join

    /** On join: decisions the player was not told yet; for staff with the appeals permission, open appeals waiting long. */
    void joined(Player player) {
        store().appealUpdates(player.getUniqueId()).thenAcceptAsync(list -> {
            for (Appeal a : list) {
                if (player.isOnline()) {
                    send(player, "appeal.result." + a.status().key(), parts(player, a));
                }
            }
        }, main).exceptionally(e -> null);
        if (!player.hasPermission(ModerationModule.PERM_APPEALS) || !cfg().enabled()) {
            return;
        }
        long cutoff = System.currentTimeMillis() - cfg().reminder().toMillis();
        int shown = 5;
        store().openAppealsBefore(cutoff, 50).thenAcceptAsync(list -> {
            if (list.isEmpty() || !player.isOnline()) {
                return;
            }
            send(player, "appeal.reminder-header", Msg.number("count", list.size()),
                    Msg.p("reminder", PunishConfig.text(cfg().reminder())));
            for (Appeal a : list.stream().limit(shown).toList()) {
                send(player, "appeal.reminder-line", parts(player, a), Msg.c("open", openButton(player, a)));
            }
            if (list.size() > shown) {
                send(player, "appeal.reminder-more", Msg.number("more", list.size() - shown));
            }
        }, main).exceptionally(e -> null);
    }

    private <T> T failed(CommandSender sender, Throwable e) {
        module.core().plugin().getLogger().log(Level.WARNING, "Appeal action failed", e);
        module.core().scheduler().ensureMain("moderation:appeal", () -> send(sender, "error.failed"));
        return null;
    }
}
