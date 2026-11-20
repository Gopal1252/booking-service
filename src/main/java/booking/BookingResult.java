package booking;

/** Outcome of {@code BookingService.book}: either {@link Booked} or {@link Rejected}. */
public sealed interface BookingResult permits BookingResult.Booked, BookingResult.Rejected {

    /** The booking succeeded; wraps the stored appointment. */
    record Booked(Appointment appointment) implements BookingResult {
    }

    /** The booking was refused; the store was not changed. */
    record Rejected(Reason reason, String message) implements BookingResult {
    }

    /** Why a booking was rejected. */
    enum Reason {
        INVALID_INPUT,
        CONFLICT
    }
}
