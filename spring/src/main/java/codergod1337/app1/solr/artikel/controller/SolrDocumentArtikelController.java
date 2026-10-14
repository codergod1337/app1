package codergod1337.app1.solr.artikel.controller;

import codergod1337.app1.solr.artikel.model.ArtikelAccessForClient;
import codergod1337.app1.solr.artikel.model.ArtikelAggregatedReferencesForClient;
import codergod1337.app1.solr.artikel.model.ArtikelDocumentForClient;
import codergod1337.app1.solr.artikel.model.ArtikelReferenceFlag;
import codergod1337.app1.solr.artikel.model.ArtikelResolvedDocumentsPage;
import codergod1337.app1.solr.artikel.model.ArtikelSearchedDocumentsPage;
import codergod1337.app1.solr.artikel.service.SolrDocumentArtikelService;
import codergod1337.app1.solr.artikel.service.SolrDocumentArtikelUtilsService;
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
 * Artikel des Kerns ARTIKEL: die Mappe, Auflösen, Suche und die aufaggregierten Referenzen je Flag. Der Pfad liegt
 * unter /api/rest/v1/**, also angemeldet; die Tür ARTIKEL beziehungsweise ARTIKEL_SUCHE und die Feldgrenze des Users
 * prüft der Service. Hier nur die Form des Bodys. Kein Schreiben durch einen Menschen: Den Spiegelkern füllt allein das
 * Mapping, die Ablage der Referenzen schreibt das Backend selbst.
 */
@RestController
@RequestMapping("/api/rest/v1/solr/artikel")
public class SolrDocumentArtikelController {

	/** Eine Artikelnummer: Ziffern, Buchstaben, Punkt, Bindestrich und Unterstrich, wie ABAS sie vergibt */
	private static final String ARTICLE_NUMBER_PATTERN = "[A-Za-z0-9._-]{1,64}";

	private final SolrDocumentArtikelService solrDocumentArtikelService;
	private final SolrDocumentArtikelUtilsService solrDocumentArtikelUtilsService;

	public SolrDocumentArtikelController(SolrDocumentArtikelService solrDocumentArtikelService,
			SolrDocumentArtikelUtilsService solrDocumentArtikelUtilsService) {
		this.solrDocumentArtikelService = solrDocumentArtikelService;
		this.solrDocumentArtikelUtilsService = solrDocumentArtikelUtilsService;
	}

	/** Was der User im Kern ARTIKEL darf: Vollsicht, Suche, lesbare und auflösbare Felder. Für die Masken, kein Schutz. */
	@GetMapping("/access")
	public ArtikelAccessForClient access() {
		solrDocumentArtikelUtilsService.requireAccessRole();
		return solrDocumentArtikelUtilsService.accessForClient();
	}

	/** Die Artikelmappe: das Dokument mit den Feldern, die der User lesen darf, und seine Familie. 404, wenn er es nicht sieht. */
	@GetMapping("/document/{artikelNummer}")
	public ArtikelDocumentForClient getArtikelmappe(@PathVariable String artikelNummer) {
		requireArticleNumber(artikelNummer);
		ArtikelDocumentForClient artikelmappe = solrDocumentArtikelService.getArtikelmappe(artikelNummer);
		if (artikelmappe == null) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "kein Artikel " + artikelNummer + " im Kern");
		}
		return artikelmappe;
	}

	/** Auflösen: {"exactValueByFieldName": {"masterArtikelNummer": "610762"}, "page": 0}. page optional, Vorgabe 0. */
	@PostMapping("/resolve")
	public ArtikelResolvedDocumentsPage resolveArtikelDocuments(@RequestBody Map<String, Object> resolveRequest) {
		int page = 0;
		Object rawPage = resolveRequest.get("page");
		if (rawPage != null) {
			if (!(rawPage instanceof Number number)) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "page ist keine Zahl");
			}
			page = number.intValue();
		}
		return solrDocumentArtikelService.resolveArtikelDocuments(
				readValueByFieldName(resolveRequest, "exactValueByFieldName"), page);
	}

	/**
	 * Suche: {"searchText": "Welle 610762", "exactFilterValuesByFieldName": {"kundenNummer": ["4711"]}}. Beides optional,
	 * eines muss da sein; ein einzelner Text als Filterwert zählt als Liste mit einem Eintrag.
	 */
	@PostMapping("/search")
	public ArtikelSearchedDocumentsPage searchArtikelDocuments(@RequestBody Map<String, Object> searchRequest) {
		String searchText = searchRequest.get("searchText") instanceof String text ? text : "";
		return solrDocumentArtikelService.searchArtikelDocuments(searchText,
				readValuesByFieldName(searchRequest, "exactFilterValuesByFieldName"), readSearchPage(searchRequest));
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

	/**
	 * Die aufaggregierten Referenzen eines Flags: {"artikelNummer": "45503670", "flag": "WERKZEUG", "alternativeDepth": 1}.
	 * alternativeDepth optional, Vorgabe 0 (nur der Artikel selbst). Aus der Ablage im Dokument, wenn sie reicht, sonst
	 * gerechnet und abgelegt.
	 */
	@PostMapping("/references")
	public ArtikelAggregatedReferencesForClient getAggregatedReferences(@RequestBody Map<String, Object> referencesRequest) {
		return solrDocumentArtikelService.getAggregatedReferences(readArticleNumber(referencesRequest),
				readReferenceFlag(referencesRequest), readAlternativeDepth(referencesRequest));
	}

	/** Derselbe Body, immer neu gerechnet und abgelegt: der Knopf „neu suchen“ der Mappe */
	@PostMapping("/references/recompute")
	public ArtikelAggregatedReferencesForClient recomputeAggregatedReferences(
			@RequestBody Map<String, Object> referencesRequest) {
		return solrDocumentArtikelService.recomputeAggregatedReferences(readArticleNumber(referencesRequest),
				readReferenceFlag(referencesRequest), readAlternativeDepth(referencesRequest));
	}

	private static String readArticleNumber(Map<String, Object> body) {
		if (!(body.get("artikelNummer") instanceof String artikelNummer)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "artikelNummer fehlt");
		}
		requireArticleNumber(artikelNummer);
		return artikelNummer;
	}

	private static ArtikelReferenceFlag readReferenceFlag(Map<String, Object> body) {
		if (!(body.get("flag") instanceof String flag)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "flag fehlt");
		}
		try {
			return ArtikelReferenceFlag.valueOf(flag);
		} catch (IllegalArgumentException e) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "flag " + flag + " ist keins von WERKZEUG, GEHTMIT, ALTERNATIVE, BENOETIGT");
		}
	}

	/** Die Tiefe über Alternativen, Vorgabe 0; ob sie unter dem Deckel liegt, prüft der Service */
	private static int readAlternativeDepth(Map<String, Object> body) {
		Object rawDepth = body.get("alternativeDepth");
		if (rawDepth == null) {
			return 0;
		}
		if (!(rawDepth instanceof Number number)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "alternativeDepth ist keine Zahl");
		}
		return number.intValue();
	}

	private static void requireArticleNumber(String artikelNummer) {
		if (artikelNummer == null || !artikelNummer.matches(ARTICLE_NUMBER_PATTERN)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "artikelNummer hat nicht die Form einer Artikelnummer");
		}
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
