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
 * es setzt, wie schwer es in der Suche wiegt und wozu es da ist. Neu gefasst 2026-10-09 (DMS als Blaupause):
 *
 * <ul>
 * <li>coreKey leer heißt: in jedem Kern, z. B. id, cursorDate und userGrantedAccess. Der Abgleich legt so ein Feld in
 * jedem Kern an, jeder Kern-Tab zeigt es.</li>
 * <li>Ein Hook (solr_hook) liegt in jedem Kern als eigene Zeile hier, mit hookKey gleich seinem Key und name gleich
 * seinem Key. Die Zeile entsteht und verschwindet mit dem Hook und mit dem Kern, von Hand gibt es sie nicht. Technik
 * und Schreibregel stehen fest (HOOK_*), je Kern änderbar sind nur Leseliste und Suchgewicht. So kennen Auflösen und
 * Suche nur Felder, keinen Sonderfall Hook.</li>
 * <li>Der Name eines normalen Feldes ist eindeutig über alle Kerne, der eines Hooks in jedem Kern derselbe: Unique ist
 * deshalb Kern plus Name, der Service prüft bei normalen Feldern weiter über alle Kerne.</li>
 * <li>readAccessRoleKeys: die ARs, deren Inhaber das Feld lesen dürfen, und nur die. Leer heißt niemand außer
 * SOLR_USERS_VIEW_ALL. „Immer“ heißt: USER steht dran, die Grundmenge. Dieselbe Liste entscheidet, wonach ein User
 * auflösen darf. Sensible Felder wie sftpPath oder der Volltext bekommen eine eigene AR, es gibt keine Felder, die
 * pauschal nie zum Client gehen (User 2026-10-09). Nur in dieser Tabelle, nie in Solr.</li>
 * <li>searchWeight: das Gewicht in der qf der Suche. -1 heißt nicht durchsucht (die Suchwörter erreichen das Feld und
 * seinen Zwilling nicht), 0 heißt durchsucht ohne Punkte (feld^0: der Treffer zählt und wird gemeldet, ordnet aber
 * nicht), ab 1 das Gewicht (User 2026-10-09). Ab 0 nur mit indexed, dann hat Solr etwas zum Durchsuchen; sonst
 * entscheidet der Admin allein. Was ein Treffer ist, bestimmt der Typ: text_edge und text_suche Wortanfang bzw. Wort,
 * suggest und Hooks über den Zwilling, string ohne Zwilling nur der ganze Wert exakt.</li>
 * <li>required: Der Client muss das Feld beim Anlegen mitschicken, sonst wird der Upload abgelehnt; in Solr derselbe
 * Schalter. Nur bei einer Schreibregel, bei der der Client beim Anlegen setzt.</li>
 * </ul>
 *
 * Kein Code legt Felder an. Sie entstehen im Kern-Tab des SolrManagers, dem Werkzeug der Entwicklung, oder kommen per
 * Stammdaten-Import zurück; der Export sichert den Stand (User 2026-10-09). Die id ist nur die Zeile in der Datenbank
 * und geht nicht in den Export.
 */
@Entity
@Table(name = "solr_field", uniqueConstraints = @UniqueConstraint(columnNames = { "core_key", "name" }))
public class SolrField {

	// ===== die feste Technik einer Hook-Zeile: exakter Wert, filterbar, mehrere Werte, Facetten, Zwilling fürs Tippen =====

	public static final SolrFieldType HOOK_TYPE = SolrFieldType.STRING;
	public static final boolean HOOK_INDEXED = true;
	public static final boolean HOOK_STORED = true;
	public static final boolean HOOK_MULTI_VALUED = true;
	public static final boolean HOOK_DOC_VALUES = true;
	/** jeder Hook ist je Dokument optional */
	public static final boolean HOOK_REQUIRED = false;
	public static final boolean HOOK_SUGGEST = true;
	public static final SolrFieldClientWriteRule HOOK_CLIENT_WRITE_RULE = SolrFieldClientWriteRule.CLIENT_SETS_AND_CHANGES;
	/** Startgewicht eines Hooks in der Suche, wie in der Vorlage */
	public static final int HOOK_SEARCH_WEIGHT = 2;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	/** Der Kern, zu dem das Feld gehört (SolrCore.key). null: in jedem Kern. Ändert sich nie. */
	@Size(max = 200)
	@Column(name = "core_key", updatable = false, length = 200)
	private String coreKey;

	/** Der Name des Feldes in Solr, z. B. fileName. Ändert sich nie: Die Dokumente tragen ihn. */
	@NotBlank
	@Size(max = 100)
	@Column(nullable = false, updatable = false, length = 100)
	private String name;

	/** Der Key des Hooks (SolrHook.key), wenn diese Zeile ein Hook in diesem Kern ist, sonst null. Ändert sich nie. */
	@Size(max = 100)
	@Column(name = "hook_key", updatable = false, length = 100)
	private String hookKey;

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

	/**
	 * Ein Zwilling <name>Ngram vom Typ text_ngram mit copyField (User 2026-10-16): jedes Stück ab 3 Zeichen trifft, an
	 * jeder Stelle des Werts, das Feld selbst bleibt exakt und facettierbar. Solr füllt den Zwilling bei jedem Schreiben
	 * neu, auch nach einem Atomic Update ADD, denn es indexiert das Dokument dann aus den stored fields komplett neu. Der
	 * Zwilling ist nie stored, sonst verdoppelte jedes Update seine Werte. Nur bei Typen mit Text
	 * ({@link SolrFieldType#isNgramTwinAllowed()}), nie an einer Hook-Zeile.
	 */
	@Column(nullable = false)
	@ColumnDefault("false")
	private boolean ngram;

	/**
	 * Essenziell (User 2026-10-14): Ohne dieses Feld ergibt ein Dokument des Kerns keinen Sinn, z. B. Artikelnummer, Name
	 * und Sternzeit eines Artikels oder die Dateiart eines DMS-Dokuments. Der Import eines Kerns verlangt jedes
	 * essenzielle Feld, aus der Datei oder über eine Zuordnung. Je Zeile, also je Kern, auch bei einer Hook-Zeile; id ist
	 * immer essenziell. Nur in dieser Tabelle, nie in Solr: anders als required lehnt Solr ohne das Feld nichts ab.
	 */
	@Column(nullable = false)
	@ColumnDefault("false")
	private boolean essential;

	// ===== wer liest, wer schreibt, wie schwer in der Suche =====

	/**
	 * Die ARs, deren Inhaber das Feld lesen dürfen. Leer heißt niemand außer SOLR_USERS_VIEW_ALL. Nur keys, kein
	 * Fremdschlüssel: dass es sie gibt, prüft der Service beim Speichern. Nur in dieser Tabelle, nie in Solr.
	 */
	@JdbcTypeCode(SqlTypes.ARRAY)
	@Column(name = "read_access_role_keys", columnDefinition = "text[]")
	private List<String> readAccessRoleKeys;

	/**
	 * Wer das Feld setzt ({@link SolrFieldClientWriteRule}): Client beim Anlegen und Bearbeiten, Client nur beim
	 * Anlegen, oder nur das Backend. Nur in dieser Tabelle, nie in Solr. Neue Felder sind BACKEND_ONLY.
	 */
	@NotNull
	@Enumerated(EnumType.STRING)
	@Column(name = "client_write_rule", nullable = false, length = 40)
	@ColumnDefault("'BACKEND_ONLY'")
	private SolrFieldClientWriteRule clientWriteRule = SolrFieldClientWriteRule.BACKEND_ONLY;

	/** Das Gewicht in der qf der Suche, 0 heißt nicht durchsucht. Nur in dieser Tabelle, nie in Solr. */
	@Column(name = "search_weight", nullable = false)
	@ColumnDefault("0")
	private int searchWeight;

	/**
	 * Der lose Fremdschlüssel aufs Mapping (User 2026-10-10): der Key eines Mapping-Eintrags, z. B. FIRMA_NAME, dessen
	 * gemappte Werte in dieses Feld gehen. Nur der Key, kein Fremdschlüssel in der Datenbank; ob es den Eintrag gibt,
	 * zeigt der Kern-Tab. null heißt, kein Mapping füllt das Feld. Nur in dieser Tabelle, nie in Solr.
	 */
	@Size(max = 200)
	@Column(name = "command", length = 200)
	private String command;

	// ===== Texte und Reihenfolge =====

	/**
	 * Mehrsprachiges JSON wie beim Hook: der Name, den ein User sieht, wo immer das Feld gezeigt wird. null oder leer
	 * heißt, es steht der technische Name. Bei einer Hook-Zeile null, der Name kommt vom Hook.
	 */
	@Column(name = "display_name", columnDefinition = "text")
	private String displayName;

	/** Mehrsprachiges JSON: wozu das Feld da ist und welche Daten hinein gehören. Bei einer Hook-Zeile null. */
	@Column(columnDefinition = "text")
	private String description;

	/** Reihenfolge in der Tabelle des Kerns, nicht eindeutig. Sortiert wird nach Position und dann nach name. */
	@Column(name = "listing_position", nullable = false)
	@ColumnDefault("0")
	private int listingPosition;

	/** Für JPA und Jackson. */
	protected SolrField() {
	}

	/** Admin und Import: ein normales Feld. clientWriteRule null heißt BACKEND_ONLY. */
	public SolrField(String coreKey, String name, SolrFieldType type, boolean indexed, boolean stored,
			boolean multiValued, boolean docValues, boolean required, boolean suggest, List<String> readAccessRoleKeys,
			SolrFieldClientWriteRule clientWriteRule, int searchWeight, String displayName, String description,
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
		this.clientWriteRule = clientWriteRule != null ? clientWriteRule : SolrFieldClientWriteRule.BACKEND_ONLY;
		this.searchWeight = searchWeight;
		this.displayName = displayName;
		this.description = description;
		this.listingPosition = listingPosition;
	}

	/** Der Service: die Zeile eines Hooks in einem Kern, Technik fest, ohne Leserolle, mit dem Startgewicht der Suche. */
	public static SolrField forHook(String coreKey, String hookKey, int listingPosition) {
		SolrField solrField = new SolrField(coreKey, hookKey, HOOK_TYPE, HOOK_INDEXED, HOOK_STORED, HOOK_MULTI_VALUED,
				HOOK_DOC_VALUES, HOOK_REQUIRED, HOOK_SUGGEST, List.of(), HOOK_CLIENT_WRITE_RULE, HOOK_SEARCH_WEIGHT, null,
				null, listingPosition);
		solrField.hookKey = hookKey;
		return solrField;
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

	/** null bei einem normalen Feld, sonst der Key des Hooks, den diese Zeile in ihrem Kern vertritt */
	public String getHookKey() {
		return hookKey;
	}

	/** true, wenn diese Zeile ein Hook in ihrem Kern ist */
	public boolean isHookField() {
		return hookKey != null;
	}

	/** true, wenn ein Suchgewicht wirken kann: nur indexed zählt, ohne Index hat Solr nichts zum Durchsuchen */
	public boolean isSearchable() {
		return indexed;
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

	public boolean isNgram() {
		return ngram;
	}

	public void setNgram(boolean ngram) {
		this.ngram = ngram;
	}

	public boolean isEssential() {
		return essential;
	}

	public void setEssential(boolean essential) {
		this.essential = essential;
	}

	/** Nie null: Eine alte Zeile ohne Wert oder ein Import ohne das Feld gilt als leer, also niemand. */
	public List<String> getReadAccessRoleKeys() {
		return readAccessRoleKeys != null ? readAccessRoleKeys : List.of();
	}

	public void setReadAccessRoleKeys(List<String> readAccessRoleKeys) {
		this.readAccessRoleKeys = readAccessRoleKeys != null ? List.copyOf(readAccessRoleKeys) : List.of();
	}

	/** Nie null: Eine alte Zeile ohne Wert oder ein Import ohne das Feld gilt als BACKEND_ONLY. */
	public SolrFieldClientWriteRule getClientWriteRule() {
		return clientWriteRule != null ? clientWriteRule : SolrFieldClientWriteRule.BACKEND_ONLY;
	}

	public void setClientWriteRule(SolrFieldClientWriteRule clientWriteRule) {
		this.clientWriteRule = clientWriteRule != null ? clientWriteRule : SolrFieldClientWriteRule.BACKEND_ONLY;
	}

	public int getSearchWeight() {
		return searchWeight;
	}

	public void setSearchWeight(int searchWeight) {
		this.searchWeight = searchWeight;
	}

	/** Der Key des Mapping-Eintrags, der das Feld füllt; null heißt keiner */
	public String getCommand() {
		return command;
	}

	public void setCommand(String command) {
		this.command = command;
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
