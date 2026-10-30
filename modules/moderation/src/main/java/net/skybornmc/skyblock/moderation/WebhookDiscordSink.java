package net.skybornmc.skyblock.moderation;

import net.skybornmc.skyblock.common.discord.DiscordSink;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * {@link DiscordSink} over Discord webhooks (spec 12.11). Off unless a channel has a webhook URL (from the untracked
 * secrets file written by {@code server/scripts/apply-secrets.sh}) <b>and</b> its feature switch is on: a disabled
 * channel makes no network call, starts no thread and logs nothing.
 *
 * <p>{@link #post} only queues (bounded; a full queue drops the new embed). One daemon thread sends, one at a time:
 * HTTP 2xx = done; 429 = wait {@code Retry-After} and retry (rate limit); 5xx or a network error = retry after a short
 * back-off; any other 4xx = dropped. At most {@code max-attempts} tries per embed. A failure never reaches the caller;
 * the console gets at most one warning per minute.</p>
 */
final class WebhookDiscordSink implements DiscordSink, AutoCloseable {

    /** One HTTP answer: status and how long Discord asks us to wait (ms, 0 = not given). */
    record Response(int status, long retryAfterMillis) {
    }

    /** Sends one JSON body (the real one uses {@link HttpClient}; tests use a fake). */
    interface Transport {
        Response post(String url, String json) throws IOException, InterruptedException;
    }

    /** Waits (tests record instead of sleeping). */
    interface Sleeper {
        void sleep(long millis) throws InterruptedException;
    }

    /** One queued message. */
    record Job(String url, String json, String channel) {
    }

    static final long WARN_EVERY = Duration.ofMinutes(1).toMillis();
    static final long BACKOFF = 1000;
    static final long MAX_WAIT = Duration.ofMinutes(2).toMillis();

    private final Supplier<Map<String, String>> urls;
    private final Predicate<String> switchedOn;
    private final Supplier<PunishConfig.Discord> config;
    private final Transport transport;
    private final Sleeper sleeper;
    private final LongSupplier clock;
    private final Logger log;
    private final BlockingQueue<Job> queue;
    private volatile Thread worker;
    private volatile boolean closed;
    /** Tests turn the worker thread off and send with {@link #drain()}. */
    private volatile boolean autoStart = true;
    private volatile long lastWarning = Long.MIN_VALUE;
    private int warnings;

    /**
     * @param urls       channel → webhook URL (secrets; empty or missing = off)
     * @param switchedOn whether a channel's feature is on (moderation: {@code extras.discord.enabled})
     */
    WebhookDiscordSink(Supplier<Map<String, String>> urls, Predicate<String> switchedOn, Supplier<PunishConfig.Discord> config,
                       Transport transport, Sleeper sleeper, LongSupplier clock, Logger log) {
        this.urls = urls;
        this.switchedOn = switchedOn;
        this.config = config;
        this.transport = transport;
        this.sleeper = sleeper;
        this.clock = clock;
        this.log = log;
        this.queue = new ArrayBlockingQueue<>(Math.max(1, config.get().queueSize()));
    }

    /** The real sink: HTTPS via the JDK client, real sleeps and clock. */
    static WebhookDiscordSink create(Supplier<Map<String, String>> urls, Predicate<String> switchedOn,
                                     Supplier<PunishConfig.Discord> config, Logger log) {
        return new WebhookDiscordSink(urls, switchedOn, config, httpTransport(), Thread::sleep, System::currentTimeMillis, log);
    }

    private String url(String channel) {
        String url = urls.get().get(channel);
        return url == null ? "" : url.strip();
    }

    @Override
    public boolean enabled(String channel) {
        if (closed || channel == null || !switchedOn.test(channel)) {
            return false;
        }
        String url = url(channel);
        return url.startsWith("https://");
    }

    @Override
    public void post(String channel, Embed embed) {
        try {
            if (!enabled(channel) || embed == null) {
                return;
            }
            if (!queue.offer(new Job(url(channel), DiscordPayload.json(embed), channel))) {
                warn("Discord: queue full (" + queue.size() + "), dropped one '" + channel + "' message");
                return;
            }
            startWorker();
        } catch (RuntimeException e) {
            warn("Discord: could not queue a message: " + e.getMessage());
        }
    }

    /** No worker thread: messages wait until {@link #drain()} (tests). */
    WebhookDiscordSink manual() {
        autoStart = false;
        return this;
    }

    /** Messages waiting to be sent. */
    int queued() {
        return queue.size();
    }

    /** Console warnings written so far (throttled to one per minute). */
    int warnings() {
        return warnings;
    }

    private synchronized void startWorker() {
        if (worker != null || closed || !autoStart) {
            return;
        }
        Thread t = new Thread(this::run, "Phoenix-Discord");
        t.setDaemon(true);
        worker = t;
        t.start();
    }

    private void run() {
        while (!closed) {
            try {
                Job job = queue.poll(30, TimeUnit.SECONDS);
                if (job != null) {
                    send(job);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (RuntimeException e) {
                warn("Discord: unexpected error: " + e);
            }
        }
    }

    /** Sends everything queued now, in the calling thread (tests; the worker does the same one by one). */
    int drain() throws InterruptedException {
        int sent = 0;
        Job job;
        while ((job = queue.poll()) != null) {
            if (send(job)) {
                sent++;
            }
        }
        return sent;
    }

    /** One embed with retries. Returns whether Discord accepted it. */
    boolean send(Job job) throws InterruptedException {
        int attempts = Math.max(1, config.get().maxAttempts());
        for (int attempt = 1; attempt <= attempts; attempt++) {
            Response r;
            try {
                r = transport.post(job.url(), job.json());
            } catch (IOException e) {
                if (attempt == attempts) {
                    warn("Discord: '" + job.channel() + "' webhook unreachable (" + e.getMessage() + "), message dropped");
                    return false;
                }
                sleeper.sleep(BACKOFF * attempt);
                continue;
            } catch (RuntimeException e) {
                warn("Discord: '" + job.channel() + "' webhook URL is not valid (" + e.getMessage() + "), message dropped");
                return false;
            }
            int s = r.status();
            if (s >= 200 && s < 300) {
                return true;
            }
            if (s == 429) {
                if (attempt == attempts) {
                    warn("Discord: '" + job.channel() + "' still rate limited, message dropped");
                    return false;
                }
                sleeper.sleep(Math.min(MAX_WAIT, Math.max(r.retryAfterMillis(), BACKOFF)));
                continue;
            }
            if (s >= 500) {
                if (attempt == attempts) {
                    warn("Discord: '" + job.channel() + "' webhook answered " + s + ", message dropped");
                    return false;
                }
                sleeper.sleep(BACKOFF * attempt);
                continue;
            }
            warn("Discord: '" + job.channel() + "' webhook refused the message (HTTP " + s + "); check the webhook URL");
            return false;
        }
        return false;
    }

    private synchronized void warn(String text) {
        long now = clock.getAsLong();
        if (lastWarning != Long.MIN_VALUE && now - lastWarning < WARN_EVERY) {
            return;
        }
        lastWarning = now;
        warnings++;
        log.warning(text);
    }

    @Override
    public void close() {
        closed = true;
        Thread t = worker;
        if (t != null) {
            t.interrupt();
        }
        queue.clear();
    }

    /** Parses {@code Retry-After} (seconds, may have decimals) to millis; 0 when missing or bad. */
    static long retryAfter(String header) {
        if (header == null || header.isBlank()) {
            return 0;
        }
        try {
            return (long) Math.ceil(Double.parseDouble(header.strip()) * 1000);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static Transport httpTransport() {
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        return (url, json) -> {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8)).build();
            HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
            return new Response(response.statusCode(), retryAfter(response.headers().firstValue("Retry-After").orElse(null)));
        };
    }
}
