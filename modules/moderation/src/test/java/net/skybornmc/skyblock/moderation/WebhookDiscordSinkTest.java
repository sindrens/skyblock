package net.skybornmc.skyblock.moderation;

import net.skybornmc.skyblock.common.discord.DiscordSink;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Spec 12.14 item 12: the Discord sink is off without a secret, queues, respects rate limits and never breaks anything. */
class WebhookDiscordSinkTest {

    private static final String URL = "https://discord.com/api/webhooks/1/secret";
    private static final DiscordSink.Embed EMBED = new DiscordSink.Embed("Ban", 0xE74C3C,
            List.of(new DiscordSink.Field("Player", "Steve", true)), "Phoenix", 1);

    /** A fake Discord: answers from a script, records calls. */
    private static final class FakeDiscord implements WebhookDiscordSink.Transport {
        final Deque<Object> answers = new ArrayDeque<>();
        final List<String> bodies = new ArrayList<>();

        @Override
        public WebhookDiscordSink.Response post(String url, String json) throws IOException {
            bodies.add(json);
            Object next = answers.isEmpty() ? new WebhookDiscordSink.Response(204, 0) : answers.poll();
            if (next instanceof IOException e) {
                throw e;
            }
            if (next instanceof RuntimeException e) {
                throw e;
            }
            return (WebhookDiscordSink.Response) next;
        }
    }

    private final FakeDiscord discord = new FakeDiscord();
    private final List<Long> sleeps = new ArrayList<>();
    private final AtomicLong clock = new AtomicLong(1_000_000);
    private final List<String> warnings = new ArrayList<>();
    private final Logger log = Logger.getAnonymousLogger();

    {
        log.setUseParentHandlers(false);
        log.addHandler(new Handler() {
            @Override
            public void publish(LogRecord record) {
                warnings.add(record.getMessage());
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        });
    }

    private WebhookDiscordSink sink(Map<String, String> urls, boolean on, int queue, int attempts) {
        PunishConfig.Discord cfg = new PunishConfig.Discord(on, "punishments", queue, attempts, PunishConfig.Discord.DEFAULT.colors());
        return new WebhookDiscordSink(() -> urls, channel -> cfg.enabled() && channel.equals(cfg.channel()), () -> cfg, discord,
                sleeps::add, clock::get, log);
    }

    @Test
    void offWithoutAWebhookOrSwitchMakesNoCallsAndNoNoise() throws Exception {
        for (WebhookDiscordSink s : List.of(sink(Map.of(), true, 10, 3), sink(Map.of("punishments", ""), true, 10, 3),
                sink(Map.of("punishments", URL), false, 10, 3), sink(Map.of("punishments", "http://insecure"), true, 10, 3),
                sink(Map.of("other", URL), true, 10, 3))) {
            assertFalse(s.enabled("punishments"));
            s.post("punishments", EMBED);
            assertEquals(0, s.queued());
            assertEquals(0, s.drain());
        }
        assertTrue(discord.bodies.isEmpty(), "no network call");
        assertTrue(warnings.isEmpty(), "no errors when off");
    }

    @Test
    void queuesAndSendsInOrder() throws Exception {
        WebhookDiscordSink s = sink(Map.of("punishments", URL), true, 2, 3).manual();
        assertTrue(s.enabled("punishments"));
        assertFalse(s.enabled("appeals"), "other channels stay off");
        s.post("punishments", EMBED);
        s.post("punishments", new DiscordSink.Embed("Mute", 1, List.of(), null, 0));
        s.post("punishments", EMBED); // queue-size 2: dropped, logged once
        assertEquals(2, s.queued());
        assertEquals(1, warnings.size());
        assertEquals(2, s.drain());
        assertEquals(2, discord.bodies.size());
        assertTrue(discord.bodies.get(0).contains("\"Ban\"") && discord.bodies.get(1).contains("\"Mute\""), "in order");
        assertEquals(0, s.queued());
    }

    @Test
    void rateLimitWaitsForRetryAfterThenSends() throws Exception {
        WebhookDiscordSink s = sink(Map.of("punishments", URL), true, 10, 3);
        discord.answers.add(new WebhookDiscordSink.Response(429, 2500));
        discord.answers.add(new WebhookDiscordSink.Response(204, 0));
        assertTrue(s.send(job(s)));
        assertEquals(List.of(2500L), sleeps, "waited Retry-After");
        assertEquals(2, discord.bodies.size());
        assertTrue(warnings.isEmpty());
    }

    @Test
    void serverErrorsAndNetworkErrorsAreRetriedThenDroppedWithOneWarning() throws Exception {
        WebhookDiscordSink s = sink(Map.of("punishments", URL), true, 10, 3);
        discord.answers.add(new WebhookDiscordSink.Response(502, 0));
        discord.answers.add(new IOException("timeout"));
        discord.answers.add(new WebhookDiscordSink.Response(500, 0));
        assertFalse(s.send(job(s)));
        assertEquals(3, discord.bodies.size(), "max-attempts 3");
        assertEquals(List.of(1000L, 2000L), sleeps, "back-off between tries");
        assertEquals(1, warnings.size());

        // a second failure within a minute: no second warning (at most one per minute)
        discord.answers.add(new WebhookDiscordSink.Response(404, 0));
        assertFalse(s.send(job(s)));
        assertEquals(1, warnings.size(), "throttled");
        clock.addAndGet(61_000);
        discord.answers.add(new WebhookDiscordSink.Response(401, 0));
        assertFalse(s.send(job(s)));
        assertEquals(2, warnings.size(), "a minute later it warns again");
    }

    @Test
    void refusedAndBrokenUrlsNeverThrow() throws Exception {
        WebhookDiscordSink s = sink(Map.of("punishments", URL), true, 10, 3);
        discord.answers.add(new WebhookDiscordSink.Response(400, 0));
        assertFalse(s.send(job(s)));
        assertEquals(1, discord.bodies.size(), "a 4xx is not retried");
        discord.answers.add(new IllegalArgumentException("bad uri"));
        assertDoesNotThrow(() -> s.send(job(s)));
    }

    @Test
    void stillRateLimitedAfterAllAttemptsIsDropped() throws Exception {
        WebhookDiscordSink s = sink(Map.of("punishments", URL), true, 10, 2);
        discord.answers.add(new WebhookDiscordSink.Response(429, 0));
        discord.answers.add(new WebhookDiscordSink.Response(429, 999_999_999));
        assertFalse(s.send(job(s)));
        assertEquals(List.of(WebhookDiscordSink.BACKOFF), sleeps, "no Retry-After: wait at least the back-off");
        assertEquals(2, discord.bodies.size());
    }

    @Test
    void retryAfterHeaderIsParsed() {
        assertEquals(1500, WebhookDiscordSink.retryAfter("1.5"));
        assertEquals(3000, WebhookDiscordSink.retryAfter("3"));
        assertEquals(0, WebhookDiscordSink.retryAfter(null));
        assertEquals(0, WebhookDiscordSink.retryAfter("soon"));
    }

    @Test
    void fullQueueDropsWithoutBlockingAndClosedSinkIsOff() {
        WebhookDiscordSink s = new WebhookDiscordSink(() -> Map.of("punishments", URL), c -> true,
                () -> new PunishConfig.Discord(true, "punishments", 1, 1, Map.of()), (url, json) -> {
                    try {
                        Thread.sleep(5_000); // a slow Discord keeps the worker busy
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return new WebhookDiscordSink.Response(204, 0);
                }, ms -> { }, clock::get, log);
        long start = System.nanoTime();
        for (int i = 0; i < 20; i++) {
            s.post("punishments", EMBED);
        }
        assertTrue(System.nanoTime() - start < 2_000_000_000L, "post() never waits for Discord");
        assertTrue(s.queued() <= 1, "bounded queue");
        assertTrue(s.warnings() >= 1, "a dropped message is logged (throttled)");
        s.close();
        assertFalse(s.enabled("punishments"));
        assertDoesNotThrow(() -> s.post("punishments", EMBED));
    }

    /** A job like post() builds it (send() is what the worker runs for each). */
    private static WebhookDiscordSink.Job job(WebhookDiscordSink s) {
        return new WebhookDiscordSink.Job(URL, DiscordPayload.json(EMBED), "punishments");
    }
}
