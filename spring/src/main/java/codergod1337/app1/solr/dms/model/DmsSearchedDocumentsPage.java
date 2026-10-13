package codergod1337.app1.solr.dms.model;

import java.util.List;

/**
 * Das Ergebnis einer Suche: wie viele Dokumente insgesamt treffen (auch über die gelieferten hinaus) und die besten
 * Treffer nach Score, höchstens app1.solr.search.hits, keine Seiten.
 */
public record DmsSearchedDocumentsPage(long totalFound, List<DmsSearchHit> hits) {
}
