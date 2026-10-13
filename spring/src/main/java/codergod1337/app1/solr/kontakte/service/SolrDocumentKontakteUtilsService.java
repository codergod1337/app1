package codergod1337.app1.solr.kontakte.service;

import codergod1337.app1.solr.core.model.SolrCore;
import codergod1337.app1.solr.field.model.SolrField;
import codergod1337.app1.solr.field.model.SolrFieldType;
import codergod1337.app1.solr.field.service.SolrFieldService;
import codergod1337.app1.solr.kontakte.model.KontakteAccessForClient;
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
 * Alles, was der SolrDocumentKontakteService zum Lesen braucht, aber selbst kein Lesen eines Dokuments ist: die Rolle
 * als Tür, die Felder und Bedingungen einer Anfrage. Spricht nie selbst mit Solr. Derselbe Schnitt wie
 * SolrDocumentArtikelUtilsService, der Kern ist wie ARTIKEL ein Spiegel aus ABAS: kein readAcl, kein Status, kein
 * Schreiben durch einen Menschen.
 *
 * Eine Bedingung auf ein Feld vom Typ EMAIL trifft ohne Rücksicht auf Groß- und Kleinschreibung, aber exakt die ganze
 * Adresse (User 2026-10-13): ABAS speichert E-Mails in beliebiger Schreibweise, eine Mail kommt in einer anderen.
 */
@Service
public class SolrDocumentKontakteUtilsService {

	/** Die Tür des Kerns: ohne diese AR kein Lesen, außer für ADMIN */
	public static final String ACCESS_ROLE_KEY = "KONTAKTE";

	/** Die AR der Suche: gibt alle Felder und Hooks frei, denn wer suchen darf, kommt ohnehin an alles */
	public static final String SEARCH_ACCESS_ROLE_KEY = "KONTAKTE_SUCHE";

	public static final String ADMIN_ACCESS_ROLE_KEY = "ADMIN";

	/** Jedes Feld und jeder Hook in jedem Kern, ein User-Recht */
	public static final String ALL_FIELDS_ACCESS_ROLE_KEY = "SOLR_USERS_VIEW_ALL";

	private final CurrentUsersProvider currentUsersProvider;
	private final SolrFieldService solrFieldService;
	private final UsersDebuffsService usersDebuffsService;

	public SolrDocumentKontakteUtilsService(CurrentUsersProvider currentUsersProvider,
			SolrFieldService solrFieldService, UsersDebuffsService usersDebuffsService) {
		this.currentUsersProvider = currentUsersProvider;
		this.solrFieldService = solrFieldService;
		this.usersDebuffsService = usersDebuffsService;
	}

	// ===== Rollen =====

	/** Vollsicht im Kern KONTAKTE: ADMIN, SOLR_USERS_VIEW_ALL oder KONTAKTE_SUCHE, dann keine fl und keine Debuffs */
	public boolean isEveryFieldReadable() {
		List<String> accessRoleKeys = currentUsersProvider.getCurrentUsersAccessRoleKeys();
		return accessRoleKeys.contains(ADMIN_ACCESS_ROLE_KEY) || accessRoleKeys.contains(ALL_FIELDS_ACCESS_ROLE_KEY)
				|| accessRoleKeys.contains(SEARCH_ACCESS_ROLE_KEY);
	}

	public boolean isSearchAllowed() {
		List<String> accessRoleKeys = currentUsersProvider.getCurrentUsersAccessRoleKeys();
		return accessRoleKeys.contains(ADMIN_ACCESS_ROLE_KEY) || accessRoleKeys.contains(SEARCH_ACCESS_ROLE_KEY);
	}

	public void requireAccessRole() {
		// Jede Lesemethode ruft das zuerst: die Tür KONTAKTE, ADMIN darf immer. 403 vor Solr.
		List<String> accessRoleKeys = currentUsersProvider.getCurrentUsersAccessRoleKeys();
		if (!accessRoleKeys.contains(ADMIN_ACCESS_ROLE_KEY) && !accessRoleKeys.contains(ACCESS_ROLE_KEY)) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "keine Rolle " + ACCESS_ROLE_KEY);
		}
	}

	public void requireSearchAccessRole() {
		// Die Suche ruft das: KONTAKTE_SUCHE oder ADMIN, sonst 403 vor Solr
		if (!isSearchAllowed()) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "keine Suche ohne " + SEARCH_ACCESS_ROLE_KEY);
		}
	}

	// ===== Felder und Bedingungen einer Anfrage =====

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
		for (SolrField solrField : solrFieldService.getSolrFieldsByCoreKey(SolrDocumentKontakteService.CORE_KEY)) {
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
		Set<String> emailFieldNames = solrFieldService.getSolrFieldsByCoreKey(SolrDocumentKontakteService.CORE_KEY)
				.stream().filter(solrField -> solrField.getType() == SolrFieldType.EMAIL).map(SolrField::getName)
				.collect(Collectors.toSet());
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
			// ergibt z. B. kundenNummer:(120120) oder email:(/[iI][nN][fF][oO]\@.../)
			params.add("fq", fieldName + ":(" + String.join(" OR ", terms) + ")");
		}
	}

	/**
	 * Ein regulärer Ausdruck, der genau diesen Wert trifft, ohne Rücksicht auf Groß- und Kleinschreibung: jeder
	 * Buchstabe als [kK], jedes andere Zeichen mit Backslash wörtlich, also auch Punkt, Plus und @. Er läuft auf dem
	 * string-Feld selbst, deshalb stimmen Treffer und Anzahl genau; der Zwilling emailSuggested kennt jeden Anfang und
	 * träfe auch längere Adressen mit demselben Anfang.
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

	public KontakteAccessForClient accessForClient() {
		// Die Masken fragen das einmal: was der User im Kern KONTAKTE lesen und wonach er auflösen darf
		List<String> readableFieldNames = readableFieldNamesOfCurrentUser();
		return new KontakteAccessForClient(SolrDocumentKontakteService.CORE_KEY, isEveryFieldReadable(),
				isSearchAllowed(), readableFieldNames, readableFieldNames);
	}

	// ===== Suche: Wörter und Gründe =====
	// Dieselben zwei Methoden stehen gleich in den Utils von DMS und ARTIKEL (User 2026-10-13: dritte Kopie); der Umzug in
	// ein kernunabhängiges Paket steht im Register docs/backend/solr-zugriff.md als offen.

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
		// multiValued kommt als Liste, ein einzelner Wert nicht
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
