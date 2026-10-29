package net.skybornmc.skyblock.common.audit;

import java.util.Map;

/**
 * One audit row. Built by {@link AuditLog#log} (id 0 until written) and returned by queries.
 *
 * @param target      what was acted on: a player uuid, island id, item uuid, … (nullable)
 * @param targetName  readable name of the target at that time (nullable)
 * @param amount      money/items/duration involved (nullable)
 * @param world       world name when the action has a place (nullable, then x/y/z are null too)
 * @param details     everything else (old/new values, item summary, reason …); serialized as JSON
 */
public record AuditEntry(long id, long time, int season, String server, AuditCategory category, String action, Actor actor,
                         String target, String targetName, Long amount, String world, Integer x, Integer y, Integer z,
                         Map<String, Object> details) {

    public AuditEntry {
        details = details == null ? Map.of() : details;
    }

    public boolean hasLocation() {
        return world != null && x != null && y != null && z != null;
    }
}
