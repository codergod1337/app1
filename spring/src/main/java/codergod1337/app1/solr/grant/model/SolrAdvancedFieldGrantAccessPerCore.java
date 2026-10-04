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

/**
 * Die Feldfreigabe: ein erweitertes Feld (SolrFieldAccess.ADVANCED) eines Kerns, das ein User ohne die Rolle
 * {@code <KERN>_FULL} trotzdem lesen darf. Pro Kern, nie pro Dokument, und nichts davon in Solr: Die Lesedienste nehmen
 * aktive Zeilen zu den Minimum-Feldern dazu, wenn sie bauen, welche Felder sie anfordern (fl). Etwas anderes als die
 * Dateifreigabe (SolrDocumentGrantedDocsPerCore), für den Benutzer der App aber dasselbe Modul „Freigaben“.
 *
 * Eindeutig ist das Tripel Empfänger, Kern, Feld nur unter den aktiven Zeilen, das prüft der Service: Eine widerrufene
 * Zeile bleibt als Protokoll stehen, danach darf dasselbe Feld neu freigegeben werden.
 *
 * Nichts wird gelöscht, eine Freigabe wechselt nur den Status (EntityState im Bereich GRANT: ACTIVE, REVOKED, EXPIRED).
 * Keine JPA-Beziehungen, User und Kern stehen als guid und key.
 */
@Entity
@Table(name = "solr_advanced_field_grant_access_per_core")
public class SolrAdvancedFieldGrantAccessPerCore {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	/** Wer die Freigabe bekommt */
	@NotNull
	@Column(nullable = false, updatable = false)
	private UUID granteeUsersGuid;

	/** Der Kern, in dem das Feld freigegeben ist (SolrCore.key). Ändert sich nie. */
	@NotBlank
	@Size(max = 200)
	@Column(nullable = false, updatable = false, length = 200)
	private String coreKey;

	/** Der Name des Feldes in Solr (SolrField.name) oder ein Hook-Key. Ändert sich nie. */
	@NotBlank
	@Size(max = 100)
	@Column(nullable = false, updatable = false, length = 100)
	private String fieldName;

	/** Wer freigegeben hat */
	@NotNull
	@Column(nullable = false, updatable = false)
	private UUID grantedByUsersGuid;

	@NotNull
	@Column(nullable = false, updatable = false)
	private Instant grantedAt;

	/** null heißt unbegrenzt. Der Systemjob setzt abgelaufene Freigaben auf EXPIRED. */
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

	/** Für JPA und Jackson. */
	protected SolrAdvancedFieldGrantAccessPerCore() {
	}

	/** Neue Freigabe: aktiv, Statuswechsel gleich dem Anlegen. */
	public SolrAdvancedFieldGrantAccessPerCore(UUID granteeUsersGuid, String coreKey, String fieldName,
			UUID grantedByUsersGuid, Instant grantedAt, Instant validUntil, String status) {
		this.granteeUsersGuid = granteeUsersGuid;
		this.coreKey = coreKey;
		this.fieldName = fieldName;
		this.grantedByUsersGuid = grantedByUsersGuid;
		this.grantedAt = grantedAt;
		this.validUntil = validUntil;
		this.status = status;
		this.statusChangedAt = grantedAt;
		this.statusChangedByUsersGuid = grantedByUsersGuid;
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

	public String getFieldName() {
		return fieldName;
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

}
