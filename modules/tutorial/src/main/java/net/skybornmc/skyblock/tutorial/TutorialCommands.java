package net.skybornmc.skyblock.tutorial;

import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.audit.AuditCategory;
import net.skybornmc.skyblock.common.command.Arg;
import net.skybornmc.skyblock.common.command.Args;
import net.skybornmc.skyblock.common.command.CommandFailure;
import net.skybornmc.skyblock.common.command.CommandSpec;
import net.skybornmc.skyblock.common.npc.NpcDirectory;
import net.skybornmc.skyblock.common.text.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.Optional;

/**
 * {@code /tutorial} (alias {@code /quest}): the book (Bedrock: the menu), {@code menu}, {@code skip} (confirm, no rewards),
 * {@code hide}/{@code show} (boss bar), {@code track} (a trail toward the next NPC); staff: {@code admin reset|stats|step}.
 */
final class TutorialCommands {

    private final TutorialModule m;

    TutorialCommands(TutorialModule m) {
        this.m = m;
    }

    void register() {
        Arg<Player> player = Args.onlinePlayer("player");
        Arg<String> step = Args.oneOf("step", () -> m.catalog().stepKeys());
        m.core().commands().register(CommandSpec.command("tutorial")
                .description("The guided tour: what to do next, step by step")
                .category("Info")
                .permission(TutorialModule.PERM_USE)
                .playerOnly()
                .aliases("quest")
                .example("tutorial")
                .executes(ctx -> m.open(ctx.player()))
                .sub(CommandSpec.sub("menu")
                        .description("The tutorial as a menu (Island, Farm and Mine buttons)")
                        .permission(TutorialModule.PERM_USE).playerOnly()
                        .executes(ctx -> m.core().menus().open(ctx.player(), new TutorialMenu(m, false))))
                .sub(CommandSpec.sub("skip")
                        .description("End the tutorial now (no rewards)")
                        .permission(TutorialModule.PERM_USE).playerOnly()
                        .confirm(15)
                        .executes(ctx -> skip(ctx.player())))
                .sub(CommandSpec.sub("hide")
                        .description("Hide the tutorial boss bar")
                        .permission(TutorialModule.PERM_USE).playerOnly()
                        .executes(ctx -> hide(ctx.player(), true)))
                .sub(CommandSpec.sub("show")
                        .description("Show the tutorial boss bar again")
                        .permission(TutorialModule.PERM_USE).playerOnly()
                        .executes(ctx -> hide(ctx.player(), false)))
                .sub(CommandSpec.sub("track")
                        .description("Show a trail toward the next NPC for a few seconds")
                        .permission(TutorialModule.PERM_USE).playerOnly()
                        .executes(ctx -> track(ctx.player())))
                .sub(CommandSpec.sub("admin")
                        .description("Staff tools for the tutorial")
                        .category("Staff")
                        .permission(TutorialModule.PERM_ADMIN)
                        .sub(CommandSpec.sub("reset")
                                .description("Start a player's tutorial over (paid rewards are not paid again)")
                                .permission(TutorialModule.PERM_ADMIN)
                                .arg(player)
                                .example("tutorial admin reset Steve")
                                .executes(ctx -> reset(ctx.sender(), ctx.get(player))))
                        .sub(CommandSpec.sub("step")
                                .description("Mark one step of a player as done")
                                .permission(TutorialModule.PERM_ADMIN)
                                .arg(player).arg(step)
                                .example("tutorial admin step Steve farm.harvest")
                                .executes(ctx -> complete(ctx.sender(), ctx.get(player), ctx.get(step))))
                        .sub(CommandSpec.sub("stats")
                                .description("How many players finished each chapter")
                                .permission(TutorialModule.PERM_ADMIN)
                                .executes(ctx -> stats(ctx.sender())))));
    }

    private Progress progress(Player p) {
        return m.progress(p).orElseThrow(() -> new CommandFailure(TutorialModule.ID, "loading"));
    }

    private void skip(Player p) {
        Progress pr = progress(p);
        if (pr.finished(m.catalog())) {
            throw new CommandFailure(TutorialModule.ID, "skip.already");
        }
        pr.skip(System.currentTimeMillis());
        m.hideBar(p);
        m.msg().send(p, "skip.done");
        m.core().audit().logPlayer(AuditCategory.TUTORIAL, "tutorial_skip", Actor.player(p), p.getUniqueId(), p.getName(), null,
                p.getLocation(), Map.of());
    }

    private void hide(Player p, boolean hide) {
        Progress pr = progress(p);
        pr.hidden(hide);
        if (hide) {
            m.hideBar(p);
        } else {
            m.showBar(p);
        }
        m.msg().send(p, hide ? "bar.hidden" : "bar.shown");
    }

    private void track(Player p) {
        Progress pr = progress(p);
        Optional<Catalog.Step> next = pr.next(m.catalog(), m.availability());
        Optional<Location> target = next.filter(s -> s.type() == net.skybornmc.skyblock.tutorial.StepType.NPC)
                .flatMap(s -> m.core().services().optional(NpcDirectory.class).flatMap(d -> d.npcByRole(s.target())))
                .map(NpcDirectory.NpcInfo::location);
        if (target.isEmpty() || target.get().getWorld() != p.getWorld()) {
            throw new CommandFailure(TutorialModule.ID, "track.none");
        }
        Color color;
        try {
            color = Color.fromRGB(Integer.parseInt(m.config().trackColor.replace("#", ""), 16));
        } catch (IllegalArgumentException e) {
            color = Color.YELLOW;
        }
        Particle.DustOptions dust = new Particle.DustOptions(color, 1.2f);
        Location goal = target.get().clone().add(0, 1, 0);
        int[] left = {m.config().trackSeconds * 4};
        m.msg().send(p, "track.on");
        net.skybornmc.skyblock.common.util.Scheduler.Task task = m.core().scheduler().repeat("tutorial:track", 0L, 5L, new Runnable() {
            @Override
            public void run() {
                if (left[0]-- <= 0 || !p.isOnline() || p.getWorld() != goal.getWorld()) {
                    return; // stops drawing; the task is cancelled below
                }
                Location from = p.getEyeLocation().subtract(0, 0.4, 0);
                Vector dir = goal.toVector().subtract(from.toVector());
                double length = Math.min(dir.length(), m.config().trackLength);
                if (length < 1) {
                    return;
                }
                dir.normalize();
                for (double d = 1; d <= length; d += 0.5) {
                    p.spawnParticle(Particle.DUST, from.clone().add(dir.clone().multiply(d)), 1, 0, 0, 0, 0, dust);
                }
            }
        });
        m.core().scheduler().later("tutorial:track-end", 20L * m.config().trackSeconds + 5, task::cancel);
    }

    private void reset(CommandSender sender, Player target) {
        m.store().reset(target.getUniqueId()).whenComplete((v, e) -> Bukkit.getScheduler().runTask(m.core().plugin(), () -> {
            if (e != null) {
                m.msg().send(sender, "admin.error");
                return;
            }
            if (target.isOnline()) {
                m.unloadWithoutSave(target);
                m.load(target);
            }
            m.msg().send(sender, "admin.reset", Msg.p("player", target.getName()));
            m.core().audit().logPlayer(AuditCategory.TUTORIAL, "tutorial_reset", actor(sender), target.getUniqueId(), target.getName(),
                    null, null, Map.of());
        }));
    }

    private void complete(CommandSender sender, Player target, String key) {
        Catalog.Step s = m.catalog().step(key).orElseThrow(() -> new CommandFailure(TutorialModule.ID, "admin.no-step", Msg.p("step", key)));
        Progress pr = progress(target);
        Progress.Change change = pr.complete(m.catalog(), s, m.availability(), System.currentTimeMillis());
        if (change.any()) {
            m.announce(target, pr, change);
        }
        m.msg().send(sender, "admin.step", Msg.p("player", target.getName()), Msg.p("step", s.text()));
        m.core().audit().logPlayer(AuditCategory.TUTORIAL, "tutorial_step", actor(sender), target.getUniqueId(), target.getName(),
                null, null, Map.of("step", key));
    }

    private void stats(CommandSender sender) {
        m.store().stats().whenComplete((st, e) -> Bukkit.getScheduler().runTask(m.core().plugin(), () -> {
            if (e != null) {
                m.msg().send(sender, "admin.error");
                return;
            }
            m.msg().send(sender, "admin.stats-head", Msg.p("started", st.started()), Msg.p("skipped", st.skipped()));
            for (Catalog.Chapter c : m.catalog().chapters()) {
                m.msg().send(sender, "admin.stats-line", Msg.p("chapter", c.name()), Msg.p("count", st.finished().getOrDefault(c.id(), 0L)));
            }
        }));
    }

    private static Actor actor(CommandSender sender) {
        return sender instanceof Player p ? Actor.player(p) : Actor.system("console");
    }
}
