package codergod1337.app1.graph.mail.service;

import codergod1337.app1.graph.mail.GraphMailUnavailableException;
import codergod1337.app1.graph.mail.model.GraphMailMessage;
import codergod1337.app1.graph.mail.model.GraphMailPlaceholderGroupForClient;
import codergod1337.app1.graph.mail.model.GraphMailProperties;
import codergod1337.app1.graph.mail.model.GraphMailTemplate;
import codergod1337.app1.graph.mail.model.GraphMailTemplatePreview;
import codergod1337.app1.graph.mail.model.GraphMailTemplateType;
import codergod1337.app1.graph.mail.model.GraphMailTemplateTypeForClient;
import codergod1337.app1.graph.mail.repository.GraphMailTemplateRepository;
import codergod1337.app1.system.HelperInputs;
import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Die Mailvorlagen: Pflege durch den Admin, Muster für den Editor, und das Rendern und Verschicken einer Mail aus
 * einer Vorlage. Platzhalter heißen {gruppe.name}; welche Gruppen eine Vorlage benutzen darf, sagt ihr Typ, die
 * Gruppen selbst sind Beans (GraphMailPlaceholderGroup). Beim Speichern wird jeder Platzhalter gegen die Gruppen des
 * Typs geprüft, unbekannte sind 400. Beim Start legt der Service für jeden Typ ohne Vorlage die Standardtexte an.
 */
@Service
public class GraphMailTemplateService {

	private static final Logger log = LoggerFactory.getLogger(GraphMailTemplateService.class);

	/** Sortiert nach listingPosition und bei gleicher Position nach key */
	private static final Sort LISTING_ORDER = Sort.by("listingPosition", "key");

	/** {gruppe.name}: Gruppe in Kleinbuchstaben, Name aus Buchstaben und Ziffern */
	private static final Pattern PLACEHOLDER = Pattern.compile("\\{([a-z]+)\\.([A-Za-z0-9]+)}");

	/** die Sprache, auf die zurückgefallen wird, wenn die Vorlage die gewünschte nicht hat */
	private static final String DEFAULT_LANGUAGE = "de";

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private final GraphMailTemplateRepository graphMailTemplateRepository;
	private final GraphMailSendService graphMailSendService;
	private final GraphMailProperties properties;

	/** alle Gruppen nach ihrem Key, aus den Beans */
	private final Map<String, GraphMailPlaceholderGroup> placeholderGroupsByKey;

	public GraphMailTemplateService(GraphMailTemplateRepository graphMailTemplateRepository,
			GraphMailSendService graphMailSendService, GraphMailProperties properties,
			List<GraphMailPlaceholderGroup> placeholderGroups) {
		this.graphMailTemplateRepository = graphMailTemplateRepository;
		this.graphMailSendService = graphMailSendService;
		this.properties = properties;
		Map<String, GraphMailPlaceholderGroup> byKey = new LinkedHashMap<>();
		for (GraphMailPlaceholderGroup group : placeholderGroups) {
			if (byKey.put(group.groupKey(), group) != null) {
				throw new IllegalStateException("zwei Platzhaltergruppen mit dem Key " + group.groupKey());
			}
		}
		this.placeholderGroupsByKey = Map.copyOf(byKey);
		for (GraphMailTemplateType type : GraphMailTemplateType.values()) {
			for (String groupKey : type.getPlaceholderGroupKeys()) {
				if (!placeholderGroupsByKey.containsKey(groupKey)) {
					throw new IllegalStateException("Vorlagentyp " + type + " verlangt die Gruppe " + groupKey
							+ ", die es nicht gibt");
				}
			}
		}
	}

	/** Beim Start: für jeden Typ ohne Vorlage die Standardtexte anlegen. Vorhandenes bleibt unberührt. */
	@PostConstruct
	void createMissingGraphMailTemplates() {
		for (GraphMailTemplateType type : GraphMailTemplateType.values()) {
			if (!graphMailTemplateRepository.existsByType(type)) {
				GraphMailTemplate created = graphMailTemplateRepository
						.save(GraphMailTemplateDefaultTexts.defaultGraphMailTemplate(type));
				log.info("Mailvorlage {} mit Standardtexten angelegt", created.getKey());
			}
		}
	}

	/** Alle Vorlagen, sortiert nach listingPosition und dann key. */
	@PreAuthorize("hasRole('ADMIN')")
	@Transactional(readOnly = true)
	public List<GraphMailTemplate> getAllGraphMailTemplates() {
		return graphMailTemplateRepository.findAll(LISTING_ORDER);
	}

	/** Die Typen mit ihren Gruppen und Platzhalternamen, für die Hinweise im Editor. */
	@PreAuthorize("hasRole('ADMIN')")
	public List<GraphMailTemplateTypeForClient> getAllGraphMailTemplateTypes() {
		List<GraphMailTemplateTypeForClient> types = new ArrayList<>();
		for (GraphMailTemplateType type : GraphMailTemplateType.values()) {
			List<GraphMailPlaceholderGroupForClient> groups = new ArrayList<>();
			for (String groupKey : type.getPlaceholderGroupKeys()) {
				groups.add(new GraphMailPlaceholderGroupForClient(groupKey,
						placeholderGroupsByKey.get(groupKey).placeholderNames()));
			}
			types.add(new GraphMailTemplateTypeForClient(type, groups));
		}
		return types;
	}

	/**
	 * Neue Vorlage. 400 bei ungültigem key, ungültigem mehrsprachigen Text oder unbekannten Platzhaltern, 409 wenn der
	 * key vergeben ist oder der Typ schon eine Vorlage hat.
	 */
	@PreAuthorize("hasRole('ADMIN')")
	@Transactional
	public GraphMailTemplate createGraphMailTemplate(String key, GraphMailTemplateType type, String displayName,
			String description, String subject, String htmlBody, int listingPosition) {
		if (!HelperInputs.isValidKey(key)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "key ist ungültig");
		}
		if (type == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "type fehlt");
		}
		// Pflicht: save mit einem vorhandenen key wäre ein Update und würde die bestehende Vorlage überschreiben
		if (graphMailTemplateRepository.existsById(key)) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "key ist bereits vergeben");
		}
		if (graphMailTemplateRepository.existsByType(type)) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "für den Typ " + type + " gibt es schon eine Vorlage");
		}
		checkGraphMailTemplateTexts(type, displayName, description, subject, htmlBody);
		return graphMailTemplateRepository
				.save(new GraphMailTemplate(key, type, displayName, description, subject, htmlBody, listingPosition));
	}

	/** Der key bestimmt nur, welche Vorlage geändert wird. Typ und key ändern sich nie, alle Texte und die Position werden ersetzt. */
	@PreAuthorize("hasRole('ADMIN')")
	@Transactional
	public GraphMailTemplate updateGraphMailTemplate(String key, String displayName, String description, String subject,
			String htmlBody, int listingPosition) {
		GraphMailTemplate graphMailTemplate = findGraphMailTemplateForWrite(key);
		checkGraphMailTemplateTexts(graphMailTemplate.getType(), displayName, description, subject, htmlBody);
		graphMailTemplate.setDisplayName(displayName);
		graphMailTemplate.setDescription(description);
		graphMailTemplate.setSubject(subject);
		graphMailTemplate.setHtmlBody(htmlBody);
		graphMailTemplate.setListingPosition(listingPosition);
		return graphMailTemplateRepository.save(graphMailTemplate);
	}

	/** Neue Positionen mehrerer Vorlagen auf einmal, eine Transaktion. Der Controller hat keys und Zahlen schon geprüft. */
	@PreAuthorize("hasRole('ADMIN')")
	@Transactional
	public List<GraphMailTemplate> changeGraphMailTemplatePositions(Map<String, Integer> newPositionByKey) {
		List<GraphMailTemplate> changed = new ArrayList<>();
		for (Map.Entry<String, Integer> newPosition : newPositionByKey.entrySet()) {
			GraphMailTemplate graphMailTemplate = findGraphMailTemplateForWrite(newPosition.getKey());
			graphMailTemplate.setListingPosition(newPosition.getValue());
			changed.add(graphMailTemplate);
		}
		graphMailTemplateRepository.saveAll(changed);
		return graphMailTemplateRepository.findAll(LISTING_ORDER);
	}

	/** Löscht die Vorlage. Beim nächsten Start legt das Backend für den Typ wieder die Standardtexte an. */
	@PreAuthorize("hasRole('ADMIN')")
	@Transactional
	public GraphMailTemplate deleteGraphMailTemplate(String key) {
		GraphMailTemplate graphMailTemplate = findGraphMailTemplateForWrite(key);
		graphMailTemplateRepository.delete(graphMailTemplate);
		return graphMailTemplate;
	}

	@PreAuthorize("hasRole('ADMIN')")
	public boolean isGraphMailTemplateKeyAvailable(String key) {
		return !graphMailTemplateRepository.existsById(key);
	}

	/**
	 * Das Muster für den Editor: Betreff und Text (noch nicht gespeichert, so wie sie gerade im Formular stehen) in
	 * einer Sprache mit den Beispielwerten der Gruppen des Typs. Unbekannte Platzhalter bleiben stehen, damit man sie
	 * im Muster sieht.
	 */
	@PreAuthorize("hasRole('ADMIN')")
	public GraphMailTemplatePreview renderGraphMailTemplatePreview(GraphMailTemplateType type, String language,
			String subject, String htmlBody) {
		Map<String, Map<String, String>> sampleValuesByGroup = new LinkedHashMap<>();
		for (String groupKey : type.getPlaceholderGroupKeys()) {
			sampleValuesByGroup.put(groupKey, placeholderGroupsByKey.get(groupKey).sampleValues());
		}
		return new GraphMailTemplatePreview(
				replacePlaceholders(textInLanguage(subject, language), sampleValuesByGroup, false),
				replacePlaceholders(textInLanguage(htmlBody, language), sampleValuesByGroup, true));
	}

	/**
	 * Rendert die Vorlage des Typs in der Sprache (Rückfall de, dann die erste vorhandene) mit den echten Werten und
	 * verschickt sie im Namen des Systempostfachs. Fehlt die Vorlage oder scheitert der Versand: 503. Ohne
	 * Rechteprüfung: Das ruft das System, z. B. beim Login.
	 */
	public void sendGraphMailFromTemplate(GraphMailTemplateType type, String language,
			Map<String, Map<String, String>> placeholderValuesByGroup, String recipientEmail) {
		GraphMailTemplate graphMailTemplate = graphMailTemplateRepository.findByType(type).orElseThrow(() -> {
			log.error("keine Mailvorlage für den Typ {}", type);
			return new GraphMailUnavailableException("der Mailversand ist gerade nicht möglich");
		});
		for (String groupKey : type.getPlaceholderGroupKeys()) {
			if (!placeholderValuesByGroup.containsKey(groupKey)) {
				throw new IllegalArgumentException("Werte der Gruppe " + groupKey + " fehlen");
			}
		}
		graphMailSendService.sendGraphMail(new GraphMailMessage(properties.systemSenderAddress(), recipientEmail,
				replacePlaceholders(textInLanguage(graphMailTemplate.getSubject(), language), placeholderValuesByGroup,
						false),
				replacePlaceholders(textInLanguage(graphMailTemplate.getHtmlBody(), language), placeholderValuesByGroup,
						true)));
	}

	/** Jedes mehrsprachige Feld muss ein JSON-Objekt mit Texten sein, Betreff und Text nur bekannte Platzhalter tragen. */
	private void checkGraphMailTemplateTexts(GraphMailTemplateType type, String displayName, String description,
			String subject, String htmlBody) {
		checkMultilingualText("displayName", displayName);
		checkMultilingualText("description", description);
		checkMultilingualText("subject", subject);
		checkMultilingualText("htmlBody", htmlBody);
		Set<String> unknownPlaceholders = new LinkedHashSet<>();
		unknownPlaceholders.addAll(findUnknownPlaceholders(type, subject));
		unknownPlaceholders.addAll(findUnknownPlaceholders(type, htmlBody));
		if (!unknownPlaceholders.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					"unbekannte Platzhalter für den Typ " + type + ": " + String.join(", ", unknownPlaceholders));
		}
	}

	private static void checkMultilingualText(String fieldName, String multilingualJson) {
		JsonNode texts = parseMultilingual(multilingualJson);
		if (texts == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					fieldName + " muss ein mehrsprachiges JSON-Objekt sein, z. B. {\"de\": \"…\"}");
		}
		for (Map.Entry<String, JsonNode> text : texts.properties()) {
			if (!text.getValue().isString()) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
						fieldName + ": der Wert für " + text.getKey() + " ist kein Text");
			}
		}
	}

	/** Alle {gruppe.name} im mehrsprachigen Feld, die der Typ nicht kennt. */
	private Set<String> findUnknownPlaceholders(GraphMailTemplateType type, String multilingualJson) {
		Set<String> unknown = new LinkedHashSet<>();
		JsonNode texts = parseMultilingual(multilingualJson);
		if (texts == null) {
			return unknown;
		}
		for (Map.Entry<String, JsonNode> text : texts.properties()) {
			Matcher matcher = PLACEHOLDER.matcher(text.getValue().asString(""));
			while (matcher.find()) {
				String groupKey = matcher.group(1);
				String placeholderName = matcher.group(2);
				boolean known = type.getPlaceholderGroupKeys().contains(groupKey)
						&& placeholderGroupsByKey.get(groupKey).placeholderNames().contains(placeholderName);
				if (!known) {
					unknown.add(matcher.group());
				}
			}
		}
		return unknown;
	}

	/** Der Text in der Sprache, sonst de, sonst der erste nicht leere, sonst leer. */
	private static String textInLanguage(String multilingualJson, String language) {
		JsonNode texts = parseMultilingual(multilingualJson);
		if (texts == null) {
			return "";
		}
		String text = texts.path(language == null ? DEFAULT_LANGUAGE : language).asString("");
		if (text.isBlank()) {
			text = texts.path(DEFAULT_LANGUAGE).asString("");
		}
		if (text.isBlank()) {
			for (Map.Entry<String, JsonNode> entry : texts.properties()) {
				if (!entry.getValue().asString("").isBlank()) {
					text = entry.getValue().asString("");
					break;
				}
			}
		}
		return text;
	}

	/**
	 * Ersetzt jedes {gruppe.name}, dessen Wert übergeben wurde. Im HTML werden die Werte escaped, damit ein Name wie
	 * „<b>“ im Text steht statt als Tag zu wirken. Unbekannte Platzhalter bleiben, wie sie sind.
	 */
	private static String replacePlaceholders(String text, Map<String, Map<String, String>> valuesByGroup,
			boolean html) {
		Matcher matcher = PLACEHOLDER.matcher(text);
		StringBuilder result = new StringBuilder();
		while (matcher.find()) {
			Map<String, String> groupValues = valuesByGroup.get(matcher.group(1));
			String value = groupValues == null ? null : groupValues.get(matcher.group(2));
			String replacement = value == null ? matcher.group() : (html ? escapeHtml(value) : value);
			matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
		}
		matcher.appendTail(result);
		return result.toString();
	}

	/** Ein JSON-Objekt, sonst null. */
	private static JsonNode parseMultilingual(String multilingualJson) {
		if (multilingualJson == null || multilingualJson.isBlank()) {
			return null;
		}
		try {
			JsonNode node = JSON.readTree(multilingualJson);
			return node.isObject() ? node : null;
		} catch (JacksonException e) {
			return null;
		}
	}

	private static String escapeHtml(String text) {
		return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
	}

	/** Zum Schreiben aus der Datenbank, so bleibt die Entity an der Transaktion. */
	private GraphMailTemplate findGraphMailTemplateForWrite(String key) {
		return graphMailTemplateRepository.findById(key)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "keine Mailvorlage mit diesem key"));
	}

}
