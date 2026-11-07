package net.skybornmc.skyblock.tutorial;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * One account's tutorial (owner: per account, not per profile, so a profile wipe never pays the rewards again). Pure and
 * main-thread only; saved by {@link TutorialStore}.
 *
 * <p>Inside the current chapter every step may be done in any order (the first open one is shown as "next"); later
 * chapters wait. A step whose feature is off ({@code available} false, e.g. its NPC is not placed yet) counts as done, so
 * a missing NPC never blocks a new player.</p>
 */
final class Progress {

    /** What one signal changed. */
    record Change(List<Catalog.Step> stepsDone, List<Catalog.Chapter> chaptersDone, boolean counted) {

        boolean any() {
            return counted || !stepsDone.isEmpty() || !chaptersDone.isEmpty();
        }

        static final Change NONE = new Change(List.of(), List.of(), false);
    }

    private final UUID player;
    private final Set<String> done = new LinkedHashSet<>();
    private final Map<String, Long> counters = new HashMap<>();
    private final Set<String> chaptersDone = new LinkedHashSet<>();
    private long skippedAt;
    private boolean hidden;
    private boolean dirty;
    private long changedAt;

    Progress(UUID player) {
        this.player = player;
    }

    UUID player() {
        return player;
    }

    // ------------------------------------------------------------------ state

    boolean done(Catalog.Step step) {
        return done.contains(step.key());
    }

    long counter(Catalog.Step step) {
        return done(step) ? step.amount() : Math.min(step.amount(), counters.getOrDefault(step.key(), 0L));
    }

    boolean chapterDone(Catalog.Chapter c) {
        return chaptersDone.contains(c.id());
    }

    boolean skipped() {
        return skippedAt > 0;
    }

    long skippedAt() {
        return skippedAt;
    }

    boolean hidden() {
        return hidden;
    }

    void hidden(boolean hide) {
        if (hidden != hide) {
            hidden = hide;
            dirty = true;
        }
    }

    boolean dirty() {
        return dirty;
    }

    void clean() {
        dirty = false;
    }

    /** When the last step or chapter was finished (0 = never in this session). */
    long changedAt() {
        return changedAt;
    }

    Set<String> doneKeys() {
        return Set.copyOf(done);
    }

    Map<String, Long> counters() {
        return Map.copyOf(counters);
    }

    Set<String> chapterIds() {
        return Set.copyOf(chaptersDone);
    }

    /** Loading: restores saved state (no dirty flag). */
    void restore(Set<String> doneKeys, Map<String, Long> counts, Set<String> chapters, long skipped, boolean hide) {
        done.addAll(doneKeys);
        counters.putAll(counts);
        chaptersDone.addAll(chapters);
        skippedAt = skipped;
        hidden = hide;
    }

    // ------------------------------------------------------------------ the tour

    /** True when every chapter is done or the player skipped. */
    boolean finished(Catalog catalog) {
        return skipped() || current(catalog).isEmpty();
    }

    /** The first chapter that is not done. */
    Optional<Catalog.Chapter> current(Catalog catalog) {
        if (skipped()) {
            return Optional.empty();
        }
        for (Catalog.Chapter c : catalog.chapters()) {
            if (!chapterDone(c)) {
                return Optional.of(c);
            }
        }
        return Optional.empty();
    }

    /** The step shown as "next": the first open step of the current chapter. */
    Optional<Catalog.Step> next(Catalog catalog, Predicate<Catalog.Step> available) {
        return current(catalog).flatMap(c -> c.steps().stream().filter(s -> open(s, available)).findFirst());
    }

    /** Not done and its feature is on. */
    boolean open(Catalog.Step s, Predicate<Catalog.Step> available) {
        return !done(s) && available.test(s);
    }

    /** Steps of a chapter that count as done (really done, or unavailable here). */
    int doneCount(Catalog.Chapter c, Predicate<Catalog.Step> available) {
        if (chapterDone(c)) {
            return c.steps().size();
        }
        int n = 0;
        for (Catalog.Step s : c.steps()) {
            if (!open(s, available)) {
                n++;
            }
        }
        return n;
    }

    /** Open steps of the current chapter that this signal counts for. */
    List<Catalog.Step> listening(Catalog catalog, Signal signal, Predicate<Catalog.Step> available) {
        Optional<Catalog.Chapter> c = current(catalog);
        if (c.isEmpty()) {
            return List.of();
        }
        List<Catalog.Step> out = new ArrayList<>(2);
        for (Catalog.Step s : c.get().steps()) {
            if (open(s, available) && s.matches(signal)) {
                out.add(s);
            }
        }
        return out;
    }

    /**
     * Counts a signal for the open steps of the current chapter; finishes steps that reached their amount and the chapter
     * (and the chapters after it whose steps are all done or unavailable) when nothing is left.
     */
    Change apply(Catalog catalog, Signal signal, Predicate<Catalog.Step> available, long now) {
        List<Catalog.Step> hits = listening(catalog, signal, available);
        if (hits.isEmpty()) {
            return Change.NONE;
        }
        List<Catalog.Step> finished = new ArrayList<>();
        boolean counted = false;
        for (Catalog.Step s : hits) {
            long have = counters.getOrDefault(s.key(), 0L);
            long next = s.type().level() ? Math.max(have, signal.amount()) // a level, not a count
                    : have + Math.max(1, signal.amount()) < have ? Long.MAX_VALUE : have + Math.max(1, signal.amount());
            if (next >= s.amount()) {
                counters.remove(s.key());
                done.add(s.key());
                finished.add(s);
            } else {
                counters.put(s.key(), next);
                counted = true;
            }
        }
        dirty = true;
        if (!finished.isEmpty()) {
            changedAt = now;
        }
        return new Change(finished, settle(catalog, available, now), counted);
    }

    /** Marks one step done (staff {@code /tutorial admin step}); finishes chapters that are complete. */
    Change complete(Catalog catalog, Catalog.Step step, Predicate<Catalog.Step> available, long now) {
        if (done(step)) {
            return Change.NONE;
        }
        counters.remove(step.key());
        done.add(step.key());
        dirty = true;
        changedAt = now;
        return new Change(List.of(step), settle(catalog, available, now), false);
    }

    /**
     * Finishes the current chapter while all its steps count as done, and the next one too (a chapter made only of
     * unavailable steps). Call after loading and whenever availability may have changed.
     */
    List<Catalog.Chapter> settle(Catalog catalog, Predicate<Catalog.Step> available, long now) {
        List<Catalog.Chapter> out = new ArrayList<>(1);
        Optional<Catalog.Chapter> c = current(catalog);
        while (c.isPresent() && doneCount(c.get(), available) >= c.get().steps().size()) {
            chaptersDone.add(c.get().id());
            out.add(c.get());
            dirty = true;
            changedAt = now;
            c = current(catalog);
        }
        return out;
    }

    /** {@code /tutorial skip}: the tour ends without rewards. */
    void skip(long now) {
        if (skippedAt == 0) {
            skippedAt = Math.max(1, now);
            dirty = true;
            changedAt = now;
        }
    }

    /** Overall progress 0..1: done steps of all chapters (done chapters count fully). */
    double overall(Catalog catalog, Predicate<Catalog.Step> available) {
        if (skipped()) {
            return 1;
        }
        int total = 0;
        int have = 0;
        for (Catalog.Chapter c : catalog.chapters()) {
            total += c.steps().size();
            have += doneCount(c, available);
        }
        return total == 0 ? 1 : (double) have / total;
    }
}
