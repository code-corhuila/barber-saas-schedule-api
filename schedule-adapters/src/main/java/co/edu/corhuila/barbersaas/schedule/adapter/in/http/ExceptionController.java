package co.edu.corhuila.barbersaas.schedule.adapter.in.http;

import co.edu.corhuila.barbersaas.schedule.adapter.in.http.ApiError.FieldError;
import co.edu.corhuila.barbersaas.schedule.adapter.in.http.ApiError.ValidationException;
import co.edu.corhuila.barbersaas.schedule.adapter.in.http.Views.ExceptionView;
import co.edu.corhuila.barbersaas.schedule.adapter.in.http.Views.PageView;
import co.edu.corhuila.barbersaas.schedule.application.port.in.Caller;
import co.edu.corhuila.barbersaas.schedule.application.port.in.Created;
import co.edu.corhuila.barbersaas.schedule.application.port.in.ExceptionUseCases;
import co.edu.corhuila.barbersaas.schedule.application.port.in.ExceptionUseCases.Filter;
import co.edu.corhuila.barbersaas.schedule.application.port.in.ExceptionUseCases.NewException;
import co.edu.corhuila.barbersaas.schedule.domain.model.ScheduleException;
import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** HTTP to use case for the tag Schedule exceptions; the barbershop is always the token's. */
@RestController
@RequestMapping("/api/v1/schedule-exceptions")
public class ExceptionController {

    private static final Set<String> CREATE_FIELDS =
            Set.of("barberId", "exceptionDate", "isDayOff", "startTime", "endTime", "reason");

    private final ExceptionUseCases exceptions;

    public ExceptionController(ExceptionUseCases exceptions) {
        this.exceptions = exceptions;
    }

    @GetMapping
    public PageView<ExceptionView> list(@RequestAttribute(AuthFilter.CALLER_ATTRIBUTE) Caller caller,
                                        @RequestParam(required = false) Integer page,
                                        @RequestParam(required = false) Integer limit,
                                        @RequestParam(required = false) UUID barberId,
                                        @RequestParam(required = false) String from,
                                        @RequestParam(required = false) String to) {
        LocalDate fromDate = JsonBody.parseDate("from", from);
        LocalDate toDate = JsonBody.parseDate("to", to);
        if (fromDate != null && toDate != null && toDate.isBefore(fromDate)) {
            throw new ValidationException("the request is not valid", List.of(new FieldError("to", "cannot be before from")));
        }
        return PageView.of(exceptions.list(caller, new Filter(barberId, fromDate, toDate), Requests.page(page, limit)),
                ExceptionView::of);
    }

    /** isDayOff defaults to true; special hours (false) need startTime and endTime, the end after the start. */
    @PostMapping
    public ResponseEntity<ExceptionView> create(@RequestAttribute(AuthFilter.CALLER_ATTRIBUTE) Caller caller,
                                                @RequestHeader(value = "Idempotency-Key", required = false) String key,
                                                @RequestBody(required = false) JsonNode json) {
        String idempotencyKey = Requests.idempotencyKey(key);
        JsonBody body = JsonBody.of(json, CREATE_FIELDS);
        UUID barberId = body.uuid("barberId");
        LocalDate date = body.date("exceptionDate");
        boolean dayOff = body.bool("isDayOff", true);
        LocalTime start = body.time("startTime", !dayOff);
        LocalTime end = body.time("endTime", !dayOff);
        if (dayOff && (start != null || end != null)) {
            body.reject("isDayOff", "a day off has no startTime or endTime");
        }
        if (start != null && end != null && !end.isAfter(start)) {
            body.reject("endTime", "must be after startTime");
        }
        String reason = body.optionalText("reason", 150);
        body.validate();
        Created<ScheduleException> result = exceptions.create(caller,
                new NewException(barberId, date, dayOff, start, end, reason), idempotencyKey);
        ExceptionView view = ExceptionView.of(result.value());
        if (!result.created()) {
            return ResponseEntity.ok(view);
        }
        return ResponseEntity.status(HttpStatus.CREATED)
                .location(URI.create("/api/v1/schedule-exceptions/" + view.id())).body(view);
    }

    @GetMapping("/{id}")
    public ExceptionView get(@RequestAttribute(AuthFilter.CALLER_ATTRIBUTE) Caller caller, @PathVariable UUID id) {
        return ExceptionView.of(exceptions.get(caller, id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@RequestAttribute(AuthFilter.CALLER_ATTRIBUTE) Caller caller,
                                       @PathVariable UUID id) {
        exceptions.delete(caller, id);
        return ResponseEntity.noContent().build();
    }
}
