package codergod1337.app1.solr.field.model;

/**
 * Unser Typkatalog für Solr-Felder. Jeder Eintrag ist ein fieldType im Basis-Schema, die Maske wählt nur aus, nie rohe
 * Analyzer-Ketten. Der Suggest-Zwilling (code_suggest) gibt es nur bei String und Zahlen: Texte brauchen keinen,
 * text_edge ist selbst per Wortanfang suchbar. Ein neuer Typ hier braucht denselben Typ im Basis-Configset, und ein
 * bestehender Kern kennt ihn erst nach dem Neuaufbau, denn das Configset wird nur beim Anlegen des Kerns kopiert.
 *
 * Genau zwei Texttypen (User 2026-10-09): text_edge für alles, was ein User tippt und beim Tippen wiederfinden will,
 * text_suche für den Volltext.
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

	/**
	 * Text, der selbst wortweise per Wortanfang suchbar ist (EdgeNGram beim Indexieren): „rech“ findet „Rechnung“ im
	 * Feld selbst. Dateiname, Index, Beschreibung, alles, was ein User tippt.
	 */
	TEXT_EDGE("text_edge", null),

	/**
	 * Der Volltext: jedes Wort einzeln, Stammform, dazu Komposita-Zerlegung und Synonyme, sobald die Dateien dafür da
	 * sind. „Rechnungen“ findet „Rechnung“, „Kapitänschifffahrtsmütze“ soll über kapitän, schiff und mütze treffen.
	 */
	TEXT_SUCHE("text_suche", null);

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

	/** Ein Textfeld: Werte werden zerlegt, exakte Bedingungen gehen als Phrase, nicht über den terms-Parser */
	public boolean isText() {
		return this == TEXT_EDGE || this == TEXT_SUCHE;
	}

}
