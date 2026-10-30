package net.skybornmc.skyblock.worlds.portals;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Where a portal sends a player (pure, unit-tested). Targets: {@code island} (the island portal: create or home),
 * a world target ({@code spawn}, {@code depths}, {@code warzone}, {@code world:<name>}: the landing in front of the
 * return portal of that world when one exists, else the world spawn), {@code point:<category>:<id>} (a build point),
 * anything else is not open yet ({@code outposts}, {@code events}).
 */
public final class PortalResolver {

    /** What the resolver needs from the server (the live implementation reads Bukkit and build-points.yml). */
    public interface World {
        boolean loaded(String world);

        /** The world's spawn (worlds.yml), if the world is loaded. */
        Optional<Portal.Pos> spawn(String world);

        /** True if at least one applied build belongs to this world. */
        boolean hasBuild(String world);

        /** A build point as world + position. */
        Optional<Located> point(String category, String id);
    }

    /** A position in a world. */
    public record Located(String world, Portal.Pos pos) {
    }

    /** Result of {@link #resolve}. */
    public sealed interface Destination permits Teleport, Island, Refused {
    }

    /** Teleport to a fixed spot. {@code target} is the name used for the arrival title. */
    public record Teleport(String world, Portal.Pos pos, String target) implements Destination {
    }

    /** The island portal: create the island or go home. */
    public record Island() implements Destination {
    }

    /** Not possible now; {@code target} is the portal's target name. */
    public record Refused(Reason reason, String target) implements Destination {
    }

    public enum Reason {
        /** {@code status: closed} or disabled in worlds.yml. */
        CLOSED,
        /** The target world isn't loaded. */
        NO_WORLD,
        /** The target world has no applied build yet (warzone). */
        NOT_BUILT,
        /** No target, an unknown point or a target that doesn't exist yet. */
        UNKNOWN
    }

    private PortalResolver() {
    }

    public static Destination resolve(Portal portal, List<Portal> all, PortalSettings settings, World world) {
        String target = settings.target(portal);
        if (PortalSettings.names(settings.disabled(), portal)) {
            return new Refused(Reason.CLOSED, target);
        }
        if (!portal.open() && !PortalSettings.names(settings.forceOpen(), portal)) {
            return new Refused(Reason.CLOSED, target);
        }
        if (settings.isIslandPortal(portal)) {
            return new Island();
        }
        if (target.startsWith("point:")) {
            String[] parts = target.substring("point:".length()).split(":", 2);
            if (parts.length != 2) {
                return new Refused(Reason.UNKNOWN, target);
            }
            Optional<Located> point = world.point(parts[0], parts[1]);
            if (point.isEmpty()) {
                return new Refused(Reason.UNKNOWN, target);
            }
            if (!world.loaded(point.get().world())) {
                return new Refused(Reason.NO_WORLD, target);
            }
            return new Teleport(point.get().world(), point.get().pos(), parts[1]);
        }
        String targetWorld = settings.worldOf(target);
        if (targetWorld == null) {
            return new Refused(Reason.UNKNOWN, target);
        }
        if (!world.loaded(targetWorld)) {
            return new Refused(Reason.NO_WORLD, target);
        }
        if (settings.needBuild().contains(targetWorld) && !world.hasBuild(targetWorld)) {
            return new Refused(Reason.NOT_BUILT, target);
        }
        Optional<Portal> back = returnPortal(portal, all, settings, targetWorld);
        if (back.isPresent()) {
            return new Teleport(targetWorld, back.get().landing(), target);
        }
        Optional<Portal.Pos> spawn = world.spawn(targetWorld);
        return spawn.<Destination>map(pos -> new Teleport(targetWorld, pos, target))
                .orElseGet(() -> new Refused(Reason.NO_WORLD, target));
    }

    /**
     * The portal in {@code targetWorld} that leads back to the world of {@code from} and has a landing: players arrive
     * in front of it (Depths → spawn lands in front of the Depths portal of the gatehouse, and the other way round).
     */
    static Optional<Portal> returnPortal(Portal from, List<Portal> all, PortalSettings settings, String targetWorld) {
        for (Portal p : all) {
            if (p.key().equals(from.key()) || p.landing() == null || !p.world().equals(targetWorld)) {
                continue;
            }
            if (Objects.equals(settings.worldOf(settings.target(p)), from.world())) {
                return Optional.of(p);
            }
        }
        return Optional.empty();
    }

    /**
     * Warzone rule (owner answer 413): a teleport into the warzone from another world is refused unless it is the
     * warzone portal's own teleport; moving inside the warzone is fine.
     */
    public static boolean deniesWarzone(boolean enabled, String warzoneWorld, String destinationWorld, String currentWorld,
                                        boolean portalPass) {
        if (!enabled || warzoneWorld == null || warzoneWorld.isBlank() || destinationWorld == null) {
            return false;
        }
        if (!destinationWorld.equalsIgnoreCase(warzoneWorld)) {
            return false;
        }
        return !destinationWorld.equalsIgnoreCase(currentWorld) && !portalPass;
    }
}
