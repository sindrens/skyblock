package net.skybornmc.skyblock.economy.sell;

/**
 * The dynamic price numbers ({@code economy.yml → sell.dynamic.*}, {@code sell.hot-item.*}) as plain values, so the price
 * logic can be tested without the config system. Fractions, not percents (floor 0.40, recovery 0.02, impact 1e-6).
 */
public record DynamicSettings(boolean enabled, double floor, double ceiling, double recovery, int intervalMinutes,
                              double target, double defaultImpact, double minImpact, double maxImpact, long minVolume,
                              int quietMinutes, double driftPercentPerDay, double trendThresholdPercent, int catchUpMaxHours,
                              boolean hotEnabled, double hotPercent) {

    /** The defaults of ECONOMY_BALANCE §8 (also the config defaults). */
    public static final DynamicSettings DEFAULTS = new DynamicSettings(true, 0.40, 1.50, 0.02, 10, 0.85, 1e-6, 1e-8, 1e-4,
            1_000, 60, 10, 0.5, 168, true, 25);

    public DynamicSettings {
        floor = Math.max(0.01, Math.min(1.0, floor));
        ceiling = Math.max(1.0, ceiling);
        recovery = Math.max(0, Math.min(1, recovery));
        intervalMinutes = Math.max(1, intervalMinutes);
        target = Math.max(floor, Math.min(1.0, target));
        minImpact = Math.max(0, minImpact);
        maxImpact = Math.max(minImpact, maxImpact);
        defaultImpact = Math.max(minImpact, Math.min(maxImpact, defaultImpact));
        minVolume = Math.max(0, minVolume);
        quietMinutes = Math.max(0, quietMinutes);
        driftPercentPerDay = Math.max(0, driftPercentPerDay);
        catchUpMaxHours = Math.max(0, catchUpMaxHours);
        hotPercent = Math.max(0, hotPercent);
    }

    /** The Hot Item factor (1.25 for +25%), 1.0 when the Hot Item is off. */
    public double hotFactor() {
        return hotEnabled ? 1.0 + hotPercent / 100.0 : 1.0;
    }

    public long intervalMillis() {
        return intervalMinutes * 60_000L;
    }

    public double driftStep() {
        return PriceMath.driftStep(driftPercentPerDay, intervalMinutes);
    }
}
