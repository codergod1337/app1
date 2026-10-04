package codergod1337.app1.dms.model;

import codergod1337.app1.solr.grant.model.SolrAdvancedFieldGrantAccessPerCore;
import codergod1337.app1.solr.grant.model.SolrDocumentGrantedDocsPerCore;
import java.util.List;
import java.util.Map;

/**
 * Was dem current user im Kern DMS freigegeben ist, für den Tab „Meine Freigaben“: seine Dateifreigaben und seine
 * Feldfreigaben aus dem Register, alle Status, neueste zuerst, dazu Anzeigenamen zu den guids der Freigebenden. Die
 * Dokumente selbst holt der Tab seitenweise über den eigenen Weg.
 */
public record DmsCurrentUsersGrantsForClient(List<SolrDocumentGrantedDocsPerCore> documentGrants,
		List<SolrAdvancedFieldGrantAccessPerCore> fieldGrants, Map<String, String> usersDisplayNameByGuid) {
}
