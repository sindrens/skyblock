package net.skybornmc.skyblock.hud;

import net.skybornmc.skyblock.common.hud.BoardTimers;
import org.bukkit.entity.Player;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * The {@link BoardTimers} implementation. Created once by {@link HudModule} and kept across {@code /skyblock reload hud}
 * and across turning the hud module off and on, so providers registered by other modules are never lost while those
 * modules are enabled (they unregister themselves when they disable).
 */
final class BoardTimerRegistry implements BoardTimers {

    private record Provider(String owner, Function<Player, Optional<Timer>> fn) {
    }

    private final CopyOnWriteArrayList<Provider> providers = new CopyOnWriteArrayList<>();
    /** Owners whose provider threw (logged once each). */
    private final Set<String> logged = ConcurrentHashMap.newKeySet();
    private final Consumer<String> warn;

    BoardTimerRegistry(Consumer<String> warn) {
        this.warn = warn;
    }

    @Override
    public void register(String owner, Function<Player, Optional<Timer>> provider) {
        providers.removeIf(p -> p.owner().equals(owner));
        providers.add(new Provider(owner, provider));
        logged.remove(owner);
    }

    @Override
    public void unregister(String owner) {
        providers.removeIf(p -> p.owner().equals(owner));
    }

    @Override
    public Optional<Timer> soonest(Player player) {
        Timer best = null;
        for (Provider p : providers) {
            Timer t;
            try {
                t = p.fn().apply(player).orElse(null);
            } catch (RuntimeException e) {
                if (logged.add(p.owner())) {
                    warn.accept("Scoreboard timer of " + p.owner() + " failed (skipped, logged once): " + e);
                }
                continue;
            }
            if (t != null && t.remainingMillis() > 0 && (best == null || t.remainingMillis() < best.remainingMillis())) {
                best = t;
            }
        }
        return Optional.ofNullable(best);
    }

    boolean isEmpty() {
        return providers.isEmpty();
    }
}
