package net.skybornmc.skyblock.common.db;

import java.nio.ByteBuffer;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Small DAO helpers. <b>Prepared statements only</b>: values are always bound as parameters, never concatenated
 * into SQL. Conventions (STANDARDS / PHASE-0 M0.3): UUIDs as {@code BINARY(16)}, times as UTC epoch millis
 * ({@code BIGINT}), money as {@code BIGINT}, every gameplay table has {@code season_id}.
 */
public final class Sql {

    private Sql() {
    }

    /** Maps one row. */
    @FunctionalInterface
    public interface RowMapper<T> {
        T map(ResultSet rs) throws SQLException;
    }

    /** Work with a connection. */
    @FunctionalInterface
    public interface SqlFunction<T> {
        T apply(Connection connection) throws SQLException;
    }

    /** Work with a connection, no result. */
    @FunctionalInterface
    public interface SqlConsumer {
        void accept(Connection connection) throws SQLException;
    }

    /** INSERT/UPDATE/DELETE; returns affected rows. */
    public static int update(Connection c, String sql, Object... params) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            bind(ps, params);
            return ps.executeUpdate();
        }
    }

    /** INSERT returning the generated key (AUTO_INCREMENT id). */
    public static long insertReturningId(Connection c, String sql, Object... params) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bind(ps, params);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
            }
            throw new SQLException("No generated key returned for: " + sql);
        }
    }

    /** Runs the same statement for many parameter rows in one batch. */
    public static int[] batch(Connection c, String sql, List<Object[]> rows) throws SQLException {
        if (rows.isEmpty()) {
            return new int[0];
        }
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            for (Object[] row : rows) {
                bind(ps, row);
                ps.addBatch();
            }
            return ps.executeBatch();
        }
    }

    public static <T> List<T> query(Connection c, String sql, RowMapper<T> mapper, Object... params) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            bind(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                List<T> out = new ArrayList<>();
                while (rs.next()) {
                    out.add(mapper.map(rs));
                }
                return out;
            }
        }
    }

    public static <T> Optional<T> queryOne(Connection c, String sql, RowMapper<T> mapper, Object... params) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            bind(ps, params);
            ps.setMaxRows(1);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.ofNullable(mapper.map(rs)) : Optional.empty();
            }
        }
    }

    /** Binds parameters: UUID → BINARY(16), enums → name, Instant → epoch millis, null → SQL NULL. */
    public static void bind(PreparedStatement ps, Object... params) throws SQLException {
        for (int i = 0; i < params.length; i++) {
            Object p = params[i];
            int index = i + 1;
            switch (p) {
                case null -> ps.setNull(index, Types.NULL);
                case UUID uuid -> ps.setBytes(index, toBytes(uuid));
                case Enum<?> e -> ps.setString(index, e.name());
                case java.time.Instant instant -> ps.setLong(index, instant.toEpochMilli());
                case Boolean b -> ps.setBoolean(index, b);
                case Integer n -> ps.setInt(index, n);
                case Long n -> ps.setLong(index, n);
                case String s -> ps.setString(index, s);
                case byte[] bytes -> ps.setBytes(index, bytes);
                default -> ps.setObject(index, p);
            }
        }
    }

    public static byte[] toBytes(UUID uuid) {
        return ByteBuffer.allocate(16).putLong(uuid.getMostSignificantBits()).putLong(uuid.getLeastSignificantBits()).array();
    }

    public static UUID toUuid(byte[] bytes) {
        if (bytes == null || bytes.length != 16) {
            throw new IllegalArgumentException("UUID column must hold 16 bytes");
        }
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        return new UUID(buffer.getLong(), buffer.getLong());
    }

    /** Reads a {@code BINARY(16)} UUID column. */
    public static UUID uuid(ResultSet rs, String column) throws SQLException {
        byte[] bytes = rs.getBytes(column);
        return bytes == null ? null : toUuid(bytes);
    }

    /** Reads a nullable BIGINT. */
    public static Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }
}
