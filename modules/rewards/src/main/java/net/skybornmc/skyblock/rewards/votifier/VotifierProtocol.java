package net.skybornmc.skyblock.rewards.votifier;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * The NuVotifier wire protocol (pure, no sockets, no Bukkit; unit-tested with real payloads). Every vote site that
 * "supports Votifier" speaks one of these:
 * <ul>
 *   <li><b>Greeting</b> (server → site, right after connecting): {@code VOTIFIER 2 <challenge>\n}.</li>
 *   <li><b>v1</b> (site → server): one 256-byte block, RSA/ECB/PKCS1 encrypted with our public key (2048 bit). Plain
 *       text {@code VOTE\n<service>\n<username>\n<address>\n<timestamp>\n}. No answer.</li>
 *   <li><b>v2</b> (site → server): {@code 0x733A} (2 bytes), a 2-byte length, then JSON
 *       {@code {"payload":"<json>","signature":"<base64 HMAC-SHA256(payload, token)>"}}; the payload holds
 *       {@code serviceName, username, address, timestamp, challenge}. The token is per site. Answer
 *       {@code {"status":"ok"}\r\n} or {@code {"status":"error",...}\r\n}.</li>
 * </ul>
 * Anything else is rejected: wrong size, bad padding, bad JSON, unknown site, wrong signature, wrong challenge, empty or
 * strange names.
 */
public final class VotifierProtocol {

    /** Size of a v1 block (a 2048-bit RSA key). */
    public static final int V1_BLOCK = 256;
    /** First two bytes of a v2 packet. */
    public static final int V2_MAGIC = 0x733A;

    static final int MAX_SERVICE = 64;
    static final int MAX_USERNAME = 36;
    static final int MAX_ADDRESS = 64;
    static final int MAX_TIMESTAMP = 32;

    /** Letters, digits, '_', '.', '-', '*' and spaces (Bedrock gamertags; Floodgate turns spaces into '_'). */
    private static final Pattern USERNAME = Pattern.compile("[A-Za-z0-9_.*\\- ]{1," + MAX_USERNAME + "}");
    /** Printable ASCII without control characters. */
    private static final Pattern PRINTABLE = Pattern.compile("[\\x20-\\x7E]*");
    private static final char[] BASE32 = "abcdefghijklmnopqrstuvwxyz234567".toCharArray();

    private VotifierProtocol() {
    }

    /** The greeting line sent right after a site connects. */
    public static byte[] greeting(String challenge) {
        return ("VOTIFIER 2 " + challenge + "\n").getBytes(StandardCharsets.US_ASCII);
    }

    /** A fresh random challenge (26 base32 characters = 130 bits), one per connection. */
    public static String challenge(SecureRandom random) {
        return token(random);
    }

    /** A fresh random token or challenge (26 base32 characters, like NuVotifier). */
    public static String token(SecureRandom random) {
        StringBuilder sb = new StringBuilder(26);
        for (int i = 0; i < 26; i++) {
            sb.append(BASE32[random.nextInt(BASE32.length)]);
        }
        return sb.toString();
    }

    /** True when the first two bytes of a packet are the v2 magic. */
    public static boolean isV2(int first, int second) {
        return ((first & 0xFF) << 8 | (second & 0xFF)) == V2_MAGIC;
    }

    /** Answer to a valid v2 vote. */
    public static byte[] ok() {
        return "{\"status\":\"ok\"}\r\n".getBytes(StandardCharsets.UTF_8);
    }

    /** Answer to a rejected v2 vote (the reason is short and never contains secrets). */
    public static byte[] error(String reason) {
        JsonObject o = new JsonObject();
        o.addProperty("status", "error");
        o.addProperty("cause", "VoteRejected");
        o.addProperty("error", reason == null ? "" : reason);
        return (o + "\r\n").getBytes(StandardCharsets.UTF_8);
    }

    // ================================================================== v1

    /** Decrypts and parses a v1 block. */
    public static Vote parseV1(byte[] block, PrivateKey key) throws VoteRejected {
        if (block == null || block.length != V1_BLOCK) {
            throw new VoteRejected("v1 block must be " + V1_BLOCK + " bytes");
        }
        if (key == null) {
            throw new VoteRejected("v1 is not ready (no RSA key)");
        }
        byte[] plain;
        try {
            Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
            cipher.init(Cipher.DECRYPT_MODE, key);
            plain = cipher.doFinal(block);
        } catch (GeneralSecurityException | RuntimeException e) {
            throw new VoteRejected("v1 block could not be decrypted (wrong public key on the vote site, or not a vote)");
        }
        String text = new String(plain, StandardCharsets.UTF_8);
        String[] parts = text.split("\n", -1);
        if (parts.length < 5 || !"VOTE".equals(parts[0])) {
            throw new VoteRejected("v1 block is not a vote");
        }
        return vote(parts[1], parts[2], parts[3], parts[4], 1);
    }

    // ================================================================== v2

    /**
     * Parses and checks a v2 message (the JSON after magic and length).
     *
     * @param tokenFor the token of a service name, or null when no site uses that service name
     */
    public static Vote parseV2(byte[] message, String challenge, Function<String, String> tokenFor) throws VoteRejected {
        JsonObject outer = object(message == null ? "" : new String(message, StandardCharsets.UTF_8), "v2 message");
        String payload = string(outer, "payload");
        String signature = string(outer, "signature");
        if (payload == null || signature == null) {
            throw new VoteRejected("v2 message needs payload and signature");
        }
        JsonObject inner = object(payload, "v2 payload");
        String service = string(inner, "serviceName");
        if (service == null || service.isBlank()) {
            throw new VoteRejected("v2 payload has no serviceName");
        }
        checkField(service, MAX_SERVICE, "serviceName");
        String token = tokenFor.apply(service);
        if (token == null || token.isEmpty()) {
            throw new VoteRejected("unknown service '" + service + "' (add it to a site in votes.yml)");
        }
        byte[] given;
        try {
            given = Base64.getDecoder().decode(signature);
        } catch (IllegalArgumentException e) {
            throw new VoteRejected("v2 signature is not base64");
        }
        if (!MessageDigest.isEqual(hmac(token, payload), given)) {
            throw new VoteRejected("v2 signature does not match (wrong token for '" + service + "'?)");
        }
        String sentChallenge = string(inner, "challenge");
        if (sentChallenge == null || challenge == null
                || !MessageDigest.isEqual(sentChallenge.getBytes(StandardCharsets.UTF_8), challenge.getBytes(StandardCharsets.UTF_8))) {
            throw new VoteRejected("v2 challenge does not match");
        }
        return vote(service, string(inner, "username"), string(inner, "address"), string(inner, "timestamp"), 2);
    }

    /** HMAC-SHA256 of the payload with a token (what a v2 site signs). */
    public static byte[] hmac(String token, String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(token.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 missing", e);
        }
    }

    // ================================================================== shared

    private static JsonObject object(String json, String what) throws VoteRejected {
        try {
            JsonElement e = JsonParser.parseString(json);
            if (!e.isJsonObject()) {
                throw new VoteRejected(what + " is not a JSON object");
            }
            return e.getAsJsonObject();
        } catch (RuntimeException e) {
            throw new VoteRejected(what + " is not valid JSON");
        }
    }

    /** A string or number field as text; null when missing or another type. */
    private static String string(JsonObject o, String field) {
        JsonElement e = o.get(field);
        if (e == null || !e.isJsonPrimitive()) {
            return null;
        }
        JsonPrimitive p = e.getAsJsonPrimitive();
        return p.isString() || p.isNumber() ? p.getAsString() : null;
    }

    private static Vote vote(String service, String username, String address, String timestamp, int protocol) throws VoteRejected {
        service = service == null ? "" : service.strip();
        username = username == null ? "" : username.strip();
        address = address == null ? "" : address.strip();
        timestamp = timestamp == null ? "" : timestamp.strip();
        if (service.isEmpty()) {
            throw new VoteRejected("vote has no service name");
        }
        checkField(service, MAX_SERVICE, "service name");
        if (!USERNAME.matcher(username).matches()) {
            throw new VoteRejected("vote username '" + safe(username) + "' is empty or has strange characters");
        }
        checkField(address, MAX_ADDRESS, "address");
        checkField(timestamp, MAX_TIMESTAMP, "timestamp");
        return new Vote(service, username, address, number(timestamp), protocol);
    }

    private static void checkField(String value, int max, String name) throws VoteRejected {
        if (value.length() > max || !PRINTABLE.matcher(value).matches()) {
            throw new VoteRejected("vote " + name + " is too long or has control characters");
        }
    }

    /** The site's timestamp as a number (0 when it is not one; sites send all kinds of formats). */
    static long number(String timestamp) {
        try {
            return Math.max(0, Long.parseLong(timestamp));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** A name for the log: at most 36 printable characters. */
    static String safe(String s) {
        String cut = s.length() > MAX_USERNAME ? s.substring(0, MAX_USERNAME) : s;
        return cut.replaceAll("[^\\x20-\\x7E]", "?");
    }
}
