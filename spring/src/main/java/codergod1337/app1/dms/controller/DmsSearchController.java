package codergod1337.app1.dms.controller;

import codergod1337.app1.dms.model.DmsSearchRequest;
import codergod1337.app1.dms.model.DmsSearchResultPage;
import codergod1337.app1.dms.model.DmsSuggestResult;
import codergod1337.app1.dms.service.DmsSearchService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Die Suche im DMS. Der Pfad liegt unter /api/rest/v1/**, also angemeldet, die AR DMS und DMS_SUCHE prüft der Service.
 *
 * POST /search: searchText, exactFilterValuesByFieldName (je Feld eine Liste von Texten), dateRangeFilterByFieldName
 * (je Datumsfeld from und to als Tag), page, size. Antwort immer eine Trefferseite, auch leer, nie 404.
 *
 * POST /search/suggest: derselbe Body ohne Seite, die Vervollständigung beim Tippen: kompakte Treffer und die
 * Filterangebote, stumm leer, wenn es nichts zu suchen gibt.
 */
@RestController
@RequestMapping("/api/rest/v1/dms")
public class DmsSearchController {

	private final DmsSearchService dmsSearchService;

	public DmsSearchController(DmsSearchService dmsSearchService) {
		this.dmsSearchService = dmsSearchService;
	}

	@PostMapping("/search")
	public DmsSearchResultPage searchDmsDocumentsPage(@RequestBody DmsSearchRequest searchRequest) {
		if (searchRequest == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "der Body fehlt");
		}
		return dmsSearchService.searchDmsSolrDocumentsPage(searchRequest);
	}

	@PostMapping("/search/suggest")
	public DmsSuggestResult suggestDmsDocuments(@RequestBody DmsSearchRequest searchRequest) {
		if (searchRequest == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "der Body fehlt");
		}
		return dmsSearchService.suggestDmsSolrDocuments(searchRequest);
	}

}
