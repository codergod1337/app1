package codergod1337.app1.solr.dms.model;

import java.time.Instant;
import java.util.List;

/**
 * Eine Zeile der Revisionsliste, allein aus dem Register: der gesicherte Stand und zugleich die Änderung, die ihn
 * abgelöst hat; changedFieldNames sind die Felder, deren Wert sich geändert hat. Revision 0 heißt: so wurde es
 * hochgeladen. Der Änderer steht als guid, den Namen löst das Frontend auf (User 2026-10-10).
 */
public record DmsSolrDocumentRevisionForClient(long revisionNo, Instant changedAt, String changedByUsersGuid,
		List<String> changedFieldNames) {
}
