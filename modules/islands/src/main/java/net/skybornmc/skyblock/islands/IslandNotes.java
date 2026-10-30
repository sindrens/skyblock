package net.skybornmc.skyblock.islands;

import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.skybornmc.skyblock.common.Core;
import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.audit.AuditCategory;
import net.skybornmc.skyblock.common.db.Database;
import net.skybornmc.skyblock.common.db.MainThread;
import net.skybornmc.skyblock.common.menu.ConfirmMenu;
import net.skybornmc.skyblock.common.menu.Menu;
import net.skybornmc.skyblock.common.settings.PlayerSettings;
import net.skybornmc.skyblock.common.settings.SettingCategory;
import net.skybornmc.skyblock.common.settings.SettingToggle;
import net.skybornmc.skyblock.common.text.ModuleMessages;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.text.TextFilter;
import net.skybornmc.skyblock.common.util.Scheduler;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.logging.Level;

/**
 * {@code /is notes} (NOTES, owner N1-N9): one shared to-do list per island. Notes are added, changed and ticked in a
 * menu (chat input with the chat filter, Bedrock-safe), shown in a temporary book ({@link NoteBook}, N3) and can be pinned
 * as a hologram on the island ({@link NoteHolograms}, N9; ticking a note done removes its pin). Online members are told
 * about new notes ({@code /settings} toggle, N5); the newest open note is the placeholder {@code island_next_note} for a
 * scoreboard line (own toggle, off by default). Deleted notes are kept for staff ({@code notes.deleted-keep-days}, N7).
 *
 * <p>Every change is saved first (one transaction, limits checked against the database), then cached and logged
 * (category island, {@code island_note_*}). Notes of an island are loaded on first use and kept while used.</p>
 */
final class IslandNotes implements Listener {

    static final String PERM_STAFF = "skyblock.island.notes.staff";
    static final String SETTING_ALERTS = "island-note-alerts";
    static final String SETTING_SCOREBOARD = "island-notes-scoreboard";
    private static final long EVICT_AFTER_MS = Duration.ofMinutes(30).toMillis();

    /** What a viewer may do with an island's notes. */
    record Rights(boolean read, boolean write, boolean manage, boolean staff) {

        boolean canView() {
            return NoteRules.canRead(read, write, manage) || staff;
        }

        boolean canAdd() {
            return NoteRules.canAdd(write);
        }

        boolean canTick() {
            return NoteRules.canTick(write, manage);
        }

        boolean canChange(boolean author) {
            return NoteRules.canChange(author, write, manage);
        }

        boolean canClearDone() {
            return NoteRules.canClearDone(manage);
        }
    }

    /** The cached notes of one island (immutable list, replaced on change; read by the placeholder from any thread). */
    private static final class Board {
        volatile List<Note> notes;
        volatile long used = System.currentTimeMillis();

        Board(List<Note> notes) {
            this.notes = List.copyOf(notes);
        }
    }

    private final IslandsModule module;
    private final Core core;
    private final IslandManager islands;
    private final IslandNoteStore store;
    private final ModuleMessages msg;
    private final Executor main;
    private final Function<String, Island> findIsland;
    private final Map<UUID, Board> boards = new ConcurrentHashMap<>();
    private final Map<UUID, CompletableFuture<List<Note>>> loading = new HashMap<>();
    private final Set<UUID> busy = new HashSet<>();
    private final Map<UUID, Long> lastAdd = new HashMap<>();
    private final NoteHolograms holograms;
    private final NoteBook book;
    private PlayerSettings settings;
    private Scheduler.Task sweepTask;

    IslandNotes(IslandsModule module, IslandManager islands, IslandNoteStore store, Function<String, Island> findIsland) {
        this.module = module;
        this.core = module.core();
        this.islands = islands;
        this.store = store;
        this.msg = module.msg();
        this.main = new MainThread(core.plugin());
        this.findIsland = findIsland;
        this.holograms = new NoteHolograms(module, islands, this);
        this.book = new NoteBook(module, islands, this);
    }

    private IslandsConfig config() {
        return module.config();
    }

    NoteHolograms holograms() {
        return holograms;
    }

    NoteBook book() {
        return book;
    }

    // ------------------------------------------------------------------ lifecycle

    void start() {
        org.bukkit.Bukkit.getPluginManager().registerEvents(this, core.plugin());
        org.bukkit.Bukkit.getPluginManager().registerEvents(holograms, core.plugin());
        org.bukkit.Bukkit.getPluginManager().registerEvents(book, core.plugin());
        book.start();
        restartSweep();
        purge();
        core.placeholders().registerText(IslandsModule.ID, "island_next_note", this::nextNoteText);
        for (Player player : org.bukkit.Bukkit.getOnlinePlayers()) {
            islands.islandOfPlayer(islands.key(player)).ifPresent(island -> ensure(island));
        }
        holograms.loadedChunks();
    }

    /** Hooks {@code /settings} (the menus module may enable after islands). */
    void hookSettings() {
        core.services().optional(PlayerSettings.class).ifPresent(service -> {
            service.register(new SettingToggle(IslandsModule.ID, SETTING_ALERTS, SettingCategory.SOCIAL, Material.WRITABLE_BOOK, true,
                    null, IslandsModule.ID, "settings." + SETTING_ALERTS, false));
            service.register(new SettingToggle(IslandsModule.ID, SETTING_SCOREBOARD, SettingCategory.SOCIAL, Material.PAPER, false,
                    null, IslandsModule.ID, "settings." + SETTING_SCOREBOARD, false));
            settings = service;
        });
    }

    /** Every 5 minutes: drop unused boards; once a day: remove deleted notes older than the keep time. */
    void restartSweep() {
        if (sweepTask != null) {
            sweepTask.cancel();
        }
        long period = 20L * 60 * 5;
        long[] runs = {0};
        sweepTask = core.scheduler().repeat("islands:notes-sweep", period, period, () -> {
            evict();
            if (++runs[0] % (12 * 24) == 0) {
                purge();
            }
        });
    }

    void stop() {
        if (sweepTask != null) {
            sweepTask.cancel();
            sweepTask = null;
        }
        book.stop();
        holograms.clearAll();
        org.bukkit.event.HandlerList.unregisterAll(this);
        org.bukkit.event.HandlerList.unregisterAll(holograms);
        org.bukkit.event.HandlerList.unregisterAll(book);
        settings = null; // the toggles go with the module's others (VisitManager.stop unregisters every islands toggle)
        boards.clear();
    }

    private void purge() {
        long before = System.currentTimeMillis() - Duration.ofDays(config().notesKeepDays).toMillis();
        store.purge(before).whenComplete((count, error) -> {
            if (error != null) {
                core.plugin().getLogger().log(Level.WARNING, "Islands: purging old deleted notes failed", Database.cause(error));
            } else if (count != null && count > 0) {
                core.audit().log(AuditCategory.ISLAND, "island_note_purge", Actor.system(IslandsModule.ID),
                        Map.of("count", count, "before", before));
            }
        });
    }

    private void evict() {
        long cutoff = System.currentTimeMillis() - EVICT_AFTER_MS;
        boards.entrySet().removeIf(e -> e.getValue().used < cutoff && !holograms.hasAny(e.getKey()) && !book.hasSession(e.getKey())
                && !anyoneOnline(islands.byId(e.getKey())));
    }

    /** True if a member or coop player of the island is online (their scoreboard placeholder reads its notes). */
    private boolean anyoneOnline(Island island) {
        if (island == null) {
            return false;
        }
        for (UUID key : island.members().keySet()) {
            if (islands.online(key) != null) {
                return true;
            }
        }
        return false;
    }

    /** The island is gone (disband, staff delete, reset): holograms and books go, the cache is dropped. */
    void islandRemoved(UUID islandId) {
        holograms.hideIsland(islandId);
        book.endIsland(islandId);
        boards.remove(islandId);
    }

    // ------------------------------------------------------------------ cache

    /** Cached notes of an island (any order), or null when not loaded. Touches the board. */
    List<Note> cached(UUID islandId) {
        Board board = boards.get(islandId);
        if (board == null) {
            return null;
        }
        board.used = System.currentTimeMillis();
        return board.notes;
    }

    /** The notes of {@code island}, loaded on first use; completes on the main thread. */
    CompletableFuture<List<Note>> ensure(Island island) {
        List<Note> cached = cached(island.id());
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }
        return loading.computeIfAbsent(island.id(), id -> store.load(id).thenApplyAsync(list -> {
            loading.remove(id);
            if (islands.byId(id) != island) {
                return List.<Note>of();
            }
            Board board = boards.computeIfAbsent(id, k -> new Board(list));
            holograms.showAll(island, board.notes);
            return board.notes;
        }, main).whenComplete((list, error) -> {
            if (error != null) {
                main.execute(() -> loading.remove(id));
                core.plugin().getLogger().log(Level.SEVERE, "Islands: loading notes of " + id + " failed", Database.cause(error));
            }
        }));
    }

    private void update(UUID islandId, Function<List<Note>, List<Note>> change) {
        Board board = boards.get(islandId);
        if (board != null) {
            board.notes = List.copyOf(change.apply(board.notes));
            board.used = System.currentTimeMillis();
        }
    }

    private void put(UUID islandId, Note note) {
        update(islandId, list -> {
            List<Note> next = new ArrayList<>(list.size() + 1);
            boolean replaced = false;
            for (Note n : list) {
                if (n.id().equals(note.id())) {
                    next.add(note);
                    replaced = true;
                } else {
                    next.add(n);
                }
            }
            if (!replaced) {
                next.add(note);
            }
            return next;
        });
    }

    private void drop(UUID islandId, java.util.Collection<UUID> ids) {
        update(islandId, list -> list.stream().filter(n -> !ids.contains(n.id())).toList());
    }

    /** Reloads one island's notes from the database (after a book save), then refreshes its holograms. */
    CompletableFuture<List<Note>> reload(Island island) {
        return store.load(island.id()).thenApplyAsync(list -> {
            if (islands.byId(island.id()) != island) {
                return List.<Note>of();
            }
            Board board = boards.computeIfAbsent(island.id(), k -> new Board(list));
            board.notes = List.copyOf(list);
            holograms.showAll(island, board.notes);
            return board.notes;
        }, main);
    }

    Note note(Island island, UUID noteId) {
        List<Note> list = cached(island.id());
        if (list == null) {
            return null;
        }
        for (Note n : list) {
            if (n.id().equals(noteId)) {
                return n;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ rights

    Rights rights(Player player, Island island) {
        UUID key = islands.key(player);
        return new Rights(islands.roleAllows(island, key, IslandPerm.NOTES_READ), islands.roleAllows(island, key, IslandPerm.NOTES_WRITE),
                islands.roleAllows(island, key, IslandPerm.NOTES_MANAGE), player.hasPermission(PERM_STAFF));
    }

    boolean isAuthor(Player player, Note note) {
        return note.author().equals(islands.key(player));
    }

    /** The island {@code /is notes} opens: your own, else the one you stand on when you may read its notes (coop). */
    Island targetOf(Player player) {
        Optional<Island> own = islands.islandOfPlayer(islands.key(player));
        if (own.isPresent()) {
            return own.get();
        }
        Location at = player.getLocation();
        if (islands.isIslandWorld(at.getWorld())) {
            Island here = islands.islandInSlotAt(at.getBlockX(), at.getBlockZ());
            if (here != null) {
                Rights rights = rights(player, here);
                if (NoteRules.canRead(rights.read(), rights.write(), rights.manage())) {
                    return here;
                }
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ command and menus

    /** {@code /is notes [player]}: the notes menu of your island, or (staff) of the island {@code typed} is on. */
    void command(Player player, String typed) {
        Island island;
        boolean staffView = false;
        if (typed != null && !typed.isBlank()) {
            if (!player.hasPermission(PERM_STAFF)) {
                islands.fail(player, "notes.staff-only");
                return;
            }
            island = findIsland.apply(typed.trim());
            if (island == null) {
                islands.fail(player, "notes.not-found", Msg.p("input", typed.trim()));
                return;
            }
            staffView = true;
        } else {
            island = targetOf(player);
            if (island == null) {
                islands.fail(player, "no-island");
                return;
            }
        }
        if (!rights(player, island).canView()) {
            islands.fail(player, "notes.no-read");
            return;
        }
        if (staffView) {
            core.audit().log(AuditCategory.STAFF, "island_notes_view", Actor.player(player), island.id().toString(),
                    islands.displayName(island), null, null, Map.of());
        }
        open(player, island, null);
    }

    /** Opens the notes menu (after loading the notes if needed); {@code parent} = the menu Back returns to. */
    void open(Player player, Island island, Menu parent) {
        ensure(island).whenComplete((list, error) -> main.execute(() -> {
            if (!player.isOnline()) {
                return;
            }
            if (error != null) {
                islands.fail(player, "error.database");
                return;
            }
            NotesMenu menu = new NotesMenu(module, islands, this, island);
            if (parent != null) {
                parent.openChild(menu);
            } else {
                core.menus().open(player, menu);
            }
        }));
    }

    /** Staff: the deleted notes of the last {@code notes.deleted-keep-days} days (N7). */
    void openDeleted(Player player, Island island, Menu parent) {
        if (!player.hasPermission(PERM_STAFF)) {
            islands.fail(player, "notes.staff-only");
            return;
        }
        long since = System.currentTimeMillis() - Duration.ofDays(config().notesKeepDays).toMillis();
        store.deleted(island.id(), since).thenAcceptAsync(list -> {
            if (player.isOnline()) {
                parent.openChild(new DeletedNotesMenu(module, islands, island, list));
            }
        }, main).exceptionally(error -> islands.databaseError(player, "deleted notes", error));
    }

    // ------------------------------------------------------------------ text input

    /** Cleans, length-checks and filters player text; null (with a message) when it can't be used. */
    String acceptText(Player player, String raw) {
        String text = NoteRules.clean(raw);
        switch (NoteRules.check(text, config().notesMaxLength)) {
            case EMPTY -> {
                islands.fail(player, "notes.empty-text");
                return null;
            }
            case TOO_LONG -> {
                islands.fail(player, "notes.too-long", Msg.p("max", config().notesMaxLength));
                return null;
            }
            default -> {
            }
        }
        Optional<TextFilter> filter = core.services().optional(TextFilter.class);
        if (filter.isPresent()) {
            TextFilter.Result result = filter.get().check(player, text);
            if (result.isBlocked()) {
                islands.fail(player, "notes.blocked");
                core.audit().logPlayer(AuditCategory.CHAT, "island_note_blocked", Actor.player(player), player.getUniqueId(),
                        player.getName(), null, null, Map.of("reason", result.blocked(), "text", text));
                return null;
            }
            text = NoteRules.clean(result.text());
        }
        return text;
    }

    /** Asks for a new note in chat, then reopens the menu. */
    void promptAdd(Player player, Island island, Menu back) {
        Rights rights = rights(player, island);
        if (!rights.canAdd()) {
            islands.fail(player, "notes.no-write");
            return;
        }
        if (countOf(island) >= config().notesMax) {
            islands.fail(player, "notes.limit", Msg.p("max", config().notesMax));
            return;
        }
        core.menus().prompt(player, IslandsModule.ID, "notes.prompt.add", config().notesMaxLength, typed -> {
            String text = acceptText(player, typed);
            if (text == null) {
                reopen(player, island, back);
                return;
            }
            add(player, island, text, () -> reopen(player, island, back));
        }, () -> reopen(player, island, back), Msg.p("max", config().notesMaxLength));
    }

    /** Asks for the new text of a note in chat, then reopens the menu. */
    void promptEdit(Player player, Island island, Note note, Menu back) {
        if (!rights(player, island).canChange(isAuthor(player, note))) {
            islands.fail(player, "notes.no-change");
            return;
        }
        core.menus().prompt(player, IslandsModule.ID, "notes.prompt.edit", config().notesMaxLength, typed -> {
            String text = acceptText(player, typed);
            if (text == null) {
                reopen(player, island, back);
                return;
            }
            edit(player, island, note.id(), text, () -> reopen(player, island, back));
        }, () -> reopen(player, island, back), Msg.p("max", config().notesMaxLength), Msg.p("text", note.text()));
    }

    private void reopen(Player player, Island island, Menu back) {
        if (player.isOnline() && islands.byId(island.id()) == island && rights(player, island).canView()) {
            if (back != null) {
                core.menus().open(player, back);
            } else {
                open(player, island, null);
            }
        }
    }

    private int countOf(Island island) {
        List<Note> list = cached(island.id());
        return list == null ? 0 : list.size();
    }

    // ------------------------------------------------------------------ changes

    private boolean begin(Player player) {
        return busy.add(player.getUniqueId());
    }

    private void end(Player player) {
        busy.remove(player.getUniqueId());
    }

    /** Adds a note (text already checked); {@code after} runs on the main thread when done. */
    void add(Player player, Island island, String text, Runnable after) {
        if (!rights(player, island).canAdd()) {
            islands.fail(player, "notes.no-write");
            after.run();
            return;
        }
        long now = System.currentTimeMillis();
        Long last = lastAdd.get(player.getUniqueId());
        if (last != null && now - last < config().notesAddCooldownMs) {
            islands.fail(player, "notes.slow-down");
            after.run();
            return;
        }
        if (!begin(player)) {
            return;
        }
        Note note = Note.create(UUID.randomUUID(), island.id(), islands.key(player), text, now);
        store.add(note, config().notesMax).thenAcceptAsync(result -> {
            end(player);
            switch (result) {
                case ADDED -> {
                    lastAdd.put(player.getUniqueId(), now);
                    put(island.id(), note);
                    audit("island_note_add", Actor.player(player), island, note, Map.of());
                    msg.send(player, "notes.added");
                    core.sounds().play(player, "success");
                    notifyAdded(player, island, note);
                }
                case LIMIT -> islands.fail(player, "notes.limit", Msg.p("max", config().notesMax));
                case ISLAND_GONE -> islands.fail(player, "error.island-gone");
            }
            after.run();
        }, main).exceptionally(error -> {
            main.execute(() -> end(player));
            return islands.databaseError(player, "note add", error);
        });
    }

    void edit(Player player, Island island, UUID noteId, String text, Runnable after) {
        Note before = note(island, noteId);
        if (before == null) {
            islands.fail(player, "notes.gone");
            after.run();
            return;
        }
        if (!rights(player, island).canChange(isAuthor(player, before))) {
            islands.fail(player, "notes.no-change");
            after.run();
            return;
        }
        if (before.text().equals(text) || !begin(player)) {
            after.run();
            return;
        }
        long now = System.currentTimeMillis();
        UUID by = islands.key(player);
        store.edit(island.id(), noteId, text, by, now).thenAcceptAsync(ok -> {
            end(player);
            if (!ok) {
                islands.fail(player, "notes.gone");
            } else {
                Note current = note(island, noteId);
                Note changed = (current == null ? before : current).withText(text, by, now);
                put(island.id(), changed);
                holograms.show(island, changed);
                audit("island_note_edit", Actor.player(player), island, changed, Map.of("old", before.text()));
                msg.send(player, "notes.changed");
                core.sounds().play(player, "success");
            }
            after.run();
        }, main).exceptionally(error -> {
            main.execute(() -> end(player));
            return islands.databaseError(player, "note edit", error);
        });
    }

    /** Ticks a note done (removes its pin) or opens it again. */
    void toggleDone(Player player, Island island, UUID noteId, Runnable after) {
        Note before = note(island, noteId);
        if (before == null) {
            islands.fail(player, "notes.gone");
            after.run();
            return;
        }
        if (!rights(player, island).canTick()) {
            islands.fail(player, "notes.no-write");
            after.run();
            return;
        }
        if (!begin(player)) {
            return;
        }
        long now = System.currentTimeMillis();
        UUID by = before.done() ? null : islands.key(player);
        store.setDone(island.id(), noteId, by, now).thenAcceptAsync(ok -> {
            end(player);
            if (!ok) {
                islands.fail(player, "notes.gone");
            } else {
                Note changed = before.withDone(by, now);
                put(island.id(), changed);
                holograms.show(island, changed);
                audit(by == null ? "island_note_reopen" : "island_note_done", Actor.player(player), island, changed,
                        Map.of("was_pinned", before.pinned()));
                msg.send(player, by == null ? "notes.reopened" : "notes.done");
                core.sounds().play(player, "success");
            }
            after.run();
        }, main).exceptionally(error -> {
            main.execute(() -> end(player));
            return islands.databaseError(player, "note done", error);
        });
    }

    /** Pins a note where the player stands (inside the island's border) or moves its pin there. */
    void pinHere(Player player, Island island, UUID noteId, Runnable after) {
        Note before = note(island, noteId);
        if (before == null) {
            islands.fail(player, "notes.gone");
            after.run();
            return;
        }
        if (!rights(player, island).canChange(isAuthor(player, before))) {
            islands.fail(player, "notes.no-change");
            after.run();
            return;
        }
        if (before.done()) {
            islands.fail(player, "notes.pin-done");
            after.run();
            return;
        }
        Location at = player.getLocation();
        if (!islands.isIslandWorld(at.getWorld()) || islands.islandAt(at.getBlockX(), at.getBlockZ()) != island) {
            islands.fail(player, "notes.pin-outside");
            after.run();
            return;
        }
        if (at.getBlockY() < config().minBuildY || at.getBlockY() > config().maxBuildY) {
            islands.fail(player, "notes.pin-height", Msg.p("min", config().minBuildY), Msg.p("max", config().maxBuildY));
            after.run();
            return;
        }
        if (config().notesMaxHolograms <= 0) {
            islands.fail(player, "notes.pin-off");
            after.run();
            return;
        }
        if (!begin(player)) {
            return;
        }
        long now = System.currentTimeMillis();
        Note.Pin pin = new Note.Pin(at.getWorld().getName(), at.getBlockX() + 0.5, at.getY(), at.getBlockZ() + 0.5);
        store.pin(island.id(), noteId, pin, config().notesMaxHolograms, now).thenAcceptAsync(result -> {
            end(player);
            switch (result) {
                case PINNED, MOVED -> {
                    Note current = note(island, noteId);
                    Note changed = (current == null ? before : current).withPin(pin, now);
                    put(island.id(), changed);
                    holograms.show(island, changed);
                    Map<String, Object> details = new HashMap<>();
                    details.put("x", pin.x());
                    details.put("y", pin.y());
                    details.put("z", pin.z());
                    details.put("moved", result == IslandNoteStore.PinResult.MOVED);
                    audit("island_note_pin", Actor.player(player), island, changed, details);
                    msg.send(player, result == IslandNoteStore.PinResult.MOVED ? "notes.pin-moved" : "notes.pinned");
                    core.sounds().play(player, "success");
                }
                case LIMIT -> islands.fail(player, "notes.pin-limit", Msg.p("max", config().notesMaxHolograms));
                case DONE -> islands.fail(player, "notes.pin-done");
                case GONE -> islands.fail(player, "notes.gone");
            }
            after.run();
        }, main).exceptionally(error -> {
            main.execute(() -> end(player));
            return islands.databaseError(player, "note pin", error);
        });
    }

    void unpin(Player player, Island island, UUID noteId, Runnable after) {
        Note before = note(island, noteId);
        if (before == null || !before.pinned()) {
            islands.fail(player, "notes.gone");
            after.run();
            return;
        }
        if (!rights(player, island).canChange(isAuthor(player, before))) {
            islands.fail(player, "notes.no-change");
            after.run();
            return;
        }
        if (!begin(player)) {
            return;
        }
        long now = System.currentTimeMillis();
        store.unpin(island.id(), noteId, now).thenAcceptAsync(ok -> {
            end(player);
            Note current = note(island, noteId);
            Note changed = (current == null ? before : current).withPin(null, now);
            put(island.id(), changed);
            holograms.show(island, changed);
            if (ok) {
                audit("island_note_unpin", Actor.player(player), island, changed, Map.of());
                msg.send(player, "notes.unpinned");
                core.sounds().play(player, "success");
            }
            after.run();
        }, main).exceptionally(error -> {
            main.execute(() -> end(player));
            return islands.databaseError(player, "note unpin", error);
        });
    }

    /** Confirm screen, then a soft delete (kept for staff, N7). */
    void confirmDelete(Player player, Island island, Note note, Menu from) {
        if (!rights(player, island).canChange(isAuthor(player, note))) {
            islands.fail(player, "notes.no-change");
            return;
        }
        TagResolver text = Msg.p("text", NoteRules.shorten(note.text(), 40));
        from.openChild(new ConfirmMenu(
                viewer -> module.text(viewer, "notes.delete.crumb"),
                core.menus().item(Material.PAPER, player, IslandsModule.ID, "notes.delete.shown", text),
                viewer -> module.lines(viewer, "notes.delete.details", Msg.p("days", config().notesKeepDays)),
                viewer -> delete(viewer, island, note.id(), () -> reopen(viewer, island, null)),
                viewer -> { }));
    }

    void delete(Player player, Island island, UUID noteId, Runnable after) {
        Note before = note(island, noteId);
        if (before == null) {
            islands.fail(player, "notes.gone");
            after.run();
            return;
        }
        if (!rights(player, island).canChange(isAuthor(player, before))) {
            islands.fail(player, "notes.no-change");
            after.run();
            return;
        }
        if (!begin(player)) {
            return;
        }
        long now = System.currentTimeMillis();
        UUID by = islands.key(player);
        store.delete(island.id(), noteId, by, IslandNoteStore.REASON_DELETE, now).thenAcceptAsync(ok -> {
            end(player);
            drop(island.id(), Set.of(noteId));
            holograms.hide(noteId);
            if (ok) {
                audit("island_note_delete", Actor.player(player), island, before, Map.of());
                msg.send(player, "notes.removed");
                core.sounds().play(player, "success");
            } else {
                islands.fail(player, "notes.gone");
            }
            after.run();
        }, main).exceptionally(error -> {
            main.execute(() -> end(player));
            return islands.databaseError(player, "note delete", error);
        });
    }

    /** Confirm screen, then every done note is soft-deleted. */
    void confirmClearDone(Player player, Island island, Menu from) {
        if (!rights(player, island).canClearDone()) {
            islands.fail(player, "notes.no-manage");
            return;
        }
        List<Note> list = cached(island.id());
        int done = list == null ? 0 : list.size() - NoteRules.openCount(list);
        if (done == 0) {
            islands.fail(player, "notes.clear-none");
            return;
        }
        from.openChild(new ConfirmMenu(
                viewer -> module.text(viewer, "notes.clear.crumb"),
                core.menus().item(Material.CAULDRON, player, IslandsModule.ID, "notes.clear.shown", Msg.p("count", done)),
                viewer -> module.lines(viewer, "notes.clear.details", Msg.p("count", done), Msg.p("days", config().notesKeepDays)),
                viewer -> clearDone(viewer, island),
                viewer -> { }));
    }

    private void clearDone(Player player, Island island) {
        if (!rights(player, island).canClearDone() || !begin(player)) {
            return;
        }
        long now = System.currentTimeMillis();
        store.clearDone(island.id(), islands.key(player), now).thenAcceptAsync(ids -> {
            end(player);
            drop(island.id(), ids);
            ids.forEach(holograms::hide);
            core.audit().log(AuditCategory.ISLAND, "island_note_clear", Actor.player(player), island.id().toString(),
                    islands.displayName(island), (long) ids.size(), null, Map.of("notes", ids.stream().map(UUID::toString).toList()));
            msg.send(player, "notes.cleared", Msg.p("count", ids.size()));
            core.sounds().play(player, "success");
            reopen(player, island, null);
        }, main).exceptionally(error -> {
            main.execute(() -> end(player));
            return islands.databaseError(player, "notes clear", error);
        });
    }

    // ------------------------------------------------------------------ book (N3)

    /** Saves the temporary book: edits and new pages in one transaction, then reloads the island's notes. */
    void saveBook(Player player, Island island, List<UUID> ids, List<String> originals, List<String> pages) {
        if (islands.byId(island.id()) != island) {
            islands.fail(player, "error.island-gone");
            return;
        }
        Rights rights = rights(player, island);
        NoteRules.BookDiff diff = NoteRules.diff(ids, originals, pages);
        if (diff.isEmpty()) {
            msg.send(player, diff.emptied() > 0 ? "notes.book.saved-nothing-emptied" : "notes.book.saved-nothing");
            return;
        }
        List<NoteRules.BookDiff.Edit> edits = new ArrayList<>();
        int refused = 0;
        int blocked = 0;
        UUID by = islands.key(player);
        for (NoteRules.BookDiff.Edit edit : diff.edits()) {
            Note note = note(island, edit.note());
            if (note == null || !rights.canChange(note.author().equals(by))) {
                refused++;
                continue;
            }
            String text = checkPage(player, edit.text());
            if (text == null) {
                blocked++;
            } else if (!text.equals(note.text())) {
                edits.add(new NoteRules.BookDiff.Edit(edit.note(), text));
            }
        }
        List<Note> adds = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (String page : diff.added()) {
            if (!rights.canAdd()) {
                refused++;
                continue;
            }
            String text = checkPage(player, page);
            if (text == null) {
                blocked++;
            } else {
                adds.add(Note.create(UUID.randomUUID(), island.id(), by, text, now));
            }
        }
        int refusedFinal = refused;
        int blockedFinal = blocked;
        if (edits.isEmpty() && adds.isEmpty()) {
            msg.send(player, "notes.book.saved-none", Msg.p("refused", refusedFinal), Msg.p("blocked", blockedFinal));
            core.sounds().play(player, "error");
            return;
        }
        Map<UUID, String> oldTexts = new HashMap<>();
        edits.forEach(e -> {
            Note n = note(island, e.note());
            oldTexts.put(e.note(), n == null ? "" : n.text());
        });
        store.saveBook(island.id(), edits, adds, by, config().notesMax, now).thenAcceptAsync(result -> {
            Actor actor = Actor.player(player);
            reload(island).thenAccept(list -> {
                for (UUID edited : result.edited()) {
                    Note n = note(island, edited);
                    if (n != null) {
                        audit("island_note_edit", actor, island, n, Map.of("old", oldTexts.getOrDefault(edited, ""), "via", "book"));
                    }
                }
                for (Note added : result.added()) {
                    audit("island_note_add", actor, island, added, Map.of("via", "book"));
                    notifyAdded(player, island, added);
                }
                if (player.isOnline()) {
                    msg.send(player, "notes.book.saved", Msg.p("added", result.added().size()), Msg.p("changed", result.edited().size()),
                            Msg.p("skipped", result.overLimit() + refusedFinal + blockedFinal));
                    core.sounds().play(player, "success");
                }
            });
        }, main).exceptionally(error -> islands.databaseError(player, "book save", error));
    }

    /** One book page through the same checks as chat input; null (no message per page) when refused. */
    private String checkPage(Player player, String page) {
        String text = NoteRules.clean(page);
        if (NoteRules.check(text, config().notesMaxLength) != NoteRules.TextCheck.OK) {
            return null;
        }
        Optional<TextFilter> filter = core.services().optional(TextFilter.class);
        if (filter.isPresent()) {
            TextFilter.Result result = filter.get().check(player, text);
            if (result.isBlocked()) {
                core.audit().logPlayer(AuditCategory.CHAT, "island_note_blocked", Actor.player(player), player.getUniqueId(),
                        player.getName(), null, null, Map.of("reason", result.blocked(), "text", text, "via", "book"));
                return null;
            }
            text = NoteRules.clean(result.text());
        }
        return text;
    }

    // ------------------------------------------------------------------ notify, audit, placeholder

    /** Tells the island's online members and coop players who can read the notes (owner N5; /settings toggle). */
    private void notifyAdded(Player author, Island island, Note note) {
        Set<UUID> keys = new HashSet<>(island.members().keySet());
        keys.addAll(island.coops());
        TagResolver[] p = {Msg.p("player", author.getName()), Msg.p("text", NoteRules.shorten(note.text(), 60))};
        for (UUID key : keys) {
            Player online = islands.online(key);
            if (online == null || online.equals(author) || !rights(online, island).canView()) {
                continue;
            }
            PlayerSettings s = settings;
            if (s != null && !s.get(online, SETTING_ALERTS)) {
                continue;
            }
            msg.send(online, "notes.notify", p);
            core.sounds().play(online, "notify");
        }
    }

    private void audit(String action, Actor actor, Island island, Note note, Map<String, Object> extra) {
        Map<String, Object> details = new HashMap<>(extra);
        details.put("note", note.id().toString());
        details.put("author", note.author().toString());
        details.put("author_name", islands.name(note.author()));
        details.put("text", note.text());
        core.audit().log(AuditCategory.ISLAND, action, actor, island.id().toString(), islands.displayName(island), null, null, details);
    }

    /** {@code island_next_note}: the newest open note of the player's island when their scoreboard toggle is on. */
    private String nextNoteText(Player player) {
        if (player == null) {
            return "";
        }
        PlayerSettings s = settings;
        if (s == null || !s.get(player, SETTING_SCOREBOARD)) {
            return "";
        }
        Island island = islands.islandOfPlayer(islands.key(player)).orElse(null);
        if (island == null) {
            return "";
        }
        Board board = boards.get(island.id());
        Note next = board == null ? null : NoteRules.next(board.notes);
        return next == null ? "" : NoteRules.shorten(next.text(), config().notesScoreboardLength);
    }

    /** Date text for menus (members.date-format). */
    String date(long millis) {
        return config().dateFormatter().format(Instant.ofEpochMilli(millis));
    }

    /** Tab completion of the staff argument: island names for staff, nothing for players. */
    List<String> staffNames(CommandSender sender, Function<CommandSender, List<String>> names) {
        return sender.hasPermission(PERM_STAFF) ? names.apply(sender) : List.of();
    }

    // ------------------------------------------------------------------ events

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        core.scheduler().later("islands:notes-join", 20L, () -> {
            if (player.isOnline()) {
                islands.islandOfPlayer(islands.key(player)).ifPresent(this::ensure);
            }
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        busy.remove(event.getPlayer().getUniqueId());
        lastAdd.remove(event.getPlayer().getUniqueId());
    }
}
