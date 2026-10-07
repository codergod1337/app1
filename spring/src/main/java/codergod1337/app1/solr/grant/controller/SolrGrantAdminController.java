package codergod1337.app1.solr.grant.controller;

import codergod1337.app1.solr.grant.model.ExpiredGrantsCount;
import codergod1337.app1.solr.grant.model.NewAdvancedFieldGrantRequest;
import codergod1337.app1.solr.grant.model.NewHookGrantRequest;
import codergod1337.app1.solr.grant.model.SolrAdvancedFieldGrantAccessPerCore;
import codergod1337.app1.solr.grant.model.SolrDocumentGrantedDocsPerCore;
import codergod1337.app1.solr.grant.model.SolrHookGrantAccess;
import codergod1337.app1.solr.grant.service.SolrAdvancedFieldGrantAccessPerCoreService;
import codergod1337.app1.solr.grant.service.SolrDocumentGrantedDocsPerCoreService;
import codergod1337.app1.solr.grant.service.SolrHookGrantAccessService;
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
 * Das Modul „Freigaben“, der Teil, der nur in Postgres steht: Hook-Freigaben (serverweit), Feldfreigaben (je Kern) und
 * das Register aller Dateifreigaben zum Lesen. Setzen, Entziehen und Ablauf einer Dateifreigabe macht je Kern dessen
 * eigener Controller, im DMS der DmsDocumentGrantAdminController. Vorerst nur für ADMIN (Pfad /api/rest/v1/admin/**
 * und @PreAuthorize im Service), später eine eigene AR.
 *
 * Keine Parameter in der URL, alles im Body. Gelöscht wird nichts: DELETE entzieht, die Zeile bleibt als Protokoll.
 */
@RestController
@RequestMapping("/api/rest/v1/admin/solr/grant")
public class SolrGrantAdminController {

	private final SolrHookGrantAccessService hookGrantService;
	private final SolrAdvancedFieldGrantAccessPerCoreService advancedFieldGrantService;
	private final SolrDocumentGrantedDocsPerCoreService documentGrantRegisterService;

	public SolrGrantAdminController(SolrHookGrantAccessService hookGrantService,
			SolrAdvancedFieldGrantAccessPerCoreService advancedFieldGrantService,
			SolrDocumentGrantedDocsPerCoreService documentGrantRegisterService) {
		this.hookGrantService = hookGrantService;
		this.advancedFieldGrantService = advancedFieldGrantService;
		this.documentGrantRegisterService = documentGrantRegisterService;
	}

	// ===== Hook-Freigaben, serverweit =====

	/** Alle Hook-Freigaben, auch entzogene und abgelaufene, neueste zuerst */
	@GetMapping("/hook")
	public List<SolrHookGrantAccess> getAllHookGrants() {
		return hookGrantService.getAllHookGrants();
	}

	/** Neue Hook-Freigaben: eine Zeile je Hook. Zurück kommen die angelegten Zeilen. */
	@PostMapping("/hook")
	@ResponseStatus(HttpStatus.CREATED)
	public List<SolrHookGrantAccess> grantHooksToUsers(@RequestBody NewHookGrantRequest newHookGrantData) {
		return hookGrantService.grantHooksToUsers(readGuid(newHookGrantData.granteeUsersGuid()),
				newHookGrantData.hookKeys(), readValidUntil(newHookGrantData.validUntil()));
	}

	/** Entzieht die Hook-Freigabe mit dieser id. Zurück kommt die Zeile mit Status REVOKED. */
	@DeleteMapping("/hook")
	public SolrHookGrantAccess revokeHookGrant(@RequestBody Map<String, Object> hookGrantToRevoke) {
		return hookGrantService.revokeHookGrant(readId(hookGrantToRevoke.get("id")));
	}

	// ===== Feldfreigaben, je Kern =====

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

	// ===== Register der Dateifreigaben, nur lesen =====

	/** Alle Dateifreigaben aller Kerne, auch entzogene und abgelaufene, neueste zuerst */
	@GetMapping("/document")
	public List<SolrDocumentGrantedDocsPerCore> getAllDocumentGrants() {
		return documentGrantRegisterService.getAllDocumentGrants();
	}

	// ===== Ablauf =====

	/** Setzt alle abgelaufenen Hook- und Feldfreigaben auf EXPIRED. Dateifreigaben laufen je Kern ab. */
	@PostMapping("/expire")
	public ExpiredGrantsCount expireGrants() {
		return new ExpiredGrantsCount(hookGrantService.expireHookGrants(), advancedFieldGrantService.expireFieldGrants());
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
