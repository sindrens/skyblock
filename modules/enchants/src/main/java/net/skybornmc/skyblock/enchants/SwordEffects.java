package net.skybornmc.skyblock.enchants;

import net.skybornmc.skyblock.common.bonus.BonusContext;
import net.skybornmc.skyblock.common.bonus.BonusFamily;
import net.skybornmc.skyblock.common.bonus.BonusService;
import net.skybornmc.skyblock.common.combat.BossMobs;
import net.skybornmc.skyblock.common.island.IslandService;
import net.skybornmc.skyblock.common.profile.ProfileService;
import net.skybornmc.skyblock.common.skills.SkillType;
import net.skybornmc.skyblock.common.stacking.StackingService;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.tools.ToolInfo;
import net.skybornmc.skyblock.common.tools.ToolKind;
import net.skybornmc.skyblock.common.util.Format;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Sword enchants on normal mobs and players (E2, ENCHANTS §6.5, §8.3): the mob damage family (Phoenix Edge and every other
 * source, plus Giant Slayer against bosses, capped once), Bloodthirst, Ember Strike, Cleave, Executioner and Phoenix
 * Wrath's fire nova. They work in the Depths combat zones and on island mobs; stacked mobs are left to the E3 stack enchants
 * (Cleave and the nova skip stacks, Phoenix Wrath counts only hits on non-stacked mobs here). Against players only the PvP
 * values apply (Phoenix Edge 40%, Bloodthirst and Ember Strike 50%; Cleave, Executioner, Giant Slayer and the nova off).
 *
 * <p>Area damage only reaches mobs the player can see, of the same island and protection group (monsters vs others) as
 * the mob that was hit, never players, pets, armor stands or NPCs, and goes through {@code damage(amount, player)} so
 * island protection still decides. A guard keeps that damage from triggering effects again.</p>
 */
final class SwordEffects implements Listener {

    static final String PHOENIX_EDGE = "phoenix_edge";
    static final String GIANT_SLAYER = "giant_slayer";
    static final String BLOODTHIRST = "bloodthirst";
    static final String EMBER_STRIKE = "ember_strike";
    static final String CLEAVE = "cleave";
    static final String EXECUTIONER = "executioner";
    static final String WRATH = "phoenix_wrath";

    private final EnchantsModule module;
    private final ProcBar bar;
    private final StackPerf perf;
    private final Map<UUID, Integer> novaHits = new HashMap<>();
    private boolean applying;

    SwordEffects(EnchantsModule module, ProcBar bar, StackPerf perf) {
        this.module = module;
        this.bar = bar;
        this.perf = perf;
    }

    /** Damage changes: the mob damage family, Giant Slayer, Executioner; Phoenix Edge at PvP strength against players. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (applying || !(event.getDamager() instanceof Player player) || !(event.getEntity() instanceof LivingEntity victim)
                || event.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK) {
            return;
        }
        long start = perf.start();
        try {
            ItemStack held = player.getInventory().getItemInMainHand();
            ToolInfo tool = module.tool(held);
            Map<String, Integer> enchants = tool != null && tool.kind() == ToolKind.SWORD ? module.working(tool) : Map.of();
            if (victim instanceof Player) {
                int edge = enchants.getOrDefault(PHOENIX_EDGE, 0);
                if (edge > 0) {
                    event.setDamage(event.getDamage() * SwordMath.pvpMultiplier(module.num(PHOENIX_EDGE, "percent", edge), module.pvp(PHOENIX_EDGE)));
                }
                return;
            }
            boolean boss = BossMobs.isBoss(victim);
            BonusService bonus = module.core.services().optional(BonusService.class).orElse(null);
            if (bonus != null) {
                double total = bonus.total(BonusFamily.MOB_DAMAGE, new BonusContext(player, held, tool, SkillType.COMBAT.id()));
                int giant = enchants.getOrDefault(GIANT_SLAYER, 0);
                double multiplier = SwordMath.mobMultiplier(total, giant > 0 ? module.num(GIANT_SLAYER, "percent", giant) : 0, boss,
                        bonus.cap(BonusFamily.MOB_DAMAGE));
                if (multiplier != 1.0) {
                    event.setDamage(event.getDamage() * multiplier);
                }
            }
            int exe = enchants.getOrDefault(EXECUTIONER, 0);
            if (exe > 0 && SwordMath.executes(victim.getHealth() - event.getFinalDamage(), maxHealth(victim),
                    module.num(EXECUTIONER, "health", exe), boss)) {
                // enough to kill through armor; only non-boss mobs ever get here
                event.setDamage(Math.max(event.getDamage(), victim.getHealth() * 4 + 20));
            }
        } finally {
            perf.end(start);
        }
    }

    /** After the hit landed: Bloodthirst, Ember Strike, Cleave and the Phoenix Wrath nova. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        if (applying || !(event.getDamager() instanceof Player player) || !(event.getEntity() instanceof LivingEntity victim)
                || event.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK) {
            return;
        }
        ToolInfo tool = module.tool(player.getInventory().getItemInMainHand());
        if (tool == null || tool.kind() != ToolKind.SWORD || tool.perks().isEmpty()) {
            return;
        }
        long start = perf.start();
        try {
            Map<String, Integer> enchants = module.working(tool);
            if (enchants.isEmpty()) {
                return;
            }
            boolean pvp = victim instanceof Player;
            double dealt = Math.max(0, Math.min(event.getFinalDamage(), victim.getHealth()));
            ThreadLocalRandom random = ThreadLocalRandom.current();
            int thirst = enchants.getOrDefault(BLOODTHIRST, 0);
            if (thirst > 0) {
                double percent = module.num(BLOODTHIRST, "percent", thirst);
                double heal = SwordMath.heal(dealt, pvp ? SwordMath.scaled(percent, module.pvp(BLOODTHIRST)) : percent);
                if (heal > 0 && !player.isDead()) {
                    player.heal(heal);
                }
            }
            int ember = enchants.getOrDefault(EMBER_STRIKE, 0);
            if (ember > 0) {
                double chance = module.num(EMBER_STRIKE, "chance", ember);
                if (GatherMath.roll(pvp ? SwordMath.scaled(chance, module.pvp(EMBER_STRIKE)) : chance, random)) {
                    victim.setFireTicks(Math.max(victim.getFireTicks(), (int) Math.round(module.num(EMBER_STRIKE, "seconds", ember) * 20)));
                }
            }
            if (pvp) {
                return; // Cleave and the nova never touch players (§8.3)
            }
            int cleave = enchants.getOrDefault(CLEAVE, 0);
            if (cleave > 0 && dealt > 0) {
                double damage = SwordMath.share(event.getFinalDamage(), module.num(CLEAVE, "percent", cleave));
                for (LivingEntity target : nearby(player, victim, module.num(CLEAVE, "radius", cleave), module.config().cleaveMaxTargets)) {
                    hurt(target, damage, player);
                }
            }
            int wrath = enchants.getOrDefault(WRATH, 0);
            StackingService stacking = module.core.services().optional(StackingService.class).orElse(null);
            if (wrath > 0 && (stacking == null || stacking.mobCount(victim) < 2)) {
                nova(player, victim, wrath, event.getDamage());
            }
        } finally {
            perf.end(start);
        }
    }

    /** Phoenix Wrath on non-stacked mobs: every Nth hit, a fire nova of N% of the hit's damage around the victim. */
    private void nova(Player player, LivingEntity victim, int level, double weaponDamage) {
        UUID key = ProfileService.of(module.core.services()).profileKey(player);
        StackKillMath.Counter counter = StackKillMath.wrathHit(novaHits.getOrDefault(key, 0), (int) module.num(WRATH, "every", level));
        novaHits.put(key, counter.value());
        if (!counter.fires()) {
            return;
        }
        double radius = module.num(WRATH, "radius", level);
        double damage = SwordMath.share(weaponDamage, module.num(WRATH, "damage", level));
        List<LivingEntity> targets = nearby(player, victim, radius, module.config().novaMaxTargets);
        int hit = 0;
        for (LivingEntity target : targets) {
            if (hurt(target, damage, player)) {
                hit++;
            }
        }
        Location at = victim.getLocation();
        player.spawnParticle(Particle.FLAME, at.getX(), at.getY() + 0.5, at.getZ(), 40, radius / 2, 0.4, radius / 2, 0.02);
        bar.show(player, "proc.nova", module.config().soundNova, Msg.p("enchant", module.text().displayName(WRATH, level)),
                Msg.p("count", Format.number(hit)));
    }

    /** Damage from the player that does not trigger the effects again. True when it landed. */
    private boolean hurt(LivingEntity target, double damage, Player player) {
        if (!(damage > 0) || !target.isValid() || target.isDead()) {
            return false;
        }
        double before = target.getHealth();
        applying = true;
        try {
            target.damage(damage, player);
        } finally {
            applying = false;
        }
        return target.isDead() || target.getHealth() < before;
    }

    /** Mobs near the victim that area damage may reach, nearest first. */
    private List<LivingEntity> nearby(Player player, LivingEntity victim, double radius, int max) {
        if (!(radius > 0) || max <= 0) {
            return List.of();
        }
        boolean monster = victim instanceof Enemy;
        Optional<UUID> island = islandAt(victim.getLocation());
        StackingService stacking = module.core.services().optional(StackingService.class).orElse(null);
        Location center = victim.getLocation();
        double r2 = radius * radius;
        List<LivingEntity> out = new ArrayList<>();
        for (Entity e : victim.getNearbyEntities(radius, radius, radius)) {
            if (!(e instanceof LivingEntity target) || target == victim || target instanceof Player || target instanceof ArmorStand
                    || !target.isValid() || target.isDead() || target.hasMetadata("NPC")
                    || (target instanceof Tameable tame && tame.isTamed()) || (target instanceof Enemy) != monster
                    || target.getLocation().distanceSquared(center) > r2 || !player.canSee(target)
                    || (stacking != null && stacking.mobCount(target) >= 2)) {
                continue;
            }
            if (!islandAt(target.getLocation()).equals(island)) {
                continue;
            }
            out.add(target);
        }
        out.sort(Comparator.comparingDouble(t -> t.getLocation().distanceSquared(center)));
        return out.size() > max ? out.subList(0, max) : out;
    }

    private Optional<UUID> islandAt(Location at) {
        return module.core.services().optional(IslandService.class).flatMap(s -> s.islandAt(at)).map(IslandService.IslandInfo::id);
    }

    private static double maxHealth(LivingEntity entity) {
        AttributeInstance a = entity.getAttribute(Attribute.MAX_HEALTH);
        return a == null ? Math.max(1, entity.getHealth()) : a.getValue();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        novaHits.remove(ProfileService.of(module.core.services()).profileKey(event.getPlayer()));
    }
}
