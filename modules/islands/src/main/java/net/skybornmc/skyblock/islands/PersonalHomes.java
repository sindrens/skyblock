package net.skybornmc.skyblock.islands;

import net.skybornmc.skyblock.common.Core;
import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.audit.AuditCategory;
import net.skybornmc.skyblock.common.db.MainThread;
import net.skybornmc.skyblock.common.text.ModuleMessages;
import net.skybornmc.skyblock.common.text.Msg;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * Personal homes on the own island (ISL2, owner I13): every stored member has {@code homes.per-member} (1) home of their
 * own, more from masteries later ({@link IslandManager#extraMemberHomes}). {@code /is setmyhome [name]} saves it where
 * the member stands (inside the border, safe spot, build height; the same name again moves it), {@code /is home <name>}
 * goes there; {@code /is home} alone is still the island home. Saved first (one transaction that re-checks membership
 * and the limit), then cached and logged ({@code island_member_home}). Homes end when the member leaves or is removed.
 */
final class PersonalHomes {

    private final IslandsModule module;
    private final Core core;
    private final IslandManager islands;
    private final ModuleMessages msg;
    private final MemberHomeStore store;
    private final Executor main;
    private final Set<UUID> busy = new HashSet<>();

    PersonalHomes(IslandsModule module, IslandManager islands, MemberHomeStore store) {
        this.module = module;
        this.core = module.core();
        this.islands = islands;
        this.msg = module.msg();
        this.store = store;
        this.main = new MainThread(core.plugin());
    }

    private IslandsConfig config() {
        return module.config();
    }

    /** Homes {@code member} may have on {@code island}: the base value plus mastery extras. */
    int maxHomes(Island island, UUID member) {
        return Math.max(0, config().homesPerMember) + islands.extraMemberHomes(island.id(), member);
    }

    private String defaultName() {
        String name = config().homesDefaultName;
        return name == null || name.isBlank() ? "my" : WarpRules.key(name.trim());
    }

    /** {@code /is setmyhome [name]}. */
    void setHome(Player player, String typed) {
        Optional<Island> own = islands.islandOfPlayer(player.getUniqueId());
        if (own.isEmpty()) {
            islands.fail(player, "no-island");
            return;
        }
        Island island = own.get();
        UUID uuid = player.getUniqueId();
        if (!MemberRules.canSetPersonalHome(island.roleOrNull(uuid))) {
            islands.fail(player, "no-island");
            return;
        }
        String name = typed == null || typed.isBlank() ? defaultName() : typed.trim();
        switch (WarpRules.checkName(name, config().homesNameMaxLength)) {
            case EMPTY, CHARACTERS -> {
                islands.fail(player, "myhome.bad-name");
                return;
            }
            case TOO_LONG -> {
                islands.fail(player, "myhome.too-long", Msg.p("max", config().homesNameMaxLength));
                return;
            }
            case OK -> {
                // valid
            }
        }
        String key = WarpRules.key(name);
        Location at = player.getLocation();
        if (!island.bounds().contains(at)) {
            islands.fail(player, "sethome.outside");
            return;
        }
        if (at.getY() < config().minBuildY || at.getY() > config().maxBuildY) {
            islands.fail(player, "sethome.height", Msg.p("min", config().minBuildY), Msg.p("max", config().maxBuildY));
            return;
        }
        if (!SafeSpots.isSafe(at)) {
            islands.fail(player, "sethome.unsafe");
            return;
        }
        int max = maxHomes(island, uuid);
        Map<String, Island.Home> homes = island.personalHomes(uuid);
        if (max <= 0) {
            islands.fail(player, "myhome.none");
            return;
        }
        if (!WarpRules.fits(homes.size(), max, homes.containsKey(key))) {
            islands.fail(player, "myhome.limit", Msg.p("max", max), Msg.p("homes", String.join(", ", homes.keySet())));
            return;
        }
        if (!busy.add(uuid)) {
            msg.send(player, "member.busy");
            return;
        }
        Island.Home home = new Island.Home(at.getX(), at.getY(), at.getZ(), at.getYaw(), at.getPitch());
        store.save(island.id(), uuid, key, home, max).whenCompleteAsync((result, error) -> {
            busy.remove(uuid);
            if (error != null) {
                islands.databaseError(player, "setmyhome", error);
                return;
            }
            switch (result) {
                case NOT_MEMBER -> islands.fail(player, "no-island");
                case LIMIT -> islands.fail(player, "myhome.limit", Msg.p("max", max),
                        Msg.p("homes", String.join(", ", island.personalHomes(uuid).keySet())));
                case SAVED, MOVED -> {
                    if (islands.byId(island.id()) != island || island.role(uuid).isEmpty()) {
                        return;
                    }
                    island.putPersonalHome(uuid, key, home);
                    core.audit().log(AuditCategory.ISLAND, "island_member_home", Actor.player(player), island.id().toString(),
                            islands.displayName(island), null, at, Map.of("name", key, "action",
                                    result == MemberHomeStore.SaveResult.SAVED ? "set" : "move", "homes", island.personalHomes(uuid).size(),
                                    "max", max));
                    msg.send(player, result == MemberHomeStore.SaveResult.SAVED ? "myhome.set" : "myhome.moved", Msg.p("name", key));
                    core.sounds().play(player, "success");
                }
            }
        }, main);
    }

    /** {@code /is home [name]}: no name = the island home; a name = that personal home. */
    void home(Player player, String name) {
        if (name == null || name.isBlank()) {
            islands.teleportHome(player);
            return;
        }
        Optional<Island> own = islands.islandOfPlayer(player.getUniqueId());
        if (own.isEmpty()) {
            islands.fail(player, "no-island");
            return;
        }
        Island island = own.get();
        Map<String, Island.Home> homes = island.personalHomes(player.getUniqueId());
        Island.Home home = homes.get(WarpRules.key(name.trim()));
        if (home == null) {
            if (homes.isEmpty()) {
                islands.fail(player, "myhome.unknown-none", Msg.p("name", name));
            } else {
                islands.fail(player, "myhome.unknown", Msg.p("name", name), Msg.p("homes", String.join(", ", homes.keySet())));
            }
            return;
        }
        Location target = IslandManager.location(island, home);
        if (target.getWorld() == null) {
            islands.fail(player, "error.world", Msg.p("world", island.world()));
            return;
        }
        target.getWorld().getChunkAtAsync(target).thenAcceptAsync(chunk -> {
            if (!player.isOnline()) {
                return;
            }
            Location safe = islands.safeHome(player, island, target);
            islands.teleport(player, safe, false, () -> msg.actionBar(player, "myhome.arrived", Msg.p("name", WarpRules.key(name.trim()))));
        }, main);
    }

    /** Tab completion of {@code /is home}: the sender's personal homes (or the default name as a hint). */
    List<String> names(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            return List.of();
        }
        return VisitManager.safely(() -> {
            List<String> names = new ArrayList<>(islands.islandOfPlayer(player.getUniqueId())
                    .map(island -> List.copyOf(island.personalHomes(player.getUniqueId()).keySet())).orElse(List.of()));
            if (names.isEmpty()) {
                names.add(defaultName());
            }
            return names;
        });
    }

    void forget(UUID player) {
        busy.remove(player);
    }
}
