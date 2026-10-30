package net.skybornmc.skyblock.staff;

import net.skybornmc.skyblock.common.Core;
import net.skybornmc.skyblock.common.command.Arg;
import net.skybornmc.skyblock.common.command.Args;
import net.skybornmc.skyblock.common.command.CommandSpec;
import net.skybornmc.skyblock.common.command.Suggest;
import net.skybornmc.skyblock.common.db.MainThread;
import net.skybornmc.skyblock.common.player.PlayerData;
import net.skybornmc.skyblock.common.text.ModuleMessages;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.util.Cooldowns;
import net.skybornmc.skyblock.common.util.Format;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@code /report} for players and {@code /reports} for staff (GUI + claim/close commands), all persisted and audited.
 * Also the {@link ReportLink} service for moderation (Phase P 12.9): a punishment closes the punishing staff member's
 * claimed reports about that player; the {@code ReportBlocking} service (Phase P 14.5): the {@code report-block} ladder
 * step stops a player from using {@code /report}; and the false report penalty (14.5): closing a report as invalid
 * tells staff how many invalid reports the reporter has and asks moderation's {@code FalseReports} for a suggestion.
 */
final class ReportService extends StaffFeature implements Listener, net.skybornmc.skyblock.common.moderation.ReportLink,
        net.skybornmc.skyblock.common.moderation.ReportBlocking {

    private final Cooldowns cooldowns = new Cooldowns();
    private final MainThread main;
    /** Open + claimed reports by id (for tab completion and quick lookups). */
    private final Map<Long, Report> active = new ConcurrentHashMap<>();

    ReportService(Core core, StaffConfig config, ModuleMessages msg, StaffStore store) {
        super(core, config, msg, store);
        this.main = new MainThread(core.plugin());
    }

    void register() {
        Arg<String> target = Args.offlinePlayer("player");
        Arg<String> text = Args.text("reason", config.reportReasons.toArray(String[]::new));
        core.commands().register(CommandSpec.command("report")
                .description("Report a player to staff").category("Community")
                .permission(StaffPerms.REPORT).playerOnly().arg(target).optional(text).example("report Steve Hacking")
                .executes(ctx -> report(ctx.player(), ctx.get(target), ctx.getOr(text, null))));

        Arg<Long> claimId = reportId("id");
        Arg<Long> unclaimId = reportId("id");
        Arg<Long> closeId = reportId("id");
        Arg<Report.Outcome> outcome = Args.choice("outcome", Report.Outcome.class);
        Arg<String> about = Args.offlinePlayer("player");
        core.commands().register(CommandSpec.command("reports")
                .description("Open the report list").category("Staff")
                .permission(StaffPerms.REPORTS).playerOnly()
                .executes(ctx -> openList(ctx.player(), Report.Status.OPEN))
                .sub(CommandSpec.sub("open").executes(ctx -> openList(ctx.player(), Report.Status.OPEN)))
                .sub(CommandSpec.sub("claimed").executes(ctx -> openList(ctx.player(), Report.Status.CLAIMED)))
                .sub(CommandSpec.sub("closed").executes(ctx -> openList(ctx.player(), Report.Status.CLOSED)))
                .sub(CommandSpec.sub("claim").arg(claimId).executes(ctx -> claim(ctx.player(), ctx.get(claimId), true)))
                .sub(CommandSpec.sub("unclaim").arg(unclaimId).executes(ctx -> claim(ctx.player(), ctx.get(unclaimId), false)))
                .sub(CommandSpec.sub("close").arg(closeId).arg(outcome)
                        .executes(ctx -> close(ctx.player(), ctx.get(closeId), ctx.get(outcome), null)))
                .sub(CommandSpec.sub("player").arg(about).executes(ctx -> openAbout(ctx.player(), ctx.get(about)))));
    }

    void start() {
        loadActive();
    }

    private void loadActive() {
        store.reports(Report.Status.OPEN, 0).thenCombine(store.reports(Report.Status.CLAIMED, 0), (open, claimed) -> {
            active.clear();
            open.forEach(r -> active.put(r.id(), r));
            claimed.forEach(r -> active.put(r.id(), r));
            return null;
        }).exceptionally(e -> {
            core.plugin().getLogger().warning("Could not load reports: " + e.getMessage());
            return null;
        });
    }

    int openCount() {
        return (int) active.values().stream().filter(r -> r.status() == Report.Status.OPEN).count();
    }

    private Arg<Long> reportId(String name) {
        return Arg.word(name, (sender, raw) -> {
            try {
                return Long.parseLong(raw.startsWith("#") ? raw.substring(1) : raw);
            } catch (NumberFormatException e) {
                throw fail("reports.invalid-id", Msg.p("input", raw));
            }
        }, Suggest.of(() -> active.keySet().stream().sorted().map(String::valueOf).toList()));
    }

    // ------------------------------------------------------------------ /report

    private void report(Player reporter, String targetName, String text) {
        // Phase P 14.5: a report block refuses /report before anything else (also before the reason picker)
        store.activeReportBlocks(reporter.getUniqueId(), System.currentTimeMillis()).thenAcceptAsync(ends -> {
            Optional<Long> until = ReportRules.blockedUntil(ends);
            if (until.isPresent()) {
                refuseBlocked(reporter, until.get());
                return;
            }
            reportChecked(reporter, targetName, text);
        }, main).exceptionally(e -> {
            core.plugin().getLogger().warning("Could not check report blocks: " + e.getMessage());
            return null;
        });
    }

    private void refuseBlocked(Player reporter, long until) {
        if (until == Long.MAX_VALUE) {
            msg.send(reporter, "report.blocked-perm");
        } else {
            msg.send(reporter, "report.blocked", Msg.p("time", Format.duration(Duration.ofMillis(Math.max(1000,
                    until - System.currentTimeMillis())))));
        }
        core.sounds().play(reporter, "error");
    }

    private void reportChecked(Player reporter, String targetName, String text) {
        Player online = Bukkit.getPlayerExact(targetName);
        CompletableFuture<Optional<UUID>> lookup = online != null
                ? CompletableFuture.completedFuture(Optional.of(online.getUniqueId()))
                : core.players().offline(targetName).thenApply(s -> s.map(PlayerData.Snapshot::uuid));
        lookup.thenAcceptAsync(uuid -> {
            if (uuid.isEmpty()) {
                msg.send(reporter, "report.unknown", Msg.p("player", targetName));
                return;
            }
            if (text == null) {
                if (precheckFails(reporter, uuid.get())) {
                    return;
                }
                core.menus().open(reporter, new ReportReasonMenu(this, uuid.get(), targetName));
            } else {
                String[] parsed = ReportRules.parse(text, config.reportReasons);
                submit(reporter, uuid.get(), targetName, parsed[0], parsed[1]);
            }
        }, main);
    }

    /** Sync checks (self, cooldown, playtime) before showing the reason picker. Returns true (and tells why) if refused. */
    private boolean precheckFails(Player reporter, UUID target) {
        Optional<ReportRules.Refusal> refusal = ReportRules.check(reporter.getUniqueId().equals(target), cooldownLeft(reporter),
                playtime(reporter), config.reportMinPlaytimeMinutes, 0, Integer.MAX_VALUE, false);
        refusal.ifPresent(r -> refuse(reporter, r));
        return refusal.isPresent();
    }

    private long cooldownLeft(Player reporter) {
        return reporter.hasPermission(StaffPerms.REPORT_EXEMPT) ? 0 : cooldowns.remaining(reporter.getUniqueId(), "report").toMillis();
    }

    private long playtime(Player reporter) {
        return core.players().get(reporter.getUniqueId()).map(PlayerData::playtimeMillis).orElse(0L);
    }

    private void refuse(Player reporter, ReportRules.Refusal refusal) {
        switch (refusal) {
            case SELF -> msg.send(reporter, "report.self");
            case COOLDOWN -> msg.send(reporter, "report.cooldown",
                    Msg.p("time", Format.duration(cooldowns.remaining(reporter.getUniqueId(), "report"))));
            case PLAYTIME -> msg.send(reporter, "report.playtime", Msg.p("minutes", config.reportMinPlaytimeMinutes));
            case TOO_MANY_OPEN -> msg.send(reporter, "report.too-many", Msg.p("max", config.reportMaxOpen));
            case ALREADY_REPORTED -> msg.send(reporter, "report.already");
        }
        core.sounds().play(reporter, "error");
    }

    List<String> reasons() {
        return config.reportReasons;
    }

    int detailsMax() {
        return config.reportDetailsMaxLength;
    }

    /** Files the report after the full checks (open counts come from the database). */
    void submit(Player reporter, UUID target, String targetName, String reason, String details) {
        String cleanDetails = ReportRules.clean(details == null ? "" : details, config.reportDetailsMaxLength);
        store.activeReportBlocks(reporter.getUniqueId(), System.currentTimeMillis())
                .thenCombine(store.reporterState(reporter.getUniqueId(), target), (ends, state) -> Map.entry(ends, state))
                .thenAcceptAsync(both -> {
            Optional<Long> blocked = ReportRules.blockedUntil(both.getKey());
            if (blocked.isPresent()) {
                refuseBlocked(reporter, blocked.get()); // Phase P 14.5 (the reason picker may have been open)
                return;
            }
            int[] state = both.getValue();
            Optional<ReportRules.Refusal> refusal = ReportRules.check(reporter.getUniqueId().equals(target), cooldownLeft(reporter),
                    playtime(reporter), config.reportMinPlaytimeMinutes, state[0], config.reportMaxOpen, state[1] > 0);
            if (refusal.isPresent()) {
                refuse(reporter, refusal.get());
                return;
            }
            cooldowns.set(reporter.getUniqueId(), "report", Duration.ofSeconds(config.reportCooldownSeconds));
            Player targetOnline = Bukkit.getPlayer(target);
            Location loc = targetOnline != null ? targetOnline.getLocation() : null;
            store.createReport(reporter.getUniqueId(), reporter.getName(), target, targetName, reason, cleanDetails,
                            loc == null ? null : loc.getWorld().getName(), loc == null ? null : loc.getBlockX(),
                            loc == null ? null : loc.getBlockY(), loc == null ? null : loc.getBlockZ())
                    .thenCompose(id -> store.targetCounts(target).thenApply(counts -> new long[]{id, counts[0], counts[1]}))
                    .thenAcceptAsync(r -> {
                        long id = r[0];
                        msg.send(reporter, "report.sent", Msg.p("id", id), Msg.p("player", targetName));
                        core.sounds().play(reporter, "success");
                        notifyStaff(StaffPerms.REPORTS, null, "report.staff-alert", Msg.p("id", id), Msg.p("reporter", reporter.getName()),
                                Msg.p("player", targetName), Msg.p("reason", reason),
                                Msg.p("details", cleanDetails.isEmpty() ? "-" : cleanDetails),
                                Msg.p("open", r[1]), Msg.p("total", r[2]));
                        for (Player staff : Bukkit.getOnlinePlayers()) {
                            if (staff.hasPermission(StaffPerms.REPORTS)) {
                                core.sounds().play(staff, "notify");
                            }
                        }
                        core.audit().logPlayer(net.skybornmc.skyblock.common.audit.AuditCategory.STAFF, "report",
                                net.skybornmc.skyblock.common.audit.Actor.player(reporter), target, targetName, id,
                                reporter.getLocation(), Map.of("reason", reason, "details", cleanDetails, "report_id", id));
                        loadActive();
                    }, main)
                    .exceptionally(e -> {
                        core.plugin().getLogger().warning("Could not save report: " + e.getMessage());
                        Bukkit.getScheduler().runTask(core.plugin(), () -> msg.send(reporter, "report.failed"));
                        return null;
                    });
        }, main);
    }

    // ------------------------------------------------------------------ /reports

    void openList(Player staff, Report.Status status) {
        store.reports(status, config.closedShown).thenAcceptAsync(list ->
                core.menus().open(staff, new ReportsMenu(this, status, list, null)), main);
    }

    private void openAbout(Player staff, String name) {
        core.players().offline(name).thenCompose(s -> s.isEmpty()
                        ? CompletableFuture.completedFuture(List.<Report>of())
                        : store.reportsAbout(s.get().uuid(), 200))
                .thenAcceptAsync(list -> core.menus().open(staff, new ReportsMenu(this, null, list, name)), main);
    }

    void openReport(Player staff, long id, Runnable afterChange) {
        store.report(id).thenAcceptAsync(r -> {
            if (r.isEmpty()) {
                msg.send(staff, "reports.not-found", Msg.p("id", id));
                return;
            }
            core.menus().open(staff, new ReportMenu(this, r.get(), afterChange));
        }, main);
    }

    void claim(Player staff, long id, boolean claim) {
        store.claim(id, claim ? staff.getUniqueId() : null, staff.getName()).thenAcceptAsync(changed -> {
            if (!changed) {
                msg.send(staff, "reports.not-changed", Msg.p("id", id));
                return;
            }
            msg.send(staff, claim ? "reports.claimed" : "reports.unclaimed", Msg.p("id", id));
            if (claim) {
                notifyStaff(StaffPerms.REPORTS, staff, "reports.staff-claimed", Msg.p("id", id), Msg.p("staff", staff.getName()));
            }
            audit(claim ? "report_claim" : "report_unclaim", staff, null, null, Map.of("report_id", id));
            loadActive();
        }, main);
    }

    void close(Player staff, long id, Report.Outcome outcome, Runnable after) {
        store.close(id, staff.getUniqueId(), staff.getName(), outcome).thenCompose(changed -> store.report(id)
                        .thenApply(r -> changed ? r : Optional.<Report>empty()))
                .thenAcceptAsync(r -> {
                    if (r.isEmpty()) {
                        msg.send(staff, "reports.not-changed", Msg.p("id", id));
                        return;
                    }
                    Report report = r.get();
                    msg.send(staff, "reports.closed", Msg.p("id", id), Msg.p("outcome", outcomeName(staff, outcome)));
                    audit("report_close", staff, report.target(), report.targetName(),
                            Map.of("report_id", id, "outcome", outcome.name(), "reporter", report.reporterName()));
                    if (ReportRules.falseReport(outcome)) {
                        falseReport(staff, report);
                    }
                    Player reporter = Bukkit.getPlayer(report.reporter());
                    if (reporter != null) {
                        tellReporter(reporter, report);
                        store.markNotified(id);
                    }
                    loadActive();
                    if (after != null) {
                        after.run();
                    }
                }, main);
    }

    String outcomeName(Player viewer, Report.Outcome outcome) {
        String raw = core.messages().raw(MODULE, "outcome." + outcome.key() + ".name");
        return raw == null ? outcome.key() : net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(core.messages().parse(viewer, raw));
    }

    private void tellReporter(Player reporter, Report report) {
        Report.Outcome outcome = Arrays.stream(Report.Outcome.values()).filter(o -> o.name().equals(report.outcome()))
                .findFirst().orElse(Report.Outcome.NO_EVIDENCE);
        msg.send(reporter, "report.closed-" + outcome.key(), Msg.p("id", report.id()), Msg.p("player", report.targetName()));
    }

    // ------------------------------------------------------------------ false reports (Phase P 14.5)

    private Optional<net.skybornmc.skyblock.common.moderation.FalseReports> falseReports() {
        return core.services().optional(net.skybornmc.skyblock.common.moderation.FalseReports.class);
    }

    /**
     * A report was closed as invalid (the only outcome that counts): "This is Steve's 3rd invalid report in 30 days." to
     * the staff member, then moderation decides about a report-abuse suggestion (threshold, weekly cap; never automatic).
     */
    private void falseReport(Player staff, Report report) {
        Optional<net.skybornmc.skyblock.common.moderation.FalseReports> service = falseReports();
        Duration window = service.map(net.skybornmc.skyblock.common.moderation.FalseReports::window)
                .orElse(Duration.ofDays(config.invalidWindowDays));
        long since = System.currentTimeMillis() - window.toMillis();
        store.closedCount(report.reporter(), Report.Outcome.INVALID, since).thenComposeAsync(count -> {
            String nth = count + core.messages().raw(MODULE, "reports.ordinal." + ReportRules.ordinalSuffix(count));
            msg.send(staff, "reports.invalid-count", Msg.p("player", report.reporterName()), Msg.p("nth", nth),
                    Msg.number("count", count), Msg.p("window", Format.duration(window)));
            if (service.isEmpty()) {
                return CompletableFuture.completedFuture(null);
            }
            return service.get().invalidReport(report.reporter(), report.reporterName(), count, staff.getUniqueId(),
                    staff.getName());
        }, main).thenAcceptAsync(result -> {
            if (result == null) {
                return;
            }
            switch (result.outcome()) {
                case SUGGESTED -> msg.send(staff, "reports.invalid-suggested", Msg.p("player", report.reporterName()),
                        Msg.p("sid", result.suggestionId()));
                case REFRESHED -> msg.send(staff, "reports.invalid-refreshed", Msg.p("player", report.reporterName()),
                        Msg.p("sid", result.suggestionId()));
                case CAPPED -> msg.send(staff, "reports.invalid-capped", Msg.p("player", report.reporterName()));
                case BELOW_THRESHOLD, DISABLED -> {
                    // the count line above is enough
                }
            }
        }, main).exceptionally(e -> {
            core.plugin().getLogger().warning("False report check failed: " + e.getMessage());
            return null;
        });
    }

    // ------------------------------------------------------------------ report blocks (Phase P 14.5)

    @Override
    public CompletableFuture<Void> blockReporting(UUID player, Long until, String reference) {
        long now = System.currentTimeMillis();
        return store.addReportBlock(player, until, reference, now).thenRun(() -> core.audit().logPlayer(
                net.skybornmc.skyblock.common.audit.AuditCategory.STAFF, "report_block",
                net.skybornmc.skyblock.common.audit.Actor.system("moderation"), player, null,
                until == null ? null : until - now, null, Map.of("reference", reference, "until", until == null ? "perm" : until)));
    }

    @Override
    public CompletableFuture<Boolean> liftReportBlock(String reference) {
        return store.liftReportBlock(reference, System.currentTimeMillis()).thenApply(lifted -> {
            if (lifted) {
                core.audit().log(net.skybornmc.skyblock.common.audit.AuditCategory.STAFF, "report_block_lift",
                        net.skybornmc.skyblock.common.audit.Actor.system("moderation"), Map.of("reference", reference));
            }
            return lifted;
        });
    }

    // ------------------------------------------------------------------ report link (Phase P 12.9)

    @Override
    public CompletableFuture<Result> punished(UUID target, String targetName, UUID staff, String staffName, long punishmentId) {
        return store.closeClaimedBy(target, staff, staffName, Report.Outcome.PUNISHED).thenApplyAsync(r -> {
            Player staffOnline = Bukkit.getPlayer(staff);
            for (Report report : r.closed()) {
                java.util.Map<String, Object> details = new java.util.LinkedHashMap<>();
                details.put("report_id", report.id());
                details.put("outcome", Report.Outcome.PUNISHED.name());
                details.put("reporter", report.reporterName());
                details.put("punishment", punishmentId);
                details.put("auto", true);
                core.audit().log(net.skybornmc.skyblock.common.audit.AuditCategory.STAFF, "report_close",
                        staffOnline != null ? net.skybornmc.skyblock.common.audit.Actor.player(staffOnline)
                                : net.skybornmc.skyblock.common.audit.Actor.player(staff, staffName),
                        report.target() == null ? null : report.target().toString(), report.targetName(), null, null, details);
                Player reporter = Bukkit.getPlayer(report.reporter());
                if (reporter != null) {
                    tellReporter(reporter, report);
                    store.markNotified(report.id());
                }
            }
            if (!r.closed().isEmpty()) {
                loadActive();
            }
            return new Result(r.closed().stream().map(Report::id).toList(), r.openUnclaimed(), r.claimedByOthers());
        }, main);
    }

    @Override
    public CompletableFuture<Result> count(UUID target, UUID staff) {
        return store.linkCounts(target, staff).thenApplyAsync(r -> new Result(List.of(), r.openUnclaimed(), r.claimedByOthers()), main);
    }

    // ------------------------------------------------------------------ join notices

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        if (p.hasPermission(StaffPerms.REPORTS)) {
            int open = openCount();
            if (open > 0) {
                Bukkit.getScheduler().runTaskLater(core.plugin(), () -> msg.send(p, "reports.join-notice", Msg.p("count", open)), 60L);
            }
        }
        store.takeUnnotified(p.getUniqueId()).thenAcceptAsync(list -> {
            if (p.isOnline()) {
                list.forEach(r -> tellReporter(p, r));
            }
        }, main);
    }

    // ------------------------------------------------------------------ helpers for menus

    Core core() {
        return core;
    }

    ModuleMessages messages() {
        return msg;
    }

    void dispatchFor(Player staff, String commandLine) {
        dispatch(staff, commandLine);
    }

    static String ago(long millis) {
        return Format.duration(Duration.ofMillis(Math.max(0, System.currentTimeMillis() - millis)));
    }
}
