package net.skybornmc.skyblock.tutorial;

import net.skybornmc.skyblock.common.Core;
import net.skybornmc.skyblock.common.util.Scheduler;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Input;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * The tutorial cameras (TUTORIAL.md §9.4, TU3, TQ10/TQ11): {@code spawn} (first join), {@code island} (arrival step),
 * {@code depths} (Mine Tier 1) and {@code market} (optional spawn tour). Three ways to play one:
 * <ul>
 *   <li><b>Java (smooth):</b> spectator mode following an invisible item display that glides along the shots.</li>
 *   <li><b>Cut-and-fade</b> (Java with {@code cameras.java-smooth: false}, Bedrock with {@code cameras.bedrock-cuts}):
 *   the player hovers at each shot facing its look-at point.</li>
 *   <li><b>Titles only</b> (Bedrock by default, and Java when the camera entity can't spawn or no shot is safe here):
 *   nothing about the player changes, only the shot titles show.</li>
 * </ul>
 * TUT-FIX (owner report 31 Oct), the safety rules: every shot is checked against the world before it plays and never
 * flies through or starts inside blocks ({@link CameraRules#check}); ANY move key, jump or sneak skips the camera
 * ({@link CameraRules#skipOnInput}); a command skips it; a teleport by anything else ends it at that teleport's
 * destination; quit, kick, death and module stop give everything back; a watchdog ends any camera still running a few
 * seconds after its own length. Before a camera changes the player, game mode, position, flight, speeds and
 * invulnerability are saved in the player's data container ({@link CameraState}), so even a crash is undone on the next
 * join. The player is invulnerable while a camera runs. Main thread.
 */
final class Cameras {

    static final String SPAWN = "spawn";
    static final String ISLAND = "island";
    static final String DEPTHS = "depths";
    static final String MARKET = "market";
    static final List<String> ALL = List.of(SPAWN, ISLAND, DEPTHS, MARKET);

    /** Standing eye height: a cut puts the player's eyes (not feet) on the shot point. */
    private static final double EYE = 1.62;

    /** How a camera plays for one player. */
    enum Mode { SMOOTH, CUTS, TITLES }

    /** One frame of the cut-and-fade or titles camera: where the player hovers (cuts only), for how long, the title. */
    private record Cut(Location at, int ticks, String text) {
    }

    private static final class Session {
        final String camera;
        final Runnable onEnd;
        Mode mode;
        final List<CameraPath.Waypoint> path = new ArrayList<>();
        final Map<Integer, String> texts = new HashMap<>();
        final List<Cut> cuts = new ArrayList<>();
        final List<Cut> titles = new ArrayList<>();
        World world;
        ItemDisplay display;
        Scheduler.Task task;
        int index;
        long startedAt;
        int totalTicks;
        /** When the player started following the camera (smooth) or hovering (cuts); 0 before. */
        long attachedAt;
        /** The player state was saved (smooth, cuts): the end gives it back. */
        boolean saved;
        boolean blinded;
        /** Another plugin teleported the player during the camera: the end puts them there, not back. */
        Location redirect;

        Session(String camera, Runnable onEnd, Mode mode) {
            this.camera = camera;
            this.onEnd = onEnd;
            this.mode = mode;
        }
    }

    private final TutorialModule module;
    private final Core core;
    private final NamespacedKey stateKey;
    private final NamespacedKey entityKey;
    private final Map<UUID, Session> sessions = new HashMap<>();
    private final Set<String> loggedFixes = ConcurrentHashMap.newKeySet();
    private volatile Map<String, CameraShot.Parsed> cameras = Map.of();
    private Scheduler.Task watchdog;
    /** True while this class teleports a player itself (those teleports never end a camera). */
    private boolean selfTeleport;

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
        loggedFixes.clear();
    }

    /** Starts the watchdog (module enable). */
    void start() {
        if (watchdog != null) {
            watchdog.cancel();
        }
        watchdog = core.scheduler().repeat("tutorial-camera-watchdog", 20, 20, this::watchdog);
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
     * {@code onEnd} runs once on the main thread after the camera ended, was skipped or was cut short (not after a quit,
     * death or module stop).
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
        TutorialConfig c = module.config();
        Mode mode = module.engine().bedrock(player) ? (c.cameraBedrockCuts ? Mode.CUTS : Mode.TITLES)
                : (c.cameraJavaSmooth ? Mode.SMOOTH : Mode.CUTS);
        Session s = new Session(camera, onEnd, mode);
        s.world = world;
        CameraRules.Blocks blocks = blocks(world);
        int number = 0;
        for (CameraShot shot : parsed.shots()) {
            number++;
            double[] from = point(shot.from(), anchors);
            double[] to = point(shot.to(), anchors);
            double[] look = point(shot.look(), anchors);
            s.titles.add(new Cut(null, shot.ticks(), shot.text()));
            if (mode == Mode.TITLES) {
                continue;
            }
            CameraRules.Fix fix = CameraRules.check(from, to, look, blocks);
            logFix(camera, number, fix);
            switch (fix) {
                case DROP -> {
                    continue;
                }
                case STILL_FROM -> to = from;
                case STILL_TO -> from = to;
                default -> {
                    // plays as written
                }
            }
            if (shot.text() != null) {
                s.texts.put(s.path.size(), shot.text());
            }
            s.path.addAll(CameraPath.waypoints(from, to, look, shot.ticks(), c.cameraSegmentTicks));
            cuts(s, world, from, to, look, shot);
        }
        if (s.mode != Mode.TITLES && s.cuts.isEmpty()) {
            s.mode = Mode.TITLES; // no shot is safe here: titles only, never a camera inside a wall
        }
        if (s.mode == Mode.SMOOTH && !spawnDisplay(player, s)) {
            s.mode = Mode.TITLES; // e.g. an entity limit refused the camera entity
        }
        s.startedAt = System.currentTimeMillis();
        s.totalTicks = switch (s.mode) {
            case SMOOTH -> CameraPath.duration(s.path) + c.cameraStartDelayTicks;
            case CUTS -> ticks(s.cuts);
            case TITLES -> ticks(s.titles);
        };
        sessions.put(player.getUniqueId(), s);
        module.engine().cameraEvent(player, camera, "camera_play");
        try {
            switch (s.mode) {
                case SMOOTH -> startSmooth(player, s);
                case CUTS -> startCuts(player, s);
                case TITLES -> {
                    s.index = -1;
                    nextCut(player, s);
                }
            }
        } catch (RuntimeException e) {
            core.plugin().getLogger().log(Level.WARNING, "Tutorial: camera " + camera + " failed for " + player.getName(), e);
            end(player, false, false);
            return false;
        }
        return true;
    }

    private static int ticks(List<Cut> cuts) {
        int sum = 0;
        for (Cut cut : cuts) {
            sum += cut.ticks();
        }
        return sum;
    }

    private void logFix(String camera, int shot, CameraRules.Fix fix) {
        if (fix == CameraRules.Fix.OK || !loggedFixes.add(camera + "#" + shot + ":" + fix)) {
            return;
        }
        String what = switch (fix) {
            case STILL_FROM -> "its path is blocked; it plays as a still shot at its start";
            case STILL_TO -> "its path and start are blocked; it plays as a still shot at its end";
            default -> "it has no clear place (or its chunks are not loaded); it is skipped";
        };
        core.plugin().getLogger().warning("Tutorial: camera '" + camera + "' shot " + shot + " would pass through blocks here: " + what
                + ". Check cameras." + camera + " in tutorial.yml (logged once per reload).");
    }

    /** The world for the line-of-sight check: unloaded chunks are unknown (never loaded just for a camera). */
    private static CameraRules.Blocks blocks(World world) {
        return (x, y, z) -> {
            if (y < world.getMinHeight() || y >= world.getMaxHeight()) {
                return false;
            }
            if (!world.isChunkLoaded(x >> 4, z >> 4)) {
                return null;
            }
            Block b = world.getBlockAt(x, y, z);
            return !b.isPassable() || b.isLiquid();
        };
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

    /** Where the player stands so that their eyes are on {@code eye}, facing {@code look}. */
    private static Location facing(World world, double[] eye, double[] look) {
        double dx = look[0] - eye[0];
        double dy = look[1] - eye[1];
        double dz = look[2] - eye[2];
        return new Location(world, eye[0], eye[1] - EYE, eye[2], CameraPath.yaw(dx, dz), CameraPath.pitch(dx, dy, dz));
    }

    private Location at(World world, CameraPath.Waypoint w) {
        return new Location(world, w.x(), w.y(), w.z(), w.yaw(), w.pitch());
    }

    /** Things every camera that moves the player does first: off a vehicle, inventory closed, state saved. */
    private void prepare(Player player, Session s) {
        if (player.isInsideVehicle()) {
            player.leaveVehicle();
        }
        player.closeInventory();
        save(player);
        s.saved = true;
        player.setInvulnerable(true);
        player.setFallDistance(0);
        module.pip().hide(player);
    }

    private void teleportSelf(Player player, Location to) {
        selfTeleport = true;
        try {
            player.teleport(to);
        } finally {
            selfTeleport = false;
        }
    }

    // ------------------------------------------------------------------ Java: spectator + gliding display

    /** Spawns the camera entity at the first waypoint (before anything about the player changes); false when refused. */
    private boolean spawnDisplay(Player player, Session s) {
        Location start = at(s.world, s.path.getFirst());
        try {
            s.display = s.world.spawn(start, ItemDisplay.class, d -> {
                d.setVisibleByDefault(false);
                d.setPersistent(false);
                d.setTeleportDuration(s.path.size() > 1 ? Math.min(CameraPath.MAX_SEGMENT_TICKS, s.path.get(1).ticks()) : 0);
                d.getPersistentDataContainer().set(entityKey, PersistentDataType.STRING, player.getUniqueId().toString());
            });
        } catch (RuntimeException e) {
            s.display = null;
        }
        if (s.display == null || !s.display.isValid()) {
            if (s.display != null) {
                s.display.remove();
            }
            s.display = null;
            return false;
        }
        return true;
    }

    private void startSmooth(Player player, Session s) {
        prepare(player, s);
        player.setGameMode(GameMode.SPECTATOR);
        teleportSelf(player, at(s.world, s.path.getFirst()));
        player.showEntity(core.plugin(), s.display);
        title(player, s, 0);
        s.index = 0;
        s.task = core.scheduler().later("tutorial-camera", module.config().cameraStartDelayTicks, () -> {
            if (sessions.get(player.getUniqueId()) != s || !player.isOnline()) {
                return;
            }
            if (player.getGameMode() != GameMode.SPECTATOR || s.display == null || !s.display.isValid()) {
                end(player, false, true);
                return;
            }
            selfTeleport = true;
            try {
                player.setSpectatorTarget(s.display);
            } finally {
                selfTeleport = false;
            }
            s.attachedAt = System.currentTimeMillis();
            module.msg().actionBar(player, "cameras.skip-hint");
            nextWaypoint(player, s);
        });
    }

    private void nextWaypoint(Player player, Session s) {
        if (sessions.get(player.getUniqueId()) != s || !player.isOnline()) {
            return;
        }
        try {
            s.index++;
            if (s.index >= s.path.size() || s.display == null || !s.display.isValid()) {
                end(player, false, true);
                return;
            }
            // The player left the camera (sneak, a click on another entity, a game mode change): that is a skip. The
            // camera is never forced back on: forcing it is what left players stuck before.
            if (player.getGameMode() != GameMode.SPECTATOR || !s.display.equals(player.getSpectatorTarget())) {
                end(player, true, true);
                return;
            }
            CameraPath.Waypoint w = s.path.get(s.index);
            s.display.teleport(at(s.world, w));
            // The smoothing of the NEXT move is set one move ahead, so the client has it before that move arrives.
            int next = s.index + 1 < s.path.size() ? s.path.get(s.index + 1).ticks() : 0;
            s.display.setTeleportDuration(Math.min(CameraPath.MAX_SEGMENT_TICKS, next));
            title(player, s, s.index);
            s.task = core.scheduler().later("tutorial-camera", Math.max(1, w.ticks()), () -> nextWaypoint(player, s));
        } catch (RuntimeException e) {
            core.plugin().getLogger().log(Level.WARNING, "Tutorial: camera " + s.camera + " broke for " + player.getName() + "; ended", e);
            end(player, false, true);
        }
    }

    private void title(Player player, Session s, int index) {
        String text = s.texts.get(index);
        if (text != null && module.msg().has("cameras." + s.camera + "." + text + ".title")) {
            module.msg().title(player, "cameras." + s.camera + "." + text);
        }
    }

    // ------------------------------------------------------------------ cut and fade, titles only

    private void startCuts(Player player, Session s) {
        prepare(player, s);
        player.setAllowFlight(true);
        player.setFlying(true);
        s.index = -1;
        s.attachedAt = System.currentTimeMillis();
        module.msg().actionBar(player, "cameras.skip-hint");
        nextCut(player, s);
    }

    /** Next frame of a cut-and-fade (moves the player) or titles-only (changes nothing) camera. */
    private void nextCut(Player player, Session s) {
        if (sessions.get(player.getUniqueId()) != s || !player.isOnline()) {
            return;
        }
        try {
            List<Cut> frames = s.mode == Mode.CUTS ? s.cuts : s.titles;
            s.index++;
            if (s.index >= frames.size()) {
                end(player, false, true);
                return;
            }
            Cut cut = frames.get(s.index);
            if (s.mode == Mode.CUTS) {
                int fade = module.config().cameraFadeTicks;
                if (fade > 0) {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, fade * 2, 0, false, false, false));
                    s.blinded = true;
                }
                teleportSelf(player, cut.at());
                if (player.getAllowFlight()) {
                    player.setFlying(true);
                }
            }
            if (cut.text() != null && module.msg().has("cameras." + s.camera + "." + cut.text() + ".title")) {
                module.msg().title(player, "cameras." + s.camera + "." + cut.text());
            }
            s.task = core.scheduler().later("tutorial-camera", Math.max(1, cut.ticks()), () -> nextCut(player, s));
        } catch (RuntimeException e) {
            core.plugin().getLogger().log(Level.WARNING, "Tutorial: camera " + s.camera + " broke for " + player.getName() + "; ended", e);
            end(player, false, true);
        }
    }

    // ================================================================== interruptions (TUT-FIX)

    /** A key press: any move key, jump or sneak skips a camera that holds the player (smooth or cuts). */
    void input(Player player, Input input) {
        Session s = sessions.get(player.getUniqueId());
        if (s == null || CameraRules.action(CameraRules.Interruption.MOVE_KEY, s.saved) == CameraRules.Action.IGNORE) {
            return;
        }
        long since = s.attachedAt == 0 ? 0 : (System.currentTimeMillis() - s.attachedAt) / 50L;
        if (CameraRules.skipOnInput(input.isForward(), input.isBackward(), input.isLeft(), input.isRight(), input.isJump(),
                input.isSneak(), since, module.config().cameraInputGraceTicks)) {
            end(player, true, true);
        }
    }

    /** Sneak (Bedrock sends it as a toggle): skips a camera that holds the player. */
    void sneak(Player player) {
        Session s = sessions.get(player.getUniqueId());
        if (s != null && CameraRules.action(CameraRules.Interruption.SNEAK, s.saved) == CameraRules.Action.SKIP) {
            end(player, true, true);
        }
    }

    /** Cut-and-fade: the player moved away from where the camera holds them = a skip (never a frozen player). */
    void moved(Player player, Location to) {
        Session s = sessions.get(player.getUniqueId());
        if (s == null || s.mode != Mode.CUTS || s.index < 0 || s.index >= s.cuts.size() || to == null) {
            return;
        }
        Location at = s.cuts.get(s.index).at();
        if (!at.getWorld().equals(to.getWorld()) || CameraRules.movedAway(to.getX() - at.getX(), to.getY() - at.getY(),
                to.getZ() - at.getZ(), module.config().cameraMoveSkipBlocks)) {
            end(player, true, true);
        }
    }

    /**
     * Another plugin teleports the player during a camera (portal, /spawn by a staff member, an AFK or void rule...):
     * the camera ends on the next tick and the player stays at that teleport's destination.
     */
    void teleported(Player player, PlayerTeleportEvent event) {
        Session s = sessions.get(player.getUniqueId());
        if (s == null || selfTeleport || event.getTo() == null
                || CameraRules.action(CameraRules.Interruption.TELEPORT, s.saved) == CameraRules.Action.IGNORE) {
            return;
        }
        PlayerTeleportEvent.TeleportCause cause = event.getCause();
        if (cause == PlayerTeleportEvent.TeleportCause.SPECTATE || cause == PlayerTeleportEvent.TeleportCause.UNKNOWN) {
            return; // the camera's own spectating moves; leaving the camera is handled by stoppedSpectating
        }
        s.redirect = event.getTo().clone();
        core.scheduler().later("tutorial-camera-teleported", 1, () -> {
            if (sessions.get(player.getUniqueId()) == s) {
                end(player, true, true);
            }
        });
    }

    /** Commands, the reset command, chat-free skips: ends a running camera with its callback. */
    void skip(Player player) {
        if (sessions.containsKey(player.getUniqueId())) {
            end(player, true, true);
        }
    }

    /** The Java player stopped spectating our display (sneak, a click, the spectator menu): skip on the next tick. */
    void stoppedSpectating(Player player, Entity target) {
        Session s = sessions.get(player.getUniqueId());
        if (s != null && s.mode == Mode.SMOOTH && s.display != null && s.display.equals(target)) {
            core.scheduler().later("tutorial-camera-skip", 1, () -> {
                if (sessions.get(player.getUniqueId()) == s) {
                    end(player, true, true);
                }
            });
        }
    }

    /** Stops a running camera without its callback (quit, death, staff changes, module stop). */
    void stop(Player player) {
        if (sessions.containsKey(player.getUniqueId())) {
            end(player, false, false);
        }
    }

    /**
     * Death during a camera (only possible in titles-only mode or by /kill): a camera that holds the player ends with its
     * callback (the arrival step still finishes); titles-only keeps showing its titles.
     */
    void died(Player player) {
        Session s = sessions.get(player.getUniqueId());
        if (s != null && CameraRules.action(CameraRules.Interruption.DEATH, s.saved) == CameraRules.Action.SKIP) {
            end(player, false, true);
        }
    }

    /** After a respawn: a state the death kept (the teleport back failed) is given back at the respawn point. */
    void respawned(Player player) {
        if (!sessions.containsKey(player.getUniqueId()) && player.getPersistentDataContainer().has(stateKey, PersistentDataType.STRING)) {
            restore(player, player.getLocation());
        }
    }

    /** Every second: a camera still running long after its length, or one that lost its entity, is ended. */
    private void watchdog() {
        if (sessions.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        int extra = module.config().cameraWatchdogSeconds;
        for (Map.Entry<UUID, Session> e : new ArrayList<>(sessions.entrySet())) {
            Session s = e.getValue();
            Player player = Bukkit.getPlayer(e.getKey());
            if (player == null || !player.isOnline()) {
                sessions.remove(e.getKey());
                if (s.display != null && s.display.isValid()) {
                    s.display.remove();
                }
                continue;
            }
            if (CameraRules.overdue(s.startedAt, s.totalTicks, now, extra)) {
                core.plugin().getLogger().warning("Tutorial: camera " + s.camera + " of " + player.getName() + " ran too long; ended by the watchdog.");
                end(player, false, true);
                continue;
            }
            if (s.mode == Mode.SMOOTH && s.attachedAt > 0
                    && (player.getGameMode() != GameMode.SPECTATOR || s.display == null || !s.display.isValid())) {
                end(player, false, true);
            }
        }
    }

    // ================================================================== end, restore

    private void end(Player player, boolean skipped, boolean callback) {
        Session s = sessions.remove(player.getUniqueId());
        if (s == null) {
            return;
        }
        if (s.task != null) {
            s.task.cancel();
        }
        try {
            if (s.blinded) {
                player.removePotionEffect(PotionEffectType.BLINDNESS);
            }
            if (s.saved) {
                restore(player, s.redirect);
            }
        } finally {
            if (s.display != null && s.display.isValid()) {
                s.display.remove();
            }
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
        if (watchdog != null) {
            watchdog.cancel();
            watchdog = null;
        }
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

    /** Join: a camera that never ended (crash, restart, a refused teleport at quit) gives everything back first. */
    void restoreOnJoin(Player player) {
        if (!sessions.containsKey(player.getUniqueId())) {
            restore(player, null);
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

    /**
     * Gives back the saved state, if any ({@code redirect}: at that place instead of the saved one). The saved state
     * stays when the teleport back failed (it is tried again on join).
     */
    private void restore(Player player, Location redirect) {
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
        CameraState back = state.get();
        if (redirect != null && redirect.getWorld() != null) {
            back = back.at(redirect.getWorld().getName(), redirect.getX(), redirect.getY(), redirect.getZ(), redirect.getYaw(), redirect.getPitch());
        }
        PlayerBody body = new PlayerBody(player);
        selfTeleport = true;
        try {
            back.restore(body);
        } catch (RuntimeException e) {
            core.plugin().getLogger().log(Level.WARNING, "Tutorial: restoring " + player.getName() + " after a camera failed", e);
        } finally {
            selfTeleport = false;
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
