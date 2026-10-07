package codergod1337.app1.dms.controller;

import codergod1337.app1.dms.model.DmsExpiredDocumentGrantsCount;
import codergod1337.app1.dms.model.DmsNewDocumentGrantRequest;
import codergod1337.app1.dms.service.DmsDocumentGrantService;
import codergod1337.app1.dms.service.DmsExactFieldValueResolveService;
import codergod1337.app1.solr.grant.model.GrantableDocumentsPreviewPage;
import codergod1337.app1.solr.grant.model.SolrDocumentGrantedDocsPerCore;
import codergod1337.app1.system.HelperInputs;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Die Dateifreigabe im Kern DMS, je Kern ein Controller (Vorgabe 2026-10-07): Vorschau, Freigeben, Entziehen, Ablauf.
 * Das Register aller Kerne liest der SolrGrantAdminController. Vorerst nur für ADMIN (Pfad /api/rest/v1/admin/** und
 * @PreAuthorize im Service), später eine eigene AR.
 *
 * Keine Parameter in der URL, alles im Body. Gelöscht wird nichts: DELETE entzieht, die Zeile bleibt als Protokoll.
 */
@RestController
@RequestMapping("/api/rest/v1/admin/dms/grant/document")
public class DmsDocumentGrantAdminController {

	private final DmsDocumentGrantService dmsDocumentGrantService;

	public DmsDocumentGrantAdminController(DmsDocumentGrantService dmsDocumentGrantService) {
		this.dmsDocumentGrantService = dmsDocumentGrantService;
	}

	/**
	 * Vorschau: die Treffer dieser Bedingungen, die der Freigebende schreiben darf, seitenweise, dazu ob sich das
	 * freigeben lässt (weniger als 500 Treffer). page ab 0, size 1 bis 200, beides optional.
	 */
	@PostMapping("/preview")
	public GrantableDocumentsPreviewPage previewGrantableDmsDocumentsPage(
			@RequestBody DmsNewDocumentGrantRequest documentGrantData) {
		int page = documentGrantData.page() != null ? documentGrantData.page() : 0;
		int size = documentGrantData.size() != null ? documentGrantData.size()
				: DmsExactFieldValueResolveService.DEFAULT_PAGE_SIZE;
		return dmsDocumentGrantService.previewGrantableDmsDocumentsPage(documentGrantData.fieldValuesByFieldName(), page,
				size);
	}

	/** Neue Dateifreigabe. Zurück kommt die Zeile mit der Zahl der Dokumente, die die guid bekamen. */
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public SolrDocumentGrantedDocsPerCore grantDmsDocumentsToUsers(
			@RequestBody DmsNewDocumentGrantRequest newDocumentGrantData) {
		return dmsDocumentGrantService.grantDmsDocumentsToUsers(readGuid(newDocumentGrantData.granteeUsersGuid()),
				newDocumentGrantData.fieldValuesByFieldName(), readValidUntil(newDocumentGrantData.validUntil()));
	}

	/** Entzieht die Dateifreigabe mit dieser id: die guid verschwindet aus den Dokumenten, die Zeile wird REVOKED. */
	@DeleteMapping
	public SolrDocumentGrantedDocsPerCore revokeDmsDocumentGrant(@RequestBody Map<String, Object> documentGrantToRevoke) {
		return dmsDocumentGrantService.revokeDmsDocumentGrant(readId(documentGrantToRevoke.get("id")));
	}

	/** Setzt alle abgelaufenen Dateifreigaben des Kerns DMS auf EXPIRED, samt guid aus den Dokumenten */
	@PostMapping("/expire")
	public DmsExpiredDocumentGrantsCount expireDmsDocumentGrants() {
		return new DmsExpiredDocumentGrantsCount(dmsDocumentGrantService.expireDmsDocumentGrants());
	}

	private static UUID readGuid(String guidText) {
		if (guidText == null || !HelperInputs.isValidGuid(guidText)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "granteeUsersGuid fehlt oder ist keine gültige guid");
		}
		return UUID.fromString(guidText);
	}

	/** null oder leer heißt unbegrenzt, sonst ISO 8601 mit Zone, z. B. 2026-12-31T23:00:00Z */
	private static Instant readValidUntil(String validUntil) {
		if (validUntil == null || validUntil.isBlank()) {
			return null;
		}
		try {
			return Instant.parse(validUntil.strip());
		} catch (DateTimeParseException e) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					"validUntil ist kein Zeitpunkt nach ISO 8601, z. B. 2026-12-31T23:00:00Z");
		}
	}

	private static Long readId(Object value) {
		if (value instanceof Number number) {
			return number.longValue();
		}
		if (value instanceof String text) {
			try {
				return Long.parseLong(text.strip());
			} catch (NumberFormatException e) {
				// unten
			}
		}
		throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "id fehlt oder ist keine ganze Zahl");
	}

}
