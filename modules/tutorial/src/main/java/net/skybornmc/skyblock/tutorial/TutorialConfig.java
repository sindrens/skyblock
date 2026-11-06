package net.skybornmc.skyblock.tutorial;

import net.skybornmc.skyblock.common.config.ModuleConfig;
import net.skybornmc.skyblock.common.config.Setting;

import java.util.List;

/**
 * Settings of the tutorial ({@code tutorial.yml}; the chapters and idle lines live in the same file, see its header).
 * Texts: messages/tutorial.yml.
 */
public final class TutorialConfig extends ModuleConfig {

    @Setting(path = "enabled", desc = "The guided tour (/tutorial, the boss bar, the Next step and talking NPCs). Off: nothing counts and /start shows its plain menu.")
    public boolean enabled = true;

    @Setting(path = "next-step-priority", desc = "Priority of the tutorial's step in the shared Next step (scoreboard, Kindle, /help). Higher wins over other modules.", min = 0, max = 10_000)
    public int nextStepPriority = 100;

    // ------------------------------------------------------------------ boss bar

    @Setting(path = "bossbar.enabled", desc = "Show the current step in a boss bar (players turn it off with /tutorial hide).")
    public boolean bossbarEnabled = true;

    @Setting(path = "bossbar.color", desc = "Boss bar colour (UI_STYLE 6.4: the tutorial is yellow): PINK, BLUE, RED, GREEN, YELLOW, PURPLE or WHITE.")
    public String bossbarColor = "YELLOW";

    @Setting(path = "bossbar.overlay", desc = "Boss bar style: PROGRESS, NOTCHED_6, NOTCHED_10, NOTCHED_12 or NOTCHED_20.")
    public String bossbarOverlay = "PROGRESS";

    @Setting(path = "bossbar.hide-after-seconds", desc = "The bar hides itself after this long without progress and comes back on the next step (0 = always shown).", min = 0, max = 3600)
    public int bossbarHideSeconds = 30;

    // ------------------------------------------------------------------ talking NPCs

    @Setting(path = "dialog.chars-per-second", desc = "Typewriter speed of NPC lines (letters per second).", min = 5, max = 200)
    public double charsPerSecond = 40;

    @Setting(path = "dialog.wrap", desc = "Letters per line of the text above the NPC.", min = 10, max = 60)
    public int wrap = 30;

    @Setting(path = "dialog.max-lines", desc = "Lines shown above the NPC at once (a longer line is shown in parts).", min = 1, max = 4)
    public int maxLines = 2;

    @Setting(path = "dialog.hold-seconds", desc = "How long a finished line stays before the next one.", min = 0.5, max = 10)
    public double holdSeconds = 2.5;

    @Setting(path = "dialog.height", desc = "Height of the dialog text above the NPC's feet (above its name).", min = 1.5, max = 5)
    public double height = 3.0;

    @Setting(path = "dialog.max-distance", desc = "Walking further than this from the NPC ends the dialog (blocks).", min = 3, max = 32)
    public double maxDistance = 8;

    @Setting(path = "dialog.sound", desc = "Sound key (sounds.yml) played every 3 letters while an NPC talks. Empty = off (owner: fewer sounds).")
    public String sound = "";

    @Setting(path = "dialog.idle-lines", desc = "NPCs say one of their idle lines (tutorial.yml idle) when clicked outside a step, then open as usual.")
    public boolean idleLines = true;

    @Setting(path = "dialog.idle-cooldown-seconds", desc = "Seconds before the same NPC says an idle line to the same player again.", min = 0, max = 3600)
    public int idleCooldownSeconds = 60;

    // ------------------------------------------------------------------ places and tracking

    @Setting(path = "places", desc = "Places of 'place' steps and step clicks, as 'id: <command> <world> [x z radius]' lines: the command (no slash) takes the player there, the area is where the place counts.")
    public List<String> places = List.of(
            "spawn: spawn spawn",
            "farm: farm grind 0 0 45",
            "mine: mine grind 1000 0 35",
            "fish: fish grind 2600 0 40",
            "pve: pve grind -1500 0 70",
            "island: is");

    /** {@code places} before v2 (7 Nov 2026: /fish and /pve join the quest line). */
    static final List<String> PLACES_V1 = List.of(
            "spawn: spawn spawn",
            "farm: farm grind 0 0 45",
            "mine: mine grind 1000 0 35",
            "island: is");

    @Setting(path = "track.seconds", desc = "How long /tutorial track shows the trail toward the next NPC.", min = 2, max = 60)
    public int trackSeconds = 10;

    @Setting(path = "track.color", desc = "Colour of the trail particles (hex, like FFD21A).")
    public String trackColor = "FFD21A";

    @Setting(path = "track.length", desc = "Length of the trail in front of the player (blocks).", min = 2, max = 24)
    public int trackLength = 10;

    @Override
    public int version() {
        return 2;
    }

    /** v2 (7 Nov 2026): the places fish and pve for the chapters fish_pro and pve_pro. An owner-edited list is kept. */
    @Override
    public java.util.Map<Integer, java.util.Map<String, ValueChange>> valueChanges() {
        return java.util.Map.of(2, java.util.Map.of("places", ValueChange.of(PLACES_V1, new TutorialConfig().places)));
    }

    @Override
    public String fileName() {
        return Catalog.FILE;
    }

    @Override
    public String header() {
        return "Tutorial settings (the chapters and idle lines are in the same file). Texts: messages/tutorial.yml. Reload: /skyblock reload tutorial";
    }
}
