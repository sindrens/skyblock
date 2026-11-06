package net.skybornmc.skyblock.essentials;

import net.skybornmc.skyblock.common.grind.GrindBreakEvent;
import net.skybornmc.skyblock.common.text.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Owner 6 Nov 2026 (FULL_PLAN.md 6, PvE v1): the /pve arena in the grind world. Ten square rings around the centre
 * (tier 1 outside, tier 10 in the middle), one mob type per tier with more health and damage further in, a fixed number
 * per ring that respawns, leashed to its ring (never wanders out), no burning. Tier n opens after enough kills in tier
 * n-1 (per player); a locked ring can't be entered. Every kill pays money, counts for Combat XP and quests (GrindBreakEvent
 * "pve"). Dying here costs nothing: back to the arena start. Bosses, the sword and Combat Shards come next.
 */
final class PveArena implements Listener {

    static final int X = -1500;
    static final int RING = 6;
    static final int TIERS = 10;
    static final int HALF = RING * TIERS;
    private static final NamespacedKey KILLS = net.skybornmc.skyblock.common.item.ItemKeys.key("grind_pve_kills");
    private static final NamespacedKey TIER = net.skybornmc.skyblock.common.item.ItemKeys.key("pve_tier");

    /** One tier: its mob, health and damage factors, money per kill, kills in this tier to open the next one. */
    record Tier(EntityType mob, double health, double damage, long pay, long next) {
    }

    // Money x100 (batch 10), then / 4 (owner 7 Nov 2026): ring 1 $500 ... ring 10 $8,750.
    static final List<Tier> TIER_LIST = List.of(
            new Tier(EntityType.ZOMBIE, 1.0, 1.0, 500, 200), new Tier(EntityType.HUSK, 1.4, 1.2, 750, 600),
            new Tier(EntityType.SKELETON, 1.8, 1.4, 1125, 1_500), new Tier(EntityType.SPIDER, 2.4, 1.6, 1625, 3_000),
            new Tier(EntityType.STRAY, 3.0, 1.8, 2250, 6_000), new Tier(EntityType.DROWNED, 3.8, 2.0, 3000, 10_000),
            new Tier(EntityType.PIGLIN_BRUTE, 4.6, 2.2, 4000, 16_000), new Tier(EntityType.BLAZE, 5.6, 2.4, 5250, 25_000),
            new Tier(EntityType.WITHER_SKELETON, 6.8, 2.7, 6750, 40_000), new Tier(EntityType.RAVAGER, 8.0, 3.0, 8750, 0)); // owner 7 Nov 2026: money / 4

    private final GrindAreas areas;
    /** Owner 7 Nov 2026: the personal bosses. */
    final PveBosses bosses;
    private final Map<UUID, Integer> mobs = new ConcurrentHashMap<>(); // entity → tier
    private final Map<UUID, Long> lastWarn = new ConcurrentHashMap<>();

    PveArena(GrindAreas areas) {
        this.areas = areas;
        this.bosses = new PveBosses(this, areas);
    }

    // ------------------------------------------------------------------ pure rules

    /** The ring (tier 1..10) of a block offset from the centre; 0 = outside the arena. Pure. */
    static int ringAt(int dx, int dz) {
        int d = Math.max(Math.abs(dx), Math.abs(dz));
        if (d > HALF) {
            return 0;
        }
        return Math.max(1, Math.min(TIERS, TIERS - d / RING));
    }

    /** The highest open tier for these kills per tier (index 0 = tier 1): tier n opens when tier n-1 reached its number. Pure. */
    static int unlocked(long[] kills, List<Tier> tiers) {
        int open = 1;
        for (int i = 0; i < tiers.size() - 1 && open == i + 1; i++) {
            if (i < kills.length && tiers.get(i).next() > 0 && kills[i] >= tiers.get(i).next()) {
                open = i + 2;
            }
        }
        return open;
    }

    // ------------------------------------------------------------------ state

    static long[] kills(Player p) {
        long[] v = p.getPersistentDataContainer().get(KILLS, PersistentDataType.LONG_ARRAY);
        long[] out = new long[TIERS];
        if (v != null) {
            System.arraycopy(v, 0, out, 0, Math.min(v.length, TIERS));
        }
        return out;
    }

    int unlocked(Player p) {
        return unlocked(kills(p), TIER_LIST);
    }

    /** "Zombie" for the tier name on the scoreboard. */
    String tierText(Player p) {
        int t = unlocked(p);
        return "Tier " + t + " · " + mobName(TIER_LIST.get(t - 1).mob());
    }

    /** "120 / 600" toward the next tier, "Max" at tier 10. */
    String progressText(Player p) {
        int t = unlocked(p);
        if (t >= TIERS) {
            return "Max";
        }
        return net.skybornmc.skyblock.common.util.Format.number(kills(p)[t - 1]) + " / "
                + net.skybornmc.skyblock.common.util.Format.number(TIER_LIST.get(t - 1).next());
    }

    private static String mobName(EntityType type) {
        String id = type.getKey().getKey().replace('_', ' ');
        StringBuilder b = new StringBuilder();
        for (String w : id.split(" ")) {
            b.append(b.isEmpty() ? "" : " ").append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        }
        return b.toString();
    }

    // ------------------------------------------------------------------ building and spawning

    /** The arena floor, built once: ten rings of alternating bricks with a line of glowing blocks between them. */
    void build(World world, int base) {
        for (int x = -HALF - 4; x <= HALF + 4; x++) {
            for (int z = -HALF - 4; z <= HALF + 4; z++) {
                int ring = ringAt(x, z);
                int d = Math.max(Math.abs(x), Math.abs(z));
                Material floor = ring == 0 ? Material.POLISHED_ANDESITE : d % RING == 0 ? Material.SEA_LANTERN
                        : ring % 2 == 0 ? Material.DEEPSLATE_BRICKS : Material.STONE_BRICKS;
                world.getBlockAt(X + x, base, z).setType(floor, false);
                if (d == HALF + 4) {
                    for (int y = 1; y <= 3; y++) {
                        world.getBlockAt(X + x, base + y, z).setType(z == -HALF - 4 && Math.abs(x) <= 2 ? Material.AIR : Material.DEEPSLATE_BRICK_WALL, false);
                    }
                }
            }
        }
    }

    /** The arena start: the gap in the outer wall. */
    Location start(World world, int base) {
        return new Location(world, X + 0.5, base + 1, -HALF - 3 + 0.5, 0, 0);
    }

    /** Every second: tops up each ring to its number of mobs and pulls strays back into their ring. */
    void tick(World world, int base, int perRing) {
        bosses.tick(world, base);
        if (world.getPlayers().stream().noneMatch(p -> inArena(p.getLocation()))) {
            return; // nobody here: nothing spawns (the ones there stay)
        }
        int[] count = new int[TIERS + 1];
        for (var it = mobs.entrySet().iterator(); it.hasNext(); ) {
            var e = it.next();
            org.bukkit.entity.Entity ent = Bukkit.getEntity(e.getKey());
            if (!(ent instanceof LivingEntity le) || le.isDead()) {
                it.remove();
                continue;
            }
            int tier = e.getValue();
            Location l = le.getLocation();
            if (ringAt(l.getBlockX() - X, l.getBlockZ()) != tier || l.getY() < base - 2) {
                le.teleport(spot(world, base, tier)); // leashed to its ring
            }
            count[tier]++;
        }
        for (int t = 1; t <= TIERS; t++) {
            for (int i = count[t]; i < perRing; i++) {
                spawn(world, base, t);
            }
        }
    }

    Location spot(World world, int base, int tier) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        int outer = (TIERS - tier + 1) * RING - 1;
        int inner = (TIERS - tier) * RING + 1;
        if (tier == TIERS) {
            inner = 0;
        }
        int x;
        int z;
        do {
            x = r.nextInt(-outer, outer + 1);
            z = r.nextInt(-outer, outer + 1);
        } while (Math.max(Math.abs(x), Math.abs(z)) < inner);
        return new Location(world, X + x + 0.5, base + 1, z + 0.5);
    }

    private void spawn(World world, int base, int tier) {
        Tier t = TIER_LIST.get(tier - 1);
        world.spawnEntity(spot(world, base, tier), t.mob(), org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.CUSTOM, e -> {
            if (!(e instanceof LivingEntity le)) {
                return;
            }
            prepare(le, tier, t.health(), t.damage());
            mobs.put(le.getUniqueId(), tier);
        });
    }

    /** An arena mob of a ring: tagged, never saved, scaled health and damage, adult, no burning, no pick-ups. */
    void prepare(LivingEntity le, int tier, double health, double damage) {
        {
            le.getPersistentDataContainer().set(TIER, PersistentDataType.INTEGER, tier);
            le.setPersistent(false);
            le.setRemoveWhenFarAway(false);
            le.setCanPickupItems(false);
            scale(le, Attribute.MAX_HEALTH, health);
            scale(le, Attribute.ATTACK_DAMAGE, damage);
            AttributeInstance hp = le.getAttribute(Attribute.MAX_HEALTH);
            if (hp != null) {
                le.setHealth(hp.getValue());
            }
            if (le instanceof org.bukkit.entity.PiglinAbstract piglin) {
                piglin.setImmuneToZombification(true);
            }
            if (le instanceof org.bukkit.entity.Ageable a) {
                a.setAdult();
            }
            if (le.getEquipment() != null) {
                le.getEquipment().setHelmet(new ItemStack(Material.LEATHER_HELMET)); // no burning in the sun, also via EntityCombustEvent
                le.getEquipment().setHelmetDropChance(0);
            }
        }
    }

    private static void scale(LivingEntity le, Attribute attribute, double factor) {
        AttributeInstance a = le.getAttribute(attribute);
        if (a != null) {
            a.setBaseValue(a.getBaseValue() * factor);
        }
    }

    /** Server stop or reload: the arena mobs go (they are never saved). */
    void clear() {
        bosses.clear();
        for (UUID id : List.copyOf(mobs.keySet())) {
            org.bukkit.entity.Entity e = Bukkit.getEntity(id);
            if (e != null) {
                e.remove();
            }
        }
        mobs.clear();
    }

    boolean inArena(Location l) {
        World w = areas.grindWorld();
        return w != null && l.getWorld().equals(w) && Math.abs(l.getBlockX() - X) <= HALF + 4 && Math.abs(l.getBlockZ()) <= HALF + 4;
    }

    private static Integer tierOf(org.bukkit.entity.Entity e) {
        return e.getPersistentDataContainer().get(TIER, PersistentDataType.INTEGER);
    }

    // ------------------------------------------------------------------ events

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(EntityDeathEvent event) {
        Integer tier = tierOf(event.getEntity());
        if (tier == null) {
            return;
        }
        mobs.remove(event.getEntity().getUniqueId());
        if (bosses.onDeath(event)) {
            return; // a personal boss (its own reward)
        }
        Player killer = event.getEntity().getKiller();
        if (killer == null || !inArena(killer.getLocation())) {
            event.getDrops().clear();
            event.setDroppedExp(0);
            return;
        }
        long[] k = kills(killer);
        int before = unlocked(k, TIER_LIST);
        k[tier - 1]++;
        killer.getPersistentDataContainer().set(KILLS, PersistentDataType.LONG_ARRAY, k);
        bosses.onKill(killer, tier, k[tier - 1]); // every 200 kills a Boss Sigil
        Tier t = TIER_LIST.get(tier - 1);
        areas.pay(killer, t.pay(), event.getEntity().getLocation().getBlock().getLocation());
        // The drops go straight to the bag (never left on the floor of a shared arena), what doesn't fit is lost.
        for (ItemStack drop : event.getDrops()) {
            killer.getInventory().addItem(drop);
        }
        Material egg = Material.matchMaterial(t.mob().getKey().getKey() + "_spawn_egg");
        Material first = event.getDrops().isEmpty() ? Material.BONE : event.getDrops().getFirst().getType();
        event.getDrops().clear();
        Bukkit.getPluginManager().callEvent(new GrindBreakEvent(killer, GrindBreakEvent.PVE, egg == null ? Material.ZOMBIE_SPAWN_EGG : egg,
                first, 1, event.getEntity().getLocation()));
        int after = unlocked(k, TIER_LIST);
        if (after > before) {
            areas.msg().send(killer, "grind.pve.unlocked", Msg.number("tier", after), Msg.p("mob", mobName(TIER_LIST.get(after - 1).mob())));
            areas.core().sounds().play(killer, "unlock");
        }
    }

    /** No burning in the sun and no fights among the arena mobs. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCombust(EntityCombustEvent event) {
        if (tierOf(event.getEntity()) != null && !(event instanceof org.bukkit.event.entity.EntityCombustByEntityEvent)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTarget(EntityTargetEvent event) {
        if (tierOf(event.getEntity()) != null && event.getTarget() != null && !(event.getTarget() instanceof Player)) {
            event.setCancelled(true);
        }
    }

    /** A locked ring can't be entered (the player is stopped at its edge with one line). */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Location to = event.getTo();
        if (to.getBlockX() == event.getFrom().getBlockX() && to.getBlockZ() == event.getFrom().getBlockZ()) {
            return;
        }
        Player p = event.getPlayer();
        if (!inArena(to) || p.getGameMode() == org.bukkit.GameMode.CREATIVE) {
            return;
        }
        int ring = ringAt(to.getBlockX() - X, to.getBlockZ());
        int open = unlocked(p);
        if (ring > open) {
            event.setCancelled(true);
            long now = System.currentTimeMillis();
            Long last = lastWarn.put(p.getUniqueId(), now);
            if (last == null || now - last > 2000) {
                areas.msg().actionBar(p, "grind.pve.locked", Msg.number("tier", ring), Msg.number("open", open),
                        Msg.p("need", progressText(p)));
            }
        }
    }

    /** Owner plan: dying in /pve costs nothing; you come back at the arena start. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (inArena(event.getEntity().getLocation())) {
            event.setKeepInventory(true);
            event.setKeepLevel(true);
            event.getDrops().clear();
            event.setDroppedExp(0);
            event.getEntity().getPersistentDataContainer().set(DIED, PersistentDataType.BYTE, (byte) 1);
        }
    }

    private static final NamespacedKey DIED = net.skybornmc.skyblock.common.item.ItemKeys.key("pve_died");

    @EventHandler(priority = EventPriority.HIGH)
    public void onRespawn(PlayerRespawnEvent event) {
        Player p = event.getPlayer();
        if (p.getPersistentDataContainer().has(DIED, PersistentDataType.BYTE)) {
            p.getPersistentDataContainer().remove(DIED);
            World w = areas.grindWorld();
            if (w != null) {
                event.setRespawnLocation(start(w, areas.base()));
            }
        }
    }

    /** Arena mobs never take a player out of the arena with knockback into the void (they stay on the floor anyway). */
    static boolean arenaMob(org.bukkit.entity.Entity e) {
        return e instanceof Mob && tierOf(e) != null;
    }

    /** Tier list as text for /help-like messages (tier: mob). */
    static List<String> tierNames() {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < TIER_LIST.size(); i++) {
            out.add((i + 1) + ": " + mobName(TIER_LIST.get(i).mob()));
        }
        return out;
    }
}
