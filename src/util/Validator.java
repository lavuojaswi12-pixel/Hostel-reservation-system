package util;

import exception.ReservationException;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Input validation and normalisation. Every method either returns the cleaned value
 * or throws a {@link ReservationException} with a user-friendly message.
 */
final public class Validator {

    public static final int MAX_ROOM = 9999;
    public static final int MAX_NIGHTS = 365;

    private static final Pattern NAME = Pattern.compile("^[\\p{L}][\\p{L} .'\\-]*$");
    private static final Pattern PHONE = Pattern.compile("^\\+?\\d{7,15}$");
    private static final Pattern EMAIL = Pattern.compile("^[A-Za-z0-9._%+\\-]+@[A-Za-z0-9\\-]+(\\.[A-Za-z0-9\\-]+)+$");

    private Validator() {}

    public static String name(String input) {
        String value = required(input, "Name").replaceAll("\\s+", " ");
        if (value.length() > 100) {
            throw new ReservationException("Name must be at most 100 characters.");
        }
        if (!NAME.matcher(value).matches()) {
            throw new ReservationException("Name may only contain letters, spaces, apostrophes, dots and hyphens.");
        }
        return value;
    }

    /** Strips spaces, dashes, dots and brackets, so "+1 (555) 123-4567" is stored as "+15551234567". */
    public static String phone(String input) {
        String value = required(input, "Phone").replaceAll("[\\s\\-().]", "");
        if (!PHONE.matcher(value).matches()) {
            throw new ReservationException("Phone must be 7 to 15 digits, optionally starting with '+'.");
        }
        return value;
    }

    public static String email(String input) {
        String value = required(input, "Email");
        if (value.length() > 150 || !EMAIL.matcher(value).matches()) {
            throw new ReservationException("Please enter a valid email address (e.g. name@example.com).");
        }
        return value.toLowerCase(Locale.ROOT);
    }

    public static int room(String input) {
        return room(integer(input, "Room number"));
    }

    public static int room(int room) {
        if (room < 1 || room > MAX_ROOM) {
            throw new ReservationException("Room number must be between 1 and " + MAX_ROOM + ".");
        }
        return room;
    }

    public static int id(String input) {
        int id = integer(input, "Reservation id");
        if (id < 1) {
            throw new ReservationException("Reservation id must be a positive number.");
        }
        return id;
    }

    public static LocalDate date(String input) {
        try {
            return LocalDate.parse(required(input, "Date"));
        } catch (DateTimeParseException e) {
            throw new ReservationException("Date must be a real date in yyyy-MM-dd format (e.g. 2026-12-31).");
        }
    }

    /** Check-in may be today or later. Only applied to new or changed check-in dates. */
    public static void futureCheckIn(LocalDate checkIn) {
        if (checkIn == null) {
            throw new ReservationException("Check-in date is required.");
        }
        if (checkIn.isBefore(LocalDate.now())) {
            throw new ReservationException("Check-in date cannot be in the past.");
        }
    }

    public static void stay(LocalDate checkIn, LocalDate checkOut) {
        if (checkIn == null || checkOut == null) {
            throw new ReservationException("Check-in and check-out dates are required.");
        }
        if (!checkOut.isAfter(checkIn)) {
            throw new ReservationException("Check-out date must be after the check-in date.");
        }
        if (ChronoUnit.DAYS.between(checkIn, checkOut) > MAX_NIGHTS) {
            throw new ReservationException("A single reservation cannot exceed " + MAX_NIGHTS + " nights.");
        }
    }

    private static int integer(String input, String label) {
        try {
            return Integer.parseInt(required(input, label));
        } catch (NumberFormatException e) {
            throw new ReservationException(label + " must be a whole number.");
        }
    }

    private static String required(String input, String label) {
        if (input == null || input.isBlank()) {
            throw new ReservationException(label + " is required.");
        }
        return input.trim();
    }
}
