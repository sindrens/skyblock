package net.skybornmc.skyblock.tutorial;

import net.skybornmc.skyblock.common.Core;
import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.command.Arg;
import net.skybornmc.skyblock.common.command.Args;
import net.skybornmc.skyblock.common.command.CommandSpec;
import net.skybornmc.skyblock.common.db.Migrations;
import net.skybornmc.skyblock.common.module.SkyblockModule;
import net.skybornmc.skyblock.common.placeholder.Placeholder;
import net.skybornmc.skyblock.common.text.ModuleMessages;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.tutorial.StarterContents;
import net.skybornmc.skyblock.common.tutorial.TutorialGate;
import net.skybornmc.skyblock.common.tutorial.TutorialService;
import net.skybornmc.skyblock.common.tutorial.TutorialService.State;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;

import java.util.Optional;
import java.util.UUID;

/**
 * The join tutorial (docs/plans/TUTORIAL.md, milestones TU1-TU4): the island portal starts it, 11 short steps teach the
 * core loop (island, menu, Depths, mine, sell, Forge, help, bank), features unlock step by step ({@link TutorialGate},
 * owner answer 131) and the custom starter chest fills new islands (owner answer 158). Progress is per account, once.
 *
 * <p>Publishes {@link TutorialGate}, {@link TutorialService} and {@link StarterContents}. Settings {@code tutorial.yml},
 * texts {@code messages/tutorial.yml}, data {@code db/tutorial}. TU5+: {@link Cameras} (spawn, island, Depths, market;
 * Java spectator path, Bedrock cut-and-fade, crash-safe restore), {@link Pip} (the companion only you see), the Guide NPC
 * opening {@code /tutorial}, {@link OptionalSteps} (O1-O9, tips, small rewards), {@link StatsMenu} (funnel, drop-off,
 * Java vs Bedrock) and the event purge.</p>
 */
public final class TutorialModule implements SkyblockModule {

    static final String ID = "tutorial";
    static final String PERM_USE = "skyblock.tutorial";

    private final Core core;
    private final TutorialConfig config = new TutorialConfig();
    private final TutorialStore store = new TutorialStore();
    private ModuleMessages msg;
    private TutorialEngine engine;
    private Guides guides;
    private Targets targets;
    private StarterKitContents starter;
    private TutorialListener listener;
    private Replays replays;
    private Cameras cameras;
    private Pip pip;
    private OptionalSteps optional;
    private TutorialStats stats;
    private boolean starterPublished;

    public TutorialModule(Core core) {
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
        targets = new Targets(this);
        guides = new Guides(this);
        engine = new TutorialEngine(this);
        starter = new StarterKitContents(this);
        listener = new TutorialListener(this);
        replays = new Replays(this);
        cameras = new Cameras(this);
        pip = new Pip(this);
        optional = new OptionalSteps(this);
        stats = new TutorialStats(this);
        core.services().register(ID, TutorialGate.class, engine);
        core.services().register(ID, TutorialService.class, engine);
        publishStarter();
        Bukkit.getPluginManager().registerEvents(listener, core.plugin());
        registerCommands();
        registerPlaceholders();
        registerSettings();
        guides.cleanStray();
        cameras.cleanStray();
        pip.cleanStray();
        guides.start();
        cameras.start();
        optional.start();
        stats.startPurge();
        // Players already online (reload): load them quietly. The reward title is added to cosmetics when missing.
        core.scheduler().later("tutorial:start", 1, () -> {
            ensureTitle();
            Bukkit.getOnlinePlayers().forEach(cameras::restoreOnJoin);
            Bukkit.getOnlinePlayers().forEach(engine::load);
        });
    }

    /** TQ26: the "Tips" switch in /settings (takes over the menus module's "coming soon" placeholder). */
    private void registerSettings() {
        core.services().optional(net.skybornmc.skyblock.common.settings.PlayerSettings.class).ifPresent(s -> s.register(
                new net.skybornmc.skyblock.common.settings.SettingToggle(ID, OptionalSteps.SETTING_TIPS,
                        net.skybornmc.skyblock.common.settings.SettingCategory.DISPLAY, org.bukkit.Material.LANTERN, true, null, ID,
                        "settings.tips", false)));
    }

    @Override
    public void reload() {
        core.configs().load(config);
        core.messages().load(ID);
        if (engine != null) {
            engine.rules();
            cameras.rules();
            optional.rules();
            optional.start();
            stats.clear();
            publishStarter();
            guides.start();
            if (!config.enabled) {
                guides.hideAll();
            } else {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    Progress pr = engine.progress(p.getUniqueId());
                    if (pr != null && pr.state == State.ACTIVE) {
                        guides.show(p, pr);
                    }
                }
            }
        }
    }

    @Override
    public void disable() {
        if (cameras != null) {
            cameras.stopAll(); // first: everyone in a camera gets game mode and position back
        }
        if (optional != null) {
            optional.stop();
        }
        if (stats != null) {
            stats.stopPurge();
        }
        if (guides != null) {
            guides.stop();
        }
        if (replays != null) {
            replays.stopAll();
        }
        if (engine != null) {
            engine.shutdown();
        }
        if (guides != null) {
            guides.hideAll();
        }
        if (pip != null) {
            pip.hideAll();
        }
        if (listener != null) {
            HandlerList.unregisterAll(listener);
        }
        core.placeholders().unregisterAll(ID);
        core.services().optional(net.skybornmc.skyblock.common.settings.PlayerSettings.class).ifPresent(s -> s.unregisterAll(ID));
        engine = null;
    }

    /** The custom starter chest is offered to the islands module only while {@code starter-chest.enabled} is on. */
    private void publishStarter() {
        if (config.starterChest && !starterPublished) {
            core.services().register(ID, StarterContents.class, starter);
            starterPublished = true;
        } else if (!config.starterChest && starterPublished) {
            core.services().unregister(ID, StarterContents.class);
            starterPublished = false;
        }
    }

    /** TQ7: the finish title ({@code reward.title}) is added to tags-titles.yml when it does not exist (staff edits stay). */
    private void ensureTitle() {
        String id = config.rewardTitle;
        if (id == null || id.isBlank()) {
            return;
        }
        core.services().optional(net.skybornmc.skyblock.common.cosmetics.Cosmetics.class).ifPresent(c -> {
            if (!c.ids(net.skybornmc.skyblock.common.cosmetics.Cosmetics.Kind.TITLE).contains(id)) {
                c.defineTitle(id, core.messages().raw(ID, "reward.title-display"), net.skybornmc.skyblock.common.item.Rarity.UNCOMMON,
                        core.messages().raw(ID, "reward.title-source"), false);
            }
            c.markAutomatic(net.skybornmc.skyblock.common.cosmetics.Cosmetics.Kind.TITLE, id);
        });
    }

    // ------------------------------------------------------------------ placeholders (HUD objective line)

    private void registerPlaceholders() {
        // hud.yml values.objective = objective: the scoreboard's "Next:" line shows the step while the tutorial runs.
        Placeholder objective = Placeholder.text(this::objectiveText);
        core.placeholders().register(ID, "objective", objective);
        core.placeholders().register(ID, "tutorial_next", objective);
        core.placeholders().registerText(ID, "tutorial_step", p -> {
            Progress pr = engine == null ? null : engine.progress(p.getUniqueId());
            return pr == null || pr.state != State.ACTIVE || pr.step == null ? ""
                    : engine.flow().number(pr.step) + "/" + engine.flow().size();
        });
    }

    private String objectiveText(Player player) {
        Progress p = engine == null ? null : engine.progress(player.getUniqueId());
        if (p == null || p.state != State.ACTIVE || p.step == null || !config.enabled) {
            return "";
        }
        return engine.objective(p.step);
    }

    // ------------------------------------------------------------------ commands

    private void registerCommands() {
        Arg<String> target = Args.offlinePlayer("player");
        Arg<String> step = Args.oneOf("step", () -> engine.flow().steps());
        core.commands().register(CommandSpec.command("tutorial")
                .description("The tutorial: your progress, take me there, I'm stuck, watch again")
                .category("Info")
                .permission(PERM_USE)
                .example("tutorial")
                .executes(ctx -> engine.openMenu(ctx.player()))
                .sub(CommandSpec.sub("admin")
                        .description("Staff tools for a player's tutorial")
                        .permission(StaffTutorialMenu.PERM_STATUS)
                        .example("tutorial admin status Steve")
                        .sub(CommandSpec.sub("status")
                                .description("A player's tutorial step, times and progress")
                                .permission(StaffTutorialMenu.PERM_STATUS)
                                .arg(target)
                                .example("tutorial admin status Steve")
                                .executes(ctx -> status(ctx.sender(), ctx.get(target))))
                        .sub(CommandSpec.sub("skip")
                                .description("Finish a player's tutorial (no reward, logged)")
                                .permission(StaffTutorialMenu.PERM_ADMIN)
                                .arg(target)
                                .confirm(15)
                                .example("tutorial admin skip Steve")
                                .executes(ctx -> staff(ctx.sender(), ctx.get(target), "skip", null)))
                        .sub(CommandSpec.sub("reset")
                                .description("Restart a player's tutorial (locks return, no second reward, logged)")
                                .permission(StaffTutorialMenu.PERM_ADMIN)
                                .arg(target)
                                .confirm(15)
                                .example("tutorial admin reset Steve")
                                .executes(ctx -> staff(ctx.sender(), ctx.get(target), "reset", null)))
                        .sub(CommandSpec.sub("step")
                                .description("Move a player to a tutorial step (support, logged)")
                                .permission(StaffTutorialMenu.PERM_ADMIN)
                                .arg(target).arg(step)
                                .example("tutorial admin step Steve mine")
                                .executes(ctx -> staff(ctx.sender(), ctx.get(target), "step", ctx.get(step))))
                        .sub(CommandSpec.sub("stats")
                                .description("Tutorial funnel: where players stop, time per step, Java vs Bedrock")
                                .permission(StatsMenu.PERM_STATS)
                                .playerOnly()
                                .example("tutorial admin stats")
                                .executes(ctx -> core.menus().open(ctx.player(), new StatsMenu(this))))));
    }

    private void status(CommandSender sender, String name) {
        resolve(sender, name, (id, real) -> engine.lookup(id).thenAccept(found -> {
            if (found.isEmpty()) {
                msg.send(sender, "staff.nothing", Msg.p("player", real));
                return;
            }
            Progress p = found.get();
            msg.send(sender, "admin.status", Msg.p("player", real),
                    Msg.p("state", TutorialStore.key(p.state)),
                    Msg.p("step", p.step == null ? "-" : p.step + " (" + engine.flow().number(p.step) + "/" + engine.flow().size() + ")"),
                    Msg.number("done", p.done.size()), Msg.number("total", engine.flow().size()),
                    Msg.p("done-list", p.done.isEmpty() ? "-" : String.join(", ", p.done.keySet())),
                    Msg.p("rewarded", p.rewarded ? "yes" : "no"));
        }));
    }

    private void staff(CommandSender sender, String name, String what, String step) {
        Actor actor = Actor.of(sender);
        resolve(sender, name, (id, real) -> {
            var future = switch (what) {
                case "skip" -> engine.staffSkip(actor, id, real);
                case "reset" -> engine.staffReset(actor, id, real);
                default -> engine.staffStep(actor, id, real, step);
            };
            future.thenAccept(ok -> {
                msg.send(sender, ok ? "staff." + what + "-done" : "staff.nothing", Msg.p("player", real), Msg.p("step", String.valueOf(step)));
                if (sender instanceof Player p) {
                    core.sounds().play(p, ok ? "success" : "error");
                }
            });
        });
    }

    private void resolve(CommandSender sender, String name, java.util.function.BiConsumer<UUID, String> then) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            then.accept(online.getUniqueId(), online.getName());
            return;
        }
        core.players().offline(name).thenAccept(found -> core.scheduler().main("tutorial-resolve", () -> {
            Optional<UUID> id = found.map(s -> s.uuid());
            if (id.isEmpty()) {
                msg.send(sender, "staff.nothing", Msg.p("player", name));
                return;
            }
            then.accept(id.get(), found.get().name());
        }));
    }

    // ------------------------------------------------------------------ shared

    Core core() {
        return core;
    }

    TutorialConfig config() {
        return config;
    }

    ModuleMessages msg() {
        return msg;
    }

    TutorialStore store() {
        return store;
    }

    TutorialEngine engine() {
        return engine;
    }

    Guides guides() {
        return guides;
    }

    Targets targets() {
        return targets;
    }

    StarterKitContents starter() {
        return starter;
    }

    TutorialListener listener() {
        return listener;
    }

    Replays replays() {
        return replays;
    }

    Cameras cameras() {
        return cameras;
    }

    Pip pip() {
        return pip;
    }

    OptionalSteps optional() {
        return optional;
    }

    TutorialStats stats() {
        return stats;
    }
}
