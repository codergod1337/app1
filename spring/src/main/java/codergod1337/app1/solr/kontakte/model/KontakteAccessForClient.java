package codergod1337.app1.solr.kontakte.model;

import java.util.List;

/**
 * Was der angemeldete User im Kern KONTAKTE darf, für die Masken: Vollsicht (ADMIN, SOLR_USERS_VIEW_ALL oder
 * KONTAKTE_SUCHE) oder nur die Felder mit passender Lese-AR, ob er suchen darf (ADMIN oder KONTAKTE_SUCHE), die Felder,
 * die er bekommt, und die, nach denen er auflösen darf (dieselben). Anzeige, kein Schutz: Das Backend prüft jede
 * Anfrage selbst.
 */
public record KontakteAccessForClient(String coreKey, boolean everyFieldAndHookOfCoreReadableByCurrentUser,
		boolean freeTextSearchAllowed, List<String> readableFieldAndHookNamesOfCoreForCurrentUser,
		List<String> exactResolveAllowedFieldAndHookNamesOfCoreForCurrentUser) {
}
