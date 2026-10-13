package codergod1337.app1.abas.service;

import codergod1337.app1.abas.model.AbasBelegnummerLookupResult;
import codergod1337.app1.abas.model.AbasInfosys;
import codergod1337.app1.mapping.model.ResponseMappingExecutionResult;
import codergod1337.app1.mapping.model.ResponseMappingWithLines;
import codergod1337.app1.mapping.service.ResponseMappingExecutionService;
import codergod1337.app1.mapping.service.ResponseMappingService;
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
 * Die Belegnummer (User 2026-10-11, nach der Bezugsnummer der Vorlage): jemand tippt eine Nummer ein, ohne zu wissen,
 * was sie ist, und ABAS sagt es. Zwei Schritte: der hinterlegte Aufruf BELEG2ID (ow1, {@code ybeleg} und Startknopf)
 * liefert in der ersten Tabellenzeile im Feld {@code yid} die Sternzeit des Objekts, gelesen über das Mapping
 * BELEGNUMMER (Key BELEGNUMMER_STERNZEIT); dann wird das Objekt geholt, allein wegen {@code meta.objectType}, denn
 * die Endziffer des Typs ist nicht ratbar (2:1, 71:7, 4:5). Dazu der Key unseres Mappings zu diesem Typ als Klartext
 * und, wenn das Mapping eine PRIMARY-id vergibt, diese id, beim Artikel die Artikelnummer. Jeder Angemeldete darf das,
 * die Tür ist der Pfad.
 */
@Service
public class AbasBelegnummerService {

	private static final Logger log = LoggerFactory.getLogger(AbasBelegnummerService.class);

	/** Der hinterlegte Aufruf, der die Nummer kennt, und sein Platzhalter */
	public static final String BELEGNUMMER_ABAS_INFOSYS_KEY = "BELEG2ID";
	public static final String BELEGNUMMER_PLACEHOLDER_NAME = "ybeleg";

	/** Der Key des Eintrags im Mapping zu infosys:data:ow1:BELEG2ID, der die Sternzeiten der Tabellenzeilen liefert */
	public static final String STERNZEIT_RESPONSE_MAPPING_LINE_KEY = "BELEGNUMMER_STERNZEIT";

	/** Eine Sternzeit, wie ABAS sie schreibt: (5168,4,0) */
	private static final Pattern STERNZEIT = Pattern.compile("\\(\\d+,\\d+,\\d+\\)");

	private final AbasInfosysService abasInfosysService;
	private final AbasInfosysFetchService abasInfosysFetchService;
	private final AbasSternzeitResponseFetchService abasSternzeitResponseFetchService;
	private final ResponseMappingExecutionService responseMappingExecutionService;
	private final ResponseMappingService responseMappingService;

	public AbasBelegnummerService(AbasInfosysService abasInfosysService, AbasInfosysFetchService abasInfosysFetchService,
			AbasSternzeitResponseFetchService abasSternzeitResponseFetchService,
			ResponseMappingExecutionService responseMappingExecutionService,
			ResponseMappingService responseMappingService) {
		this.abasInfosysService = abasInfosysService;
		this.abasInfosysFetchService = abasInfosysFetchService;
		this.abasSternzeitResponseFetchService = abasSternzeitResponseFetchService;
		this.responseMappingExecutionService = responseMappingExecutionService;
		this.responseMappingService = responseMappingService;
	}

	/**
	 * @param belegnummer die eingetippte Nummer; leer ist 400
	 * @return gefunden oder nicht; 404 ohne den Aufruf BELEG2ID oder ohne Mapping zu seiner Antwort, 503 wenn ABAS
	 *         nicht antwortet. Lehnt ABAS den Wert ab (400, z. B. eine Nummer, die das Feld nicht fasst), gilt die
	 *         Nummer als unbekannt, wie in der Vorlage.
	 */
	public AbasBelegnummerLookupResult lookupBelegnummer(String belegnummer) {
		String nummer = belegnummer == null ? "" : belegnummer.trim();
		if (nummer.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "belegnummer fehlt");
		}
		AbasInfosys abasInfosys = abasInfosysService.getAbasInfosysByKey(BELEGNUMMER_ABAS_INFOSYS_KEY);
		JsonNode response;
		try {
			response = abasInfosysFetchService.fetchAbasResponseByAbasInfosys(abasInfosys,
					Map.of(BELEGNUMMER_PLACEHOLDER_NAME, nummer));
		} catch (ResponseStatusException e) {
			if (e.getStatusCode() == HttpStatus.BAD_REQUEST) {
				log.info("Belegnummer {}: ABAS nimmt den Wert nicht an, gilt als unbekannt", nummer);
				return new AbasBelegnummerLookupResult(nummer, false, null, null, null, null, null);
			}
			throw e;
		}
		String sternzeit = sternzeitOf(responseMappingExecutionService.executeResponseMapping(response));
		if (sternzeit == null) {
			log.info("Belegnummer {}: BELEG2ID kennt sie nicht", nummer);
			return new AbasBelegnummerLookupResult(nummer, false, null, null, null, null, null);
		}

		JsonNode object = abasSternzeitResponseFetchService.fetchAbasResponseBySternzeit(sternzeit, List.of(),
				List.of());
		JsonNode objectTypeNode = object.path("content").path("data").path("meta").path("objectType");
		String objectType = objectTypeNode.isString() && !objectTypeNode.asString().isBlank()
				? objectTypeNode.asString()
				: null;
		String responseMappingKey = null;
		String responseMappingTargetKey = null;
		String primaryId = null;
		if (objectType != null) {
			List<ResponseMappingWithLines> mappings = responseMappingService.getResponseMappingsByObjectType(objectType);
			if (!mappings.isEmpty()) {
				responseMappingKey = mappings.get(0).responseMapping().getKey();
				responseMappingTargetKey = mappings.get(0).responseMapping().getTargetKey();
				primaryId = primaryIdOf(object, sternzeit);
			}
		}
		log.info("Belegnummer {}: Sternzeit {}, Typ {}, Mapping {}, id {}", nummer, sternzeit, objectType,
				responseMappingKey, primaryId);
		return new AbasBelegnummerLookupResult(nummer, true, sternzeit, objectType, responseMappingKey,
				responseMappingTargetKey, primaryId);
	}

	/** Der erste Wert des Eintrags BELEGNUMMER_STERNZEIT, der wie eine Sternzeit aussieht; BELEG2ID liefert genau eine Zeile */
	private static String sternzeitOf(ResponseMappingExecutionResult result) {
		List<Object> values = result.valuesByKey().get(STERNZEIT_RESPONSE_MAPPING_LINE_KEY);
		if (values == null) {
			return null;
		}
		for (Object value : values) {
			String text = value == null ? "" : String.valueOf(value).trim();
			if (STERNZEIT.matcher(text).matches()) {
				return text;
			}
		}
		return null;
	}

	/** Die PRIMARY-id, die das Mapping des Objekts vergibt; null, wenn der Lauf scheitert, denn die Nummer ist dann trotzdem gefunden */
	private String primaryIdOf(JsonNode object, String sternzeit) {
		try {
			ResponseMappingExecutionResult result = responseMappingExecutionService.executeResponseMapping(object);
			if (result.primaryIdKey() == null) {
				return null;
			}
			List<Object> values = result.valuesByKey().get(result.primaryIdKey());
			return values == null || values.isEmpty() || values.get(0) == null ? null
					: String.valueOf(values.get(0)).trim();
		} catch (RuntimeException e) {
			log.warn("Belegnummer: Mapping zu {} nicht gelaufen: {}", sternzeit, e.getMessage());
			return null;
		}
	}

}
