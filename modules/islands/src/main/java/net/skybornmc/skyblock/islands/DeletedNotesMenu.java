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
 * Staff only (owner N7): an island's notes deleted in the last {@code notes.deleted-keep-days} days, newest first, with
 * who deleted them and why (deleted, cleared as done, island disbanded). Read only; the full history is also in the
 * audit log ({@code island_note_*}).
 */
final class DeletedNotesMenu extends PagedMenu<Note> {

    private final IslandsModule module;
    private final IslandManager islands;
    private final Island island;
    private final List<Note> deleted;

    DeletedNotesMenu(IslandsModule module, IslandManager islands, Island island, List<Note> deleted) {
        this.module = module;
        this.islands = islands;
        this.island = island;
        this.deleted = List.copyOf(deleted);
    }

    @Override
    protected List<Note> entries() {
        return deleted;
    }

    @Override
    protected List<Component> titleCrumbs() {
        return List.of(text("notes.crumb"), text("notes.deleted.crumb"));
    }

    @Override
    public String messageModule() {
        return IslandsModule.ID;
    }

    @Override
    public String infoKey() {
        return "notes.deleted.info";
    }

    @Override
    public String helpTopic() {
        return "island";
    }

    @Override
    protected String emptyKey() {
        return "notes.deleted.empty";
    }

    @Override
    protected MenuButton entry(Note note) {
        List<Component> lore = new ArrayList<>();
        for (String line : NoteRules.wrap(note.text(), module.config().notesLoreWidth)) {
            lore.addAll(lines("notes.entry.line", Msg.p("line", line)));
        }
        lore.add(Component.empty());
        var formatter = module.config().dateFormatter();
        lore.addAll(lines("notes.entry.by", Msg.p("player", islands.name(note.author())),
                Msg.p("date", formatter.format(java.time.Instant.ofEpochMilli(note.createdAt())))));
        String reason = note.deletedReason() == null ? IslandNoteStore.REASON_DELETE : note.deletedReason();
        lore.addAll(lines("notes.deleted.by", Msg.p("player", note.deletedBy() == null ? "-" : islands.name(note.deletedBy())),
                Msg.p("date", formatter.format(java.time.Instant.ofEpochMilli(note.deletedAt()))),
                Msg.p("reason", module.raw("notes.deleted.reason." + reason))));
        lore.addAll(lines("notes.deleted.island", Msg.p("island", islands.displayName(island))));
        return MenuButton.display(service.item(new ItemStack(Material.MAP), text("notes.entry.name", Msg.p("text", NoteRules.shorten(note.text(), 28))),
                lore));
    }

    private Component text(String key, TagResolver... placeholders) {
        return service.text(viewer, IslandsModule.ID, key, placeholders);
    }

    private List<Component> lines(String key, TagResolver... placeholders) {
        return service.lines(viewer, IslandsModule.ID, key, placeholders);
    }
}
