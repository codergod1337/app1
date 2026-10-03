package codergod1337.app1.solr.hook.controller;

import codergod1337.app1.solr.SolrFieldName;
import codergod1337.app1.solr.hook.model.SolrHook;
import codergod1337.app1.solr.hook.service.SolrHookService;
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
 * Admin legt Hooks an und pflegt sie. Gelesen wird über die Stammdaten.
 *
 * Keine Admin-Prüfung hier: Der Pfad /api/rest/v1/admin/** und @PreAuthorize im Service sichern ab.
 */
@RestController
@RequestMapping("/api/rest/v1/admin/solr/hook")
public class SolrHookAdminController {

	private final SolrHookService solrHookService;

	public SolrHookAdminController(SolrHookService solrHookService) {
		this.solrHookService = solrHookService;
	}

	/** Alle Felder. Den key prüft der Service. */
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public SolrHook createSolrHook(@Valid @RequestBody SolrHook newSolrHookData) {
		return solrHookService.createSolrHook(newSolrHookData.getKey(), newSolrHookData.getDisplayName(),
				newSolrHookData.getDescription(), newSolrHookData.getHookGroupKey(), newSolrHookData.getListingPosition());
	}

	/** Alle Felder. Der key im Body bestimmt, welcher Hook geändert wird, er selbst ändert sich nie. */
	@PutMapping
	public SolrHook updateSolrHook(@Valid @RequestBody SolrHook changedSolrHookData) {
		return solrHookService.updateSolrHook(changedSolrHookData.getKey(), changedSolrHookData.getDisplayName(),
				changedSolrHookData.getDescription(), changedSolrHookData.getHookGroupKey(),
				changedSolrHookData.getListingPosition());
	}

	/**
	 * Neue Positionen, z. B. nach Drag and Drop: jeder key bekommt seine Position, {"artikelNummer": 1, "kundenNummer": 2}.
	 * Das Frontend zählt durch. Zurück kommen alle Hooks in der neuen Reihenfolge.
	 */
	@PostMapping("/position")
	public List<SolrHook> changeSolrHookPositions(@RequestBody Map<String, Object> newPositionByKey) {
		Map<String, Integer> checkedPositionByKey = new LinkedHashMap<>();
		for (Map.Entry<String, Object> newPosition : newPositionByKey.entrySet()) {
			// 1. gibt es den Hook zu diesem key?
			if (!SolrFieldName.isValid(newPosition.getKey())
					|| solrHookService.isSolrHookKeyAvailable(newPosition.getKey())) {
				throw new ResponseStatusException(HttpStatus.NOT_FOUND, "kein Hook mit key " + newPosition.getKey());
			}
			// 2. ist die Position eine ganze Zahl?
			if (!(newPosition.getValue() instanceof Integer position)) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
						"Position von " + newPosition.getKey() + " ist keine ganze Zahl");
			}
			checkedPositionByKey.put(newPosition.getKey(), position);
		}
		// 3. erst dann im Service ändern
		return solrHookService.changeSolrHookPositions(checkedPositionByKey);
	}

	/** Nur per key. 200 mit dem gelöschten Hook, 400 bei ungültigem key, 404 wenn es ihn nicht gibt. */
	@DeleteMapping
	public SolrHook deleteSolrHook(@RequestBody Map<String, Object> solrHookToDelete) {
		String key = solrHookToDelete.get("key") instanceof String keyValue ? keyValue : null;
		if (!SolrFieldName.isValid(key)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "key fehlt oder ist ungültig");
		}
		return solrHookService.deleteSolrHook(key);
	}

}
