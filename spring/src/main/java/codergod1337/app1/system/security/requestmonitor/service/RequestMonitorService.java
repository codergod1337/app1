package codergod1337.app1.system.security.requestmonitor.service;

import codergod1337.app1.system.security.requestmonitor.model.LoggedClientRequest;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

/**
 * Die Map der Anfragen (User 2026-10-15): Jede Anfrage an den Login-Endpunkt landet hier mit IP, Zeitstempel und
 * Endpunkt, der Admin sieht sie im Modul Anfragenmonitor. Nur im Speicher, ein Neustart leert sie. Schreiben kostet
 * unter einer Mikrosekunde im Anfrage-Thread, kein Postgres. Der RequestMonitorCleaner löscht alle fünf Minuten,
 * was älter als eine Stunde ist.
 *
 * Offen, nicht vergessen (User 2026-10-15, auch in ziele.md):
 *
 * Weitere Limits, jedes in der application.yml: je IP und Endpunkt Anfragen je Stunde am Login (start, confirm,
 * claim), Anfragen ohne Token an gesperrte Pfade je Minute, bei Überschreiten automatisch in die Sperrliste des
 * BlockedClientIpService; IPv6 je /64 zählen. Später Grenzen je User und AR.
 *
 * Endpunkt-History: jede Anfrage eines Users oder einer IP an jeden Endpunkt festhalten. Noch zu besprechen, mit
 * Vor- und Nachteilen: über diese Map als Eingangskorb (ein Job schreibt jede Minute gesammelt nach Postgres und
 * leert sie) oder jeden Request asynchron direkt in eine Tabelle request_history.
 *
 * Eigene History je User: seine letzten Anfragen (z. B. 100), aufbereitet nach Fachbegriffen statt roher URLs
 * (abgeschickte Suchen, geöffnete Artikelmappen, angesehene Dokumente), mit Suche darin. Die Mitarbeiter erinnern
 * sich oft nicht an das Element, aber daran, dass sie es heute oder gestern offen hatten.
 */
@Service
public class RequestMonitorService {

	private final ConcurrentHashMap<Long, LoggedClientRequest> loggedClientRequestsById = new ConcurrentHashMap<>();

	private final AtomicLong nextLoggedClientRequestId = new AtomicLong(1);

	/**
	 * Vom RequestMonitorFilter, ohne Token und ohne current user: Beim Login ist noch niemand angemeldet. blocked: die
	 * Sperrliste hat die Anfrage abgewiesen, sie steht trotzdem in der Map, damit der Admin sieht, dass die IP weiter
	 * anklopft.
	 */
	public void logClientRequest(String clientIp, String endpoint, boolean blocked) {
		long id = nextLoggedClientRequestId.getAndIncrement();
		loggedClientRequestsById.put(id, new LoggedClientRequest(id, clientIp, Instant.now(), endpoint, blocked));
	}

	/** Alle Anfragen in der Map, die neueste zuerst */
	@PreAuthorize("hasRole('ADMIN')")
	public List<LoggedClientRequest> getLoggedClientRequests() {
		return loggedClientRequestsById.values().stream()
				.sorted(Comparator.comparingLong((LoggedClientRequest request) -> request.id()).reversed())
				.toList();
	}

	/** Vom RequestMonitorCleaner: löscht alles, was vor dem Zeitpunkt ankam. Zurück kommt, wie viele es waren. */
	public int deleteLoggedClientRequestsReceivedBefore(Instant cutoff) {
		int before = loggedClientRequestsById.size();
		loggedClientRequestsById.values().removeIf(request -> request.receivedAt().isBefore(cutoff));
		return before - loggedClientRequestsById.size();
	}

}
