package codergod1337.app1.abas.service;

import codergod1337.app1.abas.model.AbasBelegnummerLookupResult;
import codergod1337.app1.abas.model.AbasInfosys;
import codergod1337.app1.mapping.model.ResponseMappingExecutionResult;
import codergod1337.app1.solr.artikel.service.SolrDocumentArtikelService;
import codergod1337.app1.solr.artikel.service.SolrDocumentArtikelUtilsService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;

/**
 * Das Nachladen der Artikelmappe (User 2026-10-11, nach Fertigung und Prüfungen der Vorlage): Was nicht im Kern steht,
 * weil es sich stündlich ändert (laufende Fertigung, Prüfaufträge) oder zu tief hängt (Prüfeinstellung, Prüfpläne),
 * holt die Mappe beim Öffnen des Tabs live bei ABAS, immer über das Mapping des Antworttyps, kein Code-Mapping. Zwei
 * Wege, beide mit der Tür ARTIKEL: Der Artikel muss für den User im Kern auflösbar sein (dieselbe Prüfung wie die
 * Mappe), und was ABAS geschickt bekommt, kommt allein aus den Feldern, die der User an diesem Artikel lesen darf.
 *
 * Infosystem: ein hinterlegter Aufruf mit dem Anzeigeort ARTIKELMAPPE, jeder Platzhalter seiner Vorlage heißt wie das
 * Feld des Dokuments, aus dem er gefüllt wird ({@code {{artikelNummer}}} aus dem Hook artikelNummer). Sternzeit: der
 * Wert eines Feldes des Dokuments, das eine Sternzeit trägt (yqmappqmpruefv, die Prüfeinstellung 71:2). Beide Wege
 * laufen mit allen Subfetches des Mappings, für den Kopf und je Zeile (User 2026-10-13): Prüfeinstellung → Prüfplan
 * → Merkmale, Prüfaufträge → Prüfauftrag → Prüfergebnis, alles mit einem Klick auf den Tab.
 *
 * Dritter Weg (User 2026-10-13): ein Artikel, den der Kern nicht kennt, wird über die Belegnummer bei ABAS gesucht und,
 * wenn ABAS einen Artikel liefert, über sein Mapping in den Kern geschrieben; die Mappe ruft das selbst, wenn sie 404
 * bekommt. Antworten aus ABAS in den Kern eintragen soll jeder mit der Kern-Rolle dürfen; das Schreiben selbst
 * verlangt heute noch ADMIN, die Rollen kommen später im Detail.
 */
@Service
public class AbasArtikelmappeNachladeService {

	private static final Logger log = LoggerFactory.getLogger(AbasArtikelmappeNachladeService.class);

	/** Der Anzeigeort eines Aufrufs, der ein Tab der Artikelmappe ist; das Frontend hält denselben Key */
	public static final String ARTIKELMAPPE_DISPLAY_CONTEXT_KEY = "ARTIKELMAPPE";

	/** Eine Sternzeit, wie ABAS sie schreibt: (608127,2,0) */
	private static final Pattern STERNZEIT = Pattern.compile("\\(\\d+,\\d+,\\d+\\)");

	private final AbasInfosysService abasInfosysService;
	private final AbasInfosysFetchService abasInfosysFetchService;
	private final AbasSternzeitResponseFetchService abasSternzeitResponseFetchService;
	private final SolrDocumentArtikelService solrDocumentArtikelService;
	private final SolrDocumentArtikelUtilsService solrDocumentArtikelUtilsService;
	private final AbasBelegnummerService abasBelegnummerService;
	private final AbasSternzeitMappingService abasSternzeitMappingService;

	public AbasArtikelmappeNachladeService(AbasInfosysService abasInfosysService,
			AbasInfosysFetchService abasInfosysFetchService,
			AbasSternzeitResponseFetchService abasSternzeitResponseFetchService,
			SolrDocumentArtikelService solrDocumentArtikelService,
			SolrDocumentArtikelUtilsService solrDocumentArtikelUtilsService,
			AbasBelegnummerService abasBelegnummerService, AbasSternzeitMappingService abasSternzeitMappingService) {
		this.abasInfosysService = abasInfosysService;
		this.abasInfosysFetchService = abasInfosysFetchService;
		this.abasSternzeitResponseFetchService = abasSternzeitResponseFetchService;
		this.solrDocumentArtikelService = solrDocumentArtikelService;
		this.solrDocumentArtikelUtilsService = solrDocumentArtikelUtilsService;
		this.abasBelegnummerService = abasBelegnummerService;
		this.abasSternzeitMappingService = abasSternzeitMappingService;
	}

	/**
	 * Einen Artikel, den der Kern nicht kennt, aus ABAS holen (User 2026-10-13): die Nummer als Belegnummer bei ABAS
	 * nachschlagen; liefert ABAS ein Objekt, dessen Mapping auf SOLR_ARTIKEL zielt, wird seine Sternzeit über das
	 * Mapping geholt und in den Kern geschrieben. Tür ARTIKEL; 404, wenn ABAS die Nummer nicht kennt oder sie kein
	 * Artikel ist; 503, wenn ABAS nicht antwortet. Die primaryId der Antwort ist die Nummer, wie sie jetzt im Kern steht.
	 */
	public AbasBelegnummerLookupResult fetchArtikelIntoCoreByArtikelNummer(String artikelNummer) {
		solrDocumentArtikelUtilsService.requireAccessRole();
		AbasBelegnummerLookupResult lookup = abasBelegnummerService.lookupBelegnummer(artikelNummer);
		if (!lookup.found() || lookup.sternzeit() == null
				|| !SolrDocumentArtikelService.TARGET_KEY.equals(lookup.responseMappingTargetKey())) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "ABAS kennt keinen Artikel " + artikelNummer
					+ (lookup.objectType() == null ? "" : " (Typ " + lookup.objectType() + ")"));
		}
		abasSternzeitMappingService.writeAbasSternzeitMapping(lookup.sternzeit());
		log.info("Artikelmappe {}: Artikel {} aus ABAS in den Kern geholt, id {}", artikelNummer, lookup.sternzeit(),
				lookup.primaryId());
		return lookup;
	}

	/** Die Aufrufe mit dem Anzeigeort ARTIKELMAPPE, nach Position und Key: die Tabs, die die Mappe anbietet. Tür ARTIKEL. */
	public List<AbasInfosys> getAbasInfosysListForArtikelmappe() {
		solrDocumentArtikelUtilsService.requireAccessRole();
		return abasInfosysService.getAbasInfosysListByDisplayContextKey(ARTIKELMAPPE_DISPLAY_CONTEXT_KEY);
	}

	/**
	 * Einen hinterlegten Aufruf für einen Artikel ausführen und die Antwort mit dem Mapping ihres Typs zerlegen.
	 *
	 * @param abasInfosysKey der Key des Aufrufs; 404 ohne ihn, 400 ohne den Anzeigeort ARTIKELMAPPE oder wenn ein
	 *                       Platzhalter kein lesbares Feld des Artikels ist
	 * @param artikelNummer  die id im Kern ARTIKEL; 404, wenn es den Artikel nicht gibt oder der User ihn nicht sieht
	 * @return die Werte je Key, die Tabellen-Einträge je Zeile ein JSON-Objekt als Text; 404 ohne Mapping zum Typ
	 */
	public ResponseMappingExecutionResult fetchAndMapAbasInfosysForArtikel(String abasInfosysKey,
			String artikelNummer) {
		Map<String, Object> document = requireArtikelDocument(artikelNummer);
		AbasInfosys abasInfosys = abasInfosysService.getAbasInfosysByKey(abasInfosysKey);
		if (!abasInfosys.isShownIn(ARTIKELMAPPE_DISPLAY_CONTEXT_KEY)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					"der Aufruf " + abasInfosysKey + " hat nicht den Anzeigeort " + ARTIKELMAPPE_DISPLAY_CONTEXT_KEY);
		}
		Map<String, String> valueByPlaceholderName = new LinkedHashMap<>();
		for (String placeholderName : AbasInfosysService.placeholderNamesOf(abasInfosys.getRequestBodyTemplate())) {
			requireReadableArtikelField(placeholderName);
			String value = firstTextOf(document.get(placeholderName));
			if (value == null) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "der Platzhalter " + placeholderName
						+ " ist kein gefülltes Feld des Artikels " + artikelNummer + " oder nicht lesbar");
			}
			valueByPlaceholderName.put(placeholderName, value);
		}
		JsonNode response = abasInfosysFetchService.fetchAbasResponseByAbasInfosys(abasInfosys, valueByPlaceholderName);
		ResponseMappingExecutionResult result = abasSternzeitMappingService.executeWithSubfetches(response);
		log.info("Artikelmappe {}: Aufruf {} nachgeladen, Mapping {}, {} Warnungen", artikelNummer, abasInfosysKey,
				result.responseMappingKey(), result.warnings().size());
		return result;
	}

	/**
	 * Das Objekt hinter einer Sternzeit in einem Feld des Artikels holen und mit dem Mapping seines Typs zerlegen, z. B.
	 * die Prüfeinstellung 71:2 aus yqmappqmpruefv.
	 *
	 * @param artikelNummer die id im Kern ARTIKEL; 404, wenn es den Artikel nicht gibt oder der User ihn nicht sieht
	 * @param fieldName     das Feld des Dokuments; 400, wenn es leer, nicht lesbar oder keine Sternzeit ist
	 */
	public ResponseMappingExecutionResult fetchAndMapAbasSternzeitFromArtikelField(String artikelNummer,
			String fieldName) {
		Map<String, Object> document = requireArtikelDocument(artikelNummer);
		requireReadableArtikelField(fieldName);
		String sternzeit = firstTextOf(document.get(fieldName));
		if (sternzeit == null || !STERNZEIT.matcher(sternzeit.trim()).matches()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					"das Feld " + fieldName + " des Artikels " + artikelNummer + " trägt keine Sternzeit");
		}
		JsonNode response = abasSternzeitResponseFetchService.fetchAbasResponseBySternzeit(sternzeit.trim(), List.of(),
				List.of());
		ResponseMappingExecutionResult result = abasSternzeitMappingService.executeWithSubfetches(response);
		log.info("Artikelmappe {}: {} aus {} nachgeladen, Mapping {}, {} Warnungen", artikelNummer, sternzeit, fieldName,
				result.responseMappingKey(), result.warnings().size());
		return result;
	}

	/**
	 * Das Recht vor dem Laden (User 2026-10-13): Ein Nachladen hängt an einem Feld des Artikels, und wer das Feld nicht
	 * lesen darf, bekommt 403, bevor ABAS gefragt wird. Dieselbe Regel wie beim Auflösen und bei den aufaggregierten
	 * Referenzen; das Frontend blendet die Tabs nach denselben Feldern aus.
	 */
	private void requireReadableArtikelField(String fieldName) {
		if (!solrDocumentArtikelUtilsService.isEveryFieldReadable()
				&& !solrDocumentArtikelUtilsService.readableFieldNamesOfCurrentUser().contains(fieldName)) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN,
					"das Feld " + fieldName + " ist für dich nicht lesbar, kein Nachladen daraus");
		}
	}

	/** Das Dokument des Artikels mit den Feldern, die der User lesen darf; prüft die Tür ARTIKEL, 404 ohne Treffer */
	private Map<String, Object> requireArtikelDocument(String artikelNummer) {
		Map<String, Object> document = solrDocumentArtikelService.getById(artikelNummer);
		if (document == null) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "kein Artikel " + artikelNummer + " im Kern");
		}
		return document;
	}

	/** Der erste Wert eines Feldes als Text, null bei nichts oder leer; Solr liefert multiValued als Liste */
	private static String firstTextOf(Object value) {
		Object first = value instanceof List<?> values ? (values.isEmpty() ? null : values.get(0)) : value;
		if (first == null) {
			return null;
		}
		String text = String.valueOf(first).trim();
		return text.isEmpty() ? null : text;
	}

}
