package codergod1337.app1.solr.kontakte.model;

import java.util.List;

/**
 * Das Ergebnis einer Suche im Kern KONTAKTE: wie viele Firmen und Ansprechpartner insgesamt treffen (auch über die
 * gelieferten hinaus) und die besten Treffer nach Score, höchstens app1.solr.search.hits, keine Seiten. Dieselbe Form
 * wie bei DMS und ARTIKEL, damit die eine Suchmaske alle Kerne gleich behandelt.
 */
public record KontakteSearchedDocumentsPage(long totalFound, List<KontakteSearchHit> hits) {
}
