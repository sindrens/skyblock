package net.skybornmc.skyblock.common.production;

import net.skybornmc.skyblock.common.audit.Actor;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * What the islands module asks the production module about Island Fuel (PHASE-7 M7.1). Registered with
 * {@code IslandService#fuelProvider} while Island Fuel is switched on ({@code production.yml → fuel.enabled}); the islands
 * module delegates {@code hasIslandFuel}, {@code islandProductionPaused} and {@code addIslandFuel} to it. Main thread.
 */
public interface FuelProvider {

    /** True while the island is Active or Fueled (spawners may then also run with no member online). */
    boolean hasFuel(UUID islandId);

    /** True while the island is Asleep and the fuel gate pauses production (spawners stop, crops don't grow). */
    boolean paused(UUID islandId);

    /**
     * Adds {@code minutes} of fuel (AFK pool, staff): one conditional, capped database update with an audit row. A full
     * tank refuses (completes with false, nothing is used up); otherwise as much as fits is added and it completes with
     * true.
     */
    CompletableFuture<Boolean> addFuel(UUID islandId, long minutes, String reason, Actor actor);
}
