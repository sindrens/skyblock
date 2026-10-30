package net.skybornmc.skyblock.common.teleport;

import java.util.Objects;
import java.util.Optional;

/**
 * One running warmup (pure state machine, unit-tested; no Bukkit types). {@link #tick} runs once per second: the
 * first tick shows the full countdown, the tick after the last second says {@link Done}. Events in between
 * ({@link #moved}, {@link #damaged}) can cancel it right away.
 */
public final class WarmupState {

    /** Why a warmup was cancelled (message {@code teleport.cancelled.<lowercase name>}). */
    public enum Cancel { MOVED, DAMAGE, COMBAT, FROZEN, WORLD }

    /** A position without Bukkit types. */
    public record Pos(String world, double x, double y, double z) {
        double distanceSquared(Pos other) {
            double dx = x - other.x;
            double dy = y - other.y;
            double dz = z - other.z;
            return dx * dx + dy * dy + dz * dz;
        }
    }

    /** What a tick decided. */
    public sealed interface Step permits Countdown, Done, Cancelled {
    }

    /** Show "teleporting in {@code seconds}". */
    public record Countdown(int seconds) implements Step {
    }

    /** The warmup is over: teleport now. */
    public record Done() implements Step {
    }

    public record Cancelled(Cancel reason) implements Step {
    }

    private final Pos start;
    private final long startedAt;
    private int left;

    /**
     * @param start     where the player stood when the warmup started
     * @param startedAt epoch millis of the start (combat tags after it cancel)
     * @param seconds   warmup length (at least 1)
     */
    public WarmupState(Pos start, long startedAt, int seconds) {
        this.start = Objects.requireNonNull(start);
        this.startedAt = startedAt;
        this.left = Math.max(1, seconds);
    }

    /** A move (or any position check): another world always cancels; distance only with {@code cancelOnMove}. */
    public Optional<Cancel> moved(Pos now, TeleportRules rules) {
        if (!Objects.equals(now.world(), start.world())) {
            return Optional.of(Cancel.WORLD);
        }
        double max = rules.moveDistance();
        if (rules.cancelOnMove() && start.distanceSquared(now) > max * max) {
            return Optional.of(Cancel.MOVED);
        }
        return Optional.empty();
    }

    public Optional<Cancel> damaged(TeleportRules rules) {
        return rules.cancelOnDamage() ? Optional.of(Cancel.DAMAGE) : Optional.empty();
    }

    /** A combat tag that started after the warmup cancels it ({@code lastCombat} = epoch millis, 0 = never). */
    public Optional<Cancel> combat(long lastCombat, TeleportRules rules) {
        return rules.cancelOnCombat() && lastCombat > startedAt ? Optional.of(Cancel.COMBAT) : Optional.empty();
    }

    /** One second passed (the first call right at the start). */
    public Step tick(Pos now, boolean frozen, long lastCombat, TeleportRules rules) {
        if (frozen) {
            return new Cancelled(Cancel.FROZEN);
        }
        Optional<Cancel> cancel = moved(now, rules).or(() -> combat(lastCombat, rules));
        if (cancel.isPresent()) {
            return new Cancelled(cancel.get());
        }
        if (left <= 0) {
            return new Done();
        }
        return new Countdown(left--);
    }

    /** Seconds still to count down. */
    public int left() {
        return left;
    }
}
