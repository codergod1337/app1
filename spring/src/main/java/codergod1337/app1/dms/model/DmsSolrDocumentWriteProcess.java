package codergod1337.app1.dms.model;

/**
 * Die Schreibvorgänge am Kern DMS, je Vorgang eine Methode im DmsSolrDocumentWriteService (Vorgabe 2026-10-07). Der
 * Name steht mit jedem Schreiben im Log, zusammen mit current user, id und Feldnamen.
 */
public enum DmsSolrDocumentWriteProcess {

	/** Upload neu: das ganze Dokument, readAcl und writeAcl aus der Dateiart */
	DMS_UPLOAD_CREATE,

	/** dieselben Bytes noch einmal hochgeladen: Hooks ADD, Beschreibung angehängt, sonst nichts */
	DMS_UPLOAD_MERGE_HOOKS,

	/** neue Fassung einer Versionskette: das ganze Dokument, Dateiart und ACLs vom Kopf der Kette */
	DMS_NEW_VERSION_CREATE,

	/** die bisherigen ACTIVE-Glieder der Kette auf ARCHIVED */
	DMS_NEW_VERSION_ARCHIVE_PREVIOUS,

	/** die Bearbeiten-Maske: nur die geschickten Felder, bei Dateiartwechsel ACLs neu */
	DMS_EDIT,

	/** Dateiartwechsel: jedes andere Glied der Kette bekommt dieselbe Dateiart und ACLs */
	DMS_EDIT_FILE_SUB_CLASS_OF_CHAIN,

	/** weiches Löschen: status DELETED */
	DMS_SOFT_DELETE,

	/** Dateifreigabe setzen: die guid des Empfängers ADD in userGrantedAccess jedes Treffers */
	DMS_DOCUMENT_GRANT_ADD_GRANTEE,

	/** Dateifreigabe entziehen oder abgelaufen: die guid des Empfängers REMOVE aus userGrantedAccess */
	DMS_DOCUMENT_GRANT_REMOVE_GRANTEE

}
