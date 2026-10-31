package net.skybornmc.skyblock.rewards;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * One reward of the daily calendar (later also votes and streaks), written in {@code daily.yml} as one short line:
 * <pre>
 *   money &lt;minutes&gt;                     minutes of reference income at your tier (ECONOMY_BALANCE §1.2)
 *   crystals &lt;base&gt;                     Crystal Pouch: base x growth^(tier-1)
 *   credits &lt;amount&gt;   (earned credits; the old word gems still works)
 *   key &lt;crate&gt; [amount]                bound crate keys (CrateKeys)
 *   fuel &lt;minutes&gt;                      Island Fuel for your island
 *   booster &lt;type&gt; &lt;percent&gt; &lt;minutes&gt;  a bound personal booster item (BoosterService)
 *   book &lt;rarity&gt; [level]               a random enchant book of that rarity (EnchantService)
 *   scroll [amount]                      Disenchant Scrolls
 *   tome                                 Wardrobe Tome (not built yet: pays its fallback)
 * </pre>
 * Any line may end with {@code fallback <crystals>}: the crystal base (scaled by tier like a pouch) paid instead when the
 * system behind the reward is not there (no islands fuel, no boosters module, tomes not built ...). Without it the
 * settings' default fallback is used.
 *
 * @param ref      crate id, booster type or book rarity (empty otherwise)
 * @param amount   minutes (money, fuel), base (crystals), amount (credits, key, scroll), percent (booster), level (book)
 * @param minutes  booster length in minutes (0 otherwise)
 * @param fallback crystal base when the reward can't be given; -1 = the default
 */
record RewardSpec(Kind kind, String ref, long amount, int minutes, long fallback) {

    /** What a reward gives. {@link #currency()} rewards are paid inside the claim's transaction. */
    enum Kind {
        MONEY("money", true),
        CRYSTALS("crystals", true),
        /** Earned credits (C1; config word {@code credits}, old word {@code gems}). */
        CREDITS("credits", true),
        KEY("key", false),
        FUEL("fuel", false),
        BOOSTER("booster", false),
        BOOK("book", false),
        SCROLL("scroll", false),
        TOME("tome", false);

        private final String key;
        private final boolean currency;

        Kind(String key, boolean currency) {
            this.key = key;
            this.currency = currency;
        }

        String key() {
            return key;
        }

        boolean currency() {
            return currency;
        }

        static Optional<Kind> byKey(String raw) {
            String k = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
            if (k.equals("gems")) {
                return Optional.of(CREDITS); // reward lines written before gems were renamed credits (C1-R1-03)
            }
            for (Kind kind : values()) {
                if (kind.key.equals(k)) {
                    return Optional.of(kind);
                }
            }
            return Optional.empty();
        }
    }

    /** Most of one amount a line may hold (a typo can't flood a profile). */
    static final long MAX_AMOUNT = 1_000_000;

    RewardSpec {
        ref = ref == null ? "" : ref;
    }

    /** Parses one reward line; problems go to {@code problems} and give an empty result. */
    static Optional<RewardSpec> parse(String raw, Consumer<String> problems) {
        String line = raw == null ? "" : raw.trim();
        if (line.isEmpty()) {
            problems.accept("empty reward");
            return Optional.empty();
        }
        List<String> words = new ArrayList<>(List.of(line.toLowerCase(Locale.ROOT).split("\\s+")));
        long fallback = -1;
        int fb = words.indexOf("fallback");
        if (fb >= 0) {
            if (fb != words.size() - 2) {
                problems.accept("'" + line + "': 'fallback' must be followed by one number at the end");
                return Optional.empty();
            }
            Long v = number(words.get(fb + 1));
            if (v == null) {
                problems.accept("'" + line + "': fallback '" + words.get(fb + 1) + "' is not a whole number >= 0");
                return Optional.empty();
            }
            fallback = v;
            words = words.subList(0, fb);
        }
        Optional<Kind> kind = Kind.byKey(words.getFirst());
        if (kind.isEmpty()) {
            problems.accept("'" + line + "': unknown reward type '" + words.getFirst() + "'");
            return Optional.empty();
        }
        List<String> args = words.subList(1, words.size());
        try {
            RewardSpec spec = switch (kind.get()) {
                case MONEY, CRYSTALS, CREDITS, FUEL -> new RewardSpec(kind.get(), "", positive(one(args, line), line), 0, fallback);
                case KEY -> {
                    if (args.isEmpty() || args.size() > 2 || !args.getFirst().matches("[a-z0-9_-]{1,32}")) {
                        throw new IllegalArgumentException("'" + line + "': write 'key <crate> [amount]'");
                    }
                    yield new RewardSpec(Kind.KEY, args.getFirst(), args.size() == 2 ? positive(args.get(1), line) : 1, 0, fallback);
                }
                case BOOSTER -> {
                    if (args.size() != 3 || !args.getFirst().matches("[a-z0-9_]{1,16}")) {
                        throw new IllegalArgumentException("'" + line + "': write 'booster <type> <percent> <minutes>'");
                    }
                    long percent = positive(args.get(1), line);
                    long minutes = positive(args.get(2), line);
                    if (minutes > Integer.MAX_VALUE) {
                        throw new IllegalArgumentException("'" + line + "': minutes too large");
                    }
                    yield new RewardSpec(Kind.BOOSTER, args.getFirst(), percent, (int) minutes, fallback);
                }
                case BOOK -> {
                    if (args.isEmpty() || args.size() > 2 || !args.getFirst().matches("[a-z]{1,16}")) {
                        throw new IllegalArgumentException("'" + line + "': write 'book <rarity> [level]'");
                    }
                    yield new RewardSpec(Kind.BOOK, args.getFirst(), args.size() == 2 ? positive(args.get(1), line) : 1, 0, fallback);
                }
                case SCROLL -> {
                    if (args.size() > 1) {
                        throw new IllegalArgumentException("'" + line + "': write 'scroll [amount]'");
                    }
                    yield new RewardSpec(Kind.SCROLL, "", args.isEmpty() ? 1 : positive(args.getFirst(), line), 0, fallback);
                }
                case TOME -> {
                    if (!args.isEmpty()) {
                        throw new IllegalArgumentException("'" + line + "': write 'tome'");
                    }
                    yield new RewardSpec(Kind.TOME, "", 1, 0, fallback);
                }
            };
            return Optional.of(spec);
        } catch (IllegalArgumentException e) {
            problems.accept(e.getMessage());
            return Optional.empty();
        }
    }

    private static String one(List<String> args, String line) {
        if (args.size() != 1) {
            throw new IllegalArgumentException("'" + line + "': needs exactly one number");
        }
        return args.getFirst();
    }

    private static long positive(String raw, String line) {
        Long v = number(raw);
        if (v == null || v < 1 || v > MAX_AMOUNT) {
            throw new IllegalArgumentException("'" + line + "': '" + raw + "' must be a whole number from 1 to " + MAX_AMOUNT);
        }
        return v;
    }

    /** A whole number ≥ 0 ({@code _} and {@code ,} allowed as separators), or null. */
    static Long number(String raw) {
        try {
            long v = Long.parseLong(raw.trim().replace("_", "").replace(",", ""));
            return v < 0 ? null : v;
        } catch (NumberFormatException | NullPointerException e) {
            return null;
        }
    }
}
