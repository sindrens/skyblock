package net.skybornmc.skyblock.islands;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Pinned notes as holograms (NOTES, owner N9): one Paper {@link TextDisplay} per pinned note, never saved with the chunk
 * ({@code persistent = false}), so a crash or a disabled module leaves nothing behind. Spawned when the island's notes
 * load and when a chunk with a pin loads; dropped when the chunk unloads, the note is unpinned, ticked done or deleted,
 * the island is removed and when the module disables. A pin is only shown inside its own island's border (re-checked on
 * every spawn), so a hologram can never appear on another island.
 */
final class NoteHolograms implements Listener {

    private record Shown(UUID islandId, TextDisplay entity) {
    }

    private final IslandsModule module;
    private final IslandManager islands;
    private final IslandNotes notes;
    private final NamespacedKey tag;
    private final Map<UUID, Shown> shown = new HashMap<>();

    NoteHolograms(IslandsModule module, IslandManager islands, IslandNotes notes) {
        this.module = module;
        this.islands = islands;
        this.notes = notes;
        this.tag = new NamespacedKey(module.core().plugin(), "island_note_hologram");
    }

    /** True if any hologram of the island is spawned (keeps its notes cached). */
    boolean hasAny(UUID islandId) {
        for (Shown s : shown.values()) {
            if (s.islandId().equals(islandId)) {
                return true;
            }
        }
        return false;
    }

    int count() {
        return shown.size();
    }

    /** Shows every pinned note of the island whose chunk is loaded; removes holograms of notes no longer pinned. */
    void showAll(Island island, List<Note> list) {
        Map<UUID, Note> byId = new HashMap<>();
        for (Note note : list) {
            byId.put(note.id(), note);
            show(island, note);
        }
        for (Map.Entry<UUID, Shown> e : List.copyOf(shown.entrySet())) {
            if (e.getValue().islandId().equals(island.id()) && !byId.containsKey(e.getKey())) {
                hide(e.getKey());
            }
        }
    }

    /** Spawns, moves, re-texts or removes the hologram of one note to match it. */
    void show(Island island, Note note) {
        if (!note.pinned() || note.done() || note.deleted()) {
            hide(note.id());
            return;
        }
        Note.Pin pin = note.pin();
        World world = Bukkit.getWorld(pin.world());
        if (world == null || !islands.isIslandWorld(world) || islands.byId(island.id()) != island
                || islands.islandAt((int) Math.floor(pin.x()), (int) Math.floor(pin.z())) != island) {
            hide(note.id()); // never outside its own island
            return;
        }
        if (!world.isChunkLoaded(pin.chunkX(), pin.chunkZ())) {
            hide(note.id());
            return;
        }
        Location at = new Location(world, pin.x(), pin.y() + module.config().notesHologramYOffset, pin.z());
        Component text = module.msg().component(null, "notes.hologram",
                net.skybornmc.skyblock.common.text.Msg.p("text", note.text()),
                net.skybornmc.skyblock.common.text.Msg.p("author", islands.name(note.author())));
        Shown current = shown.get(note.id());
        if (current != null && current.entity().isValid()) {
            current.entity().text(text);
            if (current.entity().getLocation().distanceSquared(at) > 0.01) {
                current.entity().teleport(at);
            }
            return;
        }
        if (current != null) {
            shown.remove(note.id());
        }
        IslandsConfig config = module.config();
        TextDisplay display = world.spawn(at, TextDisplay.class, d -> {
            d.setPersistent(false);
            d.text(text);
            d.setBillboard(Display.Billboard.CENTER);
            d.setLineWidth(config.notesHologramLineWidth);
            d.setViewRange((float) config.notesHologramViewRange);
            d.setShadowed(true);
            d.setSeeThrough(false);
            d.getPersistentDataContainer().set(tag, PersistentDataType.STRING, note.id().toString());
        });
        if (display.isValid()) {
            shown.put(note.id(), new Shown(island.id(), display));
        }
    }

    void hide(UUID noteId) {
        Shown s = shown.remove(noteId);
        if (s != null && s.entity().isValid()) {
            s.entity().remove();
        }
    }

    void hideIsland(UUID islandId) {
        for (Map.Entry<UUID, Shown> e : List.copyOf(shown.entrySet())) {
            if (e.getValue().islandId().equals(islandId)) {
                hide(e.getKey());
            }
        }
    }

    /** Module disable: every hologram goes. */
    void clearAll() {
        for (UUID id : List.copyOf(shown.keySet())) {
            hide(id);
        }
        shown.clear();
    }

    /** On enable/reload: the islands whose chunks are already loaded get their holograms. */
    void loadedChunks() {
        World world = Bukkit.getWorld(module.config().world);
        if (world == null) {
            return;
        }
        for (Chunk chunk : world.getLoadedChunks()) {
            chunkLoaded(chunk);
        }
    }

    private void chunkLoaded(Chunk chunk) {
        Island island = islands.islandInSlotAt((chunk.getX() << 4) + 8, (chunk.getZ() << 4) + 8);
        if (island == null) {
            return;
        }
        List<Note> cached = notes.cached(island.id());
        if (cached == null) {
            notes.ensure(island); // shows every loaded pin once the notes are in
            return;
        }
        for (Note note : cached) {
            if (note.pinned() && note.pin().chunkX() == chunk.getX() && note.pin().chunkZ() == chunk.getZ()) {
                show(island, note);
            }
        }
    }

    private boolean isOurs(Entity entity) {
        return entity instanceof TextDisplay && entity.getPersistentDataContainer().has(tag, PersistentDataType.STRING);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(ChunkLoadEvent event) {
        if (islands.isIslandWorld(event.getWorld())) {
            chunkLoaded(event.getChunk());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkUnload(ChunkUnloadEvent event) {
        if (!islands.isIslandWorld(event.getWorld()) || shown.isEmpty()) {
            return;
        }
        int cx = event.getChunk().getX();
        int cz = event.getChunk().getZ();
        List<UUID> gone = new ArrayList<>();
        for (Map.Entry<UUID, Shown> e : shown.entrySet()) {
            TextDisplay entity = e.getValue().entity();
            Location at = entity.getLocation();
            if (!entity.isValid() || (at.getBlockX() >> 4 == cx && at.getBlockZ() >> 4 == cz)) {
                gone.add(e.getKey());
            }
        }
        gone.forEach(this::hide);
    }

    /** Leftovers (should never exist: they are not saved) are removed when their chunk's entities load. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        for (Entity entity : event.getEntities()) {
            if (isOurs(entity)) {
                boolean tracked = shown.values().stream().anyMatch(s -> s.entity().equals(entity));
                if (!tracked) {
                    entity.remove();
                }
            }
        }
    }
}
