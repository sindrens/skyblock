package net.skybornmc.skyblock.islandtools;

import net.skybornmc.skyblock.common.economy.Currency;

import java.util.Locale;
import java.util.Optional;

/**
 * The island tools. Owner 4 Nov 2026: only the harvester hoe and the pickaxe (the island axe and sword are gone; old
 * {@code axe}/{@code sword} items and rows are recognised by {@link #legacy(String)} and removed).
 */
enum Kind {
    HOE(Currency.SHARDS),
    PICKAXE(Currency.MINING_SHARDS);

    private final Currency shards;

    Kind(Currency shards) {
        this.shards = shards;
    }

    /** The shard type this tool earns and its upgrades cost: Farming Shards (hoe) or Mining Shards (pickaxe). */
    Currency shards() {
        return shards;
    }

    String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    static Optional<Kind> of(String id) {
        for (Kind k : values()) {
            if (k.id().equalsIgnoreCase(id)) {
                return Optional.of(k);
            }
        }
        return Optional.empty();
    }

    /** True for the removed tool kinds (owner 4 Nov 2026: no island axe and no island sword). */
    static boolean legacy(String id) {
        return "axe".equalsIgnoreCase(id) || "sword".equalsIgnoreCase(id);
    }
}
