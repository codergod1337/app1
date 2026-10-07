package codergod1337.app1.dms.model;

import java.util.List;

/**
 * Eine Trefferseite der Suche im Kern DMS: wie viele Dokumente insgesamt treffen, welche Seite das ist, ihre Treffer,
 * und wenn gar nicht gesucht wurde, der Grund als Hinweis. Nie 404, eine leere Liste ist eine Antwort.
 *
 * @param totalFound wie viele Dokumente insgesamt treffen, 0 ohne Abfrage
 * @param page       die Seite ab 0
 * @param size       Treffer je Seite
 * @param hits       die Treffer dieser Seite
 * @param hint       warum nicht gesucht wurde, null wenn gesucht wurde
 */
public record DmsSearchResultPage(long totalFound, int page, int size, List<DmsSearchHit> hits, DmsSearchHint hint) {

	/** Keine Abfrage, nur der Grund */
	public static DmsSearchResultPage withoutQuery(DmsSearchHint hint, int size) {
		return new DmsSearchResultPage(0, 0, size, List.of(), hint);
	}

}
