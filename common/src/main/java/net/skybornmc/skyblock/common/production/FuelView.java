package net.skybornmc.skyblock.common.production;

/**
 * A snapshot of one island's Island Fuel (PHASE-7 M7.1). Never changes; ask again for fresh values.
 *
 * @param state             Active, Fueled or Asleep
 * @param fuelMs            fuel in the tank, milliseconds
 * @param capacityMs        tank size, milliseconds (start size + Fuel Tank upgrade)
 * @param tankLevel         the Fuel Tank upgrade level (0 = none)
 * @param burnRate          fuel burned per real minute while Fueled (1.0 = one minute, lower with Fuel Efficiency)
 * @param efficiencyPercent the Fuel Efficiency family after its cap ("fuel lasts +N%")
 * @param asleepSince       epoch millis the island fell asleep, 0 when it is not asleep (or never had fuel)
 */
public record FuelView(FuelState state, long fuelMs, long capacityMs, int tankLevel, double burnRate, double efficiencyPercent,
                       long asleepSince) {
}
