package net.skybornmc.skyblock.tutorial;

import net.skybornmc.skyblock.common.Core;
import net.skybornmc.skyblock.common.util.Scheduler;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;

/**
 * The tutorial cameras (TUTORIAL.md §9.4, TU3, TQ10/TQ11): {@code spawn} (first join), {@code island} (arrival step),
 * {@code depths} (Mine Tier 1) and {@code market} (optional spawn tour).
 * <ul>
 *   <li><b>Java:</b> spectator mode following an invisible item display that glides along the shots (the client smooths
 *   each move); sneaking stops spectating = skip.</li>
 *   <li><b>Bedrock</b> (and Java with {@code cameras.java-smooth: false}): cut-and-fade: a short blindness fade, the
 *   player hovers frozen at each shot facing its look-at point, a title per shot; sneak = skip. Geyser's camera API is
 *   not on this server's classpath, so this is the Bedrock path on every client version.</li>
 * </ul>
 * Before a camera starts, game mode, position, flight, speeds and invulnerability are saved in the player's data
 * container ({@link CameraState}); quit, kick, death, module stop and a crash all give them back (a crash: on the next
 * join, before anything else). The player is invulnerable while a camera runs. Main thread.
 */
final class Cameras {

    static final String SPAWN = "spawn";
    static final String ISLAND = "island";
    static final String DEPTHS = "depths";
    static final String MARKET = "market";
    static final List<String> ALL = List.of(SPAWN, ISLAND, DEPTHS, MARKET);

    /** One frame of the cut-and-fade camera: where the player hovers, for how long, and the shot's title. */
    private record Cut(Location at, int ticks, String text) {
    }

    private static final class Session {
        final String camera;
        final Runnable onEnd;
        boolean smooth;
        final List<CameraPath.Waypoint> path = new ArrayList<>();
        final Map<Integer, String> texts = new HashMap<>();
        final List<Cut> cuts = new ArrayList<>();
        World world;
        ItemDisplay display;
        Scheduler.Task task;
        int index;

        Session(String camera, Runnable onEnd, boolean smooth) {
            this.camera = camera;
            this.onEnd = onEnd;
            this.smooth = smooth;
        }
    }

    private final TutorialModule module;
    private final Core core;
    private final NamespacedKey stateKey;
    private final NamespacedKey entityKey;
    private final Map<UUID, Session> sessions = new HashMap<>();
    private volatile Map<String, CameraShot.Parsed> cameras = Map.of();

    Cameras(TutorialModule module) {
        this.module = module;
        this.core = module.core();
        this.stateKey = new NamespacedKey(core.plugin(), "tutorial_camera_state");
        this.entityKey = new NamespacedKey(core.plugin(), "tutorial_camera");
        rules();
    }

    /** (Re)reads the camera lines; problems go to the log. */
    void rules() {
        TutorialConfig c = module.config();
        int max = c.cameraMaxSeconds * 20;
        Map<String, CameraShot.Parsed> out = new HashMap<>();
        out.put(SPAWN, CameraShot.parse(SPAWN, c.cameraSpawn, max));
        out.put(ISLAND, CameraShot.parse(ISLAND, c.cameraIsland, max));
        out.put(DEPTHS, CameraShot.parse(DEPTHS, c.cameraDepths, max));
        out.put(MARKET, CameraShot.parse(MARKET, c.cameraMarket, max));
        out.values().forEach(p -> p.problems().forEach(problem -> core.plugin().getLogger().warning("tutorial.yml → " + problem)));
        cameras = Map.copyOf(out);
    }

    boolean running(UUID player) {
        return sessions.containsKey(player);
    }

    /** True if the camera exists and cameras are on (it may still fail to play for a player, see {@link #play}). */
    boolean available(String camera) {
        CameraShot.Parsed p = cameras.get(camera);
        return module.config().camerasEnabled && p != null && !p.shots().isEmpty();
    }

    // ================================================================== play

    /**
     * Plays a camera. Returns false (and changes nothing) when cameras are off, the camera has no shots, one of its
     * places can't be found (no island home, unloaded world) or the player is already in a camera. When it returns true,
     * {@code onEnd} runs once on the main thread after the camera ended or was skipped (not after a quit or module stop).
     */
    boolean play(Player player, String camera, Runnable onEnd) {
        if (!available(camera) || sessions.containsKey(player.getUniqueId()) || player.isDead() || !player.isOnline()) {
            return false;
        }
        CameraShot.Parsed parsed = cameras.get(camera);
        Map<String, Location> anchors = new HashMap<>();
        for (String anchor : parsed.anchors()) {
            Optional<Location> at = anchor(player, anchor);
            if (at.isEmpty()) {
                return false;
            }
            anchors.put(anchor, at.get());
        }
        World world = null;
        for (Location l : anchors.values()) {
            if (world == null) {
                world = l.getWorld();
            } else if (!world.equals(l.getWorld())) {
                core.plugin().getLogger().warning("tutorial.yml → cameras." + camera + " uses more than one world; it is not played.");
                return false;
            }
        }
        boolean smooth = module.config().cameraJavaSmooth && !module.engine().bedrock(player);
        Session s = new Session(camera, onEnd, smooth);
        s.world = world;
        for (CameraShot shot : parsed.shots()) {
            double[] from = point(shot.from(), anchors);
            double[] to = point(shot.to(), anchors);
            double[] look = point(shot.look(), anchors);
            if (smooth) {
                if (shot.text() != null) {
                    s.texts.put(s.path.size(), shot.text());
                }
                s.path.addAll(CameraPath.waypoints(from, to, look, shot.ticks(), module.config().cameraSegmentTicks));
            }
            cuts(s, world, from, to, look, shot); // also the fallback when the Java camera entity can't spawn
        }
        if (player.isInsideVehicle()) {
            player.leaveVehicle();
        }
        save(player);
        sessions.put(player.getUniqueId(), s);
        player.setInvulnerable(true);
        player.setFallDistance(0);
        module.engine().cameraEvent(player, camera, "camera_play");
        module.pip().hide(player);
        try {
            if (smooth) {
                startSmooth(player, s);
            } else {
                startCuts(player, s);
            }
        } catch (RuntimeException e) {
            core.plugin().getLogger().log(Level.WARNING, "Tutorial: camera " + camera + " failed for " + player.getName(), e);
            end(player, false, false);
            return false;
        }
        return true;
    }

    private Optional<Location> anchor(Player player, String anchor) {
        return switch (anchor) {
            case "@self" -> Optional.of(player.getLocation());
            case "@home" -> module.targets().home(player);
            case "@zone" -> module.targets().depthsSpawn();
            default -> {
                if (anchor.startsWith("@")) {
                    yield Optional.empty();
                }
                World w = Bukkit.getWorld(anchor);
                yield w == null ? Optional.empty() : Optional.of(new Location(w, 0, 0, 0));
            }
        };
    }

    private static double[] point(CameraShot.CamPoint p, Map<String, Location> anchors) {
        Location base = anchors.get(p.anchor());
        if (p.relative()) {
            return new double[]{base.getX() + p.x(), base.getY() + p.y(), base.getZ() + p.z()};
        }
        return new double[]{p.x(), p.y(), p.z()};
    }

    /** A long shot that moves gets two cuts (start and end), a short or still one a single cut. */
    private static void cuts(Session s, World world, double[] from, double[] to, double[] look, CameraShot shot) {
        boolean moves = Math.abs(from[0] - to[0]) + Math.abs(from[1] - to[1]) + Math.abs(from[2] - to[2]) > 1;
        if (moves && shot.ticks() >= 40) {
            int half = shot.ticks() / 2;
            s.cuts.add(new Cut(facing(world, from, look), half, shot.text()));
            s.cuts.add(new Cut(facing(world, to, look), shot.ticks() - half, null));
        } else {
            s.cuts.add(new Cut(facing(world, from, look), shot.ticks(), shot.text()));
        }
    }

    private static Location facing(World world, double[] pos, double[] look) {
        double dx = look[0] - pos[0];
        double dy = look[1] - pos[1];
        double dz = look[2] - pos[2];
        return new Location(world, pos[0], pos[1], pos[2], CameraPath.yaw(dx, dz), CameraPath.pitch(dx, dy, dz));
    }

    private Location at(World world, CameraPath.Waypoint w) {
        return new Location(world, w.x(), w.y(), w.z(), w.yaw(), w.pitch());
    }

    // ------------------------------------------------------------------ Java: spectator + gliding display

    private void startSmooth(Player player, Session s) {
        CameraPath.Waypoint first = s.path.getFirst();
        Location start = at(s.world, first);
        player.setGameMode(GameMode.SPECTATOR);
        player.teleport(start);
        s.display = s.world.spawn(start, ItemDisplay.class, d -> {
            d.setVisibleByDefault(false);
            d.setPersistent(false);
            d.setTeleportDuration(s.path.size() > 1 ? Math.min(CameraPath.MAX_SEGMENT_TICKS, s.path.get(1).ticks()) : 0);
            d.getPersistentDataContainer().set(entityKey, PersistentDataType.STRING, player.getUniqueId().toString());
        });
        if (!s.display.isValid()) {
            // Spawn refused (e.g. an island's display limit): the same shots as cut-and-fade.
            s.display = null;
            s.smooth = false;
            startCuts(player, s);
            return;
        }
        player.showEntity(core.plugin(), s.display);
        title(player, s, 0);
        s.index = 0;
        s.task = core.scheduler().later("tutorial-camera", module.config().cameraStartDelayTicks, () -> {
            if (sessions.get(player.getUniqueId()) != s || !player.isOnline()) {
                return;
            }
            player.setSpectatorTarget(s.display);
            module.msg().actionBar(player, "cameras.skip-hint");
            nextWaypoint(player, s);
        });
    }

    private void nextWaypoint(Player player, Session s) {
        if (sessions.get(player.getUniqueId()) != s || !player.isOnline()) {
            return;
        }
        s.index++;
        if (s.index >= s.path.size()) {
            end(player, false, true);
            return;
        }
        CameraPath.Waypoint w = s.path.get(s.index);
        if (s.display == null || !s.display.isValid()) {
            end(player, false, true);
            return;
        }
        s.display.teleport(at(s.world, w));
        // The smoothing of the NEXT move is set one move ahead, so the client has it before that move arrives.
        int next = s.index + 1 < s.path.size() ? s.path.get(s.index + 1).ticks() : 0;
        s.display.setTeleportDuration(Math.min(CameraPath.MAX_SEGMENT_TICKS, next));
        if (player.getSpectatorTarget() == null && player.getGameMode() == GameMode.SPECTATOR) {
            player.setSpectatorTarget(s.display);
        }
        title(player, s, s.index);
        s.task = core.scheduler().later("tutorial-camera", Math.max(1, w.ticks()), () -> nextWaypoint(player, s));
    }

    private void title(Player player, Session s, int index) {
        String text = s.texts.get(index);
        if (text != null && module.msg().has("cameras." + s.camera + "." + text + ".title")) {
            module.msg().title(player, "cameras." + s.camera + "." + text);
        }
    }

    // ------------------------------------------------------------------ Bedrock: cut and fade

    private void startCuts(Player player, Session s) {
        player.setAllowFlight(true);
        player.setFlying(true);
        player.setWalkSpeed(0f);
        player.setFlySpeed(0f);
        s.index = -1;
        module.msg().actionBar(player, "cameras.skip-hint");
        nextCut(player, s);
    }

    private void nextCut(Player player, Session s) {
        if (sessions.get(player.getUniqueId()) != s || !player.isOnline()) {
            return;
        }
        s.index++;
        if (s.index >= s.cuts.size()) {
            end(player, false, true);
            return;
        }
        Cut cut = s.cuts.get(s.index);
        int fade = module.config().cameraFadeTicks;
        if (fade > 0) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, fade * 2, 0, false, false, false));
        }
        player.teleport(cut.at());
        player.setFlying(true);
        if (cut.text() != null && module.msg().has("cameras." + s.camera + "." + cut.text() + ".title")) {
            module.msg().title(player, "cameras." + s.camera + "." + cut.text());
        }
        s.task = core.scheduler().later("tutorial-camera", Math.max(1, cut.ticks()), () -> nextCut(player, s));
    }

    /** Cut-and-fade players hover frozen: true when this move must be refused. */
    boolean frozen(Player player) {
        Session s = sessions.get(player.getUniqueId());
        return s != null && !s.smooth;
    }

    // ================================================================== end, skip, restore

    /** Sneak (Bedrock, cut-and-fade) or leaving the spectator target (Java): skip. */
    void skip(Player player) {
        if (sessions.containsKey(player.getUniqueId())) {
            end(player, true, true);
        }
    }

    /** The Java player stopped spectating our display (sneak): skip on the next tick. */
    void stoppedSpectating(Player player, Entity target) {
        Session s = sessions.get(player.getUniqueId());
        if (s != null && s.smooth && s.display != null && s.display.equals(target)) {
            core.scheduler().later("tutorial-camera-skip", 1, () -> {
                if (sessions.get(player.getUniqueId()) == s) {
                    end(player, true, true);
                }
            });
        }
    }

    /** Stops a running camera without its callback (quit, death, a command, module stop). */
    void stop(Player player) {
        if (sessions.containsKey(player.getUniqueId())) {
            end(player, false, false);
        }
    }

    private void end(Player player, boolean skipped, boolean callback) {
        Session s = sessions.remove(player.getUniqueId());
        if (s == null) {
            return;
        }
        if (s.task != null) {
            s.task.cancel();
        }
        if (!s.smooth && module.config().cameraFadeTicks > 0) {
            player.removePotionEffect(PotionEffectType.BLINDNESS);
        }
        restore(player);
        if (s.display != null && s.display.isValid()) {
            s.display.remove();
        }
        if (skipped) {
            module.engine().cameraEvent(player, s.camera, "camera_skip");
        }
        if (callback && s.onEnd != null && player.isOnline()) {
            core.scheduler().later("tutorial-camera-end", 1, () -> {
                if (player.isOnline()) {
                    s.onEnd.run();
                }
            });
        }
    }

    /** Ends every camera (module stop): everyone gets their game mode and position back, no callbacks. */
    void stopAll() {
        for (UUID id : new ArrayList<>(sessions.keySet())) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                end(p, false, false);
            } else {
                Session s = sessions.remove(id);
                if (s != null && s.display != null && s.display.isValid()) {
                    s.display.remove();
                }
            }
        }
    }

    /** Join: a camera that never ended (crash, restart) gives everything back first. */
    void restoreOnJoin(Player player) {
        if (!sessions.containsKey(player.getUniqueId())) {
            restore(player);
        }
    }

    private void save(Player player) {
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        if (pdc.has(stateKey, PersistentDataType.STRING)) {
            return; // an older camera never ended: keep the original state, never the camera's own
        }
        Location l = player.getLocation();
        CameraState state = new CameraState(player.getGameMode(), l.getWorld().getName(), l.getX(), l.getY(), l.getZ(), l.getYaw(),
                l.getPitch(), player.getAllowFlight(), player.isFlying(), player.getWalkSpeed(), player.getFlySpeed(), player.isMarkedInvulnerable());
        pdc.set(stateKey, PersistentDataType.STRING, state.encode());
    }

    /** Gives back the saved state, if any. The saved state stays when the teleport back failed (it is tried on join). */
    private void restore(Player player) {
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        String raw = pdc.get(stateKey, PersistentDataType.STRING);
        if (raw == null) {
            return;
        }
        Optional<CameraState> state = CameraState.decode(raw);
        if (state.isEmpty()) {
            pdc.remove(stateKey);
            if (player.getGameMode() == GameMode.SPECTATOR && !player.hasPermission(TutorialEngine.PERM_BYPASS)) {
                player.setGameMode(Bukkit.getDefaultGameMode());
            }
            core.plugin().getLogger().warning("Tutorial: unreadable camera state for " + player.getName() + " was removed.");
            return;
        }
        PlayerBody body = new PlayerBody(player);
        try {
            state.get().restore(body);
        } catch (RuntimeException e) {
            core.plugin().getLogger().log(Level.WARNING, "Tutorial: restoring " + player.getName() + " after a camera failed", e);
        }
        if (body.teleported) {
            pdc.remove(stateKey);
        }
    }

    /** Removes camera entities left in loaded chunks (they are never saved with the chunk; this is only a safety net). */
    void cleanStray() {
        for (World w : Bukkit.getWorlds()) {
            for (Entity e : w.getEntitiesByClass(ItemDisplay.class)) {
                if (e.getPersistentDataContainer().has(entityKey, PersistentDataType.STRING)) {
                    e.remove();
                }
            }
        }
    }

    boolean isCameraEntity(Entity e) {
        return e.getPersistentDataContainer().has(entityKey, PersistentDataType.STRING);
    }

    /** {@link CameraState.Body} over a real player. */
    private final class PlayerBody implements CameraState.Body {

        private final Player player;
        boolean teleported;

        PlayerBody(Player player) {
            this.player = player;
        }

        @Override
        public void stopSpectating() {
            if (player.getGameMode() == GameMode.SPECTATOR && player.getSpectatorTarget() != null) {
                player.setSpectatorTarget(null);
            }
        }

        @Override
        public void gameMode(GameMode mode) {
            player.setGameMode(mode);
        }

        @Override
        public boolean teleport(String world, double x, double y, double z, float yaw, float pitch) {
            World w = Bukkit.getWorld(world);
            if (w == null) {
                return false;
            }
            teleported = player.teleport(new Location(w, x, y, z, yaw, pitch));
            return true; // a refused teleport (player leaving) keeps the saved state for the next join
        }

        @Override
        public void fallbackTeleport() {
            World w = Bukkit.getWorld(module.config().spawnWorld);
            if (w == null && !Bukkit.getWorlds().isEmpty()) {
                w = Bukkit.getWorlds().getFirst();
            }
            if (w != null) {
                teleported = player.teleport(w.getSpawnLocation());
            }
        }

        @Override
        public void allowFlight(boolean allow) {
            player.setAllowFlight(allow);
        }

        @Override
        public void flying(boolean flying) {
            if (player.getAllowFlight()) {
                player.setFlying(flying);
            }
        }

        @Override
        public void walkSpeed(float speed) {
            player.setWalkSpeed(speed);
        }

        @Override
        public void flySpeed(float speed) {
            player.setFlySpeed(speed);
        }

        @Override
        public void invulnerable(boolean invulnerable) {
            player.setInvulnerable(invulnerable);
        }

        @Override
        public void resetFall() {
            player.setFallDistance(0);
        }
    }
}
