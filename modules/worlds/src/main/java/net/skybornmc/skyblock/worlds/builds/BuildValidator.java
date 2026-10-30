package net.skybornmc.skyblock.worlds.builds;

import net.skybornmc.skyblock.common.builds.BuildBox;
import net.skybornmc.skyblock.common.builds.BuildPoint;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Checks one build before it may be pasted or applied: metadata complete, world configured, schematic present and
 * readable, its copy point and bounds matching the metadata, not newer than the server, inside the world border.
 * Pure (no server access) so it is unit-tested against the real files in {@code server/builds}.
 */
public final class BuildValidator {

    /** What the validator needs to know about the server. */
    public record Context(Predicate<String> worldConfigured, int serverDataVersion, Predicate<String> borderCheckWorld,
                          java.util.function.ToDoubleFunction<String> borderRadius) {
    }

    private BuildValidator() {
    }

    /**
     * @param schematicFound true if the schematic file exists
     * @param info           the schematic's header, or {@code null} if it could not be read
     * @param readError      why it could not be read ({@code null} if it was read or is missing)
     */
    public static List<BuildProblem> validate(BuildMeta meta, boolean schematicFound, SchematicInfo info, String readError,
                                              Context context) {
        List<BuildProblem> out = new ArrayList<>();
        if (meta.schematic() == null) {
            out.add(BuildProblem.error("schematic: missing (the .schem file name)"));
        } else if (!meta.schematic().endsWith(".schem")) {
            out.add(BuildProblem.error("schematic: '" + meta.schematic() + "' is not a .schem file"));
        } else if (!schematicFound) {
            out.add(BuildProblem.error("schematic file " + meta.schematic() + " is missing next to " + meta.id() + ".yml"));
        } else if (info == null) {
            out.add(BuildProblem.error("schematic " + meta.schematic() + " can't be read: " + readError));
        }
        if (meta.world() == null) {
            out.add(BuildProblem.error("world: missing"));
        } else if (!context.worldConfigured().test(meta.world())) {
            out.add(BuildProblem.error("world '" + meta.world() + "' is not a configured world (worlds.yml)"));
        }
        if (meta.origin() == null) {
            out.add(BuildProblem.error("origin: missing (where the schematic's copy point goes)"));
        }
        if (meta.schematic() != null && meta.world() != null && !meta.id().startsWith(meta.world() + "_")) {
            out.add(BuildProblem.warning("name doesn't start with its world (" + meta.world() + "_...)"));
        }
        BuildBox pasted = null;
        if (info != null && meta.origin() != null) {
            pasted = info.pastedAt(meta.originX(), meta.originY(), meta.originZ());
            if (info.originX() != meta.originX() || info.originY() != meta.originY() || info.originZ() != meta.originZ()) {
                out.add(BuildProblem.warning("the schematic was copied at (" + info.originX() + ", " + info.originY() + ", "
                        + info.originZ() + ") but the yml origin is (" + meta.originX() + ", " + meta.originY() + ", "
                        + meta.originZ() + "); it is pasted at the yml origin"));
            }
            if (meta.bounds() != null && !pasted.contains(meta.bounds())) {
                out.add(BuildProblem.error("bounds " + meta.bounds() + " are outside the pasted area " + pasted
                        + " (wrong origin?)"));
            }
            if (context.serverDataVersion() > 0 && info.dataVersion() > context.serverDataVersion()) {
                out.add(BuildProblem.error("saved with a newer Minecraft (data version " + info.dataVersion() + ", server "
                        + context.serverDataVersion() + ")"));
            }
            if (meta.statsNonAir() >= 0 && info.nonAir() >= 0 && meta.statsNonAir() != info.nonAir()) {
                out.add(BuildProblem.warning("stats say " + meta.statsNonAir() + " blocks, the schematic has " + info.nonAir()));
            }
            if (info.entities() > 0) {
                out.add(BuildProblem.warning(info.entities() + " entities in the schematic (not pasted)"));
            }
            if (meta.world() != null && context.borderCheckWorld().test(meta.world())) {
                double r = context.borderRadius().applyAsDouble(meta.world());
                if (r > 0 && (pasted.minX() < -r || pasted.maxX() + 1 > r || pasted.minZ() < -r || pasted.maxZ() + 1 > r)) {
                    out.add(BuildProblem.error("the pasted area " + pasted + " reaches outside the world border (radius " + (long) r + ")"));
                }
            }
            if (pasted.minY() < -64 || pasted.maxY() > 319) {
                out.add(BuildProblem.error("the pasted area " + pasted + " leaves the world height (-64..319)"));
            }
        }
        if (meta.bounds() != null && meta.playerSpawn() != null) {
            BuildMeta.Spot s = meta.playerSpawn();
            if (!meta.bounds().contains(s.feetX(), s.y() - 1, s.feetZ())) {
                out.add(BuildProblem.warning("player_spawn stands outside the build's bounds"));
            }
        }
        if (meta.bounds() != null) {
            int outside = 0;
            String first = null;
            for (BuildPoint p : meta.points()) {
                if (!meta.bounds().contains(p.blockX(), Math.max(meta.bounds().minY(), Math.min(meta.bounds().maxY(), p.blockY())), p.blockZ())) {
                    outside++;
                    if (first == null) {
                        first = p.category() + "." + p.id();
                    }
                }
            }
            if (outside > 0) {
                out.add(BuildProblem.warning(outside + " point(s) lie outside the bounds (first: " + first + ")"));
            }
        }
        return out;
    }

    public static boolean hasErrors(List<BuildProblem> problems) {
        return problems.stream().anyMatch(BuildProblem::error);
    }
}
