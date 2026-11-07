package net.skybornmc.skyblock.islands;

import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.audit.AuditCategory;
import net.skybornmc.skyblock.common.db.Sql;
import net.skybornmc.skyblock.common.island.IslandService.Role;
import net.skybornmc.skyblock.common.store.CreditShop;
import net.skybornmc.skyblock.common.text.Msg;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

/**
 * {@code /is name [name]} (owner 7 Nov 2026, plans/STORE_FULL_PLAN.md 1, "rename tokens: yes"): the island leader names
 * the island (shown in /is top, visits, the scoreboard). The first name is free; every later change uses one Island
 * Rename Token (credit shop, account thing {@code island_rename}). Names go through the chat filter.
 */
final class IslandNaming {

    /** Account thing of a rename token (the credit shop's key). */
    static final String TOKEN = "island_rename";
    static final int MAX = 24;
    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9 _'-]+");

    /** Why a name can't be used, or OK. */
    enum Check { OK, EMPTY, TOO_LONG, CHARACTERS }

    /** Pure: letters, digits, spaces, {@code _ ' -}; 2-24 characters; no double spaces. */
    static Check check(String name) {
        if (name == null || name.isBlank() || name.trim().length() < 2) {
            return Check.EMPTY;
        }
        String n = name.trim();
        if (n.length() > MAX) {
            return Check.TOO_LONG;
        }
        return NAME.matcher(n).matches() && !n.contains("  ") ? Check.OK : Check.CHARACTERS;
    }

    private final IslandsModule module;
    private final IslandManager islands;
    private final java.util.Set<UUID> busy = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final java.util.concurrent.Executor main;

    IslandNaming(IslandsModule module, IslandManager islands) {
        this.module = module;
        this.islands = islands;
        this.main = new net.skybornmc.skyblock.common.db.MainThread(module.core().plugin());
    }

    void command(Player player, String raw) {
        Optional<Island> own = islands.islandOfPlayer(islands.key(player));
        if (own.isEmpty()) {
            islands.fail(player, "no-island");
            return;
        }
        Island island = own.get();
        int tokens = CreditShop.of(module.core().services()).map(s -> s.owned(player.getUniqueId(), TOKEN)).orElse(0);
        boolean free = island.customName() == null || island.customName().isBlank();
        if (raw == null || raw.isBlank()) {
            module.msg().send(player, "name.info", Msg.p("name", islands.displayName(island)), Msg.number("tokens", tokens),
                    Msg.p("cost", module.raw(free ? "name.cost-free" : "name.cost-token")));
            return;
        }
        if (island.roleOrNull(islands.key(player)) != Role.LEADER) {
            islands.fail(player, "name.leader-only");
            return;
        }
        String name = raw.trim();
        switch (check(name)) {
            case EMPTY -> {
                islands.fail(player, "name.empty");
                return;
            }
            case TOO_LONG -> {
                islands.fail(player, "name.too-long", Msg.number("max", MAX));
                return;
            }
            case CHARACTERS -> {
                islands.fail(player, "name.characters");
                return;
            }
            default -> {
            }
        }
        Optional<net.skybornmc.skyblock.common.text.TextFilter> filter = module.core().services().optional(net.skybornmc.skyblock.common.text.TextFilter.class);
        if (filter.isPresent() && !player.hasPermission("skyblock.chat.bypass")) {
            net.skybornmc.skyblock.common.text.TextFilter.Result result = filter.get().check(player, name);
            if (result.isBlocked() || !result.text().equalsIgnoreCase(name)) {
                islands.fail(player, "name.filtered");
                return;
            }
        }
        if (!free && tokens <= 0) {
            islands.fail(player, "name.no-token");
            return;
        }
        if (!busy.add(player.getUniqueId())) {
            return;
        }
        CompletableFuture<Boolean> paid = free ? CompletableFuture.completedFuture(true)
                : CreditShop.of(module.core().services()).map(s -> s.consume(player.getUniqueId(), TOKEN)).orElse(CompletableFuture.completedFuture(false));
        UUID islandId = island.id();
        String old = islands.displayName(island);
        paid.thenCompose(ok -> !ok ? CompletableFuture.completedFuture(false)
                        : module.core().database().transaction(c -> Sql.update(c, "UPDATE islands SET name = ? WHERE id = ? AND deleted_at IS NULL",
                        name, islandId) == 1))
                .whenCompleteAsync((ok, error) -> {
                    busy.remove(player.getUniqueId());
                    if (error != null || ok == null || !ok) {
                        if (player.isOnline()) {
                            islands.fail(player, free ? "name.failed" : "name.no-token");
                        }
                        return;
                    }
                    island.customName(name);
                    module.core().audit().log(AuditCategory.ISLAND, "island_rename", Actor.player(player), islandId.toString(), name, null, null,
                            Map.of("old", old, "token", !free));
                    if (player.isOnline()) {
                        module.msg().send(player, "name.done", Msg.p("name", name));
                        module.core().sounds().play(player, "success");
                    }
                }, main);
    }
}
