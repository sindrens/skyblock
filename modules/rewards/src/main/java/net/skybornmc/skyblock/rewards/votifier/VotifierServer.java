package net.skybornmc.skyblock.rewards.votifier;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * Our own Votifier receiver (DESIGN §11): a plain TCP server that never touches the main thread.
 * <ul>
 *   <li>One daemon thread accepts; each connection runs on its own virtual thread.</li>
 *   <li>Strict limits: at most {@code maxConcurrent} open connections (more are closed at once), {@code perMinute}
 *       connections per address per minute, a hard deadline for the whole connection ({@code timeoutMillis}, so a slow
 *       sender can't hold it open byte by byte), and at most {@code maxPacketBytes} read.</li>
 *   <li>Every packet is checked by {@link VotifierProtocol}; a valid vote goes to {@code onVote} (called on the
 *       connection thread: hand it to the main thread there), everything else to {@code onReject}.</li>
 * </ul>
 */
public final class VotifierServer implements AutoCloseable {

    /** Limits of the receiver (from {@code votes.yml}). */
    public record Limits(int maxPacketBytes, int timeoutMillis, int perMinute, int maxConcurrent, boolean v1, boolean v2) {

        public Limits {
            maxPacketBytes = Math.max(VotifierProtocol.V1_BLOCK, Math.min(65_535, maxPacketBytes));
            timeoutMillis = Math.max(100, timeoutMillis);
            perMinute = Math.max(1, perMinute);
            maxConcurrent = Math.max(1, maxConcurrent);
        }
    }

    private final InetSocketAddress bind;
    private final Limits limits;
    private final Supplier<PrivateKey> rsa;
    private final Function<String, String> tokens;
    private final Consumer<Vote> onVote;
    private final BiConsumer<String, String> onReject;
    private final LongSupplier clock;
    private final IpRateLimiter rate;
    private final Semaphore slots;
    private final SecureRandom random = new SecureRandom();
    private final AtomicBoolean running = new AtomicBoolean();
    private volatile ServerSocket socket;
    private volatile Thread acceptor;

    /**
     * @param rsa      the v1 private key (null = v1 not ready)
     * @param tokens   the v2 token of a service name (null = unknown service)
     * @param onReject address + short reason (throttle it before logging)
     */
    public VotifierServer(InetSocketAddress bind, Limits limits, Supplier<PrivateKey> rsa, Function<String, String> tokens,
                          Consumer<Vote> onVote, BiConsumer<String, String> onReject, LongSupplier clock) {
        this.bind = bind;
        this.limits = limits;
        this.rsa = rsa;
        this.tokens = tokens;
        this.onVote = onVote;
        this.onReject = onReject;
        this.clock = clock;
        this.rate = new IpRateLimiter(limits.perMinute(), 60_000L);
        this.slots = new Semaphore(limits.maxConcurrent());
    }

    /** Binds and starts accepting (throws when the port is taken). */
    public void start() throws IOException {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        ServerSocket s = new ServerSocket();
        try {
            s.setReuseAddress(true);
            s.bind(bind, 50);
        } catch (IOException e) {
            running.set(false);
            s.close();
            throw e;
        }
        socket = s;
        Thread t = new Thread(this::acceptLoop, "Phoenix-Votifier");
        t.setDaemon(true);
        acceptor = t;
        t.start();
    }

    /** The bound port (useful when started on port 0 in tests). */
    public int port() {
        ServerSocket s = socket;
        return s == null ? -1 : s.getLocalPort();
    }

    public boolean running() {
        return running.get();
    }

    @Override
    public void close() {
        running.set(false);
        ServerSocket s = socket;
        socket = null;
        if (s != null) {
            try {
                s.close();
            } catch (IOException ignored) {
                // closing anyway
            }
        }
        Thread t = acceptor;
        if (t != null) {
            try {
                t.join(2_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void acceptLoop() {
        while (running.get()) {
            ServerSocket s = socket;
            if (s == null) {
                return;
            }
            Socket client;
            try {
                client = s.accept();
            } catch (IOException e) {
                if (running.get() && !(e instanceof SocketException)) {
                    onReject.accept("-", "accept failed: " + e.getMessage());
                }
                continue;
            }
            String address = address(client);
            if (!rate.allow(address, clock.getAsLong())) {
                closeQuietly(client);
                onReject.accept(address, "too many connections from this address (limit " + limits.perMinute() + " a minute)");
                continue;
            }
            if (!slots.tryAcquire()) {
                closeQuietly(client);
                onReject.accept(address, "too many open connections (limit " + limits.maxConcurrent() + ")");
                continue;
            }
            try {
                Thread.ofVirtual().name("Phoenix-Votifier-conn").start(() -> {
                    try {
                        handle(client, address);
                    } finally {
                        slots.release();
                    }
                });
            } catch (RuntimeException | OutOfMemoryError e) {
                slots.release();
                closeQuietly(client);
            }
        }
    }

    private void handle(Socket client, String address) {
        long deadline = clock.getAsLong() + limits.timeoutMillis();
        boolean v2 = false;
        try {
            client.setTcpNoDelay(true);
            client.setSoTimeout(limits.timeoutMillis());
            InputStream in = client.getInputStream();
            OutputStream out = client.getOutputStream();
            String challenge = VotifierProtocol.challenge(random);
            out.write(VotifierProtocol.greeting(challenge));
            out.flush();
            byte[] head = read(client, in, 2, deadline);
            if (VotifierProtocol.isV2(head[0], head[1])) {
                v2 = true;
                if (!limits.v2()) {
                    throw new VoteRejected("protocol v2 is turned off (votes.yml)");
                }
                byte[] len = read(client, in, 2, deadline);
                int length = (len[0] & 0xFF) << 8 | (len[1] & 0xFF);
                if (length <= 0 || length + 4 > limits.maxPacketBytes()) {
                    throw new VoteRejected("v2 packet of " + length + " bytes is empty or over the limit of " + limits.maxPacketBytes());
                }
                Vote vote = VotifierProtocol.parseV2(read(client, in, length, deadline), challenge, tokens);
                deliver(vote);
                out.write(VotifierProtocol.ok());
                out.flush();
            } else {
                if (!limits.v1()) {
                    throw new VoteRejected("protocol v1 is turned off (votes.yml)");
                }
                byte[] rest = read(client, in, VotifierProtocol.V1_BLOCK - 2, deadline);
                byte[] block = new byte[VotifierProtocol.V1_BLOCK];
                block[0] = head[0];
                block[1] = head[1];
                System.arraycopy(rest, 0, block, 2, rest.length);
                deliver(VotifierProtocol.parseV1(block, rsa.get()));
            }
        } catch (VoteRejected e) {
            if (v2) {
                try {
                    client.getOutputStream().write(VotifierProtocol.error(e.getMessage()));
                    client.getOutputStream().flush();
                } catch (IOException ignored) {
                    // the site is gone already
                }
            }
            onReject.accept(address, e.getMessage());
        } catch (SocketTimeoutException e) {
            onReject.accept(address, "timed out before a full packet arrived");
        } catch (IOException e) {
            onReject.accept(address, "connection error: " + e.getMessage());
        } catch (RuntimeException e) {
            onReject.accept(address, "unexpected error: " + e);
        } finally {
            closeQuietly(client);
        }
    }

    private void deliver(Vote vote) {
        try {
            onVote.accept(vote);
        } catch (RuntimeException e) {
            onReject.accept("-", "a valid vote could not be handed over: " + e);
        }
    }

    /** Reads exactly {@code n} bytes before the connection's deadline. */
    private byte[] read(Socket client, InputStream in, int n, long deadline) throws IOException, VoteRejected {
        byte[] buf = new byte[n];
        int off = 0;
        while (off < n) {
            long left = deadline - clock.getAsLong();
            if (left <= 0) {
                throw new SocketTimeoutException("deadline");
            }
            client.setSoTimeout((int) Math.max(1, Math.min(Integer.MAX_VALUE, left)));
            int r = in.read(buf, off, n - off);
            if (r < 0) {
                throw new VoteRejected("connection closed after " + off + " of " + n + " bytes");
            }
            off += r;
        }
        return buf;
    }

    private static String address(Socket s) {
        InetAddress a = s.getInetAddress();
        return a == null ? "?" : a.getHostAddress();
    }

    private static void closeQuietly(Socket s) {
        try {
            s.close();
        } catch (IOException ignored) {
            // nothing to do
        }
    }
}
