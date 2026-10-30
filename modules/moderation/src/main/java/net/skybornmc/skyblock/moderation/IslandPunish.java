package net.skybornmc.skyblock.moderation;

import net.skybornmc.skyblock.common.Core;
import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.audit.AuditCategory;
import net.skybornmc.skyblock.common.island.IslandService;
import net.skybornmc.skyblock.common.profile.ProfileService;
import net.skybornmc.skyblock.common.text.ModuleMessages;
import net.skybornmc.skyblock.common.text.Msg;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Enforces island punishments (P7, spec 13.2) through the islands module's moderation hooks ({@link IslandService}):
 * island kick (sent off the island), island ban (can't enter that one island), coop remove (removed from the team) and
 * coop block (can't join, be cooped or create islands). The punishment row is the history; the islands module holds
 * what it enforces. Nothing here ever deletes island data. Main thread unless a method returns a future.
 */
final class IslandPunish {

    /** Reference stored with an island ban so undo/pardon lifts exactly that ban. */
    static String reference(Punishment p) {
        return "punishment:" + p.id();
    }

    private final Core core;
    private final ModuleMessages msg;
    private final PunishmentStore store;
    private final java.util.concurrent.Executor main;

    IslandPunish(Core core, ModuleMessages msg, PunishmentStore store) {
        this.core = core;
        this.main = new net.skybornmc.skyblock.common.db.MainThread(core.plugin());
        this.msg = msg;
        this.store = store;
    }

    Optional<IslandService> islands() {
        return core.services().optional(IslandService.class).filter(IslandService::supportsModeration);
    }

    /**
     * The island context of a player (spec 13.2): the island of their active profile (offline: last active), and whether
     * they own it. Completes on the main thread.
     */
    CompletableFuture<IslandSteps.Context> context(UUID player) {
        Optional<IslandService> service = islands();
        if (service.isEmpty() || player == null) {
            return CompletableFuture.completedFuture(IslandSteps.Context.UNAVAILABLE);
        }
        ProfileService profiles = ProfileService.of(core.services());
        CompletableFuture<UUID> key = Bukkit.getPlayer(player) != null
                ? CompletableFuture.completedFuture(profiles.profileKey(player))
                : profiles.profileKeyAsync(player).exceptionally(e -> player);
        return key.thenCompose(k -> core.scheduler().supplyMain("moderation:island-context", () -> {
            IslandService islands = islands().orElse(null);
            if (islands == null) {
                return IslandSteps.Context.UNAVAILABLE;
            }
            Optional<IslandService.IslandInfo> info = islands.islandOf(k == null ? player : k);
            if (info.isEmpty()) {
                return IslandSteps.Context.NO_ISLAND;
            }
            IslandService.IslandInfo island = info.get();
            return new IslandSteps.Context(true, island.id(), island.name(), island.owner().equals(k == null ? player : k));
        }));
    }

    private static Actor actor(Punishment p) {
        return p.issuer() == null ? Actor.system("moderation") : Actor.player(p.issuer(), p.issuerName());
    }

    private static long until(Punishment p) {
        return p.expires() == null ? Long.MAX_VALUE : p.expires();
    }

    /** Applies an island punishment that was just stored. Staff are told when the islands side could not do it. */
    void apply(Punishment p, PunishmentService service) {
        if (!p.type().island() || p.target() == null) {
            return;
        }
        context(p.target()).thenAccept(ctx -> {
            IslandService islands = islands().orElse(null);
            Templates.Step step = new Templates.Step(p.type(), null, false);
            IslandSteps.Why why = IslandSteps.problem(step, ctx);
            if (islands == null || why != null) {
                failed(p, service, why == null ? IslandSteps.Why.UNAVAILABLE : why);
                return;
            }
            Actor actor = actor(p);
            Player online = Bukkit.getPlayer(p.target());
            switch (p.type()) {
                case ISLAND_KICK -> {
                    boolean sent = islands.expelFromIsland(ctx.islandId(), p.target(), p.reason(), actor);
                    if (online != null && sent) {
                        msg.send(online, "island-punish.kicked", Msg.p("island", ctx.islandName()), Msg.p("reason", p.reason()));
                        core.sounds().play(online, "error");
                    }
                    audit(p, "island_kick_applied", ctx, Map.of("was_there", sent));
                }
                case ISLAND_BAN -> islands.banFromIsland(ctx.islandId(), p.target(), until(p), p.reason(), actor, reference(p))
                        .thenAcceptAsync(ok -> {
                            if (!ok) {
                                failed(p, service, IslandSteps.Why.UNAVAILABLE);
                                return;
                            }
                            Player now = Bukkit.getPlayer(p.target());
                            if (now != null) {
                                msg.send(now, "island-punish.banned", service.parts(now, p), Msg.p("island", ctx.islandName()));
                                core.sounds().play(now, "error");
                            }
                            audit(p, "island_ban_applied", ctx, Map.of());
                        }, main);
                case COOP_REMOVE -> islands.removeMember(ctx.islandId(), p.target(), p.reason(), actor).thenAcceptAsync(ok -> {
                    if (!ok) {
                        failed(p, service, IslandSteps.Why.NO_ISLAND);
                        return;
                    }
                    audit(p, "coop_remove_applied", ctx, Map.of());
                }, main);
                case COOP_BLOCK -> islands.joinBlockedUntil(p.target()).thenCompose(existing -> {
                    // never shorten a longer block that is already there
                    long until = Math.max(until(p), existing.orElse(0L));
                    return islands.blockJoining(p.target(), until, p.reason(), actor);
                }).thenAcceptAsync(ok -> {
                    if (!ok) {
                        failed(p, service, IslandSteps.Why.UNAVAILABLE);
                        return;
                    }
                    Player now = Bukkit.getPlayer(p.target());
                    if (now != null) {
                        msg.send(now, "island-punish.coop-blocked", service.parts(now, p));
                        core.sounds().play(now, "error");
                    }
                    audit(p, "coop_block_applied", ctx, Map.of());
                }, main);
                default -> {
                }
            }
        }).exceptionally(e -> {
            core.plugin().getLogger().warning("Moderation: island punishment #" + p.id() + " failed: " + e);
            return null;
        });
    }

    /** Ends the island side of an undone/pardoned island ban or coop block. */
    void lift(Punishment p, Actor by) {
        Optional<IslandService> service = islands();
        if (service.isEmpty() || p.target() == null) {
            return;
        }
        IslandService islands = service.get();
        if (p.type() == Punishment.Type.ISLAND_BAN) {
            islands.liftIslandBan(reference(p), by);
        } else if (p.type() == Punishment.Type.COOP_BLOCK) {
            // another active coop block of this player keeps its own end
            store.history(p.target()).thenAccept(history -> {
                long until = remainingBlock(history, p.id(), System.currentTimeMillis());
                islands.blockJoining(p.target(), until, "lifted #" + p.id(), by);
            });
        }
    }

    /** End of the longest other active coop block ({@link Long#MAX_VALUE} = permanent), 0 = none. */
    static long remainingBlock(List<Punishment> history, long except, long now) {
        long until = 0;
        for (Punishment other : history) {
            if (other.id() != except && other.type() == Punishment.Type.COOP_BLOCK && other.active(now)) {
                until = Math.max(until, other.expires() == null ? Long.MAX_VALUE : other.expires());
            }
        }
        return until;
    }

    private void failed(Punishment p, PunishmentService service, IslandSteps.Why why) {
        service.notifyStaff(null, "island-steps.apply-failed", Msg.p("player", p.targetName()), Msg.p("id", p.id()),
                Msg.p("why", service.text(null, "island-steps.why." + why.name().toLowerCase(java.util.Locale.ROOT))));
        audit(p, "island_punish_failed", null, Map.of("why", why.name().toLowerCase(java.util.Locale.ROOT)));
    }

    private void audit(Punishment p, String action, IslandSteps.Context ctx, Map<String, Object> extra) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("id", p.id());
        details.put("type", p.type().key());
        if (ctx != null && ctx.islandId() != null) {
            details.put("island", ctx.islandId().toString());
        }
        details.putAll(extra);
        core.audit().log(AuditCategory.PUNISHMENT, action, actor(p), p.target().toString(), p.targetName(), null, null, details);
    }
}
