package codergod1337.app1.system.access.controller;

import codergod1337.app1.system.HelperInputs;
import codergod1337.app1.system.access.model.AccessRoleCollectionUsersAssignment;
import codergod1337.app1.system.access.service.AccessRoleCollectionUsersAssignmentService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Admin weist Usern ihre ARC (Position) zu, höchstens eine pro User.
 *
 * Keine Admin-Prüfung hier: Der Pfad /api/rest/v1/admin/** sichert ab.
 */
@RestController
@RequestMapping("/api/rest/v1/admin/accessrolecollectionusersassignment")
public class AccessRoleCollectionUsersAssignmentAdminController {

	private final AccessRoleCollectionUsersAssignmentService accessRoleCollectionUsersAssignmentService;

	public AccessRoleCollectionUsersAssignmentAdminController(
			AccessRoleCollectionUsersAssignmentService accessRoleCollectionUsersAssignmentService) {
		this.accessRoleCollectionUsersAssignmentService = accessRoleCollectionUsersAssignmentService;
	}

	/** Alle Zuordnungen, für die Matrix. */
	@GetMapping
	public List<AccessRoleCollectionUsersAssignment> getAllAccessRoleCollectionUsersAssignments() {
		return accessRoleCollectionUsersAssignmentService.getAllAccessRoleCollectionUsersAssignments();
	}

	/**
	 * Setzt die ARC eines Users, z. B. {"usersGuid": "…", "accessRoleCollectionKey": "ARC_LAGER"}. Eine vorhandene wird
	 * ersetzt, null entzieht sie. 200 mit der neuen Zuordnung (ohne ARC leer), 400 bei ungültiger Eingabe oder
	 * unbekannter ARC, 404 wenn es den User nicht gibt.
	 */
	@PutMapping
	public AccessRoleCollectionUsersAssignment changeAccessRoleCollectionUsersAssignment(
			@RequestBody Map<String, Object> changedAccessRoleCollectionUsersAssignmentData) {
		String usersGuidText = changedAccessRoleCollectionUsersAssignmentData.get("usersGuid") instanceof String usersGuidValue
				? usersGuidValue
				: null;
		if (!HelperInputs.isValidGuid(usersGuidText)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "usersGuid fehlt oder ist keine gültige guid");
		}

		Object accessRoleCollectionKeyValue = changedAccessRoleCollectionUsersAssignmentData.get("accessRoleCollectionKey");
		if (accessRoleCollectionKeyValue != null
				&& !(accessRoleCollectionKeyValue instanceof String accessRoleCollectionKeyText
						&& HelperInputs.isValidKey(accessRoleCollectionKeyText))) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "accessRoleCollectionKey ist kein gültiger key");
		}

		return accessRoleCollectionUsersAssignmentService.changeAccessRoleCollectionUsersAssignment(
				UUID.fromString(usersGuidText), (String) accessRoleCollectionKeyValue);
	}

}
