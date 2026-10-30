package net.skybornmc.skyblock.tutorial;

import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.util.Scheduler;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Watch again (TUTORIAL.md §9.7, TQ25): shows every step's title and hint in order, a few seconds each, and plays the
 * step's camera (portal: spawn, arrive: island, depths: Depths) where it can. Replay mode changes nothing (no locks, no
 * rewards); stop it any time from {@code /tutorial}, skip a camera by sneaking.
 */
final class Replays {

    private static final long SHOT_TICKS = 70;

    private final TutorialModule module;
    private final Map<UUID, Scheduler.Task> running = new HashMap<>();

    Replays(TutorialModule module) {
        this.module = module;
    }

    boolean running(UUID player) {
        return running.containsKey(player);
    }

    void start(Player player) {
        stop(player);
        List<String> steps = module.engine().flow().steps();
        module.msg().send(player, "replay.started", Msg.number("total", steps.size()));
        Progress p = module.engine().progress(player.getUniqueId());
        if (p != null) {
            p.replays++;
            module.engine().save(p);
            module.engine().event(player, p, "replay");
        }
        show(player, steps, 0);
    }

    private void show(Player player, List<String> steps, int index) {
        if (!player.isOnline()) {
            running.remove(player.getUniqueId());
            return;
        }
        if (index >= steps.size()) {
            running.remove(player.getUniqueId());
            module.msg().send(player, "replay.done");
            return;
        }
        String step = steps.get(index);
        module.msg().title(player, "replay.shot", Msg.number("number", index + 1), Msg.number("total", steps.size()),
                Msg.c("step", module.engine().stepName(player, step)),
                Msg.c("objective", module.msg().component(player, "steps." + step + ".objective")));
        module.msg().send(player, "replay.line", Msg.number("number", index + 1), Msg.number("total", steps.size()),
                Msg.c("step", module.engine().stepName(player, step)), Msg.c("hint", module.engine().hint(player, step)));
        String camera = camera(step);
        if (camera != null) {
            Scheduler.Task marker = new Scheduler.Task() {
                private boolean cancelled;

                @Override
                public void cancel() {
                    cancelled = true;
                    module.cameras().stop(player);
                }

                @Override
                public boolean cancelled() {
                    return cancelled;
                }
            };
            running.put(player.getUniqueId(), marker);
            if (module.cameras().play(player, camera, () -> {
                if (running.get(player.getUniqueId()) == marker) {
                    show(player, steps, index + 1);
                }
            })) {
                return;
            }
        }
        running.put(player.getUniqueId(), module.core().scheduler().later("tutorial-replay", SHOT_TICKS,
                () -> show(player, steps, index + 1)));
    }

    /** The camera of a step in Watch again. */
    static String camera(String step) {
        return switch (step) {
            case Steps.PORTAL -> Cameras.SPAWN;
            case Steps.ARRIVE -> Cameras.ISLAND;
            case Steps.DEPTHS -> Cameras.DEPTHS;
            default -> null;
        };
    }

    void stop(Player player) {
        Scheduler.Task t = running.remove(player.getUniqueId());
        if (t != null) {
            t.cancel();
            module.msg().send(player, "replay.stopped");
        }
    }

    void stopAll() {
        running.values().forEach(Scheduler.Task::cancel);
        running.clear();
    }
}
