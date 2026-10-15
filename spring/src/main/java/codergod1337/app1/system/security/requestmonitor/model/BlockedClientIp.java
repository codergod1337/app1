package codergod1337.app1.system.security.requestmonitor.model;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Eine gesperrte Client-IP in der Sperrliste des BlockedClientIpService (User 2026-10-15). Nur im Speicher. Solange
 * expiresAt in der Zukunft liegt, beendet der RequestMonitorFilter jede Anfrage dieser IP an jedem Endpunkt sofort
 * mit 429, mit oder ohne Token. Ausgesprochen vom Admin im Anfragenmonitor, aufgehoben dort oder vom
 * RequestMonitorCleaner nach Ablauf.
 *
 * Eine Klasse und kein Record, weil der Zähler der abgewiesenen Anfragen mitläuft: eine Zahl je Sperre statt einer
 * Zeile je Anfrage, damit ein Bot, der weiter hämmert, keinen Speicher frisst.
 */
public class BlockedClientIp {

	private final String clientIp;
	private final Instant blockedAt;
	private final Instant expiresAt;
	private final String reason;
	private final AtomicLong rejectedRequests = new AtomicLong();

	public BlockedClientIp(String clientIp, Instant blockedAt, Instant expiresAt, String reason) {
		this.clientIp = clientIp;
		this.blockedAt = blockedAt;
		this.expiresAt = expiresAt;
		this.reason = reason;
	}

	/** die gesperrte Adresse */
	public String getClientIp() {
		return clientIp;
	}

	/** wann die Sperre ausgesprochen wurde */
	public Instant getBlockedAt() {
		return blockedAt;
	}

	/** wann die IP wieder frei ist */
	public Instant getExpiresAt() {
		return expiresAt;
	}

	/** warum, Freitext des Admins, später der Name der gerissenen Grenze */
	public String getReason() {
		return reason;
	}

	/** wie viele Anfragen der Filter seit der Sperre abgewiesen hat, an allen Endpunkten zusammen */
	public long getRejectedRequests() {
		return rejectedRequests.get();
	}

	/** Vom RequestMonitorFilter bei jeder abgewiesenen Anfrage */
	public void countRejectedRequest() {
		rejectedRequests.incrementAndGet();
	}

	public boolean isActiveAt(Instant now) {
		return expiresAt.isAfter(now);
	}

}
