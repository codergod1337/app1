package codergod1337.app1.dms.controller;

import codergod1337.app1.dms.model.DmsExactFieldValueResolveRequest;
import codergod1337.app1.dms.model.DmsResolvedDocumentsPage;
import codergod1337.app1.dms.service.DmsExactFieldValueResolveService;
import codergod1337.app1.solr.access.model.SolrCurrentUserReadAndWriteAccessForCore.SolrCurrentUserCoreAccessForClient;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Das Auflösen im DMS. Der Pfad liegt unter /api/rest/v1/**, also angemeldet, die AR DMS prüft der Service.
 *
 * GET /access sagt der Maske, welche Felder der current user lesen und nach welchen er auflösen darf. POST /resolve löst
 * auf: fieldValuesByFieldName als JSON-Objekt, je Feld ein Wert oder eine Liste, dazu page, size, sortFieldName,
 * sortDescending. Suche kommt später als eigener Endpunkt.
 */
@RestController
@RequestMapping("/api/rest/v1/dms")
public class DmsExactFieldValueResolveController {

	private final DmsExactFieldValueResolveService dmsExactFieldValueResolveService;

	public DmsExactFieldValueResolveController(DmsExactFieldValueResolveService dmsExactFieldValueResolveService) {
		this.dmsExactFieldValueResolveService = dmsExactFieldValueResolveService;
	}

	/** Was der current user im Kern DMS darf: Vollsicht oder nicht, Suche oder nicht, lesbare und auflösbare Felder */
	@GetMapping("/access")
	public SolrCurrentUserCoreAccessForClient currentUserDmsAccessForClient() {
		return dmsExactFieldValueResolveService.currentUserDmsAccessForClient();
	}

	/** Auflösen, mindestens eine Bedingung mit Wert ist Pflicht: Auflösen gibt nie alles aus. */
	@PostMapping("/resolve")
	public DmsResolvedDocumentsPage resolveDmsDocumentsPageByExactFieldValues(
			@RequestBody DmsExactFieldValueResolveRequest resolveRequest) {
		if (resolveRequest == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "der Body fehlt");
		}
		if (resolveRequest.fieldValuesByFieldName() != null && !resolveRequest.fieldValuesByFieldName().isNull()
				&& !resolveRequest.fieldValuesByFieldName().isObject()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "fieldValuesByFieldName ist kein JSON-Objekt");
		}
		return dmsExactFieldValueResolveService.resolveDmsSolrDocumentsPageForResolveTab(resolveRequest);
	}

}
