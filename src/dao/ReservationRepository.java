package dao;

import model.Reservation;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Storage contract for reservations, so the service layer does not depend on JDBC. */
public interface ReservationRepository {

    /**
     * Saves a new reservation and returns its generated id.
     * @throws exception.ReservationException if the room is already booked for the dates
     */
    int add(Reservation reservation);

    List<Reservation> findAll();

    Optional<Reservation> findById(int id);

    /** Reservations whose name contains {@code nameTerm} or whose phone contains {@code phoneTerm}. */
    List<Reservation> search(String nameTerm, String phoneTerm);

    /**
     * Updates name, phone, email, room and stay dates of the reservation with the given id.
     * @throws exception.ReservationException if it does not exist or the room is already booked
     */
    void update(Reservation reservation);

    /** @return true if a row was deleted */
    boolean delete(int id);

    boolean isRoomAvailable(int room, LocalDate checkIn, LocalDate checkOut);
}
