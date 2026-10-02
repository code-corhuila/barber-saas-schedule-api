package co.edu.corhuila.barbersaas.schedule.domain.model;

import co.edu.corhuila.barbersaas.schedule.domain.model.DomainException.BusinessRuleViolation;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * A day off or special hours for one date (schedule.schedule_exception). It replaces that day's
 * weekly schedule (AGGR-INV-BARBER-002); a day off has no hours and special hours need both
 * times (chk_schedule_exception_hours).
 */
public record ScheduleException(UUID id, UUID barbershopId, UUID barberProfileId, LocalDate exceptionDate,
                                boolean dayOff, TimeSlot hours, String reason) {

    public ScheduleException {
        Objects.requireNonNull(id);
        Objects.requireNonNull(barbershopId);
        Objects.requireNonNull(barberProfileId);
        Objects.requireNonNull(exceptionDate);
        if (dayOff && hours != null) {
            throw new BusinessRuleViolation("A day off has no hours");
        }
        if (!dayOff && hours == null) {
            throw new BusinessRuleViolation("Special hours need a start and an end time");
        }
        reason = reason == null || reason.isBlank() ? null : reason.strip();
        if (reason != null && reason.length() > 150) {
            throw new BusinessRuleViolation("The reason has at most 150 characters");
        }
    }

    public static ScheduleException create(UUID id, UUID barbershopId, UUID barberProfileId, LocalDate date,
                                           boolean dayOff, TimeSlot hours, String reason) {
        return new ScheduleException(id, barbershopId, barberProfileId, date, dayOff, hours, reason);
    }
}
