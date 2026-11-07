package net.skybornmc.skyblock.islandtools;

import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.audit.AuditCategory;
import net.skybornmc.skyblock.common.crate.CrateKeys;
import net.skybornmc.skyblock.common.economy.AccountRef;
import net.skybornmc.skyblock.common.economy.BatchResult;
import net.skybornmc.skyblock.common.economy.Currency;
import net.skybornmc.skyblock.common.economy.EconomyService;
import net.skybornmc.skyblock.common.economy.Transaction;
import net.skybornmc.skyblock.common.economy.TxReason;
import net.skybornmc.skyblock.common.economy.TxResult;
import net.skybornmc.skyblock.common.grind.GrindBreakEvent;
import net.skybornmc.skyblock.common.grind.GrindEvents;
import net.skybornmc.skyblock.common.grind.GrindProgress;
import net.skybornmc.skyblock.common.profile.ProfileDeletedEvent;
import net.skybornmc.skyblock.common.sell.SellPrice;
import net.skybornmc.skyblock.common.sell.SellService;
import net.skybornmc.skyblock.common.skills.SkillType;
import net.skybornmc.skyblock.common.skills.SkillXpGainEvent;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.util.Format;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

/**
 * Owner 6 Nov 2026 (plans/TOOLS_V3.md): the personal grind enchants at work. Levels per profile (cached while online),
 * bought with the player's own shards, and the procs on a natural break with the island tool at {@code /farm} or
 * {@code /mine} only. Swept/veined and blasted blocks never proc (no chains). Money, shards and keys are batched.
 */
final class EnchantProcs implements Listener {

    private final IslandToolsModule m;
    private volatile GrindEnchants defs = GrindEnchants.parse(List.of(), w -> { });
    /** Profile → {@code kind:id} → level, while the player is online. */
    private final Map<UUID, Map<String, Integer>> levels = new ConcurrentHashMap<>();
    private final Set<UUID> loading = ConcurrentHashMap.newKeySet();
    private final Set<UUID> busy = ConcurrentHashMap.newKeySet();
    /** Player → {@code kind:id} → last proc (only enchants with a cooldown). */
    private final Map<UUID, Map<String, Long>> lastProc = new ConcurrentHashMap<>();
    private final Map<UUID, Long> pendingCash = new ConcurrentHashMap<>();
    /** Player → streaks, Shard Echo and the pity counter (main thread). */
    private final Map<UUID, State> states = new ConcurrentHashMap<>();

    /** Per player and tool: the two streaks, Shard Echo and Lucky Streak's blocks since the last rare find. */
    static final class State {
        long shardAt;
        int shardStreak;
        long xpAt;
        int xpStreak;
        long echoUntil;
        double echoPercent;
        int sinceRare;
        /** Tools v6: Shard Storm and Booster Find run until these times; Golden Touch and Duplicator count drops down. */
        long stormUntil;
        long feverUntil;
        int goldenLeft;
        int dupLeft;
    }
    private final Map<UUID, Long> pendingCrystals = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> pendingKeys = new ConcurrentHashMap<>();

    EnchantProcs(IslandToolsModule m) {
        this.m = m;
    }

    void parse(List<String> lines) {
        java.util.function.Consumer<String> warn = w -> m.core().plugin().getLogger().warning(w);
        defs = GrindEnchants.parse(lines, GrindEnchants.Breakthrough.parse(m.config().enchantBreakthroughs, warn), warn);
    }

    GrindEnchants defs() {
        return defs;
    }

    // ------------------------------------------------------------------ levels

    void load(Player p) {
        UUID profile = m.profile(p);
        if (levels.containsKey(profile) || !loading.add(profile)) {
            return;
        }
        m.core().database().async(c -> EnchantStore.load(c, profile)).whenComplete((map, error) ->
                Bukkit.getScheduler().runTask(m.core().plugin(), () -> {
                    loading.remove(profile);
                    if (error != null) {
                        m.core().plugin().getLogger().log(Level.WARNING, "Island tools: could not load the grind enchants of " + profile, error);
                        return;
                    }
                    if (p.isOnline() && profile.equals(m.profile(p))) {
                        levels.put(profile, new ConcurrentHashMap<>(map));
                        m.perksLoaded(p, profile); // tools v5: personal perks (and their one-time seed from the island)
                    }
                }));
    }

    void unload(Player p) {
        unloadProfile(m.profile(p));
        lastProc.remove(p.getUniqueId());
        busy.remove(p.getUniqueId());
        states.remove(p.getUniqueId());
    }

    void unloadProfile(UUID profile) {
        if (profile != null) {
            levels.remove(profile);
        }
    }

    boolean loaded(Player p) {
        return levels.containsKey(m.profile(p));
    }

    /** Owner 7 Nov 2026 (tools v5): the cached rows of a profile (enchants and personal perks), null while loading. */
    Map<String, Integer> rows(UUID profile) {
        return profile == null ? null : levels.get(profile);
    }

    int level(Player p, GrindEnchants.Def def) {
        Map<String, Integer> map = levels.get(m.profile(p));
        return map == null ? 0 : Math.min(def.max(), map.getOrDefault(def.key(), 0)); // a level above a lowered max counts as the max
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeleted(ProfileDeletedEvent event) {
        UUID profile = event.profile().id();
        levels.remove(profile);
        m.core().database().transaction(c -> {
            EnchantStore.delete(c, profile);
            return null;
        });
        m.store().deletePersonal(profile); // the own tool levels go with the profile
    }

    // ------------------------------------------------------------------ buying

    /** Why a level can't be bought now: a message key (null = it can). */
    String refusal(Player p, GrindEnchants.Def def) {
        if (!m.config().enabled || !m.config().enchantsEnabled) {
            return "enchant.disabled";
        }
        if (m.island(p) == null) {
            return "buy.no-island";
        }
        if (!loaded(p)) {
            return "loading";
        }
        int lv = level(p, def);
        if (lv >= def.max()) {
            return "enchant.maxed";
        }
        ToolStore.Prog prog = m.view(p, def.kind()); // your own tool level and prestige
        if (prog == null) {
            return "loading";
        }
        if (m.lifetimeLevel(prog) < def.unlockLevel()) { // tools v6: once reached, a prestige never locks it again
            return "enchant.locked-level";
        }
        if (m.capPrestige(prog) < def.unlockPrestige()) {
            return "enchant.locked-prestige";
        }
        return lv >= cap(p, def) ? "enchant.capped" : null;
    }

    /** The highest level the player can have now (tools v6: it grows with the tool's lifetime levels, faster near the top). */
    int cap(Player p, GrindEnchants.Def def) {
        ToolStore.Prog prog = m.view(p, def.kind());
        return GrindEnchants.capByLevels(def.max(), prog == null ? 0 : m.lifetimeLevel(prog), m.config().enchantCapBase,
                m.config().enchantCapFullLevels, m.config().enchantCapExponent, mastery(p, def.kind()));
    }

    /** Owner 6-7 Nov 2026 (Area Masteries): the island's Farm or Mine Mastery adds enchant levels to the cap. */
    double mastery(Player p, Kind kind) {
        java.util.UUID island = m.island(p);
        if (island == null) {
            return 0;
        }
        return m.core().services().optional(net.skybornmc.skyblock.common.progression.ProgressionService.class)
                .map(s -> s.masteryEffectValue(island, switch (kind) {
                    case HOE -> "farm-mastery";
                    case PICKAXE -> "mine-mastery";
                    case ROD -> "fish-mastery";
                    case SWORD -> "combat-mastery";
                })).orElse(0.0);
    }

    /** The placeholders of a refusal message. */
    net.kyori.adventure.text.minimessage.tag.resolver.TagResolver[] refusalTags(Player p, GrindEnchants.Def def) {
        ToolStore.Prog prog = m.view(p, def.kind());
        int prestige = prog == null ? 0 : prog.prestige();
        return new net.kyori.adventure.text.minimessage.tag.resolver.TagResolver[]{Msg.p("enchant", name(def)),
                Msg.number("level", def.unlockLevel()), Msg.number("prestige", Math.max(1, def.unlockPrestige())),
                Msg.number("have", prog == null ? 0 : m.level(prog).level()), Msg.number("cap", cap(p, def)),
                Msg.number("next", prestige + 1), Msg.number("tool", def.max()), Msg.p("tool", m.raw("item.name." + def.kind().id()))};
    }

    /** Buys up to {@code count} levels: one payment of the player's own shards and the level guard in one transaction. */
    void buy(Player p, Kind kind, String id, int count, Runnable done) {
        GrindEnchants.Def def = defs.get(kind, id).orElse(null);
        EconomyService economy = m.core().services().optional(EconomyService.class).orElse(null);
        if (def == null) {
            return;
        }
        String no = economy == null ? "buy.no-economy" : refusal(p, def);
        if (no != null) {
            m.msg().send(p, no, refusalTags(p, def));
            m.core().sounds().play(p, "error");
            return;
        }
        if (!busy.add(p.getUniqueId())) {
            m.msg().actionBar(p, "buy.busy");
            return;
        }
        UUID profile = m.profile(p);
        int have = level(p, def);
        int n = Math.max(0, Math.min(count, cap(p, def) - have));
        long cost = def.price(have, n);
        long balance = economy.cached(p.getUniqueId(), kind.shards()).orElse(Long.MAX_VALUE);
        if (balance < cost) {
            busy.remove(p.getUniqueId());
            poor(p, kind, cost, balance);
            return;
        }
        UUID batch = UUID.randomUUID();
        Transaction tx = Transaction.withdraw(AccountRef.player(profile), kind.shards(), cost, TxReason.GRIND_ENCHANT)
                .actor(Actor.player(p)).reference("grind_enchant:" + batch).idempotencyKey("grind_enchant:" + batch)
                .detail("kind", kind.id()).detail("enchant", def.id()).detail("levels", n);
        economy.executeBatch(List.of(tx), c -> EnchantStore.set(c, profile, kind, def.id(), have, have + n))
                .whenComplete((BatchResult result, Throwable error) -> Bukkit.getScheduler().runTask(m.core().plugin(), () -> {
                    busy.remove(p.getUniqueId());
                    if (error == null && result != null && result.ok()) {
                        bought(p, profile, kind, def, have, n, cost);
                    } else {
                        failed(p, kind, cost, result == null ? null : result.status(), error);
                    }
                    if (done != null && p.isOnline()) {
                        done.run();
                    }
                }));
    }

    private void bought(Player p, UUID profile, Kind kind, GrindEnchants.Def def, int have, int n, long cost) {
        Map<String, Integer> map = levels.get(profile);
        if (map != null) {
            map.put(def.key(), have + n);
        }
        if (p.isOnline()) {
            m.msg().actionBar(p, "enchant.bought", Msg.p("enchant", name(def)), Msg.number("level", have + n), Msg.number("max", def.max()),
                    Msg.mm("cost", m.shardText(kind, cost)));
            m.core().sounds().play(p, "enchant");
            // FINISH_PLAN batch A: buying grind enchant levels counts for "enchant" contracts (one per purchase)
            Bukkit.getPluginManager().callEvent(new net.skybornmc.skyblock.common.quest.GameActionEvent(p,
                    net.skybornmc.skyblock.common.quest.GameActionEvent.ENCHANT, 1));
            if (def.crosses(have, have + n)) { // owner 7 Nov 2026: a breakthrough is a moment
                String x = EnchantMenu.num(def.boost(have + n));
                m.msg().title(p, "enchant.breakthrough.title", Msg.p("enchant", name(def)), Msg.p("multiplier", x));
                m.msg().send(p, "enchant.breakthrough.chat", Msg.p("enchant", name(def)), Msg.p("multiplier", x),
                        Msg.number("level", have + n));
                m.core().sounds().play(p, "tool-max");
            }
        }
        m.core().audit().logPlayer(AuditCategory.ITEM, "grind_enchant", Actor.player(p), p.getUniqueId(), p.getName(), cost,
                p.getLocation(), Map.of("kind", kind.id(), "enchant", def.id(), "from", have, "to", have + n,
                        "currency", kind.shards().key()));
    }

    private void poor(Player p, Kind kind, long cost, long balance) {
        m.msg().send(p, "buy.poor", Msg.mm("cost", m.shardText(kind, cost)), Msg.mm("have", m.shardText(kind, Math.max(0, balance))),
                Msg.p("shards", m.raw("shards.name." + kind.id())));
        m.core().sounds().play(p, "error");
    }

    private void failed(Player p, Kind kind, long cost, TxResult.Status status, Throwable error) {
        if (!p.isOnline()) {
            return;
        }
        if (status == TxResult.Status.INSUFFICIENT_FUNDS) {
            poor(p, kind, cost, m.core().services().optional(EconomyService.class)
                    .map(e -> e.cached(p.getUniqueId(), kind.shards()).orElse(0L)).orElse(0L));
            return;
        }
        if (status == TxResult.Status.DUPLICATE) {
            // another device or a double click was faster: read the real levels again
            levels.remove(m.profile(p));
            load(p);
            m.msg().send(p, "enchant.changed");
        } else {
            if (error != null) {
                m.core().plugin().getLogger().log(Level.WARNING, "Island tools: a grind enchant purchase of " + p.getName() + " failed", error);
            }
            m.msg().send(p, "buy.error");
        }
        m.core().sounds().play(p, "error");
    }

    // ------------------------------------------------------------------ procs

    /**
     * One natural, relevant break with the island tool at /farm or /mine (not swept, not blasted): every enchant the
     * player has rolls once. {@code drops} are the block's drop entities (already the player's crop or ore).
     */
    void proc(Player p, Kind kind, Block origin, List<Item> drops) {
        if (!m.config().enchantsEnabled) {
            return;
        }
        Map<String, Integer> have = levels.get(m.profile(p));
        if (have == null || have.isEmpty()) {
            return;
        }
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        double frenzy = GrindEvents.multiplier(m.core().services(), p, GrindEvents.Kind.PROC_FRENZY);
        long now = System.currentTimeMillis();
        State st = state(p, kind);
        streaks(st, now);
        // Lucky Streak: after enough blocks without a rare find, the rare chances double until one comes.
        double pity = 1;
        GrindEnchants.Def pityDef = passive(kind, GrindEnchants.Effect.PITY);
        int pityLv = pityDef == null ? 0 : Math.min(pityDef.max(), have.getOrDefault(pityDef.key(), 0));
        if (pityLv > 0 && st.sinceRare >= Math.max(1, Math.round(pityDef.value(pityLv)))) {
            pity = 2;
        }
        st.sinceRare++;
        ongoing(p, kind, origin, drops, st, have, now);
        GrindEnchants.Def area = null;
        double areaValue = 0;
        for (GrindEnchants.Def def : defs.of(kind)) {
            int lv = Math.min(def.max(), have.getOrDefault(def.key(), 0));
            if (lv <= 0 || def.effect().passive()) {
                continue;
            }
            // Proc Frenzy doubles every proc but Keyfinder (owner: keys stay rare).
            double chance = def.chance(lv) * (def.effect() == GrindEnchants.Effect.KEY ? 1 : frenzy) * (def.rare() ? pity : 1);
            if (chance <= 0 || rnd.nextDouble(100) >= chance) {
                continue;
            }
            if (def.cooldownSeconds() > 0) {
                Map<String, Long> last = lastProc.computeIfAbsent(p.getUniqueId(), k -> new HashMap<>());
                Long at = last.get(def.key());
                if (at != null && now - at < def.cooldownSeconds() * 1000L) {
                    continue;
                }
                last.put(def.key(), now);
            }
            if (def.rare()) {
                st.sinceRare = 0;
            }
            if (def.effect() == GrindEnchants.Effect.AREA) {
                if (def.value(lv) > areaValue) { // one area enchant per break: the biggest wins
                    area = def;
                    areaValue = def.value(lv);
                }
                continue;
            }
            apply(p, kind, def, def.value(lv), origin, drops, rnd, st, have, now);
        }
        if (area != null) {
            apply(p, kind, area, areaValue, origin, drops, rnd, st, have, now);
            // Tools v6: Chain Reaction, a second area enchant right after the first.
            GrindEnchants.Def chain = passive(kind, GrindEnchants.Effect.CHAIN);
            int chainLv = chain == null ? 0 : Math.min(chain.max(), have.getOrDefault(chain.key(), 0));
            if (chainLv > 0 && rnd.nextDouble(100) < chain.value(chainLv)) {
                apply(p, kind, area, areaValue, origin, drops, rnd, st, have, now);
                bar(p, "enchant.proc.chain", chain, Msg.p("amount", name(area)));
            }
        }
    }

    /** Tools v6: what a running Shard Storm, Golden Touch or Duplicator does to this break. */
    private void ongoing(Player p, Kind kind, Block origin, List<Item> drops, State st, Map<String, Integer> have, long now) {
        if (st.dupLeft > 0 && !drops.isEmpty()) {
            st.dupLeft--;
            ItemStack stack = drops.getFirst().getItemStack();
            stack.setAmount(Math.min(stack.getMaxStackSize(), stack.getAmount() * 2));
            drops.getFirst().setItemStack(stack);
        }
        if (st.goldenLeft > 0 && !drops.isEmpty()) {
            st.goldenLeft--;
            long money = cash(drops, 1.0);
            if (money > 0) {
                pendingCash.merge(m.profile(p), money, Long::sum);
            }
        }
        if (now < st.stormUntil) {
            GrindEnchants.Def burst = defs.get(kind, "shard_burst").orElse(null);
            if (burst != null) {
                int lv = Math.max(1, Math.min(burst.max(), have.getOrDefault(burst.key(), 0)));
                double doubled = GrindEvents.multiplier(m.core().services(), p, GrindEvents.Kind.DOUBLE_SHARDS);
                long shards = ToolRules.rollUp(burst.value(lv) * doubled * shardMultiplier(p, kind), ThreadLocalRandom.current().nextDouble());
                if (shards > 0) {
                    m.addShards(m.profile(p), kind.shards(), shards);
                    if (origin != null) {
                        m.popup(p, origin.getLocation(), kind.shards(), shards);
                    }
                }
            }
        }
    }

    private State state(Player p, Kind kind) {
        return states.computeIfAbsent(stateKey(p.getUniqueId(), kind), k -> new State());
    }

    /** The grind area of a tool ({@code farm}, {@code mine}, {@code fish}). */
    static String area(Kind kind) {
        return switch (kind) {
            case HOE -> GrindBreakEvent.FARM;
            case PICKAXE -> GrindBreakEvent.MINE;
            case ROD -> GrindBreakEvent.FISH;
            case SWORD -> GrindBreakEvent.PVE;
        };
    }

    /** Each tool's streaks live under their own key (a player can switch tools between breaks). */
    private static UUID stateKey(UUID player, Kind kind) {
        return switch (kind) {
            case PICKAXE -> player;
            case HOE -> hoeKey(player);
            case ROD -> new UUID(player.getMostSignificantBits() ^ 0x27d4eb2fL, player.getLeastSignificantBits());
            case SWORD -> new UUID(player.getMostSignificantBits() ^ 0x165667b1L, player.getLeastSignificantBits());
        };
    }

    /** The hoe's state lives under a second key (a player can switch tools between breaks). */
    private static UUID hoeKey(UUID player) {
        return new UUID(player.getMostSignificantBits() ^ 0x5bd1e995L, player.getLeastSignificantBits());
    }

    /** One more block in both streaks (a gap longer than the setting starts them again). */
    private void streaks(State st, long now) {
        st.shardStreak = now - st.shardAt <= m.config().enchantStreakGapShards * 1000L ? st.shardStreak + 1 : 1;
        st.shardAt = now;
        st.xpStreak = now - st.xpAt <= m.config().enchantStreakGapXp * 1000L ? st.xpStreak + 1 : 1;
        st.xpAt = now;
    }

    /** The first passive enchant of an effect for a tool (null = none configured). */
    private GrindEnchants.Def passive(Kind kind, GrindEnchants.Effect effect) {
        for (GrindEnchants.Def d : defs.of(kind)) {
            if (d.effect() == effect) {
                return d;
            }
        }
        return null;
    }

    private int levelOf(Player p, GrindEnchants.Def def) {
        return def == null ? 0 : level(p, def);
    }

    /**
     * Shard Echo + Compound Interest: the multiplier on the shards found with this tool at /farm or /mine (at most
     * {@code enchants.shard-bonus-cap} percent more). 1 elsewhere or without the enchants.
     */
    double shardMultiplier(Player p, Kind kind) {
        if (!m.config().enchantsEnabled || !m.listener().grindWorld(p)) {
            return 1;
        }
        State st = states.get(stateKey(p.getUniqueId(), kind));
        if (st == null) {
            return 1;
        }
        long now = System.currentTimeMillis();
        double pct = 0;
        GrindEnchants.Def compound = passive(kind, GrindEnchants.Effect.STREAK_SHARDS);
        int lv = levelOf(p, compound);
        if (lv > 0 && now - st.shardAt <= m.config().enchantStreakGapShards * 1000L) {
            pct += GrindEnchants.streak(st.shardStreak, m.config().enchantStreakStep, compound.value(lv));
        }
        if (now < st.echoUntil) {
            pct += st.echoPercent;
        }
        double fever = now < st.feverUntil ? 2 : 1; // tools v6: Booster Find, on top of the cap
        return (1 + Math.min(m.config().enchantShardCap, pct) / 100.0) * fever;
    }

    /** Combo: the multiplier on tool XP while the break streak runs (only at /farm and /mine). */
    double xpMultiplier(Player p, Kind kind) {
        if (!m.config().enchantsEnabled || !m.listener().grindWorld(p)) {
            return 1;
        }
        State st = states.get(stateKey(p.getUniqueId(), kind));
        GrindEnchants.Def combo = passive(kind, GrindEnchants.Effect.STREAK_XP);
        int lv = levelOf(p, combo);
        if (st == null || lv <= 0 || System.currentTimeMillis() - st.xpAt > m.config().enchantStreakGapXp * 1000L) {
            return 1;
        }
        return 1 + GrindEnchants.streak(st.xpStreak, m.config().enchantStreakStep, combo.value(lv)) / 100.0;
    }

    private void apply(Player p, Kind kind, GrindEnchants.Def def, double value, Block origin, List<Item> drops, ThreadLocalRandom rnd,
                       State st, Map<String, Integer> have, long now) {
        switch (def.effect()) {
            case AREA -> {
                int blocks = m.listener().blast(p, origin, kind, ToolRules.roll(value, rnd.nextDouble()));
                if (blocks > 0) {
                    bar(p, "enchant.proc.area", def, Msg.number("amount", blocks));
                    show(p, def, origin);
                }
            }
            case EXTRA_ITEMS -> {
                int extra = ToolRules.roll(value, rnd.nextDouble());
                if (extra > 0 && !drops.isEmpty()) {
                    ItemStack stack = drops.getFirst().getItemStack();
                    stack.setAmount(Math.min(stack.getMaxStackSize(), stack.getAmount() + extra));
                    drops.getFirst().setItemStack(stack);
                    bar(p, "enchant.proc.items", def, Msg.number("amount", extra));
                }
            }
            case SHARDS -> {
                double doubled = GrindEvents.multiplier(m.core().services(), p, GrindEvents.Kind.DOUBLE_SHARDS);
                long shards = ToolRules.rollUp(value * doubled * shardMultiplier(p, kind), rnd.nextDouble()); // tools v6: always rounded up
                if (def.id().equals("shard_burst")) {
                    // Shard Echo: a Burst starts (or refreshes) a few seconds of more shards.
                    GrindEnchants.Def echo = passive(kind, GrindEnchants.Effect.ECHO);
                    int lv = echo == null ? 0 : Math.min(echo.max(), have.getOrDefault(echo.key(), 0));
                    if (lv > 0) {
                        st.echoUntil = now + m.config().enchantEchoSeconds * 1000L;
                        st.echoPercent = echo.value(lv);
                    }
                }
                if (shards > 0) {
                    m.addShards(m.profile(p), kind.shards(), shards);
                    if (m.config().shardPopups && origin != null) {
                        m.popup(p, origin.getLocation(), kind.shards(), shards); // owner 7 Nov 2026: shards float up from the block
                    } else {
                        bar(p, "enchant.proc.shards", def, Msg.mm("amount", m.shardText(kind, shards)));
                    }
                    if (def.cooldownSeconds() > 0) {
                        m.core().sounds().play(p, "tool-lucky"); // the jackpot
                    }
                }
            }
            case CASH -> {
                long money = cash(drops, value);
                if (money > 0) {
                    pendingCash.merge(m.profile(p), money, Long::sum);
                    bar(p, "enchant.proc.cash", def, Msg.mm("amount", moneyText(money)));
                }
            }
            case PROGRESS -> {
                long blocks = ToolRules.roll(value, rnd.nextDouble());
                GrindProgress.add(m.core().services(), p, area(kind), blocks);
                bar(p, "enchant.proc.progress", def, Msg.number("amount", blocks));
            }
            case KEY -> {
                if (m.core().services().optional(CrateKeys.class).isEmpty()) {
                    return;
                }
                pendingKeys.merge(m.profile(p), 1, Integer::sum);
                m.msg().send(p, "enchant.proc.key", Msg.p("enchant", name(def)), Msg.p("crate", crateName()));
                m.msg().title(p, "enchant.key-title", Msg.p("crate", crateName()));
                m.core().sounds().play(p, "tool-lucky");
                m.core().audit().logPlayer(AuditCategory.ECONOMY, "grind_enchant_key", Actor.SYSTEM, p.getUniqueId(), p.getName(), 1L,
                        p.getLocation(), Map.of("kind", kind.id(), "crate", m.config().enchantKeyCrate));
            }
            case BASKET -> {
                int given = basket(p, kind);
                if (given > 0) {
                    bar(p, "enchant.proc.basket", def, Msg.number("amount", given));
                    m.core().sounds().play(p, "tool-lucky");
                }
            }
            case STORM -> {
                st.stormUntil = now + Math.round(value * 1000);
                bar(p, "enchant.proc.storm", def, Msg.p("amount", EnchantMenu.num(value)));
                m.core().sounds().play(p, "tool-lucky");
            }
            case FRENZY -> {
                int ticks = (int) Math.max(20, Math.round(value * 20));
                p.addPotionEffect(new org.bukkit.potion.PotionEffect(kind == Kind.SWORD ? org.bukkit.potion.PotionEffectType.STRENGTH
                        : org.bukkit.potion.PotionEffectType.HASTE, ticks, kind == Kind.SWORD ? 1 : 2, true, false, true));
                bar(p, "enchant.proc.frenzy", def, Msg.p("amount", EnchantMenu.num(value)));
            }
            case GOLDEN -> {
                st.goldenLeft += (int) Math.max(1, Math.round(value));
                bar(p, "enchant.proc.golden", def, Msg.number("amount", Math.round(value)));
            }
            case DUPLICATE -> {
                st.dupLeft += (int) Math.max(1, Math.round(value));
                bar(p, "enchant.proc.duplicate", def, Msg.number("amount", Math.round(value)));
            }
            case XP_SURGE -> {
                long xp = m.bonusXp(p, kind, value);
                if (xp > 0) {
                    bar(p, "enchant.proc.xp", def, Msg.number("amount", xp));
                }
            }
            case CRYSTALS -> {
                long n = ToolRules.rollUp(value, rnd.nextDouble());
                if (n > 0) {
                    pendingCrystals.merge(m.profile(p), n, Long::sum);
                    bar(p, "enchant.proc.crystals", def, Msg.number("amount", n));
                    m.core().sounds().play(p, "tool-lucky");
                }
            }
            case FEVER -> {
                st.feverUntil = now + Math.round(value * 1000);
                m.msg().title(p, "enchant.fever-title", Msg.number("seconds", Math.round(value)));
                m.core().sounds().play(p, "tool-max");
            }
            case RAIN -> {
                long shards = ToolRules.rollUp(value, rnd.nextDouble());
                UUID island = m.island(p);
                int others = 0;
                for (Player o : Bukkit.getOnlinePlayers()) {
                    if (o.equals(p) || island == null || !island.equals(m.island(o))) {
                        continue;
                    }
                    m.addShards(m.profile(o), kind.shards(), shards);
                    m.msg().actionBar(o, "enchant.proc.rain-got", Msg.p("player", p.getName()), Msg.mm("amount", m.shardText(kind, shards)));
                    others++;
                }
                m.addShards(m.profile(p), kind.shards(), shards);
                bar(p, "enchant.proc.rain", def, Msg.number("amount", others));
            }
            case SKILL_XP, ECHO, STREAK_SHARDS, STREAK_XP, PITY, CHAIN -> {
            }
        }
    }

    /** Geode / Harvest Basket: one of every crop or ore the player has unlocked, into the bag (then the tool's storage). */
    private int basket(Player p, Kind kind) {
        List<org.bukkit.Material> items = m.core().services().optional(GrindProgress.class)
                .map(g -> g.unlockedDrops(p, area(kind))).orElse(List.of());
        for (org.bukkit.Material mat : items) {
            Map<Integer, ItemStack> left = p.getInventory().addItem(new ItemStack(mat));
            if (!left.isEmpty()) {
                m.storage().add(p, kind, mat.getKey().getKey(), 1); // full bag and storage: lost, never on the ground
            }
        }
        return items.size();
    }

    /** A small show for the area enchants, only for the player (no lightning or explosions for everyone around). */
    private void show(Player p, GrindEnchants.Def def, Block origin) {
        org.bukkit.Location at = origin.getLocation().add(0.5, 0.5, 0.5);
        switch (def.id()) {
            case "lightning" -> {
                p.spawnParticle(org.bukkit.Particle.ELECTRIC_SPARK, at, 40, 1.2, 1.2, 1.2, 0.2);
                p.playSound(at, org.bukkit.Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.3f, 1.6f);
            }
            case "meteor", "nuke", "meteor_shower" -> {
                p.spawnParticle(org.bukkit.Particle.EXPLOSION, at, def.id().equals("nuke") ? 6 : 3, 1.5, 1, 1.5, 0);
                p.playSound(at, org.bukkit.Sound.ENTITY_GENERIC_EXPLODE, 0.4f, def.id().equals("nuke") ? 0.6f : 1.0f);
            }
            case "tornado", "row_reaper" -> {
                p.spawnParticle(org.bukkit.Particle.SWEEP_ATTACK, at, 8, 1.5, 0.2, 1.5, 0);
                p.playSound(at, org.bukkit.Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.5f, 1.0f);
            }
            default -> {
                p.spawnParticle(org.bukkit.Particle.EXPLOSION, at, 1, 0, 0, 0, 0);
                m.core().sounds().play(p, "tool-lucky");
            }
        }
    }

    /** Cash Vein / Cash Crop: the drop's sell value times {@code value} (0 when it can't be sold). */
    private long cash(List<Item> drops, double value) {
        SellService sell = m.core().services().optional(SellService.class).orElse(null);
        if (sell == null || drops.isEmpty()) {
            return 0;
        }
        ItemStack stack = drops.getFirst().getItemStack();
        double unit = sell.price(stack).map(SellPrice::unit).orElse(0.0);
        return Math.max(0, Math.round(unit * stack.getAmount() * value));
    }

    /** Scholar: more skill XP from /farm (hoe) and /mine (pickaxe) while the island tool is in the main hand. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSkillXp(SkillXpGainEvent event) {
        Kind kind = switch (event.getSource()) {
            case "grind_crop" -> event.getSkill() == SkillType.FARMING ? Kind.HOE : null;
            case "grind_block" -> event.getSkill() == SkillType.MINING ? Kind.PICKAXE : null;
            case "grind_fish" -> event.getSkill() == SkillType.FISHING ? Kind.ROD : null;
            case "grind_kill" -> event.getSkill() == SkillType.COMBAT ? Kind.SWORD : null;
            default -> null;
        };
        if (kind == null || !m.config().enchantsEnabled || ToolItems.kindOf(event.getPlayer().getInventory().getItemInMainHand()) != kind) {
            return;
        }
        for (GrindEnchants.Def def : defs.of(kind)) {
            if (def.effect() == GrindEnchants.Effect.SKILL_XP) {
                int lv = level(event.getPlayer(), def);
                if (lv > 0) {
                    event.setAmount(event.getAmount() * (1 + def.value(lv) / 100.0));
                }
            }
        }
    }

    /** Pays the batched Cash Vein money and Keyfinder keys (with the shard payout; unpaid ones are kept). */
    void pay() {
        EconomyService economy = m.core().services().optional(EconomyService.class).orElse(null);
        for (UUID profile : List.copyOf(pendingCash.keySet())) {
            Long amount = pendingCash.remove(profile);
            if (economy == null || amount == null || amount <= 0) {
                if (amount != null && amount > 0) {
                    pendingCash.merge(profile, amount, Long::sum);
                }
                continue;
            }
            UUID batch = UUID.randomUUID();
            economy.execute(Transaction.deposit(AccountRef.player(profile), Currency.MONEY, amount, TxReason.GRIND_ENCHANT_CASH)
                    .actor(Actor.system("grind_enchant")).reference("grind_enchant_cash:" + batch).idempotencyKey("grind_enchant_cash:" + batch))
                    .whenComplete((result, error) -> {
                        if (error != null || result == null || !result.ok()) {
                            pendingCash.merge(profile, amount, Long::sum);
                        }
                    });
        }
        for (UUID profile : List.copyOf(pendingCrystals.keySet())) {
            Long amount = pendingCrystals.remove(profile);
            if (economy == null || amount == null || amount <= 0) {
                if (amount != null && amount > 0) {
                    pendingCrystals.merge(profile, amount, Long::sum);
                }
                continue;
            }
            UUID batch = UUID.randomUUID();
            economy.execute(Transaction.deposit(AccountRef.player(profile), Currency.CRYSTALS, amount, TxReason.GRIND_ENCHANT_CRYSTALS)
                    .actor(Actor.system("grind_enchant")).reference("grind_enchant_crystals:" + batch).idempotencyKey("grind_enchant_crystals:" + batch))
                    .whenComplete((result, error) -> {
                        if (error != null || result == null || !result.ok()) {
                            pendingCrystals.merge(profile, amount, Long::sum);
                        }
                    });
        }
        CrateKeys keys = m.core().services().optional(CrateKeys.class).orElse(null);
        if (keys == null) {
            return;
        }
        for (UUID profile : List.copyOf(pendingKeys.keySet())) {
            Integer n = pendingKeys.remove(profile);
            if (n == null || n <= 0) {
                continue;
            }
            keys.giveKeys(profile, m.config().enchantKeyCrate, n, "keyfinder", Actor.system("keyfinder")).whenComplete((ok, error) -> {
                if (error != null || !Boolean.TRUE.equals(ok)) {
                    pendingKeys.merge(profile, n, Integer::sum);
                }
            });
        }
    }

    /** Server stop: what is still waiting, paid once more (best effort, like the shard payout). */
    void flush() {
        pay();
    }

    // ------------------------------------------------------------------ text

    String name(GrindEnchants.Def def) {
        return m.raw("enchant." + def.id() + ".name");
    }

    private String crateName() {
        String s = m.raw("enchant.crate." + m.config().enchantKeyCrate);
        return s.startsWith("enchant.crate.") ? m.config().enchantKeyCrate : s;
    }

    String moneyText(long amount) {
        return "<cur:money>" + Format.money(amount) + "</cur:money>";
    }

    private void bar(Player p, String key, GrindEnchants.Def def, net.kyori.adventure.text.minimessage.tag.resolver.TagResolver amount) {
        net.skybornmc.skyblock.common.hud.ActionBars.show(p, "enchant", m.msg().component(p, key, Msg.p("enchant", name(def)), amount),
                net.skybornmc.skyblock.common.hud.ActionBars.PAYOUT, 1500);
    }
}
