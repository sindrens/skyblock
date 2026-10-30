package net.skybornmc.skyblock.islands;

import java.util.UUID;

/**
 * One island note (NOTES, owner N1-N9), immutable: every change makes a new record. {@code author}, {@code doneBy},
 * {@code updatedBy} and {@code deletedBy} are profile keys (island profiles); names are looked up for display.
 *
 * @param pin where its hologram floats inside the island, or {@code null} when not pinned
 */
record Note(UUID id, UUID islandId, UUID author, String text, long createdAt, long updatedAt, UUID updatedBy, UUID doneBy, Long doneAt,
            Pin pin, Long deletedAt, UUID deletedBy, String deletedReason) {

    /** A pinned spot in the islands world. */
    record Pin(String world, double x, double y, double z) {

        /** Chunk x of the spot. */
        int chunkX() {
            return (int) Math.floor(x) >> 4;
        }

        /** Chunk z of the spot. */
        int chunkZ() {
            return (int) Math.floor(z) >> 4;
        }
    }

    /** A new open note. */
    static Note create(UUID id, UUID islandId, UUID author, String text, long now) {
        return new Note(id, islandId, author, text, now, now, null, null, null, null, null, null, null);
    }

    boolean done() {
        return doneAt != null;
    }

    boolean pinned() {
        return pin != null;
    }

    boolean deleted() {
        return deletedAt != null;
    }

    Note withText(String newText, UUID by, long now) {
        return new Note(id, islandId, author, newText, createdAt, now, by, doneBy, doneAt, pin, deletedAt, deletedBy, deletedReason);
    }

    /** Ticked done ({@code by} != null, also unpins: owner N9) or opened again ({@code by} == null). */
    Note withDone(UUID by, long now) {
        return by == null
                ? new Note(id, islandId, author, text, createdAt, now, updatedBy, null, null, pin, deletedAt, deletedBy, deletedReason)
                : new Note(id, islandId, author, text, createdAt, now, updatedBy, by, now, null, deletedAt, deletedBy, deletedReason);
    }

    Note withPin(Pin newPin, long now) {
        return new Note(id, islandId, author, text, createdAt, now, updatedBy, doneBy, doneAt, newPin, deletedAt, deletedBy, deletedReason);
    }

    Note withDeleted(UUID by, String reason, long now) {
        return new Note(id, islandId, author, text, createdAt, updatedAt, updatedBy, doneBy, doneAt, null, now, by, reason);
    }
}
