package co.edu.corhuila.barbersaas.schedule.adapter.out.http;

import co.edu.corhuila.barbersaas.schedule.application.port.out.Bookings;
import co.edu.corhuila.barbersaas.schedule.application.port.out.DependencyFailure;
import co.edu.corhuila.barbersaas.schedule.domain.model.TimeSlot;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Bookings over appointment-service.yaml listBusySlots (GET /internal/v1/busy-slots, DEC-APPT-05):
 * the start and end of the barber's PENDING, CONFIRMED and IN_PROGRESS appointments, already filtered,
 * asked with this service's own SERVICE_TOKEN so a client sees what others booked too (ADR-015).
 */
public class AppointmentApiClient implements Bookings {

    private final JsonApi api;
    private final String serviceToken;

    public AppointmentApiClient(String baseUrl, String serviceToken) {
        this.api = new JsonApi("appointment-api", baseUrl);
        this.serviceToken = serviceToken == null ? "" : serviceToken.strip();
    }

    @Override
    public List<TimeSlot> busy(UUID barbershopId, UUID barberId, LocalDate date) {
        if (serviceToken.isEmpty()) {
            throw new DependencyFailure("appointment-api", "SERVICE_TOKEN is not set");
        }
        JsonNode body = api.get(serviceToken, "/internal/v1/busy-slots?barbershopId=" + barbershopId
                        + "&barberId=" + barberId + "&date=" + date)
                .orElseThrow(() -> new DependencyFailure("appointment-api", "no busy-slots operation"));
        List<TimeSlot> busy = new ArrayList<>();
        try {
            for (JsonNode slot : body.path("data")) {
                busy.add(new TimeSlot(LocalTime.parse(slot.path("startTime").asText()),
                        LocalTime.parse(slot.path("endTime").asText())));
            }
        } catch (RuntimeException e) {
            throw new DependencyFailure("appointment-api", "answered busy slots that cannot be read");
        }
        return busy;
    }
}
