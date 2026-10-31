package net.skybornmc.skyblock.blackmarket;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * The Black Market offers from {@code blackmarket.yml → offers} (PHASE-10-REWARDS.md M10.11 table). One line per offer:
 * <pre>{@code <id>: slot=<headline|rare|common|fixed> price=<shards> weight=<n> reward=<kind>[:<ref>] [amount=<n>]
 *     [level=<n>] [limit=<n>/<rotation|day|month|season>] [icon=<item>]}</pre>
 * Pure (no Bukkit), unit-tested. A broken line is reported and skipped; the rest still loads.
 */
final class OfferCatalog {

    /** Where an offer shows: the rotating rows (picked by weight each rotation) or the fixed row (always in stock). */
    enum Slot {
        HEADLINE, RARE, COMMON, FIXED;

        String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        static Optional<Slot> of(String raw) {
            for (Slot s : values()) {
                if (s.id().equalsIgnoreCase(raw)) {
                    return Optional.of(s);
                }
            }
            return Optional.empty();
        }
    }

    /** What a buy gives. {@link #COMING_SOON} offers are listed but never rolled until their system exists. */
    enum Kind {
        /** A Set Token of the wardrobe set {@code ref}. Item. */
        SET_TOKEN("set-token", true),
        /** A random enchant book of the rarity {@code ref} (chosen at the buy), at {@code level}. Item. */
        ENCHANT_BOOK("enchant-book", true),
        /** A Disenchant Scroll. Item. */
        SCROLL("scroll", false),
        /** Keys of the crate {@code ref} (bound: the source {@code blackmarket} is not a tradeable key source). */
        KEY("key", true),
        /** A spawner of a random type of tier {@code ref} (1-5, chosen at the buy). Item. */
        SPAWNER("spawner", true),
        /** A plain vanilla item {@code ref} (e.g. elytra). Item. */
        ITEM("item", true),
        /** The tag {@code ref} (account-wide). Can't be bought twice. */
        TAG("tag", true),
        /** Not built yet (collectors, auto-sell chests, blueprints, sell wands, tomes): shown in the config only. */
        COMING_SOON("coming-soon", false);

        private final String key;
        private final boolean needsRef;

        Kind(String key, boolean needsRef) {
            this.key = key;
            this.needsRef = needsRef;
        }

        String key() {
            return key;
        }

        boolean needsRef() {
            return needsRef;
        }

        /** True for rewards handed out as items (inventory first, the rest to /box). */
        boolean item() {
            return this == SET_TOKEN || this == ENCHANT_BOOK || this == SCROLL || this == SPAWNER || this == ITEM;
        }

        static Optional<Kind> of(String raw) {
            String k = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT).replace('_', '-');
            for (Kind kind : values()) {
                if (kind.key.equals(k)) {
                    return Optional.of(kind);
                }
            }
            return Optional.empty();
        }
    }

    /** How long a per-account limit counts. */
    enum Period {
        ROTATION("r"), DAY("d"), MONTH("m"), SEASON("s");

        private final String prefix;

        Period(String prefix) {
            this.prefix = prefix;
        }

        String prefix() {
            return prefix;
        }

        String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        static Optional<Period> of(String raw) {
            for (Period p : values()) {
                if (p.id().equalsIgnoreCase(raw == null ? "" : raw.trim())) {
                    return Optional.of(p);
                }
            }
            return Optional.empty();
        }
    }

    /**
     * One offer.
     *
     * @param price  shards per buy (one buy gives {@code amount} of the reward)
     * @param weight chance weight inside its slot group (fixed offers ignore it)
     * @param limit  buys per account per {@code period}; 0 = no limit
     * @param icon   vanilla item id of the menu icon, or null for the kind's default
     */
    record Offer(String id, Slot slot, long price, double weight, Kind kind, String ref, int amount, int level, int limit, Period period,
                 String icon) {

        boolean rollable() {
            return slot != Slot.FIXED && kind != Kind.COMING_SOON && weight > 0;
        }
    }

    static final OfferCatalog EMPTY = new OfferCatalog(List.of());
    static final Pattern ID = Pattern.compile("[a-z0-9_]{1,40}");
    static final int MAX_AMOUNT = 64;

    private final List<Offer> offers;
    private final Map<String, Offer> byId;

    OfferCatalog(List<Offer> offers) {
        this.offers = List.copyOf(offers);
        Map<String, Offer> map = new LinkedHashMap<>();
        for (Offer o : this.offers) {
            map.put(o.id(), o);
        }
        this.byId = Collections.unmodifiableMap(map);
    }

    List<Offer> all() {
        return offers;
    }

    Optional<Offer> get(String id) {
        return id == null ? Optional.empty() : Optional.ofNullable(byId.get(id.toLowerCase(Locale.ROOT)));
    }

    List<Offer> of(Slot slot) {
        return offers.stream().filter(o -> o.slot() == slot).toList();
    }

    /** Ids of offers that can really be handed out (staff give, tab completion). */
    List<String> liveIds() {
        return offers.stream().filter(o -> o.kind() != Kind.COMING_SOON).map(Offer::id).toList();
    }

    /** Parses the config lines; every problem is reported once and its line skipped. */
    static OfferCatalog parse(List<String> lines, Consumer<String> problems) {
        List<Offer> out = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (String line : lines == null ? List.<String>of() : lines) {
            try {
                Offer o = parseLine(line);
                if (!seen.add(o.id())) {
                    problems.accept("blackmarket.yml offers: '" + o.id() + "' is listed twice; the second one is skipped.");
                    continue;
                }
                out.add(o);
            } catch (IllegalArgumentException e) {
                problems.accept("blackmarket.yml offers: '" + line + "' skipped: " + e.getMessage());
            }
        }
        return new OfferCatalog(out);
    }

    /** One line; throws {@link IllegalArgumentException} with a short reason. */
    static Offer parseLine(String line) {
        if (line == null || line.isBlank()) {
            throw new IllegalArgumentException("empty line");
        }
        int colon = line.indexOf(':');
        if (colon <= 0) {
            throw new IllegalArgumentException("needs '<id>: key=value ...'");
        }
        String id = line.substring(0, colon).trim().toLowerCase(Locale.ROOT);
        if (!ID.matcher(id).matches()) {
            throw new IllegalArgumentException("the id must be a-z, 0-9 and _ (at most 40)");
        }
        Map<String, String> values = new LinkedHashMap<>();
        for (String part : line.substring(colon + 1).trim().split("\\s+")) {
            if (part.isEmpty()) {
                continue;
            }
            int eq = part.indexOf('=');
            if (eq <= 0 || eq == part.length() - 1) {
                throw new IllegalArgumentException("'" + part + "' is not key=value");
            }
            values.put(part.substring(0, eq).toLowerCase(Locale.ROOT), part.substring(eq + 1));
        }
        Slot slot = Slot.of(required(values, "slot")).orElseThrow(() -> new IllegalArgumentException("slot must be headline, rare, common or fixed"));
        long price = number(values.getOrDefault("price", ""), "price", 1, 1_000_000_000L);
        double weight = values.containsKey("weight") ? decimal(values.get("weight")) : 1;
        String reward = required(values, "reward");
        int sep = reward.indexOf(':');
        Kind kind = Kind.of(sep < 0 ? reward : reward.substring(0, sep))
                .orElseThrow(() -> new IllegalArgumentException("unknown reward '" + reward + "'"));
        String ref = sep < 0 ? "" : reward.substring(sep + 1).trim().toLowerCase(Locale.ROOT);
        if (kind.needsRef() && ref.isEmpty()) {
            throw new IllegalArgumentException("reward " + kind.key() + " needs ':<value>'");
        }
        if (kind == Kind.SPAWNER) {
            number(ref, "spawner tier", 1, 5);
        }
        int amount = (int) number(values.getOrDefault("amount", "1"), "amount", 1, MAX_AMOUNT);
        int level = (int) number(values.getOrDefault("level", "1"), "level", 1, 100);
        int limit = 0;
        Period period = Period.ROTATION;
        String rawLimit = values.get("limit");
        if (rawLimit != null) {
            int slash = rawLimit.indexOf('/');
            limit = (int) number(slash < 0 ? rawLimit : rawLimit.substring(0, slash), "limit", 0, 10_000);
            if (slash >= 0) {
                String p = rawLimit.substring(slash + 1);
                period = Period.of(p).orElseThrow(() -> new IllegalArgumentException("limit period must be rotation, day, month or season"));
            }
        }
        if (kind == Kind.TAG && amount != 1) {
            throw new IllegalArgumentException("a tag is one per account (amount must be 1)");
        }
        String icon = values.get("icon");
        return new Offer(id, slot, price, weight, kind, ref, amount, level, limit, period, icon == null ? null : icon.toLowerCase(Locale.ROOT));
    }

    private static String required(Map<String, String> values, String key) {
        String v = values.get(key);
        if (v == null || v.isBlank()) {
            throw new IllegalArgumentException("'" + key + "=' is missing");
        }
        return v;
    }

    private static long number(String raw, String what, long min, long max) {
        try {
            long v = Long.parseLong(raw.trim().replace("_", ""));
            if (v < min || v > max) {
                throw new IllegalArgumentException(what + " must be " + min + " to " + max);
            }
            return v;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(what + " must be a whole number");
        }
    }

    private static double decimal(String raw) {
        try {
            double v = Double.parseDouble(raw.trim());
            if (!(v >= 0) || v > 1_000_000) {
                throw new IllegalArgumentException("weight must be 0 to 1000000");
            }
            return v;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("weight must be a number");
        }
    }
}
