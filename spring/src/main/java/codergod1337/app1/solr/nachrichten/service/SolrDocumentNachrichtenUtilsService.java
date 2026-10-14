package codergod1337.app1.solr.nachrichten.service;

import codergod1337.app1.solr.core.model.SolrCore;
import codergod1337.app1.solr.field.model.SolrField;
import codergod1337.app1.solr.field.model.SolrFieldType;
import codergod1337.app1.solr.field.service.SolrFieldService;
import codergod1337.app1.solr.nachrichten.model.NachrichtenAccessForClient;
import codergod1337.app1.system.security.CurrentUsersProvider;
import codergod1337.app1.system.user.model.UsersDebuffType;
import codergod1337.app1.system.user.model.UsersDebuffs;
import codergod1337.app1.system.user.service.UsersDebuffsService;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.apache.solr.client.solrj.util.ClientUtils;
import org.apache.solr.common.params.ModifiableSolrParams;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Alles, was der SolrDocumentNachrichtenService zum Lesen braucht, aber selbst kein Lesen eines Dokuments ist: die
 * Rollen als Tür, der Rechte-Filter, die Felder und Bedingungen einer Anfrage. Spricht nie selbst mit Solr. Derselbe
 * Schnitt wie SolrDocumentKontakteUtilsService, die Rechte wie im DMS: Jede Nachricht trägt readAcl aus ihrer
 * Nachrichtenart, nur mit einer Rolle darin kommt sie aus Solr, ADMIN sieht alles.
 *
 * Eine Bedingung auf ein Feld vom Typ EMAIL trifft ohne Rücksicht auf Groß- und Kleinschreibung, aber exakt die ganze
 * Adresse, wie im Kern KONTAKTE.
 */
@Service
public class SolrDocumentNachrichtenUtilsService {

	/** Die Tür des Kerns: ohne diese AR (oder die Suche) kein Lesen, außer für ADMIN */
	public static final String ACCESS_ROLE_KEY = "NACHRICHTEN";

	/** Die AR der Suche: gibt alle Felder und Hooks frei, readAcl gilt weiter */
	public static final String SEARCH_ACCESS_ROLE_KEY = "NACHRICHTEN_SUCHE";

	public static final String ADMIN_ACCESS_ROLE_KEY = "ADMIN";

	/** Jedes Feld und jeder Hook in jedem Kern, ein User-Recht */
	public static final String ALL_FIELDS_ACCESS_ROLE_KEY = "SOLR_USERS_VIEW_ALL";

	private final CurrentUsersProvider currentUsersProvider;
	private final SolrFieldService solrFieldService;
	private final UsersDebuffsService usersDebuffsService;

	public SolrDocumentNachrichtenUtilsService(CurrentUsersProvider currentUsersProvider,
			SolrFieldService solrFieldService, UsersDebuffsService usersDebuffsService) {
		this.currentUsersProvider = currentUsersProvider;
		this.solrFieldService = solrFieldService;
		this.usersDebuffsService = usersDebuffsService;
	}

	// ===== Rollen =====

	public boolean isAdmin() {
		return currentUsersProvider.getCurrentUsersAccessRoleKeys().contains(ADMIN_ACCESS_ROLE_KEY);
	}

	/** Jedes Feld und jeder Hook: ADMIN oder SOLR_USERS_VIEW_ALL; readAcl gilt für SOLR_USERS_VIEW_ALL trotzdem */
	public boolean isEveryFieldReadable() {
		List<String> accessRoleKeys = currentUsersProvider.getCurrentUsersAccessRoleKeys();
		return accessRoleKeys.contains(ADMIN_ACCESS_ROLE_KEY) || accessRoleKeys.contains(ALL_FIELDS_ACCESS_ROLE_KEY);
	}

	public boolean isSearchAllowed() {
		List<String> accessRoleKeys = currentUsersProvider.getCurrentUsersAccessRoleKeys();
		return accessRoleKeys.contains(ADMIN_ACCESS_ROLE_KEY) || accessRoleKeys.contains(SEARCH_ACCESS_ROLE_KEY);
	}

	public void requireAccessRole() {
		// Jede Lesemethode ruft das zuerst: NACHRICHTEN, oder NACHRICHTEN_SUCHE, damit ein Suchtreffer sich öffnen lässt;
		// ADMIN darf immer. 403 vor Solr.
		List<String> accessRoleKeys = currentUsersProvider.getCurrentUsersAccessRoleKeys();
		if (!accessRoleKeys.contains(ADMIN_ACCESS_ROLE_KEY) && !accessRoleKeys.contains(ACCESS_ROLE_KEY)
				&& !accessRoleKeys.contains(SEARCH_ACCESS_ROLE_KEY)) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "keine Rolle " + ACCESS_ROLE_KEY);
		}
	}

	public void requireSearchAccessRole() {
		// Die Suche ruft das: NACHRICHTEN_SUCHE oder ADMIN, sonst 403 vor Solr
		if (!isSearchAllowed()) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "keine Suche ohne " + SEARCH_ACCESS_ROLE_KEY);
		}
	}

	// ===== Rechte, Felder und Bedingungen einer Anfrage =====

	public void buildUserReadAclForSolrRequest(ModifiableSolrParams params) {
		// Jede Lesemethode ruft das für jeden außer ADMIN: eine Rolle des Users in readAcl, oder readAcl leer (eine
		// Nachrichtenart ohne Leserollen schränkt nicht ein). Keine guid-Freigabe wie im DMS: Nachrichten gibt niemand
		// einzeln frei. -readAcl:* heißt readAcl leer, das *:* davor braucht Solr für ein NOT innerhalb von OR.
		List<String> accessRoleKeys = currentUsersProvider.getCurrentUsersAccessRoleKeys();
		if (accessRoleKeys.isEmpty()) {
			params.add("fq", "*:* -readAcl:*");
			return;
		}
		List<String> escapedRoleKeys = new ArrayList<>();
		for (String accessRoleKey : accessRoleKeys) {
			escapedRoleKeys.add(ClientUtils.escapeQueryChars(accessRoleKey));
		}
		// ergibt z. B. readAcl:(USER OR VERTRIEB) OR (*:* -readAcl:*)
		params.add("fq", "readAcl:(" + String.join(" OR ", escapedRoleKeys) + ") OR (*:* -readAcl:*)");
	}

	public List<String> buildUserSolrResponseFieldList(ModifiableSolrParams params) {
		// Jede Lesemethode ruft das vor der Anfrage auf, außer bei Vollsicht: setzt fl auf die Felder, die der User sehen
		// darf, und gibt dieselbe Liste zurück, z. B. für die Prüfung der Bedingungen. id immer.
		List<String> fieldNames = readableFieldNamesOfCurrentUser();
		params.set("fl", String.join(",", fieldNames));
		return fieldNames;
	}

	public List<String> readableFieldNamesOfCurrentUser() {
		// Mit Vollsicht jedes Feld des Kerns, sonst id und die Felder mit passender Lese-AR
		List<String> accessRoleKeys = currentUsersProvider.getCurrentUsersAccessRoleKeys();
		boolean everyField = isEveryFieldReadable();
		List<String> fieldNames = new ArrayList<>();
		for (SolrField solrField : solrFieldService.getSolrFieldsByCoreKey(SolrDocumentNachrichtenService.CORE_KEY)) {
			boolean readable = everyField || SolrCore.ID_FIELD_NAME.equals(solrField.getName())
					|| solrField.getReadAccessRoleKeys().stream().anyMatch(accessRoleKeys::contains);
			if (readable) {
				fieldNames.add(solrField.getName());
			}
		}
		return fieldNames;
	}

	public List<String> buildUserDebuffsForSolrRequest(ModifiableSolrParams params) {
		// Für jeden ohne Vollsicht: je Debuff vom Typ RESOLVE ein fq solrFieldName:(wert1 OR wert2). Gibt die Namen der
		// Debuff-Felder zurück: Eine Bedingung des Clients auf so ein Feld wird verworfen, der Debuff steht über jeder Rolle.
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
		// Je Feld ein fq feld:(wert1 OR wert2), gleiches Feld heißt ODER. readableFieldNames null heißt, der User darf
		// jedes Feld; ein Debuff-Feld lässt die Bedingung weg. Ein Feld vom Typ EMAIL trifft ohne Groß- und Kleinschreibung.
		Set<String> emailFieldNames = emailFieldNames();
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
				terms.add(emailFieldNames.contains(fieldName) ? caseInsensitiveExactRegex(value.trim())
						: ClientUtils.escapeQueryChars(value));
			}
			// ergibt z. B. messageSubClassKey:(AUFTRAG) oder fromAddress:(/[iI][nN][fF][oO]\@.../)
			params.add("fq", fieldName + ":(" + String.join(" OR ", terms) + ")");
		}
	}

	/** Die Felder vom Typ EMAIL im Kern: Absender, Gesendet von, An, Cc, Bcc */
	public Set<String> emailFieldNames() {
		return solrFieldService.getSolrFieldsByCoreKey(SolrDocumentNachrichtenService.CORE_KEY).stream()
				.filter(solrField -> solrField.getType() == SolrFieldType.EMAIL).map(SolrField::getName)
				.collect(Collectors.toSet());
	}

	/**
	 * Ein regulärer Ausdruck, der genau diesen Wert trifft, ohne Rücksicht auf Groß- und Kleinschreibung: jeder
	 * Buchstabe als [kK], jedes andere Zeichen mit Backslash wörtlich. Dieselbe Methode wie im Kern KONTAKTE.
	 */
	static String caseInsensitiveExactRegex(String value) {
		StringBuilder pattern = new StringBuilder("/");
		value.codePoints().forEach(codePoint -> {
			int lower = Character.toLowerCase(codePoint);
			int upper = Character.toUpperCase(codePoint);
			if (lower != upper) {
				pattern.append('[').appendCodePoint(lower).appendCodePoint(upper).append(']');
			} else {
				pattern.append('\\').appendCodePoint(codePoint);
			}
		});
		return pattern.append('/').toString();
	}

	public NachrichtenAccessForClient accessForClient() {
		// Die Masken fragen das einmal: was der User im Kern NACHRICHTEN lesen und wonach er auflösen darf
		List<String> readableFieldNames = readableFieldNamesOfCurrentUser();
		return new NachrichtenAccessForClient(SolrDocumentNachrichtenService.CORE_KEY, isEveryFieldReadable(),
				isSearchAllowed(), readableFieldNames, readableFieldNames);
	}

	// ===== Suche: Wörter und Gründe =====
	// Dieselben zwei Methoden stehen gleich in den Utils von DMS, ARTIKEL und KONTAKTE (vierte Kopie); der Umzug in ein
	// kernunabhängiges Paket steht im Register docs/backend/solr-zugriff.md als offen.

	public List<String> searchWordsOf(String text) {
		// Die Suche ruft das für die Gründe in Java: die Wörter des Suchtexts klein und ohne + davor; Wörter mit - davor
		// sind ausgeschlossen und fehlen.
		List<String> words = new ArrayList<>();
		for (String word : text.split("\\s+")) {
			if (word.isEmpty() || word.startsWith("-")) {
				continue;
			}
			String bare = word.startsWith("+") ? word.substring(1) : word;
			if (!bare.isEmpty()) {
				words.add(bare.toLowerCase(Locale.ROOT));
			}
		}
		return words;
	}

	public String startingWithSearchWord(Object fieldValue, List<String> searchWords) {
		// Die Suche ruft das je durchsuchtem Feld ohne Highlighting: der erste Wert, der mit einem Suchwort beginnt (so
		// trifft der Zwilling), als Schnipsel mit STX und ETX um den Anfang; null, wenn kein Wert passt.
		if (fieldValue == null) {
			return null;
		}
		Collection<?> values = fieldValue instanceof Collection<?> many ? many : List.of(fieldValue);
		for (Object value : values) {
			String valueText = String.valueOf(value);
			String lowerValueText = valueText.toLowerCase(Locale.ROOT);
			for (String word : searchWords) {
				if (lowerValueText.startsWith(word)) {
					return "\u0002" + valueText.substring(0, word.length()) + "\u0003" + valueText.substring(word.length());
				}
			}
		}
		return null;
	}

}
