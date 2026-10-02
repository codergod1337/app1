package codergod1337.app1.system.security.login.model;

/** Eine frisch ausgestellte Sitzung, nur zwischen LoginService und Controller: daraus werden die Cookies gesetzt. */
public record IssuedSession(String jwt, String refreshToken, int sessionNumber) {
}
