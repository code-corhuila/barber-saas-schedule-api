package co.edu.corhuila.barbersaas.schedule.domain.model;

import static co.edu.corhuila.barbersaas.schedule.domain.model.WeeklyScheduleTest.slot;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** FR-007 and DEC-SCHED-02/03: the windows of the day minus the bookings, in slots of the service's length. */
class AvailabilityTest {

    private static final UUID SHOP = UUID.randomUUID();
    private static final UUID BARBER = UUID.randomUUID();
    private static final LocalDate MONDAY = LocalDate.parse("2026-10-05");

    private final WeeklySchedule week = WeeklySchedule.of(SHOP, BARBER, List.of(
            new ScheduleBlock(UUID.randomUUID(), 1, slot("08:00", "10:00")),
            new ScheduleBlock(UUID.randomUUID(), 1, slot("14:00", "15:00"))));

    @Test
    void every_window_of_the_day_is_cut_into_slots_of_the_service_length() {
        List<TimeSlot> windows = Availability.windows(week, Optional.empty(), MONDAY);

        assertEquals(List.of(slot("08:00", "08:45"), slot("08:45", "09:30"), slot("14:00", "14:45")),
                Availability.free(windows, 45, List.of()));
    }

    @Test
    void a_slot_that_touches_a_booking_is_free_and_one_that_overlaps_it_is_not() {
        List<TimeSlot> free = Availability.free(Availability.windows(week, Optional.empty(), MONDAY), 30,
                List.of(slot("08:30", "09:00"), slot("09:15", "09:45")));

        assertEquals(List.of(slot("08:00", "08:30"), slot("14:00", "14:30"), slot("14:30", "15:00")), free);
    }

    @Test
    void an_exception_replaces_the_day_and_is_never_merged() {
        ScheduleException special = ScheduleException.create(UUID.randomUUID(), SHOP, BARBER, MONDAY, false,
                slot("16:00", "17:00"), null);
        ScheduleException off = ScheduleException.create(UUID.randomUUID(), SHOP, BARBER, MONDAY, true, null, null);

        assertEquals(List.of(slot("16:00", "17:00")), Availability.windows(week, Optional.of(special), MONDAY));
        assertEquals(List.of(), Availability.windows(week, Optional.of(off), MONDAY));
    }

    @Test
    void a_day_without_blocks_has_no_availability() {
        assertEquals(List.of(), Availability.windows(week, Optional.empty(), MONDAY.plusDays(1)));
    }
}
