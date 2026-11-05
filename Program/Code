package app;

import exception.DataAccessException;
import exception.ReservationException;
import model.Reservation;
import services.HotelReservationService;
import util.Validator;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Scanner;
import java.util.function.Function;

/** Console user interface: prompts, re-prompts on bad input, and prints results. */
final public class ConsoleMenu {

    private static final class CancelledException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        CancelledException() {
            super("cancelled");
        }
    }

    private static final String ROW_FORMAT = "%-5s %-6s %-22s %-16s %-28s %-11s %-11s %-6s%n";

    private final Scanner scanner;
    private final HotelReservationService service;

    public ConsoleMenu(Scanner scanner, HotelReservationService service) {
        this.scanner = scanner;
        this.service = service;
    }

    public void run() {
        try {
            boolean running = true;
            while (running) {
                printMenu();
                String choice = readLine("Enter your choice: ").trim();
                try {
                    switch (choice) {
                        case "1": createReservation(); break;
                        case "2": showReservations(); break;
                        case "3": updateReservation(); break;
                        case "4": deleteReservation(); break;
                        case "5": searchReservations(); break;
                        case "6": checkAvailability(); break;
                        case "7": running = false; break;
                        default: System.out.println("Invalid choice. Please enter a number from 1 to 7.");
                    }
                } catch (CancelledException e) {
                    System.out.println("Cancelled.");
                } catch (ReservationException e) {
                    System.out.println("Error: " + e.getMessage());
                } catch (DataAccessException e) {
                    System.out.println(e.getMessage());
                    System.out.println("Check your database settings and that the 'reservations' table exists (see sql/schema.sql).");
                }
                System.out.println();
            }
        } catch (NoSuchElementException e) {
            // stdin was closed (Ctrl+D / Ctrl+Z, or piped input ran out)
            System.out.println("\nInput closed. Exiting.");
        }
    }

    private void printMenu() {
        System.out.println("----- Hotel Reservation System -----\n");
        System.out.println("1. Create Hotel Reservation");
        System.out.println("2. Show Hotel Reservations");
        System.out.println("3. Update Hotel Reservation");
        System.out.println("4. Delete Hotel Reservation");
        System.out.println("5. Search Reservations (name or phone)");
        System.out.println("6. Check Room Availability");
        System.out.println("7. Quit");
    }

    // ------------------------------------------------------------- actions

    private void createReservation() {
        System.out.println("--- Creating Reservation --- (type 'cancel' at any prompt to go back)\n");
        int room = ask("Room number", Validator::room);
        String name = ask("Client name", Validator::name);
        String phone = ask("Client phone", Validator::phone);
        String email = ask("Client email", Validator::email);
        LocalDate checkIn = ask("Check-in date (yyyy-MM-dd)", s -> {
            LocalDate d = Validator.date(s);
            Validator.futureCheckIn(d);
            return d;
        });
        LocalDate checkOut = ask("Check-out date (yyyy-MM-dd)", s -> {
            LocalDate d = Validator.date(s);
            Validator.stay(checkIn, d);
            return d;
        });

        Reservation r = service.createReservation(name, phone, email, room, checkIn, checkOut);
        System.out.printf("Reservation #%d created: room %d, %s to %s (%d night(s)).%n",
                r.getId(), r.getRoomNo(), r.getCheckIn(), r.getCheckOut(), r.getNights());
    }

    private void showReservations() {
        System.out.println("--- Reservations ---\n");
        printTable(service.getAllReservations());
    }

    private void searchReservations() {
        System.out.println("--- Search Reservations --- (type 'cancel' to go back)\n");
        String term = ask("Name or phone to search for", Function.identity());
        printTable(service.searchReservations(term));
    }

    private void updateReservation() {
        System.out.println("--- Updating Reservation --- (type 'cancel' at any prompt to go back)\n");
        int id = ask("Reservation id", Validator::id);
        Reservation r = service.getReservation(id);
        printTable(Collections.singletonList(r));
        System.out.println("Press Enter to keep the value shown in [brackets].\n");

        r.setClientName(askOrKeep("Name", r.getClientName(), Validator::name));
        r.setClientPhone(askOrKeep("Phone", r.getClientPhone(), Validator::phone));
        r.setClientEmail(askOrKeep("Email", r.getClientEmail(), Validator::email));
        r.setRoomNo(askOrKeep("Room", r.getRoomNo(), Validator::room));
        LocalDate originalCheckIn = r.getCheckIn();
        LocalDate newCheckIn = askOrKeep("Check-in (yyyy-MM-dd)", originalCheckIn, s -> {
            LocalDate d = Validator.date(s);
            Validator.futureCheckIn(d);
            return d;
        });
        r.setCheckIn(newCheckIn);
        r.setCheckOut(askOrKeep("Check-out (yyyy-MM-dd)", r.getCheckOut(), s -> {
            LocalDate d = Validator.date(s);
            Validator.stay(newCheckIn, d);
            return d;
        }));

        service.updateReservation(r);
        System.out.println("Reservation #" + r.getId() + " updated successfully.");
    }

    private void deleteReservation() {
        System.out.println("--- Deleting Reservation --- (type 'cancel' at any prompt to go back)\n");
        int id = ask("Reservation id", Validator::id);
        Reservation r = service.getReservation(id);
        printTable(Collections.singletonList(r));
        String confirm = readLine("Delete this reservation? (y/N): ").trim();
        if (confirm.equalsIgnoreCase("y") || confirm.equalsIgnoreCase("yes")) {
            service.deleteReservation(id);
            System.out.println("Reservation #" + id + " deleted successfully.");
        } else {
            System.out.println("Nothing was deleted.");
        }
    }

    private void checkAvailability() {
        System.out.println("--- Check Room Availability --- (type 'cancel' at any prompt to go back)\n");
        int room = ask("Room number", Validator::room);
        LocalDate checkIn = ask("Check-in date (yyyy-MM-dd)", Validator::date);
        LocalDate checkOut = ask("Check-out date (yyyy-MM-dd)", s -> {
            LocalDate d = Validator.date(s);
            Validator.stay(checkIn, d);
            return d;
        });
        if (service.isRoomAvailable(room, checkIn, checkOut)) {
            System.out.printf("Room %d is available from %s to %s.%n", room, checkIn, checkOut);
        } else {
            System.out.printf("Room %d is NOT available from %s to %s.%n", room, checkIn, checkOut);
        }
    }

    // ------------------------------------------------------------- output

    private void printTable(List<Reservation> reservations) {
        if (reservations.isEmpty()) {
            System.out.println("No reservations found.");
            return;
        }
        System.out.printf(ROW_FORMAT, "ID", "Room", "Name", "Phone", "Email", "Check-in", "Check-out", "Nights");
        System.out.println("-".repeat(112));
        for (Reservation r : reservations) {
            System.out.printf(ROW_FORMAT, r.getId(), r.getRoomNo(), truncate(r.getClientName(), 22),
                    truncate(r.getClientPhone(), 16), truncate(r.getClientEmail(), 28),
                    r.getCheckIn(), r.getCheckOut(), r.getNights());
        }
        System.out.println(reservations.size() + " reservation(s).");
    }

    private static String truncate(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max - 3) + "...";
    }

    // ------------------------------------------------------------- input

    private String readLine(String prompt) {
        System.out.print(prompt);
        return scanner.nextLine();
    }

    /** Asks until the parser accepts the answer. Typing "cancel" aborts the whole action. */
    private <T> T ask(String label, Function<String, T> parser) {
        while (true) {
            String input = readLine(label + ": ").trim();
            if (input.equalsIgnoreCase("cancel")) {
                throw new CancelledException();
            }
            try {
                return parser.apply(input);
            } catch (ReservationException e) {
                System.out.println("  ! " + e.getMessage());
            }
        }
    }

    /** Like {@link #ask} but an empty answer keeps the current value. */
    private <T> T askOrKeep(String label, T current, Function<String, T> parser) {
        while (true) {
            String input = readLine(label + " [" + current + "]: ").trim();
            if (input.isEmpty()) {
                return current;
            }
            if (input.equalsIgnoreCase("cancel")) {
                throw new CancelledException();
            }
            try {
                return parser.apply(input);
            } catch (ReservationException e) {
                System.out.println("  ! " + e.getMessage());
            }
        }
    }
}
