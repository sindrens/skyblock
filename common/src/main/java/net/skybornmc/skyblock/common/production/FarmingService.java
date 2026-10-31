package net.skybornmc.skyblock.common.production;

import net.skybornmc.skyblock.common.service.ServiceRegistry;
import org.bukkit.Material;

import java.util.List;
import java.util.Optional;

/**
 * Island farming (PHASE-7 M7.3), published by {@code modules/production} only while farming is switched on
 * ({@code crops.yml → enabled}). Other modules treat "this service is registered" as "farming exists": the Farming skill
 * gives XP and shows in {@code /skills}, the Crop Growth and Harvest Yield upgrades can be bought, harvest quests are
 * offered and the wardrobe reads the crop tiers for Cropwarden. Main thread.
 */
public interface FarmingService {

    /** The published service, if farming is on. */
    static Optional<FarmingService> of(ServiceRegistry services) {
        return services.optional(FarmingService.class);
    }

    /** Farming levels that open crop tiers 1, 2, 3 … (index 0 = tier 1, usually level 0), ascending. */
    List<Integer> tierLevels();

    /**
     * Farming level needed to plant this block (the block a seed places: {@code carrots}, {@code melon_stem},
     * {@code torchflower_crop} …); 0 for blocks that are not a crop or have no gate.
     */
    int plantLevel(Material plantBlock);
}
