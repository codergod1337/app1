package codergod1337.app1.solr.hook.model;

import codergod1337.app1.solr.field.model.SolrFieldType;
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
 * Ein Hook: eine Adresse, über die ein Solr-Dokument auf ein Fachobjekt zeigt, z. B. artikelNummer. Jeder Kern bekommt
 * dieselben Hooks, deshalb lässt sich der Zugriff kernunabhängig steuern.
 *
 * Wer einen Hook lesen und danach filtern darf, steht wie bei jedem Feld als AR-Liste am Hook (readAccessRoleKeys,
 * 2026-10-09), in jedem Kern gleich. SOLR_USERS_VIEW_ALL und SOLR_USERS_VIEW_HOOKS bekommen jeden Hook.
 *
 * Kein Aussehen am Hook: Alle Chips sehen gleich aus, der Look (Fels, anthrazit, rot glühende Risse) steht fest in
 * basic.css. Was einen Hook vom anderen unterscheidet, sind Anzeigename und Gruppe.
 *
 * In Solr ist jeder Hook in jedem Kern dasselbe Feld, mit den festen Einstellungen unten (FIELD_*): string für den
 * exakten Wert, indexed zum Filtern (fq) und Facettieren, stored für die Chips, multiValued weil ein Dokument auf mehrere
 * Fachobjekte zeigen darf, docValues für Facetten mit Zählern, suggest für den Zwilling {@code <key>Suggested} mit den
 * Vorschlägen beim Tippen. Nie required: Jeder Hook ist je Dokument optional. Der Abgleich (SolrManager Schritt 4) legt
 * das Feld so an. Deshalb hat ein Hook keine Schalter in der Tabelle, anders als ein normales Feld.
 *
 * Das Format des Keys prüft {@code SolrFieldName.isValid}, nicht diese Klasse.
 */
@Entity
@Table(name = "solr_hook")
public class SolrHook {

	/** In Solr ist jeder Hook ein Feld dieses Typs: exakter Wert, nicht zerlegt */
	public static final SolrFieldType FIELD_TYPE = SolrFieldType.STRING;
	public static final boolean FIELD_INDEXED = true;
	public static final boolean FIELD_STORED = true;
	public static final boolean FIELD_MULTI_VALUED = true;
	public static final boolean FIELD_DOC_VALUES = true;
	/** jeder Hook ist je Dokument optional */
	public static final boolean FIELD_REQUIRED = false;
	public static final boolean FIELD_SUGGEST = true;

	/** Der Name des Feldes in Solr, z. B. artikelNummer. Von Hand vergeben, ändert sich nie. */
	@Id
	@NotBlank
	@Size(max = 100)
	@Column(nullable = false, updatable = false, length = 100)
	private String key;

	/** Mehrsprachiges JSON, z. B. {"de":"Artikelnummer"}. Pflicht. */
	@NotBlank
	@Column(nullable = false, columnDefinition = "text")
	private String displayName;

	/** Mehrsprachiges JSON wie displayName, optional. */
	@Column(columnDefinition = "text")
	private String description;

	/**
	 * Der key der Gruppe ({@link SolrHookGroup}), nur für die Anzeige: Kern-Tab, Hook-Auswahl und Upload zeigen die Hooks
	 * gruppenweise. null: ohne Gruppe. Dass es die Gruppe gibt, prüft der Service beim Schreiben.
	 */
	@Size(max = 200)
	@Column(length = 200)
	private String hookGroupKey;

	/**
	 * Die ARs, deren Inhaber den Hook lesen und danach filtern dürfen, in jedem Kern (2026-10-09, wie
	 * SolrField.readAccessRoleKeys). Leer heißt niemand außer SOLR_USERS_VIEW_ALL und SOLR_USERS_VIEW_HOOKS. Nur keys,
	 * dass es sie gibt, prüft der Service beim Speichern.
	 */
	@JdbcTypeCode(SqlTypes.ARRAY)
	@Column(columnDefinition = "text[]")
	private List<String> readAccessRoleKeys;

	/** Reihenfolge in Listen, nicht eindeutig. Sortiert wird nach Position und dann nach key. */
	@Column(nullable = false)
	@ColumnDefault("0")
	private int listingPosition;

	/** Für JPA und Jackson. */
	protected SolrHook() {
	}

	/** Admin und Import: jedes Feld. */
	public SolrHook(String key, String displayName, String description, String hookGroupKey,
			List<String> readAccessRoleKeys, int listingPosition) {
		this.key = key;
		this.displayName = displayName;
		this.description = description;
		this.hookGroupKey = hookGroupKey;
		this.readAccessRoleKeys = readAccessRoleKeys != null ? List.copyOf(readAccessRoleKeys) : List.of();
		this.listingPosition = listingPosition;
	}

	public String getKey() {
		return key;
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

	public String getHookGroupKey() {
		return hookGroupKey;
	}

	public void setHookGroupKey(String hookGroupKey) {
		this.hookGroupKey = hookGroupKey;
	}

	/** Nie null: Eine alte Zeile ohne Wert oder ein Import ohne das Feld gilt als leer, also niemand. */
	public List<String> getReadAccessRoleKeys() {
		return readAccessRoleKeys != null ? readAccessRoleKeys : List.of();
	}

	public void setReadAccessRoleKeys(List<String> readAccessRoleKeys) {
		this.readAccessRoleKeys = readAccessRoleKeys != null ? List.copyOf(readAccessRoleKeys) : List.of();
	}

	public int getListingPosition() {
		return listingPosition;
	}

	public void setListingPosition(int listingPosition) {
		this.listingPosition = listingPosition;
	}

}
