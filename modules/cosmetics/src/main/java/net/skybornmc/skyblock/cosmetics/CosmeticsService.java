package net.skybornmc.skyblock.cosmetics;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.skybornmc.skyblock.common.Core;
import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.audit.AuditCategory;
import net.skybornmc.skyblock.common.cosmetics.Cosmetics;
import net.skybornmc.skyblock.common.db.MainThread;
import net.skybornmc.skyblock.common.db.Sql;
import net.skybornmc.skyblock.common.player.PlayerData;
import net.skybornmc.skyblock.common.text.LegacyColors;
import net.skybornmc.skyblock.common.text.ModuleMessages;
import net.skybornmc.skyblock.common.text.Msg;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Owned titles/tags (database, cached while online) and the equipped one of each (player settings
 * {@code cosmetic.title} / {@code cosmetic.tag}). Implements the {@link Cosmetics} service other modules use.
 */
public final class CosmeticsService implements Cosmetics, Listener {

    static final String EQUIPPED_TITLE = "cosmetic.title";
    static final String EQUIPPED_TAG = "cosmetic.tag";

    /** Owned ids per kind of one online player. */
    record Owned(Set<String> titles, Set<String> tags) {
        Set<String> of(Kind kind) {
            return kind == Kind.TITLE ? titles : tags;
        }
    }

    private final Core core;
    private final CosmeticRegistry registry;
    private final ModuleMessages msg;
    private final Map<UUID, Owned> owned = new ConcurrentHashMap<>();
    private volatile String autoTitle = "";

    CosmeticsService(Core core, CosmeticRegistry registry, ModuleMessages msg) {
        this.core = core;
        this.registry = registry;
        this.msg = msg;
    }

    /** Title granted automatically to every player on join (e.g. {@code newcomer}); empty = none. */
    void autoTitle(String id) {
        this.autoTitle = id == null ? "" : id;
    }

    // ------------------------------------------------------------------ loading

    void loadOnline() {
        Bukkit.getOnlinePlayers().forEach(this::load);
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onJoin(PlayerJoinEvent event) {
        load(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        owned.remove(event.getPlayer().getUniqueId());
    }

    private void load(Player player) {
        UUID uuid = player.getUniqueId();
        core.database().async(c -> Sql.query(c, "SELECT kind, cosmetic_id FROM cosmetics_owned WHERE uuid = ?",
                        rs -> new String[]{rs.getString("kind"), rs.getString("cosmetic_id")}, Sql.toBytes(uuid)))
                .thenAcceptAsync(rows -> {
                    if (!player.isOnline()) {
                        return;
                    }
                    Set<String> titles = ConcurrentHashMap.newKeySet();
                    Set<String> tags = ConcurrentHashMap.newKeySet();
                    for (String[] row : rows) {
                        (Kind.TITLE.name().equals(row[0]) ? titles : tags).add(row[1]);
                    }
                    owned.put(uuid, new Owned(titles, tags));
                    String auto = autoTitle;
                    if (!auto.isBlank() && registry.get(Kind.TITLE, auto).isPresent() && !titles.contains(auto)) {
                        grant(uuid, Kind.TITLE, auto, "auto:join");
                    }
                }, new MainThread(core.plugin()))
                .exceptionally(error -> {
                    core.plugin().getLogger().log(Level.WARNING, "Could not load titles/tags of " + player.getName(), error);
                    return null;
                });
    }

    // ------------------------------------------------------------------ Cosmetics API

    @Override
    public CompletableFuture<Boolean> grant(UUID player, Kind kind, String id, String reason) {
        return grant(player, kind, id, reason, Actor.system("cosmetics"));
    }

    /** {@link #grant} with the actor for the audit log (staff command, item redeem). */
    CompletableFuture<Boolean> grant(UUID player, Kind kind, String id, String reason, Actor actor) {
        Optional<Cosmetic> cosmetic = registry.get(kind, id);
        if (cosmetic.isEmpty()) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Unknown " + kind.name().toLowerCase(Locale.ROOT) + " '" + id + "'"));
        }
        String key = cosmetic.get().id();
        return core.database().async(c -> {
            boolean exists = Sql.queryOne(c, "SELECT 1 FROM cosmetics_owned WHERE uuid = ? AND kind = ? AND cosmetic_id = ?",
                    rs -> Boolean.TRUE, Sql.toBytes(player), kind.name(), key).isPresent();
            if (exists) {
                return false;
            }
            try {
                Sql.update(c, "INSERT INTO cosmetics_owned (uuid, kind, cosmetic_id, source, granted_by, granted_at) VALUES (?, ?, ?, ?, ?, ?)",
                        Sql.toBytes(player), kind.name(), key, clip(reason, 128), clip(actor.name(), 64), System.currentTimeMillis());
            } catch (SQLException e) {
                if (e.getErrorCode() == 1062 || "23505".equals(e.getSQLState())) {
                    return false; // granted at the same moment elsewhere
                }
                throw e;
            }
            return true;
        }).thenApplyAsync(added -> {
            if (added) {
                Owned o = owned.get(player);
                if (o != null) {
                    o.of(kind).add(key);
                }
                core.audit().logPlayer(actor.isPlayer() || actor.name().equals("CONSOLE") ? AuditCategory.STAFF : AuditCategory.ITEM,
                        kind == Kind.TITLE ? "title_grant" : "tag_grant", actor, player, null, null, null,
                        Map.of("id", key, "reason", reason == null ? "" : reason));
                Player online = Bukkit.getPlayer(player);
                if (online != null) {
                    msg.send(online, kind == Kind.TITLE ? "unlocked.title" : "unlocked.tag", Msg.c("display", display(online, cosmetic.get())));
                    core.sounds().play(online, "unlock");
                }
            }
            return added;
        }, new MainThread(core.plugin()));
    }

    @Override
    public CompletableFuture<Boolean> revoke(UUID player, Kind kind, String id, String reason) {
        return revoke(player, kind, id, reason, Actor.system("cosmetics"));
    }

    CompletableFuture<Boolean> revoke(UUID player, Kind kind, String id, String reason, Actor actor) {
        String key = id.toLowerCase(Locale.ROOT);
        return core.database().async(c -> Sql.update(c, "DELETE FROM cosmetics_owned WHERE uuid = ? AND kind = ? AND cosmetic_id = ?",
                Sql.toBytes(player), kind.name(), key) > 0).thenApplyAsync(removed -> {
            if (removed) {
                Owned o = owned.get(player);
                if (o != null) {
                    o.of(kind).remove(key);
                }
                if (key.equals(equippedId(player, kind))) {
                    equip(player, kind, null);
                }
                core.audit().logPlayer(AuditCategory.STAFF, kind == Kind.TITLE ? "title_take" : "tag_take", actor, player, null,
                        null, null, Map.of("id", key, "reason", reason == null ? "" : reason));
            }
            return removed;
        }, new MainThread(core.plugin()));
    }

    @Override
    public boolean owns(UUID player, Kind kind, String id) {
        Owned o = owned.get(player);
        return o != null && id != null && o.of(kind).contains(id.toLowerCase(Locale.ROOT));
    }

    @Override
    public Set<String> ids(Kind kind) {
        return registry.ids(kind);
    }

    /** Owned ids of an online player (empty while loading). */
    Set<String> owned(UUID player, Kind kind) {
        Owned o = owned.get(player);
        return o == null ? Set.of() : new HashSet<>(o.of(kind));
    }

    boolean loaded(UUID player) {
        return owned.containsKey(player);
    }

    // ------------------------------------------------------------------ equipping

    /** The equipped id (only if still owned and still registered). */
    String equippedId(UUID player, Kind kind) {
        String id = core.players().get(player).map(d -> d.setting(kind == Kind.TITLE ? EQUIPPED_TITLE : EQUIPPED_TAG, null)).orElse(null);
        if (id == null || !owns(player, kind, id) || registry.get(kind, id).isEmpty()) {
            return null;
        }
        return id;
    }

    Optional<Cosmetic> equipped(UUID player, Kind kind) {
        return registry.get(kind, equippedId(player, kind));
    }

    /** Equips ({@code id}) or unequips ({@code null}); must be owned. Returns false if not possible. */
    boolean equip(UUID player, Kind kind, String id) {
        Optional<PlayerData> data = core.players().get(player);
        if (data.isEmpty() || (id != null && !owns(player, kind, id))) {
            return false;
        }
        data.get().setSetting(kind == Kind.TITLE ? EQUIPPED_TITLE : EQUIPPED_TAG, id);
        return true;
    }

    /** How a title/tag looks: {@code &} codes or MiniMessage with style tags. */
    Component display(Audience viewer, Cosmetic cosmetic) {
        String raw = cosmetic.display();
        if (LegacyColors.hasCodes(raw) && raw.indexOf('<') < 0) {
            return LegacyColors.toComponent(raw);
        }
        return core.messages().parse(viewer, raw);
    }

    /** Placeholder value: the equipped one's display, or empty. */
    Component placeholder(Player player, Kind kind) {
        if (player == null) {
            return Component.empty();
        }
        return equipped(player.getUniqueId(), kind).map(c -> display(player, c)).orElse(Component.empty());
    }

    List<Cosmetic> registered(Kind kind) {
        return registry.all(kind);
    }

    private static String clip(String text, int max) {
        if (text == null) {
            return null;
        }
        return text.length() <= max ? text : text.substring(0, max);
    }
}
