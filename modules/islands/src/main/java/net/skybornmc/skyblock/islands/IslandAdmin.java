package net.skybornmc.skyblock.islands;

import com.mojang.brigadier.suggestion.SuggestionProvider;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.skybornmc.skyblock.common.Core;
import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.audit.AuditCategory;
import net.skybornmc.skyblock.common.command.Arg;
import net.skybornmc.skyblock.common.command.Args;
import net.skybornmc.skyblock.common.command.CmdContext;
import net.skybornmc.skyblock.common.command.CommandFailure;
import net.skybornmc.skyblock.common.command.CommandSpec;
import net.skybornmc.skyblock.common.command.Suggest;
import net.skybornmc.skyblock.common.db.Database;
import net.skybornmc.skyblock.common.db.MainThread;
import net.skybornmc.skyblock.common.island.IslandService.Role;
import net.skybornmc.skyblock.common.menu.ConfirmMenu;
import net.skybornmc.skyblock.common.text.ModuleMessages;
import net.skybornmc.skyblock.common.text.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.logging.Level;

/**
 * {@code /isadmin} (M2.7): staff tools for islands. Every sub-command has its own permission and full tab completion
 * (island owner names, member names, sizes, slot ids). Destructive ones (delete, reset, kick, setowner) ask first: a
 * confirm screen for players (Bedrock-safe), {@code /isadmin … confirm} for the console. Everything is logged: data
 * changes in category {@code island} (by the island code, actor = the staff member) and every use in category
 * {@code staff} ({@code isadmin_<sub>}).
 *
 * <p>An island is named by its owner or any member ({@code /isadmin info Steve} = the island Steve is on).</p>
 */
final class IslandAdmin {

    static final String PERM_ROOT = "skyblock.island.admin";
    static final String PERM_INFO = "skyblock.island.admin.info";
    static final String PERM_TP = "skyblock.island.admin.tp";
    static final String PERM_DELETE = "skyblock.island.admin.delete";
    static final String PERM_SETSIZE = "skyblock.island.admin.setsize";
    static final String PERM_ADDMEMBER = "skyblock.island.admin.addmember";
    static final String PERM_KICK = "skyblock.island.admin.kick";
    static final String PERM_SETOWNER = "skyblock.island.admin.setowner";
    static final String PERM_RESET = "skyblock.island.admin.reset";
    static final String PERM_SLOTS = "skyblock.island.admin.slots";
    static final String PERM_TELEPORT_SLOT = "skyblock.island.admin.teleport-slot";
    static final String PERM_STATS = "skyblock.island.admin.stats";
    static final String PERM_STATS_RESET = "skyblock.island.admin.stats.reset";

    private static final int CONFIRM_SECONDS = 30;

    private final IslandsModule module;
    private final Core core;
    private final IslandManager islands;
    private final IslandLifecycle lifecycle;
    private final IslandProtection protection;
    private final ModuleMessages msg;
    private final Executor main;

    IslandAdmin(IslandsModule module, IslandManager islands, IslandLifecycle lifecycle, IslandProtection protection) {
        this.module = module;
        this.core = module.core();
        this.islands = islands;
        this.lifecycle = lifecycle;
        this.protection = protection;
        this.msg = module.msg();
        this.main = new MainThread(core.plugin());
    }

    // ------------------------------------------------------------------ command tree

    void register() {
        Arg<String> infoIsland = islandArg();
        Arg<String> tpIsland = islandArg();
        Arg<String> deleteIsland = islandArg();
        Arg<String> sizeIsland = islandArg();
        Arg<Integer> size = sizeArg();
        Arg<String> addIsland = islandArg();
        Arg<String> addPlayer = Args.offlinePlayer("player");
        Arg<String> kickPlayer = Args.word("player", Suggest.bySender(sender -> memberNames()));
        Arg<String> ownerIsland = islandArg();
        Arg<String> newOwner = Args.word("member", membersOfTypedIsland());
        Arg<String> resetIsland = islandArg();
        Arg<Integer> slot = slotArg();
        Arg<String> statsIsland = islandArg();
        Arg<String> statsResetIsland = islandArg();
        Predicate<CmdContext> console = ctx -> !(ctx.sender() instanceof Player);
        core.commands().register(CommandSpec.command("isadmin")
                .description("Staff: island tools (info, teleport, delete, size, members, owner, reset, slots, stats)").category("Staff")
                .permission(PERM_ROOT)
                .sub(CommandSpec.sub("info").description("Island details: owner, members, slot, size, home").permission(PERM_INFO)
                        .arg(infoIsland).example("isadmin info Steve")
                        .executes(ctx -> info(ctx.sender(), resolve(ctx.get(infoIsland)))))
                .sub(CommandSpec.sub("tp").description("Teleport to an island's home").permission(PERM_TP).playerOnly()
                        .arg(tpIsland).example("isadmin tp Steve")
                        .executes(ctx -> tp(ctx.player(), resolve(ctx.get(tpIsland)))))
                .sub(CommandSpec.sub("delete").description("Delete an island for good (confirm); the slot is cleared and reused")
                        .permission(PERM_DELETE).arg(deleteIsland).example("isadmin delete Steve")
                        .confirmIf(CONFIRM_SECONDS, console)
                        .confirmMessage(IslandsModule.ID, "admin.delete.confirm", ctx -> new TagResolver[]{Msg.p("input", ctx.get(deleteIsland))})
                        .executes(ctx -> delete(ctx.sender(), resolve(ctx.get(deleteIsland)))))
                .sub(CommandSpec.sub("setsize").description("Set an island's border width in chunks (odd)").permission(PERM_SETSIZE)
                        .arg(sizeIsland).arg(size).example("isadmin setsize Steve 5")
                        .executes(ctx -> setSize(ctx.sender(), resolve(ctx.get(sizeIsland)), ctx.get(size))))
                .sub(CommandSpec.sub("addmember").description("Add a player to an island without an invite (slots still apply)")
                        .permission(PERM_ADDMEMBER).arg(addIsland).arg(addPlayer).example("isadmin addmember Steve Alex")
                        .executes(ctx -> addMember(ctx.sender(), resolve(ctx.get(addIsland)), ctx.get(addPlayer))))
                .sub(CommandSpec.sub("kick").description("Remove a member or coop player from their island (confirm)").permission(PERM_KICK)
                        .arg(kickPlayer).example("isadmin kick Alex")
                        .confirmIf(CONFIRM_SECONDS, console)
                        .confirmMessage(IslandsModule.ID, "admin.kick.confirm", ctx -> new TagResolver[]{Msg.p("player", ctx.get(kickPlayer))})
                        .executes(ctx -> kick(ctx.sender(), ctx.get(kickPlayer))))
                .sub(CommandSpec.sub("setowner").description("Make a member the island leader (confirm)").permission(PERM_SETOWNER)
                        .arg(ownerIsland).arg(newOwner).example("isadmin setowner Steve Alex")
                        .confirmIf(CONFIRM_SECONDS, console)
                        .confirmMessage(IslandsModule.ID, "admin.setowner.confirm",
                                ctx -> new TagResolver[]{Msg.p("input", ctx.get(ownerIsland)), Msg.p("player", ctx.get(newOwner))})
                        .executes(ctx -> setOwner(ctx.sender(), resolve(ctx.get(ownerIsland)), ctx.get(newOwner))))
                .sub(CommandSpec.sub("reset").description("Delete an island and give its leader a new one right away (confirm)")
                        .permission(PERM_RESET).arg(resetIsland).example("isadmin reset Steve")
                        .confirmIf(CONFIRM_SECONDS, console)
                        .confirmMessage(IslandsModule.ID, "admin.reset.confirm", ctx -> new TagResolver[]{Msg.p("input", ctx.get(resetIsland))})
                        .executes(ctx -> reset(ctx.sender(), resolve(ctx.get(resetIsland)))))
                .sub(CommandSpec.sub("bypass").description("Toggle ignoring island protection (logged)").permission(IslandsModule.PERM_BYPASS)
                        .playerOnly().example("isadmin bypass")
                        .executes(ctx -> protection.toggleBypass(ctx.player())))
                .sub(CommandSpec.sub("slots").description("Grid overview: every slot, used, clearing or free").permission(PERM_SLOTS)
                        .playerOnly().example("isadmin slots")
                        .executes(ctx -> openSlots(ctx.player(), null)))
                .sub(CommandSpec.sub("teleport-slot").description("Teleport to a grid slot by id").permission(PERM_TELEPORT_SLOT)
                        .playerOnly().arg(slot).example("isadmin teleport-slot 0")
                        .executes(ctx -> teleportSlot(ctx.player(), ctx.get(slot))))
                // M2.6 island stats: look at any island's stats (read only) or reset them (confirm, audited)
                .sub(CommandSpec.sub("stats").description("Island stats: look at an island's stats or reset them").permission(PERM_STATS)
                        .sub(CommandSpec.sub("view").description("Open an island's stats menu (read only)").permission(PERM_STATS)
                                .playerOnly().arg(statsIsland).example("isadmin stats view Steve")
                                .executes(ctx -> statsView(ctx.player(), resolve(ctx.get(statsIsland)))))
                        .sub(CommandSpec.sub("reset").description("Delete all stats of an island (confirm, logged)").permission(PERM_STATS_RESET)
                                .arg(statsResetIsland).example("isadmin stats reset Steve").confirm(CONFIRM_SECONDS)
                                .confirmMessage(IslandsModule.ID, "stats.admin.reset-confirm",
                                        ctx -> new TagResolver[]{Msg.p("input", ctx.get(statsResetIsland))})
                                .executes(ctx -> statsReset(ctx.sender(), resolve(ctx.get(statsResetIsland)))))));
    }

    private Arg<String> islandArg() {
        return Args.word("island", Suggest.bySender(sender -> ownerNames()));
    }

    private Arg<Integer> sizeArg() {
        return Arg.word("size", (sender, raw) -> {
            int max = module.config().maxSize();
            try {
                int value = Integer.parseInt(raw);
                if (IslandGrid.validSize(value) && value <= max) {
                    return value;
                }
            } catch (NumberFormatException ignored) {
                // falls through
            }
            throw CommandFailure.of("invalid-number", Msg.p("input", raw), Msg.p("min", 1), Msg.p("max", max));
        }, Suggest.of(() -> sizes(module.config().maxSize())));
    }

    private Arg<Integer> slotArg() {
        return Arg.word("slot", (sender, raw) -> {
            try {
                int id = Integer.parseInt(raw);
                if (islands.slotState(id).isPresent()) {
                    return id;
                }
            } catch (NumberFormatException ignored) {
                // falls through
            }
            List<Integer> ids = islands.slotIds();
            throw CommandFailure.of("invalid-number", Msg.p("input", raw), Msg.p("min", 0),
                    Msg.p("max", ids.isEmpty() ? 0 : ids.getLast()));
        }, Suggest.of(() -> islands.slotIds().stream().map(String::valueOf).toList()));
    }

    /** Valid sizes 1, 3, 5 … {@code max} as text (tab completion). Pure. */
    static List<String> sizes(int max) {
        List<String> sizes = new ArrayList<>();
        for (int size = 1; size <= max; size += 2) {
            sizes.add(String.valueOf(size));
        }
        return sizes;
    }

    /** Suggests the members (not the leader) of the island typed in the {@code island} argument before. */
    private SuggestionProvider<CommandSourceStack> membersOfTypedIsland() {
        return (ctx, builder) -> {
            List<String> names = new ArrayList<>();
            try {
                Island island = find(ctx.getArgument("island", String.class));
                if (island != null) {
                    for (Map.Entry<UUID, Role> entry : Map.copyOf(island.members()).entrySet()) {
                        if (entry.getValue() != Role.LEADER) {
                            names.add(islands.name(entry.getKey()));
                        }
                    }
                }
            } catch (IllegalArgumentException | java.util.ConcurrentModificationException ignored) {
                // no island typed yet, or the island changed meanwhile: no suggestions
            }
            Suggest.filter(names, builder.getRemaining()).forEach(builder::suggest);
            return builder.buildFuture();
        };
    }

    private List<String> ownerNames() {
        return safely(() -> {
            List<String> names = new ArrayList<>();
            for (Island island : List.copyOf(islands.islands())) {
                names.add(islands.ownerName(island));
            }
            names.sort(String.CASE_INSENSITIVE_ORDER);
            return names;
        });
    }

    /** Every stored member who isn't a leader, and every coop player (kick suggestions). */
    private List<String> memberNames() {
        return safely(() -> {
            List<String> names = new ArrayList<>();
            for (Island island : List.copyOf(islands.islands())) {
                for (Map.Entry<UUID, Role> entry : Map.copyOf(island.members()).entrySet()) {
                    if (entry.getValue() != Role.LEADER) {
                        names.add(islands.name(entry.getKey()));
                    }
                }
                for (UUID coop : List.copyOf(island.coops())) {
                    names.add(islands.name(coop));
                }
            }
            names.sort(String.CASE_INSENSITIVE_ORDER);
            return names;
        });
    }

    private static List<String> safely(java.util.function.Supplier<List<String>> names) {
        try {
            return names.get();
        } catch (RuntimeException e) {
            return List.of();
        }
    }

    // ------------------------------------------------------------------ resolving

    /** The island {@code name} owns or is a member of; fails the command with a clear message when there is none. */
    private Island resolve(String name) {
        Island island = find(name);
        if (island == null) {
            throw new CommandFailure(IslandsModule.ID, "admin.not-found", Msg.p("input", name));
        }
        return island;
    }

    /** Owner first (cached names), then members, then any online player by exact name. Null if none. */
    Island find(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            Optional<Island> own = islands.islandOfPlayer(islands.key(online));
            if (own.isPresent()) {
                return own.get();
            }
        }
        List<Island> all = List.copyOf(islands.islands());
        for (Island island : all) {
            if (islands.ownerName(island).equalsIgnoreCase(name)) {
                return island;
            }
        }
        UUID player = memberUuid(all, name);
        return player == null ? null : islands.islandOfPlayer(player).orElse(null);
    }

    /** The stored member (any island) named {@code name}, or null. */
    private UUID memberUuid(List<Island> all, String name) {
        for (Island island : all) {
            for (UUID member : List.copyOf(island.members().keySet())) {
                if (islands.name(member).equalsIgnoreCase(name)) {
                    return member;
                }
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ info / tp

    private void info(CommandSender sender, Island island) {
        String name = islands.displayName(island);
        String home = island.home().map(h -> String.format(java.util.Locale.ROOT, "%.1f %.1f %.1f", h.x(), h.y(), h.z()))
                .orElse(module.raw("admin.info.home-none"));
        String owner = islands.ownerName(island);
        msg.send(sender, "admin.info.header", Msg.p("island", name));
        msg.send(sender, "admin.info.lines", Msg.p("owner", owner), Msg.p("owner-uuid", island.owner()),
                Msg.p("type", module.raw(island.team() ? "admin.info.type-team" : "admin.info.type-solo")),
                Msg.p("season", island.seasonId()), Msg.p("slot", island.slotId()), Msg.p("grid-x", island.gridX()),
                Msg.p("grid-z", island.gridZ()), Msg.p("center-x", island.centerX()), Msg.p("center-z", island.centerZ()),
                Msg.p("size", island.sizeChunks()), Msg.p("max", module.config().maxSize()), Msg.p("members", island.memberCount()),
                Msg.p("slots", islands.slots(island)), Msg.p("coops", island.coops().size()),
                Msg.p("created", module.config().dateFormatter().format(Instant.ofEpochMilli(island.createdAt()))),
                Msg.p("home", home), Msg.p("id", island.id()));
        for (MembersMenu.Entry entry : MembersMenu.order(island.members(), island.coops(), island::joinedAt)) {
            String role = module.raw("role." + MemberStore.key(entry.role()));
            if (entry.role() == Role.COOP) {
                msg.send(sender, "admin.info.coop", Msg.p("player", islands.name(entry.uuid())), Msg.p("role", role));
            } else {
                msg.send(sender, "admin.info.member", Msg.p("player", islands.name(entry.uuid())), Msg.p("role", role),
                        Msg.p("date", module.config().dateFormatter().format(Instant.ofEpochMilli(island.joinedAt(entry.uuid())))));
            }
        }
        msg.send(sender, "admin.info.actions", MemberManager.command("owner", owner));
        staffLog(sender, "info", island, Map.of());
    }

    private void tp(Player player, Island island) {
        World world = Bukkit.getWorld(island.world());
        if (world == null) {
            islands.fail(player, "error.world", Msg.p("world", island.world()));
            return;
        }
        Location target = island.home()
                .map(h -> new Location(world, h.x(), h.y(), h.z(), h.yaw(), h.pitch()))
                .orElseGet(() -> new Location(world, island.centerX() + 0.5, module.config().surfaceY + 1, island.centerZ() + 0.5));
        String name = islands.displayName(island);
        staffTeleport(player, target, () -> msg.send(player, "admin.tp.done", Msg.p("island", name)));
        staffLog(player, "tp", island, Map.of());
    }

    /** {@code /isadmin teleport-slot <id>} (and a click in the slots overview): top of the slot's center column. */
    void teleportSlot(Player player, int slotId) {
        World world = Bukkit.getWorld(module.config().world);
        if (world == null) {
            islands.fail(player, "error.world", Msg.p("world", module.config().world));
            return;
        }
        int[] grid = IslandGrid.spiral(slotId);
        int x = IslandGrid.centerBlock(grid[0]);
        int z = IslandGrid.centerBlock(grid[1]);
        String state = islands.slotState(slotId).map(s -> module.raw("admin.state." + s.key())).orElse("?");
        world.getChunkAtAsync(x >> 4, z >> 4).thenAcceptAsync(chunk -> {
            if (!player.isOnline()) {
                return;
            }
            Optional<SafeSpot.Spot> top = SafeSpot.highestIn(SafeSpots.column(world), x, z, world.getMinHeight(), world.getMaxHeight() - 2);
            Location target = top.map(spot -> new Location(world, spot.x() + 0.5, spot.y(), spot.z() + 0.5))
                    .orElseGet(() -> new Location(world, x + 0.5, module.config().surfaceY + 1, z + 0.5));
            boolean empty = top.isEmpty();
            staffTeleport(player, target, () -> msg.send(player, empty ? "admin.teleport-slot.done-empty" : "admin.teleport-slot.done",
                    Msg.p("slot", slotId), Msg.p("state", state)));
        }, main);
        Island island = islands.islandInCell(grid[0], grid[1]);
        staffLog(player, "teleport_slot", island, Map.of("slot", slotId, "state", state));
    }

    private void staffTeleport(Player player, Location target, Runnable done) {
        player.setFallDistance(0);
        player.teleportAsync(target, PlayerTeleportEvent.TeleportCause.COMMAND).thenAcceptAsync(ok -> {
            if (ok) {
                core.sounds().play(player, "teleport");
                done.run();
            } else {
                islands.fail(player, "admin.tp.failed");
            }
        }, main);
    }

    /** {@code /isadmin slots}: loads every slot row (async), then opens the overview. */
    void openSlots(Player player, SlotState filter) {
        islands.slots().whenCompleteAsync((rows, error) -> {
            if (error != null) {
                islands.databaseError(player, "slots", error);
                return;
            }
            if (player.isOnline()) {
                core.menus().open(player, new SlotsMenu(module, islands, this, rows, filter));
            }
        }, main);
        staffLog(player, "slots", null, Map.of());
    }

    // ------------------------------------------------------------------ delete / reset

    private void delete(CommandSender sender, Island island) {
        ready(sender, island);
        String name = islands.displayName(island);
        confirm(sender, "admin.delete", Material.TNT, island, Msg.p("island", name), Msg.p("owner", islands.ownerName(island)),
                Msg.p("members", island.memberCount())).accept(() -> lifecycle.remove(island, IslandStore.REASON_DELETE, Actor.of(sender), null)
                .whenComplete((result, error) -> {
                    if (failed(sender, "delete", error)) {
                        return;
                    }
                    if (result instanceof IslandStore.DisbandResult.Disbanded done) {
                        msg.send(sender, "admin.delete.done", Msg.p("island", name), Msg.p("members", done.members().size()),
                                Msg.p("slot", done.slotId()));
                        success(sender);
                        staffLog(sender, "delete", island, Map.of("slot", done.slotId(), "members", done.members().size()));
                    } else {
                        fail(sender, "admin.changed");
                    }
                }));
    }

    private void reset(CommandSender sender, Island island) {
        ready(sender, island);
        String name = islands.displayName(island);
        Actor actor = Actor.of(sender);
        confirm(sender, "admin.reset", Material.FIRE_CHARGE, island, Msg.p("island", name), Msg.p("owner", islands.ownerName(island)),
                Msg.p("members", island.memberCount())).accept(() -> lifecycle.remove(island, IslandStore.REASON_RESET, actor, null)
                .whenComplete((result, error) -> {
                    if (failed(sender, "reset", error)) {
                        return;
                    }
                    if (!(result instanceof IslandStore.DisbandResult.Disbanded done)) {
                        fail(sender, "admin.changed");
                        return;
                    }
                    islands.createFor(done.owner(), actor).whenComplete((created, createError) -> {
                        Map<String, Object> details = new HashMap<>();
                        details.put("old_slot", done.slotId());
                        details.put("members", done.members().size());
                        if (createError != null || !(created instanceof IslandStore.CreateResult.Created fresh)) {
                            if (createError != null) {
                                core.plugin().getLogger().log(Level.SEVERE, "Islands: creating the reset island failed",
                                        Database.cause(createError));
                            }
                            fail(sender, "admin.reset.create-failed", Msg.p("island", name), Msg.p("owner", islands.name(done.owner())));
                            details.put("new_island", "-");
                        } else {
                            msg.send(sender, "admin.reset.done", Msg.p("island", name), Msg.p("owner", islands.name(done.owner())),
                                    Msg.p("slot", fresh.island().slotId()));
                            success(sender);
                            details.put("new_island", fresh.island().id().toString());
                            details.put("new_slot", fresh.island().slotId());
                        }
                        staffLog(sender, "reset", island, details);
                    });
                }));
    }

    // ------------------------------------------------------------------ size / members / owner

    private void setSize(CommandSender sender, Island island, int size) {
        int before = island.sizeChunks();
        String name = islands.displayName(island);
        islands.setSize(island.id(), size, Actor.of(sender)).whenComplete((saved, error) -> {
            if (failed(sender, "setsize", error)) {
                return;
            }
            if (!saved) {
                fail(sender, "admin.setsize.failed");
                return;
            }
            msg.send(sender, "admin.setsize.done", Msg.p("island", name), Msg.p("size", size), Msg.p("old", before));
            success(sender);
            staffLog(sender, "setsize", island, Map.of("old", before, "new", size));
        });
    }

    private void addMember(CommandSender sender, Island island, String playerName) {
        OfflinePlayer target = Bukkit.getPlayerExact(playerName);
        if (target == null) {
            target = Bukkit.getOfflinePlayerIfCached(playerName);
        }
        if (target == null) {
            throw CommandFailure.of("player-unknown", Msg.p("input", playerName));
        }
        // PROF: an online player joins with their active profile; an offline one with the key of their UUID (profile 1).
        UUID uuid = islands.resolve(target.getUniqueId());
        String shown = target.getName() == null ? playerName : target.getName();
        if (islands.islandOfPlayer(uuid).isPresent()) {
            throw new CommandFailure(IslandsModule.ID, "admin.addmember.has-island", Msg.p("player", shown));
        }
        String name = islands.displayName(island);
        islands.memberManager().addByStaff(island, uuid, Actor.of(sender)).whenComplete((result, error) -> {
            if (failed(sender, "addmember", error)) {
                return;
            }
            switch (result) {
                case MemberStore.AddResult.Added added -> {
                    msg.send(sender, "admin.addmember.done", Msg.p("player", shown), Msg.p("island", name),
                            Msg.p("members", island.memberCount()), Msg.p("slots", islands.slots(island)));
                    success(sender);
                    staffLog(sender, "addmember", island, Map.of("player", uuid.toString(), "player_name", shown,
                            "became_team", added.converted()));
                }
                case MemberStore.AddResult.HasIsland ignored -> fail(sender, "admin.addmember.has-island", Msg.p("player", shown));
                case MemberStore.AddResult.Full full -> fail(sender, "admin.addmember.full", Msg.p("island", name), Msg.p("slots", full.slots()));
                case MemberStore.AddResult.IslandGone ignored -> fail(sender, "error.island-gone");
                case MemberStore.AddResult.OwnProfile ignored -> fail(sender, "admin.addmember.own-profile", Msg.p("player", shown));
            }
        });
    }

    private void kick(CommandSender sender, String playerName) {
        List<Island> all = List.copyOf(islands.islands());
        Island island = null;
        UUID target = null;
        for (Island candidate : all) {
            for (UUID uuid : List.copyOf(candidate.members().keySet())) {
                if (islands.name(uuid).equalsIgnoreCase(playerName)) {
                    island = candidate;
                    target = uuid;
                }
            }
            for (UUID uuid : List.copyOf(candidate.coops())) {
                if (target == null && islands.name(uuid).equalsIgnoreCase(playerName)) {
                    island = candidate;
                    target = uuid;
                }
            }
            if (target != null) {
                break;
            }
        }
        if (island == null) {
            throw new CommandFailure(IslandsModule.ID, "admin.kick.not-member", Msg.p("player", playerName));
        }
        String name = islands.displayName(island);
        String shown = islands.name(target);
        if (island.roleOrNull(target) == Role.LEADER) {
            throw new CommandFailure(IslandsModule.ID, "admin.kick.leader", Msg.p("player", shown), Msg.p("island", name),
                    MemberManager.command("owner", islands.ownerName(island)));
        }
        Island from = island;
        UUID uuid = target;
        confirm(sender, "admin.kick", Material.IRON_DOOR, island, Msg.p("player", shown), Msg.p("island", name))
                .accept(() -> islands.removeMember(from.id(), uuid, "", Actor.of(sender)).whenCompleteAsync((removed, error) -> {
                    if (failed(sender, "kick", error)) {
                        return;
                    }
                    if (!removed) {
                        fail(sender, "admin.changed");
                        return;
                    }
                    msg.send(sender, "admin.kick.done", Msg.p("player", shown), Msg.p("island", name));
                    success(sender);
                    staffLog(sender, "kick", from, Map.of("player", uuid.toString(), "player_name", shown));
                }, main));
    }

    private void setOwner(CommandSender sender, Island island, String memberName) {
        String name = islands.displayName(island);
        UUID target = islands.memberManager().memberByName(island, memberName);
        if (target == null) {
            throw new CommandFailure(IslandsModule.ID, "admin.setowner.not-member", Msg.p("player", memberName), Msg.p("island", name));
        }
        String shown = islands.name(target);
        if (island.roleOrNull(target) == Role.LEADER) {
            throw new CommandFailure(IslandsModule.ID, "admin.setowner.already", Msg.p("player", shown), Msg.p("island", name));
        }
        ready(sender, island);
        UUID old = island.owner();
        confirm(sender, "admin.setowner", Material.GOLDEN_HELMET, island, Msg.p("player", shown), Msg.p("island", name),
                Msg.p("owner", islands.ownerName(island)))
                .accept(() -> islands.memberManager().setOwnerByStaff(island, target, Actor.of(sender)).whenComplete((saved, error) -> {
                    if (failed(sender, "setowner", error)) {
                        return;
                    }
                    if (!saved) {
                        fail(sender, "admin.changed");
                        return;
                    }
                    msg.send(sender, "admin.setowner.done", Msg.p("player", shown), Msg.p("island", name));
                    success(sender);
                    staffLog(sender, "setowner", island, Map.of("old_leader", old.toString(), "new_leader", target.toString(),
                            "new_leader_name", shown));
                }));
    }

    // ------------------------------------------------------------------ helpers

    /** Refuses while the island is being built or removed. */
    private void ready(CommandSender sender, Island island) {
        if (islands.byId(island.id()) != island) {
            throw new CommandFailure(IslandsModule.ID, "error.island-gone");
        }
        if (islands.isBuilding(island.owner()) || lifecycle.isRemoving(island.id())) {
            throw new CommandFailure(IslandsModule.ID, "admin.busy");
        }
    }

    /**
     * Players see a confirm screen ({@code <prefix>.crumb}, {@code .shown}, {@code .details}); the console already
     * confirmed with {@code /isadmin … confirm}. The returned consumer takes the action to run once confirmed.
     */
    private Consumer<Runnable> confirm(CommandSender sender, String prefix, Material icon, Island island, TagResolver... placeholders) {
        return action -> {
            if (!(sender instanceof Player player)) {
                action.run();
                return;
            }
            core.menus().open(player, new ConfirmMenu(
                    viewer -> module.text(viewer, prefix + ".crumb", placeholders),
                    core.menus().item(icon, player, IslandsModule.ID, prefix + ".shown", placeholders),
                    viewer -> module.lines(viewer, prefix + ".details", placeholders),
                    viewer -> {
                        if (islands.byId(island.id()) != island) {
                            fail(viewer, "error.island-gone");
                            return;
                        }
                        if (islands.isBuilding(island.owner()) || lifecycle.isRemoving(island.id())) {
                            fail(viewer, "admin.busy");
                            return;
                        }
                        action.run();
                    },
                    viewer -> msg.send(viewer, "admin.cancelled")));
        };
    }

    // ------------------------------------------------------------------ M2.6 island stats

    private void statsView(Player player, Island island) {
        IslandStats stats = module.stats();
        if (stats == null) {
            fail(player, "stats.failed");
            return;
        }
        stats.open(player, island, null, true);
        staffLog(player, "stats_view", island, Map.of());
    }

    /** Deletes every stats row of the island (and its unwritten counters); audited ({@code isadmin_stats_reset}). */
    private void statsReset(CommandSender sender, Island island) {
        IslandStats stats = module.stats();
        if (stats == null) {
            fail(sender, "stats.failed");
            return;
        }
        String name = islands.displayName(island);
        stats.tracker().reset(island.id()).whenCompleteAsync((removed, error) -> {
            if (failed(sender, "stats reset", error)) {
                return;
            }
            msg.send(sender, "stats.admin.reset-done", Msg.p("island", name));
            success(sender);
            staffLog(sender, "stats_reset", island, Map.of("rows", removed));
        }, main);
    }

    private boolean failed(CommandSender sender, String what, Throwable error) {
        if (error == null) {
            return false;
        }
        core.plugin().getLogger().log(Level.SEVERE, "Islands: /isadmin " + what + " failed", Database.cause(error));
        fail(sender, "error.database");
        return true;
    }

    private void fail(CommandSender sender, String key, TagResolver... placeholders) {
        msg.send(sender, key, placeholders);
        if (sender instanceof Player player) {
            core.sounds().play(player, "error");
        }
    }

    private void success(CommandSender sender) {
        if (sender instanceof Player player) {
            core.sounds().play(player, "success");
        }
    }

    /** Every use is logged in category staff ({@code isadmin_<sub>}). */
    private void staffLog(CommandSender sender, String sub, Island island, Map<String, Object> details) {
        Location at = sender instanceof Player player ? player.getLocation() : null;
        core.audit().log(AuditCategory.STAFF, "isadmin_" + sub, Actor.of(sender), island == null ? null : island.id().toString(),
                island == null ? null : islands.displayName(island), null, at, details);
    }
}
