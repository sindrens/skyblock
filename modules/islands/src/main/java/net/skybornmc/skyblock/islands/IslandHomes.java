package net.skybornmc.skyblock.islands;

import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.audit.AuditCategory;
import net.skybornmc.skyblock.common.island.IslandRemovedEvent;
import net.skybornmc.skyblock.common.text.Msg;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Shared island homes (PHASE-5 M5.5 Homestead, owner P5-21 / R3-17; no personal homes, answer 247). The island home stays
 * {@code /is home}; named homes are {@code /is home <name>} for every stored member, set with {@code /is sethome <name>} and
 * removed with {@code /is delhome <name>} by members with the island permission "homes". The island may have one named
 * home per Homestead rank ({@code IslandService#extraIslandHomes}). If ranks are ever taken away, existing homes keep
 * working; only new ones are refused. All homes of the island live in memory (loaded on start), written right away.
 */
final class IslandHomes implements Listener {

    private final IslandsModule module;
    private final IslandManager islands;
    private final IslandHomeStore store;
    private final Map<UUID, Map<String, Island.Home>> homes = new ConcurrentHashMap<>();
    private boolean loaded;

    IslandHomes(IslandsModule module, IslandManager islands, IslandHomeStore store) {
        this.module = module;
        this.islands = islands;
        this.store = store;
    }

    void load() {
        store.loadAll().whenComplete((all, error) -> module.core().scheduler().main("islands-homes-load", () -> {
            if (error != null) {
                module.core().plugin().getLogger().log(Level.WARNING, "Islands: loading the shared island homes failed; retrying in 30 s", error);
                module.core().scheduler().later("islands-homes-load-retry", 30 * 20L, this::load);
                return;
            }
            all.forEach((id, map) -> homes.put(id, new LinkedHashMap<>(map)));
            loaded = true;
        }));
    }

    /** Named homes of the island (sorted by name; never null). */
    List<String> names(UUID islandId) {
        Map<String, Island.Home> map = islandId == null ? null : homes.get(islandId);
        return map == null ? List.of() : map.keySet().stream().sorted().toList();
    }

    /** Tab completion: the named homes of the sender's own island. */
    List<String> ownNames(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            return List.of();
        }
        return islands.islandOfPlayer(islands.key(player)).map(i -> names(i.id())).orElse(List.of());
    }

    /** Tab completion of {@code /is sethome}: existing names (to move one) and, with a free slot, a suggested new name. */
    List<String> setNames(CommandSender sender) {
        List<String> out = new ArrayList<>(ownNames(sender));
        if (sender instanceof Player player) {
            islands.islandOfPlayer(islands.key(player)).ifPresent(i -> {
                if (out.size() < islands.extraIslandHomes(i.id())) {
                    out.add(module.raw("homes.suggest-name"));
                }
            });
        }
        return out;
    }

    // ------------------------------------------------------------------ commands

    /** {@code /is home [name]}: the island home without a name, else the named shared home. */
    void go(Player player, String typed) {
        if (typed == null || typed.isBlank()) {
            islands.teleportHome(player);
            return;
        }
        Optional<Island> own = islands.islandOfPlayer(islands.key(player));
        if (own.isEmpty()) {
            islands.fail(player, "no-island");
            return;
        }
        Island island = own.get();
        String name = IslandHomeRules.normalize(typed);
        Island.Home home = name == null ? null : homes.getOrDefault(island.id(), Map.of()).get(name);
        if (home == null) {
            unknown(player, island, typed);
            return;
        }
        Location target = IslandManager.location(island, home);
        if (target.getWorld() == null) {
            module.msg().send(player, "error.world", Msg.p("world", island.world()));
            return;
        }
        target.getWorld().getChunkAtAsync(target).thenAccept(chunk -> module.core().scheduler().main("islands-homes-go", () -> {
            if (!player.isOnline()) {
                return;
            }
            Location safe = islands.safeHome(player, island, target);
            islands.teleport(player, safe, false, () -> module.msg().actionBar(player, "homes.arrived", Msg.p("home", name)));
        }));
    }

    private void unknown(Player player, Island island, String typed) {
        List<String> names = names(island.id());
        if (names.isEmpty()) {
            islands.fail(player, "homes.none", Msg.p("home", typed));
        } else {
            islands.fail(player, "homes.unknown", Msg.p("home", typed), Msg.p("homes", String.join(", ", names)));
        }
    }

    /** {@code /is sethome <name>}: a named shared home where the player stands (Homes permission, a free Homestead slot). */
    void set(Player player, String typed) {
        Optional<Island> own = islands.islandOfPlayer(islands.key(player));
        if (own.isEmpty()) {
            islands.fail(player, "no-island");
            return;
        }
        Island island = own.get();
        if (!islands.roleAllows(island, islands.key(player), IslandPerm.HOMES)) {
            islands.fail(player, "homes.not-allowed");
            return;
        }
        if (!loaded) {
            islands.fail(player, "homes.loading");
            return;
        }
        String name = IslandHomeRules.normalize(typed);
        int allowed = islands.extraIslandHomes(island.id());
        Map<String, Island.Home> map = homes.getOrDefault(island.id(), Map.of());
        switch (IslandHomeRules.set(map.keySet(), name, allowed)) {
            case BAD_NAME -> {
                islands.fail(player, "homes.bad-name");
                return;
            }
            case LIMIT -> {
                islands.fail(player, allowed <= 0 ? "homes.need-homestead" : "homes.limit", Msg.p("max", allowed),
                        Msg.p("total", IslandHomeRules.total(allowed)));
                return;
            }
            default -> {
            }
        }
        Location at = player.getLocation();
        String where = islands.homeSpotProblem(island, at);
        if (where != null) {
            islands.fail(player, where, Msg.p("min", module.config().minBuildY), Msg.p("max", module.config().maxBuildY));
            return;
        }
        Island.Home home = new Island.Home(at.getX(), at.getY(), at.getZ(), at.getYaw(), at.getPitch());
        UUID by = islands.key(player);
        store.save(island.id(), name, home, by, allowed, System.currentTimeMillis()).whenComplete((saved, error) ->
                module.core().scheduler().main("islands-homes-set", () -> {
                    if (error != null) {
                        islands.databaseError(player, "sethome " + name, error);
                        return;
                    }
                    if (saved == IslandHomeStore.Saved.LIMIT) {
                        islands.fail(player, "homes.limit", Msg.p("max", allowed), Msg.p("total", IslandHomeRules.total(allowed)));
                        return;
                    }
                    homes.computeIfAbsent(island.id(), k -> new ConcurrentHashMap<>()).put(name, home);
                    module.core().audit().log(AuditCategory.ISLAND, "island_sethome_named", Actor.player(player), island.id().toString(),
                            islands.displayName(island), null, at, Map.of("home", name, "moved", saved == IslandHomeStore.Saved.MOVED));
                    int used = names(island.id()).size();
                    module.msg().send(player, saved == IslandHomeStore.Saved.MOVED ? "homes.moved" : "homes.set", Msg.p("home", name),
                            Msg.p("used", IslandHomeRules.total(used)), Msg.p("total", IslandHomeRules.total(allowed)));
                    module.core().sounds().play(player, "success");
                }));
    }

    /** {@code /is delhome <name>}: removes a named shared home (Homes permission). The island home can't be removed. */
    void delete(Player player, String typed) {
        Optional<Island> own = islands.islandOfPlayer(islands.key(player));
        if (own.isEmpty()) {
            islands.fail(player, "no-island");
            return;
        }
        Island island = own.get();
        if (!islands.roleAllows(island, islands.key(player), IslandPerm.HOMES)) {
            islands.fail(player, "homes.not-allowed");
            return;
        }
        String name = IslandHomeRules.normalize(typed);
        if (name == null || !homes.getOrDefault(island.id(), Map.of()).containsKey(name)) {
            unknown(player, island, typed);
            return;
        }
        store.delete(island.id(), name).whenComplete((removed, error) -> module.core().scheduler().main("islands-homes-del", () -> {
            if (error != null) {
                islands.databaseError(player, "delhome " + name, error);
                return;
            }
            Map<String, Island.Home> map = homes.get(island.id());
            if (map != null) {
                map.remove(name);
            }
            module.core().audit().log(AuditCategory.ISLAND, "island_delhome", Actor.player(player), island.id().toString(),
                    islands.displayName(island), null, player.getLocation(), Map.of("home", name));
            module.msg().send(player, "homes.deleted", Msg.p("home", name));
            module.core().sounds().play(player, "success");
        }));
    }

    @EventHandler
    public void onIslandRemoved(IslandRemovedEvent event) {
        homes.remove(event.islandId());
    }
}
