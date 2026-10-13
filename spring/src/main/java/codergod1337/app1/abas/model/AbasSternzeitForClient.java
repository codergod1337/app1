package codergod1337.app1.abas.model;

import java.time.Instant;

/**
 * Ein Stammsatz, wie die Übersicht ihn zeigt: der Objekttyp als Text statt als id, aufgelöst über die Kopie im
 * Speicher.
 *
 * @param objectType der Typ-String von ABAS, null heißt noch nie geholt
 * @param lastSyncAt null heißt noch nie geholt
 */
public record AbasSternzeitForClient(long id, String sternzeit, String objectType, Instant firstPushedAt,
		Instant lastSyncAt, int fetchCount) {
}
