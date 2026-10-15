package codergod1337.app1.solr.nachrichten.controller;

import codergod1337.app1.solr.nachrichten.model.NachrichtenAccessForClient;
import codergod1337.app1.solr.nachrichten.model.NachrichtenResolvedDocumentsPage;
import codergod1337.app1.solr.nachrichten.model.NachrichtenSearchedDocumentsPage;
import codergod1337.app1.solr.nachrichten.service.SolrDocumentNachrichtenService;
import codergod1337.app1.solr.nachrichten.service.SolrDocumentNachrichtenUtilsService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Nachrichten des Kerns NACHRICHTEN (User 2026-10-14): was der User darf, eine Nachricht, ihr Verlauf, Auflösen nach
 * exakten Werten und die Suche. Der Pfad liegt unter /api/rest/v1/**, also angemeldet; Tür, readAcl und Feldgrenze
 * prüft der Service, hier nur die Form. Die id einer Nachricht ist eine laufende Nummer (Long), sie darf in die URL.
 * Geschrieben wird nur über das Archivieren (MessageArchiveController).
 */
@RestController
@RequestMapping("/api/rest/v1/solr/nachrichten")
public class SolrDocumentNachrichtenController {

	private final SolrDocumentNachrichtenService solrDocumentNachrichtenService;
	private final SolrDocumentNachrichtenUtilsService solrDocumentNachrichtenUtilsService;

	public SolrDocumentNachrichtenController(SolrDocumentNachrichtenService solrDocumentNachrichtenService,
			SolrDocumentNachrichtenUtilsService solrDocumentNachrichtenUtilsService) {
		this.solrDocumentNachrichtenService = solrDocumentNachrichtenService;
		this.solrDocumentNachrichtenUtilsService = solrDocumentNachrichtenUtilsService;
	}

	/** Was der User im Kern NACHRICHTEN darf: Vollsicht, Suche, lesbare und auflösbare Felder. Für die Masken, kein Schutz. */
	@GetMapping("/access")
	public NachrichtenAccessForClient access() {
		solrDocumentNachrichtenUtilsService.requireAccessRole();
		return solrDocumentNachrichtenUtilsService.accessForClient();
	}

	/** Eine Nachricht mit den Feldern, die der User lesen darf. 404, wenn es sie nicht gibt oder er sie nicht sehen darf. */
	@GetMapping("/id/{id}")
	public Map<String, Object> getById(@PathVariable Long id) {
		Map<String, Object> message = solrDocumentNachrichtenService.getById(String.valueOf(id));
		if (message == null) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "keine Nachricht " + id);
		}
		return message;
	}

	/** Der Verlauf der Nachricht, älteste zuerst, sie selbst eingeschlossen. 404 wie bei id. */
	@GetMapping("/conversation/{id}")
	public List<Map<String, Object>> getConversation(@PathVariable Long id) {
		return solrDocumentNachrichtenService.getConversation(String.valueOf(id));
	}

	/**
	 * Auflösen: {"exactValueByFieldName": {"messageSubClassKey": "AUFTRAG"}, "page": 0}. page optional, Vorgabe 0. Ein
	 * Feld vom Typ EMAIL trifft ohne Rücksicht auf Groß- und Kleinschreibung.
	 */
	@PostMapping("/resolve")
	public NachrichtenResolvedDocumentsPage resolveNachrichtenDocuments(@RequestBody Map<String, Object> resolveRequest) {
		int page = 0;
		Object rawPage = resolveRequest.get("page");
		if (rawPage != null) {
			if (!(rawPage instanceof Number number)) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "page ist keine Zahl");
			}
			page = number.intValue();
		}
		Object raw = resolveRequest.get("exactValueByFieldName");
		if (!(raw instanceof Map<?, ?> object)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "exactValueByFieldName fehlt oder ist kein JSON-Objekt");
		}
		Map<String, String> exactValueByFieldName = new LinkedHashMap<>();
		for (Map.Entry<?, ?> entry : object.entrySet()) {
			if (!(entry.getValue() instanceof String value) || value.isBlank()) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
						"exactValueByFieldName: der Wert von " + entry.getKey() + " ist kein Text");
			}
			exactValueByFieldName.put(String.valueOf(entry.getKey()), value);
		}
		return solrDocumentNachrichtenService.resolveNachrichtenDocuments(exactValueByFieldName, page);
	}

	/**
	 * Suche: {"searchText": "Angebot", "exactFilterValuesByFieldName": {"messageSubClassKey": ["AUFTRAG"]}}. Beides
	 * optional, eines muss da sein. Derselbe Body wie bei DMS, ARTIKEL und KONTAKTE.
	 */
	@PostMapping("/search")
	public NachrichtenSearchedDocumentsPage searchNachrichtenDocuments(@RequestBody Map<String, Object> searchRequest) {
		String searchText = searchRequest.get("searchText") instanceof String text ? text : "";
		return solrDocumentNachrichtenService.searchNachrichtenDocuments(searchText,
				readValuesByFieldName(searchRequest, "exactFilterValuesByFieldName"), readSearchPage(searchRequest),
				Boolean.TRUE.equals(searchRequest.get("allWordsRequired")));
	}

	/** Die Seite der Suche, ab 0; fehlt sie, die erste (User 2026-10-14: „weitere laden“ in der Maske) */
	private static int readSearchPage(Map<String, Object> searchRequest) {
		Object rawPage = searchRequest.get("page");
		if (rawPage == null) {
			return 0;
		}
		if (!(rawPage instanceof Number number)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "page ist keine Zahl");
		}
		return number.intValue();
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
