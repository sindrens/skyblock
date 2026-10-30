package net.skybornmc.skyblock.enchants;

import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.audit.AuditCategory;
import net.skybornmc.skyblock.common.bonus.BonusContext;
import net.skybornmc.skyblock.common.bonus.BonusFamily;
import net.skybornmc.skyblock.common.bonus.BonusService;
import net.skybornmc.skyblock.common.island.IslandService;
import net.skybornmc.skyblock.common.item.LoreBuilder;
import net.skybornmc.skyblock.common.profile.ProfileService;
import net.skybornmc.skyblock.common.skills.SkillType;
import net.skybornmc.skyblock.common.stacking.StackKillBonus;
import net.skybornmc.skyblock.common.stacking.StackKillEvent;
import net.skybornmc.skyblock.common.stacking.StackKillProvider;
import net.skybornmc.skyblock.common.stacking.StackingService;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.tools.ToolInfo;
import net.skybornmc.skyblock.common.tools.ToolKind;
import net.skybornmc.skyblock.common.tools.ToolPerk;
import net.skybornmc.skyblock.common.tools.ToolService;
import net.skybornmc.skyblock.common.util.Format;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

/**
 * Stacked-mob enchants, E3 (ENCHANTS §6.6, §7): Slaughter (+ Bloodlust) as the {@link StackKillProvider} kill count,
 * Plunder and Soul Reaper as per-mob batch bonuses inside their family caps, Reaper's Sweep and Phoenix Wrath through the
 * stacking module's batch kill API, Soul Reaper's skill XP share, and island stack book drops (1 in N per mob, daily cap
 * per profile, one atomic database transaction per book).
 *
 * <p>Island permissions: the hit itself already passed island protection (hurt-monsters / animals role permission).
 * Area effects only reach stacks on the <b>same island</b> and of the <b>same protection group</b> (monster or not) as the
 * mob that was hit, which island protection decides the same way, so no stack the player may not hurt is touched.</p>
 *
 * <p>Counters (Bloodlust streak, Phoenix Wrath hits) are per profile key and in memory only (they last seconds).</p>
 */
final class StackEnchants implements StackKillProvider, Listener {

    static final String SLAUGHTER = "slaughter";
    static final String BLOODLUST = "bloodlust";
    static final String SOUL_REAPER = "soul_reaper";
    static final String PLUNDER = "plunder";
    static final String SWEEP = "reapers_sweep";
    static final String WRATH = "phoenix_wrath";
    /** enchant_daily roll id for island stack books. */
    static final String DAILY_BOOKS = "stack_book";
    static final String ORIGIN = "drop:stacks";
    static final String ACTIVITY = "combat";

    private final EnchantsModule module;
    private final StackPerf perf;
    private final Map<UUID, StackKillMath.Streak> streaks = new HashMap<>();
    private final Map<UUID, Integer> wrathHits = new HashMap<>();
    private final Map<UUID, Integer> stackKillTick = new HashMap<>();
    private final Map<UUID, Long> lastProcMessage = new HashMap<>();

    StackEnchants(EnchantsModule module) {
        this.module = module;
        this.perf = new StackPerf(module.core.plugin().getLogger(), () -> module.config().stackPerfWarnMs);
    }

    StackPerf perf() {
        return perf;
    }

    // ================================================================== StackKillProvider

    @Override
    public int killsPerHit(Player killer, ItemStack weapon, LivingEntity victim, int stackSize) {
        long start = perf.start();
        try {
            Map<String, Integer> enchants = enchants(weapon);
            int bloodlust = enchants.getOrDefault(BLOODLUST, 0);
            UUID key = profileKey(killer);
            long now = System.currentTimeMillis();
            double bonus = 0;
            if (bloodlust > 0) {
                long window = windowMillis(bloodlust);
                StackKillMath.Streak streak = streaks.getOrDefault(key, StackKillMath.Streak.NONE);
                bonus = StackKillMath.bloodlustPercent(streak.prior(now, window), module.config().bloodlustStepPercent,
                        value(BLOODLUST, "percent", bloodlust));
                streaks.put(key, streak.hit(now, window));
            } else {
                streaks.remove(key);
            }
            return StackKillMath.kills(slaughterCount(enchants), bonus, module.config().stackMaxKillsPerHit);
        } finally {
            perf.end(start);
        }
    }

    @Override
    public StackKillBonus bonus(Player killer, ItemStack weapon, LivingEntity victim) {
        long start = perf.start();
        try {
            ToolInfo tool = tool(weapon);
            Map<String, Integer> enchants = enchants(tool);
            int plunder = enchants.getOrDefault(PLUNDER, 0);
            int soul = enchants.getOrDefault(SOUL_REAPER, 0);
            double loot = 0;
            BonusService bonus = bonusService();
            if (bonus != null) {
                double total = bonus.total(BonusFamily.EXTRA_DROPS, new BonusContext(killer, weapon, tool, ACTIVITY));
                loot = StackKillMath.extraLootPercent(total, plunder > 0 ? value(PLUNDER, "chance", plunder) : 0,
                        bonus.cap(BonusFamily.EXTRA_DROPS));
            }
            double xp = soul > 0 ? value(SOUL_REAPER, "xp", soul) : 0;
            return new StackKillBonus(loot, xp);
        } finally {
            perf.end(start);
        }
    }

    // ================================================================== after a batch (sweep, books, Soul Reaper marker)

    @EventHandler(priority = EventPriority.NORMAL)
    public void onStackKill(StackKillEvent event) {
        Player killer = event.killer();
        if (killer == null) {
            return;
        }
        long start = perf.start();
        try {
            UUID key = profileKey(killer);
            stackKillTick.put(key, Bukkit.getCurrentTick());
            ToolInfo tool = tool(event.weapon());
            if (tool == null || tool.kind() != ToolKind.SWORD) {
                return;
            }
            Map<String, Integer> enchants = enchants(tool);
            if (event.cause() == StackKillEvent.Cause.HIT) {
                int sweep = enchants.getOrDefault(SWEEP, 0);
                if (sweep > 0) {
                    sweep(killer, event, sweep);
                }
            }
            dropBooks(killer, event.killed(), event.location());
        } finally {
            perf.end(start);
        }
    }

    /** Reaper's Sweep: part of the kill count from up to N other stacks within the radius (§7.2 step 4). */
    private void sweep(Player killer, StackKillEvent event, int level) {
        StackingService stacking = stacking();
        if (stacking == null) {
            return;
        }
        int count = StackKillMath.sweepKills(event.requested(), value(SWEEP, "percent", level), module.config().stackMaxKillsPerHit);
        int maxStacks = (int) value(SWEEP, "stacks", level);
        double radius = value(SWEEP, "radius", level);
        if (count <= 0 || maxStacks <= 0 || !(radius > 0)) {
            return;
        }
        LivingEntity hit = event.entity();
        Location at = event.location();
        Optional<UUID> island = islandAt(at);
        boolean monster = hit instanceof Enemy;
        List<LivingEntity> targets = StackKillMath.targets(stacking.stacksNear(at, radius), hit,
                e -> sameRules(e, monster, island), maxStacks);
        int total = 0;
        for (LivingEntity target : targets) {
            total += stacking.killFromStack(target, count, killer);
        }
        if (total > 0) {
            proc(killer, "stack.sweep", SWEEP, level, total);
        }
    }

    // ================================================================== Phoenix Wrath (every Nth hit on a stack)

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player) || !(event.getEntity() instanceof LivingEntity victim)
                || victim instanceof Player || event.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK) {
            return;
        }
        StackingService stacking = stacking();
        if (stacking == null || stacking.mobCount(victim) < 2) {
            return; // cheap exit for every hit that is not on a stack
        }
        long start = perf.start();
        try {
            ItemStack weapon = player.getInventory().getItemInMainHand();
            Map<String, Integer> enchants = enchants(weapon);
            int wrath = enchants.getOrDefault(WRATH, 0);
            if (wrath <= 0) {
                return;
            }
            UUID key = profileKey(player);
            StackKillMath.Counter counter = StackKillMath.wrathHit(wrathHits.getOrDefault(key, 0), (int) value(WRATH, "every", wrath));
            wrathHits.put(key, counter.value());
            if (counter.fires()) {
                wrath(player, victim, enchants, wrath, key);
            }
        } finally {
            perf.end(start);
        }
    }

    /** §7.2 step 5: the full kill count (Slaughter + current Bloodlust, not advancing the streak) from every stack nearby. */
    private void wrath(Player player, LivingEntity victim, Map<String, Integer> enchants, int level, UUID key) {
        StackingService stacking = stacking();
        double radius = value(WRATH, "radius", level);
        if (stacking == null || !(radius > 0)) {
            return;
        }
        int bloodlust = enchants.getOrDefault(BLOODLUST, 0);
        double bonus = 0;
        if (bloodlust > 0) {
            StackKillMath.Streak streak = streaks.getOrDefault(key, StackKillMath.Streak.NONE);
            bonus = StackKillMath.bloodlustPercent(streak.prior(System.currentTimeMillis(), windowMillis(bloodlust)),
                    module.config().bloodlustStepPercent, value(BLOODLUST, "percent", bloodlust));
        }
        int count = StackKillMath.kills(slaughterCount(enchants), bonus, module.config().stackMaxKillsPerHit);
        Location at = victim.getLocation();
        Optional<UUID> island = islandAt(at);
        boolean monster = victim instanceof Enemy;
        List<LivingEntity> targets = StackKillMath.targets(stacking.stacksNear(at, radius), victim,
                e -> sameRules(e, monster, island), module.config().wrathMaxStacks);
        int total = 0;
        for (LivingEntity target : targets) {
            total += stacking.killFromStack(target, count, player);
        }
        if (total > 0) {
            proc(player, "stack.wrath", WRATH, level, total);
        }
    }

    // ================================================================== Soul Reaper skill XP (called by EnchantsListener)

    /**
     * Soul Reaper's skill XP share for this gain, or 0: Combat XP from a stacked kill (paid through the stack kill event, or
     * the normal kill XP of a mob that died in a stack batch this tick) with Soul Reaper on the held sword.
     */
    double soulReaperSkillPercent(Player player, ItemStack held, SkillType skill, String source) {
        if (skill != SkillType.COMBAT) {
            return 0;
        }
        boolean stacked = StackKillEvent.SKILL_XP_SOURCE.equals(source)
                || Objects.equals(stackKillTick.get(profileKey(player)), Bukkit.getCurrentTick());
        if (!stacked) {
            return 0;
        }
        int soul = enchants(held).getOrDefault(SOUL_REAPER, 0);
        return soul > 0 ? value(SOUL_REAPER, "skill", soul) : 0;
    }

    // ================================================================== island stack books (EN15)

    private void dropBooks(Player player, int killed, Location at) {
        EnchanterConfig config = module.config();
        int found = StackKillMath.books(killed, config.stackBookChance, config.stackBookDailyCap, ThreadLocalRandom.current());
        for (int i = 0; i < found; i++) {
            mintBook(player, at);
        }
    }

    /** One stack book: picks it, then in one transaction uses one of today's stack books and writes the book row. */
    private void mintBook(Player player, Location at) {
        EnchanterConfig config = module.config();
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int tier = module.ops().tier(player, Pool.STACKS);
        Optional<EnchantDef> def = Rolls.roll(module.registry(), Pool.STACKS, Rolls.parseOdds(config.stackBookOdds), tier, random);
        if (def.isEmpty()) {
            return;
        }
        String enchant = def.get().id();
        int level = StackKillMath.bookLevel(config.stackBookLevelOdds, def.get().maxLevel(), random);
        ItemStack book = module.books().createBook(enchant, level, ORIGIN);
        UUID bookId = BookItems.id(book);
        UUID owner = player.getUniqueId();
        UUID profile = profileKey(player);
        String day = EnchantOps.today();
        int cap = config.stackBookDailyCap;
        int season = config.seasonId;
        long now = System.currentTimeMillis();
        module.core.database().transaction(c -> module.store().mintCapped(c, bookId, enchant, level, season, ORIGIN, owner, profile,
                now, day, DAILY_BOOKS, cap)).whenComplete((ok, error) -> module.core.scheduler().main("enchants-stack-book", () -> {
            if (error != null) {
                module.core.plugin().getLogger().log(Level.WARNING, "Could not save a stack book for " + player.getName(), error);
                return;
            }
            if (!Boolean.TRUE.equals(ok)) {
                return; // daily cap reached: the book was never handed out and has no row
            }
            give(module, player, at, book);
            String name = module.text().displayName(enchant, level);
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("book", bookId.toString());
            details.put("kind", BookStore.KIND_BOOK);
            details.put("enchant", enchant);
            details.put("level", level);
            details.put("origin", ORIGIN);
            details.put("profile", profile.toString());
            module.core.audit().logPlayer(AuditCategory.ITEM, "book_created", Actor.system(EnchantsModule.ID), owner, player.getName(),
                    null, at, details);
            if (player.isOnline()) {
                module.msg().send(player, "stack.book", Msg.mm("book", "<rarity:" + def.get().rarity().id() + ">" + LoreBuilder.escape(name)));
                module.core.sounds().play(player, config.soundStackBook);
            }
        }));
    }

    /** Into the inventory; what does not fit (or an offline player) drops where the stack died. */
    static void give(EnchantsModule module, Player player, Location at, ItemStack book) {
        if (player.isOnline()) {
            Map<Integer, ItemStack> rest = player.getInventory().addItem(book);
            if (rest.isEmpty()) {
                return;
            }
            rest.values().forEach(s -> player.getWorld().dropItemNaturally(player.getLocation(), s));
            module.msg().send(player, "inventory-full");
            return;
        }
        if (at.getWorld() != null) {
            at.getWorld().dropItemNaturally(at, book);
        }
    }

    // ================================================================== helpers

    /** Same island (or both on none) and the same island protection group (monsters vs everything else). */
    private boolean sameRules(LivingEntity target, boolean monster, Optional<UUID> island) {
        return (target instanceof Enemy) == monster && islandAt(target.getLocation()).equals(island);
    }

    private Optional<UUID> islandAt(Location at) {
        return module.core.services().optional(IslandService.class)
                .flatMap(s -> s.islandAt(at)).map(IslandService.IslandInfo::id);
    }

    private void proc(Player player, String key, String enchant, int level, int mobs) {
        long now = System.currentTimeMillis();
        Long last = lastProcMessage.get(player.getUniqueId());
        if (last != null && now - last < module.config().procMessageIntervalMs) {
            return;
        }
        lastProcMessage.put(player.getUniqueId(), now);
        module.msg().actionBar(player, key, Msg.p("enchant", module.text().displayName(enchant, level)),
                Msg.p("count", Format.number(mobs)));
        module.core.sounds().play(player, module.config().soundStackProc);
    }

    private int slaughterCount(Map<String, Integer> enchants) {
        int level = enchants.getOrDefault(SLAUGHTER, 0);
        if (level <= 0) {
            return 1;
        }
        double count = value(SLAUGHTER, "count", level);
        return Double.isFinite(count) ? Math.max(1, (int) count) : 1;
    }

    private long windowMillis(int bloodlustLevel) {
        double seconds = value(BLOODLUST, "window", bloodlustLevel);
        return Double.isFinite(seconds) ? Math.max(0, Math.round(seconds * 1000)) : 0;
    }

    /** An enchant number, NaN-safe (0 when missing). */
    private double value(String id, String key, int level) {
        double v = module.registry().get(id).map(d -> d.value(key, level)).orElse(Double.NaN);
        return Double.isFinite(v) ? v : 0;
    }

    private ToolInfo tool(ItemStack weapon) {
        if (weapon == null || weapon.isEmpty()) {
            return null;
        }
        ToolService tools = module.tools();
        return tools == null ? null : tools.read(weapon).orElse(null);
    }

    private Map<String, Integer> enchants(ItemStack weapon) {
        return enchants(tool(weapon));
    }

    /** Working sword enchants of a tool (disabled enchants and non-swords left out). */
    private Map<String, Integer> enchants(ToolInfo tool) {
        if (tool == null || tool.kind() != ToolKind.SWORD || tool.perks().isEmpty()) {
            return Map.of();
        }
        Map<String, Integer> out = new HashMap<>();
        for (ToolPerk perk : tool.perks()) {
            module.registry().get(perk.id()).filter(EnchantDef::enabled)
                    .ifPresent(d -> out.put(d.id(), Math.min(perk.level(), d.maxLevel())));
        }
        return out;
    }

    private UUID profileKey(Player player) {
        return ProfileService.of(module.core.services()).profileKey(player);
    }

    private StackingService stacking() {
        return module.core.services().optional(StackingService.class).orElse(null);
    }

    private BonusService bonusService() {
        return module.core.services().optional(BonusService.class).orElse(null);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID key = profileKey(player);
        streaks.remove(key);
        wrathHits.remove(key);
        stackKillTick.remove(key);
        lastProcMessage.remove(player.getUniqueId());
    }
}
