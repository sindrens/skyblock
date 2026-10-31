package net.skybornmc.skyblock.rewards.votifier;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * The secrets of the vote receiver, kept <b>outside</b> the config files so {@code /admin} and {@code /skyblock config}
 * never show them (folder {@code plugins/PhoenixSkyblock/votifier/}):
 * <ul>
 *   <li>{@code rsa/public.key} and {@code rsa/private.key}: the v1 RSA key pair (2048 bit, base64 X.509 / PKCS#8, the
 *       same files NuVotifier writes, so a NuVotifier key can be copied in). The owner pastes {@code public.key} into a
 *       v1 vote site.</li>
 *   <li>{@code tokens.txt}: one {@code <site id>: <token>} line per site (v2). Missing tokens are made on start; the
 *       owner pastes a site's token into that site.</li>
 * </ul>
 * Plain Java (no Bukkit), unit-tested with a temporary folder. Only read and written off the main thread.
 */
public final class VoteKeys {

    public static final String PUBLIC = "public.key";
    public static final String PRIVATE = "private.key";
    public static final String TOKENS = "tokens.txt";

    private static final Pattern TOKEN = Pattern.compile("[A-Za-z0-9_\\-]{8,128}");
    private static final Pattern SITE = Pattern.compile("[a-z0-9_\\-]{1,32}");

    private VoteKeys() {
    }

    /** Loads the RSA pair from {@code dir/rsa}, or makes and saves a new one when it is missing. */
    public static KeyPair loadOrCreateRsa(Path dir) throws IOException {
        Path rsa = dir.resolve("rsa");
        Path pub = rsa.resolve(PUBLIC);
        Path priv = rsa.resolve(PRIVATE);
        try {
            KeyFactory factory = KeyFactory.getInstance("RSA");
            if (Files.isRegularFile(pub) && Files.isRegularFile(priv)) {
                PublicKey publicKey = factory.generatePublic(new X509EncodedKeySpec(decode(pub)));
                PrivateKey privateKey = factory.generatePrivate(new PKCS8EncodedKeySpec(decode(priv)));
                return new KeyPair(publicKey, privateKey);
            }
            KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
            gen.initialize(2048, new SecureRandom());
            KeyPair pair = gen.generateKeyPair();
            Files.createDirectories(rsa);
            writeSecret(priv, Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded()));
            write(pub, Base64.getEncoder().encodeToString(pair.getPublic().getEncoded()));
            return pair;
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IOException("votifier/rsa: the key files are not valid RSA keys (" + e.getMessage()
                    + "). Move them away to make a new pair.", e);
        }
    }

    /** The public key as one base64 line (what a v1 vote site asks for). */
    public static String publicKeyText(PublicKey key) {
        return Base64.getEncoder().encodeToString(key.getEncoded());
    }

    /**
     * Loads {@code dir/tokens.txt} ({@code site: token} per line, {@code #} comments) and adds a fresh token for every
     * site in {@code sites} that has none (saved again only then). Broken lines are kept as they are and skipped.
     *
     * @return site id → token
     */
    public static Map<String, String> loadOrCreateTokens(Path dir, Collection<String> sites, SecureRandom random) throws IOException {
        Path file = dir.resolve(TOKENS);
        Map<String, String> tokens = new LinkedHashMap<>();
        List<String> lines = Files.isRegularFile(file) ? Files.readAllLines(file, StandardCharsets.UTF_8) : List.of();
        tokens.putAll(parseTokens(lines));
        StringBuilder out = new StringBuilder();
        if (lines.isEmpty()) {
            out.append("# Votifier v2 tokens, one per vote site: '<site id from votes.yml>: <token>'.\n")
                    .append("# Paste a site's token into that site's Votifier settings (protocol v2). Keep this file secret.\n")
                    .append("# Delete a line and restart to get a new token for that site.\n");
        } else {
            for (String line : lines) {
                out.append(line).append('\n');
            }
        }
        boolean added = false;
        for (String site : sites) {
            String id = site.toLowerCase(Locale.ROOT);
            if (!tokens.containsKey(id) && SITE.matcher(id).matches()) {
                String token = VotifierProtocol.token(random);
                tokens.put(id, token);
                out.append(id).append(": ").append(token).append('\n');
                added = true;
            }
        }
        if (added || lines.isEmpty()) {
            Files.createDirectories(dir);
            writeSecret(file, out.toString());
        }
        return tokens;
    }

    /** {@code site: token} lines → map (pure). Comments, blank and broken lines are skipped. */
    static Map<String, String> parseTokens(List<String> lines) {
        Map<String, String> tokens = new LinkedHashMap<>();
        for (String raw : lines) {
            String line = raw.strip();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            int colon = line.indexOf(':');
            if (colon <= 0) {
                continue;
            }
            String site = line.substring(0, colon).strip().toLowerCase(Locale.ROOT);
            String token = line.substring(colon + 1).strip();
            if (SITE.matcher(site).matches() && TOKEN.matcher(token).matches()) {
                tokens.put(site, token);
            }
        }
        return tokens;
    }

    private static byte[] decode(Path file) throws IOException {
        return Base64.getDecoder().decode(Files.readString(file, StandardCharsets.US_ASCII).replaceAll("\\s", ""));
    }

    private static void write(Path file, String text) throws IOException {
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(tmp, text, StandardCharsets.UTF_8);
        Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    /** Written like {@link #write}, readable by the server user only where the file system allows it. */
    private static void writeSecret(Path file, String text) throws IOException {
        write(file, text);
        try {
            Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-------"));
        } catch (UnsupportedOperationException | IOException ignored) {
            // Windows or a file system without POSIX permissions: the folder's own permissions apply.
        }
    }
}
