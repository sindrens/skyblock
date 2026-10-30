package net.skybornmc.skyblock.moderation;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * The monthly punishment report (spec 14.8), pure: month boundaries in {@code extras.stats.timezone} (Europe/Oslo), when
 * the scheduled run is due, the aggregation of raw rows into one {@link Report}, JSON for storage and the CSV file.
 *
 * <p>The report holds aggregated numbers only: no IPs, no evidence, no notes and no player names. Staff names appear only
 * in {@link Report#staff()}, which is shown to holders of {@code skyblock.staff.punish.report.staff}.</p>
 *
 * <p>Definitions (all for the report month {@code [start, end)}):</p>
 * <ul>
 *   <li>Totals, top reasons, reversed: punishments <b>created</b> in the month (system rows included); reversed = those of
 *   them reversed by the time the report was made, split by how (undo, pardon, appeal, rollback).</li>
 *   <li>Appeals: received = created in the month; accepted/reduced/denied, accepted % and the average handling time =
 *   decided in the month; open = created in the month and still waiting.</li>
 *   <li>Reports (staff module): received = created in the month; closed, invalid (false reports) and the average
 *   handling time = closed in the month. {@code null} when the reports module is not installed.</li>
 *   <li>Requests (approvals 13.4 / training 14.6), dry runs, guard events and false-report suggestions: created in the
 *   month.</li>
 *   <li>Reoffence: the cohort is the month <b>before</b> the report month, so every 30-day window is (nearly) complete when
 *   the report is made on the 1st: "of the players who got step 2 of Spam in September, 41% got another Spam punishment
 *   within 30 days". Reversed punishments are left out on both sides (they were mistakes).</li>
 *   <li>Staff: only rows with a staff issuer (system and console never count); appeals by who handled them, requests by
 *   who asked (and approvals by who approved).</li>
 * </ul>
 */
final class MonthlyReport {

    private MonthlyReport() {
    }

    /**
     * {@code extras.report}.
     *
     * @param enabled         the scheduled run (on-demand {@code /preport} always works)
     * @param day             day of the month of the scheduled run (1-28)
     * @param time            time of the scheduled run, in {@code extras.stats.timezone}
     * @param reoffenceWindow "got another punishment for the same reason within" (max 31d)
     * @param topReasons      most used reasons listed (1-25)
     * @param reoffenceLines  reoffence lines shown in chat and the menu (1-25)
     * @param staffLines      staff lines shown in chat and the menu (1-50; the CSV has all)
     * @param discord         post the embed when the report is ready (also needs {@code extras.discord.enabled} and a webhook)
     * @param csv             write {@code reports/punishments-<year>-<month>.csv}
     */
    record Settings(boolean enabled, int day, LocalTime time, Duration reoffenceWindow, int topReasons, int reoffenceLines,
                    int staffLines, boolean discord, boolean csv) {
        static final Settings DEFAULT = new Settings(true, 1, LocalTime.of(9, 0), Duration.ofDays(30), 10, 10, 15, false, true);
    }

    // ------------------------------------------------------------------ months

    /** {@code 2026-10}. */
    static String key(YearMonth month) {
        return String.format(Locale.ROOT, "%04d-%02d", month.getYear(), month.getMonthValue());
    }

    /** First moment of the month in {@code zone} (epoch ms). */
    static long start(YearMonth month, ZoneId zone) {
        return month.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli();
    }

    /** First moment of the next month (exclusive end). */
    static long end(YearMonth month, ZoneId zone) {
        return start(month.plusMonths(1), zone);
    }

    static YearMonth monthOf(long at, ZoneId zone) {
        return YearMonth.from(Instant.ofEpochMilli(at).atZone(zone));
    }

    /** The moment of the scheduled run in {@code month} (day capped at the month's length). */
    static long scheduled(YearMonth month, ZoneId zone, Settings s) {
        int day = Math.max(1, Math.min(s.day(), month.lengthOfMonth()));
        return month.atDay(day).atTime(s.time()).atZone(zone).toInstant().toEpochMilli();
    }

    /**
     * The month whose report the scheduled run should make (and announce) now: the previous month, once this month's run
     * time has passed and that report was not announced yet. Catches up after a restart or downtime at 09:00.
     *
     * @param announced keys of stored reports that were already announced
     */
    static Optional<YearMonth> due(long now, ZoneId zone, Settings s, Set<String> announced) {
        if (!s.enabled()) {
            return Optional.empty();
        }
        YearMonth current = monthOf(now, zone);
        if (now < scheduled(current, zone, s)) {
            return Optional.empty();
        }
        YearMonth previous = current.minusMonths(1);
        return announced.contains(key(previous)) ? Optional.empty() : Optional.of(previous);
    }

    /** {@code 2026-10} or {@code 2026-1}; empty when not a month. */
    static Optional<YearMonth> parseMonth(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String s = raw.strip();
        if (!s.matches("\\d{4}-\\d{1,2}")) {
            return Optional.empty();
        }
        int dash = s.indexOf('-');
        int year = Integer.parseInt(s.substring(0, dash));
        int month = Integer.parseInt(s.substring(dash + 1));
        if (month < 1 || month > 12 || year < 2000 || year > 2999) {
            return Optional.empty();
        }
        return Optional.of(YearMonth.of(year, month));
    }

    /** What {@code /preport <month>} means. */
    enum Ask { PAST, CURRENT, FUTURE }

    static Ask ask(YearMonth asked, YearMonth current) {
        if (asked.isAfter(current)) {
            return Ask.FUTURE;
        }
        return asked.equals(current) ? Ask.CURRENT : Ask.PAST;
    }

    /** Month keys for tab completion: the current and previous month, then stored ones (newest first, no duplicates). */
    static List<String> hints(YearMonth current, List<String> stored) {
        List<String> out = new ArrayList<>();
        out.add(key(current.minusMonths(1)));
        out.add(key(current));
        for (String s : stored) {
            if (!out.contains(s)) {
                out.add(s);
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ input rows

    /** How a punishment was reversed ({@code punishments.removed_kind}). */
    enum Reversal {
        NONE, UNDO, PARDON, APPEAL, ROLLBACK;

        /** Rows reversed before P10 without a kind: rollback when it has a rollback id, else pardon. */
        static Reversal of(Long removedAt, String kind, Long rollbackId) {
            if (removedAt == null) {
                return NONE;
            }
            if (kind != null) {
                try {
                    Reversal r = valueOf(kind.strip().toUpperCase(Locale.ROOT));
                    if (r != NONE) {
                        return r;
                    }
                } catch (IllegalArgumentException e) {
                    // unknown kind: fall through
                }
            }
            return rollbackId != null ? ROLLBACK : PARDON;
        }
    }

    /** One punishment (only the columns the report needs: never names of players, IPs or reason texts). */
    record PRow(Punishment.Type type, UUID target, String template, Integer step, UUID issuer, String issuerName,
                long created, Reversal reversal) {
    }

    record SanctionRow(UUID issuer, String issuerName, long created) {
    }

    /** status: OPEN, ACCEPTED, REDUCED, DENIED, CLOSED. */
    record AppealRow(UUID handler, String handlerName, String status, long created, Long handled) {
    }

    /** status: PENDING, APPROVED, DENIED, SKIPPED. */
    record RequestRow(UUID requester, String requesterName, boolean trainee, UUID handler, String handlerName, String status,
                      long created, long expires) {
    }

    /** A player report of the staff module; outcome = {@code INVALID} is a false report. */
    record ReportRow(long created, Long closed, String outcome) {
    }

    record DryRow(UUID staff, String staffName, boolean trainee, long created) {
    }

    /** A {@code guard_block} / {@code guard_alert} audit entry (target = the staff member). */
    record GuardRow(UUID staff, String staffName, long created) {
    }

    /**
     * Everything read for one report. {@code punishments} covers the cohort month too (from the start of the previous
     * month up to the end of the reoffence windows); {@code reports} is {@code null} without the reports module.
     */
    record Input(YearMonth month, ZoneId zone, long generatedAt, List<PRow> punishments, List<SanctionRow> sanctions,
                 List<AppealRow> appeals, List<RequestRow> requests, List<ReportRow> reports, List<DryRow> dryRuns,
                 List<GuardRow> guard, long falseReportSuggestions) {
    }

    /** The punishment rows {@link Input#punishments()} must cover: {@code [start of previous month, this end)}. */
    static long[] punishmentRange(YearMonth month, ZoneId zone, Settings s) {
        long from = start(month.minusMonths(1), zone);
        long to = Math.max(end(month, zone), start(month, zone) + s.reoffenceWindow().toMillis());
        return new long[]{from, to};
    }

    // ------------------------------------------------------------------ output

    /** Totals per type (spec: warns, mutes, bans, kicks, sanctions; plus island steps and report blocks). */
    record Totals(long warns, long mutes, long bans, long kicks, long sanctions, long island, long reportBlocks) {

        long punishments() {
            return warns + mutes + bans + kicks + island + reportBlocks;
        }

        long all() {
            return punishments() + sanctions;
        }
    }

    /** A reason id ({@code -} = typed reason without a template) and how often it was used. */
    record Count(String id, long count) {
    }

    /** Of {@code given} punishments, how many were reversed and how. */
    record Reversed(long given, long undo, long pardon, long appeal, long rollback) {

        long total() {
            return undo + pardon + appeal + rollback;
        }

        long percent() {
            return percentOf(total(), given);
        }
    }

    record Appeals(long received, long accepted, long reduced, long denied, long open, long avgHandleMs) {

        long decided() {
            return accepted + reduced + denied;
        }

        long acceptedPercent() {
            return percentOf(accepted, decided());
        }
    }

    record Reports(long received, long closed, long invalid, long avgHandleMs) {
    }

    record Requests(long made, long approved, long denied, long skipped, long expired, long waiting, long trainee) {
    }

    /** Reoffence of one reason and ladder step ({@code step} 1-based, as shown in the menu). */
    record Reoffence(String reason, int step, long players, long again) {

        long percent() {
            return percentOf(again, players);
        }
    }

    /** One staff member (name only, never shown without the staff permission). */
    record StaffLine(String name, long warns, long mutes, long bans, long kicks, long other, long sanctions, long reversed,
                     long appealsAccepted, long appealsReduced, long appealsDenied, long requestsMade, long requestsApproved,
                     long requestsDenied, long approvedForOthers, long guardEvents, long dryRuns, boolean trainee) {

        long given() {
            return warns + mutes + bans + kicks + other;
        }

        long reversedPercent() {
            return percentOf(reversed, given());
        }
    }

    /**
     * One report.
     *
     * @param partial     made before the month ended (the running month: never stored)
     * @param cohortMonth the month the reoffence numbers are about (the month before)
     * @param reports     {@code null} when the reports module is not installed
     */
    record Report(String month, long generatedAt, boolean partial, Totals totals, List<Count> topReasons, Reversed reversed,
                  Appeals appeals, Reports reports, Requests requests, long guardEvents, long falseReportSuggestions,
                  long dryRuns, long traineeDryRuns, String cohortMonth, long reoffenceWindowMs, List<Reoffence> reoffence,
                  List<StaffLine> staff) {

        YearMonth yearMonth() {
            return parseMonth(month).orElseThrow();
        }
    }

    static long percentOf(long part, long whole) {
        return whole <= 0 ? 0 : Math.round(part * 100.0 / whole);
    }

    // ------------------------------------------------------------------ aggregation

    static Report compute(Input in, Settings s) {
        ZoneId zone = in.zone();
        long start = start(in.month(), zone);
        long end = end(in.month(), zone);
        long now = in.generatedAt();
        boolean partial = now < end;

        Map<UUID, Staff> staff = new LinkedHashMap<>();
        long warns = 0;
        long mutes = 0;
        long bans = 0;
        long kicks = 0;
        long island = 0;
        long reportBlocks = 0;
        long[] reversed = new long[Reversal.values().length];
        Map<String, Long> reasons = new HashMap<>();
        for (PRow p : in.punishments()) {
            if (p.created() < start || p.created() >= end) {
                continue;
            }
            switch (p.type()) {
                case WARN -> warns++;
                case MUTE -> mutes++;
                case BAN -> bans++;
                case KICK -> kicks++;
                case ISLAND_KICK, ISLAND_BAN, COOP_REMOVE, COOP_BLOCK -> island++;
                case REPORT_BLOCK -> reportBlocks++;
            }
            reversed[p.reversal().ordinal()]++;
            reasons.merge(p.template() == null || p.template().isBlank() ? "-" : p.template(), 1L, Long::sum);
            if (p.issuer() != null) {
                Staff st = staff(staff, p.issuer(), p.issuerName());
                switch (p.type()) {
                    case WARN -> st.warns++;
                    case MUTE -> st.mutes++;
                    case BAN -> st.bans++;
                    case KICK -> st.kicks++;
                    default -> st.other++;
                }
                if (p.reversal() != Reversal.NONE) {
                    st.reversed++;
                }
            }
        }
        long sanctions = 0;
        for (SanctionRow r : in.sanctions()) {
            if (r.created() < start || r.created() >= end) {
                continue;
            }
            sanctions++;
            if (r.issuer() != null) {
                staff(staff, r.issuer(), r.issuerName()).sanctions++;
            }
        }
        Totals totals = new Totals(warns, mutes, bans, kicks, sanctions, island, reportBlocks);
        long given = totals.punishments();
        Reversed rev = new Reversed(given, reversed[Reversal.UNDO.ordinal()], reversed[Reversal.PARDON.ordinal()],
                reversed[Reversal.APPEAL.ordinal()], reversed[Reversal.ROLLBACK.ordinal()]);

        List<Count> top = new ArrayList<>();
        reasons.forEach((id, n) -> top.add(new Count(id, n)));
        top.sort(Comparator.comparingLong((Count c) -> -c.count()).thenComparing(Count::id));
        List<Count> topReasons = List.copyOf(top.subList(0, Math.min(Math.max(1, s.topReasons()), top.size())));

        // appeals
        long received = 0;
        long accepted = 0;
        long reduced = 0;
        long denied = 0;
        long open = 0;
        long handleSum = 0;
        for (AppealRow a : in.appeals()) {
            boolean createdIn = a.created() >= start && a.created() < end;
            if (createdIn) {
                received++;
                if ("OPEN".equals(a.status())) {
                    open++;
                }
            }
            boolean decided = "ACCEPTED".equals(a.status()) || "REDUCED".equals(a.status()) || "DENIED".equals(a.status());
            if (decided && a.handled() != null && a.handled() >= start && a.handled() < end) {
                switch (a.status()) {
                    case "ACCEPTED" -> accepted++;
                    case "REDUCED" -> reduced++;
                    default -> denied++;
                }
                handleSum += Math.max(0, a.handled() - a.created());
                if (a.handler() != null) {
                    Staff st = staff(staff, a.handler(), a.handlerName());
                    switch (a.status()) {
                        case "ACCEPTED" -> st.appealsAccepted++;
                        case "REDUCED" -> st.appealsReduced++;
                        default -> st.appealsDenied++;
                    }
                }
            }
        }
        long decidedCount = accepted + reduced + denied;
        Appeals appeals = new Appeals(received, accepted, reduced, denied, open, decidedCount == 0 ? 0 : handleSum / decidedCount);

        // reports (staff module)
        Reports reports = null;
        if (in.reports() != null) {
            long rReceived = 0;
            long rClosed = 0;
            long rInvalid = 0;
            long rSum = 0;
            for (ReportRow r : in.reports()) {
                if (r.created() >= start && r.created() < end) {
                    rReceived++;
                }
                if (r.closed() != null && r.closed() >= start && r.closed() < end) {
                    rClosed++;
                    rSum += Math.max(0, r.closed() - r.created());
                    if ("INVALID".equalsIgnoreCase(r.outcome())) {
                        rInvalid++;
                    }
                }
            }
            reports = new Reports(rReceived, rClosed, rInvalid, rClosed == 0 ? 0 : rSum / rClosed);
        }

        // requests
        long made = 0;
        long approved = 0;
        long reqDenied = 0;
        long skipped = 0;
        long expired = 0;
        long waiting = 0;
        long trainee = 0;
        for (RequestRow r : in.requests()) {
            if (r.created() < start || r.created() >= end) {
                continue;
            }
            made++;
            if (r.trainee()) {
                trainee++;
            }
            switch (r.status()) {
                case "APPROVED" -> approved++;
                case "DENIED" -> reqDenied++;
                case "SKIPPED" -> skipped++;
                default -> {
                    if (r.expires() <= now) {
                        expired++;
                    } else {
                        waiting++;
                    }
                }
            }
            if (r.requester() != null) {
                Staff st = staff(staff, r.requester(), r.requesterName());
                st.requestsMade++;
                st.trainee |= r.trainee();
                if ("APPROVED".equals(r.status())) {
                    st.requestsApproved++;
                } else if ("DENIED".equals(r.status())) {
                    st.requestsDenied++;
                }
            }
            if (r.handler() != null && "APPROVED".equals(r.status())) {
                staff(staff, r.handler(), r.handlerName()).approvedForOthers++;
            }
        }
        Requests requests = new Requests(made, approved, reqDenied, skipped, expired, waiting, trainee);

        long guardEvents = 0;
        for (GuardRow g : in.guard()) {
            if (g.created() < start || g.created() >= end) {
                continue;
            }
            guardEvents++;
            if (g.staff() != null) {
                staff(staff, g.staff(), g.staffName()).guardEvents++;
            }
        }
        long dry = 0;
        long traineeDry = 0;
        for (DryRow d : in.dryRuns()) {
            if (d.created() < start || d.created() >= end) {
                continue;
            }
            dry++;
            if (d.trainee()) {
                traineeDry++;
            }
            if (d.staff() != null) {
                Staff st = staff(staff, d.staff(), d.staffName());
                st.dryRuns++;
                st.trainee |= d.trainee();
            }
        }

        YearMonth cohort = in.month().minusMonths(1);
        List<Reoffence> reoffence = reoffence(in.punishments(), start(cohort, zone), start, s.reoffenceWindow().toMillis(), now);

        List<StaffLine> lines = new ArrayList<>();
        staff.values().forEach(st -> lines.add(st.line()));
        lines.sort(Comparator.comparingLong((StaffLine l) -> -l.given()).thenComparingLong(StaffLine::reversed)
                .thenComparing(l -> l.name().toLowerCase(Locale.ROOT)));

        return new Report(key(in.month()), now, partial, totals, topReasons, rev, appeals, reports, requests, guardEvents,
                in.falseReportSuggestions(), dry, traineeDry, key(cohort), s.reoffenceWindow().toMillis(), List.copyOf(reoffence),
                List.copyOf(lines));
    }

    /**
     * Reoffence rate per reason and ladder step: players punished with step N of a reason in {@code [from, to)}, and how
     * many of them got another (not reversed) punishment for the same reason within {@code window} after it (only what
     * happened before {@code now} counts). A player counts once per step (their first such punishment). Reversed rows and
     * rows without a reason id, ladder step or account are left out. Most players first.
     */
    static List<Reoffence> reoffence(List<PRow> rows, long from, long to, long window, long now) {
        Map<UUID, List<PRow>> byTarget = new HashMap<>();
        for (PRow p : rows) {
            if (p.reversal() == Reversal.NONE && p.target() != null && p.template() != null && !p.template().isBlank()) {
                byTarget.computeIfAbsent(p.target(), k -> new ArrayList<>()).add(p);
            }
        }
        record Key(String reason, int step) {
        }
        Map<Key, long[]> counts = new HashMap<>();
        for (List<PRow> list : byTarget.values()) {
            list.sort(Comparator.comparingLong(PRow::created));
            Map<Key, PRow> first = new LinkedHashMap<>();
            for (PRow p : list) {
                if (p.step() != null && p.created() >= from && p.created() < to) {
                    first.putIfAbsent(new Key(p.template(), p.step() + 1), p);
                }
            }
            for (Map.Entry<Key, PRow> e : first.entrySet()) {
                PRow p = e.getValue();
                long limit = Math.min(p.created() + window, now);
                boolean again = false;
                for (PRow q : list) {
                    if (q != p && q.template().equals(p.template()) && q.created() > p.created() && q.created() <= limit) {
                        again = true;
                        break;
                    }
                }
                long[] c = counts.computeIfAbsent(e.getKey(), k -> new long[2]);
                c[0]++;
                if (again) {
                    c[1]++;
                }
            }
        }
        List<Reoffence> out = new ArrayList<>();
        counts.forEach((k, c) -> out.add(new Reoffence(k.reason(), k.step(), c[0], c[1])));
        out.sort(Comparator.comparingLong((Reoffence r) -> -r.players()).thenComparing(Reoffence::reason)
                .thenComparingInt(Reoffence::step));
        return out;
    }

    private static Staff staff(Map<UUID, Staff> map, UUID uuid, String name) {
        Staff st = map.computeIfAbsent(uuid, k -> new Staff());
        if (st.name == null && name != null && !name.isBlank()) {
            st.name = name;
        }
        return st;
    }

    /** Mutable accumulator of one staff member. */
    private static final class Staff {
        String name;
        long warns;
        long mutes;
        long bans;
        long kicks;
        long other;
        long sanctions;
        long reversed;
        long appealsAccepted;
        long appealsReduced;
        long appealsDenied;
        long requestsMade;
        long requestsApproved;
        long requestsDenied;
        long approvedForOthers;
        long guardEvents;
        long dryRuns;
        boolean trainee;

        StaffLine line() {
            return new StaffLine(Objects.requireNonNullElse(name, "?"), warns, mutes, bans, kicks, other, sanctions, reversed,
                    appealsAccepted, appealsReduced, appealsDenied, requestsMade, requestsApproved, requestsDenied,
                    approvedForOthers, guardEvents, dryRuns, trainee);
        }
    }

    // ------------------------------------------------------------------ storage + CSV

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    static String toJson(Report r) {
        return GSON.toJson(r);
    }

    /** A stored report, or empty when the JSON is broken (never throws). */
    static Optional<Report> fromJson(String json) {
        if (json == null || json.isBlank()) {
            return Optional.empty();
        }
        try {
            Report r = GSON.fromJson(json, Report.class);
            return r == null || r.month() == null || parseMonth(r.month()).isEmpty() ? Optional.empty() : Optional.of(r);
        } catch (JsonParseException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /** {@code punishments-2026-10.csv}. */
    static String csvName(YearMonth month) {
        return "punishments-" + key(month) + ".csv";
    }

    /** The CSV file (UTF-8): server sections as {@code section,name,value}, then one row per staff member. */
    static List<String> csv(Report r) {
        List<String> out = new ArrayList<>();
        out.add("section,name,value");
        row(out, "report", "month", r.month());
        row(out, "report", "generated_at", Instant.ofEpochMilli(r.generatedAt()).toString());
        row(out, "report", "partial", String.valueOf(r.partial()));
        Totals t = r.totals();
        row(out, "totals", "warns", t.warns());
        row(out, "totals", "mutes", t.mutes());
        row(out, "totals", "bans", t.bans());
        row(out, "totals", "kicks", t.kicks());
        row(out, "totals", "sanctions", t.sanctions());
        row(out, "totals", "island", t.island());
        row(out, "totals", "report_blocks", t.reportBlocks());
        row(out, "totals", "all", t.all());
        for (Count c : r.topReasons()) {
            row(out, "reason", c.id(), c.count());
        }
        Reversed v = r.reversed();
        row(out, "reversed", "undo", v.undo());
        row(out, "reversed", "pardon", v.pardon());
        row(out, "reversed", "appeal", v.appeal());
        row(out, "reversed", "rollback", v.rollback());
        row(out, "reversed", "percent", v.percent());
        Appeals a = r.appeals();
        row(out, "appeals", "received", a.received());
        row(out, "appeals", "accepted", a.accepted());
        row(out, "appeals", "reduced", a.reduced());
        row(out, "appeals", "denied", a.denied());
        row(out, "appeals", "open", a.open());
        row(out, "appeals", "accepted_percent", a.acceptedPercent());
        row(out, "appeals", "avg_handling_minutes", a.avgHandleMs() / 60_000);
        if (r.reports() != null) {
            row(out, "reports", "received", r.reports().received());
            row(out, "reports", "closed", r.reports().closed());
            row(out, "reports", "invalid", r.reports().invalid());
            row(out, "reports", "avg_handling_minutes", r.reports().avgHandleMs() / 60_000);
        }
        Requests q = r.requests();
        row(out, "requests", "made", q.made());
        row(out, "requests", "approved", q.approved());
        row(out, "requests", "denied", q.denied());
        row(out, "requests", "skipped", q.skipped());
        row(out, "requests", "expired", q.expired());
        row(out, "requests", "waiting", q.waiting());
        row(out, "requests", "trainee", q.trainee());
        row(out, "safety", "guard_events", r.guardEvents());
        row(out, "safety", "false_report_suggestions", r.falseReportSuggestions());
        row(out, "training", "dry_runs", r.dryRuns());
        row(out, "training", "trainee_dry_runs", r.traineeDryRuns());
        for (Reoffence o : r.reoffence()) {
            row(out, "reoffence " + r.cohortMonth(), o.reason() + " #" + o.step(),
                    o.again() + "/" + o.players() + " (" + o.percent() + "%)");
        }
        out.add("");
        out.add("staff,given,warns,mutes,bans,kicks,other,sanctions,reversed,reversed_percent,appeals_accepted,appeals_reduced,"
                + "appeals_denied,requests_made,requests_approved,requests_denied,approved_for_others,guard_events,dry_runs,trainee");
        for (StaffLine l : r.staff()) {
            out.add(String.join(",", cell(l.name()), n(l.given()), n(l.warns()), n(l.mutes()), n(l.bans()), n(l.kicks()),
                    n(l.other()), n(l.sanctions()), n(l.reversed()), n(l.reversedPercent()), n(l.appealsAccepted()),
                    n(l.appealsReduced()), n(l.appealsDenied()), n(l.requestsMade()), n(l.requestsApproved()),
                    n(l.requestsDenied()), n(l.approvedForOthers()), n(l.guardEvents()), n(l.dryRuns()), String.valueOf(l.trainee())));
        }
        return out;
    }

    private static String n(long v) {
        return String.valueOf(v);
    }

    private static void row(List<String> out, String section, String name, long value) {
        row(out, section, name, String.valueOf(value));
    }

    private static void row(List<String> out, String section, String name, String value) {
        out.add(cell(section) + "," + cell(name) + "," + cell(value));
    }

    /** RFC 4180: quoted when it holds a comma, quote or line break; a leading = + - @ is defused (spreadsheet formulas). */
    static String cell(String raw) {
        String s = raw == null ? "" : raw;
        if (!s.isEmpty() && "=+-@".indexOf(s.charAt(0)) >= 0 && !s.matches("-?\\d+")) {
            s = "'" + s;
        }
        if (s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }
}
