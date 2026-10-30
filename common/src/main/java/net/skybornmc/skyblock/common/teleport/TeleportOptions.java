package net.skybornmc.skyblock.common.teleport;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentLike;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.Optional;
import java.util.function.Function;

/**
 * What a caller adds to one teleport: the name shown in the warmup/countdown/failure texts, what happens on arrival,
 * an optional re-check (run when asked and again right before the teleport), an optional safe-destination hook (run
 * after the destination chunk is loaded) and the command offered as {@code [Try again]} after a cancel.
 *
 * <pre>{@code
 * teleports.request(player, spawn, TeleportReason.SPAWN, TeleportOptions.builder()
 *         .destination("spawn").onArrive(() -> msg.send(player, "spawn.arrived")).build());
 * }</pre>
 */
public final class TeleportOptions {

    private static final TeleportOptions NONE = builder().build();

    private final Component destination;
    private final Runnable onArrive;
    private final Function<Player, Optional<Component>> check;
    private final Function<Location, Optional<Location>> safe;
    private final String retryCommand;
    private final PlayerTeleportEvent.TeleportCause cause;
    private final boolean guards;
    private final boolean quiet;

    private TeleportOptions(Builder b) {
        this.destination = b.destination;
        this.onArrive = b.onArrive;
        this.check = b.check;
        this.safe = b.safe;
        this.retryCommand = b.retryCommand;
        this.cause = b.cause;
        this.guards = b.guards;
        this.quiet = b.quiet;
    }

    public static Builder builder() {
        return new Builder();
    }

    /** No name, no callbacks. */
    public static TeleportOptions none() {
        return NONE;
    }

    /** Shortcut: only a plain destination name and the arrival callback. */
    public static TeleportOptions to(String destination, Runnable onArrive) {
        return builder().destination(destination).onArrive(onArrive).build();
    }

    /** Shown as {@code <destination>}; empty component when none was given. */
    public Component destination() {
        return destination;
    }

    public Runnable onArrive() {
        return onArrive;
    }

    /** A refusal message when the teleport may not happen (any more), else empty. */
    public Optional<Component> check(Player player) {
        return check == null ? Optional.empty() : check.apply(player);
    }

    /** Where to land instead of {@code destination} (empty: nowhere safe); identity when no hook was given. */
    public Optional<Location> safe(Location destination) {
        return safe == null ? Optional.of(destination) : safe.apply(destination);
    }

    public String retryCommand() {
        return retryCommand;
    }

    public PlayerTeleportEvent.TeleportCause cause() {
        return cause;
    }

    /** False for system teleports that must never be refused by a guard (profile switch). */
    public boolean guards() {
        return guards;
    }

    /** True: no teleport sound and no failure text (the caller tells the player itself). */
    public boolean quiet() {
        return quiet;
    }

    public static final class Builder {
        private Component destination = Component.empty();
        private Runnable onArrive;
        private Function<Player, Optional<Component>> check;
        private Function<Location, Optional<Location>> safe;
        private String retryCommand;
        private PlayerTeleportEvent.TeleportCause cause = PlayerTeleportEvent.TeleportCause.COMMAND;
        private boolean guards = true;
        private boolean quiet;

        private Builder() {
        }

        /** Plain text (player names etc.; never parsed as MiniMessage). */
        public Builder destination(String plain) {
            this.destination = plain == null ? Component.empty() : Component.text(plain);
            return this;
        }

        /** Styled destination (from a messages file). */
        public Builder destination(ComponentLike component) {
            this.destination = component == null ? Component.empty() : component.asComponent();
            return this;
        }

        /** Runs on the main thread after a successful teleport. */
        public Builder onArrive(Runnable onArrive) {
            this.onArrive = onArrive;
            return this;
        }

        /** Re-check run when asked and right before the teleport: a message refuses it. Main thread. */
        public Builder check(Function<Player, Optional<Component>> check) {
            this.check = check;
            return this;
        }

        /** Safe-destination hook, run on the main thread once the destination chunk is loaded. */
        public Builder safe(Function<Location, Optional<Location>> safe) {
            this.safe = safe;
            return this;
        }

        /** Command (with slash) offered as a {@code [Try again]} click after a cancelled warmup. */
        public Builder retryCommand(String command) {
            this.retryCommand = command;
            return this;
        }

        public Builder cause(PlayerTeleportEvent.TeleportCause cause) {
            this.cause = cause;
            return this;
        }

        /** System teleport: guards never refuse it. */
        public Builder ignoreGuards() {
            this.guards = false;
            return this;
        }

        /** No teleport sound and no failure text. */
        public Builder quiet() {
            this.quiet = true;
            return this;
        }

        public TeleportOptions build() {
            return new TeleportOptions(this);
        }
    }
}
