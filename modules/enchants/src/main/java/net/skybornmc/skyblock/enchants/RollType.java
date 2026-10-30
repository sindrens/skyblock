package net.skybornmc.skyblock.enchants;

import net.skybornmc.skyblock.common.item.Rarity;

import java.util.Locale;
import java.util.Map;

/** The three Book shop rolls (ENCHANTS §3.5, EN9). */
enum RollType {
    APPRENTICE,
    ADEPT,
    MASTER;

    String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /**
     * @param price   crystals
     * @param odds    rarity → weight (never Mythic)
     * @param minTier tier needed in the pool's wing
     * @param daily   rolls per player per day
     */
    record Settings(long price, Map<Rarity, Double> odds, int minTier, int daily) {
    }
}
