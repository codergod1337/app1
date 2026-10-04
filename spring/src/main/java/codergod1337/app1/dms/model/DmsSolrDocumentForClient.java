package codergod1337.app1.dms.model;

import java.util.Map;

/**
 * Ein DMS-Dokument für Detailansicht, Bearbeiten und Viewer: die Felder, die der current user lesen darf, und ob
 * writeAcl eine seiner ARs enthält. Das Flag ist Anzeige, kein Schutz: Jeder Schreibweg prüft selbst noch einmal.
 */
public record DmsSolrDocumentForClient(Map<String, Object> document, boolean writeAclMatchForCurrentUser) {
}
