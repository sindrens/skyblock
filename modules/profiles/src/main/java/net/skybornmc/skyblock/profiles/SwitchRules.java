package net.skybornmc.skyblock.profiles;

import net.skybornmc.skyblock.common.profile.Profile;

import java.util.Optional;
import java.util.UUID;

/** Whether a player may switch to a profile right now (owner P5, PR4). Pure logic, unit-tested. */
final class SwitchRules {

    /** Why a switch is refused; the key is the message key ({@code switch.<key>}). */
    enum Problem {
        NOT_LOADED("not-loaded"), BUSY("busy"), SAME("same"), GONE("gone"), LOCKED("locked"), COOLDOWN("cooldown"),
        BLOCKED_WORLD("blocked-world"), DEAD("dead");

        final String key;

        Problem(String key) {
            this.key = key;
        }
    }

    /** What the check needs to know about the player. */
    record Input(boolean loaded, boolean switching, UUID active, Profile target, boolean targetSlotUnlocked, long lastSwitch,
                 long now, long cooldownMillis, boolean bypassCooldown, boolean blockedWorld, boolean dead) {
    }

    private SwitchRules() {
    }

    static Optional<Problem> check(Input in) {
        if (!in.loaded()) {
            return Optional.of(Problem.NOT_LOADED);
        }
        if (in.switching()) {
            return Optional.of(Problem.BUSY);
        }
        if (in.target() == null) {
            return Optional.of(Problem.GONE);
        }
        if (in.target().id().equals(in.active())) {
            return Optional.of(Problem.SAME);
        }
        if (!in.targetSlotUnlocked()) {
            return Optional.of(Problem.LOCKED);
        }
        if (in.dead()) {
            return Optional.of(Problem.DEAD);
        }
        if (in.blockedWorld()) {
            return Optional.of(Problem.BLOCKED_WORLD);
        }
        if (!in.bypassCooldown() && cooldownLeft(in.lastSwitch(), in.now(), in.cooldownMillis()) > 0) {
            return Optional.of(Problem.COOLDOWN);
        }
        return Optional.empty();
    }

    /** Milliseconds until the next switch is allowed (0 = now). */
    static long cooldownLeft(long lastSwitch, long now, long cooldownMillis) {
        if (lastSwitch <= 0 || cooldownMillis <= 0) {
            return 0;
        }
        return Math.max(0, lastSwitch + cooldownMillis - now);
    }
}
