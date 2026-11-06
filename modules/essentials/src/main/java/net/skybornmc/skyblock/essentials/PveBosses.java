package net.skybornmc.skyblock.essentials;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.skybornmc.skyblock.common.grind.GrindBreakEvent;
import net.skybornmc.skyblock.common.menu.Menu;
import net.skybornmc.skyblock.common.text.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Owner 7 Nov 2026 (docs/plans/BATCH8_BOSSES.md): personal /pve bosses. Every {@code sigil-every} kills in a ring give a
 * Boss Sigil of that ring; {@code /pve boss} spends one to summon the ring's boss (30x health, 1.5x damage, 3 minutes).
 * Only the summoner sees and hurts it; its kill pays 60x the ring's money, 25 kills of Combat XP and tool progress and
 * sometimes a crate key (every 10th boss without a key gives one for sure).
 */
final class PveBosses implements Listener {

    static final List<String> NAMES = List.of("Rotting King", "Desert Tyrant", "Bone Archer", "Brood Mother", "Frost Wraith",
            "Tide Caller", "Bastion Warlord", "Infernal Core", "Wither Knight", "Ravager Alpha");
    static final double HEALTH = 30;
    static final double DAMAGE = 1.5;
    static final long PAY = 60;
    static final int PROGRESS = 25;
    static final int PITY = 10;
    static final long SECONDS = 180;
    static final int MAX_BOSSES = 20;
    static final double KEY_CHANCE = 0.10;

    private static final NamespacedKey SIGILS = net.skybornmc.skyblock.common.item.ItemKeys.key("grind_pve_sigils");
    private static final NamespacedKey PITY_KEY = net.skybornmc.skyblock.common.item.ItemKeys.key("grind_pve_boss_pity");
    private static final NamespacedKey BOSS = net.skybornmc.skyblock.common.item.ItemKeys.key("pve_boss_owner");

    /** One living boss: its summoner, ring, end time and boss bar. */
    private record Live(UUID owner, int tier, long endsAt, BossBar bar) {
    }

    private final PveArena arena;
    private final GrindAreas areas;
    private final Map<UUID, Live> bosses = new ConcurrentHashMap<>(); // boss entity → state

    PveBosses(PveArena arena, GrindAreas areas) {
        this.arena = arena;
        this.areas = areas;
    }

    // ------------------------------------------------------------------ pure rules

    /** Sigils earned with this kill: one at every {@code every}-th kill of a ring. Pure. */
    static boolean sigilAt(long killsAfter, int every) {
        return every > 0 && killsAfter > 0 && killsAfter % every == 0;
    }

    /** A key with this roll, or for sure after {@code PITY - 1} bosses without one. Pure. */
    static boolean key(int withoutKey, double roll) {
        return withoutKey + 1 >= PITY || roll < KEY_CHANCE;
    }

    /** The crate of a ring's boss key. Pure. */
    static String crate(int tier) {
        return tier <= 5 ? "ember" : "cinder";
    }

    // ------------------------------------------------------------------ sigils

    static long[] sigils(Player p) {
        long[] v = p.getPersistentDataContainer().get(SIGILS, PersistentDataType.LONG_ARRAY);
        long[] out = new long[PveArena.TIERS];
        if (v != null) {
            System.arraycopy(v, 0, out, 0, Math.min(v.length, out.length));
        }
        return out;
    }

    private static void setSigils(Player p, long[] s) {
        p.getPersistentDataContainer().set(SIGILS, PersistentDataType.LONG_ARRAY, s);
    }

    /** A normal kill in ring {@code tier} with {@code killsAfter} kills there now: maybe a Sigil. */
    void onKill(Player p, int tier, long killsAfter) {
        if (!sigilAt(killsAfter, areas.pveSigilEvery())) {
            return;
        }
        long[] s = sigils(p);
        s[tier - 1]++;
        setSigils(p, s);
        areas.msg().send(p, "grind.pve.boss.sigil", Msg.number("tier", tier), Msg.p("boss", NAMES.get(tier - 1)), Msg.number("have", s[tier - 1]));
        areas.core().sounds().play(p, "unlock");
    }

    long totalSigils(Player p) {
        long sum = 0;
        for (long v : sigils(p)) {
            sum += v;
        }
        return sum;
    }

    // ------------------------------------------------------------------ summoning

    boolean hasBoss(Player p) {
        return bosses.values().stream().anyMatch(b -> b.owner().equals(p.getUniqueId()));
    }

    /** Spends a Sigil of ring {@code tier} and summons its boss in that ring (the summoner must be in the arena). */
    void summon(Player p, int tier) {
        World w = areas.grindWorld();
        if (w == null || !arena.inArena(p.getLocation())) {
            areas.msg().send(p, "grind.pve.boss.not-here");
            return;
        }
        if (tier > arena.unlocked(p)) {
            areas.msg().send(p, "grind.pve.boss.locked", Msg.number("tier", tier));
            return;
        }
        long[] s = sigils(p);
        if (s[tier - 1] <= 0) {
            areas.msg().send(p, "grind.pve.boss.no-sigil", Msg.number("tier", tier), Msg.number("every", areas.pveSigilEvery()));
            return;
        }
        if (hasBoss(p)) {
            areas.msg().send(p, "grind.pve.boss.one-at-a-time");
            return;
        }
        if (bosses.size() >= MAX_BOSSES) {
            areas.msg().send(p, "grind.pve.boss.busy");
            return;
        }
        s[tier - 1]--;
        setSigils(p, s);
        PveArena.Tier t = PveArena.TIER_LIST.get(tier - 1);
        Location at = arena.spot(w, areas.base(), tier);
        w.spawnEntity(at, t.mob(), org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.CUSTOM, e -> {
            if (!(e instanceof LivingEntity le)) {
                return;
            }
            arena.prepare(le, tier, t.health() * HEALTH, t.damage() * DAMAGE);
            le.getPersistentDataContainer().set(BOSS, PersistentDataType.STRING, p.getUniqueId().toString());
            le.customName(areas.msg().component(p, "grind.pve.boss.name", Msg.p("boss", NAMES.get(tier - 1))));
            le.setCustomNameVisible(true);
            le.setGlowing(true);
            for (Player other : w.getPlayers()) {
                if (!other.equals(p)) {
                    other.hideEntity(areas.core().plugin(), le);
                }
            }
            BossBar bar = BossBar.bossBar(Component.text(NAMES.get(tier - 1)), 1f, BossBar.Color.RED, BossBar.Overlay.NOTCHED_10);
            p.showBossBar(bar);
            bosses.put(le.getUniqueId(), new Live(p.getUniqueId(), tier, System.currentTimeMillis() + SECONDS * 1000, bar));
            if (le instanceof Mob mob) {
                mob.setTarget(p);
            }
        });
        areas.msg().send(p, "grind.pve.boss.summoned", Msg.p("boss", NAMES.get(tier - 1)), Msg.number("seconds", SECONDS));
        areas.core().sounds().play(p, "tool-prestige");
    }

    // ------------------------------------------------------------------ every second

    void tick(World w, int base) {
        long now = System.currentTimeMillis();
        for (var it = bosses.entrySet().iterator(); it.hasNext(); ) {
            var e = it.next();
            Live b = e.getValue();
            Entity ent = Bukkit.getEntity(e.getKey());
            Player owner = Bukkit.getPlayer(b.owner());
            if (!(ent instanceof LivingEntity le) || le.isDead() || owner == null || !arena.inArena(owner.getLocation()) || now >= b.endsAt()) {
                if (ent != null && !(ent instanceof LivingEntity l2 && l2.isDead())) {
                    ent.remove();
                }
                if (owner != null) {
                    owner.hideBossBar(b.bar());
                    if (now >= b.endsAt()) {
                        areas.msg().send(owner, "grind.pve.boss.escaped", Msg.p("boss", NAMES.get(b.tier() - 1)));
                    }
                }
                it.remove();
                continue;
            }
            AttributeInstance max = le.getAttribute(Attribute.MAX_HEALTH);
            float progress = max == null ? 1f : (float) Math.max(0, Math.min(1, le.getHealth() / max.getValue()));
            long left = Math.max(0, (b.endsAt() - now) / 1000);
            b.bar().progress(progress);
            b.bar().name(areas.msg().component(owner, "grind.pve.boss.bar", Msg.p("boss", NAMES.get(b.tier() - 1)),
                    Msg.number("seconds", left)));
            Location l = le.getLocation();
            if (PveArena.ringAt(l.getBlockX() - PveArena.X, l.getBlockZ()) != b.tier() || l.getY() < base - 2) {
                le.teleport(arena.spot(w, base, b.tier()));
            }
            if (le instanceof Mob mob && mob.getTarget() == null) {
                mob.setTarget(owner);
            }
        }
    }

    // ------------------------------------------------------------------ the kill

    static UUID ownerOf(Entity e) {
        String s = e.getPersistentDataContainer().get(BOSS, PersistentDataType.STRING);
        try {
            return s == null ? null : UUID.fromString(s);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    /** True when the dead entity was a boss (handled here, not as a normal kill). */
    boolean onDeath(org.bukkit.event.entity.EntityDeathEvent event) {
        UUID owner = ownerOf(event.getEntity());
        if (owner == null) {
            return false;
        }
        Live b = bosses.remove(event.getEntity().getUniqueId());
        Player p = Bukkit.getPlayer(owner);
        event.setDroppedExp(0);
        List<ItemStack> drops = new ArrayList<>(event.getDrops());
        event.getDrops().clear();
        if (p == null || b == null) {
            return true;
        }
        p.hideBossBar(b.bar());
        PveArena.Tier t = PveArena.TIER_LIST.get(b.tier() - 1);
        areas.pay(p, t.pay() * PAY);
        for (ItemStack d : drops) {
            d.setAmount(Math.min(d.getMaxStackSize(), d.getAmount() * 5)); // a boss drops five mobs' worth
            p.getInventory().addItem(d);
        }
        Material egg = Material.matchMaterial(t.mob().getKey().getKey() + "_spawn_egg");
        Bukkit.getPluginManager().callEvent(new GrindBreakEvent(p, GrindBreakEvent.PVE, egg == null ? Material.ZOMBIE_SPAWN_EGG : egg,
                drops.isEmpty() ? Material.BONE : drops.getFirst().getType(), PROGRESS, event.getEntity().getLocation()));
        Integer v = p.getPersistentDataContainer().get(PITY_KEY, PersistentDataType.INTEGER);
        int without = v == null ? 0 : v;
        boolean key = key(without, ThreadLocalRandom.current().nextDouble());
        p.getPersistentDataContainer().set(PITY_KEY, PersistentDataType.INTEGER, key ? 0 : without + 1);
        String crate = crate(b.tier());
        if (key) {
            UUID profile = net.skybornmc.skyblock.common.profile.ProfileService.of(areas.core().services()).profileKey(p);
            areas.core().services().optional(net.skybornmc.skyblock.common.crate.CrateKeys.class).ifPresent(k ->
                    k.giveKeys(profile, crate, 1, "pve_boss", net.skybornmc.skyblock.common.audit.Actor.system("pve_boss")));
        }
        areas.core().audit().logPlayer(net.skybornmc.skyblock.common.audit.AuditCategory.ECONOMY, "pve_boss",
                net.skybornmc.skyblock.common.audit.Actor.player(p), p.getUniqueId(), p.getName(), t.pay() * PAY, p.getLocation(),
                java.util.Map.of("ring", b.tier(), "key", key));
        areas.msg().title(p, "grind.pve.boss.defeated-title", Msg.p("boss", NAMES.get(b.tier() - 1)));
        areas.msg().send(p, key ? "grind.pve.boss.defeated-key" : "grind.pve.boss.defeated", Msg.p("boss", NAMES.get(b.tier() - 1)),
                Msg.p("money", net.skybornmc.skyblock.common.util.Format.money(t.pay() * PAY)), Msg.p("crate", crate.substring(0, 1).toUpperCase() + crate.substring(1)));
        areas.core().sounds().play(p, "tool-max");
        return true;
    }

    // ------------------------------------------------------------------ only the summoner

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        UUID owner = ownerOf(event.getEntity());
        if (owner != null) {
            Entity damager = event.getDamager();
            if (damager instanceof Projectile pr && pr.getShooter() instanceof Entity shooter) {
                damager = shooter;
            }
            if (!owner.equals(damager.getUniqueId())) {
                event.setCancelled(true);
            }
            return;
        }
        UUID by = ownerOf(event.getDamager() instanceof Projectile pr && pr.getShooter() instanceof Entity s ? s : event.getDamager());
        if (by != null && !by.equals(event.getEntity().getUniqueId())) {
            event.setCancelled(true); // a boss only hurts its summoner
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTarget(EntityTargetEvent event) {
        UUID owner = ownerOf(event.getEntity());
        if (owner != null && event.getTarget() != null && !owner.equals(event.getTarget().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        hideOthers(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorld(PlayerChangedWorldEvent event) {
        hideOthers(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        for (var it = bosses.entrySet().iterator(); it.hasNext(); ) {
            var e = it.next();
            if (e.getValue().owner().equals(id)) {
                Entity ent = Bukkit.getEntity(e.getKey());
                if (ent != null) {
                    ent.remove();
                }
                it.remove();
            }
        }
    }

    private void hideOthers(Player p) {
        bosses.forEach((id, b) -> {
            Entity ent = Bukkit.getEntity(id);
            if (ent != null && !b.owner().equals(p.getUniqueId())) {
                p.hideEntity(areas.core().plugin(), ent);
            }
        });
    }

    void clear() {
        bosses.forEach((id, b) -> {
            Entity ent = Bukkit.getEntity(id);
            if (ent != null) {
                ent.remove();
            }
            Player p = Bukkit.getPlayer(b.owner());
            if (p != null) {
                p.hideBossBar(b.bar());
            }
        });
        bosses.clear();
    }

    // ------------------------------------------------------------------ the menu

    /** {@code /pve boss}: the ten rings, Sigils owned, click to summon. No glass, two rows of five. */
    final class BossMenu extends Menu {

        @Override
        public int rows() {
            return 4;
        }

        @Override
        public Component title() {
            return service.title(viewer, areas.msg().component(viewer, "grind.pve.boss.menu.title"));
        }

        @Override
        public String messageModule() {
            return EssentialsModule.ID;
        }

        @Override
        protected void build() {
            navBar();
            long[] s = sigils(viewer);
            int open = arena.unlocked(viewer);
            for (int i = 0; i < PveArena.TIERS; i++) {
                int tier = i + 1;
                int slot = (i < 5 ? 11 : 20) + i % 5; // two centred rows of five
                boolean locked = tier > open;
                PveArena.Tier t = PveArena.TIER_LIST.get(i);
                Material icon = locked ? Material.GRAY_DYE : s[i] > 0 ? Material.NETHER_STAR : Material.FIREWORK_STAR;
                String key = locked ? "grind.pve.boss.menu.locked" : s[i] > 0 ? "grind.pve.boss.menu.ready" : "grind.pve.boss.menu.empty";
                set(slot, service.item(icon, viewer, EssentialsModule.ID, key, Msg.number("tier", tier), Msg.p("boss", NAMES.get(i)),
                        Msg.number("have", s[i]), Msg.number("every", areas.pveSigilEvery()),
                        Msg.p("money", net.skybornmc.skyblock.common.util.Format.money(t.pay() * PAY)),
                        Msg.p("crate", crate(tier).substring(0, 1).toUpperCase() + crate(tier).substring(1))), click -> {
                    if (!locked && s[tier - 1] > 0) {
                        click.player().closeInventory();
                        summon(click.player(), tier);
                    } else {
                        areas.core().sounds().play(click.player(), "error");
                    }
                });
            }
        }
    }

    void open(Player p) {
        areas.core().menus().open(p, new BossMenu());
    }
}
