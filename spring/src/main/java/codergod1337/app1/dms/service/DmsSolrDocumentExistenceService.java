package codergod1337.app1.dms.service;

import codergod1337.app1.dms.model.DmsSolrDocumentExistenceCheck;
import codergod1337.app1.dms.model.DmsSolrDocumentFieldNames;
import codergod1337.app1.solr.core.model.SolrCore;
import codergod1337.app1.solr.document.service.SolrDocumentService;
import codergod1337.app1.solr.document.service.SolrDocumentService.SolrQueriedDocumentsPage;
import java.util.Map;
import org.apache.solr.client.solrj.request.SolrQuery;
import org.apache.solr.client.solrj.util.ClientUtils;
import org.springframework.stereotype.Service;

/**
 * Der Existenzcheck des Kerns DMS, die erste der drei Lesemethoden je Kern (Vorgabe 2026-10-07): Gibt es diese Bytes
 * schon? Parameter nur die id, Antwort nur id, Dateiart und Status, oder nichts. Keine Rechteprüfung, jeder darf
 * fragen, auch ein öffentlicher Endpunkt später. Mehr kommt nie zurück. Die Stapelmaske fragt damit vor dem Upload,
 * der Upload vor dem Anlegen. Baut seine Abfrage selbst und ruft die rohe Solr-Schicht direkt.
 */
@Service
public class DmsSolrDocumentExistenceService {

	private final SolrDocumentService solrDocumentService;

	public DmsSolrDocumentExistenceService(SolrDocumentService solrDocumentService) {
		this.solrDocumentService = solrDocumentService;
	}

	/** Gibt es ein Dokument mit dieser id? Mit Dateiart und Status, damit die Maske Merge oder Ablehnung vorhersagt. */
	public DmsSolrDocumentExistenceCheck loadDmsSolrDocumentExistenceCheck(String id) {
		SolrQuery query = new SolrQuery(SolrCore.ID_FIELD_NAME + ":" + ClientUtils.escapeQueryChars(id));
		query.setFields(SolrCore.ID_FIELD_NAME, DmsSolrDocumentFieldNames.FILE_SUB_CLASS_KEY,
				DmsSolrDocumentFieldNames.STATUS);
		query.setRows(1);
		SolrQueriedDocumentsPage queried = solrDocumentService.queryDocumentsPage(DmsSolrDocumentFieldNames.CORE_KEY, query);
		if (queried.documents().isEmpty()) {
			return DmsSolrDocumentExistenceCheck.missing();
		}
		Map<String, Object> found = queried.documents().get(0);
		return new DmsSolrDocumentExistenceCheck(true, id, text(found, DmsSolrDocumentFieldNames.FILE_SUB_CLASS_KEY),
				text(found, DmsSolrDocumentFieldNames.STATUS));
	}

	private static String text(Map<String, Object> document, String name) {
		Object value = document.get(name);
		return value == null ? null : value.toString();
	}

}
