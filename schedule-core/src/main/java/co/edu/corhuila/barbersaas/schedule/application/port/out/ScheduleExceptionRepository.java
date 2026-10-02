package co.edu.corhuila.barbersaas.schedule.application.port.out;

import co.edu.corhuila.barbersaas.schedule.application.port.in.ExceptionUseCases.Filter;
import co.edu.corhuila.barbersaas.schedule.application.port.in.Page;
import co.edu.corhuila.barbersaas.schedule.domain.model.ScheduleException;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/** Persistence of schedule.schedule_exception. Every read is scoped by the barbershop. */
public interface ScheduleExceptionRepository {

    /** Most recent date first. */
    Page<ScheduleException> page(UUID barbershopId, Filter filter, Page.Request page);

    Optional<ScheduleException> findById(UUID barbershopId, UUID id);

    Optional<ScheduleException> findOn(UUID barbershopId, UUID barberProfileId, LocalDate date);

    Optional<Idempotency.Stored> findKey(String key, String operation);

    /** Writes the exception and its key in ONE transaction; AlreadyExists on uq_schedule_exception_barber_date. */
    void saveNew(ScheduleException exception, Idempotency.Key key);

    void delete(UUID barbershopId, UUID id);

    class AlreadyExists extends RuntimeException {
        public AlreadyExists() {
            super("The barber already has an exception for that date");
        }
    }
}
