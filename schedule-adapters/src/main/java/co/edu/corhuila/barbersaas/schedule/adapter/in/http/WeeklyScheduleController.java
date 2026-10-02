package co.edu.corhuila.barbersaas.schedule.adapter.in.http;

import co.edu.corhuila.barbersaas.schedule.adapter.in.http.Views.WeeklyScheduleView;
import co.edu.corhuila.barbersaas.schedule.application.port.in.Caller;
import co.edu.corhuila.barbersaas.schedule.application.port.in.WeeklyScheduleUseCases;
import co.edu.corhuila.barbersaas.schedule.application.port.in.WeeklyScheduleUseCases.SlotInput;
import co.edu.corhuila.barbersaas.schedule.domain.model.WeeklySchedule;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** HTTP to use case for the tag Weekly schedule: validates the shape, never decides business rules. */
@RestController
@RequestMapping("/api/v1/barber-schedules")
public class WeeklyScheduleController {

    private static final Set<String> SLOT_FIELDS = Set.of("dayOfWeek", "startTime", "endTime");

    private final WeeklyScheduleUseCases schedules;

    public WeeklyScheduleController(WeeklyScheduleUseCases schedules) {
        this.schedules = schedules;
    }

    @GetMapping("/{barberId}")
    public WeeklyScheduleView get(@RequestAttribute(AuthFilter.CALLER_ATTRIBUTE) Caller caller,
                                  @PathVariable UUID barberId) {
        return WeeklyScheduleView.of(schedules.get(caller, barberId));
    }

    /** DEC-SCHED-01: the whole week at once. A block ending before it starts is a 400; an overlap is a 422. */
    @PutMapping("/{barberId}")
    public WeeklyScheduleView set(@RequestAttribute(AuthFilter.CALLER_ATTRIBUTE) Caller caller,
                                  @PathVariable UUID barberId, @RequestBody(required = false) JsonNode json) {
        JsonBody body = JsonBody.of(json, Set.of("slots"));
        List<SlotInput> slots = new ArrayList<>();
        for (JsonBody slot : body.objects("slots", WeeklySchedule.MAX_BLOCKS, SLOT_FIELDS)) {
            Integer day = slot.integer("dayOfWeek", 0, 6);
            LocalTime start = slot.time("startTime", true);
            LocalTime end = slot.time("endTime", true);
            if (start != null && end != null && !end.isAfter(start)) {
                slot.reject("endTime", "must be after startTime");
            }
            slots.add(new SlotInput(day == null ? 0 : day, start, end));
        }
        body.validate();
        return WeeklyScheduleView.of(schedules.set(caller, barberId, slots));
    }
}
