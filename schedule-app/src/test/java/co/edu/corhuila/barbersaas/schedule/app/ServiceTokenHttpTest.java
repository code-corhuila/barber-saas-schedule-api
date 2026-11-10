package co.edu.corhuila.barbersaas.schedule.app;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * barber-saas-docs#164: service tokens (role SERVICE) are only for /internal/v1/**. On the routes of the
 * app they answer 401, even for the services that do call schedule-api, so a copied service token
 * cannot act on an owner's agenda.
 */
class ServiceTokenHttpTest extends HttpTest {

    private final UUID shop = UUID.randomUUID();
    private final BarbershopApiStub.Barber barber = BARBERSHOP_API.barber(shop, UUID.randomUUID());
    private final BarbershopApiStub.Service cut = BARBERSHOP_API.service(shop, 30);

    @Test
    void a_service_token_answers_401_on_every_route_of_the_app() throws Exception {
        String id = barber.id().toString();
        List<Supplier<MockHttpServletRequestBuilder>> routes = List.of(
                () -> get("/api/v1/barber-schedules/" + id),
                () -> put("/api/v1/barber-schedules/" + id)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"slots\":[]}"),
                () -> get("/api/v1/availability?barberId=" + id + "&serviceId=" + cut.id() + "&date=2099-10-05"),
                () -> get("/api/v1/schedule-exceptions?barberId=" + id),
                () -> post("/api/v1/schedule-exceptions").header("Idempotency-Key", "key-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"barberId\":\"" + id + "\",\"date\":\"2099-10-05\",\"isAvailable\":false}"),
                () -> get("/api/v1/schedule-exceptions/" + UUID.randomUUID()),
                () -> delete("/api/v1/schedule-exceptions/" + UUID.randomUUID()));

        for (String service : List.of("barber-saas-appointment-api", "barber-saas-workflow", "barber-saas-worker")) {
            for (Supplier<MockHttpServletRequestBuilder> route : routes) {
                http.perform(route.get().header("Authorization", serviceBearer(service)))
                        .andExpect(status().isUnauthorized())
                        .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
            }
        }
    }
}
