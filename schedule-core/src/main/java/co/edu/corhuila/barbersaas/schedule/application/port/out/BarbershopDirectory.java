package co.edu.corhuila.barbersaas.schedule.application.port.out;

import co.edu.corhuila.barbersaas.schedule.application.port.in.Caller;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

/**
 * What schedule needs from the barbershop domain, asked through barbershop-api and never its
 * database (golden rule 8, DEC-SCHED-03). Every call carries the caller's token, so barbershop-api
 * applies the same tenant: a barber or service of another barbershop comes back empty.
 */
public interface BarbershopDirectory {

    record Barber(UUID id, UUID userId) { }

    record CatalogService(UUID id, int durationMinutes) { }

    Optional<Barber> barber(Caller caller, UUID barberId);

    /** The barber profile of the calling BARBER, if they have one. */
    Optional<Barber> barberOfCaller(Caller caller);

    /** Only active services: an inactive one cannot be booked. */
    Optional<CatalogService> service(Caller caller, UUID serviceId);

    /** barbershop.timezone: the zone dates and times of this domain are interpreted in. */
    ZoneId timezone(Caller caller);
}
