package codergod1337.app1.mapping.model;

/** Was ein Eintrag eines {@link ResponseMapping} tut */
public enum ResponseMappingLineKind {

	/** Den Wert an der Quelle lesen und an den Key geben; mit elementPath die Liste aller Elementwerte */
	VALUE,

	/**
	 * Eine Tabelle in ein Feld (User 2026-10-10): die Quelle ist eine Liste, je Element eine Zeile mit den Spalten aus
	 * tableColumnsJson; jede Zeile geht als JSON-Objekt an den Key, das Feld ist multiValued, ein Wert je Zeile
	 */
	TABLE,

	/** Ein Objekt über seine Sternzeit holen: der Parameter sternzeit aus dem JSON oder statisch; die Antwort zerlegt das Mapping ihres Typs */
	SUBFETCH_STERNZEIT,

	/** Ein hinterlegtes Infosystem (abasInfosysKey) ausführen, die Platzhalter seiner Vorlage aus dem JSON oder statisch; die Antwort zerlegt das Mapping ihres Typs */
	SUBFETCH_INFOSYS

}
