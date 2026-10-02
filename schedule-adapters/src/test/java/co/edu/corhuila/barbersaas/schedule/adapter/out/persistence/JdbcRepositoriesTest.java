package co.edu.corhuila.barbersaas.schedule.adapter.out.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.edu.corhuila.barbersaas.schedule.application.port.in.ExceptionUseCases.Filter;
import co.edu.corhuila.barbersaas.schedule.application.port.in.Page;
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
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Runs against a real PostgreSQL migrated by barber-saas-schedule-db, connected as schedule_app
 * (never the administrator). It does not create the schema: set TEST_DATABASE_URL, TEST_DATABASE_USER
 * and TEST_DATABASE_PASSWORD to run it; without them it is skipped.
 */
@EnabledIfEnvironmentVariable(named = "TEST_DATABASE_URL", matches = ".+")
class JdbcRepositoriesTest {

    private final JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(System.getenv("TEST_DATABASE_URL"),
            System.getenv("TEST_DATABASE_USER"), System.getenv("TEST_DATABASE_PASSWORD")));
    private final TransactionTemplate tx = new TransactionTemplate(new DataSourceTransactionManager(jdbc.getDataSource()));
    private final JdbcWeeklyScheduleRepository schedules = new JdbcWeeklyScheduleRepository(jdbc, tx);
    private final JdbcScheduleExceptionRepository exceptions = new JdbcScheduleExceptionRepository(jdbc, tx);

    private final UUID shop = UUID.randomUUID();
    private final UUID barber = UUID.randomUUID();

    private static ScheduleBlock block(int day, String start, String end) {
        return new ScheduleBlock(UUID.randomUUID(), day, new TimeSlot(LocalTime.parse(start), LocalTime.parse(end)));
    }

    private static Idempotency.Key key() {
        return new Idempotency.Key("it-" + UUID.randomUUID(), "TEST", "hash");
    }

    @Test
    void a_replace_returns_only_the_new_blocks_sorted_and_scoped_by_barbershop() {
        schedules.replace(WeeklySchedule.of(shop, barber, List.of(block(1, "08:00", "12:00"))));
        schedules.replace(WeeklySchedule.of(shop, barber, List.of(block(6, "09:00", "15:00"), block(1, "14:00", "19:00"))));

        WeeklySchedule week = schedules.find(shop, barber);

        assertEquals(List.of(1, 6), week.blocks().stream().map(ScheduleBlock::dayOfWeek).toList());
        assertEquals(LocalTime.parse("14:00"), week.blocks().get(0).slot().start());
        assertTrue(schedules.find(UUID.randomUUID(), barber).blocks().isEmpty());
    }

    @Test
    void exceptions_keep_their_hours_one_per_barber_and_date() {
        LocalDate date = LocalDate.parse("2026-10-15");
        ScheduleException special = ScheduleException.create(UUID.randomUUID(), shop, barber, date, false,
                new TimeSlot(LocalTime.parse("13:00"), LocalTime.parse("18:00")), "Medical appointment");
        exceptions.saveNew(special, key());
        exceptions.saveNew(ScheduleException.create(UUID.randomUUID(), shop, barber, date.plusDays(5), true, null, null),
                key());

        assertEquals(special, exceptions.findOn(shop, barber, date).orElseThrow());
        assertThrows(AlreadyExists.class, () -> exceptions.saveNew(
                ScheduleException.create(UUID.randomUUID(), shop, barber, date, true, null, null), key()));
        Page<ScheduleException> page = exceptions.page(shop, new Filter(barber, date, null), new Page.Request(1, 10));
        assertEquals(2, page.total());
        assertEquals(date.plusDays(5), page.items().get(0).exceptionDate());
        exceptions.delete(UUID.randomUUID(), special.id());
        assertTrue(exceptions.findById(shop, special.id()).isPresent());
        exceptions.delete(shop, special.id());
        assertTrue(exceptions.findById(shop, special.id()).isEmpty());
    }
}
