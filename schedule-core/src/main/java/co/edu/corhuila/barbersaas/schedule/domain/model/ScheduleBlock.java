package co.edu.corhuila.barbersaas.schedule.domain.model;

import java.util.Objects;
import java.util.UUID;

/** One block of the weekly schedule (a row of schedule.barber_schedule). */
public record ScheduleBlock(UUID id, int dayOfWeek, TimeSlot slot) {

    public ScheduleBlock {
        Objects.requireNonNull(id);
        Weekday.require(dayOfWeek);
        Objects.requireNonNull(slot);
    }
}
