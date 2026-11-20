package booking;

import java.time.Instant;

/**
 * A booked time slot for a provider over the half-open interval [start, end), in UTC.
 * Immutable: cancelling returns a new record rather than changing this one.
 */
public record Appointment(String id, String providerId, Instant start, Instant end, Status status) {

    /** Returns a copy of this appointment with status CANCELLED. */
    public Appointment cancel() {
        return new Appointment(id, providerId, start, end, Status.CANCELLED);
    }
}
