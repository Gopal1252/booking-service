package booking;

/** Outcome of {@code BookingService.cancel}. */
public enum CancelResult {
    CANCELLED,
    NOT_FOUND,
    ALREADY_CANCELLED,
    INVALID_INPUT
}
