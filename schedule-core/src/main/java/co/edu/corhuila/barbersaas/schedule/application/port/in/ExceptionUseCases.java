package co.edu.corhuila.barbersaas.schedule.application.port.in;

import co.edu.corhuila.barbersaas.schedule.domain.model.ScheduleException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/** Days off and special hours per date (schedule-service.yaml, tag Schedule exceptions). */
public interface ExceptionUseCases {

    /** {@code startTime}/{@code endTime} only for special hours (dayOff false). */
    record NewException(UUID barberId, LocalDate exceptionDate, boolean dayOff, LocalTime startTime,
                        LocalTime endTime, String reason) { }

    /** Every filter is optional; a BARBER always gets their own, whatever barberId they send. */
    record Filter(UUID barberId, LocalDate from, LocalDate to) { }

    /** ADMIN_BARBERSHOP the whole barbershop; BARBER only their own. Most recent date first. */
    Page<ScheduleException> list(Caller caller, Filter filter, Page.Request page);

    /** ADMIN_BARBERSHOP; BARBER only their own (404 otherwise). */
    ScheduleException get(Caller caller, UUID id);

    /** ADMIN_BARBERSHOP; one exception per barber and date (422). */
    Created<ScheduleException> create(Caller caller, NewException exception, String idempotencyKey);

    /** ADMIN_BARBERSHOP; the date goes back to the weekly schedule. Booked appointments are untouched. */
    void delete(Caller caller, UUID id);
}
