package net.skybornmc.skyblock.islands;

import net.skybornmc.skyblock.common.Core;
import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.audit.AuditCategory;
import net.skybornmc.skyblock.common.db.Database;
import net.skybornmc.skyblock.common.db.MainThread;
import net.skybornmc.skyblock.common.island.IslandService.Bounds;
import net.skybornmc.skyblock.common.island.IslandService.Role;
import net.skybornmc.skyblock.common.menu.ConfirmMenu;
import net.skybornmc.skyblock.common.text.ModuleMessages;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.util.Format;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.logging.Level;

/**
 * M2.7 island lifecycle (main thread): removing an island ({@code /is disband} by the leader, staff delete and reset via
 * {@code /isadmin}) and handing the emptied slot back ({@link IslandClearer}).
 *
 * <p>Removing is one database transaction ({@link IslandStore#disband}): the island row is marked deleted (kept with its
 * season for history), every membership ends, coop players, invites and permission overrides go, and the slot becomes
 * {@code clearing}. Only after the commit the caches are updated, everybody standing in the slot is sent to spawn, the
 * members and coop players are told, the change is logged and the slot is queued for clearing.</p>
 */
final class IslandLifecycle {

    private final IslandsModule module;
    private final Core core;
    private final IslandManager islands;
    private final ModuleMessages msg;
    private final Executor main;
    private final IslandClearer clearer;
    /** Islands being removed right now (against double clicks and a staff delete racing a disband). */
    private final Set<UUID> removing = new HashSet<>();

    IslandLifecycle(IslandsModule module, IslandManager islands) {
        this.module = module;
        this.core = module.core();
        this.islands = islands;
        this.msg = module.msg();
        this.main = new MainThread(core.plugin());
        this.clearer = new IslandClearer(module, islands);
    }

    private IslandsConfig config() {
        return module.config();
    }

    IslandClearer clearer() {
        return clearer;
    }

    /** Queues every slot still {@code clearing} in the database (blocking read, enable only) and starts the clearer. */
    void load() {
        for (IslandStore.ClearingSlot slot : islands.store().clearingSlotsBlocking()) {
            clearer.enqueue(new IslandClearer.Job(slot.slotId(), slot.gridX(), slot.gridZ(), slot.islandId(), slot.sizeChunks(), "resume"));
        }
        if (clearer.pending() > 0) {
            core.plugin().getLogger().info("Islands: " + clearer.pending() + " slot(s) still being cleared; resuming.");
        }
        clearer.start(config().clearStartDelaySeconds * 20L);
    }

    void stop() {
        clearer.stop();
    }

    /** True while the island is being removed. */
    boolean isRemoving(UUID islandId) {
        return removing.contains(islandId);
    }

    // ------------------------------------------------------------------ /is disband (leader)

    /** {@code /is disband}: leader only; confirm screen, then the confirm word typed in chat, then the island is removed. */
    void disband(Player player) {
        Optional<Island> own = islands.islandOfPlayer(islands.key(player));
        if (own.isEmpty()) {
            islands.fail(player, "no-island");
            return;
        }
        Island island = own.get();
        if (island.roleOrNull(islands.key(player)) != Role.LEADER) {
            islands.fail(player, "disband.leader-only");
            return;
        }
        if (islands.isBuilding(islands.key(player)) || removing.contains(island.id())) {
            islands.fail(player, "disband.busy");
            return;
        }
        String name = islands.displayName(island);
        int others = Math.max(0, island.memberCount() - 1);
        String cooldown = Format.duration(Duration.ofMillis(islands.cooldownMillis()));
        core.menus().open(player, new ConfirmMenu(
                viewer -> module.text(viewer, "disband.crumb"),
                core.menus().item(Material.TNT, player, IslandsModule.ID, "disband.shown", Msg.p("island", name)),
                viewer -> module.lines(viewer, others > 0 ? "disband.details-team" : "disband.details",
                        Msg.p("island", name), Msg.p("members", others), Msg.p("time", cooldown)),
                viewer -> askWord(viewer, island, name),
                viewer -> msg.send(viewer, "disband.cancelled")));
    }

    /** Second step: the leader types the confirm word in chat (Bedrock-friendly, no sign GUI). */
    private void askWord(Player player, Island island, String name) {
        String word = module.raw("disband.confirm-word").trim();
        core.menus().prompt(player, IslandsModule.ID, "disband.type", config().disbandPromptLength, input -> {
            if (!input.trim().equalsIgnoreCase(word)) {
                islands.fail(player, "disband.wrong-word", Msg.p("word", word));
                return;
            }
            confirmed(player, island);
        }, () -> {
        }, Msg.p("word", word), Msg.p("island", name));
    }

    private void confirmed(Player player, Island island) {
        if (islands.byId(island.id()) != island) {
            islands.fail(player, "error.island-gone");
            return;
        }
        if (island.roleOrNull(islands.key(player)) != Role.LEADER) {
            islands.fail(player, "disband.leader-only");
            return;
        }
        if (islands.isBuilding(islands.key(player)) || removing.contains(island.id())) {
            islands.fail(player, "disband.busy");
            return;
        }
        String name = islands.displayName(island);
        remove(island, IslandStore.REASON_DISBAND, Actor.player(player), islands.key(player)).whenComplete((result, error) -> {
            if (error != null) {
                islands.databaseError(player, "disband", error);
                return;
            }
            switch (result) {
                case IslandStore.DisbandResult.Disbanded ignored -> {
                    if (!player.isOnline()) {
                        return;
                    }
                    long cooldown = islands.cooldownMillis();
                    if (cooldown > 0) {
                        msg.send(player, "disband.done", Msg.p("island", name), Msg.p("time", Format.duration(Duration.ofMillis(cooldown))));
                    } else {
                        msg.send(player, "disband.done-now", Msg.p("island", name));
                    }
                    core.sounds().play(player, "success");
                }
                case IslandStore.DisbandResult.NotLeader ignored -> islands.fail(player, "disband.leader-only");
                case IslandStore.DisbandResult.Gone ignored -> islands.fail(player, "error.island-gone");
            }
        });
    }

    // ------------------------------------------------------------------ removing (shared with /isadmin)

    /**
     * Removes {@code island}: saves (one transaction), then updates caches, sends players in the slot to spawn, tells
     * members and coop players, logs ({@code island_<reason>}) and queues the slot for clearing. Completes on the main
     * thread. {@code expectedOwner} (nullable) must still lead the island. A second call while one runs returns Gone.
     *
     * @param reason {@link IslandStore#REASON_DISBAND}, {@link IslandStore#REASON_DELETE} or {@link IslandStore#REASON_RESET}
     */
    CompletableFuture<IslandStore.DisbandResult> remove(Island island, String reason, Actor actor, UUID expectedOwner) {
        UUID id = island.id();
        if (!removing.add(id)) {
            return CompletableFuture.completedFuture(new IslandStore.DisbandResult.Gone());
        }
        String name = islands.displayName(island);
        return islands.store().disband(id, expectedOwner, reason, actor.uuid()).handleAsync((result, error) -> {
            removing.remove(id);
            if (error != null) {
                core.plugin().getLogger().log(Level.SEVERE, "Islands: removing island " + id + " failed", Database.cause(error));
                throw new CompletionException(Database.cause(error));
            }
            if (result instanceof IslandStore.DisbandResult.Disbanded done) {
                removed(island, name, done, reason, actor);
            } else if (result instanceof IslandStore.DisbandResult.Gone) {
                Island cached = islands.byId(id);
                if (cached != null) {
                    islands.deindex(cached); // the database says it's gone: drop the stale cache entry too
                }
            }
            return result;
        }, main);
    }

    private void removed(Island island, String name, IslandStore.DisbandResult.Disbanded done, String reason, Actor actor) {
        Island cached = islands.byId(island.id());
        if (cached != null) {
            islands.deindex(cached);
        }
        if (cached != island) {
            islands.deindex(island);
        }
        islands.memberManager().invites().removeIsland(island.id());
        IslandNotes notes = module.notes();
        if (notes != null) {
            notes.islandRemoved(island.id()); // NOTES: holograms and temporary books go with the island
        }
        islands.refreshBorders(island);
        int sentAway = sendAway(IslandGrid.slotBounds(island.world(), done.gridX(), done.gridZ()));
        notifyRemoved(name, done, reason, actor);

        Map<String, Object> details = new HashMap<>();
        details.put("slot", done.slotId());
        details.put("grid_x", done.gridX());
        details.put("grid_z", done.gridZ());
        details.put("size", done.sizeChunks());
        details.put("owner", done.owner().toString());
        details.put("owner_name", islands.name(done.owner()));
        details.put("season", island.seasonId());
        details.put("type", island.team() ? "team" : "solo");
        details.put("members", memberList(done.members()));
        details.put("coops", done.coops().stream().map(UUID::toString).toList());
        details.put("sent_away", sentAway);
        core.audit().log(AuditCategory.ISLAND, "island_" + reason, actor, island.id().toString(), name, null,
                new Location(Bukkit.getWorld(island.world()), island.centerX(), config().surfaceY, island.centerZ()), details);

        clearer.enqueue(new IslandClearer.Job(done.slotId(), done.gridX(), done.gridZ(), island.id(), done.sizeChunks(), reason));
    }

    private List<String> memberList(Map<UUID, Role> members) {
        List<String> list = new ArrayList<>(members.size());
        members.forEach((uuid, role) -> list.add(uuid + ":" + MemberStore.key(role)));
        return list;
    }

    /** Sends every online player standing in the slot to the fallback spawn. Returns how many. */
    private int sendAway(Bounds slot) {
        int count = 0;
        for (Player player : Bukkit.getOnlinePlayers()) {
            Location at = player.getLocation();
            if (islands.isIslandWorld(at.getWorld()) && slot.contains(at.getBlockX(), at.getBlockZ())) {
                islands.toFallback(player, () -> player.setFallDistance(0));
                count++;
            }
        }
        return count;
    }

    /** Tells the island's online members and coop players (not the one who did it). */
    private void notifyRemoved(String name, IslandStore.DisbandResult.Disbanded done, String reason, Actor actor) {
        Set<UUID> told = new HashSet<>();
        for (Map.Entry<UUID, Role> entry : done.members().entrySet()) {
            UUID uuid = entry.getKey();
            if (islands.person(uuid).equals(actor.uuid()) || !told.add(uuid)) {
                continue;
            }
            Player player = islands.online(uuid);
            if (player == null) {
                continue;
            }
            boolean owner = uuid.equals(done.owner());
            String key = switch (reason) {
                case IslandStore.REASON_DELETE -> "admin.delete.you";
                case IslandStore.REASON_RESET -> owner ? "admin.reset.you" : "admin.reset.member";
                default -> "disband.notify";
            };
            msg.send(player, key, Msg.p("island", name), Msg.p("player", actor.name()));
            core.sounds().play(player, "warning");
        }
        for (UUID coop : done.coops()) {
            Player player = islands.online(coop);
            if (player != null && told.add(coop) && !islands.person(coop).equals(actor.uuid())) {
                msg.send(player, "disband.notify-coop", Msg.p("island", name));
            }
        }
    }
}
