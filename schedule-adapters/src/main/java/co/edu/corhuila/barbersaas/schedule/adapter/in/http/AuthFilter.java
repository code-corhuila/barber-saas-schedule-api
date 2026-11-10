package co.edu.corhuila.barbersaas.schedule.adapter.in.http;

import co.edu.corhuila.barbersaas.schedule.application.port.in.Caller;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Validates the bearer token on every route under /api/: nothing in schedule-service.yaml is anonymous,
 * and only user tokens are accepted there.
 */
public class AuthFilter extends OncePerRequestFilter {

    public static final String CALLER_ATTRIBUTE = "auth.caller";

    private final Rs256Verifier verifier;
    private final ObjectMapper json;

    public AuthFilter(Rs256Verifier verifier, ObjectMapper json) {
        this.verifier = verifier;
        this.json = json;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ") || header.length() == 7) {
            reject(response, "a bearer token is required");
            return;
        }
        // barber-saas-docs#164: the routes of the app take only user tokens issued for the app
        // (aud barber-saas-app); service tokens are only for /internal/v1/**, so a copied service token
        // cannot act on an owner's agenda.
        Caller caller;
        try {
            caller = verifier.verifyForApp(header.substring(7), Instant.now());
        } catch (Rs256Verifier.InvalidTokenException e) {
            reject(response, "the token is invalid or has expired");
            return;
        }
        if (caller.is(Caller.Role.SERVICE)) {
            reject(response, "service tokens are only accepted on internal routes");
            return;
        }
        request.setAttribute(CALLER_ATTRIBUTE, caller);
        chain.doFilter(request, response);
    }

    private void reject(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        json.writeValue(response.getOutputStream(), ApiError.of(ApiError.UNAUTHORIZED, message));
    }
}
