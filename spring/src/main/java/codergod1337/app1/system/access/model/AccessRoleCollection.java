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
 * Eine AccessRoleCollection (ARC): eine Position, die mehrere AccessRoles bündelt. Der Normalfall ist, den Positionen
 * AR zuzuweisen und jedem User genau eine ARC.
 *
 * Aufgelöst wird sie erst beim Ausstellen des Tokens: die AR des Users und die seiner ARC werden zusammengeworfen.
 * Kein system-Flag: eine ARC steht nie im Code, sie ist immer eine Zusammenstellung durch den Admin.
 *
 * Badge wie bei der AccessRole (Farben immer Hex, null: Standard des Themes), aber in Pillenform.
 */
@Entity
@Table(name = "access_role_collection")
public class AccessRoleCollection {

	private static final String HEX_COLOR = "#[0-9a-fA-F]{6}";

	/** Der Schlüssel, z. B. ARC_BUCHHALTUNG. Das Präfix ARC_ ist Pflicht (prüft der Service). Ändert sich nie. */
	@Id
	@NotBlank
	@Size(max = 200)
	@Column(nullable = false, updatable = false, length = 200)
	private String key;

	/** Mehrsprachiges JSON, z. B. {"de":"Buchhaltung"}. Pflicht. */
	@NotBlank
	@Column(nullable = false, columnDefinition = "text")
	private String displayName;

	/** Mehrsprachiges JSON wie displayName, optional. */
	@Column(columnDefinition = "text")
	private String description;

	/** Die gebündelten AccessRoles. Nur keys, kein Fremdschlüssel: dass es sie gibt, prüft der Service beim Speichern. */
	@JdbcTypeCode(SqlTypes.ARRAY)
	@Column(columnDefinition = "text[]")
	private List<String> accessRoleKeys;

	/**
	 * Die ARCs, die diese ARC überwacht und in Vertretung bearbeitet. Nicht rekursiv: es zählen nur die eingetragenen
	 * keys, jede andere ARC wird von Hand verknüpft. So gibt es keine Ketten und keine Endlosschleifen. Sich selbst
	 * nennen darf eine ARC nicht, das prüft der Service.
	 */
	@JdbcTypeCode(SqlTypes.ARRAY)
	@Column(columnDefinition = "text[]")
	private List<String> slaveArcKeys;

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

	/** CSS-Klassen der Badge, stapelbar, z. B. ["screws","shine"]. */
	@JdbcTypeCode(SqlTypes.ARRAY)
	@Column(columnDefinition = "text[]")
	private List<String> badgeStyles;

	/** Optionales Symbol, z. B. {"pack":"tabler","id":"desk"}. null: kein Symbol. */
	@Valid
	@JdbcTypeCode(SqlTypes.JSON)
	@Column(columnDefinition = "jsonb")
	private AccessRoleSymbol symbol;

	/** Deckkraft der Badge in Prozent, 25 bis 100. null: volle Deckkraft (100 speichert der Service als null). */
	@Min(25)
	@Max(100)
	private Integer badgeOpacity;

	/** Reihenfolge in Listen und der Badges, nicht eindeutig. Sortiert wird nach Position und dann nach key. */
	@Column(nullable = false)
	@ColumnDefault("0")
	private int listingPosition;

	/** Für JPA und Jackson. */
	protected AccessRoleCollection() {
	}

	/** Admin und Import: jedes Feld. */
	public AccessRoleCollection(String key, String displayName, String description, List<String> accessRoleKeys,
			List<String> slaveArcKeys, String badgeTextColor, String badgeTextShadowColor, String badgeGradientStart,
			String badgeGradientEnd, String badgeBorderColor, String badgeShadowColor, BadgeGradient badgeGradient,
			List<String> badgeStyles, AccessRoleSymbol symbol, Integer badgeOpacity, int listingPosition) {
		this.key = key;
		this.displayName = displayName;
		this.description = description;
		this.accessRoleKeys = accessRoleKeys;
		this.slaveArcKeys = slaveArcKeys;
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

	public List<String> getAccessRoleKeys() {
		return accessRoleKeys;
	}

	public void setAccessRoleKeys(List<String> accessRoleKeys) {
		this.accessRoleKeys = accessRoleKeys;
	}

	public List<String> getSlaveArcKeys() {
		return slaveArcKeys;
	}

	public void setSlaveArcKeys(List<String> slaveArcKeys) {
		this.slaveArcKeys = slaveArcKeys;
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
