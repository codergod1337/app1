package codergod1337.app1.system.user.controller;

import codergod1337.app1.system.HelperInputs;
import codergod1337.app1.system.user.service.UsersCredentialsService;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Admin setzt Passwörter. Es gibt keinen Leseweg: Kein Hash geht je hinaus, auch nicht an den Admin.
 *
 * Keine Admin-Prüfung hier: Ab Schritt 4 sichert der Pfad /api/rest/v1/admin/** ab.
 */
@RestController
@RequestMapping("/api/rest/v1/admin/userscredentials")
public class UsersCredentialsAdminController {

	private final UsersCredentialsService usersCredentialsService;

	public UsersCredentialsAdminController(UsersCredentialsService usersCredentialsService) {
		this.usersCredentialsService = usersCredentialsService;
	}

	/**
	 * Setzt das Passwort eines Users oder setzt es zurück, das alte ist egal. newPassword ist der SHA-256 aus dem
	 * Browser. 200, 400 bei ungültiger guid oder ungültigem Hash, 404 wenn es den User nicht gibt.
	 */
	@PutMapping("/password")
	public void setUsersCredentialsPassword(@RequestBody Map<String, Object> newUsersPasswordData) {
		String usersGuidText = newUsersPasswordData.get("usersGuid") instanceof String usersGuidValue ? usersGuidValue : null;
		if (!HelperInputs.isValidGuid(usersGuidText)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "usersGuid fehlt oder ist keine gültige guid");
		}
		String newPassword = newUsersPasswordData.get("newPassword") instanceof String newPasswordValue ? newPasswordValue : null;
		if (!HelperInputs.isValidSha256Hex(newPassword)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "newPassword fehlt oder ist kein SHA-256");
		}

		usersCredentialsService.setUsersCredentialsPassword(UUID.fromString(usersGuidText), newPassword);
	}

}
