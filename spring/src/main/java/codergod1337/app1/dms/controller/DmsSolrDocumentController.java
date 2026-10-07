package codergod1337.app1.dms.controller;

import codergod1337.app1.dms.model.DmsSolrDocumentForClient;
import codergod1337.app1.dms.model.DmsSolrDocumentGrantsForClient;
import codergod1337.app1.dms.model.DmsSolrDocumentPatchRequest;
import codergod1337.app1.dms.service.DmsCurrentUsersGrantsService;
import codergod1337.app1.dms.service.DmsExactFieldValueResolveService;
import codergod1337.app1.dms.service.DmsSolrDocumentWriteService;
import codergod1337.app1.system.HelperInputs;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Ein einzelnes DMS-Dokument: lesen für Detailansicht, Bearbeiten und Viewer, ändern über die Bearbeiten-Maske als
 * Patch, weich löschen. Der Pfad liegt unter /api/rest/v1/**, also angemeldet, die AR DMS und die Prüfungen am Dokument
 * machen die Services: Lesen über die Lese-Tür des DMS, Schreiben über den Schreibservice des DMS.
 */
@RestController
@RequestMapping("/api/rest/v1/dms/solr-document")
public class DmsSolrDocumentController {

	private final DmsExactFieldValueResolveService dmsExactFieldValueResolveService;
	private final DmsSolrDocumentWriteService dmsSolrDocumentWriteService;
	private final DmsCurrentUsersGrantsService dmsCurrentUsersGrantsService;

	public DmsSolrDocumentController(DmsExactFieldValueResolveService dmsExactFieldValueResolveService,
			DmsSolrDocumentWriteService dmsSolrDocumentWriteService,
			DmsCurrentUsersGrantsService dmsCurrentUsersGrantsService) {
		this.dmsExactFieldValueResolveService = dmsExactFieldValueResolveService;
		this.dmsSolrDocumentWriteService = dmsSolrDocumentWriteService;
		this.dmsCurrentUsersGrantsService = dmsCurrentUsersGrantsService;
	}

	/** Das Dokument mit den lesbaren Feldern und dem Flag, ob der User es ändern darf. 404 sonst. */
	@GetMapping("/{id}")
	public DmsSolrDocumentForClient loadDmsSolrDocument(@PathVariable String id) {
		requireSha256(id);
		return dmsExactFieldValueResolveService.loadDmsSolrDocumentForDocumentPage(id);
	}

	/** Der Kontrollblick: wer dieses Dokument per Freigabe hat. Vorerst ADMIN, das prüft der Service. */
	@GetMapping("/{id}/grants")
	public DmsSolrDocumentGrantsForClient loadGrantsOfDmsSolrDocument(@PathVariable String id) {
		requireSha256(id);
		return dmsCurrentUsersGrantsService.loadGrantsOfDmsSolrDocumentForAdminCheck(id);
	}

	/** Der Patch der Bearbeiten-Maske: nur die geänderten Felder mit ihrem Endwert, dazu die id. Zurück kommt das Dokument, wie es jetzt steht. */
	@PatchMapping
	public DmsSolrDocumentForClient editDmsSolrDocument(@RequestBody DmsSolrDocumentPatchRequest patchRequest) {
		if (patchRequest == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "der Body fehlt");
		}
		requireSha256(patchRequest.id());
		if (patchRequest.fieldValuesByFieldName() == null || !patchRequest.fieldValuesByFieldName().isObject()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "fieldValuesByFieldName ist kein JSON-Objekt");
		}
		return dmsSolrDocumentWriteService.editDmsSolrDocument(patchRequest);
	}

	/** Weiches Löschen: Body {id}. Das Dokument bekommt den Status DELETED, die Datei bleibt. */
	@DeleteMapping
	public DmsSolrDocumentForClient softDeleteDmsSolrDocument(@RequestBody Map<String, Object> documentToDelete) {
		Object id = documentToDelete.get("id");
		requireSha256(id instanceof String text ? text : null);
		return dmsSolrDocumentWriteService.softDeleteDmsSolrDocument((String) id);
	}

	private static void requireSha256(String id) {
		if (id == null || !HelperInputs.isValidSha256Hex(id)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "id ist kein SHA-256 in Hex (64 Zeichen, klein)");
		}
	}

}
