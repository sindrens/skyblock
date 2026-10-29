package net.skybornmc.skyblock.common.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.IntSupplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The database: a HikariCP pool (MariaDB on the VPS, H2 in MySQL mode locally) plus an async executor on
 * virtual threads. <b>All gameplay queries go through {@link #async}/{@link #run}/{@link #transaction}</b>, never on
 * the main thread; hop back with {@code .thenAcceptAsync(..., mainThread)} (see {@link MainThread}) only to touch
 * the world or players.
 */
public final class Database implements AutoCloseable {

    private final DatabaseConfig config;
    private final File dataFolder;
    private final Logger log;
    private final IntSupplier slowQueryMs;
    private HikariDataSource dataSource;
    private ExecutorService executor;

    public Database(DatabaseConfig config, File dataFolder, Logger log) {
        this.config = Objects.requireNonNull(config, "config");
        this.dataFolder = Objects.requireNonNull(dataFolder, "dataFolder");
        this.log = Objects.requireNonNull(log, "log");
        this.slowQueryMs = () -> config.slowQueryMs;
    }

    /**
     * Opens the pool and checks the connection.
     *
     * @throws SQLException with a readable message (wrong password, MariaDB down, …)
     */
    public void start() throws SQLException {
        HikariConfig hikari = new HikariConfig();
        hikari.setPoolName("PhoenixSkyblock");
        hikari.setJdbcUrl(config.jdbcUrl(dataFolder));
        hikari.setMaximumPoolSize(config.poolSize);
        hikari.setMinimumIdle(Math.min(2, config.poolSize));
        hikari.setConnectionTimeout(config.connectionTimeoutMs);
        hikari.setMaxLifetime(TimeUnit.MINUTES.toMillis(30));
        hikari.setKeepaliveTime(TimeUnit.MINUTES.toMillis(5));
        hikari.setInitializationFailTimeout(config.connectionTimeoutMs);
        switch (config.type) {
            case MARIADB -> {
                hikari.setDriverClassName("org.mariadb.jdbc.Driver");
                hikari.setUsername(config.user);
                hikari.setPassword(config.password);
                hikari.addDataSourceProperty("connectTimeout", String.valueOf(config.connectionTimeoutMs));
            }
            case H2 -> {
                hikari.setDriverClassName("org.h2.Driver");
                hikari.setUsername("sa");
                hikari.setPassword("");
                new File(dataFolder, config.h2File).getAbsoluteFile().getParentFile().mkdirs();
            }
        }
        try {
            dataSource = new HikariDataSource(hikari);
        } catch (RuntimeException e) {
            throw new SQLException(describeFailure(e), e);
        }
        executor = Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("skyblock-db-", 0).factory());
        try (Connection c = dataSource.getConnection()) {
            log.info("Database connected: " + config.type.name().toLowerCase() + " (" + c.getMetaData().getDatabaseProductName()
                    + " " + c.getMetaData().getDatabaseProductVersion() + "), pool size " + config.poolSize + ".");
        }
    }

    private String describeFailure(RuntimeException e) {
        Throwable root = e;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        String where = config.type == DatabaseConfig.Type.MARIADB
                ? "MariaDB at " + config.host + ":" + config.port + "/" + config.database + " as '" + config.user + "'"
                : "H2 file " + config.h2File;
        return "Could not connect to " + where + ": " + root.getMessage()
                + (config.type == DatabaseConfig.Type.MARIADB ? " (check database.yml / /opt/skyblock/.env and that MariaDB is running)" : "");
    }

    public DatabaseConfig.Type type() {
        return config.type;
    }

    public boolean isRunning() {
        return dataSource != null && !dataSource.isClosed() && executor != null && !executor.isShutdown();
    }

    /** Runs {@code work} on a database thread with its own connection. */
    public <T> CompletableFuture<T> async(Sql.SqlFunction<T> work) {
        ensureRunning();
        return CompletableFuture.supplyAsync(() -> blocking(work), executor);
    }

    /** Like {@link #async} without a result. */
    public CompletableFuture<Void> run(Sql.SqlConsumer work) {
        return async(c -> {
            work.accept(c);
            return null;
        });
    }

    /** Runs {@code work} in one transaction (commit on success, rollback on any error). */
    public <T> CompletableFuture<T> transaction(Sql.SqlFunction<T> work) {
        return async(c -> inTransaction(c, work));
    }

    /**
     * Runs {@code work} on the <b>current</b> thread. Only for startup (migrations), shutdown flushes and code
     * that is already off the main thread (e.g. {@code AsyncPlayerPreLoginEvent}).
     */
    public <T> T blocking(Sql.SqlFunction<T> work) {
        ensureRunning();
        long start = System.nanoTime();
        try (Connection c = dataSource.getConnection()) {
            return work.apply(c);
        } catch (SQLException e) {
            throw new CompletionException(e);
        } finally {
            long ms = (System.nanoTime() - start) / 1_000_000;
            if (ms > slowQueryMs.getAsInt()) {
                log.warning("Slow database work: " + ms + " ms (limit " + slowQueryMs.getAsInt() + " ms) at " + caller());
            }
        }
    }

    /** {@link #blocking} inside a transaction. */
    public <T> T blockingTransaction(Sql.SqlFunction<T> work) {
        return blocking(c -> inTransaction(c, work));
    }

    private static <T> T inTransaction(Connection c, Sql.SqlFunction<T> work) throws SQLException {
        boolean auto = c.getAutoCommit();
        c.setAutoCommit(false);
        try {
            T result = work.apply(c);
            c.commit();
            return result;
        } catch (SQLException | RuntimeException e) {
            try {
                c.rollback();
            } catch (SQLException rollback) {
                e.addSuppressed(rollback);
            }
            throw e;
        } finally {
            c.setAutoCommit(auto);
        }
    }

    private static String caller() {
        return StackWalker.getInstance().walk(frames -> frames
                .map(f -> f.getClassName() + "#" + f.getMethodName())
                .filter(name -> !name.startsWith(Database.class.getName()) && !name.startsWith("java."))
                .findFirst().orElse("?"));
    }

    private void ensureRunning() {
        if (!isRunning()) {
            throw new IllegalStateException("Database is not running");
        }
    }

    /** Waits up to 10 s for running work, then closes the pool. Call after every module saved its data. */
    @Override
    public void close() {
        if (executor != null) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                    log.warning("Database work still running after 10 s at shutdown; closing anyway.");
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        if (dataSource != null) {
            try {
                dataSource.close();
            } catch (RuntimeException e) {
                log.log(Level.WARNING, "Error while closing the database pool", e);
            }
        }
    }

    /** Unwraps {@link CompletionException}s for readable logs. */
    public static Throwable cause(Throwable t) {
        while ((t instanceof CompletionException || t instanceof java.util.concurrent.ExecutionException) && t.getCause() != null) {
            t = t.getCause();
        }
        return t;
    }
}
