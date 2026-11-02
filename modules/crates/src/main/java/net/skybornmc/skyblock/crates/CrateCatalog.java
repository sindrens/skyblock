package net.skybornmc.skyblock.crates;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * The crates and their reward tables from {@code crates.yml} (pure, no server). Every crate is a section under
 * {@code crates:} with a few plain keys and a {@code rewards:} catalog <b>list</b> (one map per reward); the list never
 * holds plain settings. Problems are reported through {@code warn} and the broken entry is skipped; chances are
 * relative weights (the bundled tables add up to 100 per crate, the preview shows each one's real share).
 */
final class CrateCatalog {

    static final String FILE = "crates.yml";
    static final String ROOT = "crates";
    private static final Pattern ID = Pattern.compile("[a-z0-9_]{1,32}");
    static final List<String> RARITIES = List.of("common", "uncommon", "rare", "epic", "legendary", "mythic", "special");
    /** Booster types of booster rewards (common BoosterType ids). */
    static final List<String> BOOSTER_TYPES = java.util.Arrays.stream(net.skybornmc.skyblock.common.booster.BoosterType.values())
            .map(net.skybornmc.skyblock.common.booster.BoosterType::id).toList();

    /**
     * One reward of a crate. Unused parameters of a type are 0 / empty. {@code booster} and {@code percent} are the booster
     * type ({@code sell}, {@code skill_xp}, {@code tool_xp}, {@code crystals}) and strength of booster rewards (M10.2).
     */
    record Reward(String id, RewardType type, double chance, boolean top, String rarity, String icon, int minutes, long base,
                  int amount, String crate, String pool, int level, List<String> sets, int tier, String material,
                  List<String> ids, long fallback, String booster, int percent) {

        Reward {
            sets = List.copyOf(sets);
            ids = List.copyOf(ids);
            booster = booster == null ? "" : booster;
        }

        /** A reward without booster fields. */
        Reward(String id, RewardType type, double chance, boolean top, String rarity, String icon, int minutes, long base,
               int amount, String crate, String pool, int level, List<String> sets, int tier, String material,
               List<String> ids, long fallback) {
            this(id, type, chance, top, rarity, icon, minutes, base, amount, crate, pool, level, sets, tier, material, ids,
                    fallback, "", 0);
        }

        /** The same reward with another type (a key to an unknown crate becomes coming soon). */
        Reward withType(RewardType t) {
            return new Reward(id, t, chance, top, rarity, icon, minutes, base, amount, crate, pool, level, sets, tier, material,
                    ids, fallback, booster, percent);
        }

        /** {@code type:percent:minutes} of a booster reward (the grant's reference). */
        String boosterRef() {
            return booster + ":" + percent + ":" + minutes;
        }
    }

    /** One crate. {@code pity} = opens without a top-band reward after which the next open is forced into it (0 = off). */
    record Crate(String id, String rarity, String icon, int order, int pity, boolean hidden, long fallback, List<Reward> rewards,
                 Map<String, String> bands, Map<String, Integer> draws) {

        /** Bands of a tiered crate, in draw order (Eclipse, owner V1: 9 common, 9 mid, 9 rare, then 1 of 5 jackpots). */
        static final List<String> BAND_ORDER = List.of("common", "mid", "rare", "jackpot");

        Crate {
            rewards = List.copyOf(rewards);
            bands = Map.copyOf(bands);
            draws = Map.copyOf(draws);
        }

        /** A plain weighted crate (one reward per open). */
        Crate(String id, String rarity, String icon, int order, int pity, boolean hidden, long fallback, List<Reward> rewards) {
            this(id, rarity, icon, order, pity, hidden, fallback, rewards, Map.of(), Map.of());
        }

        /** True for a tiered crate: every open gives {@link #perOpen()} rewards, drawn per band (Eclipse). */
        boolean tiered() {
            return !bands.isEmpty();
        }

        /** The band of a reward of a tiered crate, or "". */
        String band(Reward reward) {
            return bands.getOrDefault(reward.id(), "");
        }

        List<Reward> inBand(String band) {
            return rewards.stream().filter(r -> band.equals(band(r))).toList();
        }

        /** Rewards drawn from a band per open (never more than the band holds). */
        int draw(String band) {
            return Math.max(0, Math.min(draws.getOrDefault(band, 0), inBand(band).size()));
        }

        /** Rewards per open: 1, or the sum of the band draws of a tiered crate. */
        int perOpen() {
            if (!tiered()) {
                return 1;
            }
            int n = 0;
            for (String b : BAND_ORDER) {
                n += draw(b);
            }
            return Math.max(1, n);
        }

        double totalChance() {
            return rewards.stream().mapToDouble(Reward::chance).sum();
        }

        /**
         * The real share of a reward in percent (its chance / all chances of the crate). Tiered crates: the chance the
         * reward is among the open's rewards (draw / band size for common, mid and rare, which are drawn evenly without
         * repeats; the jackpot by its weight inside the jackpot band).
         */
        double percent(Reward reward) {
            if (tiered()) {
                String b = band(reward);
                List<Reward> pool = inBand(b);
                if (pool.isEmpty() || draw(b) == 0) {
                    return 0;
                }
                if (b.equals("jackpot")) {
                    double total = pool.stream().mapToDouble(Reward::chance).sum();
                    return total <= 0 ? 0 : reward.chance() * 100.0 / total * draw(b);
                }
                return draw(b) * 100.0 / pool.size();
            }
            double total = totalChance();
            return total <= 0 ? 0 : reward.chance() * 100.0 / total;
        }

        /** The share of the whole top band in percent. */
        double topPercent() {
            double total = totalChance();
            return total <= 0 ? 0 : rewards.stream().filter(Reward::top).mapToDouble(Reward::chance).sum() * 100.0 / total;
        }

        List<Reward> topRewards() {
            return rewards.stream().filter(Reward::top).toList();
        }

        boolean hasTop() {
            return rewards.stream().anyMatch(Reward::top);
        }

        Optional<Reward> reward(String rewardId) {
            return rewards.stream().filter(r -> r.id().equals(rewardId)).findFirst();
        }
    }

    private final Map<String, Crate> crates;

    /** crate id → month ("2026-11") → that month's table (Eclipse, owner M1/M4: the rewards change monthly). */
    private final Map<String, Map<String, Crate>> monthly;
    /** Clock of the month tables (Europe/Oslo by default; tests set a fixed one). */
    static volatile java.time.Clock clock = java.time.Clock.system(java.time.ZoneId.of("Europe/Oslo"));

    private CrateCatalog(Map<String, Crate> crates) {
        this(crates, Map.of());
    }

    private CrateCatalog(Map<String, Crate> crates, Map<String, Map<String, Crate>> monthly) {
        this.crates = Collections.unmodifiableMap(crates);
        this.monthly = Map.copyOf(monthly);
    }

    /** The month key now, e.g. {@code 2026-11}. */
    static String month() {
        return java.time.YearMonth.now(clock).toString();
    }

    /** The month after this one. */
    static String nextMonth() {
        return java.time.YearMonth.now(clock).plusMonths(1).toString();
    }

    /** True when the crate has its own table for {@code month}. */
    boolean hasMonth(String crateId, String month) {
        return monthly.getOrDefault(crateId, Map.of()).containsKey(month);
    }

    /** True when the crate uses month tables at all ({@code months:} in crates.yml). */
    boolean monthlyCrate(String crateId) {
        return monthly.containsKey(crateId);
    }

    static CrateCatalog empty() {
        return new CrateCatalog(new LinkedHashMap<>());
    }

    /** Every crate, in {@code order}. */
    List<Crate> crates() {
        return crates.values().stream().sorted(Comparator.comparingInt(Crate::order).thenComparing(Crate::id)).toList();
    }

    /** Crates players see in {@code /crates} (not hidden), in order. */
    List<Crate> visible() {
        return crates().stream().filter(c -> !c.hidden()).toList();
    }

    /** The crate; a crate with month tables gives this month's table (or its base table when the month has none). */
    Optional<Crate> crate(String id) {
        if (id == null) {
            return Optional.empty();
        }
        String key = id.trim().toLowerCase(Locale.ROOT);
        Crate month = monthly.getOrDefault(key, Map.of()).get(month());
        return Optional.ofNullable(month != null ? month : crates.get(key));
    }

    List<String> ids() {
        return crates().stream().map(Crate::id).toList();
    }

    int size() {
        return crates.size();
    }

    // ------------------------------------------------------------------ parsing

    static CrateCatalog load(YamlConfiguration yaml, Consumer<String> warn) {
        Map<String, Crate> out = new LinkedHashMap<>();
        ConfigurationSection root = yaml.getConfigurationSection(ROOT);
        if (root == null) {
            warn.accept("crates.yml: no 'crates:' section, no crates loaded");
            return new CrateCatalog(out);
        }
        for (String rawId : root.getKeys(false)) {
            String id = rawId.toLowerCase(Locale.ROOT);
            ConfigurationSection s = root.getConfigurationSection(rawId);
            if (s == null || !ID.matcher(id).matches()) {
                warn.accept("crates.yml → crates." + rawId + ": not a crate section or a bad id (lowercase letters, digits, _), skipped");
                continue;
            }
            String rarity = rarity(s.getString("rarity", "common"), "crates." + id + ".rarity", warn);
            long fallback = Math.max(0, s.getLong("fallback-crystals", 0));
            List<Reward> rewards = new ArrayList<>();
            List<Map<?, ?>> list = s.getMapList("rewards");
            for (int i = 0; i < list.size(); i++) {
                String where = "crates.yml → crates." + id + ".rewards[" + i + "]";
                parseReward(list.get(i), rarity, fallback, where, warn).ifPresent(r -> {
                    if (rewards.stream().anyMatch(o -> o.id().equals(r.id()))) {
                        warn.accept(where + ": duplicate reward id '" + r.id() + "', skipped");
                    } else {
                        rewards.add(r);
                    }
                });
            }
            if (rewards.isEmpty()) {
                warn.accept("crates.yml → crates." + id + ": no valid rewards, the crate is skipped");
                continue;
            }
            int pity = Math.max(0, s.getInt("pity", 0));
            // Tiered crates (Eclipse, owner V1/V2): rewards carry a band, the crate a draw per band, and there is no pity.
            Map<String, String> bands = new LinkedHashMap<>();
            for (Map<?, ?> m : list) {
                Object rid = m.get("id");
                Object band = m.get("band");
                if (rid != null && band != null) {
                    String b = String.valueOf(band).trim().toLowerCase(Locale.ROOT);
                    if (Crate.BAND_ORDER.contains(b)) {
                        bands.put(String.valueOf(rid).toLowerCase(Locale.ROOT), b);
                    } else {
                        warn.accept("crates.yml → crates." + id + ": reward '" + rid + "' has unknown band '" + band
                                + "' (use " + Crate.BAND_ORDER + "), not drawn");
                    }
                }
            }
            bands.keySet().retainAll(rewards.stream().map(Reward::id).toList());
            Map<String, Integer> draws = new LinkedHashMap<>();
            if (!bands.isEmpty()) {
                ConfigurationSection d = s.getConfigurationSection("draw");
                for (String b : Crate.BAND_ORDER) {
                    draws.put(b, Math.max(0, d == null ? (b.equals("jackpot") ? 1 : 3) : d.getInt(b, 0)));
                }
                if (pity > 0) {
                    warn.accept("crates.yml → crates." + id + ": a tiered crate has no pity (owner V2), pity ignored");
                    pity = 0;
                }
                List<Reward> unbanded = rewards.stream().filter(r -> !bands.containsKey(r.id())).toList();
                if (!unbanded.isEmpty()) {
                    warn.accept("crates.yml → crates." + id + ": rewards without a band are never drawn in a tiered crate: "
                            + unbanded.stream().map(Reward::id).toList());
                }
            }
            out.put(id, new Crate(id, rarity, s.getString("icon", "chest"), s.getInt("order", out.size() + 1), pity,
                    s.getBoolean("hidden", false), fallback, rewards, bands, draws));
        }
        // Keys must name a crate that exists; otherwise the reward pays its crystals (never a key to nothing).
        Map<String, Crate> checked = new LinkedHashMap<>();
        for (Crate c : out.values()) {
            List<Reward> fixed = new ArrayList<>();
            for (Reward r : c.rewards()) {
                if (r.type() == RewardType.KEY && !out.containsKey(r.crate())) {
                    warn.accept("crates.yml → crates." + c.id() + ".rewards." + r.id() + ": key of unknown crate '" + r.crate()
                            + "', pays its crystals instead");
                    fixed.add(r.withType(RewardType.COMING_SOON));
                } else {
                    fixed.add(r);
                }
            }
            if (c.pity() > 0 && !c.hasTop()) {
                warn.accept("crates.yml → crates." + c.id() + ": pity is set but no reward has 'top: true' (pity is off)");
            }
            if (!c.tiered() && Math.abs(c.totalChance() - 100.0) > 0.001) {
                warn.accept("crates.yml → crates." + c.id() + ": chances add up to " + c.totalChance()
                        + " instead of 100 (they are used as weights; the preview shows the real share)");
            }
            checked.put(c.id(), new Crate(c.id(), c.rarity(), c.icon(), c.order(), c.pity(), c.hidden(), c.fallback(), fixed,
                    c.bands(), c.draws()));
        }
        return new CrateCatalog(checked, months(yaml, root, checked, warn));
    }

    /**
     * Month tables: {@code crates.<id>.months.<yyyy-MM>.rewards} (and optionally {@code draw}) replace the crate's rewards in
     * that month; everything else comes from the crate. Each month is parsed like a crate of its own, next to the other
     * crates (so keys to them stay valid).
     */
    private static Map<String, Map<String, Crate>> months(YamlConfiguration yaml, ConfigurationSection root, Map<String, Crate> base,
                                                          Consumer<String> warn) {
        Map<String, Map<String, Crate>> out = new LinkedHashMap<>();
        for (String rawId : root.getKeys(false)) {
            String id = rawId.toLowerCase(Locale.ROOT);
            ConfigurationSection months = root.getConfigurationSection(rawId + ".months");
            if (months == null || !base.containsKey(id)) {
                continue;
            }
            Map<String, Crate> byMonth = new LinkedHashMap<>();
            for (String month : months.getKeys(false)) {
                if (!month.matches("\\d{4}-\\d{2}")) {
                    warn.accept("crates.yml → crates." + id + ".months." + month + ": the month must look like 2026-11, skipped");
                    continue;
                }
                YamlConfiguration tmp = new YamlConfiguration();
                for (String other : root.getKeys(false)) {
                    ConfigurationSection sec = root.getConfigurationSection(other);
                    if (sec == null) {
                        continue;
                    }
                    for (String k : sec.getKeys(false)) {
                        if (!k.equals("months")) {
                            tmp.set(ROOT + "." + other + "." + k, sec.get(k));
                        }
                    }
                }
                ConfigurationSection m = months.getConfigurationSection(month);
                tmp.set(ROOT + "." + rawId + ".rewards", m.getMapList("rewards"));
                if (m.isConfigurationSection("draw")) {
                    tmp.set(ROOT + "." + rawId + ".draw", m.getConfigurationSection("draw").getValues(false));
                }
                String where = "crates.yml → crates." + id + ".months." + month;
                CrateCatalog parsed = load(tmp, w -> {
                    if (w.contains("crates." + id + ".") || w.contains("crates." + id + ":")) {
                        warn.accept(w.replace("crates.yml → crates." + id, where)); // other crates were reported already
                    }
                });
                Crate c = parsed.crates.get(id);
                if (c != null) {
                    byMonth.put(month, c);
                }
            }
            out.put(id, Map.copyOf(byMonth));
        }
        return out;
    }

    private static Optional<Reward> parseReward(Map<?, ?> map, String crateRarity, long crateFallback, String where,
                                                Consumer<String> warn) {
        String id = text(map, "id", "").toLowerCase(Locale.ROOT);
        if (!ID.matcher(id).matches()) {
            warn.accept(where + ": missing or bad 'id' (lowercase letters, digits, _), skipped");
            return Optional.empty();
        }
        String rawType = text(map, "type", "");
        Optional<RewardType> type = RewardType.byKey(rawType);
        if ("shards".equalsIgnoreCase(rawType.trim())) {
            warn.accept(where + " (" + id + "): type 'shards' is not allowed: shards are only found in shard blocks (ECON-SHARDS), skipped");
            return Optional.empty();
        }
        if (type.isEmpty()) {
            warn.accept(where + " (" + id + "): unknown type '" + rawType + "', skipped");
            return Optional.empty();
        }
        double chance = number(map, "chance", 0);
        if (!(chance > 0) || Double.isInfinite(chance)) {
            warn.accept(where + " (" + id + "): 'chance' must be above 0, skipped");
            return Optional.empty();
        }
        RewardType t = type.get();
        int minutes = (int) Math.max(0, number(map, "minutes", 0));
        long base = (long) Math.max(0, number(map, "base", 0));
        int amount = (int) Math.max(1, Math.min(64, number(map, "amount", 1)));
        String crate = text(map, "crate", "").toLowerCase(Locale.ROOT);
        String pool = text(map, "pool", "common").toLowerCase(Locale.ROOT);
        int level = (int) Math.max(1, number(map, "level", 1));
        int tier = (int) Math.max(0, number(map, "tier", 0));
        String material = text(map, "material", "");
        List<String> sets = list(map, "sets");
        List<String> ids = list(map, "ids");
        String booster = text(map, "booster", "").toLowerCase(Locale.ROOT).replace('-', '_');
        int percent = (int) Math.max(0, number(map, "percent", 0));
        String problem = switch (t) {
            case MONEY -> minutes < 1 ? "'minutes' must be at least 1" : null;
            case CRYSTALS -> base < 1 ? "'base' must be at least 1" : null;
            case KEY -> crate.isEmpty() ? "'crate' is missing" : null;
            case ENCHANT_BOOK -> RARITIES.contains(pool) ? null : "'pool' must be a rarity";
            case SET_TOKEN -> sets.isEmpty() ? "'sets' is empty" : null;
            case SPAWNER -> tier < 1 || tier > 5 ? "'tier' must be 1-5" : null;
            case ITEM -> material.isBlank() ? "'material' is missing" : null;
            case FUEL -> minutes < 1 ? "'minutes' must be at least 1" : null;
            case BOOSTER, GLOBAL_BOOSTER -> !BOOSTER_TYPES.contains(booster) ? "'booster' must be one of " + BOOSTER_TYPES
                    : percent < 1 ? "'percent' must be at least 1" : minutes < 1 ? "'minutes' must be at least 1" : null;
            default -> null;
        };
        if (problem != null) {
            warn.accept(where + " (" + id + "): " + problem + ", skipped");
            return Optional.empty();
        }
        String rarity = map.containsKey("rarity") ? rarity(text(map, "rarity", crateRarity), where + ".rarity", warn) : crateRarity;
        long fallback = map.containsKey("fallback") ? (long) Math.max(0, number(map, "fallback", 0)) : crateFallback;
        String icon = map.containsKey("icon") ? text(map, "icon", "") : null;
        return Optional.of(new Reward(id, t, chance, bool(map, "top"), rarity, icon, minutes, base, amount, crate, pool, level,
                sets, tier, material, ids, fallback, booster, percent));
    }

    private static String rarity(String raw, String where, Consumer<String> warn) {
        String r = raw == null ? "common" : raw.trim().toLowerCase(Locale.ROOT);
        if (!RARITIES.contains(r)) {
            warn.accept(where + ": unknown rarity '" + raw + "', using common");
            return "common";
        }
        return r;
    }

    private static String text(Map<?, ?> map, String key, String fallback) {
        Object v = map.get(key);
        return v == null ? fallback : String.valueOf(v).trim();
    }

    private static double number(Map<?, ?> map, String key, double fallback) {
        Object v = map.get(key);
        if (v instanceof Number n) {
            return n.doubleValue();
        }
        if (v == null) {
            return fallback;
        }
        try {
            return Double.parseDouble(String.valueOf(v).trim().replace("_", ""));
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }

    private static boolean bool(Map<?, ?> map, String key) {
        Object v = map.get(key);
        return v instanceof Boolean b ? b : v != null && Boolean.parseBoolean(String.valueOf(v).trim());
    }

    private static List<String> list(Map<?, ?> map, String key) {
        Object v = map.get(key);
        List<String> out = new ArrayList<>();
        if (v instanceof List<?> l) {
            for (Object o : l) {
                if (o != null && !String.valueOf(o).isBlank()) {
                    out.add(String.valueOf(o).trim().toLowerCase(Locale.ROOT));
                }
            }
        } else if (v != null && !String.valueOf(v).isBlank()) {
            out.add(String.valueOf(v).trim().toLowerCase(Locale.ROOT));
        }
        return out;
    }
}
