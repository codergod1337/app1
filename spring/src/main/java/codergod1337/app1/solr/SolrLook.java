package codergod1337.app1.solr;

import codergod1337.app1.system.access.model.AccessRoleSymbol;
import codergod1337.app1.system.access.model.BadgeGradient;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Das Aussehen eines Kerns (Emblem): ein Verlauf aus drei Farben, Schrift, Schriftschatten, Schatten, Verlaufsart und
 * Symbol. In die Entity eingebettet, die Spalten liegen flach in ihrer Tabelle. Alle Farben sind Hex (#RRGGBB), null
 * heißt jeweils: Standard des Themes. Hooks haben kein Aussehen in der Datenbank, ihr Chip-Look steht fest in basic.css.
 *
 * @param mainColor       Hauptfarbe: bei einfarbig die ganze Fläche, im Verlauf die Mitte
 * @param gradientStart   Verlauf, Anfang. Fehlt er, gilt die Hauptfarbe
 * @param gradientEnd     Verlauf, Ende. Fehlt es, gilt die Hauptfarbe
 * @param textColor       Schrift und Symbol
 * @param textShadowColor Schatten hinter der Schrift, damit sie auf jedem Hintergrund lesbar bleibt
 * @param shadowColor     Schatten um das Element, beim 3D-Effekt auch die dunkle Kante
 * @param gradient        Verlaufsart, dieselben Arten wie bei den Badges. null: Standard des Themes
 * @param symbol          optionales Symbol, z. B. {"pack":"bootstrap","id":"hdd-stack"}. null: kein Symbol
 */
@Embeddable
public record SolrLook(

		@Pattern(regexp = SolrLook.HEX_COLOR)
		@Column(length = 7)
		String mainColor,

		@Pattern(regexp = SolrLook.HEX_COLOR)
		@Column(length = 7)
		String gradientStart,

		@Pattern(regexp = SolrLook.HEX_COLOR)
		@Column(length = 7)
		String gradientEnd,

		@Pattern(regexp = SolrLook.HEX_COLOR)
		@Column(length = 7)
		String textColor,

		@Pattern(regexp = SolrLook.HEX_COLOR)
		@Column(length = 7)
		String textShadowColor,

		@Pattern(regexp = SolrLook.HEX_COLOR)
		@Column(length = 7)
		String shadowColor,

		@Enumerated(EnumType.STRING)
		@Column(length = 20)
		BadgeGradient gradient,

		@Valid
		@JdbcTypeCode(SqlTypes.JSON)
		@Column(columnDefinition = "jsonb")
		AccessRoleSymbol symbol) {

	public static final String HEX_COLOR = "#[0-9a-fA-F]{6}";

	/** Alles Standard. Hibernate liefert ein Embeddable, dessen Spalten alle leer sind, als null: Dann gilt das hier. */
	public static final SolrLook EMPTY = new SolrLook(null, null, null, null, null, null, null, null);

}
