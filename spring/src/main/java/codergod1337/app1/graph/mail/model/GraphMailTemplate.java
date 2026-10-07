package codergod1337.app1.graph.mail.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Eine Mailvorlage, gepflegt im GraphManager. Betreff und Text sind mehrsprachig und tragen Platzhalter der Form
 * {gruppe.name}, z. B. {users.vorname} oder {login.confirmLink}. Welche Gruppen erlaubt sind, sagt der Typ. Je Typ
 * gibt es genau eine Vorlage; beim Start legt das Backend fehlende Typen mit Standardtexten an, was der Admin danach
 * ändert, bleibt.
 *
 * Das Format des Keys prüft {@code HelperInputs.isValidKey}, die Platzhalter prüft der GraphMailTemplateService.
 */
@Entity
@Table(name = "graph_mail_template")
public class GraphMailTemplate {

	/** Normale Key-Regel, z. B. LOGIN_2FA. Von Hand vergeben, ändert sich nie. */
	@Id
	@NotBlank
	@Size(max = 200)
	@Column(nullable = false, updatable = false, length = 200)
	private String key;

	/** Wofür die Vorlage da ist, eindeutig: je Typ genau eine Vorlage. Wird beim Anlegen gesetzt. */
	@NotNull
	@Enumerated(EnumType.STRING)
	@Column(nullable = false, unique = true, length = 50)
	private GraphMailTemplateType type;

	/** Mehrsprachiges JSON, z. B. {"de":"Anmeldung bestätigen","en":"Confirm login"}. Pflicht. */
	@NotBlank
	@Column(nullable = false, columnDefinition = "text")
	private String displayName;

	/** Mehrsprachiges JSON, Hinweise für den Admin, z. B. wann die Mail geht. Pflicht. */
	@NotBlank
	@Column(nullable = false, columnDefinition = "text")
	private String description;

	/** Mehrsprachiges JSON, der Betreff je Sprache, reiner Text mit Platzhaltern. Pflicht. */
	@NotBlank
	@Column(nullable = false, columnDefinition = "text")
	private String subject;

	/** Mehrsprachiges JSON, der Text je Sprache als HTML mit Platzhaltern. Pflicht. */
	@NotBlank
	@Column(nullable = false, columnDefinition = "text")
	private String htmlBody;

	/** Reihenfolge in der Liste, nicht eindeutig. Sortiert wird nach Position und dann nach key. */
	@Column(nullable = false)
	@ColumnDefault("0")
	private int listingPosition;

	@CreationTimestamp
	@Column(nullable = false, updatable = false)
	private OffsetDateTime createdAt;

	@UpdateTimestamp
	@Column(nullable = false)
	private OffsetDateTime updatedAt;

	/** Für JPA und Jackson. */
	protected GraphMailTemplate() {
	}

	/** Admin, Start und Import: jedes Feld. */
	public GraphMailTemplate(String key, GraphMailTemplateType type, String displayName, String description,
			String subject, String htmlBody, int listingPosition) {
		this.key = key;
		this.type = type;
		this.displayName = displayName;
		this.description = description;
		this.subject = subject;
		this.htmlBody = htmlBody;
		this.listingPosition = listingPosition;
	}

	public String getKey() {
		return key;
	}

	public GraphMailTemplateType getType() {
		return type;
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

	public String getSubject() {
		return subject;
	}

	public void setSubject(String subject) {
		this.subject = subject;
	}

	public String getHtmlBody() {
		return htmlBody;
	}

	public void setHtmlBody(String htmlBody) {
		this.htmlBody = htmlBody;
	}

	public int getListingPosition() {
		return listingPosition;
	}

	public void setListingPosition(int listingPosition) {
		this.listingPosition = listingPosition;
	}

	public OffsetDateTime getCreatedAt() {
		return createdAt;
	}

	public OffsetDateTime getUpdatedAt() {
		return updatedAt;
	}

}
