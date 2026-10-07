package codergod1337.app1.dms.model;

import java.util.List;

/**
 * Die Antwort der Vervollständigung beim Tippen im Kern DMS: kompakte Treffer mit Treffergrund und die Filterangebote,
 * höchstens zehn. Nicht suchbar (zu kurz, nur Ausschlüsse, zu viele Wörter) heißt stumm leer, den Hinweis gibt die Suche.
 *
 * @param hits         die kompakten Treffer, höchstens DmsSearchService.SUGGEST_HITS
 * @param filterOffers die Angebote in der Reihenfolge der Treffer, der erste Treffer gewinnt
 */
public record DmsSuggestResult(List<DmsSearchHit> hits, List<DmsSearchFilterOffer> filterOffers) {

	public static DmsSuggestResult empty() {
		return new DmsSuggestResult(List.of(), List.of());
	}

}
