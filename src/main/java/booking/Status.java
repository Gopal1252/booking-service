package booking;

/** Lifecycle state of an appointment. Only ACTIVE appointments take part in the overlap check. */
public enum Status {
    ACTIVE,
    CANCELLED
}
