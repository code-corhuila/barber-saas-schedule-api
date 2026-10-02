package co.edu.corhuila.barbersaas.schedule.adapter.out.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.edu.corhuila.barbersaas.schedule.application.port.out.Idempotency;
import co.edu.corhuila.barbersaas.schedule.application.port.out.ScheduleExceptionRepository.AlreadyExists;
import co.edu.corhuila.barbersaas.schedule.domain.model.ScheduleBlock;
import co.edu.corhuila.barbersaas.schedule.domain.model.ScheduleException;
import co.edu.corhuila.barbersaas.schedule.domain.model.TimeSlot;
import co.edu.corhuila.barbersaas.schedule.domain.model.WeeklySchedule;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InMemoryRepositoriesTest {

    private final UUID shop = UUID.randomUUID();
    private final UUID barber = UUID.randomUUID();

    private static Idempotency.Key key() {
        return new Idempotency.Key(UUID.randomUUID().toString(), "TEST", "hash");
    }

    @Test
    void a_barber_without_a_schedule_has_an_empty_week_and_a_replace_keeps_only_the_new_blocks() {
        InMemoryWeeklyScheduleRepository repository = new InMemoryWeeklyScheduleRepository();
        assertTrue(repository.find(shop, barber).blocks().isEmpty());

        repository.replace(WeeklySchedule.of(shop, barber, List.of(new ScheduleBlock(UUID.randomUUID(), 1,
                new TimeSlot(LocalTime.NOON, LocalTime.of(14, 0))))));

        assertEquals(1, repository.find(shop, barber).blocks().size());
        assertTrue(repository.find(UUID.randomUUID(), barber).blocks().isEmpty());
    }

    @Test
    void one_exception_per_barber_and_date() {
        InMemoryScheduleExceptionRepository repository = new InMemoryScheduleExceptionRepository();
        LocalDate date = LocalDate.parse("2026-10-12");
        repository.saveNew(ScheduleException.create(UUID.randomUUID(), shop, barber, date, true, null, null), key());

        assertThrows(AlreadyExists.class, () -> repository.saveNew(
                ScheduleException.create(UUID.randomUUID(), shop, barber, date, true, null, null), key()));
        assertTrue(repository.findOn(shop, barber, date).isPresent());
        assertTrue(repository.findOn(UUID.randomUUID(), barber, date).isEmpty());
    }
}
