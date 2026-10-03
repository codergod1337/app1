package codergod1337.app1.solr.field.model;

/**
 * Unser Typkatalog für Solr-Felder. Jeder Eintrag ist ein fieldType im Basis-Schema, die Maske wählt nur aus, nie rohe
 * Analyzer-Ketten. Der Suggest-Zwilling wählt sich seinen Typ selbst: wortweise bei Texten, ganzwertig bei String und
 * Zahlen, gar keinen bei Boolean und Datum.
 */
public enum SolrFieldType {

	/** exakter Wert, nicht zerlegt, z. B. ein Status oder ein Key */
	STRING("string", "code_suggest"),

	BOOLEAN("boolean", null),

	/** ganze Zahl, 64 Bit. Es gibt bewusst kein pint: Ganze Zahlen sind bei uns immer long. */
	LONG("plong", "code_suggest"),

	/** Kommazahl, 64 Bit */
	DOUBLE("pdouble", "code_suggest"),

	/** Zeitpunkt, ISO 8601 in UTC */
	DATE("pdate", null),

	/** Leerzeichen trennen, Kleinschreibung, sonst nichts: z. B. Dateinamen */
	TEXT_MINIMAL("text_minimal", "text_suggest"),

	/** auch Satzzeichen trennen: Beschreibungen, mehrsprachiges JSON */
	TEXT_WORD("text_word", "text_suggest"),

	/** vom Backend aufbereitete Token, der Volltext. Die Suche muss ihre Eingabe genauso aufbereiten. */
	TEXT_WORDS("text_words", "text_suggest");

	private final String solrFieldType;
	private final String suggestSolrFieldType;

	SolrFieldType(String solrFieldType, String suggestSolrFieldType) {
		this.solrFieldType = solrFieldType;
		this.suggestSolrFieldType = suggestSolrFieldType;
	}

	/** Der Name des fieldType im Basis-Schema */
	public String getSolrFieldType() {
		return solrFieldType;
	}

	/** Der fieldType des Suggest-Zwillings, null wenn dieser Typ keinen bekommt */
	public String getSuggestSolrFieldType() {
		return suggestSolrFieldType;
	}

	public boolean isSuggestable() {
		return suggestSolrFieldType != null;
	}

}
