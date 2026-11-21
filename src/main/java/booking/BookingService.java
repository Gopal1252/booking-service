package booking;

import booking.BookingResult.Booked;
import booking.BookingResult.Reason;
import booking.BookingResult.Rejected;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Books appointments and enforces the booking invariant:
 * for any provider, no two ACTIVE appointments overlap.
 * Intervals are half-open [start, end), so adjacent appointments do not overlap.
 * Thread-safe: validation runs without the lock; every store access runs under it.
 */
public class BookingService {

    private static final String TIMESTAMP_FORMAT_MESSAGE =
            "timestamps must be ISO-8601 UTC, e.g. 2026-11-20T10:00:00Z";

    private final InMemoryAppointmentStore store;

    /**
     * One lock for all store access (book, cancel, list). It makes check-then-save atomic, and it also
     * protects the store's non-thread-safe HashMap/ArrayList. It serializes every provider; see DESIGN §12.
     */
    private final ReentrantLock lock = new ReentrantLock();

    public BookingService(InMemoryAppointmentStore store) {
        this.store = store;
    }

    /**
     * Books {@code providerId} over [start, end). Timestamps are ISO-8601 UTC strings ending in 'Z'.
     * A rejected booking leaves the store unchanged.
     */
    public BookingResult book(String providerId, String start, String end) {
        // 1. Validate. No store access until the input is known to be good.
        if (isBlank(providerId)) {
            return invalid("providerId is required");
        }
        if (isBlank(start) || isBlank(end)) {
            return invalid("start and end are required");
        }
        Optional<Instant> startAt = parseUtc(start);
        Optional<Instant> endAt = parseUtc(end);
        if (startAt.isEmpty() || endAt.isEmpty()) {
            return invalid(TIMESTAMP_FORMAT_MESSAGE);
        }
        Instant from = startAt.get();
        Instant to = endAt.get();
        if (!to.isAfter(from)) {//zero or negative time period returns invalid
            return invalid("end must be after start");
        }

        // 2-4. Overlap check, create, save, under the lock so no other thread can save in between.
        lock.lock();
        try {
            //this returns a conflicted appointment
            Optional<Appointment> conflict = store.findByProvider(providerId).stream()
                    .filter(existing -> existing.status() == Status.ACTIVE)
                    .filter(existing -> overlaps(existing, from, to))
                    .findFirst();
            if (conflict.isPresent()) {
                return new Rejected(Reason.CONFLICT, "overlaps appointment " + conflict.get().id());
            }

            Appointment appointment =
                    new Appointment(UUID.randomUUID().toString(), providerId, from, to, Status.ACTIVE);
            store.save(appointment);
            return new Booked(appointment);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Cancels an appointment, which frees its time slot. The record is kept with status CANCELLED.
     * Any result other than CANCELLED leaves the store unchanged.
     */
    public CancelResult cancel(String appointmentId) {
        if (isBlank(appointmentId)) {
            return CancelResult.INVALID_INPUT;
        }
        // Look-up, status check and save under the lock, so two concurrent cancels can't both succeed.
        lock.lock();
        try {
            Optional<Appointment> found = store.findById(appointmentId);
            if (found.isEmpty()) {
                return CancelResult.NOT_FOUND;
            }
            Appointment appointment = found.get();
            if (appointment.status() == Status.CANCELLED) {
                return CancelResult.ALREADY_CANCELLED;
            }
            store.save(appointment.cancel());
            return CancelResult.CANCELLED;
        } finally {
            lock.unlock();
        }
    }

    /**
     * All appointments for the provider, ACTIVE and CANCELLED. Read-only helper for the REPL and debugging.
     * Locked so it never reads the store while a booking is writing to it; the returned list is a snapshot.
     */
    public List<Appointment> listForProvider(String providerId) {
        lock.lock();
        try {
            return store.findByProvider(providerId);
        } finally {
            lock.unlock();
        }
    }

    /** Half-open overlap: [a.start, a.end) and [start, end) overlap iff a.start < end && start < a.end. */
    private static boolean overlaps(Appointment existing, Instant start, Instant end) {
        return existing.start().isBefore(end) && start.isBefore(existing.end());
    }

    /**
     * Parses a strict UTC instant. Since JDK 12, Instant.parse also accepts offsets such as "+05:30",
     * so the 'Z' suffix is checked explicitly.
     */
    private static Optional<Instant> parseUtc(String value) {
        if (!value.endsWith("Z")) {
            return Optional.empty();
        }
        try {
            return Optional.of(Instant.parse(value));
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static Rejected invalid(String message) {
        return new Rejected(Reason.INVALID_INPUT, message);
    }
}
