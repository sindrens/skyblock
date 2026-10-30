package net.skybornmc.skyblock.enchants;

import net.skybornmc.skyblock.common.bonus.BonusContext;
import net.skybornmc.skyblock.common.bonus.BonusFamily;
import net.skybornmc.skyblock.common.bonus.BonusService;
import net.skybornmc.skyblock.common.item.ItemKeys;
import net.skybornmc.skyblock.common.skills.SkillType;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.util.Scheduler;
import net.skybornmc.skyblock.common.wardrobe.WardrobeService;
import net.skybornmc.skyblock.common.wardrobe.WornPiece;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Armor enchant effects of worn wardrobe set pieces (E5, ENCHANTS §6.8). Read from the wardrobe's cache on demand (never
 * the database), numbers from {@link ArmorMath}:
 * <ul>
 *   <li><b>Vitality</b> (health family) and <b>Fortitude</b> (damage reduction family) are reported to the BonusService by
 *   {@link EnchantBonusSource}; this class applies both families (max health as a transient attribute modifier, mob damage
 *   reduced by the capped family total, so wardrobe set bonuses of the same families count once inside the cap). Against
 *   players only Fortitude's PvP value reduces damage.</li>
 *   <li><b>Featherfall</b>, <b>Ember Ward</b>, <b>Phoenix Thorns</b> (reflected as thorns damage, never triggers sword
 *   enchants), <b>Phoenix Rebirth</b> (once per cooldown, lethal damage leaves you at a few hearts) on damage events.</li>
 *   <li><b>Swiftfoot</b> (walk speed modifier), <b>Night Eye</b> (night vision) and <b>Second Wind</b> (regeneration after
 *   a few seconds without damage) every {@code armor.refresh-ticks}.</li>
 * </ul>
 * In the warzone every armor enchant works at its PvP strength and only if W2 allows its family there (see
 * {@link ArmorMath#strength}); Night Eye, Swiftfoot, Ember Ward and Phoenix Rebirth are off there (PvP 0).
 */
final class ArmorEffects implements Listener {

    static final NamespacedKey HEALTH_KEY = ItemKeys.key("enchant_armor_health");
    static final NamespacedKey SPEED_KEY = ItemKeys.key("enchant_armor_speed");

    private final EnchantsModule module;
    private final Map<UUID, Long> lastDamage = new HashMap<>();
    private final Map<UUID, Long> rebirthUsed = new HashMap<>();
    private final Set<UUID> nightVision = new HashSet<>();
    private Scheduler.Task task;
    private boolean reflecting;

    ArmorEffects(EnchantsModule module) {
        this.module = module;
    }

    void start() {
        long period = Math.max(5, module.config().armorRefreshTicks);
        task = module.core.scheduler().repeat("enchants-armor", period, period, this::refreshAll);
    }

    void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            clear(p);
        }
    }

    // ------------------------------------------------------------------ state

    /** The working armor enchants of a player now: id → level per piece (empty when effects are off). */
    Map<String, List<Integer>> working(Player player) {
        WardrobeService w = module.wardrobe();
        if (w == null || !module.config().armorEffects) {
            return Map.of();
        }
        List<WornPiece> worn = w.wornEnchants(player);
        if (worn.isEmpty()) {
            return Map.of();
        }
        return ArmorMath.working(worn, module.armorRules(), module.registry()::get);
    }

    private boolean warzone(Player player) {
        WardrobeService w = module.wardrobe();
        return w != null && w.inWarzone(player);
    }

    private double strength(EnchantDef def, Player player) {
        WardrobeService w = module.wardrobe();
        return ArmorMath.strength(def, warzone(player), f -> w == null ? 0 : w.warzoneFactor(f));
    }

    /** What the armor enchants add to a family for this player (health: hearts, damage reduction: percent). */
    double familyValue(Player player, BonusFamily family) {
        Map<String, List<Integer>> working = working(player);
        if (working.isEmpty()) {
            return 0;
        }
        if (family == BonusFamily.HEALTH) {
            return def(ArmorMath.VITALITY).filter(d -> working.containsKey(d.id()))
                    .map(d -> ArmorMath.vitalityHearts(d, working.get(d.id())) * strength(d, player)).orElse(0.0);
        }
        if (family == BonusFamily.DAMAGE_REDUCTION) {
            return def(ArmorMath.FORTITUDE).filter(d -> working.containsKey(d.id()))
                    .map(d -> ArmorMath.sum(d, "percent", working.get(d.id())) * strength(d, player)).orElse(0.0);
        }
        return 0;
    }

    private Optional<EnchantDef> def(String id) {
        return module.registry().get(id);
    }

    // ------------------------------------------------------------------ refresh (attributes, effects, regen)

    private void refreshAll() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            try {
                refresh(p);
            } catch (RuntimeException e) {
                module.core.plugin().getLogger().log(java.util.logging.Level.WARNING, "Enchants: armor refresh failed for " + p.getName(), e);
            }
        }
    }

    void refresh(Player player) {
        if (player.isDead()) {
            return;
        }
        Map<String, List<Integer>> working = working(player);
        BonusService bonus = module.core.services().optional(BonusService.class).orElse(null);
        double hearts = bonus == null ? 0 : bonus.capped(BonusFamily.HEALTH, BonusContext.of(player));
        modifier(player, Attribute.MAX_HEALTH, HEALTH_KEY, hearts * 2, AttributeModifier.Operation.ADD_NUMBER);

        double speed = def(ArmorMath.SWIFTFOOT).filter(d -> working.containsKey(d.id()))
                .map(d -> d.value("percent", ArmorMath.best(working.get(d.id()))) * strength(d, player)).orElse(0.0);
        modifier(player, Attribute.MOVEMENT_SPEED, SPEED_KEY, Double.isFinite(speed) ? speed / 100.0 : 0, AttributeModifier.Operation.ADD_SCALAR);

        boolean eye = def(ArmorMath.NIGHT_EYE).filter(d -> working.containsKey(d.id())).map(d -> strength(d, player) > 0).orElse(false);
        nightEye(player, eye);

        def(ArmorMath.SECOND_WIND).filter(d -> working.containsKey(d.id())).ifPresent(d -> {
            long since = System.currentTimeMillis() - lastDamage.getOrDefault(player.getUniqueId(), 0L);
            double heal = ArmorMath.secondWindHeal(d, ArmorMath.best(working.get(d.id())), since, module.config().armorRefreshTicks / 20.0,
                    strength(d, player));
            AttributeInstance max = player.getAttribute(Attribute.MAX_HEALTH);
            if (heal > 0 && max != null && player.getHealth() < max.getValue()) {
                player.setHealth(Math.min(max.getValue(), player.getHealth() + heal));
            }
        });
    }

    /** Sets (or removes, at 0) a transient attribute modifier; only touches the attribute when the value changed. */
    private static void modifier(Player player, Attribute attribute, NamespacedKey key, double amount, AttributeModifier.Operation op) {
        AttributeInstance inst = player.getAttribute(attribute);
        if (inst == null) {
            return;
        }
        AttributeModifier current = inst.getModifier(key);
        if (current != null && Math.abs(current.getAmount() - amount) < 1e-9 && current.getOperation() == op) {
            return;
        }
        if (current != null) {
            inst.removeModifier(key);
        }
        if (amount != 0) {
            inst.addTransientModifier(new AttributeModifier(key, amount, op));
        }
        if (attribute == Attribute.MAX_HEALTH && player.getHealth() > inst.getValue()) {
            player.setHealth(Math.max(0.5, inst.getValue()));
        }
    }

    private void nightEye(Player player, boolean on) {
        UUID id = player.getUniqueId();
        if (on) {
            PotionEffect current = player.getPotionEffect(PotionEffectType.NIGHT_VISION);
            int ticks = module.config().nightVisionTicks;
            if (current == null || (nightVision.contains(id) && current.getDuration() < ticks - module.config().armorRefreshTicks * 2)) {
                if (current == null || current.getAmplifier() == 0) {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, ticks, 0, true, false, false));
                    nightVision.add(id);
                }
            }
        } else if (nightVision.remove(id)) {
            PotionEffect current = player.getPotionEffect(PotionEffectType.NIGHT_VISION);
            // Only our own effect (ambient, no particles, no icon) is taken away, never a potion the player drank.
            if (current != null && current.isAmbient() && !current.hasParticles() && !current.hasIcon()) {
                player.removePotionEffect(PotionEffectType.NIGHT_VISION);
            }
        }
    }

    private void clear(Player player) {
        for (Attribute a : List.of(Attribute.MAX_HEALTH, Attribute.MOVEMENT_SPEED)) {
            AttributeInstance inst = player.getAttribute(a);
            if (inst != null) {
                inst.removeModifier(a == Attribute.MAX_HEALTH ? HEALTH_KEY : SPEED_KEY);
            }
        }
        nightEye(player, false);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        clear(event.getPlayer());
        lastDamage.remove(event.getPlayer().getUniqueId());
        nightVision.remove(event.getPlayer().getUniqueId());
    }

    // ------------------------------------------------------------------ damage

    /** The living attacker behind a hit (the shooter of a projectile), or null. */
    private static LivingEntity attacker(EntityDamageEvent event) {
        if (!(event instanceof EntityDamageByEntityEvent byEntity)) {
            return null;
        }
        Entity damager = byEntity.getDamager();
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof LivingEntity shooter) {
            return shooter;
        }
        return damager instanceof LivingEntity living ? living : null;
    }

    /** Ember Ward, Featherfall and the damage reduction family (Fortitude + sets vs mobs, Fortitude's PvP value vs players). */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player) || event.getCause() == EntityDamageEvent.DamageCause.THORNS && reflecting) {
            return;
        }
        Map<String, List<Integer>> working = working(player);
        EntityDamageEvent.DamageCause cause = event.getCause();
        if (!working.isEmpty()) {
            if (fire(event) && def(ArmorMath.EMBER_WARD).filter(d -> working.containsKey(d.id()) && strength(d, player) > 0).isPresent()) {
                event.setCancelled(true);
                player.setFireTicks(0);
                return;
            }
            if (cause == EntityDamageEvent.DamageCause.FALL) {
                def(ArmorMath.FEATHERFALL).filter(d -> working.containsKey(d.id())).ifPresent(d -> event.setDamage(
                        ArmorMath.reduced(event.getDamage(), d.value("percent", ArmorMath.best(working.get(d.id()))) * strength(d, player))));
            }
        }
        LivingEntity attacker = attacker(event);
        if (attacker == null || attacker.equals(player)) {
            return;
        }
        BonusService bonus = module.core.services().optional(BonusService.class).orElse(null);
        double percent;
        if (attacker instanceof Player) {
            // EN17: against players only Fortitude at its PvP value, and only if W2 allows the family in the warzone.
            percent = def(ArmorMath.FORTITUDE).filter(d -> working.containsKey(d.id())).map(d -> {
                double s = Math.max(0, Math.min(1, d.pvpPercent() / 100.0));
                if (warzone(player)) {
                    s = ArmorMath.strength(d, true, f -> module.wardrobe() == null ? 0 : module.wardrobe().warzoneFactor(f));
                }
                return ArmorMath.sum(d, "percent", working.get(d.id())) * s;
            }).orElse(0.0);
            if (bonus != null) {
                percent = Math.min(percent, bonus.cap(BonusFamily.DAMAGE_REDUCTION));
            }
        } else {
            percent = bonus == null ? 0 : bonus.capped(BonusFamily.DAMAGE_REDUCTION, BonusContext.of(player, null, SkillType.COMBAT.id()));
        }
        if (percent > 0) {
            event.setDamage(ArmorMath.reduced(event.getDamage(), percent));
        }
    }

    private static boolean fire(EntityDamageEvent event) {
        DamageType type = event.getDamageSource().getDamageType();
        return type == DamageType.IN_FIRE || type == DamageType.ON_FIRE || type == DamageType.LAVA || type == DamageType.HOT_FLOOR
                || type == DamageType.CAMPFIRE;
    }

    /** Phoenix Rebirth: after every other change, a lethal hit leaves the player alive once per cooldown. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onLethal(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (!ArmorMath.lethal(event.getFinalDamage(), player.getHealth(), player.getAbsorptionAmount())) {
            return;
        }
        if (event.getCause() == EntityDamageEvent.DamageCause.VOID || event.getCause() == EntityDamageEvent.DamageCause.KILL
                || event.getCause() == EntityDamageEvent.DamageCause.SUICIDE) {
            return; // never against /kill or the void (it would only delay the death)
        }
        Optional<EnchantDef> rebirth = def(ArmorMath.REBIRTH);
        if (rebirth.isEmpty() || !working(player).containsKey(ArmorMath.REBIRTH) || strength(rebirth.get(), player) <= 0) {
            return;
        }
        EnchantDef d = rebirth.get();
        long now = System.currentTimeMillis();
        if (!ArmorMath.rebirthReady(rebirthUsed.get(player.getUniqueId()), now, d.value("cooldown", 1))) {
            return;
        }
        rebirthUsed.put(player.getUniqueId(), now);
        event.setCancelled(true);
        AttributeInstance max = player.getAttribute(Attribute.MAX_HEALTH);
        double hearts = d.value("hearts", 1);
        double health = (Double.isFinite(hearts) ? hearts : 4) * 2;
        player.setHealth(Math.max(1, Math.min(max == null ? 20 : max.getValue(), health)));
        int ticks = (int) Math.round((Double.isFinite(d.value("seconds", 1)) ? d.value("seconds", 1) : 5) * 20);
        player.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, ticks, 0, false, true, true));
        player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, ticks, 1, false, true, true));
        player.setFireTicks(0);
        int minutes = (int) Math.ceil(d.value("cooldown", 1) / 60.0);
        module.msg().send(player, "armor.rebirth", Msg.p("enchant", module.text().name(d.id())), Msg.p("minutes", minutes));
        String sound = module.config().soundRebirth;
        if (sound != null && !sound.isBlank()) {
            module.core.sounds().play(player, sound);
        }
        module.core.audit().logPlayer(net.skybornmc.skyblock.common.audit.AuditCategory.ITEM, "armor_rebirth",
                net.skybornmc.skyblock.common.audit.Actor.system("enchants"), player.getUniqueId(), player.getName(), null,
                player.getLocation(), Map.of("cause", event.getCause().name(), "damage", event.getFinalDamage()));
    }

    /** Last time a player took damage (Second Wind waits for a quiet moment). */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHurt(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && event.getFinalDamage() > 0) {
            lastDamage.put(player.getUniqueId(), System.currentTimeMillis());
        }
    }

    /** Phoenix Thorns: reflects a share of melee damage to the attacker (mobs; players at the PvP value). */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMelee(EntityDamageByEntityEvent event) {
        if (reflecting || event.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK || !(event.getEntity() instanceof Player player)
                || !(event.getDamager() instanceof LivingEntity attacker) || attacker.equals(player) || attacker.isDead()) {
            return;
        }
        Optional<EnchantDef> thorns = def(ArmorMath.THORNS);
        if (thorns.isEmpty()) {
            return;
        }
        Map<String, List<Integer>> working = working(player);
        List<Integer> levels = working.get(ArmorMath.THORNS);
        if (levels == null) {
            return;
        }
        EnchantDef d = thorns.get();
        double s = attacker instanceof Player && !warzone(player) ? Math.max(0, Math.min(1, d.pvpPercent() / 100.0)) : strength(d, player);
        double percent = ArmorMath.thornsPercent(d, levels, s, module.config().thornsMaxPercent);
        double amount = event.getFinalDamage() * percent / 100.0;
        if (!(amount > 0)) {
            return;
        }
        reflecting = true;
        try {
            attacker.damage(amount, DamageSource.builder(DamageType.THORNS).withCausingEntity(player).withDirectEntity(player).build());
        } finally {
            reflecting = false;
        }
    }
}
