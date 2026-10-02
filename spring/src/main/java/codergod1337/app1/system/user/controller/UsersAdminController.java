package codergod1337.app1.system.user.controller;

import codergod1337.app1.system.HelperInputs;
import codergod1337.app1.system.user.model.Users;
import codergod1337.app1.system.user.service.UsersService;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Admin legt User an und verwaltet sie: jedes Feld, auch serviceAccount, und die guid.
 *
 * Keine Admin-Prüfung hier: Ab Schritt 4 sichert der Pfad /api/rest/v1/admin/** ab.
 */
@RestController
@RequestMapping("/api/rest/v1/admin/users")
public class UsersAdminController {

	private final UsersService usersService;

	public UsersAdminController(UsersService usersService) {
		this.usersService = usersService;
	}

	/**
	 * Jedes Feld. guid und createdAt dürfen mitkommen, fehlen sie, werden sie vergeben. Fehlt serviceAccount, gilt
	 * false, fehlt status, gilt ACTIVE.
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

		Object serviceAccountValue = newUsersData.get("serviceAccount");
		if (serviceAccountValue != null && !(serviceAccountValue instanceof Boolean)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "serviceAccount ist kein Wahrheitswert");
		}

		UUID guid = null;
		if (newUsersData.containsKey("guid")) {
			String guidText = newUsersData.get("guid") instanceof String guidValue ? guidValue : null;
			if (!HelperInputs.isValidGuid(guidText)) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "guid ist keine gültige guid");
			}
			guid = UUID.fromString(guidText);
		}
		String createdAtText = newUsersData.get("createdAt") instanceof String createdAtValue ? createdAtValue : null;

		String status = newUsersData.get("status") instanceof String statusValue ? statusValue : null;
		if (newUsersData.containsKey("status") && !HelperInputs.isValidKey(status)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "status ist kein gültiger Key");
		}

		return usersService.createUsers(guid, email, username, vorname, nachname,
				createdAtText != null ? OffsetDateTime.parse(createdAtText) : null, Boolean.TRUE.equals(serviceAccountValue),
				status);
	}

	/**
	 * Vollständiges Objekt: Was fehlt, wird geleert, serviceAccount wird dann false. Fehlt status, bleibt er
	 * unverändert. guid und createdAt ändern sich nie.
	 */
	@PutMapping
	public Users updateUsers(@RequestBody Map<String, Object> updatedUsersData) {
		String guidText = updatedUsersData.get("guid") instanceof String guidValue ? guidValue : null;
		if (!HelperInputs.isValidGuid(guidText)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "guid fehlt oder ist keine gültige guid");
		}
		String email = updatedUsersData.get("email") instanceof String emailValue ? emailValue : null;
		if (email == null || email.isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "email fehlt");
		}
		String username = updatedUsersData.get("username") instanceof String usernameValue ? usernameValue : null;
		String vorname = updatedUsersData.get("vorname") instanceof String vornameValue ? vornameValue : null;
		String nachname = updatedUsersData.get("nachname") instanceof String nachnameValue ? nachnameValue : null;

		Object serviceAccountValue = updatedUsersData.get("serviceAccount");
		if (serviceAccountValue != null && !(serviceAccountValue instanceof Boolean)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "serviceAccount ist kein Wahrheitswert");
		}

		String status = updatedUsersData.get("status") instanceof String statusValue ? statusValue : null;
		if (updatedUsersData.containsKey("status") && !HelperInputs.isValidKey(status)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "status ist kein gültiger Key");
		}

		return usersService.updateUsers(UUID.fromString(guidText), email, username, vorname, nachname,
				Boolean.TRUE.equals(serviceAccountValue), status);
	}

	/** Ändert die guid eines Users, Zugangsdaten und Einstellungen ziehen mit um. */
	@PatchMapping
	public Users changeUsersGuid(@RequestBody Map<String, Object> changedUsersGuids) {
		String guidCurrentText = changedUsersGuids.get("guidCurrent") instanceof String guidCurrentValue ? guidCurrentValue : null;
		if (!HelperInputs.isValidGuid(guidCurrentText)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "guidCurrent fehlt oder ist keine gültige guid");
		}
		String guidNewText = changedUsersGuids.get("guidNew") instanceof String guidNewValue ? guidNewValue : null;
		if (!HelperInputs.isValidGuid(guidNewText)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "guidNew fehlt oder ist keine gültige guid");
		}

		return usersService.changeUsersGuid(UUID.fromString(guidCurrentText), UUID.fromString(guidNewText));
	}

	/** Alle User. */
	@GetMapping
	public List<Users> getAllUsers() {
		return usersService.getAllUsers();
	}

	/** 200 mit dem gelöschten User, 400 bei ungültiger guid, 404 wenn es ihn nicht gibt. */
	@DeleteMapping
	public Users deleteUsers(@RequestBody Map<String, Object> usersToDelete) {
		String guidText = usersToDelete.get("guid") instanceof String guidValue ? guidValue : null;
		if (!HelperInputs.isValidGuid(guidText)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "guid fehlt oder ist keine gültige guid");
		}
		return usersService.deleteUsers(UUID.fromString(guidText));
	}

}
