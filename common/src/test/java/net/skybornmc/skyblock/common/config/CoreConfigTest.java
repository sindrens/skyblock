package net.skybornmc.skyblock.common.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoreConfigTest {

    @Test
    void slowOperationDefaultIsTenAndOldDefaultMigrates() {
        CoreConfig config = new CoreConfig();
        assertEquals(10, config.slowOperationMs);
        assertEquals(2, config.version());
        ModuleConfig.ValueChange change = config.valueChanges().get(2).get("slow-operation-ms");
        assertTrue(change.matches(5), "the old default 5 is replaced");
        assertFalse(change.matches(25), "an owner's own value is kept");
        assertEquals(10, change.to());
    }
}
