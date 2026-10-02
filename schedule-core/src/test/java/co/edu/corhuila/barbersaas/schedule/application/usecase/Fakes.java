package co.edu.corhuila.barbersaas.schedule.application.usecase;

import co.edu.corhuila.barbersaas.schedule.application.port.in.Caller;
import co.edu.corhuila.barbersaas.schedule.application.port.out.BarbershopDirectory;
import co.edu.corhuila.barbersaas.schedule.application.port.out.WeeklyScheduleRepository;
import co.edu.corhuila.barbersaas.schedule.domain.model.WeeklySchedule;
import java.time.ZoneId;
import java.util.HashMap;
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
