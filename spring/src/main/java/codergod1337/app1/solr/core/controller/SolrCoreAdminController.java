package codergod1337.app1.solr.core.controller;

import codergod1337.app1.solr.core.model.SolrCore;
import codergod1337.app1.solr.core.service.SolrCoreService;
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
 * Admin legt Solr-Kerne an und pflegt sie. Gelesen wird über die Stammdaten.
 *
 * Keine Admin-Prüfung hier: Der Pfad /api/rest/v1/admin/** und @PreAuthorize im Service sichern ab.
 */
@RestController
@RequestMapping("/api/rest/v1/admin/solr/core")
public class SolrCoreAdminController {

	private final SolrCoreService solrCoreService;

	public SolrCoreAdminController(SolrCoreService solrCoreService) {
		this.solrCoreService = solrCoreService;
	}

	/** Alle Felder. Den key prüft der Service. */
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public SolrCore createSolrCore(@Valid @RequestBody SolrCore newSolrCoreData) {
		return solrCoreService.createSolrCore(newSolrCoreData.getKey(), newSolrCoreData.getDisplayName(),
				newSolrCoreData.getLook(), newSolrCoreData.getListingPosition());
	}

	/** Alle Felder. Der key im Body bestimmt, welcher Kern geändert wird, er selbst ändert sich nie. */
	@PutMapping
	public SolrCore updateSolrCore(@Valid @RequestBody SolrCore changedSolrCoreData) {
		return solrCoreService.updateSolrCore(changedSolrCoreData.getKey(), changedSolrCoreData.getDisplayName(),
				changedSolrCoreData.getLook(), changedSolrCoreData.getListingPosition());
	}

	/**
	 * Neue Positionen, z. B. nach Drag and Drop: jeder key bekommt seine Position, {"dms": 1, "artikel": 2}. Das
	 * Frontend zählt durch. Zurück kommen alle Kerne in der neuen Reihenfolge.
	 */
	@PostMapping("/position")
	public List<SolrCore> changeSolrCorePositions(@RequestBody Map<String, Object> newPositionByKey) {
		Map<String, Integer> checkedPositionByKey = new LinkedHashMap<>();
		for (Map.Entry<String, Object> newPosition : newPositionByKey.entrySet()) {
			// 1. gibt es den Kern zu diesem key?
			if (!SolrCoreService.isValidSolrCoreKey(newPosition.getKey())
					|| solrCoreService.isSolrCoreKeyAvailable(newPosition.getKey())) {
				throw new ResponseStatusException(HttpStatus.NOT_FOUND, "kein Kern mit key " + newPosition.getKey());
			}
			// 2. ist die Position eine ganze Zahl?
			if (!(newPosition.getValue() instanceof Integer position)) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
						"Position von " + newPosition.getKey() + " ist keine ganze Zahl");
			}
			checkedPositionByKey.put(newPosition.getKey(), position);
		}
		// 3. erst dann im Service ändern
		return solrCoreService.changeSolrCorePositions(checkedPositionByKey);
	}

	/** Nur per key. 200 mit dem gelöschten Kern, 400 bei ungültigem key, 404 wenn es ihn nicht gibt. */
	@DeleteMapping
	public SolrCore deleteSolrCore(@RequestBody Map<String, Object> solrCoreToDelete) {
		String key = solrCoreToDelete.get("key") instanceof String keyValue ? keyValue : null;
		if (!SolrCoreService.isValidSolrCoreKey(key)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "key fehlt oder ist ungültig");
		}
		return solrCoreService.deleteSolrCore(key);
	}

}
