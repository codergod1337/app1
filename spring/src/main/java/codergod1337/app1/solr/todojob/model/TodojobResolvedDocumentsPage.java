package codergod1337.app1.solr.todojob.model;

import java.util.List;
import java.util.Map;

/**
 * Eine Seite Jobs: wie viele insgesamt treffen, welche Seite, wie groß eine Seite ist und die Jobs mit den Feldern, die
 * der User lesen darf. Dieselbe Form wie das Auflösen in DMS, KONTAKTE und NACHRICHTEN.
 */
public record TodojobResolvedDocumentsPage(long totalFound, int page, int pageSize, List<Map<String, Object>> documents) {
}
