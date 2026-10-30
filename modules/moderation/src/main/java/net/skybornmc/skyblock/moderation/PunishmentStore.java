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
            + "issuer_uuid, issuer_name, created, expires, silent, removed_at, removed_by_uuid, removed_by_name, removed_reason, source";
    private static final String SUGGESTION_COLUMNS = "id, target_uuid, target_name, kind, rule_at, repeat_trigger, action, reason, "
            + "source, warn_count, created, expires, status, handled_at, handled_by_uuid, handled_by_name, punishment_id, note";

    private final Database db;

    PunishmentStore(Database db) {
        this.db = db;
    }

    private static Punishment map(ResultSet rs) throws SQLException {
        int step = rs.getInt("ladder_step");
        Integer ladderStep = rs.wasNull() ? null : step;
        return new Punishment(rs.getLong("id"), Punishment.Type.valueOf(rs.getString("type")), Sql.uuid(rs, "target_uuid"),
                rs.getString("target_name"), rs.getString("ip"), rs.getBoolean("ip_wide"), rs.getString("reason"),
                rs.getString("template"), ladderStep, Sql.uuid(rs, "issuer_uuid"), rs.getString("issuer_name"),
                rs.getLong("created"), Sql.nullableLong(rs, "expires"), rs.getBoolean("silent"),
                Sql.nullableLong(rs, "removed_at"), Sql.uuid(rs, "removed_by_uuid"), rs.getString("removed_by_name"),
                rs.getString("removed_reason"), rs.getString("source"));
    }

    private static byte[] bytes(UUID uuid) {
        return uuid == null ? null : Sql.toBytes(uuid);
    }

    /** Inserts a row (inside the caller's transaction) and returns it with its id. */
    private static Punishment insertRow(Connection c, Punishment p) throws SQLException {
        long id = Sql.insertReturningId(c, "INSERT INTO punishments (type, target_uuid, target_name, ip, ip_wide, reason, "
                        + "template, ladder_step, issuer_uuid, issuer_name, created, expires, silent, source) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                p.type().name(), bytes(p.target()), p.targetName(), p.ip(), p.ipWide(), clip(p.reason(), 255), p.template(),
                p.ladderStep(), bytes(p.issuer()), clip(p.issuerName(), 32), p.created(), p.expires(), p.silent(), p.source());
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

    private static List<Punishment> activeOf(Connection c, Punishment.Type type, UUID target, String ip, long now)
            throws SQLException {
        List<Punishment> out = new ArrayList<>();
        if (target != null) {
            out.addAll(Sql.query(c, "SELECT " + COLUMNS + " FROM punishments WHERE target_uuid = ? AND type = ? "
                            + "AND removed_at IS NULL AND (expires IS NULL OR expires > ?) ORDER BY created DESC",
                    PunishmentStore::map, Sql.toBytes(target), type.name(), now));
        }
        if (ip != null) {
            for (Punishment p : Sql.query(c, "SELECT " + COLUMNS + " FROM punishments WHERE ip = ? AND ip_wide = TRUE AND type = ? "
                            + "AND removed_at IS NULL AND (expires IS NULL OR expires > ?) ORDER BY created DESC",
                    PunishmentStore::map, ip, type.name(), now)) {
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

    private static List<Alt> altsBlocking(Connection c, UUID uuid, String ip) throws SQLException {
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

    private static Active active(Connection c, UUID target, String ip, long now) throws SQLException {
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

    /** Outcome of {@link #issue}: {@code punishment == null} unless the verdict applied. */
    record IssueResult(PunishDecision.Verdict verdict, Punishment punishment, TriggerResult trigger) {
    }

    /**
     * The one way to store a punishment: decides with {@code decision} (active mute/ban read in the same transaction),
     * inserts the row if it applies, then evaluates the warn rules in the same transaction ({@code hook == null} =
     * rules off). {@code draft} supplies everything but type/duration, which come from the (possibly changed) verdict.
     */
    CompletableFuture<IssueResult> issue(Punishment draft, PunishDecision.Input input, PunishDecision decision, RuleHook hook) {
        return db.async(c -> issueBlocking(c, draft, input, decision, hook));
    }

    IssueResult issueBlocking(Connection c, Punishment draft, PunishDecision.Input input, PunishDecision decision, RuleHook hook)
            throws SQLException {
        return locked(c, draft.target(), conn -> {
            long now = input.now();
            Active act = active(conn, draft.target(), draft.ip(), now);
            PunishDecision.Verdict verdict = decision.decide(input.withActive(act.mute(), act.ban()));
            if (!verdict.applied()) {
                return new IssueResult(verdict, null, null);
            }
            Punishment row = insertRow(conn, shaped(draft, verdict.input()));
            TriggerResult trigger = hook == null || row.target() == null ? null : runRules(conn, row, hook, now);
            return new IssueResult(verdict, row, trigger);
        });
    }

    /** {@code draft} with the type, duration and scope the verdict settled on. */
    private static Punishment shaped(Punishment d, PunishDecision.Input in) {
        boolean timed = in.type() == Punishment.Type.BAN || in.type() == Punishment.Type.MUTE;
        return new Punishment(0, in.type(), d.target(), d.targetName(), d.ip(), in.ipWide(), d.reason(), d.template(),
                d.ladderStep(), d.issuer(), d.issuerName(), d.created(), timed ? in.endsAt() : null, d.silent(),
                null, null, null, null, d.source());
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
                Suggestion.Status.PENDING, null, null, null, null, null);
        if (!verdict.applied()) {
            String note = "exempt".equals(verdict.stage()) ? Suggestion.NOTE_EXEMPT
                    : "never-shorten".equals(verdict.stage()) ? Suggestion.NOTE_ALREADY : clip(verdict.stage(), 32);
            return new TriggerResult(trigger, insertSuggestion(c, with(row, Suggestion.Status.SKIPPED, null, now, null, null,
                    null, note)), null, verdict);
        }
        if (settings.mode() == WarnRules.Mode.AUTO) {
            String ip = step.ipWide() ? lastIpBlocking(c, event.target()) : null;
            Punishment auto = insertRow(c, shaped(new Punishment(0, step.type(), event.target(), event.targetName(), ip,
                    step.ipWide() && ip != null, reason, null, null, null, hook.autoIssuerName(), now, null, false, null,
                    null, null, null, trigger.source()), verdict.input().withStep(step.type(), step.duration(),
                    step.ipWide() && ip != null)));
            return new TriggerResult(trigger, insertSuggestion(c, with(row, Suggestion.Status.AUTO, null, now, null,
                    hook.autoIssuerName(), auto.id(), null)), auto, verdict);
        }
        Long expires = settings.pendingExpire() == null ? null : now + settings.pendingExpire().toMillis();
        return new TriggerResult(trigger, insertSuggestion(c, with(row, Suggestion.Status.PENDING, expires, null, null, null,
                null, null)), null, verdict);
    }

    private static Suggestion with(Suggestion s, Suggestion.Status status, Long expires, Long handledAt, UUID by,
                                   String byName, Long punishmentId, String note) {
        return new Suggestion(s.id(), s.target(), s.targetName(), s.kind(), s.ruleAt(), s.repeat(), s.action(), s.reason(),
                s.source(), s.warnCount(), s.created(), expires, status, handledAt, by, byName, punishmentId, note);
    }

    private static Suggestion insertSuggestion(Connection c, Suggestion s) throws SQLException {
        long id = Sql.insertReturningId(c, "INSERT INTO punishment_suggestions (target_uuid, target_name, kind, rule_at, "
                        + "repeat_trigger, action, reason, source, warn_count, created, expires, status, handled_at, handled_by_uuid, "
                        + "handled_by_name, punishment_id, note) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                bytes(s.target()), s.targetName(), s.kind(), s.ruleAt(), s.repeat(), s.action(), s.reason(), s.source(),
                s.warnCount(), s.created(), s.expires(), s.status().name(), s.handledAt(), bytes(s.handledBy()),
                clip(s.handledByName(), 32), s.punishmentId(), s.note());
        return new Suggestion(id, s.target(), s.targetName(), s.kind(), s.ruleAt(), s.repeat(), s.action(), s.reason(),
                s.source(), s.warnCount(), s.created(), s.expires(), s.status(), s.handledAt(), s.handledBy(), s.handledByName(),
                s.punishmentId(), s.note());
    }

    private static Suggestion mapSuggestion(ResultSet rs) throws SQLException {
        int at = rs.getInt("rule_at");
        Integer ruleAt = rs.wasNull() ? null : at;
        return new Suggestion(rs.getLong("id"), Sql.uuid(rs, "target_uuid"), rs.getString("target_name"), rs.getString("kind"),
                ruleAt, rs.getBoolean("repeat_trigger"), rs.getString("action"), rs.getString("reason"), rs.getString("source"),
                rs.getInt("warn_count"), rs.getLong("created"), Sql.nullableLong(rs, "expires"),
                Suggestion.Status.valueOf(rs.getString("status")), Sql.nullableLong(rs, "handled_at"),
                Sql.uuid(rs, "handled_by_uuid"), rs.getString("handled_by_name"), Sql.nullableLong(rs, "punishment_id"),
                rs.getString("note"));
    }

    private static List<Suggestion> suggestions(Connection c, UUID target) throws SQLException {
        return Sql.query(c, "SELECT " + SUGGESTION_COLUMNS + " FROM punishment_suggestions WHERE target_uuid = ? AND kind = ? "
                + "ORDER BY created DESC, id DESC", PunishmentStore::mapSuggestion, Sql.toBytes(target), Suggestion.KIND_WARN_RULE);
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

    /** What happened when a staff member clicked [Apply] or [Dismiss]. */
    enum HandleKind { APPLIED, DISMISSED, SKIPPED, BLOCKED, ALREADY_HANDLED, EXPIRED, NOT_FOUND }

    record HandleResult(HandleKind kind, Suggestion suggestion, PunishDecision.Verdict verdict, Punishment punishment) {
    }

    /**
     * [Apply]: claims the pending suggestion and applies its action as {@code by}, atomically (first click wins). The
     * clicker's own limits apply ({@code inputFor} builds the request with their permissions); a BLOCKED verdict leaves
     * it pending for someone who may. An already longer mute/ban marks it SKIPPED (never shorten).
     */
    CompletableFuture<HandleResult> apply(long id, java.util.function.Function<Suggestion, PunishDecision.Input> inputFor,
                                          PunishDecision decision, UUID by, String byName, long now) {
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
                String ip = verdict.input().ipWide() ? lastIpBlocking(conn, s.target()) : null;
                PunishDecision.Input eff = verdict.input().withStep(verdict.input().type(), verdict.input().duration(),
                        verdict.input().ipWide() && ip != null);
                Punishment p = insertRow(conn, shaped(new Punishment(0, step.type(), s.target(), s.targetName(), ip, false,
                        s.reason(), null, null, by, byName, now, null, false, null, null, null, null, s.source()), eff));
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

    private static String lastIpBlocking(Connection c, UUID target) {
        try {
            return Sql.queryOne(c, "SELECT ip FROM social_player_ips WHERE uuid = ? ORDER BY last_seen DESC LIMIT 1",
                    rs -> rs.getString("ip"), Sql.toBytes(target)).orElse(null);
        } catch (SQLException e) {
            return null; // social module not installed
        }
    }
}
