package co.edu.corhuila.barbersaas.schedule.app;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * A stand-in for appointment-api's GET /internal/v1/busy-slots (appointment-service.yaml listBusySlots,
 * DEC-APPT-05): it answers only schedule's own service token, so a forwarded user token would get 403.
 */
final class AppointmentApiStub {

    static final String SERVICE_TOKEN = "schedule-service-token";

    /** "startTime-endTime" pairs per "barbershopId|barberId|date"; anything else answers an empty list. */
    final Map<String, List<String>> busy = new ConcurrentHashMap<>();
    volatile boolean down;
    private final HttpServer server;

    AppointmentApiStub() {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        server.createContext("/internal/v1/busy-slots", this::handle);
        server.start();
    }

    String url() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    void book(Object barbershopId, Object barberId, Object date, String start, String end) {
        busy.computeIfAbsent(barbershopId + "|" + barberId + "|" + date, k -> new java.util.ArrayList<>())
                .add(start + "-" + end);
    }

    private void handle(HttpExchange exchange) throws IOException {
        Map<String, String> q = java.util.Arrays.stream(exchange.getRequestURI().getQuery().split("&"))
                .map(p -> p.split("=", 2)).collect(Collectors.toMap(p -> p[0], p -> p[1]));
        boolean ours = ("Bearer " + SERVICE_TOKEN).equals(exchange.getRequestHeaders().getFirst("Authorization"));
        int status = down ? 503 : ours ? 200 : 403;
        String body = busy.getOrDefault(q.get("barbershopId") + "|" + q.get("barberId") + "|" + q.get("date"), List.of())
                .stream().map(s -> "{\"startTime\":\"" + s.split("-")[0] + "\",\"endTime\":\"" + s.split("-")[1] + "\"}")
                .collect(Collectors.joining(",", "{\"data\":[", "]}"));
        byte[] bytes = (status == 200 ? body : "{}").getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
