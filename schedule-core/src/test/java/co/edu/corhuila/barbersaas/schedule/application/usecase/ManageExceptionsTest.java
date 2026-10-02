package co.edu.corhuila.barbersaas.schedule.application.usecase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import co.edu.corhuila.barbersaas.schedule.application.port.in.ApplicationException.Forbidden;
import co.edu.corhuila.barbersaas.schedule.application.port.in.ApplicationException.IdempotencyKeyReused;
import co.edu.corhuila.barbersaas.schedule.application.port.in.ApplicationException.NotFound;
import co.edu.corhuila.barbersaas.schedule.application.port.in.Caller;
import co.edu.corhuila.barbersaas.schedule.application.port.in.Caller.Role;
import co.edu.corhuila.barbersaas.schedule.application.port.in.Created;
import co.edu.corhuila.barbersaas.schedule.application.port.in.ExceptionUseCases.Filter;
import co.edu.corhuila.barbersaas.schedule.application.port.in.ExceptionUseCases.NewException;
import co.edu.corhuila.barbersaas.schedule.application.port.in.Page;
import co.edu.corhuila.barbersaas.schedule.application.port.out.BarbershopDirectory.Barber;
import co.edu.corhuila.barbersaas.schedule.domain.model.DomainException.BusinessRuleViolation;
import co.edu.corhuila.barbersaas.schedule.domain.model.ScheduleException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ManageExceptionsTest {

    private static final Page.Request FIRST = new Page.Request(1, 20);
    private static final LocalDate HOLIDAY = LocalDate.parse("2026-10-12");
    private static final Filter ALL = new Filter(null, null, null);

    private final Fakes.Directory directory = new Fakes.Directory();
    private final Fakes.Exceptions exceptions = new Fakes.Exceptions();
    private final ManageExceptions useCases = new ManageExceptions(exceptions, directory, UUID::randomUUID);

    private final UUID shop = UUID.randomUUID();
    private final UUID barberUser = UUID.randomUUID();
    private final Barber barber = directory.addBarber(shop, barberUser);
    private final Barber colleague = directory.addBarber(shop, UUID.randomUUID());
    private final Caller owner = new Caller(UUID.randomUUID().toString(), Role.ADMIN_BARBERSHOP, shop, "t");
    private final Caller self = new Caller(barberUser.toString(), Role.BARBER, shop, "t");

    private ScheduleException dayOff(Barber b, LocalDate date, String key) {
        return useCases.create(owner, new NewException(b.id(), date, true, null, null, "Holiday"), key).value();
    }

    @Test
    void the_owner_registers_a_day_off_or_special_hours_and_a_retry_returns_it() {
        ScheduleException first = dayOff(barber, HOLIDAY, "key-00000001");
        Created<ScheduleException> retry = useCases.create(owner,
                new NewException(barber.id(), HOLIDAY, true, null, null, "Holiday"), "key-00000001");
        ScheduleException special = useCases.create(owner, new NewException(barber.id(), HOLIDAY.plusDays(3), false,
                LocalTime.parse("13:00"), LocalTime.parse("18:00"), null), "key-00000002").value();

        assertFalse(retry.created());
        assertEquals(first.id(), retry.value().id());
        assertEquals(LocalTime.parse("13:00"), special.hours().start());
        assertThrows(IdempotencyKeyReused.class, () -> useCases.create(owner,
                new NewException(barber.id(), HOLIDAY.plusDays(9), true, null, null, null), "key-00000001"));
    }

    @Test
    void a_second_exception_for_the_same_barber_and_date_is_refused() {
        dayOff(barber, HOLIDAY, "key-00000001");

        BusinessRuleViolation e = assertThrows(BusinessRuleViolation.class, () -> dayOff(barber, HOLIDAY, "key-00000002"));
        assertEquals("The barber already has an exception for 2026-10-12", e.getMessage());
        dayOff(colleague, HOLIDAY, "key-00000003");
    }

    @Test
    void a_barber_sees_only_their_own_exceptions_and_cannot_create_or_delete() {
        ScheduleException mine = dayOff(barber, HOLIDAY, "key-00000001");
        ScheduleException theirs = dayOff(colleague, HOLIDAY, "key-00000002");

        Page<ScheduleException> page = useCases.list(self, new Filter(colleague.id(), null, null), FIRST);
        assertEquals(1, page.total());
        assertEquals(mine.id(), page.items().get(0).id());
        assertEquals(2, useCases.list(owner, ALL, FIRST).total());
        assertThrows(NotFound.class, () -> useCases.get(self, theirs.id()));
        assertThrows(Forbidden.class, () -> useCases.delete(self, mine.id()));
        assertThrows(Forbidden.class, () -> useCases.create(self,
                new NewException(barber.id(), HOLIDAY.plusDays(1), true, null, null, null), "key-00000003"));
    }

    @Test
    void the_list_filters_by_date_range_most_recent_first() {
        dayOff(barber, HOLIDAY, "key-00000001");
        dayOff(barber, HOLIDAY.plusDays(10), "key-00000002");
        dayOff(barber, HOLIDAY.plusDays(20), "key-00000003");

        Page<ScheduleException> page = useCases.list(owner, new Filter(null, HOLIDAY, HOLIDAY.plusDays(10)), FIRST);

        assertEquals(2, page.total());
        assertEquals(HOLIDAY.plusDays(10), page.items().get(0).exceptionDate());
    }

    @Test
    void deleting_returns_the_date_to_the_weekly_schedule_and_another_barbershop_sees_nothing() {
        ScheduleException e = dayOff(barber, HOLIDAY, "key-00000001");
        Caller stranger = new Caller(UUID.randomUUID().toString(), Role.ADMIN_BARBERSHOP, UUID.randomUUID(), "t");

        assertThrows(NotFound.class, () -> useCases.get(stranger, e.id()));
        assertThrows(NotFound.class, () -> useCases.delete(stranger, e.id()));
        assertThrows(NotFound.class, () -> useCases.create(stranger,
                new NewException(barber.id(), HOLIDAY, true, null, null, null), "key-00000009"));
        useCases.delete(owner, e.id());
        assertEquals(0, useCases.list(owner, ALL, FIRST).total());
    }
}
