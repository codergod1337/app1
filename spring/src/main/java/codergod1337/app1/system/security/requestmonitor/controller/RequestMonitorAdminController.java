package codergod1337.app1.system.security.requestmonitor.controller;

import codergod1337.app1.system.security.requestmonitor.model.BlockClientIpRequest;
import codergod1337.app1.system.security.requestmonitor.model.BlockedClientIp;
import codergod1337.app1.system.security.requestmonitor.model.LoggedClientRequest;
import codergod1337.app1.system.security.requestmonitor.service.BlockedClientIpService;
import codergod1337.app1.system.security.requestmonitor.service.RequestMonitorService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Das Modul Anfragenmonitor (User 2026-10-15): die Map der Anfragen und die Sperrliste der IPs, live für den Admin.
 * Nur ADMIN, das prüfen die Services.
 */
@RestController
@RequestMapping("/api/rest/v1/admin/requestmonitor")
public class RequestMonitorAdminController {

	private final RequestMonitorService requestMonitorService;
	private final BlockedClientIpService blockedClientIpService;

	public RequestMonitorAdminController(RequestMonitorService requestMonitorService,
			BlockedClientIpService blockedClientIpService) {
		this.requestMonitorService = requestMonitorService;
		this.blockedClientIpService = blockedClientIpService;
	}

	/** Alle Anfragen in der Map, die neueste zuerst */
	@GetMapping
	public List<LoggedClientRequest> getLoggedClientRequests() {
		return requestMonitorService.getLoggedClientRequests();
	}

	/** Die Sperrliste, die am frühesten ablaufende Sperre zuerst */
	@GetMapping("/blocked")
	public List<BlockedClientIp> getBlockedClientIps() {
		return blockedClientIpService.getBlockedClientIps();
	}

	/**
	 * Sperre aussprechen: {clientIp, durationMinutes, reason}. 400 bei leerer IP oder Dauer unter 1, und 400 für die
	 * eigene Adresse: Die Sperre gilt an jedem Endpunkt, auch das Aufheben wäre dann zu, nur ein Neustart hülfe.
	 */
	@PostMapping("/blocked")
	public BlockedClientIp blockClientIp(@RequestBody BlockClientIpRequest blockClientIpRequest,
			HttpServletRequest request) {
		if (blockClientIpRequest.clientIp() != null
				&& blockClientIpRequest.clientIp().trim().equals(request.getRemoteAddr())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					"Das ist deine eigene Adresse, du würdest dich selbst aussperren");
		}
		return blockedClientIpService.blockClientIp(blockClientIpRequest);
	}

	/** Sperre aufheben: {clientIp}. 204, 400 ohne IP, 404 wenn nicht gesperrt. */
	@DeleteMapping("/blocked")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void liftClientIpBlock(@RequestBody Map<String, Object> liftData) {
		String clientIp = liftData.get("clientIp") instanceof String clientIpValue ? clientIpValue : null;
		if (clientIp == null || clientIp.isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "clientIp fehlt");
		}
		blockedClientIpService.liftClientIpBlock(clientIp);
	}

}
