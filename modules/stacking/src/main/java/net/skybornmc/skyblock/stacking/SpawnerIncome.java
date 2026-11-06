package net.skybornmc.skyblock.stacking;

/**
 * ECON-SPAWNERS (pure): income of one full spawner per production hour = spawns per hour x mobs per spawn x drops per mob x
 * price. At the defaults: 360 x 1 x 1 x price (ECON-TABLES B: $5 drop = $1,800 base, $1,530 at the 0.85 dynamic factor).
 */
final class SpawnerIncome {

    private SpawnerIncome() {
    }

    static double perHour(double secondsPerSpawn, double mobsPerSpawn, double dropsPerMob, double price) {
        if (secondsPerSpawn <= 0) {
            return 0;
        }
        return 3600.0 / secondsPerSpawn * Math.max(0, mobsPerSpawn) * Math.max(0, dropsPerMob) * Math.max(0, price);
    }

    /** Hours until {@code price} is paid back at {@code perHour} (infinite for no income). */
    static double paybackHours(double price, double perHour) {
        return perHour <= 0 ? Double.POSITIVE_INFINITY : price / perHour;
    }
}
