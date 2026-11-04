package net.skybornmc.skyblock.common.profile;

import net.skybornmc.skyblock.common.economy.Currency;

import java.util.Arrays;
import java.util.List;

/**
 * What a profile wipe takes and what it keeps (owner 4 Nov 2026, "island = profile"; pure, unit-tested).
 *
 * <h2>How the wipe works</h2>
 * A wipe never edits a module's rows. The wiped profile's key is <b>retired</b> (marked deleted, its data stays untouched
 * as the 14-day snapshot) and the slot gets a <b>fresh profile key</b>. Everything stored under the profile key is
 * therefore empty on the fresh profile in every module at once, also in modules added later; everything stored under the
 * player UUID (account) stays. A staff restore puts the old key back; after the retention the snapshot is purged and the
 * normal {@link ProfileDeletedEvent} lets every module delete the old key's rows.
 *
 * <p>Account data that a module stores per profile but the owner wants kept (achievements) is carried to the fresh key by
 * that module in its {@link ProfileWipedEvent} listener, and back in its {@link ProfileRestoredEvent} listener.</p>
 */
public final class WipeScope {

    /** One kind of player data, with whether a wipe keeps it. */
    public enum Part {
        MONEY(false), CRYSTALS(false), SHARDS(false), PERSONAL_BANK(false),
        INVENTORY(false), ARMOR(false), OFFHAND(false), ENDER_CHEST(false), XP(false), HEALTH_FOOD_EFFECTS(false),
        ISLAND_MEMBERSHIP(false), ISLAND_HOMES(false), ISLAND_TOOLS(false), TOOLS(false), SKILLS(false), QUESTS(false),
        TASKS(false), COLLECTION(false), MILESTONES(false), STORAGES(false), WARDROBE(false), CRATE_KEYS(false),
        PERSONAL_BOOSTERS(false), AUCTION_LISTINGS(false), MONEY_NOTES(false), PROFILE_SETTINGS(false), PROFILE_PLAYTIME(false),
        CREDITS(true), RANKS(true), PERMISSIONS(true), STORE_PURCHASES(true), ACHIEVEMENTS(true), COSMETICS(true), TAGS_TITLES(true),
        NICKNAME(true), ACCOUNT_PLAYTIME(true), PUNISHMENTS(true), PROFILE_SLOTS(true);

        private final boolean kept;

        Part(boolean kept) {
            this.kept = kept;
        }

        /** True when a wipe keeps it. */
        public boolean kept() {
            return kept;
        }
    }

    private WipeScope() {
    }

    /** True when a wipe keeps this part. */
    public static boolean kept(Part part) {
        return part != null && part.kept();
    }

    /** Every part a wipe keeps, in declaration order. */
    public static List<Part> keptParts() {
        return Arrays.stream(Part.values()).filter(Part::kept).toList();
    }

    /** Every part a wipe takes, in declaration order. */
    public static List<Part> wipedParts() {
        return Arrays.stream(Part.values()).filter(p -> !p.kept()).toList();
    }

    /** True when a wipe takes this currency: everything except the credits (they belong to the account). */
    public static boolean wipes(Currency currency) {
        return currency != null && !currency.accountWide();
    }
}
