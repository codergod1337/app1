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
import java.util.List;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Ein Feld eines Solr-Kerns: sein Name in Solr, sein Typ aus unserem Katalog, die Solr-Schalter, wer es lesen darf, wer
 * es setzt und wozu es da ist. Neu gefasst 2026-10-09 (DMS als Blaupause):
 *
 * <ul>
 * <li>Der Name ist eindeutig über alle Kerne. Ein Feldname bedeutet im ganzen System genau eines, wie bei den Hooks.
 * Views und Rechte verweisen deshalb nur auf den Namen, nie auf Kern plus Name.</li>
 * <li>coreKey leer heißt: in jedem Kern, z. B. id, cursorDate und userGrantedAccess. Der Abgleich legt so ein Feld in
 * jedem Kern an, jeder Kern-Tab zeigt es.</li>
 * <li>id steht wie die anderen ohne Kern in dieser Tabelle, auf Platz 1, aus dem Code (User 2026-10-09). Das
 * Basis-Configset hat es schon beim Anlegen des Kerns. Was hineinkommt, sagt der Code des Kerns.</li>
 * <li>required: Der Client muss das Feld beim Anlegen mitschicken, sonst wird der Upload abgelehnt; in Solr derselbe
 * Schalter. Nur bei einer Schreibregel, bei der der Client beim Anlegen setzt: Ein Backend-Feld ist nie required,
 * das Backend setzt es selbst (User 2026-10-09).</li>
 * <li>readAccessRoleKeys: die ARs, deren Inhaber das Feld lesen dürfen, und nur die. Leer heißt niemand außer
 * SOLR_USERS_VIEW_ALL. „Immer“ heißt: USER steht dran, die Grundmenge. Dieselbe Liste entscheidet, wonach ein
 * User filtern, sortieren, facettieren und im Freitext suchen darf. Nur in dieser Tabelle, nie in Solr.</li>
 * <li>backendOnly: nie zum Client, auch nicht mit SOLR_USERS_VIEW_ALL, z. B. sftpPath und searchableWords.</li>
 * </ul>
 *
 * Hooks sind die zweite Art Solr-Feld, in jedem Kern gleich, mit eigener Tabelle (solr_hook) und derselben AR-Liste.
 *
 * Die id ist nur die Zeile in der Datenbank, wie bei den Zuordnungen, und geht nicht in den Export. Das Format von
 * coreKey und name prüft der Service, nicht diese Klasse.
 */
@Entity
@Table(name = "solr_field", uniqueConstraints = @UniqueConstraint(columnNames = "name"))
public class SolrField {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	/** Der Kern, zu dem das Feld gehört (SolrCore.key). null: in jedem Kern. Ändert sich nie. */
	@Size(max = 200)
	@Column(updatable = false, length = 200)
	private String coreKey;

	/** Der Name des Feldes in Solr, z. B. fileName, eindeutig über alle Kerne. Ändert sich nie: Die Dokumente tragen ihn. */
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

	/** Pflicht beim Anlegen durch den Client, nur zusammen mit einer Schreibregel, bei der der Client setzt. Prüft der Service. */
	@Column(nullable = false)
	@ColumnDefault("false")
	private boolean required;

	/** Ein Zwilling <name>Suggested mit copyField für die Vervollständigung beim Tippen. Nicht bei jedem Typ. */
	@Column(nullable = false)
	@ColumnDefault("false")
	private boolean suggest;

	// ===== wer liest, wer schreibt =====

	/**
	 * Die ARs, deren Inhaber das Feld lesen dürfen. Leer heißt niemand außer SOLR_USERS_VIEW_ALL. Nur keys, kein
	 * Fremdschlüssel: dass es sie gibt, prüft der Service beim Speichern. Nur in dieser Tabelle, nie in Solr.
	 */
	@JdbcTypeCode(SqlTypes.ARRAY)
	@Column(columnDefinition = "text[]")
	private List<String> readAccessRoleKeys;

	/** Nie zum Client, auch nicht mit SOLR_USERS_VIEW_ALL: Das Backend liest es für sich, z. B. sftpPath. */
	@Column(nullable = false)
	@ColumnDefault("false")
	private boolean backendOnly;

	/**
	 * Wer das Feld setzt ({@link SolrFieldClientWriteRule}): Client beim Anlegen und Bearbeiten, Client nur beim
	 * Anlegen, oder nur das Backend. Nur in dieser Tabelle, nie in Solr. Neue Felder sind BACKEND_ONLY.
	 */
	@NotNull
	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 40)
	@ColumnDefault("'BACKEND_ONLY'")
	private SolrFieldClientWriteRule clientWriteRule = SolrFieldClientWriteRule.BACKEND_ONLY;

	// ===== Texte und Reihenfolge =====

	/**
	 * Mehrsprachiges JSON wie beim Hook: der Name, den ein User sieht, wo immer das Feld gezeigt wird. null oder leer
	 * heißt, es steht der technische Name.
	 */
	@Column(columnDefinition = "text")
	private String displayName;

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

	/** Admin, Import und Initialisierer: jedes Feld. clientWriteRule null heißt BACKEND_ONLY. */
	public SolrField(String coreKey, String name, SolrFieldType type, boolean indexed, boolean stored,
			boolean multiValued, boolean docValues, boolean required, boolean suggest, List<String> readAccessRoleKeys,
			boolean backendOnly, SolrFieldClientWriteRule clientWriteRule, String displayName, String description,
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
		this.readAccessRoleKeys = readAccessRoleKeys != null ? List.copyOf(readAccessRoleKeys) : List.of();
		this.backendOnly = backendOnly;
		this.clientWriteRule = clientWriteRule != null ? clientWriteRule : SolrFieldClientWriteRule.BACKEND_ONLY;
		this.displayName = displayName;
		this.description = description;
		this.listingPosition = listingPosition;
	}

	public Long getId() {
		return id;
	}

	/** null: in jedem Kern */
	public String getCoreKey() {
		return coreKey;
	}

	/** true, wenn das Feld in jedem Kern liegt (coreKey leer) */
	public boolean isInEveryCore() {
		return coreKey == null;
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

	/** Nie null: Eine alte Zeile ohne Wert oder ein Import ohne das Feld gilt als leer, also niemand. */
	public List<String> getReadAccessRoleKeys() {
		return readAccessRoleKeys != null ? readAccessRoleKeys : List.of();
	}

	public void setReadAccessRoleKeys(List<String> readAccessRoleKeys) {
		this.readAccessRoleKeys = readAccessRoleKeys != null ? List.copyOf(readAccessRoleKeys) : List.of();
	}

	public boolean isBackendOnly() {
		return backendOnly;
	}

	public void setBackendOnly(boolean backendOnly) {
		this.backendOnly = backendOnly;
	}

	/** Nie null: Eine alte Zeile ohne Wert oder ein Import ohne das Feld gilt als BACKEND_ONLY. */
	public SolrFieldClientWriteRule getClientWriteRule() {
		return clientWriteRule != null ? clientWriteRule : SolrFieldClientWriteRule.BACKEND_ONLY;
	}

	public void setClientWriteRule(SolrFieldClientWriteRule clientWriteRule) {
		this.clientWriteRule = clientWriteRule != null ? clientWriteRule : SolrFieldClientWriteRule.BACKEND_ONLY;
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

}
