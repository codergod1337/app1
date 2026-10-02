package codergod1337.app1.system.security.login;

import codergod1337.app1.system.security.login.model.IssuedSession;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * Baut die zwei Cookies einer Sitzung und löscht sie wieder. An einer Stelle, weil beim Löschen Name, Pfad und alle
 * übrigen Angaben genau denen beim Setzen entsprechen müssen, sonst findet der Browser das Cookie nicht wieder.
 *
 * Beide sind httpOnly (kein JavaScript kommt daran) und SameSite=Strict (keine fremde Seite kann sie mitschicken
 * lassen).
 */
@Component
public class SessionCookies {

	/** Das JWT, geht an alle Endpunkte unter /api */
	public static final String JWT_COOKIE = "app1_jwt";

	/** Der Refresh-Token, geht nur an Sitzung, Refresh und Logout */
	public static final String REFRESH_COOKIE = "app1_refresh";

	private static final String JWT_COOKIE_PATH = "/api";
	private static final String REFRESH_COOKIE_PATH = "/api/rest/v1/public/session";

	private final Duration jwtValidity;
	private final Duration refreshValidity;
	private final boolean cookieSecure;

	public SessionCookies(@Value("${app1.jwt.validity}") Duration jwtValidity,
			@Value("${app1.refresh.validity}") Duration refreshValidity,
			@Value("${app1.cookie.secure}") boolean cookieSecure) {
		this.jwtValidity = jwtValidity;
		this.refreshValidity = refreshValidity;
		this.cookieSecure = cookieSecure;
	}

	/** Die zwei Cookies einer frisch ausgestellten Sitzung */
	public HttpHeaders createSessionCookies(IssuedSession issuedSession) {
		HttpHeaders headers = new HttpHeaders();
		headers.add(HttpHeaders.SET_COOKIE, createCookie(JWT_COOKIE, issuedSession.jwt(), JWT_COOKIE_PATH, jwtValidity).toString());
		headers.add(HttpHeaders.SET_COOKIE,
				createCookie(REFRESH_COOKIE, issuedSession.refreshToken(), REFRESH_COOKIE_PATH, refreshValidity).toString());
		return headers;
	}

	/** Beide löschen: leerer Wert und Laufzeit 0, mit denselben Angaben wie beim Setzen */
	public HttpHeaders deleteSessionCookies() {
		HttpHeaders headers = new HttpHeaders();
		headers.add(HttpHeaders.SET_COOKIE, createCookie(JWT_COOKIE, "", JWT_COOKIE_PATH, Duration.ZERO).toString());
		headers.add(HttpHeaders.SET_COOKIE, createCookie(REFRESH_COOKIE, "", REFRESH_COOKIE_PATH, Duration.ZERO).toString());
		return headers;
	}

	private ResponseCookie createCookie(String name, String value, String path, Duration maxAge) {
		return ResponseCookie.from(name, value)
				.httpOnly(true)
				.secure(cookieSecure)
				.sameSite("Strict")
				.path(path)
				.maxAge(maxAge)
				.build();
	}

}
