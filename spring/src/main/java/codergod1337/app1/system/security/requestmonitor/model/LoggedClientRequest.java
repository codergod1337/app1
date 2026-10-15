package codergod1337.app1.system.security.requestmonitor.model;

import java.time.Instant;

/**
 * Eine Anfrage, wie sie der RequestMonitorFilter in die Map legt: wer (Client-IP), wann, wohin (Methode und Pfad),
 * und ob die Sperrliste sie abgewiesen hat. Nur im Speicher, der RequestMonitorCleaner löscht sie nach einer Stunde.
 *
 * @param id         laufende Nummer, der Schlüssel in der Map
 * @param clientIp   die Absenderadresse, heute getRemoteAddr(), später die aus dem Header des eigenen nginx
 * @param receivedAt wann die Anfrage ankam
 * @param endpoint   Methode und Pfad, z. B. "POST /api/rest/v1/public/intern/login/start"
 * @param blocked    true: die IP stand auf der Sperrliste, die Anfrage bekam 429 und erreichte den Controller nicht
 */
public record LoggedClientRequest(long id, String clientIp, Instant receivedAt, String endpoint, boolean blocked) {
}
