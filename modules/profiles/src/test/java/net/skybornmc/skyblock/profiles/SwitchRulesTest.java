package net.skybornmc.skyblock.profiles;

import net.skybornmc.skyblock.common.profile.Profile;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SwitchRulesTest {

    private final UUID owner = UUID.randomUUID();
    private final Profile one = new Profile(owner, owner, 1, null, null, 0, 0, 0);
    private final Profile two = new Profile(UUID.randomUUID(), owner, 2, "Farm", null, 0, 0, 0);

    private SwitchRules.Input input(Profile target, boolean unlocked, long last, long now, boolean blocked) {
        return new SwitchRules.Input(true, false, one.id(), target, unlocked, last, now, 10_000, false, blocked, false);
    }

    @Test
    void okWhenEverythingIsFine() {
        assertEquals(Optional.empty(), SwitchRules.check(input(two, true, 0, 1_000, false)));
    }

    @Test
    void refusals() {
        assertEquals(Optional.of(SwitchRules.Problem.SAME), SwitchRules.check(input(one, true, 0, 1_000, false)));
        assertEquals(Optional.of(SwitchRules.Problem.GONE), SwitchRules.check(input(null, true, 0, 1_000, false)));
        assertEquals(Optional.of(SwitchRules.Problem.LOCKED), SwitchRules.check(input(two, false, 0, 1_000, false)));
        assertEquals(Optional.of(SwitchRules.Problem.BLOCKED_WORLD), SwitchRules.check(input(two, true, 0, 1_000, true)));
        assertEquals(Optional.of(SwitchRules.Problem.COOLDOWN), SwitchRules.check(input(two, true, 5_000, 10_000, false)));
        assertEquals(Optional.empty(), SwitchRules.check(input(two, true, 5_000, 15_000, false)));
        assertEquals(Optional.of(SwitchRules.Problem.BUSY), SwitchRules.check(new SwitchRules.Input(true, true, one.id(), two, true,
                0, 1_000, 10_000, false, false, false)));
        assertEquals(Optional.of(SwitchRules.Problem.NOT_LOADED), SwitchRules.check(new SwitchRules.Input(false, false, one.id(), two,
                true, 0, 1_000, 10_000, false, false, false)));
        assertEquals(Optional.of(SwitchRules.Problem.DEAD), SwitchRules.check(new SwitchRules.Input(true, false, one.id(), two,
                true, 0, 1_000, 10_000, false, false, true)));
    }

    @Test
    void cooldownBypassAndLeft() {
        assertEquals(Optional.empty(), SwitchRules.check(new SwitchRules.Input(true, false, one.id(), two, true, 5_000, 6_000, 10_000,
                true, false, false)));
        assertEquals(9_000, SwitchRules.cooldownLeft(5_000, 6_000, 10_000));
        assertEquals(0, SwitchRules.cooldownLeft(0, 6_000, 10_000));
    }
}
