package co.edu.corhuila.barbersaas.schedule.application.usecase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.edu.corhuila.barbersaas.schedule.application.port.in.ApplicationException.Forbidden;
import co.edu.corhuila.barbersaas.schedule.application.port.in.ApplicationException.NotFound;
import co.edu.corhuila.barbersaas.schedule.application.port.in.Caller;
import co.edu.corhuila.barbersaas.schedule.application.port.in.Caller.Role;
import co.edu.corhuila.barbersaas.schedule.application.port.in.WeeklyScheduleUseCases.SlotInput;
import co.edu.corhuila.barbersaas.schedule.application.port.out.BarbershopDirectory.Barber;
import co.edu.corhuila.barbersaas.schedule.domain.model.DomainException.BusinessRuleViolation;
import co.edu.corhuila.barbersaas.schedule.domain.model.WeeklySchedule;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ManageWeeklySchedulesTest {

    private final Fakes.Directory directory = new Fakes.Directory();
    private final Fakes.Schedules schedules = new Fakes.Schedules();
    private final ManageWeeklySchedules useCases = new ManageWeeklySchedules(schedules, directory, UUID::randomUUID);

    private final UUID shop = UUID.randomUUID();
    private final UUID barberUser = UUID.randomUUID();
    private final Barber barber = directory.addBarber(shop, barberUser);
    private final Caller owner = new Caller(UUID.randomUUID().toString(), Role.ADMIN_BARBERSHOP, shop, "t");

    private static SlotInput slot(int day, String start, String end) {
        return new SlotInput(day, LocalTime.parse(start), LocalTime.parse(end));
    }

    @Test
    void the_owner_replaces_the_whole_week_and_the_barber_reads_it() {
        useCases.set(owner, barber.id(), List.of(slot(1, "08:00", "12:00")));

        WeeklySchedule week = useCases.set(owner, barber.id(), List.of(slot(1, "14:00", "19:00"), slot(6, "09:00", "15:00")));

        Caller self = new Caller(barberUser.toString(), Role.BARBER, shop, "t");
        assertEquals(week.blocks(), useCases.get(self, barber.id()).blocks());
        assertEquals(2, useCases.get(owner, barber.id()).blocks().size());
    }

    @Test
    void overlapping_blocks_save_nothing() {
        useCases.set(owner, barber.id(), List.of(slot(1, "08:00", "12:00")));

        assertThrows(BusinessRuleViolation.class, () -> useCases.set(owner, barber.id(),
                List.of(slot(2, "08:00", "12:00"), slot(2, "11:00", "15:00"))));
        assertEquals(1, useCases.get(owner, barber.id()).blocks().get(0).dayOfWeek());
    }

    @Test
    void an_empty_set_leaves_the_barber_without_a_schedule() {
        useCases.set(owner, barber.id(), List.of(slot(1, "08:00", "12:00")));

        assertTrue(useCases.set(owner, barber.id(), List.of()).blocks().isEmpty());
    }

    @Test
    void a_barber_reads_only_their_own_schedule_and_never_writes_it() {
        Caller other = new Caller(UUID.randomUUID().toString(), Role.BARBER, shop, "t");
        Caller client = new Caller(UUID.randomUUID().toString(), Role.CLIENT, shop, "t");

        assertThrows(Forbidden.class, () -> useCases.get(other, barber.id()));
        assertThrows(Forbidden.class, () -> useCases.get(client, barber.id()));
        assertThrows(Forbidden.class, () -> useCases.set(new Caller(barberUser.toString(), Role.BARBER, shop, "t"),
                barber.id(), List.of()));
    }

    @Test
    void a_barber_of_another_barbershop_is_not_found() {
        Caller stranger = new Caller(UUID.randomUUID().toString(), Role.ADMIN_BARBERSHOP, UUID.randomUUID(), "t");

        assertThrows(NotFound.class, () -> useCases.get(stranger, barber.id()));
        assertThrows(NotFound.class, () -> useCases.set(stranger, barber.id(), List.of()));
        assertFalse(schedules.rows.keySet().stream().anyMatch(k -> k.startsWith(stranger.barbershopId().toString())));
    }

    @Test
    void the_token_never_appears_when_the_caller_is_printed() {
        assertFalse(new Caller("u", Role.CLIENT, null, "secret-token").toString().contains("secret-token"));
    }
}
