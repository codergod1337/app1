package codergod1337.app1.system.security.login.model;

import java.time.OffsetDateTime;

/**
 * Eine Sitzung des angemeldeten Users, wie das Profil sie zeigt: ein belegter Platz. Kein Token, kein Hash, keine id.
 * Gerät oder IP speichert das Backend nicht; der Hinweis, welches Fenster das ist, ist lastRefreshedAt: Ein offenes
 * Fenster erneuert alle paar Minuten, ein alter Zeitpunkt ist ein vergessenes Gerät.
 *
 * @param sessionNumber   der Platz 1 bis max-sessions
 * @param area            über welchen Login die Sitzung entstand
 * @param loggedInSince   wann die Kette entstand (familyCreatedAt)
 * @param refreshCount    wie oft die Kette erneuert wurde
 * @param lastRefreshedAt wann das aktuelle Glied entstand, also die letzte Erneuerung
 * @param expiresAt       wann die Sitzung ohne Erneuerung endet
 * @param current         die Sitzung des Aufrufers, der Platz aus seinem Token
 */
public record UsersSessionForClient(int sessionNumber, Area area, OffsetDateTime loggedInSince, int refreshCount,
		OffsetDateTime lastRefreshedAt, OffsetDateTime expiresAt, boolean current) {
}
