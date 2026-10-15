package codergod1337.app1.solr.transfer.model;

/** Was der Import eines Kerns mit einem Dokument macht, dessen id es im Kern schon gibt (User 2026-10-14) */
public enum SolrCoreImportMode {

	/** bekannte überspringen: das vorhandene Dokument bleibt unangetastet */
	SKIP_EXISTING,

	/** löschen und neu schreiben: das Dokument aus der Datei ersetzt das vorhandene komplett */
	REPLACE

}
