package net.skybornmc.skyblock.rewards;

import net.skybornmc.skyblock.rewards.votifier.ThrottledLog;
import net.skybornmc.skyblock.rewards.votifier.VoteKeys;
import net.skybornmc.skyblock.rewards.votifier.VotifierServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.SecureRandom;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * Starts, restarts and stops our Votifier receiver ({@link VotifierServer}) from {@code votes.yml}, entirely off the main
 * thread (its own setup thread): makes or loads the RSA key pair and the per-site tokens in
 * {@code plugins/PhoenixSkyblock/votifier/} even while the receiver is off, so the owner can register on the vote sites
 * first and switch it on after. A reload restarts it only when its address or limits changed.
 */
final class VoteReceiver {

    private final RewardsModule m;
    private final Path dir;
    private final ThrottledLog rejects;
    private final SecureRandom random = new SecureRandom();
    private ExecutorService setup;
    private volatile KeyPair rsa;
    private volatile Map<String, String> tokens = Map.of();
    private volatile VotifierServer server;
    private volatile String running = "";

    VoteReceiver(RewardsModule module) {
        this.m = module;
        this.dir = module.core.plugin().getDataFolder().toPath().resolve("votifier");
        this.rejects = new ThrottledLog(60_000L, line -> module.core.plugin().getLogger().warning(line));
    }

    /** Applies the current settings (enable and every reload). */
    void apply() {
        if (setup == null || setup.isShutdown()) {
            setup = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "Phoenix-Votifier-setup");
                t.setDaemon(true);
                return t;
            });
        }
        VotesSettings s = m.votesSettings;
        rejects.interval(s.rejectLogSeconds * 1000L);
        List<String> siteIds = m.votes().sites().stream().map(VoteRules.Site::id).toList();
        boolean wanted = s.enabled && s.receiverEnabled;
        VotifierServer.Limits limits = new VotifierServer.Limits(s.maxPacketBytes, s.timeoutMs, s.maxPerMinute, s.maxOpen, s.v1, s.v2);
        String config = s.host.strip() + ":" + s.port + ":" + limits;
        setup.execute(() -> {
            try {
                if (rsa == null) {
                    rsa = VoteKeys.loadOrCreateRsa(dir);
                }
                tokens = Map.copyOf(VoteKeys.loadOrCreateTokens(dir, siteIds, random));
            } catch (IOException | RuntimeException e) {
                m.core.plugin().getLogger().log(Level.SEVERE, "Votifier: could not load or make the keys in " + dir
                        + " (the receiver stays off): " + e.getMessage(), e);
                stopNow();
                return;
            }
            if (!wanted) {
                if (server != null) {
                    stopNow();
                    m.core.plugin().getLogger().info("Votifier: receiver stopped (votes.yml -> receiver.enabled is off).");
                }
                return;
            }
            if (server != null && server.running() && config.equals(running)) {
                return;
            }
            stopNow();
            VotifierServer next = new VotifierServer(new InetSocketAddress(s.host.strip(), s.port), limits,
                    () -> rsa == null ? null : rsa.getPrivate(), this::tokenFor, m.votes()::received, this::reject,
                    System::currentTimeMillis);
            try {
                next.start();
                server = next;
                running = config;
                m.core.plugin().getLogger().info("Votifier: receiver listening on " + s.host.strip() + ":" + next.port() + " (v1 "
                        + (s.v1 ? "on" : "off") + ", v2 " + (s.v2 ? "on" : "off") + "). Public key: votifier/rsa/public.key, tokens: "
                        + "votifier/tokens.txt.");
            } catch (IOException | RuntimeException e) {
                m.core.plugin().getLogger().severe("Votifier: could not listen on " + s.host.strip() + ":" + s.port + " (" + e.getMessage()
                        + "). Is the port in use? Votes are not received until this is fixed and the module reloads.");
            }
        });
    }

    /** Stops the receiver and the setup thread (module disable); waits a moment for the socket to close. */
    void stop() {
        ExecutorService s = setup;
        setup = null;
        if (s != null) {
            s.execute(this::stopNow);
            s.shutdown();
            try {
                if (!s.awaitTermination(3, TimeUnit.SECONDS)) {
                    s.shutdownNow();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        stopNow();
    }

    private void stopNow() {
        VotifierServer s = server;
        server = null;
        running = "";
        if (s != null) {
            s.close();
        }
    }

    /** The v2 token of a vote's service name, or null when no site uses it (any thread). */
    String tokenFor(String service) {
        Optional<VoteRules.Site> site = VoteRules.site(m.votes().sites(), service);
        return site.map(x -> tokens.get(x.id())).orElse(null);
    }

    /** A rejected connection or vote, written to the console at most once per interval (any thread). */
    void reject(String address, String reason) {
        rejects.reject(address, reason, System.currentTimeMillis());
    }

    boolean listening() {
        VotifierServer s = server;
        return s != null && s.running();
    }
}
