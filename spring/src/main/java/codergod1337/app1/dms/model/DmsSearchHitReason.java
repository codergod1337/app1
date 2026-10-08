package codergod1337.app1.dms.model;

/**
 * Warum ein Dokument ein Treffer ist, ein Eintrag je Feld, nach dem Treffergrund der Vorlage: das Feld, in dem ein
 * Suchwort, ein Filterwert oder ein Zeitraum traf, und der Wert dieses Felds mit der Fundstelle markiert. Markiert
 * wird mit zwei Steuerzeichen (STX, ETX), in echtem Text unmöglich: Die Maske zerteilt daran und fasst jedes Stück
 * als Text in ein mark, es wird nie fremdes Markup gerendert.
 *
 * @param fieldName der Solr-Name des Felds, technisch, denn genau den will man lesen
 * @param snippet   der Wert mit Markierungen; null beim Volltext, der nie zum Client geht, dann steht nur das Feld
 */
public record DmsSearchHitReason(String fieldName, String snippet) {

	/** Anfang und Ende einer Fundstelle: STX (2) und ETX (3), aus dem Zeichencode gebaut, ein unsichtbares Zeichen im Quelltext überlebt nichts */
	public static final String MARK_OPEN = String.valueOf((char) 2);
	public static final String MARK_CLOSE = String.valueOf((char) 3);

}
