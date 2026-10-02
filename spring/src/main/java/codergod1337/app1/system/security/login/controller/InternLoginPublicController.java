package codergod1337.app1.system.security.login.controller;

import codergod1337.app1.system.HelperInputs;
import codergod1337.app1.system.security.login.SessionCookies;
import codergod1337.app1.system.security.login.model.Area;
import codergod1337.app1.system.security.login.model.IssuedSession;
import codergod1337.app1.system.security.login.model.Login2faTokens;
import codergod1337.app1.system.security.login.service.LoginService;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Der Login für die area INTERN: start, confirm, claim. Gleich aufgebaut wie der ExternLoginPublicController, nur mit
 * seiner area. Später lässt nginx diesen Pfad nur aus dem LAN durch.
 */
@RestController
@RequestMapping("/api/rest/v1/public/intern/login")
public class InternLoginPublicController {

	private final LoginService loginService;
	private final SessionCookies sessionCookies;

	public InternLoginPublicController(LoginService loginService, SessionCookies sessionCookies) {
		this.loginService = loginService;
		this.sessionCookies = sessionCookies;
	}

	/**
	 * Schritt 1: email und Passwort als SHA-256. Bis es Mailversand gibt, kommt der Bestätigungs-Token in der Antwort
	 * mit, das Frontend zeigt ihn als Aktivierungslink. 200, 400 bei ungültiger Eingabe, 401.
	 */
	@PostMapping("/start")
	public Login2faTokens startLogin(@RequestBody Map<String, Object> loginData) {
		String email = loginData.get("email") instanceof String emailValue ? emailValue : null;
		if (email == null || email.isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "email fehlt");
		}
		String password = loginData.get("password") instanceof String passwordValue ? passwordValue : null;
		if (!HelperInputs.isValidSha256Hex(password)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "password fehlt oder ist kein SHA-256");
		}
		return loginService.startLogin(Area.INTERN, email, password);
	}

	/** Schritt 2: der Aktivierungslink. 200 ohne Inhalt, 400 bei fehlendem Token, 401. */
	@PostMapping("/confirm")
	public void confirmLogin(@RequestBody Map<String, Object> confirmationData) {
		String confirmationToken = confirmationData.get("confirmationToken") instanceof String confirmationTokenValue
				? confirmationTokenValue
				: null;
		if (confirmationToken == null || confirmationToken.isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "confirmationToken fehlt");
		}
		loginService.confirmLogin(confirmationToken);
	}

	/**
	 * Schritt 3, im Sekundentakt: {status: WAITING}, solange nicht bestätigt ist. Danach {status: LOGGED_IN,
	 * sessionNumber} und die Cookies. 400 bei fehlendem Token, 401.
	 */
	@PostMapping("/claim")
	public ResponseEntity<Map<String, Object>> claimLogin(@RequestBody Map<String, Object> claimData) {
		String pollToken = claimData.get("pollToken") instanceof String pollTokenValue ? pollTokenValue : null;
		if (pollToken == null || pollToken.isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "pollToken fehlt");
		}
		Optional<IssuedSession> issuedSession = loginService.claimLogin(Area.INTERN, pollToken);
		if (issuedSession.isEmpty()) {
			return ResponseEntity.ok(Map.of("status", "WAITING"));
		}
		return ResponseEntity.ok()
				.headers(sessionCookies.createSessionCookies(issuedSession.get()))
				.body(Map.of("status", "LOGGED_IN", "sessionNumber", issuedSession.get().sessionNumber()));
	}

}
