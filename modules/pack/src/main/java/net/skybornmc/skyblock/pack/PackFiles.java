package net.skybornmc.skyblock.pack;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Extracts the bundled zip and verifies its SHA-1 against {@code pack-ids.yml}. */
final class PackFiles {

    private PackFiles() {
    }

    static String sha1(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Reads {@code in} fully, checks the SHA-1 and writes it to {@code target} (atomically replaced).
     *
     * @return the file bytes (kept in memory for hosting)
     * @throws IOException on a read/write error or when the hash differs (a corrupted or stale jar)
     */
    static byte[] extract(InputStream in, Path target, String expectedSha1) throws IOException {
        byte[] data = in.readAllBytes();
        String actual = sha1(data);
        if (!actual.equalsIgnoreCase(expectedSha1)) {
            throw new IOException("pack SHA-1 is " + actual + " but pack-ids.yml says " + expectedSha1
                    + " (run python3 tools/build_pack.py and rebuild)");
        }
        Files.createDirectories(target.getParent());
        Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
        Files.write(tmp, data);
        Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        return data;
    }
}
