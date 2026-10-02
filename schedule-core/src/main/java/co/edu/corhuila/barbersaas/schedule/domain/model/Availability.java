package co.edu.corhuila.barbersaas.schedule.domain.model;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Free slots of a day (FR-007, DEC-SCHED-02, DEC-SCHED-03). Derived, never stored. It is
 * informative: the no-double-booking guarantee belongs to appointment (INV-APPT-001).
 * Unlike the prototype, which used only the first block of a day, every block of a split shift counts.
 */
public final class Availability {

    private Availability() {
    }

    /** An exception replaces the day and is never merged: a day off gives nothing, special hours only themselves. */
    public static List<TimeSlot> windows(WeeklySchedule week, Optional<ScheduleException> exception, LocalDate date) {
        if (exception.isPresent()) {
            return exception.get().dayOff() ? List.of() : List.of(exception.get().hours());
        }
        return week.windowsOn(date);
    }

    /**
     * Cuts every window into consecutive slots of {@code durationMinutes} that fit entirely, and drops
     * the ones that overlap a booking. A slot that only touches a booking is free.
     */
    public static List<TimeSlot> free(List<TimeSlot> windows, int durationMinutes, List<TimeSlot> busy) {
        List<TimeSlot> slots = new ArrayList<>();
        for (TimeSlot window : windows.stream().sorted().toList()) {
            LocalTime start = window.start();
            while (fits(start, durationMinutes, window.end())) {
                TimeSlot candidate = new TimeSlot(start, start.plusMinutes(durationMinutes));
                if (busy.stream().noneMatch(candidate::overlaps)) {
                    slots.add(candidate);
                }
                start = candidate.end();
            }
        }
        return slots;
    }

    /** Also stops at midnight: a LocalTime past 23:59 wraps around to the early morning. */
    private static boolean fits(LocalTime start, int minutes, LocalTime end) {
        LocalTime finish = start.plusMinutes(minutes);
        return finish.isAfter(start) && !finish.isAfter(end);
    }
}
