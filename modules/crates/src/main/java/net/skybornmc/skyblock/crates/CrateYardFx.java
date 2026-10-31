package net.skybornmc.skyblock.crates;

import net.kyori.adventure.sound.Sound;
import net.skybornmc.skyblock.common.pack.PackService;
import net.skybornmc.skyblock.common.util.Scheduler;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * The ambience of the crate yard, one repeating task for all crates: rarity dust above the rarer crates while a player is
 * near, and the quiet approach chime (once per approach, with a cooldown, only for that player). The same task keeps the
 * crate models in step with each viewer's pack status. Settings are read when it starts (a reload restarts it).
 */
final class CrateYardFx {

    private final CratesModule m;
    private final Supplier<List<Pedestals.Pedestal>> pedestals;
    private final Function<String, Color> colors;
    private final CrateDisplays displays;
    private Scheduler.Task task = Scheduler.Task.NONE;
    private CrateAmbient.Approach approach = new CrateAmbient.Approach(1, 1, 0);
    private final Map<String, Particle.DustOptions> dust = new HashMap<>();
    private Optional<Sound> packSound = Optional.empty();
    private Optional<Sound> fallbackSound = Optional.empty();
    private boolean particles;
    private boolean chime;
    private double particleRangeSq;

    CrateYardFx(CratesModule module, Supplier<List<Pedestals.Pedestal>> pedestals, Function<String, Color> colors,
                CrateDisplays displays) {
        this.m = module;
        this.pedestals = pedestals;
        this.colors = colors;
        this.displays = displays;
    }

    void start() {
        stop();
        CratesSettings c = m.settings;
        if (!c.ambientEnabled) {
            return;
        }
        particles = c.ambientParticles > 0;
        chime = c.approachEnabled;
        particleRangeSq = c.ambientParticleRange * c.ambientParticleRange;
        approach = new CrateAmbient.Approach(c.approachRange, c.approachResetRange, c.approachCooldownSeconds * 1000L);
        packSound = CrateShow.sound(c.approachPackSound);
        fallbackSound = CrateShow.sound(c.approachFallbackSound);
        dust.clear();
        task = m.core.scheduler().repeat("crates:yard-ambient", c.ambientInterval, c.ambientInterval, this::tick);
    }

    void stop() {
        task.cancel();
        task = Scheduler.Task.NONE;
        approach.clear();
    }

    void quit(Player player) {
        approach.forget(player.getUniqueId());
    }

    private void tick() {
        displays.syncAll();
        List<Pedestals.Pedestal> list = pedestals.get();
        if (list.isEmpty() || Bukkit.getOnlinePlayers().isEmpty() || !(particles || chime)) {
            return;
        }
        long now = System.currentTimeMillis();
        boolean[] near = new boolean[list.size()];
        PackService pack = m.core.services().optional(PackService.class).orElse(null);
        for (Player player : Bukkit.getOnlinePlayers()) {
            Location loc = player.getLocation();
            String world = loc.getWorld().getName();
            UUID id = player.getUniqueId();
            boolean spectator = player.getGameMode() == GameMode.SPECTATOR;
            for (int i = 0; i < near.length; i++) {
                Pedestals.Pedestal p = list.get(i);
                double d2 = Double.MAX_VALUE;
                if (p.world().equals(world)) {
                    double dx = loc.getX() - (p.x() + 0.5);
                    double dy = loc.getY() - (p.y() + 0.5);
                    double dz = loc.getZ() - (p.z() + 0.5);
                    d2 = dx * dx + dy * dy + dz * dz;
                }
                if (d2 <= particleRangeSq) {
                    near[i] = true;
                }
                if (chime && !spectator && approach.update(id, p.crateId(), d2, now) && visible(p.crateId())) {
                    Optional<Sound> sound = pack != null && pack.hasPack(player) ? packSound : fallbackSound;
                    sound.ifPresent(s -> player.playSound(s, p.x() + 0.5, p.y() + 0.5, p.z() + 0.5));
                }
            }
        }
        if (particles) {
            for (int i = 0; i < near.length; i++) {
                if (near[i]) {
                    dust(list.get(i));
                }
            }
        }
    }

    private boolean visible(String crateId) {
        return m.catalog().crate(crateId).map(c -> !c.hidden()).orElse(false);
    }

    private void dust(Pedestals.Pedestal p) {
        Optional<CrateCatalog.Crate> crate = m.catalog().crate(p.crateId());
        World w = Bukkit.getWorld(p.world());
        if (crate.isEmpty() || crate.get().hidden() || w == null || !CrateAmbient.atLeast(crate.get().rarity(), m.settings.ambientMinRarity)) {
            return;
        }
        Particle.DustOptions options = dust.computeIfAbsent(p.crateId(),
                k -> new Particle.DustOptions(colors.apply(crate.get().rarity()), (float) m.settings.ambientParticleSize));
        w.spawnParticle(Particle.DUST, p.x() + 0.5, p.y() + 1.0, p.z() + 0.5, m.settings.ambientParticles, 0.4, 0.3, 0.4, 0,
                options);
    }
}
