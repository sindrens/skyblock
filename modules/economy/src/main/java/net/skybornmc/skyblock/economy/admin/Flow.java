package net.skybornmc.skyblock.economy.admin;

import java.util.Locale;
import java.util.Optional;

/** How a ledger row changed the money supply (M3.8). Stored as the lowercase key in {@code eco_watch_flows.flow}. */
public enum Flow {
    /** From nobody: new money (selling, drops, interest, staff give). */
    CREATED,
    /** To nobody: money left the economy (shop, fees, upgrades, staff take). */
    REMOVED,
    /** Between two accounts: the supply did not change (pay, trade, bank deposits). */
    MOVED;

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Optional<Flow> byKey(String key) {
        for (Flow f : values()) {
            if (f.key().equalsIgnoreCase(key)) {
                return Optional.of(f);
            }
        }
        return Optional.empty();
    }
}
