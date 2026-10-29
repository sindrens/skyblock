package net.skybornmc.skyblock.islands;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.function.operation.Operation;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.world.block.BlockTypes;
import com.sk89q.worldedit.session.ClipboardHolder;
import org.bukkit.World;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * The only class that touches the FastAsyncWorldEdit/WorldEdit API. It is loaded only after checking that the plugin
 * is enabled ({@link IslandBuilder#faweAvailable()}), so the module works without FAWE (placeholder islet).
 */
final class FaweSchematics {

    /** Pasted area relative to the paste origin (both corners inclusive). */
    record Pasted(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
    }

    private FaweSchematics() {
    }

    /**
     * Reads {@code file} and pastes it with its origin at x,y,z, skipping air (never deletes blocks). Blocking: call
     * off the main thread (FAWE pastes asynchronously by design).
     */
    static Pasted paste(File file, World world, int x, int y, int z) throws IOException {
        ClipboardFormat format = ClipboardFormats.findByFile(file);
        if (format == null) {
            throw new IOException("Unknown schematic format: " + file.getName());
        }
        Clipboard clipboard;
        try (InputStream in = new FileInputStream(file); ClipboardReader reader = format.getReader(in)) {
            clipboard = reader.read();
        }
        BlockVector3 origin = clipboard.getOrigin();
        BlockVector3 min = clipboard.getRegion().getMinimumPoint();
        BlockVector3 max = clipboard.getRegion().getMaximumPoint();
        try (EditSession session = WorldEdit.getInstance().newEditSession(BukkitAdapter.adapt(world))) {
            Operation operation = new ClipboardHolder(clipboard).createPaste(session)
                    .to(BlockVector3.at(x, y, z))
                    .ignoreAirBlocks(true)
                    .build();
            Operations.complete(operation);
        } catch (com.sk89q.worldedit.WorldEditException e) {
            throw new IOException("Paste failed: " + e.getMessage(), e);
        }
        return new Pasted(min.x() - origin.x(), min.y() - origin.y(), min.z() - origin.z(),
                max.x() - origin.x(), max.y() - origin.y(), max.z() - origin.z());
    }

    /**
     * Sets every block of the cuboid (both corners inclusive) to air without history (M2.7 slot clearing). Blocking:
     * call off the main thread. Returns the number of changed blocks as reported by FAWE.
     */
    static int clear(World world, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) throws IOException {
        com.sk89q.worldedit.world.World weWorld = BukkitAdapter.adapt(world);
        CuboidRegion region = new CuboidRegion(weWorld, BlockVector3.at(minX, minY, minZ), BlockVector3.at(maxX, maxY, maxZ));
        try (EditSession session = WorldEdit.getInstance().newEditSessionBuilder().world(weWorld)
                .changeSetNull().fastMode(true).limitUnlimited().build()) {
            return session.setBlocks((com.sk89q.worldedit.regions.Region) region, BlockTypes.AIR.getDefaultState());
        } catch (com.sk89q.worldedit.WorldEditException e) {
            throw new IOException("Clearing failed: " + e.getMessage(), e);
        }
    }
}
