package booking;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemoryAppointmentStoreTest {

    private static final Instant T10 = Instant.parse("2026-11-20T10:00:00Z");
    private static final Instant T11 = Instant.parse("2026-11-20T11:00:00Z");
    private static final Instant T12 = Instant.parse("2026-11-20T12:00:00Z");

    private InMemoryAppointmentStore store;

    @BeforeEach
    void setUp() {
        store = new InMemoryAppointmentStore();
    }

    @Test
    void savedAppointmentCanBeFoundById() {
        Appointment a = new Appointment("a1", "dr-smith", T10, T11, Status.ACTIVE);

        store.save(a);

        assertEquals(a, store.findById("a1").orElseThrow());
    }

    @Test
    void unknownIdAndUnknownProviderReturnEmpty() {
        assertTrue(store.findById("missing").isEmpty());
        assertTrue(store.findByProvider("nobody").isEmpty());
    }

    @Test
    void findByProviderReturnsOnlyThatProvidersAppointmentsInOrder() {
        Appointment a1 = new Appointment("a1", "dr-smith", T10, T11, Status.ACTIVE);
        Appointment a2 = new Appointment("a2", "dr-jones", T10, T11, Status.ACTIVE);
        Appointment a3 = new Appointment("a3", "dr-smith", T11, T12, Status.ACTIVE);

        store.save(a1);
        store.save(a2);
        store.save(a3);

        assertEquals(List.of(a1, a3), store.findByProvider("dr-smith"));
        assertEquals(List.of(a2), store.findByProvider("dr-jones"));
    }

    @Test
    void savingSameIdReplacesRecordWithoutDuplicatingIndex() {
        Appointment active = new Appointment("a1", "dr-smith", T10, T11, Status.ACTIVE);
        store.save(active);

        store.save(active.cancel());

        assertEquals(Status.CANCELLED, store.findById("a1").orElseThrow().status());
        List<Appointment> forProvider = store.findByProvider("dr-smith");
        assertEquals(1, forProvider.size());
        assertEquals(Status.CANCELLED, forProvider.get(0).status());
    }

    @Test
    void cancelReturnsNewRecordAndLeavesOriginalUnchanged() {
        Appointment active = new Appointment("a1", "dr-smith", T10, T11, Status.ACTIVE);

        Appointment cancelled = active.cancel();

        assertEquals(Status.ACTIVE, active.status());
        assertEquals(new Appointment("a1", "dr-smith", T10, T11, Status.CANCELLED), cancelled);
    }
}
