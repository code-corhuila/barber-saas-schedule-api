package co.edu.corhuila.barbersaas.schedule.app;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/** schedule-service.yaml over HTTP: in-memory repositories and a stand-in barbershop-api. */
class ScheduleHttpTest extends HttpTest {

    private static final String WEEK = "{\"slots\":[{\"dayOfWeek\":1,\"startTime\":\"08:00\",\"endTime\":\"09:00\"},"
            + "{\"dayOfWeek\":1,\"startTime\":\"14:00\",\"endTime\":\"15:00\"}]}";

    private final UUID shop = UUID.randomUUID();
    private final UUID barberUser = UUID.randomUUID();
    private final BarbershopApiStub.Barber barber = BARBERSHOP_API.barber(shop, barberUser);
    private final BarbershopApiStub.Service cut = BARBERSHOP_API.service(shop, 30);
    private final String owner = bearer("ADMIN_BARBERSHOP", shop);
    private final String self = bearer(barberUser, "BARBER", shop);
    /** A Monday far enough ahead never to be "past". */
    private final LocalDate monday = LocalDate.parse("2099-10-05");

    private void setWeek() throws Exception {
        http.perform(put("/api/v1/barber-schedules/" + barber.id()).header("Authorization", owner)
                        .contentType(MediaType.APPLICATION_JSON).content(WEEK))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slots[0].startTime").value("08:00"))
                .andExpect(jsonPath("$.slots[1].isActive").value(true));
    }

    @Test
    void the_owner_sets_the_week_and_the_barber_reads_it() throws Exception {
        setWeek();

        http.perform(get("/api/v1/barber-schedules/" + barber.id()).header("Authorization", self))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.barberProfileId").value(barber.id().toString()))
                .andExpect(jsonPath("$.slots.length()").value(2));
        http.perform(get("/api/v1/barber-schedules/" + barber.id()).header("Authorization", bearer("BARBER", shop)))
                .andExpect(status().isForbidden());
    }

    @Test
    void a_bad_shape_is_400_with_its_position_and_an_overlap_is_422() throws Exception {
        http.perform(put("/api/v1/barber-schedules/" + barber.id()).header("Authorization", owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"slots\":[{\"dayOfWeek\":1,\"startTime\":\"12:00\",\"endTime\":\"08:00\"}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0].field").value("slots[0].endTime"));
        http.perform(put("/api/v1/barber-schedules/" + barber.id()).header("Authorization", owner)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"slots\":[{\"dayOfWeek\":1,\"startTime\":"
                                + "\"08:00\",\"endTime\":\"12:00\"},{\"dayOfWeek\":1,\"startTime\":\"11:00\",\"endTime\":\"15:00\"}]}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("The Monday blocks 08:00–12:00 and 11:00–15:00 overlap"));
    }

    @Test
    void availability_is_every_block_cut_by_the_service_length_and_replaced_by_an_exception() throws Exception {
        setWeek();
        String query = "/api/v1/availability?barberId=" + barber.id() + "&serviceId=" + cut.id() + "&date=";

        http.perform(get(query + monday).header("Authorization", bearer("CLIENT", shop)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slots.length()").value(4))
                .andExpect(jsonPath("$.slots[2].startTime").value("14:00"));

        http.perform(post("/api/v1/schedule-exceptions").header("Authorization", owner)
                        .header("Idempotency-Key", "key-" + UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"barberId\":\"" + barber.id() + "\",\"exceptionDate\":\"" + monday + "\","
                                + "\"isDayOff\":false,\"startTime\":\"16:00\",\"endTime\":\"17:00\",\"reason\":\"Doctor\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"));
        http.perform(get(query + monday).header("Authorization", bearer("CLIENT", shop)))
                .andExpect(jsonPath("$.slots.length()").value(2))
                .andExpect(jsonPath("$.slots[0].startTime").value("16:00"));
        http.perform(get(query + "2020-01-06").header("Authorization", owner))
                .andExpect(jsonPath("$.slots.length()").value(0));
    }

    @Test
    void a_client_and_the_barber_see_the_same_slots_without_the_ones_others_booked() throws Exception {
        setWeek();
        APPOINTMENT_API.book(shop, barber.id(), monday, "08:00", "08:30");
        String query = "/api/v1/availability?barberId=" + barber.id() + "&serviceId=" + cut.id() + "&date=" + monday;

        String forClient = http.perform(get(query).header("Authorization", bearer("CLIENT", shop)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slots.length()").value(3))
                .andExpect(jsonPath("$.slots[0].startTime").value("08:30"))
                .andReturn().getResponse().getContentAsString();
        String forBarber = http.perform(get(query).header("Authorization", bearer(barber.userId(), "BARBER", shop)))
                .andReturn().getResponse().getContentAsString();

        org.junit.jupiter.api.Assertions.assertEquals(forClient, forBarber);
    }

    @Test
    void availability_answers_503_when_appointment_does_not_answer() throws Exception {
        setWeek();
        APPOINTMENT_API.down = true;
        try {
            http.perform(get("/api/v1/availability?barberId=" + barber.id() + "&serviceId=" + cut.id() + "&date=" + monday)
                            .header("Authorization", bearer("CLIENT", shop)))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.error").value("SERVICE_UNAVAILABLE"));
        } finally {
            APPOINTMENT_API.down = false;
        }
    }

    @Test
    void availability_needs_its_three_parameters_and_hides_another_barbershop() throws Exception {
        http.perform(get("/api/v1/availability?barberId=" + barber.id()).header("Authorization", owner))
                .andExpect(status().isBadRequest());
        http.perform(get("/api/v1/availability?barberId=" + barber.id() + "&serviceId=" + cut.id() + "&date=" + monday)
                        .header("Authorization", bearer("CLIENT", UUID.randomUUID())))
                .andExpect(status().isNotFound());
        http.perform(get("/api/v1/availability?barberId=" + barber.id() + "&serviceId=" + cut.id() + "&date=" + monday)
                        .header("Authorization", bearer("CLIENT", null)))
                .andExpect(status().isForbidden());
    }

    @Test
    void exceptions_are_one_per_date_listed_for_the_barber_and_deleted_by_the_owner() throws Exception {
        String key = "key-" + UUID.randomUUID();
        String dayOff = "{\"barberId\":\"" + barber.id() + "\",\"exceptionDate\":\"2099-10-12\",\"reason\":\"Holiday\"}";
        String body = http.perform(post("/api/v1/schedule-exceptions").header("Authorization", owner)
                        .header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON).content(dayOff))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.isDayOff").value(true))
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(body, "$.id");

        http.perform(post("/api/v1/schedule-exceptions").header("Authorization", owner).header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON).content(dayOff)).andExpect(status().isOk());
        http.perform(post("/api/v1/schedule-exceptions").header("Authorization", owner)
                        .header("Idempotency-Key", "key-" + UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON)
                        .content(dayOff))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("The barber already has an exception for 2099-10-12"));
        http.perform(post("/api/v1/schedule-exceptions").header("Authorization", owner)
                        .header("Idempotency-Key", "key-" + UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"barberId\":\"" + barber.id() + "\",\"exceptionDate\":\"2099-10-13\",\"isDayOff\":false}"))
                .andExpect(status().isBadRequest());

        http.perform(get("/api/v1/schedule-exceptions").header("Authorization", self))
                .andExpect(jsonPath("$.meta.total").value(1));
        http.perform(get("/api/v1/schedule-exceptions?from=2099-10-13&to=2099-10-12").header("Authorization", owner))
                .andExpect(status().isBadRequest());
        http.perform(get("/api/v1/schedule-exceptions/" + id).header("Authorization", bearer("ADMIN_BARBERSHOP",
                UUID.randomUUID()))).andExpect(status().isNotFound());
        http.perform(delete("/api/v1/schedule-exceptions/" + id).header("Authorization", self))
                .andExpect(status().isForbidden());
        http.perform(delete("/api/v1/schedule-exceptions/" + id).header("Authorization", owner))
                .andExpect(status().isNoContent());
    }
}
