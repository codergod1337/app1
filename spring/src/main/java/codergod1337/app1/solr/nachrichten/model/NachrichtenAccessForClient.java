package codergod1337.app1.solr.nachrichten.model;

import java.util.List;

/**
 * Was der angemeldete User im Kern NACHRICHTEN darf, für die Masken: Vollsicht (ADMIN oder SOLR_USERS_VIEW_ALL) oder
 * nur die Felder mit passender Lese-AR, ob er suchen darf (ADMIN oder NACHRICHTEN_SUCHE), die Felder, die er bekommt,
 * und die, nach denen er auflösen darf (dieselben). Welche Nachrichten er sieht, entscheidet readAcl an jeder einzelnen.
 * Anzeige, kein Schutz: Das Backend prüft jede Anfrage selbst.
 */
public record NachrichtenAccessForClient(String coreKey, boolean everyFieldAndHookOfCoreReadableByCurrentUser,
		boolean freeTextSearchAllowed, List<String> readableFieldAndHookNamesOfCoreForCurrentUser,
		List<String> exactResolveAllowedFieldAndHookNamesOfCoreForCurrentUser) {
}
