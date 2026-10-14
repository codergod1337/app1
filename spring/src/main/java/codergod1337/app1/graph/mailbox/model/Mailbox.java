package codergod1337.app1.graph.mailbox.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Ein Postfach, aus dem archiviert wird (User 2026-10-13, nach der Mailbox der Vorlage). Von Hand gepflegt und nicht
 * aus Graph geholt: Die Anwendungsberechtigung Mail.Read erlaubt das Lesen in jedem Postfach, aber nicht, die Postfächer
 * zu erfahren, dafür bräuchte es User.Read.All.
 *
 * Am Postfach hängt, wer aus ihm archivieren darf: die ARC-Positionen. Mit den Leserechten der archivierten Nachricht
 * hat es nichts zu tun, die kommen aus der Nachrichtenart. Archivieren verlangt dazu eine eigene Rolle; beides prüft
 * MailboxService.requireArchiveAccessToMailbox. An welcher Nachrichtenart ein Postfach hängt, steht lose an der
 * Nachrichtenart (MessageSubClass.mailboxEmailAddresses), nicht hier.
 */
@Entity
@Table(name = "mailbox")
public class Mailbox {

	/** Die E-Mail-Adresse des Postfachs, klein geschrieben, mit genau einem @. Sie ist der Schlüssel und ändert sich nie. */
	@Id
	@NotBlank
	@Size(max = 320)
	@Column(nullable = false, updatable = false, length = 320)
	private String emailAddress;

	/** Klartext für die Auswahl, z. B. „Bestellungen“, nicht mehrsprachig: Es ist ein Eigenname. Pflicht. */
	@NotBlank
	@Size(max = 200)
	@Column(nullable = false, length = 200)
	private String displayName;

	/** Wozu das Postfach dient, Klartext, optional. */
	@Column(columnDefinition = "text")
	private String description;

	/** Reihenfolge in Listen, nicht eindeutig. Sortiert wird nach Position und dann nach Adresse. */
	@Column(nullable = false)
	@ColumnDefault("0")
	private int listingPosition;

	/** Die ARC-Positionen, die aus diesem Postfach archivieren dürfen: ARC-Keys. */
	@JdbcTypeCode(SqlTypes.ARRAY)
	@Column(columnDefinition = "text[]")
	private List<String> accessRoleCollectionKeys;

	/** Für JPA und Jackson. */
	protected Mailbox() {
	}

	/** Admin und Import: jedes Feld. */
	public Mailbox(String emailAddress, String displayName, String description, int listingPosition,
			List<String> accessRoleCollectionKeys) {
		this.emailAddress = emailAddress;
		this.displayName = displayName;
		this.description = description;
		this.listingPosition = listingPosition;
		this.accessRoleCollectionKeys = accessRoleCollectionKeys;
	}

	public String getEmailAddress() {
		return emailAddress;
	}

	public String getDisplayName() {
		return displayName;
	}

	public void setDisplayName(String displayName) {
		this.displayName = displayName;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public int getListingPosition() {
		return listingPosition;
	}

	public void setListingPosition(int listingPosition) {
		this.listingPosition = listingPosition;
	}

	/** Nie null: ohne Wert darf keine Position aus dem Postfach archivieren, nur der Admin. */
	public List<String> getAccessRoleCollectionKeys() {
		return accessRoleCollectionKeys != null ? accessRoleCollectionKeys : List.of();
	}

	public void setAccessRoleCollectionKeys(List<String> accessRoleCollectionKeys) {
		this.accessRoleCollectionKeys = accessRoleCollectionKeys;
	}

}
