package codergod1337.app1.dms.controller;

import codergod1337.app1.dms.model.DmsSolrDocumentForClient;
import codergod1337.app1.dms.model.DmsSolrDocumentGrantsForClient;
import codergod1337.app1.dms.model.DmsSolrDocumentPatchRequest;
import codergod1337.app1.dms.model.DmsSolrDocumentRevisionForClient;
import codergod1337.app1.dms.service.DmsCurrentUsersGrantsService;
import codergod1337.app1.dms.service.DmsExactFieldValueResolveService;
import codergod1337.app1.dms.service.DmsSolrDocumentRevisionService;
import codergod1337.app1.dms.service.DmsSolrDocumentWriteService;
import codergod1337.app1.system.HelperInputs;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Ein einzelnes DMS-Dokument: lesen für Detailansicht, Bearbeiten und Viewer, ändern über die Bearbeiten-Maske als
 * Patch, weich löschen, seine Revisionen lesen und eine zurückladen. Der Pfad liegt unter /api/rest/v1/**, also
 * angemeldet, die AR DMS und die Prüfungen am Dokument machen die Services: Lesen über die Lese-Tür des DMS, Schreiben
 * über den Schreibservice des DMS, Revisionen über den Revisionsservice mit der Lese-Tür als Tor.
 */
@RestController
@RequestMapping("/api/rest/v1/dms/solr-document")
public class DmsSolrDocumentController {

	private final DmsExactFieldValueResolveService dmsExactFieldValueResolveService;
	private final DmsSolrDocumentWriteService dmsSolrDocumentWriteService;
	private final DmsCurrentUsersGrantsService dmsCurrentUsersGrantsService;
	private final DmsSolrDocumentRevisionService dmsSolrDocumentRevisionService;

	public DmsSolrDocumentController(DmsExactFieldValueResolveService dmsExactFieldValueResolveService,
			DmsSolrDocumentWriteService dmsSolrDocumentWriteService,
			DmsCurrentUsersGrantsService dmsCurrentUsersGrantsService,
			DmsSolrDocumentRevisionService dmsSolrDocumentRevisionService) {
		this.dmsExactFieldValueResolveService = dmsExactFieldValueResolveService;
		this.dmsSolrDocumentWriteService = dmsSolrDocumentWriteService;
		this.dmsCurrentUsersGrantsService = dmsCurrentUsersGrantsService;
		this.dmsSolrDocumentRevisionService = dmsSolrDocumentRevisionService;
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

	/** Weiches Löschen: Body {id}. Das Dokument bekommt den Status DELETED, die Datei bleibt, der User sieht es nicht mehr. */
	@DeleteMapping
	public void softDeleteDmsSolrDocument(@RequestBody Map<String, Object> documentToDelete) {
		Object id = documentToDelete.get("id");
		requireSha256(id instanceof String text ? text : null);
		dmsSolrDocumentWriteService.softDeleteDmsSolrDocument((String) id);
	}

	/** Die Revisionen eines Dokuments, neueste zuerst, allein aus Postgres. 404, wenn der User das Dokument nicht sehen darf. */
	@GetMapping("/{id}/revisions")
	public List<DmsSolrDocumentRevisionForClient> loadDmsSolrDocumentRevisions(@PathVariable String id) {
		requireSha256(id);
		return dmsSolrDocumentRevisionService.loadDmsSolrDocumentRevisions(id);
	}

	/** Eine Revision als fertiges JSON: Das Backend entpackt das ZIP, das Frontend rechnet den Unterschied selbst. */
	@GetMapping("/{id}/revision/{revisionNo}")
	public Map<String, Object> loadDmsSolrDocumentRevision(@PathVariable String id, @PathVariable long revisionNo) {
		requireSha256(id);
		requireRevisionNo(revisionNo);
		return dmsSolrDocumentRevisionService.loadDmsSolrDocumentRevision(id, revisionNo);
	}

	/**
	 * Zurückladen: Body {id, revisionNo}, kein Feldwert von außen, das Backend liest sein eigenes ZIP. Zurück kommt das
	 * Dokument, wie es jetzt steht, oder null, wenn der User es danach nicht mehr sehen darf.
	 */
	@PostMapping("/revision/restore")
	public DmsSolrDocumentForClient restoreDmsSolrDocumentRevision(@RequestBody Map<String, Object> restoreRequest) {
		Object id = restoreRequest.get("id");
		requireSha256(id instanceof String text ? text : null);
		Object revisionNo = restoreRequest.get("revisionNo");
		if (!(revisionNo instanceof Number number)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "revisionNo fehlt oder ist keine Zahl");
		}
		requireRevisionNo(number.longValue());
		return dmsSolrDocumentWriteService.restoreDmsSolrDocumentRevision((String) id, number.longValue());
	}

	private static void requireRevisionNo(long revisionNo) {
		if (revisionNo < 0) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "revisionNo muss 0 oder größer sein");
		}
	}

	private static void requireSha256(String id) {
		if (id == null || !HelperInputs.isValidSha256Hex(id)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "id ist kein SHA-256 in Hex (64 Zeichen, klein)");
		}
	}

}
