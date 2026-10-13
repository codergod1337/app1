package codergod1337.app1.abas.service;

import codergod1337.app1.abas.model.AbasInfosys;
import codergod1337.app1.abas.model.AbasSternzeitMappingPreview;
import codergod1337.app1.mapping.model.ResponseMappingExecutionResult;
import codergod1337.app1.mapping.model.ResponseMappingLine;
import codergod1337.app1.mapping.model.ResponseMappingLineKind;
import codergod1337.app1.mapping.model.ResponseMappingRowSubfetch;
import codergod1337.app1.mapping.model.ResponseMappingSubfetchInputParameter;
import codergod1337.app1.mapping.service.ResponseMappingExecutionService;
import codergod1337.app1.mapping.service.ResponseMappingService;
import codergod1337.app1.solr.artikel.service.SolrDocumentArtikelService;
import codergod1337.app1.solr.kontakte.service.SolrDocumentKontakteService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;

/**
 * Der Weg von der Sternzeit ins Dokument (User 2026-10-10): holen, das Mapping laufen lassen, jeden Subfetch-Eintrag
 * holen und mappen und seine Werte dazulegen, das Dokument des Ziels bauen, auf Wunsch schreiben. Liegt im abas-Paket,
 * damit das Mapping ABAS nicht kennt und Solr nicht das Mapping. Ziele heute SOLR_ARTIKEL und SOLR_KONTAKTE (seit
 * 2026-10-13); ein Mapping mit anderem Ziel liefert die Werte, aber kein Dokument.
 *
 * Subfetches nach der Fehlerpolitik der Vorlage: Das Hauptmapping muss gelingen, ein Subfetch ohne Wert, ohne Antwort
 * oder ohne Mapping wird zur Warnung mit Key und Grund, das Dokument entsteht trotzdem, mit syncStatus WARN und den
 * Gründen in syncFehler. Eine Unterantwort darf selbst Subfetches haben, bis zu drei Stufen tief. Ein Subfetch je
 * Zeile (User 2026-10-13) wird je Zeile seiner Tabelle geholt, seine Unterantwort hängt in der Zeile; scheitert er,
 * steht Key und Zeile in der Warnung.
 */
@Service
public class AbasSternzeitMappingService {

	private static final Logger log = LoggerFactory.getLogger(AbasSternzeitMappingService.class);

	/** Eine Sternzeit, wie ABAS sie schreibt: (608127,2,0) */
	private static final Pattern STERNZEIT = Pattern.compile("\\(\\d+,\\d+,\\d+\\)");

	/** Tiefer verschachtelt holt niemand: Artikel, Unterobjekt, dessen Unterobjekt */
	private static final int MAX_SUBFETCH_DEPTH = 3;

	/** Die Ziele mit Schreibweg in einen Kern: Nur Typen mit einem Mapping auf eins davon mappt der Worker */
	private static final Set<String> CORE_TARGET_KEYS = Set.of(SolrDocumentArtikelService.TARGET_KEY,
			SolrDocumentKontakteService.TARGET_KEY);

	private final AbasSternzeitResponseFetchService abasSternzeitResponseFetchService;
	private final AbasInfosysFetchService abasInfosysFetchService;
	private final AbasInfosysService abasInfosysService;
	private final ResponseMappingService responseMappingService;
	private final ResponseMappingExecutionService responseMappingExecutionService;
	private final SolrDocumentArtikelService solrDocumentArtikelService;
	private final SolrDocumentKontakteService solrDocumentKontakteService;

	public AbasSternzeitMappingService(AbasSternzeitResponseFetchService abasSternzeitResponseFetchService,
			AbasInfosysFetchService abasInfosysFetchService, AbasInfosysService abasInfosysService,
			ResponseMappingService responseMappingService,
			ResponseMappingExecutionService responseMappingExecutionService,
			SolrDocumentArtikelService solrDocumentArtikelService,
			SolrDocumentKontakteService solrDocumentKontakteService) {
		this.abasSternzeitResponseFetchService = abasSternzeitResponseFetchService;
		this.abasInfosysFetchService = abasInfosysFetchService;
		this.abasInfosysService = abasInfosysService;
		this.responseMappingService = responseMappingService;
		this.responseMappingExecutionService = responseMappingExecutionService;
		this.solrDocumentArtikelService = solrDocumentArtikelService;
		this.solrDocumentKontakteService = solrDocumentKontakteService;
	}

	/** Holen, mappen, Dokument bauen, nichts schreiben */
	@PreAuthorize("hasRole('ADMIN')")
	public AbasSternzeitMappingPreview previewAbasSternzeitMapping(String sternzeit) {
		ResponseMappingExecutionResult result = execute(sternzeit);
		return new AbasSternzeitMappingPreview(result, documentOf(result), false);
	}

	/** Holen, mappen, Dokument bauen und in den Kern seines Ziels schreiben */
	@PreAuthorize("hasRole('ADMIN')")
	public AbasSternzeitMappingPreview writeAbasSternzeitMapping(String sternzeit) {
		ResponseMappingExecutionResult result = execute(sternzeit);
		Map<String, Object> document = documentOf(result);
		if (document != null) {
			writeDocumentToTargetCore(result.targetKey(), document);
		}
		return new AbasSternzeitMappingPreview(result, document, document != null);
	}

	/**
	 * Der Weg des Workers nach dem Holen (User 2026-10-13): die schon geholte Antwort mappen, ihre Subfetches holen und
	 * das Dokument in den Kern des Ziels schreiben, ohne die Sternzeit ein zweites Mal zu holen. Der Worker ruft das als
	 * System-User.
	 *
	 * Die Queue holt jeden Typ, den ABAS meldet. Hat der Typ kein Mapping mit einem Kern als Ziel, passiert still nichts,
	 * auch kein Subfetch: leer zurück. Hat er eins, trifft aber keins seiner Mappings die Antwort, wirft die Ausführung
	 * 404, der Eintrag der Queue bekommt den Fehler. Ein gewähltes Mapping ohne Ziel liefert eine Vorschau ohne Dokument.
	 */
	@PreAuthorize("hasRole('ADMIN')")
	public Optional<AbasSternzeitMappingPreview> writeAbasSternzeitMappingFromResponse(JsonNode response) {
		JsonNode objectTypeNode = response.path("content").path("data").path("meta").path("objectType");
		if (objectTypeNode.isMissingNode() || objectTypeNode.isNull() || objectTypeNode.asString().isBlank()) {
			return Optional.empty();
		}
		boolean hasMappingWithCoreTarget = responseMappingService
				.getResponseMappingsByObjectType(objectTypeNode.asString()).stream()
				.anyMatch(mapping -> CORE_TARGET_KEYS.contains(mapping.responseMapping().getTargetKey()));
		if (!hasMappingWithCoreTarget) {
			return Optional.empty();
		}
		ResponseMappingExecutionResult result = executeWithSubfetches(response);
		Map<String, Object> document = documentOf(result);
		if (document != null) {
			writeDocumentToTargetCore(result.targetKey(), document);
		}
		return Optional.of(new AbasSternzeitMappingPreview(result, document, document != null));
	}

	/** Der Schreibweg des Ziels: Kern KONTAKTE oder Kern ARTIKEL, je über den Schreibservice des Kerns */
	private void writeDocumentToTargetCore(String targetKey, Map<String, Object> document) {
		if (SolrDocumentKontakteService.TARGET_KEY.equals(targetKey)) {
			solrDocumentKontakteService.writeKontakteDocument(document);
		} else {
			solrDocumentArtikelService.writeArtikelDocument(document);
		}
	}

	private ResponseMappingExecutionResult execute(String sternzeit) {
		// alle Kopffelder: Welche das Mapping braucht, ließe sich aus seinen Einträgen ableiten, später
		JsonNode response = abasSternzeitResponseFetchService.fetchAbasResponseBySternzeit(sternzeit, List.of(),
				List.of());
		return executeWithSubfetches(response);
	}

	/**
	 * Eine Antwort über das Mapping ihres Typs zerlegen und alle Subfetches holen, für den Kopf und je Zeile. Der Weg
	 * für jeden, der eine Antwort samt Unterobjekten braucht, auch das Nachladen der Artikelmappe (User 2026-10-13).
	 */
	public ResponseMappingExecutionResult executeWithSubfetches(JsonNode response) {
		return withSubfetches(responseMappingExecutionService.executeResponseMapping(response), response, 1);
	}

	/**
	 * Jeden Subfetch-Eintrag des Ergebnisses holen, mit dem Mapping seines Typs zerlegen und die Werte zu denen des
	 * Ergebnisses legen; ein Key, der schon einen Wert hat, bleibt. Danach jeden Subfetch je Zeile holen und seine Werte
	 * in die Zeile hängen. Was scheitert, wird eine Warnung, nie ein Abbruch.
	 */
	private ResponseMappingExecutionResult withSubfetches(ResponseMappingExecutionResult result, JsonNode response,
			int depth) {
		if (result.subfetchLines().isEmpty() && result.rowSubfetches().isEmpty()) {
			return result;
		}
		Map<String, List<Object>> valuesByKey = new LinkedHashMap<>(result.valuesByKey());
		List<String> warnings = new ArrayList<>(result.warnings());
		for (ResponseMappingLine line : result.subfetchLines()) {
			if (depth > MAX_SUBFETCH_DEPTH) {
				warnings.add(line.getKey() + ": zu tief verschachtelt, nicht geholt");
				continue;
			}
			try {
				JsonNode subResponse = fetchSubfetch(line, response);
				if (subResponse == null) {
					// die Eingabe ist in dieser Antwort leer, z. B. ein Artikel ohne Hersteller: kein Subfetch, keine Warnung
					continue;
				}
				ResponseMappingExecutionResult subResult = withSubfetches(
						responseMappingExecutionService.executeResponseMapping(subResponse), subResponse, depth + 1);
				for (Map.Entry<String, List<Object>> values : subResult.valuesByKey().entrySet()) {
					valuesByKey.putIfAbsent(values.getKey(), values.getValue());
				}
				warnings.addAll(subResult.warnings());
			} catch (ResponseStatusException e) {
				warnings.add(line.getKey() + ": " + e.getReason());
				log.warn("Subfetch {} nicht geholt: {}", line.getKey(), e.getReason());
			} catch (RuntimeException e) {
				warnings.add(line.getKey() + ": " + e.getMessage());
				log.warn("Subfetch {} gescheitert", line.getKey(), e);
			}
		}
		for (ResponseMappingRowSubfetch rowSubfetch : result.rowSubfetches()) {
			String where = rowSubfetch.line().getKey() + ", Zeile " + (rowSubfetch.rowIndex() + 1);
			if (depth > MAX_SUBFETCH_DEPTH) {
				warnings.add(where + ": zu tief verschachtelt, nicht geholt");
				continue;
			}
			try {
				JsonNode subResponse = fetchWithInputValues(rowSubfetch.line(), rowSubfetch.inputValues());
				ResponseMappingExecutionResult subResult = withSubfetches(
						responseMappingExecutionService.executeResponseMapping(subResponse), subResponse, depth + 1);
				responseMappingExecutionService.attachRowSubfetchResult(valuesByKey, rowSubfetch, subResult);
				warnings.addAll(subResult.warnings());
			} catch (ResponseStatusException e) {
				warnings.add(where + ": " + e.getReason());
				log.warn("Subfetch {} nicht geholt: {}", where, e.getReason());
			} catch (RuntimeException e) {
				warnings.add(where + ": " + e.getMessage());
				log.warn("Subfetch {} gescheitert", where, e);
			}
		}
		return new ResponseMappingExecutionResult(result.responseMappingKey(), result.responseObjectType(),
				result.targetKey(), result.primaryIdKey(), valuesByKey, List.of(), List.of(), warnings);
	}

	/** Die Anfrage eines Subfetch-Eintrags gegen die Antwort, in der er steht, und ihre Antwort; null, wenn eine Eingabe leer ist */
	private JsonNode fetchSubfetch(ResponseMappingLine line, JsonNode response) {
		Map<String, String> inputValues = responseMappingExecutionService.resolveSubfetchInputValues(line, response);
		if (inputValues == null) {
			return null;
		}
		return fetchWithInputValues(line, inputValues);
	}

	/** Die Anfrage eines Subfetch-Eintrags mit schon aufgelösten Eingaben: Sternzeit oder hinterlegter Aufruf */
	private JsonNode fetchWithInputValues(ResponseMappingLine line, Map<String, String> inputValues) {
		if (line.getKind() == ResponseMappingLineKind.SUBFETCH_STERNZEIT) {
			String sternzeit = inputValues.get(ResponseMappingSubfetchInputParameter.STERNZEIT_PARAMETER_NAME);
			if (sternzeit == null || !STERNZEIT.matcher(sternzeit.trim()).matches()) {
				throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST,
						"keine Sternzeit: " + sternzeit);
			}
			return abasSternzeitResponseFetchService.fetchAbasResponseBySternzeit(sternzeit.trim(), List.of(), List.of());
		}
		// der hinterlegte Aufruf mit den Eingaben als Werte seiner Platzhalter: der einzige Weg zu einem Infosystem
		AbasInfosys abasInfosys = abasInfosysService.getAbasInfosysByKey(line.getAbasInfosysKey());
		Map<String, String> placeholderValues = new LinkedHashMap<>();
		for (Map.Entry<String, String> inputValue : inputValues.entrySet()) {
			if (inputValue.getValue() != null) {
				placeholderValues.put(inputValue.getKey(), inputValue.getValue());
			}
		}
		return abasInfosysFetchService.fetchAbasResponseByAbasInfosys(abasInfosys, placeholderValues);
	}

	/** Das Dokument des Ziels: der Kern ARTIKEL oder der Kern KONTAKTE, sonst null, z. B. bei einer Antwort nur für Subfetches */
	private Map<String, Object> documentOf(ResponseMappingExecutionResult result) {
		if (SolrDocumentArtikelService.TARGET_KEY.equals(result.targetKey())) {
			return solrDocumentArtikelService.buildArtikelDocumentFromMappedValues(result);
		}
		if (SolrDocumentKontakteService.TARGET_KEY.equals(result.targetKey())) {
			return solrDocumentKontakteService.buildKontakteDocumentFromMappedValues(result);
		}
		return null;
	}

}
