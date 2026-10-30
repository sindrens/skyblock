package net.skybornmc.skyblock.common.discord;

import java.util.List;
import java.util.Objects;

/**
 * Posts embeds to named Discord webhooks (spec 12.11, later also 13.10 appeals and 14.9 notifications). Discord is
 * postponed by the owner (docs/LATER.md): every channel is <b>off</b> until a webhook URL is set on the server (a
 * secret, never in a tracked file) <b>and</b> the owning feature enables it. A disabled channel makes no network call
 * and logs nothing.
 *
 * <p>Implementations must never block the caller and never throw: posting is queued, sent asynchronously with retry and
 * Discord rate limits respected, and a failing webhook only produces a throttled console warning.</p>
 *
 * <pre>{@code
 * core.services().optional(DiscordSink.class).ifPresent(d -> d.post("punishments", embed));
 * }</pre>
 */
public interface DiscordSink {

    /** One embed field. Values are plain text (Discord markdown is escaped by the sink). */
    record Field(String name, String value, boolean inline) {
        public Field {
            Objects.requireNonNull(name, "name");
            value = value == null ? "-" : value;
        }
    }

    /**
     * One embed.
     *
     * @param color     RGB colour (0xRRGGBB)
     * @param timestamp epoch millis shown by Discord in the viewer's own time zone ({@code 0} = none)
     */
    record Embed(String title, int color, List<Field> fields, String footer, long timestamp) {
        public Embed {
            Objects.requireNonNull(title, "title");
            fields = fields == null ? List.of() : List.copyOf(fields);
        }
    }

    /** True if {@code channel} has a webhook and is switched on (callers may skip building the embed otherwise). */
    boolean enabled(String channel);

    /** Queues {@code embed} for {@code channel}; does nothing when the channel is off. Never blocks, never throws. */
    void post(String channel, Embed embed);
}
