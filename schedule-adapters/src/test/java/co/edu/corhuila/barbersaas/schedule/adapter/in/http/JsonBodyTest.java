package co.edu.corhuila.barbersaas.schedule.adapter.in.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.edu.corhuila.barbersaas.schedule.adapter.in.http.ApiError.FieldError;
import co.edu.corhuila.barbersaas.schedule.adapter.in.http.ApiError.ValidationException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class JsonBodyTest {

    private static final Set<String> SLOT = Set.of("dayOfWeek", "startTime", "endTime");
    private final ObjectMapper json = new ObjectMapper();

    private JsonBody body(String text, String... allowed) throws Exception {
        return JsonBody.of(json.readTree(text), Set.of(allowed));
    }

    private static Set<String> fields(ValidationException e) {
        return Set.copyOf(e.details().stream().map(FieldError::field).toList());
    }

    @Test
    void the_slots_of_a_week_name_their_errors_with_their_position() throws Exception {
        JsonBody b = body("{\"slots\":[{\"dayOfWeek\":1,\"startTime\":\"08:00\",\"endTime\":\"12:00\"},"
                + "{\"dayOfWeek\":7,\"startTime\":\"8:00\",\"endTime\":\"12:00\",\"isActive\":true}]}", "slots");
        List<JsonBody> slots = b.objects("slots", 28, SLOT);
        slots.forEach(s -> {
            s.integer("dayOfWeek", 0, 6);
            s.time("startTime", true);
            s.time("endTime", true);
        });

        ValidationException e = assertThrows(ValidationException.class, b::validate);
        assertEquals(Set.of("slots[1].dayOfWeek", "slots[1].startTime", "slots[1].isActive"), fields(e));
    }

    @Test
    void times_are_hh_mm_and_dates_iso() throws Exception {
        JsonBody b = body("{\"startTime\":\"23:59\",\"endTime\":\"24:00\",\"exceptionDate\":\"2026-02-30\"}",
                "startTime", "endTime", "exceptionDate");

        assertEquals(LocalTime.parse("23:59"), b.time("startTime", true));
        assertNull(b.time("endTime", true));
        assertNull(b.date("exceptionDate"));
        assertEquals(Set.of("endTime", "exceptionDate"), fields(assertThrows(ValidationException.class, b::validate)));
    }

    @Test
    void an_unknown_field_is_a_400_and_an_absent_optional_one_is_not() throws Exception {
        JsonBody b = body("{\"barberId\":\"x\",\"barbershopId\":\"y\"}", "barberId", "isDayOff", "reason");
        b.uuid("barberId");

        assertTrue(b.bool("isDayOff", true));
        assertNull(b.optionalText("reason", 150));
        assertEquals(Set.of("barberId", "barbershopId"), fields(assertThrows(ValidationException.class, b::validate)));
    }

    @Test
    void the_body_must_be_an_object_and_paging_follows_the_shared_parameters() throws Exception {
        assertThrows(ValidationException.class, () -> JsonBody.of(json.readTree("[1]"), Set.of()));
        assertThrows(ValidationException.class, () -> Requests.page(1, 101));
        assertThrows(ValidationException.class, () -> Requests.idempotencyKey("short"));
        assertThrows(ValidationException.class, () -> JsonBody.parseDate("from", "yesterday"));
        assertEquals(20, Requests.page(null, null).limit());
    }
}
