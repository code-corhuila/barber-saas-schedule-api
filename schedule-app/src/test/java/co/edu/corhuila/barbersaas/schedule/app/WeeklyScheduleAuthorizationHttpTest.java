package co.edu.corhuila.barbersaas.schedule.app;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/**
 * barber-saas-docs#163: the weekly schedule of a barber is changed only by the owner of that barber's
 * barbershop. Two barbershops, A and B; B's owner sets the week first and A's owner then tries to replace it.
 */
class WeeklyScheduleAuthorizationHttpTest extends HttpTest {

    private static final String B_WEEK = "{\"slots\":[{\"dayOfWeek\":2,\"startTime\":\"09:00\",\"endTime\":\"12:00\"}]}";
    private static final String A_WEEK = "{\"slots\":[{\"dayOfWeek\":5,\"startTime\":\"18:00\",\"endTime\":\"20:00\"}]}";

    private final UUID shopA = UUID.randomUUID();
    private final UUID shopB = UUID.randomUUID();
    private final BarbershopApiStub.Barber barberOfB = BARBERSHOP_API.barber(shopB, UUID.randomUUID());
    private final String ownerOfA = bearer("ADMIN_BARBERSHOP", shopA);
    private final String ownerOfB = bearer("ADMIN_BARBERSHOP", shopB);

    private ResultActions putWeek(String token, String week) throws Exception {
        var request = put("/api/v1/barber-schedules/" + barberOfB.id())
                .contentType(MediaType.APPLICATION_JSON).content(week);
        if (token != null) {
            request.header("Authorization", token);
        }
        return http.perform(request);
    }

    private void expectBWeekUnchanged() throws Exception {
        http.perform(get("/api/v1/barber-schedules/" + barberOfB.id()).header("Authorization", ownerOfB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slots.length()").value(1))
                .andExpect(jsonPath("$.slots[0].dayOfWeek").value(2))
                .andExpect(jsonPath("$.slots[0].startTime").value("09:00"))
                .andExpect(jsonPath("$.slots[0].endTime").value("12:00"));
    }

    @BeforeEach
    void ownerOfBSetsTheWeek() throws Exception {
        putWeek(ownerOfB, B_WEEK).andExpect(status().isOk());
    }

    @Test
    void the_owner_of_another_barbershop_gets_404_and_the_week_stays_unchanged() throws Exception {
        putWeek(ownerOfA, A_WEEK)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"));
        http.perform(get("/api/v1/barber-schedules/" + barberOfB.id()).header("Authorization", ownerOfA))
                .andExpect(status().isNotFound());

        expectBWeekUnchanged();
    }

    @Test
    void a_barber_or_a_client_gets_403_and_the_week_stays_unchanged() throws Exception {
        putWeek(bearer(barberOfB.userId(), "BARBER", shopB), A_WEEK)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));
        putWeek(bearer("CLIENT", null), A_WEEK)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));

        expectBWeekUnchanged();
    }

    @Test
    void without_a_token_it_answers_401_and_the_week_stays_unchanged() throws Exception {
        putWeek(null, A_WEEK)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));

        expectBWeekUnchanged();
    }
}
