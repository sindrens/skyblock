package net.skybornmc.skyblock.tutorial;

import net.skybornmc.skyblock.common.audit.AuditEntry;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Audit rows other modules already write, as tutorial signals (pure, unit-tested; the achievements module counts sales
 * and trades the same way, so no module needs a new event): a shop purchase ({@code economy:shop_buy}), an island tool
 * upgrade ({@code item:island_tool_upgrade}, detail {@code kind}), an island upgrade ({@code island:upgrade_buy}), an
 * invite ({@code island:island_invite}), the daily reward ({@code rewards:daily_claim}).
 * The player is the row's actor, or for system rows (votes) the target player.
 */
final class AuditSignals {

    /** A signal for one player. */
    record Hit(UUID player, Signal signal) {
    }

    private AuditSignals() {
    }

    /** {@code category:action} keys the AUDIT steps of the catalog listen to (lowercase). */
    static Set<String> wanted(Catalog catalog) {
        Set<String> out = new HashSet<>();
        for (Catalog.Chapter c : catalog.chapters()) {
            for (Catalog.Step s : c.steps()) {
                if (s.type() != StepType.AUDIT) {
                    continue;
                }
                String[] parts = s.target().split(":", 3);
                if (parts.length < 2) {
                    continue;
                }
                for (String action : parts[1].split("\\|")) {
                    out.add(parts[0].strip().toLowerCase(Locale.ROOT) + ":" + action.strip().toLowerCase(Locale.ROOT));
                }
            }
        }
        return Set.copyOf(out);
    }

    static Optional<Hit> map(AuditEntry e, Set<String> wanted) {
        if (e == null || e.category() == null || e.action() == null) {
            return Optional.empty();
        }
        String key = e.category().key() + ":" + e.action().toLowerCase(Locale.ROOT);
        if (!wanted.contains(key)) {
            return Optional.empty();
        }
        UUID player = e.actor() != null && e.actor().isPlayer() ? e.actor().uuid() : uuid(e.target());
        if (player == null) {
            return Optional.empty();
        }
        Map<String, String> details = new HashMap<>();
        e.details().forEach((k, v) -> {
            if (k != null && (v instanceof String || v instanceof Number || v instanceof Boolean)) {
                details.put(k, String.valueOf(v));
            }
        });
        return Optional.of(new Hit(player, new Signal(StepType.AUDIT, key, 1, details)));
    }

    private static UUID uuid(String text) {
        if (text == null || text.length() != 36) {
            return null;
        }
        try {
            return UUID.fromString(text);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
