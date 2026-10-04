package codergod1337.app1.dms.service;

import codergod1337.app1.solr.access.model.SolrCurrentUserReadAndWriteAccessForCore.SolrCurrentUserCoreAccessForClient;
import codergod1337.app1.solr.access.service.SolrCurrentUserCoreAccessService;
import codergod1337.app1.solr.document.model.SolrExactFieldValueResolveRequest;
import codergod1337.app1.solr.document.service.SolrExactFieldValueResolveService;
import codergod1337.app1.solr.document.service.SolrExactFieldValueResolveService.SolrResolvedDocumentsPage;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

/**
 * Das Auflösen im DMS, dünn: der Kern DMS, sonst nichts Eigenes. Was der User darf, baut der
 * SolrCurrentUserCoreAccessService, das Auflösen macht der SolrExactFieldValueResolveService. Fachliches, das später
 * dazukommt (Download-Zähler, Revisionen), gehört hierher.
 */
@Service
public class DmsExactFieldValueResolveService {

	private final SolrCurrentUserCoreAccessService solrCurrentUserCoreAccessService;
	private final SolrExactFieldValueResolveService solrExactFieldValueResolveService;

	public DmsExactFieldValueResolveService(SolrCurrentUserCoreAccessService solrCurrentUserCoreAccessService,
			SolrExactFieldValueResolveService solrExactFieldValueResolveService) {
		this.solrCurrentUserCoreAccessService = solrCurrentUserCoreAccessService;
		this.solrExactFieldValueResolveService = solrExactFieldValueResolveService;
	}

	/** Was der current user im Kern DMS darf und sieht: danach baut die Maske, welche Felder sie anbietet. */
	@PreAuthorize("hasRole('DMS')")
	public SolrCurrentUserCoreAccessForClient currentUserDmsAccessForClient() {
		return solrCurrentUserCoreAccessService.readAndWriteAccessOfCurrentUserForCore(DmsUploadService.CORE_KEY)
				.forClient();
	}

	/** Auflösen im Kern DMS mit den Prüfungen des current users. Fehlende Angaben nehmen die Standards. */
	@PreAuthorize("hasRole('DMS')")
	public SolrResolvedDocumentsPage resolveDmsDocumentsPageByExactFieldValues(
			SolrExactFieldValueResolveRequest resolveRequest) {
		int page = resolveRequest.page() != null ? resolveRequest.page() : 0;
		int size = resolveRequest.size() != null ? resolveRequest.size()
				: SolrExactFieldValueResolveService.DEFAULT_PAGE_SIZE;
		String sortFieldName = resolveRequest.sortFieldName() != null && !resolveRequest.sortFieldName().isBlank()
				? resolveRequest.sortFieldName().strip()
				: null;
		boolean sortDescending = Boolean.TRUE.equals(resolveRequest.sortDescending());
		return solrExactFieldValueResolveService.resolveDocumentsPageByExactFieldValues(DmsUploadService.CORE_KEY,
				resolveRequest.fieldValuesByFieldName(), page, size, sortFieldName, sortDescending);
	}

}
