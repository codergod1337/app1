package codergod1337.app1.dms.model;

import java.time.LocalDate;

/**
 * Ein Zeitraum als Filter der Suche, nur für Datumsfelder und nur in der Suche, nie im Auflösen (Vorgabe 2026-10-07):
 * von einschließlich bis einschließlich, je ein Tag. Eins von beiden darf fehlen, dann ist der Zeitraum nach dieser
 * Seite offen. Tagesgrenzen in UTC.
 *
 * @param from der erste Tag, null heißt ohne Anfang
 * @param to   der letzte Tag, null heißt ohne Ende
 */
public record DmsSearchDateRange(LocalDate from, LocalDate to) {

	public boolean isEmpty() {
		return from == null && to == null;
	}

}
