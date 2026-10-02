package co.edu.corhuila.barbersaas.schedule.adapter.out.persistence;

import co.edu.corhuila.barbersaas.schedule.application.port.in.ExceptionUseCases.Filter;
import co.edu.corhuila.barbersaas.schedule.application.port.in.Page;
import co.edu.corhuila.barbersaas.schedule.application.port.out.Idempotency;
import co.edu.corhuila.barbersaas.schedule.application.port.out.ScheduleExceptionRepository;
import co.edu.corhuila.barbersaas.schedule.domain.model.ScheduleException;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Used when DATABASE_URL is empty: the service starts and its HTTP contract can be tested without a database. */
public class InMemoryScheduleExceptionRepository implements ScheduleExceptionRepository {

    private final Map<UUID, ScheduleException> rows = new ConcurrentHashMap<>();
    private final Map<String, Idempotency.Stored> keys = new ConcurrentHashMap<>();

    @Override
    public Page<ScheduleException> page(UUID barbershopId, Filter f, Page.Request page) {
        return Page.of(rows.values().stream()
                .filter(e -> e.barbershopId().equals(barbershopId))
                .filter(e -> f.barberId() == null || e.barberProfileId().equals(f.barberId()))
                .filter(e -> f.from() == null || !e.exceptionDate().isBefore(f.from()))
                .filter(e -> f.to() == null || !e.exceptionDate().isAfter(f.to()))
                .sorted(Comparator.comparing(ScheduleException::exceptionDate).reversed()
                        .thenComparing(ScheduleException::id))
                .toList(), page);
    }

    @Override
    public Optional<ScheduleException> findById(UUID barbershopId, UUID id) {
        return Optional.ofNullable(rows.get(id)).filter(e -> e.barbershopId().equals(barbershopId));
    }

    @Override
    public Optional<ScheduleException> findOn(UUID barbershopId, UUID barberProfileId, LocalDate date) {
        return rows.values().stream()
                .filter(e -> e.barbershopId().equals(barbershopId) && e.barberProfileId().equals(barberProfileId)
                        && e.exceptionDate().equals(date))
                .findFirst();
    }

    @Override
    public Optional<Idempotency.Stored> findKey(String key, String operation) {
        return Optional.ofNullable(keys.get(operation + " " + key));
    }

    /** Same rule as uq_schedule_exception_barber_date: one per barber and date. */
    @Override
    public synchronized void saveNew(ScheduleException e, Idempotency.Key key) {
        if (rows.values().stream().anyMatch(o -> o.barberProfileId().equals(e.barberProfileId())
                && o.exceptionDate().equals(e.exceptionDate()))) {
            throw new AlreadyExists();
        }
        rows.put(e.id(), e);
        keys.put(key.operation() + " " + key.key(), new Idempotency.Stored(e.id(), key.requestHash()));
    }

    @Override
    public void delete(UUID barbershopId, UUID id) {
        findById(barbershopId, id).ifPresent(e -> rows.remove(e.id()));
    }
}
