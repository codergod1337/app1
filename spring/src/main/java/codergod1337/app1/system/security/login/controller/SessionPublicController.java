package codergod1337.app1.system.security.login.controller;

import codergod1337.app1.system.security.login.SessionCookies;
import codergod1337.app1.system.security.login.model.IssuedSession;
import codergod1337.app1.system.security.login.model.SessionInfo;
import codergod1337.app1.system.security.login.service.LoginService;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Die Sitzung, für beide areas und auch ohne Anmeldung: wer bin ich, erneuern, abmelden. Das Refresh-Cookie geht nur
 * an diesen Pfad.
 */
@RestController
@RequestMapping("/api/rest/v1/public/session")
public class SessionPublicController {

	private final LoginService loginService;
	private final SessionCookies sessionCookies;

	public SessionPublicController(LoginService loginService, SessionCookies sessionCookies) {
		this.loginService = loginService;
		this.sessionCookies = sessionCookies;
	}

	/** Immer 200: die Sitzung des Angemeldeten oder die leere Sitzung eines Unangemeldeten. */
	@GetMapping
	public SessionInfo getSessionInfo() {
		return loginService.getSessionInfo();
	}

	/**
	 * Neues JWT und neuer Refresh-Token, beide als Cookie. 401, wenn das Refresh-Cookie fehlt oder nicht mehr gilt.
	 * Ein gescheiterter Refresh darf nie automatisch wiederholt werden: Der Token ist dann verbraucht, ein zweiter
	 * Versuch sähe aus wie Diebstahl.
	 */
	@PostMapping("/refresh")
	public ResponseEntity<Map<String, Object>> refreshSession(
			@CookieValue(name = SessionCookies.REFRESH_COOKIE, required = false) String refreshToken) {
		if (refreshToken == null || refreshToken.isBlank()) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Anmeldung fehlgeschlagen");
		}
		IssuedSession issuedSession = loginService.refreshLogin(refreshToken);
		return ResponseEntity.ok()
				.headers(sessionCookies.createSessionCookies(issuedSession))
				.body(Map.of("sessionNumber", issuedSession.sessionNumber()));
	}

	/** Immer 204 und beide Cookies weg, auch ohne oder mit unbekanntem Cookie: Ein Logout darf nie scheitern. */
	@PostMapping("/logout")
	public ResponseEntity<Void> logoutSession(
			@CookieValue(name = SessionCookies.REFRESH_COOKIE, required = false) String refreshToken) {
		if (refreshToken != null && !refreshToken.isBlank()) {
			loginService.logout(refreshToken);
		}
		return ResponseEntity.noContent().headers(sessionCookies.deleteSessionCookies()).build();
	}

}
