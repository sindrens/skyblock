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
    /** Ids ({@code KIND:id}) only their module grants and takes (spec 14.11: the clean-streak title). */
    private final Set<String> automatic = ConcurrentHashMap.newKeySet();
    private volatile String autoTitle = "";
    private volatile String tagFormat = "";
    private volatile boolean tagStripBrackets = true;

    CosmeticsService(Core core, CosmeticRegistry registry, ModuleMessages msg) {
        this.core = core;
        this.registry = registry;
        this.msg = msg;
    }

    /** Title granted automatically to every player on join (e.g. {@code newcomer}); empty = none. */
    void autoTitle(String id) {
        this.autoTitle = id == null ? "" : id;
    }

    /** How tags are shown ({@code cosmetics.yml → tag.*}): {@code format} wraps the tag, e.g. muted ( ). */
    void tagStyle(String format, boolean stripBrackets) {
        this.tagFormat = format == null ? "" : format;
        this.tagStripBrackets = stripBrackets;
    }

    /** The cosmetic's display as raw MiniMessage, tags wrapped in the tag format (for item lore built from text). */
    String displayRaw(Cosmetic cosmetic, java.util.function.UnaryOperator<String> toMini) {
        String inner = toMini.apply(innerRaw(cosmetic));
        return cosmetic.kind() == Kind.TAG ? TagStyle.wrapRaw(tagFormat, inner) : inner;
    }

    private String innerRaw(Cosmetic cosmetic) {
        String raw = cosmetic.display();
        return cosmetic.kind() == Kind.TAG && tagStripBrackets ? TagStyle.unbracket(raw) : raw;
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

    @Override
    public CompletableFuture<Boolean> grant(UUID player, Kind kind, String id, String reason, boolean notify) {
        return grant(player, kind, id, reason, Actor.system("cosmetics"), notify);
    }

    @Override
    public boolean defineTitle(String id, String display, net.skybornmc.skyblock.common.item.Rarity rarity, String source, boolean secret) {
        if (!Cosmetic.validId(id) || registry.get(Kind.TITLE, id).isPresent()) {
            return false;
        }
        try {
            registry.put(new Cosmetic(Kind.TITLE, id, display, rarity == null ? net.skybornmc.skyblock.common.item.Rarity.COMMON : rarity,
                    source, secret, true));
        } catch (java.io.IOException e) {
            core.plugin().getLogger().log(Level.WARNING, "Could not add title '" + id + "' to " + CosmeticRegistry.FILE, e);
            return false;
        }
        core.audit().log(AuditCategory.CONFIG, "title_define", Actor.system("cosmetics"), id, null, null, null,
                Map.of("display", String.valueOf(display), "source", String.valueOf(source)));
        return true;
    }

    /** {@link #grant} with the actor for the audit log (staff command, item redeem). */
    CompletableFuture<Boolean> grant(UUID player, Kind kind, String id, String reason, Actor actor) {
        return grant(player, kind, id, reason, actor, true);
    }

    private CompletableFuture<Boolean> grant(UUID player, Kind kind, String id, String reason, Actor actor, boolean notify) {
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
                Player online = notify ? Bukkit.getPlayer(player) : null;
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
    public void markAutomatic(Kind kind, String id) {
        if (kind != null && id != null) {
            automatic.add(kind.name() + ":" + id.toLowerCase(Locale.ROOT));
        }
    }

    @Override
    public boolean automatic(Kind kind, String id) {
        return kind != null && id != null && automatic.contains(kind.name() + ":" + id.toLowerCase(Locale.ROOT));
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

    @Override
    public Component display(Audience viewer, Kind kind, String id) {
        return registry.get(kind, id).map(c -> display(viewer, c)).orElseGet(() -> Component.text(String.valueOf(id)));
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
        String raw = innerRaw(cosmetic);
        Component inner = LegacyColors.hasCodes(raw) && raw.indexOf('<') < 0
                ? LegacyColors.toComponent(raw)
                : core.messages().parse(viewer, raw);
        String format = tagFormat;
        if (cosmetic.kind() != Kind.TAG || format.isBlank() || !format.contains(TagStyle.PLACEHOLDER)) {
            return inner;
        }
        return core.messages().parse(viewer, format, net.skybornmc.skyblock.common.text.Msg.c("tag", inner));
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
