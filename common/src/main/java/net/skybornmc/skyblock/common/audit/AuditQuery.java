package net.skybornmc.skyblock.common.audit;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Filter for the staff log viewer ({@code /logs}, Phase 1): by actor, target, category/action, time range, location
 * radius, with paging. Newest first. Every filter is optional; combine freely.
 *
 * <pre>{@code
 * audit.query(AuditQuery.create().actor(uuid).categories(AuditCategory.ECONOMY).since(Instant.now().minus(Duration.ofDays(7))).page(0, 50));
 * }</pre>
 */
public final class AuditQuery {

    private UUID actor;
    private String actorName;
    private String target;
    private UUID actorOrTarget;
    private final Set<AuditCategory> categories = EnumSet.noneOf(AuditCategory.class);
    private final Set<String> actions = new LinkedHashSet<>();
    private Long from;
    private Long to;
    private String world;
    private Integer centerX;
    private Integer centerZ;
    private int radius;
    private Integer season;
    private int limit = 50;
    private int offset;

    private AuditQuery() {
    }

    public static AuditQuery create() {
        return new AuditQuery();
    }

    /** Rows done by this player. */
    public AuditQuery actor(UUID uuid) {
        this.actor = uuid;
        return this;
    }

    /** Rows done by console/system parts, e.g. {@code "CONSOLE"}, {@code "SYSTEM:interest"}. */
    public AuditQuery actorName(String name) {
        this.actorName = name;
        return this;
    }

    /** Rows about this target (player uuid string, island id, item uuid …). */
    public AuditQuery target(String target) {
        this.target = target;
        return this;
    }

    public AuditQuery target(UUID target) {
        return target(target == null ? null : target.toString());
    }

    /** Rows where this player is the actor <b>or</b> the target ("everything about Steve"). */
    public AuditQuery involving(UUID player) {
        this.actorOrTarget = player;
        return this;
    }

    public AuditQuery categories(AuditCategory... categories) {
        this.categories.addAll(List.of(categories));
        return this;
    }

    public AuditQuery categories(Collection<AuditCategory> categories) {
        this.categories.addAll(categories);
        return this;
    }

    public AuditQuery actions(String... actions) {
        this.actions.addAll(List.of(actions));
        return this;
    }

    public AuditQuery since(Instant from) {
        this.from = from == null ? null : from.toEpochMilli();
        return this;
    }

    public AuditQuery until(Instant to) {
        this.to = to == null ? null : to.toEpochMilli();
        return this;
    }

    public AuditQuery season(int season) {
        this.season = season;
        return this;
    }

    /** Rows within {@code radius} blocks (square, x/z) of a point in a world. */
    public AuditQuery near(String world, int x, int z, int radius) {
        this.world = Objects.requireNonNull(world, "world");
        this.centerX = x;
        this.centerZ = z;
        this.radius = Math.max(0, radius);
        return this;
    }

    /** Page {@code page} (0-based) of {@code size} rows; the size is capped by {@code query.max-rows}. */
    public AuditQuery page(int page, int size) {
        this.limit = Math.max(1, size);
        this.offset = Math.max(0, page) * this.limit;
        return this;
    }

    int limit() {
        return limit;
    }

    int offset() {
        return offset;
    }

    AuditQuery withPage(int newOffset, int newLimit) {
        AuditQuery copy = create();
        copy.actor = actor;
        copy.actorName = actorName;
        copy.target = target;
        copy.actorOrTarget = actorOrTarget;
        copy.categories.addAll(categories);
        copy.actions.addAll(actions);
        copy.from = from;
        copy.to = to;
        copy.world = world;
        copy.centerX = centerX;
        copy.centerZ = centerZ;
        copy.radius = radius;
        copy.season = season;
        copy.limit = newLimit;
        copy.offset = newOffset;
        return copy;
    }

    /** WHERE clause (without the keyword, "1=1" when empty) and its parameters, in order. */
    record Where(String sql, List<Object> params) {
    }

    Where where() {
        StringBuilder sql = new StringBuilder("1=1");
        List<Object> params = new ArrayList<>();
        if (actor != null) {
            sql.append(" AND actor_uuid = ?");
            params.add(actor);
        }
        if (actorName != null) {
            sql.append(" AND actor_uuid IS NULL AND actor_name = ?");
            params.add(actorName);
        }
        if (target != null) {
            sql.append(" AND target = ?");
            params.add(target);
        }
        if (actorOrTarget != null) {
            sql.append(" AND (actor_uuid = ? OR target = ?)");
            params.add(actorOrTarget);
            params.add(actorOrTarget.toString());
        }
        if (!categories.isEmpty()) {
            sql.append(" AND category IN (").append("?,".repeat(categories.size() - 1)).append("?)");
            categories.forEach(c -> params.add(c.key()));
        }
        if (!actions.isEmpty()) {
            sql.append(" AND action IN (").append("?,".repeat(actions.size() - 1)).append("?)");
            params.addAll(actions);
        }
        if (from != null) {
            sql.append(" AND created_at >= ?");
            params.add(from);
        }
        if (to != null) {
            sql.append(" AND created_at < ?");
            params.add(to);
        }
        if (season != null) {
            sql.append(" AND season_id = ?");
            params.add(season);
        }
        if (world != null) {
            sql.append(" AND world = ? AND x BETWEEN ? AND ? AND z BETWEEN ? AND ?");
            params.add(world);
            params.add(centerX - radius);
            params.add(centerX + radius);
            params.add(centerZ - radius);
            params.add(centerZ + radius);
        }
        return new Where(sql.toString(), params);
    }
}
