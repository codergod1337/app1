package codergod1337.app1.dms.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

/**
 * Eine Revision eines DMS-Dokuments: die Registerzeile zum ZIP auf dem SFTP (gebaut 2026-10-08 nach der dms_history
 * der Vorlage). Das ZIP ist die Wahrheit, die Zeile nur das Register: Hier steht, was die Liste braucht (Nummer,
 * Zeitpunkt, wer, welche Felder sich wirklich geändert haben) und der Pfad zum ZIP. Bewusst keine Kopie von
 * Dateiname, Status oder ACLs, eine zweite Wahrheit liefe auseinander; auch kein {@code _version_}, Solrs Zähler sagt
 * hier nichts (User 2026-10-08).
 *
 * Long-PK, dieselbe Garantie liefert der UNIQUE auf (document_id, revision_no); er legt nebenbei den Index an, über
 * den die Liste liest. Keine JPA-Beziehungen, der User steht als guid.
 */
@Entity
@Table(name = "dms_solr_document_revision",
		uniqueConstraints = @UniqueConstraint(columnNames = { "document_id", "revision_no" }))
public class DmsSolrDocumentRevision {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	/** Die id des Dokuments, der SHA-256 seiner Bytes */
	@NotBlank
	@Size(max = 64)
	@Column(nullable = false, updatable = false, length = 64)
	private String documentId;

	/** Die Nummer, die der gesicherte Stand trug, also vor dem Hochzählen. 0 heißt immer: so wurde es hochgeladen. */
	@Column(nullable = false, updatable = false)
	private long revisionNo;

	/** Pfad zum ZIP, relativ zur SFTP-Wurzel (dmsBackup/…) */
	@NotBlank
	@Size(max = 300)
	@Column(nullable = false, length = 300)
	private String backupPath;

	/** Wer die Änderung ausgelöst hat, die diesen Stand abgelöst hat */
	@NotNull
	@Column(nullable = false)
	private UUID changedByUsersGuid;

	/** Wann */
	@NotNull
	@Column(nullable = false)
	private Instant changedAt;

	/**
	 * Die Felder, deren Wert die Änderung wirklich geändert hat, mit Komma getrennt; für die Liste, ohne das ZIP zu
	 * öffnen. Ohne den Stempel changingUsers und lastChangedAt und ohne fileIdRevisionNo.
	 */
	@Column(columnDefinition = "text")
	private String changedFieldNames;

	/** Für JPA und Jackson. */
	protected DmsSolrDocumentRevision() {
	}

	public DmsSolrDocumentRevision(String documentId, long revisionNo) {
		this.documentId = documentId;
		this.revisionNo = revisionNo;
	}

	public Long getId() {
		return id;
	}

	public String getDocumentId() {
		return documentId;
	}

	public long getRevisionNo() {
		return revisionNo;
	}

	public String getBackupPath() {
		return backupPath;
	}

	public void setBackupPath(String backupPath) {
		this.backupPath = backupPath;
	}

	public UUID getChangedByUsersGuid() {
		return changedByUsersGuid;
	}

	public void setChangedByUsersGuid(UUID changedByUsersGuid) {
		this.changedByUsersGuid = changedByUsersGuid;
	}

	public Instant getChangedAt() {
		return changedAt;
	}

	public void setChangedAt(Instant changedAt) {
		this.changedAt = changedAt;
	}

	public String getChangedFieldNames() {
		return changedFieldNames;
	}

	public void setChangedFieldNames(String changedFieldNames) {
		this.changedFieldNames = changedFieldNames;
	}

}
