package codergod1337.app1.system.security.requestmonitor.model;

/**
 * Was der Admin schickt, um eine IP zu sperren (Anfragenmonitor, User 2026-10-15).
 *
 * @param clientIp        die Adresse
 * @param durationMinutes so lange gilt die Sperre ab jetzt
 * @param reason          warum, Freitext, darf leer sein
 */
public record BlockClientIpRequest(String clientIp, int durationMinutes, String reason) {
}
