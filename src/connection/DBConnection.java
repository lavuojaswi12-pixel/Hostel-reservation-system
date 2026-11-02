package connection;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Creates JDBC connections. Credentials are NOT hard-coded: set them with environment
 * variables (HOTEL_DB_URL, HOTEL_DB_USER, HOTEL_DB_PASSWORD) or with JVM properties
 * (-Dhotel.db.url, -Dhotel.db.user, -Dhotel.db.password). Defaults match a local MySQL
 * install with user "root" and an empty password.
 */
final public class DBConnection {

    private static final String URL = setting("hotel.db.url", "HOTEL_DB_URL", "jdbc:mysql://localhost:3306/hotel_db");
    private static final String USERNAME = setting("hotel.db.user", "HOTEL_DB_USER", "root");
    private static final String PASSWORD = setting("hotel.db.password", "HOTEL_DB_PASSWORD", "");

    private DBConnection() {
    }

    /** The caller owns the connection and must close it (use try-with-resources). */
    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL, USERNAME, PASSWORD);
    }

    public static String getUrl() {
        return URL;
    }

    private static String setting(String property, String environmentVariable, String defaultValue) {
        String value = System.getProperty(property);
        if (value == null || value.isEmpty()) {
            value = System.getenv(environmentVariable);
        }
        return (value == null || value.isEmpty()) ? defaultValue : value;
    }
}
