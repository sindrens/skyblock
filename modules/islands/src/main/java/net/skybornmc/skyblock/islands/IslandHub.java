package net.skybornmc.skyblock.islands;

import net.skybornmc.skyblock.common.island.IslandService.Role;
import net.skybornmc.skyblock.common.text.Msg;
import org.bukkit.entity.Player;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * M2.5 entry points: the island menu ({@link IslandMenu}, {@code /is} with an island) and {@code /is info [player]}
 * (owner, members, created, size, level, warps of an island, in chat).
 */
final class IslandHub {

    private final IslandsModule module;
    private final IslandManager islands;

    IslandHub(IslandsModule module, IslandManager islands) {
        this.module = module;
        this.islands = islands;
    }

    /** Opens the island menu of the player's island. */
    void open(Player player, Island island) {
        module.core().menus().open(player, new IslandMenu(module, islands, island));
    }

    /** {@code /is info [player]}: the player's own island without a name. */
    void info(Player player, String name) {
        Island island;
        if (name == null || name.isBlank()) {
            island = islands.islandOfPlayer(islands.key(player)).orElse(null);
            if (island == null) {
                islands.fail(player, "no-island");
                return;
            }
        } else {
            island = module.visits().islandByName(name.trim());
            if (island == null) {
                islands.fail(player, "visit.no-island", Msg.p("player", name.trim()));
                return;
            }
        }
        List<String> names = new ArrayList<>();
        for (MembersMenu.Entry entry : MembersMenu.order(island.members(), List.of(), island::joinedAt)) {
            names.add(islands.name(entry.uuid()));
        }
        int blocks = island.sizeChunks() * IslandGrid.CHUNK;
        Role own = island.roleOrNull(islands.key(player));
        module.msg().send(player, "info.body",
                Msg.p("island", islands.displayName(island)),
                Msg.p("leader", islands.ownerName(island)),
                Msg.p("members", String.join(", ", names)),
                Msg.p("count", island.memberCount()),
                Msg.p("type", module.raw(island.team() ? "menu.island.team" : "menu.island.solo")),
                Msg.p("size", blocks),
                Msg.p("level", module.levelText(island.id())),
                Msg.p("warps", module.warps().warps(island.id()).size()),
                Msg.p("created", module.config().dateFormatter().format(Instant.ofEpochMilli(island.createdAt()))),
                Msg.p("state", module.raw(island.locked() ? "info.locked" : "info.open")),
                Msg.p("role", own == null ? module.raw("role.visitor") : module.raw("role." + MemberStore.key(own))));
    }
}
