package net.skybornmc.skyblock.depths.combat;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * One mini-boss fight (M4.8 + REVIEW3 R3-10): damage per profile ({@code ProfileService.profileKey}), when each profile
 * last hit and from which address, the health scaling and the one-time payout. Pure; time passed in; main thread only.
 *
 * <p><b>Eligible</b> (rewards) = dealt at least {@code minShare} of the damage, hit within the last {@code windowMs}, not
 * AFK, and among the first {@code maxPerIp} profiles of its address (most damage first). Every eligible profile gets the
 * full reward; {@link #claim} hands the list out once, so a second death event can never pay twice.</p>
 *
 * <p><b>Scaling</b>: health ×(1 + {@code perExtra} × extra fighters), extra = active fighters − 1, at most
 * {@code maxExtra}. A fighter counts for scaling with any damage (hit in the window, not AFK, address cap); the 10% rule
 * is for the reward only, so a group can't keep the health low by holding back. The health share left is kept when the
 * scale changes.</p>
 */
public final class BossFight {

    private static final class Entry {
        double damage;
        long lastHit;
        String ip;
    }

    private final double baseHealth;
    private final Map<UUID, Entry> entries = new LinkedHashMap<>();
    private double total;
    private double scale = 1.0;
    private boolean paid;

    /** @param baseHealth the boss's max health at spawn (scale 1) */
    public BossFight(double baseHealth) {
        this.baseHealth = baseHealth > 0 && Double.isFinite(baseHealth) ? baseHealth : 1;
    }

    public double baseHealth() {
        return baseHealth;
    }

    /** The current health multiplier (1 = unscaled). */
    public double scale() {
        return scale;
    }

    /** Max health at the current scale. */
    public double scaledMax() {
        return baseHealth * scale;
    }

    /** Remembers a new scale (after it was applied to the entity). */
    public void scale(double value) {
        this.scale = value > 0 && Double.isFinite(value) ? value : 1.0;
    }

    /** Records a hit (negative or NaN damage ignored; the hit time and address still count). */
    public void hit(UUID profile, String ip, double amount, long now) {
        if (profile == null) {
            return;
        }
        Entry e = entries.computeIfAbsent(profile, k -> new Entry());
        if (amount > 0 && Double.isFinite(amount)) {
            e.damage += amount;
            total += amount;
        }
        e.lastHit = Math.max(e.lastHit, now);
        if (ip != null && !ip.isBlank()) {
            e.ip = ip;
        }
    }

    public double of(UUID profile) {
        Entry e = entries.get(profile);
        return e == null ? 0 : e.damage;
    }

    public double total() {
        return total;
    }

    public boolean paid() {
        return paid;
    }

    /** Profiles that count for scaling now, most damage first. */
    public List<UUID> active(long now, long windowMs, int maxPerIp, Predicate<UUID> afk) {
        return filter(now, windowMs, maxPerIp, afk, 0);
    }

    /** Profiles that earned the full reward now, most damage first (does not mark anything as paid). */
    public List<UUID> eligible(long now, double maxHealth, double minShare, long windowMs, int maxPerIp, Predicate<UUID> afk) {
        double base = Math.max(maxHealth > 0 ? maxHealth : 0, total);
        double needed = base * Math.max(0, minShare);
        return filter(now, windowMs, maxPerIp, afk, needed);
    }

    /**
     * The payout list, exactly once per fight: the first call returns {@link #eligible}, every later call an empty list.
     */
    public List<UUID> claim(long now, double maxHealth, double minShare, long windowMs, int maxPerIp, Predicate<UUID> afk) {
        if (paid) {
            return List.of();
        }
        paid = true;
        return eligible(now, maxHealth, minShare, windowMs, maxPerIp, afk);
    }

    private List<UUID> filter(long now, long windowMs, int maxPerIp, Predicate<UUID> afk, double minDamage) {
        List<Map.Entry<UUID, Entry>> sorted = new ArrayList<>(entries.entrySet());
        sorted.sort(Comparator.comparingDouble((Map.Entry<UUID, Entry> e) -> e.getValue().damage).reversed());
        Map<String, Integer> perIp = new HashMap<>();
        List<UUID> out = new ArrayList<>();
        for (Map.Entry<UUID, Entry> e : sorted) {
            Entry v = e.getValue();
            if (v.damage <= 0 || v.damage < minDamage) {
                continue;
            }
            if (windowMs > 0 && now - v.lastHit > windowMs) {
                continue;
            }
            if (afk != null && afk.test(e.getKey())) {
                continue;
            }
            if (maxPerIp > 0 && v.ip != null) {
                int used = perIp.getOrDefault(v.ip, 0);
                if (used >= maxPerIp) {
                    continue;
                }
                perIp.put(v.ip, used + 1);
            }
            out.add(e.getKey());
        }
        return out;
    }

    // ------------------------------------------------------------------ math

    /**
     * Health multiplier for {@code fighters} active fighters: 1 + perExtraPercent/100 × min(maxExtra, fighters − 1).
     * 0 or 1 fighter = 1.
     */
    public static double scale(int fighters, double perExtraPercent, int maxExtra) {
        int extra = Math.max(0, Math.min(Math.max(0, maxExtra), fighters - 1));
        double per = Double.isFinite(perExtraPercent) ? Math.max(0, perExtraPercent) / 100.0 : 0;
        return 1.0 + per * extra;
    }

    /** The health after a max health change that keeps the share left (never 0 for a living boss, never above max). */
    public static double rescaled(double health, double oldMax, double newMax) {
        if (!(oldMax > 0) || !(newMax > 0) || !(health > 0)) {
            return Math.max(0, Math.min(newMax, health));
        }
        double next = health / oldMax * newMax;
        return Math.max(Math.min(newMax, next), Math.min(newMax, 0.5));
    }
}
