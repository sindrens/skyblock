package net.skybornmc.skyblock.worlds.builds;

import net.skybornmc.skyblock.common.builds.BuildBox;

import java.io.File;
import java.util.List;

/**
 * One build of the catalog: its metadata, schematic header and problems.
 *
 * @param info the schematic header, or {@code null} if missing/unreadable
 */
public record BuildEntry(BuildMeta meta, File yml, File schematic, SchematicInfo info, List<BuildProblem> problems) {

    public BuildEntry {
        problems = List.copyOf(problems);
    }

    public String id() {
        return meta.id();
    }

    /** No errors: may be pasted and applied. */
    public boolean ok() {
        return !BuildValidator.hasErrors(problems);
    }

    public long errors() {
        return problems.stream().filter(BuildProblem::error).count();
    }

    public long warnings() {
        return problems.size() - errors();
    }

    /** The area the paste changes, or {@code null} if unknown. */
    public BuildBox pasteBox() {
        return info == null || meta.origin() == null ? null : info.pastedAt(meta.originX(), meta.originY(), meta.originZ());
    }

    /** Blocks the paste writes: the whole area with air, only non-air blocks without. */
    public long blocksToPaste() {
        if (info == null) {
            return -1;
        }
        return meta.pasteAir() ? info.volume() : info.nonAir();
    }
}
