package net.skybornmc.skyblock.moderation;

import net.skybornmc.skyblock.common.db.Database;
import net.skybornmc.skyblock.common.db.Sql;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.locks.ReentrantLock;

/** Database access for punishments. Everything async; rows are never deleted (pardons set the removed_* columns). */
final class PunishmentStore {

    private static final String COLUMNS = "id, type, target_uuid, target_name, ip, ip_wide, reason, template, ladder_step, "
            + "issuer_uuid, issuer_name, created, expires, silent, removed_at, removed_by_uuid, removed_by_name, removed_reason, source, "
            + "weight, acknowledged_at, approved_by_uuid, approved_by_name";
    private static final String SUGGESTION_COLUMNS = "id, target_uuid, target_name, kind, rule_at, repeat_trigger, action, reason, "
            + "source, warn_count, created, expires, status, handled_at, handled_by_uuid, handled_by_name, punishment_id, note, "
            + "warn_tenths, reason_id, details";

    private final Database db;
    /** spec 13.12: this server's id and scope rules (filter off by default: one server, nothing changes). */
    private volatile java.util.function.Supplier<NetworkScope.Settings> network = () -> NetworkScope.Settings.DEFAULT;

    PunishmentStore(Database db) {
        this.db = db;
    }

    /** Where the network settings come from (the module passes the live punishments.yml value, so reloads apply). */
    void network(java.util.function.Supplier<NetworkScope.Settings> settings) {
        this.network = settings == null ? () -> NetworkScope.Settings.DEFAULT : settings;
    }

    private NetworkScope.Settings networkSettings() {
        NetworkScope.Settings s = network.get();
        return s == null ? NetworkScope.Settings.DEFAULT : s;
    }

    private static Punishment map(ResultSet rs) throws SQLException {
        int step = rs.getInt("ladder_step");
        Integer ladderStep = rs.wasNull() ? null : step;
        return new Punishment(rs.getLong("id"), Punishment.Type.valueOf(rs.getString("type")), Sql.uuid(rs, "target_uuid"),
                rs.getString("target_name"), rs.getString("ip"), rs.getBoolean("ip_wide"), rs.getString("reason"),
                rs.getString("template"), ladderStep, Sql.uuid(rs, "issuer_uuid"), rs.getString("issuer_name"),
                rs.getLong("created"), Sql.nullableLong(rs, "expires"), rs.getBoolean("silent"),
                Sql.nullableLong(rs, "removed_at"), Sql.uuid(rs, "removed_by_uuid"), rs.getString("removed_by_name"),
                rs.getString("removed_reason"), rs.getString("source"), Weights.fromDb(rs.getBigDecimal("weight")),
                Sql.nullableLong(rs, "acknowledged_at"), Sql.uuid(rs, "approved_by_uuid"), rs.getString("approved_by_name"));
    }

    private static byte[] bytes(UUID uuid) {
        return uuid == null ? null : Sql.toBytes(uuid);
    }

    /**
     * Inserts a row (inside the caller's transaction) and returns it with its id. Every row stores this server's id and
     * its scope (spec 13.12): bans and mutes get {@code extras.network.default-scope}, warnings and kicks {@code server}.
     */
    /** Called with every inserted row (P9, spec 14.1: the guard counts what each staff member applied). */
    private volatile java.util.function.Consumer<Punishment> applied = p -> { };

    void onApplied(java.util.function.Consumer<Punishment> hook) {
        this.applied = hook == null ? p -> { } : hook;
    }

    private Punishment insertRow(Connection c, Punishment p) throws SQLException {
        Punishment row = insertRowOnly(c, p);
        try {
            applied.accept(row);
        } catch (RuntimeException e) {
            // the guard never breaks the punishment path
        }
        return row;
    }

    private Punishment insertRowOnly(Connection c, Punishment p) throws SQLException {
        // weight only matters for warns (spec 12.7); NULL = 1 for everything else
        java.math.BigDecimal weight = p.type() == Punishment.Type.WARN && p.weight() != Weights.ONE ? Weights.decimal(p.weight()) : null;
        NetworkScope.Settings ns = networkSettings();
        long id = Sql.insertReturningId(c, "INSERT INTO punishments (type, target_uuid, target_name, ip, ip_wide, reason, "
                        + "template, ladder_step, issuer_uuid, issuer_name, created, expires, silent, source, weight, "
                        + "approved_by_uuid, approved_by_name, server_id, scope) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                p.type().name(), bytes(p.target()), p.targetName(), p.ip(), p.ipWide(), clip(p.reason(), 255), p.template(),
                p.ladderStep(), bytes(p.issuer()), clip(p.issuerName(), 32), p.created(), p.expires(), p.silent(), p.source(), weight,
                bytes(p.approvedBy()), clip(p.approvedByName(), 32), clip(ns.serverId(), 32), NetworkScope.scopeFor(p.type(), ns));
        return p.withId(id);
    }

    private static String clip(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }

    /** Pardons every active punishment of {@code type} for the target (uuid) and/or IP. Returns the pardoned rows. */
    CompletableFuture<List<Punishment>> pardon(Punishment.Type type, UUID target, String ip, UUID by, String byName,
                                               String reason, boolean latestOnly) {
        return db.transaction(c -> {
            long now = System.currentTimeMillis();
            List<Punishment> active = new ArrayList<>(activeOf(c, type, target, ip, now));
            if (latestOnly && active.size() > 1) {
                active = new ArrayList<>(active.subList(0, 1));
            }
            for (Punishment p : active) {
                Sql.update(c, "UPDATE punishments SET removed_at = ?, removed_by_uuid = ?, removed_by_name = ?, removed_reason = ? "
                        + "WHERE id = ? AND removed_at IS NULL", now, bytes(by), byName, reason, p.id());
            }
            return active;
        });
    }

    /**
     * Active punishments of {@code type} for the account and/or IP-wide on the address. With {@code extras.network.enabled}
     * only rows that count on this server (spec 13.12: scope network, or given here); otherwise all of them.
     */
    private List<Punishment> activeOf(Connection c, Punishment.Type type, UUID target, String ip, long now)
            throws SQLException {
        NetworkScope.Settings ns = networkSettings();
        String scope = NetworkScope.sql(ns);
        String server = NetworkScope.parameter(ns);
        List<Punishment> out = new ArrayList<>();
        if (target != null) {
            String sql = "SELECT " + COLUMNS + " FROM punishments WHERE target_uuid = ? AND type = ? "
                    + "AND removed_at IS NULL AND (expires IS NULL OR expires > ?)" + scope + " ORDER BY created DESC";
            out.addAll(server == null ? Sql.query(c, sql, PunishmentStore::map, Sql.toBytes(target), type.name(), now)
                    : Sql.query(c, sql, PunishmentStore::map, Sql.toBytes(target), type.name(), now, server));
        }
        if (ip != null) {
            String sql = "SELECT " + COLUMNS + " FROM punishments WHERE ip = ? AND ip_wide = TRUE AND type = ? "
                    + "AND removed_at IS NULL AND (expires IS NULL OR expires > ?)" + scope + " ORDER BY created DESC";
            for (Punishment p : server == null ? Sql.query(c, sql, PunishmentStore::map, ip, type.name(), now)
                    : Sql.query(c, sql, PunishmentStore::map, ip, type.name(), now, server)) {
                if (out.stream().noneMatch(o -> o.id() == p.id())) {
                    out.add(p);
                }
            }
        }
        return out;
    }

    /** The active ban for this account or an IP-wide ban on this address (login check). */
    CompletableFuture<Optional<Punishment>> activeBan(UUID target, String ip) {
        return db.async(c -> activeOf(c, Punishment.Type.BAN, target, ip, System.currentTimeMillis()).stream().findFirst());
    }

    CompletableFuture<Optional<Punishment>> activeMute(UUID target, String ip) {
        return db.async(c -> activeOf(c, Punishment.Type.MUTE, target, ip, System.currentTimeMillis()).stream().findFirst());
    }

    /** Blocking variant for AsyncPlayerPreLoginEvent (already off the main thread). */
    Optional<Punishment> activeBlocking(Punishment.Type type, UUID target, String ip) {
        return db.blocking(c -> activeOf(c, type, target, ip, System.currentTimeMillis()).stream().findFirst());
    }

    /** All punishments of a player, newest first. */
    CompletableFuture<List<Punishment>> history(UUID target) {
        return db.async(c -> Sql.query(c, "SELECT " + COLUMNS + " FROM punishments WHERE target_uuid = ? ORDER BY created DESC",
                PunishmentStore::map, Sql.toBytes(target)));
    }

    /** How many earlier (not pardoned) punishments of this template the player has: decides the ladder step. */
    CompletableFuture<Integer> templateCount(UUID target, String template) {
        return db.async(c -> Sql.queryOne(c, "SELECT COUNT(*) AS n FROM punishments WHERE target_uuid = ? AND template = ? "
                        + "AND removed_at IS NULL", rs -> rs.getInt("n"), Sql.toBytes(target), template).orElse(0));
    }

    /** Newest known IP of a player (from the social module's join history), for -ip and /ipban on offline players. */
    CompletableFuture<Optional<String>> lastIp(UUID target) {
        return db.async(c -> {
            try {
                return Sql.queryOne(c, "SELECT ip FROM social_player_ips WHERE uuid = ? ORDER BY last_seen DESC LIMIT 1",
                        rs -> rs.getString("ip"), Sql.toBytes(target));
            } catch (SQLException e) {
                return Optional.empty(); // social module not installed
            }
        });
    }

    /** An account that shares an address with the given one, with its active ban/mute (if any). */
    record Alt(UUID uuid, String name, String ip, long lastSeen, Punishment ban, Punishment mute) {
    }

    /** Accounts sharing any address with {@code uuid} or with {@code ip} (if given), max 30, with active bans/mutes. */
    CompletableFuture<List<Alt>> alts(UUID uuid, String ip) {
        return db.async(c -> altsBlocking(c, uuid, ip));
    }

    List<Alt> altsBlocking(UUID uuid, String ip) {
        return db.blocking(c -> altsBlocking(c, uuid, ip));
    }

    private List<Alt> altsBlocking(Connection c, UUID uuid, String ip) throws SQLException {
        record Row(UUID uuid, String name, String ip, long lastSeen) {
        }
        List<Row> rows;
        try {
            rows = Sql.query(c, "SELECT o.uuid, p.name, o.ip, o.last_seen FROM social_player_ips o "
                            + "LEFT JOIN players p ON p.uuid = o.uuid "
                            + "WHERE o.uuid <> ? AND (o.ip = ? OR o.ip IN (SELECT m.ip FROM social_player_ips m WHERE m.uuid = ?)) "
                            + "ORDER BY o.last_seen DESC LIMIT 30",
                    rs -> new Row(Sql.uuid(rs, "uuid"), rs.getString("name"), rs.getString("ip"), rs.getLong("last_seen")),
                    Sql.toBytes(uuid), ip == null ? "" : ip, Sql.toBytes(uuid));
        } catch (SQLException e) {
            return List.of(); // social module not installed
        }
        long now = System.currentTimeMillis();
        List<Alt> out = new ArrayList<>();
        java.util.Set<UUID> seen = new java.util.HashSet<>();
        for (Row r : rows) {
            if (!seen.add(r.uuid())) {
                continue;
            }
            Punishment ban = activeOf(c, Punishment.Type.BAN, r.uuid(), null, now).stream().findFirst().orElse(null);
            Punishment mute = activeOf(c, Punishment.Type.MUTE, r.uuid(), null, now).stream().findFirst().orElse(null);
            out.add(new Alt(r.uuid(), r.name() == null ? r.uuid().toString() : r.name(), r.ip(), r.lastSeen(), ban, mute));
        }
        return out;
    }

    /** Names of players with an active ban (tab completion for /unban), max 200. */
    CompletableFuture<List<String>> bannedNames() {
        return db.async(c -> Sql.query(c, "SELECT DISTINCT target_name FROM punishments WHERE type = 'BAN' AND removed_at IS NULL "
                + "AND (expires IS NULL OR expires > ?) ORDER BY target_name LIMIT 200", rs -> rs.getString("target_name"),
                System.currentTimeMillis()));
    }

    // ================================================================== Phase P: one atomic issue path + warn rules

    /** Per-target lock stripes: everything that decides about one target runs one at a time (spec 5.5, 12.2). */
    private final ReentrantLock[] stripes = new ReentrantLock[64];

    {
        for (int i = 0; i < stripes.length; i++) {
            stripes[i] = new ReentrantLock();
        }
    }

    /**
     * Runs {@code work} in one transaction while holding the target's lock, so the decision (active punishments, warn
     * counter) and the writes are atomic: two staff warning the same player in the same tick can't both fire a rule,
     * two [Apply] clicks can't both apply. The lock covers the commit. (Network-ready, spec 13.12, will add a database
     * row lock here; one server needs none.)
     */
    private <T> T locked(Connection c, UUID target, Sql.SqlFunction<T> work) throws SQLException {
        ReentrantLock lock = target == null ? null : stripes[Math.floorMod(target.hashCode(), stripes.length)];
        if (lock != null) {
            lock.lock();
        }
        try {
            boolean auto = c.getAutoCommit();
            c.setAutoCommit(false);
            try {
                T result = work.apply(c);
                c.commit();
                return result;
            } catch (SQLException | RuntimeException e) {
                try {
                    c.rollback();
                } catch (SQLException rollback) {
                    e.addSuppressed(rollback);
                }
                throw e;
            } finally {
                c.setAutoCommit(auto);
            }
        } finally {
            if (lock != null) {
                lock.unlock();
            }
        }
    }

    /** The target's longest active mute and ban (perm wins), or {@code null}. */
    record Active(Punishment mute, Punishment ban) {
    }

    private Active active(Connection c, UUID target, String ip, long now) throws SQLException {
        return new Active(longest(activeOf(c, Punishment.Type.MUTE, target, ip, now)),
                longest(activeOf(c, Punishment.Type.BAN, target, ip, now)));
    }

    static Punishment longest(List<Punishment> list) {
        Punishment best = null;
        for (Punishment p : list) {
            if (best == null || PunishDecision.covers(p.expires(), best.expires()) && !java.util.Objects.equals(p.expires(), best.expires())) {
                best = p;
            }
        }
        return best;
    }

    /** What the warn rules need inside the issue transaction. */
    record RuleHook(WarnRules.Settings settings, PunishDecision decision, PunishDecision.Input autoTemplate,
                    String autoIssuerName, java.util.function.Function<WarnRules.Trigger, String> reason) {
    }

    /** A fired warn rule: the suggestion row, and in auto mode the punishment it created. */
    record TriggerResult(WarnRules.Trigger trigger, Suggestion suggestion, Punishment autoPunishment,
                         PunishDecision.Verdict verdict) {
    }

    /**
     * Outcome of {@link #issue}: {@code punishment == null} unless the verdict applied; {@code request} = the request
     * created (or merged into, {@code merged}) for a REQUEST verdict (spec 13.4).
     */
    record IssueResult(PunishDecision.Verdict verdict, Punishment punishment, TriggerResult trigger, PunishRequest request,
                       boolean merged) {

        IssueResult(PunishDecision.Verdict verdict, Punishment punishment, TriggerResult trigger) {
            this(verdict, punishment, trigger, null, false);
        }
    }

    /** How long a request waits when none is given (spec 13.4 default). */
    static final java.time.Duration DEFAULT_REQUEST_EXPIRE = java.time.Duration.ofHours(24);

    /**
     * The one way to store a punishment: decides with {@code decision} (active mute/ban read in the same transaction),
     * inserts the row if it applies, then evaluates the warn rules in the same transaction ({@code hook == null} =
     * rules off). {@code draft} supplies everything but type/duration, which come from the (possibly changed) verdict.
     */
    CompletableFuture<IssueResult> issue(Punishment draft, PunishDecision.Input input, PunishDecision decision, RuleHook hook) {
        return issue(draft, input, decision, hook, DEFAULT_REQUEST_EXPIRE);
    }

    /** Same, with how long a request waits for an approver (REQUEST verdicts, spec 13.4). */
    CompletableFuture<IssueResult> issue(Punishment draft, PunishDecision.Input input, PunishDecision decision, RuleHook hook,
                                         java.time.Duration requestExpire) {
        return issue(draft, input, decision, hook, requestExpire, null);
    }

    /** Same, with the {@code /logs} line a request keeps as evidence (spec 13.11), or {@code null}. */
    CompletableFuture<IssueResult> issue(Punishment draft, PunishDecision.Input input, PunishDecision decision, RuleHook hook,
                                         java.time.Duration requestExpire, Long evidenceEntry) {
        return db.async(c -> issueBlocking(c, draft, input, decision, hook, requestExpire, evidenceEntry));
    }

    IssueResult issueBlocking(Connection c, Punishment draft, PunishDecision.Input input, PunishDecision decision, RuleHook hook)
            throws SQLException {
        return issueBlocking(c, draft, input, decision, hook, DEFAULT_REQUEST_EXPIRE);
    }

    IssueResult issueBlocking(Connection c, Punishment draft, PunishDecision.Input input, PunishDecision decision, RuleHook hook,
                              java.time.Duration requestExpire) throws SQLException {
        return issueBlocking(c, draft, input, decision, hook, requestExpire, null);
    }

    IssueResult issueBlocking(Connection c, Punishment draft, PunishDecision.Input input, PunishDecision decision, RuleHook hook,
                              java.time.Duration requestExpire, Long evidenceEntry) throws SQLException {
        return locked(c, draft.target(), conn -> {
            long now = input.now();
            Active act = active(conn, draft.target(), draft.ip(), now);
            Punishment recent = input.reasonKey() == null || draft.target() == null ? null
                    : newestFor(conn, draft.target(), input.reasonKey());
            PunishDecision.Verdict verdict = decision.decide(input.withActive(act.mute(), act.ban()).withRecent(recent));
            if (verdict.outcome() == PunishDecision.Outcome.REQUEST) {
                RequestSaved saved = requestLocked(conn, shaped(draft, verdict.input()), verdict.input().duration(), now,
                        requestExpire == null ? DEFAULT_REQUEST_EXPIRE : requestExpire, evidenceEntry,
                        verdict.input().actor().trainee());
                return new IssueResult(verdict, null, null, saved.request(), saved.merged());
            }
            if (!verdict.applied()) {
                return new IssueResult(verdict, null, null);
            }
            Punishment row = insertRow(conn, shaped(draft, verdict.input()));
            TriggerResult trigger = hook == null || row.target() == null ? null : runRules(conn, row, hook, now);
            return new IssueResult(verdict, row, trigger);
        });
    }

    /** The target's newest not-pardoned punishment for this reason (duplicate check, spec 12.2), or {@code null}. */
    private static Punishment newestFor(Connection c, UUID target, String template) throws SQLException {
        return Sql.queryOne(c, "SELECT " + COLUMNS + " FROM punishments WHERE target_uuid = ? AND template = ? "
                        + "AND removed_at IS NULL ORDER BY created DESC, id DESC LIMIT 1", PunishmentStore::map,
                Sql.toBytes(target), template).orElse(null);
    }

    /**
     * {@code draft} with the type, duration and scope the verdict settled on. A step raised by probation (spec 13.7)
     * stores its own ladder index and weight. IP-wide only with a known address (a raised {@code ipban} step for a player
     * without one stays an account ban).
     */
    private static Punishment shaped(Punishment d, PunishDecision.Input in) {
        boolean timed = in.type().timed();
        PunishDecision.Escalation e = in.escalation();
        boolean raised = e != null && e.raised();
        Integer step = raised ? Integer.valueOf(e.index()) : d.ladderStep();
        int weight = in.type() != Punishment.Type.WARN ? Weights.ONE : raised ? e.step().weight() : d.weight();
        boolean ipWide = in.ipWide() && d.ip() != null;
        return new Punishment(0, in.type(), d.target(), d.targetName(), ipWide ? d.ip() : null, ipWide, d.reason(),
                d.template(), step, d.issuer(), d.issuerName(), d.created(), timed ? in.endsAt() : null, d.silent(),
                null, null, null, null, d.source(), weight, null, d.approvedBy(), d.approvedByName());
    }

    private TriggerResult runRules(Connection c, Punishment event, RuleHook hook, long now) throws SQLException {
        WarnRules.Settings settings = hook.settings();
        if (!settings.enabled()) {
            return null;
        }
        List<Punishment> history = Sql.query(c, "SELECT " + COLUMNS + " FROM punishments WHERE target_uuid = ? "
                + "AND type IN ('WARN', 'MUTE')", PunishmentStore::map, Sql.toBytes(event.target()));
        List<Suggestion> suggestions = suggestions(c, event.target());
        java.util.Optional<WarnRules.Trigger> fired = WarnRules.evaluate(event, history, suggestions, settings, now);
        if (fired.isEmpty()) {
            return null;
        }
        WarnRules.Trigger trigger = fired.get();
        Templates.Step step = trigger.action();
        Active act = active(c, event.target(), null, now);
        PunishDecision.Input autoIn = hook.autoTemplate().withStep(step.type(), step.duration(), step.ipWide())
                .withActive(act.mute(), act.ban());
        PunishDecision.Verdict verdict = hook.decision().decide(autoIn);
        String reason = hook.reason().apply(trigger);
        Suggestion row = new Suggestion(0, event.target(), event.targetName(), Suggestion.KIND_WARN_RULE, trigger.ruleAt(),
                trigger.repeat(), step.describe(), clip(reason, 255), trigger.source(), trigger.count(), now, null,
                Suggestion.Status.PENDING, null, null, null, null, null, trigger.tenths(), null, null);
        if (!verdict.applied()) {
            String note = "exempt".equals(verdict.stage()) ? Suggestion.NOTE_EXEMPT
                    : "never-shorten".equals(verdict.stage()) ? Suggestion.NOTE_ALREADY : clip(verdict.stage(), 32);
            return new TriggerResult(trigger, insertSuggestion(c, with(row, Suggestion.Status.SKIPPED, null, now, null, null,
                    null, note), event.id()), null, verdict);
        }
        if (settings.mode() == WarnRules.Mode.AUTO) {
            String ip = step.ipWide() ? lastIpBlocking(c, event.target()) : null;
            Punishment auto = insertRow(c, shaped(new Punishment(0, step.type(), event.target(), event.targetName(), ip,
                    step.ipWide() && ip != null, reason, null, null, null, hook.autoIssuerName(), now, null, false, null,
                    null, null, null, trigger.source()), verdict.input().withStep(step.type(), step.duration(),
                    step.ipWide() && ip != null)));
            return new TriggerResult(trigger, insertSuggestion(c, with(row, Suggestion.Status.AUTO, null, now, null,
                    hook.autoIssuerName(), auto.id(), null), event.id()), auto, verdict);
        }
        Long expires = settings.pendingExpire() == null ? null : now + settings.pendingExpire().toMillis();
        return new TriggerResult(trigger, insertSuggestion(c, with(row, Suggestion.Status.PENDING, expires, null, null, null,
                null, null), event.id()), null, verdict);
    }

    private static Suggestion with(Suggestion s, Suggestion.Status status, Long expires, Long handledAt, UUID by,
                                   String byName, Long punishmentId, String note) {
        return new Suggestion(s.id(), s.target(), s.targetName(), s.kind(), s.ruleAt(), s.repeat(), s.action(), s.reason(),
                s.source(), s.warnCount(), s.created(), expires, status, handledAt, by, byName, punishmentId, note,
                s.warnTenths(), s.reasonId(), s.details());
    }

    /** Inserts a suggestion; {@code causeId} = the punishment that fired it (undoing that warn removes it, 12.1). */
    private static Suggestion insertSuggestion(Connection c, Suggestion s, Long causeId) throws SQLException {
        long id = Sql.insertReturningId(c, "INSERT INTO punishment_suggestions (target_uuid, target_name, kind, rule_at, "
                        + "repeat_trigger, action, reason, source, warn_count, created, expires, status, handled_at, handled_by_uuid, "
                        + "handled_by_name, punishment_id, note, cause_id, warn_tenths, reason_id, details) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                bytes(s.target()), s.targetName(), s.kind(), s.ruleAt(), s.repeat(), s.action(), s.reason(), clip(s.source(), 48),
                s.warnCount(), s.created(), s.expires(), s.status().name(), s.handledAt(), bytes(s.handledBy()),
                clip(s.handledByName(), 32), s.punishmentId(), s.note(), causeId, s.warnTenths(), s.reasonId(),
                clip(s.details(), 255));
        return new Suggestion(id, s.target(), s.targetName(), s.kind(), s.ruleAt(), s.repeat(), s.action(), s.reason(),
                s.source(), s.warnCount(), s.created(), s.expires(), s.status(), s.handledAt(), s.handledBy(), s.handledByName(),
                s.punishmentId(), s.note(), s.warnTenths(), s.reasonId(), s.details());
    }

    private static Suggestion mapSuggestion(ResultSet rs) throws SQLException {
        int at = rs.getInt("rule_at");
        Integer ruleAt = rs.wasNull() ? null : at;
        return new Suggestion(rs.getLong("id"), Sql.uuid(rs, "target_uuid"), rs.getString("target_name"), rs.getString("kind"),
                ruleAt, rs.getBoolean("repeat_trigger"), rs.getString("action"), rs.getString("reason"), rs.getString("source"),
                rs.getInt("warn_count"), rs.getLong("created"), Sql.nullableLong(rs, "expires"),
                Suggestion.Status.valueOf(rs.getString("status")), Sql.nullableLong(rs, "handled_at"),
                Sql.uuid(rs, "handled_by_uuid"), rs.getString("handled_by_name"), Sql.nullableLong(rs, "punishment_id"),
                rs.getString("note"), nullableInt(rs, "warn_tenths"), rs.getString("reason_id"), rs.getString("details"));
    }

    private static Integer nullableInt(ResultSet rs, String column) throws SQLException {
        int v = rs.getInt(column);
        return rs.wasNull() ? null : v;
    }

    private static List<Suggestion> suggestions(Connection c, UUID target) throws SQLException {
        // REMOVED rows (their warn was undone, 12.1) count as if they never fired, so the rules never see them.
        return Sql.query(c, "SELECT " + SUGGESTION_COLUMNS + " FROM punishment_suggestions WHERE target_uuid = ? AND kind = ? "
                + "AND status <> 'REMOVED' ORDER BY created DESC, id DESC", PunishmentStore::mapSuggestion, Sql.toBytes(target),
                Suggestion.KIND_WARN_RULE);
    }

    /** All warn-rule suggestions of a player, newest first (the counter is derived from these + the punishments). */
    CompletableFuture<List<Suggestion>> suggestions(UUID target) {
        return db.async(c -> suggestions(c, target));
    }

    CompletableFuture<java.util.Optional<Suggestion>> suggestion(long id) {
        return db.async(c -> suggestionById(c, id));
    }

    private static java.util.Optional<Suggestion> suggestionById(Connection c, long id) throws SQLException {
        return Sql.queryOne(c, "SELECT " + SUGGESTION_COLUMNS + " FROM punishment_suggestions WHERE id = ?",
                PunishmentStore::mapSuggestion, id);
    }

    /** Ids of suggestions still waiting (tab completion of /warns ... apply|dismiss), max 100. */
    CompletableFuture<List<String>> pendingIds() {
        return db.async(c -> Sql.query(c, "SELECT id FROM punishment_suggestions WHERE status = 'PENDING' "
                + "AND (expires IS NULL OR expires > ?) ORDER BY id DESC LIMIT 100", rs -> String.valueOf(rs.getLong("id")),
                System.currentTimeMillis()));
    }

    /** What happened when a staff member clicked [Apply] or [Dismiss]. REQUESTED = sent for approval (spec 13.4). */
    enum HandleKind { APPLIED, DISMISSED, SKIPPED, BLOCKED, ALREADY_HANDLED, EXPIRED, NOT_FOUND, REQUESTED }

    record HandleResult(HandleKind kind, Suggestion suggestion, PunishDecision.Verdict verdict, Punishment punishment,
                        PunishRequest request, boolean merged) {

        HandleResult(HandleKind kind, Suggestion suggestion, PunishDecision.Verdict verdict, Punishment punishment) {
            this(kind, suggestion, verdict, punishment, null, false);
        }
    }

    /**
     * [Apply]: claims the pending suggestion and applies its action as {@code by}, atomically (first click wins). The
     * clicker's own limits apply ({@code inputFor} builds the request with their permissions); a BLOCKED verdict leaves
     * it pending for someone who may. An already longer mute/ban marks it SKIPPED (never shorten).
     */
    CompletableFuture<HandleResult> apply(long id, java.util.function.Function<Suggestion, PunishDecision.Input> inputFor,
                                          PunishDecision decision, UUID by, String byName, long now) {
        return apply(id, inputFor, decision, by, byName, now, DEFAULT_REQUEST_EXPIRE);
    }

    /**
     * Same; a REQUEST verdict (the clicker needs an approver, spec 13.4) creates a request with the suggestion's punishment
     * and marks the suggestion REQUESTED (handled: the warn counter restarts as for any handled trigger).
     */
    CompletableFuture<HandleResult> apply(long id, java.util.function.Function<Suggestion, PunishDecision.Input> inputFor,
                                          PunishDecision decision, UUID by, String byName, long now,
                                          java.time.Duration requestExpire) {
        return db.async(c -> {
            java.util.Optional<Suggestion> first = suggestionById(c, id);
            if (first.isEmpty()) {
                return new HandleResult(HandleKind.NOT_FOUND, null, null, null);
            }
            return locked(c, first.get().target(), conn -> {
                Suggestion s = suggestionById(conn, id).orElseThrow();
                HandleResult gone = notPending(s, now);
                if (gone != null) {
                    return gone;
                }
                Templates.Step step = s.step();
                Active act = active(conn, s.target(), null, now);
                PunishDecision.Verdict verdict = decision.decide(inputFor.apply(s).withActive(act.mute(), act.ban()));
                if (verdict.outcome() == PunishDecision.Outcome.BLOCKED) {
                    return new HandleResult(HandleKind.BLOCKED, s, verdict, null);
                }
                if (verdict.outcome() == PunishDecision.Outcome.SKIPPED) {
                    claim(conn, id, Suggestion.Status.SKIPPED, by, byName, null, Suggestion.NOTE_ALREADY, now);
                    return new HandleResult(HandleKind.SKIPPED, suggestionById(conn, id).orElseThrow(), verdict, null);
                }
                if (verdict.outcome() == PunishDecision.Outcome.REQUEST) {
                    String rip = verdict.input().ipWide() ? lastIpBlocking(conn, s.target()) : null;
                    Punishment wanted = shaped(new Punishment(0, step.type(), s.target(), s.targetName(), rip, false,
                            s.reason(), s.api() ? s.reasonId() : null, null, by, byName, now, null, false, null, null, null, null,
                            s.source()), verdict.input());
                    RequestSaved saved = requestLocked(conn, wanted, verdict.input().duration(), now,
                            requestExpire == null ? DEFAULT_REQUEST_EXPIRE : requestExpire, null,
                            verdict.input().actor().trainee());
                    if (claim(conn, id, Suggestion.Status.REQUESTED, by, byName, null,
                            Suggestion.NOTE_REQUEST + saved.request().id(), now) != 1) {
                        throw new SQLException("suggestion " + id + " changed while locked");
                    }
                    return new HandleResult(HandleKind.REQUESTED, suggestionById(conn, id).orElseThrow(), verdict, null,
                            saved.request(), saved.merged());
                }
                String ip = verdict.input().ipWide() ? lastIpBlocking(conn, s.target()) : null;
                PunishDecision.Input eff = verdict.input().withStep(verdict.input().type(), verdict.input().duration(),
                        verdict.input().ipWide() && ip != null);
                // API suggestions (12.8) carry their reason id, so the applied punishment counts as an offence of that reason
                Punishment p = insertRow(conn, shaped(new Punishment(0, step.type(), s.target(), s.targetName(), ip, false,
                        s.reason(), s.api() ? s.reasonId() : null, null, by, byName, now, null, false, null, null, null, null,
                        s.source()), eff));
                if (claim(conn, id, Suggestion.Status.APPLIED, by, byName, p.id(), null, now) != 1) {
                    throw new SQLException("suggestion " + id + " changed while locked");
                }
                return new HandleResult(HandleKind.APPLIED, suggestionById(conn, id).orElseThrow(), verdict, p);
            });
        });
    }

    /** [Dismiss]: claims the pending suggestion without applying anything (first click wins). */
    CompletableFuture<HandleResult> dismiss(long id, UUID by, String byName, long now) {
        return db.async(c -> {
            java.util.Optional<Suggestion> first = suggestionById(c, id);
            if (first.isEmpty()) {
                return new HandleResult(HandleKind.NOT_FOUND, null, null, null);
            }
            return locked(c, first.get().target(), conn -> {
                Suggestion s = suggestionById(conn, id).orElseThrow();
                HandleResult gone = notPending(s, now);
                if (gone != null) {
                    return gone;
                }
                claim(conn, id, Suggestion.Status.DISMISSED, by, byName, null, null, now);
                return new HandleResult(HandleKind.DISMISSED, suggestionById(conn, id).orElseThrow(), null, null);
            });
        });
    }

    private static HandleResult notPending(Suggestion s, long now) {
        if (s.expired(now)) {
            return new HandleResult(HandleKind.EXPIRED, s, null, null);
        }
        if (!s.pending(now)) {
            return new HandleResult(HandleKind.ALREADY_HANDLED, s, null, null);
        }
        return null;
    }

    private static int claim(Connection c, long id, Suggestion.Status status, UUID by, String byName, Long punishmentId,
                             String note, long now) throws SQLException {
        return Sql.update(c, "UPDATE punishment_suggestions SET status = ?, handled_at = ?, handled_by_uuid = ?, "
                        + "handled_by_name = ?, punishment_id = ?, note = ? WHERE id = ? AND status = 'PENDING' "
                        + "AND (expires IS NULL OR expires > ?)",
                status.name(), now, bytes(by), clip(byName, 32), punishmentId, note, id, now);
    }

    // ================================================================== P2: undo (spec 12.1)

    /** Outcome of {@link #undo}: {@code punishment} is the row as it was before (null if not found). */
    record UndoResult(Undo.Result result, Punishment punishment, List<Long> removedSuggestions) {
    }

    private static Optional<Punishment> byId(Connection c, long id) throws SQLException {
        return Sql.queryOne(c, "SELECT " + COLUMNS + " FROM punishments WHERE id = ?", PunishmentStore::map, id);
    }

    CompletableFuture<Optional<Punishment>> punishment(long id) {
        return db.async(c -> byId(c, id));
    }

    /**
     * [Undo]: checks {@link Undo#check} and pardons the punishment with {@code reason}, atomically under the target's lock.
     * Undoing a warn also removes the pending suggestion that warn fired (status REMOVED: it counts as never fired).
     */
    CompletableFuture<UndoResult> undo(long id, UUID by, String byName, boolean others, java.time.Duration window,
                                       String reason, long now) {
        return db.async(c -> {
            Optional<Punishment> first = byId(c, id);
            if (first.isEmpty()) {
                return new UndoResult(Undo.Result.NOT_FOUND, null, List.of());
            }
            return locked(c, first.get().target(), conn -> {
                Punishment p = byId(conn, id).orElse(null);
                Undo.Result check = Undo.check(p, by, others, window, now);
                if (check != Undo.Result.OK) {
                    return new UndoResult(check, p, List.of());
                }
                Sql.update(conn, "UPDATE punishments SET removed_at = ?, removed_by_uuid = ?, removed_by_name = ?, removed_reason = ? "
                        + "WHERE id = ? AND removed_at IS NULL", now, bytes(by), clip(byName, 32), clip(reason, 255), id);
                List<Long> removed = List.of();
                if (p.type() == Punishment.Type.WARN) {
                    removed = Sql.query(conn, "SELECT id FROM punishment_suggestions WHERE cause_id = ? AND status = 'PENDING'",
                            rs -> rs.getLong("id"), id);
                    if (!removed.isEmpty()) {
                        Sql.update(conn, "UPDATE punishment_suggestions SET status = 'REMOVED', handled_at = ?, handled_by_uuid = ?, "
                                        + "handled_by_name = ?, note = ? WHERE cause_id = ? AND status = 'PENDING'",
                                now, bytes(by), clip(byName, 32), Suggestion.NOTE_UNDONE, id);
                    }
                }
                return new UndoResult(Undo.Result.OK, p, removed);
            });
        });
    }

    // ================================================================== P2: /pstats (spec 12.10)

    private static PunishStats.Row statRow(ResultSet rs) throws SQLException {
        return new PunishStats.Row(Punishment.Type.valueOf(rs.getString("type")), rs.getLong("n"), rs.getLong("rev"),
                rs.getLong("auto"), rs.getLong("rb"));
    }

    private static final String STAT_SUMS = "type, COUNT(*) AS n, "
            + "SUM(CASE WHEN removed_at IS NOT NULL THEN 1 ELSE 0 END) AS rev, "
            + "SUM(CASE WHEN source LIKE 'auto:%' THEN 1 ELSE 0 END) AS auto, "
            + "SUM(CASE WHEN rollback_id IS NOT NULL THEN 1 ELSE 0 END) AS rb";

    /** Grouped counts of what {@code staff} gave since {@code since} (index issuer_uuid, created). */
    CompletableFuture<List<PunishStats.Row>> statRows(UUID staff, long since) {
        return db.async(c -> Sql.query(c, "SELECT " + STAT_SUMS + " FROM punishments WHERE issuer_uuid = ? AND created >= ? "
                + "GROUP BY type", PunishmentStore::statRow, Sql.toBytes(staff), since));
    }

    /** Grouped counts of every staff member since {@code since}, plus their newest name. */
    record TopRows(java.util.Map<UUID, String> names, java.util.Map<UUID, List<PunishStats.Row>> rows) {
    }

    CompletableFuture<TopRows> topRows(long since) {
        return db.async(c -> {
            java.util.Map<UUID, String> names = new java.util.HashMap<>();
            java.util.Map<UUID, List<PunishStats.Row>> rows = new java.util.HashMap<>();
            Sql.query(c, "SELECT issuer_uuid, MAX(issuer_name) AS name, " + STAT_SUMS + " FROM punishments "
                    + "WHERE issuer_uuid IS NOT NULL AND created >= ? GROUP BY issuer_uuid, type", rs -> {
                UUID uuid = Sql.uuid(rs, "issuer_uuid");
                names.put(uuid, rs.getString("name"));
                rows.computeIfAbsent(uuid, k -> new ArrayList<>()).add(statRow(rs));
                return uuid;
            }, since);
            return new TopRows(names, rows);
        });
    }

    /** Names of staff who ever punished someone (tab completion of /pstats), max 200. */
    CompletableFuture<List<String>> staffNames() {
        return db.async(c -> Sql.query(c, "SELECT DISTINCT issuer_name FROM punishments WHERE issuer_uuid IS NOT NULL "
                + "ORDER BY issuer_name LIMIT 200", rs -> rs.getString("issuer_name")));
    }

    /** A staff member by the name they punished with (newest), for /pstats <staff>. */
    CompletableFuture<Optional<java.util.Map.Entry<UUID, String>>> staffByName(String name) {
        return db.async(c -> Sql.queryOne(c, "SELECT issuer_uuid, issuer_name FROM punishments WHERE issuer_uuid IS NOT NULL "
                        + "AND LOWER(issuer_name) = LOWER(?) ORDER BY created DESC LIMIT 1",
                rs -> java.util.Map.entry(Sql.uuid(rs, "issuer_uuid"), rs.getString("issuer_name")), name));
    }

    // ================================================================== P3: evidence (spec 12.4)

    /** Saves the evidence lines of a punishment (already sanitized by {@link Evidence#build}). */
    CompletableFuture<Void> saveEvidence(long punishmentId, long created, List<String> lines) {
        return db.run(c -> Sql.update(c, "INSERT INTO punishment_evidence (punishment_id, created, line_count, chat_lines) "
                + "VALUES (?, ?, ?, ?)", punishmentId, created, lines.size(), Evidence.join(lines)));
    }

    /** Evidence lines of a punishment (empty = none saved, or deleted after the retention). */
    CompletableFuture<List<String>> evidence(long punishmentId) {
        return db.async(c -> Sql.queryOne(c, "SELECT chat_lines FROM punishment_evidence WHERE punishment_id = ? ORDER BY id DESC LIMIT 1",
                rs -> Evidence.split(rs.getString("chat_lines")), punishmentId).orElse(List.of()));
    }

    /** Ids of the given punishments that have evidence (history menu marks them). */
    CompletableFuture<java.util.Set<Long>> withEvidence(UUID target) {
        return db.async(c -> new java.util.HashSet<>(Sql.query(c, "SELECT e.punishment_id FROM punishment_evidence e "
                + "JOIN punishments p ON p.id = e.punishment_id WHERE p.target_uuid = ?", rs -> rs.getLong(1), Sql.toBytes(target))));
    }

    /** Deletes evidence older than {@code before} (retention); returns how many rows went. */
    CompletableFuture<Integer> purgeEvidence(long before) {
        return db.async(c -> Sql.update(c, "DELETE FROM punishment_evidence WHERE created < ?", before));
    }

    // ================================================================== P3: staff notes (spec 12.5)

    private static final String NOTE_COLUMNS = "id, target_uuid, target_name, author_uuid, author_name, note, created, removed, "
            + "removed_at, removed_by_uuid, removed_by_name";

    private static Notes.Note mapNote(ResultSet rs) throws SQLException {
        return new Notes.Note(rs.getLong("id"), Sql.uuid(rs, "target_uuid"), rs.getString("target_name"), Sql.uuid(rs, "author_uuid"),
                rs.getString("author_name"), rs.getString("note"), rs.getLong("created"), rs.getBoolean("removed"),
                Sql.nullableLong(rs, "removed_at"), Sql.uuid(rs, "removed_by_uuid"), rs.getString("removed_by_name"));
    }

    CompletableFuture<Notes.Note> addNote(UUID target, String targetName, UUID author, String authorName, String text, long now) {
        return db.async(c -> {
            long id = Sql.insertReturningId(c, "INSERT INTO staff_notes (target_uuid, target_name, author_uuid, author_name, note, "
                            + "created) VALUES (?, ?, ?, ?, ?, ?)", Sql.toBytes(target), clip(targetName, 32), bytes(author),
                    clip(authorName, 32), clip(text, 255), now);
            return new Notes.Note(id, target, targetName, author, authorName, clip(text, 255), now, false, null, null, null);
        });
    }

    /** Active notes of a player, newest first ({@code limit} rows at most). */
    CompletableFuture<List<Notes.Note>> notes(UUID target, int limit) {
        return db.async(c -> Sql.query(c, "SELECT " + NOTE_COLUMNS + " FROM staff_notes WHERE target_uuid = ? AND removed = FALSE "
                + "ORDER BY created DESC, id DESC LIMIT " + Math.max(1, limit), PunishmentStore::mapNote, Sql.toBytes(target)));
    }

    /** Number of active notes of a player. */
    CompletableFuture<Integer> noteCount(UUID target) {
        return db.async(c -> Sql.queryOne(c, "SELECT COUNT(*) FROM staff_notes WHERE target_uuid = ? AND removed = FALSE",
                rs -> rs.getInt(1), Sql.toBytes(target)).orElse(0));
    }

    CompletableFuture<Optional<Notes.Note>> note(long id) {
        return db.async(c -> Sql.queryOne(c, "SELECT " + NOTE_COLUMNS + " FROM staff_notes WHERE id = ?", PunishmentStore::mapNote, id));
    }

    /**
     * Marks a note as removed (the row stays). Returns the note as it was before, or empty if it does not exist;
     * {@code changed} is false when it was already removed.
     */
    record NoteRemoval(Notes.Note note, boolean changed) {
    }

    CompletableFuture<Optional<NoteRemoval>> removeNote(long id, UUID by, String byName, long now) {
        return db.transaction(c -> {
            Optional<Notes.Note> before = Sql.queryOne(c, "SELECT " + NOTE_COLUMNS + " FROM staff_notes WHERE id = ?",
                    PunishmentStore::mapNote, id);
            if (before.isEmpty()) {
                return Optional.<NoteRemoval>empty();
            }
            int n = Sql.update(c, "UPDATE staff_notes SET removed = TRUE, removed_at = ?, removed_by_uuid = ?, removed_by_name = ? "
                    + "WHERE id = ? AND removed = FALSE", now, bytes(by), clip(byName, 32), id);
            return Optional.of(new NoteRemoval(before.get(), n == 1));
        });
    }

    /** Ids of recent active notes (tab completion of /pnote remove), newest first, max 100. */
    CompletableFuture<List<String>> recentNoteIds(UUID target) {
        return db.async(c -> target == null
                ? Sql.query(c, "SELECT id FROM staff_notes WHERE removed = FALSE ORDER BY id DESC LIMIT 100",
                rs -> String.valueOf(rs.getLong("id")))
                : Sql.query(c, "SELECT id FROM staff_notes WHERE removed = FALSE AND target_uuid = ? ORDER BY id DESC LIMIT 100",
                rs -> String.valueOf(rs.getLong("id")), Sql.toBytes(target)));
    }

    // ================================================================== P3: acknowledgement (spec 12.6)

    /** Marks every unacknowledged active warning of the player as acknowledged; returns their ids. */
    CompletableFuture<List<Long>> acknowledge(UUID target, long now) {
        return db.transaction(c -> {
            List<Long> ids = Sql.query(c, "SELECT id FROM punishments WHERE target_uuid = ? AND type = 'WARN' "
                    + "AND removed_at IS NULL AND acknowledged_at IS NULL", rs -> rs.getLong("id"), Sql.toBytes(target));
            if (!ids.isEmpty()) {
                Sql.update(c, "UPDATE punishments SET acknowledged_at = ? WHERE target_uuid = ? AND type = 'WARN' "
                        + "AND removed_at IS NULL AND acknowledged_at IS NULL", now, Sql.toBytes(target));
            }
            return ids;
        });
    }

    /**
     * Unacknowledged warnings (not removed, created after {@code since}) for the join reminder: returns them and marks the
     * one join reminder as sent ({@code ack_join_sent}), atomically, so the reminder is shown once.
     *
     * @return {@code [owing, remindNow]}: all unacknowledged warnings, and those whose join reminder is due now
     */
    record JoinAck(List<Punishment> owing, List<Punishment> remindNow) {
    }

    CompletableFuture<JoinAck> joinAck(UUID target, long since) {
        return db.transaction(c -> {
            List<Punishment> owing = Sql.query(c, "SELECT " + COLUMNS + " FROM punishments WHERE target_uuid = ? AND type = 'WARN' "
                    + "AND removed_at IS NULL AND acknowledged_at IS NULL AND created > ? ORDER BY created DESC",
                    PunishmentStore::map, Sql.toBytes(target), since);
            List<Long> due = Sql.query(c, "SELECT id FROM punishments WHERE target_uuid = ? AND type = 'WARN' AND removed_at IS NULL "
                    + "AND acknowledged_at IS NULL AND ack_join_sent = FALSE AND created > ?", rs -> rs.getLong("id"),
                    Sql.toBytes(target), since);
            if (!due.isEmpty()) {
                Sql.update(c, "UPDATE punishments SET ack_join_sent = TRUE WHERE target_uuid = ? AND type = 'WARN' "
                        + "AND removed_at IS NULL AND acknowledged_at IS NULL AND ack_join_sent = FALSE AND created > ?",
                        Sql.toBytes(target), since);
            }
            return new JoinAck(owing, owing.stream().filter(p -> due.contains(p.id())).toList());
        });
    }

    // ================================================================== P3: suggestion API (spec 12.8)

    /** Outcome of {@link #suggestApi}: the row, and whether it refreshed an existing pending one. */
    record ApiSuggestion(Suggestion suggestion, boolean refreshed) {
    }

    /**
     * Creates a pending {@code api} suggestion, or refreshes the pending one for the same target + reason (one per target
     * + reason; never a duplicate), atomically under the target's lock. Never applies anything.
     */
    CompletableFuture<ApiSuggestion> suggestApi(UUID target, String targetName, String reasonId, Templates.Step step,
                                                String reason, String details, long now, Long expires) {
        return suggestApi(target, targetName, reasonId, step, reason, details, now, expires, false);
    }

    /**
     * Same; {@code append} = on a refresh, {@code details} is appended to the waiting row's details as a new line (newest 5
     * lines kept, spec 13.3) instead of replacing them.
     */
    CompletableFuture<ApiSuggestion> suggestApi(UUID target, String targetName, String reasonId, Templates.Step step,
                                                String reason, String details, long now, Long expires, boolean append) {
        return db.async(c -> locked(c, target, conn -> suggestApiLocked(conn, target, targetName, reasonId, step, reason,
                details, now, expires, append)));
    }

    private static ApiSuggestion suggestApiLocked(Connection c, UUID target, String targetName, String reasonId,
                                                  Templates.Step step, String reason, String details, long now, Long expires,
                                                  boolean append) throws SQLException {
        Optional<Suggestion> waiting = Sql.queryOne(c, "SELECT " + SUGGESTION_COLUMNS + " FROM punishment_suggestions "
                        + "WHERE target_uuid = ? AND kind = ? AND reason_id = ? AND status = 'PENDING' AND (expires IS NULL OR expires > ?) "
                        + "ORDER BY id DESC LIMIT 1", PunishmentStore::mapSuggestion, Sql.toBytes(target), Suggestion.KIND_API,
                reasonId, now);
        if (waiting.isPresent()) {
            String merged = append ? ApiSuggestions.appendDetails(waiting.get().details(), details,
                    ApiSuggestions.MAX_DETAIL_LINES, ApiSuggestions.MAX_DETAIL_CHARS) : clip(details, 255);
            Sql.update(c, "UPDATE punishment_suggestions SET details = ?, expires = ?, action = ?, target_name = ? WHERE id = ?",
                    merged, expires, step.describe(), clip(targetName, 32), waiting.get().id());
            return new ApiSuggestion(suggestionById(c, waiting.get().id()).orElseThrow(), true);
        }
        Suggestion row = new Suggestion(0, target, targetName, Suggestion.KIND_API, null, false, step.describe(), clip(reason, 255),
                Punishment.AUTO_PREFIX + Suggestion.KIND_API + ":" + reasonId, 0, now, expires, Suggestion.Status.PENDING,
                null, null, null, null, null, null, reasonId, details);
        return new ApiSuggestion(insertSuggestion(c, row, null), false);
    }

    /**
     * P9, spec 14.5: like {@link #suggestApi}, with a cap: a waiting suggestion for this target + reason is refreshed,
     * otherwise a new one is only created when none (any status) was created at or after {@code capSince}. Empty =
     * capped. Atomic under the target's lock.
     */
    CompletableFuture<Optional<ApiSuggestion>> suggestApiCapped(UUID target, String targetName, String reasonId,
                                                                Templates.Step step, String reason, String details, long now,
                                                                Long expires, long capSince) {
        return db.async(c -> locked(c, target, conn -> {
            boolean waiting = Sql.queryOne(conn, "SELECT COUNT(*) AS n FROM punishment_suggestions WHERE target_uuid = ? "
                            + "AND kind = ? AND reason_id = ? AND status = 'PENDING' AND (expires IS NULL OR expires > ?)",
                    rs -> rs.getLong("n"), Sql.toBytes(target), Suggestion.KIND_API, reasonId, now).orElse(0L) > 0;
            if (!waiting) {
                Long last = Sql.queryOne(conn, "SELECT MAX(created) AS last_created FROM punishment_suggestions "
                                + "WHERE target_uuid = ? AND reason_id = ?", rs -> Sql.nullableLong(rs, "last_created"),
                        Sql.toBytes(target), reasonId).orElse(null);
                if (last != null && last >= capSince) {
                    return Optional.<ApiSuggestion>empty();
                }
            }
            return Optional.of(suggestApiLocked(conn, target, targetName, reasonId, step, reason, details, now, expires, false));
        }));
    }

    /** Pending (not expired) API suggestions of a player, newest first. */
    CompletableFuture<List<Suggestion>> apiSuggestions(UUID target) {
        return db.async(c -> Sql.query(c, "SELECT " + SUGGESTION_COLUMNS + " FROM punishment_suggestions WHERE target_uuid = ? "
                        + "AND kind = ? AND status = 'PENDING' AND (expires IS NULL OR expires > ?) ORDER BY created DESC, id DESC",
                PunishmentStore::mapSuggestion, Sql.toBytes(target), Suggestion.KIND_API, System.currentTimeMillis()));
    }

    private static String lastIpBlocking(Connection c, UUID target) {
        try {
            return Sql.queryOne(c, "SELECT ip FROM social_player_ips WHERE uuid = ? ORDER BY last_seen DESC LIMIT 1",
                    rs -> rs.getString("ip"), Sql.toBytes(target)).orElse(null);
        } catch (SQLException e) {
            return null; // social module not installed
        }
    }

    // ================================================================== P4: approval requests (spec 13.4)

    private static final String REQUEST_COLUMNS = "id, target_uuid, target_name, reason_key, type, duration_ms, ip_wide, ip, "
            + "reason, template, ladder_step, weight_tenths, silent, source, requester_uuid, requester_name, created, expires, "
            + "status, handled_at, handler_uuid, handler_name, deny_reason, punishment_id, merges, notified, evidence_entry, "
            + "requester_trainee";

    private static PunishRequest mapRequest(ResultSet rs) throws SQLException {
        return new PunishRequest(rs.getLong("id"), Sql.uuid(rs, "target_uuid"), rs.getString("target_name"),
                rs.getString("reason_key"), Punishment.Type.valueOf(rs.getString("type")), Sql.nullableLong(rs, "duration_ms"),
                rs.getBoolean("ip_wide"), rs.getString("ip"), rs.getString("reason"), rs.getString("template"),
                nullableInt(rs, "ladder_step"), rs.getInt("weight_tenths"), rs.getBoolean("silent"), rs.getString("source"),
                Sql.uuid(rs, "requester_uuid"), rs.getString("requester_name"), rs.getLong("created"), rs.getLong("expires"),
                PunishRequest.Status.valueOf(rs.getString("status")), Sql.nullableLong(rs, "handled_at"),
                Sql.uuid(rs, "handler_uuid"), rs.getString("handler_name"), rs.getString("deny_reason"),
                Sql.nullableLong(rs, "punishment_id"), rs.getInt("merges"), rs.getBoolean("notified"),
                Sql.nullableLong(rs, "evidence_entry"), rs.getBoolean("requester_trainee"));
    }

    /** A request after {@link #requestLocked}: the row, and whether it was merged into an existing pending one. */
    record RequestSaved(PunishRequest request, boolean merged) {
    }

    private static Optional<PunishRequest> requestById(Connection c, long id) throws SQLException {
        return Sql.queryOne(c, "SELECT " + REQUEST_COLUMNS + " FROM punishment_requests WHERE id = ?", PunishmentStore::mapRequest, id);
    }

    /**
     * Stores a request for {@code wanted} (the punishment as it would be applied; {@code duration} {@code null} = perm), or
     * merges it into the pending request for the same target and reason (spec 13.4: "a second request ... is merged").
     * Runs inside the caller's transaction and target lock.
     */
    private static RequestSaved requestLocked(Connection c, Punishment wanted, java.time.Duration duration, long now,
                                              java.time.Duration expire, Long evidenceEntry, boolean trainee) throws SQLException {
        String key = Approval.mergeKey(wanted.template(), wanted.type(), wanted.reason());
        if (wanted.target() != null) {
            Optional<PunishRequest> waiting = Sql.queryOne(c, "SELECT " + REQUEST_COLUMNS + " FROM punishment_requests "
                            + "WHERE target_uuid = ? AND reason_key = ? AND status = 'PENDING' AND expires > ? ORDER BY id DESC LIMIT 1",
                    PunishmentStore::mapRequest, Sql.toBytes(wanted.target()), key, now);
            if (waiting.isPresent()) {
                Sql.update(c, "UPDATE punishment_requests SET merges = merges + 1 WHERE id = ?", waiting.get().id());
                if (evidenceEntry != null) {
                    // spec 13.11: the first request's log line stays; a merged request only fills it in when there was none
                    Sql.update(c, "UPDATE punishment_requests SET evidence_entry = ? WHERE id = ? AND evidence_entry IS NULL",
                            evidenceEntry, waiting.get().id());
                }
                return new RequestSaved(requestById(c, waiting.get().id()).orElseThrow(), true);
            }
        }
        // timed(): bans, mutes, island bans and coop blocks keep their length (P8: trainees request island steps too)
        boolean timed = wanted.type().timed();
        long id = Sql.insertReturningId(c, "INSERT INTO punishment_requests (target_uuid, target_name, reason_key, type, duration_ms, "
                        + "ip_wide, ip, reason, template, ladder_step, weight_tenths, silent, source, requester_uuid, requester_name, "
                        + "created, expires, status, evidence_entry, requester_trainee) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'PENDING', ?, ?)",
                bytes(wanted.target()), clip(wanted.targetName(), 32), key, wanted.type().name(),
                timed && duration != null ? duration.toMillis() : null, wanted.ipWide(), wanted.ip(), clip(wanted.reason(), 255),
                wanted.template(), wanted.ladderStep(), wanted.weight(), wanted.silent(), clip(wanted.source(), 48),
                bytes(wanted.issuer()), clip(wanted.issuerName(), 32), now, now + expire.toMillis(), evidenceEntry, trainee);
        return new RequestSaved(requestById(c, id).orElseThrow(), false);
    }

    CompletableFuture<Optional<PunishRequest>> request(long id) {
        return db.async(c -> requestById(c, id));
    }

    /** Pending (not expired) requests for one player, oldest first (head of /p). */
    CompletableFuture<List<PunishRequest>> pendingRequests(UUID target, long now) {
        return db.async(c -> Sql.query(c, "SELECT " + REQUEST_COLUMNS + " FROM punishment_requests WHERE target_uuid = ? "
                        + "AND status = 'PENDING' AND expires > ? ORDER BY created, id", PunishmentStore::mapRequest,
                Sql.toBytes(target), now));
    }

    /** All pending (not expired) requests, oldest first, at most {@code limit} (approvers on join). */
    CompletableFuture<List<PunishRequest>> pendingRequests(long now, int limit) {
        return db.async(c -> Sql.query(c, "SELECT " + REQUEST_COLUMNS + " FROM punishment_requests WHERE status = 'PENDING' "
                + "AND expires > ? ORDER BY created, id LIMIT " + Math.max(1, limit), PunishmentStore::mapRequest, now));
    }

    /** What happened when an approver clicked [Approve] or [Deny]. */
    enum RequestKind { APPROVED, DENIED, SKIPPED, BLOCKED, OWN_REQUEST, ALREADY_HANDLED, EXPIRED, NOT_FOUND }

    record RequestResult(RequestKind kind, PunishRequest request, PunishDecision.Verdict verdict, Punishment punishment) {
    }

    private static RequestKind kindOf(Approval.Check check) {
        return switch (check) {
            case NOT_FOUND -> RequestKind.NOT_FOUND;
            case EXPIRED -> RequestKind.EXPIRED;
            case ALREADY_HANDLED -> RequestKind.ALREADY_HANDLED;
            case OWN_REQUEST -> RequestKind.OWN_REQUEST;
            case OK -> throw new IllegalArgumentException("ok");
        };
    }

    private static int claimRequest(Connection c, long id, PunishRequest.Status status, UUID by, String byName, Long punishmentId,
                                    String denyReason, long now) throws SQLException {
        return Sql.update(c, "UPDATE punishment_requests SET status = ?, handled_at = ?, handler_uuid = ?, handler_name = ?, "
                        + "punishment_id = ?, deny_reason = ? WHERE id = ? AND status = 'PENDING' AND expires > ?",
                status.name(), now, bytes(by), clip(byName, 32), punishmentId, clip(denyReason, 255), id, now);
    }

    /**
     * [Approve]: under the target's lock, checks the request ({@link Approval#check}: pending, not expired, not your own),
     * decides with the approver's permissions ({@code inputFor}: their rank limit stays the hard maximum) and applies it
     * with {@code issuer} = the requester and {@code approved_by} = the approver. First click wins. BLOCKED leaves it
     * pending for someone who may; an already longer mute/ban marks it SKIPPED.
     */
    CompletableFuture<RequestResult> approve(long id, java.util.function.Function<PunishRequest, PunishDecision.Input> inputFor,
                                             PunishDecision decision, UUID by, String byName, boolean selfApprove, long now) {
        return db.async(c -> {
            Optional<PunishRequest> first = requestById(c, id);
            if (first.isEmpty()) {
                return new RequestResult(RequestKind.NOT_FOUND, null, null, null);
            }
            return locked(c, first.get().target(), conn -> {
                PunishRequest r = requestById(conn, id).orElseThrow();
                Approval.Check check = Approval.check(r, by, true, selfApprove, now);
                if (check != Approval.Check.OK) {
                    return new RequestResult(kindOf(check), r, null, null);
                }
                Active act = active(conn, r.target(), r.ipWide() ? r.ip() : null, now);
                PunishDecision.Verdict verdict = decision.decide(inputFor.apply(r).withActive(act.mute(), act.ban()));
                if (verdict.outcome() == PunishDecision.Outcome.SKIPPED) {
                    claimRequest(conn, id, PunishRequest.Status.SKIPPED, by, byName, null, null, now);
                    return new RequestResult(RequestKind.SKIPPED, requestById(conn, id).orElseThrow(), verdict, null);
                }
                if (!verdict.applied()) {
                    return new RequestResult(RequestKind.BLOCKED, r, verdict, null);
                }
                Punishment p = insertRow(conn, shaped(new Punishment(0, r.type(), r.target(), r.targetName(), r.ip(), r.ipWide(),
                        r.reason(), r.template(), r.ladderStep(), r.requester(), r.requesterName(), now, null, r.silent(), null,
                        null, null, null, r.source(), r.weight(), null, by, byName), verdict.input()));
                if (claimRequest(conn, id, PunishRequest.Status.APPROVED, by, byName, p.id(), null, now) != 1) {
                    throw new SQLException("request " + id + " changed while locked");
                }
                return new RequestResult(RequestKind.APPROVED, requestById(conn, id).orElseThrow(), verdict, p);
            });
        });
    }

    /** [Deny] with a reason (first click wins). Denying your own request withdraws it. */
    CompletableFuture<RequestResult> deny(long id, UUID by, String byName, String reason, long now) {
        return db.async(c -> {
            Optional<PunishRequest> first = requestById(c, id);
            if (first.isEmpty()) {
                return new RequestResult(RequestKind.NOT_FOUND, null, null, null);
            }
            return locked(c, first.get().target(), conn -> {
                PunishRequest r = requestById(conn, id).orElseThrow();
                Approval.Check check = Approval.check(r, by, false, true, now);
                if (check != Approval.Check.OK) {
                    return new RequestResult(kindOf(check), r, null, null);
                }
                if (claimRequest(conn, id, PunishRequest.Status.DENIED, by, byName, null, reason, now) != 1) {
                    throw new SQLException("request " + id + " changed while locked");
                }
                return new RequestResult(RequestKind.DENIED, requestById(conn, id).orElseThrow(), null, null);
            });
        });
    }

    /**
     * The requester's outcomes not told yet (approved, denied, skipped, or expired without an answer); marks them told,
     * atomically, so each is told once (now if online, else on the next join).
     */
    CompletableFuture<List<PunishRequest>> requesterUpdates(UUID requester, long now) {
        return db.transaction(c -> {
            List<PunishRequest> due = Sql.query(c, "SELECT " + REQUEST_COLUMNS + " FROM punishment_requests WHERE requester_uuid = ? "
                            + "AND notified = FALSE AND (status <> 'PENDING' OR expires <= ?) ORDER BY id",
                    PunishmentStore::mapRequest, Sql.toBytes(requester), now);
            List<PunishRequest> out = new ArrayList<>();
            for (PunishRequest r : due) {
                if (Sql.update(c, "UPDATE punishment_requests SET notified = TRUE WHERE id = ? AND notified = FALSE", r.id()) == 1) {
                    out.add(r);
                }
            }
            return out;
        });
    }

    /** Marks one request's outcome as told (the requester was online when it was handled). */
    CompletableFuture<Boolean> markNotified(long id) {
        return db.async(c -> Sql.update(c, "UPDATE punishment_requests SET notified = TRUE WHERE id = ? AND notified = FALSE", id) == 1);
    }

    /**
     * Request numbers of one staff member since {@code since} (/pstats, spec 13.4): as requester (made, approved, denied,
     * waiting, expired) and as approver (approved, denied).
     */
    record RequestCounts(long made, long approved, long denied, long waiting, long expired, long handledApproved,
                         long handledDenied) {
        static final RequestCounts NONE = new RequestCounts(0, 0, 0, 0, 0, 0, 0);
    }

    CompletableFuture<RequestCounts> requestCounts(UUID staff, long since, long now) {
        return db.async(c -> {
            long[] own = Sql.queryOne(c, "SELECT COUNT(*) AS n, "
                            + "SUM(CASE WHEN status IN ('APPROVED', 'SKIPPED') THEN 1 ELSE 0 END) AS ok_n, "
                            + "SUM(CASE WHEN status = 'DENIED' THEN 1 ELSE 0 END) AS denied_n, "
                            + "SUM(CASE WHEN status = 'PENDING' AND expires > ? THEN 1 ELSE 0 END) AS waiting_n, "
                            + "SUM(CASE WHEN status = 'PENDING' AND expires <= ? THEN 1 ELSE 0 END) AS expired_n "
                            + "FROM punishment_requests WHERE requester_uuid = ? AND created >= ?",
                    rs -> new long[]{rs.getLong("n"), rs.getLong("ok_n"), rs.getLong("denied_n"), rs.getLong("waiting_n"), rs.getLong("expired_n")},
                    now, now, Sql.toBytes(staff), since).orElse(new long[5]);
            long[] handled = Sql.queryOne(c, "SELECT SUM(CASE WHEN status IN ('APPROVED', 'SKIPPED') THEN 1 ELSE 0 END) AS ok_n, "
                            + "SUM(CASE WHEN status = 'DENIED' THEN 1 ELSE 0 END) AS denied_n FROM punishment_requests "
                            + "WHERE handler_uuid = ? AND handled_at >= ?",
                    rs -> new long[]{rs.getLong("ok_n"), rs.getLong("denied_n")}, Sql.toBytes(staff), since).orElse(new long[2]);
            return new RequestCounts(own[0], own[1], own[2], own[3], own[4], handled[0], handled[1]);
        });
    }

    // ================================================================== P4: watchlist (spec 13.5)

    private static final String WATCH_COLUMNS = "id, target_uuid, target_name, author_uuid, author_name, reason, created, expires, "
            + "removed, removed_at, removed_by_name";

    private static Watchlist.Entry mapWatch(ResultSet rs) throws SQLException {
        return new Watchlist.Entry(rs.getLong("id"), Sql.uuid(rs, "target_uuid"), rs.getString("target_name"),
                Sql.uuid(rs, "author_uuid"), rs.getString("author_name"), rs.getString("reason"), rs.getLong("created"),
                Sql.nullableLong(rs, "expires"), rs.getBoolean("removed"), Sql.nullableLong(rs, "removed_at"),
                rs.getString("removed_by_name"));
    }

    private static Optional<Watchlist.Entry> activeWatch(Connection c, UUID target, long now) throws SQLException {
        return Sql.queryOne(c, "SELECT " + WATCH_COLUMNS + " FROM watchlist WHERE target_uuid = ? AND removed = FALSE "
                + "AND (expires IS NULL OR expires > ?) ORDER BY id DESC LIMIT 1", PunishmentStore::mapWatch, Sql.toBytes(target), now);
    }

    /** {@link #watch} result: the entry now, and the active entry it replaced ({@code null} = newly added). */
    record WatchSaved(Watchlist.Entry entry, Watchlist.Entry before) {
    }

    /**
     * Adds a player to the watchlist, or renews the active entry (new reason, author, date and expiry), under the target's
     * lock so there is never more than one active entry per player.
     */
    CompletableFuture<WatchSaved> watch(UUID target, String targetName, UUID author, String authorName, String reason, long now,
                                        Long expires) {
        return db.async(c -> locked(c, target, conn -> {
            Optional<Watchlist.Entry> before = activeWatch(conn, target, now);
            long id;
            if (before.isPresent()) {
                id = before.get().id();
                Sql.update(conn, "UPDATE watchlist SET target_name = ?, author_uuid = ?, author_name = ?, reason = ?, created = ?, "
                                + "expires = ? WHERE id = ?", clip(targetName, 32), bytes(author), clip(authorName, 32),
                        clip(reason, 255), now, expires, id);
            } else {
                id = Sql.insertReturningId(conn, "INSERT INTO watchlist (target_uuid, target_name, author_uuid, author_name, reason, "
                                + "created, expires) VALUES (?, ?, ?, ?, ?, ?, ?)", Sql.toBytes(target), clip(targetName, 32),
                        bytes(author), clip(authorName, 32), clip(reason, 255), now, expires);
            }
            Watchlist.Entry saved = Sql.queryOne(conn, "SELECT " + WATCH_COLUMNS + " FROM watchlist WHERE id = ?",
                    PunishmentStore::mapWatch, id).orElseThrow();
            return new WatchSaved(saved, before.orElse(null));
        }));
    }

    /** Removes the player's active entry (the row stays, marked removed); empty if they were not watched. */
    CompletableFuture<Optional<Watchlist.Entry>> unwatch(UUID target, UUID by, String byName, long now) {
        return db.async(c -> locked(c, target, conn -> {
            Optional<Watchlist.Entry> before = activeWatch(conn, target, now);
            if (before.isEmpty()) {
                return Optional.<Watchlist.Entry>empty();
            }
            Sql.update(conn, "UPDATE watchlist SET removed = TRUE, removed_at = ?, removed_by_uuid = ?, removed_by_name = ? "
                    + "WHERE id = ? AND removed = FALSE", now, bytes(by), clip(byName, 32), before.get().id());
            return before;
        }));
    }

    CompletableFuture<Optional<Watchlist.Entry>> activeWatch(UUID target, long now) {
        return db.async(c -> activeWatch(c, target, now));
    }

    /** Every active entry, newest first, at most 200 (/watchlist, tab completion of /watch remove). */
    CompletableFuture<List<Watchlist.Entry>> watchlist(long now) {
        return db.async(c -> Sql.query(c, "SELECT " + WATCH_COLUMNS + " FROM watchlist WHERE removed = FALSE "
                + "AND (expires IS NULL OR expires > ?) ORDER BY created DESC, id DESC LIMIT 200", PunishmentStore::mapWatch, now));
    }

    // ================================================================== P4: "mute ended" / "welcome back" (spec 13.13)

    /**
     * Claims the end notices of the target's mutes ({@code mutes}) and/or bans ({@code bans}) that ended naturally (not
     * pardoned or undone) and were not told yet: sets {@code end_notice_sent} and returns those rows, atomically under the
     * target's lock, so the line is sent exactly once (online at the end, or on the next join).
     */
    CompletableFuture<List<Punishment>> claimEndNotices(UUID target, long now, boolean mutes, boolean bans) {
        if (!mutes && !bans) {
            return CompletableFuture.completedFuture(List.of());
        }
        String types = mutes && bans ? "('MUTE', 'BAN')" : mutes ? "('MUTE')" : "('BAN')";
        return db.async(c -> locked(c, target, conn -> {
            List<Punishment> ended = Sql.query(conn, "SELECT " + COLUMNS + " FROM punishments WHERE target_uuid = ? AND type IN "
                    + types + " AND removed_at IS NULL AND expires IS NOT NULL AND expires <= ? AND end_notice_sent = FALSE "
                    + "ORDER BY expires", PunishmentStore::map, Sql.toBytes(target), now);
            List<Punishment> claimed = new ArrayList<>();
            for (Punishment p : ended) {
                if (Sql.update(conn, "UPDATE punishments SET end_notice_sent = TRUE WHERE id = ? AND end_notice_sent = FALSE",
                        p.id()) == 1) {
                    claimed.add(p);
                }
            }
            return claimed;
        }));
    }

    // ================================================================== P5: appeals (spec 13.10)

    private static final String APPEAL_COLUMNS = "id, punishment_id, punishment_type, player_uuid, player_name, issuer_uuid, "
            + "issuer_name, appeal_text, status, handler_uuid, handler_name, response, old_expires, new_expires, created, handled, "
            + "player_notified";

    private static Appeal mapAppeal(ResultSet rs) throws SQLException {
        return new Appeal(rs.getLong("id"), rs.getLong("punishment_id"), Punishment.Type.valueOf(rs.getString("punishment_type")),
                Sql.uuid(rs, "player_uuid"), rs.getString("player_name"), Sql.uuid(rs, "issuer_uuid"), rs.getString("issuer_name"),
                rs.getString("appeal_text"), Appeal.Status.valueOf(rs.getString("status")), Sql.uuid(rs, "handler_uuid"),
                rs.getString("handler_name"), rs.getString("response"), Sql.nullableLong(rs, "old_expires"),
                Sql.nullableLong(rs, "new_expires"), rs.getLong("created"), Sql.nullableLong(rs, "handled"),
                rs.getBoolean("player_notified"));
    }

    private static Optional<Appeal> appealById(Connection c, long id) throws SQLException {
        return Sql.queryOne(c, "SELECT " + APPEAL_COLUMNS + " FROM appeals WHERE id = ?", PunishmentStore::mapAppeal, id);
    }

    private static List<Appeal> appealsOf(Connection c, long punishmentId) throws SQLException {
        return Sql.query(c, "SELECT " + APPEAL_COLUMNS + " FROM appeals WHERE punishment_id = ? ORDER BY id",
                PunishmentStore::mapAppeal, punishmentId);
    }

    /** {@link #submitAppeal} result: the new appeal, or why it was refused (and the punishment, when found). */
    record Submitted(Appeal appeal, AppealRules.Problem problem, Punishment punishment) {
    }

    /**
     * {@code /appeal}: checks {@link AppealRules#canAppeal} (own, active, appealable type and reason, no open appeal, no
     * cooldown) with the punishment's earlier appeals and inserts an OPEN appeal, under the player's lock so two quick
     * commands can't create two open appeals for one punishment.
     */
    CompletableFuture<Submitted> submitAppeal(long punishmentId, UUID player, String playerName, String text,
                                              java.util.function.Predicate<String> reasonAppealable, PunishConfig.Appeals cfg,
                                              long now) {
        return db.async(c -> locked(c, player, conn -> {
            Punishment p = byId(conn, punishmentId).orElse(null);
            List<Appeal> previous = p == null ? List.of() : appealsOf(conn, punishmentId);
            Optional<AppealRules.Problem> problem = AppealRules.canAppeal(p, player, previous,
                    p != null && reasonAppealable.test(p.template()), cfg, now);
            if (problem.isPresent()) {
                return new Submitted(null, problem.get(), p);
            }
            long id = Sql.insertReturningId(conn, "INSERT INTO appeals (punishment_id, punishment_type, player_uuid, player_name, "
                            + "issuer_uuid, issuer_name, appeal_text, status, created) VALUES (?, ?, ?, ?, ?, ?, ?, 'OPEN', ?)",
                    p.id(), p.type().name(), Sql.toBytes(player), clip(playerName, 32), bytes(p.issuer()), clip(p.issuerName(), 32),
                    clip(text, 600), now);
            return new Submitted(appealById(conn, id).orElseThrow(), null, p);
        }));
    }

    CompletableFuture<Optional<Appeal>> appeal(long id) {
        return db.async(c -> appealById(c, id));
    }

    /** Every appeal of one punishment, oldest first. */
    CompletableFuture<List<Appeal>> appealsFor(long punishmentId) {
        return db.async(c -> appealsOf(c, punishmentId));
    }

    /** Open appeals, oldest first (the /appeals queue), at most {@code limit}. */
    CompletableFuture<List<Appeal>> openAppeals(int limit) {
        return db.async(c -> Sql.query(c, "SELECT " + APPEAL_COLUMNS + " FROM appeals WHERE status = 'OPEN' ORDER BY created, id "
                + "LIMIT " + Math.max(1, limit), PunishmentStore::mapAppeal));
    }

    /** Open appeals created before {@code cutoff} (reminder on staff join), oldest first. */
    CompletableFuture<List<Appeal>> openAppealsBefore(long cutoff, int limit) {
        return db.async(c -> Sql.query(c, "SELECT " + APPEAL_COLUMNS + " FROM appeals WHERE status = 'OPEN' AND created < ? "
                + "ORDER BY created, id LIMIT " + Math.max(1, limit), PunishmentStore::mapAppeal, cutoff));
    }

    /** A player's appeals, newest first (the /appeal status), at most {@code limit}. */
    CompletableFuture<List<Appeal>> appealsBy(UUID player, int limit) {
        return db.async(c -> Sql.query(c, "SELECT " + APPEAL_COLUMNS + " FROM appeals WHERE player_uuid = ? ORDER BY created DESC, "
                + "id DESC LIMIT " + Math.max(1, limit), PunishmentStore::mapAppeal, Sql.toBytes(player)));
    }

    /** What happened when staff handled an appeal. */
    enum AppealKind { ACCEPTED, REDUCED, DENIED, CLOSED, ALREADY_HANDLED, NOT_FOUND, NOT_SHORTER }

    record AppealResult(AppealKind kind, Appeal appeal, Punishment punishment) {
    }

    private static int claimAppeal(Connection c, long id, Appeal.Status status, UUID by, String byName, String response,
                                   Long oldExpires, Long newExpires, long now) throws SQLException {
        return Sql.update(c, "UPDATE appeals SET status = ?, handler_uuid = ?, handler_name = ?, response = ?, old_expires = ?, "
                        + "new_expires = ?, handled = ? WHERE id = ? AND status = 'OPEN'", status.name(), bytes(by), clip(byName, 32),
                clip(response, 255), oldExpires, newExpires, now, id);
    }

    /** The shared part of accept/reduce/deny: finds the appeal, locks its player, checks it is still open. */
    private CompletableFuture<AppealResult> handleAppeal(long id, AppealWork work) {
        return db.async(c -> {
            Optional<Appeal> first = appealById(c, id);
            if (first.isEmpty()) {
                return new AppealResult(AppealKind.NOT_FOUND, null, null);
            }
            return locked(c, first.get().player(), conn -> {
                Appeal a = appealById(conn, id).orElseThrow();
                Punishment p = byId(conn, a.punishmentId()).orElse(null);
                if (!a.open()) {
                    return new AppealResult(AppealKind.ALREADY_HANDLED, a, p);
                }
                return work.apply(conn, a, p);
            });
        });
    }

    @FunctionalInterface
    private interface AppealWork {
        AppealResult apply(Connection c, Appeal appeal, Punishment punishment) throws SQLException;
    }

    /** The punishment ended or was lifted before anyone handled the appeal: close it (no decision). */
    private static AppealResult closeAppeal(Connection c, Appeal a, Punishment p, UUID by, String byName, long now)
            throws SQLException {
        if (claimAppeal(c, a.id(), Appeal.Status.CLOSED, by, byName, null, null, null, now) != 1) {
            throw new SQLException("appeal " + a.id() + " changed while locked");
        }
        return new AppealResult(AppealKind.CLOSED, appealById(c, a.id()).orElseThrow(), p);
    }

    /**
     * Accept: lifts the punishment (a pardon with {@code pardonReason}, "Appeal accepted") and marks the appeal ACCEPTED, in
     * one transaction under the player's lock. First click wins; a punishment that is no longer active closes the appeal.
     */
    CompletableFuture<AppealResult> acceptAppeal(long id, UUID by, String byName, String pardonReason, long now) {
        return handleAppeal(id, (conn, a, p) -> {
            if (p == null || !p.active(now)) {
                return closeAppeal(conn, a, p, by, byName, now);
            }
            Sql.update(conn, "UPDATE punishments SET removed_at = ?, removed_by_uuid = ?, removed_by_name = ?, removed_reason = ? "
                    + "WHERE id = ? AND removed_at IS NULL", now, bytes(by), clip(byName, 32), clip(pardonReason, 255), p.id());
            if (claimAppeal(conn, a.id(), Appeal.Status.ACCEPTED, by, byName, pardonReason, p.expires(), null, now) != 1) {
                throw new SQLException("appeal " + a.id() + " changed while locked");
            }
            return new AppealResult(AppealKind.ACCEPTED, appealById(conn, a.id()).orElseThrow(), byId(conn, p.id()).orElseThrow());
        });
    }

    /**
     * Reduce: the punishment now ends at {@code created + amount unit} (never later than before; an end already passed
     * means now), and the appeal is REDUCED with the old and new end. Same lock and first-click rules as accept.
     */
    CompletableFuture<AppealResult> reduceAppeal(long id, UUID by, String byName, AppealRules.Unit unit, long amount,
                                                 String response, long now) {
        return handleAppeal(id, (conn, a, p) -> {
            if (p == null || !p.active(now)) {
                return closeAppeal(conn, a, p, by, byName, now);
            }
            Long end = AppealRules.reducedEnd(p, unit, amount, now);
            if (end == null) {
                return new AppealResult(AppealKind.NOT_SHORTER, a, p);
            }
            Sql.update(conn, "UPDATE punishments SET expires = ? WHERE id = ? AND removed_at IS NULL", end, p.id());
            if (claimAppeal(conn, a.id(), Appeal.Status.REDUCED, by, byName, response, p.expires(), end, now) != 1) {
                throw new SQLException("appeal " + a.id() + " changed while locked");
            }
            return new AppealResult(AppealKind.REDUCED, appealById(conn, a.id()).orElseThrow(), byId(conn, p.id()).orElseThrow());
        });
    }

    /** Deny with a reason (required, checked by the caller). First click wins. */
    CompletableFuture<AppealResult> denyAppeal(long id, UUID by, String byName, String reason, long now) {
        return handleAppeal(id, (conn, a, p) -> {
            if (claimAppeal(conn, a.id(), Appeal.Status.DENIED, by, byName, reason, null, null, now) != 1) {
                throw new SQLException("appeal " + a.id() + " changed while locked");
            }
            return new AppealResult(AppealKind.DENIED, appealById(conn, a.id()).orElseThrow(), p);
        });
    }

    /** Decisions the player was not told yet; marks them told, atomically (each is told once: now, or on the next join). */
    CompletableFuture<List<Appeal>> appealUpdates(UUID player) {
        return db.transaction(c -> {
            List<Appeal> due = Sql.query(c, "SELECT " + APPEAL_COLUMNS + " FROM appeals WHERE player_uuid = ? AND status <> 'OPEN' "
                    + "AND player_notified = FALSE ORDER BY handled, id", PunishmentStore::mapAppeal, Sql.toBytes(player));
            List<Appeal> claimed = new ArrayList<>();
            for (Appeal a : due) {
                if (Sql.update(c, "UPDATE appeals SET player_notified = TRUE WHERE id = ? AND player_notified = FALSE", a.id()) == 1) {
                    claimed.add(a);
                }
            }
            return claimed;
        });
    }

    /** Marks one decision told; true if this call did it (the player is online and gets it now). */
    CompletableFuture<Boolean> markAppealNotified(long id) {
        return db.async(c -> Sql.update(c, "UPDATE appeals SET player_notified = TRUE WHERE id = ? AND player_notified = FALSE "
                + "AND status <> 'OPEN'", id) == 1);
    }

    /**
     * /pstats (spec 13.10): appeals {@code staff} handled since {@code since} (accepted / reduced / denied) and how many of
     * their own punishments were reversed by an accepted appeal.
     */
    record AppealCounts(long accepted, long reduced, long denied, long reversedByAppeal) {
        static final AppealCounts NONE = new AppealCounts(0, 0, 0, 0);

        long handled() {
            return accepted + reduced + denied;
        }
    }

    CompletableFuture<AppealCounts> appealCounts(UUID staff, long since) {
        return db.async(c -> {
            long[] n = new long[3];
            Sql.query(c, "SELECT status, COUNT(*) AS n FROM appeals WHERE handler_uuid = ? AND handled >= ? GROUP BY status", rs -> {
                switch (rs.getString("status")) {
                    case "ACCEPTED" -> n[0] = rs.getLong("n");
                    case "REDUCED" -> n[1] = rs.getLong("n");
                    case "DENIED" -> n[2] = rs.getLong("n");
                    default -> {
                        // CLOSED is no decision
                    }
                }
                return 0;
            }, Sql.toBytes(staff), since);
            long reversed = Sql.queryOne(c, "SELECT COUNT(*) AS n FROM appeals WHERE issuer_uuid = ? AND status = 'ACCEPTED' "
                    + "AND handled >= ?", rs -> rs.getLong("n"), Sql.toBytes(staff), since).orElse(0L);
            return new AppealCounts(n[0], n[1], n[2], reversed);
        });
    }

    // ================================================================== P9: rollback (spec 14.1)

    /**
     * Punishments a rollback of {@code staff} for {@code [since, until]} touches (index issuer_uuid, created), filtered by
     * {@link RollbackRules#selects}: issued by that staff member, not pardoned, never kicks / island kicks / coop removals.
     */
    CompletableFuture<List<Punishment>> rollbackCandidates(UUID staff, long since, long until) {
        return db.async(c -> RollbackRules.select(Sql.query(c, "SELECT " + COLUMNS + " FROM punishments WHERE issuer_uuid = ? "
                        + "AND created >= ? AND created <= ? AND removed_at IS NULL ORDER BY created ASC, id ASC",
                PunishmentStore::map, Sql.toBytes(staff), since, until), staff, since, until));
    }

    /** Outcome of {@link #rollback}: the group id, the rows reversed now (as they were before) and removed suggestions. */
    record RollbackResult(long rollbackId, List<Punishment> reversed, List<Long> removedSuggestions) {
    }

    /**
     * Reverses exactly the punishments {@code ids} that still match the rollback (issued by {@code staff} inside
     * {@code [since, until]}, not pardoned meanwhile): one group row ({@code punish_rollbacks}), then per target one
     * transaction under the target's lock. Every UPDATE repeats the issuer and "not removed" conditions, so unrelated
     * rows are never touched and a second confirm changes nothing. A reversed warn also removes the pending suggestion it
     * fired (spec: "pending suggestions that came from rolled back warns are removed").
     */
    CompletableFuture<RollbackResult> rollback(List<Long> ids, UUID staff, String staffName, long since, long until, UUID by,
                                               String byName, String reason, long now) {
        return db.async(c -> {
            long group = Sql.insertReturningId(c, "INSERT INTO punish_rollbacks (staff_uuid, staff_name, since_ms, until_ms, "
                            + "run_by_uuid, run_by_name, created, punishment_count, sanction_count) VALUES (?, ?, ?, ?, ?, ?, ?, 0, 0)",
                    Sql.toBytes(staff), clip(staffName, 32), since, until, bytes(by), clip(byName, 32), now);
            java.util.Map<UUID, List<Long>> byTarget = new java.util.LinkedHashMap<>();
            List<Long> noTarget = new ArrayList<>();
            for (Long id : ids) {
                Punishment p = byId(c, id).orElse(null);
                if (p == null) {
                    continue;
                }
                if (p.target() == null) {
                    noTarget.add(id);
                } else {
                    byTarget.computeIfAbsent(p.target(), k -> new ArrayList<>()).add(id);
                }
            }
            List<Punishment> reversed = new ArrayList<>();
            List<Long> removed = new ArrayList<>();
            for (java.util.Map.Entry<UUID, List<Long>> e : byTarget.entrySet()) {
                locked(c, e.getKey(), conn -> {
                    rollbackRows(conn, e.getValue(), staff, since, until, by, byName, reason, now, group, reversed, removed);
                    return null;
                });
            }
            if (!noTarget.isEmpty()) {
                locked(c, null, conn -> {
                    rollbackRows(conn, noTarget, staff, since, until, by, byName, reason, now, group, reversed, removed);
                    return null;
                });
            }
            Sql.update(c, "UPDATE punish_rollbacks SET punishment_count = ? WHERE id = ?", reversed.size(), group);
            return new RollbackResult(group, List.copyOf(reversed), List.copyOf(removed));
        });
    }

    private static void rollbackRows(Connection c, List<Long> ids, UUID staff, long since, long until, UUID by, String byName,
                                     String reason, long now, long group, List<Punishment> reversed, List<Long> removed)
            throws SQLException {
        for (Long id : ids) {
            Punishment p = byId(c, id).orElse(null);
            if (!RollbackRules.selects(p, staff, since, until)) {
                continue;
            }
            int n = Sql.update(c, "UPDATE punishments SET removed_at = ?, removed_by_uuid = ?, removed_by_name = ?, "
                            + "removed_reason = ?, rollback_id = ? WHERE id = ? AND removed_at IS NULL AND issuer_uuid = ?",
                    now, bytes(by), clip(byName, 32), clip(reason, 255), group, id, Sql.toBytes(staff));
            if (n != 1) {
                continue;
            }
            reversed.add(p);
            if (p.type() == Punishment.Type.WARN) {
                List<Long> sids = Sql.query(c, "SELECT id FROM punishment_suggestions WHERE cause_id = ? AND status = 'PENDING'",
                        rs -> rs.getLong("id"), id);
                if (!sids.isEmpty()) {
                    Sql.update(c, "UPDATE punishment_suggestions SET status = 'REMOVED', handled_at = ?, handled_by_uuid = ?, "
                                    + "handled_by_name = ?, note = ? WHERE cause_id = ? AND status = 'PENDING'",
                            now, bytes(by), clip(byName, 32), Suggestion.NOTE_ROLLBACK, id);
                    removed.addAll(sids);
                }
            }
        }
    }

    /** Stores how many sanctions a rollback gave back / lifted. */
    CompletableFuture<Void> rollbackSanctions(long group, int count) {
        return db.run(c -> Sql.update(c, "UPDATE punish_rollbacks SET sanction_count = ? WHERE id = ?", count, group));
    }

    // ================================================================== P9: data deletion (spec 14.10)

    /** What a purge would remove and keep (also the result of a purge). */
    record PurgePlan(UUID target, PurgeRules.Ledger ledger, int evidence, int notes, int appeals, int watch, int suggestions,
                     int requests, int dryRuns, int sanctions, int ips, boolean ipTable) {
    }

    private static int count(Connection c, String sql, Object... params) throws SQLException {
        return Sql.queryOne(c, sql, rs -> rs.getInt("n"), params).orElse(0);
    }

    /** IP rows of the social module's join history (0 and {@code false} when that table does not exist). */
    private static int[] ipRows(Connection c, UUID target) {
        try {
            return new int[]{count(c, "SELECT COUNT(*) AS n FROM social_player_ips WHERE uuid = ?", Sql.toBytes(target)), 1};
        } catch (SQLException e) {
            return new int[]{0, 0};
        }
    }

    private PurgePlan purgePlan(Connection c, UUID target, PurgeRules.Settings settings, long now) throws SQLException {
        byte[] id = Sql.toBytes(target);
        List<Punishment> history = Sql.query(c, "SELECT " + COLUMNS + " FROM punishments WHERE target_uuid = ?",
                PunishmentStore::map, id);
        int[] ips = ipRows(c, target);
        return new PurgePlan(target, PurgeRules.ledger(history, settings, now),
                count(c, "SELECT COUNT(*) AS n FROM punishment_evidence WHERE punishment_id IN "
                        + "(SELECT id FROM punishments WHERE target_uuid = ?)", id),
                count(c, "SELECT COUNT(*) AS n FROM staff_notes WHERE target_uuid = ?", id),
                count(c, "SELECT COUNT(*) AS n FROM appeals WHERE player_uuid = ?", id),
                count(c, "SELECT COUNT(*) AS n FROM watchlist WHERE target_uuid = ?", id),
                count(c, "SELECT COUNT(*) AS n FROM punishment_suggestions WHERE target_uuid = ?", id),
                count(c, "SELECT COUNT(*) AS n FROM punishment_requests WHERE target_uuid = ?", id),
                count(c, "SELECT COUNT(*) AS n FROM punish_dry_runs WHERE target_uuid = ?", id),
                count(c, "SELECT COUNT(*) AS n FROM sanctions WHERE target_uuid = ?", id), ips[0], ips[1] == 1);
    }

    /** The preview of {@code /ppurge <player> preview} (nothing changes). */
    CompletableFuture<PurgePlan> purgePreview(UUID target, PurgeRules.Settings settings, long now) {
        return db.async(c -> purgePlan(c, target, settings, now));
    }

    /**
     * {@code /ppurge <player> confirm}: removes the player's personal data and shrinks the punishment rows to the minimal
     * ledger ({@link PurgeRules}), in one transaction under the player's lock. Returns what was done (counted inside the
     * same transaction, so the numbers are exactly what changed). The IP join history of the social module is removed
     * afterwards (its table is optional). The audit log is never touched.
     */
    CompletableFuture<PurgePlan> purge(UUID target, PurgeRules.Settings settings, long now) {
        return db.async(c -> {
            PurgePlan plan = locked(c, target, conn -> {
                PurgePlan before = purgePlan(conn, target, settings, now);
                byte[] id = Sql.toBytes(target);
                String anon = PurgeRules.anonymousName(target);
                List<Punishment> history = Sql.query(conn, "SELECT " + COLUMNS + " FROM punishments WHERE target_uuid = ?",
                        PunishmentStore::map, id);
                Sql.update(conn, "DELETE FROM punishment_evidence WHERE punishment_id IN "
                        + "(SELECT id FROM punishments WHERE target_uuid = ?)", id);
                Sql.update(conn, "DELETE FROM staff_notes WHERE target_uuid = ?", id);
                Sql.update(conn, "DELETE FROM watchlist WHERE target_uuid = ?", id);
                Sql.update(conn, "DELETE FROM punishment_suggestions WHERE target_uuid = ?", id);
                Sql.update(conn, "DELETE FROM punishment_requests WHERE target_uuid = ? AND status <> 'PENDING'", id);
                Sql.update(conn, "UPDATE punishment_requests SET target_name = ?, ip = NULL WHERE target_uuid = ?", anon, id);
                Sql.update(conn, "UPDATE punish_dry_runs SET target_name = ? WHERE target_uuid = ?", anon, id);
                Sql.update(conn, "UPDATE sanctions SET target_name = ? WHERE target_uuid = ?", anon, id);
                for (Punishment p : history) {
                    switch (PurgeRules.fate(p, settings, now)) {
                        case DELETE -> {
                            Sql.update(conn, "DELETE FROM appeals WHERE punishment_id = ?", p.id());
                            Sql.update(conn, "DELETE FROM punishments WHERE id = ?", p.id());
                        }
                        case KEEP_ACTIVE, KEEP_LEDGER -> Sql.update(conn, "UPDATE punishments SET target_name = ?, reason = ?, "
                                        + "removed_reason = NULL, ip = ?, ip_wide = ?, purged_at = ? WHERE id = ?", anon,
                                clip(PurgeRules.ledgerReason(p), 255), PurgeRules.keepsIp(p, now) ? p.ip() : null,
                                PurgeRules.keepsIp(p, now), now, p.id());
                    }
                }
                // the appeals of kept punishments stay as a record of the decision, without the player's texts
                Sql.update(conn, "UPDATE appeals SET appeal_text = '', response = NULL, player_name = ? WHERE player_uuid = ?",
                        anon, id);
                return before;
            });
            try {
                Sql.update(c, "DELETE FROM social_player_ips WHERE uuid = ?", Sql.toBytes(target));
            } catch (SQLException e) {
                // social module not installed: no IP history to remove
            }
            return plan;
        });
    }

    /**
     * Retention of purged ledgers (spec 14.10, run with the evidence purge): deletes purged rows that ended more than
     * {@code keep-ledger} ago and are not in force, and drops the IP of purged rows no longer in force. Returns the
     * number of deleted rows.
     */
    CompletableFuture<Integer> purgeLedgers(PurgeRules.Settings settings, long now) {
        long cutoff = now - (settings.keepLedger() == null ? 0 : settings.keepLedger().toMillis());
        String inForce = "(type IN ('BAN', 'MUTE', 'ISLAND_BAN', 'COOP_BLOCK', 'REPORT_BLOCK') AND removed_at IS NULL "
                + "AND (expires IS NULL OR expires > ?))";
        return db.transaction(c -> {
            Sql.update(c, "DELETE FROM appeals WHERE punishment_id IN (SELECT id FROM punishments WHERE purged_at IS NOT NULL "
                    + "AND COALESCE(removed_at, expires, created) < ? AND NOT " + inForce + ")", cutoff, now);
            int n = Sql.update(c, "DELETE FROM punishments WHERE purged_at IS NOT NULL AND COALESCE(removed_at, expires, created) < ? "
                    + "AND NOT " + inForce, cutoff, now);
            Sql.update(c, "UPDATE punishments SET ip = NULL, ip_wide = FALSE WHERE purged_at IS NOT NULL AND ip IS NOT NULL "
                    + "AND NOT " + inForce, now);
            return n;
        });
    }
}
