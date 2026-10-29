package net.skybornmc.skyblock.common.audit;

import net.skybornmc.skyblock.common.db.Database;
import net.skybornmc.skyblock.common.db.Sql;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;
import java.util.zip.GZIPOutputStream;

/**
 * Daily retention job: rows of the short categories older than {@code retention.short-days} are first appended to
 * {@code <archive-folder>/<yyyy-MM>.jsonl.gz} (UTC month of the row), and only after the archive write succeeded are
 * they deleted, in chunks. Each run appends a new gzip member, which every gzip tool reads as one file.
 */
final class AuditRetention {

    /** Outcome of one run. */
    record Result(int archived, int removed, long cutoff, File folder) {
    }

    static final int CHUNK = 5_000;
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyy-MM", Locale.ROOT).withZone(ZoneOffset.UTC);

    private final Database db;
    private final AuditConfig config;
    private final File dataFolder;
    private final Logger log;

    AuditRetention(Database db, AuditConfig config, File dataFolder, Logger log) {
        this.db = db;
        this.config = config;
        this.dataFolder = dataFolder;
        this.log = log;
    }

    Result run(long now) {
        List<String> categories = config.shortCategories.stream()
                .map(c -> AuditCategory.byKey(c).map(AuditCategory::key).orElse(null))
                .filter(java.util.Objects::nonNull).distinct().toList();
        long cutoff = now - Duration.ofDays(config.shortDays).toMillis();
        File folder = archiveFolder();
        if (categories.isEmpty()) {
            return new Result(0, 0, cutoff, folder);
        }
        String in = "?,".repeat(categories.size() - 1) + "?";
        int archived = 0;
        int removed = 0;
        while (true) {
            List<Object> params = new ArrayList<>(categories);
            params.add(cutoff);
            params.add(CHUNK);
            List<AuditEntry> chunk = db.blocking(c -> Sql.query(c, "SELECT id, created_at, season_id, server_id, category, action, "
                    + "actor_uuid, actor_name, target, target_name, amount, world, x, y, z, details FROM audit_log "
                    + "WHERE category IN (" + in + ") AND created_at < ? ORDER BY created_at, id LIMIT ?", AuditLog::map, params.toArray()));
            if (chunk.isEmpty()) {
                break;
            }
            try {
                archive(folder, chunk);
            } catch (IOException e) {
                // Never delete without an archive.
                log.severe("Audit log: archive write to " + folder + " failed, nothing removed: " + e.getMessage());
                break;
            }
            archived += chunk.size();
            List<Object[]> keys = new ArrayList<>(chunk.size());
            chunk.forEach(e -> keys.add(new Object[]{e.id(), e.time()}));
            db.blockingTransaction(c -> Sql.batch(c, "DELETE FROM audit_log WHERE id = ? AND created_at = ?", keys));
            removed += chunk.size();
            if (chunk.size() < CHUNK) {
                break;
            }
        }
        if (removed > 0) {
            log.info("Audit log: archived and removed " + removed + " row(s) older than " + config.shortDays + " days ("
                    + String.join(", ", categories) + ") to " + folder);
        }
        return new Result(archived, removed, cutoff, folder);
    }

    /** The configured folder, or {@code plugins/PhoenixSkyblock/audit-archive} if it can't be created or written. */
    File archiveFolder() {
        File configured = new File(config.archiveFolder);
        if ((configured.isDirectory() || configured.mkdirs()) && configured.canWrite()) {
            return configured;
        }
        File fallback = new File(dataFolder, "audit-archive");
        fallback.mkdirs();
        log.warning("Audit log: archive folder " + configured + " is not writable, using " + fallback);
        return fallback;
    }

    static void archive(File folder, List<AuditEntry> rows) throws IOException {
        Map<String, List<AuditEntry>> byMonth = new LinkedHashMap<>();
        for (AuditEntry e : rows) {
            byMonth.computeIfAbsent(MONTH.format(Instant.ofEpochMilli(e.time())), k -> new ArrayList<>()).add(e);
        }
        for (Map.Entry<String, List<AuditEntry>> month : byMonth.entrySet()) {
            File file = new File(folder, month.getKey() + ".jsonl.gz");
            try (OutputStream out = Files.newOutputStream(file.toPath(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                 Writer w = new OutputStreamWriter(new GZIPOutputStream(out), StandardCharsets.UTF_8)) {
                for (AuditEntry e : month.getValue()) {
                    w.write(AuditJson.line(e));
                    w.write('\n');
                }
            }
        }
    }
}
