package net.skybornmc.skyblock.motd;

import net.skybornmc.skyblock.common.config.ModuleConfig;
import net.skybornmc.skyblock.common.config.Setting;

import java.util.List;

/** Typed, validated settings of {@code motd.yml}. The {@code header} and {@code motds} texts are read as free text. */
public final class MotdConfig extends ModuleConfig {

    public enum Mode { ROTATE, RANDOM }

    @Setting(desc = "Which messages rotate (ids from 'motds' below), in order.")
    public List<String> active = List.of("coming-soon");

    @Setting(desc = "rotate = next message every rotate-seconds, random = random message each refresh.")
    public Mode mode = Mode.ROTATE;

    @Setting(desc = "Seconds each message is shown in rotate mode.", min = 1, max = 3600)
    public int rotateSeconds = 6;

    @Setting(path = "animation.enabled", desc = "Animate {phase} gradients.")
    public boolean animationEnabled = true;

    @Setting(path = "animation.cycle-seconds", desc = "Seconds for one full gradient cycle.", min = 1, max = 120)
    public int animationCycleSeconds = 6;

    @Setting(path = "animation.frames", desc = "Frames per cycle (one frame per server list refresh).", min = 1, max = 120)
    public int animationFrames = 12;

    @Setting(desc = "Center both lines in the server list.")
    public boolean center = true;

    @Setting(path = "countdown.target", desc = "Release date/time for {countdown}, e.g. 2027-06-01T18:00. Empty = messages with {countdown} are skipped.")
    public String countdownTarget = "";

    @Setting(path = "countdown.zone", desc = "Time zone of countdown.target.")
    public String countdownZone = "Europe/Oslo";

    @Setting(path = "countdown.finished", desc = "Text shown instead of the countdown once the time has passed.")
    public String countdownFinished = "NOW";

    @Setting(desc = "Shown when hovering over the player count (only the 16 classic colors work there).")
    public List<String> hover = List.of();

    @Override
    public String fileName() {
        return "motd.yml";
    }
}
