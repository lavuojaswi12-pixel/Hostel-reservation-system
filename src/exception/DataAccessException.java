package exception;

/**
 * Wraps a low-level {@link java.sql.SQLException} so upper layers do not depend on JDBC.
 */
public class DataAccessException extends RuntimeException {
    private static final long serialVersionUID = 1L;


    public DataAccessException(String message, Throwable cause) {
        super(message, cause);
    }
}
