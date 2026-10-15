package codergod1337.app1.system.security.requestmonitor;

import codergod1337.app1.system.security.requestmonitor.model.BlockedClientIp;
import codergod1337.app1.system.security.requestmonitor.service.BlockedClientIpService;
import codergod1337.app1.system.security.requestmonitor.service.RequestMonitorService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Der erste Filter der Kette (User 2026-10-15), zwei Aufgaben:
 *
 * Sperrliste: Steht die Client-IP mit laufender Sperre im BlockedClientIpService, endet jede Anfrage hier, an jedem
 * Endpunkt, mit oder ohne Token: 429, Retry-After bis zum Ablauf, Connection: close, Body ungelesen, keine
 * Token-Prüfung, kein Controller. Gezählt wird das je Sperre, nicht je Anfrage, damit ein hämmernder Bot keinen
 * Speicher frisst.
 *
 * Map der Anfragen: Jede Anfrage an den Login-Endpunkt landet im RequestMonitorService mit IP, Zeitstempel, Endpunkt
 * und ob die Sperrliste sie abgewiesen hat. Vorerst nur POST /api/rest/v1/public/{intern|extern}/login/start, weitere
 * Endpunkte kommen Schritt für Schritt dazu.
 *
 * Eingehängt in der SecurityConfig per new, kein Component, sonst registrierte Spring Boot ihn ein zweites Mal in der
 * Servlet-Kette.
 */
public class RequestMonitorFilter extends OncePerRequestFilter {

	/** Die Endpunkte, die in die Map gehen */
	private static final Pattern MONITORED_PATHS = Pattern.compile("^/api/rest/v1/public/(intern|extern)/login/start$");

	private final RequestMonitorService requestMonitorService;
	private final BlockedClientIpService blockedClientIpService;

	public RequestMonitorFilter(RequestMonitorService requestMonitorService,
			BlockedClientIpService blockedClientIpService) {
		this.requestMonitorService = requestMonitorService;
		this.blockedClientIpService = blockedClientIpService;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		String clientIp = request.getRemoteAddr();
		Optional<BlockedClientIp> activeBlock = blockedClientIpService.findActiveClientIpBlock(clientIp);

		if ("POST".equals(request.getMethod()) && MONITORED_PATHS.matcher(request.getRequestURI()).matches()) {
			requestMonitorService.logClientRequest(clientIp, request.getMethod() + " " + request.getRequestURI(),
					activeBlock.isPresent());
		}

		if (activeBlock.isPresent()) {
			activeBlock.get().countRejectedRequest();
			long retryAfterSeconds = Math.max(1,
					Duration.between(Instant.now(), activeBlock.get().getExpiresAt()).toSeconds());
			response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
			response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(retryAfterSeconds));
			// Tomcat schließt die Verbindung nach der Antwort
			response.setHeader(HttpHeaders.CONNECTION, "close");
			return;
		}
		filterChain.doFilter(request, response);
	}

}
