package booking;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Storage and lookups only. No validation and no business rules: the booking invariant
 * is enforced by {@code BookingService}, not here.
 */
public class InMemoryAppointmentStore {

    /** The only place appointments are kept. */
    private final Map<String, Appointment> byId = new HashMap<>();

    /** providerId -> appointment ids, in booking order. Holds ids so a replaced record never goes stale. */
    private final Map<String, List<String>> byProvider = new HashMap<>();

    /** Puts or replaces the appointment by id. The id is indexed under its provider the first time it is saved. */
    public void save(Appointment appointment) {
        Appointment previous = byId.put(appointment.id(), appointment);
        if (previous == null) {
            byProvider.computeIfAbsent(appointment.providerId(), p -> new ArrayList<>()).add(appointment.id());
        }
    }

    public Optional<Appointment> findById(String id) {
        return Optional.ofNullable(byId.get(id));
    }

    /** All appointments for the provider, ACTIVE and CANCELLED. Empty if the provider is unknown. */
    public List<Appointment> findByProvider(String providerId) {
        return byProvider.getOrDefault(providerId, List.of()).stream()
                .map(byId::get)
                .toList();
    }
}
