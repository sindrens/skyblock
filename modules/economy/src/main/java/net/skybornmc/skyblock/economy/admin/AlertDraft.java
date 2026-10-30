package net.skybornmc.skyblock.economy.admin;

import net.skybornmc.skyblock.common.economy.Currency;

import java.util.List;
import java.util.UUID;

/**
 * An anomaly found by the {@link AnomalyDetector} (M3.8), before it is stored.
 *
 * @param subject  the profile key (player account) it is about, {@code null} for server-wide alerts
 * @param other    the other side (receiver of transfers), nullable
 * @param amount   what was seen (earned in the hour, the transfer, the jump in balance ...)
 * @param expected the threshold or expected value it is compared with
 * @param hits     how many transactions (repeat-transfer, note-redeem), else 1
 * @param hour     the hour bucket the alert is about
 * @param ledgerIds evidence rows (newest last), at most the configured number
 */
public record AlertDraft(Rule rule, Severity severity, Currency currency, UUID subject, UUID other, long amount, long expected,
                         int hits, long hour, List<Long> ledgerIds) {

    public enum Severity {
        WATCH, HIGH;

        public String key() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }

        public static Severity byKey(String key) {
            return "high".equalsIgnoreCase(key) ? HIGH : WATCH;
        }
    }

    public AlertDraft {
        ledgerIds = ledgerIds == null ? List.of() : List.copyOf(ledgerIds);
    }

    /** Alerts with the same key share one cooldown. */
    public String cooldownKey() {
        return switch (rule) {
            case REPEAT_TRANSFER -> rule.id() + ":" + subject + ":" + other + ":" + currency.key();
            case SELL_SPIKE, SINK_RATIO -> rule.id() + ":" + currency.key();
            default -> rule.id() + ":" + subject + ":" + currency.key();
        };
    }
}
