package codergod1337.app1.solr.hook.model;

/**
 * Wohin ein Badge mit dem Wert eines Hooks führt (User 2026-10-13), gepflegt je Hook im SolrManager. Nur für das
 * Frontend: Es steht allein an der Definition des Hooks (solr_hook), nie in den Zeilen der Kerne und nie in Solr, und
 * reist mit den Stammdaten. Weitere Ziele je Nummernart kommen als weitere Werte dazu.
 */
public enum SolrHookValueBadgeLinkTarget {

	/** Die Vorgabe: in die Suche mit dem Wert als Filter; in der Suche selbst wird der Wert ein weiterer Filter */
	SEARCH,

	/** In die Artikelmappe der Nummer, z. B. artikelNummer und masterArtikelNummer */
	ARTIKELMAPPE,

	/** In die Kundenmappe der Nummer, z. B. kundenNummer */
	KUNDENMAPPE,

	/** In die Lieferantenmappe der Nummer, z. B. lieferantenNummer */
	LIEFERANTENMAPPE

}
