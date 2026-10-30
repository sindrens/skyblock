package net.skybornmc.skyblock.economy.admin;

import net.skybornmc.skyblock.common.economy.Currency;

import java.util.List;
import java.util.UUID;

/**
 * A stored anomaly alert (M3.8, {@code eco_watch_alerts}).
 *
 * @param subject   the profile key it is about, {@code null} for server-wide alerts
 * @param other     the other side of a transfer, or the island/bank account of a balance mismatch, nullable
 * @param hour      the hour bucket it is about
 * @param state     open, reviewed or dismissed
 */
public record WatchAlert(long id, long createdAt, long hour, Rule rule, AlertDraft.Severity severity, Currency currency,
                         UUID subject, UUID other, long amount, long expected, int hits, List<Long> ledgerIds, State state,
                         String reviewedBy, Long reviewedAt) {

    public enum State {
        OPEN, REVIEWED, DISMISSED;

        public String key() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }

        public static State byKey(String key) {
            for (State s : values()) {
                if (s.key().equalsIgnoreCase(key)) {
                    return s;
                }
            }
            return OPEN;
        }
    }

    public WatchAlert {
        ledgerIds = ledgerIds == null ? List.of() : List.copyOf(ledgerIds);
    }

    /** {@code "12,15,99"} → ids (bad parts skipped). */
    static List<Long> parseIds(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        java.util.List<Long> out = new java.util.ArrayList<>();
        for (String part : text.split(",")) {
            try {
                out.add(Long.parseLong(part.trim()));
            } catch (NumberFormatException ignored) {
                // skip
            }
        }
        return List.copyOf(out);
    }

    /** Ids as stored: comma separated, never longer than {@code maxChars} (the oldest ids are dropped first). */
    static String joinIds(List<Long> ids, int maxChars) {
        StringBuilder out = new StringBuilder();
        for (int i = ids.size() - 1; i >= 0; i--) {
            String part = String.valueOf(ids.get(i));
            int extra = part.length() + (out.isEmpty() ? 0 : 1);
            if (out.length() + extra > maxChars) {
                break;
            }
            out.insert(0, out.isEmpty() ? part : part + ",");
        }
        return out.toString();
    }
}
