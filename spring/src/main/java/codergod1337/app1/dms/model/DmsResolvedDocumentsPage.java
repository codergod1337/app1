package codergod1337.app1.dms.model;

import java.util.List;
import java.util.Map;

/**
 * Eine Trefferseite des Auflösens im Kern DMS: wie viele Dokumente es insgesamt gibt, welche Seite das ist und ihre
 * Dokumente als Maps mit den Feldern, die der current user lesen darf, dazu Solrs _version_ als Text.
 */
public record DmsResolvedDocumentsPage(long totalFound, int page, int size, List<Map<String, Object>> documents) {
}
