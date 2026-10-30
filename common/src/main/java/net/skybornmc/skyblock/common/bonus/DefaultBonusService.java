package net.skybornmc.skyblock.common.bonus;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.DoubleSupplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The {@link BonusService} of the plugin core. Sources are kept by id; a source that throws is skipped (and logged once
 * per minute) so one broken module never stops mining.
 */
public final class DefaultBonusService implements BonusService {

    private final Map<String, BonusSource> sources = new ConcurrentHashMap<>();
    private volatile Map<BonusFamily, Double> caps = defaults();
    private final DoubleSupplier sellCap;
    private final Logger log;
    private volatile long lastError;

    /**
     * @param sellCap the economy's sell cap in percent (looked up on every call, so an economy reload counts at once)
     * @param log     for broken sources; may be null in tests
     */
    public DefaultBonusService(DoubleSupplier sellCap, Logger log) {
        this.sellCap = Objects.requireNonNull(sellCap, "sellCap");
        this.log = log;
    }

    private static Map<BonusFamily, Double> defaults() {
        Map<BonusFamily, Double> out = new EnumMap<>(BonusFamily.class);
        for (BonusFamily f : BonusFamily.values()) {
            out.put(f, f.defaultCap());
        }
        return out;
    }

    @Override
    public void register(String id, BonusSource source) {
        sources.put(Objects.requireNonNull(id, "id"), Objects.requireNonNull(source, "source"));
    }

    @Override
    public void unregister(String id) {
        if (id != null) {
            sources.remove(id);
        }
    }

    @Override
    public double cap(BonusFamily family) {
        if (family == BonusFamily.SELL) {
            double cap = sellCap.getAsDouble();
            return Double.isFinite(cap) ? Math.max(0, cap) : family.defaultCap();
        }
        return caps.getOrDefault(family, family.defaultCap());
    }

    @Override
    public void caps(Map<BonusFamily, Double> values) {
        Map<BonusFamily, Double> next = defaults();
        if (values != null) {
            values.forEach((family, cap) -> {
                if (family != null && family != BonusFamily.SELL && cap != null && Double.isFinite(cap) && cap >= 0) {
                    next.put(family, cap);
                }
            });
        }
        caps = next;
    }

    @Override
    public List<BonusPart> breakdown(BonusFamily family, BonusContext context) {
        List<BonusPart> out = new ArrayList<>(2);
        for (Map.Entry<String, BonusSource> e : sources.entrySet()) {
            double value;
            try {
                value = e.getValue().value(family, context);
            } catch (RuntimeException error) {
                long now = System.currentTimeMillis();
                if (log != null && now - lastError > 60_000) {
                    lastError = now;
                    log.log(Level.WARNING, "Bonus source '" + e.getKey() + "' failed for " + family.id() + " (skipped)", error);
                }
                continue;
            }
            if (Double.isFinite(value) && value != 0) {
                out.add(new BonusPart(e.getKey(), value));
            }
        }
        return out;
    }
}
