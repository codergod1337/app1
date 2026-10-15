package codergod1337.app1.solr.todojob.service;

import codergod1337.app1.solr.core.model.SolrCore;
import codergod1337.app1.solr.field.model.SolrField;
import codergod1337.app1.solr.field.service.SolrFieldService;
import codergod1337.app1.solr.todojob.model.TodojobAccessForClient;
import codergod1337.app1.solr.todojob.model.TodojobFieldNames;
import codergod1337.app1.solr.todojob.model.TodojobRueckmeldungEntryType;
import codergod1337.app1.system.security.CurrentUsersProvider;
import codergod1337.app1.system.user.model.UsersDebuffType;
import codergod1337.app1.system.user.model.UsersDebuffs;
import codergod1337.app1.system.user.service.UsersDebuffsService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.solr.client.solrj.util.ClientUtils;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.params.ModifiableSolrParams;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * Alles, was der SolrDocumentTodojobService braucht, aber selbst kein Lesen oder Schreiben eines Jobs ist: die Tür, die
 * Parteien-Prüfung als fq und in Java, die Felder einer Anfrage, die JSON-Einträge der Listen. Spricht nie selbst mit
 * Solr. Derselbe Schnitt wie SolrDocumentNachrichtenUtilsService.
 *
 * Die Sicht auf einen Job ist die Parteien-Prüfung (User 2026-10-16): Owner oder Servant, jeweils über guid, AR oder
 * ARC des Tokens, sonst niemand, auch ADMIN nicht über die User-Endpunkte. Kein readAcl, keine guid-Freigabe, keine
 * Tür-Rolle: Jobs darf erst mal jeder Angemeldete empfangen, eine Rolle fürs Erteilen kommt später. Den Auszug für die
 * Mappen (zweiter Weg) prüft der Service über den Anzeigeort.
 */
@Service
public class SolrDocumentTodojobUtilsService {

	public static final String ADMIN_ACCESS_ROLE_KEY = "ADMIN";

	/** Jedes Feld und jeder Hook in jedem Kern, ein User-Recht */
	public static final String ALL_FIELDS_ACCESS_ROLE_KEY = "SOLR_USERS_VIEW_ALL";

	private final CurrentUsersProvider currentUsersProvider;
	private final SolrFieldService solrFieldService;
	private final UsersDebuffsService usersDebuffsService;
	private final ObjectMapper objectMapper;

	public SolrDocumentTodojobUtilsService(CurrentUsersProvider currentUsersProvider,
			SolrFieldService solrFieldService, UsersDebuffsService usersDebuffsService, ObjectMapper objectMapper) {
		this.currentUsersProvider = currentUsersProvider;
		this.solrFieldService = solrFieldService;
		this.usersDebuffsService = usersDebuffsService;
		this.objectMapper = objectMapper;
	}

	// ===== Rollen =====

	public void requireCurrentUser() {
		// Jede Methode des Kerns ruft das zuerst. Keine Tür-Rolle (User 2026-10-16): Jobs darf erst mal jeder empfangen,
		// eine Rolle fürs Erteilen kommt später dort. Ohne angemeldeten User wirft der Provider.
		currentUsersProvider.getCurrentUsersGuid();
	}

	/** Jedes Feld und jeder Hook: ADMIN oder SOLR_USERS_VIEW_ALL */
	public boolean isEveryFieldReadable() {
		List<String> accessRoleKeys = currentUsersProvider.getCurrentUsersAccessRoleKeys();
		return accessRoleKeys.contains(ADMIN_ACCESS_ROLE_KEY) || accessRoleKeys.contains(ALL_FIELDS_ACCESS_ROLE_KEY);
	}

	// ===== die Parteien =====

	/**
	 * Die Parteien-Prüfung als ein fq: der User ist Owner oder Servant, über guid, eine seiner ARs oder seine ARC.
	 * Jede Lesemethode für Parteien hängt das an, ergibt z. B.
	 * (ownerGuid:3f2a… OR servantGuids:3f2a… OR ownerAccessRoleKeys:(USER OR VERTRIEB) OR servantAccessRoleKeys:(…) OR
	 * ownerArcKeys:ARC_VERTRIEB OR servantArcKeys:ARC_VERTRIEB)
	 */
	public String partyFilterQueryOfCurrentUser() {
		String guid = ClientUtils.escapeQueryChars(currentUsersProvider.getCurrentUsersGuid().toString());
		List<String> parts = new ArrayList<>();
		parts.add(TodojobFieldNames.OWNER_GUID + ":" + guid);
		parts.add(TodojobFieldNames.SERVANT_GUIDS + ":" + guid);
		List<String> accessRoleKeys = currentUsersProvider.getCurrentUsersAccessRoleKeys();
		if (!accessRoleKeys.isEmpty()) {
			String roles = "(" + String.join(" OR ", accessRoleKeys.stream().map(ClientUtils::escapeQueryChars).toList()) + ")";
			parts.add(TodojobFieldNames.OWNER_ACCESS_ROLE_KEYS + ":" + roles);
			parts.add(TodojobFieldNames.SERVANT_ACCESS_ROLE_KEYS + ":" + roles);
		}
		String arcKey = currentUsersProvider.getCurrentUsersAccessRoleCollectionKey();
		if (arcKey != null) {
			String arc = ClientUtils.escapeQueryChars(arcKey);
			parts.add(TodojobFieldNames.OWNER_ARC_KEYS + ":" + arc);
			parts.add(TodojobFieldNames.SERVANT_ARC_KEYS + ":" + arc);
		}
		return "(" + String.join(" OR ", parts) + ")";
	}

	/** Nur die Servant-Seite als fq, für den Stapel „Für mich“ */
	public String servantFilterQueryOfCurrentUser() {
		return sideFilterQuery(TodojobFieldNames.SERVANT_GUIDS, TodojobFieldNames.SERVANT_ACCESS_ROLE_KEYS,
				TodojobFieldNames.SERVANT_ARC_KEYS);
	}

	/** Nur die Owner-Seite als fq, für den Stapel „Von mir“ */
	public String ownerFilterQueryOfCurrentUser() {
		return sideFilterQuery(TodojobFieldNames.OWNER_GUID, TodojobFieldNames.OWNER_ACCESS_ROLE_KEYS,
				TodojobFieldNames.OWNER_ARC_KEYS);
	}

	private String sideFilterQuery(String guidField, String accessRoleField, String arcField) {
		String guid = ClientUtils.escapeQueryChars(currentUsersProvider.getCurrentUsersGuid().toString());
		List<String> parts = new ArrayList<>();
		parts.add(guidField + ":" + guid);
		List<String> accessRoleKeys = currentUsersProvider.getCurrentUsersAccessRoleKeys();
		if (!accessRoleKeys.isEmpty()) {
			parts.add(accessRoleField + ":("
					+ String.join(" OR ", accessRoleKeys.stream().map(ClientUtils::escapeQueryChars).toList()) + ")");
		}
		String arcKey = currentUsersProvider.getCurrentUsersAccessRoleCollectionKey();
		if (arcKey != null) {
			parts.add(arcField + ":" + ClientUtils.escapeQueryChars(arcKey));
		}
		return "(" + String.join(" OR ", parts) + ")";
	}

	/** Dieselbe Prüfung in Java an einem geladenen Job: handelt der User als Owner? */
	public boolean isOwnerSide(Map<String, Object> job) {
		String guid = currentUsersProvider.getCurrentUsersGuid().toString();
		return guid.equals(firstValueOf(job.get(TodojobFieldNames.OWNER_GUID)))
				|| matchesRolesOrArc(job, TodojobFieldNames.OWNER_ACCESS_ROLE_KEYS, TodojobFieldNames.OWNER_ARC_KEYS);
	}

	/** Handelt der User als Servant: genannt, über eine AR oder seine ARC? */
	public boolean isServantSide(Map<String, Object> job) {
		String guid = currentUsersProvider.getCurrentUsersGuid().toString();
		return valuesOf(job.get(TodojobFieldNames.SERVANT_GUIDS)).contains(guid)
				|| matchesRolesOrArc(job, TodojobFieldNames.SERVANT_ACCESS_ROLE_KEYS, TodojobFieldNames.SERVANT_ARC_KEYS);
	}

	private boolean matchesRolesOrArc(Map<String, Object> job, String accessRoleField, String arcField) {
		List<String> accessRoleKeys = currentUsersProvider.getCurrentUsersAccessRoleKeys();
		if (valuesOf(job.get(accessRoleField)).stream().anyMatch(accessRoleKeys::contains)) {
			return true;
		}
		String arcKey = currentUsersProvider.getCurrentUsersAccessRoleCollectionKey();
		return arcKey != null && valuesOf(job.get(arcField)).contains(arcKey);
	}

	/** Ein Job an eine Gruppe: mehr als eine Person, oder eine AR oder ARC. Nur dann ist ein Claim möglich. */
	public static boolean isGroupJob(Map<String, Object> job) {
		return valuesOf(job.get(TodojobFieldNames.SERVANT_GUIDS)).size() > 1
				|| !valuesOf(job.get(TodojobFieldNames.SERVANT_ACCESS_ROLE_KEYS)).isEmpty()
				|| !valuesOf(job.get(TodojobFieldNames.SERVANT_ARC_KEYS)).isEmpty();
	}

	public String currentUsersGuid() {
		return currentUsersProvider.getCurrentUsersGuid().toString();
	}

	public UUID currentUsersGuidAsUuid() {
		return currentUsersProvider.getCurrentUsersGuid();
	}

	// ===== Felder und Bedingungen einer Anfrage =====

	public List<String> buildUserSolrResponseFieldList(ModifiableSolrParams params) {
		// setzt fl auf die Felder, die der User sehen darf, und gibt dieselbe Liste zurück. id immer.
		List<String> fieldNames = readableFieldNamesOfCurrentUser();
		params.set("fl", String.join(",", fieldNames));
		return fieldNames;
	}

	public List<String> readableFieldNamesOfCurrentUser() {
		// Mit Vollsicht jedes Feld des Kerns, sonst id und die Felder mit passender Lese-AR
		List<String> accessRoleKeys = currentUsersProvider.getCurrentUsersAccessRoleKeys();
		boolean everyField = isEveryFieldReadable();
		List<String> fieldNames = new ArrayList<>();
		for (SolrField solrField : solrFieldService.getSolrFieldsByCoreKey(SolrDocumentTodojobService.CORE_KEY)) {
			boolean readable = everyField || SolrCore.ID_FIELD_NAME.equals(solrField.getName())
					|| solrField.getReadAccessRoleKeys().stream().anyMatch(accessRoleKeys::contains);
			if (readable) {
				fieldNames.add(solrField.getName());
			}
		}
		return fieldNames;
	}

	/** Die Hook-Zeilen des Kerns, nach Key: beim Erteilen darf nur ein bekannter Hook Werte bekommen */
	public List<String> hookKeysOfCore() {
		return solrFieldService.getSolrFieldsByCoreKey(SolrDocumentTodojobService.CORE_KEY).stream()
				.filter(SolrField::isHookField).map(SolrField::getName).toList();
	}

	/** Die Felder des Kerns mit Suchgewicht ab 0, in Listenreihenfolge: daraus baut die Suche die qf */
	public List<SolrField> searchedFieldsOfCore() {
		return solrFieldService.getSolrFieldsByCoreKey(SolrDocumentTodojobService.CORE_KEY).stream()
				.filter(solrField -> solrField.getSearchWeight() >= 0).toList();
	}

	public List<String> buildUserDebuffsForSolrRequest(ModifiableSolrParams params) {
		// Für jeden ohne Vollsicht: je Debuff vom Typ RESOLVE ein fq. Der Debuff steht über jeder Rolle.
		List<String> debuffedFieldNames = new ArrayList<>();
		if (!currentUsersProvider.hasCurrentUsersDebuffs()) {
			return debuffedFieldNames;
		}
		UUID usersGuid = currentUsersProvider.getCurrentUsersGuid();
		for (UsersDebuffs usersDebuffs : usersDebuffsService.getUsersDebuffsByUsersGuid(usersGuid)) {
			if (usersDebuffs.getType() != UsersDebuffType.RESOLVE) {
				continue;
			}
			List<String> escapedValues = new ArrayList<>();
			for (String value : usersDebuffs.getValues()) {
				escapedValues.add(ClientUtils.escapeQueryChars(value));
			}
			params.add("fq", usersDebuffs.getSolrFieldName() + ":(" + String.join(" OR ", escapedValues) + ")");
			debuffedFieldNames.add(usersDebuffs.getSolrFieldName());
		}
		return debuffedFieldNames;
	}

	public void buildClientConditionsForSolrRequest(ModifiableSolrParams params,
			Map<String, List<String>> valuesByFieldName, List<String> readableFieldNames,
			List<String> debuffedFieldNames) {
		// Je Feld ein fq feld:(wert1 OR wert2). readableFieldNames null heißt, der User darf jedes Feld.
		for (Map.Entry<String, List<String>> condition : valuesByFieldName.entrySet()) {
			String fieldName = condition.getKey();
			if (readableFieldNames != null && !readableFieldNames.contains(fieldName)) {
				throw new ResponseStatusException(HttpStatus.FORBIDDEN, "keine Bedingung auf " + fieldName);
			}
			if (debuffedFieldNames.contains(fieldName)) {
				continue;
			}
			if (condition.getValue() == null || condition.getValue().isEmpty()) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "keine Werte für " + fieldName);
			}
			List<String> terms = new ArrayList<>();
			for (String value : condition.getValue()) {
				terms.add(ClientUtils.escapeQueryChars(value));
			}
			params.add("fq", fieldName + ":(" + String.join(" OR ", terms) + ")");
		}
	}

	public TodojobAccessForClient accessForClient() {
		List<String> rueckmeldungsArten = new ArrayList<>();
		for (TodojobRueckmeldungEntryType type : TodojobRueckmeldungEntryType.values()) {
			if (type.isRueckmeldungsArt()) {
				rueckmeldungsArten.add(type.name());
			}
		}
		return new TodojobAccessForClient(SolrDocumentTodojobService.CORE_KEY, isEveryFieldReadable(),
				readableFieldNamesOfCurrentUser(), rueckmeldungsArten);
	}

	// ===== JSON-Einträge der Listen rueckmeldung, rueckfragen, rueckantworten, log =====

	/** Ein Eintrag als JSON-String, Zeitpunkte als ISO-Text, in der Reihenfolge der Map */
	public String toJsonEntry(Map<String, Object> entry) {
		try {
			return objectMapper.writeValueAsString(entry);
		} catch (JacksonException e) {
			throw new IllegalStateException("Eintrag lässt sich nicht als JSON schreiben", e);
		}
	}

	/** Alle Einträge eines Listenfelds als Maps, in Reihenfolge; ein Eintrag, der kein JSON ist, wird übersprungen */
	public List<Map<String, Object>> parseEntries(Object listFieldValue) {
		List<Map<String, Object>> entries = new ArrayList<>();
		for (String text : valuesOf(listFieldValue)) {
			try {
				entries.add(objectMapper.readValue(text, new TypeReference<Map<String, Object>>() {
				}));
			} catch (JacksonException e) {
				// ein kaputter Eintrag bricht den Job nicht, er fehlt nur in der Auswertung
			}
		}
		return entries;
	}

	/** Der Grundstock jedes Eintrags: Zeitpunkt und User, in dieser Reihenfolge */
	public Map<String, Object> newEntry(Instant at) {
		Map<String, Object> entry = new LinkedHashMap<>();
		entry.put("at", at.toString());
		entry.put("user", currentUsersGuid());
		return entry;
	}

	// ===== Werte =====

	/** Alle Werte eines Feldes als Texte, ein einzelner Wert als Liste mit einem Eintrag, leer bei nichts */
	public static List<String> valuesOf(Object fieldValue) {
		if (fieldValue == null) {
			return List.of();
		}
		List<String> values = new ArrayList<>();
		for (Object value : fieldValue instanceof Collection<?> many ? many : List.of(fieldValue)) {
			String text = String.valueOf(value);
			if (!text.isBlank()) {
				values.add(text);
			}
		}
		return values;
	}

	/** Der erste Wert eines Feldes als Text, null bei nichts */
	public static String firstValueOf(Object fieldValue) {
		List<String> values = valuesOf(fieldValue);
		return values.isEmpty() ? null : values.get(0);
	}

	/** Eine Zahl aus einem Feld, 0 wenn es fehlt */
	public static long longValueOf(Object fieldValue) {
		String text = firstValueOf(fieldValue);
		if (text == null) {
			return 0;
		}
		try {
			return Long.parseLong(text);
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	/** Date wird Instant, multiValued kommt als Liste, einwertig als Wert: wie SolrDocumentService.toMap */
	public static Map<String, Object> toMap(SolrDocument document) {
		Map<String, Object> map = new LinkedHashMap<>();
		for (String name : document.getFieldNames()) {
			map.put(name, fromSolrValue(document.get(name)));
		}
		return map;
	}

	private static Object fromSolrValue(Object value) {
		if (value instanceof Date date) {
			return date.toInstant();
		}
		if (value instanceof Collection<?> values) {
			List<Object> converted = new ArrayList<>(values.size());
			for (Object element : values) {
				converted.add(fromSolrValue(element));
			}
			return converted;
		}
		return value;
	}

}
