package dao;

import connection.DBConnection;
import exception.DataAccessException;
import exception.ReservationException;
import model.Reservation;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * JDBC implementation. Every method opens its own connection and closes it with
 * try-with-resources, so nothing leaks and the class holds no mutable state.
 * Writes run in a transaction: the overlap check and the INSERT/UPDATE commit together.
 */
final public class HotelReservationDB implements ReservationRepository {

    // `date` and `time` are quoted because they are keywords in some SQL dialects.
    private static final String COLUMNS = "id, name, room, email, phone, check_in, check_out, `date`, `time`";

    @FunctionalInterface
    private interface SqlWork<T> {
        T run(Connection connection) throws SQLException;
    }

    public HotelReservationDB() {
    }

    @Override
    public int add(Reservation r) {
        return inTransaction(c -> {
            requireNoConflict(c, r.getRoomNo(), r.getCheckIn(), r.getCheckOut(), 0);
            String sql = "INSERT INTO reservations(name, room, email, phone, check_in, check_out, `date`, `time`) "
                    + "VALUES(?,?,?,?,?,?,?,?)";
            try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, r.getClientName());
                ps.setInt(2, r.getRoomNo());
                ps.setString(3, r.getClientEmail());
                ps.setString(4, r.getClientPhone());
                ps.setDate(5, Date.valueOf(r.getCheckIn()));
                ps.setDate(6, Date.valueOf(r.getCheckOut()));
                ps.setDate(7, Date.valueOf(r.getReservationDate()));
                ps.setString(8, r.getReservationTime());
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (keys.next()) {
                        return keys.getInt(1);
                    }
                }
                throw new SQLException("Reservation was inserted but no id was returned.");
            }
        });
    }

    @Override
    public List<Reservation> findAll() {
        return withConnection(c -> {
            try (PreparedStatement ps = c.prepareStatement("SELECT " + COLUMNS + " FROM reservations ORDER BY id")) {
                return readAll(ps);
            }
        });
    }

    @Override
    public Optional<Reservation> findById(int id) {
        return withConnection(c -> {
            try (PreparedStatement ps = c.prepareStatement("SELECT " + COLUMNS + " FROM reservations WHERE id = ?")) {
                ps.setInt(1, id);
                List<Reservation> rows = readAll(ps);
                return rows.isEmpty() ? Optional.<Reservation>empty() : Optional.of(rows.get(0));
            }
        });
    }

    @Override
    public List<Reservation> search(String nameTerm, String phoneTerm) {
        return withConnection(c -> {
            String sql = "SELECT " + COLUMNS + " FROM reservations WHERE name LIKE ? OR phone LIKE ? ORDER BY id";
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setString(1, "%" + escapeLike(nameTerm) + "%");
                ps.setString(2, "%" + escapeLike(phoneTerm) + "%");
                return readAll(ps);
            }
        });
    }

    @Override
    public void update(Reservation r) {
        inTransaction(c -> {
            try (PreparedStatement ps = c.prepareStatement("SELECT id FROM reservations WHERE id = ? FOR UPDATE")) {
                ps.setInt(1, r.getId());
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw new ReservationException("Reservation #" + r.getId() + " not found.");
                    }
                }
            }
            requireNoConflict(c, r.getRoomNo(), r.getCheckIn(), r.getCheckOut(), r.getId());
            String sql = "UPDATE reservations SET name = ?, room = ?, email = ?, phone = ?, "
                    + "check_in = ?, check_out = ? WHERE id = ?";
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setString(1, r.getClientName());
                ps.setInt(2, r.getRoomNo());
                ps.setString(3, r.getClientEmail());
                ps.setString(4, r.getClientPhone());
                ps.setDate(5, Date.valueOf(r.getCheckIn()));
                ps.setDate(6, Date.valueOf(r.getCheckOut()));
                ps.setInt(7, r.getId());
                ps.executeUpdate();
            }
            return null;
        });
    }

    @Override
    public boolean delete(int id) {
        return inTransaction(c -> {
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM reservations WHERE id = ?")) {
                ps.setInt(1, id);
                return ps.executeUpdate() > 0;
            }
        });
    }

    @Override
    public boolean isRoomAvailable(int room, LocalDate checkIn, LocalDate checkOut) {
        return withConnection(c -> !findConflict(c, room, checkIn, checkOut, 0, false).isPresent());
    }

    // ---------------------------------------------------------------- helpers

    /** Existing.check_in < new.check_out AND existing.check_out > new.check_in, same room, other reservation. */
    private Optional<Reservation> findConflict(Connection c, int room, LocalDate checkIn, LocalDate checkOut,
                                               int excludeId, boolean lock) throws SQLException {
        String sql = "SELECT " + COLUMNS + " FROM reservations "
                + "WHERE room = ? AND check_in < ? AND check_out > ? AND id <> ? "
                + "ORDER BY check_in LIMIT 1" + (lock ? " FOR UPDATE" : "");
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, room);
            ps.setDate(2, Date.valueOf(checkOut));
            ps.setDate(3, Date.valueOf(checkIn));
            ps.setInt(4, excludeId);
            List<Reservation> rows = readAll(ps);
            return rows.isEmpty() ? Optional.<Reservation>empty() : Optional.of(rows.get(0));
        }
    }

    private void requireNoConflict(Connection c, int room, LocalDate checkIn, LocalDate checkOut,
                                   int excludeId) throws SQLException {
        Optional<Reservation> conflict = findConflict(c, room, checkIn, checkOut, excludeId, true);
        if (conflict.isPresent()) {
            Reservation other = conflict.get();
            throw new ReservationException(String.format(
                    "Room %d is already booked from %s to %s (reservation #%d).",
                    room, other.getCheckIn(), other.getCheckOut(), other.getId()));
        }
    }

    private static List<Reservation> readAll(PreparedStatement ps) throws SQLException {
        List<Reservation> list = new ArrayList<>();
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapRow(rs));
            }
        }
        return list;
    }

    private static Reservation mapRow(ResultSet rs) throws SQLException {
        Reservation r = new Reservation();
        r.setId(rs.getInt("id"));
        r.setClientName(rs.getString("name"));
        r.setRoomNo(rs.getInt("room"));
        r.setClientEmail(rs.getString("email"));
        r.setClientPhone(rs.getString("phone"));
        r.setCheckIn(rs.getObject("check_in", LocalDate.class));
        r.setCheckOut(rs.getObject("check_out", LocalDate.class));
        r.setReservationDate(rs.getObject("date", LocalDate.class));
        r.setReservationTime(rs.getString("time"));
        return r;
    }

    /** Escapes LIKE wildcards so a search for "50%" or "a_b" is taken literally. */
    private static String escapeLike(String term) {
        return term.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private <T> T withConnection(SqlWork<T> work) {
        try (Connection c = DBConnection.getConnection()) {
            return work.run(c);
        } catch (SQLException e) {
            throw new DataAccessException("Database error: " + e.getMessage(), e);
        }
    }

    private <T> T inTransaction(SqlWork<T> work) {
        return withConnection(c -> {
            c.setAutoCommit(false);
            try {
                T result = work.run(c);
                c.commit();
                return result;
            } catch (SQLException | RuntimeException e) {
                c.rollback();
                throw e;
            }
        });
    }
}
