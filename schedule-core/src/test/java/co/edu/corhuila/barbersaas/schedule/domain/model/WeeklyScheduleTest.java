package co.edu.corhuila.barbersaas.schedule.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.edu.corhuila.barbersaas.schedule.domain.model.DomainException.BusinessRuleViolation;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WeeklyScheduleTest {

    private static final UUID SHOP = UUID.randomUUID();
    private static final UUID BARBER = UUID.randomUUID();

    static TimeSlot slot(String start, String end) {
        return new TimeSlot(LocalTime.parse(start), LocalTime.parse(end));
    }

    private static ScheduleBlock block(int day, String start, String end) {
        return new ScheduleBlock(UUID.randomUUID(), day, slot(start, end));
    }

    @Test
    void a_slot_ends_after_it_starts_and_overlap_is_half_open() {
        assertThrows(BusinessRuleViolation.class, () -> slot("12:00", "12:00"));
        assertThrows(BusinessRuleViolation.class, () -> slot("12:00", "08:00"));
        assertTrue(slot("08:00", "12:00").overlaps(slot("11:00", "15:00")));
        assertFalse(slot("08:00", "12:00").overlaps(slot("12:00", "15:00")));
    }

    @Test
    void the_day_of_week_goes_from_sunday_zero_to_saturday_six() {
        assertEquals(0, Weekday.of(LocalDate.parse("2026-10-04")));
        assertEquals(1, Weekday.of(LocalDate.parse("2026-10-05")));
        assertEquals(6, Weekday.of(LocalDate.parse("2026-10-10")));
        assertThrows(BusinessRuleViolation.class, () -> block(7, "08:00", "12:00"));
    }

    @Test
    void a_split_shift_is_allowed_but_overlapping_blocks_of_one_day_are_not() {
        WeeklySchedule split = WeeklySchedule.of(SHOP, BARBER, List.of(
                block(1, "14:00", "19:00"), block(1, "08:00", "12:00"), block(2, "08:00", "19:00")));

        assertEquals(List.of(slot("08:00", "12:00"), slot("14:00", "19:00")),
                split.windowsOn(LocalDate.parse("2026-10-05")));
        BusinessRuleViolation e = assertThrows(BusinessRuleViolation.class, () -> WeeklySchedule.of(SHOP, BARBER,
                List.of(block(1, "08:00", "12:00"), block(1, "11:00", "15:00"))));
        assertEquals("The Monday blocks 08:00–12:00 and 11:00–15:00 overlap", e.getMessage());
    }

    @Test
    void a_week_has_at_most_twenty_eight_blocks_and_may_be_empty() {
        List<ScheduleBlock> many = new ArrayList<>();
        for (int i = 0; i < 29; i++) {
            many.add(block(i % 7, String.format("%02d:00", i / 7 * 2), String.format("%02d:30", i / 7 * 2)));
        }

        assertThrows(BusinessRuleViolation.class, () -> WeeklySchedule.of(SHOP, BARBER, many));
        assertTrue(WeeklySchedule.of(SHOP, BARBER, List.of()).windowsOn(LocalDate.parse("2026-10-05")).isEmpty());
    }

    @Test
    void an_exception_is_a_day_off_or_special_hours_with_both_times() {
        LocalDate date = LocalDate.parse("2026-10-12");

        ScheduleException off = ScheduleException.create(UUID.randomUUID(), SHOP, BARBER, date, true, null, "Holiday");
        ScheduleException special = ScheduleException.create(UUID.randomUUID(), SHOP, BARBER, date, false,
                slot("13:00", "18:00"), null);

        assertTrue(off.dayOff());
        assertEquals(slot("13:00", "18:00"), special.hours());
        assertThrows(BusinessRuleViolation.class,
                () -> ScheduleException.create(UUID.randomUUID(), SHOP, BARBER, date, false, null, null));
        assertThrows(BusinessRuleViolation.class,
                () -> ScheduleException.create(UUID.randomUUID(), SHOP, BARBER, date, true, slot("13:00", "18:00"), null));
        assertThrows(BusinessRuleViolation.class,
                () -> ScheduleException.create(UUID.randomUUID(), SHOP, BARBER, date, true, null, "r".repeat(151)));
    }
}
