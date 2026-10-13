package codergod1337.app1.solr.artikel.model;

import java.util.List;

/**
 * Die vier Arten einer Verbindung zwischen Artikeln, so wie ABAS sie im Referenzobjekt (obj:data:20:0) je Tabellenzeile
 * im Feld yartreferenz führt (User 2026-10-13): WERKZEUG und BENOETIGT sind gerichtet (der Artikel führt sein Werkzeug,
 * das Werkzeug nicht den Artikel), GEHTMIT und ALTERNATIVE sind von der Sache her beidseitig, stehen in ABAS aber nur
 * auf einer Seite. Darum braucht jede Art Rückverweise, und die Alternativen bilden eine Gruppe, deren Werkzeuge und
 * benötigte Teile für jedes Mitglied gelten.
 *
 * Je Flag sechs Felder im Kern ARTIKEL: das Paar aus dem Subfetch des 20ers (Nummern und Sternzeiten, index-parallel,
 * Tabellenfilter yartreferenz gleich Flag) und die vier Ablagefelder der aufaggregierten Suche, die das Backend per
 * Atomic Update schreibt (BACKEND_ONLY, ohne command, vom Admin im Kern-Tab angelegt).
 */
public enum ArtikelReferenceFlag {

	WERKZEUG("werkzeug", "Werkzeug"), GEHTMIT("gehtmit", "Gehtmit"), ALTERNATIVE("alternative", "Alternative"),
	BENOETIGT("benoetigt", "Benoetigt");

	/** Die Vorsilbe der Felder, der Filterwert klein geschrieben (Regel: Filterwert als Vorsilbe) */
	private final String fieldNamePrefix;

	/** Dieselbe Vorsilbe groß, hinter {@code table} */
	private final String fieldNameInfix;

	ArtikelReferenceFlag(String fieldNamePrefix, String fieldNameInfix) {
		this.fieldNamePrefix = fieldNamePrefix;
		this.fieldNameInfix = fieldNameInfix;
	}

	/** Die Artikelnummern der Verbindungen aus dem 20er, z. B. werkzeugYreferenzText; danach läuft die Rückwärtssuche */
	public String articleNumberFieldName() {
		return fieldNamePrefix + "YreferenzText";
	}

	/** Die Sternzeiten der Verbindungen aus dem 20er, index-parallel zu den Nummern, z. B. werkzeugYreferenz */
	public String sternzeitFieldName() {
		return fieldNamePrefix + "Yreferenz";
	}

	/** Ablage: je Treffer der aufaggregierten Suche ein JSON-Objekt (ArtikelAggregatedReference), z. B. tableWerkzeugAggregiert */
	public String aggregatedTableFieldName() {
		return "table" + fieldNameInfix + "Aggregiert";
	}

	/** Ablage: nur die Nummern der Treffer, indexed, damit die Suchmaske danach filtern kann */
	public String aggregatedArticleNumberFieldName() {
		return fieldNamePrefix + "AggregiertArtikelNummer";
	}

	/** Ablage: mit welcher Tiefe über Alternativen gerechnet wurde (LONG) */
	public String aggregatedDepthFieldName() {
		return fieldNamePrefix + "AggregiertTiefe";
	}

	/** Ablage: wann gerechnet wurde (DATE), das „Stand vom“ der Mappe; null heißt veraltet oder nie gerechnet */
	public String aggregatedAtFieldName() {
		return fieldNamePrefix + "AggregiertAm";
	}

	/** Die vier Ablagefelder, die der Kern haben muss, damit die Suche ihr Ergebnis ablegen kann */
	public List<String> aggregatedFieldNames() {
		return List.of(aggregatedTableFieldName(), aggregatedArticleNumberFieldName(), aggregatedDepthFieldName(),
				aggregatedAtFieldName());
	}

}
