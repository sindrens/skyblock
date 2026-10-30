package net.skybornmc.skyblock.economy.admin;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/** The anomaly rules of the economy watch (M3.8). The id is stored in {@code eco_watch_alerts.rule_id} and used in config. */
public enum Rule {
    /** A profile earned far more in one hour than the baseline. */
    EARN_RATE("earn-rate"),
    /** One very large transfer, or one very large amount created at once. */
    BIG_TRANSFER("big-transfer"),
    /** The same profile sent another the same amount many times in a short window. */
    REPEAT_TRANSFER("repeat-transfer"),
    /** A profile cashed many money notes in one hour. */
    NOTE_REDEEM("note-redeem"),
    /** The whole server sold far more in one hour than the hourly average. */
    SELL_SPIKE("sell-spike"),
    /** An account's balance changed without a ledger row (money from nowhere). */
    BALANCE_MISMATCH("balance-mismatch"),
    /** The 14-day sink ratio stayed below the act line (ECONOMY_BALANCE §7.2). */
    SINK_RATIO("sink-ratio");

    private final String id;

    Rule(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static Optional<Rule> byId(String id) {
        return Arrays.stream(values()).filter(r -> r.id.equalsIgnoreCase(id == null ? "" : id.trim())).findFirst();
    }

    public static List<String> ids() {
        return Arrays.stream(values()).map(Rule::id).toList();
    }
}
