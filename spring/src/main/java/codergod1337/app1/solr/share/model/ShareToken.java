package codergod1337.app1.solr.share.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Ein Abholschein (User 2026-10-09): die Freigabe einer Ressource, Kern plus Dokument-id, an jemanden ohne Konto über
 * einen Link. Die Tabelle ist das Gedächtnis des Scheins, Art, Felder, Ablauf, Limit, Stand und die History jeder
 * Einlösung; das Solr-Dokument trägt nur den Hash in downloadGrantedAccess zur Prüfung. Der Klartext steht allein im
 * Link, hier liegt sein SHA-256 (wie LoginToken). Kernübergreifend: Welcher Kern mit welcher Station einlöst, sagt
 * coreKey, heute nur DMS.
 */
@Entity
@Table(name = "share_token", uniqueConstraints = @UniqueConstraint(columnNames = "token_hash"))
public class ShareToken {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	/** Die Ressource: der Kern und die id des Dokuments darin */
	@Column(name = "core_key", nullable = false, length = 40)
	private String coreKey;

	@Column(name = "document_id", nullable = false, length = 200)
	private String documentId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private ShareTokenKind kind;

	/** Die Felder, die der Empfänger sehen darf; leer bei BINARY */
	@JdbcTypeCode(SqlTypes.ARRAY)
	@Column(name = "included_field_names", columnDefinition = "text[]")
	private List<String> includedFieldNames;

	/** Darf die Datei geladen werden: bei BINARY immer, bei DOCUMENT wählbar */
	@Column(name = "binary_included", nullable = false)
	private boolean binaryIncluded;

	/** von wem: {@code Users.guid} des Angemeldeten beim Anlegen */
	@Column(name = "granted_by_users_guid", nullable = false)
	private UUID grantedByUsersGuid;

	/** an wen: freier Text wie Name oder Mail, der Empfänger hat kein Konto */
	@Column(columnDefinition = "text")
	private String recipient;

	/** SHA-256 des Klartexts in Hex; nie zum Client, der Klartext ist der Link */
	@JsonIgnore
	@Column(name = "token_hash", nullable = false, length = 64)
	private String tokenHash;

	/** Wie oft der Schein eingelöst werden darf, Ansicht und Download zählen beide */
	@Column(name = "max_uses", nullable = false)
	private int maxUses;

	@Column(nullable = false)
	private int uses;

	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private ShareTokenStatus status;

	/** Die History: je Einlösung ein Eintrag "Zeitpunkt IP Art", z. B. "2026-10-09T13:45:12Z 192.168.1.17 DOWNLOAD" */
	@JdbcTypeCode(SqlTypes.ARRAY)
	@Column(columnDefinition = "text[]")
	private List<String> redemptions;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	/** Für JPA und Jackson. */
	protected ShareToken() {
	}

	/** Neuer Schein: ACTIVE, keine Einlösung, die id vergibt die Datenbank. */
	public ShareToken(String coreKey, String documentId, ShareTokenKind kind, List<String> includedFieldNames,
			boolean binaryIncluded, UUID grantedByUsersGuid, String recipient, String tokenHash, int maxUses,
			Instant expiresAt) {
		this.coreKey = coreKey;
		this.documentId = documentId;
		this.kind = kind;
		this.includedFieldNames = includedFieldNames != null ? List.copyOf(includedFieldNames) : List.of();
		this.binaryIncluded = binaryIncluded;
		this.grantedByUsersGuid = grantedByUsersGuid;
		this.recipient = recipient;
		this.tokenHash = tokenHash;
		this.maxUses = maxUses;
		this.uses = 0;
		this.expiresAt = expiresAt;
		this.status = ShareTokenStatus.ACTIVE;
		this.redemptions = List.of();
		this.createdAt = Instant.now();
	}

	/** Eine Einlösung festhalten: der Eintrag kommt in die History, uses zählt hoch */
	public void addRedemption(String entry) {
		List<String> next = new ArrayList<>(getRedemptions());
		next.add(entry);
		this.redemptions = next;
		this.uses++;
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

	public ShareTokenKind getKind() {
		return kind;
	}

	public List<String> getIncludedFieldNames() {
		return includedFieldNames != null ? includedFieldNames : List.of();
	}

	public boolean isBinaryIncluded() {
		return binaryIncluded;
	}

	public UUID getGrantedByUsersGuid() {
		return grantedByUsersGuid;
	}

	public String getRecipient() {
		return recipient;
	}

	public String getTokenHash() {
		return tokenHash;
	}

	public int getMaxUses() {
		return maxUses;
	}

	public int getUses() {
		return uses;
	}

	public Instant getExpiresAt() {
		return expiresAt;
	}

	public ShareTokenStatus getStatus() {
		return status;
	}

	public void setStatus(ShareTokenStatus status) {
		this.status = status;
	}

	public List<String> getRedemptions() {
		return redemptions != null ? redemptions : List.of();
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

}
