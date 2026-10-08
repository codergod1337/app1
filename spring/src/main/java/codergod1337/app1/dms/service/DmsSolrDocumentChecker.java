package codergod1337.app1.dms.service;

import codergod1337.app1.dms.model.DmsSolrDocumentFieldNames;
import codergod1337.app1.solr.core.model.SolrCore;
import codergod1337.app1.solr.document.model.SolrDocumentFieldChange;
import codergod1337.app1.solr.document.model.SolrDocumentFieldChangeOperation;
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
 * Die Formprüfung des Kerns DMS (Vorgabe 2026-10-07, je Kern ein Checker): Was ein Client an die DMS-Controller
 * schickt, ein neues Dokument, die Bedingungen einer Auflösung, die Feldänderungen eines Patch, wird gegen die
 * Felddefinition des Kerns DMS in Postgres geprüft. Jeder Name muss ein Feld des Kerns oder ein Hook sein, jeder Wert
 * muss zum Typ passen und zu multiValued, jede Operation zu einwertig oder Liste. Hier geht es nur um die Form: Wer ein
 * Feld setzen darf, sagt die Schreibregel je Feld, was ein Feld bedeutet, der Schreibservice des DMS. Kein Zugriff auf
 * Solr, nur auf die Kopien der Felder im Speicher.
 *
 * Geprüft wird alles, nicht nur bis zum ersten Fehler: Die 400 nennt jede Beanstandung.
 */
@Service
public class DmsSolrDocumentChecker {

	private final SolrFieldService solrFieldService;
	private final SolrHookService solrHookService;

	public DmsSolrDocumentChecker(SolrFieldService solrFieldService, SolrHookService solrHookService) {
		this.solrFieldService = solrFieldService;
		this.solrHookService = solrHookService;
	}

	/**
	 * Der geprüfte Teil eines neuen Dokuments als Map in der Feldreihenfolge des Kerns, erst die Felder, dann die Hooks.
	 * Die Werte sind String, Boolean, Long, Double oder Instant, bei multiValued eine Liste davon ohne Doppelte. null,
	 * leere Texte und leere Listen fallen weg, als wären sie nicht geschickt. Die eingebauten Felder (id, cursorDate,
	 * userGrantedAccess) hat der Schreibservice vorher entfernt, kommt doch eins an, ist das eine 400.
	 */
	public Map<String, Object> checkNewDmsSolrDocument(JsonNode document) {
		if (document == null || !document.isObject()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "das Dokument ist kein JSON-Objekt");
		}
		Map<String, FieldShape> shapes = shapes();
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
				problems.add(name + ": kein Feld des Kerns " + DmsSolrDocumentFieldNames.CORE_KEY + " und kein Hook");
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
		Map<String, Object> ordered = new LinkedHashMap<>();
		for (String name : shapes.keySet()) {
			if (checked.containsKey(name)) {
				ordered.put(name, checked.get(name));
			}
		}
		return ordered;
	}

	/**
	 * Die Feldwerte eines Patch (Bearbeiten-Maske): je Feld der Endwert, wie er jetzt sein soll, kein Delta. Ein
	 * einwertiges Feld wird SET, eine Liste SET_ALL. null, ein leerer Text oder eine leere Liste heißt hier, anders als
	 * beim neuen Dokument, das Feld leeren. Jeder Name muss ein Feld des Kerns oder ein Hook sein, kein eingebautes.
	 *
	 * @return je Feld eine Änderung mit geprüften Werten, in der Feldreihenfolge des Kerns
	 */
	public List<SolrDocumentFieldChange> checkPatchFieldValues(JsonNode fieldValuesByFieldName) {
		if (fieldValuesByFieldName == null || !fieldValuesByFieldName.isObject()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "fieldValuesByFieldName ist kein JSON-Objekt");
		}
		Map<String, FieldShape> shapes = shapes();
		List<String> problems = new ArrayList<>();
		Map<String, SolrDocumentFieldChange> changes = new LinkedHashMap<>();
		for (Map.Entry<String, JsonNode> property : fieldValuesByFieldName.properties()) {
			String name = property.getKey();
			JsonNode value = property.getValue();
			if (SolrCore.BUILT_IN_FIELD_NAMES.contains(name)) {
				problems.add(name + ": setzt das Backend, nicht der Client");
				continue;
			}
			FieldShape shape = shapes.get(name);
			if (shape == null) {
				problems.add(name + ": kein Feld des Kerns " + DmsSolrDocumentFieldNames.CORE_KEY + " und kein Hook");
				continue;
			}
			Object converted = value == null || value.isNull() ? null : convert(name, shape, value, problems);
			if (shape.multiValued()) {
				changes.put(name, SolrDocumentFieldChange.setAll(name, converted instanceof List<?> list ? list : List.of()));
			} else {
				changes.put(name, SolrDocumentFieldChange.set(name, converted));
			}
		}
		if (!problems.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, String.join("; ", problems));
		}
		List<SolrDocumentFieldChange> ordered = new ArrayList<>();
		for (String name : shapes.keySet()) {
			if (changes.containsKey(name)) {
				ordered.add(changes.get(name));
			}
		}
		return ordered;
	}

	/**
	 * Feldänderungen, wie der Schreibservice sie baut (auch an eingebauten Feldern): Jeder Name muss ein Feld des Kerns
	 * oder ein Hook sein, die Operation muss zu einwertig oder Liste passen, INC nur bei long und double, jeder Wert zum
	 * Typ. Texte werden gestrippt, Listen verlieren Doppelte.
	 *
	 * @return dieselben Änderungen mit geprüften Werten, in der Reihenfolge des Aufrufers
	 */
	public List<SolrDocumentFieldChange> checkDocumentFieldChanges(List<SolrDocumentFieldChange> changes) {
		Map<String, FieldShape> shapes = shapes();
		List<String> problems = new ArrayList<>();
		List<SolrDocumentFieldChange> checked = new ArrayList<>();
		for (SolrDocumentFieldChange change : changes) {
			String name = change.fieldName();
			FieldShape shape = shapes.get(name);
			if (shape == null) {
				problems.add(name + ": kein Feld des Kerns " + DmsSolrDocumentFieldNames.CORE_KEY + " und kein Hook");
				continue;
			}
			SolrDocumentFieldChangeOperation operation = change.operation();
			if (operation.isForMultiValuedField() != shape.multiValued()) {
				problems.add(name + ": " + operation + " passt nicht zu "
						+ (shape.multiValued() ? "einer Liste" : "einem einwertigen Feld"));
				continue;
			}
			if (operation == SolrDocumentFieldChangeOperation.INC && shape.type() != SolrFieldType.LONG
					&& shape.type() != SolrFieldType.DOUBLE) {
				problems.add(name + ": INC geht nur bei long und double");
				continue;
			}
			if (!operation.isForMultiValuedField() && change.values().size() > 1) {
				problems.add(name + ": " + operation + " erwartet höchstens einen Wert");
				continue;
			}
			Set<Object> values = new LinkedHashSet<>();
			for (Object value : change.values()) {
				Object converted = convertObject(name, shape.type(), value, problems);
				if (converted != null) {
					values.add(converted);
				}
			}
			checked.add(new SolrDocumentFieldChange(name, operation, new ArrayList<>(values)));
		}
		if (!problems.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, String.join("; ", problems));
		}
		return checked;
	}

	/**
	 * Die Werte einer Auflösung: je Feld ein Wert oder eine Liste, egal ob das Feld multiValued ist, denn aufgelöst wird
	 * mit ODER über die Werte. Jeder Name muss ein Feld des Kerns oder ein Hook sein, auch die eingebauten (nach id darf
	 * man auflösen), jeder Wert muss zum Typ passen. Ob der User nach dem Feld auflösen darf, prüft der Aufrufer vorher.
	 *
	 * @return je Feld seine Werte ohne Doppelte, in der Feldreihenfolge des Kerns. Leere Werte fallen weg, ein Feld ohne
	 *         Werte auch.
	 */
	public Map<String, List<Object>> checkExactResolveFieldValues(JsonNode fieldValues) {
		if (fieldValues == null || !fieldValues.isObject()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "fieldValuesByFieldName ist kein JSON-Objekt");
		}
		Map<String, FieldShape> shapes = shapes();
		List<String> problems = new ArrayList<>();
		Map<String, List<Object>> checked = new LinkedHashMap<>();
		for (Map.Entry<String, JsonNode> property : fieldValues.properties()) {
			String name = property.getKey();
			JsonNode value = property.getValue();
			FieldShape shape = shapes.get(name);
			if (shape == null) {
				problems.add(name + ": kein Feld des Kerns " + DmsSolrDocumentFieldNames.CORE_KEY + " und kein Hook");
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

	/** Der Typ eines Feldes oder Hooks des Kerns DMS, null wenn es keins gibt. Für den Aufbau der Abfrage. */
	public SolrFieldType solrFieldTypeOf(String name) {
		FieldShape shape = shapes().get(name);
		return shape == null ? null : shape.type();
	}

	/** Gibt es dieses Feld oder diesen Hook im Kern DMS? */
	public boolean isFieldOrHookOfDmsCore(String name) {
		return shapes().containsKey(name);
	}

	private static JsonNode singletonArray(JsonNode value) {
		ArrayNode array = JsonNodeFactory.instance.arrayNode(1);
		array.add(value);
		return array;
	}

	/** Typ und multiValued eines Feldes, mehr braucht die Prüfung nicht */
	private record FieldShape(SolrFieldType type, boolean multiValued) {
	}

	/** Die Felder des Kerns DMS in Listenreihenfolge, dahinter die Hooks mit ihren festen Einstellungen */
	private Map<String, FieldShape> shapes() {
		Map<String, FieldShape> shapes = new LinkedHashMap<>();
		for (SolrField solrField : solrFieldService.getSolrFieldsByCoreKey(DmsSolrDocumentFieldNames.CORE_KEY)) {
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
			case STRING, TEXT_EDGE, TEXT_SUCHE -> {
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

	/**
	 * Ein Wert, wie der Schreibservice ihn baut, nach Typ: Texte gestrippt, leer heißt nicht gesetzt, ganze Zahlen
	 * werden long, Kommazahlen double, ein Zeitpunkt darf Instant oder ISO-8601-Text sein.
	 */
	private static Object convertObject(String name, SolrFieldType type, Object value, List<String> problems) {
		if (value == null) {
			return null;
		}
		return switch (type) {
			case STRING, TEXT_EDGE, TEXT_SUCHE -> {
				if (!(value instanceof String text)) {
					problems.add(name + ": erwartet einen Text (" + type + ")");
					yield null;
				}
				String stripped = text.strip();
				yield stripped.isEmpty() ? null : stripped;
			}
			case BOOLEAN -> {
				if (!(value instanceof Boolean bool)) {
					problems.add(name + ": erwartet true oder false");
					yield null;
				}
				yield bool;
			}
			case LONG -> {
				if (!(value instanceof Integer) && !(value instanceof Long)) {
					problems.add(name + ": erwartet eine ganze Zahl (long)");
					yield null;
				}
				yield ((Number) value).longValue();
			}
			case DOUBLE -> {
				if (!(value instanceof Number number)) {
					problems.add(name + ": erwartet eine Zahl (double)");
					yield null;
				}
				yield number.doubleValue();
			}
			case DATE -> {
				if (value instanceof Instant instant) {
					yield instant;
				}
				Instant parsed = value instanceof String text ? parseInstant(text.strip()) : null;
				if (parsed == null) {
					problems.add(name + ": erwartet einen Zeitpunkt (Instant oder ISO 8601)");
				}
				yield parsed;
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
