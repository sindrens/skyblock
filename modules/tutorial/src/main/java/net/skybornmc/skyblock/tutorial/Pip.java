package net.skybornmc.skyblock.tutorial;

import net.kyori.adventure.text.Component;
import net.skybornmc.skyblock.common.Core;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Parrot;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Pip, the small red parrot only you can see (TUTORIAL.md §9.3, TU5, TQ9). It flies over your shoulder toward the step's
 * target, perches on it when you are close, and says one short line per step (name tag for {@code pip.line-seconds},
 * and in chat). No AI, silent, invulnerable, never saved with the chunk, visible to its player only (works on Bedrock:
 * a normal parrot). Hidden during cameras and while vanished. Moved by the guide tick. Main thread.
 */
final class Pip {

    private static final class Companion {
        Parrot parrot;
        double[] pos;
        long lineUntil;
        /** No spawn before this time: the last one was refused (PERF7: it used to retry on every guide run). */
        long retryAt;
    }

    private final TutorialModule module;
    private final Core core;
    private final NamespacedKey tag;
    private final Map<UUID, Companion> pips = new HashMap<>();

    Pip(TutorialModule module) {
        this.module = module;
        this.core = module.core();
        this.tag = new NamespacedKey(core.plugin(), "tutorial_pip");
    }

    /** Moves (or spawns) the player's Pip; called by the guide tick for running tutorials. */
    void update(Player player, Optional<Location> target) {
        if (!module.config().pipEnabled || hiddenFor(player)) {
            hide(player);
            return;
        }
        Companion c = pips.computeIfAbsent(player.getUniqueId(), k -> new Companion());
        Location at = player.getLocation();
        double[] targetPos = target.filter(t -> t.getWorld() != null && t.getWorld().equals(at.getWorld()))
                .map(t -> new double[]{t.getX(), t.getY(), t.getZ()}).orElse(null);
        TutorialConfig cfg = module.config();
        double[] desired = PipMotion.desired(new double[]{at.getX(), at.getY(), at.getZ()}, at.getYaw(), targetPos, cfg.pipPerchRange,
                cfg.pipSide, cfg.pipHeight);
        if (c.parrot != null && (!c.parrot.isValid() || !c.parrot.getWorld().equals(at.getWorld()))) {
            remove(c);
        }
        double[] next = c.parrot == null ? desired : PipMotion.step(c.pos, desired, cfg.pipStep, cfg.pipSnap);
        boolean perched = PipMotion.perched(next, targetPos);
        double dx = (perched ? at.getX() : desired[0]) - next[0];
        double dz = (perched ? at.getZ() : desired[2]) - next[2];
        // Face the player when perched or close, else the direction of flight.
        float yaw = perched || Math.abs(dx) + Math.abs(dz) < 0.2 ? CameraPath.yaw(at.getX() - next[0], at.getZ() - next[2]) : CameraPath.yaw(dx, dz);
        Location loc = new Location(at.getWorld(), next[0], next[1], next[2], yaw, 0);
        if (c.parrot == null) {
            long now = System.currentTimeMillis();
            if (now < c.retryAt) {
                return;
            }
            c.parrot = spawn(player, loc);
            if (c.parrot == null) {
                c.retryAt = now + module.config().spawnRetryMillis;
                return;
            }
        } else {
            c.parrot.teleport(loc);
        }
        c.parrot.setSitting(perched);
        c.pos = next;
        if (c.lineUntil > 0 && System.currentTimeMillis() > c.lineUntil) {
            c.lineUntil = 0;
            c.parrot.customName(module.msg().component(player, "pip.name"));
        }
    }

    private Parrot spawn(Player player, Location loc) {
        Parrot.Variant variant;
        try {
            variant = Parrot.Variant.valueOf(module.config().pipVariant.strip().toUpperCase(Locale.ROOT));
        } catch (RuntimeException e) {
            variant = Parrot.Variant.RED;
        }
        Parrot.Variant v = variant;
        Component name = module.msg().component(player, "pip.name");
        Parrot parrot = loc.getWorld().spawn(loc, Parrot.class, p -> {
            p.setVisibleByDefault(false);
            p.setPersistent(false);
            p.setAI(false);
            p.setSilent(true);
            p.setInvulnerable(true);
            p.setGravity(false);
            p.setCollidable(false);
            p.setRemoveWhenFarAway(false);
            p.setVariant(v);
            p.customName(name);
            p.setCustomNameVisible(true);
            p.addScoreboardTag(net.skybornmc.skyblock.common.stacking.StackingService.NO_STACK_TAG);
            p.getPersistentDataContainer().set(tag, PersistentDataType.STRING, player.getUniqueId().toString());
        });
        if (!parrot.isValid()) {
            return null; // spawn refused here; tried again after guide.spawn-retry-millis
        }
        player.showEntity(core.plugin(), parrot);
        return parrot;
    }

    /**
     * Pip says a line ({@code pip.lines.<key>}): the name tag shows it for {@code pip.line-seconds}, and chat when
     * {@code pip.chat}. Nothing happens when the line doesn't exist or Pip is off.
     */
    void say(Player player, String key) {
        String full = "pip.lines." + key;
        if (!module.config().pipEnabled || !module.msg().has(full)) {
            return;
        }
        if (module.config().pipChat) {
            module.msg().send(player, "pip.chat", net.skybornmc.skyblock.common.text.Msg.c("line", module.msg().component(player, full)));
        }
        Companion c = pips.get(player.getUniqueId());
        if (c != null && c.parrot != null && c.parrot.isValid()) {
            c.parrot.customName(module.msg().component(player, "pip.tag", net.skybornmc.skyblock.common.text.Msg.c("line",
                    module.msg().component(player, full))));
            c.lineUntil = System.currentTimeMillis() + module.config().pipLineSeconds * 1000L;
        }
    }

    void hide(Player player) {
        Companion c = pips.remove(player.getUniqueId());
        if (c != null) {
            remove(c);
        }
    }

    void hideAll() {
        for (Companion c : pips.values()) {
            remove(c);
        }
        pips.clear();
    }

    /** Drops Pips whose player is gone (safety net of the guide tick). */
    void prune() {
        for (UUID id : new ArrayList<>(pips.keySet())) {
            if (Bukkit.getPlayer(id) == null) {
                remove(pips.remove(id));
            }
        }
    }

    private static void remove(Companion c) {
        if (c.parrot != null && c.parrot.isValid()) {
            c.parrot.remove();
        }
        c.parrot = null;
        c.pos = null;
    }

    private boolean hiddenFor(Player player) {
        if (module.cameras().running(player.getUniqueId()) || player.isDead()
                || player.getGameMode() == org.bukkit.GameMode.SPECTATOR) {
            return true;
        }
        // TQ28: staff in vanish see no companion (vanish is published as the "vanished" placeholder).
        return core.placeholders().has("vanished")
                && core.placeholders().text(player, "vanished").map("true"::equalsIgnoreCase).orElse(false);
    }

    boolean isPip(Entity e) {
        return e.getPersistentDataContainer().has(tag, PersistentDataType.STRING);
    }

    /** Removes Pips left in loaded chunks (never saved; safety net at start). */
    void cleanStray() {
        for (World w : Bukkit.getWorlds()) {
            for (Entity e : w.getEntitiesByClass(Parrot.class)) {
                if (isPip(e)) {
                    e.remove();
                }
            }
        }
    }
}
