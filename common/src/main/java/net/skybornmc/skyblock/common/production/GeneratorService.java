package net.skybornmc.skyblock.common.production;

import net.skybornmc.skyblock.common.service.ServiceRegistry;

import java.util.Optional;
import java.util.UUID;

/**
 * Custom island generators (PHASE-7 M7.2), published by {@code modules/production} only while they are switched on
 * ({@code generators.yml → enabled}). Other modules treat "this service is registered" as "custom generators exist": the
 * Generator upgrade in {@code /is upgrade} can then be bought (otherwise "Coming soon"). Main thread.
 */
public interface GeneratorService {

    /** The published service, if custom generators are on. */
    static Optional<GeneratorService> of(ServiceRegistry services) {
        return services.optional(GeneratorService.class);
    }

    /** The island's Generator level (0 = plain cobblestone), from the Generator upgrade (or the staff test override). */
    int generatorLevel(UUID islandId);

    /** The highest Generator level of the table ({@code generators.yml → levels}). */
    int maxGeneratorLevel();
}
