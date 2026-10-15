package codergod1337.app1.solr.transfer.model;

import java.time.Instant;

/**
 * Der Stand des letzten Imports (User 2026-10-14). Der Import hält beim ersten Fehler an: dann stehen Zeile, id und
 * Meldung hier, und ein neuer Start ab stoppedAtLine macht genau dort weiter.
 *
 * @param fromLine            ab dieser Zeile der Datei lief der Import
 * @param line                die zuletzt bearbeitete Zeile
 * @param written             geschriebene Dokumente
 * @param skippedExisting     übersprungen, weil die id im Kern schon war (Modus SKIP_EXISTING)
 * @param totalDocuments      Dokumente der Datei laut Prüfung
 * @param stoppedAtLine       hier hielt der Import an, Fehler oder Abbruch; null, wenn er durchlief
 * @param stoppedAtId         die id des Dokuments in dieser Zeile, soweit bekannt
 * @param errorMessage        warum er anhielt, null bei Abbruch von Hand oder ohne Halt
 */
public record SolrCoreImportStatus(long importId, String coreKey, boolean running, boolean cancelRequested,
		SolrCoreImportMode mode, Instant startedAt, Instant finishedAt, long fromLine, long line, long written,
		long skippedExisting, long totalDocuments, double documentsPerSecond, Long stoppedAtLine, String stoppedAtId,
		String errorMessage) {
}
