package net.skybornmc.skyblock.rewards.votifier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The receiver over a real localhost socket (no Minecraft server): real v1/v2 votes in, bad ones out, limits hold. */
class VotifierServerTest {

    static KeyPair keys;
    private final List<Vote> votes = new CopyOnWriteArrayList<>();
    private final List<String> rejects = new CopyOnWriteArrayList<>();
    private VotifierServer server;

    @BeforeAll
    static void keys() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        keys = gen.generateKeyPair();
    }

    private void start(VotifierServer.Limits limits) throws IOException {
        server = new VotifierServer(new InetSocketAddress("127.0.0.1", 0), limits, keys::getPrivate, VotifierProtocolTest.TOKENS,
                votes::add, (address, reason) -> rejects.add(reason), System::currentTimeMillis);
        server.start();
    }

    private void start() throws IOException {
        start(new VotifierServer.Limits(4096, 2000, 100, 8, true, true));
    }

    @AfterEach
    void stop() {
        if (server != null) {
            server.close();
        }
    }

    private Socket connect() throws IOException {
        Socket s = new Socket("127.0.0.1", server.port());
        s.setSoTimeout(5000);
        return s;
    }

    /** Reads the greeting line and returns the challenge. */
    private static String greeting(InputStream in) throws IOException {
        ByteArrayOutputStream line = new ByteArrayOutputStream();
        int b;
        while ((b = in.read()) != -1 && b != '\n') {
            line.write(b);
        }
        String text = line.toString(StandardCharsets.US_ASCII);
        assertTrue(text.startsWith("VOTIFIER 2 "), text);
        return text.substring("VOTIFIER 2 ".length());
    }

    private static String readAll(InputStream in) throws IOException {
        return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }

    private void await(int votes, int rejects) throws InterruptedException {
        long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < until && (this.votes.size() < votes || this.rejects.size() < rejects)) {
            Thread.sleep(10);
        }
    }

    @Test
    void v1VoteOverTheWire() throws Exception {
        start();
        try (Socket s = connect()) {
            greeting(s.getInputStream());
            s.getOutputStream().write(VotifierProtocolTest.v1(keys.getPublic(), "VOTE\nPlanetMinecraft.com\nSteve\n1.2.3.4\n99\n"));
            s.getOutputStream().flush();
            assertEquals("", readAll(s.getInputStream()), "v1 gets no answer, the connection just closes");
        }
        await(1, 0);
        assertEquals(List.of(new Vote("PlanetMinecraft.com", "Steve", "1.2.3.4", 99, 1)), votes);
        assertEquals(List.of(), rejects);
    }

    private static void sendV2(OutputStream out, byte[] message) throws IOException {
        DataOutputStream d = new DataOutputStream(out);
        d.writeShort(VotifierProtocol.V2_MAGIC);
        d.writeShort(message.length);
        d.write(message);
        d.flush();
    }

    @Test
    void v2VoteOverTheWireIsAnswered() throws Exception {
        start();
        try (Socket s = connect()) {
            String challenge = greeting(s.getInputStream());
            sendV2(s.getOutputStream(), VotifierProtocolTest.v2(VotifierProtocolTest.TOKEN, challenge, "PlanetMinecraft.com", "Alex"));
            assertEquals("{\"status\":\"ok\"}\r\n", readAll(s.getInputStream()));
        }
        await(1, 0);
        assertEquals("Alex", votes.getFirst().username());
        assertEquals(2, votes.getFirst().protocol());
    }

    @Test
    void v2WrongTokenGetsAnErrorAndNoVote() throws Exception {
        start();
        try (Socket s = connect()) {
            String challenge = greeting(s.getInputStream());
            sendV2(s.getOutputStream(), VotifierProtocolTest.v2("wrongwrongwrongwrongwrongw", challenge, "PlanetMinecraft.com", "Alex"));
            assertTrue(readAll(s.getInputStream()).contains("\"status\":\"error\""));
        }
        await(0, 1);
        assertEquals(List.of(), votes);
        assertEquals(1, rejects.size());
    }

    @Test
    void garbageAndOversizedPacketsAreRejected() throws Exception {
        start(new VotifierServer.Limits(512, 2000, 100, 8, true, true));
        try (Socket s = connect()) {
            greeting(s.getInputStream());
            s.getOutputStream().write(new byte[256]); // 256 zero bytes: not an RSA block of our key
            s.getOutputStream().flush();
            readAll(s.getInputStream());
        }
        try (Socket s = connect()) {
            greeting(s.getInputStream());
            DataOutputStream d = new DataOutputStream(s.getOutputStream());
            d.writeShort(VotifierProtocol.V2_MAGIC);
            d.writeShort(60_000); // claims a huge packet: closed without reading it
            d.flush();
            assertTrue(readAll(s.getInputStream()).contains("error"));
        }
        await(0, 2);
        assertEquals(List.of(), votes);
        assertEquals(2, rejects.size());
    }

    @Test
    void slowSenderIsCutOffAtTheDeadline() throws Exception {
        start(new VotifierServer.Limits(4096, 300, 100, 8, true, true));
        try (Socket s = connect()) {
            greeting(s.getInputStream());
            s.getOutputStream().write(new byte[]{1, 2, 3});
            s.getOutputStream().flush();
            long t = System.nanoTime();
            assertEquals("", readAll(s.getInputStream()));
            assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t) < 3000);
        }
        await(0, 1);
        assertTrue(rejects.getFirst().contains("timed out"), rejects.toString());
    }

    @Test
    void perAddressLimitClosesExtraConnections() throws Exception {
        start(new VotifierServer.Limits(4096, 500, 2, 8, true, true));
        for (int i = 0; i < 3; i++) {
            try (Socket s = connect()) {
                readAll(s.getInputStream()); // first two: greeting then timeout; third: closed at once
            }
        }
        await(0, 3);
        assertTrue(rejects.stream().anyMatch(r -> r.contains("too many connections")), rejects.toString());
    }

    @Test
    void protocolSwitchesAreHonoured() throws Exception {
        start(new VotifierServer.Limits(4096, 2000, 100, 8, false, true));
        try (Socket s = connect()) {
            greeting(s.getInputStream());
            s.getOutputStream().write(VotifierProtocolTest.v1(keys.getPublic(), "VOTE\nPlanetMinecraft.com\nSteve\n1.2.3.4\n99\n"));
            s.getOutputStream().flush();
            readAll(s.getInputStream());
        }
        await(0, 1);
        assertEquals(List.of(), votes);
        assertTrue(rejects.getFirst().contains("v1 is turned off"), rejects.toString());
    }
}
