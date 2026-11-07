package net.skybornmc.skyblock.islandtools;

import net.skybornmc.skyblock.common.Core;
import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.audit.AuditCategory;
import net.skybornmc.skyblock.common.bonus.BonusFamily;
import net.skybornmc.skyblock.common.bonus.BonusService;
import net.skybornmc.skyblock.common.command.Arg;
import net.skybornmc.skyblock.common.command.Args;
import net.skybornmc.skyblock.common.command.CommandFailure;
import net.skybornmc.skyblock.common.command.CommandSpec;
import net.skybornmc.skyblock.common.db.Migrations;
import net.skybornmc.skyblock.common.economy.AccountRef;
import net.skybornmc.skyblock.common.economy.BatchResult;
import net.skybornmc.skyblock.common.economy.Currency;
import net.skybornmc.skyblock.common.economy.EconomyService;
import net.skybornmc.skyblock.common.economy.Transaction;
import net.skybornmc.skyblock.common.economy.TxReason;
import net.skybornmc.skyblock.common.economy.TxResult;
import net.skybornmc.skyblock.common.island.IslandService;
import net.skybornmc.skyblock.common.module.SkyblockModule;
import net.skybornmc.skyblock.common.profile.ProfileService;
import net.skybornmc.skyblock.common.progression.ProgressionService;
import net.skybornmc.skyblock.common.text.ModuleMessages;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.util.Format;
import net.skybornmc.skyblock.common.util.Scheduler;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

/**
 * Island tools (owner 4 Nov 2026 rework): the island's harvester HOE and PICKAXE, diamond from the start. Level (1 to
 * {@code levels.max}), XP, perks and prestige belong to the island ({@code island_tool_progress}, cached here, written
 * behind); every member holds a handle item ({@link ToolItems}). Perks unlock at tool levels and are paid with the
 * tool's own shards (Farming Shards for the hoe, Mining Shards for the pickaxe), which are personal per profile. At the
 * max level the owner/co-owner can prestige a tool: it resets (level and perks), that shard type goes to 0 for every
 * member, and the island keeps +5% XP and shards and 5% cheaper perks per prestige.
 */
public final class IslandToolsModule implements SkyblockModule {

    public static final String ID = "islandtools";
    static final String PERM = "skyblock.islandtools.use";
    static final String PERM_ADMIN = "skyblock.islandtools.admin";
    static final String PERM_TOOL = "skyblock.islandtools.tool";

    private final Core core;
    private final IslandToolsConfig config = new IslandToolsConfig();
    private ModuleMessages msg;
    private ToolStore store;
    private ToolItems items;
    private volatile Upgrades upgrades = Upgrades.parse(List.of(), w -> { });
    private volatile List<ToolRules.Band> bands = List.of();
    private ToolListener listener;
    private EnchantProcs enchants;
    private HoeFarming farming;
    private IslandGrowth growth;
    private HoeStorage storage;
    private final List<Scheduler.Task> tasks = new ArrayList<>();
    /** Island → its tools (XP here includes what is not written yet). Main thread. */
    private final Map<UUID, Map<Kind, ToolStore.Prog>> progs = new ConcurrentHashMap<>();
    /** Islands being loaded and what waits for them. */
    private final Map<UUID, List<Runnable>> loading = new ConcurrentHashMap<>();
    /** Tool XP not written yet. */
    private final Map<UUID, Map<Kind, Long>> xpDelta = new ConcurrentHashMap<>();
    /** Islands whose handles show an old level bar (re-rendered every 10 s). */
    private final Set<UUID> dirty = ConcurrentHashMap.newKeySet();
    private final Set<UUID> busy = ConcurrentHashMap.newKeySet();
    /** Islands whose tool is being prestiged right now. */
    private final Set<String> prestiging = ConcurrentHashMap.newKeySet();
    /** Auto-Sell: drops waiting for their sale, per player, and the Sell Bonus of the tool that collected them. */
    private final Map<UUID, Map<org.bukkit.Material, Integer>> autoSell = new ConcurrentHashMap<>();
    private final Map<UUID, Double> autoSellBonus = new ConcurrentHashMap<>();
    /** Owner 6-7 Nov 2026: each player's own tool level, prestige and rebirth (profile → kind), while online. */
    private final Map<UUID, Map<Kind, ToolStore.Personal>> personal = new ConcurrentHashMap<>();
    /** Personal tool XP not written yet (profile → kind → XP). */
    private final Map<UUID, Map<Kind, Long>> personalDelta = new ConcurrentHashMap<>();
    private final Set<UUID> personalLoading = ConcurrentHashMap.newKeySet();
    /** Profiles with a prestige going through (a double click never prestiges twice). */
    private final Set<String> prestigingPersonal = ConcurrentHashMap.newKeySet();
    /** Shards found and not paid yet, per profile and shard type. */
    private final Map<UUID, Map<Currency, Long>> pendingShards = new ConcurrentHashMap<>();

    public IslandToolsModule(Core core) {
        this.core = core;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public Set<String> dependencies() {
        return Set.of("islands", "profiles", "economy");
    }

    @Override
    public void enable() throws Exception {
        new Migrations(core.database(), core.plugin().getLogger()).migrate(ID, getClass().getClassLoader());
        core.configs().load(config);
        msg = core.messages(ID);
        core.messages().load(ID);
        store = new ToolStore(core.database());
        enchants = new EnchantProcs(this);
        parse();
        items = new ToolItems(this);
        items.register();
        storage = new HoeStorage(this);
        farming = new HoeFarming(this);
        growth = new IslandGrowth(this);
        Bukkit.getPluginManager().registerEvents(growth, core.plugin());
        listener = new ToolListener(this);
        Bukkit.getPluginManager().registerEvents(listener, core.plugin());
        Bukkit.getPluginManager().registerEvents(farming, core.plugin());
        Bukkit.getPluginManager().registerEvents(enchants, core.plugin());
        core.services().optional(BonusService.class).ifPresent(b -> b.register("island_tool", (family, ctx) ->
                family == BonusFamily.EXTRA_DROPS ? dropsBonus(ctx.player(), ctx.item()) : 0));
        tasks.add(core.scheduler().repeat("islandtools:effects", 40L, 40L, listener::effectsTick));
        tasks.add(core.scheduler().repeat("islandtools:regrow", 20L, 10L, farming::tick));
        tasks.add(core.scheduler().repeat("islandtools:growth", 20L, 10L, growth::tick));
        tasks.add(core.scheduler().repeat("islandtools:storage", 200L, 200L, storage::flushAll));
        tasks.add(core.scheduler().repeat("islandtools:xp", 200L, 200L, this::flushXp));
        long shardTicks = Math.max(1, config.shardPayoutSeconds) * 20L;
        tasks.add(core.scheduler().repeat("islandtools:shards", shardTicks, shardTicks, this::payShards));
        long sellTicks = Math.max(1, config.autoSellSeconds) * 20L;
        tasks.add(core.scheduler().repeat("islandtools:auto-sell", sellTicks, sellTicks, this::flushAutoSell));
        registerCommands();
        // Owner Q-30: the /farm and /mine scoreboard shows how full the tool's storage is.
        for (Kind kind : Kind.values()) {
            core.placeholders().registerText(ID, "tool_storage_" + kind.id(), p -> {
                if (p == null) {
                    return "";
                }
                long used = storage.used(profile(p), kind);
                long cap = Math.max(1, storage.capacity(p, kind));
                return Format.number(used) + "/" + Format.number(cap) + " (" + Math.min(100, used * 100 / cap) + "%)";
            });
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            load(p);
        }
    }

    private void parse() {
        upgrades = Upgrades.parse(config.perks, w -> core.plugin().getLogger().warning(w));
        bands = ToolRules.bands(config.sweepBands);
        if (enchants != null) {
            enchants.parse(config.enchantList);
        }
    }

    @Override
    public void reload() {
        core.configs().load(config);
        core.messages().load(ID);
        parse();
        progs.keySet().forEach(island -> refreshHandles(island, null));
    }

    @Override
    public void disable() {
        tasks.forEach(Scheduler.Task::cancel);
        tasks.clear();
        flushXp();
        if (storage != null) {
            storage.flushAllBlocking();
        }
        if (listener != null) {
            HandlerList.unregisterAll(listener);
            listener.clearEffects();
        }
        if (farming != null) {
            HandlerList.unregisterAll(farming);
            farming.ripenAll();
        }
        if (growth != null) {
            HandlerList.unregisterAll(growth);
            growth.stop();
        }
        if (enchants != null) {
            HandlerList.unregisterAll(enchants);
            enchants.flush();
        }
        core.services().optional(BonusService.class).ifPresent(b -> b.unregister("island_tool"));
        if (items != null) {
            items.unregister();
        }
    }

    // ------------------------------------------------------------------ access

    Core core() {
        return core;
    }

    IslandToolsConfig config() {
        return config;
    }

    ModuleMessages msg() {
        return msg;
    }

    Upgrades upgrades() {
        return upgrades;
    }

    ToolItems items() {
        return items;
    }

    ToolListener listener() {
        return listener;
    }

    /** Owner 6 Nov 2026: the personal grind enchants. */
    /** Owner 7 Nov 2026: tool skins (looks bought with credits). */
    private final ToolSkins skins = new ToolSkins(this);

    ToolSkins skins() {
        return skins;
    }

    EnchantProcs enchants() {
        return enchants;
    }

    /** Shards found for a profile, paid with the next shard batch. */
    void addShards(UUID profile, Currency currency, long amount) {
        if (amount > 0) {
            pendingShards.computeIfAbsent(profile, k -> new ConcurrentHashMap<>()).merge(currency, amount, Long::sum);
        }
    }

    ToolStore store() {
        return store;
    }

    HoeStorage storage() {
        return storage;
    }

    HoeFarming farming() {
        return farming;
    }

    IslandGrowth growth() {
        return growth;
    }

    String raw(String key) {
        String s = core.messages().raw(ID, key);
        return s == null ? key : s;
    }

    UUID profile(Player p) {
        return core.services().optional(ProfileService.class).map(s -> s.profileKey(p)).orElse(p.getUniqueId());
    }

    Optional<IslandService> islands() {
        return core.services().optional(IslandService.class);
    }

    /** The island of a profile, or null (no island: no island tools). */
    UUID islandOf(UUID profile) {
        return islands().flatMap(i -> i.islandOfProfile(profile)).map(IslandService.IslandInfo::id).orElse(null);
    }

    /** The island the player is a member of right now (the boosts and tools follow it), or null. */
    UUID island(Player p) {
        return islandOf(profile(p));
    }

    /** The cached tool of an island (null while not loaded). */
    ToolStore.Prog prog(UUID island, Kind kind) {
        if (island == null || kind == null) {
            return null;
        }
        Map<Kind, ToolStore.Prog> map = progs.get(island);
        return map == null ? null : map.get(kind);
    }

    /** The player's island's tool, or null. */
    ToolStore.Prog prog(Player p, Kind kind) {
        return prog(island(p), kind);
    }

    boolean loaded(Player p) {
        UUID island = island(p);
        return island != null && progs.containsKey(island) && personal.containsKey(profile(p)) && enchants.loaded(p);
    }

    /** A profile's own tool (null while loading). */
    ToolStore.Personal personal(UUID profile, Kind kind) {
        Map<Kind, ToolStore.Personal> map = profile == null ? null : personal.get(profile);
        return map == null ? null : map.get(kind);
    }

    /**
     * Owner 6-7 Nov 2026 (personal tools): the tool as one player sees it: the island's shared perks and guard version with
     * the player's own XP, prestige and rebirths. Null while either is loading.
     */
    ToolStore.Prog view(UUID island, UUID profile, Kind kind) {
        ToolStore.Prog isl = prog(island, kind);
        ToolStore.Personal own = personal(profile, kind);
        String perks = perkCode(profile, kind);
        if (isl == null || own == null || perks == null) {
            return null;
        }
        return new ToolStore.Prog(own.xp(), own.prestige(), perks, isl.version(), own.rebirth());
    }

    // ------------------------------------------------------------------ tools v5: personal perks (owner 7 Nov 2026)

    /** The row prefix of a personal perk in {@code island_tool_enchants} ({@code perk.shard_finder}). */
    static final String PERK = "perk.";
    /** The one-time seed marker row (kind hoe): the island's old shared levels were copied to this profile. */
    static final String SEED = "perk._v5";

    private final Map<UUID, Map<Kind, String>> perkCodes = new ConcurrentHashMap<>();

    /** The personal perk levels of a profile as an upgrades code ("shard_finder=37,..."), null while loading. */
    String perkCode(UUID profile, Kind kind) {
        if (profile == null || kind == null) {
            return null;
        }
        Map<String, Integer> rows = enchants.rows(profile);
        if (rows == null) {
            return null;
        }
        return perkCodes.computeIfAbsent(profile, k -> new ConcurrentHashMap<>()).computeIfAbsent(kind, k -> {
            String prefix = kind.id() + ":" + PERK;
            Map<String, Integer> levels = new java.util.TreeMap<>();
            rows.forEach((key, lv) -> {
                if (key.startsWith(prefix) && lv > 0 && !key.equals(kind.id() + ":" + SEED)) {
                    levels.put(key.substring(prefix.length()), lv);
                }
            });
            // Tools v6 (owner 7 Nov 2026: "only need to buy ONCE"): Auto-Sell and Auto-Compactor bought on one tool are on
            // every tool that has them, and they stay through prestige and rebirth.
            for (Upgrades.Def d : upgrades.of(kind)) {
                if (!accountWide(d) || levels.containsKey(d.id())) {
                    continue;
                }
                for (Kind other : Kind.values()) {
                    if (other == kind) {
                        continue;
                    }
                    boolean owned = upgrades.of(other).stream().anyMatch(o -> o.effect() == d.effect()
                            && rows.getOrDefault(other.id() + ":" + PERK + o.id(), 0) > 0);
                    if (owned) {
                        levels.put(d.id(), d.max());
                        break;
                    }
                }
            }
            return Upgrades.encode(levels);
        });
    }

    /** One-time perks that are bought once for all of a player's tools. Pure. */
    static boolean accountWide(Upgrades.Def d) {
        return d.max() == 1 && (d.effect() == Upgrades.Effect.AUTOSELL || d.effect() == Upgrades.Effect.COMPACT);
    }

    void forgetPerks(UUID profile) {
        if (profile != null) {
            perkCodes.remove(profile);
        }
    }

    /**
     * Tools v5 (owner 7 Nov 2026): perks are personal now. The first time a profile that already played loads, it gets
     * the island's old shared levels as its own, converted to the same effect on the new long tracks (once, a marker row
     * stops a second copy). New players start at 0.
     */
    void perksLoaded(Player p, UUID profile) {
        forgetPerks(profile);
        Map<String, Integer> rows = enchants.rows(profile);
        if (rows == null || rows.containsKey(Kind.HOE.id() + ":" + SEED)) {
            refreshOwnHandles(p);
            return;
        }
        UUID island = island(p);
        boolean ready = island != null && progs.containsKey(island) && personal.containsKey(profile);
        if (!ready) {
            if (island != null && p.isOnline() && seedTries.merge(profile, 1, Integer::sum) <= 30) {
                Bukkit.getScheduler().runTaskLater(core.plugin(), () -> { if (p.isOnline()) { perksLoaded(p, profile); } }, 40L);
            }
            return;
        }
        seedTries.remove(profile);
        boolean played = java.util.Arrays.stream(Kind.values()).map(k -> personal(profile, k))
                .anyMatch(o -> o != null && (o.xp() > 0 || o.prestige() > 0 || o.rebirth() > 0));
        Map<String, Integer> seed = new java.util.LinkedHashMap<>();
        if (played) {
            for (Kind k : Kind.values()) {
                ToolStore.Prog isl = prog(island, k);
                if (isl == null) {
                    continue;
                }
                isl.levels().forEach((id, lv) -> upgrades.get(k, id).ifPresent(d -> {
                    int to = PerkSeed.convert(d.effect(), lv, d.max());
                    if (to > 0) {
                        seed.put(k.id() + ":" + PERK + id, to);
                    }
                }));
            }
        }
        core.database().transaction(c -> {
            for (Map.Entry<String, Integer> e : seed.entrySet()) {
                String[] kp = e.getKey().split(":", 2);
                EnchantStore.set(c, profile, Kind.of(kp[0]).orElseThrow(), kp[1], 0, e.getValue());
            }
            return EnchantStore.set(c, profile, Kind.HOE, SEED, 0, 1);
        }).whenComplete((ok, error) -> Bukkit.getScheduler().runTask(core.plugin(), () -> {
            if (error != null) {
                core.plugin().getLogger().log(Level.WARNING, "Island tools: could not copy the island perks to " + profile, error);
                return;
            }
            Map<String, Integer> now = enchants.rows(profile);
            if (now != null && Boolean.TRUE.equals(ok)) {
                seed.forEach((k, v) -> now.merge(k, v, Math::max));
                now.put(Kind.HOE.id() + ":" + SEED, 1);
                core.audit().logPlayer(AuditCategory.ITEM, "island_tool_perks_seeded", Actor.SYSTEM, p.getUniqueId(), p.getName(),
                        (long) seed.size(), p.getLocation(), Map.of("island", island.toString(), "perks", String.valueOf(seed)));
            }
            forgetPerks(profile);
            if (p.isOnline()) {
                refreshOwnHandles(p);
            }
        }));
    }

    private final Map<UUID, Integer> seedTries = new ConcurrentHashMap<>();

    /** The player's own handles get their own enchant-like perks (Efficiency, Fortune ...). */
    void refreshOwnHandles(Player p) {
        UUID island = island(p);
        if (island != null) {
            refreshHandles(p, island, null);
        }
    }

    ToolStore.Prog view(Player p, Kind kind) {
        return view(island(p), profile(p), kind);
    }

    /** The prestige the enchant cap counts: a player with a rebirth has every prestige behind them. */
    int capPrestige(ToolStore.Prog view) {
        return view == null ? 0 : view.rebirth() > 0 ? Math.max(view.prestige(), config.prestigeMax) : view.prestige();
    }

    /** Tools v6: every level the tool ever reached (prestige x max level + level; a rebirth counts as all 50 prestiges). */
    int lifetimeLevel(ToolStore.Prog prog) {
        return prog == null ? 0 : capPrestige(prog) * config.maxLevel + level(prog).level();
    }

    /**
     * Tools v6, XP Surge: tool XP worth {@code blocks} relevant blocks at once (with the prestige bonus), on the player's own
     * tool, with the normal level-up. Returns the XP given (0 at the max level or while loading).
     */
    long bonusXp(Player p, Kind kind, double blocks) {
        UUID island = island(p);
        UUID profile = profile(p);
        ToolStore.Prog prog = view(island, profile, kind);
        if (prog == null || blocks <= 0) {
            return 0;
        }
        ToolRules.Level before = level(prog);
        if (before.maxed()) {
            return 0;
        }
        double perBlock = kind == Kind.HOE ? config.xpPerBlock / Math.max(1, config.hoeXpDivisor) : config.xpPerBlock;
        long xp = ToolRules.rollUp(blocks * perBlock * prestigeBonus(p, kind), ThreadLocalRandom.current().nextDouble());
        if (xp <= 0) {
            return 0;
        }
        ToolStore.Prog next = prog.withXp(prog.xp() + xp);
        personal.get(profile).put(kind, personal(profile, kind).withXp(next.xp()));
        personalDelta.computeIfAbsent(profile, k -> new ConcurrentHashMap<>()).merge(kind, xp, Long::sum);
        dirty.add(island);
        ToolRules.Level after = level(next);
        if (after.level() > before.level()) {
            levelUp(p, island, kind, after, next);
        }
        return xp;
    }

    ToolRules.Level level(ToolStore.Prog prog) {
        return ToolRules.levelOf(prog.xp(), prog.prestige(), config.maxLevel, xpBase(prog.rebirth()), config.xpExponent, config.xpPrestigeFactor);
    }

    long xpToMax(int prestige, int rebirth) {
        return ToolRules.xpToMax(prestige, config.maxLevel, xpBase(rebirth), config.xpExponent, config.xpPrestigeFactor);
    }

    /** Owner 6-7 Nov 2026: every rebirth needs {@code rebirth.xp-percent} more XP per level. */
    private double xpBase(int rebirth) {
        return config.xpBase * (1 + Math.max(0, rebirth) * config.rebirthXpPercent / 100.0);
    }

    /** Level of the first perk with this effect on the island's tool (0 = none). */
    int effect(Player p, Kind kind, Upgrades.Effect effect) {
        return p == null ? 0 : effect(island(p), profile(p), kind, effect);
    }

    /** Level of the first perk with this effect on a profile's own tool (tools v5: personal; 0 = none or loading). */
    int effect(UUID island, UUID profile, Kind kind, Upgrades.Effect effect) {
        ToolStore.Prog prog = view(island, profile, kind);
        if (prog == null) {
            return 0;
        }
        return upgrades.byEffect(kind, effect).map(d -> Math.min(prog.level(d.id()), d.max())).orElse(0); // a level above a lowered max counts as the max
    }

    /** The player's lasting bonus on tool XP and shards: +5% per prestige and +10% per rebirth (owner 6-7 Nov 2026). */
    double prestigeBonus(Player p, Kind kind) {
        ToolStore.Personal own = personal(profile(p), kind);
        if (own == null) {
            return 1;
        }
        return ToolRules.prestigeBonus(own.prestige(), config.prestigeBonus) + Math.max(0, own.rebirth()) * config.rebirthBonus / 100.0;
    }

    double priceFactor(ToolStore.Prog prog) {
        return ToolRules.priceFactor(prog.prestige(), config.prestigeDiscount, config.priceFloor);
    }

    /** Sweep/Vein chance (percent) and blocks at the player's own prestige; null when locked. */
    ToolRules.Band band(Player p, Kind kind) {
        ToolStore.Prog prog = view(p, kind);
        return prog == null ? null : ToolRules.band(bands, capPrestige(prog));
    }

    double sweepChance(Player p, Kind kind) {
        ToolRules.Band band = band(p, kind);
        Upgrades.Def def = upgrades.byEffect(kind, Upgrades.Effect.SWEEP).orElse(null);
        return def == null ? 0 : ToolRules.sweepChance(band, effect(p, kind, Upgrades.Effect.SWEEP), def.max());
    }

    /** Growth (hoe perk): percent faster crop growth on {@code island}. */
    double growthPercent(UUID island) {
        int best = 0; // tools v5: the best Growth of the members online
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (island.equals(island(p))) {
                best = Math.max(best, effect(island, profile(p), Kind.HOE, Upgrades.Effect.GROWTH));
            }
        }
        return best * config.growthPerLevel;
    }

    /** Sell Bonus (perk) in percent for sales made by the player's own tool. */
    double sellBonus(Player p, Kind kind) {
        return effect(p, kind, Upgrades.Effect.SELL) * config.sellPerLevel;
    }

    /**
     * Owner 4 Nov 2026: the island's own farm/mine boost (island upgrade, much stronger than the tool perks), in percent:
     * harvest-yield for the hoe, mine-fortune for the pickaxe, from the island the player is a member of now.
     */
    double islandBoost(Player p, Kind kind) {
        UUID island = island(p);
        if (island == null) {
            return 0;
        }
        String effect = switch (kind) {
            case HOE -> config.hoeBoostEffect;
            case PICKAXE -> config.pickaxeBoostEffect;
            case ROD -> config.rodBoostEffect;
            case SWORD -> config.swordBoostEffect;
        };
        return core.services().optional(ProgressionService.class).map(s -> s.upgradeEffectValue(island, effect)).orElse(0.0);
    }

    /** The tool's own Extra Drops (percent) for the bonus service, only for a handle of a loaded island. */
    private double dropsBonus(Player p, ItemStack item) {
        Kind kind = ToolItems.kindOf(item);
        UUID island = ToolItems.islandOf(item);
        return kind == null || p == null || island == null || !island.equals(island(p)) ? 0
                : effect(p, kind, Upgrades.Effect.DROPS) * config.dropsPerLevel;
    }

    /** The shown value of a perk level: roman numerals for enchant-like perks, "132/1000" for tracks. */
    String levelText(Upgrades.Def def, int level) {
        if (def.effect() == Upgrades.Effect.ENCHANT || def.effect() == Upgrades.Effect.HASTE) {
            return roman(level);
        }
        return def.max() == 1 ? raw("menu.owned") : Format.number(Math.min(level, def.max())) + "<c:muted>/" + Format.number(def.max()) + "</c:muted>";
    }

    static String roman(int n) {
        String[] r = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
        return n >= 0 && n < r.length ? r[n] : String.valueOf(n);
    }

    /** {@code ✧ 1,500} in the colour of the tool's shards (MiniMessage). */
    String shardText(Kind kind, long amount) {
        String key = kind.shards().key();
        String plain = core.services().optional(EconomyService.class).map(e -> e.format(kind.shards(), amount)).orElse(Format.number(amount));
        return "<cur:" + key + ">" + plain + "</cur:" + key + ">";
    }

    // ------------------------------------------------------------------ loading

    /** Runs {@code then} on the main thread once the island's tools are loaded (at once when they are). */
    void ensure(UUID island, Runnable then) {
        if (island == null) {
            return;
        }
        if (progs.containsKey(island)) {
            then.run();
            return;
        }
        List<Runnable> waiting = loading.computeIfAbsent(island, k -> new ArrayList<>());
        waiting.add(then);
        if (waiting.size() > 1) {
            return; // already loading
        }
        store.loadIsland(island, (kind, old) -> Upgrades.encode(ToolRules.migrate(kind, old, upgrades)))
                .whenComplete((map, error) -> Bukkit.getScheduler().runTask(core.plugin(), () -> {
                    List<Runnable> run = loading.remove(island);
                    if (error != null) {
                        core.plugin().getLogger().log(Level.WARNING, "Island tools: could not load the tools of island " + island, error);
                        return;
                    }
                    Map<Kind, ToolStore.Prog> loaded = new EnumMap<>(Kind.class);
                    Map<Kind, Long> pending = xpDelta.getOrDefault(island, Map.of());
                    map.forEach((kind, prog) -> loaded.put(kind, prog.withXp(prog.xp() + pending.getOrDefault(kind, 0L))));
                    progs.put(island, loaded);
                    refreshHandles(island, null);
                    if (run != null) {
                        run.forEach(Runnable::run);
                    }
                }));
    }

    /** Join, profile switch, island change: the handles are checked and the first ones given. */
    void load(Player p) {
        enchants.load(p);
        loadPersonal(p);
        UUID island = island(p);
        if (island == null) {
            check(p);
            return;
        }
        ensure(island, () -> {
            if (!p.isOnline()) {
                return;
            }
            check(p);
            storage.load(profile(p));
            giveFirst(p, island);
        });
    }

    void unload(Player p) {
        forgetPerks(profile(p));
        seedTries.remove(profile(p));
        enchants.unload(p);
        personal.remove(profile(p));
        storage.unload(profile(p));
        busy.remove(p.getUniqueId());
    }

    void unloadProfile(UUID profile) {
        if (profile != null) {
            storage.unload(profile);
            forgetPerks(profile);
            enchants.unloadProfile(profile);
            personal.remove(profile);
        }
    }

    /** The player's own tools (seeded once from their island's tool), with the XP not written yet on top. */
    private void loadPersonal(Player p) {
        UUID profile = profile(p);
        if (personal.containsKey(profile) || !personalLoading.add(profile)) {
            return;
        }
        store.loadPersonal(profile, island(p)).whenComplete((map, error) -> Bukkit.getScheduler().runTask(core.plugin(), () -> {
            personalLoading.remove(profile);
            if (error != null) {
                core.plugin().getLogger().log(Level.WARNING, "Island tools: could not load the own tools of " + profile, error);
                return;
            }
            if (!p.isOnline() || !profile.equals(profile(p))) {
                return;
            }
            Map<Kind, ToolStore.Personal> loaded = new EnumMap<>(Kind.class);
            Map<Kind, Long> pending = personalDelta.getOrDefault(profile, Map.of());
            map.forEach((kind, own) -> loaded.put(kind, own.withXp(own.xp() + pending.getOrDefault(kind, 0L))));
            personal.put(profile, new ConcurrentHashMap<>(loaded));
            UUID island = island(p);
            if (island != null) {
                refreshHandles(island, null);
            }
        }));
    }

    /** The first handles of an island, once per profile and island (lost ones come from the tool menu or /tool). */
    private void giveFirst(Player p, UUID island) {
        UUID profile = profile(p);
        store.given(profile, island).whenComplete((given, error) -> Bukkit.getScheduler().runTask(core.plugin(), () -> {
            if (error != null || !p.isOnline() || !island.equals(island(p)) || !config.enabled) {
                return;
            }
            Set<Kind> missing = EnumSet.noneOf(Kind.class);
            for (Kind kind : Kind.values()) {
                if (!given.contains(kind) && !carries(p, kind, island)) {
                    missing.add(kind);
                }
            }
            Set<Kind> mark = EnumSet.allOf(Kind.class);
            mark.removeAll(given);
            if (!mark.isEmpty()) {
                store.markGiven(profile, island, mark);
            }
            if (missing.isEmpty()) {
                return;
            }
            List<ItemStack> give = new ArrayList<>();
            for (Kind kind : missing) {
                give.add(items.create(kind, island, profile));
            }
            giveAll(p, give);
            msg.send(p, "starter.given");
            core.audit().logPlayer(AuditCategory.ITEM, "island_tools_given", Actor.SYSTEM, p.getUniqueId(), p.getName(), null,
                    p.getLocation(), Map.of("island", island.toString(), "kinds", missing.toString()));
        }));
    }

    /** True when the player carries a handle of {@code island}'s {@code kind}. */
    boolean carries(Player p, Kind kind, UUID island) {
        PlayerInventory inv = p.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (ToolItems.kindOf(s) == kind && island.equals(ToolItems.islandOf(s))) {
                return true;
            }
        }
        ItemStack cursor = p.getItemOnCursor();
        return ToolItems.kindOf(cursor) == kind && island.equals(ToolItems.islandOf(cursor));
    }

    /**
     * Owner 4 Nov 2026: tidies the player's island tool items. Removed (audit-logged): the island axe and sword, a handle
     * of another island (or any handle without an island), and a second handle of the same kind. A hoe or pickaxe from
     * before the handles becomes the island's handle in the same slot (diamond).
     */
    void check(Player p) {
        UUID island = island(p);
        UUID profile = profile(p);
        PlayerInventory inv = p.getInventory();
        Set<Kind> seen = EnumSet.noneOf(Kind.class);
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (!ToolItems.isTool(s)) {
                continue;
            }
            String raw = ToolItems.rawKind(s);
            Kind kind = ToolItems.kindOf(s);
            UUID of = ToolItems.islandOf(s);
            String why;
            if (kind == null) {
                why = Kind.legacy(raw) ? "removed_tool" : "unknown_tool";
            } else if (island == null) {
                why = "no_island";
            } else if (of == null) {
                if (!progs.containsKey(island)) {
                    continue; // not loaded yet: migrated once the island is (load calls this again)
                }
                if (seen.add(kind)) {
                    inv.setItem(i, items.create(kind, island, profile));
                    core.audit().logPlayer(AuditCategory.ITEM, "island_tool_migrated", Actor.SYSTEM, p.getUniqueId(), p.getName(), null,
                            p.getLocation(), Map.of("kind", kind.id(), "island", island.toString(), "slot", i));
                    continue;
                }
                why = "duplicate";
            } else if (!of.equals(island)) {
                why = "other_island";
            } else if (!seen.add(kind)) {
                why = "duplicate";
            } else {
                continue;
            }
            inv.setItem(i, null);
            core.audit().logPlayer(AuditCategory.ITEM, "island_tool_removed", Actor.SYSTEM, p.getUniqueId(), p.getName(), null,
                    p.getLocation(), Map.of("kind", raw == null ? "?" : raw, "reason", why, "slot", i,
                            "island", of == null ? "-" : of.toString()));
            if ("removed_tool".equals(why)) {
                msg.send(p, "tool.removed-kind", Msg.p("tool", raw));
            } else if ("other_island".equals(why) || "no_island".equals(why)) {
                msg.send(p, "tool.other-island");
            }
        }
    }

    void giveAll(Player p, List<ItemStack> stacks) {
        core.services().optional(net.skybornmc.skyblock.common.box.ItemBox.class).ifPresentOrElse(
                box -> box.giveItems(p, stacks, net.skybornmc.skyblock.common.box.BoxSource.TOOLS),
                () -> p.getInventory().addItem(stacks.toArray(new ItemStack[0])).values()
                        .forEach(left -> p.getWorld().dropItemNaturally(p.getLocation(), left)));
    }

    /** Re-renders the handles of an island (one kind, or both with null) in every online member's inventory. */
    void refreshHandles(UUID island, Kind only) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (island.equals(island(p))) {
                refreshHandles(p, island, only);
            }
        }
    }

    /** One player's handles of {@code island} get the player's own enchant-like perks. */
    void refreshHandles(Player p, UUID island, Kind only) {
        PlayerInventory inv = p.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack s = inv.getItem(i);
            Kind kind = ToolItems.kindOf(s);
            if (kind == null || (only != null && kind != only) || !island.equals(ToolItems.islandOf(s))) {
                continue;
            }
            ItemStack copy = s.clone();
            items.applyEnchants(copy, kind, view(island, profile(p), kind));
            core.items().render(copy);
            skins.apply(copy, kind, profile(p)); // owner 7 Nov 2026: the chosen look
            inv.setItem(i, copy);
        }
    }

    /** "Get your hoe": a new handle when the player has none (never a second one). */
    void giveHandle(Player p, Kind kind) {
        UUID island = island(p);
        if (island == null) {
            msg.send(p, "buy.no-island");
            return;
        }
        if (carries(p, kind, island)) {
            msg.send(p, "copy.have", Msg.p("tool", raw("item.name." + kind.id())));
            return;
        }
        if (p.getInventory().firstEmpty() < 0) {
            msg.send(p, "copy.full");
            return;
        }
        p.getInventory().addItem(items.create(kind, island, profile(p)));
        msg.send(p, "copy.done", Msg.p("tool", raw("item.name." + kind.id())));
        core.sounds().play(p, "success");
        core.audit().logPlayer(AuditCategory.ITEM, "island_tool_handle", Actor.player(p), p.getUniqueId(), p.getName(), null,
                p.getLocation(), Map.of("kind", kind.id(), "island", island.toString()));
    }

    // ------------------------------------------------------------------ XP, shards, finds

    /**
     * One relevant block with the island's tool (a ripe crop for the hoe, a natural ore/stone for the pickaxe): tool XP
     * for the island, and (unless {@code swept}) a shard roll and a Lucky roll for the player.
     */
    void action(Player p, Kind kind, boolean swept) {
        action(p, kind, swept, null);
    }

    /** {@link #action(Player, Kind, boolean)} with the block or mob spot that gave it ({@code at}: for the shard popup; null = none). */
    void action(Player p, Kind kind, boolean swept, org.bukkit.Location at) {
        UUID island = island(p);
        UUID profile = profile(p);
        ToolStore.Prog prog = view(island, profile, kind);
        if (prog == null) {
            return;
        }
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        double bonus = prestigeBonus(p, kind);
        ToolRules.Level before = level(prog);
        if (!before.maxed()) {
            double wisdom = 1 + effect(island, profile, kind, Upgrades.Effect.WISDOM) * config.wisdomPerLevel / 100.0;
            double event = net.skybornmc.skyblock.common.grind.GrindEvents.multiplier(core.services(), p,
                    net.skybornmc.skyblock.common.grind.GrindEvents.Kind.DOUBLE_XP); // owner 6 Nov 2026: Double XP
            double combo = enchants.xpMultiplier(p, kind); // owner 6 Nov 2026: Combo (grind enchant)
            double perBlock = kind == Kind.HOE ? config.xpPerBlock / Math.max(1, config.hoeXpDivisor) : config.xpPerBlock; // owner 7 Nov 2026
            long xp = ToolRules.roll(perBlock * bonus * wisdom * event * combo, rnd.nextDouble());
            if (xp > 0) {
                ToolStore.Prog next = prog.withXp(prog.xp() + xp);
                personal.get(profile).put(kind, personal(profile, kind).withXp(next.xp()));
                personalDelta.computeIfAbsent(profile, k -> new ConcurrentHashMap<>()).merge(kind, xp, Long::sum);
                dirty.add(island);
                ToolRules.Level after = level(next);
                if (after.level() > before.level()) {
                    levelUp(p, island, kind, after, next);
                }
            }
        }
        if (swept && !config.sweepShards) {
            return;
        }
        rollShards(p, island, profile, kind, bonus, at, rnd);
    }

    /**
     * Tools v5 (owner 7 Nov 2026): a block broken by an area enchant (Explode, Blast ...) rolls for shards too (no tool
     * XP), so the shard math holds at the speed players really break blocks.
     */
    void areaShards(Player p, Kind kind, org.bukkit.Location at) {
        if (!config.areaShards) {
            return;
        }
        UUID island = island(p);
        UUID profile = profile(p);
        if (view(island, profile, kind) == null) {
            return;
        }
        rollShards(p, island, profile, kind, prestigeBonus(p, kind), at, ThreadLocalRandom.current());
    }

    private void rollShards(Player p, UUID island, UUID profile, Kind kind, double bonus, org.bukkit.Location at, ThreadLocalRandom rnd) {
        double chance = switch (kind) {
            case HOE -> config.shardHoe;
            case PICKAXE -> config.shardPickaxe;
            case ROD -> config.shardRod;
            case SWORD -> config.shardSword;
        } / 100.0;
        int finderLv = effect(island, profile, kind, Upgrades.Effect.SHARDS);
        double finder = ToolRules.compound(config.shardFinderPerLevel, finderLv) // tools v6: +3% a level, compound
                * ToolRules.compound(config.shardFinderMilestoneBonus, finderLv / Math.max(1, config.shardFinderMilestoneEvery)); // + milestones
        double doubled = net.skybornmc.skyblock.common.grind.GrindEvents.multiplier(core.services(), p,
                net.skybornmc.skyblock.common.grind.GrindEvents.Kind.DOUBLE_SHARDS); // owner 6 Nov 2026: Double Shards
        double streak = enchants.shardMultiplier(p, kind); // owner 6 Nov 2026: Shard Echo + Compound Interest
        int shards = ToolRules.rollUp(chance * finder * bonus * doubled * streak, rnd.nextDouble()); // tools v6: always rounded up
        int lucky = effect(island, profile, kind, Upgrades.Effect.LUCKY);
        if (lucky > 0 && rnd.nextDouble(100) < lucky * config.luckyPerLevel) {
            int pouch = (int) Math.round(rnd.nextInt(Math.max(1, config.luckyMin), Math.max(config.luckyMin, config.luckyMax) + 1) * bonus);
            shards += pouch;
            core.sounds().play(p, "tool-lucky"); // tools v5: a pouch is a popup and a sound, no title (no spam at 13 blocks a second)
            pendingShards.computeIfAbsent(profile(p), k -> new ConcurrentHashMap<>()).merge(kind.shards(), (long) pouch, Long::sum);
            if (config.shardPopups && at != null) {
                net.skybornmc.skyblock.common.hud.Popups.show(core.plugin(), p, at.clone().add(0, 0.4, 0),
                        msg.component(p, "lucky.popup", Msg.mm("amount", shardText(kind, pouch))));
            } else {
                msg.actionBar(p, "lucky.bar", Msg.mm("amount", shardText(kind, pouch)));
            }
            shards -= pouch;
        }
        if (shards > 0) {
            pendingShards.computeIfAbsent(profile(p), k -> new ConcurrentHashMap<>()).merge(kind.shards(), (long) shards, Long::sum);
            popup(p, at, kind.shards(), shards);
        }
    }

    /** Owner 7 Nov 2026: "+N shards" floats up from the block or mob that gave it (0.5 s, only this player sees it). */
    void popup(Player p, org.bukkit.Location at, Currency currency, long amount) {
        if (!config.shardPopups || at == null || amount <= 0) {
            return;
        }
        net.skybornmc.skyblock.common.hud.Popups.show(core.plugin(), p, at,
                msg.component(p, "shards.popup." + currency.key(), Msg.number("amount", amount)));
    }

    private void levelUp(Player p, UUID island, Kind kind, ToolRules.Level after, ToolStore.Prog now) {
        String tool = raw("item.name." + kind.id());
        boolean max = after.maxed();
        if (config.levelTitle) {
            msg.title(p, max ? "level.max" : "level.up", Msg.p("tool", tool), Msg.number("level", after.level()));
        }
        core.sounds().play(p, max ? "tool-max" : "tool-level-up");
        if (max) {
            msg.send(p, now.prestige() >= config.prestigeMax ? "level.can-rebirth" : "level.can-prestige", Msg.p("tool", tool));
        }
        refreshHandles(island, kind);
        // C-02: the common level-up event (quests "tool levels", the Toolsmith achievements) now comes from these tools.
        net.skybornmc.skyblock.common.tools.ToolInfo info = new net.skybornmc.skyblock.common.tools.ToolInfo(profile(p),
                switch (kind) {
                    case HOE -> net.skybornmc.skyblock.common.tools.ToolKind.HOE;
                    case PICKAXE -> net.skybornmc.skyblock.common.tools.ToolKind.PICKAXE;
                    case ROD -> net.skybornmc.skyblock.common.tools.ToolKind.ROD;
                    case SWORD -> net.skybornmc.skyblock.common.tools.ToolKind.SWORD;
                },
                0, after.level(), now.xp(), now.prestige(), 0, List.of(), null, null, false, Map.of());
        Bukkit.getPluginManager().callEvent(new net.skybornmc.skyblock.common.tools.ToolLevelUpEvent(p, info, Math.max(1, after.level() - 1)));
    }

    /** Staff: adds tool XP to a player's own tool (testing the levels, prestige and rebirth). */
    void addXp(Player target, Kind kind, long xp) {
        UUID profile = profile(target);
        ToolStore.Personal own = personal(profile, kind);
        if (own == null || xp <= 0) {
            return;
        }
        personal.get(profile).put(kind, own.withXp(own.xp() + xp));
        personalDelta.computeIfAbsent(profile, k -> new ConcurrentHashMap<>()).merge(kind, xp, Long::sum);
        UUID island = island(target);
        if (island != null) {
            refreshHandles(island, kind);
        }
    }

    private void flushPersonalXp() {
        if (personalDelta.isEmpty() || store == null) {
            return;
        }
        Map<UUID, Map<Kind, Long>> batch = new HashMap<>();
        for (UUID profile : List.copyOf(personalDelta.keySet())) {
            Map<Kind, Long> d = personalDelta.remove(profile);
            if (d != null && !d.isEmpty()) {
                batch.put(profile, new EnumMap<>(d));
            }
        }
        if (batch.isEmpty()) {
            return;
        }
        store.addPersonalXp(batch).whenComplete((v, e) -> {
            if (e != null) { // keep it for the next try
                core.plugin().getLogger().log(Level.WARNING, "Island tools: could not save own tool XP, trying again", e);
                batch.forEach((profile, d) -> d.forEach((kind, xp) ->
                        personalDelta.computeIfAbsent(profile, k -> new ConcurrentHashMap<>()).merge(kind, xp, Long::sum)));
            }
        });
    }

    private void flushXp() {
        for (UUID island : List.copyOf(dirty)) {
            dirty.remove(island);
            if (progs.containsKey(island)) {
                refreshHandles(island, null);
            }
        }
        flushPersonalXp();
        if (xpDelta.isEmpty() || store == null) {
            return;
        }
        Map<UUID, Map<Kind, Long>> batch = new HashMap<>();
        for (UUID island : List.copyOf(xpDelta.keySet())) {
            Map<Kind, Long> d = xpDelta.remove(island);
            if (d != null && !d.isEmpty()) {
                batch.put(island, new EnumMap<>(d));
            }
        }
        if (batch.isEmpty()) {
            return;
        }
        store.addXp(batch).whenComplete((v, e) -> {
            if (e != null) {
                core.plugin().getLogger().log(Level.WARNING, "Island tools: tool XP not saved (kept for the next try)", e);
                batch.forEach((island, d) -> d.forEach((k, n) ->
                        xpDelta.computeIfAbsent(island, x -> new ConcurrentHashMap<>()).merge(k, n, Long::sum)));
            }
        });
    }

    /** Pays the found shards in one ledger row per profile and shard type (one action bar line). */
    private void payShards() {
        enchants.pay();
        EconomyService economy = core.services().optional(EconomyService.class).orElse(null);
        if (economy == null || pendingShards.isEmpty()) {
            return;
        }
        for (UUID profile : List.copyOf(pendingShards.keySet())) {
            Map<Currency, Long> due = pendingShards.remove(profile);
            if (due == null) {
                continue;
            }
            due.forEach((currency, n) -> {
                if (n == null || n <= 0) {
                    return;
                }
                UUID batch = UUID.randomUUID();
                Transaction tx = Transaction.deposit(AccountRef.player(profile), currency, n, TxReason.ISLAND_TOOL_SHARD)
                        .actor(Actor.SYSTEM).reference("island_tool_shard:" + batch).idempotencyKey("island_tool_shard:" + batch);
                economy.execute(tx).whenComplete((result, error) -> Bukkit.getScheduler().runTask(core.plugin(), () -> {
                    if (error != null || result == null || !result.ok()) {
                        pendingShards.computeIfAbsent(profile, k -> new ConcurrentHashMap<>()).merge(currency, n, Long::sum);
                        return;
                    }
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        if (profile.equals(profile(p))) {
                            // owner 7 Nov 2026: the popups show each find; the action bar line only when popups are off
                            if (!config.shardPopups) {
                                net.skybornmc.skyblock.common.hud.ActionBars.show(p, "shards." + currency.key(), msg.component(p, "shards.found." + currency.key(), Msg.number("amount", n)),
                                        net.skybornmc.skyblock.common.hud.ActionBars.PAYOUT, 2500);
                            }
                            core.sounds().play(p, "shard");
                        }
                    }
                }));
            });
        }
    }

    /** True when this drop is taken by Auto-Sell (the island's tool has it and the item can be sold). */
    boolean autoSell(Player p, Kind kind, ItemStack stack) {
        if (effect(p, kind, Upgrades.Effect.AUTOSELL) <= 0) {
            return false;
        }
        var sell = core.services().optional(net.skybornmc.skyblock.common.sell.SellService.class).orElse(null);
        if (sell == null || !sell.sellable(stack)) {
            return false;
        }
        autoSell.computeIfAbsent(p.getUniqueId(), k -> new ConcurrentHashMap<>()).merge(stack.getType(), stack.getAmount(), Integer::sum);
        autoSellBonus.put(p.getUniqueId(), sellBonus(p, kind));
        return true;
    }

    /** Sells what Auto-Sell collected, one sale per player; what can't be sold goes back to the player (or /box). */
    private void flushAutoSell() {
        var sell = core.services().optional(net.skybornmc.skyblock.common.sell.SellService.class).orElse(null);
        for (UUID id : List.copyOf(autoSell.keySet())) {
            Map<org.bukkit.Material, Integer> found = autoSell.remove(id);
            double toolBonus = Optional.ofNullable(autoSellBonus.remove(id)).orElse(0.0);
            Player p = Bukkit.getPlayer(id);
            if (found == null || found.isEmpty() || p == null) {
                continue; // a player who left: their few seconds of drops are gone, like drops left on the ground
            }
            List<ItemStack> stacks = new ArrayList<>();
            found.forEach((mat, n) -> {
                for (int left = n; left > 0; left -= mat.getMaxStackSize()) {
                    stacks.add(new ItemStack(mat, Math.min(left, mat.getMaxStackSize())));
                }
            });
            if (sell == null) {
                giveAll(p, stacks);
                continue;
            }
            double bonus = sell.bonusPercent(p) + toolBonus - (100 - config.autoSellPercent);
            var request = new net.skybornmc.skyblock.common.sell.SellRequest(p.getUniqueId(), p.getName(), profile(p), stacks, bonus,
                    "auto_sell", Actor.player(p));
            sell.sell(request).whenComplete((result, error) -> Bukkit.getScheduler().runTask(core.plugin(), () -> {
                List<ItemStack> back = error != null || result == null ? stacks : result.giveBack();
                if (back != null && !back.isEmpty() && p.isOnline()) {
                    giveAll(p, back);
                }
            }));
        }
    }

    // ------------------------------------------------------------------ perk purchases

    /** Why a perk can't be bought right now (null = it can); the message key with its placeholders. */
    record Refusal(String key, net.kyori.adventure.text.minimessage.tag.resolver.TagResolver[] tags) {
    }

    /** The first reason a perk level can't be bought (owner 4 Nov 2026: one specific message per failure). */
    Refusal refusal(Player p, Kind kind, Upgrades.Def def) {
        UUID island = island(p);
        if (!config.enabled) {
            return new Refusal("disabled", new net.kyori.adventure.text.minimessage.tag.resolver.TagResolver[0]);
        }
        if (island == null) {
            return new Refusal("buy.no-island", new net.kyori.adventure.text.minimessage.tag.resolver.TagResolver[0]);
        }
        ToolStore.Prog prog = view(p, kind); // the island's perks, the player's own level and prestige
        if (prog == null) {
            return new Refusal("loading", new net.kyori.adventure.text.minimessage.tag.resolver.TagResolver[0]);
        }
        ToolRules.Level lv = level(prog);
        String perk = raw("upgrade." + def.id() + ".name");
        if (lv.level() < def.unlock()) {
            return new Refusal("buy.locked-level", new net.kyori.adventure.text.minimessage.tag.resolver.TagResolver[]{
                    Msg.p("perk", perk), Msg.number("level", def.unlock()), Msg.number("have", lv.level())});
        }
        if (capPrestige(prog) < def.prestige() || (def.effect() == Upgrades.Effect.SWEEP && band(p, kind) == null)) {
            return new Refusal("buy.locked-prestige", new net.kyori.adventure.text.minimessage.tag.resolver.TagResolver[]{
                    Msg.p("perk", perk), Msg.number("prestige", Math.max(1, def.prestige()))});
        }
        if (prog.level(def.id()) >= def.max()) {
            return new Refusal("buy.maxed", new net.kyori.adventure.text.minimessage.tag.resolver.TagResolver[]{Msg.p("perk", perk)});
        }
        return null;
    }

    /** Price of the next {@code count} levels (with the prestige discount). */
    long price(ToolStore.Prog prog, Upgrades.Def def, int count) {
        return def.sumCost(prog.level(def.id()), count, config.priceExponent, priceFactor(prog));
    }

    /** Buys up to {@code count} levels of a personal perk (tools v5): one shard payment and the level row in one transaction. */
    void buy(Player p, Kind kind, String perkId, int count, Runnable done) {
        EconomyService economy = core.services().optional(EconomyService.class).orElse(null);
        Upgrades.Def def = upgrades.get(kind, perkId).orElse(null);
        if (def == null) {
            return;
        }
        Refusal no = economy == null ? new Refusal("buy.no-economy", new net.kyori.adventure.text.minimessage.tag.resolver.TagResolver[0])
                : refusal(p, kind, def);
        if (no != null) {
            msg.send(p, no.key(), no.tags());
            core.sounds().play(p, "error");
            return;
        }
        if (!busy.add(p.getUniqueId())) {
            msg.actionBar(p, "buy.busy");
            return;
        }
        UUID island = island(p);
        UUID profile = profile(p);
        ToolStore.Prog before = view(p, kind); // the player's own perks and prestige discount
        int have = before.level(def.id());
        int n = def.buyable(have, count);
        long cost = price(before, def, n);
        long balance = economy.cached(p.getUniqueId(), kind.shards()).orElse(Long.MAX_VALUE);
        if (balance < cost) {
            busy.remove(p.getUniqueId());
            poor(p, kind, cost, balance);
            return;
        }
        UUID batch = UUID.randomUUID();
        Transaction tx = Transaction.withdraw(AccountRef.player(profile), kind.shards(), cost, TxReason.ISLAND_TOOL_UPGRADE)
                .actor(Actor.player(p)).reference("island_tool:" + batch).idempotencyKey("island_tool:" + batch)
                .detail("kind", kind.id()).detail("upgrade", def.id()).detail("levels", n);
        economy.executeBatch(List.of(tx), c -> EnchantStore.set(c, profile, kind, PERK + def.id(), have, have + n))
                .whenComplete((BatchResult result, Throwable error) -> Bukkit.getScheduler().runTask(core.plugin(), () -> {
                    busy.remove(p.getUniqueId());
                    if (error != null || result == null || !result.ok()) {
                        failed(p, kind, cost, result == null ? null : result.status(), error);
                    } else {
                        bought(p, island, profile, kind, def, have, n, cost);
                    }
                    if (done != null && p.isOnline()) {
                        done.run();
                    }
                }));
    }

    /** "Not enough Farming Shards: this costs X, you have Y." */
    private void poor(Player p, Kind kind, long cost, long balance) {
        msg.send(p, "buy.poor", Msg.mm("cost", shardText(kind, cost)), Msg.mm("have", shardText(kind, Math.max(0, balance))),
                Msg.p("shards", raw("shards.name." + kind.id())));
        core.sounds().play(p, "error");
    }

    /** A specific message for each way a payment can fail (owner 4 Nov 2026). */
    private void failed(Player p, Kind kind, long cost, TxResult.Status status, Throwable error) {
        if (!p.isOnline()) {
            return;
        }
        if (status == TxResult.Status.INSUFFICIENT_FUNDS) {
            core.services().optional(EconomyService.class).ifPresent(e -> e.balance(AccountRef.player(profile(p)), kind.shards())
                    .whenComplete((have, err) -> Bukkit.getScheduler().runTask(core.plugin(), () -> {
                        if (p.isOnline()) {
                            poor(p, kind, cost, have == null ? 0 : have);
                        }
                    })));
            return;
        }
        if (status == TxResult.Status.DUPLICATE) {
            msg.send(p, "buy.changed", Msg.p("tool", raw("item.name." + kind.id())));
        } else {
            if (error != null) {
                core.plugin().getLogger().log(Level.WARNING, "Island tools: a perk purchase of " + p.getName() + " failed", error);
            }
            msg.send(p, "buy.error");
        }
        core.sounds().play(p, "error");
    }

    private void bought(Player p, UUID island, UUID profile, Kind kind, Upgrades.Def def, int have, int n, long cost) {
        Map<String, Integer> rows = enchants.rows(profile);
        if (rows != null) {
            rows.put(kind.id() + ":" + PERK + def.id(), have + n);
        }
        forgetPerks(profile);
        if (!p.isOnline()) {
            return;
        }
        refreshHandles(p, island, kind);
        String perk = raw("upgrade." + def.id() + ".name");
        String tool = raw("item.name." + kind.id());
        String level = levelText(def, have + n);
        msg.actionBar(p, "buy.done", Msg.p("perk", perk), Msg.mm("level", level), Msg.p("tool", tool), Msg.mm("cost", shardText(kind, cost)));
        core.sounds().play(p, "unlock");
        core.audit().logPlayer(AuditCategory.ITEM, "island_tool_upgrade", Actor.player(p), p.getUniqueId(), p.getName(), cost,
                p.getLocation(), Map.of("kind", kind.id(), "upgrade", def.id(), "from", have, "to", have + n, "island", island.toString(),
                        "currency", kind.shards().key()));
    }

    // ------------------------------------------------------------------ prestige

    /** Why the player can't prestige (or rebirth) their own tool right now (null = they can). */
    Refusal prestigeRefusal(Player p, Kind kind) {
        net.kyori.adventure.text.minimessage.tag.resolver.TagResolver[] none = new net.kyori.adventure.text.minimessage.tag.resolver.TagResolver[0];
        if (!config.enabled) {
            return new Refusal("disabled", none);
        }
        if (island(p) == null) {
            return new Refusal("buy.no-island", none);
        }
        ToolStore.Prog prog = view(p, kind);
        if (prog == null) {
            return new Refusal("loading", none);
        }
        String tool = raw("item.name." + kind.id());
        ToolRules.Level lv = level(prog);
        if (!lv.maxed()) {
            return new Refusal("prestige.not-max", new net.kyori.adventure.text.minimessage.tag.resolver.TagResolver[]{Msg.p("tool", tool),
                    Msg.number("level", lv.level()), Msg.number("max", config.maxLevel)});
        }
        if (prestigingPersonal.contains(profile(p) + ":" + kind.id())) {
            return new Refusal("prestige.busy", none);
        }
        return null;
    }

    /** True when the next step of the player's tool is a rebirth (prestige {@code prestige.max} reached). */
    boolean rebirthNext(ToolStore.Prog view) {
        return view != null && view.prestige() >= config.prestigeMax;
    }

    /**
     * Owner 6-7 Nov 2026 (plans/TOOLS_V3.md section 5): the player's own prestige (up to {@code prestige.max}), then
     * rebirths without end. Resets the tool's level and XP only: enchants, perks and shards are kept. One guarded row
     * update (a double click or a second device never prestiges twice).
     */
    void prestige(Player p, Kind kind, Runnable done) {
        Refusal no = prestigeRefusal(p, kind);
        if (no != null) {
            msg.send(p, no.key(), no.tags());
            core.sounds().play(p, "error");
            return;
        }
        UUID profile = profile(p);
        String key = profile + ":" + kind.id();
        if (!prestigingPersonal.add(key)) {
            return;
        }
        ToolStore.Personal before = personal(profile, kind);
        boolean rebirth = before.prestige() >= config.prestigeMax;
        Map<Kind, Long> deltas = personalDelta.get(profile);
        Long removed = deltas == null ? null : deltas.remove(kind);
        long pending = removed == null ? 0 : removed;
        long need = xpToMax(before.prestige(), before.rebirth());
        core.database().transaction(c -> ToolStore.prestigePersonal(c, profile, kind,
                        new ToolStore.Personal(before.xp() - pending, before.prestige(), before.rebirth(), before.version()), pending, need, rebirth))
                .whenComplete((ok, error) -> Bukkit.getScheduler().runTask(core.plugin(), () -> {
                    prestigingPersonal.remove(key);
                    if (error == null && Boolean.TRUE.equals(ok)) {
                        prestiged(p, profile, kind, before, rebirth);
                    } else {
                        if (pending > 0) {
                            personalDelta.computeIfAbsent(profile, k -> new ConcurrentHashMap<>()).merge(kind, pending, Long::sum);
                        }
                        if (error != null) {
                            core.plugin().getLogger().log(Level.WARNING, "Island tools: a prestige of " + p.getName() + " failed", error);
                        }
                        if (p.isOnline()) {
                            msg.send(p, error == null ? "prestige.changed" : "prestige.error");
                            core.sounds().play(p, "error");
                            if (error == null) {
                                personal.remove(profile); // read the real row again
                                loadPersonal(p);
                            }
                        }
                    }
                    if (done != null && p.isOnline()) {
                        done.run();
                    }
                }));
    }

    /** Owner 7 Nov 2026 (plans/HOE_IMPROVEMENTS.md 18, 27): a key every prestige, and the hoe's titles. */
    private void prestigeRewards(Player p, UUID profile, Kind kind, ToolStore.Personal now, boolean rebirth) {
        String crate = rebirth || now.prestige() % 10 == 0 ? config.prestigeKeyCrateTenth : config.prestigeKeyCrate;
        if (crate != null && !crate.isBlank()) {
            core.services().optional(net.skybornmc.skyblock.common.crate.CrateKeys.class).ifPresent(k ->
                    k.giveKeys(profile, crate, 1, "tool_prestige", Actor.player(p)));
            if (p.isOnline()) {
                msg.send(p, "prestige.key", Msg.p("crate", raw("enchant.crate." + crate)));
            }
        }
        String title = hoeTitle(kind, now.prestige(), rebirth, now.rebirth());
        if (title != null) {
            core.services().optional(net.skybornmc.skyblock.common.cosmetics.Cosmetics.class).ifPresent(c ->
                    c.grantTitle(p.getUniqueId(), title, "tool_prestige:" + kind.id()).exceptionally(e -> {
                        core.plugin().getLogger().warning("Island tools: title " + title + " could not be given: " + e.getMessage());
                        return false;
                    }));
        }
    }

    /** The hoe's prestige titles: Farmhand 1, Harvester 10, Field Lord 25, Harvest King 50, Reborn Reaper (first rebirth). Pure. */
    static String hoeTitle(Kind kind, int prestige, boolean rebirth, int rebirths) {
        if (kind != Kind.HOE) {
            return null;
        }
        if (rebirth) {
            return rebirths == 1 ? "reborn_reaper" : null;
        }
        return switch (prestige) {
            case 1 -> "farmhand";
            case 10 -> "harvester_hoe";
            case 25 -> "field_lord";
            case 50 -> "harvest_king";
            default -> null;
        };
    }

    private void prestiged(Player p, UUID profile, Kind kind, ToolStore.Personal before, boolean rebirth) {
        Map<Kind, ToolStore.Personal> map = personal.get(profile);
        if (map != null) {
            Map<Kind, Long> deltas = personalDelta.get(profile);
            long since = deltas == null ? 0 : deltas.getOrDefault(kind, 0L); // XP after the click: written to the new prestige
            map.put(kind, before.next(rebirth).withXp(since));
        }
        UUID island = island(p);
        if (island != null) {
            refreshHandles(island, kind);
        }
        ToolStore.Personal now = before.next(rebirth);
        String tool = raw("item.name." + kind.id());
        String stars = ToolRules.stars(now.prestige(), config.maxStars);
        long bonus = Math.round(now.prestige() * config.prestigeBonus + now.rebirth() * config.rebirthBonus);
        if (p.isOnline()) {
            if (rebirth) {
                msg.title(p, "rebirth.done", Msg.p("tool", tool), Msg.number("rebirth", now.rebirth()));
                msg.send(p, "rebirth.chat", Msg.p("tool", tool), Msg.number("rebirth", now.rebirth()), Msg.number("bonus", bonus));
            } else {
                msg.title(p, "prestige.done", Msg.p("tool", tool), Msg.p("stars", stars), Msg.number("prestige", now.prestige()));
                msg.send(p, "prestige.chat", Msg.p("tool", tool), Msg.p("stars", stars), Msg.number("prestige", now.prestige()),
                        Msg.number("bonus", bonus), Msg.number("cap", GrindEnchants.capByLevels(100, now.prestige() * config.maxLevel + 1,
                                1, config.enchantCapFullLevels, config.enchantCapExponent, 0))); // tools v6: percent of every track
            }
            core.sounds().play(p, "tool-prestige");
        }
        prestigeRewards(p, profile, kind, now, rebirth);
        // Milestones are worth telling the island and the server about (psychology: others see the progress).
        if (rebirth || now.prestige() % 10 == 0) {
            for (Player other : Bukkit.getOnlinePlayers()) {
                if (other != p) {
                    msg.send(other, rebirth ? "rebirth.broadcast" : "prestige.broadcast", Msg.p("player", p.getName()), Msg.p("tool", tool),
                            Msg.number("prestige", now.prestige()), Msg.number("rebirth", now.rebirth()));
                }
            }
        }
        core.audit().logPlayer(AuditCategory.ITEM, rebirth ? "island_tool_rebirth" : "island_tool_prestige", Actor.player(p),
                p.getUniqueId(), p.getName(), null, p.getLocation(), Map.of("kind", kind.id(), "profile", profile.toString(),
                        "prestige", now.prestige(), "rebirth", now.rebirth()));
    }

    // ------------------------------------------------------------------ commands

    private void registerCommands() {
        core.commands().extend("is", CommandSpec.sub("tools").description("Your island's hoe and pickaxe: levels, perks, prestige and the hoe storage")
                .permission(PERM).playerOnly().example("is tools")
                .executes(ctx -> open(ctx.player())));
        Arg<Player> target = Args.onlinePlayer("player");
        Arg<Kind> tool = Args.choice("tool", Kind.class);
        Arg<Long> amount = Args.amount("amount");
        core.commands().register(CommandSpec.command("islandtools").description("Island tools (staff: give, reset, XP)")
                .category("Island").permission(PERM).playerOnly().aliases("itools").example("islandtools")
                .executes(ctx -> open(ctx.player()))
                .sub(CommandSpec.sub("reset").description("Staff: a player's island tool handles and hoe storage are reset (new handles on the next join)")
                        .permission(PERM_ADMIN).arg(target).example("islandtools reset Steve")
                        .executes(ctx -> reset(ctx.sender(), ctx.get(target))))
                .sub(CommandSpec.sub("resetisland").description("Staff: the tools of a player's island back to level 1 without perks or prestige")
                        .permission(PERM_ADMIN).arg(target).example("islandtools resetisland Steve")
                        .executes(ctx -> resetIsland(ctx.sender(), ctx.get(target))))
                .sub(CommandSpec.sub("addxp").description("Staff: tool XP for a player's island tool (to test levels and prestige)")
                        .permission(PERM_ADMIN).arg(target).arg(tool).arg(amount).example("islandtools addxp Steve hoe 5000")
                        .executes(ctx -> addXp(ctx.sender(), ctx.get(target), ctx.get(tool), ctx.get(amount))))
                .sub(CommandSpec.sub("give").description("Staff: give a player the handle of their island's tool (never a second one)")
                        .permission(PERM_ADMIN).arg(target).arg(tool).example("islandtools give Steve pickaxe")
                        .executes(ctx -> giveHandle(ctx.get(target), ctx.get(tool)))));
        // Feedback 4 Nov 2026 (B7): /tool opens the held tool's menu (Bedrock: a click in the air may not reach us). The
        // custom tools module owns /tool when it is on and asks HeldToolMenu for an island tool; else it is ours.
        core.services().register(ID, net.skybornmc.skyblock.common.item.HeldToolMenu.class, this::openHeld);
        core.services().register(ID, net.skybornmc.skyblock.common.tools.ToolTop.class, (kind, limit) -> core.database().async(c ->
                ToolStore.top(c, kind, limit, config.rebirthBoardWeight,
                        net.skybornmc.skyblock.common.profile.ProfileService.of(core.services())::bestPerOwner))); // owner 7 Nov 2026: Top Tools
        core.services().register(ID, net.skybornmc.skyblock.common.tools.IslandToolLevels.class, (player, kind) -> Kind.of(kind)
                .map(k -> view(player, k)).map(v -> level(v).level()).orElse(0)); // FINISH_PLAN A: quest steps count levels you have
        if (!core.commands().registered("tool")) {
            core.commands().register(CommandSpec.command("tool").description("Open the menu of the island tool in your hand")
                    .category("Island").permission(PERM_TOOL).playerOnly().example("tool")
                    .executes(ctx -> {
                        if (!openHeld(ctx.player())) {
                            open(ctx.player()); // no island tool in hand: both island tools
                        }
                    }));
        }
    }

    /** The menu of the island tool in the main hand; false when the item in hand is not an island tool. */
    boolean openHeld(Player p) {
        ItemStack hand = p.getInventory().getItemInMainHand();
        Kind kind = ToolItems.kindOf(hand);
        if (kind == null) {
            return false;
        }
        if (!config.enabled) {
            msg.send(p, "disabled");
        } else if (island(p) == null) {
            msg.send(p, "buy.no-island");
        } else if (!loaded(p)) {
            msg.send(p, "loading");
            load(p);
        } else {
            core.menus().open(p, new ToolMenu(this, kind));
        }
        return true;
    }

    void open(Player p) {
        if (!config.enabled) {
            throw new CommandFailure(ID, "disabled");
        }
        if (island(p) == null) {
            throw new CommandFailure(ID, "buy.no-island");
        }
        if (!loaded(p)) {
            load(p);
            throw new CommandFailure(ID, "loading");
        }
        core.menus().open(p, new ToolsMenu(this));
    }

    private void reset(org.bukkit.command.CommandSender sender, Player target) {
        UUID profile = profile(target);
        PlayerInventory inv = target.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            if (ToolItems.isTool(inv.getItem(i))) {
                inv.setItem(i, null);
            }
        }
        storage.unload(profile);
        personal.remove(profile);
        personalDelta.remove(profile);
        store.resetPersonal(profile).thenCompose(v -> store.reset(profile)).whenComplete((v, e) -> Bukkit.getScheduler().runTask(core.plugin(), () -> {
            if (target.isOnline()) {
                load(target);
            }
            msg.send(sender, "admin.reset", Msg.p("player", target.getName()));
        }));
        core.audit().logPlayer(AuditCategory.STAFF, "island_tools_reset", sender instanceof Player sp ? Actor.player(sp) : Actor.SYSTEM,
                target.getUniqueId(), target.getName(), null, null, Map.of("profile", profile.toString()));
    }

    private void resetIsland(org.bukkit.command.CommandSender sender, Player target) {
        UUID island = island(target);
        if (island == null) {
            msg.send(sender, "admin.no-island", Msg.p("player", target.getName()));
            return;
        }
        xpDelta.remove(island);
        store.resetIsland(island).whenComplete((v, e) -> Bukkit.getScheduler().runTask(core.plugin(), () -> {
            if (e != null) {
                msg.send(sender, "buy.error");
                return;
            }
            progs.remove(island);
            ensure(island, () -> { });
            msg.send(sender, "admin.reset-island", Msg.p("player", target.getName()));
        }));
        core.audit().logPlayer(AuditCategory.STAFF, "island_tools_reset_island", sender instanceof Player sp ? Actor.player(sp) : Actor.SYSTEM,
                target.getUniqueId(), target.getName(), null, null, Map.of("island", island.toString()));
    }

    private void addXp(org.bukkit.command.CommandSender sender, Player target, Kind kind, long amount) {
        UUID island = island(target);
        if (island == null) {
            msg.send(sender, "admin.no-island", Msg.p("player", target.getName()));
            return;
        }
        ensure(island, () -> {
            if (personal(profile(target), kind) == null) {
                msg.send(sender, "loading");
                return;
            }
            addXp(target, kind, amount);
            ToolStore.Prog prog = view(target, kind);
            msg.send(sender, "admin.xp", Msg.p("player", target.getName()), Msg.p("tool", raw("item.name." + kind.id())),
                    Msg.number("amount", amount), Msg.number("level", prog == null ? 1 : level(prog).level()));
            core.audit().logPlayer(AuditCategory.STAFF, "island_tool_xp", sender instanceof Player sp ? Actor.player(sp) : Actor.SYSTEM,
                    target.getUniqueId(), target.getName(), amount, null, Map.of("profile", profile(target).toString(), "kind", kind.id()));
        });
    }

    /** Island locations for listeners: the island at a block (or null). */
    UUID islandAt(Location loc) {
        return islands().flatMap(i -> i.islandAt(loc)).map(IslandService.IslandInfo::id).orElse(null);
    }
}
