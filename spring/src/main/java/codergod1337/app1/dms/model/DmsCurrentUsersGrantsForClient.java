package codergod1337.app1.dms.model;

import codergod1337.app1.solr.grant.model.SolrAdvancedFieldGrantAccessPerCore;
import codergod1337.app1.solr.grant.model.SolrDocumentGrantedDocsPerCore;
import codergod1337.app1.solr.grant.model.SolrHookGrantAccess;
import java.util.List;
import java.util.Map;

/**
 * Was dem current user freigegeben ist, für den Tab „Meine Freigaben“ im DMS: seine Dateifreigaben im Kern DMS, seine
 * Feldfreigaben im Kern DMS und seine Hook-Freigaben (serverweit) aus dem Register, alle Status, neueste zuerst, dazu
 * Anzeigenamen zu den guids der Freigebenden. Die Dokumente selbst holt der Tab seitenweise über den eigenen Weg.
 */
public record DmsCurrentUsersGrantsForClient(List<SolrDocumentGrantedDocsPerCore> documentGrants,
		List<SolrAdvancedFieldGrantAccessPerCore> fieldGrants, List<SolrHookGrantAccess> hookGrants,
		Map<String, String> usersDisplayNameByGuid) {
}
