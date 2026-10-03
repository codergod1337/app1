package codergod1337.app1.solr.hook.controller;

import codergod1337.app1.solr.hook.model.SolrHookGroup;
import codergod1337.app1.solr.hook.service.SolrHookGroupService;
import codergod1337.app1.system.HelperInputs;
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
 * Admin legt Hook-Gruppen an und pflegt sie. Gelesen wird über die Stammdaten.
 *
 * Keine Admin-Prüfung hier: Der Pfad /api/rest/v1/admin/** und @PreAuthorize im Service sichern ab.
 */
@RestController
@RequestMapping("/api/rest/v1/admin/solr/hookgroup")
public class SolrHookGroupAdminController {

	private final SolrHookGroupService solrHookGroupService;

	public SolrHookGroupAdminController(SolrHookGroupService solrHookGroupService) {
		this.solrHookGroupService = solrHookGroupService;
	}

	/** Alle Felder. Den key prüft der Service. */
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public SolrHookGroup createSolrHookGroup(@Valid @RequestBody SolrHookGroup newSolrHookGroupData) {
		return solrHookGroupService.createSolrHookGroup(newSolrHookGroupData.getKey(),
				newSolrHookGroupData.getDisplayName(), newSolrHookGroupData.getListingPosition());
	}

	/** Alle Felder. Der key im Body bestimmt, welche Gruppe geändert wird, er selbst ändert sich nie. */
	@PutMapping
	public SolrHookGroup updateSolrHookGroup(@Valid @RequestBody SolrHookGroup changedSolrHookGroupData) {
		return solrHookGroupService.updateSolrHookGroup(changedSolrHookGroupData.getKey(),
				changedSolrHookGroupData.getDisplayName(), changedSolrHookGroupData.getListingPosition());
	}

	/**
	 * Neue Positionen, z. B. nach Drag and Drop: jeder key bekommt seine Position, {"ARTIKEL": 1, "PARTNER": 2}. Das
	 * Frontend zählt durch. Zurück kommen alle Gruppen in der neuen Reihenfolge.
	 */
	@PostMapping("/position")
	public List<SolrHookGroup> changeSolrHookGroupPositions(@RequestBody Map<String, Object> newPositionByKey) {
		Map<String, Integer> checkedPositionByKey = new LinkedHashMap<>();
		for (Map.Entry<String, Object> newPosition : newPositionByKey.entrySet()) {
			// 1. gibt es die Gruppe zu diesem key?
			if (!HelperInputs.isValidKey(newPosition.getKey())
					|| solrHookGroupService.isSolrHookGroupKeyAvailable(newPosition.getKey())) {
				throw new ResponseStatusException(HttpStatus.NOT_FOUND,
						"keine Hook-Gruppe mit key " + newPosition.getKey());
			}
			// 2. ist die Position eine ganze Zahl?
			if (!(newPosition.getValue() instanceof Integer position)) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
						"Position von " + newPosition.getKey() + " ist keine ganze Zahl");
			}
			checkedPositionByKey.put(newPosition.getKey(), position);
		}
		// 3. erst dann im Service ändern
		return solrHookGroupService.changeSolrHookGroupPositions(checkedPositionByKey);
	}

	/** Nur per key. 200 mit der gelöschten Gruppe, 400 bei ungültigem key, 404 wenn es sie nicht gibt, 409 mit Hooks darin. */
	@DeleteMapping
	public SolrHookGroup deleteSolrHookGroup(@RequestBody Map<String, Object> solrHookGroupToDelete) {
		String key = solrHookGroupToDelete.get("key") instanceof String keyValue ? keyValue : null;
		if (!HelperInputs.isValidKey(key)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "key fehlt oder ist ungültig");
		}
		return solrHookGroupService.deleteSolrHookGroup(key);
	}

}
