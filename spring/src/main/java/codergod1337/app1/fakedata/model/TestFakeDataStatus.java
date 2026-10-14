package codergod1337.app1.fakedata.model;

import java.time.Instant;
import java.util.List;

/**
 * Der Stand des Testdaten-Laufs für die Maske, die ihn alle paar Sekunden abfragt (User 2026-10-14). Ziel des Laufs ist,
 * früh die spätere Performance einzuschätzen: Je Phase steht deshalb neben dem Fortschritt, wie viele Solr-Dokumente
 * je Sekunde über die normalen Schreibwege gingen.
 *
 * @param running         läuft gerade ein Lauf
 * @param cancelRequested Abbrechen ist angefordert, der Lauf hört nach dem laufenden Eintrag auf
 * @param currentPhase    die Phase, die gerade läuft, null ohne Lauf
 * @param startedAt       Start des letzten Laufs, null, wenn seit dem Start des Backends keiner lief
 * @param finishedAt      Ende des letzten Laufs, null, solange er läuft
 * @param request         die Eingabe des letzten Laufs
 * @param phases          die Phasen in ihrer Reihenfolge: CUSTOMERS, SUPPLIERS, ARTICLES, DOCUMENTS
 * @param errorMessage    warum der Lauf abgebrochen ist, null ohne Abbruch durch Fehler
 */
public record TestFakeDataStatus(boolean running, boolean cancelRequested, String currentPhase, Instant startedAt,
		Instant finishedAt, TestFakeDataRequest request, List<Phase> phases, String errorMessage) {

	/**
	 * Eine Phase des Laufs.
	 *
	 * @param key                    CUSTOMERS, SUPPLIERS, ARTICLES oder DOCUMENTS
	 * @param total                  Einheiten insgesamt: Firmen, Artikel oder Datenblätter
	 * @param done                   davon erledigt, ob geschrieben, übersprungen oder gescheitert
	 * @param skipped                übersprungen, weil die id schon da war
	 * @param failed                 gescheitert, der Grund des letzten steht in lastErrorMessage
	 * @param writtenSolrDocuments   geschriebene Solr-Dokumente; bei Kunden und Lieferanten Firma plus Ansprechpartner
	 * @param startedAt              Start der Phase, null, solange sie nicht dran war
	 * @param finishedAt             Ende der Phase, null, solange sie läuft
	 * @param solrDocumentsPerSecond geschriebene Solr-Dokumente je Sekunde seit dem Start der Phase
	 * @param lastErrorMessage       der Grund des letzten Fehlers, null ohne
	 */
	public record Phase(String key, long total, long done, long skipped, long failed, long writtenSolrDocuments,
			Instant startedAt, Instant finishedAt, double solrDocumentsPerSecond, String lastErrorMessage) {
	}

}
