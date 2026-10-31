package net.skybornmc.skyblock.rewards.votifier;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.crypto.Cipher;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** NuVotifier v1 (RSA) and v2 (token) payloads built exactly like a vote site builds them, plus bad input. */
class VotifierProtocolTest {

    static KeyPair keys;
    static KeyPair otherKeys;
    static final String TOKEN = "abcdefghijklmnopqrstuvwxyz";
    static final Function<String, String> TOKENS = s -> Map.of("PlanetMinecraft.com", TOKEN).get(s);

    @BeforeAll
    static void keys() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        keys = gen.generateKeyPair();
        otherKeys = gen.generateKeyPair();
    }

    /** A v1 block as a site sends it. */
    static byte[] v1(PublicKey key, String plain) throws Exception {
        Cipher c = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        c.init(Cipher.ENCRYPT_MODE, key);
        return c.doFinal(plain.getBytes(StandardCharsets.UTF_8));
    }

    /** A v2 message (the JSON after magic + length) as a site sends it. */
    static byte[] v2(String token, String challenge, String service, String user) {
        JsonObject payload = new JsonObject();
        payload.addProperty("serviceName", service);
        payload.addProperty("username", user);
        payload.addProperty("address", "203.0.113.7");
        payload.addProperty("timestamp", 1_761_900_000_000L);
        payload.addProperty("challenge", challenge);
        String p = payload.toString();
        JsonObject outer = new JsonObject();
        outer.addProperty("payload", p);
        outer.addProperty("signature", Base64.getEncoder().encodeToString(VotifierProtocol.hmac(token, p)));
        return outer.toString().getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void greetingLooksLikeNuVotifier() {
        String challenge = VotifierProtocol.challenge(new SecureRandom());
        assertEquals(26, challenge.length());
        assertEquals("VOTIFIER 2 " + challenge + "\n", new String(VotifierProtocol.greeting(challenge), StandardCharsets.US_ASCII));
        assertTrue(VotifierProtocol.isV2(0x73, 0x3A));
        assertFalse(VotifierProtocol.isV2(0x3A, 0x73));
        assertArrayEquals("{\"status\":\"ok\"}\r\n".getBytes(StandardCharsets.UTF_8), VotifierProtocol.ok());
        assertTrue(new String(VotifierProtocol.error("bad"), StandardCharsets.UTF_8).contains("\"status\":\"error\""));
    }

    @Test
    void v1RealPayload() throws Exception {
        byte[] block = v1(keys.getPublic(), "VOTE\nPlanetMinecraft.com\nSteve\n203.0.113.7\n1761900000\n");
        assertEquals(256, block.length);
        Vote v = VotifierProtocol.parseV1(block, keys.getPrivate());
        assertEquals(new Vote("PlanetMinecraft.com", "Steve", "203.0.113.7", 1_761_900_000L, 1), v);
    }

    @Test
    void v1TimestampThatIsNotANumberIsZero() throws Exception {
        Vote v = VotifierProtocol.parseV1(v1(keys.getPublic(), "VOTE\nTopG.org\n.Bedrock_Guy\n\n2026-10-31 12:00\n"), keys.getPrivate());
        assertEquals(0, v.timestamp());
        assertEquals(".Bedrock_Guy", v.username());
        assertEquals("", v.address());
    }

    @Test
    void v1BadInputIsRejected() throws Exception {
        assertThrows(VoteRejected.class, () -> VotifierProtocol.parseV1(new byte[255], keys.getPrivate()));
        assertThrows(VoteRejected.class, () -> VotifierProtocol.parseV1(new byte[256], keys.getPrivate()));
        assertThrows(VoteRejected.class, () -> VotifierProtocol.parseV1(null, keys.getPrivate()));
        // Encrypted with another server's key (the site has the wrong public key).
        assertThrows(VoteRejected.class, () -> VotifierProtocol.parseV1(v1(otherKeys.getPublic(),
                "VOTE\nPlanetMinecraft.com\nSteve\n1.2.3.4\n1\n"), keys.getPrivate()));
        // Decrypts, but is not a vote / misses fields.
        assertThrows(VoteRejected.class, () -> VotifierProtocol.parseV1(v1(keys.getPublic(), "HELLO\na\nb\nc\nd\n"), keys.getPrivate()));
        assertThrows(VoteRejected.class, () -> VotifierProtocol.parseV1(v1(keys.getPublic(), "VOTE\nsite\nSteve\n"), keys.getPrivate()));
        // Empty or strange names, control characters.
        assertThrows(VoteRejected.class, () -> VotifierProtocol.parseV1(v1(keys.getPublic(), "VOTE\nsite\n\n1.2.3.4\n1\n"), keys.getPrivate()));
        assertThrows(VoteRejected.class, () -> VotifierProtocol.parseV1(v1(keys.getPublic(), "VOTE\nsite\n<red>x\n1.2.3.4\n1\n"),
                keys.getPrivate()));
        assertThrows(VoteRejected.class, () -> VotifierProtocol.parseV1(v1(keys.getPublic(),
                "VOTE\nsite\nSteve\u0007\n1.2.3.4\n1\n"), keys.getPrivate()));
        assertThrows(VoteRejected.class, () -> VotifierProtocol.parseV1(v1(keys.getPublic(),
                "VOTE\n\nSteve\n1.2.3.4\n1\n"), keys.getPrivate()));
        assertThrows(VoteRejected.class, () -> VotifierProtocol.parseV1(v1(keys.getPublic(),
                "VOTE\nsite\nAVeryVeryLongNameThatIsLongerThanAnyName\n1.2.3.4\n1\n"), keys.getPrivate()));
        // No key yet.
        assertThrows(VoteRejected.class, () -> VotifierProtocol.parseV1(new byte[256], null));
    }

    @Test
    void v2RealPayload() throws Exception {
        Vote v = VotifierProtocol.parseV2(v2(TOKEN, "chal", "PlanetMinecraft.com", "Alex"), "chal", TOKENS);
        assertEquals(new Vote("PlanetMinecraft.com", "Alex", "203.0.113.7", 1_761_900_000_000L, 2), v);
    }

    @Test
    void v2BadInputIsRejected() {
        // Wrong token (signature), wrong challenge (replay), unknown service.
        assertThrows(VoteRejected.class, () -> VotifierProtocol.parseV2(v2("zzzzzzzzzzzzzzzzzzzzzzzzzz", "chal", "PlanetMinecraft.com", "Alex"),
                "chal", TOKENS));
        assertThrows(VoteRejected.class, () -> VotifierProtocol.parseV2(v2(TOKEN, "old-challenge", "PlanetMinecraft.com", "Alex"),
                "chal", TOKENS));
        assertThrows(VoteRejected.class, () -> VotifierProtocol.parseV2(v2(TOKEN, "chal", "Unknown.net", "Alex"), "chal", TOKENS));
        // Malformed JSON, wrong types, missing parts, bad base64.
        for (String bad : new String[]{"", "not json", "[]", "{}", "{\"payload\":1}", "{\"payload\":\"x\",\"signature\":\"y\"}",
                "{\"payload\":\"{}\",\"signature\":\"AAAA\"}", "{\"payload\":{\"a\":1},\"signature\":\"AAAA\"}",
                "{\"payload\":\"{\\\"serviceName\\\":\\\"PlanetMinecraft.com\\\"}\",\"signature\":\"%%%\"}"}) {
            assertThrows(VoteRejected.class, () -> VotifierProtocol.parseV2(bad.getBytes(StandardCharsets.UTF_8), "chal", TOKENS), bad);
        }
        assertThrows(VoteRejected.class, () -> VotifierProtocol.parseV2(null, "chal", TOKENS));
        // Signed correctly, but the name is bad.
        assertThrows(VoteRejected.class, () -> VotifierProtocol.parseV2(v2(TOKEN, "chal", "PlanetMinecraft.com", ""), "chal", TOKENS));
        assertThrows(VoteRejected.class, () -> VotifierProtocol.parseV2(v2(TOKEN, "chal", "PlanetMinecraft.com", "a\nb"), "chal", TOKENS));
    }
}
