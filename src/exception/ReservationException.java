package exception;

/**
 * Thrown when a request breaks a business rule: invalid input, reservation not found,
 * room already booked, and so on. The message is safe to show directly to the user.
 */
public class ReservationException extends RuntimeException {
    private static final long serialVersionUID = 1L;


    public ReservationException(String message) {
        super(message);
    }
}
