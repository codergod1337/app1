package codergod1337.app1.solr.hook.model;

import codergod1337.app1.solr.field.model.SolrFieldType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.ColumnDefault;

/**
 * Ein Hook: eine Adresse, über die ein Solr-Dokument auf ein Fachobjekt zeigt, z. B. artikelNummer. Jeder Kern bekommt
 * dieselben Hooks, deshalb lässt sich der Zugriff kernunabhängig steuern.
 *
 * Der Hook trägt keine Rechte. Wer HOOK_FULL hat, darf jeden Hook benutzen, alle anderen nur, was ihnen je Hook und
 * Wert freigegeben ist. Jeder Hook ist freigebbar.
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

	/** Reihenfolge in Listen, nicht eindeutig. Sortiert wird nach Position und dann nach key. */
	@Column(nullable = false)
	@ColumnDefault("0")
	private int listingPosition;

	/** Für JPA und Jackson. */
	protected SolrHook() {
	}

	/** Admin und Import: jedes Feld. */
	public SolrHook(String key, String displayName, String description, String hookGroupKey, int listingPosition) {
		this.key = key;
		this.displayName = displayName;
		this.description = description;
		this.hookGroupKey = hookGroupKey;
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

	public int getListingPosition() {
		return listingPosition;
	}

	public void setListingPosition(int listingPosition) {
		this.listingPosition = listingPosition;
	}

}
