package net.skybornmc.skyblock.tutorial;

import org.bukkit.GameMode;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;

/**
 * What a camera changes on a player, saved before it starts (TUTORIAL.md §8, §9.4): game mode, position, flight, walk
 * and fly speed and invulnerability. Stored in the player's data container (PDC) so a quit, kick, crash or restart in
 * the middle of a camera gives everything back on the next join. Pure, unit-tested: {@link #encode()} / {@link #decode}
 * and {@link #restore(Body)} (the order in which it is given back).
 */
record CameraState(GameMode mode, String world, double x, double y, double z, float yaw, float pitch, boolean allowFlight,
                   boolean flying, float walkSpeed, float flySpeed, boolean invulnerable) {

    private static final String VERSION = "c1";

    /** The player as the restore sees it (adapter over {@code Player}, a fake in tests). */
    interface Body {

        /** Leaves the camera entity (Java spectator target). */
        void stopSpectating();

        void gameMode(GameMode mode);

        /** Teleports back; false when the world is gone. */
        boolean teleport(String world, double x, double y, double z, float yaw, float pitch);

        /** Somewhere safe when the saved world is gone (the main spawn). */
        void fallbackTeleport();

        void allowFlight(boolean allow);

        void flying(boolean flying);

        void walkSpeed(float speed);

        void flySpeed(float speed);

        void invulnerable(boolean invulnerable);

        void resetFall();
    }

    String encode() {
        return String.join(";", VERSION, mode.name(), URLEncoder.encode(world, StandardCharsets.UTF_8), num(x), num(y), num(z),
                num(yaw), num(pitch), bool(allowFlight), bool(flying), num(walkSpeed), num(flySpeed), bool(invulnerable));
    }

    static Optional<CameraState> decode(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String[] p = raw.split(";", -1);
        if (p.length != 13 || !VERSION.equals(p[0])) {
            return Optional.empty();
        }
        try {
            GameMode mode = GameMode.valueOf(p[1].toUpperCase(Locale.ROOT));
            String world = URLDecoder.decode(p[2], StandardCharsets.UTF_8);
            if (world.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(new CameraState(mode, world, Double.parseDouble(p[3]), Double.parseDouble(p[4]), Double.parseDouble(p[5]),
                    Float.parseFloat(p[6]), Float.parseFloat(p[7]), flag(p[8]), flag(p[9]), Float.parseFloat(p[10]),
                    Float.parseFloat(p[11]), flag(p[12])));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    /**
     * Gives everything back, in a safe order: leave the camera, game mode, position (or the main spawn when the world is
     * gone), flight (flying only when flight is allowed), speeds (kept in the range Minecraft accepts), no fall damage,
     * and last invulnerability (so the player is never hurt by the trip back).
     */
    void restore(Body body) {
        body.stopSpectating();
        body.gameMode(mode);
        if (!body.teleport(world, x, y, z, yaw, pitch)) {
            body.fallbackTeleport();
        }
        body.allowFlight(allowFlight);
        body.flying(allowFlight && flying);
        body.walkSpeed(speed(walkSpeed, 0.2f));
        body.flySpeed(speed(flySpeed, 0.1f));
        body.resetFall();
        body.invulnerable(invulnerable);
    }

    private static float speed(float value, float fallback) {
        if (Float.isNaN(value) || value < -1f || value > 1f) {
            return fallback;
        }
        return value;
    }

    private static String num(double v) {
        return Double.toString(v);
    }

    private static String bool(boolean b) {
        return b ? "1" : "0";
    }

    private static boolean flag(String s) {
        return switch (s) {
            case "1" -> true;
            case "0" -> false;
            default -> throw new IllegalArgumentException(s);
        };
    }
}
