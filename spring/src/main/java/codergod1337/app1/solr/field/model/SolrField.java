package codergod1337.app1.solr.field.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.ColumnDefault;

/**
 * Ein Feld eines Solr-Kerns: sein Name in Solr, sein Typ aus unserem Katalog, die Solr-Schalter und wozu es da ist.
 * Es gibt genau zwei Arten von Solr-Feldern: Hooks, die in jedem Kern gleich sind und ihre eigene Tabelle haben, und
 * diese normalen Felder je Kern. Hier steht jedes normale Feld, das der Kern haben wird, auch id: der Primärschlüssel
 * jedes Kerns, was hineinkommt, sagt seine Beschreibung (SolrCore.ID_FIELD_NAME).
 *
 * Eindeutig ist das Paar coreKey und name. Die id ist nur die Zeile in der Datenbank, wie bei den Zuordnungen, und
 * geht nicht in den Export.
 *
 * Das Format von coreKey und name prüft der Service, nicht diese Klasse.
 */
@Entity
@Table(name = "solr_field", uniqueConstraints = @UniqueConstraint(columnNames = { "core_key", "name" }))
public class SolrField {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	/** Der Kern, zu dem das Feld gehört (SolrCore.key). Ändert sich nie. */
	@NotBlank
	@Size(max = 200)
	@Column(nullable = false, updatable = false, length = 200)
	private String coreKey;

	/** Der Name des Feldes in Solr, z. B. fileName. Ändert sich nie: Die Dokumente tragen ihn. */
	@NotBlank
	@Size(max = 100)
	@Column(nullable = false, updatable = false, length = 100)
	private String name;

	/** Der Typ aus unserem Katalog. */
	@NotNull
	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private SolrFieldType type;

	// ===== die Solr-Schalter =====

	@Column(nullable = false)
	@ColumnDefault("true")
	private boolean indexed;

	/** Alle echten Felder sollen stored sein: Nur so lässt sich ein Kern aus sich selbst neu aufbauen. */
	@Column(nullable = false)
	@ColumnDefault("true")
	private boolean stored;

	@Column(nullable = false)
	@ColumnDefault("false")
	private boolean multiValued;

	@Column(nullable = false)
	@ColumnDefault("false")
	private boolean docValues;

	@Column(nullable = false)
	@ColumnDefault("false")
	private boolean required;

	/** Ein Zwilling <name>Suggested mit copyField für die Vervollständigung beim Tippen. Nicht bei jedem Typ. */
	@Column(nullable = false)
	@ColumnDefault("false")
	private boolean suggest;

	/** Mehrsprachiges JSON: wozu das Feld da ist und welche Daten hinein gehören. */
	@Column(columnDefinition = "text")
	private String description;

	/** Reihenfolge in der Tabelle des Kerns, nicht eindeutig. Sortiert wird nach Position und dann nach name. */
	@Column(nullable = false)
	@ColumnDefault("0")
	private int listingPosition;

	/** Für JPA und Jackson. */
	protected SolrField() {
	}

	/** Admin und Import: jedes Feld außer id. */
	public SolrField(String coreKey, String name, SolrFieldType type, boolean indexed, boolean stored,
			boolean multiValued, boolean docValues, boolean required, boolean suggest, String description,
			int listingPosition) {
		this.coreKey = coreKey;
		this.name = name;
		this.type = type;
		this.indexed = indexed;
		this.stored = stored;
		this.multiValued = multiValued;
		this.docValues = docValues;
		this.required = required;
		this.suggest = suggest;
		this.description = description;
		this.listingPosition = listingPosition;
	}

	public Long getId() {
		return id;
	}

	public String getCoreKey() {
		return coreKey;
	}

	public String getName() {
		return name;
	}

	public SolrFieldType getType() {
		return type;
	}

	public void setType(SolrFieldType type) {
		this.type = type;
	}

	public boolean isIndexed() {
		return indexed;
	}

	public void setIndexed(boolean indexed) {
		this.indexed = indexed;
	}

	public boolean isStored() {
		return stored;
	}

	public void setStored(boolean stored) {
		this.stored = stored;
	}

	public boolean isMultiValued() {
		return multiValued;
	}

	public void setMultiValued(boolean multiValued) {
		this.multiValued = multiValued;
	}

	public boolean isDocValues() {
		return docValues;
	}

	public void setDocValues(boolean docValues) {
		this.docValues = docValues;
	}

	public boolean isRequired() {
		return required;
	}

	public void setRequired(boolean required) {
		this.required = required;
	}

	public boolean isSuggest() {
		return suggest;
	}

	public void setSuggest(boolean suggest) {
		this.suggest = suggest;
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

}
