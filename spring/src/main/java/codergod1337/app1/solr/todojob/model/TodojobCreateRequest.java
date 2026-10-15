package codergod1337.app1.solr.todojob.model;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Erteilen eines Jobs (User 2026-10-16): der Befehl aus Verb und Objekt, was zu tun ist, die Eingrenzungen, welche
 * Rückmeldung verlangt ist, ob der Owner bestätigen muss, ob die Rückmeldung dauerhaft relevant ist, die Anzeigeorte,
 * die Owner-Seite neben dem Erteilenden (optional), die Servants (mindestens eine Adresse), die Frist und die Hooks.
 * Alles andere setzt das Backend.
 */
public record TodojobCreateRequest(
		String verb,
		String objekt,
		String aufgabe,
		List<String> kontext,
		TodojobRueckmeldungEntryType rueckmeldungsArt,
		boolean bestaetigungNoetig,
		boolean dauerhaftRelevant,
		List<String> displayContextKeys,
		List<String> ownerAccessRoleKeys,
		List<String> ownerArcKeys,
		List<String> servantGuids,
		List<String> servantAccessRoleKeys,
		List<String> servantArcKeys,
		Instant fristAm,
		Map<String, List<String>> hookValuesByHookKey) {
}
