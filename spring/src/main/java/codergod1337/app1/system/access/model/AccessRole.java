package codergod1337.app1.system.access.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Eine Berechtigungsrolle (AR). Der Key ist der Primärschlüssel: Er steht im Code und im Token, eine zusätzliche
 * UUID brächte nichts.
 *
 * Das Format des Keys prüft {@code HelperInputs.isValidKey}, nicht diese Klasse.
 *
 * Die Badge-Farben sind immer Hex (#RRGGBB). null heißt jeweils: Standard des Themes.
 */
@Entity
@Table(name = "access_role")
public class AccessRole {

	private static final String HEX_COLOR = "#[0-9a-fA-F]{6}";

	/** Der Schlüssel, z. B. ADMIN. Von Hand vergeben, ändert sich nie, sonst zeigten alle Verweise ins Leere. */
	@Id
	@NotBlank
	@Size(max = 200)
	@Column(nullable = false, updatable = false, length = 200)
	private String key;

	/** Mehrsprachiges JSON, z. B. {"de":"Verwalter","en":"Administrator"}. Pflicht. */
	@NotBlank
	@Column(nullable = false, columnDefinition = "text")
	private String displayName;

	/** Mehrsprachiges JSON wie displayName, optional. */
	@Column(columnDefinition = "text")
	private String description;

	/**
	 * true, wenn der Key im Code direkt verwendet wird, z. B. ADMIN. Schützt vor dem Löschen, sonst liefe jede
	 * Prüfung darauf ins Leere. Darf nur von false auf true wechseln, nie zurück: Das prüft der Service.
	 */
	@Column(nullable = false)
	@ColumnDefault("false")
	private boolean system;

	/** Schrift und Symbol der Badge. */
	@Pattern(regexp = HEX_COLOR)
	@Column(length = 7)
	private String badgeTextColor;

	/** Schatten hinter der Schrift, damit sie auf jedem Hintergrund lesbar bleibt. */
	@Pattern(regexp = HEX_COLOR)
	@Column(length = 7)
	private String badgeTextShadowColor;

	/** Verlauf, Anfang. Bei FLAT die einzige Hintergrundfarbe. */
	@Pattern(regexp = HEX_COLOR)
	@Column(length = 7)
	private String badgeGradientStart;

	/** Verlauf, Ende. Gleich dem Anfang: einfarbig. */
	@Pattern(regexp = HEX_COLOR)
	@Column(length = 7)
	private String badgeGradientEnd;

	@Pattern(regexp = HEX_COLOR)
	@Column(length = 7)
	private String badgeBorderColor;

	/** Schatten oder Leuchten der Badge, beim 3D-Effekt auch die dunkle Kante. */
	@Pattern(regexp = HEX_COLOR)
	@Column(length = 7)
	private String badgeShadowColor;

	/** Verlaufsart. null: Standard des Themes. */
	@Enumerated(EnumType.STRING)
	@Column(length = 20)
	private BadgeGradient badgeGradient;

	/** CSS-Klassen der Badge, stapelbar, z. B. ["screws","shine"]. Array-Spalte statt eigener Tabelle. */
	@JdbcTypeCode(SqlTypes.ARRAY)
	@Column(columnDefinition = "text[]")
	private List<String> badgeStyles;

	/** Optionales Symbol, z. B. {"pack":"tabler","id":"shield-lock"}. null: kein Symbol. */
	@Valid
	@JdbcTypeCode(SqlTypes.JSON)
	@Column(columnDefinition = "jsonb")
	private AccessRoleSymbol symbol;

	/**
	 * Deckkraft der Badge in Prozent, 25 bis 100, damit sie immer sichtbar bleibt.
	 * null: volle Deckkraft. 100 speichert der Service ebenfalls als null.
	 */
	@Min(25)
	@Max(100)
	private Integer badgeOpacity;

	/** Reihenfolge in Listen, nicht eindeutig. Sortiert wird nach Position und dann nach key. */
	@Column(nullable = false)
	@ColumnDefault("0")
	private int listingPosition;

	/** Für JPA und Jackson. */
	protected AccessRole() {
	}

	/** Admin und Import: jedes Feld. */
	public AccessRole(String key, String displayName, String description, boolean system, String badgeTextColor,
			String badgeTextShadowColor, String badgeGradientStart, String badgeGradientEnd, String badgeBorderColor,
			String badgeShadowColor, BadgeGradient badgeGradient, List<String> badgeStyles, AccessRoleSymbol symbol,
			Integer badgeOpacity, int listingPosition) {
		this.key = key;
		this.displayName = displayName;
		this.description = description;
		this.system = system;
		this.badgeTextColor = badgeTextColor;
		this.badgeTextShadowColor = badgeTextShadowColor;
		this.badgeGradientStart = badgeGradientStart;
		this.badgeGradientEnd = badgeGradientEnd;
		this.badgeBorderColor = badgeBorderColor;
		this.badgeShadowColor = badgeShadowColor;
		this.badgeGradient = badgeGradient;
		this.badgeStyles = badgeStyles;
		this.symbol = symbol;
		this.badgeOpacity = badgeOpacity;
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

	public boolean isSystem() {
		return system;
	}

	public void setSystem(boolean system) {
		this.system = system;
	}

	public String getBadgeTextColor() {
		return badgeTextColor;
	}

	public void setBadgeTextColor(String badgeTextColor) {
		this.badgeTextColor = badgeTextColor;
	}

	public String getBadgeTextShadowColor() {
		return badgeTextShadowColor;
	}

	public void setBadgeTextShadowColor(String badgeTextShadowColor) {
		this.badgeTextShadowColor = badgeTextShadowColor;
	}

	public String getBadgeGradientStart() {
		return badgeGradientStart;
	}

	public void setBadgeGradientStart(String badgeGradientStart) {
		this.badgeGradientStart = badgeGradientStart;
	}

	public String getBadgeGradientEnd() {
		return badgeGradientEnd;
	}

	public void setBadgeGradientEnd(String badgeGradientEnd) {
		this.badgeGradientEnd = badgeGradientEnd;
	}

	public String getBadgeBorderColor() {
		return badgeBorderColor;
	}

	public void setBadgeBorderColor(String badgeBorderColor) {
		this.badgeBorderColor = badgeBorderColor;
	}

	public String getBadgeShadowColor() {
		return badgeShadowColor;
	}

	public void setBadgeShadowColor(String badgeShadowColor) {
		this.badgeShadowColor = badgeShadowColor;
	}

	public BadgeGradient getBadgeGradient() {
		return badgeGradient;
	}

	public void setBadgeGradient(BadgeGradient badgeGradient) {
		this.badgeGradient = badgeGradient;
	}

	public List<String> getBadgeStyles() {
		return badgeStyles;
	}

	public void setBadgeStyles(List<String> badgeStyles) {
		this.badgeStyles = badgeStyles;
	}

	public AccessRoleSymbol getSymbol() {
		return symbol;
	}

	public void setSymbol(AccessRoleSymbol symbol) {
		this.symbol = symbol;
	}

	public Integer getBadgeOpacity() {
		return badgeOpacity;
	}

	public void setBadgeOpacity(Integer badgeOpacity) {
		this.badgeOpacity = badgeOpacity;
	}

	public int getListingPosition() {
		return listingPosition;
	}

	public void setListingPosition(int listingPosition) {
		this.listingPosition = listingPosition;
	}

}
