package net.skybornmc.skyblock.islands;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.skybornmc.skyblock.common.Core;
import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.audit.AuditCategory;
import net.skybornmc.skyblock.common.profile.ProfileSwitchEvent;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.util.Scheduler;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerEditBookEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.meta.WritableBookMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The temporary notes book (NOTES, owner N3): "Open as book" in the notes menu puts a book in the inventory. Writers get
 * a Book and Quill with one open note per page and an empty page at the end; closing it (Done, sign or Escape) saves:
 * changed pages change their notes (own notes, or anyone's with the manage right), new pages become notes, emptied pages
 * are ignored (deleting is done in the menu). Readers (coop players) get a written book. Either way the book is taken
 * back right after, on timeout ({@code notes.book-seconds}), quit, death, profile switch and module disable.
 *
 * <p>The book can't leave the inventory slot it was put in: every click, drag, drop, crafting use, item frame, allay,
 * lectern and chiseled bookshelf use with it is refused. Each book carries a one-time token; a book whose token has no
 * live session (after a crash) is removed on join or when used.</p>
 */
final class NoteBook implements Listener {

    private record Session(UUID token, UUID player, UUID islandId, List<UUID> ids, List<String> originals, boolean writable,
                           long expiresAt) {
    }

    private final IslandsModule module;
    private final Core core;
    private final IslandManager islands;
    private final IslandNotes notes;
    private final NamespacedKey tag;
    private final Map<UUID, Session> sessions = new HashMap<>();
    private final Map<UUID, UUID> byPlayer = new HashMap<>();
    private Scheduler.Task expiry;

    NoteBook(IslandsModule module, IslandManager islands, IslandNotes notes) {
        this.module = module;
        this.core = module.core();
        this.islands = islands;
        this.notes = notes;
        this.tag = new NamespacedKey(core.plugin(), "island_note_book");
    }

    void start() {
        if (expiry != null) {
            expiry.cancel();
        }
        expiry = core.scheduler().repeat("islands:note-book-expiry", 100L, 100L, this::expire);
        Bukkit.getOnlinePlayers().forEach(this::removeAll);
    }

    void stop() {
        if (expiry != null) {
            expiry.cancel();
            expiry = null;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            removeAll(player);
        }
        sessions.clear();
        byPlayer.clear();
    }

    boolean hasSession(UUID islandId) {
        for (Session s : sessions.values()) {
            if (s.islandId().equals(islandId)) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ giving

    /** Puts the book in a free inventory slot (needs the read right; writable with the write right). */
    void give(Player player, Island island) {
        IslandNotes.Rights rights = notes.rights(player, island);
        if (!rights.canView()) {
            islands.fail(player, "notes.no-read");
            return;
        }
        List<Note> list = notes.cached(island.id());
        if (list == null) {
            islands.fail(player, "error.database");
            return;
        }
        end(player, false);
        PlayerInventory inventory = player.getInventory();
        int slot = inventory.firstEmpty();
        if (slot < 0 || slot > 35) {
            islands.fail(player, "notes.book.full");
            return;
        }
        boolean writable = rights.canAdd() || rights.write() || rights.manage();
        List<Note> ordered = NoteRules.order(list).stream().filter(n -> !n.done() || !writable).toList();
        UUID token = UUID.randomUUID();
        List<UUID> ids = new ArrayList<>();
        List<String> originals = new ArrayList<>();
        ItemStack book = core.menus().item(new ItemStack(writable ? Material.WRITABLE_BOOK : Material.WRITTEN_BOOK),
                module.text(player, writable ? "notes.book.name" : "notes.book.name-read"),
                module.lines(player, writable ? "notes.book.lore" : "notes.book.lore-read", Msg.p("seconds", module.config().notesBookSeconds)));
        if (writable) {
            book.editMeta(WritableBookMeta.class, meta -> {
                List<String> pages = new ArrayList<>();
                for (Note note : ordered) {
                    ids.add(note.id());
                    originals.add(note.text());
                    pages.add(note.text());
                }
                pages.add("");
                meta.setPages(pages);
            });
        } else {
            book.editMeta(BookMeta.class, meta -> {
                meta.setTitle(PlainTextComponentSerializer.plainText().serialize(module.text(player, "notes.book.title")));
                meta.setAuthor(islands.displayName(island));
                List<Component> pages = new ArrayList<>();
                for (Note note : ordered) {
                    pages.add(module.msg().component(player, note.done() ? "notes.book.page-done" : "notes.book.page",
                            Msg.p("text", note.text()), Msg.p("author", islands.name(note.author())), Msg.p("date", notes.date(note.createdAt()))));
                }
                if (pages.isEmpty()) {
                    pages.add(module.msg().component(player, "notes.book.page-empty"));
                }
                meta.pages(pages);
            });
        }
        book.editMeta(meta -> meta.getPersistentDataContainer().set(tag, PersistentDataType.STRING, token.toString()));
        Session session = new Session(token, player.getUniqueId(), island.id(), List.copyOf(ids), List.copyOf(originals), writable,
                System.currentTimeMillis() + module.config().notesBookSeconds * 1000L);
        sessions.put(token, session);
        byPlayer.put(player.getUniqueId(), token);
        inventory.setItem(slot, book);
        player.closeInventory();
        module.msg().send(player, writable ? "notes.book.given" : "notes.book.given-read", Msg.p("seconds", module.config().notesBookSeconds));
        core.sounds().play(player, "success");
        core.audit().log(AuditCategory.ISLAND, "island_note_book", Actor.player(player), island.id().toString(),
                islands.displayName(island), null, null, Map.of("writable", writable, "pages", ids.size()));
    }

    // ------------------------------------------------------------------ taking back

    /** Ends the player's session and takes the book back ({@code tell} = say so). */
    void end(Player player, boolean tell) {
        UUID token = byPlayer.remove(player.getUniqueId());
        if (token != null) {
            sessions.remove(token);
        }
        if (removeAll(player) && tell) {
            module.msg().send(player, "notes.book.taken");
        }
    }

    /** The island is gone: every book of it is taken back. */
    void endIsland(UUID islandId) {
        for (Session s : List.copyOf(sessions.values())) {
            if (s.islandId().equals(islandId)) {
                Player player = Bukkit.getPlayer(s.player());
                sessions.remove(s.token());
                byPlayer.remove(s.player());
                if (player != null) {
                    removeAll(player);
                }
            }
        }
    }

    private void expire() {
        long now = System.currentTimeMillis();
        for (Session s : List.copyOf(sessions.values())) {
            if (s.expiresAt() <= now) {
                Player player = Bukkit.getPlayer(s.player());
                sessions.remove(s.token());
                byPlayer.remove(s.player());
                if (player != null && removeAll(player)) {
                    module.msg().send(player, "notes.book.expired");
                }
            }
        }
    }

    /** Removes every notes book from the player's inventory and cursor. True if one was found. */
    private boolean removeAll(Player player) {
        boolean found = false;
        PlayerInventory inventory = player.getInventory();
        ItemStack[] contents = inventory.getContents();
        for (int i = 0; i < contents.length; i++) {
            if (isBook(contents[i])) {
                inventory.setItem(i, null);
                found = true;
            }
        }
        if (isBook(player.getItemOnCursor())) {
            player.setItemOnCursor(null);
            found = true;
        }
        return found;
    }

    private UUID token(ItemStack item) {
        if (item == null || (item.getType() != Material.WRITABLE_BOOK && item.getType() != Material.WRITTEN_BOOK) || !item.hasItemMeta()) {
            return null;
        }
        String raw = item.getItemMeta().getPersistentDataContainer().get(tag, PersistentDataType.STRING);
        if (raw == null) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            return new UUID(0, 0);
        }
    }

    boolean isBook(ItemStack item) {
        return token(item) != null;
    }

    // ------------------------------------------------------------------ saving

    @EventHandler(priority = EventPriority.LOWEST)
    public void onEdit(PlayerEditBookEvent event) {
        Player player = event.getPlayer();
        UUID token = tokenOf(event.getPreviousBookMeta());
        if (token == null) {
            return;
        }
        event.setCancelled(true); // the item itself never changes; it is taken back below
        Session session = sessions.remove(token);
        byPlayer.remove(player.getUniqueId(), token);
        core.scheduler().later("islands:note-book-take", 1L, () -> removeAll(player));
        if (session == null || !session.player().equals(player.getUniqueId())) {
            module.msg().send(player, "notes.book.expired");
            return;
        }
        if (!session.writable()) {
            return;
        }
        Island island = islands.byId(session.islandId());
        if (island == null) {
            islands.fail(player, "error.island-gone");
            return;
        }
        List<String> pages = new ArrayList<>();
        for (Component page : event.getNewBookMeta().pages()) {
            pages.add(PlainTextComponentSerializer.plainText().serialize(page));
        }
        notes.saveBook(player, island, session.ids(), session.originals(), pages);
    }

    private UUID tokenOf(BookMeta meta) {
        if (meta == null) {
            return null;
        }
        String raw = meta.getPersistentDataContainer().get(tag, PersistentDataType.STRING);
        if (raw == null) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            return new UUID(0, 0);
        }
    }

    // ------------------------------------------------------------------ the book stays put

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        boolean involved = isBook(event.getCurrentItem()) || isBook(event.getCursor());
        if (!involved && event.getClick() == ClickType.NUMBER_KEY && event.getHotbarButton() >= 0) {
            involved = isBook(player.getInventory().getItem(event.getHotbarButton()));
        }
        if (!involved && event.getClick() == ClickType.SWAP_OFFHAND) {
            involved = isBook(player.getInventory().getItemInOffHand());
        }
        if (involved) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrag(InventoryDragEvent event) {
        if (isBook(event.getOldCursor())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrop(PlayerDropItemEvent event) {
        if (isBook(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
            module.msg().send(event.getPlayer(), "notes.book.no-drop");
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        ItemStack hand = event.getPlayer().getInventory().getItem(event.getHand());
        if (isBook(hand)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || !isBook(event.getItem())) {
            return;
        }
        Block block = event.getClickedBlock();
        if (block != null && (block.getType() == Material.LECTERN || block.getType() == Material.CHISELED_BOOKSHELF)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCraft(PrepareItemCraftEvent event) {
        for (ItemStack item : event.getInventory().getMatrix()) {
            if (isBook(item)) {
                event.getInventory().setResult(null);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDeath(PlayerDeathEvent event) {
        event.getDrops().removeIf(this::isBook);
        event.getItemsToKeep().removeIf(this::isBook);
        UUID token = byPlayer.remove(event.getEntity().getUniqueId());
        if (token != null) {
            sessions.remove(token);
        }
    }

    /** Before a profile switch swaps the inventory: the book never travels to another profile. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onProfileSwitch(ProfileSwitchEvent event) {
        end(event.getPlayer(), false);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        removeAll(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        end(event.getPlayer(), false);
    }
}
