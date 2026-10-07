package codergod1337.app1.system.security.login.model;

/**
 * Die Antwort auf Schritt 1 des Logins: nur der Abholschein für das Fenster mit dem Passwort. Der Bestätigungs-Token
 * geht nie in der Antwort mit, nur per Mail an den User.
 */
public record Login2faPollToken(String pollToken) {
}
