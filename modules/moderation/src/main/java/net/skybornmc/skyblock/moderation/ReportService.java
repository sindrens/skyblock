package net.skybornmc.skyblock.moderation;

import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.skybornmc.skyblock.common.Core;
import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.audit.AuditCategory;
import net.skybornmc.skyblock.common.audit.AuditEntry;
import net.skybornmc.skyblock.common.audit.AuditQuery;
import net.skybornmc.skyblock.common.command.CommandFailure;
import net.skybornmc.skyblock.common.discord.DiscordSink;
import net.skybornmc.skyblock.common.text.ModuleMessages;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.util.Format;
import net.skybornmc.skyblock.common.util.Scheduler;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.logging.Level;

/**
 * The monthly punishment report (spec 14.8): made off the main thread on the 1st of every month at 09:00 in
 * {@code extras.stats.timezone} (catching up after downtime), stored, announced once to staff with the report permission
 * (alert type {@code report}), posted to Discord (off by default) and written as a CSV file; {@code /preport [month]}
 * shows a stored report (or makes it on demand) in a Bedrock-safe menu or as a chat summary.
 */
final class ReportService {

    static final String PERM = "skyblock.staff.punish.report";
    static final String PERM_STAFF = "skyblock.staff.punish.report.staff";
    static final String SYSTEM = "System";

    private final Core core;
    private final ModuleMessages msg;
    private final ModerationModule module;
    private final ReportStore store;
    private final Supplier<PunishConfig.Extras> extras;
    private final Supplier<Optional<DiscordSink>> discord;
    private final Map<String, CompletableFuture<MonthlyReport.Report>> inflight = new ConcurrentHashMap<>();
    /** The running month's live report, reused for {@code extras.stats.cache} (60s). */
    private volatile MonthlyReport.Report live;
    private volatile List<String> months = List.of();
    private volatile long retryAfter;
    private Scheduler.Task task;

    ReportService(Core core, ModuleMessages msg, ModerationModule module, ReportStore store,
                  Supplier<PunishConfig.Extras> extras, Supplier<Optional<DiscordSink>> discord) {
        this.core = core;
        this.msg = msg;
        this.module = module;
        this.store = store;
        this.extras = extras;
        this.discord = discord;
    }

    private MonthlyReport.Settings settings() {
        return extras.get().report();
    }

    private ZoneId zone() {
        return extras.get().statsZone();
    }

    void start() {
        refreshMonths();
        // every 10 minutes (first after 2): is the scheduled report due? Never on the main thread.
        task = core.scheduler().asyncRepeat(20L * 120, 20L * 600, this::tick);
    }

    void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    /** Month keys for tab completion of /preport. */
    List<String> monthHints() {
        return MonthlyReport.hints(MonthlyReport.monthOf(System.currentTimeMillis(), zone()), months);
    }

    private void refreshMonths() {
        store.months().thenAccept(list -> months = List.copyOf(list)).exceptionally(e -> null);
    }

    // ------------------------------------------------------------------ the scheduled run

    private void tick() {
        long now = System.currentTimeMillis();
        if (now < retryAfter) {
            return;
        }
        store.announced().thenCompose(done -> {
            Optional<YearMonth> due = MonthlyReport.due(now, zone(), settings(), new HashSet<>(done));
            if (due.isEmpty()) {
                return CompletableFuture.completedFuture(null);
            }
            YearMonth month = due.get();
            return storedOrGenerate(month, SYSTEM).thenCompose(report -> store.claimAnnouncement(report.month())
                    .thenAccept(won -> {
                        if (won) {
                            announce(report);
                        }
                    }));
        }).exceptionally(e -> {
            retryAfter = now + Duration.ofHours(1).toMillis(); // a broken database never spams every 10 minutes
            core.plugin().getLogger().log(Level.WARNING, "Monthly punishment report failed (retry in 1h)", e);
            return null;
        });
    }

    /** "The punishment report for October is ready: /preport", Discord embed, audit. Called once per month. */
    private void announce(MonthlyReport.Report report) {
        String key = report.month();
        core.audit().log(AuditCategory.PUNISHMENT, "report_ready", Actor.system("report"), key, null, null, null,
                Map.<String, Object>of("month", key, "punishments", report.totals().punishments(),
                        "sanctions", report.totals().sanctions()));
        core.scheduler().ensureMain("moderation:report", () -> {
            for (Player staff : Bukkit.getOnlinePlayers()) {
                if (staff.hasPermission(PERM) && module.service().wants(staff, NotifyRules.Type.REPORT)) {
                    msg.send(staff, "report.ready", Msg.p("month", monthName(staff, report.yearMonth())),
                            PunishmentService.pre("month_id", key));
                }
            }
            msg.send(Bukkit.getConsoleSender(), "report.ready", Msg.p("month", monthName(null, report.yearMonth())),
                    PunishmentService.pre("month_id", key));
        });
        if (settings().discord()) {
            postDiscord(report);
        }
    }

    private void postDiscord(MonthlyReport.Report report) {
        try {
            PunishConfig.Extras x = extras.get();
            String channel = NotifyRules.discordChannel(NotifyRules.Type.REPORT, x.notifications(), x.discord().channel());
            Optional<DiscordSink> sink = discord.get();
            if (channel != null && sink.isPresent() && sink.get().enabled(channel)) {
                TagResolver[] parts = parts(null, report);
                List<DiscordSink.Field> fields = new ArrayList<>();
                for (String k : List.of("totals", "reversed", "appeals", "reports", "requests", "safety")) {
                    String key = k.equals("reports") && report.reports() == null ? "reports-none" : k;
                    fields.add(new DiscordSink.Field(text(null, "discord.report.fields." + k),
                            text(null, "discord.report.values." + key, parts), false));
                }
                List<String> reasons = new ArrayList<>();
                for (MonthlyReport.Count c : report.topReasons().subList(0, Math.min(5, report.topReasons().size()))) {
                    reasons.add(text(null, "discord.report.values.reason", Msg.p("reason", reasonName(null, c.id())),
                            Msg.number("count", c.count())));
                }
                fields.add(new DiscordSink.Field(text(null, "discord.report.fields.reasons"),
                        reasons.isEmpty() ? "-" : String.join("\n", reasons), false));
                sink.get().post(channel, PunishEmbeds.report(text(null, "discord.report.title", parts), fields, x.discord(),
                        text(null, "discord.footer"), System.currentTimeMillis()));
            }
        } catch (RuntimeException e) {
            core.plugin().getLogger().log(Level.WARNING, "Discord report failed", e);
        }
    }

    // ------------------------------------------------------------------ making reports

    /** The stored report of a past month, else a new one (stored, CSV written). Async. */
    CompletableFuture<MonthlyReport.Report> storedOrGenerate(YearMonth month, String by) {
        String key = MonthlyReport.key(month);
        return store.load(key).thenCompose(stored -> {
            if (stored.isPresent()) {
                Optional<MonthlyReport.Report> parsed = MonthlyReport.fromJson(stored.get().json());
                if (parsed.isPresent()) {
                    return CompletableFuture.completedFuture(parsed.get());
                }
                core.plugin().getLogger().warning("Stored punishment report " + key + " is unreadable; showing a fresh one");
                return generate(month, System.currentTimeMillis());
            }
            return inflight.computeIfAbsent(key, k -> generate(month, System.currentTimeMillis())
                    .thenCompose(report -> store.save(report, by).thenApply(saved -> {
                        if (saved) {
                            core.audit().log(AuditCategory.PUNISHMENT, "report_generated", Actor.system("report"), key, by,
                                    null, null, Map.<String, Object>of("month", key, "by", by));
                            writeCsv(report);
                            refreshMonths();
                        }
                        return report;
                    })).whenComplete((r, e) -> inflight.remove(k)))
                    .thenCompose(r -> store.load(key).thenApply(s -> s.flatMap(x -> MonthlyReport.fromJson(x.json())).orElse(r)));
        });
    }

    /** A fresh report (nothing stored). Reads on the database thread, adds the guard events from the audit log. */
    CompletableFuture<MonthlyReport.Report> generate(YearMonth month, long now) {
        ZoneId zone = zone();
        MonthlyReport.Settings s = settings();
        long start = MonthlyReport.start(month, zone);
        long end = MonthlyReport.end(month, zone);
        String falseReason = extras.get().falseReports().reason();
        CompletableFuture<List<AuditEntry>> guard = core.audit().query(AuditQuery.create().categories(AuditCategory.PUNISHMENT)
                        .actions("guard_block", "guard_alert").since(Instant.ofEpochMilli(start)).until(Instant.ofEpochMilli(end))
                        .page(0, core.audit().maxQueryRows()))
                .exceptionally(e -> List.of());
        return store.input(month, zone, s, now, falseReason).thenCombine(guard, (in, entries) -> {
            List<MonthlyReport.GuardRow> rows = new ArrayList<>();
            for (AuditEntry e : entries) {
                rows.add(new MonthlyReport.GuardRow(uuid(e.target()), e.targetName(), e.time()));
            }
            MonthlyReport.Input full = new MonthlyReport.Input(in.month(), in.zone(), in.generatedAt(), in.punishments(),
                    in.sanctions(), in.appeals(), in.requests(), in.reports(), in.dryRuns(), rows, in.falseReportSuggestions());
            return MonthlyReport.compute(full, s);
        });
    }

    private static UUID uuid(String raw) {
        try {
            return raw == null ? null : UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** {@code plugins/PhoenixSkyblock/reports/punishments-<year>-<month>.csv} (async; written to a temp file first). */
    private void writeCsv(MonthlyReport.Report report) {
        if (!settings().csv() || report.partial()) {
            return;
        }
        File dir = new File(core.plugin().getDataFolder(), "reports");
        File out = new File(dir, MonthlyReport.csvName(report.yearMonth()));
        try {
            Files.createDirectories(dir.toPath());
            File tmp = new File(dir, out.getName() + ".tmp");
            Files.write(tmp.toPath(), MonthlyReport.csv(report), StandardCharsets.UTF_8);
            Files.move(tmp.toPath(), out.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            core.plugin().getLogger().log(Level.WARNING, "Could not write " + out, e);
        }
    }

    // ------------------------------------------------------------------ /preport [month]

    void command(CommandSender sender, String raw) {
        YearMonth current = MonthlyReport.monthOf(System.currentTimeMillis(), zone());
        YearMonth month;
        if (raw == null || raw.isBlank()) {
            month = current.minusMonths(1);
        } else {
            month = MonthlyReport.parseMonth(raw).orElseThrow(() -> new CommandFailure(ModerationModule.ID, "report.bad-month",
                    Msg.p("input", raw), Msg.p("example", MonthlyReport.key(current.minusMonths(1)))));
        }
        MonthlyReport.Ask ask = MonthlyReport.ask(month, current);
        if (ask == MonthlyReport.Ask.FUTURE) {
            throw new CommandFailure(ModerationModule.ID, "report.future", Msg.p("month", monthName(viewer(sender), month)));
        }
        String key = MonthlyReport.key(month);
        core.audit().log(AuditCategory.STAFF, "report_view", Actor.of(sender), key, null, null, null,
                Map.<String, Object>of("month", key, "staff_section", sender.hasPermission(PERM_STAFF)));
        CompletableFuture<MonthlyReport.Report> report;
        if (ask == MonthlyReport.Ask.CURRENT) {
            MonthlyReport.Report cached = live;
            long ttl = extras.get().statsCache().toMillis();
            if (cached != null && cached.month().equals(key) && System.currentTimeMillis() - cached.generatedAt() < ttl) {
                report = CompletableFuture.completedFuture(cached);
            } else {
                msg.send(sender, "report.generating", Msg.p("month", monthName(viewer(sender), month)));
                report = generate(month, System.currentTimeMillis()).thenApply(r -> {
                    live = r;
                    return r;
                });
            }
        } else {
            if (!months.contains(key)) {
                msg.send(sender, "report.generating", Msg.p("month", monthName(viewer(sender), month)));
            }
            report = storedOrGenerate(month, sender.getName());
        }
        report.thenAccept(r -> core.scheduler().ensureMain("moderation:report", () -> show(sender, r)))
                .exceptionally(e -> {
                    core.plugin().getLogger().log(Level.WARNING, "Punishment report " + key + " failed", e);
                    core.scheduler().ensureMain("moderation:report", () -> msg.send(sender, "report.failed"));
                    return null;
                });
    }

    /** Players get the menu, the console the chat summary. Main thread. */
    void show(CommandSender sender, MonthlyReport.Report report) {
        if (sender instanceof Player p) {
            if (!p.isOnline()) {
                return;
            }
            core.menus().open(p, new ReportMenu(this, report, p.hasPermission(PERM_STAFF)));
        } else {
            summary(sender, report, true);
        }
    }

    /** Opens another month from the menu (arrows). */
    void open(Player viewer, YearMonth month) {
        if (!viewer.hasPermission(PERM)) {
            return;
        }
        command(viewer, MonthlyReport.key(month));
    }

    List<String> storedMonths() {
        return months;
    }

    YearMonth currentMonth() {
        return MonthlyReport.monthOf(System.currentTimeMillis(), zone());
    }

    // ------------------------------------------------------------------ texts

    static Player viewer(CommandSender sender) {
        return sender instanceof Player p ? p : null;
    }

    String monthName(Player viewer, YearMonth month) {
        return module.service().text(viewer, "report.months." + month.getMonthValue()) + " " + month.getYear();
    }

    String reasonName(Player viewer, String id) {
        if (id == null || id.equals("-")) {
            return module.service().text(viewer, "report.custom-reason");
        }
        return module.templates().template(id).map(t -> module.reasonName(viewer, t)).orElseGet(() -> {
            String warn = "warn-buttons." + id + ".name";
            return msg.has(warn) ? module.service().text(viewer, warn) : id;
        });
    }

    String when(long at) {
        return DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT).format(Instant.ofEpochMilli(at).atZone(zone()));
    }

    static String length(long ms) {
        return ms <= 0 ? "-" : Format.duration(Duration.ofMillis(ms));
    }

    String text(Player viewer, String key, TagResolver... placeholders) {
        return module.service().text(viewer, key, placeholders);
    }

    /** The common placeholders of every report line. */
    TagResolver[] parts(Player viewer, MonthlyReport.Report r) {
        MonthlyReport.Totals t = r.totals();
        MonthlyReport.Reversed v = r.reversed();
        MonthlyReport.Appeals a = r.appeals();
        MonthlyReport.Requests q = r.requests();
        MonthlyReport.Reports rp = r.reports();
        List<TagResolver> out = new ArrayList<>(List.of(
                Msg.p("month", monthName(viewer, r.yearMonth())), Msg.p("generated", when(r.generatedAt())),
                Msg.number("warns", t.warns()), Msg.number("mutes", t.mutes()), Msg.number("bans", t.bans()),
                Msg.number("kicks", t.kicks()), Msg.number("sanctions", t.sanctions()), Msg.number("island", t.island()),
                Msg.number("report_blocks", t.reportBlocks()), Msg.number("given", t.punishments()), Msg.number("all", t.all()),
                Msg.number("reversed", v.total()), Msg.number("reversed_percent", v.percent()), Msg.number("undo", v.undo()),
                Msg.number("pardon", v.pardon()), Msg.number("appeal", v.appeal()), Msg.number("rollback", v.rollback()),
                Msg.number("received", a.received()), Msg.number("accepted", a.accepted()), Msg.number("reduced", a.reduced()),
                Msg.number("denied", a.denied()), Msg.number("open", a.open()), Msg.number("accepted_percent", a.acceptedPercent()),
                Msg.p("appeal_time", length(a.avgHandleMs())),
                Msg.number("reports", rp == null ? 0 : rp.received()), Msg.number("reports_closed", rp == null ? 0 : rp.closed()),
                Msg.number("invalid", rp == null ? 0 : rp.invalid()), Msg.p("report_time", rp == null ? "-" : length(rp.avgHandleMs())),
                Msg.number("made", q.made()), Msg.number("approved", q.approved()), Msg.number("req_denied", q.denied()),
                Msg.number("skipped", q.skipped()), Msg.number("expired", q.expired()), Msg.number("waiting", q.waiting()),
                Msg.number("trainee", q.trainee()), Msg.number("guard", r.guardEvents()),
                Msg.number("false_suggestions", r.falseReportSuggestions()), Msg.number("dry", r.dryRuns()),
                Msg.number("trainee_dry", r.traineeDryRuns()),
                Msg.p("cohort", MonthlyReport.parseMonth(r.cohortMonth()).map(m -> monthName(viewer, m)).orElse(r.cohortMonth())),
                Msg.p("window", Format.duration(Duration.ofMillis(r.reoffenceWindowMs())))));
        return out.toArray(new TagResolver[0]);
    }

    /** The chat summary ({@code staffNames}: the staff section, only with {@link #PERM_STAFF}). Main thread. */
    void summary(CommandSender sender, MonthlyReport.Report r, boolean staffNames) {
        Player viewer = viewer(sender);
        TagResolver[] parts = parts(viewer, r);
        MonthlyReport.Settings s = settings();
        msg.send(sender, r.partial() ? "report.chat.header-partial" : "report.chat.header", parts);
        msg.send(sender, "report.chat.totals", parts);
        msg.send(sender, "report.chat.reversed", parts);
        msg.send(sender, "report.chat.appeals", parts);
        msg.send(sender, r.reports() == null ? "report.chat.reports-none" : "report.chat.reports", parts);
        msg.send(sender, "report.chat.requests", parts);
        msg.send(sender, "report.chat.safety", parts);
        msg.send(sender, "report.chat.reasons-header", parts);
        if (r.topReasons().isEmpty()) {
            msg.send(sender, "report.chat.empty");
        }
        int place = 1;
        for (MonthlyReport.Count c : r.topReasons()) {
            msg.send(sender, "report.chat.reason", Msg.number("place", place++), Msg.p("reason", reasonName(viewer, c.id())),
                    Msg.number("count", c.count()));
        }
        msg.send(sender, "report.chat.reoffence-header", parts);
        if (r.reoffence().isEmpty()) {
            msg.send(sender, "report.chat.empty");
        }
        for (MonthlyReport.Reoffence o : r.reoffence().subList(0, Math.min(s.reoffenceLines(), r.reoffence().size()))) {
            msg.send(sender, "report.chat.reoffence", Msg.p("reason", reasonName(viewer, o.reason())), Msg.number("step", o.step()),
                    Msg.number("percent", o.percent()), Msg.number("again", o.again()), Msg.number("players", o.players()));
        }
        if (staffNames && sender.hasPermission(PERM_STAFF)) {
            msg.send(sender, "report.chat.staff-header", parts);
            if (r.staff().isEmpty()) {
                msg.send(sender, "report.chat.empty");
            }
            place = 1;
            for (MonthlyReport.StaffLine l : r.staff().subList(0, Math.min(s.staffLines(), r.staff().size()))) {
                msg.send(sender, l.trainee() ? "report.chat.staff-trainee" : "report.chat.staff", staffParts(place++, l));
            }
        }
    }

    TagResolver[] staffParts(int place, MonthlyReport.StaffLine l) {
        return new TagResolver[]{Msg.number("place", place), Msg.p("staff", l.name()), Msg.number("given", l.given()),
                Msg.number("warns", l.warns()), Msg.number("mutes", l.mutes()), Msg.number("bans", l.bans()),
                Msg.number("kicks", l.kicks()), Msg.number("other", l.other()), Msg.number("sanctions", l.sanctions()),
                Msg.number("reversed", l.reversed()), Msg.number("percent", l.reversedPercent()),
                Msg.number("accepted", l.appealsAccepted()), Msg.number("reduced", l.appealsReduced()),
                Msg.number("denied", l.appealsDenied()), Msg.number("made", l.requestsMade()),
                Msg.number("approved", l.requestsApproved()), Msg.number("req_denied", l.requestsDenied()),
                Msg.number("approved_others", l.approvedForOthers()), Msg.number("guard", l.guardEvents()),
                Msg.number("dry", l.dryRuns())};
    }

    MonthlyReport.Settings currentSettings() {
        return settings();
    }

    ModuleMessages messages() {
        return msg;
    }
}
