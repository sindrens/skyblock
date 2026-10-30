package net.skybornmc.skyblock.worlds.protection;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BypassRuleTest {

    private final BypassRule rule = BypassRule.parse("skyblock.worlds.protection.bypass",
            List.of("depths=skyblock.depths.build", "spawn=skyblock.build.spawn"), true, p -> {
                throw new AssertionError(p);
            });

    @Test
    void needsCreativeAndAPermission() {
        Set<String> builder = Set.of("skyblock.worlds.protection.bypass");
        assertTrue(rule.bypasses("depths", true, builder::contains));
        assertTrue(rule.bypasses("warzone", true, builder::contains));
        assertFalse(rule.bypasses("depths", false, builder::contains), "survival builders are protected from accidents");
        assertTrue(rule.mayBypass("depths", builder::contains), "hint: they could, in creative");
    }

    @Test
    void worldPermissionsOnlyCountInTheirWorld() {
        Set<String> depthsAdmin = Set.of("skyblock.depths.build");
        assertTrue(rule.bypasses("depths", true, depthsAdmin::contains));
        assertTrue(rule.bypasses("DEPTHS", true, depthsAdmin::contains));
        assertFalse(rule.bypasses("spawn", true, depthsAdmin::contains));
        assertFalse(rule.bypasses("warzone", true, depthsAdmin::contains));
    }

    @Test
    void playersNeverBypass() {
        Set<String> none = Set.of();
        assertFalse(rule.bypasses("depths", true, none::contains));
        assertFalse(rule.mayBypass("spawn", none::contains));
    }

    @Test
    void withoutCreativeRule() {
        BypassRule loose = new BypassRule("x.bypass", java.util.Map.of(), false);
        assertTrue(loose.bypasses("depths", false, Set.of("x.bypass")::contains));
        BypassRule empty = new BypassRule("", java.util.Map.of(), false);
        assertFalse(empty.bypasses("depths", true, p -> true), "an empty permission never matches");
    }

    @Test
    void brokenLinesAreReported() {
        List<String> warnings = new ArrayList<>();
        BypassRule.parse("p", List.of("nope", "=x", "spawn="), true, warnings::add);
        assertEquals(3, warnings.size());
    }
}
