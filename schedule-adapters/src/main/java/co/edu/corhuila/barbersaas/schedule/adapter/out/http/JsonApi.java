package co.edu.corhuila.barbersaas.schedule.adapter.out.http;

import co.edu.corhuila.barbersaas.schedule.application.port.in.Caller;
import co.edu.corhuila.barbersaas.schedule.application.port.out.DependencyFailure;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import org.slf4j.MDC;

/**
 * GET against another domain's API with explicit limits (norm 5.3.10): 2 s to connect, 3 s per
 * request, no retries (a read the user can repeat). It passes on the caller's token, which the
 * other service validates again, and the X-Correlation-Id, so one request is traced across services.
 */
final class JsonApi {

    static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
    static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(3);

    private final String name;
    private final String baseUrl;
    private final HttpClient http;
    private final ObjectMapper json = new ObjectMapper();

    JsonApi(String name, String baseUrl) {
        this.name = name;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.http = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
    }

    /** 200: the body. 404: empty (absent, or another barbershop's). Anything else: DependencyFailure. */
    Optional<JsonNode> get(Caller caller, String pathAndQuery) {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(baseUrl + pathAndQuery))
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", "application/json")
                .header("Authorization", "Bearer " + caller.credential())
                .GET();
        String correlationId = MDC.get("correlationId");
        if (correlationId != null) {
            request.header("X-Correlation-Id", correlationId);
        }
        HttpResponse<byte[]> response;
        try {
            response = http.send(request.build(), HttpResponse.BodyHandlers.ofByteArray());
        } catch (IOException e) {
            throw new DependencyFailure(name, "unreachable or too slow (" + e.getClass().getSimpleName() + ")");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DependencyFailure(name, "interrupted");
        }
        if (response.statusCode() == 404) {
            return Optional.empty();
        }
        if (response.statusCode() != 200) {
            throw new DependencyFailure(name, "answered " + response.statusCode() + " to GET " + pathAndQuery);
        }
        try {
            return Optional.of(json.readTree(response.body()));
        } catch (IOException e) {
            throw new DependencyFailure(name, "answered a body that is not JSON");
        }
    }
}
