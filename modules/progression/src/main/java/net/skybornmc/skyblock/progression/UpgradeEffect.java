package net.skybornmc.skyblock.progression;

import java.util.Locale;
import java.util.Optional;

/**
 * What an island upgrade does (PHASE-5 M5.4), the {@code effect} of an {@code upgrades.yml} entry. The value of a level is
 * the effect at that level (never added up). {@link #live()} effects are wired to an existing feature; the others show
 * "Coming soon" and can't be bought until their feature exists (the config entry stays as the hook).
 */
enum UpgradeEffect {
    /** Border width in chunks (odd), through {@code IslandService#setSize}. */
    ISLAND_SIZE("island-size", true),
    /** Member slots with the leader, through {@code IslandService#setMemberSlots}. */
    MEMBER_SLOTS("member-slots", true),
    /** Warps, through {@code IslandService#setWarpSlots}. */
    WARP_SLOTS("warp-slots", true),
    /** Island Vault rows (I7): the island storage does not exist yet. */
    ISLAND_VAULT("island-vault", false),
    /** +% island entity limits (stacking module). */
    ENTITY_LIMITS("entity-limits", true),
    /** Bank Capacity level (economy module, bank.yml table). */
    BANK_CAPACITY("bank-capacity", true),
    /** Bank Interest level (economy module, bank.yml table). */
    BANK_INTEREST("bank-interest", true),
    /** +% sell prices for members (BonusService SELL family). */
    SELL_BONUS("sell-bonus", true),
    /**
     * +% extra drops in the Depths mine for members (EXTRA_DROPS, activity mining). Not live yet: the Depths drop roll
     * uses only the tool's own fortune while the tools module is loaded (ToolService#dropMultiplier), so the upgrade
     * would do nothing. The bonus source already answers it; switch to live once the roll asks with the player.
     */
    MINE_FORTUNE("mine-fortune", false),
    /** +% extra logs in the Depths forest for members (EXTRA_DROPS, activity foraging). Not live yet: see MINE_FORTUNE. */
    LUMBERJACK("lumberjack", false),
    /** +% skill XP for members (SKILL_XP family). Not live yet: the skills module does not read SKILL_XP for skill gains. */
    XP_BOOST("xp-boost", false),
    /** Spawners per stacked spawner block (stacking module). */
    SPAWNER_STACK("spawner-stack", true),
    /** +% spawner output (stacking module, spawner rate multiplier). */
    SPAWNER_RATE("spawner-rate", true),
    /** +% spawner drops: no hook in the stacking module yet. */
    MOB_LOOT("mob-loot", false),
    /** +% crop growth on the island: no island crop growth hook yet. */
    CROP_GROWTH("crop-growth", false),
    /** +% double-crop chance: no hook yet. */
    HARVEST_YIELD("harvest-yield", false),
    /** Generator ore tier (Phase 7). */
    GENERATOR("generator", false),
    /** Island Fuel tank size (Phase 7): live while Island Fuel is on ({@link IslandFuelFeature}). */
    FUEL_TANK("fuel-tank", false);

    private final String id;
    private final boolean live;

    UpgradeEffect(String id, boolean live) {
        this.id = id;
        this.live = live;
    }

    /** Key in {@code upgrades.yml} and the message keys ({@code upgrades.effect.<id>}). */
    String id() {
        return id;
    }

    /** True when the feature behind it exists (otherwise "Coming soon", never bought). */
    boolean live() {
        return live || (this == FUEL_TANK && IslandFuelFeature.on());
    }

    /** Effects stored by the islands module (raised through its setters, never lowered). */
    boolean stored() {
        return this == ISLAND_SIZE || this == MEMBER_SLOTS || this == WARP_SLOTS;
    }

    static Optional<UpgradeEffect> byId(String id) {
        if (id == null) {
            return Optional.empty();
        }
        String key = id.trim().toLowerCase(Locale.ROOT).replace('_', '-');
        for (UpgradeEffect e : values()) {
            if (e.id.equals(key)) {
                return Optional.of(e);
            }
        }
        return Optional.empty();
    }
}
