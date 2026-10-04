package codergod1337.app1.dms.controller;

import codergod1337.app1.dms.model.DmsCurrentUsersGrantsForClient;
import codergod1337.app1.dms.model.DmsGrantedDocumentsPageRequest;
import codergod1337.app1.dms.service.DmsCurrentUsersGrantsService;
import codergod1337.app1.solr.document.service.SolrExactFieldValueResolveService.SolrResolvedDocumentsPage;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Der Tab „Meine Freigaben“ im DMS. Der Pfad liegt unter /api/rest/v1/**, also angemeldet, die AR DMS prüft der Service.
 * Nur die eigenen Freigaben, die guid kommt aus dem Token.
 */
@RestController
@RequestMapping("/api/rest/v1/dms/my-grants")
public class DmsCurrentUsersGrantsController {

	private final DmsCurrentUsersGrantsService dmsCurrentUsersGrantsService;

	public DmsCurrentUsersGrantsController(DmsCurrentUsersGrantsService dmsCurrentUsersGrantsService) {
		this.dmsCurrentUsersGrantsService = dmsCurrentUsersGrantsService;
	}

	/** Das Register: Datei- und Feldfreigaben des current users im Kern DMS, alle Status, neueste zuerst */
	@GetMapping
	public DmsCurrentUsersGrantsForClient currentUsersGrants() {
		return dmsCurrentUsersGrantsService.currentUsersGrantsForClient();
	}

	/** Die Dokumente, die dem current user freigegeben sind, seitenweise. Body optional: page, size. */
	@PostMapping("/documents")
	public SolrResolvedDocumentsPage resolveDocumentsPageGrantedToCurrentUser(
			@RequestBody(required = false) DmsGrantedDocumentsPageRequest pageRequest) {
		return dmsCurrentUsersGrantsService.resolveDocumentsPageGrantedToCurrentUser(
				pageRequest == null ? null : pageRequest.page(), pageRequest == null ? null : pageRequest.size());
	}

}
