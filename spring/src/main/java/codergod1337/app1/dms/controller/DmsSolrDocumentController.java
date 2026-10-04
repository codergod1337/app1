package codergod1337.app1.dms.controller;

import codergod1337.app1.dms.model.DmsSolrDocumentEditRequest;
import codergod1337.app1.dms.model.DmsSolrDocumentForClient;
import codergod1337.app1.dms.model.DmsSolrDocumentGrantsForClient;
import codergod1337.app1.dms.service.DmsSolrDocumentService;
import codergod1337.app1.system.HelperInputs;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Ein einzelnes DMS-Dokument: lesen für Detailansicht, Bearbeiten und Viewer, ändern über die Bearbeiten-Maske. Der Pfad
 * liegt unter /api/rest/v1/**, also angemeldet, die AR DMS und die Prüfungen am Dokument macht der Service.
 */
@RestController
@RequestMapping("/api/rest/v1/dms/solr-document")
public class DmsSolrDocumentController {

	private final DmsSolrDocumentService dmsSolrDocumentService;

	public DmsSolrDocumentController(DmsSolrDocumentService dmsSolrDocumentService) {
		this.dmsSolrDocumentService = dmsSolrDocumentService;
	}

	/** Das Dokument mit den lesbaren Feldern und dem Flag, ob der User es ändern darf. 404 sonst. */
	@GetMapping("/{id}")
	public DmsSolrDocumentForClient loadDmsSolrDocument(@PathVariable String id) {
		requireSha256(id);
		return dmsSolrDocumentService.loadDmsSolrDocumentForClient(id);
	}

	/** Der Kontrollblick: wer dieses Dokument per Freigabe hat. Vorerst ADMIN, das prüft der Service. */
	@GetMapping("/{id}/grants")
	public DmsSolrDocumentGrantsForClient loadGrantsOfDmsSolrDocument(@PathVariable String id) {
		requireSha256(id);
		return dmsSolrDocumentService.loadGrantsOfDmsSolrDocument(id);
	}

	/** Ändert, was im Body steht, alles andere bleibt. Zurück kommt das Dokument, wie es jetzt steht. */
	@PutMapping
	public DmsSolrDocumentForClient editDmsSolrDocument(@RequestBody DmsSolrDocumentEditRequest editRequest) {
		if (editRequest == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "der Body fehlt");
		}
		requireSha256(editRequest.id());
		return dmsSolrDocumentService.editDmsSolrDocument(editRequest);
	}

	private static void requireSha256(String id) {
		if (id == null || !HelperInputs.isValidSha256Hex(id)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "id ist kein SHA-256 in Hex (64 Zeichen, klein)");
		}
	}

}
