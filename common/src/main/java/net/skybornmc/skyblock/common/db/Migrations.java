package net.skybornmc.skyblock.common.db;

import java.io.IOException;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Applies {@code db/<module>/V001__description.sql}, {@code V002__…} in order and records them in {@code schema_version}.
 * Already applied versions are skipped; an edited applied file is reported (never re-run). A failing migration throws
 * {@link MigrationException} naming the file and statement, so the module can be disabled while the server keeps running.
 *
 * <p>SQL must run on both MariaDB and H2 (MySQL mode): no {@code ENGINE=}, indexes via
 * {@code CREATE INDEX IF NOT EXISTS idx_<table>_<col> ON <table> (...)}, statements end with {@code ;} at line end.</p>
 *
 * <p><b>Dialect variants:</b> when one database needs its own DDL (e.g. MariaDB partitioning), add
 * {@code V002__audit.mariadb.sql} and/or {@code V002__audit.h2.sql}. The variant for the running database wins over a
 * generic {@code V002__audit.sql}; variants for the other database are ignored.</p>
 */
public final class Migrations {

    static final Pattern FILE = Pattern.compile("V(\\d{3,})__([A-Za-z0-9_]+)(?:\\.(mariadb|h2))?\\.sql");
    private static final Pattern STATEMENT_END = Pattern.compile(";\\s*(?:\\r?\\n|$)");

    /** A migration that could not be applied. */
    public static final class MigrationException extends Exception {
        public MigrationException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    record Migration(int version, String description, String file, String sql) {
    }

    private final Database db;
    private final Logger log;

    public Migrations(Database db, Logger log) {
        this.db = db;
        this.log = log;
    }

    /**
     * Applies all pending migrations of {@code module} found on {@code loader}'s classpath. Blocking; call during
     * module enable.
     *
     * @return number of migrations applied now
     */
    public int migrate(String module, ClassLoader loader) throws MigrationException {
        if (!module.matches("[a-z0-9_-]+")) {
            throw new IllegalArgumentException("Invalid module id for migrations: " + module);
        }
        List<Migration> migrations;
        try {
            migrations = find(module, loader, db.type().name().toLowerCase(java.util.Locale.ROOT));
        } catch (IOException | URISyntaxException e) {
            throw new MigrationException("Could not read migrations in db/" + module + "/: " + e.getMessage(), e);
        }
        if (migrations.isEmpty()) {
            log.warning("Database: no migrations found in db/" + module + "/ (expected V001__*.sql).");
        }
        try {
            return db.blocking(c -> apply(c, module, migrations));
        } catch (RuntimeException e) {
            Throwable cause = Database.cause(e);
            if (cause instanceof MigrationFailure failure) {
                throw new MigrationException(failure.getMessage(), failure.getCause());
            }
            throw new MigrationException("Migrations for '" + module + "' failed: " + cause.getMessage(), cause);
        }
    }

    /** Internal carrier so the message survives {@link Database#blocking}'s wrapping. */
    private static final class MigrationFailure extends SQLException {
        MigrationFailure(String message, SQLException cause) {
            super(message, cause);
        }
    }

    private int apply(Connection c, String module, List<Migration> migrations) throws SQLException {
        try (Statement st = c.createStatement()) {
            st.execute("""
                    CREATE TABLE IF NOT EXISTS schema_version (
                      module_id VARCHAR(64) NOT NULL,
                      version_no INT NOT NULL,
                      description VARCHAR(255) NOT NULL,
                      checksum VARCHAR(64) NOT NULL,
                      applied_at BIGINT NOT NULL,
                      PRIMARY KEY (module_id, version_no)
                    )""");
        }
        Map<Integer, String> applied = new TreeMap<>();
        for (Object[] row : Sql.query(c, "SELECT version_no, checksum FROM schema_version WHERE module_id = ?",
                rs -> new Object[]{rs.getInt(1), rs.getString(2)}, module)) {
            applied.put((Integer) row[0], (String) row[1]);
        }
        int count = 0;
        for (Migration m : migrations) {
            String checksum = checksum(m.sql());
            String known = applied.get(m.version());
            if (known != null) {
                if (!known.equals(checksum)) {
                    log.warning("db/" + module + "/" + m.file() + " was changed after it was applied. Changes to applied "
                            + "migrations are ignored; add a new V" + String.format("%03d", m.version() + 1) + " file instead.");
                }
                continue;
            }
            List<String> statements = split(m.sql());
            for (int i = 0; i < statements.size(); i++) {
                try (Statement st = c.createStatement()) {
                    st.execute(statements.get(i));
                } catch (SQLException e) {
                    throw new MigrationFailure("db/" + module + "/" + m.file() + " failed at statement " + (i + 1) + "/"
                            + statements.size() + ": " + e.getMessage(), e);
                }
            }
            Sql.update(c, "INSERT INTO schema_version (module_id, version_no, description, checksum, applied_at) VALUES (?, ?, ?, ?, ?)",
                    module, m.version(), m.description(), checksum, System.currentTimeMillis());
            log.info("Database: applied db/" + module + "/" + m.file());
            count++;
        }
        return count;
    }

    /** Finds and orders the generic migration files of a module (no dialect variants). */
    static List<Migration> find(String module, ClassLoader loader) throws IOException, URISyntaxException {
        return find(module, loader, null);
    }

    /**
     * Finds and orders the migration files of a module for {@code dialect} ({@code "mariadb"}/{@code "h2"}, or
     * {@code null} for generic files only).
     */
    static List<Migration> find(String module, ClassLoader loader, String dialect) throws IOException, URISyntaxException {
        String dir = "db/" + module;
        Map<Integer, Migration> byVersion = new TreeMap<>();
        List<URL> urls = new ArrayList<>(java.util.Collections.list(loader.getResources(dir)));
        if (urls.isEmpty()) {
            // Some jars have no directory entries; scan the plugin jar itself.
            URL self = Migrations.class.getProtectionDomain().getCodeSource().getLocation();
            if (self != null && self.getPath().endsWith(".jar")) {
                urls.add(java.net.URI.create("jar:" + self + "!/" + dir).toURL());
            }
        }
        for (URL url : urls) {
            for (String name : list(url, dir)) {
                Matcher m = FILE.matcher(name);
                if (!m.matches()) {
                    if (name.endsWith(".sql")) {
                        throw new IOException(dir + "/" + name + " must be named like V001__description.sql");
                    }
                    continue;
                }
                int version = Integer.parseInt(m.group(1));
                String variant = m.group(3);
                if (variant != null && !variant.equals(dialect)) {
                    continue; // for the other database
                }
                Migration existing = byVersion.get(version);
                if (existing != null && variant == null && existing.file().contains("." + dialect + ".")) {
                    continue; // the dialect variant already won
                }
                String sql;
                try (InputStream in = loader.getResourceAsStream(dir + "/" + name)) {
                    if (in == null) {
                        continue;
                    }
                    sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                }
                Migration previous = byVersion.put(version, new Migration(version, m.group(2).replace('_', ' '), name, sql));
                boolean variantReplacesGeneric = previous != null && variant != null && !previous.file().contains("." + variant + ".")
                        && previous.file().replace(".sql", "").equals(name.replace("." + variant + ".sql", ""));
                if (previous != null && !previous.file().equals(name) && !variantReplacesGeneric) {
                    throw new IOException(dir + ": two migrations with version " + version + " (" + previous.file() + ", " + name + ")");
                }
            }
        }
        return new ArrayList<>(byVersion.values());
    }

    private static List<String> list(URL url, String dir) throws IOException, URISyntaxException {
        List<String> names = new ArrayList<>();
        switch (url.getProtocol()) {
            case "file" -> {
                try (Stream<Path> files = Files.list(Paths.get(url.toURI()))) {
                    files.map(p -> p.getFileName().toString()).forEach(names::add);
                }
            }
            case "jar" -> {
                JarURLConnection connection = (JarURLConnection) url.openConnection();
                connection.setUseCaches(false);
                try (JarFile jar = connection.getJarFile()) {
                    String prefix = dir + "/";
                    Enumeration<JarEntry> entries = jar.entries();
                    while (entries.hasMoreElements()) {
                        String name = entries.nextElement().getName();
                        if (name.startsWith(prefix) && name.length() > prefix.length() && name.indexOf('/', prefix.length()) < 0) {
                            names.add(name.substring(prefix.length()));
                        }
                    }
                }
            }
            default -> throw new IOException("Unsupported resource location " + url);
        }
        return names;
    }

    /** Splits a script into statements (ends with ; at line end); drops {@code --} comment lines. */
    static List<String> split(String sql) {
        StringBuilder cleaned = new StringBuilder();
        for (String line : sql.split("\\r?\\n")) {
            if (!line.trim().startsWith("--")) {
                cleaned.append(line).append('\n');
            }
        }
        List<String> out = new ArrayList<>();
        for (String part : STATEMENT_END.split(cleaned)) {
            String s = part.trim();
            if (!s.isEmpty()) {
                out.add(s);
            }
        }
        return out;
    }

    static String checksum(String sql) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(sql.replace("\r\n", "\n").trim().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
