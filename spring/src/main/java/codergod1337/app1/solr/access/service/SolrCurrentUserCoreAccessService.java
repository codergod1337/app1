package codergod1337.app1.solr.access.service;

import codergod1337.app1.solr.access.model.SolrCurrentUserReadAndWriteAccessForCore;
import codergod1337.app1.solr.core.model.SolrCore;
import codergod1337.app1.solr.core.service.SolrCoreService;
import codergod1337.app1.solr.field.model.SolrField;
import codergod1337.app1.solr.field.service.SolrFieldService;
import codergod1337.app1.solr.grant.service.SolrAdvancedFieldGrantAccessPerCoreService;
import codergod1337.app1.solr.grant.service.SolrHookGrantAccessService;
import codergod1337.app1.solr.hook.model.SolrHook;
import codergod1337.app1.solr.hook.service.SolrHookService;
import codergod1337.app1.system.security.CurrentUsersProvider;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.apache.solr.client.solrj.util.ClientUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Die einzige Stelle, die zusammensetzt, was der angemeldete User in einem Kern darf. Je Request aus dem Token, der
 * guid und den Kopien im Speicher, kein Zugriff auf Postgres, damit die Suche normaler User durch die Rechteprüfung
 * nicht langsamer wird.
 *
 * Feldachse (neu gefasst 2026-10-09): Jedes Feld und jeder Hook trägt die ARs, die es lesen dürfen
 * (readAccessRoleKeys): USER für die Grundmenge, die jeder bekommt, sonst SOLR_FIELD_VIEW_*. Ein User bekommt ein Feld,
 * wenn eine seiner Rollen dransteht. SOLR_USERS_VIEW_ALL bekommt jedes Feld und jeden Hook in jedem Kern,
 * SOLR_USERS_VIEW_HOOKS jeden Hook, {@code <KERN>_SUCHE} ebenfalls alles, denn wer suchen darf, kommt ohnehin an
 * alles. backendOnly-Felder bekommt kein Client, auch nicht mit ALL. Dieselbe Liste
 * entscheidet, wonach er filtern, sortieren, facettieren und auflösen darf: Lesen heißt Auflösen, sofern das Feld
 * indexiert ist, und id immer. Die alten Feld- und Hook-Freigaben je User zählen noch mit, bis sie abgebaut sind.
 *
 * Dokumentachse, zwei Prüfungen, die nichts miteinander zu tun haben:
 * <ul>
 * <li>readAcl und writeAcl (nur in Kernen, die die Felder haben, im DMS aus der Dateiart): die AR-Keys als Liste, ohne
 * Präfix. Welche Gruppen im Betrieb welche Dateiart lesen und ändern dürfen.</li>
 * <li>userGrantedAccess (in jedem Kern): die Freigabe je User.</li>
 * </ul>
 *
 * Schreiben: Vor jedem Schreibvorgang an einem vorhandenen Dokument muss writeAcl eine AR des Users enthalten. Den Check
 * macht die Lesemechanik mit dem Schreib-fq von hier, dieser Service rechnet nur und liest nie aus Solr. Vollsicht gibt
 * kein Schreibrecht, Schreiben impliziert kein Lesen. In einem Kern ohne writeAcl schreibt kein User.
 *
 * Ob ein Kern readAcl und writeAcl hat, liest dieser Service am Vorhandensein der Felder ab. Die Namen der Felder, mit
 * denen das Backend rechnet, stehen hart im Code, fehlt eins, fliegen Fehler.
 */
@Service
public class SolrCurrentUserCoreAccessService {

	/** Die Rechtefelder, die ein Kern haben kann. userGrantedAccess hat jeder Kern (SolrCore.USER_GRANTED_ACCESS_FIELD_NAME). */
	public static final String READ_ACL_FIELD_NAME = "readAcl";
	public static final String WRITE_ACL_FIELD_NAME = "writeAcl";
	/** Die Internetfreigabe: noch in keiner Prüfung, kommt mit dem Freigabe-Service */
	public static final String IS_PUBLIC_FIELD_NAME = "isPublic";

	public static final String SEARCH_ROLE_SUFFIX = "_SUCHE";

	/**
	 * Jedes Feld und jeder Hook in jedem Kern, ohne dass die Rolle an einem Feld steht. Ein User-Recht, kein Feld-Recht
	 * (User 2026-10-09), deshalb SOLR_USERS_VIEW_ und nicht SOLR_FIELD_. Die AR gibt es in der Datenbank.
	 */
	public static final String ALL_FIELDS_ROLE_KEY = "SOLR_USERS_VIEW_ALL";

	/** Jeder Hook in jedem Kern, weil Hooks in jedem Kern dieselben sind. Früher HOOK_FULL. Ebenfalls ein User-Recht. */
	public static final String ALL_HOOKS_ROLE_KEY = "SOLR_USERS_VIEW_HOOKS";

	/** Ein fq, das kein Dokument trifft: die Schreibprüfung, wo kein User schreibt */
	private static final String FILTER_QUERY_MATCHING_NO_DOCUMENT = "-*:*";

	private final SolrCoreService solrCoreService;
	private final SolrFieldService solrFieldService;
	private final SolrHookService solrHookService;
	private final SolrAdvancedFieldGrantAccessPerCoreService advancedFieldGrantService;
	private final SolrHookGrantAccessService hookGrantService;
	private final CurrentUsersProvider currentUsersProvider;

	public SolrCurrentUserCoreAccessService(SolrCoreService solrCoreService, SolrFieldService solrFieldService,
			SolrHookService solrHookService, SolrAdvancedFieldGrantAccessPerCoreService advancedFieldGrantService,
			SolrHookGrantAccessService hookGrantService, CurrentUsersProvider currentUsersProvider) {
		this.solrCoreService = solrCoreService;
		this.solrFieldService = solrFieldService;
		this.solrHookService = solrHookService;
		this.advancedFieldGrantService = advancedFieldGrantService;
		this.hookGrantService = hookGrantService;
		this.currentUsersProvider = currentUsersProvider;
	}

	/** Alles, was der angemeldete User in diesem Kern darf. 404 bei unbekanntem Kern, 403 ohne die Rolle {@code <KERN>}. */
	public SolrCurrentUserReadAndWriteAccessForCore readAndWriteAccessOfCurrentUserForCore(String coreKey) {
		solrCoreService.getSolrCoreByKey(coreKey);
		List<String> accessRoleKeys = currentUsersProvider.getCurrentUsersAccessRoleKeys();
		if (!accessRoleKeys.contains(coreKey)) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "keine Rolle " + coreKey);
		}
		boolean freeTextSearchAllowed = accessRoleKeys.contains(coreKey + SEARCH_ROLE_SUFFIX);
		boolean everyFieldAndHookReadable = freeTextSearchAllowed || accessRoleKeys.contains(ALL_FIELDS_ROLE_KEY);
		boolean everyHookReadable = everyFieldAndHookReadable || accessRoleKeys.contains(ALL_HOOKS_ROLE_KEY);
		UUID usersGuid = currentUsersProvider.getCurrentUsersGuid();
		// die alten Freigaben je User zählen noch mit, bis sie abgebaut sind
		Set<String> grantedFieldNames = everyFieldAndHookReadable ? Set.of()
				: advancedFieldGrantService.activeGrantedAdvancedFieldNamesOfUsersInCore(usersGuid, coreKey);
		Set<String> grantedHookKeys = everyHookReadable ? Set.of()
				: hookGrantService.activeGrantedHookKeysOfUsers(usersGuid);

		List<SolrField> solrFields = solrFieldService.getSolrFieldsByCoreKey(coreKey);
		List<String> readableFieldAndHookNames = new ArrayList<>();
		List<String> exactResolveAllowedFieldAndHookNames = new ArrayList<>();
		for (SolrField solrField : solrFields) {
			if (solrField.isBackendOnly()) {
				continue;
			}
			// id ist immer lesbar und auflösbar, egal was an seiner Zeile steht: ohne id ist kein Dokument brauchbar
			boolean readable = SolrCore.ID_FIELD_NAME.equals(solrField.getName()) || everyFieldAndHookReadable
					|| grantedFieldNames.contains(solrField.getName())
					|| containsAny(solrField.getReadAccessRoleKeys(), accessRoleKeys);
			if (!readable) {
				continue;
			}
			readableFieldAndHookNames.add(solrField.getName());
			if (solrField.isIndexed()) {
				exactResolveAllowedFieldAndHookNames.add(solrField.getName());
			}
		}
		for (SolrHook solrHook : solrHookService.getAllSolrHooks()) {
			boolean readable = everyHookReadable || grantedHookKeys.contains(solrHook.getKey())
					|| containsAny(solrHook.getReadAccessRoleKeys(), accessRoleKeys);
			if (readable) {
				readableFieldAndHookNames.add(solrHook.getKey());
				exactResolveAllowedFieldAndHookNames.add(solrHook.getKey());
			}
		}

		return new SolrCurrentUserReadAndWriteAccessForCore(coreKey, everyFieldAndHookReadable,
				freeTextSearchAllowed, List.copyOf(readableFieldAndHookNames),
				List.copyOf(exactResolveAllowedFieldAndHookNames),
				readAclAndGrantedAccessFilterQueries(solrFields, accessRoleKeys, usersGuid, everyFieldAndHookReadable),
				writeAclFilterQueries(solrFields, accessRoleKeys));
	}

	private static boolean containsAny(Collection<String> fieldAccessRoleKeys, Collection<String> usersAccessRoleKeys) {
		for (String accessRoleKey : fieldAccessRoleKeys) {
			if (usersAccessRoleKeys.contains(accessRoleKey)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Die Leseprüfung als ein fq: (userGrantedAccess:guid OR readAcl:(AR …)). readAcl nur, wenn der Kern das Feld hat.
	 * Mit Vollsicht in einem Kern ohne readAcl gibt es keine Prüfung: leere Liste. Mit Vollsicht in einem Kern mit
	 * readAcl bleibt die Dateiart-Prüfung, die eigenen Freigaben kommen dazu. isPublic spielt hier nicht mit, das ist
	 * Sache des Freigabe-Service. Jeder Wert escaped, auch die AR-Keys, obwohl die Key-Regel nichts Gefährliches
	 * zulässt: Hier wird nichts zusammengeklebt, dem man trauen muss.
	 */
	private static List<String> readAclAndGrantedAccessFilterQueries(List<SolrField> solrFields,
			List<String> accessRoleKeys, UUID usersGuid, boolean everyFieldAndHookReadable) {
		boolean hasReadAcl = hasField(solrFields, READ_ACL_FIELD_NAME);
		if (everyFieldAndHookReadable && !hasReadAcl) {
			return List.of();
		}
		List<String> parts = new ArrayList<>();
		parts.add(SolrCore.USER_GRANTED_ACCESS_FIELD_NAME + ":" + ClientUtils.escapeQueryChars(usersGuid.toString()));
		if (hasReadAcl && !accessRoleKeys.isEmpty()) {
			parts.add(READ_ACL_FIELD_NAME + ":(" + joinedAccessRoleKeys(accessRoleKeys) + ")");
		}
		return List.of("(" + String.join(" OR ", parts) + ")");
	}

	/**
	 * Die Schreibprüfung als ein fq: writeAcl enthält eine AR des Users. Hat der Kern kein writeAcl oder der User keine
	 * Rolle, trifft sie nichts: Dort schreibt kein User, nur das System.
	 */
	private static List<String> writeAclFilterQueries(List<SolrField> solrFields, List<String> accessRoleKeys) {
		if (!hasField(solrFields, WRITE_ACL_FIELD_NAME) || accessRoleKeys.isEmpty()) {
			return List.of(FILTER_QUERY_MATCHING_NO_DOCUMENT);
		}
		return List.of(WRITE_ACL_FIELD_NAME + ":(" + joinedAccessRoleKeys(accessRoleKeys) + ")");
	}

	private static boolean hasField(List<SolrField> solrFields, String name) {
		return solrFields.stream().anyMatch(field -> name.equals(field.getName()));
	}

	/** Die AR-Keys als ODER-Liste, jeder escaped */
	private static String joinedAccessRoleKeys(List<String> accessRoleKeys) {
		List<String> escaped = new ArrayList<>();
		for (String accessRoleKey : accessRoleKeys) {
			escaped.add(ClientUtils.escapeQueryChars(accessRoleKey));
		}
		return String.join(" OR ", escaped);
	}

}
