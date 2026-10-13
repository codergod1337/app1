package codergod1337.app1.system.user.controller;

import codergod1337.app1.system.HelperInputs;
import codergod1337.app1.system.security.CurrentUsersProvider;
import codergod1337.app1.system.user.model.Users;
import codergod1337.app1.system.user.service.UsersService;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Der User-Weg zu {@link Users}: jeder nur bei sich selbst. Alles, was der Admin darf, steht im
 * {@link UsersAdminController}. Löschen gehört dazu: Ein User kann sein Konto nicht selbst löschen, auch nicht weich
 * (Entschieden 2026-10-07).
 */
@RestController
@RequestMapping("/api/rest/v1/users")
public class UsersController {

	private final UsersService usersService;
	private final CurrentUsersProvider currentUsersProvider;

	public UsersController(UsersService usersService, CurrentUsersProvider currentUsersProvider) {
		this.usersService = usersService;
		this.currentUsersProvider = currentUsersProvider;
	}

	/**
	 * Nur email, username, vorname, nachname. guid vergibt der Konstruktor, createdAt ist jetzt, kein serviceAccount,
	 * Status ACTIVE.
	 */
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public Users createUsers(@RequestBody Map<String, Object> newUsersData) {
		String email = newUsersData.get("email") instanceof String emailValue ? emailValue : null;
		if (email == null || email.isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "email fehlt");
		}
		String username = newUsersData.get("username") instanceof String usernameValue ? usernameValue : null;
		String vorname = newUsersData.get("vorname") instanceof String vornameValue ? vornameValue : null;
		String nachname = newUsersData.get("nachname") instanceof String nachnameValue ? nachnameValue : null;

		return usersService.createUsers(null, email, username, vorname, nachname, OffsetDateTime.now(), false, null);
	}

	/** Der User ändert seine Namen, was fehlt, wird geleert. email, serviceAccount und status bleiben unverändert. */
	@PutMapping
	public Users updateUsers(@RequestBody Map<String, Object> updatedUsersData) {
		String guidText = updatedUsersData.get("guid") instanceof String guidValue ? guidValue : null;
		if (!HelperInputs.isValidGuid(guidText)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "guid fehlt oder ist keine gültige guid");
		}
		UUID guid = UUID.fromString(guidText);

		if (!guid.equals(currentUsersProvider.getCurrentUsersGuid())) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "nur der eigene User darf geändert werden");
		}

		String username = updatedUsersData.get("username") instanceof String usernameValue ? usernameValue : null;
		String vorname = updatedUsersData.get("vorname") instanceof String vornameValue ? vornameValue : null;
		String nachname = updatedUsersData.get("nachname") instanceof String nachnameValue ? nachnameValue : null;

		return usersService.updateUsers(guid, null, username, vorname, nachname, null, null);
	}

	/**
	 * Die Vervollständigung beim Freigeben: {"text": "ma"} → höchstens zehn User, deren E-Mail den Text enthält, ab zwei
	 * Zeichen. POST, weil freier Suchtext nie in eine GET-URL gehört. Jeder angemeldete Nutzer darf das.
	 */
	@PostMapping("/suggest")
	public List<Users> suggestUsers(@RequestBody Map<String, Object> suggestRequest) {
		return usersService.suggestUsers(suggestRequest.get("text") instanceof String text ? text : "");
	}

	/** Ein User. Jeder angemeldete Nutzer darf das, Users enthält nur öffentliche Daten. 200, 400 oder 404. */
	@GetMapping("/{guid}")
	public Users getUsers(@PathVariable("guid") String guidText) {
		if (!HelperInputs.isValidGuid(guidText)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "guid ist keine gültige guid");
		}
		return usersService.getUsersByGuid(UUID.fromString(guidText));
	}

}
