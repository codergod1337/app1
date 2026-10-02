package codergod1337.app1.system.security.login.model;

/**
 * Die zwei Klartexte einer neuen Wartezeile, nur dieses eine Mal zu haben: der Abholschein für das Fenster mit dem
 * Passwort und der Bestätigungs-Token für den Aktivierungslink.
 */
public record Login2faTokens(String pollToken, String confirmationToken) {
}
