package codergod1337.app1.solr.artikel.model;

import java.util.List;

/**
 * Ein Treffer der aufaggregierten Suche nach einem Flag (User 2026-10-13): ein Artikel, der mit dem Startartikel über
 * eine oder mehrere Beziehungen verbunden ist. Zugleich die JSON-Zeile im Ablagefeld {@code table<Flag>Aggregiert}, so
 * wie {@code tableProduct} die Stückliste trägt.
 *
 * @param artikelNummer         die hauseigene Nummer des gefundenen Artikels, der Weg in seine Mappe
 * @param sternzeit             seine Sternzeit für den ABAS-Knopf; leer, wenn er nicht im Spiegel steht
 * @param name                  sein Name aus dem Spiegel; leer ohne Dokument oder wenn der User das Feld nicht lesen darf
 * @param distance              über wie viele Beziehungen er mit dem Startartikel verbunden ist: 1 heißt direkt am
 *                              Artikel hinterlegt, 2 heißt über eine Alternative, und so weiter; bei ALTERNATIVE die
 *                              Zahl der Alternativschritte
 * @param pathArtikelNummern    der Weg vom Startartikel bis zu dem Artikel, an dem die Beziehung hängt, Startartikel
 *                              zuerst; der kürzeste, wenn es mehrere gibt
 * @param foundByReverseLookup  false: der letzte Artikel des Weges führt diesen Treffer in seiner Liste des Flags.
 *                              true: der Treffer führt den letzten Artikel des Weges in seiner Liste (Rückverweis)
 */
public record ArtikelAggregatedReference(String artikelNummer, String sternzeit, String name, int distance,
		List<String> pathArtikelNummern, boolean foundByReverseLookup) {

	public ArtikelAggregatedReference {
		sternzeit = sternzeit == null ? "" : sternzeit;
		name = name == null ? "" : name;
		pathArtikelNummern = pathArtikelNummern == null ? List.of() : List.copyOf(pathArtikelNummern);
	}

}
