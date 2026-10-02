package co.edu.corhuila.barbersaas.schedule.domain.model;

import co.edu.corhuila.barbersaas.schedule.domain.model.DomainException.BusinessRuleViolation;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * A barber's whole weekly schedule. It is replaced as a whole (DEC-SCHED-01), so the overlap rule
 * (AGGR-INV-BARBER-001) is checked on the final set: several blocks on one day are a split shift
 * as long as none of them overlap.
 */
public final class WeeklySchedule {

    public static final int MAX_BLOCKS = 28;

    private final UUID barbershopId;
    private final UUID barberProfileId;
    private final List<ScheduleBlock> blocks;

    private WeeklySchedule(UUID barbershopId, UUID barberProfileId, List<ScheduleBlock> blocks) {
        this.barbershopId = Objects.requireNonNull(barbershopId);
        this.barberProfileId = Objects.requireNonNull(barberProfileId);
        this.blocks = blocks;
    }

    public static WeeklySchedule of(UUID barbershopId, UUID barberProfileId, List<ScheduleBlock> blocks) {
        if (blocks.size() > MAX_BLOCKS) {
            throw new BusinessRuleViolation("A weekly schedule has at most " + MAX_BLOCKS + " blocks");
        }
        List<ScheduleBlock> sorted = blocks.stream()
                .sorted(Comparator.comparingInt(ScheduleBlock::dayOfWeek).thenComparing(ScheduleBlock::slot))
                .toList();
        for (int i = 1; i < sorted.size(); i++) {
            ScheduleBlock previous = sorted.get(i - 1);
            ScheduleBlock current = sorted.get(i);
            if (previous.dayOfWeek() == current.dayOfWeek() && previous.slot().overlaps(current.slot())) {
                throw new BusinessRuleViolation("The " + Weekday.name(current.dayOfWeek()) + " blocks "
                        + previous.slot() + " and " + current.slot() + " overlap");
            }
        }
        return new WeeklySchedule(barbershopId, barberProfileId, sorted);
    }

    /** The working windows of that date's weekday, sorted by start. */
    public List<TimeSlot> windowsOn(LocalDate date) {
        int day = Weekday.of(date);
        return blocks.stream().filter(b -> b.dayOfWeek() == day).map(ScheduleBlock::slot).toList();
    }

    public UUID barbershopId() { return barbershopId; }
    public UUID barberProfileId() { return barberProfileId; }
    /** Sorted by day of week and start time, as the contract returns them. */
    public List<ScheduleBlock> blocks() { return blocks; }
}
