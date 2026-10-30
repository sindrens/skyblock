package net.skybornmc.skyblock.common.audit;

import net.skybornmc.skyblock.common.config.ModuleConfig;
import net.skybornmc.skyblock.common.config.Setting;

import java.util.List;

/** Settings of the audit log ({@code audit.yml}). */
public final class AuditConfig extends ModuleConfig {

    @Setting(desc = "Name of this server in every log row (network-ready: hub, skyblock-1, ...).", restart = true,
            level = Setting.Level.OWNER)
    public String serverId = "skyblock-1";

    @Setting(path = "writer.flush-interval-ms", desc = "Write queued log rows at least this often (milliseconds).",
            min = 250, max = 60_000)
    public int flushIntervalMs = 2_000;

    @Setting(path = "writer.batch-size", desc = "Write immediately once this many rows are queued.", min = 10, max = 10_000)
    public int batchSize = 500;

    @Setting(path = "writer.max-queue", desc = "Rows kept in memory while the database is unreachable; above this they are "
            + "spooled to audit-spool.jsonl in the plugin folder and written later, so nothing is lost.",
            min = 1_000, max = 2_000_000)
    public int maxQueue = 200_000;

    @Setting(path = "retention.short-categories", desc = "Categories kept only for retention.short-days (owner decision: "
            + "chat, dm, command, item). Everything else is kept forever.", level = Setting.Level.OWNER)
    public List<String> shortCategories = List.of("chat", "dm", "command", "item");

    @Setting(path = "retention.short-days", desc = "Days to keep the short categories before they are archived and removed.",
            min = 1, max = 3650, level = Setting.Level.OWNER)
    public int shortDays = 30;

    @Setting(path = "retention.purge-hour", desc = "Hour of the day (server time) for the daily archive + purge. "
            + "After the 04:30 database backup by default.", min = 0, max = 23)
    public int purgeHour = 5;

    @Setting(path = "retention.archive-folder", desc = "Folder for the compressed monthly archives of purged rows "
            + "(<folder>/2026-10.jsonl.gz). Falls back to plugins/PhoenixSkyblock/audit-archive if not writable.",
            level = Setting.Level.OWNER)
    public String archiveFolder = "/opt/skyblock/backups/audit-archive";

    @Setting(path = "partitions.months-ahead", desc = "MariaDB: monthly partitions created in advance.", min = 1, max = 24)
    public int partitionMonthsAhead = 3;

    @Setting(path = "query.max-rows", desc = "Maximum rows one staff query may return per page.", min = 10, max = 5_000)
    public int maxQueryRows = 500;

    /** Version 2 (R3-9b): DMs got their own category (dm), kept as short as chat. */
    @Override
    public int version() {
        return 2;
    }

    @Override
    public java.util.Map<Integer, java.util.Map<String, ValueChange>> valueChanges() {
        return java.util.Map.of(2, java.util.Map.of("retention.short-categories",
                ValueChange.of(List.of("chat", "command", "item"), List.of("chat", "dm", "command", "item"))));
    }

    @Override
    public String fileName() {
        return "audit.yml";
    }

    @Override
    public String header() {
        return """
                Phoenix Skyblock audit log: every action that changes data is stored in the database on our own server.
                Rows are append-only. Short categories are archived to compressed monthly files before they are removed.
                Reload: /skyblock reload core""";
    }
}
