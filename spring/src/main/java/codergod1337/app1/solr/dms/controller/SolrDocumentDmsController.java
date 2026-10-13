package codergod1337.app1.solr.dms.controller;

import codergod1337.app1.solr.dms.model.DmsAccessForClient;
import codergod1337.app1.solr.dms.model.DmsDocumentForClient;
import codergod1337.app1.solr.dms.model.DmsReindexOption;
import codergod1337.app1.solr.dms.model.DmsResolvedDocumentsPage;
import codergod1337.app1.solr.dms.model.DmsSearchedDocumentsPage;
import codergod1337.app1.solr.dms.model.DmsSolrDocumentRevisionForClient;
import codergod1337.app1.solr.dms.service.SolrDocumentDmsService;
import codergod1337.app1.solr.dms.service.SolrDocumentDmsUtilsService;
import codergod1337.app1.system.HelperInputs;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Dokumente des Kerns DMS: Suche, Auflösen, Dokumentseite, Bearbeiten, Löschen, Revisionen, Nachziehen. Der Pfad
 * liegt unter /api/rest/v1/**, also angemeldet; welche ARs ein Aufruf braucht und welche Dokumente und Felder der
 * User sieht, entscheidet der Service. Hier nur die Form des Bodys. Dateien hinein und heraus macht der
 * FileDataController.
 */
@RestController
@RequestMapping("/api/rest/v1/solr/dms")
public class SolrDocumentDmsController {

	private final SolrDocumentDmsService solrDocumentDmsService;
	private final SolrDocumentDmsUtilsService solrDocumentDmsUtilsService;

	public SolrDocumentDmsController(SolrDocumentDmsService solrDocumentDmsService,
			SolrDocumentDmsUtilsService solrDocumentDmsUtilsService) {
		this.solrDocumentDmsService = solrDocumentDmsService;
		this.solrDocumentDmsUtilsService = solrDocumentDmsUtilsService;
	}

	/** Was der User im Kern DMS darf: Vollsicht, Suche, lesbare und auflösbare Felder. Für die Masken, kein Schutz. */
	@GetMapping("/access")
	public DmsAccessForClient access() {
		return solrDocumentDmsUtilsService.accessForClient();
	}

	/** Die Dokumentseite: die Felder, die der User lesen darf, und ob er schreiben darf. 404, wenn er es nicht sieht. */
	@GetMapping("/document/{id}")
	public DmsDocumentForClient getDocument(@PathVariable String id) {
		requireId(id);
		return documentForClient(id);
	}

	/**
	 * Bearbeiten: {"id": "…", "fieldValuesByFieldName": {"fileDescription": "…", "artikelNummer": ["4711"]}}. Nur die
	 * geschickten Felder, je Feld der Endwert, null leert. Felder, die der Client nicht ändern darf, fallen weg.
	 */
	@PatchMapping("/document")
	public DmsDocumentForClient editDocument(@RequestBody Map<String, Object> patch) {
		String id = patch.get("id") instanceof String idValue ? idValue : null;
		requireId(id);
		Map<String, Object> fieldValues = solrDocumentDmsUtilsService.checkClientFieldValues(readObject(patch,
				"fieldValuesByFieldName"), false);
		solrDocumentDmsService.editDmsDocument(id, fieldValues);
		return documentForClient(id);
	}

	/** Weiches Löschen: {"id": "…"}. Status DELETED, die Datei bleibt. */
	@DeleteMapping("/document")
	public void softDeleteDocument(@RequestBody Map<String, Object> body) {
		String id = body.get("id") instanceof String idValue ? idValue : null;
		requireId(id);
		solrDocumentDmsService.softDeleteDmsDocument(id);
	}

	/** Die Revisionen eines Dokuments, neueste zuerst. 404, wenn der User das Dokument nicht sehen darf. */
	@GetMapping("/document/{id}/revisions")
	public List<DmsSolrDocumentRevisionForClient> getRevisions(@PathVariable String id) {
		requireId(id);
		requireReadable(id);
		return solrDocumentDmsUtilsService.getRevisions(id);
	}

	/** Eine Revision als Dokument aus dem ZIP, mit der Feldgrenze des Users wie die Dokumentseite */
	@GetMapping("/document/{id}/revision/{revisionNo}")
	public Map<String, Object> getRevision(@PathVariable String id, @PathVariable long revisionNo) {
		requireId(id);
		requireReadable(id);
		return solrDocumentDmsUtilsService.limitToReadableFields(solrDocumentDmsUtilsService.readRevision(id, revisionNo));
	}

	/** Zurückladen: {"id": "…", "revisionNo": 3}. Das Backend liest sein ZIP und schreibt die Felder zurück, vorher sichert es den heutigen Stand. */
	@PostMapping("/document/revision/restore")
	public DmsDocumentForClient restoreRevision(@RequestBody Map<String, Object> body) {
		String id = body.get("id") instanceof String idValue ? idValue : null;
		requireId(id);
		if (!(body.get("revisionNo") instanceof Number revisionNo)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "revisionNo fehlt oder ist keine Zahl");
		}
		solrDocumentDmsService.restoreRevision(id, revisionNo.longValue());
		return documentForClient(id);
	}

	/** Nachziehen aller Dokumente, nur ADMIN: {"options": ["ACLS_FROM_FILE_SUB_CLASS", "SEARCHABLE_WORDS_FROM_BINARY"]}. Antwort: wie viele geändert. */
	@PostMapping("/reindex")
	public Map<String, Integer> reindex(@RequestBody Map<String, Object> body) {
		Set<DmsReindexOption> options = EnumSet.noneOf(DmsReindexOption.class);
		if (!(body.get("options") instanceof List<?> rawOptions) || rawOptions.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "options fehlt oder ist leer");
		}
		for (Object rawOption : rawOptions) {
			try {
				options.add(DmsReindexOption.valueOf(String.valueOf(rawOption)));
			} catch (IllegalArgumentException e) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Option " + rawOption + " ist unbekannt");
			}
		}
		return Map.of("changed", solrDocumentDmsService.reindexDmsDocuments(options));
	}

	/** Das Dokument mit den Rechten des Users und dem Schreibflag, 404 wenn er es nicht sieht */
	private DmsDocumentForClient documentForClient(String id) {
		Map<String, Object> document = solrDocumentDmsService.getById(id);
		if (document == null) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "kein Dokument mit id " + id);
		}
		return new DmsDocumentForClient(document, solrDocumentDmsUtilsService.isWritableByCurrentUser(document));
	}

	private void requireReadable(String id) {
		if (solrDocumentDmsService.getById(id) == null) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "kein Dokument mit id " + id);
		}
	}

	private static void requireId(String id) {
		if (id == null || !HelperInputs.isValidSha256Hex(id)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "id ist kein SHA-256 in Hex (64 Zeichen, klein)");
		}
	}

	/** Ein JSON-Objekt als Map, fehlt es, eine leere */
	private static Map<String, Object> readObject(Map<String, Object> body, String key) {
		Object raw = body.get(key);
		if (raw == null) {
			return Map.of();
		}
		if (!(raw instanceof Map<?, ?> object)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, key + " ist kein JSON-Objekt");
		}
		Map<String, Object> values = new LinkedHashMap<>();
		for (Map.Entry<?, ?> entry : object.entrySet()) {
			values.put(String.valueOf(entry.getKey()), entry.getValue());
		}
		return values;
	}

	/**
	 * Suche: {"searchText": "Datenblatt 1234", "exactFilterValuesByFieldName": {"fileSubClassKey": ["DATENBLATT"]}}.
	 * Beides optional, eines muss da sein; ein einzelner Text als Filterwert zählt als Liste mit einem Eintrag.
	 */
	@PostMapping("/search")
	public DmsSearchedDocumentsPage searchDmsDocuments(@RequestBody Map<String, Object> searchRequest) {
		String searchText = searchRequest.get("searchText") instanceof String text ? text : "";
		return solrDocumentDmsService.searchDmsDocuments(searchText,
				readValuesByFieldName(searchRequest, "exactFilterValuesByFieldName"));
	}

	/** Auflösen: {"exactValueByFieldName": {"artikelNummer": "4711"}, "page": 0}. page optional, Vorgabe 0. */
	@PostMapping("/resolve")
	public DmsResolvedDocumentsPage resolveDmsDocuments(@RequestBody Map<String, Object> resolveRequest) {
		int page = 0;
		Object rawPage = resolveRequest.get("page");
		if (rawPage != null) {
			if (!(rawPage instanceof Number number)) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "page ist keine Zahl");
			}
			page = number.intValue();
		}
		return solrDocumentDmsService.resolveDmsDocuments(readValueByFieldName(resolveRequest, "exactValueByFieldName"),
				page);
	}

	/** Meine Freigaben: die Dokumente, die die guid des angemeldeten Users in userGrantedAccess tragen, seitenweise ab 0 */
	@GetMapping("/granted-to-me")
	public DmsResolvedDocumentsPage getGrantedToCurrentUser(@RequestParam(defaultValue = "0") int page) {
		return solrDocumentDmsService.getGrantedToCurrentUser(page);
	}

	/** Ein Dokument über seine DMS-id: 404, wenn es keins gibt oder der User es nicht sehen darf. */
	@GetMapping("/id/{id}")
	public Map<String, Object> getById(@PathVariable String id) {
		Map<String, Object> document = solrDocumentDmsService.getById(id);
		if (document == null) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "kein Dokument mit id " + id);
		}
		return document;
	}

	/**
	 * Alle Fassungen einer Versionskette, jüngste zuerst, immer eine Liste: eine ACTIVE, die anderen ARCHIVED; leer, wenn
	 * es die Kette nicht gibt oder der User nichts davon sieht. Die versionChain vergibt das Backend als UUID.
	 */
	@GetMapping("/version-chain/{versionGuid}")
	public List<Map<String, Object>> getByVersionGuid(@PathVariable String versionGuid) {
		if (!HelperInputs.isValidGuid(versionGuid)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "versionGuid ist keine UUID");
		}
		return solrDocumentDmsService.getByVersionGuid(versionGuid);
	}

	/** Ein JSON-Objekt Feldname -> ein Text; fehlt es, gibt es keine Bedingung */
	private static Map<String, String> readValueByFieldName(Map<String, Object> body, String key) {
		Object raw = body.get(key);
		if (raw == null) {
			return Map.of();
		}
		if (!(raw instanceof Map<?, ?> object)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, key + " ist kein JSON-Objekt");
		}
		Map<String, String> valueByFieldName = new LinkedHashMap<>();
		for (Map.Entry<?, ?> entry : object.entrySet()) {
			if (!(entry.getValue() instanceof String value)) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
						key + ": der Wert von " + entry.getKey() + " ist kein Text");
			}
			valueByFieldName.put(String.valueOf(entry.getKey()), value);
		}
		return valueByFieldName;
	}

	/** Ein JSON-Objekt Feldname -> Liste von Texten; ein einzelner Text zählt als Liste mit einem Eintrag */
	private static Map<String, List<String>> readValuesByFieldName(Map<String, Object> body, String key) {
		Object raw = body.get(key);
		if (raw == null) {
			return Map.of();
		}
		if (!(raw instanceof Map<?, ?> object)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, key + " ist kein JSON-Objekt");
		}
		Map<String, List<String>> valuesByFieldName = new LinkedHashMap<>();
		for (Map.Entry<?, ?> entry : object.entrySet()) {
			String fieldName = String.valueOf(entry.getKey());
			if (entry.getValue() instanceof String single) {
				valuesByFieldName.put(fieldName, List.of(single));
				continue;
			}
			if (!(entry.getValue() instanceof List<?> list)) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
						key + ": der Wert von " + fieldName + " ist weder Text noch Liste");
			}
			List<String> values = new ArrayList<>();
			for (Object item : list) {
				if (!(item instanceof String value)) {
					throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
							key + ": die Liste von " + fieldName + " enthält einen Eintrag, der kein Text ist");
				}
				values.add(value);
			}
			valuesByFieldName.put(fieldName, values);
		}
		return valuesByFieldName;
	}

}
