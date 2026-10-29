package net.skybornmc.skyblock.islands;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.skybornmc.skyblock.common.Core;
import net.skybornmc.skyblock.common.command.CommandSpec;
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
 * PvP off, staff {@code /is bypass}. Publishes {@link IslandService} and the placeholders {@code island_name},
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
    private IslandBorders borders;
    private IslandProtection protection;
    private BorderProtection borderProtection;
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
        borders = new IslandBorders(manager);
        manager.borders(borders);
        protection = new IslandProtection(this, manager);
        borderProtection = new BorderProtection(this, manager, protection);
        Bukkit.getPluginManager().registerEvents(borders, core.plugin());
        Bukkit.getPluginManager().registerEvents(protection, core.plugin());
        Bukkit.getPluginManager().registerEvents(borderProtection, core.plugin());
        startSweep();
        Bukkit.getPluginManager().registerEvents(this, core.plugin());
        core.services().register(ID, IslandService.class, manager);
        registerPlaceholders();
        registerCommands();
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
        core.messages().load(ID);
        Bukkit.getOnlinePlayers().forEach(borders::update);
    }

    @Override
    public void disable() {
        if (borders != null) {
            borders.clearAll();
            HandlerList.unregisterAll(borders);
        }
        if (sweepTask != null) {
            sweepTask.cancel();
            sweepTask = null;
        }
        if (protection != null) {
            protection.clearBypass();
            HandlerList.unregisterAll(protection);
        }
        if (borderProtection != null) {
            HandlerList.unregisterAll(borderProtection);
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
                .sub(CommandSpec.sub("sethome").description("Set your island home where you stand").permission(PERM_USE).playerOnly()
                        .cooldown(Duration.ofSeconds(3)).example("is sethome")
                        .executes(ctx -> manager.setHome(ctx.player())))
                .sub(CommandSpec.sub("bypass").description("Staff: toggle island protection bypass (logged)").permission(PERM_BYPASS)
                        .playerOnly().example("is bypass")
                        .executes(ctx -> protection.toggleBypass(ctx.player()))));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        manager.forget(event.getPlayer().getUniqueId());
    }
}
