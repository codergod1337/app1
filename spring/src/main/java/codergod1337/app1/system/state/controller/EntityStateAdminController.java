package codergod1337.app1.system.state.controller;

import codergod1337.app1.system.HelperInputs;
import codergod1337.app1.system.state.model.EntityState;
import codergod1337.app1.system.state.service.EntityStateService;
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
 * Admin legt Status an und pflegt sie. Gelesen wird über die Stammdaten.
 *
 * Keine Admin-Prüfung hier: Der Pfad /api/rest/v1/admin/** und @PreAuthorize im Service sichern ab.
 */
@RestController
@RequestMapping("/api/rest/v1/admin/entitystate")
public class EntityStateAdminController {

	private final EntityStateService entityStateService;

	public EntityStateAdminController(EntityStateService entityStateService) {
		this.entityStateService = entityStateService;
	}

	/** Alle Felder. key und scope prüft der Service. */
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public EntityState createEntityState(@Valid @RequestBody EntityState newEntityStateData) {
		return entityStateService.createEntityState(newEntityStateData.getKey(), newEntityStateData.getDisplayName(),
				newEntityStateData.getScope(), newEntityStateData.getListingPosition());
	}

	/** Alle Felder. Der key im Body bestimmt, welcher Status geändert wird, er selbst ändert sich nie. */
	@PutMapping
	public EntityState updateEntityState(@Valid @RequestBody EntityState changedEntityStateData) {
		return entityStateService.updateEntityState(changedEntityStateData.getKey(),
				changedEntityStateData.getDisplayName(), changedEntityStateData.getScope(),
				changedEntityStateData.getListingPosition());
	}

	/**
	 * Neue Positionen, z. B. nach Drag and Drop: jeder key bekommt seine Position, {"ACTIVE": 1, "ARCHIVED": 2}. Das
	 * Frontend zählt durch. Zurück kommen alle Status in der neuen Reihenfolge.
	 */
	@PostMapping("/position")
	public List<EntityState> changeEntityStatePositions(@RequestBody Map<String, Object> newPositionByKey) {
		Map<String, Integer> checkedPositionByKey = new LinkedHashMap<>();
		for (Map.Entry<String, Object> newPosition : newPositionByKey.entrySet()) {
			// 1. gibt es den Status zu diesem key?
			if (!HelperInputs.isValidKey(newPosition.getKey())
					|| entityStateService.isEntityStateKeyAvailable(newPosition.getKey())) {
				throw new ResponseStatusException(HttpStatus.NOT_FOUND, "kein Status mit key " + newPosition.getKey());
			}
			// 2. ist die Position eine ganze Zahl?
			if (!(newPosition.getValue() instanceof Integer position)) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
						"Position von " + newPosition.getKey() + " ist keine ganze Zahl");
			}
			checkedPositionByKey.put(newPosition.getKey(), position);
		}
		// 3. erst dann im Service ändern
		return entityStateService.changeEntityStatePositions(checkedPositionByKey);
	}

	/** Nur per key. 200 mit dem gelöschten Status, 400 bei ungültigem key, 404 wenn es ihn nicht gibt. */
	@DeleteMapping
	public EntityState deleteEntityState(@RequestBody Map<String, Object> entityStateToDelete) {
		String key = entityStateToDelete.get("key") instanceof String keyValue ? keyValue : null;
		if (!HelperInputs.isValidKey(key)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "key fehlt oder ist ungültig");
		}
		return entityStateService.deleteEntityState(key);
	}

}
