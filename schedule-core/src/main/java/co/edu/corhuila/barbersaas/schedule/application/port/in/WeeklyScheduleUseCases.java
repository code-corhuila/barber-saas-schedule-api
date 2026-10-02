package co.edu.corhuila.barbersaas.schedule.application.port.in;

import co.edu.corhuila.barbersaas.schedule.domain.model.WeeklySchedule;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/** A barber's weekly schedule (schedule-service.yaml, tag Weekly schedule). */
public interface WeeklyScheduleUseCases {

    record SlotInput(int dayOfWeek, LocalTime startTime, LocalTime endTime) { }

    /** ADMIN_BARBERSHOP any barber of their barbershop; BARBER only their own (403). */
    WeeklySchedule get(Caller caller, UUID barberId);

    /** ADMIN_BARBERSHOP; replaces every block (DEC-SCHED-01); overlapping blocks of a day answer 422. */
    WeeklySchedule set(Caller caller, UUID barberId, List<SlotInput> slots);
}
