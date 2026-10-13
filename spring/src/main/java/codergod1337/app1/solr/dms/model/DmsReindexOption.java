package codergod1337.app1.solr.dms.model;

/**
 * Was ein Lauf über alle Dokumente des Kerns DMS nachzieht (User 2026-10-10). Jede Option ist ein Block in
 * SolrDocumentDmsService.reindexDmsDocuments; eine neue Option ist ein neuer Wert hier und ein neuer Block dort.
 */
public enum DmsReindexOption {

	/** readAcl und writeAcl neu aus der Dateiart des Dokuments, wie sie jetzt in Postgres steht */
	ACLS_FROM_FILE_SUB_CLASS,

	/** die Datei vom SFTP holen, durch Tika, searchableWords neu; die Spracherkennung kommt später dazu */
	SEARCHABLE_WORDS_FROM_BINARY

}
