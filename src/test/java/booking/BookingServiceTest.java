package booking;

import booking.BookingResult.Booked;
import booking.BookingResult.Reason;
import booking.BookingResult.Rejected;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BookingServiceTest {

    private static final String T10 = "2026-11-20T10:00:00Z";
    private static final String T1030 = "2026-11-20T10:30:00Z";
    private static final String T11 = "2026-11-20T11:00:00Z";
    private static final String T1130 = "2026-11-20T11:30:00Z";
    private static final String T12 = "2026-11-20T12:00:00Z";

    private InMemoryAppointmentStore store;
    private BookingService service;

    @BeforeEach
    void setUp() {
        store = new InMemoryAppointmentStore();
        service = new BookingService(store);
    }

    // T1 (AC1)
    @Test
    void validBookingReturnsBookedAndStoresActiveAppointment() {
        BookingResult result = service.book("dr-smith", T10, T11);

        Appointment booked = assertInstanceOf(Booked.class, result).appointment();
        assertDoesNotThrow(() -> UUID.fromString(booked.id()));
        assertEquals("dr-smith", booked.providerId());
        assertEquals(Instant.parse(T10), booked.start());
        assertEquals(Instant.parse(T11), booked.end());
        assertEquals(Status.ACTIVE, booked.status());
        assertEquals(booked, store.findById(booked.id()).orElseThrow());
    }

    // T2 (AC2)
    @Test
    void overlappingBookingForSameProviderIsRejectedAsConflict() {
        Appointment existing = bookOrFail("dr-smith", T10, T11);

        BookingResult result = service.book("dr-smith", T1030, T1130);

        Rejected rejected = assertInstanceOf(Rejected.class, result);
        assertEquals(Reason.CONFLICT, rejected.reason());
        assertEquals("overlaps appointment " + existing.id(), rejected.message());
        assertEquals(List.of(existing), store.findByProvider("dr-smith"));
    }

    // T3 (AC3)
    @Test
    void bookingThatStartsWhenAnotherEndsIsAllowed() {
        bookOrFail("dr-smith", T10, T11);

        BookingResult result = service.book("dr-smith", T11, T12);

        assertInstanceOf(Booked.class, result);
        assertEquals(2, store.findByProvider("dr-smith").size());
    }

    // T4 (AC2)
    @Test
    void sameIntervalForDifferentProvidersDoesNotConflict() {
        BookingResult first = service.book("dr-smith", T10, T11);
        BookingResult second = service.book("dr-jones", T10, T11);

        assertInstanceOf(Booked.class, first);
        assertInstanceOf(Booked.class, second);
    }

    // T5 (AC4)
    @Test
    void cancelledAppointmentFreesItsSlotForRebooking() {
        Appointment original = bookOrFail("dr-smith", T10, T11);

        assertEquals(CancelResult.CANCELLED, service.cancel(original.id()));
        BookingResult rebooked = service.book("dr-smith", T10, T11);

        Appointment replacement = assertInstanceOf(Booked.class, rebooked).appointment();
        assertEquals(Status.CANCELLED, store.findById(original.id()).orElseThrow().status());
        assertEquals(Status.ACTIVE, replacement.status());
        assertEquals(List.of(original.cancel(), replacement), service.listForProvider("dr-smith"));
    }

    // T6 (AC4)
    @Test
    void cancellingMissingIdReturnsNotFound() {
        Appointment existing = bookOrFail("dr-smith", T10, T11);

        assertEquals(CancelResult.NOT_FOUND, service.cancel(UUID.randomUUID().toString()));
        assertEquals(List.of(existing), store.findByProvider("dr-smith"));
    }

    // T6 (AC4)
    @Test
    void cancellingTwiceReturnsAlreadyCancelled() {
        Appointment existing = bookOrFail("dr-smith", T10, T11);
        service.cancel(existing.id());

        assertEquals(CancelResult.ALREADY_CANCELLED, service.cancel(existing.id()));
        assertEquals(List.of(existing.cancel()), store.findByProvider("dr-smith"));
    }

    // AC5: a missing identifier on cancel is rejected without touching the store
    @ParameterizedTest(name = "appointmentId=\"{0}\"")
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void cancellingWithBlankIdReturnsInvalidInput(String appointmentId) {
        Appointment existing = bookOrFail("dr-smith", T10, T11);

        assertEquals(CancelResult.INVALID_INPUT, service.cancel(appointmentId));
        assertEquals(List.of(existing), store.findByProvider("dr-smith"));
    }

    // T7 (AC5)
    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidInputs")
    void invalidInputIsRejectedAndStoreIsUnchanged(
            String label, String providerId, String start, String end, String expectedMessage) {
        Appointment existing = bookOrFail("dr-smith", T10, T11);

        BookingResult result = service.book(providerId, start, end);

        Rejected rejected = assertInstanceOf(Rejected.class, result);
        assertEquals(Reason.INVALID_INPUT, rejected.reason());
        assertEquals(expectedMessage, rejected.message());
        assertEquals(List.of(existing), store.findByProvider("dr-smith"));
        assertTrue(store.findByProvider("dr-jones").isEmpty());
    }

    static Stream<Arguments> invalidInputs() {
        String badFormat = "timestamps must be ISO-8601 UTC, e.g. 2026-11-20T10:00:00Z";
        return Stream.of(
                Arguments.of("null provider", null, T11, T12, "providerId is required"),
                Arguments.of("blank provider", "  ", T11, T12, "providerId is required"),
                Arguments.of("null start", "dr-jones", null, T12, "start and end are required"),
                Arguments.of("blank end", "dr-jones", T11, "", "start and end are required"),
                Arguments.of("unparseable timestamp", "dr-jones", "tomorrow", T12, badFormat),
                Arguments.of("missing time part", "dr-jones", "2026-11-20Z", T12, badFormat),
                Arguments.of("offset instead of Z", "dr-jones", "2026-11-20T16:30:00+05:30", T12, badFormat),
                Arguments.of("zero duration", "dr-jones", T11, T11, "end must be after start"),
                Arguments.of("negative duration", "dr-jones", T12, T11, "end must be after start"));
    }

    private Appointment bookOrFail(String providerId, String start, String end) {
        return assertInstanceOf(Booked.class, service.book(providerId, start, end)).appointment();
    }
}
