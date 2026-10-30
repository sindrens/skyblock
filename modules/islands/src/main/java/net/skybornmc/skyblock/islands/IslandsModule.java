package net.skybornmc.skyblock.islands;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.skybornmc.skyblock.common.Core;
import net.skybornmc.skyblock.common.command.Arg;
import net.skybornmc.skyblock.common.command.Args;
import net.skybornmc.skyblock.common.command.CommandSpec;
import net.skybornmc.skyblock.common.command.Suggest;
import net.skybornmc.skyblock.common.db.Migrations;
import net.skybornmc.skyblock.common.island.IslandService;
import net.skybornmc.skyblock.common.module.SkyblockModule;
import net.skybornmc.skyblock.common.text.ModuleMessages;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Phase 2 islands. M2.1: the 512×512 slot grid in the islands world (spiral allocation), creation via {@code /is} with a
 * confirm menu, the starter islet (FAWE schematic or the code-built "Ember Meadow" placeholder) with a filled starter
 * chest, {@code /is home}, {@code /is sethome}, {@code /is go}, per-player island borders and void rescue. M2.2: role
 * permission protection ({@link IslandProtection}), cross-border rules ({@link BorderProtection}), Y 0-256 build limit,
 * PvP off, staff {@code /is bypass}. M2.3: members and roles ({@link MemberManager}: invite with the Team Island warning,
 * accept/deny, kick, leave, promote/demote, transfer, coop/uncoop, {@code /is members} and the permission matrix,
 * join limits). M2.7: lifecycle ({@link IslandLifecycle}: {@code /is disband}, slot clearing by {@link IslandClearer}) and
 * the staff tools {@code /isadmin} ({@link IslandAdmin}). M2.4: visiting, lock, expel, bans, rating, likes and the visit
 * browser ({@link VisitManager}), island warps with categories, sign warps and the warp browser ({@link WarpManager}). Publishes {@link IslandService} and the placeholders {@code island_name},
 * {@code island_level} and {@code island_rank}.
 */
public final class IslandsModule implements SkyblockModule, Listener {

    static final String ID = "islands";
    static final String PERM_USE = "skyblock.island";
    static final String PERM_BYPASS = "skyblock.island.bypass";

    private final Core core;
    private final ModuleMessages msg;
    private final IslandsConfig config = new IslandsConfig();
    private List<StarterItems.Entry> starterItems = List.of();
    private IslandManager manager;
    private MemberManager members;
    private IslandBorders borders;
    private IslandProtection protection;
    private BorderProtection borderProtection;
    private IslandLifecycle lifecycle;
    private VisitManager visits;
    private WarpManager warps;
    private RolePermissions rolePermissions = RolePermissions.none();
    private net.skybornmc.skyblock.common.util.Scheduler.Task sweepTask;

    public IslandsModule(Core core) {
        this.core = core;
        this.msg = core.messages(ID);
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public Set<String> dependencies() {
        return Set.of("worlds");
    }

    @Override
    public void enable() throws Exception {
        loadConfig();
        new Migrations(core.database(), core.plugin().getLogger()).migrate(ID, getClass().getClassLoader());
        manager = new IslandManager(this, new IslandStore(core.database()));
        manager.rolePermissions(rolePermissions);
        manager.load();
        members = new MemberManager(this, manager, new MemberStore(core.database()));
        manager.members(members);
        members.load();
        members.startSweep();
        borders = new IslandBorders(manager);
        manager.borders(borders);
        protection = new IslandProtection(this, manager);
        borderProtection = new BorderProtection(this, manager, protection);
        lifecycle = new IslandLifecycle(this, manager);
        lifecycle.load();
        visits = new VisitManager(this, manager, protection, new VisitStore(core.database()));
        warps = new WarpManager(this, manager, visits);
        visits.warps(warps);
        visits.load();
        warps.load();
        Bukkit.getPluginManager().registerEvents(borders, core.plugin());
        Bukkit.getPluginManager().registerEvents(protection, core.plugin());
        Bukkit.getPluginManager().registerEvents(borderProtection, core.plugin());
        Bukkit.getPluginManager().registerEvents(visits, core.plugin());
        Bukkit.getPluginManager().registerEvents(warps, core.plugin());
        // /settings (menus) and the TeleportService (essentials) may enable after us: hook them one tick later.
        core.scheduler().later("islands:services", 1L, visits::hookServices);
        startSweep();
        Bukkit.getPluginManager().registerEvents(this, core.plugin());
        core.services().register(ID, IslandService.class, manager);
        registerPlaceholders();
        registerCommands();
        new IslandAdmin(this, manager, lifecycle, protection).register();
        Bukkit.getOnlinePlayers().forEach(borders::update);
        if (Bukkit.getWorld(config.world) == null) {
            core.plugin().getLogger().warning("Islands: world '" + config.world + "' is not loaded; /is can't create islands.");
        }
        core.plugin().getLogger().info("Islands: starter island = " + (IslandBuilder.faweAvailable() && manager.builder().schematicFile().isFile()
                ? "schematic " + manager.builder().schematicFile().getName() : "built-in placeholder (Ember Meadow)"));
    }

    @Override
    public void reload() {
        loadConfig();
        if (manager != null) {
            manager.rolePermissions(rolePermissions);
        }
        startSweep();
        if (members != null) {
            members.startSweep();
        }
        if (visits != null) {
            visits.startStats();
        }
        core.messages().load(ID);
        Bukkit.getOnlinePlayers().forEach(borders::update);
    }

    @Override
    public void disable() {
        if (lifecycle != null) {
            lifecycle.stop();
        }
        if (borders != null) {
            borders.clearAll();
            HandlerList.unregisterAll(borders);
        }
        if (sweepTask != null) {
            sweepTask.cancel();
            sweepTask = null;
        }
        if (members != null) {
            members.stopSweep();
        }
        if (protection != null) {
            protection.clearBypass();
            HandlerList.unregisterAll(protection);
        }
        if (borderProtection != null) {
            HandlerList.unregisterAll(borderProtection);
        }
        if (visits != null) {
            visits.stop();
            HandlerList.unregisterAll(visits);
        }
        if (warps != null) {
            HandlerList.unregisterAll(warps);
        }
        HandlerList.unregisterAll(this);
    }

    private void loadConfig() {
        core.configs().load(config);
        config.sizeProblems().forEach(core.plugin().getLogger()::warning);
        StarterItems.Result parsed = StarterItems.parse(config.starterItems, key -> {
            Material material = Material.matchMaterial(key);
            return material != null && material.isItem() && !material.isAir();
        });
        parsed.problems().forEach(problem -> core.plugin().getLogger().warning("islands.yml: " + problem));
        starterItems = parsed.entries();
        RolePermissions.Result perms = config.rolePermissions();
        perms.problems().forEach(problem -> core.plugin().getLogger().warning("islands.yml: " + problem));
        rolePermissions = perms.permissions();
    }

    /** (Re)starts the sweep that returns entities pushed out of island borders. */
    private void startSweep() {
        if (sweepTask != null) {
            sweepTask.cancel();
            sweepTask = null;
        }
        if (borderProtection != null && config.returnEntities) {
            long period = config.returnEntitiesIntervalTicks;
            sweepTask = core.scheduler().repeat("island-return-entities", period, period, borderProtection::sweep);
        }
    }

    // ------------------------------------------------------------------ shared helpers

    Core core() {
        return core;
    }

    IslandsConfig config() {
        return config;
    }

    ModuleMessages msg() {
        return msg;
    }

    IslandLifecycle lifecycle() {
        return lifecycle;
    }

    List<StarterItems.Entry> starterItems() {
        return starterItems;
    }

    Component text(Player viewer, String key, TagResolver... placeholders) {
        return core.menus().text(viewer, ID, key, placeholders);
    }

    List<Component> lines(Player viewer, String key, TagResolver... placeholders) {
        return core.menus().lines(viewer, ID, key, placeholders);
    }

    /** Raw (unparsed) message text, or a visible "[missing]" marker. */
    String raw(String key) {
        String raw = core.messages().raw(ID, key);
        return raw == null ? "[missing: islands:" + key + "]" : raw;
    }

    /** A message stored as a list (lines joined with &lt;newline&gt; by the loader), split back into lines. */
    List<String> rawLines(String key) {
        String raw = core.messages().raw(ID, key);
        List<String> lines = new ArrayList<>();
        if (raw != null) {
            lines.addAll(List.of(raw.split("<newline>", -1)));
        }
        return lines;
    }

    // ------------------------------------------------------------------ placeholders and commands

    private void registerPlaceholders() {
        core.placeholders().registerText(ID, "island_name",
                p -> p == null ? "" : manager.islandOfPlayer(p.getUniqueId()).map(manager::displayName).orElse(""));
        // Island level arrives in Phase 5; "-" until then (empty without an island so the scoreboard shows its dash).
        core.placeholders().registerText(ID, "island_level",
                p -> p == null || manager.islandOfPlayer(p.getUniqueId()).isEmpty() ? "" : raw("placeholder.level-none"));
        // Leaderboard position (Phase 5 island top). Empty until levels exist, so the scoreboard shows no "#".
        core.placeholders().registerText(ID, "island_rank", p -> "");
    }

    private void registerCommands() {
        Arg<Player> invitee = Args.onlinePlayer("player");
        Arg<Player> cooped = Args.onlinePlayer("player");
        Arg<String> inviter = Args.word("player", Suggest.bySender(members::inviterNames));
        Arg<String> denied = Args.word("player", Suggest.bySender(members::inviterNames));
        Arg<String> kickable = Args.word("player", Suggest.bySender(s -> members.memberNames(s, MemberRules::canKick)));
        Arg<String> promotable = Args.word("player", Suggest.bySender(s -> members.memberNames(s, MemberRules::canPromote)));
        Arg<String> demotable = Args.word("player", Suggest.bySender(s -> members.memberNames(s, MemberRules::canDemote)));
        Arg<String> heir = Args.word("player", Suggest.bySender(s -> members.memberNames(s, MemberRules::canTransfer)));
        Arg<String> uncooped = Args.word("player", Suggest.bySender(members::coopNames));
        // M2.4 visiting and warps
        Arg<String> visited = Args.word("player", Suggest.bySender(visits::islandNames));
        Arg<String> expelled = Args.word("player", Suggest.bySender(visits::visitorNames));
        Arg<String> bannable = Args.word("player", Suggest.bySender(visits::banCandidates));
        Arg<String> unbannable = Args.word("player", Suggest.bySender(visits::bannedNames));
        Arg<String> rated = Args.word("player", Suggest.bySender(visits::islandNames));
        Arg<Integer> stars = Args.integer("stars", 1, VisitManager.MAX_STARS, "1", "2", "3", "4", "5");
        Arg<String> liked = Args.word("player", Suggest.bySender(visits::islandNames));
        Arg<String> newWarp = Args.word("name", Suggest.bySender(warps::setwarpNames));
        Arg<WarpCategory> category = Args.choice("category", WarpCategory.class);
        Arg<String> oldWarp = Args.word("name", Suggest.bySender(warps::ownWarpNames));
        Arg<String> warpIsland = Args.word("player", Suggest.bySender(visits::islandNames));
        Arg<String> warpName = Args.word("name", warps.warpNamesOfTypedIsland("player"));
        core.commands().register(CommandSpec.command("is")
                .description("Your island: create it, or go home").category("Island")
                .permission(PERM_USE).playerOnly().aliases("island")
                .executes(ctx -> manager.openOrCreate(ctx.player()))
                .sub(CommandSpec.sub("home").description("Teleport to your island home").permission(PERM_USE).playerOnly()
                        .cooldown(Duration.ofSeconds(3)).example("is home")
                        .executes(ctx -> manager.teleportHome(ctx.player())))
                .sub(CommandSpec.sub("go").description("Teleport to your island (same as /is home)").permission(PERM_USE).playerOnly()
                        .cooldown(Duration.ofSeconds(3)).example("is go")
                        .executes(ctx -> manager.teleportHome(ctx.player())))
                .sub(CommandSpec.sub("invite").description("Invite a player to your island (leader/mod)").permission(PERM_USE).playerOnly()
                        .cooldown(Duration.ofSeconds(2)).arg(invitee).example("is invite Steve")
                        .executes(ctx -> members.invite(ctx.player(), ctx.get(invitee), null)))
                .sub(CommandSpec.sub("accept").description("Accept an island invite").permission(PERM_USE).playerOnly()
                        .optional(inviter).example("is accept")
                        .executes(ctx -> members.accept(ctx.player(), ctx.getOr(inviter, null))))
                .sub(CommandSpec.sub("deny").description("Decline an island invite").permission(PERM_USE).playerOnly()
                        .optional(denied).example("is deny")
                        .executes(ctx -> members.deny(ctx.player(), ctx.getOr(denied, null))))
                .sub(CommandSpec.sub("kick").description("Remove a member from your island (confirm)").permission(PERM_USE).playerOnly()
                        .arg(kickable).example("is kick Steve")
                        .executes(ctx -> members.kick(ctx.player(), ctx.get(kickable), null)))
                .sub(CommandSpec.sub("leave").description("Leave your island (confirm)").permission(PERM_USE).playerOnly()
                        .example("is leave")
                        .executes(ctx -> members.leave(ctx.player(), null)))
                .sub(CommandSpec.sub("promote").description("Make a member a mod (leader)").permission(PERM_USE).playerOnly()
                        .arg(promotable).example("is promote Steve")
                        .executes(ctx -> members.promote(ctx.player(), ctx.get(promotable))))
                .sub(CommandSpec.sub("demote").description("Make a mod a member again (leader)").permission(PERM_USE).playerOnly()
                        .arg(demotable).example("is demote Steve")
                        .executes(ctx -> members.demote(ctx.player(), ctx.get(demotable))))
                .sub(CommandSpec.sub("transfer").description("Hand your island to a member (leader, double confirm)").permission(PERM_USE)
                        .playerOnly().arg(heir).example("is transfer Steve")
                        .executes(ctx -> members.transfer(ctx.player(), ctx.get(heir), null)))
                .sub(CommandSpec.sub("coop").description("Trust a player while you are online (no XP, no bank)").permission(PERM_USE)
                        .playerOnly().cooldown(Duration.ofSeconds(2)).arg(cooped).example("is coop Steve")
                        .executes(ctx -> members.coop(ctx.player(), ctx.get(cooped))))
                .sub(CommandSpec.sub("uncoop").description("End a player's coop trust").permission(PERM_USE).playerOnly()
                        .arg(uncooped).example("is uncoop Steve")
                        .executes(ctx -> members.uncoop(ctx.player(), ctx.get(uncooped))))
                .sub(CommandSpec.sub("members").description("Your island's members, roles and permissions").permission(PERM_USE)
                        .playerOnly().example("is members")
                        .executes(ctx -> members.openMembers(ctx.player())))
                .sub(CommandSpec.sub("disband").description("Delete your island for good (leader; confirm + type the word)")
                        .permission(PERM_USE).playerOnly().cooldown(Duration.ofSeconds(3)).example("is disband")
                        .executes(ctx -> lifecycle.disband(ctx.player())))
                .sub(CommandSpec.sub("sethome").description("Set your island home where you stand (leader/mod)").permission(PERM_USE).playerOnly()
                        .cooldown(Duration.ofSeconds(3)).example("is sethome")
                        .executes(ctx -> manager.setHome(ctx.player())))
                .sub(CommandSpec.sub("visit").description("Visit a player's island, or browse islands without a name").permission(PERM_USE)
                        .playerOnly().cooldown(Duration.ofSeconds(3)).optional(visited).example("is visit Steve")
                        .executes(ctx -> visits.visit(ctx.player(), ctx.getOr(visited, null))))
                .sub(CommandSpec.sub("lock").description("Lock your island: only members can enter (leader/mod)").permission(PERM_USE)
                        .playerOnly().cooldown(Duration.ofSeconds(2)).example("is lock")
                        .executes(ctx -> visits.lock(ctx.player(), true)))
                .sub(CommandSpec.sub("unlock").description("Open your island to visitors again (leader/mod)").permission(PERM_USE)
                        .playerOnly().cooldown(Duration.ofSeconds(2)).example("is unlock")
                        .executes(ctx -> visits.lock(ctx.player(), false)))
                .sub(CommandSpec.sub("expel").description("Send a visitor off your island (leader/mod)").permission(PERM_USE)
                        .playerOnly().cooldown(Duration.ofSeconds(1)).arg(expelled).example("is expel Steve")
                        .executes(ctx -> visits.expel(ctx.player(), ctx.get(expelled))))
                .sub(CommandSpec.sub("ban").description("Ban a player from visiting your island (leader/mod)").permission(PERM_USE)
                        .playerOnly().cooldown(Duration.ofSeconds(2)).arg(bannable).example("is ban Steve")
                        .executes(ctx -> visits.ban(ctx.player(), ctx.get(bannable))))
                .sub(CommandSpec.sub("unban").description("Let a banned player visit again (leader/mod)").permission(PERM_USE)
                        .playerOnly().cooldown(Duration.ofSeconds(2)).arg(unbannable).example("is unban Steve")
                        .executes(ctx -> visits.unban(ctx.player(), ctx.get(unbannable))))
                .sub(CommandSpec.sub("setwarp").description("Set an island warp where you stand (leader/mod)").permission(PERM_USE)
                        .playerOnly().cooldown(Duration.ofSeconds(2)).arg(newWarp).optional(category).example("is setwarp shop shops")
                        .executes(ctx -> warps.setWarp(ctx.player(), ctx.get(newWarp),
                                ctx.has(category) ? ctx.get(category).key() : null)))
                .sub(CommandSpec.sub("delwarp").description("Delete one of your island warps (leader/mod)").permission(PERM_USE)
                        .playerOnly().cooldown(Duration.ofSeconds(2)).arg(oldWarp).example("is delwarp shop")
                        .executes(ctx -> warps.delWarp(ctx.player(), ctx.get(oldWarp))))
                .sub(CommandSpec.sub("warp").description("Go to an island warp, or browse warps without a name").permission(PERM_USE)
                        .playerOnly().cooldown(Duration.ofSeconds(3)).optional(warpIsland).optional(warpName).example("is warp Steve shop")
                        .executes(ctx -> warps.warp(ctx.player(), ctx.getOr(warpIsland, null), ctx.getOr(warpName, null))))
                .sub(CommandSpec.sub("rate").description("Rate an island 1-5 stars (once per season, changeable)").permission(PERM_USE)
                        .playerOnly().cooldown(Duration.ofSeconds(2)).arg(rated).arg(stars).example("is rate Steve 5")
                        .executes(ctx -> visits.rate(ctx.player(), ctx.get(rated), ctx.get(stars))))
                .sub(CommandSpec.sub("like").description("Like an island (once per day)").permission(PERM_USE)
                        .playerOnly().cooldown(Duration.ofSeconds(2)).arg(liked).example("is like Steve")
                        .executes(ctx -> visits.like(ctx.player(), ctx.get(liked))))
                .sub(CommandSpec.sub("bypass").description("Staff: toggle island protection bypass (logged)").permission(PERM_BYPASS)
                        .playerOnly().example("is bypass")
                        .executes(ctx -> protection.toggleBypass(ctx.player()))));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        manager.forget(event.getPlayer().getUniqueId());
        members.onQuit(event.getPlayer());
        warps.forget(event.getPlayer().getUniqueId());
    }
}
