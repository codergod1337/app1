package codergod1337.app1.solr.hook.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.ColumnDefault;

/**
 * Ein Hook: eine Adresse, über die ein Solr-Dokument auf ein Fachobjekt zeigt, z. B. artikelNummer. Jeder Kern bekommt
 * dieselben Hooks. Hier steht nur die Definition: Key, Anzeigename, Beschreibung, Gruppe, Ziel der Wert-Badges,
 * Reihenfolge.
 *
 * In jedem Kern liegt der Hook als eigene Zeile in solr_field (SolrField.hookKey), mit fester Technik (SolrField.HOOK_*):
 * string für den exakten Wert, indexed zum Filtern, stored für die Chips, multiValued weil ein Dokument auf mehrere
 * Fachobjekte zeigen darf, docValues für Facetten, suggest für den Zwilling {@code <key>Suggested}. Dort stehen je Kern
 * auch Leseliste und Suchgewicht (2026-10-09). Die Zeilen entstehen und verschwinden mit dem Hook, der Service macht das.
 *
 * Kein Aussehen am Hook: Alle Chips sehen gleich aus, der Look (Fels, anthrazit, rot glühende Risse) steht fest in
 * basic.css. Was einen Hook vom anderen unterscheidet, sind Anzeigename und Gruppe.
 *
 * Das Format des Keys prüft {@code SolrFieldName.isValid}, nicht diese Klasse.
 */
@Entity
@Table(name = "solr_hook")
public class SolrHook {

	/** Der Name des Feldes in Solr, z. B. artikelNummer. Von Hand vergeben, ändert sich nie. */
	@Id
	@NotBlank
	@Size(max = 100)
	@Column(nullable = false, updatable = false, length = 100)
	private String key;

	/** Mehrsprachiges JSON, z. B. {"de":"Artikelnummer"}. Pflicht. */
	@NotBlank
	@Column(name = "display_name", nullable = false, columnDefinition = "text")
	private String displayName;

	/** Mehrsprachiges JSON wie displayName, optional. */
	@Column(columnDefinition = "text")
	private String description;

	/**
	 * Der key der Gruppe ({@link SolrHookGroup}), nur für die Anzeige: Kern-Tab, Hook-Auswahl und Upload zeigen die Hooks
	 * gruppenweise. null: ohne Gruppe. Dass es die Gruppe gibt, prüft der Service beim Schreiben.
	 */
	@Size(max = 200)
	@Column(name = "hook_group_key", length = 200)
	private String hookGroupKey;

	/**
	 * Wohin ein Badge mit einem Wert dieses Hooks führt (User 2026-10-13): Suche, Artikel-, Kunden- oder
	 * Lieferantenmappe. Nur für das Frontend, es kommt mit den Stammdaten; die Zeilen des Hooks in den Kernen und Solr
	 * kennen es nicht. Fehlt es im Body, gilt die Suche.
	 */
	@Enumerated(EnumType.STRING)
	@Column(name = "value_badge_link_target", nullable = false, length = 30)
	@ColumnDefault("'SEARCH'")
	private SolrHookValueBadgeLinkTarget valueBadgeLinkTarget = SolrHookValueBadgeLinkTarget.SEARCH;

	/** Reihenfolge in Listen, nicht eindeutig. Sortiert wird nach Position und dann nach key. */
	@Column(name = "listing_position", nullable = false)
	@ColumnDefault("0")
	private int listingPosition;

	/** Für JPA und Jackson. */
	protected SolrHook() {
	}

	/** Admin und Import: jedes Feld. Ein fehlendes Ziel der Badges heißt Suche. */
	public SolrHook(String key, String displayName, String description, String hookGroupKey,
			SolrHookValueBadgeLinkTarget valueBadgeLinkTarget, int listingPosition) {
		this.key = key;
		this.displayName = displayName;
		this.description = description;
		this.hookGroupKey = hookGroupKey;
		this.valueBadgeLinkTarget = valueBadgeLinkTarget != null ? valueBadgeLinkTarget : SolrHookValueBadgeLinkTarget.SEARCH;
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

	public SolrHookValueBadgeLinkTarget getValueBadgeLinkTarget() {
		return valueBadgeLinkTarget;
	}

	public void setValueBadgeLinkTarget(SolrHookValueBadgeLinkTarget valueBadgeLinkTarget) {
		this.valueBadgeLinkTarget = valueBadgeLinkTarget;
	}

	public int getListingPosition() {
		return listingPosition;
	}

	public void setListingPosition(int listingPosition) {
		this.listingPosition = listingPosition;
	}

}
