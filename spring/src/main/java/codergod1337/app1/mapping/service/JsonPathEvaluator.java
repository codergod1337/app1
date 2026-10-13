package codergod1337.app1.mapping.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.NullNode;

/**
 * Die Adress-Grammatik des Mappings in Java, dieselbe wie im Frontend (modul/mapping/jsonPath.ts):
 * {@code $.content.data.head.fields.name.value}, Listen über {@code [*]} alle, {@code [feld]} wo das Feld gefüllt ist,
 * {@code [feld=wert]} ein Wert. Ein Feld eines Elements ist bei ABAS {@code fields.<feld>} mit value und text, sonst
 * das Element selbst; verglichen wird ohne Klammern und ohne Groß und Klein, wie in der Vorlage. Nie eine Position.
 * Ohne Fachwissen: kennt weder ABAS noch Solr.
 */
public final class JsonPathEvaluator {

	/** Ein Stück der Adresse: der Name und, in eckigen Klammern, der Filter; null heißt kein Filter */
	record PathSegment(String name, String filter) {
	}

	/**
	 * Die Werte eines Eintrags: ohne Liste ein Wert oder keiner; mit Liste je Element einer, {@link NullNode} bei einer
	 * Lücke, damit die Listen eines Mappings zeilengleich bleiben
	 */
	public record LineValues(boolean list, List<JsonNode> values) {
	}

	private JsonPathEvaluator() {
	}

	/** {@code $.content.data.table[artikel].fields} wird zu content, data, table[artikel], fields; das $ fällt weg */
	static List<PathSegment> pathSegmentsOf(String path) {
		List<PathSegment> segments = new ArrayList<>();
		StringBuilder name = new StringBuilder();
		StringBuilder filter = null;
		boolean inBracket = false;
		for (char character : path.trim().toCharArray()) {
			if (inBracket && filter != null) {
				if (character == ']') {
					inBracket = false;
				} else {
					filter.append(character);
				}
				continue;
			}
			if (character == '[') {
				inBracket = true;
				filter = new StringBuilder();
				continue;
			}
			if (character == '.') {
				push(segments, name, filter);
				name = new StringBuilder();
				filter = null;
				continue;
			}
			name.append(character);
		}
		push(segments, name, filter);
		segments.removeIf(segment -> segment.name().equals("$") && segment.filter() == null);
		return segments;
	}

	private static void push(List<PathSegment> segments, StringBuilder name, StringBuilder filter) {
		if (name.length() > 0 || filter != null) {
			segments.add(new PathSegment(name.toString(), filter == null ? null : filter.toString()));
		}
	}

	/** Alle Treffer einer Adresse im JSON; ohne Liste darin höchstens einer */
	public static List<JsonNode> evaluatePath(JsonNode json, String path) {
		return evaluateSegments(List.of(json), pathSegmentsOf(path));
	}

	/** Der erste Treffer einer Adresse als Vergleichsform, leer wenn nichts da ist */
	public static String firstComparable(JsonNode json, String path) {
		List<JsonNode> found = evaluatePath(json, path);
		return found.isEmpty() ? "" : comparable(found.get(0));
	}

	/**
	 * Die Werte eines Eintrags: Ohne Liste ein Wert oder keiner. Mit Liste je Element einer, NullNode bei einer Lücke;
	 * ohne elementPath die Elemente selbst.
	 */
	public static LineValues evaluateLine(JsonNode json, String sourcePath, String elementPath) {
		List<PathSegment> sourceSegments = pathSegmentsOf(sourcePath);
		List<JsonNode> sources = evaluateSegments(List.of(json), sourceSegments);
		boolean list = sourceSegments.stream().anyMatch(segment -> segment.filter() != null);
		if (elementPath == null || elementPath.isBlank()) {
			return list ? new LineValues(true, sources) : new LineValues(false, sources.subList(0, Math.min(1, sources.size())));
		}
		List<PathSegment> elementSegments = pathSegmentsOf(elementPath);
		List<JsonNode> values = new ArrayList<>(sources.size());
		for (JsonNode element : sources) {
			List<JsonNode> found = evaluateSegments(List.of(element), elementSegments);
			values.add(found.isEmpty() ? NullNode.getInstance() : found.get(0));
		}
		return new LineValues(true, values);
	}

	private static List<JsonNode> evaluateSegments(List<JsonNode> nodes, List<PathSegment> segments) {
		List<JsonNode> current = nodes;
		for (PathSegment segment : segments) {
			List<JsonNode> next = new ArrayList<>();
			for (JsonNode node : current) {
				JsonNode child;
				if (segment.name().isEmpty()) {
					child = node;
				} else if (node.isObject()) {
					child = node.get(segment.name());
				} else {
					child = null;
				}
				if (child == null || child.isMissingNode()) {
					continue;
				}
				if (segment.filter() == null) {
					next.add(child);
				} else {
					for (JsonNode element : elementsOf(child)) {
						if (filterMatches(element, segment.filter())) {
							next.add(element);
						}
					}
				}
			}
			current = next;
		}
		return current;
	}

	/** Die Elemente einer Liste; eine Map (die Map-Form von ABAS) liefert ihre Werte */
	private static List<JsonNode> elementsOf(JsonNode node) {
		List<JsonNode> elements = new ArrayList<>();
		if (node.isArray()) {
			node.forEach(elements::add);
		} else if (node.isObject()) {
			for (Map.Entry<String, JsonNode> property : node.properties()) {
				elements.add(property.getValue());
			}
		}
		return elements;
	}

	private static boolean filterMatches(JsonNode element, String filter) {
		String trimmed = filter.trim();
		if (trimmed.isEmpty() || trimmed.equals("*")) {
			return true;
		}
		int equals = trimmed.indexOf('=');
		String fieldName = (equals < 0 ? trimmed : trimmed.substring(0, equals)).trim();
		JsonNode[] field = fieldOf(element, fieldName);
		if (field == null) {
			return false;
		}
		if (equals < 0) {
			return !comparable(field[0]).isEmpty() || !comparable(field[1]).isEmpty();
		}
		String wanted = comparableText(trimmed.substring(equals + 1));
		return comparable(field[0]).equals(wanted) || comparable(field[1]).equals(wanted);
	}

	/** Das Feld eines Elements als Paar value und text: bei ABAS fields.<feld>, sonst der Wert direkt; null wenn es fehlt */
	private static JsonNode[] fieldOf(JsonNode element, String fieldName) {
		if (!element.isObject()) {
			return null;
		}
		JsonNode fields = element.get("fields");
		if (fields != null && fields.isObject() && fields.has(fieldName)) {
			JsonNode found = fields.get(fieldName);
			if (found.isObject()) {
				return new JsonNode[] { found.get("value"), found.get("text") };
			}
			return new JsonNode[] { found, null };
		}
		if (element.has(fieldName)) {
			return new JsonNode[] { element.get(fieldName), null };
		}
		return null;
	}

	/** Vergleichsform eines Knotens: ohne Klammern, getrimmt, klein; nichts ist der leere Text */
	public static String comparable(JsonNode node) {
		if (node == null || node.isNull() || node.isMissingNode()) {
			return "";
		}
		return comparableText(node.isString() ? node.asString() : node.toString());
	}

	/** Vergleichsform eines Texts, z. B. des Werts einer Bedingung: ohne Klammern, getrimmt, klein */
	public static String comparableText(String text) {
		String trimmed = text.trim();
		if (trimmed.startsWith("(")) {
			trimmed = trimmed.substring(1);
		}
		if (trimmed.endsWith(")")) {
			trimmed = trimmed.substring(0, trimmed.length() - 1);
		}
		return trimmed.trim().toLowerCase();
	}

	/** Der Wert eines Knotens als Java-Objekt: Text, Zahl, Wahrheitswert, null bei nichts; ein Objekt oder eine Liste als JSON-Text */
	public static Object valueOf(JsonNode node) {
		if (node == null || node.isNull() || node.isMissingNode()) {
			return null;
		}
		if (node.isString()) {
			return node.asString();
		}
		if (node.isIntegralNumber()) {
			return node.asLong();
		}
		if (node.isNumber()) {
			return node.asDouble();
		}
		if (node.isBoolean()) {
			return node.asBoolean();
		}
		return node.toString();
	}

}
