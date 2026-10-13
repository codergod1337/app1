package codergod1337.app1.solr.share.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Die Freigabe eines Dokuments an einen User mit Konto (User 2026-10-10): Wer hat wann wem welches Dokument freigegeben.
 * Der Zugriff selbst steht im Solr-Dokument (die guid in userGrantedAccess), diese Zeile ist das Gedächtnis dazu:
 * Freigeben legt sie an, Zurücknehmen setzt sie REVOKED und lässt sie stehen. Kernübergreifend wie der Abholschein.
 */
@Entity
@Table(name = "user_grant")
public class UserGrant {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "core_key", nullable = false, length = 40)
	private String coreKey;

	@Column(name = "document_id", nullable = false, length = 200)
	private String documentId;

	/** an wen: {@code Users.guid} des Empfängers, dieselbe guid wie in userGrantedAccess */
	@Column(name = "grantee_users_guid", nullable = false)
	private UUID granteeUsersGuid;

	/** von wem: {@code Users.guid} des Angemeldeten beim Freigeben */
	@Column(name = "granted_by_users_guid", nullable = false)
	private UUID grantedByUsersGuid;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private UserGrantStatus status;

	@Column(name = "revoked_at")
	private Instant revokedAt;

	@Column(name = "revoked_by_users_guid")
	private UUID revokedByUsersGuid;

	/** Für JPA und Jackson. */
	protected UserGrant() {
	}

	public UserGrant(String coreKey, String documentId, UUID granteeUsersGuid, UUID grantedByUsersGuid) {
		this.coreKey = coreKey;
		this.documentId = documentId;
		this.granteeUsersGuid = granteeUsersGuid;
		this.grantedByUsersGuid = grantedByUsersGuid;
		this.createdAt = Instant.now();
		this.status = UserGrantStatus.ACTIVE;
	}

	/** Zurücknehmen: REVOKED mit Zeitpunkt und wer, die Zeile bleibt */
	public void revoke(UUID revokedByUsersGuid) {
		this.status = UserGrantStatus.REVOKED;
		this.revokedAt = Instant.now();
		this.revokedByUsersGuid = revokedByUsersGuid;
	}

	public Long getId() {
		return id;
	}

	public String getCoreKey() {
		return coreKey;
	}

	public String getDocumentId() {
		return documentId;
	}

	public UUID getGranteeUsersGuid() {
		return granteeUsersGuid;
	}

	public UUID getGrantedByUsersGuid() {
		return grantedByUsersGuid;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public UserGrantStatus getStatus() {
		return status;
	}

	public Instant getRevokedAt() {
		return revokedAt;
	}

	public UUID getRevokedByUsersGuid() {
		return revokedByUsersGuid;
	}

}
