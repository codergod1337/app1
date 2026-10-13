package codergod1337.app1.abas.service;

import codergod1337.app1.abas.model.AbasInfosys;
import codergod1337.app1.abas.repository.AbasInfosysRepository;
import codergod1337.app1.file.filesubclass.service.FileSubClassDestinationService;
import codergod1337.app1.system.HelperInputs;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Die hinterlegten Infosystem-Aufrufe (User 2026-10-10): anlegen, ändern, löschen, und aus der Vorlage mit den
 * Eingaben je Aufruf die Aktionen bauen, die der Fetch-Service an ABAS schickt. Spricht nie selbst mit ABAS.
 */
@Service
public class AbasInfosysService {

	private static final Logger log = LoggerFactory.getLogger(AbasInfosysService.class);

	/** Ein Platzhalter in der Vorlage: {{kartikel}}, der Name wie ein Feldname */
	private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{([A-Za-z0-9_]+)}}");

	private static final TypeReference<List<Map<String, Object>>> ACTION_LIST = new TypeReference<>() {
	};

	private final AbasInfosysRepository abasInfosysRepository;
	private final FileSubClassDestinationService fileSubClassDestinationService;
	private final ObjectMapper objectMapper;

	public AbasInfosysService(AbasInfosysRepository abasInfosysRepository,
			FileSubClassDestinationService fileSubClassDestinationService, ObjectMapper objectMapper) {
		this.abasInfosysRepository = abasInfosysRepository;
		this.fileSubClassDestinationService = fileSubClassDestinationService;
		this.objectMapper = objectMapper;
	}

	/** Alle, nach Position und Key; für die Masken und den Export, ohne @PreAuthorize wie die übrigen Stammdaten */
	public List<AbasInfosys> getAllAbasInfosys() {
		return abasInfosysRepository.findAllByOrderByListingPositionAscKeyAsc();
	}

	/** Eines per Key, 404 ohne */
	public AbasInfosys getAbasInfosysByKey(String key) {
		return abasInfosysRepository.findById(key)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "kein Infosystem mit key " + key));
	}

	/** Die Aufrufe mit diesem Anzeigeort, nach Position und Key; z. B. die Tabs der Artikelmappe (ARTIKELMAPPE) */
	public List<AbasInfosys> getAbasInfosysListByDisplayContextKey(String displayContextKey) {
		return abasInfosysRepository.findAllByOrderByListingPositionAscKeyAsc().stream()
				.filter(abasInfosys -> abasInfosys.isShownIn(displayContextKey)).toList();
	}

	@PreAuthorize("hasRole('ADMIN')")
	public boolean isAbasInfosysKeyAvailable(String key) {
		return !abasInfosysRepository.existsById(key);
	}

	/** 400 bei ungültigem key, Gruppe, Name, Vorlage oder Anzeigeort, 409 wenn der key vergeben ist */
	@PreAuthorize("hasRole('ADMIN')")
	@Transactional
	public AbasInfosys createAbasInfosys(String key, String displayName, String infosysGroup, String infosysName,
			String requestBodyTemplate, int listingPosition, List<String> displayContextKeys) {
		if (!HelperInputs.isValidKey(key)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "key ist ungültig");
		}
		if (abasInfosysRepository.existsById(key)) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "key ist bereits vergeben");
		}
		AbasInfosys saved = abasInfosysRepository.save(new AbasInfosys(key, displayName, checkedPart(infosysGroup, "Gruppe"),
				checkedPart(infosysName, "Name"), checkedTemplate(requestBodyTemplate), listingPosition,
				checkedDisplayContextKeys(displayContextKeys)));
		log.info("Infosystem {} angelegt: {}/{}", key, saved.getInfosysGroup(), saved.getInfosysName());
		return saved;
	}

	/** Der key bestimmt nur, welches geändert wird, er selbst ändert sich nie. Alle anderen Felder werden ersetzt. */
	@PreAuthorize("hasRole('ADMIN')")
	@Transactional
	public AbasInfosys updateAbasInfosys(String key, String displayName, String infosysGroup, String infosysName,
			String requestBodyTemplate, int listingPosition, List<String> displayContextKeys) {
		AbasInfosys abasInfosys = getAbasInfosysByKey(key);
		abasInfosys.setDisplayName(displayName);
		abasInfosys.setInfosysGroup(checkedPart(infosysGroup, "Gruppe"));
		abasInfosys.setInfosysName(checkedPart(infosysName, "Name"));
		abasInfosys.setRequestBodyTemplate(checkedTemplate(requestBodyTemplate));
		abasInfosys.setListingPosition(listingPosition);
		abasInfosys.setDisplayContextKeys(checkedDisplayContextKeys(displayContextKeys));
		return abasInfosysRepository.save(abasInfosys);
	}

	/** Die Anzeigeorte: jeder ein angelegter Anzeigeort (FileSubClassDestination, 400 sonst), ohne Doppelte, null heißt keine; wie bei der Dateiart */
	private List<String> checkedDisplayContextKeys(List<String> displayContextKeys) {
		if (displayContextKeys == null) {
			return List.of();
		}
		Set<String> checked = new LinkedHashSet<>();
		for (String displayContextKey : displayContextKeys) {
			if (!fileSubClassDestinationService.isFileSubClassDestinationKeyKnown(displayContextKey)) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
						"Anzeigeort " + displayContextKey + " ist nicht angelegt");
			}
			checked.add(displayContextKey);
		}
		return new ArrayList<>(checked);
	}

	/** Löscht per key, 404 ohne. Ob ein Mapping-Eintrag darauf zeigt, prüft niemand: Der meldet beim Lauf, dass der key fehlt. */
	@PreAuthorize("hasRole('ADMIN')")
	@Transactional
	public AbasInfosys deleteAbasInfosys(String key) {
		AbasInfosys abasInfosys = getAbasInfosysByKey(key);
		abasInfosysRepository.delete(abasInfosys);
		log.info("Infosystem {} gelöscht", key);
		return abasInfosys;
	}

	// ===== Die Vorlage =====

	/** Die Namen der Platzhalter einer Vorlage in Reihenfolge, ohne Doppelte: das, was ein Aufruf eingeben muss */
	public static List<String> placeholderNamesOf(String requestBodyTemplate) {
		Set<String> names = new LinkedHashSet<>();
		Matcher matcher = PLACEHOLDER.matcher(requestBodyTemplate == null ? "" : requestBodyTemplate);
		while (matcher.find()) {
			names.add(matcher.group(1));
		}
		return new ArrayList<>(names);
	}

	/**
	 * Die Aktionen eines Aufrufs: die Vorlage, jeder Platzhalter durch seinen Wert ersetzt. Ein Platzhalter ohne Wert ist
	 * 400 mit seinem Namen. Ersetzt wird im JSON-Baum, nie im Text, damit ein Wert mit Anführungszeichen oder Backslash
	 * den Body nicht zerreißt. Ein Wert, der ein ganzer Platzhalter ist, bleibt ein Text.
	 */
	public List<Map<String, Object>> actionsOf(AbasInfosys abasInfosys, Map<String, String> valueByPlaceholderName) {
		JsonNode body = parsedTemplate(abasInfosys.getRequestBodyTemplate());
		ArrayNode actions = (ArrayNode) body.get("actions");
		for (JsonNode action : actions) {
			ObjectNode object = (ObjectNode) action;
			List<String> fieldNames = new ArrayList<>();
			object.propertyNames().forEach(fieldNames::add);
			for (String fieldName : fieldNames) {
				JsonNode value = object.get(fieldName);
				if (value.isString()) {
					object.put(fieldName, filled(value.asString(), valueByPlaceholderName));
				}
			}
		}
		return objectMapper.convertValue(actions, ACTION_LIST);
	}

	private static String filled(String text, Map<String, String> valueByPlaceholderName) {
		Matcher matcher = PLACEHOLDER.matcher(text);
		StringBuilder result = new StringBuilder();
		while (matcher.find()) {
			String value = valueByPlaceholderName.get(matcher.group(1));
			if (value == null) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Eingabe " + matcher.group(1) + " fehlt");
			}
			matcher.appendReplacement(result, Matcher.quoteReplacement(value));
		}
		matcher.appendTail(result);
		return result.toString();
	}

	private static String checkedPart(String part, String label) {
		if (part == null || part.isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, label + " fehlt");
		}
		return part.trim();
	}

	/** Die Vorlage muss ein JSON-Objekt mit einer Liste actions aus Objekten sein, jede Aktion mit fieldName */
	private String checkedTemplate(String requestBodyTemplate) {
		JsonNode body = parsedTemplate(requestBodyTemplate);
		for (JsonNode action : body.get("actions")) {
			if (!(action.get("fieldName") instanceof JsonNode fieldName) || !fieldName.isString()
					|| fieldName.asString().isBlank()) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "jede Aktion der Vorlage braucht fieldName");
			}
		}
		return requestBodyTemplate.trim();
	}

	private JsonNode parsedTemplate(String requestBodyTemplate) {
		if (requestBodyTemplate == null || requestBodyTemplate.isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vorlage fehlt");
		}
		JsonNode body;
		try {
			body = objectMapper.readTree(requestBodyTemplate);
		} catch (JacksonException e) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vorlage ist kein JSON: " + e.getOriginalMessage());
		}
		if (!body.isObject() || !(body.get("actions") instanceof JsonNode actions) || !actions.isArray()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vorlage braucht ein Objekt mit der Liste actions");
		}
		for (JsonNode action : actions) {
			if (!action.isObject()) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "jede Aktion der Vorlage ist ein Objekt");
			}
		}
		return body;
	}

}
