import app.ConsoleMenu;
import dao.ReservationRepository;
import exception.ReservationException;
import model.Reservation;
import services.HotelReservationService;
import util.Validator;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Scanner;
import java.util.stream.Collectors;

/**
 * Dependency-free test runner (no JUnit needed to build offline).
 * Run with: ant test
 */
public class TestRunner {

    interface Check { void run() throws Exception; }

    private static int passed = 0;
    private static int failed = 0;

    static void test(String name, Check check) {
        try {
            check.run();
            passed++;
            System.out.println("  PASS  " + name);
        } catch (Throwable t) {
            failed++;
            System.out.println("  FAIL  " + name + "  ->  " + t);
        }
    }

    static void assertTrue(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    static void assertEquals(Object expected, Object actual) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError("expected <" + expected + "> but was <" + actual + ">");
        }
    }

    static void assertThrows(String messagePart, Check check) {
        try {
            check.run();
        } catch (ReservationException e) {
            assertTrue(e.getMessage().contains(messagePart),
                    "message <" + e.getMessage() + "> should contain <" + messagePart + ">");
            return;
        } catch (Exception e) {
            throw new AssertionError("wrong exception type: " + e);
        }
        throw new AssertionError("expected ReservationException containing <" + messagePart + ">");
    }

    /** In-memory stand-in for the MySQL repository, with the same overlap rule as the SQL. */
    static class InMemoryRepository implements ReservationRepository {
        private final List<Reservation> rows = new ArrayList<>();
        private int nextId = 1;

        public int add(Reservation r) {
            checkConflict(r);
            Reservation copy = r.copy();
            copy.setId(nextId++);
            rows.add(copy);
            return copy.getId();
        }
        public List<Reservation> findAll() {
            return rows.stream().map(Reservation::copy).collect(Collectors.toList());
        }
        public Optional<Reservation> findById(int id) {
            return rows.stream().filter(r -> r.getId() == id).findFirst().map(Reservation::copy);
        }
        public List<Reservation> search(String nameTerm, String phoneTerm) {
            return rows.stream()
                    .filter(r -> r.getClientName().toLowerCase().contains(nameTerm.toLowerCase())
                            || r.getClientPhone().contains(phoneTerm))
                    .map(Reservation::copy).collect(Collectors.toList());
        }
        public void update(Reservation r) {
            if (!findById(r.getId()).isPresent()) throw new ReservationException("Reservation #" + r.getId() + " not found.");
            checkConflict(r);
            rows.removeIf(x -> x.getId() == r.getId());
            rows.add(r.copy());
            rows.sort(Comparator.comparingInt(Reservation::getId));
        }
        public boolean delete(int id) {
            return rows.removeIf(r -> r.getId() == id);
        }
        public boolean isRoomAvailable(int room, LocalDate in, LocalDate out) {
            return rows.stream().noneMatch(r -> r.getRoomNo() == room && r.overlaps(in, out));
        }
        private void checkConflict(Reservation candidate) {
            for (Reservation r : rows) {
                if (r.getId() != candidate.getId() && r.getRoomNo() == candidate.getRoomNo()
                        && r.overlaps(candidate.getCheckIn(), candidate.getCheckOut())) {
                    throw new ReservationException("Room " + r.getRoomNo() + " is already booked from "
                            + r.getCheckIn() + " to " + r.getCheckOut() + " (reservation #" + r.getId() + ").");
                }
            }
        }
    }

    static final LocalDate D = LocalDate.now().plusDays(10);

    static HotelReservationService newService() {
        return new HotelReservationService(new InMemoryRepository());
    }

    public static void main(String[] args) {
        System.out.println("Validator");
        test("name accepts normal names and collapses spaces", () -> assertEquals("Mary O'Neil-Smith", Validator.name("  Mary   O'Neil-Smith ")));
        test("name accepts unicode letters", () -> assertEquals("Zoë Müller", Validator.name("Zoë Müller")));
        test("name rejects digits", () -> assertThrows("Name may only", () -> Validator.name("R2D2")));
        test("name rejects blank", () -> assertThrows("required", () -> Validator.name("   ")));
        test("phone is normalised", () -> assertEquals("+15551234567", Validator.phone("+1 (555) 123-4567")));
        test("phone rejects letters", () -> assertThrows("Phone must", () -> Validator.phone("12ab567890")));
        test("phone rejects too short", () -> assertThrows("Phone must", () -> Validator.phone("12345")));
        test("email lower-cased", () -> assertEquals("a.b@example.com", Validator.email(" A.B@Example.com ")));
        test("email rejects missing domain", () -> assertThrows("valid email", () -> Validator.email("bob@")));
        test("room rejects 0 and text", () -> {
            assertThrows("between 1", () -> Validator.room("0"));
            assertThrows("whole number", () -> Validator.room("abc"));
            assertThrows("whole number", () -> Validator.room("99999999999"));
        });
        test("date rejects impossible dates", () -> assertThrows("yyyy-MM-dd", () -> Validator.date("2026-02-30")));
        test("past check-in rejected, today allowed", () -> {
            assertThrows("past", () -> Validator.futureCheckIn(LocalDate.now().minusDays(1)));
            Validator.futureCheckIn(LocalDate.now());
        });
        test("stay must be at least one night and at most a year", () -> {
            assertThrows("after the check-in", () -> Validator.stay(D, D));
            assertThrows("after the check-in", () -> Validator.stay(D, D.minusDays(1)));
            assertThrows("exceed", () -> Validator.stay(D, D.plusDays(400)));
        });

        System.out.println("Reservation model");
        test("overlap is half-open (same-day turnover is fine)", () -> {
            Reservation r = new Reservation();
            r.setCheckIn(D); r.setCheckOut(D.plusDays(3));
            assertTrue(r.overlaps(D.plusDays(2), D.plusDays(5)), "partial overlap");
            assertTrue(r.overlaps(D.minusDays(1), D.plusDays(10)), "enclosing");
            assertTrue(!r.overlaps(D.plusDays(3), D.plusDays(5)), "starts at checkout");
            assertTrue(!r.overlaps(D.minusDays(2), D), "ends at check-in");
            assertEquals(3L, r.getNights());
        });

        System.out.println("Service");
        test("create returns id, normalised data and booking timestamp", () -> {
            Reservation r = newService().createReservation("john  doe", "555 123 4567", "J@X.com", 101, D, D.plusDays(2));
            assertEquals(1, r.getId());
            assertEquals("5551234567", r.getClientPhone());
            assertEquals("j@x.com", r.getClientEmail());
            assertEquals(LocalDate.now(), r.getReservationDate());
            assertTrue(r.getReservationTime().matches("\\d\\d:\\d\\d"), "HH:mm");
        });
        test("two reservations get independent objects (old shared-object bug)", () -> {
            HotelReservationService s = newService();
            Reservation a = s.createReservation("Ann", "5551110000", "a@x.com", 1, D, D.plusDays(1));
            Reservation b = s.createReservation("Bob", "5552220000", "b@x.com", 2, D, D.plusDays(1));
            assertEquals("Ann", s.getReservation(a.getId()).getClientName());
            assertEquals("Bob", s.getReservation(b.getId()).getClientName());
        });
        test("double booking is rejected with a helpful message", () -> {
            HotelReservationService s = newService();
            s.createReservation("Ann", "5551110000", "a@x.com", 5, D, D.plusDays(4));
            assertThrows("Room 5 is already booked", () -> s.createReservation("Bob", "5552220000", "b@x.com", 5, D.plusDays(3), D.plusDays(6)));
        });
        test("back-to-back stays and other rooms are allowed", () -> {
            HotelReservationService s = newService();
            s.createReservation("Ann", "5551110000", "a@x.com", 5, D, D.plusDays(4));
            s.createReservation("Bob", "5552220000", "b@x.com", 5, D.plusDays(4), D.plusDays(6));
            s.createReservation("Cy", "5553330000", "c@x.com", 6, D, D.plusDays(4));
            assertEquals(3, s.getAllReservations().size());
        });
        test("create validates before touching the repository", () -> {
            HotelReservationService s = newService();
            assertThrows("past", () -> s.createReservation("Ann", "5551110000", "a@x.com", 5, LocalDate.now().minusDays(2), D));
            assertThrows("Phone", () -> s.createReservation("Ann", "x", "a@x.com", 5, D, D.plusDays(1)));
            assertEquals(0, s.getAllReservations().size());
        });
        test("update changes one reservation only (old update-by-phone bug)", () -> {
            HotelReservationService s = newService();
            Reservation a = s.createReservation("Ann", "5551110000", "a@x.com", 1, D, D.plusDays(1));
            Reservation b = s.createReservation("Ann", "5551110000", "a@x.com", 2, D.plusDays(5), D.plusDays(6));
            Reservation edit = s.getReservation(a.getId());
            edit.setClientName("Anna");
            s.updateReservation(edit);
            assertEquals("Anna", s.getReservation(a.getId()).getClientName());
            assertEquals("Ann", s.getReservation(b.getId()).getClientName());
        });
        test("update can extend own stay but cannot collide with another", () -> {
            HotelReservationService s = newService();
            Reservation a = s.createReservation("Ann", "5551110000", "a@x.com", 1, D, D.plusDays(2));
            s.createReservation("Bob", "5552220000", "b@x.com", 1, D.plusDays(5), D.plusDays(7));
            Reservation edit = s.getReservation(a.getId());
            edit.setCheckOut(D.plusDays(4));
            s.updateReservation(edit);
            edit.setCheckOut(D.plusDays(6));
            assertThrows("already booked", () -> s.updateReservation(edit));
        });
        test("update allowed for a stay that already started", () -> {
            InMemoryRepository repo = new InMemoryRepository();
            Reservation old = new Reservation();
            old.setClientName("Ann"); old.setClientPhone("5551110000"); old.setClientEmail("a@x.com"); old.setRoomNo(1);
            old.setCheckIn(LocalDate.now().minusDays(2)); old.setCheckOut(LocalDate.now().plusDays(2));
            old.setReservationDate(LocalDate.now().minusDays(9)); old.setReservationTime("10:00");
            int id = repo.add(old);
            HotelReservationService s = new HotelReservationService(repo);
            Reservation edit = s.getReservation(id);
            edit.setClientName("Anna");
            s.updateReservation(edit);
            assertEquals("Anna", s.getReservation(id).getClientName());
        });
        test("delete removes one row; unknown id gives a clear error", () -> {
            HotelReservationService s = newService();
            Reservation a = s.createReservation("Ann", "5551110000", "a@x.com", 1, D, D.plusDays(1));
            s.deleteReservation(a.getId());
            assertEquals(0, s.getAllReservations().size());
            assertThrows("not found", () -> s.deleteReservation(a.getId()));
            assertThrows("not found", () -> s.getReservation(42));
        });
        test("search by partial name (any case) and by formatted phone", () -> {
            HotelReservationService s = newService();
            s.createReservation("Ann Smith", "+15551110000", "a@x.com", 1, D, D.plusDays(1));
            s.createReservation("Bob Jones", "5552220000", "b@x.com", 2, D, D.plusDays(1));
            assertEquals(1, s.searchReservations("smi").size());
            assertEquals(1, s.searchReservations("+1 555-111").size());
            assertEquals(0, s.searchReservations("zzz").size());
            assertThrows("required", () -> s.searchReservations("  "));
        });
        test("availability", () -> {
            HotelReservationService s = newService();
            s.createReservation("Ann", "5551110000", "a@x.com", 1, D, D.plusDays(3));
            assertTrue(!s.isRoomAvailable(1, D.plusDays(1), D.plusDays(2)), "inside booked stay");
            assertTrue(s.isRoomAvailable(1, D.plusDays(3), D.plusDays(5)), "after checkout");
            assertTrue(s.isRoomAvailable(2, D, D.plusDays(3)), "other room");
        });

        System.out.println("Console menu (end-to-end with scripted input)");
        test("full session: create, show, search, update, availability, delete, quit", () -> {
            String in = D.toString(), out = D.plusDays(2).toString(), later = D.plusDays(3).toString();
            String script = String.join("\n",
                    "1", "abc", "101", "John Doe", "+1 555 123 4567", "bad-email", "john@example.com", in, out,   // create (with 2 bad inputs)
                    "2",                                                                                          // show
                    "5", "doe",                                                                                   // search
                    "3", "1", "", "", "", "", "", later,                                                          // update: keep all, new check-out
                    "6", "101", in, later,                                                                        // availability: now booked
                    "1", "101", "cancel",                                                                         // cancel mid-flow
                    "4", "1", "n",                                                                                // delete declined
                    "4", "1", "y",                                                                                // delete confirmed
                    "9",                                                                                          // invalid choice
                    "7") + "\n";
            String output = runMenu(script);
            assertTrue(output.contains("Room number must be a whole number"), "bad room re-prompt");
            assertTrue(output.contains("Please enter a valid email address"), "bad email re-prompt");
            assertTrue(output.contains("Reservation #1 created: room 101"), "created");
            assertTrue(output.contains("+15551234567") && output.contains("john@example.com"), "listed normalised");
            assertTrue(output.contains("1 reservation(s)."), "search/show count");
            assertTrue(output.contains("Reservation #1 updated successfully."), "updated");
            assertTrue(output.contains("Room 101 is NOT available"), "availability reflects update");
            assertTrue(output.contains("Cancelled."), "cancel");
            assertTrue(output.contains("Nothing was deleted."), "delete declined");
            assertTrue(output.contains("Reservation #1 deleted successfully."), "delete confirmed");
            assertTrue(output.contains("Invalid choice"), "invalid menu choice");
        });
        test("closed stdin exits cleanly instead of crashing", () -> {
            String output = runMenu("2\n");   // no '7' -> input ends
            assertTrue(output.contains("Input closed. Exiting."), "graceful EOF");
        });

        System.out.println("\n" + passed + " passed, " + failed + " failed");
        if (failed > 0) System.exit(1);
    }

    static String runMenu(String input) {
        PrintStream original = System.out;
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try {
            System.setOut(new PrintStream(buffer, true, "UTF-8"));
            new ConsoleMenu(new Scanner(input), newService()).run();
            return buffer.toString("UTF-8");
        } catch (java.io.UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        } finally {
            System.setOut(original);
        }
    }
}
