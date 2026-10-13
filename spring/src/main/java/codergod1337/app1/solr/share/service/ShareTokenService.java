package codergod1337.app1.solr.share.service;

import codergod1337.app1.solr.share.model.CreatedShareToken;
import codergod1337.app1.solr.share.model.ShareToken;
import codergod1337.app1.solr.share.model.ShareTokenKind;
import codergod1337.app1.solr.share.model.ShareTokenStatus;
import codergod1337.app1.solr.share.repository.ShareTokenRepository;
import codergod1337.app1.system.security.login.LoginToken;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Das Gedächtnis der Abholscheine, kernübergreifend: anlegen, auflisten, zurückziehen, einlösen. Kennt keinen Kern
 * und keine Rechte: Wer einen Schein anlegen darf und welche Felder er mitgeben darf, prüft der Service des Kerns
 * (im DMS der SolrDocumentDmsService), der auch den Hash ins Dokument schreibt. Welche Station einen Schein einlöst,
 * entscheidet dessen Controller über kind und coreKey.
 */
@Service
public class ShareTokenService {

	private final ShareTokenRepository shareTokenRepository;

	public ShareTokenService(ShareTokenRepository shareTokenRepository) {
		this.shareTokenRepository = shareTokenRepository;
	}

	/**
	 * Ein neuer Schein, ACTIVE. Der Klartext entsteht hier (32 Byte Zufall wie beim Login) und geht genau einmal
	 * zurück, gespeichert wird sein Hash. 400 bei einem Limit unter 1 oder einem Ablauf, der nicht in der Zukunft liegt.
	 */
	@Transactional
	public CreatedShareToken createShareToken(String coreKey, String documentId, ShareTokenKind kind,
			List<String> includedFieldNames, boolean binaryIncluded, UUID grantedByUsersGuid, String recipient,
			int maxUses, Instant expiresAt) {
		if (maxUses < 1) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "maxUses ist kleiner als 1");
		}
		if (expiresAt == null || !expiresAt.isAfter(Instant.now())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "expiresAt fehlt oder liegt nicht in der Zukunft");
		}
		String token = LoginToken.createLoginToken();
		ShareToken saved = shareTokenRepository.save(new ShareToken(coreKey, documentId, kind, includedFieldNames,
				binaryIncluded, grantedByUsersGuid, recipient, LoginToken.hashLoginToken(token), maxUses, expiresAt));
		return new CreatedShareToken(saved, token);
	}

	/** Alle Scheine einer Ressource, jüngste zuerst, auch die abgelaufenen: das ist die History */
	public List<ShareToken> getShareTokensByResource(String coreKey, String documentId) {
		return shareTokenRepository.findByCoreKeyAndDocumentIdOrderByCreatedAtDesc(coreKey, documentId);
	}

	/** Alle Scheine eines Kerns, jüngste zuerst, auch die abgelaufenen: die Übersicht „Freigaben“ */
	public List<ShareToken> getShareTokensByCore(String coreKey) {
		return shareTokenRepository.findByCoreKeyOrderByCreatedAtDesc(coreKey);
	}

	public ShareToken getShareTokenById(long id) {
		return shareTokenRepository.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "kein Abholschein mit dieser id"));
	}

	/** Von Hand zurückziehen: EXPIRED, die Zeile bleibt. Der Aufrufer nimmt den Hash vom Dokument. */
	@Transactional
	public ShareToken expireShareToken(ShareToken shareToken) {
		shareToken.setStatus(ShareTokenStatus.EXPIRED);
		return shareTokenRepository.save(shareToken);
	}

	/** Endgültig löschen, samt History: nur der Admin in der Übersicht (User 2026-10-10). Der Aufrufer nimmt den Hash vom Dokument. */
	@Transactional
	public void deleteShareToken(ShareToken shareToken) {
		shareTokenRepository.delete(shareToken);
	}

	/**
	 * Die Zeile zu einem Klartext aus einem Link, wenn sie noch einlösbar ist: ACTIVE, Ablauf nicht vorbei, Nutzungen
	 * unter dem Limit; sonst null, die Station macht daraus 404. Ein nach Datum abgelaufener Schein wird dabei EXPIRED.
	 */
	@Transactional
	public ShareToken findRedeemableShareToken(String token) {
		ShareToken shareToken = shareTokenRepository.findByTokenHash(LoginToken.hashLoginToken(token)).orElse(null);
		if (shareToken == null || shareToken.getStatus() != ShareTokenStatus.ACTIVE) {
			return null;
		}
		if (!shareToken.getExpiresAt().isAfter(Instant.now()) || shareToken.getUses() >= shareToken.getMaxUses()) {
			shareToken.setStatus(ShareTokenStatus.EXPIRED);
			shareTokenRepository.save(shareToken);
			return null;
		}
		return shareToken;
	}

	/**
	 * Eine Einlösung festhalten, nach der Ansicht oder dem vollständigen Download: Zeitpunkt, IP und Art in die
	 * History, uses hoch. Mit der letzten erlaubten wird der Schein EXPIRED; true heißt genau das, der Aufrufer nimmt
	 * dann den Hash vom Dokument.
	 */
	@Transactional
	public boolean redeemShareToken(ShareToken shareToken, String what, String ip) {
		shareToken.addRedemption(Instant.now() + " " + ip + " " + what);
		if (shareToken.getUses() >= shareToken.getMaxUses()) {
			shareToken.setStatus(ShareTokenStatus.EXPIRED);
		}
		shareTokenRepository.save(shareToken);
		return shareToken.getStatus() == ShareTokenStatus.EXPIRED;
	}

}
