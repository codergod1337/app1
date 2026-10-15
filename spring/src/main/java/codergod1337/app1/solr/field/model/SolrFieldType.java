package codergod1337.app1.solr.field.model;

/**
 * Unser Typkatalog für Solr-Felder. Jeder Eintrag ist ein fieldType im Basis-Schema, die Maske wählt nur aus, nie rohe
 * Analyzer-Ketten. Der Suggest-Zwilling (code_suggest) gibt es nur bei String und Zahlen: Texte brauchen keinen,
 * text_edge ist selbst per Wortanfang suchbar. Ein neuer Typ hier braucht denselben Typ im Basis-Configset, und ein
 * bestehender Kern kennt ihn erst nach dem Neuaufbau, denn das Configset wird nur beim Anlegen des Kerns kopiert;
 * text_ngram legt SolrSchemaSyncService in älteren Kernen selbst an.
 *
 * Drei Texttypen: text_edge für alles, was ein User tippt und beim Tippen wiederfinden will, text_suche für den
 * Volltext (User 2026-10-09), text_ngram für die wenigen Schlüsselfelder, in denen jedes Stück trifft (User 2026-10-14).
 */
public enum SolrFieldType {

	/** exakter Wert, nicht zerlegt, z. B. ein Status oder ein Key */
	STRING("string", "code_suggest"),

	/**
	 * Eine E-Mail-Adresse (User 2026-10-13): im Feld selbst ein exakter Wert wie string, der Zwilling email_suggest
	 * kennt aber jeden Anfang bis zur vollen Länge der Adresse, nicht nur bis 30 Zeichen wie code_suggest. So trifft
	 * jede getippte Länge, und die ganze Adresse klein geschrieben trifft immer, auch ohne Rücksicht auf Groß- und
	 * Kleinschreibung.
	 */
	EMAIL("string", "email_suggest"),

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
	 * Text, in dem jedes Stück ab 3 Zeichen trifft, an jeder Stelle des Worts (NGram beim Indexieren, User 2026-10-14):
	 * „schild“ und „folie“ finden „Typenschild 100x50 Polyesterfolie“. Nur für die essenziellen Felder, die jeder User
	 * sieht, wie den Artikelnamen und Dateiname und Beschreibung im DMS: Der Index wächst mit jeder Wortlänge.
	 */
	TEXT_NGRAM("text_ngram", null),

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

	/**
	 * Ob das Feld einen Ngram-Zwilling {@code <name>Ngram} vom Typ text_ngram bekommen kann (User 2026-10-16): jedes
	 * Stück ab 3 Zeichen trifft, das Feld selbst bleibt exakt. Nur bei Werten mit Text: string, E-Mail und die Texttypen
	 * außer text_ngram, das ist selbst schon einer. Zahlen, boolean und date haben nichts, worin ein Wortteil treffen
	 * könnte.
	 */
	public boolean isNgramTwinAllowed() {
		return this == STRING || this == EMAIL || this == TEXT_EDGE || this == TEXT_SUCHE;
	}

	/** Ein Textfeld: Werte werden zerlegt, exakte Bedingungen gehen als Phrase, nicht über den terms-Parser */
	public boolean isText() {
		return this == TEXT_EDGE || this == TEXT_NGRAM || this == TEXT_SUCHE;
	}

}
