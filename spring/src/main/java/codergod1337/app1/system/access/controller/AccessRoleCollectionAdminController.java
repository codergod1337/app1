package codergod1337.app1.system.access.controller;

import codergod1337.app1.system.HelperInputs;
import codergod1337.app1.system.access.model.AccessRoleCollection;
import codergod1337.app1.system.access.service.AccessRoleCollectionService;
import jakarta.validation.Valid;
import java.util.ArrayList;
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
 * Admin legt AccessRoleCollections an und bearbeitet sie.
 *
 * Keine Admin-Prüfung hier: Ab Schritt 4 sichern der Pfad /api/rest/v1/admin/** und @PreAuthorize im Service ab.
 * key (mit Präfix ARC_), AR und Slave-ARCs prüft der Service. Die AR einer ARC setzt nur die ARC-AR-Matrix
 * (PUT /accessroles), Anlegen und Bearbeiten fassen sie nicht an.
 */
@RestController
@RequestMapping("/api/rest/v1/admin/accessrolecollection")
public class AccessRoleCollectionAdminController {

	private final AccessRoleCollectionService accessRoleCollectionService;

	public AccessRoleCollectionAdminController(AccessRoleCollectionService accessRoleCollectionService) {
		this.accessRoleCollectionService = accessRoleCollectionService;
	}

	/**
	 * Der Admin darf jedes Feld setzen, deshalb kommt das JSON direkt als AccessRoleCollection. Außer accessRoleKeys:
	 * Die neue ARC startet ohne AR, die verleiht die ARC-AR-Matrix.
	 */
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public AccessRoleCollection createAccessRoleCollection(@Valid @RequestBody AccessRoleCollection newData) {
		return accessRoleCollectionService.createAccessRoleCollection(newData.getKey(), newData.getDisplayName(),
				newData.getDescription(), newData.getSlaveArcKeys(), newData.getBadgeTextColor(),
				newData.getBadgeTextShadowColor(), newData.getBadgeGradientStart(), newData.getBadgeGradientEnd(),
				newData.getBadgeBorderColor(), newData.getBadgeShadowColor(), newData.getBadgeGradient(),
				newData.getBadgeStyles(), newData.getSymbol(), newData.getBadgeOpacity(), newData.getListingPosition());
	}

	/**
	 * Alle Felder der ARC außer accessRoleKeys, die bleiben unverändert. Der key im Body bestimmt, welche ARC geändert
	 * wird, er selbst ändert sich nie.
	 */
	@PutMapping
	public AccessRoleCollection updateAccessRoleCollection(@Valid @RequestBody AccessRoleCollection changedData) {
		return accessRoleCollectionService.updateAccessRoleCollection(changedData.getKey(),
				changedData.getDisplayName(), changedData.getDescription(), changedData.getSlaveArcKeys(),
				changedData.getBadgeTextColor(), changedData.getBadgeTextShadowColor(),
				changedData.getBadgeGradientStart(), changedData.getBadgeGradientEnd(),
				changedData.getBadgeBorderColor(), changedData.getBadgeShadowColor(), changedData.getBadgeGradient(),
				changedData.getBadgeStyles(), changedData.getSymbol(), changedData.getBadgeOpacity(),
				changedData.getListingPosition());
	}

	/**
	 * Die AR mehrerer ARCs auf einmal, für die ARC-AR-Matrix: [{"key": "ARC_LAGER", "accessRoleKeys": ["MITARBEITER"]},
	 * …], je ARC die vollständige neue Liste. 200 mit allen ARCs, 400 bei ungültiger Eingabe oder unbekannter AR, 404
	 * wenn es eine ARC nicht gibt.
	 */
	@PutMapping("/accessroles")
	public List<AccessRoleCollection> changeAccessRoleCollectionAccessRoleKeys(
			@RequestBody List<Map<String, Object>> changedAccessRoleKeysData) {
		Map<String, List<String>> accessRoleKeysByKey = new LinkedHashMap<>();
		for (Map<String, Object> changedAccessRoleKeys : changedAccessRoleKeysData) {
			String key = changedAccessRoleKeys.get("key") instanceof String keyValue ? keyValue : null;
			if (!HelperInputs.isValidKey(key)) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "key fehlt oder ist ungültig");
			}
			if (!(changedAccessRoleKeys.get("accessRoleKeys") instanceof List<?> accessRoleKeyValues)) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "accessRoleKeys fehlt bei " + key);
			}
			List<String> accessRoleKeys = new ArrayList<>();
			for (Object accessRoleKeyValue : accessRoleKeyValues) {
				if (!(accessRoleKeyValue instanceof String accessRoleKey) || !HelperInputs.isValidKey(accessRoleKey)) {
					throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ungültiger AR-Key bei " + key);
				}
				accessRoleKeys.add(accessRoleKey);
			}
			accessRoleKeysByKey.put(key, accessRoleKeys);
		}
		return accessRoleCollectionService.changeAccessRoleCollectionAccessRoleKeys(accessRoleKeysByKey);
	}

	/**
	 * Neue Positionen, z. B. nach Drag and Drop: jeder key bekommt seine Position, {"ARC_LAGER": 1, "ARC_EINKAUF": 2}.
	 * Das Frontend zählt durch. Zurück kommen alle ARCs in der neuen Reihenfolge.
	 */
	@PostMapping("/position")
	public List<AccessRoleCollection> changeAccessRoleCollectionPositions(
			@RequestBody Map<String, Object> newPositionByKey) {
		Map<String, Integer> checkedPositionByKey = new LinkedHashMap<>();
		for (Map.Entry<String, Object> newPosition : newPositionByKey.entrySet()) {
			// 1. gibt es die ARC zu diesem key?
			if (accessRoleCollectionService.isAccessRoleCollectionKeyAvailable(newPosition.getKey())) {
				throw new ResponseStatusException(HttpStatus.NOT_FOUND, "keine ARC mit key " + newPosition.getKey());
			}
			// 2. ist die Position eine ganze Zahl?
			if (!(newPosition.getValue() instanceof Integer position)) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
						"Position von " + newPosition.getKey() + " ist keine ganze Zahl");
			}
			checkedPositionByKey.put(newPosition.getKey(), position);
		}
		// 3. erst dann im Service ändern
		return accessRoleCollectionService.changeAccessRoleCollectionPositions(checkedPositionByKey);
	}

	/** Nur per key. 200 mit der gelöschten ARC, 400 bei ungültigem key, 404 wenn es sie nicht gibt. */
	@DeleteMapping
	public AccessRoleCollection deleteAccessRoleCollection(@RequestBody Map<String, Object> accessRoleCollectionToDelete) {
		String key = accessRoleCollectionToDelete.get("key") instanceof String keyValue ? keyValue : null;
		if (!HelperInputs.isValidKey(key)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "key fehlt oder ist ungültig");
		}
		return accessRoleCollectionService.deleteAccessRoleCollection(key);
	}

}
