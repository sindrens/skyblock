package net.skybornmc.skyblock.tutorial;

import io.papermc.paper.entity.TeleportFlag;
import net.kyori.adventure.text.Component;
import net.skybornmc.skyblock.common.Core;
import net.skybornmc.skyblock.common.util.Scheduler;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Parrot;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Pip, the small red parrot only you can see (TUTORIAL.md §9.3, TU5, TQ9; TUT-FIX2, owner 31 Oct: "cuter, more alive,
 * clearly wants you to follow"). He leads the way along the guide trail, waits and circles when you stop, flies back
 * when you go another way, lands on the target (the chest, an NPC's head) with little hops and hearts, chirps when he
 * has something to say, and shows a 2-line name: {@code [ Pip ]} and a short callout ("Follow me!", "Open me!"). A
 * normal parrot (works on Bedrock): Java gets the name as a text display riding him, Bedrock a 2-line name tag. No AI,
 * silent, invulnerable, never saved, visible to its player only; hidden during cameras, in spectator and while vanished.
 * The guide tick gives him the target and route; his own light task ({@code pip.update-ticks}) moves him. Main thread.
 */
final class Pip {

    private static final class Companion {
        Parrot parrot;
        /** Pip's facing (degrees), eased every move (owner 3 Nov 2026: no snapping turns). */
        float yaw;
        TextDisplay label;
        boolean nameTag;
        final double[] pos = new double[3];
        boolean placed;
        final double[] desired = new double[3];
        final double[] anchor = new double[3];
        /** No spawn before this time: the last one was refused (PERF7: it used to retry on every guide run). */
        long retryAt;
        // ---- from the guide tick
        boolean guided;
        String step;
        boolean hasPerch;
        String perchWorld;
        final double[] perch = new double[3];
        /** He sits on the perch (a block: chest, farm spot, ore); with an offset (portal, NPC) he hovers. */
        boolean perchSits;
        Route route;
        int cursor;
        // ---- behaviour
        PipMotion.Mode mode = PipMotion.Mode.FOLLOW;
        double lastPx = Double.NaN;
        double lastPz = Double.NaN;
        long lastMoved;
        long hopStart = Long.MIN_VALUE / 2;
        long lastHop;
        long lastHeart;
        long lastCall;
        double angle;
        // ---- name display
        String lineKey;
        long lineUntil;
        String shown;
    }

    private final TutorialModule module;
    private final Core core;
    private final NamespacedKey tag;
    private final Map<UUID, Companion> pips = new HashMap<>();
    private Scheduler.Task task;

    Pip(TutorialModule module) {
        this.module = module;
        this.core = module.core();
        this.tag = new NamespacedKey(core.plugin(), "tutorial_pip");
    }

    void start() {
        stop();
        int every = Math.max(1, module.config().pipUpdateTicks);
        task = core.scheduler().repeat("tutorial-pip", every, every, () -> core.scheduler().timer().time("tutorial-guides-pip", this::tick));
    }

    void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    /**
     * The guide tick's input for a running tutorial: the step, where Pip lands ({@code perch}, empty = nowhere) and the
     * trail route with the player's place on it ({@code null} = no trail). Main thread.
     */
    void guide(Player player, String step, Optional<Location> perch, Route route, int cursor) {
        if (!module.config().pipEnabled) {
            hide(player);
            return;
        }
        Companion c = pips.computeIfAbsent(player.getUniqueId(), k -> new Companion());
        if (c.step != null && !c.step.equals(step)) {
            c.lineKey = null; // a new step: the old step line is over
        }
        c.guided = true;
        c.step = step;
        c.route = route;
        c.cursor = cursor;
        c.hasPerch = perch.isPresent() && perch.get().getWorld() != null;
        if (c.hasPerch) {
            Location l = perch.get();
            c.perchWorld = l.getWorld().getName();
            double[] off = PipMotion.perchOffset(module.config().pipPerchOffsets, step);
            c.perch[0] = l.getX() + off[0];
            c.perch[1] = l.getY() + off[1];
            c.perch[2] = l.getZ() + off[2];
            c.perchSits = off[0] == 0 && off[1] == 0 && off[2] == 0;
        }
    }

    // ------------------------------------------------------------------ motion (own task)

    private void tick() {
        if (pips.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, Companion> e : new ArrayList<>(pips.entrySet())) {
            Player player = Bukkit.getPlayer(e.getKey());
            Companion c = e.getValue();
            if (player == null) {
                remove(c);
                pips.remove(e.getKey());
                continue;
            }
            if (!module.config().pipEnabled || !c.guided || hiddenFor(player)) {
                remove(c);
                continue;
            }
            try {
                move(player, c, now);
            } catch (RuntimeException ex) {
                // TUT-FIX2: Pip must never break the tutorial; he is gone until the next try.
                core.plugin().getLogger().log(java.util.logging.Level.WARNING, "Tutorial: Pip failed for " + player.getName(), ex);
                remove(c);
                c.retryAt = now + module.config().spawnRetryMillis;
            }
        }
    }

    @SuppressWarnings("removal") // RETAIN_PASSENGERS: the name display rides Pip; kept until Paper drops the flag
    private void move(Player player, Companion c, long now) {
        TutorialConfig cfg = module.config();
        Location at = player.getLocation();
        World world = at.getWorld();
        if (c.parrot != null && (!c.parrot.isValid() || !c.parrot.getWorld().equals(world))) {
            remove(c);
        }
        // Did the player move (for WAIT / IDLE)?
        if (Double.isNaN(c.lastPx) || Math.abs(at.getX() - c.lastPx) + Math.abs(at.getZ() - c.lastPz) > 0.15) {
            c.lastPx = at.getX();
            c.lastPz = at.getZ();
            c.lastMoved = now;
        }
        boolean still = now - c.lastMoved > 1500;
        boolean perchHere = c.hasPerch && world.getName().equals(c.perchWorld);
        double toPerch = perchHere ? Math.hypot(c.perch[0] - at.getX(), c.perch[2] - at.getZ()) : Double.MAX_VALUE;
        double pipToPlayer = c.placed ? Math.sqrt(sq(c.pos[0] - at.getX()) + sq(c.pos[1] - at.getY()) + sq(c.pos[2] - at.getZ())) : 0;
        Route route = c.route;
        PipMotion.Mode before = c.mode;
        c.mode = PipMotion.mode(perchHere, toPerch, route != null, pipToPlayer, still, cfg.pipPerchRange, cfg.pipWaitDistance,
                cfg.pipReturnDistance, before);
        double[] d = c.desired;
        switch (c.mode) {
            case PERCH -> {
                d[0] = c.perch[0];
                d[1] = c.perch[1];
                d[2] = c.perch[2];
            }
            case LEAD -> {
                PipMotion.lead(route, c.cursor, cfg.pipLeadDistance, cfg.pipHeight, d);
                d[1] += PipMotion.bob(now, cfg.pipBobHeight);
            }
            case WAIT -> {
                PipMotion.lead(route, c.cursor, Math.min(cfg.pipLeadDistance, 3), cfg.pipHeight, c.anchor);
                c.angle += 0.35 * Math.max(1, cfg.pipUpdateTicks) / 2.0;
                PipMotion.circle(c.anchor, c.angle, PipMotion.CIRCLE, d);
            }
            default -> {
                double[] target = perchHere ? c.perch : null;
                double[] spot = PipMotion.desired(new double[]{at.getX(), at.getY(), at.getZ()}, at.getYaw(), target,
                        -1, cfg.pipSide, cfg.pipHeight);
                d[0] = spot[0];
                d[1] = spot[1] + PipMotion.bob(now, cfg.pipBobHeight);
                d[2] = spot[2];
            }
        }
        // Little hops while perched, waiting or idle.
        boolean hops = c.mode == PipMotion.Mode.PERCH || c.mode == PipMotion.Mode.WAIT || c.mode == PipMotion.Mode.IDLE;
        if (hops && cfg.pipHopSeconds > 0 && now - c.lastHop > cfg.pipHopSeconds * 1000L) {
            c.lastHop = now;
            c.hopStart = now;
        }
        double hop = PipMotion.hop(now - c.hopStart);
        double maxStep = cfg.pipStep * Math.max(1, cfg.pipUpdateTicks) / 5.0;
        // Owner 3 Nov 2026: never inside a block. The wanted spot goes up to free air first; a step that would end in a
        // block rises out of it, or Pip stays where he is this update (he never pushes through walls or floors).
        PipMotion.Solid solid = (bx, by, bz) -> world.getBlockAt(bx, by, bz).isSolid();
        PipMotion.lift(d, solid, 3);
        if (!c.placed) {
            c.pos[0] = d[0];
            c.pos[1] = d[1];
            c.pos[2] = d[2];
        } else {
            double ox = c.pos[0], oy = c.pos[1], oz = c.pos[2];
            PipMotion.stepInto(c.pos, d, maxStep, cfg.pipSnap, c.pos);
            if (!PipMotion.lift(c.pos, solid, 2)) {
                c.pos[0] = ox;
                c.pos[1] = oy + Math.min(maxStep, 0.4); // boxed in: rise slowly instead of clipping
                c.pos[2] = oz;
            }
        }
        boolean perched = c.mode == PipMotion.Mode.PERCH && PipMotion.perched(c.pos, c.perch);
        // Face the player when perched, waiting or close; else the way he flies.
        double fx = d[0] - c.pos[0];
        double fz = d[2] - c.pos[2];
        boolean facePlayer = perched || c.mode == PipMotion.Mode.WAIT || c.mode == PipMotion.Mode.IDLE || Math.abs(fx) + Math.abs(fz) < 0.2;
        if (c.mode == PipMotion.Mode.LEAD && route != null) {
            PipMotion.lead(route, c.cursor, cfg.pipLeadDistance + 2, 0, c.anchor); // look further along the way
            fx = c.anchor[0] - c.pos[0];
            fz = c.anchor[2] - c.pos[2];
            facePlayer = false;
        }
        float wanted = facePlayer ? CameraPath.yaw(at.getX() - c.pos[0], at.getZ() - c.pos[2]) : CameraPath.yaw(fx, fz);
        float yaw = c.placed ? PipMotion.easeYaw(c.yaw, wanted, 25f * Math.max(1, cfg.pipUpdateTicks)) : wanted;
        c.yaw = yaw;
        Location loc = new Location(world, c.pos[0], c.pos[1] + hop, c.pos[2], yaw, 0);
        if (c.parrot == null) {
            if (now < c.retryAt) {
                return;
            }
            if (!spawn(player, c, loc)) {
                c.retryAt = now + cfg.spawnRetryMillis;
                return;
            }
        } else {
            c.parrot.teleport(loc, TeleportFlag.EntityState.RETAIN_PASSENGERS);
        }
        c.placed = true;
        c.parrot.setSitting(perched && c.perchSits && hop == 0);
        effects(player, c, before, perched, now);
        label(player, c, now);
    }

    /** Hearts on the perch, a chirp and a note when he starts waiting or lands (only this player sees and hears it). */
    private void effects(Player player, Companion c, PipMotion.Mode before, boolean perched, long now) {
        TutorialConfig cfg = module.config();
        if (c.mode != before) {
            if (c.mode == PipMotion.Mode.WAIT && now - c.lastCall > 6000) {
                c.lastCall = now;
                sound(player, c, cfg.pipSoundSay, 1.3f);
                particle(player, c, Particle.NOTE, 1);
            } else if (c.mode == PipMotion.Mode.PERCH) {
                sound(player, c, cfg.pipSoundHappy, 1.4f);
                particle(player, c, Particle.HEART, 2);
                c.lastHeart = now;
            }
        }
        if (perched && now - c.lastHeart > cfg.pipHeartSeconds * 1000L) {
            c.lastHeart = now;
            particle(player, c, Particle.HEART, 1);
        }
    }

    private void particle(Player player, Companion c, Particle type, int count) {
        if (module.config().pipParticles) {
            player.spawnParticle(type, c.pos[0], c.pos[1] + 0.9, c.pos[2], count, 0.2, 0.1, 0.2, 0);
        }
    }

    private void sound(Player player, Companion c, String sound, float pitch) {
        TutorialConfig cfg = module.config();
        if (!cfg.pipSounds || sound == null || sound.isBlank()) {
            return;
        }
        try {
            player.playSound(new Location(player.getWorld(), c.pos[0], c.pos[1], c.pos[2]), sound.strip().toLowerCase(Locale.ROOT),
                    SoundCategory.NEUTRAL, (float) cfg.pipSoundVolume, pitch);
        } catch (RuntimeException ignored) {
            // an unknown sound id in tutorial.yml: silent
        }
    }

    // ------------------------------------------------------------------ the 2-line name

    /** Which message line 2 shows now: the step line for a moment, then the perch / wait / step callout. */
    private String lineKey(Companion c, long now) {
        if (c.lineKey != null && now < c.lineUntil && module.msg().has("pip.lines." + c.lineKey)) {
            return "pip.lines." + c.lineKey;
        }
        String step = c.step == null ? "" : c.step;
        if (c.mode == PipMotion.Mode.PERCH && module.msg().has("pip.perch." + step)) {
            return "pip.perch." + step;
        }
        if (c.mode == PipMotion.Mode.WAIT && module.msg().has("pip.wait")) {
            return "pip.wait";
        }
        return module.msg().has("pip.callouts." + step) ? "pip.callouts." + step : "pip.name-line2";
    }

    private void label(Player player, Companion c, long now) {
        String key = lineKey(c, now);
        long generation = core.messages().generation();
        String id = key + '|' + generation;
        if (id.equals(c.shown)) {
            return;
        }
        c.shown = id;
        Component text = module.msg().component(player, "pip.name-line1").appendNewline().append(module.msg().component(player, key));
        if (c.nameTag) {
            c.parrot.customName(text);
        } else if (c.label != null && c.label.isValid()) {
            c.label.text(text);
        }
    }

    private boolean useNameTag(Player player) {
        String mode = module.config().pipNameDisplay == null ? "auto" : module.config().pipNameDisplay.strip().toLowerCase(Locale.ROOT);
        return switch (mode) {
            case "name-tag", "name_tag", "nametag" -> true;
            case "text-display", "text_display" -> false;
            default -> module.engine().bedrock(player);
        };
    }

    private boolean spawn(Player player, Companion c, Location loc) {
        Parrot.Variant variant;
        try {
            variant = Parrot.Variant.valueOf(module.config().pipVariant.strip().toUpperCase(Locale.ROOT));
        } catch (RuntimeException e) {
            variant = Parrot.Variant.RED;
        }
        Parrot.Variant v = variant;
        boolean nameTag = useNameTag(player);
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
            p.setCustomNameVisible(nameTag);
            p.addScoreboardTag(net.skybornmc.skyblock.common.stacking.StackingService.NO_STACK_TAG);
            p.getPersistentDataContainer().set(tag, PersistentDataType.STRING, player.getUniqueId().toString());
        });
        if (!parrot.isValid()) {
            return false; // spawn refused here; tried again after guide.spawn-retry-millis
        }
        c.parrot = parrot;
        c.nameTag = nameTag;
        c.shown = null;
        if (!nameTag) {
            TextDisplay label = loc.getWorld().spawn(loc, TextDisplay.class, d -> {
                d.setVisibleByDefault(false);
                d.setPersistent(false);
                d.setBillboard(Display.Billboard.CENTER);
                d.setAlignment(TextDisplay.TextAlignment.CENTER);
                d.setShadowed(true);
                d.setSeeThrough(false);
                d.setTransformation(new Transformation(new Vector3f(0, 0.2f, 0), new AxisAngle4f(), new Vector3f(0.8f, 0.8f, 0.8f),
                        new AxisAngle4f()));
                d.getPersistentDataContainer().set(tag, PersistentDataType.STRING, player.getUniqueId().toString());
            });
            if (label.isValid() && parrot.addPassenger(label)) {
                c.label = label;
                player.showEntity(core.plugin(), label);
            } else {
                // No text display here: fall back to the name tag (never a Pip without a name).
                if (label.isValid()) {
                    label.remove();
                }
                c.nameTag = true;
                parrot.setCustomNameVisible(true);
            }
        }
        player.showEntity(core.plugin(), parrot);
        return true;
    }

    // ------------------------------------------------------------------ lines and moments

    /**
     * Pip says a line ({@code pip.lines.<key>}): his name display shows it for {@code pip.line-seconds} with a chirp, and
     * chat only when {@code pip.chat} (off by default: one place per message). Nothing when the line doesn't exist.
     */
    void say(Player player, String key) {
        String full = "pip.lines." + key;
        if (!module.config().pipEnabled || !module.msg().has(full)) {
            return;
        }
        if (module.config().pipChat) {
            module.msg().send(player, "pip.chat", net.skybornmc.skyblock.common.text.Msg.c("line", module.msg().component(player, full)));
        }
        Companion c = pips.computeIfAbsent(player.getUniqueId(), k -> new Companion());
        c.lineKey = key;
        c.lineUntil = System.currentTimeMillis() + module.config().pipLineSeconds * 1000L;
        c.shown = null;
        if (c.parrot != null && c.parrot.isValid()) {
            sound(player, c, module.config().pipSoundSay, 1.2f);
            particle(player, c, Particle.NOTE, 1);
        }
    }

    /** A step was done: a happy chirp and a few hearts. */
    void celebrate(Player player) {
        Companion c = pips.get(player.getUniqueId());
        if (c == null || c.parrot == null || !c.parrot.isValid()) {
            return;
        }
        sound(player, c, module.config().pipSoundHappy, 1.6f);
        particle(player, c, Particle.HEART, 4);
        c.hopStart = System.currentTimeMillis();
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
        if (c.label != null && c.label.isValid()) {
            c.label.remove();
        }
        if (c.parrot != null && c.parrot.isValid()) {
            c.parrot.remove();
        }
        c.label = null;
        c.parrot = null;
        c.placed = false;
        c.shown = null;
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

    /** Removes Pips and their name displays left in loaded chunks (never saved; safety net at start). */
    void cleanStray() {
        for (World w : Bukkit.getWorlds()) {
            for (Entity e : w.getEntitiesByClasses(Parrot.class, TextDisplay.class)) {
                if (isPip(e)) {
                    e.remove();
                }
            }
        }
    }

    private static double sq(double v) {
        return v * v;
    }
}
