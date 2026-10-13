package codergod1337.app1.mapping.service;

import codergod1337.app1.mapping.model.ResponseMappingExecutionResult;
import codergod1337.app1.mapping.model.ResponseMappingLine;
import codergod1337.app1.mapping.model.ResponseMappingLineIdRole;
import codergod1337.app1.mapping.model.ResponseMappingLineKind;
import codergod1337.app1.mapping.model.ResponseMappingMatchCondition;
import codergod1337.app1.mapping.model.ResponseMappingRowSubfetch;
import codergod1337.app1.mapping.model.ResponseMappingSubfetchInputParameter;
import codergod1337.app1.mapping.model.ResponseMappingTableColumn;
import codergod1337.app1.mapping.model.ResponseMappingWithLines;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Lässt das Gesamt-Mapping gegen eine Antwort laufen: den Typ aus {@code content.data.meta.objectType} lesen, unter den
 * Mappings dieses Typs das wählen, dessen kennzeichnende Felder in der Antwort stehen (das mit den meisten gewinnt), dann
 * die Einträge in Reihenfolge auswerten. Derselbe Key zweimal ist die Kaskade, der erste Eintrag mit Wert gewinnt; ein
 * Eintrag mit Bedingung liefert nur, wenn sie steht. Eine Tabelle liefert je Zeile ein JSON-Objekt als Text.
 * Subfetch-Einträge werden gemeldet, nicht geholt: Holen kann nur, wer ABAS kennt, und der legt die Werte der
 * Unterantworten zum Ergebnis dazu ({@code AbasSternzeitMappingService}). Ein Subfetch je Zeile (User 2026-10-13) wird
 * je gelieferter Zeile seiner Tabelle mit aufgelösten Eingaben gemeldet, der Aufrufer hängt die Unterantwort über
 * {@link #attachRowSubfetchResult} in die Zeile. Kennt weder ABAS noch Solr.
 */
@Service
public class ResponseMappingExecutionService {

	private static final Logger log = LoggerFactory.getLogger(ResponseMappingExecutionService.class);

	private static final TypeReference<List<ResponseMappingMatchCondition>> MATCH_CONDITION_LIST = new TypeReference<>() {
	};

	private static final TypeReference<Map<String, Object>> JSON_OBJECT = new TypeReference<>() {
	};

	/** Die Zeilen eines TABLE-Eintrags als JSON-Texte und, zeilengleich, die Elemente, aus denen sie entstanden */
	private record TableRows(List<Object> rows, List<JsonNode> elements) {
	}

	private final ResponseMappingService responseMappingService;
	private final ObjectMapper objectMapper;

	public ResponseMappingExecutionService(ResponseMappingService responseMappingService, ObjectMapper objectMapper) {
		this.responseMappingService = responseMappingService;
		this.objectMapper = objectMapper;
	}

	/** 400 ohne Typ in der Antwort, 404 ohne passendes Mapping */
	public ResponseMappingExecutionResult executeResponseMapping(JsonNode response) {
		JsonNode objectTypeNode = response.path("content").path("data").path("meta").path("objectType");
		if (objectTypeNode.isMissingNode() || objectTypeNode.isNull() || objectTypeNode.asString().isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "die Antwort trägt keinen objectType");
		}
		String objectType = objectTypeNode.asString();
		ResponseMappingWithLines mapping = chooseResponseMapping(objectType, response);
		Map<String, List<Object>> valuesByKey = new LinkedHashMap<>();
		List<ResponseMappingLine> subfetchLines = new ArrayList<>();
		List<ResponseMappingLine> rowSubfetchLines = new ArrayList<>();
		// je TABLE-Eintrag, der geliefert hat, seine Zeilenelemente, zeilengleich zu den JSON-Texten im Ergebnis
		Map<String, List<JsonNode>> elementsByTableKey = new LinkedHashMap<>();
		List<String> warnings = new ArrayList<>();
		String primaryIdKey = null;
		for (ResponseMappingLine line : mapping.lines()) {
			if (line.getIdRole() == ResponseMappingLineIdRole.PRIMARY) {
				primaryIdKey = line.getKey();
			}
			if (line.isRowSubfetch()) {
				rowSubfetchLines.add(line);
				continue;
			}
			if (line.isSubfetch()) {
				subfetchLines.add(line);
				continue;
			}
			if (hasValue(valuesByKey.get(line.getKey()))) {
				// Kaskade: der erste Eintrag mit Wert hat schon geliefert
				continue;
			}
			if (line.getOnlyIfPath() != null && !JsonPathEvaluator.firstComparable(response, line.getOnlyIfPath())
					.equals(JsonPathEvaluator.comparableText(line.getOnlyIfEquals()))) {
				continue;
			}
			List<Object> values;
			if (line.getKind() == ResponseMappingLineKind.TABLE) {
				TableRows tableRows = tableRowsOf(line, response, warnings);
				values = tableRows.rows();
				if (!tableRows.rows().isEmpty()) {
					elementsByTableKey.put(line.getKey(), tableRows.elements());
				}
			} else {
				values = lineValuesOf(line, response);
			}
			if (hasValue(values)) {
				valuesByKey.put(line.getKey(), values);
			} else {
				valuesByKey.putIfAbsent(line.getKey(), values);
			}
		}
		// Subfetches je Zeile: erst jetzt, denn ihre Tabelle darf im Mapping hinter ihnen stehen
		List<ResponseMappingRowSubfetch> rowSubfetches = new ArrayList<>();
		for (ResponseMappingLine line : rowSubfetchLines) {
			List<JsonNode> elements = elementsByTableKey.get(line.getTableLineKey());
			if (elements == null) {
				// die Tabelle hat keine Zeile geliefert oder gibt es nicht: nichts zu holen, keine Warnung
				continue;
			}
			for (int rowIndex = 0; rowIndex < elements.size(); rowIndex++) {
				Map<String, String> inputValues = resolveSubfetchInputValues(line, elements.get(rowIndex));
				if (inputValues != null) {
					rowSubfetches.add(new ResponseMappingRowSubfetch(line, line.getTableLineKey(), rowIndex, inputValues));
				}
			}
		}
		log.info("Mapping {} auf {} gelaufen: {} Keys mit Wert, {} Subfetches, {} Subfetches je Zeile",
				mapping.responseMapping().getKey(), objectType,
				valuesByKey.values().stream().filter(ResponseMappingExecutionService::hasValue).count(),
				subfetchLines.size(), rowSubfetches.size());
		return new ResponseMappingExecutionResult(mapping.responseMapping().getKey(), objectType,
				mapping.responseMapping().getTargetKey(), primaryIdKey, valuesByKey, subfetchLines, rowSubfetches,
				warnings);
	}

	/**
	 * Die Eingaben eines Subfetch-Eintrags gegen das JSON, in dem er steht: die ganze Antwort beim Subfetch für den Kopf,
	 * das Zeilenelement beim Subfetch je Zeile. Je Name der Wert, aus dem JSON (der erste Treffer der Adresse als Text)
	 * oder statisch; eine Eingabe ohne beides steht mit null drin, sie wird ohne Wert gesendet. Liefert eine Adresse
	 * nichts, kommt null zurück: Der Artikel hat z. B. keinen Hersteller, der Subfetch entfällt still, wie in der
	 * Vorlage, keine Warnung.
	 */
	public Map<String, String> resolveSubfetchInputValues(ResponseMappingLine line, JsonNode scope) {
		Map<String, String> valueByName = new LinkedHashMap<>();
		for (ResponseMappingSubfetchInputParameter parameter : responseMappingService.subfetchInputParametersOf(line)) {
			String value = null;
			if (parameter.path() != null) {
				List<JsonNode> found = JsonPathEvaluator.evaluatePath(scope, parameter.path());
				Object first = found.isEmpty() ? null : JsonPathEvaluator.valueOf(found.get(0));
				if (first == null || String.valueOf(first).isBlank()) {
					log.debug("Subfetch {}: die Eingabe {} ist im JSON leer ({}), entfällt", line.getKey(),
							parameter.name(), parameter.path());
					return null;
				}
				value = String.valueOf(first);
			} else if (parameter.value() != null) {
				value = parameter.value();
			}
			valueByName.put(parameter.name(), value);
		}
		return valueByName;
	}

	/**
	 * Die Werte einer Unterantwort in die Zeile hängen, für die der Subfetch lief (User 2026-10-13): Die Zeile, ein
	 * JSON-Text in der Liste ihres Tabellen-Keys, bekommt unter dem Key des Subfetch-Eintrags ein Objekt mit den Werten
	 * der Unterantwort je Key; Tabellen darin werden Objekte statt JSON-Texte, damit der Client einen echten Baum sieht.
	 * Ändert die Liste in {@code valuesByKey}. Ist die Zeile nicht mehr da, passiert nichts außer einer Zeile im Log.
	 */
	public void attachRowSubfetchResult(Map<String, List<Object>> valuesByKey, ResponseMappingRowSubfetch rowSubfetch,
			ResponseMappingExecutionResult subResult) {
		List<Object> rows = valuesByKey.get(rowSubfetch.tableKey());
		if (rows == null || rowSubfetch.rowIndex() >= rows.size() || rows.get(rowSubfetch.rowIndex()) == null) {
			log.warn("Subfetch {} je Zeile: Zeile {} der Tabelle {} gibt es nicht mehr, Unterantwort verworfen",
					rowSubfetch.line().getKey(), rowSubfetch.rowIndex() + 1, rowSubfetch.tableKey());
			return;
		}
		Map<String, Object> row = objectMapper.readValue(String.valueOf(rows.get(rowSubfetch.rowIndex())), JSON_OBJECT);
		Map<String, Object> nested = new LinkedHashMap<>();
		for (Map.Entry<String, List<Object>> values : subResult.valuesByKey().entrySet()) {
			List<Object> parsed = new ArrayList<>(values.getValue().size());
			for (Object value : values.getValue()) {
				parsed.add(objectIfJsonText(value));
			}
			nested.put(values.getKey(), parsed);
		}
		row.put(rowSubfetch.line().getKey(), nested);
		List<Object> changed = new ArrayList<>(rows);
		changed.set(rowSubfetch.rowIndex(), objectMapper.writeValueAsString(row));
		valuesByKey.put(rowSubfetch.tableKey(), changed);
	}

	/** Ein JSON-Text eines Objekts (eine Tabellenzeile) als Objekt, alles andere unverändert */
	private Object objectIfJsonText(Object value) {
		if (value instanceof String text && text.startsWith("{")) {
			try {
				return objectMapper.readValue(text, JSON_OBJECT);
			} catch (JacksonException e) {
				return value;
			}
		}
		return value;
	}

	/** Die Werte eines VALUE-Eintrags: ein Wert, oder je Element der Liste einer, Lücken als null */
	private static List<Object> lineValuesOf(ResponseMappingLine line, JsonNode response) {
		JsonPathEvaluator.LineValues found = JsonPathEvaluator.evaluateLine(response, line.getSourcePath(),
				line.getElementPath());
		List<Object> values = new ArrayList<>(found.values().size());
		for (JsonNode node : found.values()) {
			values.add(JsonPathEvaluator.valueOf(node));
		}
		return values;
	}

	/**
	 * Die Zeilen eines TABLE-Eintrags: je Element der Liste an sourcePath ein JSON-Objekt mit den Spalten, jede Spalte der
	 * erste Treffer ihrer Adresse im Element, fehlende Spalten null. Eine Zeile ohne einen einzigen Wert fällt weg, samt
	 * ihrem Element, damit Zeilen und Elemente zeilengleich bleiben.
	 */
	private TableRows tableRowsOf(ResponseMappingLine line, JsonNode response, List<String> warnings) {
		List<ResponseMappingTableColumn> columns;
		try {
			columns = responseMappingService.tableColumnsOf(line);
		} catch (ResponseStatusException e) {
			warnings.add(line.getKey() + ": " + e.getReason());
			return new TableRows(List.of(), List.of());
		}
		List<JsonNode> elements = JsonPathEvaluator.evaluateLine(response, line.getSourcePath(), null).values();
		List<Object> rows = new ArrayList<>(elements.size());
		List<JsonNode> keptElements = new ArrayList<>(elements.size());
		for (JsonNode element : elements) {
			Map<String, Object> row = new LinkedHashMap<>();
			boolean any = false;
			for (ResponseMappingTableColumn column : columns) {
				List<JsonNode> found = JsonPathEvaluator.evaluatePath(element, column.elementPath());
				Object value = found.isEmpty() ? null : JsonPathEvaluator.valueOf(found.get(0));
				if (value != null && !String.valueOf(value).isBlank()) {
					any = true;
				}
				row.put(column.name(), value);
			}
			if (any) {
				rows.add(objectMapper.writeValueAsString(row));
				keptElements.add(element);
			}
		}
		return new TableRows(rows, keptElements);
	}

	/** Unter den Mappings des Typs das, dessen kennzeichnende Felder alle in der Antwort stehen; die meisten Felder gewinnen */
	private ResponseMappingWithLines chooseResponseMapping(String objectType, JsonNode response) {
		ResponseMappingWithLines best = null;
		int bestConditions = -1;
		for (ResponseMappingWithLines candidate : responseMappingService.getResponseMappingsByObjectType(objectType)) {
			List<ResponseMappingMatchCondition> conditions = matchConditionsOf(candidate);
			boolean matches = conditions.stream().allMatch(condition -> JsonPathEvaluator
					.firstComparable(response, condition.path())
					.equals(JsonPathEvaluator.comparableText(condition.equals())));
			if (matches && conditions.size() > bestConditions) {
				best = candidate;
				bestConditions = conditions.size();
			}
		}
		if (best == null) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND,
					"kein Mapping zu Typ " + objectType + ", dessen kennzeichnende Felder in der Antwort stehen");
		}
		return best;
	}

	private List<ResponseMappingMatchCondition> matchConditionsOf(ResponseMappingWithLines mapping) {
		String json = mapping.responseMapping().getMatchConditionsJson();
		if (json == null || json.isBlank()) {
			return List.of();
		}
		List<ResponseMappingMatchCondition> conditions = objectMapper.readValue(json, MATCH_CONDITION_LIST);
		return conditions == null ? List.of() : conditions;
	}

	/** Ein Wert ist da, wenn die Liste einen Eintrag hat, der nicht null und nicht leer ist */
	private static boolean hasValue(List<Object> values) {
		return values != null && values.stream().anyMatch(value -> value != null && !String.valueOf(value).isBlank());
	}

}
