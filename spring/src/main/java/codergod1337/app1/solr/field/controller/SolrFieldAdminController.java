package codergod1337.app1.solr.field.controller;

import codergod1337.app1.solr.field.model.SolrField;
import codergod1337.app1.solr.field.service.SolrFieldService;
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
 * Admin legt die Felder der Solr-Kerne an und pflegt sie, Zeile für Zeile aus der Tabelle des Kern-Tabs. Gelesen wird
 * über die Stammdaten.
 *
 * Keine Admin-Prüfung hier: Der Pfad /api/rest/v1/admin/** und @PreAuthorize im Service sichern ab.
 */
@RestController
@RequestMapping("/api/rest/v1/admin/solr/field")
public class SolrFieldAdminController {

	private final SolrFieldService solrFieldService;

	public SolrFieldAdminController(SolrFieldService solrFieldService) {
		this.solrFieldService = solrFieldService;
	}

	/** Alle Felder außer id. Kern und name prüft der Service. */
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public SolrField createSolrField(@Valid @RequestBody SolrField newSolrFieldData) {
		return solrFieldService.createSolrField(newSolrFieldData.getCoreKey(), newSolrFieldData.getName(),
				newSolrFieldData.getType(), newSolrFieldData.isIndexed(), newSolrFieldData.isStored(),
				newSolrFieldData.isMultiValued(), newSolrFieldData.isDocValues(), newSolrFieldData.isRequired(),
				newSolrFieldData.isSuggest(), newSolrFieldData.getDescription(), newSolrFieldData.getListingPosition());
	}

	/** Die id im Body bestimmt, welches Feld geändert wird. Kern und name ändern sich nie. */
	@PutMapping
	public SolrField updateSolrField(@Valid @RequestBody SolrField changedSolrFieldData) {
		return solrFieldService.updateSolrField(changedSolrFieldData.getId(), changedSolrFieldData.getType(),
				changedSolrFieldData.isIndexed(), changedSolrFieldData.isStored(), changedSolrFieldData.isMultiValued(),
				changedSolrFieldData.isDocValues(), changedSolrFieldData.isRequired(), changedSolrFieldData.isSuggest(),
				changedSolrFieldData.getDescription(), changedSolrFieldData.getListingPosition());
	}

	/**
	 * Neue Positionen, z. B. nach Drag and Drop: jede id bekommt ihre Position, {"7": 1, "9": 2}. Das Frontend zählt
	 * durch. Zurück kommen alle Felder in der neuen Reihenfolge.
	 */
	@PostMapping("/position")
	public List<SolrField> changeSolrFieldPositions(@RequestBody Map<String, Object> newPositionById) {
		Map<Long, Integer> checkedPositionById = new LinkedHashMap<>();
		for (Map.Entry<String, Object> newPosition : newPositionById.entrySet()) {
			// 1. gibt es das Feld zu dieser id?
			Long id = readId(newPosition.getKey());
			if (id == null || !solrFieldService.isSolrFieldIdKnown(id)) {
				throw new ResponseStatusException(HttpStatus.NOT_FOUND, "kein Feld mit id " + newPosition.getKey());
			}
			// 2. ist die Position eine ganze Zahl?
			if (!(newPosition.getValue() instanceof Integer position)) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
						"Position von " + newPosition.getKey() + " ist keine ganze Zahl");
			}
			checkedPositionById.put(id, position);
		}
		// 3. erst dann im Service ändern
		return solrFieldService.changeSolrFieldPositions(checkedPositionById);
	}

	/** Nur per id. 200 mit dem gelöschten Feld, 400 bei ungültiger id, 404 wenn es das Feld nicht gibt, 409 beim Feld id. */
	@DeleteMapping
	public SolrField deleteSolrField(@RequestBody Map<String, Object> solrFieldToDelete) {
		Long id = readId(solrFieldToDelete.get("id"));
		if (id == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "id fehlt oder ist keine ganze Zahl");
		}
		return solrFieldService.deleteSolrField(id);
	}

	/** Eine id aus JSON: als Zahl oder als Text (Map-Schlüssel sind immer Text). null, wenn es keine ist. */
	private static Long readId(Object value) {
		if (value instanceof Number number) {
			return number.longValue();
		}
		if (value instanceof String text) {
			try {
				return Long.parseLong(text.trim());
			} catch (NumberFormatException e) {
				return null;
			}
		}
		return null;
	}

}
