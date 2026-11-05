package net.skybornmc.skyblock.social;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.skybornmc.skyblock.common.Core;
import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.audit.AuditCategory;
import net.skybornmc.skyblock.common.command.Arg;
import net.skybornmc.skyblock.common.command.CommandFailure;
import net.skybornmc.skyblock.common.command.CommandSpec;
import net.skybornmc.skyblock.common.command.Suggest;
import net.skybornmc.skyblock.common.db.Database;
import net.skybornmc.skyblock.common.db.Sql;
import net.skybornmc.skyblock.common.menu.Menu;
import net.skybornmc.skyblock.common.player.PlayerData;
import net.skybornmc.skyblock.common.settings.PlayerSettings;
import net.skybornmc.skyblock.common.text.ModuleMessages;
import net.skybornmc.skyblock.common.text.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

/**
 * Friends (owner 4 Nov 2026, plan B section E): {@code /friends} (a menu with online status, Visit island and requests),
 * {@code /friends add|accept|deny|remove|list}. Per account (player UUID), so leaving an island never loses them. A
 * friend joining shows one chat line (off in /settings → Social → Friend notices). Data:
 * {@code db/social/V002__friends.sql}; texts: messages/social.yml {@code friends.*}.
 */
final class Friends implements Listener {

    static final String SETTING_NOTICES = "friend-notices";
    static final String PERM = "skyblock.social.friends";

    private final Core core;
    private final SocialConfig config;
    private final ModuleMessages msg;
    private final Executor main;
    /** Online players' friends (loaded on join). */
    private final Map<UUID, Set<UUID>> friends = new ConcurrentHashMap<>();
    /** Online players' incoming requests. */
    private final Map<UUID, Set<UUID>> incoming = new ConcurrentHashMap<>();
    private volatile PlayerSettings settings;

    Friends(Core core, SocialConfig config, ModuleMessages msg, Executor main) {
        this.core = core;
        this.config = config;
        this.msg = msg;
        this.main = main;
    }

    void settings(PlayerSettings settings) {
        this.settings = settings;
    }

    // ------------------------------------------------------------------ pure rules (unit-tested)

    /** What {@code /friends add} does between two players. */
    enum AddResult { SELF, ALREADY, LIMIT, THEIR_LIMIT, ACCEPTED, REQUESTED, ALREADY_REQUESTED }

    static AddResult add(UUID me, UUID them, Set<UUID> myFriends, int theirCount, Set<UUID> myIncoming, Set<UUID> myOutgoing, int max) {
        if (me.equals(them)) {
            return AddResult.SELF;
        }
        if (myFriends.contains(them)) {
            return AddResult.ALREADY;
        }
        if (myFriends.size() >= max) {
            return AddResult.LIMIT;
        }
        if (myIncoming.contains(them)) {
            return theirCount >= max ? AddResult.THEIR_LIMIT : AddResult.ACCEPTED;
        }
        return myOutgoing.contains(them) ? AddResult.ALREADY_REQUESTED : AddResult.REQUESTED;
    }

    /** The pair in its stored order (smaller bytes first). */
    static UUID[] pair(UUID x, UUID y) {
        return Arrays.compare(Sql.toBytes(x), Sql.toBytes(y)) <= 0 ? new UUID[]{x, y} : new UUID[]{y, x};
    }

    // ------------------------------------------------------------------ SQL

    private Database db() {
        return core.database();
    }

    static Set<UUID> friendsOf(Connection c, UUID p) throws SQLException {
        Set<UUID> out = ConcurrentHashMap.newKeySet();
        out.addAll(Sql.query(c, "SELECT b FROM social_friends WHERE a = ?", rs -> Sql.uuid(rs, "b"), Sql.toBytes(p)));
        out.addAll(Sql.query(c, "SELECT a FROM social_friends WHERE b = ?", rs -> Sql.uuid(rs, "a"), Sql.toBytes(p)));
        return out;
    }

    static Set<UUID> requestsTo(Connection c, UUID p) throws SQLException {
        Set<UUID> out = ConcurrentHashMap.newKeySet();
        out.addAll(Sql.query(c, "SELECT sender FROM social_friend_requests WHERE receiver = ?", rs -> Sql.uuid(rs, "sender"), Sql.toBytes(p)));
        return out;
    }

    static Set<UUID> requestsFrom(Connection c, UUID p) throws SQLException {
        return Set.copyOf(Sql.query(c, "SELECT receiver FROM social_friend_requests WHERE sender = ?", rs -> Sql.uuid(rs, "receiver"), Sql.toBytes(p)));
    }

    static void befriend(Connection c, UUID x, UUID y, long now) throws SQLException {
        UUID[] p = pair(x, y);
        Sql.update(c, "DELETE FROM social_friend_requests WHERE (sender = ? AND receiver = ?) OR (sender = ? AND receiver = ?)",
                Sql.toBytes(x), Sql.toBytes(y), Sql.toBytes(y), Sql.toBytes(x));
        Sql.update(c, "DELETE FROM social_friends WHERE a = ? AND b = ?", Sql.toBytes(p[0]), Sql.toBytes(p[1]));
        Sql.update(c, "INSERT INTO social_friends (a, b, since) VALUES (?, ?, ?)", Sql.toBytes(p[0]), Sql.toBytes(p[1]), now);
    }

    static void unfriend(Connection c, UUID x, UUID y) throws SQLException {
        UUID[] p = pair(x, y);
        Sql.update(c, "DELETE FROM social_friends WHERE a = ? AND b = ?", Sql.toBytes(p[0]), Sql.toBytes(p[1]));
    }

    static void request(Connection c, UUID from, UUID to, long now) throws SQLException {
        Sql.update(c, "DELETE FROM social_friend_requests WHERE sender = ? AND receiver = ?", Sql.toBytes(from), Sql.toBytes(to));
        Sql.update(c, "INSERT INTO social_friend_requests (sender, receiver, created) VALUES (?, ?, ?)", Sql.toBytes(from), Sql.toBytes(to), now);
    }

    static void deleteRequest(Connection c, UUID from, UUID to) throws SQLException {
        Sql.update(c, "DELETE FROM social_friend_requests WHERE sender = ? AND receiver = ?", Sql.toBytes(from), Sql.toBytes(to));
    }

    // ------------------------------------------------------------------ join / quit

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        load(p.getUniqueId()).thenRunAsync(() -> {
            if (!p.isOnline()) {
                return;
            }
            for (UUID f : friends.getOrDefault(p.getUniqueId(), Set.of())) {
                Player online = Bukkit.getPlayer(f);
                if (online != null && notices(online)) {
                    msg.send(online, "friends.joined", Msg.p("player", p.getName()));
                }
            }
            int waiting = incoming.getOrDefault(p.getUniqueId(), Set.of()).size();
            if (waiting > 0) {
                p.sendMessage(msg.component(p, "friends.requests-waiting", Msg.number("count", waiting))
                        .clickEvent(ClickEvent.runCommand("/friends")));
            }
        }, main);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        friends.remove(id);
        incoming.remove(id);
    }

    CompletableFuture<Void> load(UUID p) {
        return db().async(c -> new Set[]{friendsOf(c, p), requestsTo(c, p)}).thenAccept(r -> {
            @SuppressWarnings("unchecked") Set<UUID> f = (Set<UUID>) r[0];
            @SuppressWarnings("unchecked") Set<UUID> in = (Set<UUID>) r[1];
            friends.put(p, f);
            incoming.put(p, in);
        });
    }

    void loadOnline() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            load(p.getUniqueId());
        }
    }

    private boolean notices(Player p) {
        PlayerSettings s = settings;
        return s == null || s.get(p.getUniqueId(), SETTING_NOTICES);
    }

    // ------------------------------------------------------------------ commands

    void register() {
        Arg<String> anyone = Arg.word("player", (sender, raw) -> raw, Suggest.bySender(sender -> {
            List<String> out = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (!(sender instanceof Player me) || !p.getUniqueId().equals(me.getUniqueId())) {
                    out.add(p.getName());
                }
            }
            return out;
        }));
        Arg<String> requester = Arg.word("player", (sender, raw) -> raw, Suggest.bySender(sender ->
                sender instanceof Player me ? names(incoming.getOrDefault(me.getUniqueId(), Set.of())) : List.of()));
        Arg<String> friend = Arg.word("player", (sender, raw) -> raw, Suggest.bySender(sender ->
                sender instanceof Player me ? names(friends.getOrDefault(me.getUniqueId(), Set.of())) : List.of()));
        core.commands().register(CommandSpec.command("friends")
                .description("Your friends: who is online, visit their island, requests")
                .category("Social")
                .permission(PERM)
                .playerOnly()
                .aliases("friend")
                .example("friends add Steve")
                .executes(ctx -> core.menus().open(ctx.player(), new FriendsMenu()))
                .sub(CommandSpec.sub("add").description("Send a friend request (or accept theirs)").permission(PERM).playerOnly()
                        .arg(anyone).example("friends add Steve").executes(ctx -> add(ctx.player(), ctx.get(anyone))))
                .sub(CommandSpec.sub("accept").description("Accept a friend request").permission(PERM).playerOnly()
                        .arg(requester).executes(ctx -> add(ctx.player(), ctx.get(requester))))
                .sub(CommandSpec.sub("deny").description("Turn a friend request down").permission(PERM).playerOnly()
                        .arg(requester).executes(ctx -> deny(ctx.player(), ctx.get(requester))))
                .sub(CommandSpec.sub("remove").description("Remove a friend").permission(PERM).playerOnly()
                        .arg(friend).executes(ctx -> remove(ctx.player(), ctx.get(friend))))
                .sub(CommandSpec.sub("list").description("Your friends in chat").permission(PERM).playerOnly()
                        .executes(ctx -> list(ctx.player()))));
    }

    private List<String> names(Collection<UUID> ids) {
        List<String> out = new ArrayList<>();
        for (UUID id : ids) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                out.add(p.getName());
            }
        }
        return out;
    }

    private record Found(UUID uuid, String name) {
    }

    private CompletableFuture<Optional<Found>> resolve(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return CompletableFuture.completedFuture(Optional.of(new Found(online.getUniqueId(), online.getName())));
        }
        return core.players().offline(name).thenApply(s -> s.map(snap -> new Found(snap.uuid(), snap.name())));
    }

    private CompletableFuture<String> nameOf(UUID uuid) {
        Player online = Bukkit.getPlayer(uuid);
        if (online != null) {
            return CompletableFuture.completedFuture(online.getName());
        }
        return core.players().offline(uuid).thenApply(s -> s.map(PlayerData.Snapshot::name).orElse("?"));
    }

    void add(Player me, String name) {
        resolve(name).thenAcceptAsync(found -> {
            if (found.isEmpty()) {
                msg.send(me, "friends.unknown", Msg.p("player", name));
                return;
            }
            UUID them = found.get().uuid();
            String theirName = found.get().name();
            UUID id = me.getUniqueId();
            db().async(c -> new Object[]{friendsOf(c, id), requestsTo(c, id), requestsFrom(c, id), friendsOf(c, them).size()})
                    .whenCompleteAsync((r, error) -> {
                        if (error != null) {
                            msg.send(me, "friends.error");
                            return;
                        }
                        @SuppressWarnings("unchecked") Set<UUID> mine = (Set<UUID>) r[0];
                        @SuppressWarnings("unchecked") Set<UUID> in = (Set<UUID>) r[1];
                        @SuppressWarnings("unchecked") Set<UUID> out = (Set<UUID>) r[2];
                        int theirs = (int) r[3];
                        AddResult result = add(id, them, mine, theirs, in, out, config.friendsMax);
                        long now = System.currentTimeMillis();
                        switch (result) {
                            case SELF -> msg.send(me, "friends.self");
                            case ALREADY -> msg.send(me, "friends.already", Msg.p("player", theirName));
                            case LIMIT -> msg.send(me, "friends.limit", Msg.number("max", config.friendsMax));
                            case THEIR_LIMIT -> msg.send(me, "friends.their-limit", Msg.p("player", theirName));
                            case ALREADY_REQUESTED -> msg.send(me, "friends.already-requested", Msg.p("player", theirName));
                            case ACCEPTED -> db().transaction(c -> {
                                befriend(c, id, them, now);
                                return null;
                            }).thenRunAsync(() -> {
                                friends.computeIfAbsent(id, k -> ConcurrentHashMap.newKeySet()).add(them);
                                incoming.getOrDefault(id, Set.of()).remove(them);
                                friends.computeIfPresent(them, (k, v) -> {
                                    v.add(id);
                                    return v;
                                });
                                msg.send(me, "friends.now", Msg.p("player", theirName));
                                Player other = Bukkit.getPlayer(them);
                                if (other != null) {
                                    msg.send(other, "friends.now", Msg.p("player", me.getName()));
                                }
                                core.audit().logPlayer(AuditCategory.CHAT, "friend_add", Actor.player(me), them, theirName, null, null, Map.of());
                            }, main);
                            case REQUESTED -> db().transaction(c -> {
                                request(c, id, them, now);
                                return null;
                            }).thenRunAsync(() -> {
                                msg.send(me, "friends.requested", Msg.p("player", theirName));
                                Player other = Bukkit.getPlayer(them);
                                if (other != null) {
                                    incoming.computeIfAbsent(them, k -> ConcurrentHashMap.newKeySet()).add(id);
                                    Component line = msg.component(other, "friends.request", Msg.p("player", me.getName()))
                                            .append(Component.space())
                                            .append(msg.component(other, "friends.accept-button").clickEvent(ClickEvent.runCommand("/friends accept " + me.getName())))
                                            .append(Component.space())
                                            .append(msg.component(other, "friends.deny-button").clickEvent(ClickEvent.runCommand("/friends deny " + me.getName())));
                                    other.sendMessage(line);
                                }
                            }, main);
                            default -> { }
                        }
                    }, main);
        }, main);
    }

    void deny(Player me, String name) {
        resolve(name).thenAcceptAsync(found -> {
            if (found.isEmpty()) {
                msg.send(me, "friends.unknown", Msg.p("player", name));
                return;
            }
            UUID them = found.get().uuid();
            db().transaction(c -> {
                deleteRequest(c, them, me.getUniqueId());
                return null;
            }).thenRunAsync(() -> {
                incoming.getOrDefault(me.getUniqueId(), Set.of()).remove(them);
                msg.send(me, "friends.denied", Msg.p("player", found.get().name()));
            }, main);
        }, main);
    }

    void remove(Player me, String name) {
        resolve(name).thenAcceptAsync(found -> {
            if (found.isEmpty() || !friends.getOrDefault(me.getUniqueId(), Set.of()).contains(found.get().uuid())) {
                msg.send(me, "friends.not-friend", Msg.p("player", name));
                return;
            }
            UUID them = found.get().uuid();
            db().transaction(c -> {
                unfriend(c, me.getUniqueId(), them);
                return null;
            }).thenRunAsync(() -> {
                friends.getOrDefault(me.getUniqueId(), Set.of()).remove(them);
                friends.computeIfPresent(them, (k, v) -> {
                    v.remove(me.getUniqueId());
                    return v;
                });
                msg.send(me, "friends.removed", Msg.p("player", found.get().name()));
            }, main);
        }, main);
    }

    void list(Player me) {
        Set<UUID> set = Set.copyOf(friends.getOrDefault(me.getUniqueId(), Set.of()));
        if (set.isEmpty()) {
            msg.send(me, "friends.none");
            return;
        }
        List<CompletableFuture<String>> names = set.stream().map(this::nameOf).toList();
        CompletableFuture.allOf(names.toArray(CompletableFuture[]::new)).thenRunAsync(() -> {
            List<String> online = new ArrayList<>();
            List<String> offline = new ArrayList<>();
            List<UUID> ids = List.copyOf(set);
            for (int i = 0; i < ids.size(); i++) {
                (Bukkit.getPlayer(ids.get(i)) != null ? online : offline).add(names.get(i).join());
            }
            online.sort(String.CASE_INSENSITIVE_ORDER);
            offline.sort(String.CASE_INSENSITIVE_ORDER);
            msg.send(me, "friends.list", Msg.number("count", set.size()), Msg.p("online", online.isEmpty() ? "-" : String.join(", ", online)),
                    Msg.p("offline", offline.isEmpty() ? "-" : String.join(", ", offline)));
        }, main);
    }

    // ------------------------------------------------------------------ menu

    /** {@code /friends}: requests first (click = accept, right-click = deny), then friends, online ones first (click = visit). */
    private final class FriendsMenu extends Menu {

        @Override
        public int rows() {
            return 6;
        }

        @Override
        public Component title() {
            return service.title(viewer, msg.component(viewer, "friends.menu.title"));
        }

        @Override
        public String messageModule() {
            return SocialModule.ID;
        }

        @Override
        protected void build() {
            navBar();
            UUID me = viewer.getUniqueId();
            List<UUID> requests = List.copyOf(incoming.getOrDefault(me, Set.of()));
            List<UUID> list = new ArrayList<>(friends.getOrDefault(me, Set.of()));
            list.sort((x, y) -> Boolean.compare(Bukkit.getPlayer(y) != null, Bukkit.getPlayer(x) != null));
            int slot = 10;
            for (UUID r : requests) {
                if (slot > 43) {
                    break;
                }
                int at = slot;
                nameOf(r).thenAcceptAsync(name -> {
                    if (isOpen()) {
                        set(at, head(r, msg.component(viewer, "friends.menu.request.name", Msg.p("player", name)),
                                List.of(msg.component(viewer, "friends.menu.request.subtitle"), Component.empty(),
                                        msg.component(viewer, "friends.menu.request.accept"), msg.component(viewer, "friends.menu.request.deny"))),
                                click -> {
                                    click.player().closeInventory();
                                    if (click.isRight()) {
                                        deny(click.player(), name);
                                    } else {
                                        add(click.player(), name);
                                    }
                                });
                    }
                }, main);
                slot = next(slot);
            }
            for (UUID f : list) {
                if (slot > 43) {
                    break;
                }
                int at = slot;
                boolean online = Bukkit.getPlayer(f) != null;
                nameOf(f).thenAcceptAsync(name -> {
                    if (!isOpen()) {
                        return;
                    }
                    List<Component> lore = List.of(msg.component(viewer, online ? "friends.menu.online" : "friends.menu.offline"),
                            Component.empty(), msg.component(viewer, "friends.menu.visit"), msg.component(viewer, "friends.menu.remove"));
                    set(at, head(f, msg.component(viewer, online ? "friends.menu.name-online" : "friends.menu.name-offline", Msg.p("player", name)), lore),
                            click -> {
                                click.player().closeInventory();
                                if (click.isRight() && click.isShift()) {
                                    remove(click.player(), name);
                                } else {
                                    click.player().performCommand("is visit " + name);
                                }
                            });
                }, main);
                slot = next(slot);
            }
            if (requests.isEmpty() && list.isEmpty()) {
                set(22, service.item(Material.PLAYER_HEAD, msg.component(viewer, "friends.menu.empty.name"),
                        List.of(msg.component(viewer, "friends.menu.empty.lore"))));
            }
        }

        private int next(int slot) {
            int n = slot + 1;
            return n % 9 == 8 ? n + 2 : n;
        }

        private ItemStack head(UUID id, Component name, List<Component> lore) {
            ItemStack item = service.item(Material.PLAYER_HEAD, name, lore);
            if (item.getItemMeta() instanceof SkullMeta meta) {
                meta.setOwningPlayer(Bukkit.getOfflinePlayer(id));
                item.setItemMeta(meta);
            }
            return item;
        }
    }

    static CommandFailure fail(String key) {
        return new CommandFailure(SocialModule.ID, key);
    }
}
