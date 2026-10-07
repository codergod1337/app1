package codergod1337.app1.solr.access.service;

import codergod1337.app1.solr.access.model.SolrCurrentUserReadAndWriteAccessForCore;
import codergod1337.app1.solr.core.model.SolrCore;
import codergod1337.app1.solr.core.service.SolrCoreService;
import codergod1337.app1.solr.field.model.SolrField;
import codergod1337.app1.solr.field.model.SolrFieldReadAccessLevel;
import codergod1337.app1.solr.field.service.SolrFieldService;
import codergod1337.app1.solr.grant.service.SolrAdvancedFieldGrantAccessPerCoreService;
import codergod1337.app1.solr.grant.service.SolrHookGrantAccessService;
import codergod1337.app1.solr.hook.model.SolrHook;
import codergod1337.app1.solr.hook.service.SolrHookService;
import codergod1337.app1.system.security.CurrentUsersProvider;
import java.util.ArrayList;
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
 * Drei Rollen je Kern, aus dem Kern-Key abgeleitet, alle additiv: {@code <KERN>} heißt Kern betreten und Auflösen,
 * {@code <KERN>_FULL} ist die Vollsicht ohne Suche, {@code <KERN>_SUCHE} die Vollsicht mit Suche. Wer suchen darf,
 * kommt ohnehin an alles, eine Feldgrenze wäre dort Theater.
 *
 * Zwei Prüfungen am Dokument, die nichts miteinander zu tun haben:
 * <ul>
 * <li>readAcl und writeAcl (nur in Kernen, die die Felder haben, im DMS aus der Dateiart): die AR-Keys als Liste, ohne
 * Präfix. Welche Gruppen im Betrieb welche Dateiart lesen und ändern dürfen. Gilt für jeden, auch mit Vollsicht, denn
 * die Dateiart entscheidet, wer eine Rechnung liest, nicht die Rolle am Kern.</li>
 * <li>userGrantedAccess (in jedem Kern): die Freigabe je User, z. B. die Dokumente einer Artikelnummer für einen
 * Sublieferanten. Nur wer allein {@code <KERN>} hat, ist daran gebunden. Mit Vollsicht sieht er alle Dokumente, die
 * readAcl zulässt; in einem Kern ohne readAcl alle.</li>
 * </ul>
 *
 * Schreiben heißt: Metadaten eines Dokuments ändern, ein neues anlegen oder ein vorhandenes mergen. Vor jedem
 * Schreibvorgang an einem vorhandenen Dokument, in jedem Kern, muss writeAcl eine AR des Users enthalten, sonst wird
 * nichts geschrieben. Den Check macht die Lesemechanik mit dem Schreib-fq von hier (requireWriteAclMatch), dieser
 * Service rechnet nur und liest nie aus Solr (Vorgabe 2026-10-07). Vollsicht gibt kein Schreibrecht, Schreiben
 * impliziert kein Lesen. In einem Kern ohne writeAcl schreibt kein User.
 *
 * Felder: Mit Vollsicht alle Felder und Hooks. Ohne nur die Minimum-Felder plus die Feldfreigaben des Users für diesen
 * Kern. Hooks stehen nicht im Sheet, ohne Vollsicht sieht ein User einen Hook nur über eine Feldfreigabe.
 *
 * Ob ein Kern readAcl und writeAcl hat, liest dieser Service am Vorhandensein der Felder ab. Die Namen der Felder, mit
 * denen das Backend rechnet, stehen hart im Code (Vorgabe 2026-10-07), fehlt eins, fliegen Fehler.
 */
@Service
public class SolrCurrentUserCoreAccessService {

	/** Die Rechtefelder, die ein Kern haben kann. userGrantedAccess hat jeder Kern (SolrCore.USER_GRANTED_ACCESS_FIELD_NAME). */
	public static final String READ_ACL_FIELD_NAME = "readAcl";
	public static final String WRITE_ACL_FIELD_NAME = "writeAcl";
	/** Die Internetfreigabe: noch in keiner Prüfung, kommt ganz zum Schluss */
	public static final String IS_PUBLIC_FIELD_NAME = "isPublic";

	public static final String SEARCH_ROLE_SUFFIX = "_SUCHE";
	public static final String FULL_ROLE_SUFFIX = "_FULL";
	/** Kernunabhängig, weil Hooks in jedem Kern dieselben sind: nach jedem Hook auflösen und die Hooks lesen */
	public static final String HOOK_FULL_ROLE_KEY = "HOOK_FULL";

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
		// Rollen am Kern (Vorgabe 2026-10-04): <KERN> heißt nur, ein Dokument über seine id anfordern. <KERN>_FULL heißt
		// alle Felder lesen, nicht auflösen. <KERN>_SUCHE heißt suchen, damit alle Felder lesen und nach allem auflösen.
		// HOOK_FULL (kernunabhängig) heißt nach jedem Hook auflösen. Eine Feldfreigabe ist ein Auflöserecht an einem
		// Feld oder Hook und macht ihn damit auch lesbar.
		boolean freeTextSearchAllowed = accessRoleKeys.contains(coreKey + SEARCH_ROLE_SUFFIX);
		// Vollsicht: _FULL oder _SUCHE, beides heißt alle Felder und keine Bindung an die Freigabe
		boolean everyFieldAndHookReadable = freeTextSearchAllowed
				|| accessRoleKeys.contains(coreKey + FULL_ROLE_SUFFIX);
		boolean everyHookResolvable = freeTextSearchAllowed || accessRoleKeys.contains(HOOK_FULL_ROLE_KEY);
		UUID usersGuid = currentUsersProvider.getCurrentUsersGuid();
		// Freigaben zählen, solange der User nicht suchen darf: auch mit _FULL, denn _FULL löst nicht auf.
		// Feldfreigaben je Kern, Hook-Freigaben serverweit (Vorgabe 2026-10-07).
		Set<String> grantedFieldNames = freeTextSearchAllowed ? Set.of()
				: advancedFieldGrantService.activeGrantedAdvancedFieldNamesOfUsersInCore(usersGuid, coreKey);
		Set<String> grantedHookKeys = everyHookResolvable ? Set.of()
				: hookGrantService.activeGrantedHookKeysOfUsers(usersGuid);

		List<SolrField> solrFields = solrFieldService.getSolrFieldsByCoreKey(coreKey);
		List<String> readableFieldAndHookNames = new ArrayList<>();
		List<String> exactResolveAllowedFieldAndHookNames = new ArrayList<>();
		for (SolrField solrField : solrFields) {
			boolean granted = grantedFieldNames.contains(solrField.getName());
			boolean readable = everyFieldAndHookReadable
					|| solrField.getReadAccessLevel() == SolrFieldReadAccessLevel.MINIMUM_SET_EVERY_READER || granted;
			if (!readable) {
				continue;
			}
			readableFieldAndHookNames.add(solrField.getName());
			boolean resolvable = SolrCore.ID_FIELD_NAME.equals(solrField.getName()) || freeTextSearchAllowed
					|| granted;
			if (resolvable && solrField.isIndexed()) {
				exactResolveAllowedFieldAndHookNames.add(solrField.getName());
			}
		}
		for (SolrHook solrHook : solrHookService.getAllSolrHooks()) {
			boolean resolvable = everyHookResolvable || grantedHookKeys.contains(solrHook.getKey());
			if (everyFieldAndHookReadable || resolvable) {
				readableFieldAndHookNames.add(solrHook.getKey());
			}
			if (resolvable) {
				exactResolveAllowedFieldAndHookNames.add(solrHook.getKey());
			}
		}

		return new SolrCurrentUserReadAndWriteAccessForCore(coreKey, everyFieldAndHookReadable,
				freeTextSearchAllowed, List.copyOf(readableFieldAndHookNames),
				List.copyOf(exactResolveAllowedFieldAndHookNames),
				readAclAndGrantedAccessFilterQueries(solrFields, accessRoleKeys, usersGuid, everyFieldAndHookReadable),
				writeAclFilterQueries(solrFields, accessRoleKeys));
	}

	/**
	 * Die Leseprüfung als ein fq: (userGrantedAccess:guid OR readAcl:(AR …)). readAcl nur, wenn der Kern das Feld hat.
	 * Mit Vollsicht in einem Kern ohne readAcl gibt es keine Prüfung: leere Liste. Mit Vollsicht in einem Kern mit
	 * readAcl bleibt die Dateiart-Prüfung, die eigenen Freigaben kommen dazu. isPublic spielt noch nicht mit, das ist der
	 * seltene Sonderfall fürs Internet und kommt ganz zum Schluss (2026-10-03). Jeder Wert escaped, auch die AR-Keys,
	 * obwohl die Key-Regel nichts Gefährliches zulässt: Hier wird nichts zusammengeklebt, dem man trauen muss.
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
