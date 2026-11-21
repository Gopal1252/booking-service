package booking;

import booking.BookingResult.Booked;
import booking.BookingResult.Reason;
import booking.BookingResult.Rejected;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P5 (DESIGN §12): many threads book overlapping slots for one provider at the same moment.
 * Exactly one may succeed. Races don't show up on every run, so the scenario is repeated for many rounds.
 */
class BookingServiceConcurrencyTest {

    private static final int THREADS = 50;
    private static final int ROUNDS = 200;
    private static final Instant BASE = Instant.parse("2026-11-20T10:00:00Z");

    private ExecutorService pool;

    @BeforeEach
    void setUp() {
        pool = Executors.newFixedThreadPool(THREADS);
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        pool.shutdownNow();
        assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS), "pool did not shut down");
    }

    @Test
    void concurrentOverlappingBookingsForOneProviderLetExactlyOneSucceed() throws Exception {
        for (int round = 1; round <= ROUNDS; round++) {
            InMemoryAppointmentStore store = new InMemoryAppointmentStore();
            BookingService service = new BookingService(store);

            List<BookingResult> results = bookAllAtOnce(service);

            long booked = results.stream().filter(r -> r instanceof Booked).count();
            assertEquals(1, booked, "round " + round + ": expected exactly one booking to win");
            for (BookingResult r : results) {
                if (r instanceof Rejected rejected) {
                    assertEquals(Reason.CONFLICT, rejected.reason(), "round " + round);
                }
            }
            assertEquals(1, store.findByProvider("dr-smith").size(),
                    "round " + round + ": expected exactly one stored appointment");
        }
    }

    /**
     * Thread i books dr-smith for [10:00 + i min, 11:00 + i min). With i < 60, every pair of intervals overlaps.
     * Each thread signals "ready" and waits for "go", so all of them call book() as close together as possible.
     * Any exception thrown in a thread is rethrown by Future.get and fails the test.
     */
    private List<BookingResult> bookAllAtOnce(BookingService service) throws Exception {
        CountDownLatch ready = new CountDownLatch(THREADS);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<BookingResult>> futures = new ArrayList<>();

        for (int i = 0; i < THREADS; i++) {
            String start = BASE.plus(Duration.ofMinutes(i)).toString();
            String end = BASE.plus(Duration.ofMinutes(60 + i)).toString();
            futures.add(pool.submit(() -> {
                ready.countDown();
                go.await();
                return service.book("dr-smith", start, end);
            }));
        }

        assertTrue(ready.await(5, TimeUnit.SECONDS), "threads did not all start");
        go.countDown();

        List<BookingResult> results = new ArrayList<>();
        for (Future<BookingResult> f : futures) {
            results.add(f.get(5, TimeUnit.SECONDS));
        }
        return results;
    }

    /**
     * Books one appointment, then 50 threads try to cancel that same appointment at once.
     * Exactly one cancel succeeds (CANCELLED); the other 49 get ALREADY_CANCELLED.
     * Repeated for many rounds, since races don't show up on every run.
     */
    @Test
    void concurrentCancelsOfOneAppointmentLetExactlyOneSucceed() throws Exception {
        for (int round = 1; round <= ROUNDS; round++) {
            BookingService service = new BookingService(new InMemoryAppointmentStore());
            Appointment appointment = assertInstanceOf(Booked.class,
                    service.book("dr-smith", BASE.toString(), BASE.plus(Duration.ofHours(1)).toString()))
                    .appointment();

            CountDownLatch ready = new CountDownLatch(THREADS);
            CountDownLatch go = new CountDownLatch(1);
            List<Future<CancelResult>> futures = new ArrayList<>();
            for (int i = 0; i < THREADS; i++) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    return service.cancel(appointment.id());
                }));
            }
            assertTrue(ready.await(5, TimeUnit.SECONDS), "threads did not all start");
            go.countDown();

            int cancelled = 0;
            for (Future<CancelResult> f : futures) {
                CancelResult result = f.get(5, TimeUnit.SECONDS);
                if (result == CancelResult.CANCELLED) {
                    cancelled++;
                } else {
                    assertEquals(CancelResult.ALREADY_CANCELLED, result, "round " + round);
                }
            }
            assertEquals(1, cancelled, "round " + round + ": expected exactly one cancel to win");
        }
    }
}
