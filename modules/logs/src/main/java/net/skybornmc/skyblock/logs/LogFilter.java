package net.skybornmc.skyblock.logs;

import net.skybornmc.skyblock.common.audit.AuditCategory;
import net.skybornmc.skyblock.common.audit.AuditQuery;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * What a staff member is looking for: a subject (who/what), categories and a time range. Built by commands and the
 * {@code /logs} menu; turned into an {@link AuditQuery} limited to the categories the viewer may see.
 */
final class LogFilter {

    /** Who/what the search is about. */
    enum Subject {
        /** Everything (no subject). */
        ALL,
        /** Everything a player did or had done to them. */
        PLAYER,
        /** Only what a player did (staff accountability, chat, commands). */
        ACTOR,
        /** Between two players, both directions (payments). */
        BETWEEN,
        /** The life of a unique item (target = item uuid). */
        ITEM,
        /** An island by id. */
        ISLAND,
        /** Around a location. */
        NEAR
    }

    Subject subject = Subject.ALL;
    UUID player;
    String playerName;
    UUID other;
    String otherName;
    String target;
    String world;
    int x;
    int z;
    int radius;
    /** Empty = all categories the viewer may see. */
    final Set<AuditCategory> categories = EnumSet.noneOf(AuditCategory.class);
    /** {@code null} or zero = all time. */
    Duration range;

    LogFilter copy() {
        LogFilter c = new LogFilter();
        c.subject = subject;
        c.player = player;
        c.playerName = playerName;
        c.other = other;
        c.otherName = otherName;
        c.target = target;
        c.world = world;
        c.x = x;
        c.z = z;
        c.radius = radius;
        c.categories.addAll(categories);
        c.range = range;
        return c;
    }

    LogFilter player(UUID uuid, String name) {
        subject = Subject.PLAYER;
        player = uuid;
        playerName = name;
        return this;
    }

    LogFilter actor(UUID uuid, String name) {
        subject = Subject.ACTOR;
        player = uuid;
        playerName = name;
        return this;
    }

    LogFilter categories(AuditCategory... values) {
        categories.clear();
        categories.addAll(Set.of(values));
        return this;
    }

    /** Categories that will actually be searched: the chosen ones the viewer may see (all allowed when none chosen). */
    Set<AuditCategory> effectiveCategories(Set<AuditCategory> allowed) {
        Set<AuditCategory> out = EnumSet.noneOf(AuditCategory.class);
        if (categories.isEmpty()) {
            out.addAll(allowed);
        } else {
            for (AuditCategory c : categories) {
                if (allowed.contains(c)) {
                    out.add(c);
                }
            }
        }
        return out;
    }

    /**
     * The audit query for a viewer allowed to see {@code allowed}. Returns {@code null} when nothing would be visible
     * (the caller shows "no access").
     */
    AuditQuery query(Set<AuditCategory> allowed, long now) {
        Set<AuditCategory> effective = effectiveCategories(allowed);
        if (effective.isEmpty()) {
            return null;
        }
        AuditQuery q = AuditQuery.create();
        if (effective.size() < AuditCategory.values().length) {
            q.categories(effective);
        }
        switch (subject) {
            case PLAYER -> q.involving(player);
            case ACTOR -> q.actor(player);
            case BETWEEN -> q.between(player, other);
            case ITEM -> q.target(target);
            case ISLAND -> {
                if (player != null) {
                    q.involving(player);
                } else {
                    q.target(target);
                }
            }
            case NEAR -> q.near(world, x, z, radius);
            case ALL -> {
            }
        }
        if (range != null && !range.isZero()) {
            q.since(Instant.ofEpochMilli(now).minus(range));
        }
        return q;
    }

    /** Readable summary for the audit row of {@code staff/logs_view} and exports. */
    Map<String, Object> describe(Set<AuditCategory> allowed) {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("subject", subject.name().toLowerCase(java.util.Locale.ROOT));
        if (playerName != null) {
            d.put("player", playerName);
        }
        if (otherName != null) {
            d.put("other", otherName);
        }
        if (target != null) {
            d.put("target", target);
        }
        if (subject == Subject.NEAR) {
            d.put("near", world + " " + x + " " + z + " r" + radius);
        }
        d.put("categories", effectiveCategories(allowed).stream().map(AuditCategory::key).toList());
        d.put("range", range == null || range.isZero() ? "all" : range.toString());
        return d;
    }

    /** The audit target of the view row (the player/item/island looked at), or {@code null}. */
    String auditTarget() {
        return switch (subject) {
            case PLAYER, ACTOR, BETWEEN -> player == null ? null : player.toString();
            case ITEM, ISLAND -> player != null ? player.toString() : target;
            default -> null;
        };
    }
}
