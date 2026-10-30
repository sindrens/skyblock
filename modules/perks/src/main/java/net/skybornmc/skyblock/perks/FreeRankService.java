package net.skybornmc.skyblock.perks;

import net.skybornmc.skyblock.common.Core;
import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.audit.AuditCategory;
import net.skybornmc.skyblock.common.command.CommandSpec;
import net.skybornmc.skyblock.common.cosmetics.Cosmetics;
import net.skybornmc.skyblock.common.db.MainThread;
import net.skybornmc.skyblock.common.player.PlayerData;
import net.skybornmc.skyblock.common.text.ModuleMessages;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.util.Format;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * {@code /freerank} (owner RW4, approved): after {@code freerank.min-playtime-minutes} (30) of total playtime a player
 * claims the free lowest rank once (LuckPerms group {@code freerank.group}, {@code ember}), then picks 1 of 3 tags
 * ({@code freerank.tags}) in a small menu. Both are kept forever. The claim and the pick are single atomic database
 * writes (see {@link FreeRankStore}); a failed rank or tag grant is rolled back so the player can try again. Audited.
 */
final class FreeRankService {

    static final String PERM = "skyblock.perks.freerank";

    private final Core core;
    private final PerksConfig config;
    private final ModuleMessages msg;
    private final FreeRankStore store;
    /** Players with a claim or pick in progress (double clicks, command spam). */
    private final Set<UUID> busy = ConcurrentHashMap.newKeySet();

    FreeRankService(Core core, PerksConfig config, ModuleMessages msg) {
        this.core = core;
        this.config = config;
        this.msg = msg;
        this.store = new FreeRankStore(core.database());
    }

    void register() {
        core.commands().register(CommandSpec.command("freerank")
                .description("Claim your free rank and pick a tag").category("Perks")
                .permission(PERM).playerOnly().example("freerank")
                .executes(ctx -> run(ctx.player())));
    }

    private long playtime(Player player) {
        return core.players().get(player.getUniqueId()).map(PlayerData::playtimeMillis).orElse(0L);
    }

    /** The tags offered right now (configured ones that exist in tags-titles.yml). */
    List<String> choices() {
        return core.services().optional(Cosmetics.class)
                .map(c -> FreeRankRules.tagChoices(config.freerankTags, c.ids(Cosmetics.Kind.TAG)))
                .orElse(List.of());
    }

    private void run(Player player) {
        UUID id = player.getUniqueId();
        if (busy.contains(id)) {
            msg.send(player, "freerank.busy");
            return;
        }
        store.load(id).whenCompleteAsync((claim, error) -> {
            if (!player.isOnline()) {
                return;
            }
            if (error != null) {
                fail(player, "load", error);
                return;
            }
            FreeRankRules.State state = FreeRankRules.state(config.freerankEnabled, core.ranks().available(), playtime(player),
                    config.freerankMinPlaytimeMinutes, claim.orElse(null));
            switch (state) {
                case DISABLED -> msg.send(player, "freerank.disabled");
                case DONE -> msg.send(player, "freerank.done");
                case NO_RANKS -> msg.send(player, "freerank.unavailable");
                case TOO_EARLY -> msg.send(player, "freerank.too-early", Msg.p("time", Format.duration(Duration.ofMillis(
                        FreeRankRules.remainingMillis(playtime(player), config.freerankMinPlaytimeMinutes)))),
                        Msg.p("minutes", config.freerankMinPlaytimeMinutes));
                case PICK_TAG -> openPicker(player);
                case CLAIM -> claim(player);
            }
            if (state != FreeRankRules.State.CLAIM && state != FreeRankRules.State.PICK_TAG) {
                core.sounds().play(player, "error");
            }
        }, new MainThread(core.plugin()));
    }

    private void claim(Player player) {
        UUID id = player.getUniqueId();
        if (!busy.add(id)) {
            return;
        }
        String group = config.freerankGroup;
        long now = System.currentTimeMillis();
        long played = playtime(player);
        store.claim(id, player.getName(), group, now)
                .thenCompose(inserted -> {
                    if (!inserted) {
                        return CompletableFuture.completedFuture(Outcome.ALREADY);
                    }
                    return core.ranks().addGroup(id, group).handle((added, error) -> error).thenCompose(error -> {
                        if (error == null) {
                            return CompletableFuture.completedFuture(Outcome.CLAIMED);
                        }
                        core.plugin().getLogger().log(Level.WARNING, "/freerank: could not give group '" + group + "' to "
                                + player.getName() + " (claim undone)", error);
                        return store.unclaim(id).thenApply(undone -> Outcome.RANK_FAILED);
                    });
                })
                .whenCompleteAsync((outcome, error) -> {
                    busy.remove(id);
                    if (error != null) {
                        fail(player, "claim", error);
                        return;
                    }
                    switch (outcome) {
                        case ALREADY -> {
                            if (player.isOnline()) {
                                run(player);
                            }
                        }
                        case RANK_FAILED -> {
                            msg.send(player, "freerank.failed");
                            core.sounds().play(player, "error");
                        }
                        case CLAIMED -> {
                            core.audit().logPlayer(AuditCategory.AUTH, "freerank_claim", Actor.player(player), id, player.getName(),
                                    null, player.getLocation(), Map.of("group", group, "playtime_minutes", played / 60_000));
                            if (player.isOnline()) {
                                msg.send(player, "freerank.claimed");
                                core.sounds().play(player, "success");
                                openPicker(player);
                            }
                        }
                    }
                }, new MainThread(core.plugin()));
    }

    private enum Outcome { ALREADY, RANK_FAILED, CLAIMED }

    private void openPicker(Player player) {
        List<String> choices = choices();
        if (choices.isEmpty()) {
            msg.send(player, "freerank.no-tags");
            return;
        }
        core.menus().open(player, new FreeRankMenu(this, choices));
    }

    /** A tag was clicked in the menu: checked again, stored once, then granted (rolled back if granting fails). */
    void pick(Player player, String tag) {
        UUID id = player.getUniqueId();
        List<String> choices = choices();
        Optional<Cosmetics> cosmetics = core.services().optional(Cosmetics.class);
        if (!FreeRankRules.validChoice(tag, choices) || cosmetics.isEmpty()) {
            msg.send(player, "freerank.no-tags");
            return;
        }
        if (!busy.add(id)) {
            return;
        }
        player.closeInventory();
        long now = System.currentTimeMillis();
        store.pickTag(id, tag, now)
                .thenCompose(stored -> {
                    if (!stored) {
                        return CompletableFuture.completedFuture(PickOutcome.NOT_ALLOWED);
                    }
                    return cosmetics.get().grantTag(id, tag, "freerank").handle((isNew, error) -> error).thenCompose(error -> {
                        if (error == null) {
                            return CompletableFuture.completedFuture(PickOutcome.PICKED);
                        }
                        core.plugin().getLogger().log(Level.WARNING, "/freerank: could not give tag '" + tag + "' to "
                                + player.getName() + " (pick undone)", error);
                        return store.clearTag(id, tag).thenApply(undone -> PickOutcome.FAILED);
                    });
                })
                .whenCompleteAsync((outcome, error) -> {
                    busy.remove(id);
                    if (error != null) {
                        fail(player, "pick", error);
                        return;
                    }
                    if (!player.isOnline()) {
                        return;
                    }
                    switch (outcome) {
                        case NOT_ALLOWED -> {
                            msg.send(player, "freerank.done");
                            core.sounds().play(player, "error");
                        }
                        case FAILED -> {
                            msg.send(player, "freerank.failed");
                            core.sounds().play(player, "error");
                        }
                        case PICKED -> {
                            core.audit().logPlayer(AuditCategory.AUTH, "freerank_tag", Actor.player(player), id, player.getName(),
                                    null, player.getLocation(), Map.of("tag", tag));
                            msg.send(player, "freerank.tag-picked", Msg.c("tag", cosmetics.get().display(player, Cosmetics.Kind.TAG, tag)));
                            core.sounds().play(player, "success");
                        }
                    }
                }, new MainThread(core.plugin()));
    }

    private enum PickOutcome { NOT_ALLOWED, FAILED, PICKED }

    Cosmetics cosmetics() {
        return core.services().optional(Cosmetics.class).orElse(null);
    }

    private void fail(Player player, String step, Throwable error) {
        core.plugin().getLogger().log(Level.WARNING, "/freerank " + step + " failed for " + player.getName(), error);
        if (player.isOnline() && Bukkit.isPrimaryThread()) {
            msg.send(player, "freerank.failed");
            core.sounds().play(player, "error");
        }
    }
}
