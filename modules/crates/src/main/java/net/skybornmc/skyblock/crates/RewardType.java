package net.skybornmc.skyblock.crates;

import java.util.Locale;
import java.util.Optional;

/**
 * What a crate reward gives ({@code type:} of an entry in {@code crates.yml}). Rewards whose system does not exist yet
 * (tomes, skins, sell wands, collectors, pet eggs ...) are {@link #COMING_SOON}: they keep their chance in the table and
 * pay their crystal value ({@code fallback}) until the system is built, so the odds never change.
 */
enum RewardType {
    /** Money pouch: {@code minutes} of reference income at the opener's tier (ECONOMY_BALANCE §1.2). In the transaction. */
    MONEY("money", true),
    /** Crystal pouch: {@code base} x growth^(tier-1). In the transaction. */
    CRYSTALS("crystals", true),
    /** Shards ({@code amount}). In the transaction. */
    SHARDS("shards", true),
    /** Keys of another crate ({@code crate}, {@code amount}). In the transaction. */
    KEY("key", true),
    /** A random enchant book of a rarity ({@code pool}, {@code level}). Item, handed out after the commit. */
    ENCHANT_BOOK("enchant-book", false),
    /** A Disenchant Scroll. Item. */
    SCROLL("scroll", false),
    /** A Set Token of one of {@code sets}. Item. */
    SET_TOKEN("set-token", false),
    /** Spawners of a random type of {@code tier} ({@code amount}). Item. */
    SPAWNER("spawner", false),
    /** A plain item ({@code material}, {@code amount}), e.g. an elytra. Item. */
    ITEM("item", false),
    /** A tag from {@code ids} the opener does not own yet (duplicate re-roll). Account-wide unlock after the commit. */
    TAG("tag", false),
    /** A title from {@code ids}, like {@link #TAG}. */
    TITLE("title", false),
    /** Island Fuel for the opener's island ({@code minutes}). After the commit. */
    FUEL("fuel", false),
    /**
     * A personal booster item (M10.2: {@code booster} type, {@code percent}, {@code minutes}, {@code amount}); always bound
     * to the opener's profile (RX39). Item, handed out after the commit.
     */
    BOOSTER("booster", false),
    /** A global booster item (M10.2), like {@link #BOOSTER}; bound when opened with a bound key. Item. */
    GLOBAL_BOOSTER("global-booster", false),
    /** Not built yet: pays the fallback crystals. In the transaction. */
    COMING_SOON("coming-soon", true);

    private final String key;
    private final boolean inTransaction;

    RewardType(String key, boolean inTransaction) {
        this.key = key;
        this.inTransaction = inTransaction;
    }

    String key() {
        return key;
    }

    /** True for rewards written in the open's database transaction (currencies, keys); false for hand-outs after it. */
    boolean inTransaction() {
        return inTransaction;
    }

    boolean cosmetic() {
        return this == TAG || this == TITLE;
    }

    static Optional<RewardType> byKey(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String k = raw.trim().toLowerCase(Locale.ROOT).replace('_', '-');
        for (RewardType t : values()) {
            if (t.key.equals(k)) {
                return Optional.of(t);
            }
        }
        return Optional.empty();
    }
}
