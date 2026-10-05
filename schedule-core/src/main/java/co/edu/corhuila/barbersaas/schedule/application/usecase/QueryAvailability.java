package co.edu.corhuila.barbersaas.schedule.application.usecase;

import co.edu.corhuila.barbersaas.schedule.application.port.in.ApplicationException.NotFound;
import co.edu.corhuila.barbersaas.schedule.application.port.in.AvailabilityUseCases;
import co.edu.corhuila.barbersaas.schedule.application.port.in.Caller;
import co.edu.corhuila.barbersaas.schedule.application.port.in.Caller.Role;
import co.edu.corhuila.barbersaas.schedule.application.port.out.BarbershopDirectory;
import co.edu.corhuila.barbersaas.schedule.application.port.out.BarbershopDirectory.Barber;
import co.edu.corhuila.barbersaas.schedule.application.port.out.BarbershopDirectory.CatalogService;
import co.edu.corhuila.barbersaas.schedule.application.port.out.Bookings;
import co.edu.corhuila.barbersaas.schedule.application.port.out.ScheduleExceptionRepository;
import co.edu.corhuila.barbersaas.schedule.application.port.out.WeeklyScheduleRepository;
import co.edu.corhuila.barbersaas.schedule.domain.model.Availability;
import co.edu.corhuila.barbersaas.schedule.domain.model.TimeSlot;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public class QueryAvailability implements AvailabilityUseCases {

    private final WeeklyScheduleRepository schedules;
    private final ScheduleExceptionRepository exceptions;
    private final BarbershopDirectory directory;
    private final Bookings bookings;
    private final Clock clock;

    public QueryAvailability(WeeklyScheduleRepository schedules, ScheduleExceptionRepository exceptions,
                             BarbershopDirectory directory, Bookings bookings, Clock clock) {
        this.schedules = schedules;
        this.exceptions = exceptions;
        this.directory = directory;
        this.bookings = bookings;
        this.clock = clock;
    }

    @Override
    public FreeSlots free(Caller caller, UUID barberId, UUID serviceId, LocalDate date) {
        caller.require(Role.CLIENT, Role.ADMIN_BARBERSHOP, Role.BARBER);
        UUID tenant = caller.tenant();
        Barber barber = directory.barber(caller, barberId).orElseThrow(() -> new NotFound("Barber"));
        CatalogService service = directory.service(caller, serviceId).orElseThrow(() -> new NotFound("Service"));
        if (date.isBefore(LocalDate.now(clock.withZone(directory.timezone(caller))))) {
            return new FreeSlots(barberId, serviceId, date, List.of());
        }
        List<TimeSlot> windows = Availability.windows(schedules.find(tenant, barber.id()),
                exceptions.findOn(tenant, barber.id(), date), date);
        if (windows.isEmpty()) {
            return new FreeSlots(barberId, serviceId, date, List.of());
        }
        // The barber was found in the caller's barbershop above; busy slots are asked for that barbershop.
        List<TimeSlot> busy = bookings.busy(tenant, barber.id(), date);
        return new FreeSlots(barberId, serviceId, date, Availability.free(windows, service.durationMinutes(), busy));
    }
}
