package co.edu.corhuila.barbersaas.schedule.adapter.out.http;

import co.edu.corhuila.barbersaas.schedule.application.port.out.Bookings;
import co.edu.corhuila.barbersaas.schedule.domain.model.TimeSlot;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Used ONLY when APPOINTMENT_API_URL is empty, while barber-saas-appointment-api is not deployed:
 * availability is then the working hours alone. It says so in the log at startup, so nobody takes
 * it for the real thing; appointment still refuses double bookings (INV-APPT-001).
 */
public class NoBookingsYet implements Bookings {

    private static final Logger log = LoggerFactory.getLogger(NoBookingsYet.class);

    public NoBookingsYet() {
        log.warn("APPOINTMENT_API_URL is empty: availability does not subtract booked appointments");
    }

    @Override
    public List<TimeSlot> busy(UUID barbershopId, UUID barberId, LocalDate date) {
        return List.of();
    }
}
