package co.edu.corhuila.barbersaas.schedule.application.port.in;

import co.edu.corhuila.barbersaas.schedule.domain.model.TimeSlot;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Free slots to book (schedule-service.yaml, tag Availability). */
public interface AvailabilityUseCases {

    record FreeSlots(UUID barberId, UUID serviceId, LocalDate date, List<TimeSlot> slots) { }

    /**
     * CLIENT, ADMIN_BARBERSHOP and BARBER of the token's barbershop. The barber and the service must
     * exist there (404). A past date, in the barbershop's time zone, has no slots.
     */
    FreeSlots free(Caller caller, UUID barberId, UUID serviceId, LocalDate date);
}
