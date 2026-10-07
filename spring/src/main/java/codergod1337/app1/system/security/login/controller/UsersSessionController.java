package codergod1337.app1.system.security.login.controller;

import codergod1337.app1.system.security.login.SessionCookies;
import codergod1337.app1.system.security.login.model.UsersSessionForClient;
import codergod1337.app1.system.security.login.service.LoginService;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Die eigenen Sitzungen eines angemeldeten Users: sehen und beenden, eine oder alle. Nur mit gültigem Token, der User
 * kommt aus dem Token, nie aus dem Body. Der öffentliche Teil (wer bin ich, Refresh, Logout) liegt im
 * SessionPublicController.
 */
@RestController
@RequestMapping("/api/rest/v1/session")
public class UsersSessionController {

	private final LoginService loginService;
	private final SessionCookies sessionCookies;

	public UsersSessionController(LoginService loginService, SessionCookies sessionCookies) {
		this.loginService = loginService;
		this.sessionCookies = sessionCookies;
	}

	/** Je belegtem Platz eine Sitzung, die eigene markiert. */
	@GetMapping
	public List<UsersSessionForClient> getCurrentUsersSessions() {
		return loginService.getCurrentUsersSessions();
	}

	/**
	 * Eine Sitzung beenden: {sessionNumber}. 204. War es die eigene, gehen die Cookie-Löschungen mit wie beim Logout.
	 * 400 ohne gültige Zahl. Ein leerer Platz ist kein Fehler.
	 */
	@DeleteMapping
	public ResponseEntity<Void> endCurrentUsersSession(@RequestBody Map<String, Object> sessionToEnd) {
		if (!(sessionToEnd.get("sessionNumber") instanceof Integer sessionNumber) || sessionNumber < 1) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "sessionNumber fehlt oder ist keine Platznummer");
		}
		boolean ownSession = loginService.endCurrentUsersSession(sessionNumber);
		if (ownSession) {
			return ResponseEntity.noContent().headers(sessionCookies.deleteSessionCookies()).build();
		}
		return ResponseEntity.noContent().build();
	}

	/** Alle Sitzungen beenden, auch diese: 204 mit Cookie-Löschungen, danach ist man überall draußen. */
	@DeleteMapping("/all")
	public ResponseEntity<Void> endAllCurrentUsersSessions() {
		loginService.endAllCurrentUsersSessions();
		return ResponseEntity.noContent().headers(sessionCookies.deleteSessionCookies()).build();
	}

}
