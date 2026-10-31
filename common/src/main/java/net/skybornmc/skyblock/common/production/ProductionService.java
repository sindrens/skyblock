package net.skybornmc.skyblock.common.production;

import net.skybornmc.skyblock.common.service.ServiceRegistry;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Island production (PHASE-7), published by {@code modules/production} only while Island Fuel is switched on
 * ({@code production.yml → fuel.enabled}). Other modules treat "this service is registered" as "Island Fuel exists":
 * the AFK pool then delivers its banked fuel, the daily calendar and crates hand out Fuel Cells instead of their
 * fallback, and the Fuel Tank upgrade and the Fuel Efficiency mastery can be bought. Main thread.
 */
public interface ProductionService {

    /** The published service, if Island Fuel is on. */
    static Optional<ProductionService> of(ServiceRegistry services) {
        return services.optional(ProductionService.class);
    }

    /** The island's fuel right now (Asleep with an empty tank for an island that never had fuel). */
    FuelView fuel(UUID islandId);

    /**
     * New Fuel Cell items worth {@code minutes} in total, split into the configured cell sizes (largest first; a rest
     * smaller than the smallest cell becomes one smallest cell, at least one cell). Always bound to {@code profileKey}
     * (PD4: fuel can't be traded, so money can't buy production hours). Unique items (dupe scanner, used once).
     *
     * @param origin where they came from, stored on the item (e.g. {@code crate:rare}, {@code rewards:daily})
     */
    List<ItemStack> createFuelCells(UUID profileKey, long minutes, String origin);

    /**
     * The fuel gauge for menus (the island menu shows it): state, fuel and tank, burn rate and how to get fuel, in the
     * viewer's look. Empty for an unknown island.
     */
    Optional<ItemStack> fuelGauge(Player viewer, UUID islandId);
}
