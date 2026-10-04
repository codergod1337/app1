package codergod1337.app1.dms.model;

import codergod1337.app1.solr.grant.model.SolrDocumentGrantedDocsPerCore;
import java.util.List;
import java.util.Map;

/**
 * Der Kontrollblick auf die Freigaben eines DMS-Dokuments (Vorgabe 2026-10-04): die guids in userGrantedAccess, die
 * Wahrheit für den Zugriff, dazu die wirksamen Dateifreigaben dieser User im Kern DMS aus dem Register und Anzeigenamen
 * zu den guids. Für ADMIN, später weitere Rollen über denselben User-Endpunkt. Anzeige, kein Schutz.
 */
public record DmsSolrDocumentGrantsForClient(List<String> grantedUsersGuids,
		List<SolrDocumentGrantedDocsPerCore> documentGrantsInForce, Map<String, String> usersDisplayNameByGuid) {
}
