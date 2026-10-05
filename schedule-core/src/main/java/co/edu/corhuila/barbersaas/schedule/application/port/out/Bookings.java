package co.edu.corhuila.barbersaas.schedule.application.port.out;

import co.edu.corhuila.barbersaas.schedule.domain.model.TimeSlot;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * The barber's booked time on a date, asked through appointment-api and never its database
 * (DEC-SCHED-03, golden rule 8). It is asked with this service's own token and the barbershop of
 * the caller's token, never with the caller's token, so every role gets the same answer (ADR-015).
 */
public interface Bookings {

    List<TimeSlot> busy(UUID barbershopId, UUID barberId, LocalDate date);
}
