package codergod1337.app1.solr.artikel.model;

import java.util.List;

/**
 * Das Ergebnis einer Suche im Kern ARTIKEL: wie viele Artikel insgesamt treffen (auch über die gelieferten hinaus) und
 * die besten Treffer nach Score, höchstens app1.solr.search.hits, keine Seiten.
 */
public record ArtikelSearchedDocumentsPage(long totalFound, List<ArtikelSearchHit> hits) {
}
