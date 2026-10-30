package net.skybornmc.skyblock.common.player;

import org.bukkit.entity.Player;

import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Crash-safe ordering for every path that moves items between a player's inventory and the database (SECFIX1, findings
 * F-01, F-01b, F-01c of {@code docs/planning/SECURITY_ANTI_EXPLOIT.md}). The player file is only written at autosave, so
 * a crash or kill restores an older inventory. The rule that makes a crash <b>lose at worst, never duplicate</b>:
 *
 * <ol>
 *   <li><b>Inventory to database</b> (auction listing, adding spawners): take the items out of the inventory, then
 *       {@link #save} the player file, and only then write the database ({@link #commitAfterSave}). A crash before the
 *       save keeps the item in the file and nothing in the database; a crash after the save but before the commit loses
 *       the item (logged, staff can give it back). Never both.</li>
 *   <li><b>Database to inventory</b> (box take, satchel take-out, restores): remove the row first (exactly once), give
 *       the items, then {@link #save} right away. A crash in between loses the items (the row is gone, the file is old);
 *       the save shrinks that window to almost nothing.</li>
 *   <li>If the save itself fails, the database is <b>not</b> written ({@link #commitAfterSave} completes exceptionally
 *       with {@link SaveFailed}); the caller's error path gives the items back. A database write next to an unsaved
 *       removal is exactly the dupe this class exists to prevent.</li>
 * </ol>
 *
 * Main thread only: {@link Player#saveData()} serializes the live player.
 */
public final class PlayerSaves {

    private static final Logger LOG = Logger.getLogger("PhoenixSkyblock");

    /** The player file could not be written, so the database step was skipped (nothing changed there). */
    public static final class SaveFailed extends RuntimeException {
        public SaveFailed(String message) {
            super(message);
        }
    }

    private PlayerSaves() {
    }

    /**
     * Writes the player's data file now. True when written (or when the player is offline: Paper saved them on quit).
     * Main thread.
     *
     * @param context what just happened, for the log ("auction listing")
     */
    public static boolean save(Player player, String context) {
        if (player == null || !player.isOnline()) {
            return true;
        }
        try {
            player.saveData();
            return true;
        } catch (RuntimeException e) {
            LOG.log(Level.SEVERE, "Could not save " + player.getName() + "'s player data after " + context, e);
            return false;
        }
    }

    /**
     * Step 1 above: the items already left the inventory; saves the player file and only then starts the database write.
     * If the save fails, {@code commit} never runs and the future fails with {@link SaveFailed}. Main thread.
     */
    public static <T> CompletableFuture<T> commitAfterSave(Player player, String context, Supplier<CompletableFuture<T>> commit) {
        return commitAfterSave(() -> save(player, context), context, commit);
    }

    /** {@link #commitAfterSave(Player, String, Supplier)} with any save step (pure; unit-tested). */
    public static <T> CompletableFuture<T> commitAfterSave(BooleanSupplier save, String context, Supplier<CompletableFuture<T>> commit) {
        boolean saved;
        try {
            saved = save.getAsBoolean();
        } catch (RuntimeException e) {
            saved = false;
        }
        if (!saved) {
            return CompletableFuture.failedFuture(new SaveFailed("player data not saved before " + context));
        }
        try {
            return commit.get();
        } catch (RuntimeException e) {
            return CompletableFuture.failedFuture(e);
        }
    }
}
