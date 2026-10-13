package codergod1337.app1.solr.share.service;

import codergod1337.app1.solr.share.model.UserGrant;
import codergod1337.app1.solr.share.model.UserGrantStatus;
import codergod1337.app1.solr.share.repository.UserGrantRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Das Gedächtnis der User-Freigaben, kernübergreifend: wer wann wem welches Dokument freigegeben hat, und wann wer es
 * zurückgenommen hat. Kennt keinen Kern und keine Rechte und schreibt nicht in Solr: Das macht der Service des Kerns
 * (im DMS der DmsShareService), der die guid ins Dokument setzt.
 */
@Service
public class UserGrantService {

	private final UserGrantRepository userGrantRepository;

	public UserGrantService(UserGrantRepository userGrantRepository) {
		this.userGrantRepository = userGrantRepository;
	}

	/** Eine neue Freigabe, ACTIVE. Gibt es schon eine aktive für denselben Empfänger, bleibt die und kommt zurück. */
	@Transactional
	public UserGrant createUserGrant(String coreKey, String documentId, UUID granteeUsersGuid, UUID grantedByUsersGuid) {
		List<UserGrant> active = userGrantRepository.findByCoreKeyAndDocumentIdAndGranteeUsersGuidAndStatus(coreKey,
				documentId, granteeUsersGuid, UserGrantStatus.ACTIVE);
		if (!active.isEmpty()) {
			return active.get(0);
		}
		return userGrantRepository.save(new UserGrant(coreKey, documentId, granteeUsersGuid, grantedByUsersGuid));
	}

	/** Alle Freigaben eines Dokuments, jüngste zuerst, auch zurückgenommene: die History */
	public List<UserGrant> getUserGrantsByResource(String coreKey, String documentId) {
		return userGrantRepository.findByCoreKeyAndDocumentIdOrderByCreatedAtDesc(coreKey, documentId);
	}

	/** Alle Freigaben eines Kerns, jüngste zuerst: die Übersicht „Freigaben“ */
	public List<UserGrant> getUserGrantsByCore(String coreKey) {
		return userGrantRepository.findByCoreKeyOrderByCreatedAtDesc(coreKey);
	}

	public UserGrant getUserGrantById(long id) {
		return userGrantRepository.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "keine Freigabe mit dieser id"));
	}

	/** Zurücknehmen: jede aktive Freigabe dieses Empfängers am Dokument wird REVOKED, die Zeilen bleiben */
	@Transactional
	public List<UserGrant> revokeUserGrants(String coreKey, String documentId, UUID granteeUsersGuid,
			UUID revokedByUsersGuid) {
		List<UserGrant> active = userGrantRepository.findByCoreKeyAndDocumentIdAndGranteeUsersGuidAndStatus(coreKey,
				documentId, granteeUsersGuid, UserGrantStatus.ACTIVE);
		for (UserGrant userGrant : active) {
			userGrant.revoke(revokedByUsersGuid);
		}
		return userGrantRepository.saveAll(active);
	}

	/** Endgültig löschen samt History: nur der Admin in der Übersicht. Der Aufrufer nimmt die guid aus dem Dokument. */
	@Transactional
	public void deleteUserGrant(UserGrant userGrant) {
		userGrantRepository.delete(userGrant);
	}

}
