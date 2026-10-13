package codergod1337.app1.solr.dms.model;

import java.util.List;
import java.util.Map;

/**
 * Eine Seite einer Auflösung: wie viele Dokumente insgesamt treffen (auch über das Limit hinaus), welche Seite das ist,
 * wie viele je Seite kommen, und die Dokumente dieser Seite als Maps Feldname -> Wert.
 */
public record DmsResolvedDocumentsPage(long totalFound, int page, int pageSize, List<Map<String, Object>> documents) {
}
