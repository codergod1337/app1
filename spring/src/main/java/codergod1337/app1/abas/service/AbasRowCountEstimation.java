package codergod1337.app1.abas.service;

import java.util.function.LongSupplier;

/**
 * Die Zeilenzahl einer großen Tabelle: genau, solange sie klein ist, sonst die Schätzung aus dem Postgres-Katalog.
 * Zwei Gründe für die Grenze statt „immer schätzen“: Eine frische oder gerade massenhaft befüllte Tabelle hat noch kein
 * ANALYZE hinter sich, reltuples steht dann auf -1 oder einem alten Wert. Und unter einer halben Million kostet ein
 * echtes count(*) so wenig, dass die Genauigkeit geschenkt ist.
 */
final class AbasRowCountEstimation {

	/** Bis hierhin wird genau gezählt, darüber geschätzt */
	static final long EXACT_COUNT_UP_TO = 500_000;

	/** Das Ergebnis: die Zahl und ob sie nur geschätzt ist */
	record EstimatedOrExactRowCount(long count, boolean estimated) {
	}

	private AbasRowCountEstimation() {
	}

	/**
	 * @param estimatedRowCount reltuples aus pg_class, -1 wenn nie analysiert
	 * @param exactRowCount     das count(*), nur gerufen, wenn die Schätzung fehlt oder klein ist
	 */
	static EstimatedOrExactRowCount rowCount(long estimatedRowCount, LongSupplier exactRowCount) {
		if (estimatedRowCount < 0 || estimatedRowCount < EXACT_COUNT_UP_TO) {
			return new EstimatedOrExactRowCount(exactRowCount.getAsLong(), false);
		}
		return new EstimatedOrExactRowCount(estimatedRowCount, true);
	}

}
