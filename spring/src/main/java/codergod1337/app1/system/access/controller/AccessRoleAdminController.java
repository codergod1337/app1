package codergod1337.app1.system.access.controller;

import codergod1337.app1.system.HelperInputs;
import codergod1337.app1.system.access.model.AccessRole;
import codergod1337.app1.system.access.service.AccessRoleService;
import jakarta.validation.Valid;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Admin legt AccessRoles an und bearbeitet sie.
 *
 * Keine Admin-Prüfung hier: Ab Schritt 4 sichern der Pfad /api/rest/v1/admin/** und @PreAuthorize im Service ab.
 * Den Key prüft der Service mit HelperInputs.isValidKey.
 */
@RestController
@RequestMapping("/api/rest/v1/admin/accessrole")
public class AccessRoleAdminController {

	private final AccessRoleService accessRoleService;

	public AccessRoleAdminController(AccessRoleService accessRoleService) {
		this.accessRoleService = accessRoleService;
	}

	/** Der Admin darf jedes Feld setzen, deshalb kommt das JSON direkt als AccessRole. Fehlt system, gilt false. */
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public AccessRole createAccessRole(@Valid @RequestBody AccessRole newAccessRoleData) {
		return accessRoleService.createAccessRole(newAccessRoleData.getKey(), newAccessRoleData.getDisplayName(),
				newAccessRoleData.getDescription(), newAccessRoleData.isSystem(),
				newAccessRoleData.getBadgeTextColor(), newAccessRoleData.getBadgeTextShadowColor(),
				newAccessRoleData.getBadgeGradientStart(), newAccessRoleData.getBadgeGradientEnd(),
				newAccessRoleData.getBadgeBorderColor(), newAccessRoleData.getBadgeShadowColor(),
				newAccessRoleData.getBadgeGradient(), newAccessRoleData.getBadgeStyles(), newAccessRoleData.getSymbol(),
				newAccessRoleData.getBadgeOpacity(), newAccessRoleData.getListingPosition());
	}

	/** Alle Felder der Rolle. Der key im Body bestimmt, welche Rolle geändert wird, er selbst ändert sich nie. */
	@PutMapping
	public AccessRole updateAccessRole(@Valid @RequestBody AccessRole changedAccessRoleData) {
		return accessRoleService.updateAccessRole(changedAccessRoleData.getKey(),
				changedAccessRoleData.getDisplayName(), changedAccessRoleData.getDescription(),
				changedAccessRoleData.isSystem(), changedAccessRoleData.getBadgeTextColor(),
				changedAccessRoleData.getBadgeTextShadowColor(), changedAccessRoleData.getBadgeGradientStart(),
				changedAccessRoleData.getBadgeGradientEnd(), changedAccessRoleData.getBadgeBorderColor(),
				changedAccessRoleData.getBadgeShadowColor(), changedAccessRoleData.getBadgeGradient(),
				changedAccessRoleData.getBadgeStyles(), changedAccessRoleData.getSymbol(),
				changedAccessRoleData.getBadgeOpacity(), changedAccessRoleData.getListingPosition());
	}

	/**
	 * Neue Positionen, z. B. nach Drag and Drop: jeder key bekommt seine Position, {"ADMIN": 1, "FILE_READ": 2}.
	 * Das Frontend zählt durch. Zurück kommen alle Rollen in der neuen Reihenfolge.
	 */
	@PostMapping("/position")
	public List<AccessRole> changeAccessRolePositions(@RequestBody Map<String, Object> newPositionByKey) {
		Map<String, Integer> checkedPositionByKey = new LinkedHashMap<>();
		for (Map.Entry<String, Object> newPosition : newPositionByKey.entrySet()) {
			// 1. gibt es die AccessRole zu diesem key?
			if (accessRoleService.isAccessRoleKeyAvailable(newPosition.getKey())) {
				throw new ResponseStatusException(HttpStatus.NOT_FOUND,
						"keine AccessRole mit key " + newPosition.getKey());
			}
			// 2. ist die Position eine ganze Zahl?
			if (!(newPosition.getValue() instanceof Integer position)) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
						"Position von " + newPosition.getKey() + " ist keine ganze Zahl");
			}
			checkedPositionByKey.put(newPosition.getKey(), position);
		}
		// 3. erst dann im Service ändern
		return accessRoleService.changeAccessRolePositions(checkedPositionByKey);
	}

	/** Nur per key. 200 mit der gelöschten Rolle, 400 bei ungültigem key, 404 wenn es sie nicht gibt, 409 bei system. */
	@DeleteMapping
	public AccessRole deleteAccessRole(@RequestBody Map<String, Object> accessRoleToDelete) {
		String key = accessRoleToDelete.get("key") instanceof String keyValue ? keyValue : null;
		if (!HelperInputs.isValidKey(key)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "key fehlt oder ist ungültig");
		}
		return accessRoleService.deleteAccessRole(key);
	}

}
