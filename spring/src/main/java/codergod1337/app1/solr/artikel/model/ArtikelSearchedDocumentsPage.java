package codergod1337.app1.solr.artikel.model;

import java.util.List;

/**
 * Das Ergebnis einer Suche im Kern ARTIKEL: wie viele Artikel insgesamt treffen (auch über die gelieferten hinaus) und
 * eine Seite der Treffer nach Score. Seiten ab 0, je Seite app1.solr.search.hits Treffer; die Maske hängt mit „weitere
 * laden“ die nächste Seite an (User 2026-10-14).
 */
public record ArtikelSearchedDocumentsPage(long totalFound, int page, int pageSize, List<ArtikelSearchHit> hits) {
}
