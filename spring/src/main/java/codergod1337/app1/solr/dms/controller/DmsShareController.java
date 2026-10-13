package codergod1337.app1.solr.dms.controller;

import codergod1337.app1.solr.dms.model.DmsSharedDocument;
import codergod1337.app1.solr.share.model.CreatedShareToken;
import codergod1337.app1.solr.share.model.ShareToken;
import codergod1337.app1.solr.share.model.ShareTokenKind;
import codergod1337.app1.solr.share.model.UserGrant;
import codergod1337.app1.solr.share.service.DmsShareService;
import codergod1337.app1.solr.share.service.ShareTokenService;
import codergod1337.app1.system.HelperInputs;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Abholscheine des Kerns DMS: angemeldet unter /api/rest/v1/solr/dms/share anlegen, auflisten und zurückziehen (die
 * Recht ADMIN oder DMS_PUBLIC prüft der Service), und die Station „Detailansicht“ unter /api/rest/v1/public/**, ohne
 * Anmeldung: Der Empfänger ruft den Link, bekommt die freigegebenen Felder, die Einlösung landet in der History. Die
 * Station „nur der Download“ liegt im BinaryDownloadController.
 */
@RestController
public class DmsShareController {

	private final DmsShareService dmsShareService;
	private final ShareTokenService shareTokenService;

	public DmsShareController(DmsShareService dmsShareService, ShareTokenService shareTokenService) {
		this.dmsShareService = dmsShareService;
		this.shareTokenService = shareTokenService;
	}

	/** Station Detailansicht, ohne Anmeldung: nur ein Schein der Art DOCUMENT, sonst 404. Der Aufruf zählt als Einlösung. */
	@GetMapping("/api/rest/v1/public/solr/dms/document/token/{token}")
	public DmsSharedDocument sharedDocumentByToken(@PathVariable String token, HttpServletRequest request) {
		ShareToken shareToken = shareTokenService.findRedeemableShareToken(token);
		if (shareToken == null || shareToken.getKind() != ShareTokenKind.DOCUMENT) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "kein Abholschein");
		}
		Map<String, Object> fieldValues = dmsShareService.getByToken(shareToken);
		if (fieldValues == null) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "kein Abholschein");
		}
		// die letzte erlaubte Einlösung nimmt den Hash vom Dokument
		if (shareTokenService.redeemShareToken(shareToken, "VIEW", request.getRemoteAddr())) {
			dmsShareService.removePickupToken(shareToken.getDocumentId(), shareToken.getTokenHash());
		}
		return new DmsSharedDocument(shareToken.getDocumentId(), shareToken.getKind(), shareToken.isBinaryIncluded(),
				shareToken.getExpiresAt(), shareToken.getMaxUses() - shareToken.getUses(), fieldValues);
	}

	/** Alle Scheine des Kerns DMS, jüngste zuerst, auch abgelaufene: die Übersicht „Freigaben“ */
	@GetMapping("/api/rest/v1/solr/dms/share")
	public List<ShareToken> getAllShareTokens() {
		return dmsShareService.getAllShareTokens();
	}

	/** Alle Scheine eines Dokuments, jüngste zuerst, auch abgelaufene: die History */
	@GetMapping("/api/rest/v1/solr/dms/share/{id}")
	public List<ShareToken> getShareTokens(@PathVariable String id) {
		requireId(id);
		return dmsShareService.getShareTokens(id);
	}

	/**
	 * Neuer Schein: {"id": "…", "kind": "DOCUMENT", "includedFieldNames": ["fileName", "fileDescription"],
	 * "binaryIncluded": true, "recipient": "Max", "maxUses": 3, "expiresAt": "2026-11-01T00:00:00Z"}. Zurück kommt die
	 * Zeile mit dem Klartext des Tokens, den es nur dieses eine Mal gibt.
	 */
	@PostMapping("/api/rest/v1/solr/dms/share")
	@ResponseStatus(HttpStatus.CREATED)
	public CreatedShareToken createShareToken(@RequestBody Map<String, Object> newShareTokenData) {
		String id = newShareTokenData.get("id") instanceof String idValue ? idValue : null;
		requireId(id);
		ShareTokenKind kind = readKind(newShareTokenData);
		boolean binaryIncluded = Boolean.TRUE.equals(newShareTokenData.get("binaryIncluded"));
		String recipient = newShareTokenData.get("recipient") instanceof String recipientValue ? recipientValue : null;
		if (!(newShareTokenData.get("maxUses") instanceof Number maxUses)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "maxUses fehlt oder ist keine Zahl");
		}
		return dmsShareService.createShareToken(id, kind, readIncludedFieldNames(newShareTokenData), binaryIncluded,
				recipient, maxUses.intValue(), readExpiresAt(newShareTokenData));
	}

	/** Zurückziehen: {"shareTokenId": 7}. Die Zeile wird EXPIRED und bleibt, der Hash geht vom Dokument. */
	@DeleteMapping("/api/rest/v1/solr/dms/share")
	public ShareToken expireShareToken(@RequestBody Map<String, Object> shareTokenToExpire) {
		if (!(shareTokenToExpire.get("shareTokenId") instanceof Number shareTokenId)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "shareTokenId fehlt oder ist keine Zahl");
		}
		return dmsShareService.expireShareToken(shareTokenId.longValue());
	}

	// ===== Freigabe an einen User: seine guid in userGrantedAccess (User 2026-10-10) =====

	/** Alle User-Freigaben des Kerns DMS, jüngste zuerst, auch zurückgenommene: die Übersicht „Freigaben“ */
	@GetMapping("/api/rest/v1/solr/dms/share/user")
	public List<UserGrant> getAllUserGrants() {
		return dmsShareService.getAllUserGrants();
	}

	/** Die User-Freigaben eines Dokuments: wer wann wem, jüngste zuerst, auch zurückgenommene */
	@GetMapping("/api/rest/v1/solr/dms/share/user/{id}")
	public List<UserGrant> getUserGrants(@PathVariable String id) {
		requireId(id);
		return dmsShareService.getUserGrants(id);
	}

	/** Freigeben: {"id": "…", "usersGuid": "…"}. Der User muss existieren. Zurück kommt die Zeile des Registers. */
	@PostMapping("/api/rest/v1/solr/dms/share/user")
	@ResponseStatus(HttpStatus.CREATED)
	public UserGrant grantToUser(@RequestBody Map<String, Object> grant) {
		String id = grant.get("id") instanceof String idValue ? idValue : null;
		requireId(id);
		return dmsShareService.grantToUser(id, grant.get("usersGuid") instanceof String guid ? guid : null);
	}

	/** Endgültig löschen, nur ADMIN: {"userGrantId": 7}. War sie aktiv, geht die guid aus dem Dokument. */
	@DeleteMapping("/api/rest/v1/admin/solr/dms/share/user")
	public void deleteUserGrant(@RequestBody Map<String, Object> userGrantToDelete) {
		if (!(userGrantToDelete.get("userGrantId") instanceof Number userGrantId)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "userGrantId fehlt oder ist keine Zahl");
		}
		dmsShareService.deleteUserGrant(userGrantId.longValue());
	}

	/** Zurücknehmen: {"id": "…", "usersGuid": "…"} */
	@DeleteMapping("/api/rest/v1/solr/dms/share/user")
	public void revokeFromUser(@RequestBody Map<String, Object> grant) {
		String id = grant.get("id") instanceof String idValue ? idValue : null;
		requireId(id);
		dmsShareService.revokeFromUser(id, grant.get("usersGuid") instanceof String guid ? guid : null);
	}

	/** Endgültig löschen, nur ADMIN (der Pfad /admin/** verlangt die Rolle): {"shareTokenId": 7}. Zeile und History sind weg. */
	@DeleteMapping("/api/rest/v1/admin/solr/dms/share")
	public void deleteShareToken(@RequestBody Map<String, Object> shareTokenToDelete) {
		if (!(shareTokenToDelete.get("shareTokenId") instanceof Number shareTokenId)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "shareTokenId fehlt oder ist keine Zahl");
		}
		dmsShareService.deleteShareToken(shareTokenId.longValue());
	}

	private static void requireId(String id) {
		if (id == null || !HelperInputs.isValidSha256Hex(id)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "id ist kein SHA-256 in Hex (64 Zeichen, klein)");
		}
	}

	private static ShareTokenKind readKind(Map<String, Object> body) {
		if (!(body.get("kind") instanceof String kind)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "kind fehlt");
		}
		try {
			return ShareTokenKind.valueOf(kind);
		} catch (IllegalArgumentException e) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "kind " + kind + " ist unbekannt");
		}
	}

	/** includedFieldNames: eine Liste von Feldnamen; fehlt sie, ist sie leer */
	private static List<String> readIncludedFieldNames(Map<String, Object> body) {
		Object raw = body.get("includedFieldNames");
		if (raw == null) {
			return List.of();
		}
		if (!(raw instanceof List<?> list)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "includedFieldNames ist keine Liste");
		}
		List<String> fieldNames = new ArrayList<>();
		for (Object entry : list) {
			if (!(entry instanceof String fieldName)) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
						"includedFieldNames enthält einen Eintrag, der kein Text ist");
			}
			fieldNames.add(fieldName);
		}
		return fieldNames;
	}

	/** expiresAt: ein Zeitpunkt in ISO 8601 mit Zone, z. B. 2026-11-01T00:00:00Z */
	private static Instant readExpiresAt(Map<String, Object> body) {
		if (!(body.get("expiresAt") instanceof String expiresAt)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "expiresAt fehlt");
		}
		try {
			return Instant.parse(expiresAt);
		} catch (DateTimeParseException e) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "expiresAt ist kein Zeitpunkt wie 2026-11-01T00:00:00Z");
		}
	}

}
