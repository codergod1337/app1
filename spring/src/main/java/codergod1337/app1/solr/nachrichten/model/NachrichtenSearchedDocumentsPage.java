package codergod1337.app1.solr.nachrichten.model;

import java.util.List;

/**
 * Das Ergebnis einer Suche im Kern NACHRICHTEN: wie viele Nachrichten insgesamt treffen (auch über die gelieferten
 * hinaus) und eine Seite der Treffer nach Score. Seiten ab 0, je Seite app1.solr.search.hits Treffer; die Maske hängt
 * mit „weitere laden“ die nächste Seite an (User 2026-10-14). Dieselbe Form wie bei DMS, ARTIKEL und KONTAKTE.
 */
public record NachrichtenSearchedDocumentsPage(long totalFound, int page, int pageSize, List<NachrichtenSearchHit> hits) {
}
