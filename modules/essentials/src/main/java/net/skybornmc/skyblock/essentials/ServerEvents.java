package net.skybornmc.skyblock.essentials;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.skybornmc.skyblock.common.Core;
import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.audit.AuditCategory;
import net.skybornmc.skyblock.common.economy.AccountRef;
import net.skybornmc.skyblock.common.economy.Currency;
import net.skybornmc.skyblock.common.economy.EconomyService;
import net.skybornmc.skyblock.common.economy.Transaction;
import net.skybornmc.skyblock.common.grind.GrindBreakEvent;
import net.skybornmc.skyblock.common.profile.ProfileService;
import net.skybornmc.skyblock.common.text.ModuleMessages;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.util.Format;
import net.skybornmc.skyblock.common.util.Scheduler;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

/**
 * Owner 7 Nov 2026 (OWNER_PICKS_7NOV.md): two server events. <b>Meteor at spawn:</b> every hour five glowing fragments
 * land on the spawn plaza (display entities: no block changes); hitting one wears it down, the player who breaks it gets
 * the loot. <b>Fishing Contest:</b> every two hours for ten minutes at /fish, most catches win keys and crystals. One
 * chat line to start, one to end, a title and one boss bar each.
 */
final class ServerEvents implements Listener {

    static final String TAG = "phoenix_meteor";

    private final Core core;
    private final ModuleMessages msg;
    private final Supplier<EssentialsConfig> config;
    private Scheduler.Task task;

    // meteor
    private final Map<UUID, Fragment> fragments = new HashMap<>(); // interaction id -> fragment
    private final Map<UUID, Long> lastHit = new ConcurrentHashMap<>();
    private long meteorNext;
    private long meteorEnds;
    private BossBar meteorBar;

    // fishing contest
    private final Map<UUID, Integer> catches = new ConcurrentHashMap<>();
    private final Map<UUID, String> names = new ConcurrentHashMap<>();
    private long contestNext;
    private long contestEnds;
    private BossBar contestBar;

    private static final class Fragment {
        final Interaction box;
        final BlockDisplay look;
        int hp;

        Fragment(Interaction box, BlockDisplay look, int hp) {
            this.box = box;
            this.look = look;
            this.hp = hp;
        }
    }

    ServerEvents(Core core, ModuleMessages msg, Supplier<EssentialsConfig> config) {
        this.core = core;
        this.msg = msg;
        this.config = config;
    }

    void start() {
        long now = System.currentTimeMillis();
        meteorNext = now + 60_000L * Math.max(1, config.get().meteorEveryMinutes) / 2; // the first one sooner after a restart
        contestNext = now + 60_000L * Math.max(1, config.get().contestEveryMinutes) / 2;
        // Leftovers of a crash (non-persistent, but a chunk saved mid-event could keep them): removed once.
        World w = Bukkit.getWorld(config.get().meteorWorld);
        if (w != null) {
            w.getEntities().stream().filter(e -> e.getScoreboardTags().contains(TAG)).forEach(Entity::remove);
        }
        task = core.scheduler().repeat("essentials:server-events", 20L, 20L, this::tick);
    }

    void stop() {
        if (task != null) {
            task.cancel();
        }
        endMeteor(false);
        endContest(false);
    }

    private void tick() {
        EssentialsConfig c = config.get();
        long now = System.currentTimeMillis();
        if (c.meteorEnabled) {
            if (meteorEnds == 0 && now >= meteorNext && !Bukkit.getOnlinePlayers().isEmpty()) {
                startMeteor(c);
            } else if (meteorEnds > 0 && (now >= meteorEnds || fragments.isEmpty())) {
                endMeteor(true);
            } else if (meteorEnds > 0) {
                updateMeteorBar(now);
            }
        }
        if (c.contestEnabled) {
            if (contestEnds == 0 && now >= contestNext && !Bukkit.getOnlinePlayers().isEmpty()) {
                startContest(c);
            } else if (contestEnds > 0 && now >= contestEnds) {
                endContest(true);
            } else if (contestEnds > 0) {
                updateContestBar(now);
            }
        }
    }

    // ------------------------------------------------------------------ meteor

    private void startMeteor(EssentialsConfig c) {
        World w = Bukkit.getWorld(c.meteorWorld);
        if (w == null) {
            meteorNext = System.currentTimeMillis() + 60_000L * Math.max(1, c.meteorEveryMinutes);
            return;
        }
        Location center = new Location(w, c.meteorX, c.meteorY, c.meteorZ);
        int n = Math.max(1, c.meteorFragments);
        for (int i = 0; i < n; i++) {
            double a = Math.PI * 2 * i / n;
            Location at = center.clone().add(Math.cos(a) * c.meteorRadius, 0, Math.sin(a) * c.meteorRadius);
            BlockDisplay look = w.spawn(at.clone().add(-0.6, 0, -0.6), BlockDisplay.class, d -> {
                d.setBlock(Material.MAGMA_BLOCK.createBlockData());
                d.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(1.2f, 1.2f, 1.2f), new AxisAngle4f()));
                d.setGlowing(true);
                d.setPersistent(false);
                d.addScoreboardTag(TAG);
            });
            Interaction box = w.spawn(at, Interaction.class, b -> {
                b.setInteractionWidth(1.4f);
                b.setInteractionHeight(1.4f);
                b.setResponsive(true);
                b.setPersistent(false);
                b.addScoreboardTag(TAG);
            });
            fragments.put(box.getUniqueId(), new Fragment(box, look, Math.max(1, c.meteorHits)));
        }
        w.spawnParticle(Particle.EXPLOSION, center, 4, 2, 0.5, 2);
        w.playSound(center, org.bukkit.Sound.ENTITY_GENERIC_EXPLODE, 2f, 0.7f);
        meteorEnds = System.currentTimeMillis() + 1000L * Math.max(10, c.meteorSeconds);
        meteorBar = BossBar.bossBar(Component.empty(), 1f, BossBar.Color.RED, BossBar.Overlay.PROGRESS);
        for (Player p : Bukkit.getOnlinePlayers()) {
            msg.send(p, "server-events.meteor.landed");
            if (p.getWorld().equals(w)) {
                msg.title(p, "server-events.meteor.title");
            }
        }
        updateMeteorBar(System.currentTimeMillis());
    }

    private void updateMeteorBar(long now) {
        if (meteorBar == null) {
            return;
        }
        EssentialsConfig c = config.get();
        long left = Math.max(0, meteorEnds - now) / 1000;
        meteorBar.name(msg.component(net.kyori.adventure.audience.Audience.empty(), "server-events.meteor.bar",
                Msg.number("left", fragments.size()), Msg.p("time", clock(left))));
        meteorBar.progress(Math.max(0f, Math.min(1f, left / (float) Math.max(10, c.meteorSeconds))));
        World w = Bukkit.getWorld(c.meteorWorld);
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (w != null && p.getWorld().equals(w)) {
                p.showBossBar(meteorBar);
            } else {
                p.hideBossBar(meteorBar);
            }
        }
    }

    private void endMeteor(boolean announce) {
        for (Fragment f : List.copyOf(fragments.values())) {
            f.box.remove();
            f.look.remove();
        }
        boolean wasOn = meteorEnds > 0;
        fragments.clear();
        meteorEnds = 0;
        meteorNext = System.currentTimeMillis() + 60_000L * Math.max(1, config.get().meteorEveryMinutes);
        if (meteorBar != null) {
            BossBar bar = meteorBar;
            Bukkit.getOnlinePlayers().forEach(p -> p.hideBossBar(bar));
            meteorBar = null;
        }
        if (announce && wasOn) {
            World w = Bukkit.getWorld(config.get().meteorWorld);
            if (w != null) {
                w.getPlayers().forEach(p -> msg.actionBar(p, "server-events.meteor.gone"));
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEntityEvent event) {
        if (event.getRightClicked().getScoreboardTags().contains(TAG)) {
            event.setCancelled(true);
            if (event.getHand() == org.bukkit.inventory.EquipmentSlot.HAND) {
                hit(event.getPlayer(), event.getRightClicked());
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onAttack(io.papermc.paper.event.player.PrePlayerAttackEntityEvent event) {
        if (event.getAttacked().getScoreboardTags().contains(TAG)) {
            event.setCancelled(true);
            hit(event.getPlayer(), event.getAttacked());
        }
    }

    private void hit(Player p, Entity clicked) {
        Fragment f = fragments.get(clicked.getUniqueId());
        if (f == null) {
            return;
        }
        long now = System.currentTimeMillis();
        Long last = lastHit.get(p.getUniqueId());
        if (last != null && now - last < 200) {
            return; // no auto-clicker race
        }
        lastHit.put(p.getUniqueId(), now);
        f.hp--;
        Location at = f.box.getLocation().add(0, 0.7, 0);
        at.getWorld().spawnParticle(Particle.LAVA, at, 3, 0.3, 0.3, 0.3);
        at.getWorld().playSound(at, org.bukkit.Sound.BLOCK_STONE_HIT, 1f, 0.8f);
        if (f.hp > 0) {
            msg.actionBar(p, "server-events.meteor.hit", Msg.number("hp", f.hp));
            return;
        }
        fragments.remove(clicked.getUniqueId());
        f.box.remove();
        f.look.remove();
        at.getWorld().spawnParticle(Particle.EXPLOSION, at, 1);
        at.getWorld().playSound(at, org.bukkit.Sound.ENTITY_GENERIC_EXPLODE, 1f, 1.2f);
        loot(p);
    }

    private void loot(Player p) {
        EssentialsConfig c = config.get();
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        long money = rnd.nextLong(Math.max(1, c.meteorMoneyMin), Math.max(c.meteorMoneyMin, c.meteorMoneyMax) + 1);
        long crystals = rnd.nextLong(Math.max(0, c.meteorCrystalsMin), Math.max(c.meteorCrystalsMin, c.meteorCrystalsMax) + 1);
        boolean key = rnd.nextDouble(100) < c.meteorKeyChance;
        UUID profile = ProfileService.of(core.services()).profileKey(p);
        UUID ref = UUID.randomUUID();
        core.services().optional(EconomyService.class).ifPresent(e -> {
            List<Transaction> txs = new ArrayList<>();
            txs.add(Transaction.deposit(AccountRef.player(profile), Currency.MONEY, money, "meteor").actor(Actor.system("essentials"))
                    .idempotencyKey("meteor:" + ref));
            if (crystals > 0) {
                txs.add(Transaction.deposit(AccountRef.player(profile), Currency.CRYSTALS, crystals, "meteor").actor(Actor.system("essentials"))
                        .idempotencyKey("meteor_c:" + ref));
            }
            e.executeBatch(txs, conn -> true);
        });
        if (key) {
            core.services().optional(net.skybornmc.skyblock.common.crate.CrateKeys.class)
                    .ifPresent(k -> k.giveKeys(profile, c.eventKeyCrate, 1, "meteor", Actor.system("essentials")));
        }
        msg.send(p, key ? "server-events.meteor.loot-key" : "server-events.meteor.loot", Msg.p("money", Format.money(money)),
                Msg.p("crystals", Format.number(crystals)), Msg.p("crate", c.eventKeyCrate));
        core.sounds().play(p, "unlock");
        core.audit().logPlayer(AuditCategory.ECONOMY, "meteor_loot", Actor.SYSTEM, p.getUniqueId(), p.getName(), money, p.getLocation(),
                Map.of("crystals", crystals, "key", key));
    }

    // ------------------------------------------------------------------ fishing contest

    private void startContest(EssentialsConfig c) {
        catches.clear();
        names.clear();
        contestEnds = System.currentTimeMillis() + 60_000L * Math.max(1, c.contestMinutes);
        contestBar = BossBar.bossBar(Component.empty(), 1f, BossBar.Color.BLUE, BossBar.Overlay.PROGRESS);
        for (Player p : Bukkit.getOnlinePlayers()) {
            msg.send(p, "server-events.contest.start", Msg.number("minutes", c.contestMinutes));
            if (inPond(p)) {
                msg.title(p, "server-events.contest.title", Msg.number("minutes", c.contestMinutes));
            }
        }
        updateContestBar(System.currentTimeMillis());
    }

    private boolean inPond(Player p) {
        return p.getWorld().getName().equals(config.get().contestWorld);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onCatch(GrindBreakEvent event) {
        if (contestEnds > 0 && event.fish() && event.amount() > 0) {
            catches.merge(event.getPlayer().getUniqueId(), 1, Integer::sum);
            names.put(event.getPlayer().getUniqueId(), event.getPlayer().getName());
        }
    }

    private List<Map.Entry<UUID, Integer>> ranking() {
        List<Map.Entry<UUID, Integer>> list = new ArrayList<>(catches.entrySet());
        list.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
        return list;
    }

    private void updateContestBar(long now) {
        if (contestBar == null) {
            return;
        }
        EssentialsConfig c = config.get();
        long left = Math.max(0, contestEnds - now) / 1000;
        contestBar.progress(Math.max(0f, Math.min(1f, left / (float) (60L * Math.max(1, c.contestMinutes)))));
        List<Map.Entry<UUID, Integer>> rank = ranking();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!inPond(p)) {
                p.hideBossBar(contestBar);
                continue;
            }
            int place = 0;
            for (int i = 0; i < rank.size(); i++) {
                if (rank.get(i).getKey().equals(p.getUniqueId())) {
                    place = i + 1;
                }
            }
            BossBar own = contestBar;
            own.name(msg.component(p, "server-events.contest.bar", Msg.p("time", clock(left)),
                    Msg.number("catches", catches.getOrDefault(p.getUniqueId(), 0)), Msg.p("place", place == 0 ? "-" : "#" + place)));
            p.showBossBar(own);
        }
    }

    private void endContest(boolean pay) {
        if (contestBar != null) {
            BossBar bar = contestBar;
            Bukkit.getOnlinePlayers().forEach(p -> p.hideBossBar(bar));
            contestBar = null;
        }
        boolean wasOn = contestEnds > 0;
        contestEnds = 0;
        contestNext = System.currentTimeMillis() + 60_000L * Math.max(1, config.get().contestEveryMinutes);
        if (!pay || !wasOn) {
            return;
        }
        EssentialsConfig c = config.get();
        List<Map.Entry<UUID, Integer>> rank = ranking();
        Map<Integer, String> line = new LinkedHashMap<>();
        for (int i = 0; i < Math.min(3, rank.size()); i++) {
            UUID player = rank.get(i).getKey();
            int keys = i < c.contestKeys.size() ? c.contestKeys.get(i) : 0;
            long crystals = i < c.contestCrystals.size() ? c.contestCrystals.get(i) : 0;
            line.put(i + 1, names.getOrDefault(player, "?") + " (" + rank.get(i).getValue() + ")");
            Player online = Bukkit.getPlayer(player);
            UUID profile = online == null ? player : ProfileService.of(core.services()).profileKey(online);
            String ref = "contest:" + contestNext + ":" + (i + 1);
            if (crystals > 0) {
                core.services().optional(EconomyService.class).ifPresent(e -> e.execute(Transaction.deposit(AccountRef.player(profile),
                        Currency.CRYSTALS, crystals, "fishing_contest").actor(Actor.system("essentials")).idempotencyKey(ref)));
            }
            if (keys > 0) {
                core.services().optional(net.skybornmc.skyblock.common.crate.CrateKeys.class)
                        .ifPresent(k -> k.giveKeys(profile, c.eventKeyCrate, keys, "fishing_contest", Actor.system("essentials")));
            }
            if (online != null) {
                msg.send(online, "server-events.contest.won", Msg.number("place", i + 1), Msg.number("keys", keys),
                        Msg.p("crystals", Format.number(crystals)), Msg.p("crate", c.eventKeyCrate));
            }
        }
        core.audit().log(AuditCategory.ECONOMY, "fishing_contest", Actor.system("essentials"), "server", (long) rank.size(), null,
                Map.of("top", String.valueOf(line)));
        for (Player p : Bukkit.getOnlinePlayers()) {
            msg.send(p, rank.isEmpty() ? "server-events.contest.nobody" : "server-events.contest.end",
                    Msg.p("first", line.getOrDefault(1, "-")), Msg.p("second", line.getOrDefault(2, "-")), Msg.p("third", line.getOrDefault(3, "-")));
        }
    }

    /** {@code 7:05}. Pure. */
    static String clock(long seconds) {
        return (seconds / 60) + ":" + (seconds % 60 < 10 ? "0" : "") + (seconds % 60);
    }

    /** The current contest's catches (tests and staff). */
    Map<UUID, Integer> catches() {
        return Map.copyOf(catches);
    }

    Set<UUID> fragmentIds() {
        return Set.copyOf(fragments.keySet());
    }
}
