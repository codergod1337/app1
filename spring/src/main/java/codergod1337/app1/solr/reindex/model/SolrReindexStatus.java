package codergod1337.app1.solr.reindex.model;

import java.time.Instant;
import java.util.List;

/**
 * Der Stand des letzten Laufs beim Nachziehen für den SolrManager (User 2026-10-14). Ein Lauf zur Zeit, über alle
 * Kerne: coreKey sagt, welcher Kern dran ist oder war.
 *
 * @param total               Dokumente im Kern beim Start, -1 bis zur ersten Seite
 * @param checked             fertig geprüfte Dokumente, geändert oder nicht
 * @param changed             davon geschrieben
 * @param failed              Dokumente mit Fehler, der Lauf ging weiter
 * @param documentsPerSecond  geprüfte und gescheiterte Dokumente je Sekunde seit dem Start
 * @param changedIdsRecorded  ob der Lauf die geänderten ids aufschreibt; der Neuaufbau der Suche zählt nur
 * @param firstChangedIds     die ersten hundert geänderten ids; alle stehen in der Datei unter /changed-ids
 * @param lastErrorMessage    der letzte Fehler an einem Dokument
 * @param errorMessage        warum der ganze Lauf abbrach, null ohne Abbruch
 */
public record SolrReindexStatus(String coreKey, boolean running, boolean cancelRequested, List<String> options,
		Instant startedAt, Instant finishedAt, long total, long checked, long changed, long failed,
		double documentsPerSecond, boolean changedIdsRecorded, List<String> firstChangedIds, String lastErrorMessage,
		String errorMessage) {
}
