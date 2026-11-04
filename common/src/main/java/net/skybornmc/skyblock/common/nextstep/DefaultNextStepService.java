package net.skybornmc.skyblock.common.nextstep;

import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The core's {@link NextStepService}: providers by priority (highest first, then the order they were added), then the
 * fallback (the start flow's objective, {@link ObjectiveNextStep}). A provider that throws is skipped and logged once,
 * so a broken module never hides the step of the others. Pure apart from the logger; unit-tested.
 */
public final class DefaultNextStepService implements NextStepService {

    private record Entry(String owner, int priority, long order, NextStepProvider provider) {
    }

    private static final Comparator<Entry> ORDER = Comparator.comparingInt(Entry::priority).reversed()
            .thenComparingLong(Entry::order);

    /** Sorted, replaced as a whole on every change (readers never see a half-built list). */
    private volatile List<Entry> entries = List.of();
    private final NextStepProvider fallback;
    private final Logger log;
    private final Set<String> reported = ConcurrentHashMap.newKeySet();
    private long added;

    /** @param fallback asked when no provider has a step (null = none) */
    public DefaultNextStepService(NextStepProvider fallback, Logger log) {
        this.fallback = fallback;
        this.log = Objects.requireNonNull(log, "log");
    }

    @Override
    public Optional<NextStep> current(Player player) {
        for (Entry e : entries) {
            Optional<NextStep> step = ask(e.owner(), e.provider(), player);
            if (step.isPresent()) {
                return step;
            }
        }
        return fallback == null ? Optional.empty() : ask("core", fallback, player);
    }

    private Optional<NextStep> ask(String owner, NextStepProvider provider, Player player) {
        try {
            Optional<NextStep> step = provider.next(player);
            return step == null ? Optional.empty() : step;
        } catch (RuntimeException ex) {
            if (reported.add(owner)) {
                log.log(Level.WARNING, "Next step provider of '" + owner + "' failed (skipped; logged once)", ex);
            }
            return Optional.empty();
        }
    }

    @Override
    public synchronized void register(String owner, int priority, NextStepProvider provider) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(provider, "provider");
        List<Entry> next = new ArrayList<>(entries);
        next.add(new Entry(owner, priority, added++, provider));
        next.sort(ORDER);
        entries = List.copyOf(next);
    }

    @Override
    public synchronized void unregisterAll(String owner) {
        entries = entries.stream().filter(e -> !e.owner().equals(owner)).toList();
        reported.remove(owner);
    }

    /** Number of providers (tests, /skyblock debug). */
    public int size() {
        return entries.size();
    }
}
