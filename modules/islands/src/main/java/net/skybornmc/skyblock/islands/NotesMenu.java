package net.skybornmc.skyblock.islands;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.skybornmc.skyblock.common.menu.MenuButton;
import net.skybornmc.skyblock.common.menu.PagedMenu;
import net.skybornmc.skyblock.common.text.Msg;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code /is notes} (NOTES, owner N1-N9): the island's shared to-do list, open notes newest first, then done ones.
 * <pre>
 *  R . . . ✎ . . . R      4 summary (open/done, notes x/50, pins x/5)
 *  B n n n n n n n B      one item per note: paper = open, map = pinned, lime dye = done;
 *  B n n n n n n n B      lore = the full text, author and date, who ticked it, where it is pinned;
 *  B n n n n n n n B      click = the note's actions ({@link NoteActionsMenu}) when the viewer has any
 *  B n n n n n n n B
 *  ← 📖 + ? X ⓘ ♻ 🗄 →    46 open as book, 47 add (write), 51 clear done (manage); top row 6 deleted notes (staff)
 * </pre>
 * One plain click per action (Bedrock-safe). Refreshes every 2 s; closes when the viewer can no longer read the notes.
 */
final class NotesMenu extends PagedMenu<Note> {

    static final int SLOT_SUMMARY = 4;
    static final int SLOT_BOOK = 46;
    static final int SLOT_ADD = 47;
    static final int SLOT_CLEAR = 51;
    static final int SLOT_DELETED = 6; // GUI-1: 52 is Previous page now

    private final IslandsModule module;
    private final IslandManager islands;
    private final IslandNotes notes;
    private final Island island;

    NotesMenu(IslandsModule module, IslandManager islands, IslandNotes notes, Island island) {
        this.module = module;
        this.islands = islands;
        this.notes = notes;
        this.island = island;
    }

    private List<Note> all() {
        List<Note> list = notes.cached(island.id());
        return list == null ? List.of() : list;
    }

    @Override
    protected List<Note> entries() {
        return NoteRules.order(all());
    }

    @Override
    protected List<Component> titleCrumbs() {
        return List.of(text("menu.crumb"), text("notes.crumb"));
    }

    @Override
    public String messageModule() {
        return IslandsModule.ID;
    }

    @Override
    public String infoKey() {
        return "notes.info";
    }

    @Override
    public String helpTopic() {
        return "island";
    }

    @Override
    protected String emptyKey() {
        return "notes.empty";
    }

    @Override
    public int tickInterval() {
        return 40;
    }

    @Override
    protected void tick() {
        if (islands.byId(island.id()) != island || !notes.rights(viewer, island).canView()) {
            viewer.closeInventory();
            return;
        }
        if (notes.cached(island.id()) == null) {
            notes.ensure(island);
        }
        refresh();
    }

    @Override
    protected void decorate() {
        IslandNotes.Rights rights = notes.rights(viewer, island);
        IslandsConfig config = module.config();
        List<Note> list = all();
        int open = NoteRules.openCount(list);
        set(SLOT_SUMMARY, service.item(new ItemStack(Material.WRITABLE_BOOK), text("notes.summary.name", Msg.p("island", islands.displayName(island))),
                lines("notes.summary.lore", Msg.p("open", open), Msg.p("done", list.size() - open), Msg.p("count", list.size()),
                        Msg.p("max", config.notesMax), Msg.p("pins", NoteRules.pinCount(list)), Msg.p("max-pins", config.notesMaxHolograms))));
        set(SLOT_BOOK, service.item(new ItemStack(Material.BOOK), text("notes.book-button.name"),
                lines(rights.canAdd() ? "notes.book-button.lore" : "notes.book-button.lore-read", Msg.p("seconds", config.notesBookSeconds))),
                click -> notes.book().give(viewer, island));
        if (rights.canAdd()) {
            boolean full = list.size() >= config.notesMax;
            set(SLOT_ADD, service.item(new ItemStack(full ? Material.GRAY_DYE : Material.FEATHER), text("notes.add.name"),
                    lines(full ? "notes.add.lore-full" : "notes.add.lore", Msg.p("count", list.size()), Msg.p("max", config.notesMax),
                            Msg.p("length", config.notesMaxLength))), click -> notes.promptAdd(viewer, island, this));
        }
        if (rights.canClearDone() && list.size() > open) {
            set(SLOT_CLEAR, service.item(new ItemStack(Material.CAULDRON), text("notes.clear-button.name"),
                    lines("notes.clear-button.lore", Msg.p("count", list.size() - open))), click -> notes.confirmClearDone(viewer, island, this));
        }
        if (rights.staff()) {
            set(SLOT_DELETED, service.item(new ItemStack(Material.CHEST), text("notes.deleted-button.name"),
                    lines("notes.deleted-button.lore", Msg.p("days", config.notesKeepDays))), click -> notes.openDeleted(viewer, island, this));
        }
    }

    @Override
    protected MenuButton entry(Note note) {
        IslandNotes.Rights rights = notes.rights(viewer, island);
        boolean author = notes.isAuthor(viewer, note);
        boolean actions = NoteActionsMenu.hasActions(rights, author, note);
        ItemStack item = service.item(new ItemStack(icon(note)), text(note.done() ? "notes.entry.name-done" : "notes.entry.name",
                Msg.p("text", NoteRules.shorten(note.text(), 28))), describe(note, actions));
        return actions ? MenuButton.of(item, click -> openChild(new NoteActionsMenu(module, islands, notes, island, note.id())))
                : MenuButton.display(item);
    }

    static Material icon(Note note) {
        if (note.done()) {
            return Material.LIME_DYE;
        }
        return note.pinned() ? Material.FILLED_MAP : Material.PAPER;
    }

    /** The lore of one note (also used by the actions menu). */
    List<Component> describe(Note note, boolean clickHint) {
        return describe(service, viewer, module, islands, notes, note, clickHint);
    }

    static List<Component> describe(net.skybornmc.skyblock.common.menu.MenuService service, org.bukkit.entity.Player viewer,
                                    IslandsModule module, IslandManager islands, IslandNotes notes, Note note, boolean clickHint) {
        List<Component> lore = new ArrayList<>();
        for (String line : NoteRules.wrap(note.text(), module.config().notesLoreWidth)) {
            lore.addAll(service.lines(viewer, IslandsModule.ID, "notes.entry.line", Msg.p("line", line)));
        }
        lore.add(Component.empty());
        lore.addAll(service.lines(viewer, IslandsModule.ID, "notes.entry.by", Msg.p("player", islands.name(note.author())),
                Msg.p("date", notes.date(note.createdAt()))));
        if (note.updatedBy() != null && note.updatedAt() > note.createdAt()) {
            lore.addAll(service.lines(viewer, IslandsModule.ID, "notes.entry.changed", Msg.p("player", islands.name(note.updatedBy())),
                    Msg.p("date", notes.date(note.updatedAt()))));
        }
        if (note.done()) {
            lore.addAll(service.lines(viewer, IslandsModule.ID, "notes.entry.done",
                    Msg.p("player", note.doneBy() == null ? "?" : islands.name(note.doneBy())), Msg.p("date", notes.date(note.doneAt()))));
        } else {
            lore.addAll(service.lines(viewer, IslandsModule.ID, "notes.entry.open"));
        }
        if (note.pinned()) {
            Note.Pin pin = note.pin();
            TagResolver[] p = {Msg.p("x", (int) Math.floor(pin.x())), Msg.p("y", (int) Math.floor(pin.y())), Msg.p("z", (int) Math.floor(pin.z()))};
            lore.addAll(service.lines(viewer, IslandsModule.ID, "notes.entry.pinned", p));
        }
        if (clickHint) {
            lore.add(Component.empty());
            lore.addAll(service.lines(viewer, IslandsModule.ID, "notes.entry.click"));
        }
        return lore;
    }

    private Component text(String key, TagResolver... placeholders) {
        return service.text(viewer, IslandsModule.ID, key, placeholders);
    }

    private List<Component> lines(String key, TagResolver... placeholders) {
        return service.lines(viewer, IslandsModule.ID, key, placeholders);
    }
}
