package codergod1337.app1.solr.share.model;

/**
 * Was ein Abholschein freigibt (User 2026-10-09): nur die Datei, oder das Dokument mit den gewählten Feldern, bei
 * Kernen mit Datei wahlweise samt Download. Jede Art hat ihre eigene Station, also ihren eigenen Abholendpunkt; ein
 * Schein der falschen Art ist dort ein 404.
 */
public enum ShareTokenKind {

	/** nur die Bytes, mit Dateiname und MIME-Typ */
	BINARY,

	/** das Dokument mit den Feldern aus includedFieldNames, Download nur mit binaryIncluded */
	DOCUMENT

}
