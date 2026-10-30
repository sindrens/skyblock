package net.skybornmc.skyblock.skills;

import net.skybornmc.skyblock.common.Core;
import net.skybornmc.skyblock.common.command.Arg;
import net.skybornmc.skyblock.common.command.Args;
import net.skybornmc.skyblock.common.command.CommandFailure;
import net.skybornmc.skyblock.common.command.CommandSpec;
import net.skybornmc.skyblock.common.db.Migrations;
import net.skybornmc.skyblock.common.module.SkyblockModule;
import net.skybornmc.skyblock.common.placeholder.Placeholder;
import net.skybornmc.skyblock.common.settings.PlayerSettings;
import net.skybornmc.skyblock.common.settings.SettingCategory;
import net.skybornmc.skyblock.common.settings.SettingToggle;
import net.skybornmc.skyblock.common.skills.SkillService;
import net.skybornmc.skyblock.common.skills.SkillType;
import net.skybornmc.skyblock.common.text.ModuleMessages;
import net.skybornmc.skyblock.common.text.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

/**
 * Skills (M4.5, pulled forward from Phase 6): XP and levels for Mining, Foraging and Combat; Farming and Fishing are
 * shown locked until Phase 7. Publishes {@link SkillService}, the placeholders {@code skill_<id>_level} and
 * {@code skill_<id>_progress}, the {@code /settings} toggle {@code skill-xp-bar} and {@code /skills [player]}.
 * Settings and XP tables in {@code skills.yml}, texts in {@code messages/skills.yml}, data in the {@code skills} table.
 */
public final class SkillsModule implements SkyblockModule {

    static final String ID = "skills";
    static final String PERM_MENU = "skyblock.skills";
    static final String PERM_OTHERS = "skyblock.skills.others";
    static final String PERM_ADMIN = "skyblock.skills.admin";

    private final Core core;
    private final SkillsConfig config = new SkillsConfig();
    private ModuleMessages msg;
    private SkillManager manager;
    private DepthsXpListener depthsListener;

    public SkillsModule(Core core) {
        this.core = core;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void enable() throws Exception {
        core.configs().load(config);
        msg = core.messages(ID);
        new Migrations(core.database(), core.plugin().getLogger()).migrate(ID, getClass().getClassLoader());
        manager = new SkillManager(core, config, msg);
        manager.tables(loadTables());
        core.services().register(ID, SkillService.class, manager);
        Bukkit.getPluginManager().registerEvents(manager, core.plugin());
        registerDepthsListener();
        registerPlaceholders();
        registerSetting();
        registerCommand();
        manager.start();
    }

    @Override
    public void reload() {
        core.configs().load(config);
        core.messages().load(ID);
        manager.applyConfig();
        manager.tables(loadTables());
    }

    @Override
    public void disable() {
        if (manager == null) {
            return;
        }
        HandlerList.unregisterAll(manager);
        if (depthsListener != null) {
            HandlerList.unregisterAll(depthsListener);
            depthsListener = null;
        }
        core.services().optional(PlayerSettings.class).ifPresent(s -> s.unregisterAll(ID));
        manager.shutdown();
    }

    SkillManager manager() {
        return manager;
    }

    SkillsConfig config() {
        return config;
    }

    // ------------------------------------------------------------------ setup

    /** Depths XP only if the depths classes are in the jar (see {@link DepthsXpListener}). */
    private void registerDepthsListener() {
        try {
            depthsListener = new DepthsXpListener(manager);
            Bukkit.getPluginManager().registerEvents(depthsListener, core.plugin());
        } catch (NoClassDefFoundError e) {
            depthsListener = null;
            core.plugin().getLogger().info("Skills: the depths module is not in this build; Mining/Foraging XP from The Depths is off.");
        }
    }

    private SkillTables loadTables() {
        File file = new File(core.plugin().getDataFolder(), config.fileName());
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.load(file);
        } catch (IOException | InvalidConfigurationException e) {
            core.plugin().getLogger().warning(config.fileName() + ": not valid YAML, using the default XP tables. "
                    + String.valueOf(e.getMessage()).lines().findFirst().orElse(""));
            return SkillTables.defaults();
        }
        SkillTables.Loaded loaded = SkillTables.load(yaml, config.fileName(), SkillsModule::validId,
                problem -> core.plugin().getLogger().warning(problem));
        if (loaded.changed()) {
            try {
                yaml.save(file);
            } catch (IOException e) {
                core.plugin().getLogger().log(Level.WARNING, "Could not write the default XP tables to " + config.fileName(), e);
            }
        }
        return loaded.tables();
    }

    /** Combat ids must be entity types, the others blocks. */
    private static boolean validId(SkillType skill, String id) {
        NamespacedKey key = NamespacedKey.fromString(id);
        if (key == null) {
            return false;
        }
        if (skill == SkillType.COMBAT) {
            return Registry.ENTITY_TYPE.get(key) != null;
        }
        Material material = Material.matchMaterial(id);
        return material != null && material.isBlock();
    }

    private void registerPlaceholders() {
        for (SkillType skill : SkillType.values()) {
            core.placeholders().register(ID, "skill_" + skill.id() + "_level", Placeholder.text(p ->
                    p == null ? "0" : String.valueOf(manager.level(p.getUniqueId(), skill))));
            core.placeholders().register(ID, "skill_" + skill.id() + "_progress", Placeholder.text(p ->
                    p == null ? "0" : String.valueOf(XpFormat.percent(manager.progress(p.getUniqueId(), skill)))));
        }
    }

    private void registerSetting() {
        core.services().optional(PlayerSettings.class).ifPresent(settings -> settings.register(new SettingToggle(ID,
                SkillManager.TOGGLE, SettingCategory.DISPLAY, Material.EXPERIENCE_BOTTLE, true, null, ID,
                "settings." + SkillManager.TOGGLE, false)));
    }

    private void registerCommand() {
        Arg<String> target = Args.offlinePlayer("player");
        core.commands().register(CommandSpec.command("skills")
                .description("Your skill levels, progress and next unlocks").category("Progression")
                .permission(PERM_MENU).playerOnly().optional(target).example("skills")
                .executes(ctx -> open(ctx.player(), ctx.has(target) ? ctx.get(target) : null))
                .sub(adminCommand(SkillAdmin.Mode.SET, "set", "Set a player's skill level (staff)", 0, "skills set Steve mining 20"))
                .sub(adminCommand(SkillAdmin.Mode.ADD, "add", "Add levels to a player's skill (staff)", 1, "skills add Steve combat 5")));
    }

    /** Owner #68: {@code /skills set|add <player> <skill> <levels>} for admin + owner; online players; audited (staff). */
    private CommandSpec adminCommand(SkillAdmin.Mode mode, String name, String description, int min, String example) {
        Arg<Player> player = Args.onlinePlayer("player");
        Arg<SkillType> skill = Args.choice("skill", SkillType.class);
        Arg<Integer> levels = Args.integer("levels", min, MAX_LEVEL_INPUT, mode == SkillAdmin.Mode.SET
                ? new String[]{"0", "10", "66", "150"} : new String[]{"1", "5", "10"});
        return CommandSpec.sub(name).description(description).category("Staff").permission(PERM_ADMIN)
                .arg(player).arg(skill).arg(levels).example(example)
                .executes(ctx -> change(ctx.sender(), mode, ctx.get(player), ctx.get(skill), ctx.get(levels)));
    }

    /** Highest number the level argument accepts; the curve's max level clamps it (skills.yml can go up to 500). */
    private static final int MAX_LEVEL_INPUT = 500;

    private void change(org.bukkit.command.CommandSender sender, SkillAdmin.Mode mode, Player target, SkillType skill, int levels) {
        SkillCurve curve = manager.curve();
        int before = manager.level(target.getUniqueId(), skill);
        SkillAdmin.Result result = SkillAdmin.apply(mode, manager.xp(target.getUniqueId(), skill), levels, curve);
        String reason = "/skills " + mode.name().toLowerCase(java.util.Locale.ROOT) + " " + levels;
        if (!manager.setXp(target, skill, result.xp(), net.skybornmc.skyblock.common.audit.Actor.of(sender), reason)) {
            throw new CommandFailure(ID, "admin.not-loaded", Msg.p("player", target.getName()));
        }
        msg.send(sender, "admin.done", Msg.p("player", target.getName()), Msg.c("skill", msg.component(sender, "skill." + skill.id() + ".name")),
                Msg.p("from", before), Msg.p("level", result.level()), Msg.p("max", curve.maxLevel()));
        if (!target.equals(sender)) {
            msg.send(target, "admin.changed", Msg.c("skill", msg.component(target, "skill." + skill.id() + ".name")), Msg.p("level", result.level()),
                    Msg.p("staff", sender.getName()));
        }
    }

    private record Target(UUID uuid, String name, Map<SkillType, Double> xp) {
    }

    /** Opens the menu for yourself, an online player (live) or an offline player (loaded first). */
    private void open(Player viewer, String name) {
        if (name == null || name.equalsIgnoreCase(viewer.getName())) {
            core.menus().open(viewer, new SkillsMenu(this, viewer.getUniqueId(), viewer.getName(), null));
            return;
        }
        if (!viewer.hasPermission(PERM_OTHERS)) {
            throw new CommandFailure(ID, "others-no-permission");
        }
        Player online = Bukkit.getPlayerExact(name);
        if (online != null && viewer.canSee(online)) {
            core.menus().open(viewer, new SkillsMenu(this, online.getUniqueId(), online.getName(), null));
            return;
        }
        msg.send(viewer, "loading", Msg.p("player", name));
        UUID viewerId = viewer.getUniqueId();
        core.players().offline(name).thenCompose(snapshot -> snapshot
                .map(s -> manager.loadXp(s.uuid()).thenApply(xp -> new Target(s.uuid(), s.name(), xp)))
                .orElseGet(() -> CompletableFuture.completedFuture(null)))
                .whenComplete((found, error) -> core.scheduler().main("skills-open", () -> {
                    Player p = Bukkit.getPlayer(viewerId);
                    if (p == null) {
                        return;
                    }
                    if (error != null) {
                        core.plugin().getLogger().log(Level.WARNING, "Could not load skills of " + name, error);
                        msg.send(p, "load-failed", Msg.p("player", name));
                        core.sounds().play(p, "error");
                    } else if (found == null) {
                        msg.send(p, "unknown-player", Msg.p("player", name));
                        core.sounds().play(p, "error");
                    } else {
                        core.menus().open(p, new SkillsMenu(this, found.uuid(), found.name(), found.xp()));
                    }
                }));
    }
}
