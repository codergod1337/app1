package codergod1337.app1.solr.grant.controller;

import codergod1337.app1.solr.document.service.SolrExactFieldValueResolveService;
import codergod1337.app1.solr.grant.model.ExpiredGrantsCount;
import codergod1337.app1.solr.grant.model.GrantableDocumentsPreviewPage;
import codergod1337.app1.solr.grant.model.NewAdvancedFieldGrantRequest;
import codergod1337.app1.solr.grant.model.NewDocumentGrantRequest;
import codergod1337.app1.solr.grant.model.SolrAdvancedFieldGrantAccessPerCore;
import codergod1337.app1.solr.grant.model.SolrDocumentGrantedDocsPerCore;
import codergod1337.app1.solr.grant.service.SolrAdvancedFieldGrantAccessPerCoreService;
import codergod1337.app1.solr.grant.service.SolrDocumentGrantedDocsPerCoreService;
import codergod1337.app1.system.HelperInputs;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Das Modul „Freigaben“: Dateifreigaben und Feldfreigaben je Kern anlegen, ansehen, entziehen. Vorerst nur für ADMIN
 * (Pfad /api/rest/v1/admin/** und @PreAuthorize im Service), später eine eigene AR.
 *
 * Keine Parameter in der URL, alles im Body. Gelöscht wird nichts: DELETE entzieht, die Zeile bleibt als Protokoll.
 */
@RestController
@RequestMapping("/api/rest/v1/admin/solr/grant")
public class SolrGrantAdminController {

	private final SolrAdvancedFieldGrantAccessPerCoreService advancedFieldGrantService;
	private final SolrDocumentGrantedDocsPerCoreService documentGrantService;

	public SolrGrantAdminController(SolrAdvancedFieldGrantAccessPerCoreService advancedFieldGrantService,
			SolrDocumentGrantedDocsPerCoreService documentGrantService) {
		this.advancedFieldGrantService = advancedFieldGrantService;
		this.documentGrantService = documentGrantService;
	}

	// ===== Feldfreigaben =====

	/** Alle Feldfreigaben, auch entzogene und abgelaufene, neueste zuerst */
	@GetMapping("/field")
	public List<SolrAdvancedFieldGrantAccessPerCore> getAllFieldGrants() {
		return advancedFieldGrantService.getAllFieldGrants();
	}

	/** Neue Feldfreigaben: eine Zeile je Feld. Zurück kommen die angelegten Zeilen. */
	@PostMapping("/field")
	@ResponseStatus(HttpStatus.CREATED)
	public List<SolrAdvancedFieldGrantAccessPerCore> grantAdvancedFieldsToUsers(
			@RequestBody NewAdvancedFieldGrantRequest newAdvancedFieldGrantData) {
		return advancedFieldGrantService.grantAdvancedFieldsToUsers(
				readGuid(newAdvancedFieldGrantData.granteeUsersGuid()), readCoreKey(newAdvancedFieldGrantData.coreKey()),
				newAdvancedFieldGrantData.fieldNames(), readValidUntil(newAdvancedFieldGrantData.validUntil()));
	}

	/** Entzieht die Feldfreigabe mit dieser id. Zurück kommt die Zeile mit Status REVOKED. */
	@DeleteMapping("/field")
	public SolrAdvancedFieldGrantAccessPerCore revokeFieldGrant(@RequestBody Map<String, Object> fieldGrantToRevoke) {
		return advancedFieldGrantService.revokeFieldGrant(readId(fieldGrantToRevoke.get("id")));
	}

	// ===== Dateifreigaben =====

	/** Alle Dateifreigaben, auch entzogene und abgelaufene, neueste zuerst */
	@GetMapping("/document")
	public List<SolrDocumentGrantedDocsPerCore> getAllDocumentGrants() {
		return documentGrantService.getAllDocumentGrants();
	}

	/**
	 * Vorschau: die Treffer dieser Bedingungen, die der Freigebende sieht und ändern darf, seitenweise, dazu ob sich das
	 * freigeben lässt (weniger als 500 Treffer). page ab 0, size 1 bis 200, beides optional.
	 */
	@PostMapping("/document/preview")
	public GrantableDocumentsPreviewPage previewGrantableDocumentsPage(
			@RequestBody NewDocumentGrantRequest documentGrantData) {
		int page = documentGrantData.page() != null ? documentGrantData.page() : 0;
		int size = documentGrantData.size() != null ? documentGrantData.size()
				: SolrExactFieldValueResolveService.DEFAULT_PAGE_SIZE;
		return documentGrantService.previewGrantableDocumentsPage(readCoreKey(documentGrantData.coreKey()),
				documentGrantData.fieldValuesByFieldName(), page, size);
	}

	/** Neue Dateifreigabe. Zurück kommt die Zeile mit der Zahl der Dokumente, die die guid bekamen. */
	@PostMapping("/document")
	@ResponseStatus(HttpStatus.CREATED)
	public SolrDocumentGrantedDocsPerCore grantDocumentsToUsers(@RequestBody NewDocumentGrantRequest newDocumentGrantData) {
		return documentGrantService.grantDocumentsToUsers(readGuid(newDocumentGrantData.granteeUsersGuid()),
				readCoreKey(newDocumentGrantData.coreKey()), newDocumentGrantData.fieldValuesByFieldName(),
				readValidUntil(newDocumentGrantData.validUntil()));
	}

	/** Entzieht die Dateifreigabe mit dieser id: die guid verschwindet aus den Dokumenten, die Zeile wird REVOKED. */
	@DeleteMapping("/document")
	public SolrDocumentGrantedDocsPerCore revokeDocumentGrant(@RequestBody Map<String, Object> documentGrantToRevoke) {
		return documentGrantService.revokeDocumentGrant(readId(documentGrantToRevoke.get("id")));
	}

	// ===== Ablauf =====

	/** Setzt alle abgelaufenen Freigaben auf EXPIRED, bei Dateifreigaben samt guid aus den Dokumenten */
	@PostMapping("/expire")
	public ExpiredGrantsCount expireGrants() {
		return new ExpiredGrantsCount(advancedFieldGrantService.expireFieldGrants(),
				documentGrantService.expireDocumentGrants());
	}

	// ===== Form der Eingaben =====

	private static UUID readGuid(String guidText) {
		if (guidText == null || !HelperInputs.isValidGuid(guidText)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "granteeUsersGuid fehlt oder ist keine gültige guid");
		}
		return UUID.fromString(guidText);
	}

	private static String readCoreKey(String coreKey) {
		if (coreKey == null || !HelperInputs.isValidKey(coreKey)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "coreKey fehlt oder ist kein gültiger Key");
		}
		return coreKey;
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
