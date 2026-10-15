package codergod1337.app1.system.security.requestmonitor.service;

import codergod1337.app1.system.security.requestmonitor.model.BlockClientIpRequest;
import codergod1337.app1.system.security.requestmonitor.model.BlockedClientIp;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Die Sperrliste der Client-IPs (User 2026-10-15): eine eigene ConcurrentHashMap neben der Map der Anfragen, je IP
 * eine Sperre mit Ablauf. Nur im Speicher, ein Neustart leert sie. Der Admin spricht Sperren im Anfragenmonitor aus
 * und hebt sie auf; der RequestMonitorFilter fragt vor jeder Anfrage an jedem Endpunkt nach, der
 * RequestMonitorCleaner räumt Abgelaufenes weg.
 */
@Service
public class BlockedClientIpService {

	/** 45 Zeichen: eine IPv6-Adresse in ihrer längsten Schreibweise */
	private static final int MAX_CLIENT_IP_LENGTH = 45;

	private static final int MAX_REASON_LENGTH = 200;

	private final ConcurrentHashMap<String, BlockedClientIp> blockedClientIpsByIp = new ConcurrentHashMap<>();

	/** Sperre aussprechen. Eine bestehende Sperre derselben IP wird ersetzt. 400 bei leerer IP oder Dauer unter 1. */
	@PreAuthorize("hasRole('ADMIN')")
	public BlockedClientIp blockClientIp(BlockClientIpRequest request) {
		String clientIp = request.clientIp() == null ? "" : request.clientIp().trim();
		if (clientIp.isEmpty() || clientIp.length() > MAX_CLIENT_IP_LENGTH) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "clientIp fehlt oder ist zu lang");
		}
		if (request.durationMinutes() < 1) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "durationMinutes muss mindestens 1 sein");
		}
		String reason = request.reason() == null ? "" : request.reason().trim();
		if (reason.length() > MAX_REASON_LENGTH) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "reason ist zu lang");
		}
		Instant now = Instant.now();
		BlockedClientIp blockedClientIp = new BlockedClientIp(clientIp, now,
				now.plus(Duration.ofMinutes(request.durationMinutes())), reason);
		blockedClientIpsByIp.put(clientIp, blockedClientIp);
		return blockedClientIp;
	}

	/** Sperre aufheben. 404, wenn die IP nicht gesperrt ist. */
	@PreAuthorize("hasRole('ADMIN')")
	public void liftClientIpBlock(String clientIp) {
		if (blockedClientIpsByIp.remove(clientIp == null ? "" : clientIp.trim()) == null) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Diese IP ist nicht gesperrt");
		}
	}

	/** Alle Sperren, die am frühesten ablaufende zuerst, auch die schon abgelaufenen bis der Cleaner sie wegräumt */
	@PreAuthorize("hasRole('ADMIN')")
	public List<BlockedClientIp> getBlockedClientIps() {
		return blockedClientIpsByIp.values().stream()
				.sorted(Comparator.comparing((BlockedClientIp blocked) -> blocked.getExpiresAt()))
				.toList();
	}

	/** Vom RequestMonitorFilter, vor jeder Anfrage: die laufende Sperre dieser IP, leer wenn keine oder abgelaufen */
	public Optional<BlockedClientIp> findActiveClientIpBlock(String clientIp) {
		BlockedClientIp blockedClientIp = blockedClientIpsByIp.get(clientIp);
		if (blockedClientIp == null || !blockedClientIp.isActiveAt(Instant.now())) {
			return Optional.empty();
		}
		return Optional.of(blockedClientIp);
	}

	/** Vom RequestMonitorCleaner: abgelaufene Sperren weg. Zurück kommt, wie viele es waren. */
	public int deleteExpiredClientIpBlocks(Instant now) {
		int before = blockedClientIpsByIp.size();
		blockedClientIpsByIp.values().removeIf(blocked -> !blocked.isActiveAt(now));
		return before - blockedClientIpsByIp.size();
	}

}
