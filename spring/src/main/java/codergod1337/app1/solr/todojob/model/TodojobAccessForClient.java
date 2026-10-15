package codergod1337.app1.solr.todojob.model;

import java.util.List;

/**
 * Was der angemeldete User im Kern TODOJOB darf, für die Masken: die Felder, die er bekommt, und was er als Vorgaben
 * braucht (die Arten der Rückmeldung). Welche Jobs er sieht, entscheidet allein, ob er Partei ist. Anzeige, kein Schutz.
 */
public record TodojobAccessForClient(String coreKey, boolean everyFieldAndHookOfCoreReadableByCurrentUser,
		List<String> readableFieldAndHookNamesOfCoreForCurrentUser, List<String> rueckmeldungsArten) {
}
