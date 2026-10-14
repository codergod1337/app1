package codergod1337.app1.solr.nachrichten.model;

import java.util.List;
import java.util.Map;

/**
 * Eine Seite des Auflösens im Kern NACHRICHTEN: wie viele Nachrichten insgesamt treffen, welche Seite, wie groß eine
 * Seite ist (app1.solr.resolve.page-size) und die Nachrichten mit den Feldern, die der User lesen darf. Dieselbe Form wie
 * bei DMS und KONTAKTE.
 */
public record NachrichtenResolvedDocumentsPage(long totalFound, int page, int pageSize,
		List<Map<String, Object>> documents) {
}
