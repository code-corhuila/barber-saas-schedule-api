package co.edu.corhuila.barbersaas.schedule.domain.model;

import co.edu.corhuila.barbersaas.schedule.domain.model.DomainException.BusinessRuleViolation;
import java.time.LocalTime;
import java.util.Objects;

/**
 * The TimeSlot value object (02-domain): a half-open interval [start, end) of barbershop-local
 * time. It always ends after it starts (chk_barber_schedule_time); two slots that only touch do
 * not overlap.
 */
public record TimeSlot(LocalTime start, LocalTime end) implements Comparable<TimeSlot> {

    public TimeSlot {
        Objects.requireNonNull(start);
        Objects.requireNonNull(end);
        if (!end.isAfter(start)) {
            throw new BusinessRuleViolation("The end time must be after the start time");
        }
    }

    public boolean overlaps(TimeSlot other) {
        return start.isBefore(other.end) && other.start.isBefore(end);
    }

    @Override
    public int compareTo(TimeSlot other) {
        int byStart = start.compareTo(other.start);
        return byStart != 0 ? byStart : end.compareTo(other.end);
    }

    /** "08:00–12:00", as the contract's messages show it. */
    @Override
    public String toString() {
        return start + "–" + end;
    }
}
