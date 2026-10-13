package codergod1337.app1.solr.artikel.model;

import java.time.Instant;
import java.util.List;

/**
 * Was der Tab eines Flags in der Artikelmappe bekommt: die aufaggregierten Verbindungen eines Artikels, aus der Ablage
 * im Dokument oder gerade gerechnet (User 2026-10-13).
 *
 * @param artikelNummer    der Startartikel
 * @param flag             das Flag, nach dem gesucht wurde
 * @param alternativeDepth bis zu wie vielen Alternativschritten die Treffer reichen; 0 heißt nur der Artikel selbst
 * @param aggregatedAt     wann dieser Stand gerechnet wurde, das „Stand vom“ der Mappe
 * @param references       die Treffer, nach Entfernung und dann nach Nummer sortiert, nie der Startartikel selbst
 * @param truncated        true, wenn der Deckel app1.solr.artikel.references.max-rows erreicht wurde und Treffer fehlen
 */
public record ArtikelAggregatedReferencesForClient(String artikelNummer, ArtikelReferenceFlag flag,
		int alternativeDepth, Instant aggregatedAt, List<ArtikelAggregatedReference> references, boolean truncated) {
}
