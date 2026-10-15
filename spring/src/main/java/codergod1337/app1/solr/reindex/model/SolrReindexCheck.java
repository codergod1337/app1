package codergod1337.app1.solr.reindex.model;

import java.util.Map;
import java.util.Set;
import org.apache.solr.common.SolrInputDocument;

/**
 * Eine Prüfung beim Nachziehen eines Kerns (User 2026-10-14): welche Felder sie je Dokument braucht, wie sie den
 * Iststand mit dem Sollstand vergleicht und was sie bei einer Abweichung schreibt. Der SolrReindexIterator lädt die
 * Felder aller Prüfungen eines Laufs, lässt jede auf jedes Dokument los und schreibt das Dokument einzeln, sobald eine
 * etwas geändert hat. Eine neue Prüfung ist eine neue Klasse und ein neuer Wert im Options-Enum ihres Kerns; die
 * Schleife bleibt, wie sie ist.
 *
 * Den Sollstand (z. B. die Dateiarten aus Postgres) liest eine Prüfung einmal beim Bauen, nie je Dokument.
 */
public interface SolrReindexCheck {

	/** Die Felder, die diese Prüfung je Dokument braucht; die id lädt der Lauf immer */
	Set<String> loadedFieldNames();

	/**
	 * Vergleicht das geladene Dokument (nur id und die Felder aus loadedFieldNames) mit dem Sollstand. Weicht es ab,
	 * legt die Prüfung ihre Schreiboperationen ins Atomic Update, z. B. Map.of("set", wert), "add", "remove" oder "inc".
	 * true, wenn etwas zu schreiben ist. Eine Ausnahme zählt als Fehler an diesem Dokument, der Lauf geht weiter.
	 */
	boolean addChanges(Map<String, Object> document, SolrInputDocument update);

}
