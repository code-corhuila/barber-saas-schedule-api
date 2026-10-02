package co.edu.corhuila.barbersaas.schedule.application.usecase;

import co.edu.corhuila.barbersaas.schedule.application.port.in.ApplicationException.Forbidden;
import co.edu.corhuila.barbersaas.schedule.application.port.in.ApplicationException.NotFound;
import co.edu.corhuila.barbersaas.schedule.application.port.in.Caller;
import co.edu.corhuila.barbersaas.schedule.application.port.in.Caller.Role;
import co.edu.corhuila.barbersaas.schedule.application.port.in.WeeklyScheduleUseCases;
import co.edu.corhuila.barbersaas.schedule.application.port.out.BarbershopDirectory;
import co.edu.corhuila.barbersaas.schedule.application.port.out.BarbershopDirectory.Barber;
import co.edu.corhuila.barbersaas.schedule.application.port.out.IdGenerator;
import co.edu.corhuila.barbersaas.schedule.application.port.out.WeeklyScheduleRepository;
import co.edu.corhuila.barbersaas.schedule.domain.model.ScheduleBlock;
import co.edu.corhuila.barbersaas.schedule.domain.model.TimeSlot;
import co.edu.corhuila.barbersaas.schedule.domain.model.WeeklySchedule;
import java.util.List;
import java.util.UUID;

public class ManageWeeklySchedules implements WeeklyScheduleUseCases {

    private final WeeklyScheduleRepository schedules;
    private final BarbershopDirectory directory;
    private final IdGenerator ids;

    public ManageWeeklySchedules(WeeklyScheduleRepository schedules, BarbershopDirectory directory, IdGenerator ids) {
        this.schedules = schedules;
        this.directory = directory;
        this.ids = ids;
    }

    @Override
    public WeeklySchedule get(Caller caller, UUID barberId) {
        caller.require(Role.ADMIN_BARBERSHOP, Role.BARBER);
        Barber barber = barberOfTenant(caller, barberId);
        if (caller.is(Role.BARBER) && !barber.userId().equals(caller.userId())) {
            throw new Forbidden("A barber can only read their own schedule");
        }
        return schedules.find(caller.tenant(), barber.id());
    }

    @Override
    public WeeklySchedule set(Caller caller, UUID barberId, List<SlotInput> slots) {
        caller.require(Role.ADMIN_BARBERSHOP);
        Barber barber = barberOfTenant(caller, barberId);
        WeeklySchedule schedule = WeeklySchedule.of(caller.tenant(), barber.id(), slots.stream()
                .map(s -> new ScheduleBlock(ids.next(), s.dayOfWeek(), new TimeSlot(s.startTime(), s.endTime())))
                .toList());
        schedules.replace(schedule);
        return schedule;
    }

    /** The barber must exist in the token's barbershop; another barbershop's answers 404, like a missing one. */
    private Barber barberOfTenant(Caller caller, UUID barberId) {
        caller.tenant();
        return directory.barber(caller, barberId).orElseThrow(() -> new NotFound("Barber"));
    }
}
