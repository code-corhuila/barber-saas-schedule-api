package co.edu.corhuila.barbersaas.schedule.adapter.out.persistence;

import co.edu.corhuila.barbersaas.schedule.application.port.out.WeeklyScheduleRepository;
import co.edu.corhuila.barbersaas.schedule.domain.model.WeeklySchedule;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Used when DATABASE_URL is empty: the service starts and its HTTP contract can be tested without a database. */
public class InMemoryWeeklyScheduleRepository implements WeeklyScheduleRepository {

    private final Map<String, WeeklySchedule> rows = new ConcurrentHashMap<>();

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
