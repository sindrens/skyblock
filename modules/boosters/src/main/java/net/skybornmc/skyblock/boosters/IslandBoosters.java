package net.skybornmc.skyblock.boosters;

import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.booster.BoosterType;
import net.skybornmc.skyblock.common.db.Sql;
import net.skybornmc.skyblock.common.island.IslandService;
import net.skybornmc.skyblock.common.item.BoundItems;
import net.skybornmc.skyblock.common.item.ItemDefinition;
import net.skybornmc.skyblock.common.item.ItemKeys;
import net.skybornmc.skyblock.common.item.ItemRegistry;
import net.skybornmc.skyblock.common.item.ItemType;
import net.skybornmc.skyblock.common.item.LoreBuilder;
import net.skybornmc.skyblock.common.item.Rarity;
import net.skybornmc.skyblock.common.text.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Island boosters (owner 7 Nov 2026, plans/STORE_FULL_PLAN.md 3): a booster for the whole island, every member,
 * everywhere. One running per island and type: the same strength adds time (up to {@code island.max-minutes}), another
 * strength is refused while one runs. They run in real time. Items {@code island_booster_<type>} (right-click as a member
 * of an island); KOTH starts one for the winning island ({@link #start}).
 */
final class IslandBoosters {

    static final String PREFIX = "island_booster_";

    /** What an island booster item holds. */
    record Spec(BoosterType type, int percent, int minutes, UUID itemUuid) {
    }

    /** One running island booster. */
    record Run(int percent, long endsAt) {
    }

    private final BoostersModule m;
    private final Map<UUID, Map<BoosterType, Run>> runs = new ConcurrentHashMap<>();
    /** Player → (island id or null, checked at). Cheap lookups for the bonus source (per block). */
    private final Map<UUID, Object[]> islandCache = new ConcurrentHashMap<>();
    private final List<String> registered = new ArrayList<>();

    IslandBoosters(BoostersModule module) {
        this.m = module;
    }

    // ------------------------------------------------------------------ items

    void register(Material material) {
        for (BoosterType type : BoosterType.values()) {
            String id = PREFIX + type.id();
            if (m.core.items().get(id).isPresent()) {
                continue;
            }
            m.core.items().register(ItemDefinition.builder(id, material, ItemType.CONSUMABLE, Rarity.EPIC)
                    .name(m.raw("item.island.name").replace("{type}", m.typeName(type)).replace("{percent}", "?"))
                    .description(m.raw("item.island.description"))
                    .action(m.raw("item.action-input"), m.raw("item.action-text"))
                    .unique()
                    .glint()
                    .renderer(new ItemDefinition.Renderer() {
                        @Override
                        public String name(ItemStack stack, ItemDefinition definition) {
                            return read(stack).map(s -> "<rarity:epic>" + LoreBuilder.fill(m.raw("item.island.name"),
                                    Map.of("type", m.typeName(type), "percent", String.valueOf(s.percent())))).orElse(definition.name());
                        }

                        @Override
                        public void lore(ItemStack stack, ItemDefinition definition, LoreBuilder lore) {
                            read(stack).ifPresent(s -> {
                                Map<String, String> tokens = Map.of("type", m.typeName(type), "percent", String.valueOf(s.percent()),
                                        "time", BoosterRules.time(s.minutes() * BoosterRules.MINUTE));
                                for (String line : m.rawList("item.island.lore")) {
                                    lore.line(LoreBuilder.fill(line, tokens));
                                }
                                if (BoundItems.isBound(stack)) {
                                    lore.line(m.raw("item.bound"));
                                }
                            });
                        }
                    })
                    .build());
            registered.add(id);
        }
    }

    void unregister() {
        registered.forEach(m.core.items()::unregister);
        registered.clear();
    }

    static Optional<Spec> read(ItemStack stack) {
        Optional<String> id = ItemRegistry.idOf(stack);
        if (id.isEmpty() || !id.get().startsWith(PREFIX)) {
            return Optional.empty();
        }
        Optional<BoosterType> type = BoosterType.byId(id.get().substring(PREFIX.length()));
        Optional<UUID> uuid = ItemRegistry.uuidOf(stack);
        var pdc = stack.getPersistentDataContainer();
        Integer percent = pdc.get(BoosterItems.PERCENT, PersistentDataType.INTEGER);
        Integer minutes = pdc.get(BoosterItems.MINUTES, PersistentDataType.INTEGER);
        if (type.isEmpty() || uuid.isEmpty() || percent == null || minutes == null) {
            return Optional.empty();
        }
        return Optional.of(new Spec(type.get(), percent, minutes, uuid.get()));
    }

    static boolean isIslandBooster(ItemStack stack) {
        return ItemRegistry.idOf(stack).filter(id -> id.startsWith(PREFIX)).isPresent();
    }

    ItemStack create(BoosterType type, int percent, int minutes, UUID profile, boolean bind, String origin) {
        ItemStack stack = m.core.items().create(PREFIX + type.id(), 1, origin, pdc -> {
            pdc.set(BoosterItems.PERCENT, PersistentDataType.INTEGER, percent);
            pdc.set(BoosterItems.MINUTES, PersistentDataType.INTEGER, minutes);
        });
        if (bind && profile != null) {
            BoundItems.mark(stack, profile);
            m.core.items().render(stack);
        }
        return stack;
    }

    boolean valid(int percent, int minutes) {
        return percent >= m.settings.personalMinPercent && percent <= m.settings.islandMaxPercent
                && minutes >= 1 && minutes <= m.settings.islandMaxItemMinutes;
    }

    // ------------------------------------------------------------------ state

    /** Start: the running island boosters. */
    void load() {
        long now = System.currentTimeMillis();
        m.core.database().async(c -> Sql.query(c, "SELECT island_id, booster_type, bonus_percent, ends_at FROM booster_island WHERE ends_at > ?",
                rs -> new Object[]{Sql.uuid(rs, "island_id"), rs.getString("booster_type"), rs.getInt("bonus_percent"), rs.getLong("ends_at")}, now))
                .thenAcceptAsync(rows -> {
                    runs.clear();
                    for (Object[] r : rows) {
                        BoosterType.byId((String) r[1]).ifPresent(t -> runs.computeIfAbsent((UUID) r[0], k -> new EnumMap<>(BoosterType.class))
                                .put(t, new Run((int) r[2], (long) r[3])));
                    }
                }, m.main());
    }

    /** The island of an online player (cached 10 s), or null. */
    UUID islandOf(Player p) {
        Object[] c = islandCache.get(p.getUniqueId());
        long now = System.currentTimeMillis();
        if (c != null && now - (long) c[1] < 10_000L) {
            return (UUID) c[0];
        }
        UUID id = m.core.services().optional(IslandService.class).flatMap(s -> s.islandOf(p.getUniqueId()))
                .map(i -> i.id()).orElse(null);
        islandCache.put(p.getUniqueId(), new Object[]{id, now});
        return id;
    }

    void forget(Player p) {
        islandCache.remove(p.getUniqueId());
    }

    /** The running booster of an island and type, or null. */
    Run run(UUID island, BoosterType type) {
        if (island == null) {
            return null;
        }
        Map<BoosterType, Run> map = runs.get(island);
        Run r = map == null ? null : map.get(type);
        return r == null || r.endsAt() <= System.currentTimeMillis() ? null : r;
    }

    /** Percent the player's island booster of this type gives now (the bonus source). */
    int percent(Player p, BoosterType type) {
        if (!m.settings.islandEnabled) {
            return 0;
        }
        Run r = run(islandOf(p), type);
        return r == null ? 0 : r.percent();
    }

    /** Pure: the end after adding {@code millis} (from now when nothing runs), or -1 when refused (other strength / too long). */
    static long addTo(Run current, int percent, long millis, long now, long maxMillis) {
        if (current == null || current.endsAt() <= now) {
            return now + Math.min(millis, maxMillis);
        }
        if (current.percent() != percent) {
            return -1;
        }
        long end = current.endsAt() + millis;
        return end - now > maxMillis ? -2 : end;
    }

    // ------------------------------------------------------------------ starting

    /** Right-click / menu: starts an island booster item on the player's island. Main thread. */
    void use(Player p, ItemStack stack) {
        Optional<Spec> spec = read(stack);
        if (spec.isEmpty() || !valid(spec.get().percent(), spec.get().minutes())) {
            m.msg.send(p, "invalid");
            return;
        }
        if (!m.settings.islandEnabled) {
            m.msg.send(p, "island.disabled");
            return;
        }
        Optional<String> problem = m.problem(p);
        if (problem.isPresent()) {
            m.msg.send(p, problem.get());
            return;
        }
        UUID profile = m.profiles().profileKey(p);
        if (BoundItems.isBound(stack) && !BoundItems.mayReceive(stack, profile)) {
            m.msg.send(p, "not-yours");
            return;
        }
        UUID island = islandOf(p);
        if (island == null) {
            m.msg.send(p, "island.no-island");
            return;
        }
        Spec s = spec.get();
        long now = System.currentTimeMillis();
        long end = addTo(run(island, s.type()), s.percent(), s.minutes() * BoosterRules.MINUTE, now, m.settings.islandMaxMinutes * BoosterRules.MINUTE);
        if (end < 0) {
            m.msg.send(p, end == -1 ? "island.other-strength" : "island.full", Msg.p("type", m.typeName(s.type())),
                    Msg.p("max", BoosterRules.time(m.settings.islandMaxMinutes * BoosterRules.MINUTE)));
            return;
        }
        Optional<ItemStack> taken = m.take(p, stack, s.itemUuid());
        if (taken.isEmpty()) {
            m.msg.send(p, "moved");
            return;
        }
        save(island, s.type(), s.percent(), end, profile.toString(), c -> {
            if (Sql.queryOne(c, "SELECT 1 FROM booster_used_items WHERE item_uuid = ?", rs -> 1, s.itemUuid()).isPresent()) {
                return false;
            }
            Sql.update(c, "INSERT INTO booster_used_items (item_uuid, profile_key, item_kind, used_at) VALUES (?, ?, ?, ?)",
                    s.itemUuid(), profile, "island", now);
            return true;
        }).whenCompleteAsync((ok, error) -> {
            if (error != null) {
                m.core.plugin().getLogger().log(java.util.logging.Level.WARNING, "Boosters: island booster could not start", error);
                m.giveBack(p.isOnline() ? p : null, profile, taken.get());
                m.msg.send(p, "error");
                return;
            }
            if (!ok) {
                m.msg.send(p, "already-used");
                return;
            }
            Map<String, Object> d = new LinkedHashMap<>();
            d.put("type", s.type().id());
            d.put("percent", s.percent());
            d.put("minutes", s.minutes());
            d.put("island", island.toString());
            m.audit("island_booster_start", Actor.player(p), p.getUniqueId(), p.getName(), (long) s.percent(), d);
            announce(island, "island.started", p.getName(), s.type(), s.percent(), end);
            m.sound(p, m.settings.startSound);
            m.refreshMenu(p);
        }, m.main());
    }

    /**
     * Starts (or extends) an island booster without an item (KOTH prize). Main thread; completes true when it runs now.
     */
    CompletableFuture<Boolean> start(UUID island, BoosterType type, int percent, int minutes, String source) {
        long now = System.currentTimeMillis();
        long end = addTo(run(island, type), percent, minutes * BoosterRules.MINUTE, now, m.settings.islandMaxMinutes * BoosterRules.MINUTE);
        if (end < 0) {
            return CompletableFuture.completedFuture(false);
        }
        return save(island, type, percent, end, source, c -> true).thenApplyAsync(ok -> {
            if (ok) {
                announce(island, "island.started", source, type, percent, end);
            }
            return ok;
        }, m.main());
    }

    private CompletableFuture<Boolean> save(UUID island, BoosterType type, int percent, long end, String by, Sql.SqlFunction<Boolean> guard) {
        long now = System.currentTimeMillis();
        return m.core.database().transaction(c -> {
            if (!guard.apply(c)) {
                return false;
            }
            upsert(c, island, type, percent, end, by, now);
            return true;
        }).thenApplyAsync(ok -> {
            if (ok) {
                runs.computeIfAbsent(island, k -> new EnumMap<>(BoosterType.class)).put(type, new Run(percent, end));
            }
            return ok;
        }, m.main());
    }

    static void upsert(Connection c, UUID island, BoosterType type, int percent, long end, String by, long now) throws SQLException {
        if (Sql.update(c, "UPDATE booster_island SET bonus_percent = ?, ends_at = ?, started_by = ?, updated_at = ? WHERE island_id = ? AND booster_type = ?",
                percent, end, by, now, island, type.id()) == 0) {
            Sql.update(c, "INSERT INTO booster_island (island_id, booster_type, bonus_percent, ends_at, started_by, updated_at) VALUES (?, ?, ?, ?, ?, ?)",
                    island, type.id(), percent, end, by, now);
        }
    }

    /** Tells every online member of the island. */
    private void announce(UUID island, String key, String who, BoosterType type, int percent, long end) {
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (island.equals(islandOf(online))) {
                m.msg.send(online, key, Msg.p("player", who), Msg.p("type", m.typeName(type)), Msg.number("percent", percent),
                        Msg.p("time", BoosterRules.time(Math.max(0, end - System.currentTimeMillis()))));
                m.refreshMenu(online);
            }
        }
    }

    /** Every 20 s: drops ended boosters from memory (the rows stay until replaced). */
    void tick() {
        long now = System.currentTimeMillis();
        runs.values().forEach(map -> map.values().removeIf(r -> r.endsAt() <= now));
        runs.values().removeIf(Map::isEmpty);
    }
}
