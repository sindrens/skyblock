package net.skybornmc.skyblock.islands;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.skybornmc.skyblock.common.menu.Menu;
import net.skybornmc.skyblock.common.text.Msg;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/**
 * What the viewer can do with one note (opened from {@link NotesMenu}, 3 rows, big buttons):
 * <pre>
 *  R O B B B B B O R
 *  B ✎ . ✔ ✏ ⚑ ⚐ ✘ B     10 the note, 12 done / open again, 13 change text, 14 pin here / move pin, 15 unpin, 16 delete
 *  ← O B B ✘ B B O R     18 Back, 22 Close (framework)
 * </pre>
 * Only the buttons the viewer may use are shown; every action re-checks the rights when it runs.
 */
final class NoteActionsMenu extends Menu {

    static final int SLOT_NOTE = 10;
    static final int SLOT_DONE = 12;
    static final int SLOT_EDIT = 13;
    static final int SLOT_PIN = 14;
    static final int SLOT_UNPIN = 15;
    static final int SLOT_DELETE = 16;

    private final IslandsModule module;
    private final IslandManager islands;
    private final IslandNotes notes;
    private final Island island;
    private final UUID noteId;

    NoteActionsMenu(IslandsModule module, IslandManager islands, IslandNotes notes, Island island, UUID noteId) {
        this.module = module;
        this.islands = islands;
        this.notes = notes;
        this.island = island;
        this.noteId = noteId;
    }

    /** True if the viewer has at least one action on the note. */
    static boolean hasActions(IslandNotes.Rights rights, boolean author, Note note) {
        return rights.canTick() || rights.canChange(author);
    }

    @Override
    public int rows() {
        return 3;
    }

    @Override
    public Component title() {
        return service.title(viewer, text("notes.crumb"), text("notes.actions.crumb"));
    }

    @Override
    public String messageModule() {
        return IslandsModule.ID;
    }

    @Override
    protected void build() {
        frame();
        Note note = notes.note(island, noteId);
        if (note == null || islands.byId(island.id()) != island) {
            set(13, service.item(Material.BARRIER, viewer, IslandsModule.ID, "notes.actions.gone"));
            return;
        }
        IslandNotes.Rights rights = notes.rights(viewer, island);
        boolean author = notes.isAuthor(viewer, note);
        set(SLOT_NOTE, service.item(new ItemStack(NotesMenu.icon(note)), text(note.done() ? "notes.entry.name-done" : "notes.entry.name",
                Msg.p("text", NoteRules.shorten(note.text(), 28))), NotesMenu.describe(service, viewer, module, islands, notes, note, false)));
        Runnable again = this::refreshLater;
        if (rights.canTick()) {
            set(SLOT_DONE, service.item(note.done() ? Material.ORANGE_DYE : Material.LIME_DYE, viewer, IslandsModule.ID,
                    note.done() ? "notes.actions.reopen" : "notes.actions.done"), click -> notes.toggleDone(viewer, island, noteId, again));
        }
        if (rights.canChange(author)) {
            set(SLOT_EDIT, service.item(Material.FEATHER, viewer, IslandsModule.ID, "notes.actions.edit",
                    Msg.p("length", module.config().notesMaxLength)), click -> notes.promptEdit(viewer, island, note, this));
            if (!note.done()) {
                set(SLOT_PIN, service.item(Material.ITEM_FRAME, viewer, IslandsModule.ID, note.pinned() ? "notes.actions.move-pin" : "notes.actions.pin",
                        Msg.p("max", module.config().notesMaxHolograms)), click -> notes.pinHere(viewer, island, noteId, again));
            }
            if (note.pinned()) {
                set(SLOT_UNPIN, service.item(Material.SHEARS, viewer, IslandsModule.ID, "notes.actions.unpin"),
                        click -> notes.unpin(viewer, island, noteId, again));
            }
            set(SLOT_DELETE, service.item(Material.LAVA_BUCKET, viewer, IslandsModule.ID, "notes.actions.delete",
                    Msg.p("days", module.config().notesKeepDays)), click -> notes.confirmDelete(viewer, island, note, this));
        }
    }

    /** Rebuilds after an action finished (the menu may have been closed meanwhile; refresh does nothing then). */
    private void refreshLater() {
        refresh();
    }

    private Component text(String key, TagResolver... placeholders) {
        return service.text(viewer, IslandsModule.ID, key, placeholders);
    }
}
