package net.skybornmc.skyblock.common.audit;

import net.skybornmc.skyblock.common.db.Database;
import net.skybornmc.skyblock.common.db.DatabaseConfig;
import net.skybornmc.skyblock.common.db.Sql;

import java.sql.Statement;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.logging.Logger;

/**
 * MariaDB only: keeps monthly partitions ({@code p202610}, {@code p202611} …, upper bound = start of the next UTC month
 * in millis) ready {@code monthsAhead} months in advance by splitting them off the {@code p_future} catch-all.
 * Does nothing on H2 or when the table is not partitioned.
 */
final class AuditPartitions {

    private AuditPartitions() {
    }

    record Partition(String name, Long bound) {
    }

    static void maintain(Database db, int monthsAhead, Logger log) {
        if (db.type() != DatabaseConfig.Type.MARIADB) {
            return;
        }
        db.blocking(c -> {
            List<Partition> existing = Sql.query(c, "SELECT PARTITION_NAME, PARTITION_DESCRIPTION FROM information_schema.PARTITIONS "
                            + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'audit_log' AND PARTITION_NAME IS NOT NULL",
                    rs -> new Partition(rs.getString(1), parseBound(rs.getString(2))));
            if (existing.stream().noneMatch(p -> p.name().equals("p_future"))) {
                return null; // not partitioned the way we expect; leave it alone
            }
            for (String ddl : missing(existing, YearMonth.now(ZoneOffset.UTC), monthsAhead)) {
                try (Statement st = c.createStatement()) {
                    st.execute(ddl);
                }
                log.info("Audit log: " + ddl.substring(ddl.indexOf("PARTITION p2"), ddl.indexOf(" VALUES")).replace("PARTITION ", "added partition "));
            }
            return null;
        });
    }

    /** DDL statements to add the missing months (ascending, only after the highest existing bound). */
    static List<String> missing(List<Partition> existing, YearMonth current, int monthsAhead) {
        long highest = existing.stream().map(Partition::bound).filter(java.util.Objects::nonNull).mapToLong(Long::longValue).max()
                .orElse(Long.MIN_VALUE);
        List<String> ddl = new ArrayList<>();
        for (int i = 0; i <= monthsAhead; i++) {
            YearMonth month = current.plusMonths(i);
            long bound = month.plusMonths(1).atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli();
            String name = "p" + String.format(Locale.ROOT, "%04d%02d", month.getYear(), month.getMonthValue());
            if (bound <= highest || existing.stream().anyMatch(p -> p.name().equals(name))) {
                continue;
            }
            ddl.add("ALTER TABLE audit_log REORGANIZE PARTITION p_future INTO (PARTITION " + name + " VALUES LESS THAN ("
                    + bound + "), PARTITION p_future VALUES LESS THAN MAXVALUE)");
            highest = bound;
        }
        return ddl;
    }

    private static Long parseBound(String description) {
        if (description == null || description.isBlank() || description.equalsIgnoreCase("MAXVALUE")) {
            return null;
        }
        try {
            return Long.parseLong(description.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
