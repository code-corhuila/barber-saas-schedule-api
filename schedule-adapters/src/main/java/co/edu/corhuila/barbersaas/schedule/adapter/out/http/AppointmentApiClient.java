package co.edu.corhuila.barbersaas.schedule.adapter.out.http;

import co.edu.corhuila.barbersaas.schedule.application.port.in.Caller;
import co.edu.corhuila.barbersaas.schedule.application.port.out.Bookings;
import co.edu.corhuila.barbersaas.schedule.application.port.out.DependencyFailure;
import co.edu.corhuila.barbersaas.schedule.domain.model.TimeSlot;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Bookings over appointment-service.yaml (listAppointments filtered by barber and date). Only
 * PENDING, CONFIRMED and IN_PROGRESS take time (schedule-service.yaml, getAvailability).
 * Known limit: with a CLIENT token appointment-api returns only that client's appointments, so
 * the slots booked by others are not subtracted; booking itself still refuses them (INV-APPT-001).
 */
public class AppointmentApiClient implements Bookings {

    private static final Set<String> TAKING_TIME = Set.of("PENDING", "CONFIRMED", "IN_PROGRESS");
    /** A barber's day fits easily in this many pages of 100. */
    private static final int MAX_PAGES = 5;

    private final JsonApi api;

    public AppointmentApiClient(String baseUrl) {
        this.api = new JsonApi("appointment-api", baseUrl);
    }

    @Override
    public List<TimeSlot> busy(Caller caller, UUID barberId, LocalDate date) {
        List<TimeSlot> busy = new ArrayList<>();
        for (int page = 1; page <= MAX_PAGES; page++) {
            JsonNode body = api.get(caller, "/api/v1/appointments?barberId=" + barberId + "&date=" + date
                            + "&limit=100&page=" + page)
                    .orElseThrow(() -> new DependencyFailure("appointment-api", "no appointment list"));
            for (JsonNode a : body.path("data")) {
                if (TAKING_TIME.contains(a.path("status").asText())) {
                    busy.add(new TimeSlot(LocalTime.parse(a.path("startTime").asText()),
                            LocalTime.parse(a.path("endTime").asText())));
                }
            }
            if (page >= body.path("meta").path("totalPages").asInt(0)) {
                break;
            }
        }
        return busy;
    }
}
