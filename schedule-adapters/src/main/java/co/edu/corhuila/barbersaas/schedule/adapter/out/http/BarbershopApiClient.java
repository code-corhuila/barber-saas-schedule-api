package co.edu.corhuila.barbersaas.schedule.adapter.out.http;

import co.edu.corhuila.barbersaas.schedule.application.port.in.Caller;
import co.edu.corhuila.barbersaas.schedule.application.port.out.BarbershopDirectory;
import co.edu.corhuila.barbersaas.schedule.application.port.out.DependencyFailure;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

/** BarbershopDirectory over barbershop-service.yaml; barbershop-api applies the token's tenant itself. */
public class BarbershopApiClient implements BarbershopDirectory {

    /** At most 100 per page (LimitParam); a barbershop with more barbers than this is not expected. */
    private static final int MAX_PAGES = 10;

    private final JsonApi api;

    public BarbershopApiClient(String baseUrl) {
        this.api = new JsonApi("barbershop-api", baseUrl);
    }

    @Override
    public Optional<Barber> barber(Caller caller, UUID barberId) {
        return api.get(caller, "/api/v1/barbers/" + barberId).map(BarbershopApiClient::barber);
    }

    @Override
    public Optional<Barber> barberOfCaller(Caller caller) {
        for (int page = 1; page <= MAX_PAGES; page++) {
            JsonNode body = api.get(caller, "/api/v1/barbers?limit=100&page=" + page)
                    .orElseThrow(() -> new DependencyFailure("barbershop-api", "no barber list"));
            for (JsonNode b : body.path("data")) {
                if (b.path("userId").asText().equals(caller.subject())) {
                    return Optional.of(barber(b));
                }
            }
            if (page >= body.path("meta").path("totalPages").asInt(0)) {
                break;
            }
        }
        return Optional.empty();
    }

    /** An inactive service cannot be booked: it is treated as absent. */
    @Override
    public Optional<CatalogService> service(Caller caller, UUID serviceId) {
        return api.get(caller, "/api/v1/services/" + serviceId)
                .filter(s -> s.path("isActive").asBoolean(false))
                .map(s -> new CatalogService(UUID.fromString(s.path("id").asText()), s.path("durationMinutes").asInt()));
    }

    @Override
    public ZoneId timezone(Caller caller) {
        String zone = api.get(caller, "/api/v1/barbershops/me")
                .map(b -> b.path("timezone").asText())
                .orElseThrow(() -> new DependencyFailure("barbershop-api", "the token's barbershop does not exist"));
        return ZoneId.of(zone);
    }

    private static Barber barber(JsonNode b) {
        return new Barber(UUID.fromString(b.path("id").asText()), UUID.fromString(b.path("userId").asText()));
    }
}
