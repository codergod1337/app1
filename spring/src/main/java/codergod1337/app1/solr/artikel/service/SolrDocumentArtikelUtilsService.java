package codergod1337.app1.solr.artikel.service;

import codergod1337.app1.solr.artikel.model.ArtikelAccessForClient;
import codergod1337.app1.solr.core.model.SolrCore;
import codergod1337.app1.solr.field.model.SolrField;
import codergod1337.app1.solr.field.service.SolrFieldService;
import codergod1337.app1.system.security.CurrentUsersProvider;
import codergod1337.app1.system.user.model.UsersDebuffType;
import codergod1337.app1.system.user.model.UsersDebuffs;
import codergod1337.app1.system.user.service.UsersDebuffsService;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.apache.solr.client.solrj.util.ClientUtils;
import org.apache.solr.common.params.ModifiableSolrParams;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Alles, was der SolrDocumentArtikelService zum Lesen braucht, aber selbst kein Lesen eines Dokuments ist: die Rolle
 * als Tür, die Felder und Filter einer Anfrage, die Wörter und Gründe der Suche. Spricht nie selbst mit Solr. Derselbe
 * Schnitt wie SolrDocumentDmsUtilsService, nur ohne alles, was der Spiegelkern nicht hat: kein readAcl, kein Status,
 * kein Schreibrecht, keine Revision.
 */
@Service
public class SolrDocumentArtikelUtilsService {

	/** Die Tür des Kerns: ohne diese AR kein Lesen, außer für ADMIN (User 2026-10-10) */
	public static final String ACCESS_ROLE_KEY = "ARTIKEL";

	/** Die AR der Suche: gibt alle Felder und Hooks frei, denn wer suchen darf, kommt ohnehin an alles */
	public static final String SEARCH_ACCESS_ROLE_KEY = "ARTIKEL_SUCHE";

	public static final String ADMIN_ACCESS_ROLE_KEY = "ADMIN";

	/** Jedes Feld und jeder Hook in jedem Kern, ein User-Recht (SolrCurrentUserCoreAccessService.ALL_FIELDS_ROLE_KEY) */
	public static final String ALL_FIELDS_ACCESS_ROLE_KEY = "SOLR_USERS_VIEW_ALL";

	private final CurrentUsersProvider currentUsersProvider;
	private final SolrFieldService solrFieldService;
	private final UsersDebuffsService usersDebuffsService;

	public SolrDocumentArtikelUtilsService(CurrentUsersProvider currentUsersProvider, SolrFieldService solrFieldService,
			UsersDebuffsService usersDebuffsService) {
		this.currentUsersProvider = currentUsersProvider;
		this.solrFieldService = solrFieldService;
		this.usersDebuffsService = usersDebuffsService;
	}

	// ===== Rollen =====

	public boolean isAdmin() {
		return currentUsersProvider.getCurrentUsersAccessRoleKeys().contains(ADMIN_ACCESS_ROLE_KEY);
	}

	/** Vollsicht im Kern ARTIKEL: ADMIN, SOLR_USERS_VIEW_ALL oder ARTIKEL_SUCHE, dann keine fl und keine Debuffs */
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
		// Jede Lesemethode ruft das zuerst: die Tür ARTIKEL, ADMIN darf immer. 403 vor Solr.
		List<String> accessRoleKeys = currentUsersProvider.getCurrentUsersAccessRoleKeys();
		if (!accessRoleKeys.contains(ADMIN_ACCESS_ROLE_KEY) && !accessRoleKeys.contains(ACCESS_ROLE_KEY)) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "keine Rolle " + ACCESS_ROLE_KEY);
		}
	}

	public void requireSearchAccessRole() {
		// Die Suche ruft das: ARTIKEL_SUCHE oder ADMIN, sonst 403 vor Solr
		if (!isSearchAllowed()) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "keine Suche ohne " + SEARCH_ACCESS_ROLE_KEY);
		}
	}

	// ===== Felder und Filter einer Anfrage =====

	public List<String> buildUserSolrResponseFieldList(ModifiableSolrParams params) {
		// Jede Lesemethode ruft das vor der Anfrage auf, außer bei Vollsicht: setzt fl, die field list, auf die Felder,
		// die der User sehen darf, und gibt dieselbe Liste zurück, z. B. für die Prüfung der Bedingungen. Hooks sind
		// Felder des Kerns wie alle anderen, mit eigener Leseliste je Kern. id immer, ohne id ist kein Dokument brauchbar.
		List<String> fieldNames = readableFieldNamesOfCurrentUser();
		params.set("fl", String.join(",", fieldNames));
		return fieldNames;
	}

	public List<String> readableFieldNamesOfCurrentUser() {
		// Die Masken und die fl fragen das: mit Vollsicht jedes Feld des Kerns, sonst id und die Felder mit passender Lese-AR
		List<String> accessRoleKeys = currentUsersProvider.getCurrentUsersAccessRoleKeys();
		boolean everyField = isEveryFieldReadable();
		List<String> fieldNames = new ArrayList<>();
		for (SolrField solrField : solrFieldService.getSolrFieldsByCoreKey(SolrDocumentArtikelService.CORE_KEY)) {
			boolean readable = everyField || SolrCore.ID_FIELD_NAME.equals(solrField.getName())
					|| solrField.getReadAccessRoleKeys().stream().anyMatch(accessRoleKeys::contains);
			if (readable) {
				fieldNames.add(solrField.getName());
			}
		}
		return fieldNames;
	}

	public List<String> buildUserDebuffsForSolrRequest(ModifiableSolrParams params) {
		// Jede Lesemethode ruft das für jeden ohne Vollsicht auf: je Debuff vom Typ RESOLVE des angemeldeten Users ein fq
		// solrFieldName:(wert1 OR wert2). Gibt die Namen der Debuff-Felder zurück: Eine Bedingung des Clients auf so ein
		// Feld wird verworfen, der Debuff steht über jeder Rolle.
		List<String> debuffedFieldNames = new ArrayList<>();
		// das Flag steht im Token: ohne Debuffs kein Zugriff auf Postgres
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
			// ergibt z. B. kundenNummer:(4711 OR 4712)
			params.add("fq", usersDebuffs.getSolrFieldName() + ":(" + String.join(" OR ", escapedValues) + ")");
			debuffedFieldNames.add(usersDebuffs.getSolrFieldName());
		}
		return debuffedFieldNames;
	}

	public void buildClientConditionsForSolrRequest(ModifiableSolrParams params,
			Map<String, List<String>> valuesByFieldName, List<String> readableFieldNames,
			List<String> debuffedFieldNames) {
		// Auflösen und Suche rufen das für die Bedingungen des Clients: je Feld ein fq feld:(wert1 OR wert2), gleiches
		// Feld heißt ODER. readableFieldNames null heißt, der User darf jedes Feld; ein Debuff-Feld lässt die Bedingung weg.
		for (Map.Entry<String, List<String>> condition : valuesByFieldName.entrySet()) {
			String fieldName = condition.getKey();
			// Bedingung auf ein Feld, das der User nicht lesen darf: 403, nicht still ignorieren
			if (readableFieldNames != null && !readableFieldNames.contains(fieldName)) {
				throw new ResponseStatusException(HttpStatus.FORBIDDEN, "keine Bedingung auf " + fieldName);
			}
			// auf einem Debuff-Feld zählt nur der Debuff, die Bedingung des Clients fällt weg
			if (debuffedFieldNames.contains(fieldName)) {
				continue;
			}
			// feld:() wäre für Solr ein Syntaxfehler
			if (condition.getValue() == null || condition.getValue().isEmpty()) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "keine Werte für " + fieldName);
			}
			List<String> escapedValues = new ArrayList<>();
			for (String value : condition.getValue()) {
				escapedValues.add(ClientUtils.escapeQueryChars(value));
			}
			// ergibt z. B. masterArtikelNummer:(610762)
			params.add("fq", fieldName + ":(" + String.join(" OR ", escapedValues) + ")");
		}
	}

	public ArtikelAccessForClient accessForClient() {
		// Die Masken fragen das einmal: was der User im Kern ARTIKEL lesen und wonach er auflösen darf, ob Vollsicht, ob Suche
		List<String> readableFieldNames = readableFieldNamesOfCurrentUser();
		return new ArtikelAccessForClient(SolrDocumentArtikelService.CORE_KEY, isEveryFieldReadable(), isSearchAllowed(),
				readableFieldNames, readableFieldNames);
	}

	// ===== Suche: Wörter und Gründe =====

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
