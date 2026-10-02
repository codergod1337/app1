package codergod1337.app1.system.access.controller;

import codergod1337.app1.system.HelperInputs;
import codergod1337.app1.system.access.model.AccessRoleUsersAssignment;
import codergod1337.app1.system.access.service.AccessRoleUsersAssignmentService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Admin weist Usern AR zu. Nur der Admin: Damit entstehen Rechte im System überhaupt. Was ein User selbst hat,
 * erfährt er später aus seinem Token.
 *
 * Keine Admin-Prüfung hier: Ab Schritt 4 sichert der Pfad /api/rest/v1/admin/** ab.
 */
@RestController
@RequestMapping("/api/rest/v1/admin/accessroleusersassignment")
public class AccessRoleUsersAssignmentAdminController {

	private final AccessRoleUsersAssignmentService accessRoleUsersAssignmentService;

	public AccessRoleUsersAssignmentAdminController(AccessRoleUsersAssignmentService accessRoleUsersAssignmentService) {
		this.accessRoleUsersAssignmentService = accessRoleUsersAssignmentService;
	}

	/** Alle Zuordnungen, für die Userliste. */
	@GetMapping
	public List<AccessRoleUsersAssignment> getAllAccessRoleUsersAssignments() {
		return accessRoleUsersAssignmentService.getAllAccessRoleUsersAssignments();
	}

	/** Die AR-Keys eines Users. 200 oder 400 bei ungültiger guid. Ein User ohne AR ergibt eine leere Liste. */
	@GetMapping("/{usersGuid}")
	public List<String> getAccessRoleKeysByUsersGuid(@PathVariable("usersGuid") String usersGuidText) {
		if (!HelperInputs.isValidGuid(usersGuidText)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "usersGuid ist keine gültige guid");
		}
		return accessRoleUsersAssignmentService.getAccessRoleKeysByUsersGuid(UUID.fromString(usersGuidText));
	}

	/**
	 * Setzt die AR eines Users auf genau diese Liste, z. B. {"usersGuid": "…", "accessRoleKeys": ["ADMIN"]}.
	 * Eine leere Liste entzieht alle. 200 mit den neuen Keys, 400 bei ungültiger Eingabe oder unbekanntem key, 404 wenn
	 * es den User nicht gibt.
	 */
	@PutMapping
	public List<String> changeAccessRoleUsersAssignments(
			@RequestBody Map<String, Object> changedAccessRoleUsersAssignmentsData) {
		String usersGuidText = changedAccessRoleUsersAssignmentsData.get("usersGuid") instanceof String usersGuidValue
				? usersGuidValue
				: null;
		if (!HelperInputs.isValidGuid(usersGuidText)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "usersGuid fehlt oder ist keine gültige guid");
		}
		if (!(changedAccessRoleUsersAssignmentsData.get("accessRoleKeys") instanceof List<?> accessRoleKeysValue)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "accessRoleKeys fehlt oder ist keine Liste");
		}
		List<String> accessRoleKeys = new ArrayList<>();
		for (Object accessRoleKeyValue : accessRoleKeysValue) {
			if (!(accessRoleKeyValue instanceof String accessRoleKey) || !HelperInputs.isValidKey(accessRoleKey)) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "accessRoleKeys enthält einen ungültigen key");
			}
			accessRoleKeys.add(accessRoleKey);
		}

		return accessRoleUsersAssignmentService.changeAccessRoleUsersAssignments(UUID.fromString(usersGuidText),
				accessRoleKeys);
	}

}
