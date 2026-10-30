package net.skybornmc.skyblock.depths.combat;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Damage dealt to one mini-boss per profile ({@code ProfileService.profileKey}), so the rewards go to everyone who
 * really fought it (M4.8). Pure; main thread only.
 */
public final class DamageShares {

    private final Map<UUID, Double> damage = new LinkedHashMap<>();
    private double total;

    /** Adds damage (negative or NaN ignored). */
    public void add(UUID profile, double amount) {
        if (profile == null || !(amount > 0)) {
            return;
        }
        damage.merge(profile, amount, Double::sum);
        total += amount;
    }

    public double of(UUID profile) {
        return damage.getOrDefault(profile, 0.0);
    }

    public double total() {
        return total;
    }

    /**
     * Profiles that earned the rewards: at least {@code minShare} of {@code maxHealth} dealt (or of the total damage when
     * that is larger, e.g. after healing), plus the killer always, killer first.
     */
    public List<UUID> eligible(UUID killer, double maxHealth, double minShare) {
        double base = Math.max(maxHealth, total);
        double needed = base * Math.max(0, minShare);
        List<UUID> out = new ArrayList<>();
        if (killer != null) {
            out.add(killer);
        }
        for (Map.Entry<UUID, Double> e : damage.entrySet()) {
            if (!e.getKey().equals(killer) && e.getValue() >= needed && e.getValue() > 0) {
                out.add(e.getKey());
            }
        }
        return out;
    }
}
