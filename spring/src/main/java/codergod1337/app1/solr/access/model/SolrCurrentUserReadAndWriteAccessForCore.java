package codergod1337.app1.solr.access.model;

import java.util.List;

/**
 * Was der angemeldete User in genau einem Kern lesen und schreiben darf, je Request gebaut
 * (SolrCurrentUserCoreAccessService.readAndWriteAccessOfCurrentUserForCore). Kein Entity, nichts davon wird
 * gespeichert. Die Lesedienste holen es sich selbst, es ist nie ein Parameter von außen.
 *
 * @param coreKey                                                 der eine Kern, für den alles hier gilt
 * @param everyFieldAndHookOfCoreReadableByCurrentUser            Vollsicht: {@code <KERN>_FULL} oder {@code <KERN>_SUCHE},
 *                                                                jedes Feld des Kerns und jeder Hook ist lesbar
 * @param freeTextSearchAllowed                                   Rolle {@code <KERN>_SUCHE}: Freitext, Zwillinge, Facetten
 * @param readableFieldAndHookNamesOfCoreForCurrentUser           erst die Felder des Kerns in Sheet-Reihenfolge, dann die
 *                                                                Hooks. Daraus wird fl.
 * @param exactResolveAllowedFieldAndHookNamesOfCoreForCurrentUser wonach er auflösen darf (Vorgabe 2026-10-04): immer
 *                                                                {@code id}; mit {@code <KERN>_SUCHE} jedes lesbare
 *                                                                indexierte Feld und jeder Hook; mit {@code HOOK_FULL}
 *                                                                jeder Hook; dazu seine Feldfreigaben. {@code <KERN>_FULL}
 *                                                                allein gibt kein Auflösen, nur Lesen
 * @param readAclAndGrantedAccessFilterQueries                    die fq fürs Lesen: readAcl enthält eine AR des Users ODER
 *                                                                userGrantedAccess seine guid. Leer heißt alles. Nie zum Client.
 * @param writeAclFilterQueries                                   die fq fürs Schreiben: writeAcl enthält eine AR des Users.
 *                                                                Nie leer: In einem Kern ohne writeAcl oder ohne Rollen trifft
 *                                                                sie nichts, dann schreibt kein User. Nie zum Client.
 */
public record SolrCurrentUserReadAndWriteAccessForCore(String coreKey,
		boolean everyFieldAndHookOfCoreReadableByCurrentUser, boolean freeTextSearchAllowed,
		List<String> readableFieldAndHookNamesOfCoreForCurrentUser,
		List<String> exactResolveAllowedFieldAndHookNamesOfCoreForCurrentUser,
		List<String> readAclAndGrantedAccessFilterQueries, List<String> writeAclFilterQueries) {

	public boolean isReadableByCurrentUser(String fieldName) {
		return readableFieldAndHookNamesOfCoreForCurrentUser.contains(fieldName);
	}

	public boolean isExactResolveAllowedForCurrentUser(String fieldName) {
		return exactResolveAllowedFieldAndHookNamesOfCoreForCurrentUser.contains(fieldName);
	}

	/** Was der Client davon erfährt: alles außer den fq, die bleiben im Backend. */
	public SolrCurrentUserCoreAccessForClient forClient() {
		return new SolrCurrentUserCoreAccessForClient(coreKey, everyFieldAndHookOfCoreReadableByCurrentUser,
				freeTextSearchAllowed, readableFieldAndHookNamesOfCoreForCurrentUser,
				exactResolveAllowedFieldAndHookNamesOfCoreForCurrentUser);
	}

	/** Die Antwort an den Client, z. B. GET /dms/access: danach baut die Maske, welche Felder sie anbietet. */
	public record SolrCurrentUserCoreAccessForClient(String coreKey,
			boolean everyFieldAndHookOfCoreReadableByCurrentUser, boolean freeTextSearchAllowed,
			List<String> readableFieldAndHookNamesOfCoreForCurrentUser,
			List<String> exactResolveAllowedFieldAndHookNamesOfCoreForCurrentUser) {
	}

}
