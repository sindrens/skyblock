package net.skybornmc.skyblock.moderation;

import net.skybornmc.skyblock.common.Core;
import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.audit.AuditCategory;
import net.skybornmc.skyblock.common.db.MainThread;
import net.skybornmc.skyblock.common.text.ModuleMessages;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.util.Format;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * {@code /prollback <staff> <period> [confirm]} (spec 14.1, admins only): undoes a staff member's recent punishments in
 * bulk after abuse or a hacked account. Always a preview first ("This will undo 37 punishments by Alex in the last 1h
 * ..."); a confirm (click, or typed) within {@code confirm-window} reverses exactly the previewed rows that are still not
 * reversed ({@link PunishmentStore#rollback}). Players are told their mute/ban/warning was lifted, sanctions are lifted
 * or given back, pending suggestions from reversed warns are removed. One audit entry, one staff line and one Discord
 * embed with the counts. There is no "undo the rollback": staff re-apply what was valid.
 */
final class RollbackService {

    static final String PERM = "skyblock.admin.punish.rollback";

    /** A preview waiting for its confirm (per sender). */
    private record Pending(UUID staff, String staffName, Duration period, long since, long until, List<Long> ids,
                           List<Sanction> sanctions, long at) {
    }

    private final Core core;
    private final ModuleMessages msg;
    private final PunishmentService service;
    private final Sanctions sanctions;
    private final Supplier<PunishConfig.Extras> extras;
    private final MainThread main;
    private final Map<String, Pending> pending = new ConcurrentHashMap<>();

    RollbackService(Core core, ModuleMessages msg, PunishmentService service, Sanctions sanctions,
                    Supplier<PunishConfig.Extras> extras) {
        this.core = core;
        this.msg = msg;
        this.service = service;
        this.sanctions = sanctions;
        this.extras = extras;
        this.main = new MainThread(core.plugin());
    }

    private RollbackRules.Settings settings() {
        return extras.get().rollback();
    }

    /** Period suggestions up to the configured maximum. */
    List<String> periodHints() {
        Duration max = settings().maxPeriod();
        Duration min = settings().minPeriod();
        List<String> out = new ArrayList<>();
        for (Duration d : List.of(Duration.ofMinutes(15), Duration.ofMinutes(30), Duration.ofHours(1), Duration.ofHours(6),
                Duration.ofDays(1), Duration.ofDays(3), Duration.ofDays(7))) {
            if (d.compareTo(min) >= 0 && d.compareTo(max) <= 0) {
                out.add(Templates.Step.compact(d));
            }
        }
        return out;
    }

    private static String key(CommandSender sender) {
        return sender instanceof Player p ? p.getUniqueId().toString() : "console:" + sender.getName();
    }

    private record Who(UUID uuid, String name) {
    }

    /** The staff member: by the name they punished with (works for accounts that left), else any known player. */
    private CompletableFuture<Optional<Who>> resolve(String name) {
        return service.store().staffByName(name).thenCompose(found -> {
            if (found.isPresent()) {
                return CompletableFuture.completedFuture(Optional.of(new Who(found.get().getKey(), found.get().getValue())));
            }
            Player online = Bukkit.getPlayerExact(name);
            if (online != null) {
                return CompletableFuture.completedFuture(Optional.of(new Who(online.getUniqueId(), online.getName())));
            }
            return core.players().offline(name).thenApply(s -> s.map(snap -> new Who(snap.uuid(), snap.name())));
        });
    }

    /** {@code /prollback <staff> <period> [confirm]}. Main thread in. */
    void run(CommandSender sender, String staffName, Duration period, boolean confirm) {
        RollbackRules.PeriodProblem problem = RollbackRules.check(settings(), period);
        if (problem != null) {
            msg.send(sender, problem == RollbackRules.PeriodProblem.TOO_SHORT ? "rollback.too-short" : "rollback.too-long",
                    Msg.p("min", Format.duration(settings().minPeriod())), Msg.p("max", Format.duration(settings().maxPeriod())));
            error(sender);
            return;
        }
        resolve(staffName).thenAcceptAsync(found -> {
            if (found.isEmpty()) {
                msg.send(sender, "error.unknown", Msg.p("player", staffName));
                error(sender);
                return;
            }
            Who who = found.get();
            long now = System.currentTimeMillis();
            Pending p = pending.get(key(sender));
            if (confirm && p != null && RollbackRules.confirms(p.staff(), p.period(), p.at(), who.uuid(), period, now,
                    settings().confirmWindow())) {
                pending.remove(key(sender));
                execute(sender, p);
                return;
            }
            if (confirm) {
                msg.send(sender, "rollback.preview-first", Msg.p("window", Format.duration(settings().confirmWindow())));
            }
            preview(sender, who, period, now);
        }, main).exceptionally(e -> failed(sender, e));
    }

    private void preview(CommandSender sender, Who who, Duration period, long now) {
        long since = now - period.toMillis();
        CompletableFuture<List<Punishment>> rows = service.store().rollbackCandidates(who.uuid(), since, now);
        CompletableFuture<List<Sanction>> held = sanctions.store().rollbackCandidates(who.uuid(), since, now, now);
        rows.thenCombine(held, (list, sanctionList) -> Map.entry(list, sanctionList)).thenAcceptAsync(both -> {
            RollbackRules.Counts c = RollbackRules.count(both.getKey(), both.getValue(), now);
            String periodText = Templates.Step.compact(period);
            if (c.total() == 0) {
                pending.remove(key(sender));
                msg.send(sender, "rollback.nothing", Msg.p("staff", who.name()), Msg.p("period", Format.duration(period)));
                return;
            }
            pending.put(key(sender), new Pending(who.uuid(), who.name(), period, since, now,
                    both.getKey().stream().map(Punishment::id).toList(), both.getValue(), now));
            msg.send(sender, "rollback.preview", Msg.p("staff", who.name()), Msg.number("total", c.punishments()),
                    Msg.p("period", Format.duration(period)), Msg.number("bans", c.bans()), Msg.number("mutes", c.mutes()),
                    Msg.number("warns", c.warns()), Msg.number("other", c.other()), Msg.number("sanctions", c.sanctions()),
                    Msg.number("active", c.active()));
            msg.send(sender, "rollback.confirm", PunishmentService.pre("target", who.name()),
                    PunishmentService.pre("period_id", periodText), Msg.p("window", Format.duration(settings().confirmWindow())),
                    Msg.number("count", c.total()));
            core.audit().log(AuditCategory.PUNISHMENT, "rollback_preview", Actor.of(sender), who.uuid().toString(), who.name(),
                    period.toMillis(), sender instanceof Player p ? p.getLocation() : null,
                    Map.of("punishments", c.punishments(), "sanctions", c.sanctions(), "active", c.active()));
        }, main).exceptionally(e -> failed(sender, e));
    }

    private void execute(CommandSender sender, Pending p) {
        long now = System.currentTimeMillis();
        UUID by = sender instanceof Player pl ? pl.getUniqueId() : null;
        String reason = service.text(null, "rollback.reason", Msg.p("staff", p.staffName()));
        service.store().rollback(p.ids(), p.staff(), p.staffName(), p.since(), p.until(), by, sender.getName(), reason, now)
                .thenAcceptAsync(r -> {
                    int lifted = 0;
                    for (Punishment row : r.reversed()) {
                        if (row.active(now)) {
                            service.lift(row, "rollback"); // unmute/unban (unless another one stays), tell the player
                            lifted++;
                        }
                    }
                    AtomicInteger sanctionCount = new AtomicInteger();
                    List<Long> sanctionIds = new ArrayList<>();
                    if (!p.sanctions().isEmpty()) {
                        sanctions.rollback(sender, p.sanctions(), r.rollbackId(), reason, s -> {
                            sanctionIds.add(s.id());
                            service.store().rollbackSanctions(r.rollbackId(), sanctionCount.incrementAndGet());
                        });
                    }
                    msg.send(sender, "rollback.done", Msg.p("staff", p.staffName()), Msg.number("count", r.reversed().size()),
                            Msg.number("lifted", lifted), Msg.number("sanctions", p.sanctions().size()),
                            Msg.p("period", Format.duration(p.period())));
                    service.notifyStaff(NotifyRules.Type.PUNISHMENTS, sender, "rollback.notify", Msg.p("staff", p.staffName()),
                            Msg.p("by", sender.getName()), Msg.number("count", r.reversed().size()),
                            Msg.number("sanctions", p.sanctions().size()), Msg.p("period", Format.duration(p.period())));
                    Map<String, Object> details = new LinkedHashMap<>();
                    details.put("rollback_id", r.rollbackId());
                    details.put("staff", p.staffName());
                    details.put("period", Templates.Step.compact(p.period()));
                    details.put("since", p.since());
                    details.put("until", p.until());
                    details.put("punishments", r.reversed().size());
                    details.put("lifted", lifted);
                    details.put("ids", r.reversed().stream().map(Punishment::id).toList());
                    details.put("sanctions", p.sanctions().stream().map(Sanction::id).toList());
                    if (!r.removedSuggestions().isEmpty()) {
                        details.put("removed_suggestions", r.removedSuggestions());
                    }
                    core.audit().log(AuditCategory.PUNISHMENT, "rollback", Actor.of(sender), p.staff().toString(), p.staffName(),
                            p.period().toMillis(), sender instanceof Player pl ? pl.getLocation() : null, details);
                    if (service.hooks() != null) {
                        service.hooks().rollbackPosted(p.staffName(), sender.getName(), p.period(), r.reversed().size(),
                                p.sanctions().size());
                    }
                    if (sender instanceof Player pl) {
                        core.sounds().play(pl, "success");
                    }
                }, main).exceptionally(e -> failed(sender, e));
    }

    private void error(CommandSender sender) {
        if (sender instanceof Player p) {
            core.sounds().play(p, "error");
        }
    }

    private Void failed(CommandSender sender, Throwable e) {
        core.plugin().getLogger().log(java.util.logging.Level.WARNING, "Moderation: rollback failed", e);
        core.scheduler().ensureMain("moderation:rollback-error", () -> msg.send(sender, "error.failed"));
        return null;
    }
}
