package co.edu.corhuila.barbersaas.schedule.adapter.in.http;

import co.edu.corhuila.barbersaas.schedule.adapter.in.http.ApiError.FieldError;
import co.edu.corhuila.barbersaas.schedule.adapter.in.http.ApiError.ValidationException;
import co.edu.corhuila.barbersaas.schedule.adapter.in.http.Views.AvailabilityView;
import co.edu.corhuila.barbersaas.schedule.application.port.in.AvailabilityUseCases;
import co.edu.corhuila.barbersaas.schedule.application.port.in.Caller;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** HTTP to use case for the tag Availability: what appointment-api and the booking screen consume. */
@RestController
@RequestMapping("/api/v1/availability")
public class AvailabilityController {

    private final AvailabilityUseCases availability;

    public AvailabilityController(AvailabilityUseCases availability) {
        this.availability = availability;
    }

    @GetMapping
    public AvailabilityView free(@RequestAttribute(AuthFilter.CALLER_ATTRIBUTE) Caller caller,
                                 @RequestParam(required = false) UUID barberId,
                                 @RequestParam(required = false) UUID serviceId,
                                 @RequestParam(required = false) String date) {
        List<FieldError> missing = new ArrayList<>();
        if (barberId == null) {
            missing.add(new FieldError("barberId", "required"));
        }
        if (serviceId == null) {
            missing.add(new FieldError("serviceId", "required"));
        }
        if (date == null) {
            missing.add(new FieldError("date", "required"));
        }
        if (!missing.isEmpty()) {
            throw new ValidationException("the request is not valid", missing);
        }
        LocalDate day = JsonBody.parseDate("date", date);
        return AvailabilityView.of(availability.free(caller, barberId, serviceId, day));
    }
}
