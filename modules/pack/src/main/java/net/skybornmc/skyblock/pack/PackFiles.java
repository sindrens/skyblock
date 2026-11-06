package net.skybornmc.skyblock.pack;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** SHA-1 of the bundled zip and its copy in {@code plugins/PhoenixSkyblock/pack/} (for staff to inspect). */
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

    /** The served file name: {@code phoenix-icons-<first 8 hex of the SHA-1>.zip} (a new hash = a new name, cache-safe). */
    static String fileName(String sha1) {
        return "phoenix-icons-" + sha1.substring(0, 8) + ".zip";
    }

    /** Writes {@code data} to {@code target}, atomically replaced. Async callers only (disk I/O). */
    static void extract(byte[] data, Path target) throws IOException {
        Files.createDirectories(target.getParent());
        Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
        Files.write(tmp, data);
        Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }
}
