package net.skybornmc.skyblock.box;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Last line of defence when the database refuses a delivery: the stacks are appended (and synced to disk) to
 * {@code box/spool.txt} and imported into the database later (every {@code spool.import-seconds} and on start).
 * Every spooled stack gets a unique ref, so importing the same file twice (crash during an import) never doubles it.
 *
 * <p>Import: the current file is first renamed to {@code spool-<time>.txt} (new deliveries go to a fresh
 * {@code spool.txt}), then imported, then deleted after the database committed.</p>
 */
final class BoxSpool {

    /** Spooled stacks of one file, grouped by profile. */
    record Batch(File file, Map<UUID, List<BoxItem>> items, int badLines) {
    }

    private static final String CURRENT = "spool.txt";
    private final File dir;

    BoxSpool(File dir) {
        this.dir = dir;
    }

    /** Appends the stacks with fresh refs and syncs the file. Throws if the disk refuses too. */
    synchronized void append(UUID profile, List<BoxItem> items) throws IOException {
        if (items.isEmpty()) {
            return;
        }
        Files.createDirectories(dir.toPath());
        File file = new File(dir, CURRENT);
        try (FileOutputStream out = new FileOutputStream(file, true);
             Writer w = new OutputStreamWriter(out, StandardCharsets.UTF_8)) {
            for (BoxItem i : items) {
                String ref = i.ref() != null ? i.ref() : "spool:" + UUID.randomUUID();
                w.write(String.join("\t", ref, profile.toString(), i.source(), Long.toString(i.createdAt()), i.material(),
                        Integer.toString(i.amount()), b64(i.name().getBytes(StandardCharsets.UTF_8)), b64(i.bytes())));
                w.write('\n');
            }
            w.flush();
            out.getFD().sync();
        }
    }

    /** True when there is anything to import. */
    synchronized boolean hasWork() {
        File[] files = dir.listFiles((d, n) -> n.startsWith("spool") && n.endsWith(".txt"));
        return files != null && Arrays.stream(files).anyMatch(f -> f.length() > 0);
    }

    /** Moves the current file aside and reads every spool file waiting for import, oldest first. */
    synchronized List<Batch> takeForImport() throws IOException {
        File current = new File(dir, CURRENT);
        if (current.isFile() && current.length() > 0) {
            File aside = new File(dir, "spool-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 8) + ".txt");
            try {
                Files.move(current.toPath(), aside.toPath(), StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(current.toPath(), aside.toPath());
            }
        }
        File[] files = dir.listFiles((d, n) -> n.startsWith("spool-") && n.endsWith(".txt"));
        if (files == null) {
            return List.of();
        }
        Arrays.sort(files, Comparator.comparing(File::getName));
        List<Batch> out = new ArrayList<>();
        for (File f : files) {
            out.add(read(f));
        }
        return out;
    }

    /** Deletes an imported file. */
    synchronized void done(Batch batch) throws IOException {
        Files.deleteIfExists(batch.file().toPath());
    }

    static Batch read(File f) throws IOException {
        Map<UUID, List<BoxItem>> items = new LinkedHashMap<>();
        int bad = 0;
        for (String line : Files.readAllLines(f.toPath(), StandardCharsets.UTF_8)) {
            if (line.isBlank()) {
                continue;
            }
            String[] p = line.split("\t", -1);
            try {
                if (p.length != 8) {
                    throw new IllegalArgumentException("fields: " + p.length);
                }
                UUID profile = UUID.fromString(p[1]);
                BoxItem item = new BoxItem(Base64.getDecoder().decode(p[7]), p[4], Integer.parseInt(p[5]),
                        new String(Base64.getDecoder().decode(p[6]), StandardCharsets.UTF_8), p[2], Long.parseLong(p[3]), p[0]);
                items.computeIfAbsent(profile, k -> new ArrayList<>()).add(item);
            } catch (RuntimeException e) {
                bad++; // a torn last line after a crash; the full line is still in the file for staff
            }
        }
        return new Batch(f, items, bad);
    }

    private static String b64(byte[] bytes) {
        return Base64.getEncoder().encodeToString(bytes);
    }
}
