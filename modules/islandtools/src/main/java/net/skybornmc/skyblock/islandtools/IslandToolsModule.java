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
        core.services().optional(BonusService.class).ifPresent(b -> b.register("island_tool", (family, ctx) ->
                family == BonusFamily.EXTRA_DROPS ? dropsBonus(ctx.item()) : 0));
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
        for (Player p : Bukkit.getOnlinePlayers()) {
            load(p);
        }
    }

    private void parse() {
        upgrades = Upgrades.parse(config.perks, w -> core.plugin().getLogger().warning(w));
        bands = ToolRules.bands(config.sweepBands);
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
        return island != null && progs.containsKey(island);
    }

    ToolRules.Level level(ToolStore.Prog prog) {
        return ToolRules.levelOf(prog.xp(), prog.prestige(), config.maxLevel, config.xpBase, config.xpExponent, config.xpPrestigeFactor);
    }

    long xpToMax(int prestige) {
        return ToolRules.xpToMax(prestige, config.maxLevel, config.xpBase, config.xpExponent, config.xpPrestigeFactor);
    }

    /** Level of the first perk with this effect on the island's tool (0 = none). */
    int effect(UUID island, Kind kind, Upgrades.Effect effect) {
        ToolStore.Prog prog = prog(island, kind);
        if (prog == null) {
            return 0;
        }
        return upgrades.byEffect(kind, effect).map(d -> prog.level(d.id())).orElse(0);
    }

    double prestigeBonus(UUID island, Kind kind) {
        ToolStore.Prog prog = prog(island, kind);
        return ToolRules.prestigeBonus(prog == null ? 0 : prog.prestige(), config.prestigeBonus);
    }

    double priceFactor(ToolStore.Prog prog) {
        return ToolRules.priceFactor(prog.prestige(), config.prestigeDiscount, config.priceFloor);
    }

    /** Sweep/Vein chance (percent) and blocks of the island's tool; null when locked. */
    ToolRules.Band band(UUID island, Kind kind) {
        ToolStore.Prog prog = prog(island, kind);
        return prog == null ? null : ToolRules.band(bands, prog.prestige());
    }

    double sweepChance(UUID island, Kind kind) {
        ToolRules.Band band = band(island, kind);
        Upgrades.Def def = upgrades.byEffect(kind, Upgrades.Effect.SWEEP).orElse(null);
        return def == null ? 0 : ToolRules.sweepChance(band, effect(island, kind, Upgrades.Effect.SWEEP), def.max());
    }

    /** Growth (hoe perk): percent faster crop growth on {@code island}. */
    double growthPercent(UUID island) {
        return effect(island, Kind.HOE, Upgrades.Effect.GROWTH) * config.growthPerLevel;
    }

    /** Sell Bonus (perk) in percent for sales made by the island's tool. */
    double sellBonus(UUID island, Kind kind) {
        return effect(island, kind, Upgrades.Effect.SELL) * config.sellPerLevel;
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
        String effect = kind == Kind.HOE ? config.hoeBoostEffect : config.pickaxeBoostEffect;
        return core.services().optional(ProgressionService.class).map(s -> s.upgradeEffectValue(island, effect)).orElse(0.0);
    }

    /** The tool's own Extra Drops (percent) for the bonus service, only for a handle of a loaded island. */
    private double dropsBonus(ItemStack item) {
        Kind kind = ToolItems.kindOf(item);
        UUID island = ToolItems.islandOf(item);
        return kind == null ? 0 : effect(island, kind, Upgrades.Effect.DROPS) * config.dropsPerLevel;
    }

    /** The shown value of a perk level: roman numerals for enchant-like perks, "132/1000" for tracks. */
    String levelText(Upgrades.Def def, int level) {
        if (def.effect() == Upgrades.Effect.ENCHANT || def.effect() == Upgrades.Effect.HASTE) {
            return roman(level);
        }
        return def.max() == 1 ? raw("menu.owned") : Format.number(level) + "<c:muted>/" + Format.number(def.max()) + "</c:muted>";
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
        storage.unload(profile(p));
        busy.remove(p.getUniqueId());
    }

    void unloadProfile(UUID profile) {
        if (profile != null) {
            storage.unload(profile);
        }
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
            if (!island.equals(island(p))) {
                continue;
            }
            PlayerInventory inv = p.getInventory();
            for (int i = 0; i < inv.getSize(); i++) {
                ItemStack s = inv.getItem(i);
                Kind kind = ToolItems.kindOf(s);
                if (kind == null || (only != null && kind != only) || !island.equals(ToolItems.islandOf(s))) {
                    continue;
                }
                ItemStack copy = s.clone();
                items.applyEnchants(copy, kind, prog(island, kind));
                core.items().render(copy);
                inv.setItem(i, copy);
            }
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
        UUID island = island(p);
        ToolStore.Prog prog = prog(island, kind);
        if (prog == null) {
            return;
        }
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        double bonus = prestigeBonus(island, kind);
        ToolRules.Level before = level(prog);
        if (!before.maxed()) {
            double wisdom = 1 + effect(island, kind, Upgrades.Effect.WISDOM) * config.wisdomPerLevel / 100.0;
            long xp = ToolRules.roll(config.xpPerBlock * bonus * wisdom, rnd.nextDouble());
            if (xp > 0) {
                ToolStore.Prog next = prog.withXp(prog.xp() + xp);
                progs.get(island).put(kind, next);
                xpDelta.computeIfAbsent(island, k -> new ConcurrentHashMap<>()).merge(kind, xp, Long::sum);
                dirty.add(island);
                ToolRules.Level after = level(next);
                if (after.level() > before.level()) {
                    levelUp(p, island, kind, after);
                }
            }
        }
        if (swept && !config.sweepShards) {
            return;
        }
        double chance = (kind == Kind.HOE ? config.shardHoe : config.shardPickaxe) / 100.0;
        double finder = 1 + effect(island, kind, Upgrades.Effect.SHARDS) * config.shardFinderPerLevel / 100.0;
        int shards = ToolRules.roll(chance * finder * bonus, rnd.nextDouble());
        int lucky = effect(island, kind, Upgrades.Effect.LUCKY);
        if (lucky > 0 && rnd.nextDouble(100) < lucky * config.luckyPerLevel) {
            int pouch = (int) Math.round(rnd.nextInt(Math.max(1, config.luckyMin), Math.max(config.luckyMin, config.luckyMax) + 1) * bonus);
            shards += pouch;
            msg.title(p, "lucky.found", Msg.mm("amount", shardText(kind, pouch)), Msg.p("shards", raw("shards.name." + kind.id())));
            core.sounds().play(p, "tool-lucky");
            core.audit().logPlayer(AuditCategory.ECONOMY, "island_tool_lucky", Actor.SYSTEM, p.getUniqueId(), p.getName(), (long) pouch,
                    p.getLocation(), Map.of("kind", kind.id(), "currency", kind.shards().key()));
        }
        if (shards > 0) {
            pendingShards.computeIfAbsent(profile(p), k -> new ConcurrentHashMap<>()).merge(kind.shards(), (long) shards, Long::sum);
        }
    }

    private void levelUp(Player p, UUID island, Kind kind, ToolRules.Level after) {
        String tool = raw("item.name." + kind.id());
        boolean max = after.maxed();
        if (config.levelTitle) {
            msg.title(p, max ? "level.max" : "level.up", Msg.p("tool", tool), Msg.number("level", after.level()));
        }
        core.sounds().play(p, max ? "tool-max" : "tool-level-up");
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other != p && island.equals(island(other))) {
                msg.actionBar(other, "level.island", Msg.p("tool", tool), Msg.number("level", after.level()));
            }
        }
        if (max) {
            msg.send(p, "level.can-prestige", Msg.p("tool", tool));
        }
        refreshHandles(island, kind);
    }

    /** Staff: adds tool XP to an island's tool (testing the levels and prestige). */
    void addXp(UUID island, Kind kind, long xp) {
        ToolStore.Prog prog = prog(island, kind);
        if (prog == null || xp <= 0) {
            return;
        }
        progs.get(island).put(kind, prog.withXp(prog.xp() + xp));
        xpDelta.computeIfAbsent(island, k -> new ConcurrentHashMap<>()).merge(kind, xp, Long::sum);
        refreshHandles(island, kind);
    }

    private void flushXp() {
        for (UUID island : List.copyOf(dirty)) {
            dirty.remove(island);
            if (progs.containsKey(island)) {
                refreshHandles(island, null);
            }
        }
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
                            msg.actionBar(p, "shards.found." + currency.key(), Msg.number("amount", n));
                            core.sounds().play(p, "shard");
                        }
                    }
                }));
            });
        }
    }

    /** True when this drop is taken by Auto-Sell (the island's tool has it and the item can be sold). */
    boolean autoSell(Player p, Kind kind, ItemStack stack) {
        UUID island = island(p);
        if (effect(island, kind, Upgrades.Effect.AUTOSELL) <= 0) {
            return false;
        }
        var sell = core.services().optional(net.skybornmc.skyblock.common.sell.SellService.class).orElse(null);
        if (sell == null || !sell.sellable(stack)) {
            return false;
        }
        autoSell.computeIfAbsent(p.getUniqueId(), k -> new ConcurrentHashMap<>()).merge(stack.getType(), stack.getAmount(), Integer::sum);
        autoSellBonus.put(p.getUniqueId(), sellBonus(island, kind));
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
        ToolStore.Prog prog = prog(island, kind);
        if (prog == null) {
            return new Refusal("loading", new net.kyori.adventure.text.minimessage.tag.resolver.TagResolver[0]);
        }
        ToolRules.Level lv = level(prog);
        String perk = raw("upgrade." + def.id() + ".name");
        if (lv.level() < def.unlock()) {
            return new Refusal("buy.locked-level", new net.kyori.adventure.text.minimessage.tag.resolver.TagResolver[]{
                    Msg.p("perk", perk), Msg.number("level", def.unlock()), Msg.number("have", lv.level())});
        }
        if (prog.prestige() < def.prestige() || (def.effect() == Upgrades.Effect.SWEEP && band(island, kind) == null)) {
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

    /** Buys up to {@code count} levels of a perk: one shard payment and the island row change in one transaction. */
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
        ToolStore.Prog before = prog(island, kind);
        int have = before.level(def.id());
        int n = def.buyable(have, count);
        long cost = price(before, def, n);
        long balance = economy.cached(p.getUniqueId(), kind.shards()).orElse(Long.MAX_VALUE);
        if (balance < cost) {
            busy.remove(p.getUniqueId());
            poor(p, kind, cost, balance);
            return;
        }
        Map<String, Integer> levels = new java.util.TreeMap<>(before.levels());
        levels.put(def.id(), have + n);
        String newLevels = Upgrades.encode(levels);
        UUID batch = UUID.randomUUID();
        Transaction tx = Transaction.withdraw(AccountRef.player(profile), kind.shards(), cost, TxReason.ISLAND_TOOL_UPGRADE)
                .actor(Actor.player(p)).reference("island_tool:" + batch).idempotencyKey("island_tool:" + batch)
                .detail("kind", kind.id()).detail("upgrade", def.id()).detail("levels", n);
        economy.executeBatch(List.of(tx), c -> ToolStore.buy(c, island, kind, before.version(), newLevels))
                .whenComplete((BatchResult result, Throwable error) -> Bukkit.getScheduler().runTask(core.plugin(), () -> {
                    busy.remove(p.getUniqueId());
                    if (error != null || result == null || !result.ok()) {
                        failed(p, kind, cost, result == null ? null : result.status(), error);
                    } else {
                        bought(p, island, kind, def, have, n, cost, newLevels);
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

    private void bought(Player p, UUID island, Kind kind, Upgrades.Def def, int have, int n, long cost, String newLevels) {
        Map<Kind, ToolStore.Prog> map = progs.get(island);
        ToolStore.Prog now = map == null ? null : map.get(kind);
        if (map != null && now != null) {
            map.put(kind, now.withUpgrades(newLevels));
        }
        refreshHandles(island, kind);
        String perk = raw("upgrade." + def.id() + ".name");
        String tool = raw("item.name." + kind.id());
        String level = levelText(def, have + n);
        msg.send(p, "buy.done", Msg.p("perk", perk), Msg.mm("level", level), Msg.p("tool", tool), Msg.mm("cost", shardText(kind, cost)));
        core.sounds().play(p, "unlock");
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other != p && island.equals(island(other))) {
                msg.send(other, "buy.island", Msg.p("player", p.getName()), Msg.p("perk", perk), Msg.mm("level", level), Msg.p("tool", tool));
            }
        }
        core.audit().logPlayer(AuditCategory.ITEM, "island_tool_upgrade", Actor.player(p), p.getUniqueId(), p.getName(), cost,
                p.getLocation(), Map.of("kind", kind.id(), "upgrade", def.id(), "from", have, "to", have + n, "island", island.toString(),
                        "currency", kind.shards().key()));
    }

    // ------------------------------------------------------------------ prestige

    /** Why the player can't prestige the tool right now (null = they can). */
    Refusal prestigeRefusal(Player p, Kind kind) {
        UUID island = island(p);
        net.kyori.adventure.text.minimessage.tag.resolver.TagResolver[] none = new net.kyori.adventure.text.minimessage.tag.resolver.TagResolver[0];
        if (island == null) {
            return new Refusal("buy.no-island", none);
        }
        ToolStore.Prog prog = prog(island, kind);
        if (prog == null) {
            return new Refusal("loading", none);
        }
        String tool = raw("item.name." + kind.id());
        IslandService.Role role = islands().flatMap(i -> i.role(island, p.getUniqueId())).orElse(null);
        if (role == null || config.prestigeRoles.stream().noneMatch(r -> r.equalsIgnoreCase(role.name()))) {
            return new Refusal("prestige.not-owner", new net.kyori.adventure.text.minimessage.tag.resolver.TagResolver[]{Msg.p("tool", tool)});
        }
        ToolRules.Level lv = level(prog);
        if (!lv.maxed()) {
            return new Refusal("prestige.not-max", new net.kyori.adventure.text.minimessage.tag.resolver.TagResolver[]{Msg.p("tool", tool),
                    Msg.number("level", lv.level()), Msg.number("max", config.maxLevel)});
        }
        if (prestiging.contains(island + ":" + kind.id())) {
            return new Refusal("prestige.busy", none);
        }
        return null;
    }

    /**
     * Prestige (after the confirm screen): resets the island's tool (XP and perks), prestige + 1, and takes every member's
     * shards of that type (online and offline) in the same database transaction.
     */
    void prestige(Player p, Kind kind, Runnable done) {
        Refusal no = prestigeRefusal(p, kind);
        EconomyService economy = core.services().optional(EconomyService.class).orElse(null);
        if (no == null && economy == null) {
            no = new Refusal("buy.no-economy", new net.kyori.adventure.text.minimessage.tag.resolver.TagResolver[0]);
        }
        if (no != null) {
            msg.send(p, no.key(), no.tags());
            core.sounds().play(p, "error");
            return;
        }
        UUID island = island(p);
        String key = island + ":" + kind.id();
        if (!prestiging.add(key)) {
            return;
        }
        ToolStore.Prog before = prog(island, kind);
        Map<Kind, Long> deltas = xpDelta.get(island);
        Long removed = deltas == null ? null : deltas.remove(kind);
        long pending = removed == null ? 0 : removed;
        List<UUID> members = new ArrayList<>(islands().map(i -> i.members(island).keySet()).orElse(Set.of()));
        for (UUID member : members) {
            Map<Currency, Long> unpaid = pendingShards.get(member);
            if (unpaid != null) {
                unpaid.remove(kind.shards()); // found in the last seconds: gone with the rest
            }
        }
        attemptPrestige(p, economy, island, kind, before, pending, members, 0, done);
    }

    private void attemptPrestige(Player p, EconomyService economy, UUID island, Kind kind, ToolStore.Prog before, long pending,
                                 List<UUID> members, int tries, Runnable done) {
        Currency currency = kind.shards();
        List<CompletableFuture<Long>> reads = new ArrayList<>();
        for (UUID member : members) {
            reads.add(economy.balance(AccountRef.player(member), currency));
        }
        CompletableFuture.allOf(reads.toArray(new CompletableFuture[0])).thenCompose(v -> {
            UUID batch = UUID.randomUUID();
            List<Transaction> txs = new ArrayList<>();
            Map<String, Object> taken = new java.util.TreeMap<>();
            for (int i = 0; i < members.size(); i++) {
                long have = reads.get(i).join();
                if (have > 0) {
                    txs.add(Transaction.withdraw(AccountRef.player(members.get(i)), currency, have, TxReason.ISLAND_TOOL_PRESTIGE)
                            .actor(Actor.player(p)).reference("island_tool_prestige:" + batch)
                            .idempotencyKey("island_tool_prestige:" + batch + ":" + i).detail("kind", kind.id()));
                    taken.put(members.get(i).toString(), have);
                }
            }
            long need = xpToMax(before.prestige());
            return economy.executeBatch(txs, c -> ToolStore.prestige(c, island, kind, before, pending, need))
                    .thenApply(result -> Map.entry(result, taken));
        }).whenComplete((entry, error) -> Bukkit.getScheduler().runTask(core.plugin(), () -> {
            BatchResult result = entry == null ? null : entry.getKey();
            if (error == null && result != null && result.ok()) {
                prestiging.remove(island + ":" + kind.id());
                prestiged(p, island, kind, before, entry.getValue());
            } else if (error == null && result != null && result.status() == TxResult.Status.INSUFFICIENT_FUNDS && tries < 2) {
                attemptPrestige(p, economy, island, kind, before, pending, members, tries + 1, done); // someone spent meanwhile
                return;
            } else {
                prestiging.remove(island + ":" + kind.id());
                if (pending > 0) {
                    xpDelta.computeIfAbsent(island, k -> new ConcurrentHashMap<>()).merge(kind, pending, Long::sum);
                }
                if (error != null) {
                    core.plugin().getLogger().log(Level.WARNING, "Island tools: a prestige of island " + island + " failed", error);
                }
                if (p.isOnline()) {
                    msg.send(p, result != null && result.status() == TxResult.Status.DUPLICATE ? "prestige.changed" : "prestige.error");
                    core.sounds().play(p, "error");
                }
            }
            if (done != null && p.isOnline()) {
                done.run();
            }
        }));
    }

    private void prestiged(Player p, UUID island, Kind kind, ToolStore.Prog before, Map<String, Object> taken) {
        Map<Kind, ToolStore.Prog> map = progs.get(island);
        if (map != null) {
            Map<Kind, Long> deltas = xpDelta.get(island);
            long since = deltas == null ? 0 : deltas.getOrDefault(kind, 0L); // XP after the click: written to the new prestige
            ToolStore.Prog next = before.prestiged();
            map.put(kind, next.withXp(since));
        }
        refreshHandles(island, kind);
        int prestige = before.prestige() + 1;
        String tool = raw("item.name." + kind.id());
        String stars = ToolRules.stars(prestige, config.maxStars);
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (island.equals(island(other))) {
                msg.title(other, "prestige.done", Msg.p("tool", tool), Msg.p("stars", stars), Msg.number("prestige", prestige));
                msg.send(other, "prestige.chat", Msg.p("player", p.getName()), Msg.p("tool", tool), Msg.p("stars", stars),
                        Msg.number("bonus", Math.round(prestige * config.prestigeBonus)), Msg.p("shards", raw("shards.name." + kind.id())));
                core.sounds().play(other, "tool-prestige");
            }
        }
        Map<String, Object> details = new java.util.HashMap<>(taken);
        details.put("kind", kind.id());
        details.put("island", island.toString());
        details.put("prestige", prestige);
        details.put("currency", kind.shards().key());
        core.audit().logPlayer(AuditCategory.ITEM, "island_tool_prestige", Actor.player(p), p.getUniqueId(), p.getName(), null,
                p.getLocation(), details);
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
        store.reset(profile).whenComplete((v, e) -> Bukkit.getScheduler().runTask(core.plugin(), () -> {
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
            addXp(island, kind, amount);
            ToolStore.Prog prog = prog(island, kind);
            msg.send(sender, "admin.xp", Msg.p("player", target.getName()), Msg.p("tool", raw("item.name." + kind.id())),
                    Msg.number("amount", amount), Msg.number("level", prog == null ? 1 : level(prog).level()));
            core.audit().logPlayer(AuditCategory.STAFF, "island_tool_xp", sender instanceof Player sp ? Actor.player(sp) : Actor.SYSTEM,
                    target.getUniqueId(), target.getName(), amount, null, Map.of("island", island.toString(), "kind", kind.id()));
        });
    }

    /** Island locations for listeners: the island at a block (or null). */
    UUID islandAt(Location loc) {
        return islands().flatMap(i -> i.islandAt(loc)).map(IslandService.IslandInfo::id).orElse(null);
    }
}
