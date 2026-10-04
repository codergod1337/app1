package codergod1337.app1.solr.document.service;

import codergod1337.app1.solr.core.model.SolrCore;
import codergod1337.app1.solr.field.model.SolrField;
import codergod1337.app1.solr.field.model.SolrFieldType;
import codergod1337.app1.solr.field.service.SolrFieldService;
import codergod1337.app1.solr.hook.model.SolrHook;
import codergod1337.app1.solr.hook.service.SolrHookService;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;

/**
 * Prüft ein Dokument, wie es ein Client schickt, gegen die Felddefinition eines Kerns: Jeder Name muss ein Feld des
 * Kerns oder ein Hook sein, jeder Wert muss zum Typ passen und zu multiValued. Ohne Fachwissen: Was ein Feld bedeutet
 * und wer es füllen darf, weiß der Service des Kerns (z. B. das DMS), hier geht es nur um die Form. Die eingebauten
 * Felder nimmt kein Client je mit: id ist der Schlüssel, den das Backend bildet, cursorDate gehört dem Reindex,
 * userGrantedAccess schreibt nur die Dateifreigabe.
 *
 * Geprüft wird alles, nicht nur bis zum ersten Fehler: Die 400 nennt jede Beanstandung, der Aufrufer sieht auf einmal,
 * was nicht passt. Die Felder kommen aus den Kopien der Services im Speicher, nicht aus Postgres.
 */
@Service
public class SolrDocumentChecker {

	private final SolrFieldService solrFieldService;
	private final SolrHookService solrHookService;

	public SolrDocumentChecker(SolrFieldService solrFieldService, SolrHookService solrHookService) {
		this.solrFieldService = solrFieldService;
		this.solrHookService = solrHookService;
	}

	/**
	 * Der geprüfte Teil des Dokuments als Map in der Feldreihenfolge des Kerns, erst die Felder, dann die Hooks. Die
	 * Werte sind String, Boolean, Long, Double oder Instant, bei multiValued eine Liste davon ohne Doppelte. null, leere
	 * Texte und leere Listen fallen weg, als wären sie nicht geschickt.
	 *
	 * @throws ResponseStatusException 400 mit allen Beanstandungen: unbekannter Name, Wert passt nicht zum Typ oder zu
	 *         multiValued, oder ein eingebautes Feld ist dabei
	 */
	public Map<String, Object> checkDocument(String coreKey, JsonNode document) {
		if (document == null || !document.isObject()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "das Dokument ist kein JSON-Objekt");
		}
		Map<String, FieldShape> shapes = shapesOf(coreKey);
		List<String> problems = new ArrayList<>();
		Map<String, Object> checked = new LinkedHashMap<>();
		for (Map.Entry<String, JsonNode> property : document.properties()) {
			String name = property.getKey();
			JsonNode value = property.getValue();
			if (SolrCore.BUILT_IN_FIELD_NAMES.contains(name)) {
				problems.add(name + ": setzt das Backend, nicht der Client");
				continue;
			}
			FieldShape shape = shapes.get(name);
			if (shape == null) {
				problems.add(name + ": kein Feld des Kerns " + coreKey + " und kein Hook");
				continue;
			}
			if (value == null || value.isNull()) {
				continue;
			}
			Object converted = convert(name, shape, value, problems);
			if (converted != null) {
				checked.put(name, converted);
			}
		}
		if (!problems.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, String.join("; ", problems));
		}
		// in der Reihenfolge des Kerns, nicht in der des Clients
		Map<String, Object> ordered = new LinkedHashMap<>();
		for (String name : shapes.keySet()) {
			if (checked.containsKey(name)) {
				ordered.put(name, checked.get(name));
			}
		}
		return ordered;
	}

	/**
	 * Die Werte einer Auflösung: je Feld ein Wert oder eine Liste, egal ob das Feld multiValued ist, denn aufgelöst wird
	 * mit ODER über die Werte. Jeder Name muss ein Feld des Kerns oder ein Hook sein, auch die eingebauten (nach id darf
	 * man auflösen), jeder Wert muss zum Typ passen. Ob der User nach dem Feld auflösen darf, prüft der Aufrufer vorher
	 * und gibt nur die erlaubten Namen herein: Hier geht es nur um die Form.
	 *
	 * @return je Feld seine Werte (String, Boolean, Long, Double oder Instant) ohne Doppelte, in der Feldreihenfolge des
	 *         Kerns. Leere Werte fallen weg, ein Feld ohne Werte auch.
	 * @throws ResponseStatusException 400 mit allen Beanstandungen
	 */
	public Map<String, List<Object>> checkExactResolveFieldValues(String coreKey, JsonNode fieldValues) {
		if (fieldValues == null || !fieldValues.isObject()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "fieldValuesByFieldName ist kein JSON-Objekt");
		}
		Map<String, FieldShape> shapes = shapesOf(coreKey);
		List<String> problems = new ArrayList<>();
		Map<String, List<Object>> checked = new LinkedHashMap<>();
		for (Map.Entry<String, JsonNode> property : fieldValues.properties()) {
			String name = property.getKey();
			JsonNode value = property.getValue();
			FieldShape shape = shapes.get(name);
			if (shape == null) {
				problems.add(name + ": kein Feld des Kerns " + coreKey + " und kein Hook");
				continue;
			}
			if (value == null || value.isNull()) {
				continue;
			}
			// immer als Liste prüfen: ein einzelner Wert ist eine Liste mit einem Eintrag
			FieldShape asList = new FieldShape(shape.type(), true);
			Object converted = convert(name, asList, value.isArray() ? value : singletonArray(value), problems);
			if (converted instanceof List<?> values && !values.isEmpty()) {
				checked.put(name, List.copyOf(values));
			}
		}
		if (!problems.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, String.join("; ", problems));
		}
		Map<String, List<Object>> ordered = new LinkedHashMap<>();
		for (String name : shapes.keySet()) {
			if (checked.containsKey(name)) {
				ordered.put(name, checked.get(name));
			}
		}
		return ordered;
	}

	/** Der Typ eines Feldes oder Hooks dieses Kerns, null wenn es keins gibt. Für den Aufbau der Abfrage. */
	public SolrFieldType solrFieldTypeOf(String coreKey, String name) {
		FieldShape shape = shapesOf(coreKey).get(name);
		return shape == null ? null : shape.type();
	}

	private static JsonNode singletonArray(JsonNode value) {
		ArrayNode array = JsonNodeFactory.instance.arrayNode(1);
		array.add(value);
		return array;
	}

	/** Typ und multiValued eines Feldes, mehr braucht die Prüfung nicht */
	private record FieldShape(SolrFieldType type, boolean multiValued) {
	}

	/** Die Felder des Kerns in Listenreihenfolge, dahinter die Hooks mit ihren festen Einstellungen */
	private Map<String, FieldShape> shapesOf(String coreKey) {
		Map<String, FieldShape> shapes = new LinkedHashMap<>();
		for (SolrField solrField : solrFieldService.getSolrFieldsByCoreKey(coreKey)) {
			shapes.put(solrField.getName(), new FieldShape(solrField.getType(), solrField.isMultiValued()));
		}
		for (SolrHook solrHook : solrHookService.getAllSolrHooks()) {
			shapes.putIfAbsent(solrHook.getKey(), new FieldShape(SolrHook.FIELD_TYPE, SolrHook.FIELD_MULTI_VALUED));
		}
		return shapes;
	}

	/** multiValued: eine Liste, jeder Eintrag geprüft, Doppelte weg. Sonst genau ein Wert. */
	private static Object convert(String name, FieldShape shape, JsonNode value, List<String> problems) {
		if (shape.multiValued()) {
			if (!value.isArray()) {
				problems.add(name + ": multiValued, erwartet eine Liste");
				return null;
			}
			Set<Object> values = new LinkedHashSet<>();
			for (int index = 0; index < value.size(); index++) {
				JsonNode element = value.get(index);
				if (element == null || element.isNull()) {
					continue;
				}
				Object converted = convertSingle(name + "[" + index + "]", shape.type(), element, problems);
				if (converted != null) {
					values.add(converted);
				}
			}
			return values.isEmpty() ? null : List.copyOf(values);
		}
		if (value.isArray()) {
			problems.add(name + ": nicht multiValued, erwartet einen einzelnen Wert");
			return null;
		}
		return convertSingle(name, shape.type(), value, problems);
	}

	/** Ein Wert nach Typ: Texte gestrippt, leer heißt nicht gesetzt. Passt er nicht, kommt die Beanstandung in die Liste. */
	private static Object convertSingle(String name, SolrFieldType type, JsonNode value, List<String> problems) {
		return switch (type) {
			case STRING, TEXT_MINIMAL, TEXT_WORD, TEXT_WORDS -> {
				if (!value.isString()) {
					problems.add(name + ": erwartet einen Text (" + type + ")");
					yield null;
				}
				String text = value.asString().strip();
				yield text.isEmpty() ? null : text;
			}
			case BOOLEAN -> {
				if (!value.isBoolean()) {
					problems.add(name + ": erwartet true oder false");
					yield null;
				}
				yield value.booleanValue();
			}
			case LONG -> {
				if (!value.isIntegralNumber() || !value.canConvertToLong()) {
					problems.add(name + ": erwartet eine ganze Zahl (long)");
					yield null;
				}
				yield value.longValue();
			}
			case DOUBLE -> {
				if (!value.isNumber()) {
					problems.add(name + ": erwartet eine Zahl (double)");
					yield null;
				}
				yield value.doubleValue();
			}
			case DATE -> {
				if (!value.isString()) {
					problems.add(name + ": erwartet einen Zeitpunkt als Text nach ISO 8601");
					yield null;
				}
				Instant instant = parseInstant(value.asString().strip());
				if (instant == null) {
					problems.add(name + ": kein Zeitpunkt nach ISO 8601, z. B. 2026-10-03T12:00:00Z");
				}
				yield instant;
			}
		};
	}

	/** 2026-10-03T12:00:00Z oder mit Versatz 2026-10-03T14:00:00+02:00, sonst null. Ohne Zone ist ein Zeitpunkt nicht eindeutig. */
	private static Instant parseInstant(String text) {
		try {
			return Instant.parse(text);
		} catch (DateTimeParseException ignored) {
			// kein Z am Ende, vielleicht ein Versatz
		}
		try {
			return OffsetDateTime.parse(text).toInstant();
		} catch (DateTimeParseException ignored) {
			return null;
		}
	}

}
