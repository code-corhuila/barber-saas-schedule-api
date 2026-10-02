package co.edu.corhuila.barbersaas.schedule.application.port.out;

import co.edu.corhuila.barbersaas.schedule.domain.model.WeeklySchedule;
import java.util.UUID;

/** Persistence of schedule.barber_schedule, scoped by the barbershop. */
public interface WeeklyScheduleRepository {

    /** The active blocks; a barber without a schedule gets an empty one. */
    WeeklySchedule find(UUID barbershopId, UUID barberProfileId);

    /** Replaces every block of the barber in ONE transaction (DEC-SCHED-01). */
    void replace(WeeklySchedule schedule);
}
