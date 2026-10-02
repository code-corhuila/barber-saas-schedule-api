package co.edu.corhuila.barbersaas.schedule.application.usecase;

import co.edu.corhuila.barbersaas.schedule.application.port.in.Caller;
import co.edu.corhuila.barbersaas.schedule.application.port.in.ExceptionUseCases.Filter;
import co.edu.corhuila.barbersaas.schedule.application.port.in.Page;
import co.edu.corhuila.barbersaas.schedule.application.port.out.BarbershopDirectory;
import co.edu.corhuila.barbersaas.schedule.application.port.out.Idempotency;
import co.edu.corhuila.barbersaas.schedule.application.port.out.ScheduleExceptionRepository;
import co.edu.corhuila.barbersaas.schedule.application.port.out.WeeklyScheduleRepository;
import co.edu.corhuila.barbersaas.schedule.domain.model.ScheduleException;
import co.edu.corhuila.barbersaas.schedule.domain.model.WeeklySchedule;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Test doubles of the outbound ports: the use cases are tested without Spring, a database or HTTP. */
final class Fakes {

    private Fakes() {
    }

    /** barbershop-api as seen by each barbershop: what one tenant sees, another does not. */
    static final class Directory implements BarbershopDirectory {
        final Map<UUID, Map<UUID, Barber>> barbers = new HashMap<>();
        final Map<UUID, Map<UUID, CatalogService>> services = new HashMap<>();
        ZoneId zone = ZoneId.of("America/Bogota");

        Barber addBarber(UUID barbershopId, UUID userId) {
            Barber b = new Barber(UUID.randomUUID(), userId);
            barbers.computeIfAbsent(barbershopId, k -> new HashMap<>()).put(b.id(), b);
            return b;
        }

        CatalogService addService(UUID barbershopId, int minutes) {
            CatalogService s = new CatalogService(UUID.randomUUID(), minutes);
            services.computeIfAbsent(barbershopId, k -> new HashMap<>()).put(s.id(), s);
            return s;
        }

        @Override
        public Optional<Barber> barber(Caller caller, UUID barberId) {
            return Optional.ofNullable(barbers.getOrDefault(caller.barbershopId(), Map.of()).get(barberId));
        }

        @Override
        public Optional<Barber> barberOfCaller(Caller caller) {
            return barbers.getOrDefault(caller.barbershopId(), Map.of()).values().stream()
                    .filter(b -> b.userId().toString().equals(caller.subject())).findFirst();
        }

        @Override
        public Optional<CatalogService> service(Caller caller, UUID serviceId) {
            return Optional.ofNullable(services.getOrDefault(caller.barbershopId(), Map.of()).get(serviceId));
        }

        @Override
        public ZoneId timezone(Caller caller) {
            return zone;
        }
    }

    static final class Exceptions implements ScheduleExceptionRepository {
        final Map<UUID, ScheduleException> rows = new LinkedHashMap<>();
        final Map<String, Idempotency.Stored> keys = new HashMap<>();

        @Override
        public Page<ScheduleException> page(UUID barbershopId, Filter f, Page.Request page) {
            return Page.of(rows.values().stream()
                    .filter(e -> e.barbershopId().equals(barbershopId))
                    .filter(e -> f.barberId() == null || e.barberProfileId().equals(f.barberId()))
                    .filter(e -> f.from() == null || !e.exceptionDate().isBefore(f.from()))
                    .filter(e -> f.to() == null || !e.exceptionDate().isAfter(f.to()))
                    .sorted(Comparator.comparing(ScheduleException::exceptionDate).reversed())
                    .toList(), page);
        }

        @Override
        public Optional<ScheduleException> findById(UUID barbershopId, UUID id) {
            return Optional.ofNullable(rows.get(id)).filter(e -> e.barbershopId().equals(barbershopId));
        }

        @Override
        public Optional<ScheduleException> findOn(UUID barbershopId, UUID barberProfileId, LocalDate date) {
            return rows.values().stream().filter(e -> e.barbershopId().equals(barbershopId)
                    && e.barberProfileId().equals(barberProfileId) && e.exceptionDate().equals(date)).findFirst();
        }

        @Override
        public Optional<Idempotency.Stored> findKey(String key, String operation) {
            return Optional.ofNullable(keys.get(operation + " " + key));
        }

        @Override
        public void saveNew(ScheduleException e, Idempotency.Key key) {
            rows.put(e.id(), e);
            keys.put(key.operation() + " " + key.key(), new Idempotency.Stored(e.id(), key.requestHash()));
        }

        @Override
        public void delete(UUID barbershopId, UUID id) {
            rows.remove(id);
        }
    }

    static final class Schedules implements WeeklyScheduleRepository {
        final Map<String, WeeklySchedule> rows = new HashMap<>();

        @Override
        public WeeklySchedule find(UUID barbershopId, UUID barberProfileId) {
            return rows.getOrDefault(barbershopId + "/" + barberProfileId,
                    WeeklySchedule.of(barbershopId, barberProfileId, List.of()));
        }

        @Override
        public void replace(WeeklySchedule schedule) {
            rows.put(schedule.barbershopId() + "/" + schedule.barberProfileId(), schedule);
        }
    }
}
