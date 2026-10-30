package net.skybornmc.skyblock.worlds.builds;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.function.operation.Operation;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.session.ClipboardHolder;
import org.bukkit.Bukkit;
import org.bukkit.World;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * The only class of the worlds module that touches the FastAsyncWorldEdit/WorldEdit API; loaded only after
 * {@link #available()} said yes, so the module works without FAWE (pasting is then refused).
 */
public final class FaweBuildPaste {

    private FaweBuildPaste() {
    }

    public static boolean available() {
        return Bukkit.getPluginManager().isPluginEnabled("FastAsyncWorldEdit");
    }

    /**
     * Pastes {@code file} with its copy point at x, y, z. Blocking: call off the main thread (FAWE works asynchronously
     * by design). No undo history (memory on the small VPS; back up the world first). Entities are never pasted.
     *
     * @return blocks changed as reported by FAWE
     */
    public static long paste(File file, World world, int x, int y, int z, boolean pasteAir, boolean pasteBiomes) throws IOException {
        ClipboardFormat format = ClipboardFormats.findByFile(file);
        if (format == null) {
            throw new IOException("unknown schematic format: " + file.getName());
        }
        Clipboard clipboard;
        try (InputStream in = new FileInputStream(file); ClipboardReader reader = format.getReader(in)) {
            clipboard = reader.read();
        }
        com.sk89q.worldedit.world.World weWorld = BukkitAdapter.adapt(world);
        try (EditSession session = WorldEdit.getInstance().newEditSessionBuilder().world(weWorld)
                .changeSetNull().fastMode(true).limitUnlimited().build()) {
            Operation operation = new ClipboardHolder(clipboard).createPaste(session)
                    .to(BlockVector3.at(x, y, z))
                    .ignoreAirBlocks(!pasteAir)
                    .copyBiomes(pasteBiomes)
                    .copyEntities(false)
                    .build();
            Operations.complete(operation);
            session.flushQueue();
            return session.getBlockChangeCount();
        } catch (WorldEditException e) {
            throw new IOException("paste failed: " + e.getMessage(), e);
        } finally {
            clipboard.close();
        }
    }
}
