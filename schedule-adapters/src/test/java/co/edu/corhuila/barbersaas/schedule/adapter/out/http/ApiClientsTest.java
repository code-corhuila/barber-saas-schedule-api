package co.edu.corhuila.barbersaas.schedule.adapter.out.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.edu.corhuila.barbersaas.schedule.application.port.in.Caller;
import co.edu.corhuila.barbersaas.schedule.application.port.in.Caller.Role;
import co.edu.corhuila.barbersaas.schedule.application.port.out.DependencyFailure;
import co.edu.corhuila.barbersaas.schedule.domain.model.TimeSlot;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

/** The clients against a stub of barbershop-api and appointment-api on a local port. */
class ApiClientsTest {

    private HttpServer server;
    private String base;
    private final Map<String, String> bodies = new ConcurrentHashMap<>();
    private final Map<String, String> seenHeaders = new ConcurrentHashMap<>();
    private final UUID barberUser = UUID.randomUUID();
    private final Caller caller = new Caller(barberUser.toString(), Role.BARBER, UUID.randomUUID(), "the-token");

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String key = exchange.getRequestURI().toString();
            seenHeaders.put("Authorization", String.valueOf(exchange.getRequestHeaders().getFirst("Authorization")));
            seenHeaders.put("X-Correlation-Id", String.valueOf(exchange.getRequestHeaders().getFirst("X-Correlation-Id")));
            if (key.contains("slow")) {
                try {
                    Thread.sleep(4_000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            String body = bodies.get(key);
            int status = body == null ? 404 : body.startsWith("!") ? Integer.parseInt(body.substring(1)) : 200;
            byte[] bytes = (body == null || status != 200 ? "{}" : body).getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        base = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stop() {
        server.stop(0);
        MDC.clear();
    }

    @Test
    void the_barber_comes_from_barbershop_api_with_the_callers_token_and_correlation_id() {
        UUID id = UUID.randomUUID();
        bodies.put("/api/v1/barbers/" + id, "{\"id\":\"" + id + "\",\"userId\":\"" + barberUser + "\"}");
        MDC.put("correlationId", "corr-42");

        BarbershopApiClient client = new BarbershopApiClient(base);

        assertEquals(barberUser, client.barber(caller, id).orElseThrow().userId());
        assertEquals("Bearer the-token", seenHeaders.get("Authorization"));
        assertEquals("corr-42", seenHeaders.get("X-Correlation-Id"));
        assertTrue(client.barber(caller, UUID.randomUUID()).isEmpty());
    }

    @Test
    void an_inactive_service_is_absent_and_the_zone_comes_from_my_barbershop() {
        UUID active = UUID.randomUUID();
        UUID inactive = UUID.randomUUID();
        bodies.put("/api/v1/services/" + active, "{\"id\":\"" + active + "\",\"durationMinutes\":45,\"isActive\":true}");
        bodies.put("/api/v1/services/" + inactive, "{\"id\":\"" + inactive + "\",\"durationMinutes\":45,\"isActive\":false}");
        bodies.put("/api/v1/barbershops/me", "{\"timezone\":\"America/Bogota\"}");

        BarbershopApiClient client = new BarbershopApiClient(base);

        assertEquals(45, client.service(caller, active).orElseThrow().durationMinutes());
        assertTrue(client.service(caller, inactive).isEmpty());
        assertEquals(ZoneId.of("America/Bogota"), client.timezone(caller));
    }

    @Test
    void the_callers_own_profile_is_found_across_pages() {
        UUID mine = UUID.randomUUID();
        bodies.put("/api/v1/barbers?limit=100&page=1", "{\"data\":[{\"id\":\"" + UUID.randomUUID() + "\",\"userId\":\""
                + UUID.randomUUID() + "\"}],\"meta\":{\"totalPages\":2}}");
        bodies.put("/api/v1/barbers?limit=100&page=2", "{\"data\":[{\"id\":\"" + mine + "\",\"userId\":\"" + barberUser
                + "\"}],\"meta\":{\"totalPages\":2}}");

        assertEquals(mine, new BarbershopApiClient(base).barberOfCaller(caller).orElseThrow().id());
    }

    @Test
    void busy_slots_come_from_the_internal_route_with_this_services_own_token() {
        UUID shop = UUID.randomUUID();
        UUID barber = UUID.randomUUID();
        LocalDate date = LocalDate.parse("2026-10-05");
        bodies.put("/internal/v1/busy-slots?barbershopId=" + shop + "&barberId=" + barber + "&date=" + date,
                "{\"data\":[{\"startTime\":\"08:00\",\"endTime\":\"08:30\"},{\"startTime\":\"10:00\",\"endTime\":\"10:30\"}]}");
        MDC.put("correlationId", "corr-7");

        List<TimeSlot> busy = new AppointmentApiClient(base, "schedule-service-token").busy(shop, barber, date);

        assertEquals(List.of(new TimeSlot(LocalTime.parse("08:00"), LocalTime.parse("08:30")),
                new TimeSlot(LocalTime.parse("10:00"), LocalTime.parse("10:30"))), busy);
        assertEquals("Bearer schedule-service-token", seenHeaders.get("Authorization"));
        assertEquals("corr-7", seenHeaders.get("X-Correlation-Id"));
    }

    @Test
    void without_busy_slots_availability_fails_instead_of_guessing() {
        UUID shop = UUID.randomUUID();
        UUID barber = UUID.randomUUID();
        LocalDate date = LocalDate.parse("2026-10-05");
        bodies.put("/internal/v1/busy-slots?barbershopId=" + shop + "&barberId=" + barber + "&date=" + date, "!403");

        assertThrows(DependencyFailure.class, () -> new AppointmentApiClient(base, "t").busy(shop, barber, date));
        assertThrows(DependencyFailure.class,
                () -> new AppointmentApiClient(base, "t").busy(shop, UUID.randomUUID(), date));
        assertThrows(DependencyFailure.class, () -> new AppointmentApiClient(base, " ").busy(shop, barber, date));
        assertThrows(DependencyFailure.class,
                () -> new AppointmentApiClient("http://127.0.0.1:9", "t").busy(shop, barber, date));
    }

    @Test
    void an_error_or_a_slow_answer_fails_instead_of_guessing() {
        UUID broken = UUID.randomUUID();
        bodies.put("/api/v1/barbers/" + broken, "!500");

        BarbershopApiClient client = new BarbershopApiClient(base);

        assertThrows(DependencyFailure.class, () -> client.barber(caller, broken));
        assertThrows(DependencyFailure.class, () -> new BarbershopApiClient(base + "/slow").barber(caller, broken));
        assertThrows(DependencyFailure.class, () -> new BarbershopApiClient("http://127.0.0.1:9").barber(caller, broken));
    }
}
