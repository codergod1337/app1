package codergod1337.app1.solr.kontakte.controller;

import codergod1337.app1.solr.kontakte.model.KontakteAccessForClient;
import codergod1337.app1.solr.kontakte.model.KontakteResolvedDocumentsPage;
import codergod1337.app1.solr.kontakte.model.KontakteSearchedDocumentsPage;
import codergod1337.app1.solr.kontakte.service.SolrDocumentKontakteService;
import codergod1337.app1.solr.kontakte.service.SolrDocumentKontakteUtilsService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Kontakte des Kerns KONTAKTE (User 2026-10-13): was der User darf, Auflösen nach exakten Werten und die Suche. Der
 * Pfad liegt unter /api/rest/v1/**, also angemeldet; die Tür KONTAKTE beziehungsweise KONTAKTE_SUCHE und die
 * Feldgrenze prüft der Service, hier nur die Form des Bodys. Eine Sternzeit steht nie in der URL, sie ist eine Bedingung auf id. Kein Schreiben durch einen Menschen: Den
 * Spiegelkern füllt allein das Mapping.
 */
@RestController
@RequestMapping("/api/rest/v1/solr/kontakte")
public class SolrDocumentKontakteController {

	private final SolrDocumentKontakteService solrDocumentKontakteService;
	private final SolrDocumentKontakteUtilsService solrDocumentKontakteUtilsService;

	public SolrDocumentKontakteController(SolrDocumentKontakteService solrDocumentKontakteService,
			SolrDocumentKontakteUtilsService solrDocumentKontakteUtilsService) {
		this.solrDocumentKontakteService = solrDocumentKontakteService;
		this.solrDocumentKontakteUtilsService = solrDocumentKontakteUtilsService;
	}

	/** Was der User im Kern KONTAKTE darf: Vollsicht, Suche, lesbare und auflösbare Felder. Für die Masken, kein Schutz. */
	@GetMapping("/access")
	public KontakteAccessForClient access() {
		solrDocumentKontakteUtilsService.requireAccessRole();
		return solrDocumentKontakteUtilsService.accessForClient();
	}

	/**
	 * Auflösen: {"exactValueByFieldName": {"kundenNummer": "120120", "kontaktorig": "Adresse"}, "page": 0}. page optional,
	 * Vorgabe 0. Ein Feld vom Typ EMAIL trifft ohne Rücksicht auf Groß- und Kleinschreibung.
	 */
	@PostMapping("/resolve")
	public KontakteResolvedDocumentsPage resolveKontakteDocuments(@RequestBody Map<String, Object> resolveRequest) {
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
		return solrDocumentKontakteService.resolveKontakteDocuments(exactValueByFieldName, page);
	}

	/**
	 * Suche: {"searchText": "Vermes", "exactFilterValuesByFieldName": {"kundenNummer": ["120120"]}}. Beides optional,
	 * eines muss da sein; ein einzelner Text als Filterwert zählt als Liste mit einem Eintrag. Derselbe Body wie bei DMS
	 * und ARTIKEL.
	 */
	@PostMapping("/search")
	public KontakteSearchedDocumentsPage searchKontakteDocuments(@RequestBody Map<String, Object> searchRequest) {
		String searchText = searchRequest.get("searchText") instanceof String text ? text : "";
		return solrDocumentKontakteService.searchKontakteDocuments(searchText,
				readValuesByFieldName(searchRequest, "exactFilterValuesByFieldName"));
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
