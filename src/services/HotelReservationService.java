package services;

import dao.ReservationRepository;
import exception.ReservationException;
import model.Reservation;
import util.DateAndTime;
import util.Validator;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * Business logic: validation, normalisation and orchestration. No console I/O happens
 * here, so the same service could back a GUI or a REST API.
 */
final public class HotelReservationService {

    private final ReservationRepository repository;

    public HotelReservationService(ReservationRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    public Reservation createReservation(String name, String phone, String email, int room,
                                         LocalDate checkIn, LocalDate checkOut) {
        Reservation reservation = new Reservation();
        reservation.setClientName(Validator.name(name));
        reservation.setClientPhone(Validator.phone(phone));
        reservation.setClientEmail(Validator.email(email));
        reservation.setRoomNo(Validator.room(room));
        Validator.stay(checkIn, checkOut);
        Validator.futureCheckIn(checkIn);
        reservation.setCheckIn(checkIn);
        reservation.setCheckOut(checkOut);
        reservation.setReservationDate(DateAndTime.getDate());
        reservation.setReservationTime(DateAndTime.getTime());

        reservation.setId(repository.add(reservation));
        return reservation;
    }

    public List<Reservation> getAllReservations() {
        return repository.findAll();
    }

    public Reservation getReservation(int id) {
        return repository.findById(id)
                .orElseThrow(() -> new ReservationException("Reservation #" + id + " not found."));
    }

    public List<Reservation> searchReservations(String term) {
        if (term == null || term.isBlank()) {
            throw new ReservationException("Search text is required.");
        }
        String nameTerm = term.trim();
        String phoneTerm = nameTerm.replaceAll("[\\s\\-().]", "");
        return repository.search(nameTerm, phoneTerm.isEmpty() ? nameTerm : phoneTerm);
    }

    /** Saves the edited fields of a reservation previously loaded with {@link #getReservation(int)}. */
    public void updateReservation(Reservation updated) {
        Reservation existing = getReservation(updated.getId());
        updated.setClientName(Validator.name(updated.getClientName()));
        updated.setClientPhone(Validator.phone(updated.getClientPhone()));
        updated.setClientEmail(Validator.email(updated.getClientEmail()));
        updated.setRoomNo(Validator.room(updated.getRoomNo()));
        Validator.stay(updated.getCheckIn(), updated.getCheckOut());
        // A guest who is already in the hotel must still be editable, so only re-check a *changed* check-in.
        if (!updated.getCheckIn().equals(existing.getCheckIn())) {
            Validator.futureCheckIn(updated.getCheckIn());
        }
        repository.update(updated);
    }

    public void deleteReservation(int id) {
        if (!repository.delete(id)) {
            throw new ReservationException("Reservation #" + id + " not found.");
        }
    }

    public boolean isRoomAvailable(int room, LocalDate checkIn, LocalDate checkOut) {
        Validator.room(room);
        Validator.stay(checkIn, checkOut);
        return repository.isRoomAvailable(room, checkIn, checkOut);
    }
}
