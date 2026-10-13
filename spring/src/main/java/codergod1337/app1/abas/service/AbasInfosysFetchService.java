package codergod1337.app1.abas.service;

import codergod1337.app1.abas.model.AbasInfosys;
import java.net.URI;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Alles zu ABAS-Infosystemen, roh und einmal geparst: einen hinterlegten Aufruf ausführen ({@code POST
 * infosys/data/{gruppe}/{name}} mit den Aktionen aus seiner Vorlage im Body), die Liste aller Infosysteme ({@code GET
 * infosys/data}) und die Felder eines Infosystems ({@code GET infosys/meta/{gruppe}/{name}}). Jede Infosystem-Anfrage
 * geht über einen hinterlegten Aufruf ({@link AbasInfosys}, Modul Infosysteme) mit Werten für seine Platzhalter (User
 * 2026-10-11): Gruppe, Name und Aktionen nennt niemand mehr frei, sie stehen in der Vorlage. Die Antwort eines Laufs
 * trägt in {@code content.data.meta.objectType} den Wert {@code infosys:data:<gruppe>:<name>}. Kein @PreAuthorize, aus
 * demselben Grund wie beim Sternzeit-Service.
 */
@Service
public class AbasInfosysFetchService {

	/** Der Schlüssel, an dem ABAS die Aktionsart liest; er muss als Erstes im Objekt stehen */
	private static final String ACTION_TYPE_KEY = "_type";

	/** Die Aktionsart, die ein Feld füllt oder einen Knopf drückt; gilt, wenn eine Aktion keine nennt */
	private static final String DEFAULT_ACTION_TYPE = "SetFieldValue";

	private final AbasErpRestClientService abasErpRestClientService;
	private final AbasInfosysService abasInfosysService;
	private final ObjectMapper objectMapper;

	public AbasInfosysFetchService(AbasErpRestClientService abasErpRestClientService,
			AbasInfosysService abasInfosysService, ObjectMapper objectMapper) {
		this.abasErpRestClientService = abasErpRestClientService;
		this.abasInfosysService = abasInfosysService;
		this.objectMapper = objectMapper;
	}

	/**
	 * Einen hinterlegten Aufruf ausführen: die Aktionen aus seiner Vorlage, jeder Platzhalter durch seinen Wert ersetzt
	 * ({@link AbasInfosysService#actionsOf}), als Body an {@code POST infosys/data/<gruppe>/<name>}. Der einzige Weg zu
	 * einem Infosystem bei ABAS.
	 *
	 * @param abasInfosys            der hinterlegte Aufruf, Gruppe, Name und Vorlage
	 * @param valueByPlaceholderName je Platzhalter der Vorlage sein Wert; fehlt einer, 400 mit seinem Namen
	 * @return die Antwort; 400 wenn ABAS die Eingabe ablehnt, 404 bei unbekanntem Infosystem, 503 wenn ABAS nicht
	 *         antwortet
	 */
	public JsonNode fetchAbasResponseByAbasInfosys(AbasInfosys abasInfosys,
			Map<String, String> valueByPlaceholderName) {
		return fetchAbasResponseByInfosys(abasInfosys.getInfosysGroup(), abasInfosys.getInfosysName(),
				abasInfosysService.actionsOf(abasInfosys, valueByPlaceholderName == null ? Map.of() : valueByPlaceholderName));
	}

	/**
	 * Alle Infosysteme bei ABAS, rund 500 in knapp 40 Gruppen, als LinkList: {@code content.data.links[]}, je Eintrag
	 * {@code description} als „gruppe/NAME“ und {@code objectType} als {@code infosys:data:gruppe:NAME}. Damit wählt das
	 * Formular eines Aufrufs Gruppe und Name aus, statt sie zu raten (User 2026-10-10).
	 */
	public JsonNode fetchAbasInfosysList() {
		URI uri = abasErpRestClientService.buildUri("infosys", "data").build().toUri();
		return abasErpRestClientService.exchange(HttpMethod.GET, uri, null, "Infosystem-Liste");
	}

	/**
	 * Die Felder eines Infosystems: {@code content.data.fields[]}, je Feld {@code name} (der fieldName einer Aktion),
	 * {@code description}, {@code type.baseType} (BUTTON ist ein Knopf wie {@code bstart}, sonst REFERENCE, DATE,
	 * INTEGER, …) und {@code tableColumn} (true heißt Tabellenspalte, false Kopffeld). 400 ohne Gruppe oder Name, 404
	 * bei unbekanntem Infosystem.
	 */
	public JsonNode fetchAbasInfosysMeta(String infosysGroup, String infosysName) {
		requireGroupAndName(infosysGroup, infosysName);
		URI uri = abasErpRestClientService.buildUri("infosys", "meta", infosysGroup.trim(), infosysName.trim()).build()
				.toUri();
		return abasErpRestClientService.exchange(HttpMethod.GET, uri, null,
				"Infosystem-Meta " + infosysGroup.trim() + "/" + infosysName.trim());
	}

	/**
	 * Der rohe Lauf, nur von hier aus: Gruppe, Name und die Aktionen in dieser Reihenfolge, jede ein freies Objekt wie
	 * ABAS es erwartet (z. B. {@code fieldName} und {@code value}, bei Tabellen {@code rowSpec}); {@code _type} wird als
	 * Erstes geschrieben, fehlt es, gilt SetFieldValue. Eine Aktion, die null ist oder einen leeren Schlüssel trägt, ist
	 * 400 im Klartext, denn die Aktionen kommen aus der Vorlage in Postgres (die Vorlage überspringt stumm).
	 */
	private JsonNode fetchAbasResponseByInfosys(String infosysGroup, String infosysName,
			List<Map<String, Object>> infosysActions) {
		requireGroupAndName(infosysGroup, infosysName);
		String jsonBody = buildActionsBody(infosysActions);
		URI uri = abasErpRestClientService.buildUri("infosys", "data", infosysGroup.trim(), infosysName.trim()).build()
				.toUri();
		return abasErpRestClientService.exchange(HttpMethod.POST, uri, jsonBody,
				"Infosystem " + infosysGroup.trim() + "/" + infosysName.trim());
	}

	private static void requireGroupAndName(String infosysGroup, String infosysName) {
		if (isBlank(infosysGroup) || isBlank(infosysName)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Infosystem braucht Gruppe und Name");
		}
	}

	/**
	 * Der Body: {@code {"actions":[{"_type":"SetFieldValue","fieldName":…,"value":…}, …]}}. {@code _type} muss der
	 * erste Schlüssel jeder Aktion sein, sonst ignoriert ABAS die Aktion stumm und die Antwort ist leer; deshalb wird er
	 * zuerst geschrieben, alle anderen Schlüssel folgen in der Reihenfolge der Eingabe (ObjectNode hält sie).
	 */
	private String buildActionsBody(List<Map<String, Object>> infosysActions) {
		ArrayNode actions = objectMapper.createArrayNode();
		int position = 0;
		for (Map<String, Object> inputAction : infosysActions == null ? List.<Map<String, Object>>of()
				: infosysActions) {
			position++;
			if (inputAction == null) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
						"Aktion " + position + " des Infosystems fehlt");
			}
			ObjectNode action = actions.addObject();
			action.put(ACTION_TYPE_KEY, actionTypeOf(inputAction));
			for (Map.Entry<String, Object> entry : inputAction.entrySet()) {
				String key = entry.getKey();
				if (ACTION_TYPE_KEY.equals(key)) {
					continue;
				}
				if (isBlank(key)) {
					throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
							"Aktion " + position + " des Infosystems hat einen leeren Schlüssel");
				}
				if (entry.getValue() == null) {
					action.putNull(key);
				} else {
					JsonNode valueNode = objectMapper.valueToTree(entry.getValue());
					action.set(key, valueNode);
				}
			}
		}
		ObjectNode body = objectMapper.createObjectNode();
		body.set("actions", actions);
		return objectMapper.writeValueAsString(body);
	}

	/** {@code _type} aus der Eingabe, getrimmt; fehlt er oder ist er leer, SetFieldValue */
	private static String actionTypeOf(Map<String, Object> inputAction) {
		return inputAction.get(ACTION_TYPE_KEY) instanceof String type && !type.isBlank() ? type.trim()
				: DEFAULT_ACTION_TYPE;
	}

	private static boolean isBlank(String text) {
		return text == null || text.isBlank();
	}

}
