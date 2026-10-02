package co.edu.corhuila.barbersaas.schedule.application.port.out;

import co.edu.corhuila.barbersaas.schedule.application.port.in.Caller;
import co.edu.corhuila.barbersaas.schedule.domain.model.TimeSlot;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * The barber's booked time on a date, asked through appointment-api and never its database
 * (DEC-SCHED-03, golden rule 8): appointments in PENDING, CONFIRMED or IN_PROGRESS.
 * The circular dependency with appointment is OQ-09.
 */
public interface Bookings {

    List<TimeSlot> busy(Caller caller, UUID barberId, LocalDate date);
}
