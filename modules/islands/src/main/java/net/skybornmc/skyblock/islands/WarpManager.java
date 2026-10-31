package net.skybornmc.skyblock.islands;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.skybornmc.skyblock.common.Core;
import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.audit.AuditCategory;
import net.skybornmc.skyblock.common.command.Suggest;
import net.skybornmc.skyblock.common.db.Database;
import net.skybornmc.skyblock.common.db.MainThread;
import net.skybornmc.skyblock.common.island.IslandService.Role;
import net.skybornmc.skyblock.common.text.ModuleMessages;
import net.skybornmc.skyblock.common.text.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.SignChangeEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.logging.Level;

/**
 * M2.4 island warps (main thread): {@code /is setwarp <name> [category]} (a second setwarp with the same name moves it),
 * {@code /is delwarp <name>}, {@code /is warp <player> [name]}, [warp] signs (owner I15: count toward the slots; breaking
 * the sign deletes the warp), the edit actions of the warp menus (icon, public/private, category, delete) and the warp
 * visit counter (unique visitors per day). Saved first ({@link VisitStore}), then cached, logged (category island).
 */
final class WarpManager implements Listener {

    static final String REASON_COMMAND = "command";
    static final String REASON_MENU = "menu";
    static final String REASON_SIGN_BROKEN = "sign-broken";
    static final String REASON_SIGN_MISSING = "sign-missing";

    private final IslandsModule module;
    private final Core core;
    private final IslandManager islands;
    private final VisitManager visits;
    private final ModuleMessages msg;
    private final VisitStore store;
    private final Executor main;
    /** Warps per island in creation order (lists are copy-on-write: tab completion may read them async). */
    private final Map<UUID, List<IslandWarp>> byIsland = new ConcurrentHashMap<>();
    /** Sign warps by sign block ("x|y|z"; the islands world only). */
    private final Map<String, IslandWarp> bySign = new HashMap<>();
    private final Set<UUID> busy = new HashSet<>();

    WarpManager(IslandsModule module, IslandManager islands, VisitManager visits) {
        this.module = module;
        this.core = module.core();
        this.islands = islands;
        this.visits = visits;
        this.msg = module.msg();
        this.store = visits.store();
        this.main = new MainThread(core.plugin());
    }

    private IslandsConfig config() {
        return module.config();
    }

    /** Loads every warp of a loaded island (blocking, enable only). */
    void load() {
        byIsland.clear();
        bySign.clear();
        for (IslandWarp warp : store.loadWarpsBlocking()) {
            if (islands.byId(warp.islandId()) != null) {
                cache(warp);
            }
        }
        islands.onRemoved(island -> {
            List<IslandWarp> removed = byIsland.remove(island.id());
            if (removed != null) {
                removed.forEach(warp -> {
                    if (warp.sign() != null) {
                        bySign.remove(signKey(warp.sign()), warp);
                    }
                });
            }
        });
    }

    private void cache(IslandWarp warp) {
        byIsland.computeIfAbsent(warp.islandId(), id -> new CopyOnWriteArrayList<>()).add(warp);
        if (warp.sign() != null) {
            bySign.put(signKey(warp.sign()), warp);
        }
    }

    private void uncache(IslandWarp warp) {
        List<IslandWarp> list = byIsland.get(warp.islandId());
        if (list != null) {
            list.remove(warp);
        }
        if (warp.sign() != null) {
            bySign.remove(signKey(warp.sign()), warp);
        }
    }

    static String signKey(IslandWarp.SignPos pos) {
        return pos.x() + "|" + pos.y() + "|" + pos.z();
    }

    // ------------------------------------------------------------------ lookups

    /** The warps of an island in creation order (never null). */
    List<IslandWarp> warps(UUID islandId) {
        List<IslandWarp> list = byIsland.get(islandId);
        return list == null ? List.of() : List.copyOf(list);
    }

    IslandWarp warp(UUID islandId, String name) {
        if (name == null) {
            return null;
        }
        String key = WarpRules.key(name);
        for (IslandWarp warp : warps(islandId)) {
            if (warp.key().equals(key)) {
                return warp;
            }
        }
        return null;
    }

    int slots(Island island) {
        return WarpRules.slots(island.warpSlots(), config().warpLimits().startSlots(), config().warpLimits().maxSlots());
    }

    /** Public warps of islands visitors can enter (not locked), optionally one category; for the warp browser. */
    List<IslandWarp> browsable(WarpCategory category) {
        List<IslandWarp> list = new ArrayList<>();
        for (Map.Entry<UUID, List<IslandWarp>> entry : byIsland.entrySet()) {
            Island island = islands.byId(entry.getKey());
            if (island == null || island.locked()) {
                continue;
            }
            for (IslandWarp warp : entry.getValue()) {
                if (warp.isPublic() && (category == null || warp.category() == category)) {
                    list.add(warp);
                }
            }
        }
        return list;
    }

    /** The warps of {@code island} that {@code viewer} may use (private ones only for members and coop players). */
    List<IslandWarp> usable(Island island, Player viewer) {
        Role role = island.roleOrNull(islands.key(viewer));
        return warps(island.id()).stream().filter(w -> WarpRules.canUse(w.isPublic(), role)).toList();
    }

    Material icon(IslandWarp warp) {
        Material material = Material.matchMaterial(warp.icon());
        if (material == null || !material.isItem() || material.isAir()) {
            material = Material.matchMaterial(config().categoryIcon(warp.category()));
        }
        return material == null || !material.isItem() ? Material.ENDER_PEARL : material;
    }

    /** The icons of the picker (valid item ids from warps.icons, in order). */
    List<Material> pickableIcons() {
        List<Material> list = new ArrayList<>();
        for (String key : config().warpIcons) {
            Material material = key == null ? null : Material.matchMaterial(key.trim());
            if (material != null && material.isItem() && !material.isAir() && !list.contains(material)) {
                list.add(material);
            }
        }
        return list;
    }

    // ------------------------------------------------------------------ /is setwarp, /is delwarp

    void setWarp(Player player, String name, String categoryKey) {
        Island island = managedIsland(player);
        if (island == null) {
            return;
        }
        if (!validName(player, name)) {
            return;
        }
        WarpCategory category = null;
        if (categoryKey != null) {
            Optional<WarpCategory> parsed = WarpCategory.byKey(categoryKey);
            if (parsed.isEmpty()) {
                islands.fail(player, "warp.bad-category", Msg.p("input", categoryKey), Msg.p("choices", String.join(", ", WarpCategory.keys())));
                return;
            }
            category = parsed.get();
        }
        Location at = player.getLocation();
        if (!spotOk(player, island, at)) {
            return;
        }
        IslandWarp existing = warp(island.id(), name);
        if (existing == null && warps(island.id()).size() >= slots(island)) {
            islands.fail(player, "warp.full", Msg.p("slots", slots(island)));
            return;
        }
        WarpCategory iconCategory = category == null ? WarpCategory.OTHER : category;
        save(player, island, name, category, config().categoryIcon(iconCategory), home(at), null, existing != null);
    }

    private void save(Player player, Island island, String name, WarpCategory category, String icon, Island.Home at,
                      IslandWarp.SignPos sign, boolean move) {
        if (!begin(player)) {
            return;
        }
        store.saveWarp(island.id(), name, category, icon, at, sign, player.getUniqueId(), config().warpLimits())
                .whenCompleteAsync((result, error) -> {
                    end(player);
                    if (error != null) {
                        islands.databaseError(player, "setwarp", error);
                        return;
                    }
                    if (islands.byId(island.id()) != island) {
                        islands.fail(player, "error.island-gone");
                        return;
                    }
                    switch (result) {
                        case VisitStore.WarpResult.Gone ignored -> islands.fail(player, "error.island-gone");
                        case VisitStore.WarpResult.Full full -> islands.fail(player, "warp.full", Msg.p("slots", full.slots()));
                        case VisitStore.WarpResult.NameTaken ignored -> islands.fail(player, "warp.sign-name-taken", Msg.p("warp", name));
                        case VisitStore.WarpResult.Created created -> {
                            cache(created.warp());
                            IslandWarp warp = created.warp();
                            core.audit().log(AuditCategory.ISLAND, "island_warp_set", Actor.player(player), island.id().toString(),
                                    islands.displayName(island), null, player.getLocation(),
                                    Map.of("warp", warp.name(), "category", warp.category().key(), "sign", sign != null, "action", "create"));
                            msg.send(player, sign != null ? "warp.sign-created" : "warp.created", Msg.p("warp", warp.name()),
                                    Msg.p("category", categoryName(warp.category())), Msg.p("count", warps(island.id()).size()),
                                    Msg.p("slots", slots(island)));
                            core.sounds().play(player, "success");
                        }
                        case VisitStore.WarpResult.Moved moved -> {
                            IslandWarp warp = byId(island.id(), moved.warpId());
                            if (warp != null) {
                                if (warp.sign() != null) {
                                    bySign.remove(signKey(warp.sign()), warp);
                                }
                                warp.location(moved.location(), moved.sign());
                                warp.category(moved.category());
                            }
                            core.audit().log(AuditCategory.ISLAND, "island_warp_set", Actor.player(player), island.id().toString(),
                                    islands.displayName(island), null, player.getLocation(),
                                    Map.of("warp", name, "category", moved.category().key(), "action", "move"));
                            msg.send(player, "warp.moved", Msg.p("warp", warp == null ? name : warp.name()));
                            core.sounds().play(player, "success");
                        }
                    }
                }, main);
    }

    private IslandWarp byId(UUID islandId, long id) {
        for (IslandWarp warp : warps(islandId)) {
            if (warp.id() == id) {
                return warp;
            }
        }
        return null;
    }

    void delWarp(Player player, String name) {
        Island island = managedIsland(player);
        if (island == null) {
            return;
        }
        IslandWarp warp = warp(island.id(), name);
        if (warp == null) {
            islands.fail(player, "warp.not-found", Msg.p("warp", name));
            return;
        }
        delete(player, island, warp, REASON_COMMAND);
    }

    /** Deletes a warp (command, menu); tells {@code player} when not null. */
    void delete(Player player, Island island, IslandWarp warp, String reason) {
        if (player != null && !begin(player)) {
            return;
        }
        store.deleteWarp(warp.id()).whenCompleteAsync((removed, error) -> {
            if (player != null) {
                end(player);
            }
            if (error != null) {
                if (player != null) {
                    islands.databaseError(player, "delwarp", error);
                } else {
                    core.plugin().getLogger().log(Level.WARNING, "Islands: deleting warp " + warp.id() + " failed", Database.cause(error));
                }
                return;
            }
            uncache(warp);
            if (!removed) {
                if (player != null) {
                    islands.fail(player, "warp.not-found", Msg.p("warp", warp.name()));
                }
                return;
            }
            Actor actor = player == null ? Actor.system("islands") : Actor.player(player);
            core.audit().log(AuditCategory.ISLAND, "island_warp_delete", actor, island.id().toString(), islands.displayName(island), null,
                    null, Map.of("warp", warp.name(), "reason", reason, "visits", warp.visits()));
            if (player != null) {
                msg.send(player, "warp.deleted", Msg.p("warp", warp.name()));
                core.sounds().play(player, "success");
            } else {
                islands.memberManager().notifyMembers(island, Set.of(), "notify.warp-gone", Msg.p("warp", warp.name()));
            }
        }, main);
    }

    // ------------------------------------------------------------------ edits (warp menus)

    void setIcon(Player player, Island island, IslandWarp warp, Material icon, Runnable done) {
        if (!canEdit(player, island, warp)) {
            return;
        }
        String key = icon.getKey().getKey();
        String before = warp.icon();
        edit(player, store.setWarpIcon(warp.id(), key), () -> {
            warp.icon(key);
            audit(player, island, warp, "island_warp_icon", before, key);
            msg.send(player, "warp.icon-set", Msg.p("warp", warp.name()));
            done.run();
        });
    }

    void togglePublic(Player player, Island island, IslandWarp warp, Runnable done) {
        if (!canEdit(player, island, warp)) {
            return;
        }
        boolean next = !warp.isPublic();
        edit(player, store.setWarpPublic(warp.id(), next), () -> {
            warp.isPublic(next);
            audit(player, island, warp, "island_warp_public", String.valueOf(!next), String.valueOf(next));
            msg.send(player, next ? "warp.now-public" : "warp.now-private", Msg.p("warp", warp.name()));
            done.run();
        });
    }

    /** Sets a warp's category (the edit menu's pick list, GUI-4/G9). */
    void setCategory(Player player, Island island, IslandWarp warp, WarpCategory next, Runnable done) {
        if (!canEdit(player, island, warp) || warp.category() == next) {
            return;
        }
        WarpCategory before = warp.category();
        edit(player, store.setWarpCategory(warp.id(), next), () -> {
            warp.category(next);
            audit(player, island, warp, "island_warp_category", before.key(), next.key());
            done.run();
        });
    }

    private void audit(Player player, Island island, IslandWarp warp, String action, String before, String after) {
        core.audit().log(AuditCategory.ISLAND, action, Actor.player(player), island.id().toString(), islands.displayName(island), null, null,
                Map.of("warp", warp.name(), "old", before, "new", after));
    }

    private void edit(Player player, CompletableFuture<Boolean> save, Runnable apply) {
        if (!begin(player)) {
            return;
        }
        save.whenCompleteAsync((saved, error) -> {
            end(player);
            if (error != null) {
                islands.databaseError(player, "warp edit", error);
                return;
            }
            if (!saved) {
                islands.fail(player, "warp.gone");
                return;
            }
            apply.run();
            core.sounds().play(player, "success");
        }, main);
    }

    boolean canManage(Player player, Island island) {
        return WarpRules.canManage(island.roleOrNull(islands.key(player)));
    }

    private boolean canEdit(Player player, Island island, IslandWarp warp) {
        if (islands.byId(island.id()) != island || !warps(island.id()).contains(warp)) {
            islands.fail(player, "warp.gone");
            return false;
        }
        if (!canManage(player, island)) {
            islands.fail(player, "warp.not-allowed");
            return false;
        }
        return true;
    }

    // ------------------------------------------------------------------ /is warp

    /** {@code /is warp [player] [name]}: the browser, that island's warp list, or a teleport to the named warp. */
    void warp(Player player, String owner, String name) {
        if (net.skybornmc.skyblock.common.tutorial.TutorialGate.denied(core.services(), player, net.skybornmc.skyblock.common.tutorial.Gates.ISLAND_TEAM)) {
            return; // TUTORIAL §4: island warps open after the first-crop step
        }
        if (owner == null) {
            core.menus().open(player, new WarpBrowserMenu(module, islands, visits, this, null));
            return;
        }
        Island island = visits.islandByName(owner);
        if (island == null) {
            islands.fail(player, "visit.no-island", Msg.p("player", owner));
            return;
        }
        if (!visits.checkEntry(player, island)) {
            return;
        }
        List<IslandWarp> usable = usable(island, player);
        if (name == null) {
            if (usable.isEmpty()) {
                islands.fail(player, "warp.none", Msg.p("island", islands.displayName(island)));
            } else if (usable.size() == 1) {
                teleport(player, island, usable.getFirst());
            } else {
                core.menus().open(player, new WarpBrowserMenu(module, islands, visits, this, island));
            }
            return;
        }
        IslandWarp warp = warp(island.id(), name);
        if (warp == null || !usable.contains(warp)) {
            islands.fail(player, "warp.not-found-on", Msg.p("warp", name), Msg.p("island", islands.displayName(island)));
            return;
        }
        teleport(player, island, warp);
    }

    /** Teleports to a warp (entry rules, private warps, sign still there, safe spot) and counts the visit. */
    void teleport(Player player, Island island, IslandWarp warp) {
        if (islands.byId(island.id()) != island || !warps(island.id()).contains(warp)) {
            islands.fail(player, "warp.gone");
            return;
        }
        if (!visits.checkEntry(player, island)) {
            return;
        }
        Role role = island.roleOrNull(islands.key(player));
        if (!WarpRules.canUse(warp.isPublic(), role)) {
            islands.fail(player, "warp.private", Msg.p("warp", warp.name()));
            return;
        }
        World world = Bukkit.getWorld(island.world());
        if (world == null) {
            islands.fail(player, "error.world", Msg.p("world", island.world()));
            return;
        }
        Island.Home at = warp.location();
        Location target = new Location(world, at.x(), at.y(), at.z(), at.yaw(), at.pitch());
        if (warp.sign() != null) {
            IslandWarp.SignPos sign = warp.sign();
            world.getChunkAtAsync(sign.x() >> 4, sign.z() >> 4).thenAcceptAsync(chunk -> {
                if (!Tag.ALL_SIGNS.isTagged(world.getBlockAt(sign.x(), sign.y(), sign.z()).getType())) {
                    // The sign was destroyed some other way (explosion, piston, water): the warp ends with it.
                    islands.fail(player, "warp.gone");
                    delete(null, island, warp, REASON_SIGN_MISSING);
                    return;
                }
                go(player, island, warp, target, role);
            }, main);
            return;
        }
        go(player, island, warp, target, role);
    }

    private void go(Player player, Island island, IslandWarp warp, Location target, Role role) {
        String destination = module.raw("warp.destination").replace("<warp>", warp.name()).replace("<island>", islands.displayName(island));
        boolean count = role == null && !VisitManager.hidden(player);
        // The visit counts once the player really arrived (a cancelled warmup never counts).
        visits.teleportVisitor(player, island, target, destination, net.skybornmc.skyblock.common.teleport.TeleportReason.ISLAND_WARP,
                count ? () -> countVisit(player, island, warp) : null);
    }

    private void countVisit(Player player, Island island, IslandWarp warp) {
        UUID visitor = player.getUniqueId();
        String visitorName = player.getName();
        int day = visits.today();
        store.recordWarpVisit(warp.id(), visitor, day).whenCompleteAsync((counted, error) -> {
            if (error != null) {
                core.plugin().getLogger().log(Level.WARNING, "Islands: counting a warp visit failed", Database.cause(error));
                return;
            }
            if (counted) {
                warp.addVisit();
                core.audit().log(AuditCategory.ISLAND, "island_warp_visit", Actor.player(visitor, visitorName), island.id().toString(),
                        islands.displayName(island), null, null, Map.of("warp", warp.name(), "day", day));
            }
        }, main);
    }

    // ------------------------------------------------------------------ [warp] signs

    /** {@code [warp]} on line 1 creates a warp named by line 2 (category on line 3) where the player stands. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSign(SignChangeEvent event) {
        Block block = event.getBlock();
        if (!islands.isIslandWorld(block.getWorld())) {
            return;
        }
        Player player = event.getPlayer();
        IslandWarp existing = bySign.get(block.getX() + "|" + block.getY() + "|" + block.getZ());
        if (existing != null) {
            // Editing a warp sign would leave a warp the sign no longer describes.
            event.setCancelled(true);
            islands.fail(player, "warp.sign-locked", Msg.p("warp", existing.name()));
            return;
        }
        String tag = plain(event.line(0));
        if (!tag.equalsIgnoreCase(config().warpSignTag.trim())) {
            return;
        }
        Island island = islands.islandAt(block.getX(), block.getZ());
        if (island == null || island.roleOrNull(islands.key(player)) == null) {
            islands.fail(player, "warp.sign-not-yours");
            return;
        }
        if (!canManage(player, island)) {
            islands.fail(player, "warp.not-allowed");
            return;
        }
        String name = plain(event.line(1));
        if (!validName(player, name)) {
            return;
        }
        String categoryText = plain(event.line(2));
        WarpCategory category = categoryText.isBlank() ? WarpCategory.OTHER : WarpCategory.byKey(categoryText).orElse(null);
        if (category == null) {
            islands.fail(player, "warp.bad-category", Msg.p("input", categoryText), Msg.p("choices", String.join(", ", WarpCategory.keys())));
            return;
        }
        if (warp(island.id(), name) != null) {
            islands.fail(player, "warp.sign-name-taken", Msg.p("warp", name));
            return;
        }
        if (warps(island.id()).size() >= slots(island)) {
            islands.fail(player, "warp.full", Msg.p("slots", slots(island)));
            return;
        }
        Location at = player.getLocation();
        if (!spotOk(player, island, at)) {
            return;
        }
        event.line(0, module.text(player, "warp.sign.line-1"));
        event.line(1, module.text(player, "warp.sign.line-2", Msg.p("warp", name)));
        event.line(2, module.text(player, "warp.sign.line-3", Msg.p("category", categoryName(category))));
        event.line(3, module.text(player, "warp.sign.line-4", Msg.p("island", islands.displayName(island))));
        save(player, island, name, category, config().categoryIcon(category), home(at),
                new IslandWarp.SignPos(block.getX(), block.getY(), block.getZ()), false);
    }

    /** Breaking a warp sign deletes its warp. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (bySign.isEmpty() || !islands.isIslandWorld(block.getWorld())) {
            return;
        }
        IslandWarp warp = bySign.get(block.getX() + "|" + block.getY() + "|" + block.getZ());
        if (warp == null) {
            return;
        }
        Island island = islands.byId(warp.islandId());
        if (island == null) {
            return;
        }
        Player player = event.getPlayer();
        bySign.remove(signKey(warp.sign()), warp);
        store.deleteWarp(warp.id()).whenCompleteAsync((removed, error) -> {
            if (error != null) {
                core.plugin().getLogger().log(Level.WARNING, "Islands: deleting sign warp " + warp.id() + " failed", Database.cause(error));
                return;
            }
            uncache(warp);
            if (removed) {
                core.audit().log(AuditCategory.ISLAND, "island_warp_delete", Actor.player(player), island.id().toString(),
                        islands.displayName(island), null, block.getLocation(), Map.of("warp", warp.name(), "reason", REASON_SIGN_BROKEN,
                                "visits", warp.visits()));
                if (player.isOnline()) {
                    msg.send(player, "warp.sign-deleted", Msg.p("warp", warp.name()));
                }
            }
        }, main);
    }

    private static String plain(Component line) {
        return line == null ? "" : PlainTextComponentSerializer.plainText().serialize(line).trim();
    }

    // ------------------------------------------------------------------ helpers

    String categoryName(WarpCategory category) {
        return module.raw("warps.category." + category.key());
    }

    private boolean validName(Player player, String name) {
        switch (WarpRules.checkName(name, config().warpNameMaxLength)) {
            case OK -> {
                // Owner answer 304: warp names go through the chat filter (a changed or blocked name is refused).
                Optional<net.skybornmc.skyblock.common.text.TextFilter> filter =
                        core.services().optional(net.skybornmc.skyblock.common.text.TextFilter.class);
                if (filter.isPresent() && !player.hasPermission("skyblock.chat.bypass")) {
                    net.skybornmc.skyblock.common.text.TextFilter.Result result = filter.get().check(player, name);
                    if (result.isBlocked() || !result.text().equalsIgnoreCase(name)) {
                        islands.fail(player, "warp.name-filtered");
                        return false;
                    }
                }
                return true;
            }
            case EMPTY -> islands.fail(player, "warp.name-empty");
            case TOO_LONG -> islands.fail(player, "warp.name-long", Msg.p("max", config().warpNameMaxLength));
            case CHARACTERS -> islands.fail(player, "warp.name-chars");
        }
        return false;
    }

    /** A warp spot: inside the island's border, within the build height, safe to stand on. */
    private boolean spotOk(Player player, Island island, Location at) {
        if (!island.bounds().contains(at)) {
            islands.fail(player, "warp.outside");
            return false;
        }
        if (at.getY() < config().minBuildY || at.getY() > config().maxBuildY) {
            islands.fail(player, "sethome.height", Msg.p("min", config().minBuildY), Msg.p("max", config().maxBuildY));
            return false;
        }
        if (!SafeSpots.isSafe(at)) {
            islands.fail(player, "warp.unsafe");
            return false;
        }
        return true;
    }

    private static Island.Home home(Location at) {
        return new Island.Home(at.getX(), at.getY(), at.getZ(), at.getYaw(), at.getPitch());
    }

    private Island managedIsland(Player player) {
        Optional<Island> own = islands.islandOfPlayer(islands.key(player));
        if (own.isEmpty()) {
            islands.fail(player, "no-island");
            return null;
        }
        if (!WarpRules.canManage(own.get().roleOrNull(islands.key(player)))) {
            islands.fail(player, "warp.not-allowed");
            return null;
        }
        return own.get();
    }

    private boolean begin(Player player) {
        if (!busy.add(player.getUniqueId())) {
            msg.send(player, "member.busy");
            return false;
        }
        return true;
    }

    private void end(Player player) {
        busy.remove(player.getUniqueId());
    }

    void forget(UUID player) {
        busy.remove(player);
    }

    // ------------------------------------------------------------------ tab completion

    /** /is delwarp and /is setwarp: the sender's island's warp names. */
    List<String> ownWarpNames(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            return List.of();
        }
        return VisitManager.safely(() -> islands.islandOfPlayer(islands.key(player))
                .map(island -> warps(island.id()).stream().map(IslandWarp::name).toList()).orElse(List.of()));
    }

    /** /is setwarp name hint: existing names (to move one) or a sample. */
    List<String> setwarpNames(CommandSender sender) {
        List<String> names = new ArrayList<>(ownWarpNames(sender));
        if (names.isEmpty()) {
            names.addAll(List.of(module.raw("warp.name-hints").split(",")));
        }
        return names.stream().map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    /** /is warp &lt;player&gt; [name]: the usable warp names of the island typed in the {@code player} argument. */
    SuggestionProvider<CommandSourceStack> warpNamesOfTypedIsland(String playerArg) {
        return (ctx, builder) -> {
            List<String> names = VisitManager.safely(() -> namesFor(ctx, playerArg));
            for (String value : Suggest.filter(names, builder.getRemaining())) {
                builder.suggest(value);
            }
            return builder.buildFuture();
        };
    }

    private List<String> namesFor(CommandContext<CommandSourceStack> ctx, String playerArg) {
        String owner;
        try {
            owner = ctx.getArgument(playerArg, String.class);
        } catch (IllegalArgumentException e) {
            return List.of();
        }
        Island island = visits.islandByName(owner);
        if (island == null) {
            return List.of();
        }
        CommandSender sender = ctx.getSource().getSender();
        Role role = sender instanceof Player p ? island.roleOrNull(islands.key(p)) : null;
        List<String> names = new ArrayList<>();
        for (IslandWarp warp : warps(island.id())) {
            if (WarpRules.canUse(warp.isPublic(), role)) {
                names.add(warp.name());
            }
        }
        return names;
    }

}
