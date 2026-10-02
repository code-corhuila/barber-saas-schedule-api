package co.edu.corhuila.barbersaas.schedule.application.usecase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.edu.corhuila.barbersaas.schedule.application.port.in.ApplicationException.Forbidden;
import co.edu.corhuila.barbersaas.schedule.application.port.in.ApplicationException.NotFound;
import co.edu.corhuila.barbersaas.schedule.application.port.in.Caller;
import co.edu.corhuila.barbersaas.schedule.application.port.in.Caller.Role;
import co.edu.corhuila.barbersaas.schedule.application.port.in.ExceptionUseCases.NewException;
import co.edu.corhuila.barbersaas.schedule.application.port.in.WeeklyScheduleUseCases.SlotInput;
import co.edu.corhuila.barbersaas.schedule.application.port.out.BarbershopDirectory.Barber;
import co.edu.corhuila.barbersaas.schedule.application.port.out.BarbershopDirectory.CatalogService;
import co.edu.corhuila.barbersaas.schedule.domain.model.TimeSlot;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class QueryAvailabilityTest {

    /** Sunday 2026-10-04, 20:00 in Bogotá (01:00 UTC on Monday): "today" must be read in the barbershop's zone. */
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-05T01:00:00Z"), ZoneOffset.UTC);
    private static final LocalDate MONDAY = LocalDate.parse("2026-10-05");

    private final Fakes.Directory directory = new Fakes.Directory();
    private final Fakes.Schedules schedules = new Fakes.Schedules();
    private final Fakes.Exceptions exceptions = new Fakes.Exceptions();
    private final List<TimeSlot> booked = new ArrayList<>();
    private final QueryAvailability useCase = new QueryAvailability(schedules, exceptions, directory,
            (caller, barber, date) -> booked, CLOCK);

    private final UUID shop = UUID.randomUUID();
    private final Barber barber = directory.addBarber(shop, UUID.randomUUID());
    private final CatalogService cut = directory.addService(shop, 30);
    private final Caller owner = new Caller(UUID.randomUUID().toString(), Role.ADMIN_BARBERSHOP, shop, "t");
    private final Caller client = new Caller(UUID.randomUUID().toString(), Role.CLIENT, shop, "t");

    QueryAvailabilityTest() {
        new ManageWeeklySchedules(schedules, directory, UUID::randomUUID).set(owner, barber.id(), List.of(
                new SlotInput(1, LocalTime.parse("08:00"), LocalTime.parse("09:30")),
                new SlotInput(1, LocalTime.parse("14:00"), LocalTime.parse("15:00"))));
    }

    private static TimeSlot slot(String start, String end) {
        return new TimeSlot(LocalTime.parse(start), LocalTime.parse(end));
    }

    @Test
    void a_client_gets_the_free_slots_of_every_block_minus_the_bookings() {
        booked.add(slot("08:30", "09:00"));

        assertEquals(List.of(slot("08:00", "08:30"), slot("09:00", "09:30"), slot("14:00", "14:30"),
                slot("14:30", "15:00")), useCase.free(client, barber.id(), cut.id(), MONDAY).slots());
    }

    @Test
    void special_hours_replace_the_day_and_a_day_off_leaves_nothing() {
        ManageExceptions manage = new ManageExceptions(exceptions, directory, UUID::randomUUID);
        manage.create(owner, new NewException(barber.id(), MONDAY, false, LocalTime.parse("16:00"),
                LocalTime.parse("17:00"), null), "key-00000001");
        manage.create(owner, new NewException(barber.id(), MONDAY.plusDays(7), true, null, null, null), "key-00000002");

        assertEquals(List.of(slot("16:00", "16:30"), slot("16:30", "17:00")),
                useCase.free(client, barber.id(), cut.id(), MONDAY).slots());
        assertTrue(useCase.free(client, barber.id(), cut.id(), MONDAY.plusDays(7)).slots().isEmpty());
    }

    @Test
    void a_past_date_in_the_barbershop_zone_has_no_slots() {
        // At 01:00 UTC on Monday it is still Sunday in Bogotá: Monday is today, not past.
        assertEquals(5, useCase.free(client, barber.id(), cut.id(), MONDAY).slots().size());
        assertTrue(useCase.free(client, barber.id(), cut.id(), MONDAY.minusDays(7)).slots().isEmpty());
    }

    @Test
    void an_unknown_or_foreign_barber_or_service_is_not_found() {
        Caller stranger = new Caller(UUID.randomUUID().toString(), Role.CLIENT, UUID.randomUUID(), "t");

        assertThrows(NotFound.class, () -> useCase.free(client, UUID.randomUUID(), cut.id(), MONDAY));
        assertThrows(NotFound.class, () -> useCase.free(client, barber.id(), UUID.randomUUID(), MONDAY));
        assertThrows(NotFound.class, () -> useCase.free(stranger, barber.id(), cut.id(), MONDAY));
    }

    @Test
    void a_client_without_a_barbershop_in_the_token_is_refused_until_oq07_is_closed() {
        assertThrows(Forbidden.class, () -> useCase.free(
                new Caller(UUID.randomUUID().toString(), Role.CLIENT, null, "t"), barber.id(), cut.id(), MONDAY));
        assertThrows(Forbidden.class, () -> useCase.free(
                new Caller(UUID.randomUUID().toString(), Role.SUPER_ADMIN, null, "t"), barber.id(), cut.id(), MONDAY));
    }
}
