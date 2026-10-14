package codergod1337.app1.solr.kontakte.model;

import java.util.List;

/**
 * Das Ergebnis einer Suche im Kern KONTAKTE: wie viele Firmen und Ansprechpartner insgesamt treffen (auch über die
 * gelieferten hinaus) und eine Seite der Treffer nach Score. Seiten ab 0, je Seite app1.solr.search.hits Treffer; die
 * Maske hängt mit „weitere laden“ die nächste Seite an (User 2026-10-14). Dieselbe Form wie bei DMS und ARTIKEL, damit
 * die eine Suchmaske alle Kerne gleich behandelt.
 */
public record KontakteSearchedDocumentsPage(long totalFound, int page, int pageSize, List<KontakteSearchHit> hits) {
}
