package co.edu.corhuila.barbersaas.schedule.application.usecase;

import co.edu.corhuila.barbersaas.schedule.application.port.in.ApplicationException.IdempotencyKeyReused;
import co.edu.corhuila.barbersaas.schedule.application.port.in.ApplicationException.NotFound;
import co.edu.corhuila.barbersaas.schedule.application.port.in.Caller;
import co.edu.corhuila.barbersaas.schedule.application.port.in.Caller.Role;
import co.edu.corhuila.barbersaas.schedule.application.port.in.Created;
import co.edu.corhuila.barbersaas.schedule.application.port.in.ExceptionUseCases;
import co.edu.corhuila.barbersaas.schedule.application.port.in.Page;
import co.edu.corhuila.barbersaas.schedule.application.port.out.BarbershopDirectory;
import co.edu.corhuila.barbersaas.schedule.application.port.out.BarbershopDirectory.Barber;
import co.edu.corhuila.barbersaas.schedule.application.port.out.IdGenerator;
import co.edu.corhuila.barbersaas.schedule.application.port.out.Idempotency;
import co.edu.corhuila.barbersaas.schedule.application.port.out.ScheduleExceptionRepository;
import co.edu.corhuila.barbersaas.schedule.domain.model.DomainException.BusinessRuleViolation;
import co.edu.corhuila.barbersaas.schedule.domain.model.ScheduleException;
import co.edu.corhuila.barbersaas.schedule.domain.model.TimeSlot;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class ManageExceptions implements ExceptionUseCases {

    static final String CREATE_OPERATION = "POST /api/v1/schedule-exceptions";

    private final ScheduleExceptionRepository exceptions;
    private final BarbershopDirectory directory;
    private final IdGenerator ids;

    public ManageExceptions(ScheduleExceptionRepository exceptions, BarbershopDirectory directory, IdGenerator ids) {
        this.exceptions = exceptions;
        this.directory = directory;
        this.ids = ids;
    }

    @Override
    public Page<ScheduleException> list(Caller caller, Filter filter, Page.Request page) {
        caller.require(Role.ADMIN_BARBERSHOP, Role.BARBER);
        UUID tenant = caller.tenant();
        if (caller.is(Role.BARBER)) {
            // The server forces the barber's own profile; a barber without one has no exceptions.
            Optional<Barber> self = directory.barberOfCaller(caller);
            if (self.isEmpty()) {
                return new Page<>(List.of(), page.page(), page.limit(), 0);
            }
            filter = new Filter(self.get().id(), filter.from(), filter.to());
        }
        return exceptions.page(tenant, filter, page);
    }

    @Override
    public ScheduleException get(Caller caller, UUID id) {
        caller.require(Role.ADMIN_BARBERSHOP, Role.BARBER);
        ScheduleException exception = exceptions.findById(caller.tenant(), id)
                .orElseThrow(() -> new NotFound("Schedule exception"));
        if (caller.is(Role.BARBER) && directory.barberOfCaller(caller)
                .filter(b -> b.id().equals(exception.barberProfileId())).isEmpty()) {
            throw new NotFound("Schedule exception");
        }
        return exception;
    }

    @Override
    public Created<ScheduleException> create(Caller caller, NewException e, String idempotencyKey) {
        caller.require(Role.ADMIN_BARBERSHOP);
        UUID tenant = caller.tenant();
        String hash = RequestHash.of(tenant, e.barberId(), e.exceptionDate(), e.dayOff(), e.startTime(), e.endTime(),
                e.reason());
        Optional<Idempotency.Stored> stored = exceptions.findKey(idempotencyKey, CREATE_OPERATION);
        if (stored.isPresent()) {
            if (!stored.get().requestHash().equals(hash)) {
                throw new IdempotencyKeyReused();
            }
            return new Created<>(exceptions.findById(tenant, stored.get().resourceId())
                    .orElseThrow(IdempotencyKeyReused::new), false);
        }
        Barber barber = directory.barber(caller, e.barberId()).orElseThrow(() -> new NotFound("Barber"));
        String taken = "The barber already has an exception for " + e.exceptionDate();
        if (exceptions.findOn(tenant, barber.id(), e.exceptionDate()).isPresent()) {
            throw new BusinessRuleViolation(taken);
        }
        TimeSlot hours = e.dayOff() ? null : new TimeSlot(e.startTime(), e.endTime());
        ScheduleException exception = ScheduleException.create(ids.next(), tenant, barber.id(), e.exceptionDate(),
                e.dayOff(), hours, e.reason());
        try {
            exceptions.saveNew(exception, new Idempotency.Key(idempotencyKey, CREATE_OPERATION, hash));
        } catch (ScheduleExceptionRepository.AlreadyExists race) {
            throw new BusinessRuleViolation(taken);
        }
        return new Created<>(exception, true);
    }

    @Override
    public void delete(Caller caller, UUID id) {
        caller.require(Role.ADMIN_BARBERSHOP);
        ScheduleException exception = exceptions.findById(caller.tenant(), id)
                .orElseThrow(() -> new NotFound("Schedule exception"));
        exceptions.delete(caller.tenant(), exception.id());
    }
}
