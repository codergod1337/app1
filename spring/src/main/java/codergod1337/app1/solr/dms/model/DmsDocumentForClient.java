package codergod1337.app1.solr.dms.model;

import java.util.Map;

/**
 * Ein DMS-Dokument für Detailansicht, Bearbeiten und Viewer: die Felder, die der User lesen darf, und ob writeAcl
 * eine seiner ARs enthält. Das Flag ist Anzeige (Stift, Mülleimer), das Backend prüft beim Schreiben selbst.
 */
public record DmsDocumentForClient(Map<String, Object> document, boolean writeAclMatchForCurrentUser) {
}
