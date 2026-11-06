package net.skybornmc.skyblock.common.nextstep;

import net.skybornmc.skyblock.common.service.ServiceRegistry;
import org.bukkit.entity.Player;

import java.util.Optional;

/**
 * The one shared "what do I do next?" (plan 4 Nov C): Kindle's Next step button, /help "Find my way", the scoreboard
 * {@code Next:} line and the {@code next_step} placeholder all read {@link #current}, so they never disagree.
 *
 * <p>Modules add a {@link NextStepProvider} with a priority; the highest priority provider that has a step wins. With
 * none, the core falls back to the start flow's {@code objective} placeholder ("Talk to Rowan at spawn", runs
 * {@code /start}; {@code core.yml → next-step.*}). The future tutorial registers a higher priority provider.</p>
 *
 * <pre>{@code
 * core.services().get(NextStepService.class).register("tutorial", 100, player -> tutorial.step(player));
 * NextStepService.of(core.services()).current(player).ifPresent(step -> player.performCommand(step.command()));
 * }</pre>
 * Providers are removed automatically when their module disables.
 */
public interface NextStepService {

    /** Used when no core service is registered (unit tests): never has a step. */
    NextStepService NONE = new NextStepService() {
        @Override
        public Optional<NextStep> current(Player player) {
            return Optional.empty();
        }

        @Override
        public void register(String owner, int priority, NextStepProvider provider) {
        }

        @Override
        public void unregisterAll(String owner) {
        }
    };

    /** The registered service, or {@link #NONE}. */
    static NextStepService of(ServiceRegistry services) {
        return services == null ? NONE : services.optional(NextStepService.class).orElse(NONE);
    }

    /** The step for {@code player} (main thread), or empty when nobody has one. */
    Optional<NextStep> current(Player player);

    /** Adds a provider of module {@code owner}; higher {@code priority} is asked first (the start flow fallback is last). */
    void register(String owner, int priority, NextStepProvider provider);

    /** Removes every provider of {@code owner} (called when that module disables). */
    void unregisterAll(String owner);
}
