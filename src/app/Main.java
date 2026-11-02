package app;

import connection.DBConnection;
import dao.HotelReservationDB;
import services.HotelReservationService;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Scanner;

public class Main {

    @SuppressWarnings("try")
    public static void main(String[] args) {
        // Fail fast with a clear message instead of crashing on the first menu action.
        try (Connection ignored = DBConnection.getConnection()) {
            // connection opened and closed: configuration is fine
        } catch (SQLException e) {
            System.err.println("Could not connect to the database: " + e.getMessage());
            System.err.println("URL: " + DBConnection.getUrl());
            System.err.println("Set HOTEL_DB_URL / HOTEL_DB_USER / HOTEL_DB_PASSWORD, and make sure the");
            System.err.println("MySQL Connector/J jar is on the classpath (put it in lib/).");
            System.exit(1);
        }

        try (Scanner scanner = new Scanner(System.in)) {
            new ConsoleMenu(scanner, new HotelReservationService(new HotelReservationDB())).run();
        }
        System.out.println("Terminated");
    }
}
