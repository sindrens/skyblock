package net.skybornmc.skyblock.common.db;

import net.skybornmc.skyblock.common.config.ModuleConfig;
import net.skybornmc.skyblock.common.config.Setting;

/**
 * {@code database.yml}. On the VPS, {@code server/scripts/apply-secrets.sh} switches it to MariaDB and fills the
 * credentials from {@code /opt/skyblock/.env}; the tracked file (and local test servers) use H2.
 */
public final class DatabaseConfig extends ModuleConfig {

    public enum Type { MARIADB, H2 }

    @Setting(desc = "mariadb = the real database (VPS), h2 = a local file database for test servers.", restart = true,
            level = Setting.Level.OWNER)
    public Type type = Type.H2;

    @Setting(desc = "MariaDB host.", restart = true, level = Setting.Level.OWNER)
    public String host = "127.0.0.1";

    @Setting(desc = "MariaDB port.", min = 1, max = 65535, restart = true, level = Setting.Level.OWNER)
    public int port = 3306;

    @Setting(desc = "MariaDB database name.", restart = true, level = Setting.Level.OWNER)
    public String database = "skyblock";

    @Setting(desc = "MariaDB user.", restart = true, level = Setting.Level.OWNER)
    public String user = "skyblock";

    @Setting(desc = "MariaDB password (filled in on the VPS by apply-secrets.sh, never commit it).", restart = true,
            level = Setting.Level.OWNER)
    public String password = "";

    @Setting(path = "h2-file", desc = "H2 database file, relative to plugins/PhoenixSkyblock/ (without .mv.db).", restart = true,
            level = Setting.Level.OWNER)
    public String h2File = "data/skyblock";

    @Setting(path = "pool-size", desc = "Maximum open database connections.", min = 2, max = 50, restart = true)
    public int poolSize = 10;

    @Setting(path = "connection-timeout-ms", desc = "How long to wait for a free connection before failing.", min = 1000, max = 60000,
            restart = true)
    public int connectionTimeoutMs = 5000;

    @Setting(path = "slow-query-ms", desc = "Database work slower than this (milliseconds) is logged as a warning.", min = 1, max = 60000)
    public int slowQueryMs = 250;

    @Override
    public String fileName() {
        return "database.yml";
    }

    @Override
    public String header() {
        return """
                Phoenix Skyblock database. Changes need a server restart.
                On the VPS this file is filled in automatically from /opt/skyblock/.env (apply-secrets.sh).""";
    }

    /** JDBC URL for the configured type. */
    public String jdbcUrl(java.io.File dataFolder) {
        return switch (type) {
            case MARIADB -> "jdbc:mariadb://" + host + ":" + port + "/" + database;
            case H2 -> "jdbc:h2:file:" + new java.io.File(dataFolder, h2File).getAbsolutePath() + H2_OPTIONS;
        };
    }

    /** H2 in MySQL mode so the same SQL runs on MariaDB and H2. */
    public static final String H2_OPTIONS = ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;NON_KEYWORDS=VALUE,KEY";
}
