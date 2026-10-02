package codergod1337.app1.system.user.controller;

import codergod1337.app1.system.HelperInputs;
import codergod1337.app1.system.security.CurrentUsersProvider;
import codergod1337.app1.system.user.service.UsersCredentialsService;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Der User-Weg zu den Zugangsdaten: jeder nur bei sich selbst. */
@RestController
@RequestMapping("/api/rest/v1/userscredentials")
public class UsersCredentialsController {

	private final UsersCredentialsService usersCredentialsService;
	private final CurrentUsersProvider currentUsersProvider;

	public UsersCredentialsController(UsersCredentialsService usersCredentialsService,
			CurrentUsersProvider currentUsersProvider) {
		this.usersCredentialsService = usersCredentialsService;
		this.currentUsersProvider = currentUsersProvider;
	}

	/**
	 * Der User ändert sein eigenes Passwort, das alte muss stimmen. Beide sind der SHA-256 aus dem Browser.
	 * 200, 400 bei ungültiger Eingabe oder falschem alten Passwort, 403 wenn usersGuid nicht der current user ist.
	 */
	@PutMapping("/password")
	public void changeUsersCredentialsPassword(@RequestBody Map<String, Object> changedUsersPasswordData) {
		String usersGuidText = changedUsersPasswordData.get("usersGuid") instanceof String usersGuidValue ? usersGuidValue : null;
		if (!HelperInputs.isValidGuid(usersGuidText)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "usersGuid fehlt oder ist keine gültige guid");
		}
		UUID usersGuid = UUID.fromString(usersGuidText);

		if (!usersGuid.equals(currentUsersProvider.getCurrentUsersGuid())) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "nur das eigene Passwort darf geändert werden");
		}

		String oldPassword = changedUsersPasswordData.get("oldPassword") instanceof String oldPasswordValue ? oldPasswordValue : null;
		if (!HelperInputs.isValidSha256Hex(oldPassword)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "oldPassword fehlt oder ist kein SHA-256");
		}
		String newPassword = changedUsersPasswordData.get("newPassword") instanceof String newPasswordValue ? newPasswordValue : null;
		if (!HelperInputs.isValidSha256Hex(newPassword)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "newPassword fehlt oder ist kein SHA-256");
		}

		usersCredentialsService.changeUsersCredentialsPassword(usersGuid, oldPassword, newPassword);
	}

}
