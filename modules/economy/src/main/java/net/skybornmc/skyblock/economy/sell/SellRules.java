package net.skybornmc.skyblock.economy.sell;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Which items can be sold (M3.4). Pure, unit-tested: the Bukkit side turns an ItemStack into {@link ItemFacts}.
 *
 * <p>Order of the checks (the first that applies wins):</p>
 * <ol>
 *   <li>flagged as a dupe → never;</li>
 *   <li>a staff item ({@code /give}, {@code /i}, creative; owner answer 332) → never;</li>
 *   <li>unique items (a unique item id: custom tools, enchant books, money notes, set pieces) → <b>never</b>, whatever the
 *       price table says;</li>
 *   <li>custom ids in {@code sell.blocked-ids} (starter kit, notes ...) and items with a blocked data key → never;</li>
 *   <li>custom items whose type is not in {@code sell.custom-types} (default: materials only) → never;</li>
 *   <li>not in the price table → can't be sold; in it but {@code sellable: false} → switched off.</li>
 * </ol>
 */
public final class SellRules {

    /** Why an item can't be sold; texts: {@code sell.reason.<key>}. */
    public enum Reason {
        EMPTY, DUPE, STAFF_ITEM, UNIQUE, BLOCKED, CUSTOM_TYPE, NOT_IN_TABLE, DISABLED;

        public String key() {
            return name().toLowerCase(Locale.ROOT).replace('_', '-');
        }
    }

    /**
     * What the rules need to know about one stack.
     *
     * @param material     vanilla item id ({@code coal}), null for an empty stack
     * @param customId     custom item id, null for vanilla items
     * @param customType   the custom item's type id ({@code material}, {@code tool} ...), null if unknown or vanilla
     * @param unique       the stack has a unique item id
     * @param dupeFlagged  the dupe detector flagged it
     * @param blockedData  it carries a data key from {@code sell.blocked-data-keys}
     * @param staffItem    it is (or contains) a staff item (owner answer 332)
     */
    public record ItemFacts(String material, String customId, String customType, boolean unique, boolean dupeFlagged,
                            boolean blockedData, boolean staffItem) {

        public ItemFacts(String material, String customId, String customType, boolean unique, boolean dupeFlagged,
                         boolean blockedData) {
            this(material, customId, customType, unique, dupeFlagged, blockedData, false);
        }

        public static ItemFacts vanilla(String material) {
            return new ItemFacts(material, null, null, false, false, false);
        }

        public static ItemFacts custom(String material, String customId, String customType, boolean unique) {
            return new ItemFacts(material, customId, customType, unique, false, false);
        }

        /** The price table key: {@code custom:<id>} for custom items, the vanilla id otherwise. */
        public String key() {
            return customId != null ? SellCatalog.customKey(customId) : material;
        }
    }

    private SellRules() {
    }

    /** Why this item can't be sold, or empty if it can. */
    public static Optional<Reason> check(ItemFacts facts, SellCatalog catalog, List<String> blockedIds, List<String> customTypes) {
        if (facts == null || facts.material() == null || facts.material().isBlank()) {
            return Optional.of(Reason.EMPTY);
        }
        if (facts.dupeFlagged()) {
            return Optional.of(Reason.DUPE);
        }
        if (facts.staffItem()) {
            return Optional.of(Reason.STAFF_ITEM);
        }
        if (facts.unique()) {
            return Optional.of(Reason.UNIQUE);
        }
        if (facts.blockedData() || (facts.customId() != null && idBlocked(facts.customId(), blockedIds))) {
            return Optional.of(Reason.BLOCKED);
        }
        if (facts.customId() != null && !typeAllowed(facts.customType(), customTypes)) {
            return Optional.of(Reason.CUSTOM_TYPE);
        }
        Optional<SellCatalog.Entry> entry = catalog.get(facts.key());
        if (entry.isEmpty()) {
            return Optional.of(Reason.NOT_IN_TABLE);
        }
        if (!entry.get().sellable()) {
            return Optional.of(Reason.DISABLED);
        }
        return Optional.empty();
    }

    /** {@code starter_*} = any id starting with {@code starter_}; otherwise an exact, case-insensitive match. */
    public static boolean idBlocked(String id, List<String> blocked) {
        if (id == null || blocked == null) {
            return false;
        }
        String lower = id.toLowerCase(Locale.ROOT);
        for (String raw : blocked) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            String b = raw.trim().toLowerCase(Locale.ROOT);
            if (b.endsWith("*") ? lower.startsWith(b.substring(0, b.length() - 1)) : lower.equals(b)) {
                return true;
            }
        }
        return false;
    }

    /** A custom item with no known type is never sold (safest). */
    static boolean typeAllowed(String type, List<String> allowed) {
        if (type == null || allowed == null) {
            return false;
        }
        String t = type.trim().toLowerCase(Locale.ROOT).replace('_', '-');
        return allowed.stream().filter(a -> a != null).map(a -> a.trim().toLowerCase(Locale.ROOT).replace('_', '-'))
                .anyMatch(t::equals);
    }
}
