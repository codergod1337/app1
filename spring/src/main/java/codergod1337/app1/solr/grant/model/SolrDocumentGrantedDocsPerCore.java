package codergod1337.app1.solr.grant.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.ColumnDefault;

/**
 * Die Dateifreigabe: das Register einer Freigabe von Dokumenten eines Kerns an einen User. Der Freigebende nennt
 * Feld-Wert-Paare wie beim Auflösen, der Service löst mit der Grenze des Freigebenden auf und schreibt die guid des
 * Empfängers per Atomic Update in {@code userGrantedAccess} jedes Treffers. Die Wahrheit für den Zugriff ist das
 * Dokument, diese Zeile ist das Protokoll und die Grundlage für Entzug und Ablauf: Beide lösen dieselben Paare noch
 * einmal auf und nehmen die guid wieder heraus.
 *
 * Schnappschuss: Ein später angelegtes Dokument mit denselben Werten trägt die guid nicht.
 *
 * Nichts wird gelöscht, eine Freigabe wechselt nur den Status (EntityState im Bereich GRANT: ACTIVE, REVOKED, EXPIRED).
 * Keine JPA-Beziehungen, User und Kern stehen als guid und key.
 */
@Entity
@Table(name = "solr_document_granted_docs_per_core")
public class SolrDocumentGrantedDocsPerCore {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	/** Wer die Freigabe bekommt */
	@NotNull
	@Column(nullable = false, updatable = false)
	private UUID granteeUsersGuid;

	/** Der Kern, dessen Dokumente freigegeben sind (SolrCore.key). Ändert sich nie. */
	@NotBlank
	@Size(max = 200)
	@Column(nullable = false, updatable = false, length = 200)
	private String coreKey;

	/**
	 * Die Feld-Wert-Paare der Auflösung als JSON-Objekt, z. B. {"artikelNummer":["4711"]}: jedes Feld eine Liste von
	 * Werten, mehrere Werte ODER, mehrere Felder UND. Entzug und Ablauf lösen damit dieselbe Menge noch einmal auf.
	 */
	@NotBlank
	@Column(nullable = false, updatable = false, columnDefinition = "text")
	private String fieldValues;

	/** Wer freigegeben hat */
	@NotNull
	@Column(nullable = false, updatable = false)
	private UUID grantedByUsersGuid;

	@NotNull
	@Column(nullable = false, updatable = false)
	private Instant grantedAt;

	/** null heißt unbegrenzt. Der Systemjob setzt abgelaufene Freigaben auf EXPIRED und nimmt die guid aus den Dokumenten. */
	private Instant validUntil;

	/** key des EntityState im Bereich GRANT: ACTIVE, REVOKED oder EXPIRED */
	@NotBlank
	@Size(max = 200)
	@Column(nullable = false, length = 200)
	private String status;

	/** Beim Anlegen gleich grantedAt, danach der letzte Statuswechsel */
	@NotNull
	@Column(nullable = false)
	private Instant statusChangedAt;

	/** Beim Anlegen der Freigebende, beim Entzug wer entzogen hat, beim Ablauf der System-User */
	@NotNull
	@Column(nullable = false)
	private UUID statusChangedByUsersGuid;

	/** Wie viele Dokumente beim Freigeben die guid bekamen, für die Liste in der Maske */
	@Column(nullable = false)
	@ColumnDefault("0")
	private int documentCount;

	/** Für JPA und Jackson. */
	protected SolrDocumentGrantedDocsPerCore() {
	}

	/** Neue Freigabe: aktiv, Statuswechsel gleich dem Anlegen. */
	public SolrDocumentGrantedDocsPerCore(UUID granteeUsersGuid, String coreKey, String fieldValues,
			UUID grantedByUsersGuid, Instant grantedAt, Instant validUntil, String status, int documentCount) {
		this.granteeUsersGuid = granteeUsersGuid;
		this.coreKey = coreKey;
		this.fieldValues = fieldValues;
		this.grantedByUsersGuid = grantedByUsersGuid;
		this.grantedAt = grantedAt;
		this.validUntil = validUntil;
		this.status = status;
		this.statusChangedAt = grantedAt;
		this.statusChangedByUsersGuid = grantedByUsersGuid;
		this.documentCount = documentCount;
	}

	public Long getId() {
		return id;
	}

	public UUID getGranteeUsersGuid() {
		return granteeUsersGuid;
	}

	public String getCoreKey() {
		return coreKey;
	}

	public String getFieldValues() {
		return fieldValues;
	}

	public UUID getGrantedByUsersGuid() {
		return grantedByUsersGuid;
	}

	public Instant getGrantedAt() {
		return grantedAt;
	}

	public Instant getValidUntil() {
		return validUntil;
	}

	public void setValidUntil(Instant validUntil) {
		this.validUntil = validUntil;
	}

	public String getStatus() {
		return status;
	}

	/** Statuswechsel immer mit wann und wer, nie der Status allein */
	public void changeStatus(String status, Instant statusChangedAt, UUID statusChangedByUsersGuid) {
		this.status = status;
		this.statusChangedAt = statusChangedAt;
		this.statusChangedByUsersGuid = statusChangedByUsersGuid;
	}

	public Instant getStatusChangedAt() {
		return statusChangedAt;
	}

	public UUID getStatusChangedByUsersGuid() {
		return statusChangedByUsersGuid;
	}

	public int getDocumentCount() {
		return documentCount;
	}

	public void setDocumentCount(int documentCount) {
		this.documentCount = documentCount;
	}

}
