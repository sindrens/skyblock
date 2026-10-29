package net.skybornmc.skyblock.common.audit;

import net.skybornmc.skyblock.common.db.Database;
import net.skybornmc.skyblock.common.db.Sql;
import org.bukkit.Location;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.IntSupplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.GZIPOutputStream;

/**
 * The audit log: <b>every module logs every meaningful action here</b> (M0.8). Backbone of the staff log viewer.
 *
 * <ul>
 *   <li>{@link #log} never blocks and is safe from any thread: rows are queued and written in batches
 *       (every {@code writer.flush-interval-ms} or {@code writer.batch-size} rows) on the audit thread.</li>
 *   <li>If the database is down, rows stay queued; above {@code writer.max-queue} (and on shutdown) they are spooled to
 *       {@code audit-spool.jsonl} and written on the next start. Nothing is lost silently.</li>
 *   <li>Append-only: there is no update/delete API. Only the retention job removes short-category rows, after
 *       archiving them to compressed monthly files, and logs that it did.</li>
 *   <li>MariaDB: monthly partitions are created ahead of time.</li>
 * </ul>
 */
public final class AuditLog implements AutoCloseable {

    static final String INSERT = "INSERT INTO audit_log (created_at, season_id, server_id, category, action, actor_uuid, "
            + "actor_name, target, target_name, amount, world, x, y, z, details) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
    private static final String COLUMNS = "id, created_at, season_id, server_id, category, action, actor_uuid, actor_name, "
            + "target, target_name, amount, world, x, y, z, details";
    private static final int MAX_FLUSH_ROWS = 5_000;

    private final Database db;
    private final AuditConfig config;
    private final File dataFolder;
    private final Logger log;
    private final File spoolFile;
    private final ConcurrentLinkedDeque<AuditEntry> queue = new ConcurrentLinkedDeque<>();
    private final AtomicInteger queued = new AtomicInteger();
    private final AtomicBoolean flushPending = new AtomicBoolean();
    private final AtomicLong written = new AtomicLong();
    private final AtomicLong spooled = new AtomicLong();
    private final Object spoolLock = new Object();
    private volatile IntSupplier season = () -> 0;
    private volatile ScheduledExecutorService scheduler;
    private volatile boolean closed;
    private volatile long lastFailureWarning;

    public AuditLog(Database db, AuditConfig config, File dataFolder, Logger log) {
        this.db = Objects.requireNonNull(db, "db");
        this.config = Objects.requireNonNull(config, "config");
        this.dataFolder = Objects.requireNonNull(dataFolder, "dataFolder");
        this.log = Objects.requireNonNull(log, "log");
        this.spoolFile = new File(dataFolder, "audit-spool.jsonl");
    }

    /** Where the current season id comes from (the season service, later). Default 0. */
    public void seasonSource(IntSupplier source) {
        this.season = Objects.requireNonNull(source, "source");
    }

    /** Starts the writer, replays any spooled rows, schedules partition maintenance and the daily archive + purge. */
    public void start() {
        if (scheduler != null) {
            return;
        }
        closed = false;
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "skyblock-audit");
            t.setDaemon(true);
            return t;
        });
        replaySpool();
        scheduler.scheduleWithFixedDelay(this::flushSafely, config.flushIntervalMs, config.flushIntervalMs, TimeUnit.MILLISECONDS);
        scheduler.schedule(this::maintainPartitionsSafely, 10, TimeUnit.SECONDS);
        long untilPurge = millisUntilHour(config.purgeHour);
        scheduler.scheduleAtFixedRate(() -> {
            maintainPartitionsSafely();
            purgeSafely();
        }, untilPurge, TimeUnit.DAYS.toMillis(1), TimeUnit.MILLISECONDS);
    }

    /** Call after {@code audit.yml} was reloaded (timings apply on the next start; retention/paths immediately). */
    public void reload() {
        // Nothing cached besides the scheduler timings, which need a restart; retention and paths are read live.
    }

    // ------------------------------------------------------------------------------------------------ logging API

    /**
     * Logs one action.
     *
     * @param category what it is about
     * @param action   short snake_case verb, e.g. {@code "pay"}, {@code "island_invite"}, {@code "ban"}
     * @param actor    who did it ({@link Actor#player}, {@link Actor#CONSOLE}, {@link Actor#SYSTEM})
     * @param target   what it was done to (player uuid, island id, item uuid …), nullable
     * @param amount   money/items/time involved, nullable
     * @param location where it happened, nullable
     * @param details  everything else (old/new values, reason, item summary …), nullable
     */
    public void log(AuditCategory category, String action, Actor actor, String target, Long amount, Location location,
                    Map<String, Object> details) {
        log(category, action, actor, target, null, amount, location, details);
    }

    /** {@link #log} with a readable target name (e.g. the target player's name at that time). */
    public void log(AuditCategory category, String action, Actor actor, String target, String targetName, Long amount,
                    Location location, Map<String, Object> details) {
        String world = null;
        Integer x = null;
        Integer y = null;
        Integer z = null;
        if (location != null && location.getWorld() != null) {
            world = location.getWorld().getName();
            x = location.getBlockX();
            y = location.getBlockY();
            z = location.getBlockZ();
        }
        enqueue(new AuditEntry(0, System.currentTimeMillis(), season.getAsInt(), config.serverId,
                Objects.requireNonNull(category, "category"), clip(Objects.requireNonNull(action, "action"), 48),
                Objects.requireNonNull(actor, "actor"), clip(target, 64), clip(targetName, 64), amount, world, x, y, z,
                details == null ? Map.of() : Map.copyOf(details)));
    }

    /** {@link #log} where the target is a player (stored as the uuid string, name kept for display). */
    public void logPlayer(AuditCategory category, String action, Actor actor, UUID targetPlayer, String targetName, Long amount,
                    Location location, Map<String, Object> details) {
        log(category, action, actor, targetPlayer == null ? null : targetPlayer.toString(), targetName, amount, location, details);
    }

    /** Short form without target, amount or location. */
    public void log(AuditCategory category, String action, Actor actor, Map<String, Object> details) {
        log(category, action, actor, (String) null, null, null, null, details);
    }

    void enqueue(AuditEntry entry) {
        if (closed) {
            spool(List.of(entry));
            return;
        }
        queue.addLast(entry);
        int size = queued.incrementAndGet();
        if (size >= config.batchSize && flushPending.compareAndSet(false, true)) {
            ScheduledExecutorService s = scheduler;
            if (s != null && !s.isShutdown()) {
                s.execute(this::flushSafely);
            } else {
                flushPending.set(false);
            }
        }
    }

    // ------------------------------------------------------------------------------------------------ writer

    private void flushSafely() {
        flushPending.set(false);
        try {
            flush();
        } catch (RuntimeException e) {
            log.log(Level.SEVERE, "Audit log: unexpected error while writing", e);
        }
    }

    /**
     * Writes everything queued now (blocking; the audit thread and shutdown use it).
     *
     * @return rows written
     */
    public int flush() {
        int total = 0;
        while (!queue.isEmpty()) {
            List<AuditEntry> batch = new ArrayList<>(Math.min(queued.get(), MAX_FLUSH_ROWS));
            AuditEntry e;
            while (batch.size() < MAX_FLUSH_ROWS && (e = queue.pollFirst()) != null) {
                batch.add(e);
            }
            queued.addAndGet(-batch.size());
            if (batch.isEmpty()) {
                break;
            }
            try {
                db.blockingTransaction(c -> {
                    insert(c, batch);
                    return null;
                });
                written.addAndGet(batch.size());
                total += batch.size();
            } catch (RuntimeException ex) {
                requeue(batch);
                long now = System.currentTimeMillis();
                if (now - lastFailureWarning > 60_000) {
                    lastFailureWarning = now;
                    log.warning("Audit log: could not write " + batch.size() + " row(s), keeping them queued ("
                            + queued.get() + " waiting): " + Database.cause(ex).getMessage());
                }
                break;
            }
        }
        return total;
    }

    static void insert(Connection c, List<AuditEntry> batch) throws SQLException {
        List<Object[]> rows = new ArrayList<>(batch.size());
        for (AuditEntry e : batch) {
            rows.add(new Object[]{e.time(), e.season(), e.server(), e.category().key(), e.action(), e.actor().uuid(),
                    e.actor().name(), e.target(), e.targetName(), e.amount(), e.world(), e.x(), e.y(), e.z(),
                    AuditJson.details(e.details())});
        }
        Sql.batch(c, INSERT, rows);
    }

    private void requeue(List<AuditEntry> batch) {
        for (int i = batch.size() - 1; i >= 0; i--) {
            queue.addFirst(batch.get(i));
        }
        queued.addAndGet(batch.size());
        int over = queued.get() - config.maxQueue;
        if (over > 0) {
            List<AuditEntry> overflow = new ArrayList<>(over);
            AuditEntry e;
            while (overflow.size() < over && (e = queue.pollFirst()) != null) {
                overflow.add(e);
            }
            queued.addAndGet(-overflow.size());
            spool(overflow);
        }
    }

    private void spool(List<AuditEntry> rows) {
        if (rows.isEmpty()) {
            return;
        }
        synchronized (spoolLock) {
            try {
                dataFolder.mkdirs();
                try (BufferedWriter w = Files.newBufferedWriter(spoolFile.toPath(), StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
                    for (AuditEntry e : rows) {
                        w.write(AuditJson.line(e));
                        w.newLine();
                    }
                }
                spooled.addAndGet(rows.size());
                log.warning("Audit log: " + rows.size() + " row(s) saved to " + spoolFile.getName() + "; they are written on the next start.");
            } catch (IOException ex) {
                log.severe("Audit log: LOST " + rows.size() + " row(s), could not write " + spoolFile + ": " + ex.getMessage());
            }
        }
    }

    private void replaySpool() {
        synchronized (spoolLock) {
            if (!spoolFile.isFile()) {
                return;
            }
            int count = 0;
            int bad = 0;
            try (BufferedReader r = Files.newBufferedReader(spoolFile.toPath(), StandardCharsets.UTF_8)) {
                String line;
                while ((line = r.readLine()) != null) {
                    if (line.isBlank()) {
                        continue;
                    }
                    AuditEntry e = AuditJson.parse(line);
                    if (e == null) {
                        bad++;
                        continue;
                    }
                    queue.addLast(e);
                    queued.incrementAndGet();
                    count++;
                }
            } catch (IOException ex) {
                log.severe("Audit log: could not read " + spoolFile + ": " + ex.getMessage() + " (left in place)");
                return;
            }
            File done = new File(dataFolder, "audit-spool.replayed-" + System.currentTimeMillis() + ".jsonl");
            if (!spoolFile.renameTo(done)) {
                log.warning("Audit log: could not rename " + spoolFile.getName() + "; delete it after checking the rows were written.");
            }
            log.info("Audit log: re-queued " + count + " spooled row(s)" + (bad > 0 ? " (" + bad + " unreadable line(s) skipped)" : "")
                    + ". The old spool file is kept as " + done.getName() + ".");
        }
        flushSafely();
    }

    /** Stops the writer and writes everything queued; what can't be written is spooled. Call before closing the database. */
    @Override
    public void close() {
        if (closed) {
            return;
        }
        ScheduledExecutorService s = scheduler;
        scheduler = null;
        if (s != null) {
            s.shutdown();
            try {
                if (!s.awaitTermination(10, TimeUnit.SECONDS)) {
                    s.shutdownNow();
                }
            } catch (InterruptedException e) {
                s.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        if (db.isRunning()) {
            flush();
        }
        closed = true;
        List<AuditEntry> rest = new ArrayList<>();
        AuditEntry e;
        while ((e = queue.pollFirst()) != null) {
            rest.add(e);
        }
        queued.set(0);
        spool(rest);
    }

    // ------------------------------------------------------------------------------------------------ queries

    /** Rows matching {@code query}, newest first (async). */
    public CompletableFuture<List<AuditEntry>> query(AuditQuery query) {
        int limit = Math.min(query.limit(), config.maxQueryRows);
        return db.async(c -> select(c, query, query.offset(), limit));
    }

    /** Number of rows matching {@code query} (async). */
    public CompletableFuture<Long> count(AuditQuery query) {
        return db.async(c -> {
            AuditQuery.Where where = query.where();
            return Sql.queryOne(c, "SELECT COUNT(*) FROM audit_log WHERE " + where.sql(), rs -> rs.getLong(1),
                    where.params().toArray()).orElse(0L);
        });
    }

    static List<AuditEntry> select(Connection c, AuditQuery query, int offset, int limit) throws SQLException {
        AuditQuery.Where where = query.where();
        List<Object> params = new ArrayList<>(where.params());
        params.add(limit);
        params.add(offset);
        return Sql.query(c, "SELECT " + COLUMNS + " FROM audit_log WHERE " + where.sql()
                + " ORDER BY created_at DESC, id DESC LIMIT ? OFFSET ?", AuditLog::map, params.toArray());
    }

    static AuditEntry map(ResultSet rs) throws SQLException {
        byte[] actorBytes = rs.getBytes("actor_uuid");
        Actor actor = new Actor(actorBytes == null ? null : Sql.toUuid(actorBytes), rs.getString("actor_name"));
        return new AuditEntry(rs.getLong("id"), rs.getLong("created_at"), rs.getInt("season_id"), rs.getString("server_id"),
                AuditCategory.byKey(rs.getString("category")).orElse(AuditCategory.STAFF), rs.getString("action"), actor,
                rs.getString("target"), rs.getString("target_name"), Sql.nullableLong(rs, "amount"), rs.getString("world"),
                intOrNull(rs, "x"), intOrNull(rs, "y"), intOrNull(rs, "z"), AuditJson.details(rs.getString("details")));
    }

    private static Integer intOrNull(ResultSet rs, String column) throws SQLException {
        int v = rs.getInt(column);
        return rs.wasNull() ? null : v;
    }

    /**
     * Owner-only export: writes every row matching {@code query} as gzipped JSON lines to {@code out}. The export itself
     * is logged ({@code staff/audit_export}). The caller checks the permission.
     *
     * @return rows exported
     */
    public CompletableFuture<Integer> export(AuditQuery query, Actor by, File out) {
        return db.async(c -> {
            int total = 0;
            out.getAbsoluteFile().getParentFile().mkdirs();
            try (Writer w = new OutputStreamWriter(new GZIPOutputStream(Files.newOutputStream(out.toPath())), StandardCharsets.UTF_8)) {
                for (int offset = 0; ; offset += MAX_FLUSH_ROWS) {
                    List<AuditEntry> page = select(c, query, offset, MAX_FLUSH_ROWS);
                    for (AuditEntry e : page) {
                        w.write(AuditJson.line(e));
                        w.write('\n');
                    }
                    total += page.size();
                    if (page.size() < MAX_FLUSH_ROWS) {
                        break;
                    }
                }
            } catch (IOException e) {
                throw new SQLException("Export to " + out + " failed: " + e.getMessage(), e);
            }
            log(AuditCategory.STAFF, "audit_export", by, null, null, (long) total, null, Map.of("file", out.getAbsolutePath()));
            return total;
        });
    }

    // ------------------------------------------------------------------------------------------------ retention

    private void purgeSafely() {
        try {
            AuditRetention.Result result = new AuditRetention(db, config, dataFolder, log).run(System.currentTimeMillis());
            if (result.removed() > 0) {
                log(AuditCategory.STAFF, "audit_purge", Actor.system("audit"), null, null, (long) result.removed(), null,
                        Map.of("archived", result.archived(), "archive_folder", result.folder().getAbsolutePath(),
                                "cutoff", result.cutoff(), "categories", String.join(",", config.shortCategories)));
            }
        } catch (RuntimeException e) {
            log.log(Level.SEVERE, "Audit log: archive + purge failed (nothing was removed without an archive)", e);
        }
    }

    private void maintainPartitionsSafely() {
        try {
            AuditPartitions.maintain(db, config.partitionMonthsAhead, log);
        } catch (RuntimeException e) {
            log.log(Level.WARNING, "Audit log: partition maintenance failed: " + Database.cause(e).getMessage());
        }
    }

    static long millisUntilHour(int hour) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime next = now.withHour(hour).withMinute(0).withSecond(0).withNano(0);
        if (!next.isAfter(now)) {
            next = next.plusDays(1);
        }
        return Duration.between(now.atZone(ZoneId.systemDefault()), next.atZone(ZoneId.systemDefault())).toMillis();
    }

    // ------------------------------------------------------------------------------------------------ stats

    /** Rows waiting to be written. */
    public int queued() {
        return queued.get();
    }

    /** Rows written since start. */
    public long written() {
        return written.get();
    }

    /** Rows saved to the spool file since start. */
    public long spooled() {
        return spooled.get();
    }

    private static String clip(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
