package co.edu.corhuila.barbersaas.schedule.app;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * A stand-in for barbershop-api on a local port, answering like barbershop-service.yaml: it reads
 * the tenant from the forwarded token, so another barbershop's barber or service answers 404.
 */
final class BarbershopApiStub {

    record Barber(UUID id, UUID barbershopId, UUID userId) { }

    record Service(UUID id, UUID barbershopId, int durationMinutes, boolean active) { }

    final Map<UUID, Barber> barbers = new ConcurrentHashMap<>();
    final Map<UUID, Service> services = new ConcurrentHashMap<>();
    private final ObjectMapper json = new ObjectMapper();
    private final HttpServer server;

    BarbershopApiStub() {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        server.createContext("/", this::handle);
        server.start();
    }

    String url() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    Barber barber(UUID barbershopId, UUID userId) {
        Barber b = new Barber(UUID.randomUUID(), barbershopId, userId);
        barbers.put(b.id(), b);
        return b;
    }

    Service service(UUID barbershopId, int minutes) {
        Service s = new Service(UUID.randomUUID(), barbershopId, minutes, true);
        services.put(s.id(), s);
        return s;
    }

    private void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String tenant = tenantOf(exchange.getRequestHeaders().getFirst("Authorization"));
        String body = null;
        if (path.equals("/api/v1/barbershops/me")) {
            body = "{\"timezone\":\"America/Bogota\"}";
        } else if (path.equals("/api/v1/barbers")) {
            body = "{\"data\":[" + barbers.values().stream().filter(b -> b.barbershopId().toString().equals(tenant))
                    .map(this::barberJson).collect(Collectors.joining(",")) + "],\"meta\":{\"totalPages\":1}}";
        } else if (path.startsWith("/api/v1/barbers/")) {
            Barber b = barbers.get(UUID.fromString(path.substring("/api/v1/barbers/".length())));
            body = b != null && b.barbershopId().toString().equals(tenant) ? barberJson(b) : null;
        } else if (path.startsWith("/api/v1/services/")) {
            Service s = services.get(UUID.fromString(path.substring("/api/v1/services/".length())));
            body = s != null && s.barbershopId().toString().equals(tenant)
                    ? "{\"id\":\"" + s.id() + "\",\"durationMinutes\":" + s.durationMinutes() + ",\"isActive\":"
                    + s.active() + "}" : null;
        }
        byte[] bytes = (body == null ? "{\"error\":\"NOT_FOUND\"}" : body).getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(body == null ? 404 : 200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private String barberJson(Barber b) {
        return "{\"id\":\"" + b.id() + "\",\"userId\":\"" + b.userId() + "\"}";
    }

    /** The real barbershop-api validates the signature; the stub only needs the claim. */
    private String tenantOf(String authorization) throws IOException {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return "";
        }
        String payload = authorization.substring(7).split("\\.")[1];
        JsonNode claims = json.readTree(Base64.getUrlDecoder().decode(payload));
        return claims.path("barbershopId").asText("");
    }
}
