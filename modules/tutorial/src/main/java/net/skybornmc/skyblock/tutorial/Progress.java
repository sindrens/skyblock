package net.skybornmc.skyblock.tutorial;

import net.skybornmc.skyblock.common.tutorial.TutorialService.State;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * One account's tutorial progress in memory (loaded on join, saved on every change). Main thread only. The decisions
 * about it live in {@link StepRules}.
 */
final class Progress {

    final UUID player;
    State state;
    /** The step to do now; {@code null} when not running. */
    String step;
    long stepStartedAt;
    Long startedAt;
    Long finishedAt;
    int replays;
    int flowVersion;
    boolean packGiven;
    /** The finish reward was paid for this account (never twice, even after a staff reset). */
    boolean rewarded;
    /** Starter chest of this account's first island ({@code world:x,y,z}), or {@code null}. */
    String chest;
    /** Steps done → done early. */
    final Map<String, Boolean> done = new LinkedHashMap<>();
    /** Counters of counted steps. */
    final Map<String, Integer> counts = new HashMap<>();
    /** "I'm stuck" presses per step (this session only). */
    final Map<String, Integer> stuck = new HashMap<>();
    /** The starter chest was opened during this session (chest step). */
    boolean chestOpened;
    /** Last time the player did anything that counts (idle reminder). */
    long lastActivity;
    boolean idleReminded;

    Progress(UUID player, State state) {
        this.player = player;
        this.state = state;
    }

    static Progress fresh(UUID player) {
        return new Progress(player, State.NEW);
    }

    Set<String> doneSet() {
        return done.keySet();
    }

    boolean isDone(String step) {
        return done.containsKey(step);
    }

    int count(String step) {
        return counts.getOrDefault(step, 0);
    }
}
