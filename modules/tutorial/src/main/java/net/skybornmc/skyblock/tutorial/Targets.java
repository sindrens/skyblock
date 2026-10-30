package net.skybornmc.skyblock.tutorial;

import net.skybornmc.skyblock.common.Core;
import net.skybornmc.skyblock.common.builds.BuildPoints;
import net.skybornmc.skyblock.common.depths.DepthsZones;
import net.skybornmc.skyblock.common.island.IslandService;
import net.skybornmc.skyblock.common.npc.NpcDirectory;
import net.skybornmc.skyblock.common.profile.ProfileService;
import net.skybornmc.skyblock.common.teleport.TeleportOptions;
import net.skybornmc.skyblock.common.teleport.TeleportReason;
import net.skybornmc.skyblock.common.teleport.TeleportService;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.Optional;

/**
 * Where each step points (boss bar compass, flame column, hologram) and where "Take me there" goes (TUTORIAL.md §5,
 * §8). Everything is optional: a missing service or build point just means no target (the bar shows the objective).
 * Main thread.
 */
final class Targets {

    /** Where a step happens, for the rejoin teleport and "Take me there". */
    enum Place { NONE, ISLAND, DEPTHS, SPAWN }

    private final TutorialModule module;
    private final Core core;

    Targets(TutorialModule module) {
        this.module = module;
        this.core = module.core();
    }

    static Place place(String step) {
        if (step == null) {
            return Place.NONE;
        }
        return switch (step) {
            case Steps.ARRIVE, Steps.CHEST, Steps.CROP, Steps.MENU -> Place.ISLAND;
            case Steps.DEPTHS, Steps.MINE, Steps.SELL, Steps.FORGE, Steps.HELP -> Place.DEPTHS;
            case Steps.PORTAL -> Place.SPAWN;
            default -> Place.NONE;
        };
    }

    /** True if the rejoin teleport should move the player (TUTORIAL §8: steps 1 and 11 stay). */
    boolean hasPlace(String step) {
        Place p = place(step);
        return p == Place.ISLAND || p == Place.DEPTHS;
    }

    /** The point the guides lead to for the player's current step, or empty. */
    Optional<Location> target(Player player, Progress p) {
        if (p.step == null) {
            return Optional.empty();
        }
        TutorialConfig c = module.config();
        return switch (p.step) {
            case Steps.PORTAL -> resolve(c.portalTarget, null);
            case Steps.CHEST -> chest(p).map(l -> l.clone().add(0.5, 1.0, 0.5)).or(() -> home(player));
            case Steps.CROP -> home(player);
            case Steps.DEPTHS -> depthsSpawn();
            case Steps.FORGE -> resolve(c.forgeTarget, c.forgeRole);
            case Steps.HELP -> resolve(c.helpTarget, c.helpRole);
            default -> Optional.empty();
        };
    }

    /** The starter chest block of the account's first island, if known and its world is loaded. */
    Optional<Location> chest(Progress p) {
        if (p.chest == null) {
            return Optional.empty();
        }
        return TargetSpec.parse(p.chest).flatMap(this::point).map(l -> new Location(l.getWorld(), l.getBlockX(), l.getBlockY(), l.getBlockZ()));
    }

    Optional<Location> home(Player player) {
        Optional<IslandService> islands = core.services().optional(IslandService.class);
        if (islands.isEmpty()) {
            return Optional.empty();
        }
        return islands.get().islandOf(ProfileService.of(core.services()).profileKey(player))
                .flatMap(i -> islands.get().home(i.id()));
    }

    Optional<Location> depthsSpawn() {
        return core.services().optional(DepthsZones.class).flatMap(d -> d.spawnOf(module.config().depthsZone));
    }

    /** {@code world:x,y,z}, {@code npc:id}, {@code role:x}, {@code build:cat:id}; then the fallback NPC role. */
    Optional<Location> resolve(String spec, String fallbackRole) {
        Optional<Location> found = TargetSpec.parse(spec).flatMap(this::locate);
        if (found.isEmpty() && fallbackRole != null && !fallbackRole.isBlank()) {
            found = locate(new TargetSpec.Role(fallbackRole));
        }
        return found;
    }

    private Optional<Location> locate(TargetSpec spec) {
        return switch (spec) {
            case TargetSpec.Point pt -> point(pt);
            case TargetSpec.Npc npc -> core.services().optional(NpcDirectory.class).flatMap(d -> d.npcLocation(npc.id()));
            case TargetSpec.Role role -> core.services().optional(NpcDirectory.class).flatMap(d -> d.npcByRole(role.role()))
                    .map(NpcDirectory.NpcInfo::location);
            case TargetSpec.Build b -> core.services().optional(BuildPoints.class).flatMap(bp -> bp.point(b.category(), b.id()))
                    .flatMap(bp -> {
                        World w = Bukkit.getWorld(bp.world());
                        return w == null ? Optional.empty() : Optional.of(new Location(w, bp.x(), bp.y(), bp.z()));
                    });
        };
    }

    private Optional<Location> point(TargetSpec spec) {
        if (!(spec instanceof TargetSpec.Point pt)) {
            return Optional.empty();
        }
        World w = Bukkit.getWorld(pt.world());
        return w == null ? Optional.empty() : Optional.of(new Location(w, pt.x(), pt.y(), pt.z()));
    }

    /** True if the player is where the step happens (on their island for island steps, in the Depths for Depths steps). */
    boolean atPlace(Player player, String step) {
        return switch (place(step)) {
            case ISLAND -> core.services().optional(IslandService.class).map(i -> i.islandAt(player.getLocation())
                    .map(info -> i.isMember(info.id(), ProfileService.of(core.services()).profileKey(player))).orElse(false)).orElse(true);
            case DEPTHS -> core.services().optional(DepthsZones.class).map(d -> d.inDepths(player.getLocation())).orElse(true);
            default -> true;
        };
    }

    /**
     * Instant teleport to the step's place (owner principle 1): own island home, Mine Tier 1, or the NPC of the Forge /
     * help steps. Returns false when there is nowhere to go (the player is told when {@code tell}).
     */
    boolean takeMeThere(Player player, Progress p, boolean tell) {
        String step = p.step;
        Place place = place(step);
        if (place == Place.ISLAND) {
            Optional<IslandService> islands = core.services().optional(IslandService.class);
            if (islands.isPresent() && islands.get().teleportHome(player)) {
                return true;
            }
        } else if (place == Place.DEPTHS) {
            Optional<Location> to = (step.equals(Steps.FORGE) || step.equals(Steps.HELP))
                    ? target(player, p).or(this::depthsSpawn) : depthsSpawn();
            if (to.isPresent() && core.services().optional(TeleportService.class).isPresent()) {
                Location dest = to.get();
                if (step.equals(Steps.FORGE) || step.equals(Steps.HELP)) {
                    dest = dest.clone().add(dest.getDirection().setY(0).normalize().multiply(2.5));
                    dest.setYaw(dest.getYaw() + 180f);
                }
                core.services().get(TeleportService.class).request(player, dest, TeleportReason.DUNGEON, TeleportOptions.builder()
                        .destination(module.msg().component(player, "take-me-there.destination"))
                        .cause(PlayerTeleportEvent.TeleportCause.PLUGIN)
                        .build());
                return true;
            }
        } else if (place == Place.SPAWN) {
            Optional<Location> to = target(player, p);
            if (to.isPresent() && core.services().optional(TeleportService.class).isPresent()) {
                Location dest = to.get().clone().add(0, -2, 4);
                dest.setYaw(180f);
                core.services().get(TeleportService.class).request(player, dest, TeleportReason.SPAWN, TeleportOptions.builder()
                        .destination(module.msg().component(player, "take-me-there.destination"))
                        .cause(PlayerTeleportEvent.TeleportCause.PLUGIN)
                        .build());
                return true;
            }
        }
        if (tell) {
            module.msg().send(player, "take-me-there.none");
            core.sounds().play(player, "error");
        }
        return false;
    }
}
