package net.skybornmc.skyblock.common.service;

import org.junit.jupiter.api.Test;

import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServiceRegistryTest {

    interface Greeter {
        String hi();
    }

    private final ServiceRegistry registry = new ServiceRegistry(Logger.getLogger("test"));

    @Test
    void registerAndGet() {
        Greeter g = () -> "hi";
        registry.register("greet", Greeter.class, g);
        assertSame(g, registry.get(Greeter.class));
        assertTrue(registry.optional(Greeter.class).isPresent());
        assertEquals("greet", registry.describe().get("Greeter"));
    }

    @Test
    void duplicateProviderFails() {
        registry.register("a", Greeter.class, () -> "a");
        assertThrows(IllegalStateException.class, () -> registry.register("b", Greeter.class, () -> "b"));
    }

    @Test
    void missingServiceNamesTheType() {
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> registry.get(Greeter.class));
        assertTrue(e.getMessage().contains("Greeter"));
    }

    @Test
    void unregisterAllOnlyRemovesOwner() {
        registry.register("a", Greeter.class, () -> "a");
        registry.register("b", Runnable.class, () -> { });
        registry.unregisterAll("a");
        assertFalse(registry.has(Greeter.class));
        assertTrue(registry.has(Runnable.class));
    }

    @Test
    void unregisterIgnoresOtherOwner() {
        registry.register("a", Greeter.class, () -> "a");
        registry.unregister("b", Greeter.class);
        assertTrue(registry.has(Greeter.class));
    }
}
